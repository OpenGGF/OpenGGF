package com.openggf.game.sonic3k.timing;

import com.openggf.data.Rom;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static com.openggf.game.sonic3k.timing.S3kKosBusTimeline.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Hand-derived CPU/bus controls, not recorded windows or fitted service counts. */
class TestS3kKosBusTimeline {
    private static byte[] rom;
    private static final int STREAM = 0x3000, DEST = 0xFFFF0000, RTE = 0x5E4;

    @BeforeAll
    static void originalRom() throws Exception {
        File file = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file != null, "original S3K ROM required");
        try (Rom source = new Rom()) {
            assertTrue(source.open(file.getAbsolutePath())); rom = source.readAllBytes();
        }
        assertEquals("cfbf98c36c776677290a872547ac47c53d2761d6",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rom)));
        assertEquals(0x4E73, (rom[RTE] & 255) << 8 | rom[RTE + 1] & 255);
    }

    private static BusInput noWork() { return new BusInput(List.of(), List.of(), 0, null); }
    private static BusInput irq(int level, int entry) {
        return new BusInput(List.of(), List.of(), level, new InterruptInput(entry, 0, List.of()));
    }
    private static State empty(long clock, long refresh, int sr, int irq) {
        return new State(new S3kKosServiceModel.State(), new InitialClock(clock, refresh, 0, sr, irq));
    }
    private static byte[] literalImage() {
        byte[] image = rom.clone();
        // Descriptor: three literals then separate-copy end marker. This is test input, not altered ROM code.
        byte[] stream = {0x17, 0, 'A', 'B', 'C', 0, (byte) 0xF0, 0};
        System.arraycopy(stream, 0, image, STREAM, stream.length);
        return image;
    }
    private static State queued(long clock, long refresh, int sr) {
        var kos = new S3kKosServiceModel.State(); kos.queueKos(STREAM, DEST);
        return new State(kos, new InitialClock(clock, refresh, 0, sr, 0));
    }

    @Test
    void fetchRefreshUsesTheActualFetchClockAndAdvancesTheKosInstruction() {
        var core = new S3kKosBusTimeline(rom); var state = empty(896, 896, 0x2300, 0);
        var step = core.advance(state, noWork(), null);
        // tst.w 12 cycles + one refresh2; deadline restarts from fetch, not its end.
        assertEquals(994, state.mclk()); assertEquals(1792, state.refreshDeadlineMclk());
        assertEquals(0x1C1A, step.executedPc()); assertEquals(0x1C1E, state.pc());
        assertEquals(4, state.kos().conditionCodes()); assertEquals(14, state.refreshMclk());
    }

    @Test
    void crossingRefreshDeadlineDoesNotChargeUntilTheNextOpcodeFetch() {
        var core = new S3kKosBusTimeline(rom); var state = empty(895, 896, 0x2300, 0);
        core.advance(state, noWork(), null);
        assertEquals(979, state.mclk()); assertEquals(0, state.refreshMclk());
        core.advance(state, noWork(), null); // empty queue's beq.w is taken:10, not12
        assertEquals(1063, state.mclk()); assertEquals(1875, state.refreshDeadlineMclk());
        assertEquals(0x1CFA, state.pc());
    }

    @Test
    void longFetchStallAbsorbsRefreshPeriodsWithoutCatchupAndRetainsMclkRemainder() {
        var core = new S3kKosBusTimeline(rom); var state = empty(100, 200, 0x2300, 0);
        core.advance(state, new BusInput(List.of(new Stall(StallKind.DMA_FREEZE, 5003)), List.of(), 0, null), null);
        assertEquals(5201, state.mclk()); assertEquals(5999, state.refreshDeadlineMclk());
        assertEquals(5003, state.stallMclk()); assertEquals(14, state.refreshMclk());
        assertEquals(84, state.instructionMclk());
    }

    @Test
    void withinInstructionStallsAffectAcceptanceButRefreshWaitsForNextFetch() {
        var core = new S3kKosBusTimeline(rom); var state = empty(0, 100, 0x2300, 0);
        core.advance(state, new BusInput(List.of(),
                List.of(new Stall(StallKind.Z80_BUS, 19), new Stall(StallKind.VDP_FIFO, 5)),
                6, new InterruptInput(RTE, 0, List.of())), null);
        // 12*7+24=108; floor(108/7)%10=5 =>55*7. No mid-instruction refresh.
        assertEquals(493, state.mclk()); assertEquals(385, state.acceptanceMclk());
        assertEquals(0, state.refreshMclk()); assertEquals(0x1C1E, state.interruptedPc());
    }

    @Test
    void acceptanceWaitPrecedesTenPhaseLookupAndTheActualAckLevelIsCaptured() {
        var core = new S3kKosBusTimeline(rom); var state = empty(0, 10000, 0x2300, 6);
        core.advance(state, new BusInput(List.of(), List.of(), 0,
                new InterruptInput(RTE, 4, List.of(new Stall(StallKind.Z80_BUS, 7)))), null);
        // Entry wait7 moves phase0 to phase1:59 cycles. Acknowledgment leaves IRQ4 pending under mask6.
        assertEquals(420, state.mclk()); assertEquals(413, state.acceptanceMclk());
        assertEquals(7, state.stallMclk()); assertEquals(4, state.pendingIrq());
        assertEquals(0x2600, state.statusRegister()); assertEquals(0, state.instructionMclk());
    }

    @Test
    void rteChecksPendingIrqBeforeItsOwnBaseCostAndChargesThatCostToTheNewInterval() {
        var core = new S3kKosBusTimeline(rom); var state = empty(0, 10000, 0x2300, 6);
        core.advance(state, new BusInput(List.of(), List.of(), 0,
                new InterruptInput(RTE, 6, List.of())), null);
        State captured = state.copy();
        // Missing inline-acceptance work must refuse before popping the frame/restoring SR.
        assertThrows(Unavailable.class, () -> core.advance(state, noWork(),
                new HandlerInstruction(RTE, -1, 20, -1, Flow.RTE)));
        assertEquals(captured, state);
        core.advance(state, new BusInput(List.of(), List.of(), 0,
                new InterruptInput(RTE, 0, List.of())),
                new HandlerInstruction(RTE, -1, 20, -1, Flow.RTE));
        assertEquals(840, state.mclk()); // accept50 + inline accept50 + RTE20
        assertEquals(140, state.intervalInstructionMclk()); // base20 AFTER the second VInt acceptance
        assertEquals(0x1C1A, state.interruptedPc()); assertEquals(1, state.exceptionDepth());
    }

    @Test
    void restoreKosRteUnmasksPendingVintAtTheRestoredBookmarkBeforeChargingItsBaseCost() {
        byte[] image = literalImage(); var model = new S3kKosServiceModel(image);
        var kos = new S3kKosServiceModel.State(); kos.queueKos(STREAM, DEST);
        var stop = model.serve(kos, 88, S3kKosServiceModel.BoundaryVInt.CALLS_SET_KOS_BOOKMARK);
        assertEquals(0x1C34, stop.stopPc());
        var core = new S3kKosBusTimeline(image);
        var state = new State(kos, new InitialClock(0, 10000, 0, 0x2700 | kos.conditionCodes(), 6));
        for (int i = 0; i < 6; i++) core.advance(state, irq(6, RTE), null);
        assertEquals(0x1D0A, state.pc()); assertEquals(0, state.exceptionDepth());
        var step = core.advance(state, new BusInput(List.of(), List.of(), 0,
                new InterruptInput(RTE, 0, List.of())), null);
        assertEquals(0x1D0A, step.executedPc()); assertEquals(0x1C34, step.interruptedPc());
        assertEquals(6, step.acceptedIrq()); assertEquals(140, state.intervalInstructionMclk());
        assertEquals(Mode.HANDLER, state.mode());
    }

    @Test
    void identicalInstructionAndStallTotalsDoNotSubstituteForTheirFetchOrdering() {
        var core = new S3kKosBusTimeline(rom);
        var beforeFetch = empty(0, 100, 0x2300, 0); var during = beforeFetch.copy();
        var stall = List.of(new Stall(StallKind.DMA_FREEZE, 112));
        core.advance(beforeFetch, new BusInput(stall, List.of(), 0, null), null);
        core.advance(during, new BusInput(List.of(), stall, 0, null), null);
        assertEquals(beforeFetch.instructionMclk(), during.instructionMclk());
        assertEquals(beforeFetch.stallMclk(), during.stallMclk());
        assertEquals(210, beforeFetch.mclk()); assertEquals(196, during.mclk());
        assertEquals(1008, beforeFetch.refreshDeadlineMclk()); assertEquals(100, during.refreshDeadlineMclk());
    }

    @Test
    void allTenAcceptancePhasesUseTheCumulativeCounterAndDoNotExecuteKosFirst() {
        var core = new S3kKosBusTimeline(rom);
        int[] cycles = {50, 59, 58, 57, 56, 55, 54, 53, 52, 51}; // independent explicit GPGX table
        for (int phase = 0; phase < 10; phase++) {
            var state = empty(7000 + phase * 7, 10000, 0x2300, 6);
            var step = core.advance(state, irq(0, RTE), null);
            assertEquals(7000 + phase * 7 + cycles[phase] * 7, state.mclk(), "phase " + phase);
            assertEquals(-1, step.executedPc()); assertEquals(6, step.acceptedIrq());
            assertEquals(0x1C1A, step.interruptedPc()); assertEquals(0x2600, state.statusRegister());
            assertEquals(0, state.instructionMclk()); assertEquals(1, state.exceptionDepth());
        }
    }

    @Test
    void interruptDuringKosStacksTheActualInstructionSuccessor() {
        var core = new S3kKosBusTimeline(rom); var state = empty(7, 10000, 0x2300, 0);
        var step = core.advance(state, irq(6, RTE), null);
        // TST ends at91 =>counter13 =>phase3 costs57.
        assertEquals(490, state.mclk()); assertEquals(0x1C1E, step.interruptedPc());
        assertEquals(12, step.instructionCycles());
        core.advance(state, noWork(), new HandlerInstruction(RTE, -1, 20, -1, Flow.RTE));
        assertEquals(0x1C1E, state.pc()); assertEquals(Mode.KOS, state.mode());
        core.advance(state, noWork(), null);
        assertEquals(0x1CFA, state.pc()); // resumes BEQ; does not restart TST/call
    }

    @Test
    void srMaskSevenDefersLevelSixAndMoveSrInsideKosUsesTheRealSystemBits() {
        var core = new S3kKosBusTimeline(literalImage()); var state = queued(0, 10000, 0x2700);
        for (int i = 0; i < 12 && state.pc() != 0x1C44; i++) core.advance(state, irq(6, RTE), null);
        assertEquals(0x1C44, state.pc());
        int expected = state.statusRegister();
        core.advance(state, irq(6, RTE), null);
        assertEquals(expected, state.kos().dataRegister(6) & 0xFFFF);
        assertEquals(6, state.pendingIrq()); assertEquals(0, state.exceptionDepth());
    }

    @Test
    void explicitNativeCounterRebaseChangesAcceptancePhaseWithoutMovingTheRefreshDeadline() {
        var core = new S3kKosBusTimeline(rom); var state = empty(7000, 8000, 0x2300, 6);
        core.rebasePhase(state, 3420);
        core.advance(state, irq(0, RTE), null);
        // Native relative counter3580 =>floor(/7)=511 =>phase1 =>59, rather than absolute phase0=>50.
        assertEquals(7413, state.mclk()); assertEquals(3420, state.phaseOriginMclk());
        assertEquals(8000, state.refreshDeadlineMclk());
        State before = state.copy();
        assertThrows(Unavailable.class, () -> core.rebasePhase(state, 0));
        assertEquals(before, state);
    }

    private static State interruptBusy(S3kKosBusTimeline core) {
        State state = queued(0, 896, 0x2300);
        for (int i = 0; i < 5; i++) core.advance(state, noWork(), null);
        core.advance(state, irq(6, 0x1BF0), null);
        assertEquals(0x1C34, state.interruptedPc()); assertEquals(0x8001, state.kos().queueCount());
        assertEquals(0, bookmark(state)); assertEquals(0, state.intervalInstructionMclk());
        return state;
    }
    private static int bookmark(State state) {
        byte[] b = state.kos().ramRange(0xFFFFFF3A, 4);
        return (b[0] & 255) << 24 | (b[1] & 255) << 16 | (b[2] & 255) << 8 | b[3] & 255;
    }
    private static final int[][] BOOKMARK_PATH = {
            {0x1BF0, 0x1BF4, 12}, {0x1BF4, 0x1BF6, 8}, {0x1BF6, 0x1BFA, 16},
            {0x1BFA, 0x1C00, 14}, {0x1C00, 0x1C02, 8}, {0x1C02, 0x1C08, 14},
            {0x1C08, 0x1C0A, 8}, {0x1C0A, 0x1C10, 28}, {0x1C10, 0x1C18, 24}, {0x1C18, RTE, 16}};
    private static void handlerTail(S3kKosBusTimeline core, State state) {
        // A synthetic isolated handler continuation: no claim that omitted native handler work is zero.
        boolean found = false;
        for (int[] row : BOOKMARK_PATH) {
            if (state.pc() == row[0]) found = true;
            if (found) core.advance(state, noWork(), new HandlerInstruction(row[0], row[1], row[2], 0x2600, Flow.CONTINUE));
        }
        core.advance(state, noWork(), new HandlerInstruction(RTE, -1, 20, -1, Flow.RTE));
    }
    private static void finish(S3kKosBusTimeline core, State state) {
        for (int i = 0; i < 1000 && state.mode() != Mode.RETURNED; i++) core.advance(state, noWork(), null);
        assertEquals(Mode.RETURNED, state.mode());
    }

    @Test
    void bookmarkWritesHappenInHandlerAndAllThreeBackupInstructionsBelongToFollowingInterval() {
        var core = new S3kKosBusTimeline(literalImage()); State state = interruptBusy(core);
        long interruptedInstructionWork = state.instructionMclk();
        assertEquals(88 * 7, interruptedInstructionWork);
        handlerTail(core, state);
        assertEquals(0x1C34, bookmark(state)); assertEquals(Mode.BACKUP, state.mode());
        assertEquals(168 * 7, state.intervalInstructionMclk()); // Set148 +RTE20; no Backup yet
        assertEquals(0, state.kos().ramByte(0xFFFFFF38));
        finish(core, state);
        assertEquals((168 + 124) * 7, state.intervalInstructionMclk());
        assertEquals(0x23, state.kos().ramByte(0xFFFFFF38)); // actual move SR after RTE
        core.beginNextCall(state);
        long start = state.instructionMclk();
        for (int i = 0; i < 7; i++) core.advance(state, noWork(), null);
        assertEquals(190 * 7, state.instructionMclk() - start); assertEquals(0x1C34, state.pc());
        finish(core, state);
        assertArrayEquals(new byte[]{'A','B','C'}, state.kos().ramRange(DEST, 3));
        assertEquals(0, state.kos().queueCount());
    }

    @Test
    void nestedVintRestoresTheLiveHintInstructionAndItsMask() {
        var core = new S3kKosBusTimeline(rom); var state = empty(0, 10000, 0x2300, 4);
        core.advance(state, irq(0, 0x584), null);
        assertEquals(0x2400, state.statusRegister());
        // Isolated handler event: shipped NOP at584,4 cycles, then IRQ6 arrives.
        core.advance(state, irq(6, RTE), new HandlerInstruction(0x584, 0x586, 4, 0x2400, Flow.CONTINUE));
        assertEquals(2, state.exceptionDepth()); assertEquals(0x2600, state.statusRegister());
        core.advance(state, noWork(), new HandlerInstruction(RTE, -1, 20, -1, Flow.RTE));
        assertEquals(Mode.HANDLER, state.mode()); assertEquals(0x2400, state.statusRegister());
        assertEquals(0x586, state.pc()); assertEquals(1, state.exceptionDepth());
        assertEquals(0x1C1A, state.interruptedPc());
    }

    @Test
    void captureIsDefensiveAndTwoRestoredHandlerBackupAndForwardContinuationsAreIdentical() {
        byte[] image = literalImage(); var core = new S3kKosBusTimeline(image); State live = interruptBusy(core);
        for (int i = 0; i < 4; i++) {
            int[] r = BOOKMARK_PATH[i];
            core.advance(live, noWork(), new HandlerInstruction(r[0], r[1], r[2], 0x2600, Flow.CONTINUE));
        }
        State saved = live.copy(), unchanged = saved.copy();
        // Mutating returned RAM/state, the source image or live continuation cannot mutate a capture/core.
        var escaped = saved.kos(); escaped.queueKos(0x1234, DEST + 0x100);
        image[0x1C1A] = 0; handlerTail(core, live); finish(core, live); core.beginNextCall(live); finish(core, live);
        assertEquals(unchanged, saved);
        for (int restore = 0; restore < 2; restore++) {
            State replay = saved.copy(); handlerTail(core, replay); finish(core, replay);
            core.beginNextCall(replay); finish(core, replay);
            assertEquals(live, replay); assertEquals(live.hashCode(), replay.hashCode());
        }
    }

    @Test
    void liveRamTrampolineRequiresAllSixBytesAndCapturesItsRomContinuationTwice() {
        var core = new S3kKosBusTimeline(rom); var entry = empty(0, 0, 0x2300, 6);
        core.advance(entry, irq(0, 0xFFFFF0), null);
        State before = entry.copy();
        assertThrows(Unavailable.class, () -> core.advance(entry, noWork(), null));
        assertThrows(Unavailable.class, () -> core.advance(entry, noWork(),
                new HandlerInstruction(0xFFFFF0, 0x584, 12, 0x2600, Flow.CONTINUE)));
        assertEquals(before, entry);
        byte[] bytes = {0x4E, (byte) 0xF9, 0, 0, 5, (byte) 0x84};
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= 1;
            var bad = new HandlerInstruction(0xFFFFF0, 0x584, 12, 0x2600, Flow.CONTINUE, bytes);
            assertThrows(Unavailable.class, () -> core.advance(entry, noWork(), bad));
            assertEquals(before, entry); bytes[i] ^= 1;
        }
        for (int cost : new int[]{11, 13}) {
            var bad = new HandlerInstruction(0xFFFFF0, 0x584, cost, 0x2600, Flow.CONTINUE, bytes);
            assertThrows(Unavailable.class, () -> core.advance(entry, noWork(), bad)); assertEquals(before, entry);
        }
        var jump = new HandlerInstruction(0xFFFFF0, 0x584, 12, 0x2600, Flow.CONTINUE, bytes);
        bytes[0] = 0; jump.ramInstruction()[1] = 0; // descriptor owns a defensive copy
        for (int restore = 0; restore < 2; restore++) {
            State replay = before.copy(); core.advance(replay, noWork(), jump);
            assertEquals(0x584, replay.pc()); assertEquals(Mode.HANDLER, replay.mode());
            assertEquals(84, replay.instructionMclk()); assertEquals(14, replay.refreshMclk());
            State saved = replay.copy();
            core.advance(replay, noWork(), new HandlerInstruction(0x584, 0x586, 4, 0x2600, Flow.CONTINUE));
            State second = saved.copy();
            core.advance(second, noWork(), new HandlerInstruction(0x584, 0x586, 4, 0x2600, Flow.CONTINUE));
            assertEquals(replay, second); assertEquals(0x586, second.pc());
            State frozen = second.copy();
            assertThrows(Unavailable.class, () -> core.advance(second, noWork(), null));
            assertEquals(frozen, second); // no invented remaining V-int body
        }
    }

    @Test
    void missingBusVectorOrHandlerWorkAndWrongBookmarkOperandRefuseAtomically() {
        var core = new S3kKosBusTimeline(literalImage()); State state = queued(0, 0, 0x2300), before = state.copy();
        assertThrows(Unavailable.class, () -> core.advance(state, null, null)); assertEquals(before, state);
        assertThrows(Unavailable.class, () -> core.advance(state, new BusInput(List.of(), List.of(), 6, null), null));
        assertEquals(before, state);
        State handler = interruptBusy(core), captured = handler.copy();
        assertThrows(Unavailable.class, () -> core.advance(handler, noWork(), null)); assertEquals(captured, handler);
        assertThrows(Unavailable.class, () -> core.advance(handler, noWork(),
                new HandlerInstruction(0x1BF0, 0x1BF4, 11, 0x2600, Flow.CONTINUE)));
        assertEquals(captured, handler);
        assertThrows(Unavailable.class, () -> new BusInput(null, List.of(), 0, null));
        assertThrows(Unavailable.class, () -> new InitialClock(0, 0, 0, 0x2300, 7));
        assertThrows(Unavailable.class, () -> new State(new S3kKosServiceModel.State(), null));
        assertThrows(Unavailable.class, () -> new InterruptInput(RTE, 0, null));
    }

    @Test
    void overflowOrAnInstructionSuppliedAheadOfPendingIrqRefusesBeforeRamOrClockMutation() {
        var core = new S3kKosBusTimeline(literalImage()); State state = queued(0, 0, 0x2300), before = state.copy();
        var huge = new BusInput(List.of(new Stall(StallKind.DMA_FREEZE, Long.MAX_VALUE)), List.of(), 0, null);
        assertThrows(ArithmeticException.class, () -> core.advance(state, huge, null)); assertEquals(before, state);
        State pending = empty(0, 0, 0x2300, 6), saved = pending.copy();
        var stale = new BusInput(List.of(new Stall(StallKind.Z80_BUS, 7)), List.of(), 0,
                new InterruptInput(RTE, 0, List.of()));
        assertThrows(Unavailable.class, () -> core.advance(pending, stale, null)); assertEquals(saved, pending);
    }

    private static void advanceTo(S3kKosBusTimeline core, State state, int pc) {
        for (int i = 0; i < 100 && state.pc() != pc; i++) core.advance(state, noWork(), null);
        assertEquals(pc, state.pc());
    }

    @Test
    void unsupportedLiteralDestinationRefusesBeforeSourceCursorOrAnyCapturedStateChanges() {
        var core = new S3kKosBusTimeline(literalImage());
        var kos = new S3kKosServiceModel.State(); kos.queueKos(STREAM, 0);
        var state = new State(kos, new InitialClock(0, 896, 0, 0x2300, 0));
        advanceTo(core, state, 0x1C58); State before = state.copy();
        assertThrows(IllegalStateException.class, () -> core.advance(state, noWork(), null));
        assertEquals(before, state, "unsupported write must preserve CPU, cursor, RAM and complete timeline state");
    }

    @Test
    void unsupportedSourceRefusesBeforeFetchStallRefreshOrAnyCapturedStateChanges() {
        var core = new S3kKosBusTimeline(rom);
        var kos = new S3kKosServiceModel.State(); kos.queueKos(rom.length, DEST);
        var state = new State(kos, new InitialClock(0, 0, 0, 0x2300, 0));
        advanceTo(core, state, 0x1C38); State before = state.copy();
        var bus = new BusInput(List.of(new Stall(StallKind.Z80_BUS, 7)), List.of(), 0, null);
        assertThrows(IllegalStateException.class, () -> core.advance(state, bus, null));
        assertEquals(before, state, "unsupported read must preserve CPU, cursor, RAM and complete timeline state");
    }

    @Test
    void busListsAreDefensiveAndUnknownOrNegativeStallsAreNotSilentZero() {
        var stalls = new ArrayList<Stall>(); stalls.add(new Stall(StallKind.Z80_BUS, 17));
        var input = new BusInput(stalls, List.of(), 0, null); stalls.clear();
        assertEquals(1, input.fetchStalls().size());
        assertThrows(UnsupportedOperationException.class, () -> input.fetchStalls().clear());
        assertThrows(Unavailable.class, () -> new Stall(null, 1));
        assertThrows(Unavailable.class, () -> new Stall(StallKind.DMA_FREEZE, -1));
        assertThrows(Unavailable.class, () -> new HandlerInstruction(RTE, -1, 0, -1, Flow.RTE));
    }

    @Test
    void everyCodeAndOperandByteIncludingBookmarkBackupTargetIsAuthenticatedInMemoryOnly() {
        byte[] changed = rom.clone();
        for (int pc = 0x1BF0; pc < 0x1D18; pc++) {
            changed[pc] ^= 1;
            assertThrows(Unavailable.class, () -> new S3kKosBusTimeline(changed), "changed byte " + Integer.toHexString(pc));
            changed[pc] ^= 1;
        }
    }
}
