package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.internal.PlayerDplcDmaSink;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.resources.PlcParser;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Native S2 VRAM publication around the session's SAME production PLC service.
 * Bind plcLifecycle() for PlcLifecycleService and this instance for RuntimeArtCoordinator. The
 * lifecycle's one claimed VBlank drains player DMA before Nemesis writes; runtime
 * timing callbacks only forward the existing delegate, never add another service.
 * Vint_Level (s2.asm:781-808) and Vint_TitleCard (s2.asm:1046-1056) own that order.
 * The host supplies the same REV01 ROM to this owner, its PLC and zone profiles.
 * Retain this owner with its per-act PLC; replacing a module service cannot retarget
 * an existing lifecycle view. Register this composite rewind owner instead of the
 * raw PLC adapter, retaining unrelated music adapters and the delegate's adapters.
 * Restoring an act first reselects that owner and retains/readmits its bound actor
 * instances, or reconciles bindings after roster reconstruction.
 *
 * <p>The host explicitly clears VRAM at native entry, binds the actual Sonic/Tails
 * objects, and patches zone art after initial PLC drain. The ordinary player art
 * caches remain independent. No comparison rows, timers, or readiness jobs are read
 * or created. A declared Vint_CtrlDMA uses serviceDmaQueueVBlank from the host's
 * existing QueuedPatternDmaPublication hook, even when its lifecycle phase is LAG.</p>
 */
