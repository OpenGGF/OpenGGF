package com.openggf.game.sonic2.competition.view;

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

/** Semantic native pattern residency around the world's production PLC service.
 * Queued pattern uploads precede partial Nemesis output at a servicing VInt.
 * No plane, sprite table, hardware address space or port protocol is retained.
 */
public final class Sonic2CompetitionArtResidency implements RuntimeArtCoordinator,
        PlayerDplcDmaSink, RewindSnapshottable<Sonic2CompetitionArtResidency.Snapshot> {
    public static final String REWIND_KEY = "sonic2.competition.art-residency";
    private static final int DMA_CAPACITY = 18;
    private final Rom rom;
    private final Sonic2PlcService plc;
    private final RuntimeArtCoordinator delegate;
    private Slot[] slots = emptySlots();
    private final ArrayList<Upload> pending = new ArrayList<>();
    private Sonic2CompetitionFieldPatterns fields;
    public Sonic2CompetitionFieldPatterns fields() {
        if (fields == null) fields = new Sonic2CompetitionFieldPatterns(this);
        return fields;
    }
    private AbstractPlayableSprite sonic;
    private AbstractPlayableSprite tails;
    private int sonicLast, tailsLast, tailLast;
    private final PlcLifecycleService lifecycleView = new PlcLifecycleService() {
        @Override public void serviceVBlank(PlcLifecyclePhase phase) { Sonic2CompetitionArtResidency.this.serviceVBlank(phase); }
        @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
            return Sonic2CompetitionArtResidency.this.hasPreparationBoundary(phase);
        }
        @Override public boolean ownsTimedLoopTailArm() { return Sonic2CompetitionArtResidency.this.ownsTimedLoopTailArm(); }
        @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { Sonic2CompetitionArtResidency.this.prepareAfterLoop(phase); }
        @Override public List<QueueDiagnosticSnapshot> captureQueueDiagnostics() { return plc.captureQueueDiagnostics(); }
    };

    public Sonic2CompetitionArtResidency(Rom rom, Sonic2PlcService plc) {
        this(rom, plc, RuntimeArtCoordinator.NONE);
    }

    public Sonic2CompetitionArtResidency(Rom rom, Sonic2PlcService plc, RuntimeArtCoordinator delegate) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.plc = Objects.requireNonNull(plc, "production PLC service");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public boolean ownsPlayer(AbstractPlayableSprite player) {
        return player != null && (player == sonic || player == tails);
    }

    public Sonic2PlcService plcService() { return plc; }
    /** One stable lifecycle view; the session collects these PLC diagnostics separately from runtime art. */
    public PlcLifecycleService plcLifecycle() { return lifecycleView; }
    private static Slot[] emptySlots() {
        var result = new Slot[2048];
        java.util.Arrays.fill(result, new Slot(new byte[32]));
        return result;
    }
    byte[] slot(int index) { return slots[Objects.checkIndex(index, 2048)].bytes; }
    public byte[] patternBytes(int index) { return slot(index).clone(); }

    /** Atomic copy-on-write pattern publication; input is decoded native 32-byte patterns. */
    public void writePatterns(int firstSlot, byte[] bytes) {
        slots = written(slots, firstSlot, bytes);
    }
    private static Slot[] written(Slot[] previous, int firstSlot, byte[] bytes) {
        if (bytes.length % 32 != 0) throw new IllegalArgumentException("Whole native patterns required");
        Objects.checkFromIndexSize(firstSlot, bytes.length / 32, 2048);
        var next = previous.clone();
        for (int i = 0; i < bytes.length / 32; i++)
            next[firstSlot + i] = new Slot(java.util.Arrays.copyOfRange(bytes, i * 32, (i + 1) * 32));
        return next;
    }

    /** Exact native actors; a ghost, preview or donor copy cannot submit native body DMA. */
    public void bindPlayers(AbstractPlayableSprite sonic, AbstractPlayableSprite tails) {
        if (sonic == null || tails == null || sonic == tails)
            throw new IllegalArgumentException("Native Sonic and Tails must be distinct bound playables");
        this.sonic = sonic;
        this.tails = tails;
    }

    /** Level_ClrRam (s2.asm:4806-4810) clears all three last-loaded bytes to zero. */
    public void resetForNativeEntry() {
        slots = emptySlots();
        pending.clear();
        sonicLast = tailsLast = tailLast = 0;
        // The host owns ClearPLC/LoadPLC. Never reset an already submitted production queue.
    }

    /** swap_players (s2.asm:26035-26041) forces the next native frame-zero call to reload. */
    public void invalidatePlayerDplcs() { sonicLast = tailsLast = tailLast = 0xFF; }

    /**
     * TitleScreen loads Std1 before entering the 2P menu (s2.asm:4418-4420).
     * Its completed patterns survive ClearScreen and Level's ClearPLC. A fresh
     * native world has no preceding title renderer, so seed that inherited art
     * directly from the same ROM descriptors, without submitting extra PLC work.
     */
    public void loadInheritedStandardArt() throws IOException {
        var definition = PlcParser.parse(rom,
                com.openggf.game.sonic2.Sonic2RomAddresses.address(rom,
                        com.openggf.game.sonic2.constants.Sonic2Constants.ART_LOAD_CUES_ADDR),
                com.openggf.game.sonic2.constants.Sonic2Constants.PLC_STD1);
        for (var entry : definition.entries())
            writePatterns(entry.tileIndex(), PlcParser.decompressEntryRaw(rom, entry));
    }

    /** Patch exactly LoadZoneTiles' decoded span, preserving completed high PLC uploads. */
    public void loadZoneArt(Sonic2CompetitionLevelProfile profile) throws IOException {
        byte[] bytes;
        try (var channel = RomChannel.at(rom, profile.art().patternAddress())) {
            bytes = KosinskiReader.decompress(channel, false);
        }
        writePatterns(0, bytes);
    }

    /** The native 18-entry command-buffer drop rule is game state, not a DMA model. */
    public boolean queueUpload(int firstSlot, byte[] patterns) {
        var upload = new Upload(firstSlot, patterns);
        if (pending.size() == DMA_CAPACITY) return false;
        pending.add(upload);
        return true;
    }
    private Upload romUpload(int source, int firstSlot, int count) {
        byte[] bytes = new byte[Math.multiplyExact(count, 32)];
        var reader = reader();
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) reader.readU8(source + i);
        return new Upload(firstSlot, bytes);
    }
    public record Upload(int firstSlot, byte[] patterns) {
        public Upload {
            if (patterns.length % 32 != 0) throw new IllegalArgumentException("Whole native patterns required");
            Objects.checkFromIndexSize(firstSlot, patterns.length / 32, 2048);
            patterns = patterns.clone();
        }
        @Override public byte[] patterns() { return patterns.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Upload upload && firstSlot == upload.firstSlot
                    && java.util.Arrays.equals(patterns, upload.patterns);
        }
        @Override public int hashCode() { return 31 * firstSlot + java.util.Arrays.hashCode(patterns); }
    }
    /** Immutable native pattern content; snapshots share slots safely. */
    public static final class Slot {
        private final byte[] bytes;
        private Slot(byte[] owned) { bytes = owned; }
        public byte[] bytes() { return bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Slot slot && java.util.Arrays.equals(bytes, slot.bytes);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(bytes); }
    }
    public record Snapshot(Slot[] slots, NemesisPlcQueueSnapshot plc, List<Upload> pending,
                           AbstractPlayableSprite sonic, AbstractPlayableSprite tails,
                           int sonicLast, int tailsLast, int tailLast) {
        public Snapshot {
            if (slots.length != 2048) throw new IllegalArgumentException("Pattern slot count");
            slots = slots.clone();
            for (var slot : slots) Objects.requireNonNull(slot, "pattern slot");
            pending = List.copyOf(pending);
            Objects.requireNonNull(plc);
            if (pending.size() > DMA_CAPACITY || sonicLast < 0 || sonicLast > 255
                    || tailsLast < 0 || tailsLast > 255 || tailLast < 0 || tailLast > 255
                    || ((sonic == null) != (tails == null)) || (sonic != null && sonic == tails))
                throw new IllegalArgumentException("Invalid pattern residency snapshot");
        }
        @Override public Slot[] slots() { return slots.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Snapshot state && java.util.Arrays.equals(slots, state.slots)
                    && plc.equals(state.plc) && pending.equals(state.pending)
                    && sonic == state.sonic && tails == state.tails && sonicLast == state.sonicLast
                    && tailsLast == state.tailsLast && tailLast == state.tailLast;
        }
        @Override public int hashCode() {
            return 31 * java.util.Arrays.hashCode(slots)
                    + Objects.hash(plc, pending, sonic, tails, sonicLast, tailsLast, tailLast);
        }
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
        var commands = new ArrayList<Upload>();
        if ((short) counter >= 0) {
            for (int i = 0; i <= counter; i++) {
                int entry = reader.readU16BE(address + 2 + i * 2);
                int count = (entry >>> 12) + 1;
                commands.add(romUpload(art + (entry & 0xFFF) * 32, destination / 32, count));
                destination += count * 32;
            }
        }
        // Parse before mutating so an unreadable ROM fails without half a submission.
        // The ROM latches BEFORE empty-count/full-buffer branches: both still return frame.
        for (var command : commands)
            queueUpload(command.firstSlot(), command.patterns());
        return frame;
    }

    private Sonic2CompetitionVideoFrame videoFrame;
    public void bindVideoFrame(Sonic2CompetitionVideoFrame frame) { videoFrame = Objects.requireNonNull(frame); }

    public void serviceVBlank(PlcLifecyclePhase phase) {
        Objects.requireNonNull(phase);
        var beforeSlots = slots;
        var beforePending = List.copyOf(pending);
        try {
            if (videoFrame != null && Sonic2CompetitionVideoFrame.accepts(phase)) {
                videoFrame.serviceVBlank(phase);
                return;
            }
            if (servicesPlayerDma(phase)) serviceDmaQueueVBlank();
            servicePlc(phase);
        } catch (RuntimeException failure) {
            slots = beforeSlots;
            pending.clear(); pending.addAll(beforePending);
            throw failure;
        }
    }
    public void serviceDmaQueueVBlank() {
        Slot[] next = slots;
        for (var upload : pending) next = written(next, upload.firstSlot(), upload.patterns());
        slots = next;
        pending.clear();
    }
    public void servicePlc(PlcLifecyclePhase phase) {
        var before = plc.capture();
        try {
            plc.serviceVBlank(phase);
            var after = plc.capture();
            var active = before.activeEntry();
            boolean progressed = active != null && (after.activeEntry() == null
                    || active.remainingPatterns() != after.activeEntry().remainingPatterns());
            if (progressed) {
                var end = after.activeEntry();
                int remaining = end == null ? 0 : end.remainingPatterns();
                if (end != null && (active.sourceAddress() != end.sourceAddress()
                        || active.destinationTile() != end.destinationTile()
                        || active.totalPatterns() != end.totalPatterns()))
                    throw new IllegalStateException("PLC descriptor changed during service");
                int first = active.totalPatterns() - active.remainingPatterns();
                int last = active.totalPatterns() - remaining;
                byte[] decoded = decodeActive(active);
                writePatterns(active.destinationTile() + first,
                        java.util.Arrays.copyOfRange(decoded, first * 32, last * 32));
            }
        } catch (RuntimeException failure) {
            plc.restore(before);
            throw failure;
        }
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
            case TITLE_SCREEN, LEVEL_ENTRY_WAIT, PALETTE_FADE, CREDITS_DEMO_FADE, LAG, SPECIAL_STAGE_PAUSE -> false;
        };
    }

    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) { return plc.hasPreparationBoundary(phase); }
    public boolean ownsTimedLoopTailArm() { return plc.ownsTimedLoopTailArm(); }
    public void prepareAfterLoop(PlcLifecyclePhase phase) { plc.prepareAfterLoop(phase); }
    @Override public String key() { return REWIND_KEY; }
    @Override public Snapshot capture() {
        return new Snapshot(slots, plc.capture(), pending, sonic, tails, sonicLast, tailsLast, tailLast);
    }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        plc.restore(snapshot.plc()); // validates the entire real FIFO before any local mutation
        slots = snapshot.slots();
        if (fields != null) fields.rebuild();
        pending.clear(); pending.addAll(snapshot.pending());
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
