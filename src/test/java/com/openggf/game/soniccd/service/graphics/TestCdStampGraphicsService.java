package com.openggf.game.soniccd.service.graphics;

import com.openggf.game.soniccd.device.CdStampGraphics;
import com.openggf.game.soniccd.device.CdStampRenderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.device.CdStampRenderRequest.MapSize.PIXELS_256;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.Priority.NORMAL;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.StampSize.PIXELS_16;
import static com.openggf.game.soniccd.service.graphics.CdStampGraphicsService.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public operation behavior only; no engine, media, native process, or timing fixture. */
class TestCdStampGraphicsService {
    private static final int MAP = 0x20000;
    private static final int TRACE = 0x30000;
    private static final int IMAGE = 0x10000;
    private static final WordRamAccess SUB = new WordRamAccess(WordRamMode.TWO_M, WordRamOwner.SUB);
    private static final WordRamAccess MAIN = new WordRamAccess(WordRamMode.TWO_M, WordRamOwner.MAIN);
    private static final WordRamAccess ONE_M = new WordRamAccess(WordRamMode.ONE_M, WordRamOwner.SUB);
    private static final CompletionSignals QUIET = new CompletionSignals(false, false);

    @Test
    void positiveElapsedUsesCeilingAndCanAdvanceAheadWhileExactAndNonpositiveBoundariesDoNoExtraWork() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(4, 3), clock(1000), SUB); // 156 clocks per line.
        Progress initial = service.readback(operation);
        assertTrue(service.advance(operation, clock(1000), SUB, QUIET, 0).renderTicket().isEmpty());
        assertTrue(service.advance(operation, clock(999), SUB, QUIET, 0).renderTicket().isEmpty());
        assertEquals(initial, service.readback(operation));

        var first = service.advance(operation, clock(1001), SUB, QUIET, 1);
        assertEquals(clock(1156), first.progress().graphicsClock());
        assertEquals(2, first.progress().reportedRemainingLines());
        assertEquals(0, first.progress().rendered().completedLines());
        assertTrue(first.effects().isEmpty());
        service.render(first.renderTicket().orElseThrow(), ram, SUB);
        assertEquals(0x7777, word(ram, IMAGE));
        assertTrue(service.advance(operation, clock(1001), SUB, QUIET, 0).renderTicket().isEmpty());
        assertTrue(service.advance(operation, clock(1156), SUB, QUIET, 0).renderTicket().isEmpty());

        render(service, service.advance(operation, clock(1312), SUB, QUIET, 1), ram);
        assertEquals(2, service.readback(operation).rendered().completedLines());
        var last = service.advance(operation, clock(1313), SUB, QUIET, 1);
        assertEquals(clock(1468), last.progress().graphicsClock());
        assertFalse(last.progress().busy());
        assertFalse(last.progress().finalPixelsRendered());
        render(service, last, ram);
        assertTrue(service.readback(operation).finalPixelsRendered());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void busyClearThenConditionalPollWakeAndIrqPrecedeTheWholeCompletingRender(boolean irq, boolean poll) {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        Arrays.fill(ram, IMAGE, IMAGE + 12, (byte) 0xa5);
        byte[] before = ram.clone();
        var operation = service.submit(request(1, 3), clock(50), SUB);
        var step = service.advance(operation, clock(302), SUB, new CompletionSignals(irq, poll), 3);

        Progress clear = step.progress();
        assertEquals(Phase.RENDER_PENDING, clear.phase());
        assertEquals(0, clear.reportedRemainingLines());
        assertEquals(3, clear.pendingLines());
        assertEquals(0, clear.rendered().completedLines());
        assertEquals(TRACE, clear.rendered().traceByteCursor());
        assertFalse(clear.busy());
        assertFalse(clear.finalPixelsRendered());
        assertArrayEquals(before, ram, "busy clears before every pixel of the completing batch");
        int effect = 0;
        assertEquals(new BusyCleared(clear), step.effects().get(effect++));
        if (poll) { assertEquals(new PollWakeRequested(clock(302)), step.effects().get(effect++)); }
        if (irq) { assertEquals(new LevelOneInterruptRequested(), step.effects().get(effect++)); }
        assertEquals(effect, step.effects().size());
        assertThrows(UnsupportedOperationException.class, () -> step.effects().clear());

        var rendered = service.render(step.renderTicket().orElseThrow(), ram, SUB);
        assertEquals(0x75, Byte.toUnsignedInt(ram[IMAGE]));
        assertEquals(0x75, Byte.toUnsignedInt(ram[IMAGE + 4]));
        assertEquals(0x75, Byte.toUnsignedInt(ram[IMAGE + 8]));
        assertEquals(TRACE + 24, rendered.progress().rendered().traceByteCursor());
        assertEquals(2L * IMAGE + 24, rendered.progress().rendered().imageNibbleCursor());
        assertEquals(3, rendered.progress().rendered().completedLines());
        assertTrue(rendered.progress().finalPixelsRendered());
        assertEquals(List.of(new FinalPixelsRendered(rendered.progress())), rendered.effects());
        assertTrue(service.advance(operation, clock(10000), SUB, new CompletionSignals(true, true), 255)
                .effects().isEmpty(), "completion effects are one-shot");
    }

    @Test
    void mainOwnedTwoMRamResetsTheGraphicsCounterWithoutProgressOrAccumulatingHaltedTime() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(2, 2), clock(100), MAIN);
        var halted = service.advance(operation, clock(1000), MAIN, new CompletionSignals(true, true), 0);
        assertEquals(clock(1000), halted.progress().graphicsClock());
        assertEquals(2, halted.progress().reportedRemainingLines());
        assertEquals(0, halted.progress().rendered().completedLines());
        assertTrue(halted.progress().busy());
        assertTrue(halted.effects().isEmpty());
        assertTrue(halted.renderTicket().isEmpty());
        assertTrue(service.advance(operation, clock(1000), SUB, QUIET, 0).renderTicket().isEmpty());
        var first = service.advance(operation, clock(1001), SUB, QUIET, 1);
        assertEquals(clock(1108), first.progress().graphicsClock());
        render(service, first, ram);
        var backwardsHalt = service.advance(operation, clock(50), MAIN, QUIET, 0);
        assertEquals(clock(50), backwardsHalt.progress().graphicsClock());
        assertEquals(1, backwardsHalt.progress().rendered().completedLines());
        render(service, service.advance(operation, clock(51), SUB, QUIET, 1), ram);
        assertEquals(clock(158), service.graphicsClock());
    }

    @Test
    void explicitRebasePreservesProgressAndHandlesActiveAndInactiveUnsignedWrap() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(2, 2), clock(900), SUB);
        render(service, service.advance(operation, clock(901), SUB, QUIET, 1), ram);
        var before = service.readback(operation).rendered();
        service.rebase(new CdStampClock.Rebase(0, 1, 1000));
        assertEquals(new CdStampClock(1, 8), service.graphicsClock());
        assertEquals(before, service.readback(operation).rendered());
        assertThrows(IllegalArgumentException.class,
                () -> service.advance(operation, clock(9), SUB, QUIET, 1));
        assertTrue(service.advance(operation, new CdStampClock(1, 8), SUB, QUIET, 0).renderTicket().isEmpty());
        render(service, service.advance(operation, new CdStampClock(1, 9), SUB, QUIET, 1), ram);
        assertEquals(new CdStampClock(1, 116), service.graphicsClock());
        var completed = service.readback(operation).rendered();
        service.rebase(new CdStampClock.Rebase(1, 2, 1000));
        assertEquals(new CdStampClock(2, 0xfffffc8cL), service.graphicsClock());
        assertEquals(completed, service.readback(operation).rendered());
        assertThrows(IllegalArgumentException.class,
                () -> service.rebase(new CdStampClock.Rebase(1, 2, 1000)));

        var idle = new CdStampGraphicsService(0);
        idle.rebase(new CdStampClock.Rebase(0, 1, 834208));
        assertEquals(new CdStampClock(1, 4294133088L), idle.graphicsClock());
    }

    @Test
    void signedElapsedUsesUint32SubtractionAcrossCounterWrapAndDoesNotTreatNegativeAsHugeWork() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(1, 2), clock(0xfffffff0L), SUB);
        render(service, service.advance(operation, clock(0x10), SUB, QUIET, 1), ram);
        assertEquals(clock(0x44), service.graphicsClock());
        assertEquals(1, service.readback(operation).rendered().completedLines());
        assertTrue(service.advance(operation, clock(0x43), SUB, QUIET, 0).renderTicket().isEmpty());

        service.reset(0);
        operation = service.submit(request(1, 2), new CdStampClock(1, 0, 0), SUB);
        assertTrue(service.advance(operation, new CdStampClock(1, 0, 0x80000000L), SUB, QUIET, 0)
                .renderTicket().isEmpty());
        assertEquals(2, service.readback(operation).reportedRemainingLines());
        render(service, service.advance(operation, new CdStampClock(1, 0, 0x7fffffffL), SUB, QUIET, 2), ram);
        assertEquals(new CdStampClock(1, 0, 168), service.graphicsClock());
    }

    @Test
    void zeroHeightStillNeedsPositiveServiceBeforeBusyClearAndZeroWidthStillConsumesTraceLines() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        byte[] original = ram.clone();
        var empty = service.submit(request(1, 0), clock(0), SUB);
        assertTrue(service.readback(empty).busy());
        assertTrue(service.advance(empty, clock(0), SUB, QUIET, 0).renderTicket().isEmpty());
        var emptyStep = service.advance(empty, clock(1), SUB, new CompletionSignals(true, true), 0);
        assertEquals(0, emptyStep.progress().pendingLines());
        assertEquals(clock(0), emptyStep.progress().graphicsClock());
        assertEquals(3, emptyStep.effects().size());
        render(service, emptyStep, ram);
        assertArrayEquals(original, ram);
        assertEquals(TRACE, service.readback(empty).rendered().traceByteCursor());

        var noDots = service.submit(new CdStampOperationRequest(geometry(0), TRACE, 2L * IMAGE + 3, 2),
                clock(10), SUB);
        assertEquals(60, service.readback(noDots).cyclesPerLine());
        render(service, service.advance(noDots, clock(11), SUB, QUIET, 1), ram);
        render(service, service.advance(noDots, clock(71), SUB, QUIET, 1), ram);
        assertArrayEquals(original, ram);
        assertEquals(clock(130), service.graphicsClock());
        assertEquals(TRACE + 16, service.readback(noDots).rendered().traceByteCursor());
        assertEquals(2L * IMAGE + 19, service.readback(noDots).rendered().imageNibbleCursor());
    }

    @Test
    void maximumRequestIsBoundedAndWrapsTraceAndDestinationAccessWithoutTruncatingCursors() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = new byte[CdStampGraphics.WORD_RAM_BYTES];
        var operation = service.submit(new CdStampOperationRequest(geometry(511), 0x3fff8, 0x7ffff, 255),
                clock(0), SUB);
        var step = service.advance(operation, clock(3534300), SUB, QUIET, 255);
        assertEquals(255, step.progress().pendingLines());
        render(service, step, ram);
        var done = service.readback(operation);
        assertEquals(255, done.rendered().completedLines());
        assertEquals(0x7f0, done.rendered().traceByteCursor());
        assertEquals(0x807f7L, done.rendered().imageNibbleCursor());
        assertEquals(clock(3534300), done.graphicsClock());
        assertArrayEquals(new byte[ram.length], ram);
    }

    @Test
    void invalidBudgetsModesEpochsAndRamFailBeforePixelsOrStateChanges() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        byte[] original = ram.clone();
        assertThrows(IllegalArgumentException.class, () -> service.submit(request(1, 2), clock(0), ONE_M));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(request(1, 2), new CdStampClock(1, 0), SUB));
        var operation = service.submit(request(1, 2), clock(0), SUB);
        Progress initial = service.readback(operation);
        for (int budget : new int[]{-1, 256, 0, 1}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.advance(operation, clock(168), SUB, QUIET, budget));
            assertEquals(initial, service.readback(operation));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.advance(operation, clock(168), ONE_M, QUIET, 2));
        assertThrows(NullPointerException.class,
                () -> service.advance(operation, clock(168), SUB, null, 2));
        assertEquals(initial, service.readback(operation));
        var step = service.advance(operation, clock(168), SUB, QUIET, 2);
        RenderTicket ticket = step.renderTicket().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> service.render(ticket, new byte[CdStampGraphics.WORD_RAM_BYTES - 1], SUB));
        assertThrows(NullPointerException.class, () -> service.render(ticket, null, SUB));
        assertThrows(IllegalArgumentException.class, () -> service.render(ticket, ram, ONE_M));
        assertThrows(IllegalStateException.class, () -> service.render(ticket, ram, MAIN));
        assertThrows(IllegalStateException.class,
                () -> service.advance(operation, clock(200), SUB, QUIET, 2));
        assertThrows(IllegalStateException.class, () -> service.rebase(new CdStampClock.Rebase(0, 1, 100)));
        assertThrows(IllegalStateException.class, () -> service.submit(request(1, 1), clock(200), SUB));
        assertEquals(step.progress(), service.readback(operation));
        assertArrayEquals(original, ram);
        render(service, step, ram); // Rejected attempts do not consume the valid ticket.
    }

    @Test
    void readsLiveRamAtRenderAndLaterTraceReadsObserveEarlierDestinationWrites() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(1, 1), clock(0), SUB);
        var pending = service.advance(operation, clock(1), SUB, QUIET, 1);
        ram[128] = (byte) 0x90;
        render(service, pending, ram);
        assertEquals(0x90, Byte.toUnsignedInt(ram[IMAGE]));

        service.reset(0);
        ram = memory();
        // First output changes the next line's X word from 0000 to 7000. Nonrepeat then clips it.
        var overlapping = new CdStampOperationRequest(geometry(1), TRACE, 2L * (TRACE + 8), 2);
        operation = service.submit(overlapping, new CdStampClock(1, 0, 0), SUB);
        render(service, service.advance(operation, new CdStampClock(1, 0, 168), SUB, QUIET, 2), ram);
        assertEquals(0x7000, word(ram, TRACE + 8));
        assertEquals(0, Byte.toUnsignedInt(ram[TRACE + 12]), "later trace read observes earlier writes");
        assertEquals(TRACE + 16, service.readback(operation).rendered().traceByteCursor());
    }

    @Test
    void snapshotsRestoreCallerRamAndPartialCursorsThenReproduceForwardExecution() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var request = new CdStampOperationRequest(geometry(1), TRACE, 2L * (TRACE + 8), 3);
        var operation = service.submit(request, clock(100), SUB);
        render(service, service.advance(operation, clock(101), SUB, QUIET, 1), ram);
        var snapshot = service.capture(operation, ram);
        assertEquals(1, snapshot.progress().rendered().completedLines());
        byte[] copy = snapshot.wordRamCopy();
        copy[TRACE + 8] = 0;
        assertEquals(0x70, Byte.toUnsignedInt(snapshot.wordRamCopy()[TRACE + 8]));

        render(service, service.advance(operation, clock(352), SUB, QUIET, 2), ram);
        byte[] expected = ram.clone();
        Progress expectedProgress = service.readback(operation);
        Arrays.fill(ram, (byte) 0xff);
        var restored = service.restore(snapshot, ram);
        assertThrows(IllegalStateException.class, () -> service.readback(operation));
        assertEquals(snapshot.progress().rendered(), service.readback(restored).rendered());
        assertArrayEquals(snapshot.wordRamCopy(), ram);
        render(service, service.advance(restored, clock(185), SUB, QUIET, 1), ram);
        render(service, service.advance(restored, clock(269), SUB, QUIET, 1), ram);
        assertArrayEquals(expected, ram, "restored overlap behavior and all caller RAM bytes");
        assertEquals(expectedProgress.rendered(), service.readback(restored).rendered());
        assertEquals(expectedProgress.graphicsClock(), service.graphicsClock());
        assertTrue(service.readback(restored).finalPixelsRendered());
    }

    @Test
    void restoringFinalPendingBatchReplacesTicketsWithoutReplayingCompletionEffects() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(1, 1), clock(0), SUB);
        var step = service.advance(operation, clock(1), SUB, new CompletionSignals(true, true), 1);
        var snapshot = service.capture(operation, ram);
        RenderTicket originalTicket = step.renderTicket().orElseThrow();
        service.render(originalTicket, ram, SUB);
        byte[] expected = ram.clone();

        var restored = service.restore(snapshot, ram);
        assertFalse(service.readback(restored).busy());
        assertFalse(service.readback(restored).finalPixelsRendered());
        assertThrows(IllegalStateException.class, () -> service.readback(operation));
        assertThrows(IllegalStateException.class, () -> service.render(originalTicket, ram, SUB));
        var result = service.render(service.pendingRender(restored).orElseThrow(), ram, SUB);
        assertEquals(List.of(new FinalPixelsRendered(result.progress())), result.effects());
        assertArrayEquals(expected, ram);
        assertThrows(IllegalArgumentException.class, () -> service.restore(snapshot, new byte[1]));
        assertEquals(result.progress(), service.readback(restored));
    }

    @Test
    void rejectsOldBatchesForeignHandlesAndCancelledOperationsWithoutReplayingPixels() {
        var service = new CdStampGraphicsService(0);
        byte[] ram = memory();
        var operation = service.submit(request(1, 2), clock(0), SUB);
        var first = service.advance(operation, clock(1), SUB, QUIET, 1);
        RenderTicket oldTicket = first.renderTicket().orElseThrow();
        service.render(oldTicket, ram, SUB);
        var second = service.advance(operation, clock(85), SUB, QUIET, 1);
        byte[] before = ram.clone();
        assertThrows(IllegalStateException.class, () -> service.render(oldTicket, ram, SUB));
        var foreign = new CdStampGraphicsService(0).submit(request(1, 2), clock(0), SUB);
        assertThrows(IllegalStateException.class, () -> service.readback(foreign));
        assertThrows(IllegalArgumentException.class, () -> service.reset(-1));
        assertEquals(second.progress(), service.readback(operation));
        service.reset(7);
        assertEquals(new CdStampClock(1, 7, 0), service.graphicsClock());
        assertThrows(IllegalStateException.class, () -> service.readback(operation));
        assertThrows(IllegalStateException.class, () -> service.render(second.renderTicket().orElseThrow(), ram, SUB));
        assertArrayEquals(before, ram, "reset cancels work without rolling memory back or rendering it");
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(request(1, 1), new CdStampClock(7, 10), SUB));
        var next = service.submit(request(1, 1), new CdStampClock(1, 7, 10), SUB);
        assertThrows(IllegalArgumentException.class,
                () -> service.advance(next, new CdStampClock(0, 7, 11), SUB, QUIET, 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.rebase(new CdStampClock.Rebase(0, 7, 8, 10)));
        assertThrows(IllegalArgumentException.class, () -> service.reset(1, 7));
        assertNotSame(operation, next);
        assertEquals(0, service.readback(next).rendered().completedLines());
    }

    private static CdStampClock clock(long cycles) { return new CdStampClock(0, cycles); }

    private static CdStampRenderRequest geometry(int width) {
        return new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, NORMAL, MAP, 1, width);
    }

    private static CdStampOperationRequest request(int width, int height) {
        return new CdStampOperationRequest(geometry(width), TRACE, 2L * IMAGE, height);
    }

    private static byte[] memory() {
        byte[] ram = new byte[CdStampGraphics.WORD_RAM_BYTES];
        ram[MAP + 1] = 1;
        Arrays.fill(ram, 128, 256, (byte) 0x77); // Stamp 1, all pixels color 7; zero trace vectors.
        return ram;
    }

    private static int word(byte[] ram, int address) {
        return (Byte.toUnsignedInt(ram[address]) << 8) | Byte.toUnsignedInt(ram[address + 1]);
    }

    private static void render(CdStampGraphicsService service, ServiceStep step, byte[] ram) {
        service.render(step.renderTicket().orElseThrow(), ram, SUB);
    }
}