public final class Sonic2InterlaceVramService implements RuntimeArtCoordinator,
        PlayerDplcDmaSink, RewindSnapshottable<Sonic2InterlaceVramService.Snapshot> {
    public static final String REWIND_KEY = "sonic2-native-interlace-vram";
    private static final int DMA_CAPACITY = 18;
    private final Rom rom;
    private final Sonic2PlcService plc;
    private final RuntimeArtCoordinator delegate;
    private Sonic2InterlaceVram vram = new Sonic2InterlaceVram(new byte[65536]);
    private final ArrayList<DmaTransfer> pending = new ArrayList<>();
    private AbstractPlayableSprite sonic;
    private AbstractPlayableSprite tails;
    private int sonicLast, tailsLast, tailLast;
    private final PlcLifecycleService lifecycleView = new PlcLifecycleService() {
        @Override public void serviceVBlank(PlcLifecyclePhase phase) { Sonic2InterlaceVramService.this.serviceVBlank(phase); }
        @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
            return Sonic2InterlaceVramService.this.hasPreparationBoundary(phase);
        }
        @Override public boolean ownsTimedLoopTailArm() { return Sonic2InterlaceVramService.this.ownsTimedLoopTailArm(); }
        @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { Sonic2InterlaceVramService.this.prepareAfterLoop(phase); }
        @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() { return plc.captureQueueDiagnostics(); }
    };

    public Sonic2InterlaceVramService(Rom rom, Sonic2PlcService plc) {
        this(rom, plc, RuntimeArtCoordinator.NONE);
    }

    public Sonic2InterlaceVramService(Rom rom, Sonic2PlcService plc, RuntimeArtCoordinator delegate) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.plc = Objects.requireNonNull(plc, "production PLC service");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public Sonic2PlcService plcService() { return plc; }
    /** One stable lifecycle view; the session collects these PLC diagnostics separately from runtime art. */
    public PlcLifecycleService plcLifecycle() { return lifecycleView; }
    public Sonic2InterlaceVram current() { return vram; }

    /** Exact native actors; a ghost, preview or donor copy cannot submit native body DMA. */
    public void bindPlayers(AbstractPlayableSprite sonic, AbstractPlayableSprite tails) {
        if (sonic == null || tails == null || sonic == tails)
            throw new IllegalArgumentException("Native Sonic and Tails must be distinct bound playables");
        this.sonic = sonic;
        this.tails = tails;
    }

    /** Level_ClrRam (s2.asm:4806-4810) clears all three last-loaded bytes to zero. */
    public void resetForNativeEntry() {
        vram = new Sonic2InterlaceVram(new byte[65536]);
        pending.clear();
        sonicLast = tailsLast = tailLast = 0;
        // The host owns ClearPLC/LoadPLC. Never reset an already submitted production queue.
    }

    /** swap_players (s2.asm:26035-26041) forces the next native frame-zero call to reload. */
    public void invalidatePlayerDplcs() { sonicLast = tailsLast = tailLast = 0xFF; }

    /** Patch exactly LoadZoneTiles' decoded span, preserving completed high PLC uploads. */
    public void loadZoneArt(Sonic2CompetitionLevelProfile profile) throws IOException {
        byte[] bytes;
        try (var channel = RomChannel.at(rom, profile.art().patternAddress())) {
            bytes = KosinskiReader.decompress(channel, false);
        }
        vram = vram.withWrite(0, bytes);
    }

    /** A real queued ROM DMA command. The common native buffer drops commands after its 18th entry. */
    public boolean queueDmaTransfer(int sourceAddress, int destinationByte, int wordCount) {
        var transfer = new DmaTransfer(sourceAddress, destinationByte, wordCount);
        if (pending.size() == DMA_CAPACITY) return false;
        pending.add(transfer);
        return true;
    }

    /** Immutable VDP command inputs, not already-published pixels. Zero length denotes 65536 words. */
    public record DmaTransfer(int sourceAddress, int destinationByte, int wordCount) {
        public DmaTransfer {
            if (sourceAddress < 0 || sourceAddress > 0xFFFFFF || (sourceAddress & 1) != 0
                    || (destinationByte & 1) != 0 || wordCount < 0 || wordCount > 0xFFFF)
                throw new IllegalArgumentException("Invalid ROM-to-VRAM word DMA");
            destinationByte &= 0xFFFF;
        }
        int words() { return wordCount == 0 ? 65536 : wordCount; }
    }

    /** Both identities and pending physical work survive destructive restore. */
    public record Snapshot(Sonic2InterlaceVram vram, NemesisPlcQueueSnapshot plc,
                           List<DmaTransfer> pending, AbstractPlayableSprite sonic,
                           AbstractPlayableSprite tails, int sonicLast, int tailsLast, int tailLast) {
        public Snapshot {
            Objects.requireNonNull(vram, "vram"); Objects.requireNonNull(plc, "plc");
            pending = List.copyOf(pending);
            if (pending.size() > DMA_CAPACITY || !isByte(sonicLast) || !isByte(tailsLast) || !isByte(tailLast)
                    || ((sonic == null) != (tails == null)) || (sonic != null && sonic == tails))
                throw new IllegalArgumentException("Invalid native VRAM snapshot");
        }
        private static boolean isByte(int value) { return value >= 0 && value <= 255; }
    }

    @Override public void playerLoadPlc(AbstractPlayableSprite player, int mappingFrame, SpriteDplcFrame ignored) {
        submitPlayerFrame(player, mappingFrame);
        if (delegate instanceof PlayerDplcDmaSink sink) sink.playerLoadPlc(player, mappingFrame, ignored);
    }

    private void submitPlayerFrame(AbstractPlayableSprite player, int mappingFrame) {
        if (player == sonic && sonic != null) {
            sonicLast = submitFrame(mappingFrame, sonicLast, 0x714E0, 0x50000, 0xF000);
        } else if (player == tails && tails != null) {
            tailsLast = submitFrame(mappingFrame, tailsLast, 0x7446C, 0x64320, 0xF400);
        }
    }

    @Override public void tailsTailLoadPlc(int mappingFrame, SpriteDplcFrame ignored) {
        // LoadTailsTailsDynPLC (s2.asm:41637-41650): SAME table and raw art as body.
        // Directional frame banking already happened in the actual tail animation.
        if (tails != null) tailLast = submitFrame(mappingFrame, tailLast, 0x7446C, 0x64320, 0xF600);
        if (delegate instanceof PlayerDplcDmaSink sink) sink.tailsTailLoadPlc(mappingFrame, ignored);
    }

    @Override public void playerLoadPlcOwnedByObject(AbstractPlayableSprite player) {
        // The shared notice follows S3K's object-owned animation gate. S2 always
        // calls its DPLC routine after animation: Obj01_Control (s2.asm:36236-36265),
        // Obj02_Control_Part2 (s2.asm:38973-39001). Read only the actual stored frame.
        if (player != null && (player == sonic || player == tails)) submitPlayerFrame(player, player.getMappingFrame());
        if (delegate instanceof PlayerDplcDmaSink sink) sink.playerLoadPlcOwnedByObject(player);
    }

    /** LoadSonicDynPLC / SPLC_ReadEntry (s2.asm:38829-38860), TPLC_ReadEntry (41675-41689). */
    private int submitFrame(int mappingFrame, int previous, int table, int art, int destination) {
        int frame = mappingFrame & 255;
        if (frame == previous) return previous;
        RomByteReader reader = reader();
        int address = table + reader.readS16BE(table + frame * 2);
        int counter = (reader.readU16BE(address) - 1) & 65535;
        var commands = new ArrayList<DmaTransfer>();
        if ((short) counter >= 0) {
            for (int i = 0; i <= counter; i++) {
                int entry = reader.readU16BE(address + 2 + i * 2);
                int words = ((entry >>> 12) + 1) * 16;
                commands.add(new DmaTransfer(art + (entry & 0xFFF) * 32, destination, words));
                destination = (destination + words * 2) & 65535;
            }
        }
        // Parse before mutating so an unreadable ROM fails without half a submission.
        // The ROM latches BEFORE empty-count/full-buffer branches: both still return frame.
        for (var command : commands)
            queueDmaTransfer(command.sourceAddress(), command.destinationByte(), command.wordCount());
        return frame;
    }

    /** Actual claimed VInt: queued DMA first; subsequent partial Nemesis output wins overlaps. */
    public void serviceVBlank(PlcLifecyclePhase phase) {
        Objects.requireNonNull(phase, "phase");
        var before = plc.capture();
        boolean dma = servicesPlayerDma(phase);
        byte[] physical = vram.bytes();
        if (dma) applyDma(physical);
        try {
            plc.serviceVBlank(phase);
            var after = plc.capture();
            var active = before.activeEntry();
            boolean progressed = active != null && (after.activeEntry() == null
                    || active.remainingPatterns() != after.activeEntry().remainingPatterns());
            // No decode/read at a phase where the actual service consumed no work.
            applyPlcDelta(physical, active, after.activeEntry(), progressed ? decodeActive(active) : null);
        } catch (RuntimeException failure) {
            // The final production service mutates only its logical FIFO. A bad ROM
            // must leave both that FIFO and the physical transaction retryable.
            plc.restore(before);
            throw failure;
        }
        vram = new Sonic2InterlaceVram(physical);
        if (dma) pending.clear();
    }

    /** Vint_CtrlDMA (s2.asm:998-1002): explicit real DMA-only VBlank, never an inferred lag row. */
    public void serviceDmaQueueVBlank() {
        byte[] physical = vram.bytes();
        applyDma(physical);
        vram = new Sonic2InterlaceVram(physical);
        pending.clear();
    }

    private byte[] decodeActive(NemesisPlcQueueSnapshot.Entry entry) {
        if (entry == null) return null;
        try {
            byte[] bytes = PlcParser.decompressEntryRaw(rom,
                    new PlcParser.PlcEntry(entry.sourceAddress(), entry.destinationTile()));
            if (bytes.length != (long) entry.totalPatterns() * 32)
                throw new IllegalStateException("Nemesis output differs from the actual PLC descriptor");
            return bytes;
        } catch (IOException error) { throw new UncheckedIOException("Native PLC ROM decode failed", error); }
    }

    private static void applyPlcDelta(byte[] physical, NemesisPlcQueueSnapshot.Entry before,
            NemesisPlcQueueSnapshot.Entry after, byte[] decoded) {
        if (before == null) {
            if (after != null) throw new IllegalStateException("PLC service unexpectedly prepared a head");
            return;
        }
        int remaining = after == null ? 0 : after.remainingPatterns();
        if (after != null && (before.sourceAddress() != after.sourceAddress()
                || before.destinationTile() != after.destinationTile() || before.totalPatterns() != after.totalPatterns())
                || remaining < 0 || remaining > before.remainingPatterns())
            throw new IllegalStateException("PLC service changed descriptor identity");
        int first = (before.totalPatterns() - before.remainingPatterns()) * 32;
        int end = (before.totalPatterns() - remaining) * 32;
        for (int offset = first; offset < end; offset++)
            physical[(before.destinationTile() * 32 + offset) & 65535] = decoded[offset];
    }

    private void applyDma(byte[] physical) {
        if (pending.isEmpty()) return;
        RomByteReader reader = reader();
        for (var dma : pending) for (int word = 0; word < dma.words(); word++) {
            // The VDP increments the low 16 source-word bits in a fixed 128 KiB window.
            int source = (dma.sourceAddress() & ~0x1FFFF) | ((dma.sourceAddress() + word * 2) & 0x1FFFF);
            int value = reader.readU16BE(source);
            int destination = (dma.destinationByte() + word * 2) & 65535;
            physical[destination] = (byte) (value >>> 8);
            physical[(destination + 1) & 65535] = (byte) value;
        }
    }

    private RomByteReader reader() {
        try { return RomByteReader.fromRom(rom); }
        catch (IOException error) { throw new UncheckedIOException("Native DMA ROM read failed", error); }
    }

    private static boolean servicesPlayerDma(PlcLifecyclePhase phase) {
        // LevelSelect_Main (s2.asm:12467-12469) and LevelSelect2P_Main
        // (s2.asm:11827-11829) select Vint_Menu; Vint_Pause_specialStage
        // (s2.asm:818-834) omits ProcessDMAQueue entirely.
        return switch (phase) {
            case CONTINUE_SCREEN, LEVEL_SELECT, ORDINARY_LEVEL, SPECIAL_STAGE, SPECIAL_STAGE_RESULTS, TWO_PLAYER_RESULTS,
                    CREDITS_TEXT, CREDITS_DEMO, ENDING, POST_CREDITS, NORMAL_PAUSE, LEVEL_TITLE_CARD -> true;
            case TITLE_SCREEN, PALETTE_FADE, CREDITS_DEMO_FADE, LAG, SPECIAL_STAGE_PAUSE -> false;
        };
    }

    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) { return plc.hasPreparationBoundary(phase); }
    public boolean ownsTimedLoopTailArm() { return plc.ownsTimedLoopTailArm(); }
    public void prepareAfterLoop(PlcLifecyclePhase phase) { plc.prepareAfterLoop(phase); }
    @Override public String key() { return REWIND_KEY; }
    @Override public Snapshot capture() {
        return new Snapshot(vram, plc.capture(), pending, sonic, tails, sonicLast, tailsLast, tailLast);
    }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        plc.restore(snapshot.plc()); // validates the entire real FIFO before any local mutation
        vram = snapshot.vram(); pending.clear(); pending.addAll(snapshot.pending());
        sonic = snapshot.sonic(); tails = snapshot.tails();
        sonicLast = snapshot.sonicLast(); tailsLast = snapshot.tailsLast(); tailLast = snapshot.tailLast();
    }
    @Override public void resetForMissingSnapshot() {
        resetForNativeEntry();
        plc.resetForMissingSnapshot();
        delegate.resetForMissingSnapshot();
    }
    @Override public void registerRewindAdapters(RewindRegistry registry) {
        delegate.registerRewindAdapters(registry); registry.register(this);
    }
    @Override public void deregisterRewindAdapters(RewindRegistry registry) {
        registry.deregister(REWIND_KEY); delegate.deregisterRewindAdapters(registry);
    }
    @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() {
        return List.copyOf(delegate.captureQueueDiagnostics());
    }
    @Override public void beforeTimingService(HardwareServiceBoundary boundary) { delegate.beforeTimingService(boundary); }
    @Override public void afterTimingService(HardwareServiceBoundary boundary) { delegate.afterTimingService(boundary); }
    @Override public void deferProductionSubmissionForHeldLoopTail() { delegate.deferProductionSubmissionForHeldLoopTail(); }
    @Override public void deferProductionFirstChildForLateProducer() { delegate.deferProductionFirstChildForLateProducer(); }
    @Override public void deferProductionSubmissionForHeldLoopTailClosure() { delegate.deferProductionSubmissionForHeldLoopTailClosure(); }
    @Override public void finishHeldLoopTailClosure() { delegate.finishHeldLoopTailClosure(); }
    @Override public boolean ownsHeldLevelCounterHardwareTail() { return delegate.ownsHeldLevelCounterHardwareTail(); }
    @Override public void runHeldIterationLoopTail() { delegate.runHeldIterationLoopTail(); }
}
