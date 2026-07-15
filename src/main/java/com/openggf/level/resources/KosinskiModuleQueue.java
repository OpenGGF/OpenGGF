package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.tools.KosinskiReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gameplay-scoped model of S3K's {@code Kos_module_queue} and
 * {@code Process_Kos_Module_Queue} readiness state.
 *
 * <p>The renderer may already hold decompressed Java-side patterns; this owner
 * preserves the ROM-visible scheduling contract used by objects that wait on
 * {@code Kos_modules_left}. Each native frame performs exactly one start or DMA
 * completion phase. Archives are FIFO and contain {@code $1000}-byte modules
 * ({@code $800} words), with the final module allowed to be shorter.
 *
 * <p>The bound {@link DmaTarget} is a gameplay-lifetime capability. Its
 * implementation must resolve the currently installed pattern-memory owner on
 * every read and apply; it must not retain a level that can be replaced during
 * an act transition. The queue journals the first byte observed at every
 * touched address plus the deterministic current byte image. Restore first
 * writes the complete pre-journal baseline, then overlays the captured image,
 * so overlapping writes and repeated writes of different lengths round-trip
 * exactly. If the target is temporarily unavailable, a completed DMA retains
 * its payload and native queue counters, while rewind reconciliation retains
 * its baseline/current-image pair. The next native-frame or immediate-DMA call
 * retries those writes against the newly installed owner exactly once before
 * queue processing resumes. A newer restore supersedes the deferred image and
 * publishes only the original physical baseline plus the newly requested image;
 * it never exposes a stale intermediate snapshot. Target implementations must
 * copy or consume payloads synchronously.</p>
 *
 * <p>An unbound queue has no physical pattern-memory contract (for example,
 * ROM-accurate headless scheduling tests), so DMA phases still complete as
 * logical bookkeeping and no physical rewind reconciliation is invented. This
 * is distinct from a bound target whose {@link DmaTarget#isAvailable()} returns
 * false: that represents transient loss of a real sink and must stall.</p>
 */
public final class KosinskiModuleQueue
        implements RewindSnapshottable<KosinskiModuleQueue.Snapshot> {

    public static final int CAPACITY = 4;
    public enum Phase {
        IDLE,
        READY_TO_START,
        DECOMPRESSION_IN_PROGRESS
    }

    /** One completed native KosM DMA payload. */
    public record DmaChunk(int destinationVramBytes, byte[] data) {
        public DmaChunk {
            data = data == null ? new byte[0] : data.clone();
        }
        @Override public byte[] data() { return data.clone(); }
    }

    /** Capability implemented by the gameplay pattern-memory owner. */
    public interface DmaTarget {
        /** False during level-owner gaps; callers retain and retry writes instead of dropping them. */
        default boolean isAvailable() { return true; }
        byte[] read(int destinationVramBytes, int length);
        void apply(DmaChunk chunk);
    }

    public record DmaWriteState(int destinationVramBytes, byte[] data) {
        public DmaWriteState {
            data = data == null ? new byte[0] : data.clone();
        }
        @Override public byte[] data() { return data.clone(); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof DmaWriteState state
                    && destinationVramBytes == state.destinationVramBytes
                    && Arrays.equals(data, state.data);
        }
        @Override public int hashCode() {
            return 31 * Integer.hashCode(destinationVramBytes) + Arrays.hashCode(data);
        }
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

    public record Snapshot(List<ArchiveState> archives, Phase phase, byte[] pendingModuleData,
                           List<DmaWriteState> baselines, List<DmaWriteState> appliedWrites) {
        public Snapshot {
            archives = List.copyOf(archives);
            pendingModuleData = pendingModuleData == null ? null : pendingModuleData.clone();
            baselines = List.copyOf(baselines);
            appliedWrites = List.copyOf(appliedWrites);
        }
        @Override public byte[] pendingModuleData() {
            return pendingModuleData == null ? null : pendingModuleData.clone();
        }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Snapshot snapshot
                    && archives.equals(snapshot.archives)
                    && phase == snapshot.phase
                    && Arrays.equals(pendingModuleData, snapshot.pendingModuleData)
                    && baselines.equals(snapshot.baselines)
                    && appliedWrites.equals(snapshot.appliedWrites);
        }
        @Override public int hashCode() {
            int result = archives.hashCode();
            result = 31 * result + phase.hashCode();
            result = 31 * result + Arrays.hashCode(pendingModuleData);
            result = 31 * result + baselines.hashCode();
            return 31 * result + appliedWrites.hashCode();
        }
    }

    private final ArrayDeque<ArchiveState> archives = new ArrayDeque<>(CAPACITY);
    private Phase phase = Phase.IDLE;
    private Rom rom;
    private byte[] pendingModuleData;
    private DmaTarget dmaTarget;
    private final TreeMap<Integer, Byte> baselineBytes = new TreeMap<>();
    private final TreeMap<Integer, Byte> appliedBytes = new TreeMap<>();
    private List<DmaWriteState> pendingRestoreBaseline;
    private List<DmaWriteState> pendingRestoreImage;

    public void bindDmaTarget(DmaTarget target) {
        this.dmaTarget = target;
    }

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
        if (!flushPendingPatternRestore()) return;
        ArchiveState active = archives.peekFirst();
        if (active == null) {
            phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.READY_TO_START) {
            StartResult started = startDecompression(active);
            archives.removeFirst();
            archives.addFirst(started.state());
            pendingModuleData = started.data();
            phase = Phase.DECOMPRESSION_IN_PROGRESS;
            return;
        }
        if (phase != Phase.DECOMPRESSION_IN_PROGRESS) {
            throw new IllegalStateException("Non-empty KosM queue has phase " + phase);
        }

        int remaining = active.modulesRemaining() - 1;
        int dmaWords = remaining == 0 ? active.lastModuleWords() : 0x800;
        int dmaBytes = dmaWords * 2;
        if (pendingModuleData == null || pendingModuleData.length < dmaBytes) {
            throw new IllegalStateException("KosM module completed without its decompressed DMA payload");
        }
        if (!applyDma(new DmaChunk(active.destinationVramBytes(),
                Arrays.copyOf(pendingModuleData, dmaBytes)))) {
            // A completed native DMA remains pending until pattern memory is
            // installed again; no queue counters or source/destination advance.
            return;
        }
        pendingModuleData = null;
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

    private record StartResult(ArchiveState state, byte[] data) { }

    private StartResult startDecompression(ArchiveState active) {
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
                    byte[] data = KosinskiReader.decompress(channel);
                    compressedEnd = Math.toIntExact(channel.position());
                    return new StartResult(new ArchiveState(active.archiveAddress(), active.sourceAddress(),
                            active.destinationVramBytes(), active.uncompressedBytes(),
                            active.totalModules(), active.modulesRemaining(), active.lastModuleWords(),
                            compressedEnd, true), data);
                } finally {
                    channel.position(priorPosition);
                }
            }
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
        pendingModuleData = null;
        baselineBytes.clear();
        appliedBytes.clear();
        pendingRestoreBaseline = null;
        pendingRestoreImage = null;
    }

    /** Routes synchronous runtime PLC writes through the same rewind journal. */
    public boolean applyImmediateDma(int destinationVramBytes, byte[] data) {
        return flushPendingPatternRestore() && applyDma(new DmaChunk(destinationVramBytes, data));
    }

    private boolean applyDma(DmaChunk chunk) {
        if (dmaTarget == null) return true;
        if (!dmaTarget.isAvailable()) return false;
        if (chunk.data().length == 0) return true;
        byte[] bytes = chunk.data();
        byte[] before = dmaTarget.read(chunk.destinationVramBytes(), bytes.length);
        if (before == null || before.length != bytes.length) {
            throw new IllegalStateException("DMA target returned "
                    + (before == null ? "null" : before.length + " bytes")
                    + " for a " + bytes.length + "-byte read");
        }
        for (int i = 0; i < bytes.length; i++) {
            int address = chunk.destinationVramBytes() + i;
            baselineBytes.putIfAbsent(address, before[i]);
            appliedBytes.put(address, bytes[i]);
        }
        dmaTarget.apply(new DmaChunk(chunk.destinationVramBytes(), bytes));
        return true;
    }

    @Override
    public String key() {
        return "kosinski-module-queue";
    }

    @Override
    public Snapshot capture() {
        return new Snapshot(new ArrayList<>(archives), phase, pendingModuleData,
                writeStates(baselineBytes), writeStates(appliedBytes));
    }

    @Override
    public void restore(Snapshot snapshot) {
        restorePatternWrites(snapshot);
        archives.clear();
        phase = Phase.IDLE;
        pendingModuleData = null;
        baselineBytes.clear();
        appliedBytes.clear();
        if (snapshot == null) {
            return;
        }
        archives.addAll(snapshot.archives());
        phase = archives.isEmpty() ? Phase.IDLE : snapshot.phase();
        pendingModuleData = snapshot.pendingModuleData();
        copyStates(snapshot.baselines(), baselineBytes);
        copyStates(snapshot.appliedWrites(), appliedBytes);
    }

    private void restorePatternWrites(Snapshot snapshot) {
        List<DmaWriteState> requestedImage = snapshot == null
                ? List.of() : List.copyOf(snapshot.appliedWrites());
        if (pendingRestoreBaseline != null) {
            if (dmaTarget == null || !dmaTarget.isAvailable()) {
                pendingRestoreImage = requestedImage;
                return;
            }
            // The target still contains the physical state from before the
            // first deferred restore. Publish its retained baseline and the
            // newest requested image directly; flushing pendingRestoreImage
            // here would expose a stale intermediate snapshot and duplicate
            // renderer refreshes for a same-snapshot restore.
            applyWriteStates(pendingRestoreBaseline);
            applyWriteStates(requestedImage);
            pendingRestoreBaseline = null;
            pendingRestoreImage = null;
            return;
        }
        if (dmaTarget == null) {
            // No sink has been configured, so restore is logical-only. Do not
            // create a reconciliation that headless processing can never flush.
            return;
        }
        if (!dmaTarget.isAvailable()) {
            pendingRestoreBaseline = List.copyOf(writeStates(baselineBytes));
            pendingRestoreImage = requestedImage;
            return;
        }
        applyWriteStates(writeStates(baselineBytes));
        applyWriteStates(requestedImage);
    }

    private boolean flushPendingPatternRestore() {
        if (pendingRestoreBaseline == null) return true;
        if (dmaTarget == null || !dmaTarget.isAvailable()) return false;
        applyWriteStates(pendingRestoreBaseline);
        applyWriteStates(pendingRestoreImage);
        pendingRestoreBaseline = null;
        pendingRestoreImage = null;
        return true;
    }

    private void applyWriteStates(List<DmaWriteState> writes) {
        for (DmaWriteState entry : writes)
            dmaTarget.apply(new DmaChunk(entry.destinationVramBytes(), entry.data()));
    }

    private static List<DmaWriteState> writeStates(TreeMap<Integer, Byte> source) {
        List<DmaWriteState> result = new ArrayList<>();
        if (source.isEmpty()) return result;
        int start = -1, previous = -2;
        ArrayList<Byte> bytes = new ArrayList<>();
        for (Map.Entry<Integer, Byte> entry : source.entrySet()) {
            if (entry.getKey() != previous + 1 && !bytes.isEmpty()) {
                result.add(new DmaWriteState(start, toArray(bytes)));
                bytes.clear();
            }
            if (bytes.isEmpty()) start = entry.getKey();
            bytes.add(entry.getValue());
            previous = entry.getKey();
        }
        result.add(new DmaWriteState(start, toArray(bytes)));
        return result;
    }

    private static byte[] toArray(List<Byte> source) {
        byte[] result = new byte[source.size()];
        for (int i = 0; i < result.length; i++) result[i] = source.get(i);
        return result;
    }

    private static void copyStates(List<DmaWriteState> source, TreeMap<Integer, Byte> target) {
        for (DmaWriteState state : source) {
            byte[] data = state.data();
            for (int i = 0; i < data.length; i++) target.put(state.destinationVramBytes() + i, data[i]);
        }
    }
}
