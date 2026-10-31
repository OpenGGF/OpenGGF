package com.openggf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic wall time and tick-indexed input; no sleeping, window, ROM or device. */
class TestFixedSimulationScheduler {
    private static final long SECOND = 1_000_000_000L;

    @ParameterizedTest
    @CsvSource({"50,30", "50,50", "50,60", "50,120", "50,144",
            "60,30", "60,50", "60,60", "60,120", "60,144"})
    void renderingCadenceCannotChangeTickIndexedBehavior(int hz, int fps) {
        var scheduler = new FixedSimulationScheduler(hz, fps, 8, SECOND);
        var state = new TickState();
        var reference = new TickState();
        List<Long> states = new ArrayList<>();
        List<Long> expected = new ArrayList<>();
        for (int tick = 0; tick < 2 * hz; tick++) expected.add(reference.step());
        long previous = 0;
        int pictures = 0;
        int renderOnly = 0;
        int multipleTicks = 0;
        // Absolute rational deadlines, including an exact two-second endpoint.
        for (int picture = 1; picture <= 2 * fps; picture++) {
            long now = (picture * SECOND + fps - 1) / fps;
            var batch = scheduler.advance(now - previous);
            previous = now;
            if (batch.render()) pictures++;
            if (batch.ticks() == 0) renderOnly++;
            if (batch.ticks() > 1) multipleTicks++;
            for (int tick = 0; tick < batch.ticks(); tick++) states.add(state.step());
            assertEquals(0, batch.pendingTicks());
            assertEquals(0, batch.discardedWallNanos());
        }
        assertEquals(expected, states, "all authoritative states, not only final tick count");
        assertEquals(2 * hz, state.tick);
        assertEquals(2 * fps, pictures);
        if (fps > hz) assertTrue(renderOnly > 0);
        if (fps < hz) assertTrue(multipleTicks > 0);
    }

    @ParameterizedTest
    @CsvSource({"50", "60"})
    void jitterAndShortStallsCatchUpWithoutChangingBehavior(int hz) {
        var scheduler = new FixedSimulationScheduler(hz, 144, 4, SECOND);
        var state = new TickState();
        var reference = new TickState();
        List<Long> actual = new ArrayList<>();
        List<Long> expected = new ArrayList<>();
        for (int tick = 0; tick < 2 * hz; tick++) expected.add(reference.step());
        long[] intervals = {1, 7_000_003, 250_000_000, 33_333_333, 900_017, 100_000_000};
        long elapsed = 0;
        int interval = 0;
        int eventPolls = 0;
        boolean caughtUp = false;
        while (elapsed < 2 * SECOND) {
            long delta = Math.min(intervals[interval++ % intervals.length], 2 * SECOND - elapsed);
            elapsed += delta;
            var batch = scheduler.advance(delta);
            do {
                eventPolls++; // Caller regains control between bounded batches.
                assertTrue(batch.ticks() <= 4);
                assertEquals(0, batch.discardedWallNanos());
                for (int tick = 0; tick < batch.ticks(); tick++) actual.add(state.step());
                if (batch.pendingTicks() == 0) break;
                caughtUp = true;
                assertEquals(0, batch.nanosUntilTick());
                batch = scheduler.advance(0);
                assertFalse(batch.render(), "do not render a backlog of missed pictures");
            } while (true);
        }
        assertTrue(caughtUp);
        assertTrue(eventPolls > interval);
        assertEquals(expected, actual);
    }

    @Test
    void fractionalNtscBoundaryAndSleepDeadlineDoNotRoundTheTickPeriod() {
        var scheduler = new FixedSimulationScheduler(60, 144, 4, SECOND);
        var initial = scheduler.advance(0);
        assertEquals(0, initial.ticks());
        assertFalse(initial.render());
        assertEquals(16_666_667, initial.nanosUntilTick());
        assertEquals(6_944_445, initial.nanosUntilRender());
        assertEquals(0, scheduler.advance(16_666_666).ticks());
        var first = scheduler.advance(1);
        assertEquals(1, first.ticks());
        assertEquals(20, first.tickRemainderUnits());
        assertEquals(0, scheduler.advance(16_666_666).ticks());
        var second = scheduler.advance(1);
        assertEquals(1, second.ticks());
        assertEquals(40, second.tickRemainderUnits());
    }

    @ParameterizedTest
    @CsvSource({"50,180000", "60,216000"})
    void anHourOfSyntheticTimeHasNoCumulativeRoundingDrift(int hz, int expectedTicks) {
        var scheduler = new FixedSimulationScheduler(hz, 144, 64, SECOND);
        long ticks = 0;
        for (int second = 0; second < 3_600; second++) {
            var batch = scheduler.advance(SECOND);
            ticks += batch.ticks();
            assertTrue(batch.render());
            assertEquals(0, batch.pendingTicks());
            assertEquals(0, batch.tickRemainderUnits());
            assertEquals(0, batch.discardedWallNanos());
        }
        assertEquals(expectedTicks, ticks);
    }

