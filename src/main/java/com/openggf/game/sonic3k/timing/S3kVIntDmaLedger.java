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
 * Process_Kos_Module_Queue): object DPLC loads submitted here as they happen ({@code Tails_Tail_Load_PLC});
 * Animate_Tiles uploads and KosM module completions, read from their own pending queues when the V-int drains them.
 * Shipped {@code Add_To_DMA_Queue} (Sonic3_Complete = 0) makes one VRAM entry per call and returns without queueing
 * once the 18-entry {@code DMA_queue} is full; entries are capped in that phase order. That order is exact unless the
 * queue overflows across a V-int that did not drain it.
 *
 * <p>This is a diagnostic budget term, never an activation input: it does not decide readiness, rows, profiles or
 * fingerprints, and it covers only the producers above. Other {@code Add_To_DMA_Queue} callers (player and object
 * DPLCs other than the Tails tail, zone tile routines that bypass the AniPLC queue) are not yet recorded, so a record
 * is never a complete statement of the V-int's DMA. The freeze is exact only when each transfer ends strictly inside
 * VBLANK, which depends on V-int handler instruction time this ledger does not have.
 */
public final class S3kVIntDmaLedger implements RewindSnapshottable<S3kVIntDmaLedger.State> {
    public static final String REWIND_KEY = "s3k-vint-dma-ledger";
    /** {@code DMA_queue: ds.w $12*7}: eighteen seven-word entries. */
    public static final int DMA_QUEUE_ENTRIES = 0x12;
    /** {@code ArtTile_Player_2_Tail}: {@code Tails_Tail_Load_PLC} loads at {@code tiles_to_bytes} of it, VRAM $D600. */
    public static final int TAILS_TAIL_VRAM = 0xD600;

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

    /**
     * One draining V-int. {@code freezeMclk} sums the per-transfer GPGX freezes and is -1 when the fixed transfers
     * are unmodelled; {@code queueFreezeMclk} covers the drained entries alone and is -1 before the ROM is bound.
     */
    public record Record(long ordinal, PlcLifecyclePhase phase, FixedTransfers fixed, List<Transfer> fixedTransfers,
                         List<QueuedEntry> drained, int droppedEntries, long queueFreezeMclk, long freezeMclk) {
        public Record {
            Objects.requireNonNull(fixed, "fixed");
            fixedTransfers = List.copyOf(fixedTransfers);
            drained = List.copyOf(drained);
        }
    }

    /**
     * Rewind state: undrained object entries and the submissions the full queue refused since the last drain, the
     * {@code Player_prev_frame_P2_tail} byte and the last record.
     */
    public record State(List<QueuedEntry> objectPending, int refusedSincePreviousDrain, int tailPreviousFrame,
                        long ordinal, Record last) {
        public State {
            objectPending = List.copyOf(objectPending);
        }
    }

    private final List<QueuedEntry> objectPending = new ArrayList<>();
    /** Add_To_DMA_Queue calls that found the queue full: {@code cmpa.w #DMA_queue_slot,a1 / beq.s ..._Done}. */
    private int refusedSincePreviousDrain;
    /** {@code Player_prev_frame_P2_tail}; Level's RAM clear leaves it 0. */
    private int tailPreviousFrame;
    private long ordinal;
    private Record last;
    private S3kVIntDma.VInt8Transfers vint8;

    /** Decoded once from the session ROM; a constant of the ROM, not rewind state. */
    public void bindRom(S3kVIntDma.VInt8Transfers transfers) {
        this.vint8 = Objects.requireNonNull(transfers, "transfers");
    }

    public boolean romBound() {
        return vint8 != null;
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
        if (dplc == null) {
            return;
        }
        int destination = TAILS_TAIL_VRAM;
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
    public Record serviceVInt(PlcLifecyclePhase phase, boolean hyperFlashStateLive,
                              List<QueuedEntry> animateTilesPending, List<QueuedEntry> kosModulePending) {
        List<QueuedEntry> queued = new ArrayList<>(objectPending);
        queued.addAll(animateTilesPending);
        queued.addAll(kosModulePending);
        objectPending.clear();
        int refused = refusedSincePreviousDrain;
        refusedSincePreviousDrain = 0;
        int kept = Math.min(queued.size(), DMA_QUEUE_ENTRIES);
        List<QueuedEntry> drained = List.copyOf(queued.subList(0, kept));
        // The VDP width comes from the ROM's Level register write; without it no freeze is stated.
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
        long freeze = fixed == FixedTransfers.VINT_8
                ? S3kVIntDma.blankFreeze(fixedTransfers, vint8.h40()) + queueFreeze : -1;
        last = new Record(++ordinal, phase, fixed, fixedTransfers, drained, refused + queued.size() - kept,
                queueFreeze, freeze);
        return last;
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
        return new State(objectPending, refusedSincePreviousDrain, tailPreviousFrame, ordinal, last);
    }

    @Override
    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        objectPending.clear();
        objectPending.addAll(state.objectPending());
        refusedSincePreviousDrain = state.refusedSincePreviousDrain();
        tailPreviousFrame = state.tailPreviousFrame();
        ordinal = state.ordinal();
        last = state.last();
    }

    @Override
    public void resetForMissingSnapshot() {
        clear();
    }

    /**
     * {@code Level} loc_61BE (ROM $61C6): {@code clr.w (DMA_queue).w / move.l #DMA_queue,(DMA_queue_slot).w}.
     * {@code Player_prev_frame_P2_tail} is left as it is.
     */
    public void resetQueueAtLevelLoad() {
        objectPending.clear();
        refusedSincePreviousDrain = 0;
    }

    /** Missing-snapshot reset: no queue, no record. */
    public void clear() {
        objectPending.clear();
        refusedSincePreviousDrain = 0;
        tailPreviousFrame = 0;
        ordinal = 0;
        last = null;
    }
}
