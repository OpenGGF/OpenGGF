package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Three actual original-USA World paths through the naturally reached first Platform prefix.
 * J a691574d0c5980679f1ff82029f5a9374560234b; N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Platform/Main.asm:7-13,30-86,541-555; Object Functions.asm:11-33,137-196;
 * Object Spawner.asm:329-340. The full source-derived scanner and Ring/Spring/Platform
 * value oracle never invoke the live body, Solid, animation or Frame to produce expectations.
 * Native Init returns before Main. A later Platform Main/Solid reach is an explicit
 * oracle gap; neither the next unowned slot/ID nor a successful controller row is fitted.
 * Every healthy capture/restore precedes the sole terminal forward attempt. No failed
 * Frame is captured/restored/retried. No camera/SAT/pixel/audio or regional result is claimed.
 */
@Isolated
class TestSonicCdPastPlatformWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualPlatformPrefixMatchesAllNativePoolBankAndPhysicalQueueBytesAtItsDerivedBoundary() throws Exception {
        var p = ready();
        var before = before(p);
        assertPlatformTerminal(p, before, 45L, 8, 0);
    }

    @Test void fullHealthy44RestoreUndoesBankAndPoolContrastBeforeOneActualPlatformPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing")); assertNull(actualField(p.visit(), "activeSpring"));
        assertNull(actualField(p.visit(), "activePlatform"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertPlatformTerminal(p, before, 45L, 8, 0);
    }

    @Test void realStart45Hold46CourseRestoreThenUnpause47RunsTheNativePlatformPrefixOnce() throws Exception {
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
        var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing")); assertNull(actualField(p.visit(), "activeSpring"));
        assertNull(actualField(p.visit(), "activePlatform"));
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertPlatformTerminal(p, before, 47L, 0x80, 0x80);
    }

    private static void assertPlatformTerminal(Prepared p, Before before, long clock, int held, int tap) throws Exception {
        assertFalse(before.prefix().completedPlatformWholes().isEmpty(),
                "The actual source-derived walk must reach Platform; no static row/physical boundary is substituted");
        assertTrue(before.prefix().completedRingWholes().contains(32),
                "The prior native Ring32 remains independently counted");
        assertFalse(before.prefix().completedSpringWholes().isEmpty(),
                "The prior source-derived Spring body remains independently counted");
        assertTrue(before.prefix().completedPlatformWholes().stream().noneMatch(before.prefix().completedRingWholes()::contains));
        assertTrue(before.prefix().completedPlatformWholes().stream().noneMatch(before.prefix().completedSpringWholes()::contains));
        // Retains complete State/Environment/history/fractions, bank766, full96 images,
        // HUD31, source/lifetime, current/plane/copy images, all queue entries and CPU bytes.
        // The admitted native Init path has no player/Solid/angle/FM stores.
        assertTerminal(p, before, clock, held, tap, "PAST_PLATFORM_PREFIX");
    }
}
