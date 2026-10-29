package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Three actual original-USA World paths through the naturally reached first Spring prefix.
 * J60ff901e7684cd07a550e3297dab7c366e82a738, native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Spring/Main.asm:130-244; Object Functions.asm:11-33,137-196;
 * Object Tile ID Set.asm:7-17; Object Tile IDs (Act 1 Past).asm:7-44.
 * The shared detached scanner/Ring/Spring model reads the full real placements and carried bank.
 * No placement row, child count, future ID or physical boundary is fitted here. The increasing live
 * walk derives every completed body and the first unowned physical slot before actual forwarding.
 * A reached later Spring control/mirror path stops as an explicit source-model gap.
 * Healthy capture/restore precedes the sole failing attempt; terminal diagnostics retain references.
 * No completed7775, camera continuation, SAT/pixel/audio or whole-game parity is claimed.
 */
@Isolated
class TestSonicCdPastSpringWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualSpringPrefixMatchesAllNativePoolBankAndPhysicalQueueBytesAtItsDerivedBoundary() throws Exception {
        var p = ready();
        var before = before(p);
        assertSpringTerminal(p, before, 45L, 8, 0);
    }

    @Test void fullHealthy44RestoreUndoesBankAndPoolContrastBeforeOneActualSpringPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing")); assertNull(actualField(p.visit(), "activeSpring"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertSpringTerminal(p, before, 45L, 8, 0);
    }

    @Test void realStart45Hold46CourseRestoreThenUnpause47RunsTheNativeSpringPrefixOnce() throws Exception {
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
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertSpringTerminal(p, before, 47L, 0x80, 0x80);
    }

    private static void assertSpringTerminal(Prepared p, Before before, long clock, int held, int tap) throws Exception {
        assertFalse(before.prefix().completedSpringWholes().isEmpty(), "The real source-derived walk must reach Spring");
        assertTrue(before.prefix().completedRingWholes().contains(32), "The prior native Ring32 remains independently counted");
        assertTrue(before.prefix().completedSpringWholes().stream().noneMatch(before.prefix().completedRingWholes()::contains));
        // The shared terminal checker compares full State/Environment/history/fractions, all766 bank
        // bytes, all96 complete64-byte images, all31 HUD images, every queue pointer/unused entry,
        // complete CPU/current/plane/scroll/copy images and source/lifetime/camera nonwrites.
        // Reached ordinary Spring Init/display-only wrappers own no player/Solid/FM stores.
        assertTerminal(p, before, clock, held, tap, "PAST_SPRING_PREFIX");
    }
}
