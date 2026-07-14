package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.tools.KosinskiReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Gameplay-scoped model of S3K's {@code Kos_module_queue} and
 * {@code Process_Kos_Module_Queue} readiness state.
 *
 * <p>The renderer may already hold decompressed Java-side patterns; this owner
 * preserves the ROM-visible scheduling contract used by objects that wait on
 * {@code Kos_modules_left}. Each native frame performs exactly one start or DMA
 * completion phase. Archives are FIFO and contain {@code $1000}-byte modules
 * ({@code $800} words), with the final module allowed to be shorter.
 */
public final class KosinskiModuleQueue
        implements RewindSnapshottable<KosinskiModuleQueue.Snapshot> {

    public static final int CAPACITY = 4;
    public enum Phase {
        IDLE,
        READY_TO_START,
        DECOMPRESSION_IN_PROGRESS
    }

    public record ArchiveState(int archiveAddress,
                               int sourceAddress,
                               int destinationVramBytes,
                               int uncompressedBytes,
                               int totalModules,
                               int modulesRemaining,
                               int lastModuleWords,
                               int decompressionEndAddress,
                               boolean initialized) {
    }

    public record Snapshot(List<ArchiveState> archives, Phase phase) {
        public Snapshot {
            archives = List.copyOf(archives);
        }
    }

    private final ArrayDeque<ArchiveState> archives = new ArrayDeque<>(CAPACITY);
    private Phase phase = Phase.IDLE;
    private Rom rom;

    /**
     * Mirrors {@code Queue_Kos_Module}. The first archive is initialized
     * immediately; later archives wait in the four-entry FIFO.
     */
    public boolean enqueue(Rom rom, int sourceAddress, int destinationVramBytes)
            throws IOException {
        if (rom == null) {
            throw new IllegalArgumentException("rom");
        }
        if (archives.size() >= CAPACITY) {
            return false;
        }
        if (this.rom != null && this.rom != rom) {
            throw new IllegalArgumentException("A gameplay KosM queue cannot mix ROM owners");
        }
        this.rom = rom;
        ArchiveState queued = new ArchiveState(sourceAddress, -1,
                destinationVramBytes, 0, 0, 0, 0, -1, false);
        if (archives.isEmpty()) {
            archives.addLast(initialize(queued));
            phase = Phase.READY_TO_START;
        } else {
            archives.addLast(queued);
        }
        return true;
    }

    /**
     * Runs one call of {@code Process_Kos_Module_Queue} at the native gameplay
     * frame phase. The earlier {@code Process_Kos_Queue} call is represented by
     * an in-progress module becoming DMA-ready on the following call.
     */
    public void processNativeFrame() {
        ArchiveState active = archives.peekFirst();
        if (active == null) {
            phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.READY_TO_START) {
            archives.removeFirst();
            archives.addFirst(startDecompression(active));
            phase = Phase.DECOMPRESSION_IN_PROGRESS;
            return;
        }
        if (phase != Phase.DECOMPRESSION_IN_PROGRESS) {
            throw new IllegalStateException("Non-empty KosM queue has phase " + phase);
        }

        int remaining = active.modulesRemaining() - 1;
        int dmaWords = remaining == 0 ? active.lastModuleWords() : 0x800;
        int nextDestination = active.destinationVramBytes() + dmaWords * 2;
        int nextSource = alignToModuleResidue(
                active.sourceAddress(), active.decompressionEndAddress());
        archives.removeFirst();
        if (remaining > 0) {
            archives.addFirst(new ArchiveState(active.archiveAddress(), nextSource,
                    nextDestination, active.uncompressedBytes(), active.totalModules(),
                    remaining, active.lastModuleWords(), -1, true));
            phase = Phase.READY_TO_START;
            return;
        }

        // ROM shifts the four queue entries, initializes the next archive, and
        // returns. It cannot begin that archive until the next gameplay frame.
        if (archives.isEmpty()) {
            phase = Phase.IDLE;
        } else {
            ArchiveState next = archives.peekFirst();
            ArchiveState initialized;
            try {
                initialized = initialize(next);
            } catch (IOException e) {
                throw new IllegalStateException(String.format(
                        "Could not initialize deferred KosM archive at $%06X",
                        next.archiveAddress()), e);
            }
            archives.removeFirst();
            archives.addFirst(initialized);
            phase = Phase.READY_TO_START;
        }
    }

    private ArchiveState initialize(ArchiveState queued) throws IOException {
        int encodedSize = rom.read16BitAddr(queued.archiveAddress()) & 0xFFFF;
        int uncompressedBytes = encodedSize == 0xA000 ? 0x8000 : encodedSize;
        int totalWords = uncompressedBytes >>> 1;
        if (totalWords == 0) {
            throw new IOException(String.format(
                    "Invalid KosM uncompressed size $%04X at $%06X",
                    encodedSize, queued.archiveAddress()));
        }
        int completeModules = totalWords >>> 11;
        int lastModuleWords = totalWords & 0x7FF;
        if (lastModuleWords == 0) {
            completeModules--;
            lastModuleWords = 0x800;
        }
        int totalModules = completeModules + 1;
        return new ArchiveState(queued.archiveAddress(), queued.archiveAddress() + 2,
                queued.destinationVramBytes(), uncompressedBytes,
                totalModules, totalModules, lastModuleWords,
                -1, true);
    }

    private ArchiveState startDecompression(ArchiveState active) {
        if (rom == null) {
            throw new IllegalStateException("KosM ROM owner is unavailable");
        }
        FileChannel channel = rom.getFileChannel();
        if (channel == null) {
            throw new IllegalStateException("KosM ROM channel is unavailable");
        }
        try {
            int compressedEnd;
            synchronized (rom) {
                long priorPosition = channel.position();
                try {
                    channel.position(active.sourceAddress());
                    KosinskiReader.decompress(channel);
                    compressedEnd = Math.toIntExact(channel.position());
                } finally {
                    channel.position(priorPosition);
                }
            }
            return new ArchiveState(active.archiveAddress(), active.sourceAddress(),
                    active.destinationVramBytes(), active.uncompressedBytes(),
                    active.totalModules(), active.modulesRemaining(), active.lastModuleWords(),
                    compressedEnd, true);
        } catch (IOException e) {
            throw new IllegalStateException(String.format(
                    "Could not process KosM module at $%06X", active.sourceAddress()), e);
        }
    }

    static int alignToModuleResidue(int moduleSource, int compressedEnd) {
        return compressedEnd + ((moduleSource - compressedEnd) & 0xF);
    }

    public boolean isIdle() {
        return archives.isEmpty();
    }

    public int queuedArchiveCount() {
        return archives.size();
    }

    public int modulesLeft() {
        ArchiveState active = archives.peekFirst();
        return active != null ? active.modulesRemaining() : 0;
    }

    /** Mirrors the ROM byte, including bit 7 while decompression is pending. */
    public int modulesLeftRaw() {
        int remaining = modulesLeft();
        return phase == Phase.DECOMPRESSION_IN_PROGRESS ? remaining | 0x80 : remaining;
    }

    public Phase phase() {
        return phase;
    }

    public int activeSourceAddress() {
        ArchiveState active = archives.peekFirst();
        return active != null ? active.sourceAddress() : -1;
    }

    public int activeDestinationVramBytes() {
        ArchiveState active = archives.peekFirst();
        return active != null ? active.destinationVramBytes() : -1;
    }

    public List<ArchiveState> queuedArchives() {
        return List.copyOf(archives);
    }

    public void clear() {
        archives.clear();
        phase = Phase.IDLE;
    }

    @Override
    public String key() {
        return "kosinski-module-queue";
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(new ArrayList<>(archives), phase);
    }

    @Override
    public void restore(Snapshot snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }
        archives.addAll(snapshot.archives());
        phase = archives.isEmpty() ? Phase.IDLE : snapshot.phase();
    }
}
