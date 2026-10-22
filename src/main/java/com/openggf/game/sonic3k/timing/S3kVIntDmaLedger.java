package com.openggf.game.sonic3k.timing;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Target;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Transfer;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Per-V-int record of the 68k-bus DMA work the engine's own S3K owners submit: the fixed VInt_8 transfers plus
 * every {@code Add_To_DMA_Queue} entry {@code Process_DMA_Queue} drains, with its GPGX bus freeze.
 *
 * <p>Producers, in LevelLoop order (sonic3k.asm {@code LevelLoop}: Process_Sprites, then Animate_Tiles, then
 * Process_Kos_Module_Queue): object DPLC loads submitted here as they happen (the players' {@code *_Load_PLC} and
 * {@code Tails_Tail_Load_PLC});
 * Animate_Tiles uploads and KosM module completions, read from their own pending queues when the V-int drains them.
 * Shipped {@code Add_To_DMA_Queue} (Sonic3_Complete = 0) makes one VRAM entry per call and returns without queueing
 * once the 18-entry {@code DMA_queue} is full; entries are capped in that phase order. The drained order equals the
 * ROM's only when one LevelLoop iteration's submissions reach a drain; when several iterations share one (a V-int
 * that did not drain), the ROM interleaves them per iteration and this record does not.
 *
 * <p>This is a diagnostic budget term, never an activation input: it does not decide readiness, rows, profiles or
 * fingerprints, and it covers only the producers above. Other {@code Add_To_DMA_Queue} callers are not recorded:
 * object {@code Perform_DPLC} users, the 38 {@code Perform_Player_DPLC} gameplay sites and direct object calls of a
 * player's {@code *_Load_PLC} (the AIZ intro plane, the ending objects), and zone tile routines that bypass the AniPLC
 * queue. Recorded body entries are exact only while no such owner wrote the previous-frame byte; an object-owned
 * interval makes the byte unknown and the next drains report {@link FreezeStatus#PRODUCER_STATE_UNKNOWN}. A record is
 * never a complete statement of the V-int's DMA. Its freeze is a conditional subtotal of the recorded transfers: it equals the GPGX freeze only if every
 * transfer starts and ends strictly inside VBLANK, and the start depends on V-int handler instruction time this
 * ledger does not have. {@link FreezeStatus} says which statement a record can make.
 */
public final class S3kVIntDmaLedger implements RewindSnapshottable<S3kVIntDmaLedger.State> {
    public static final String REWIND_KEY = "s3k-vint-dma-ledger";
    /** {@code DMA_queue: ds.w $12*7}: eighteen seven-word entries. */
    public static final int DMA_QUEUE_ENTRIES = 0x12;
    /** {@code ArtTile_Player_2_Tail}: {@code Tails_Tail_Load_PLC} loads at {@code tiles_to_bytes} of it, VRAM $D600. */
    public static final int TAILS_TAIL_VRAM = 0xD600;

    /** The body DPLC routine's previous-frame byte and VRAM base. */
    public enum BodySlot {
        /** {@code Sonic_Load_PLC} / {@code Knuckles_Load_PLC}: {@code Player_prev_frame}, ArtTile_Player_1 ($680). */
        SONIC_KNUCKLES(0xD000),
        /** {@code Tails_Load_PLC}: {@code Player_prev_frame_P2}, ArtTile_Player_2 ($6A0). */
        TAILS(0xD400);

        private final int vram;

        BodySlot(int vram) {
            this.vram = vram;
        }

        public int vram() {
            return vram;
        }
    }

    /** The console region whose VBLANK the transfers must fit in; UNKNOWN states no total. */
    public enum VideoRegion {
        NTSC, PAL, UNKNOWN;

        long vblankMclk() {
            return this == UNKNOWN ? -1 : S3kVIntDma.vblankMclk(this == PAL);
        }
    }

    public enum Producer { OBJECT_DPLC, ANIMATE_TILES, KOS_MODULE }

    /** One {@code Add_To_DMA_Queue} entry: always a 68k-to-VRAM transfer. */
    public record QueuedEntry(Producer producer, int words, int vramAddress) {
        public QueuedEntry {
            Objects.requireNonNull(producer, "producer");
            if (words <= 0 || words > 0xFFFF) {
                throw new IllegalArgumentException("queue entry word count out of range: " + words);
            }
        }

        Transfer transfer() {
            return new Transfer(words, Target.VRAM, vramAddress & 0xFFFF);
        }
    }

    /** Which fixed transfers the draining V-int routine performs before {@code Process_DMA_Queue}. */
    public enum FixedTransfers {
        /** VInt_8 (ordinary LevelLoop, credits demo, and pause through VInt_10). */
        VINT_8,
        /** A routine this ledger does not model, or a VInt_8 whose Hyper Sonic flash state is live. */
        UNMODELLED
    }

    /** What a record's freeze figure means. */
    public enum FreezeStatus {
        /**
         * The fixed transfers plus the recorded queue sum to less than the region's VBLANK. The figure is the GPGX
         * freeze only if the V-int reaches each transfer early enough for it to end strictly inside VBLANK; that
         * start time is not modelled yet. A recorded-producer subtotal, never a measured or complete V-int freeze.
         */
        CONDITIONAL_ON_VINT_START,
        /** The sum alone reaches the end of VBLANK, so some transfer continues into active display: no figure. */
        EXCEEDS_VBLANK,
        /** The configured region is neither NTSC nor PAL: no figure. */
        UNKNOWN_REGION,
        /** The draining routine, or a live Hyper flash, is not modelled: no figure for the V-int. */
        FIXED_UNMODELLED,
        /**
         * Since the previous drain an object owned a player's frame (and may have run that player's {@code *_Load_PLC}
         * or written its previous-frame byte), or a non-native producer was refused: the recorded body entries may be
         * missing or spurious, so no figure.
         */
        PRODUCER_STATE_UNKNOWN,
        /** The session ROM does not carry the pinned VInt_8 instructions: nothing is priced. */
        ROM_UNSUPPORTED
    }

    /**
     * One draining V-int. {@code conditionalFreezeMclk} is the per-transfer GPGX floor sum of the fixed transfers and
     * the recorded queue, and {@code queueFreezeMclk} the same sum over the drained entries alone; both are -1 unless
     * {@code freezeStatus} is {@link FreezeStatus#CONDITIONAL_ON_VINT_START}.
     */
    public record Record(long ordinal, PlcLifecyclePhase phase, VideoRegion region, FixedTransfers fixed,
                         List<Transfer> fixedTransfers, List<QueuedEntry> drained, int droppedEntries,
                         long queueFreezeMclk, FreezeStatus freezeStatus, long conditionalFreezeMclk,
                         S3kVIntInstructionCycles.Term instructions) {
        public Record {
            Objects.requireNonNull(fixed, "fixed");
            Objects.requireNonNull(instructions, "instructions");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(freezeStatus, "freezeStatus");
            fixedTransfers = List.copyOf(fixedTransfers);
            drained = List.copyOf(drained);
        }
    }

    /**
     * Rewind state: undrained object entries and the submissions the full queue refused since the last drain, the
     * {@code Player_prev_frame_P2_tail} byte and the last record.
     */
    public record State(List<QueuedEntry> objectPending, int refusedSincePreviousDrain, int tailPreviousFrame,
                        int player1PreviousFrame, int player2PreviousFrame, boolean player1Known,
                        boolean player2Known, boolean producerStateUnknownSinceDrain, int demoTimer, long ordinal,
                        Record last) {
        public State {
            objectPending = List.copyOf(objectPending);
        }
    }

    private final List<QueuedEntry> objectPending = new ArrayList<>();
    /** Add_To_DMA_Queue calls that found the queue full: {@code cmpa.w #DMA_queue_slot,a1 / beq.s ..._Done}. */
    private int refusedSincePreviousDrain;
    /** {@code Player_prev_frame_P2_tail}; a fresh Level clears it to 0. */
    private int tailPreviousFrame;
    /** {@code Player_prev_frame} and {@code Player_prev_frame_P2}; a fresh Level clears them to 0. */
    private int player1PreviousFrame;
    private int player2PreviousFrame;
    /** False after an unobserved interval in which another owner may have written the byte. */
    private boolean player1Known = true;
    private boolean player2Known = true;
    private boolean producerStateUnknownSinceDrain;
    /** {@code Demo_timer}: Level's loc_6468 sets 1800, Do_Updates counts it down once per VInt_8; -1 unknown. */
    private int demoTimer = -1;
    private boolean vintRomChecked;
    private boolean vintRomMatches;
    private long ordinal;
    private Record last;
    private S3kVIntDma.VInt8Transfers vint8;
    private boolean romChecked;

    /** Decoded once from the session ROM; a constant of the ROM, not rewind state. */
    public void bindRom(S3kVIntDma.VInt8Transfers transfers) {
        bindRom(java.util.Optional.of(transfers));
    }

    /** Empty when the session ROM lacks the pinned VInt_8 instructions: records then price nothing. */
    public void bindRom(java.util.Optional<S3kVIntDma.VInt8Transfers> transfers) {
        this.vint8 = transfers.orElse(null);
        this.romChecked = true;
    }

    public boolean romBound() {
        return romChecked;
    }

    /** Whether the session ROM carries the V-int closed routines the instruction figure is pinned to. */
    public void bindVIntRom(boolean matches) {
        vintRomMatches = matches;
        vintRomChecked = true;
    }

    public boolean vintRomBound() {
        return vintRomChecked;
    }

    /**
     * Engine-owned inputs of the V-int instruction figure. {@code kosQueueEmpty}: the physical Kos queue is empty, so
     * {@code Kos_decomp_queue_count} is 0 and Set_Kos_Bookmark's {@code bpl} returns at once (a non-empty queue leaves
     * the busy bit and stacked PC unknown). {@code levelHasWater}: {@code Water_flag}; null when no water owner answers.
     */
    public record InstructionInputs(boolean kosQueueEmpty, Boolean levelHasWater) {
    }

    /**
     * {@code Tails_Tail_Load_PLC}: compare {@code mapping_frame} with {@code Player_prev_frame_P2_tail}, store it,
     * then queue one entry per DPLC request ({@code (count) * $10} words, consecutive from $D600).
     */
    public void observeTailsTailFrame(int mappingFrame, SpriteDplcFrame dplc) {
        int frame = mappingFrame & 0xFF;
        if (frame == tailPreviousFrame) {
            return;
        }
        tailPreviousFrame = frame;
        queueDplc(dplc, TAILS_TAIL_VRAM);
    }

    /**
     * {@code Sonic_Load_PLC} / {@code Knuckles_Load_PLC} / {@code Tails_Load_PLC}: the same compare, store and
     * per-piece {@code Add_To_DMA_Queue} as the tail, against the slot's previous-frame byte and VRAM base.
     */
    public void observePlayerFrame(BodySlot slot, int mappingFrame, SpriteDplcFrame dplc) {
        Objects.requireNonNull(slot, "slot");
        int frame = mappingFrame & 0xFF;
        boolean tails = slot == BodySlot.TAILS;
        if (!(tails ? player2Known : player1Known)) {
            // The byte's value is unknown, so whether the ROM queues now is unknown too. After this call it equals
            // the frame either way, which re-establishes a known byte for later compares.
            setPrevious(tails, frame);
            producerStateUnknownSinceDrain = true;
            return;
        }
        if (frame == (tails ? player2PreviousFrame : player1PreviousFrame)) {
            return;
        }
        setPrevious(tails, frame);
        queueDplc(dplc, slot.vram());
    }

    private void setPrevious(boolean tails, int frame) {
        if (tails) {
            player2PreviousFrame = frame;
            player2Known = true;
        } else {
            player1PreviousFrame = frame;
            player1Known = true;
        }
    }

    /**
     * The player's own routine did not run its {@code *_Load_PLC} this frame because an object owns the mapping frame
     * ({@code object_control} bit 1). Owners such as the 38 {@code Perform_Player_DPLC} gameplay sites, the AIZ intro
     * plane ($135534, after {@code move.b #-1,(Player_prev_frame).w}) and the ending objects queue and store through the
     * same byte without being recorded here, or the producer is not a native one; the byte becomes unknown.
     */
    public void markPlayerFrameUnobserved(BodySlot slot) {
        Objects.requireNonNull(slot, "slot");
        if (slot == BodySlot.TAILS) {
            player2Known = false;
        } else {
            player1Known = false;
        }
        producerStateUnknownSinceDrain = true;
    }

    /**
     * A native {@code move.b #-1,(Player_prev_frame).w}: {@code SonicKnux_SuperHyper .revertToNormal}
     * (sonic3k.asm line 23695 at the 044fa46 pin) or giant-ring capture {@code loc_6173A}. The next
     * {@code Sonic_Load_PLC}/{@code Knuckles_Load_PLC} reloads even for an unchanged frame. Tails' revert writes no
     * previous-frame byte; the ring writes this global byte regardless of the touched character.
     */
    public void forcePlayerOneReload() {
        player1PreviousFrame = 0xFF;
        player1Known = true;
    }

    private void queueDplc(SpriteDplcFrame dplc, int base) {
        if (dplc == null) {
            return;
        }
        int destination = base;
        for (TileLoadRequest request : dplc.requests()) {
            int words = request.count() * 0x10;
            if (objectPending.size() < DMA_QUEUE_ENTRIES) {
                objectPending.add(new QueuedEntry(Producer.OBJECT_DPLC, words, destination));
            } else {
                refusedSincePreviousDrain++;
            }
            destination += words * 2;
        }
    }

    /**
     * The draining V-int: fixed transfers for {@code phase}, then the queue in LevelLoop phase order, capped at
     * eighteen entries.
     */
    public Record serviceVInt(PlcLifecyclePhase phase, VideoRegion region, boolean hyperFlashStateLive,
                              List<QueuedEntry> animateTilesPending, List<QueuedEntry> kosModulePending) {
        return serviceVInt(phase, region, hyperFlashStateLive, animateTilesPending, kosModulePending, null);
    }

    /** As above, with the V-int instruction figure's engine-owned inputs; null refuses that figure. */
    public Record serviceVInt(PlcLifecyclePhase phase, VideoRegion region, boolean hyperFlashStateLive,
                              List<QueuedEntry> animateTilesPending, List<QueuedEntry> kosModulePending,
                              InstructionInputs instructionInputs) {
        Objects.requireNonNull(region, "region");
        List<QueuedEntry> queued = new ArrayList<>(objectPending);
        queued.addAll(animateTilesPending);
        queued.addAll(kosModulePending);
        objectPending.clear();
        int refused = refusedSincePreviousDrain;
        refusedSincePreviousDrain = 0;
        int kept = Math.min(queued.size(), DMA_QUEUE_ENTRIES);
        List<QueuedEntry> drained = List.copyOf(queued.subList(0, kept));
        // The VDP width comes from the ROM's Level register write; without it nothing is priced.
        long queueFreeze = -1;
        if (vint8 != null) {
            queueFreeze = 0;
            for (QueuedEntry entry : drained) {
                queueFreeze += S3kVIntDma.blankFreeze(entry.transfer(), vint8.h40());
            }
        }
        FixedTransfers fixed = vint8 != null && !hyperFlashStateLive && runsVInt8(phase)
                ? FixedTransfers.VINT_8 : FixedTransfers.UNMODELLED;
        List<Transfer> fixedTransfers = fixed == FixedTransfers.VINT_8 ? vint8.inOrder(false) : List.of();
        FreezeStatus status;
        long freeze = -1;
        boolean producerStateUnknown = producerStateUnknownSinceDrain;
        producerStateUnknownSinceDrain = false;
        if (vint8 == null) {
            status = FreezeStatus.ROM_UNSUPPORTED;
        } else if (fixed != FixedTransfers.VINT_8) {
            status = FreezeStatus.FIXED_UNMODELLED;
        } else if (producerStateUnknown) {
            status = FreezeStatus.PRODUCER_STATE_UNKNOWN;
        } else if (region == VideoRegion.UNKNOWN) {
            status = FreezeStatus.UNKNOWN_REGION;
        } else {
            long sum = S3kVIntDma.blankFreeze(fixedTransfers, vint8.h40()) + queueFreeze;
            // Necessary, not sufficient: even a sum inside VBLANK needs every transfer to start early enough.
            if (sum >= region.vblankMclk()) {
                status = FreezeStatus.EXCEEDS_VBLANK;
            } else {
                status = FreezeStatus.CONDITIONAL_ON_VINT_START;
                freeze = sum;
            }
        }
        if (status != FreezeStatus.CONDITIONAL_ON_VINT_START) {
            queueFreeze = -1;
        }
        S3kVIntInstructionCycles.Term instructions = instructionTerm(phase, region, hyperFlashStateLive,
                producerStateUnknown, drained.size(), instructionInputs);
        if (runsVInt8(phase) && demoTimer > 0) {
            demoTimer--;   // Do_Updates (in the V-int, or the H-int when deferred): subq.w #1,(Demo_timer).w
        }
        last = new Record(++ordinal, phase, region, fixed, fixedTransfers, drained, refused + queued.size() - kept,
                queueFreeze, status, freeze, instructions);
        return last;
    }

    private S3kVIntInstructionCycles.Term instructionTerm(PlcLifecyclePhase phase, VideoRegion region,
                                                         boolean hyperFlash, boolean producerStateUnknown,
                                                         int drainedEntries, InstructionInputs inputs) {
        var missing = java.util.EnumSet.noneOf(S3kVIntInstructionCycles.MissingInput.class);
        if (!vintRomChecked || !vintRomMatches || vint8 == null) {
            missing.add(S3kVIntInstructionCycles.MissingInput.ROM_UNSUPPORTED);
        }
        // NORMAL_PAUSE reaches VInt_8 through VInt_10's own test, which the closed form does not include.
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL && phase != PlcLifecyclePhase.CREDITS_DEMO) {
            missing.add(S3kVIntInstructionCycles.MissingInput.ROUTINE_NOT_CLOSED);
        }
        if (region == VideoRegion.UNKNOWN) {
            missing.add(S3kVIntInstructionCycles.MissingInput.REGION_UNKNOWN);
        }
        if (producerStateUnknown) {
            missing.add(S3kVIntInstructionCycles.MissingInput.DMA_PRODUCER_STATE_UNKNOWN);
        }
        if (hyperFlash) {
            missing.add(S3kVIntInstructionCycles.MissingInput.HYPER_FLASH_LIVE);
        }
        if (demoTimer < 0) {
            missing.add(S3kVIntInstructionCycles.MissingInput.DEMO_TIMER_UNKNOWN);
        }
        if (inputs == null) {
            missing.add(S3kVIntInstructionCycles.MissingInput.INPUTS_NOT_SUPPLIED);
        } else {
            if (!inputs.kosQueueEmpty()) {
                missing.add(S3kVIntInstructionCycles.MissingInput.KOS_BUSY_PC_UNKNOWN);
            }
            // Water_flag set: the line test reads H_int_counter, which no engine owner exposes yet.
            if (inputs.levelHasWater() == null || inputs.levelHasWater()) {
                missing.add(S3kVIntInstructionCycles.MissingInput.WATER_LINE_UNOWNED);
            }
        }
        if (!missing.isEmpty()) {
            return S3kVIntInstructionCycles.Term.refused(missing);
        }
        int cycles = S3kVIntInstructionCycles.cycles(new S3kVIntInstructionCycles.Input(region == VideoRegion.PAL,
                drainedEntries, false, 0, false, false, false, 0xFF, demoTimer));
        return new S3kVIntInstructionCycles.Term(cycles, java.util.Set.of(),
                java.util.EnumSet.allOf(S3kVIntInstructionCycles.UnpricedCallee.class));
    }

    /** The routine LevelLoop and its pause arm: VInt_ID_8 directly, or VInt_10 falling into VInt_8 outside SS. */
    static boolean runsVInt8(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.ORDINARY_LEVEL || phase == PlcLifecyclePhase.CREDITS_DEMO
                || phase == PlcLifecyclePhase.NORMAL_PAUSE;
    }

    public Record lastRecord() {
        return last;
    }

    public List<QueuedEntry> pendingObjectEntries() {
        return List.copyOf(objectPending);
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public State capture() {
        return new State(objectPending, refusedSincePreviousDrain, tailPreviousFrame, player1PreviousFrame,
                player2PreviousFrame, player1Known, player2Known, producerStateUnknownSinceDrain, demoTimer, ordinal,
                last);
    }

    @Override
    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        objectPending.clear();
        objectPending.addAll(state.objectPending());
        refusedSincePreviousDrain = state.refusedSincePreviousDrain();
        tailPreviousFrame = state.tailPreviousFrame();
        player1PreviousFrame = state.player1PreviousFrame();
        player2PreviousFrame = state.player2PreviousFrame();
        player1Known = state.player1Known();
        player2Known = state.player2Known();
        producerStateUnknownSinceDrain = state.producerStateUnknownSinceDrain();
        demoTimer = state.demoTimer();
        ordinal = state.ordinal();
        last = state.last();
    }

    @Override
    public void resetForMissingSnapshot() {
        clear();
    }

    /**
     * A fresh {@code Level} entry: loc_60DE's {@code clearRAM Tails_CPU_interact,$100} at ROM $610E zeroes $F700-$F7FF,
     * which holds {@code Player_prev_frame} ($F766), {@code Player_prev_frame_P2} ($F7DE) and
     * {@code Player_prev_frame_P2_tail} ($F7DF); loc_61BE then empties the queue
     * ({@code clr.w (DMA_queue).w / move.l #DMA_queue,(DMA_queue_slot).w} at $61C6).
     */
    public void resetAtFreshLevel() {
        objectPending.clear();
        refusedSincePreviousDrain = 0;
        tailPreviousFrame = 0;
        player1PreviousFrame = 0;
        player2PreviousFrame = 0;
        player1Known = true;
        player2Known = true;
        producerStateUnknownSinceDrain = false;
        // loc_6468: move.w #1800,(Demo_timer).w, after the setup objects and before LevelLoop; only VInt_C
        // title-card drains, which do not touch Demo_timer, run in between.
        demoTimer = 1800;
    }

    /** Missing-snapshot reset: no queue, no record. */
    public void clear() {
        objectPending.clear();
        refusedSincePreviousDrain = 0;
        tailPreviousFrame = 0;
        player1PreviousFrame = 0;
        player2PreviousFrame = 0;
        player1Known = true;
        player2Known = true;
        producerStateUnknownSinceDrain = false;
        demoTimer = -1;
        ordinal = 0;
        last = null;
    }
}
