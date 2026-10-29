package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Three original-USA actual-World paths through the naturally reached Tunnel Door prefix.
 * J e759814297cc993de2a7bf6ebbeb74c23e8b088d; N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * ObjTunnelDoor:7-20, ChkPlayer:23-50, Init/Main:55-77, ChkCollision:103-129;
 * Object Solid.asm:15-55,174-200,473-479,591-607; Object Spawner.asm:385-446.
 * The detached scanner/native-plane/Ring/Spring/Platform/Door oracle uses the real
 * healthy source values and literal native arithmetic, never production helpers.
 * Main's reached primary callback precedes the actual all64-ZERO secondary Top call;
 * its entry1/GetOff/final0 is distinguished from a skipped call by ordered callback
 * observations. Primary opening may omit Top but cannot skip the secondary check.
 * Every full/course restore is healthy and precedes the sole terminal forward row.
 * Later collision/linked-player/native-stop, successful child/FM and animation paths
 * are hard oracle prerequisites. No next slot/ID or successful7775 is predetermined.
 * No failed Frame is captured/restored/retried; camera/SAT/media/parity are unclaimed.
 */
@Isolated
class TestSonicCdPastTunnelDoorWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualTunnelDoorPrefixMatchesAllNativePoolBankReservedAndOrderedCallbackBytesAtItsDerivedBoundary() throws Exception {
        var p = ready();
        var before = before(p);
        assertTunnelDoorTerminal(p, before, 45L, 8, 0);
    }

    @Test void fullHealthy44RestoreUndoesBankAndPoolContrastBeforeOneActualTunnelDoorPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing")); assertNull(actualField(p.visit(), "activeSpring"));
        assertNull(actualField(p.visit(), "activePlatform")); assertNull(actualField(p.visit(), "activeTunnelDoor"));
        assertSame(before.tunnelDoorBody(), actualField(p.visit(), "tunnelDoor"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTunnelDoorTerminal(p, before, 45L, 8, 0);
    }

    @Test void realStart45Hold46CourseRestoreThenUnpause47RunsTheNativeTunnelDoorPrefixOnce() throws Exception {
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
        assertNull(actualField(p.visit(), "activePlatform")); assertNull(actualField(p.visit(), "activeTunnelDoor"));
        assertSame(before.tunnelDoorBody(), actualField(p.visit(), "tunnelDoor"));
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTunnelDoorTerminal(p, before, 47L, 0x80, 0x80);
    }

    private static void assertTunnelDoorTerminal(Prepared p, Before before, long clock, int held, int tap) throws Exception {
        assertFalse(before.prefix().completedTunnelDoors().isEmpty(),
                "The actual source-derived physical walk must reach Door; no static row or fitted frontier substitutes");
        assertTrue(before.prefix().completedRingWholes().contains(32),
                "The preceding independently modeled Ring32 must remain part of the native physical walk");
        assertFalse(before.prefix().completedSpringWholes().isEmpty());
        assertFalse(before.prefix().completedPlatformWholes().isEmpty());
        // Inherited terminal checks compare the complete observed call suffix to the
        // literal source-derived primary(if reached)/secondary/body order for each Door.
        // They also retain full96/saved766/reserved1984, complete Frame/Environment,
        // history/fractions/source/current/planes/copies, every queue entry and sounds.
        // Final SOLID_TYPE0 alone is deliberately insufficient to prove a Top call.
        assertTerminal(p, before, clock, held, tap, "PAST_TUNNEL_DOOR_PREFIX");
    }
}