    @ParameterizedTest
    @CsvSource({"50,46", "60,56"})
    void longStallDiscardsWallDebtButDoesNotInventSkippedTickIdentities(int hz, int pending) {
        var scheduler = new FixedSimulationScheduler(hz, 60, 4, SECOND);
        var state = new TickState();
        var batch = scheduler.advance(5 * SECOND);
        assertEquals(4, batch.ticks());
        assertEquals(pending, batch.pendingTicks());
        assertEquals(4 * SECOND, batch.discardedWallNanos());
        assertTrue(batch.render());
        while (true) {
            for (int tick = 0; tick < batch.ticks(); tick++) state.step();
            if (batch.pendingTicks() == 0) break;
            batch = scheduler.advance(0);
            assertFalse(batch.render());
            assertEquals(0, batch.discardedWallNanos());
        }
        assertEquals(hz, state.tick);
        var next = scheduler.advance(SECOND);
        assertEquals(4, next.ticks());
        for (int tick = 0; tick < next.ticks(); tick++) state.step();
        assertEquals(hz + 4, state.tick, "discarded wall time never advances the state owner");
    }

    @ParameterizedTest
    @CsvSource({"50,1220000000,19", "60,1600000000,24"})
    void sustainedOverloadKeepsBatchesBoundedAndReportsDegradedWallTime(
            int hz, long expectedDiscardedNanos, int expectedDebt) {
        var scheduler = new FixedSimulationScheduler(hz, 144, 6, 500_000_000);
        long discarded = 0;
        long ticks = 0;
        FixedSimulationScheduler.Batch batch = null;
        // Host can execute only 30 ticks/s; its 200 ms work interval is observed next iteration.
        for (int iteration = 0; iteration < 20; iteration++) {
            batch = scheduler.advance(200_000_000);
            assertEquals(6, batch.ticks());
            assertTrue(batch.pendingTicks() <= expectedDebt);
            assertTrue(batch.render());
            discarded += batch.discardedWallNanos();
            ticks += batch.ticks();
        }
        assertEquals(120, ticks);
        assertEquals(expectedDiscardedNanos, discarded);
        assertEquals(expectedDebt, batch.pendingTicks());
        assertEquals(0, batch.nanosUntilTick());
    }

    @Test
    void resetDropsPauseOrRestoreDebtAndReanchorsBothCadences() {
        var scheduler = new FixedSimulationScheduler(60, 144, 2, SECOND);
        assertTrue(scheduler.advance(500_000_000).pendingTicks() > 0);
        scheduler.reset(); // Caller excludes paused wall time and resets its monotonic anchor.
        var resumed = scheduler.advance(0);
        assertEquals(0, resumed.ticks());
        assertEquals(0, resumed.pendingTicks());
        assertEquals(0, resumed.tickRemainderUnits());
        assertFalse(resumed.render());
        assertEquals(16_666_667, resumed.nanosUntilTick());
        assertEquals(6_944_445, resumed.nanosUntilRender());
        assertEquals(1, scheduler.advance(16_666_667).ticks());
    }

    @Test
    void hugeElapsedTimeCannotOverflowAndStillAllowsOnlyOnePicture() {
        var scheduler = new FixedSimulationScheduler(60, 144, 4, SECOND);
        var batch = scheduler.advance(Long.MAX_VALUE);
        assertEquals(4, batch.ticks());
        assertEquals(56, batch.pendingTicks());
        assertEquals(Long.MAX_VALUE - SECOND, batch.discardedWallNanos());
        assertTrue(batch.render());
        assertFalse(scheduler.advance(0).render());
    }

    @Test
    void tickAndRenderDeadlinesAreReportedSeparately() {
        var scheduler = new FixedSimulationScheduler(60, 30, 4, SECOND);
        var first = scheduler.advance(16_666_667);
        assertEquals(1, first.ticks());
        assertFalse(first.render(), "a 30 FPS gate does not open on the first 60 Hz tick");
        assertEquals(16_666_667, first.nanosUntilTick());
        assertEquals(16_666_667, first.nanosUntilRender());
        var second = scheduler.advance(16_666_667);
        assertEquals(1, second.ticks());
        assertTrue(second.render());
        assertEquals(16_666_666, second.nanosUntilTick());
        assertEquals(33_333_333, second.nanosUntilRender(), "the render phase keeps its own deadline");
        var debt = scheduler.advance(100_000_000);
        assertEquals(4, debt.ticks());
        assertEquals(2, debt.pendingTicks());
        assertEquals(0, debt.nanosUntilTick(), "pending services wake immediately");
        assertTrue(debt.nanosUntilRender() > 0, "debt never pulls the render deadline forward");
    }

    @Test
    void rejectedClockDiscontinuityDoesNotMutateOutstandingDebt() {
        var scheduler = new FixedSimulationScheduler(60, 144, 4, SECOND);
        scheduler.advance(1);
        assertThrows(IllegalArgumentException.class, () -> scheduler.advance(-1));
        assertEquals(1, scheduler.advance(16_666_666).ticks());
    }

    @Test
    void invalidPoliciesAreRejectedBeforeScheduling() {
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(0, 60, 4, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(59, 60, 4, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(60, 0, 4, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(60, 1_000_000_001, 4, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(60, 60, 0, SECOND));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(50, 60, 4, 19_999_999));
        assertThrows(IllegalArgumentException.class, () -> new FixedSimulationScheduler(60, 60, 4, Long.MAX_VALUE));
    }

    /** Stateful integer recurrence fed solely by tick-indexed held input and isolated press edges. */
    private static final class TickState {
        int tick;
        long position;
        long velocity;
        long hash;

        long step() {
            boolean held = tick % 17 < 9;
            boolean pressed = tick % 23 == 3;
            velocity += held ? 2 : -1;
            if (pressed) velocity = -velocity + 7;
            position += velocity;
            hash = hash * 31 + position * 17 + velocity + ++tick;
            return hash;
        }
    }
}
