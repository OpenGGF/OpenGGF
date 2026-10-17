package com.openggf.game.sonic3k.timing;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Target;
import com.openggf.game.sonic3k.timing.S3kVIntDma.Transfer;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FixedTransfers;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Producer;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.QueuedEntry;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.BodySlot;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.VideoRegion;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FreezeStatus;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Ledger semantics without a ROM: the VInt_8 transfers are the decoded values written out. */
class TestS3kVIntDmaLedger {
    private static final S3kVIntDma.VInt8Transfers VINT8 = new S3kVIntDma.VInt8Transfers(
            new Transfer(0x40, Target.CRAM, 0), new Transfer(0x1C0, Target.VRAM, 0xF000),
            new Transfer(0x140, Target.VRAM, 0xF800), true);

    private static S3kVIntDmaLedger ledger() {
        S3kVIntDmaLedger ledger = new S3kVIntDmaLedger();
        ledger.bindRom(VINT8);
        return ledger;
    }

    private static SpriteDplcFrame dplc(int... counts) {
        List<TileLoadRequest> requests = new ArrayList<>();
        for (int count : counts) {
            requests.add(new TileLoadRequest(0, count));
        }
        return new SpriteDplcFrame(requests);
    }

    @Test
    void tailDplcQueuesOnePerRequestOnlyWhenTheFrameChanges() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observeTailsTailFrame(5, dplc(6));
        ledger.observeTailsTailFrame(5, dplc(6));
        assertEquals(List.of(new QueuedEntry(Producer.OBJECT_DPLC, 96, 0xD600)), ledger.pendingObjectEntries(),
                "frame 5 once: six tiles = $60 words to ArtTile_Player_2_Tail");
        ledger.observeTailsTailFrame(6, dplc(2, 3));
        assertEquals(List.of(new QueuedEntry(Producer.OBJECT_DPLC, 96, 0xD600),
                new QueuedEntry(Producer.OBJECT_DPLC, 32, 0xD600),
                new QueuedEntry(Producer.OBJECT_DPLC, 48, 0xD640)), ledger.pendingObjectEntries(),
                "d4 advances by each piece");
        // an empty DPLC still stores Player_prev_frame_P2_tail before bmi.s returns
        ledger.observeTailsTailFrame(7, dplc());
        ledger.observeTailsTailFrame(7, dplc(1));
        assertEquals(3, ledger.pendingObjectEntries().size());
        // mapping_frame is a byte
        ledger.observeTailsTailFrame(0x107, dplc(1));
        assertEquals(3, ledger.pendingObjectEntries().size());
    }

    @Test
    void vint8RecordsFixedTransfersThenTheQueueInLevelLoopOrder() {
        S3kVIntDmaLedger ledger = ledger();
        assertEquals(26_855, ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of())
                .conditionalFreezeMclk());
        ledger.observeTailsTailFrame(5, dplc(6));
        QueuedEntry aniPlc = new QueuedEntry(Producer.ANIMATE_TILES, 0x40, 0x5000);
        QueuedEntry kosm = new QueuedEntry(Producer.KOS_MODULE, 0x800, 0x2000);
        Record record = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(aniPlc), List.of(kosm));
        assertEquals(2, record.ordinal());
        assertEquals(FixedTransfers.VINT_8, record.fixed());
        assertEquals(List.of(new QueuedEntry(Producer.OBJECT_DPLC, 96, 0xD600), aniPlc, kosm), record.drained());
        assertEquals(3218 + 0x40 * 3420 / 102 + 68_668, record.queueFreezeMclk());
        assertEquals(26_855 + record.queueFreezeMclk(), record.conditionalFreezeMclk());
        assertEquals(List.of(), ledger.pendingObjectEntries(), "Process_DMA_Queue empties the queue");
    }

    @Test
    void fullQueueDropsLaterEntriesInSubmissionOrder() {
        S3kVIntDmaLedger ledger = ledger();
        for (int frame = 1; frame <= 15; frame++) {
            ledger.observeTailsTailFrame(frame, dplc(1));
        }
        List<QueuedEntry> tiles = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            tiles.add(new QueuedEntry(Producer.ANIMATE_TILES, 0x10, 0x100 * k));
        }
        Record record = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, tiles,
                List.of(new QueuedEntry(Producer.KOS_MODULE, 0x800, 0)));
        assertEquals(18, record.drained().size());
        assertEquals(3, record.droppedEntries(), "two Animate_Tiles uploads and the KosM entry");
        assertEquals(Producer.ANIMATE_TILES, record.drained().get(17).producer());
    }

    @Test
    void submissionsToAFullQueueAreRefusedAndLevelLoadEmptiesIt() {
        S3kVIntDmaLedger ledger = ledger();
        for (int frame = 1; frame <= 20; frame++) {
            ledger.observeTailsTailFrame(frame, dplc(1));
        }
        assertEquals(18, ledger.pendingObjectEntries().size(), "the queue never holds more than 18 entries");
        S3kVIntDmaLedger.State full = ledger.capture();
        Record record = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false,
                List.of(new QueuedEntry(Producer.ANIMATE_TILES, 0x10, 0)), List.of());
        assertEquals(18, record.drained().size());
        assertEquals(3, record.droppedEntries(), "two refused tail pieces and the Animate_Tiles upload");
        ledger.restore(full);
        assertEquals(full, ledger.capture(), "the refused count is rewind state");
        ledger.resetAtFreshLevel();
        assertEquals(List.of(), ledger.pendingObjectEntries());
        assertEquals(0, ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of())
                .droppedEntries());
    }

    @Test
    void bodyRoutinesUseTheirOwnPreviousFrameAndVramBase() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(4, 2));
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(4, 2));
        ledger.observePlayerFrame(BodySlot.TAILS, 0xBA, dplc(3));
        ledger.observeTailsTailFrame(0xBA, dplc(1));
        assertEquals(List.of(new QueuedEntry(Producer.OBJECT_DPLC, 64, 0xD000),
                new QueuedEntry(Producer.OBJECT_DPLC, 32, 0xD080),
                new QueuedEntry(Producer.OBJECT_DPLC, 48, 0xD400),
                new QueuedEntry(Producer.OBJECT_DPLC, 16, 0xD600)), ledger.pendingObjectEntries(),
                "Player_prev_frame, Player_prev_frame_P2 and the tail byte are independent; one entry per piece");
        S3kVIntDmaLedger.State queued = ledger.capture();
        Record drained = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(),
                List.of());
        assertEquals(4, drained.drained().size());
        ledger.restore(queued);
        assertEquals(drained, ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false,
                List.of(), List.of()), "restore across queued body DMA replays the same drain");
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(4, 2));
        assertEquals(List.of(), ledger.pendingObjectEntries(), "the stored byte survives the drain");
    }

    @Test
    void freshLevelClearsThePreviousFrameBytesSoTheSameNonZeroFrameQueuesAgain() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observeTailsTailFrame(0x27, dplc(2));
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(3));
        ledger.observePlayerFrame(BodySlot.TAILS, 0xAD, dplc(1));
        ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of());
        // control: without a Level entry the same frames queue nothing
        ledger.observeTailsTailFrame(0x27, dplc(2));
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(3));
        ledger.observePlayerFrame(BodySlot.TAILS, 0xAD, dplc(1));
        assertEquals(List.of(), ledger.pendingObjectEntries());
        // Level $610E zeroes $F766/$F7DE/$F7DF: the first post-load call with the same frame queues again
        ledger.resetAtFreshLevel();
        ledger.observeTailsTailFrame(0x27, dplc(2));
        ledger.observePlayerFrame(BodySlot.SONIC_KNUCKLES, 0xBA, dplc(3));
        ledger.observePlayerFrame(BodySlot.TAILS, 0xAD, dplc(1));
        assertEquals(List.of(new QueuedEntry(Producer.OBJECT_DPLC, 32, 0xD600),
                new QueuedEntry(Producer.OBJECT_DPLC, 48, 0xD000),
                new QueuedEntry(Producer.OBJECT_DPLC, 16, 0xD400)), ledger.pendingObjectEntries());
    }

    @Test
    void reviewCounterexampleExceedsNtscVBlankAndIsNotATotal() {
        // fixed 26,855 + KosM $800 words 68,668 + AniPLC $600 words 51,501 = 147,024 >= NTSC 126,540
        List<QueuedEntry> aniPlc = List.of(new QueuedEntry(Producer.ANIMATE_TILES, 0x600, 0x2000));
        List<QueuedEntry> kosm = List.of(new QueuedEntry(Producer.KOS_MODULE, 0x800, 0x4000));
        Record ntsc = ledger().serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, aniPlc, kosm);
        assertEquals(FreezeStatus.EXCEEDS_VBLANK, ntsc.freezeStatus());
        assertEquals(-1, ntsc.conditionalFreezeMclk());
        assertEquals(68_668 + 51_501, ntsc.queueFreezeMclk());
        Record pal = ledger().serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.PAL, false, aniPlc, kosm);
        assertEquals(FreezeStatus.CONDITIONAL_ON_VINT_START, pal.freezeStatus(), "conditional, never a total");
        assertEquals(147_024, pal.conditionalFreezeMclk());
    }

    @Test
    void anAcceptedImageWithoutThePinnedVInt8FailsClosed() {
        assertEquals(java.util.Optional.empty(), S3kVIntDma.VInt8Transfers.tryDecode(new byte[0x8000]));
        S3kVIntDmaLedger ledger = new S3kVIntDmaLedger();
        ledger.bindRom(S3kVIntDma.VInt8Transfers.tryDecode(new byte[0x8000]));
        assertEquals(true, ledger.romBound());
        ledger.observeTailsTailFrame(5, dplc(6));
        Record record = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(),
                List.of());
        assertEquals(FreezeStatus.ROM_UNSUPPORTED, record.freezeStatus());
        assertEquals(-1, record.conditionalFreezeMclk());
        assertEquals(-1, record.queueFreezeMclk());
        assertEquals(1, record.drained().size(), "the work itself is still recorded");
    }

    @Test
    void theTotalMustFitTheRegionsVBlank() {
        QueuedEntry module = new QueuedEntry(Producer.KOS_MODULE, 0x800, 0);
        List<QueuedEntry> modules = List.of(module, module);
        long expected = 26_855 + 2L * 68_668;
        assertEquals(-1, ledger().serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(),
                modules).conditionalFreezeMclk(), "164,191 cycles cannot end inside NTSC's 126,540-cycle VBLANK");
        assertEquals(expected, ledger().serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.PAL, false,
                List.of(), modules).conditionalFreezeMclk(), "PAL's 300,960-cycle VBLANK holds it");
        Record unknown = ledger().serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.UNKNOWN, false, List.of(),
                List.of());
        assertEquals(-1, unknown.conditionalFreezeMclk());
        assertEquals(FreezeStatus.UNKNOWN_REGION, unknown.freezeStatus());
        assertEquals(VideoRegion.UNKNOWN, unknown.region());
    }

    @Test
    void unmodelledRoutinesAndHyperFlashStateNoTotal() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observeTailsTailFrame(5, dplc(6));
        Record titleCard = ledger.serviceVInt(PlcLifecyclePhase.LEVEL_TITLE_CARD, VideoRegion.NTSC, false, List.of(), List.of());
        assertEquals(FixedTransfers.UNMODELLED, titleCard.fixed(), "VInt_A_C is not modelled here");
        assertEquals(-1, titleCard.conditionalFreezeMclk());
        assertEquals(FreezeStatus.FIXED_UNMODELLED, titleCard.freezeStatus());
        assertEquals(3218, titleCard.queueFreezeMclk(), "its drained queue is still recorded");
        Record flash = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, true, List.of(), List.of());
        assertEquals(FixedTransfers.UNMODELLED, flash.fixed());
        assertEquals(-1, flash.conditionalFreezeMclk());
        S3kVIntDmaLedger unbound = new S3kVIntDmaLedger();
        Record noRom = unbound.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of());
        assertEquals(-1, noRom.conditionalFreezeMclk());
        assertEquals(FreezeStatus.ROM_UNSUPPORTED, noRom.freezeStatus());
        assertEquals(-1, noRom.queueFreezeMclk());
    }

    @Test
    void undrainedEntriesSurviveUntilTheNextDrainingVInt() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observeTailsTailFrame(5, dplc(6));
        // a lag V-int (VInt_0) never calls serviceVInt; the next object pass adds to the same queue
        ledger.observeTailsTailFrame(6, dplc(1));
        Record record = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of());
        assertEquals(2, record.drained().size());
        assertEquals(1, record.ordinal());
    }

    @Test
    void captureRestoreAndForwardReplayAreExact() {
        S3kVIntDmaLedger ledger = ledger();
        ledger.observeTailsTailFrame(5, dplc(6));
        ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false, List.of(), List.of());
        ledger.observeTailsTailFrame(9, dplc(2, 1));
        S3kVIntDmaLedger.State capture = ledger.capture();
        Record forward = ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false,
                List.of(new QueuedEntry(Producer.ANIMATE_TILES, 0x20, 0x400)), List.of());
        ledger.observeTailsTailFrame(9, dplc(2, 1));
        ledger.observeTailsTailFrame(10, dplc(4));
        S3kVIntDmaLedger.State after = ledger.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            ledger.restore(capture);
            assertEquals(capture, ledger.capture());
            assertEquals(forward, ledger.serviceVInt(PlcLifecyclePhase.ORDINARY_LEVEL, VideoRegion.NTSC, false,
                    List.of(new QueuedEntry(Producer.ANIMATE_TILES, 0x20, 0x400)), List.of()));
            ledger.observeTailsTailFrame(9, dplc(2, 1));
            ledger.observeTailsTailFrame(10, dplc(4));
            assertEquals(after, ledger.capture());
        }
        ledger.resetForMissingSnapshot();
        assertNull(ledger.lastRecord());
        assertEquals(List.of(), ledger.pendingObjectEntries());
    }
}
