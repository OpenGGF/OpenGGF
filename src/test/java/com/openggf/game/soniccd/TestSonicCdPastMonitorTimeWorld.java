package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA first ID19 closure on the unchanged controller recipe.
 * N1ecc5f3a Monitor and Time Post/Main.asm:75-173,196-236,269-375;
 * Object Spawner.asm:385-446; Object Functions.asm:137-196,228-237.
 * The shared detached scanner reads each current raw64 image in increasing physical
 * order. No subtype/slot/row/next-count is fitted to an observed production result.
 * All capture/restore occurs on healthy cuts before the single terminal forward call.
 * Source-authored only: these declarations carry no execution or parity claim.
 */
@Isolated
class TestSonicCdPastMonitorTimeWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualMonitorTimePrefixMatchesAllNativePoolBankAndPhysicalQueueBytesAtItsDerivedBoundary() throws Exception {
        var p = ready(); var before = monitorTimeBefore(p);
        assertTerminal(p, before, 45L, 8, 0, "PAST_MONITOR_TIME_PREFIX");
    }

    @Test void fullHealthy44RestoreUndoesContrastBeforeOneActualMonitorTimePrefix() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = monitorTimeBefore(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p); assertNoReservedPass(p);
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 45L, 8, 0, "PAST_MONITOR_TIME_FULL_RESTORE_PREFIX");
    }

    @Test void realStart45Hold46CourseRestoreThenUnpause47RunsTheNativeMonitorTimePrefixOnce() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var actor = frame.captureActor(); var initialReserved = reserved(p.visit());
        step(h, start(true));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        step(h, start(false));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        var registry = h.mode().getRewindRegistry(); var course = registry.captureCourse();
        var nativeBefore = captureNative(h); var before = monitorTimeBefore(p);
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertUnscanned(p); assertNoReservedPass(p);
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 47L, 0x80, 0x80, "PAST_MONITOR_TIME_COURSE_RESTORE_PREFIX");
    }

    private static Before monitorTimeBefore(Prepared p) throws Exception {
        assertMonitorScopeRetired(p);
        var before = before(p);
        assertFalse(before.prefix().completedMonitorTimes().isEmpty(),
                "The independent scanner/current-image walk must actually reach ID19; no fitted physical slot");
        return before;
    }
}
