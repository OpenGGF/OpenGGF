package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.ByteBuffer;
import java.util.List;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ordinary unchanged five-jump controller route, measured4November2026.
 * Full/course8083->8084,8084->8085 and8275->8276 compare complete represented
 * native state BEFORE forwarding. Increasing32..127 dispatch identifies the
 * unvisited Robot Generator32; the public failure exposes only DYNAMIC_OBJECTS.
 * Native comparison values never seed gameplay. No framebuffer/audio/parity claim.
 */
@Isolated
class TestSonicCdPastControllerRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void unchangedControllerReturns8276ReplaysAndRetainsTheRobotGeneratorPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        boolean naturalCollapse = false;
        for (int call = p.returned() + 1; call <= 8083; call++) {
            step(h, route(call)); healthy(p, world, generation);
            naturalCollapse |= h.module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.COLLAPSE_PLATFORM_BODY);
        }
        assertTrue(naturalCollapse, "Actual layout Collapse runs on completed ordinary rows");
        replay(p, 8084, world, generation);
        // Comparison-only observations from the actual completed8084 cut, not production inputs.
        assertEquals(503679488, frame.state().motion().centreX16_16());
        assertEquals(53667328, frame.state().motion().centreY16_16());
        assertEquals(310, frame.environment().globals().levelFrames());
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(h.module(), "playerPresentation"));
        assertEquals(7557, presentation.pastForegroundImage().cameraX());
        assertEquals(690, presentation.pastForegroundImage().cameraYWord());

        replay(p, 8085, world, generation);
        assertTrue(h.module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.MOSQUI_BODY),
                "The retired8085 fence must now complete the actual cached Mosqui");
        var pool = p.placement().pool();
        assertEquals(0x20, pool.state(32).id()); assertEquals(2, pool.state(32).routine());
        assertEquals(0x20C4A8, pool.state(32).longBits(MAPPINGS)); assertEquals(0x44BE, pool.state(32).word(TILE));
        assertEquals(0x1B, pool.state(33).id()); assertEquals(2, pool.state(33).routine());
        assertEquals(0x2094AC, pool.state(33).longBits(MAPPINGS)); assertEquals(0x346, pool.state(33).word(TILE));
        assertEquals(0x3D, pool.state(51).id()); assertEquals(2, pool.state(51).routine());
        for (int call = 8086; call <= 8275; call++) {
            step(h, route(call)); healthy(p, world, generation);
        }
        replay(p, 8276, world, generation);
        assertEquals(560168960, frame.state().motion().centreX16_16());
        assertEquals(63095808, frame.state().motion().centreY16_16());
        assertEquals(502, frame.environment().globals().levelFrames());
        assertEquals(8451, presentation.pastForegroundImage().cameraX());
        assertEquals(784, presentation.pastForegroundImage().cameraYWord());
        assertEquals(8320, p.placement().status().previousChunkWord());
        assertEquals(467, p.placement().status().rightRecordIndex());
        assertArrayEquals(new byte[64], pool.state(32).copyBytes());
        var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, route(8277)));
        assertEquals(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS, stopped.boundary());
        assertTrue(frame.closed()); assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        // Retained references only after failure: no guarded require/capture/restore or replay.
        var reached = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertTrue(reached.contains(SonicCdGameModule.PastLoadCall.PRIMARY_MAIN));
        assertTrue(reached.contains(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertFalse(reached.contains(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
        assertFalse(reached.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        byte[] expected = new byte[64]; expected[0] = 0x2D; expected[0x23] = (byte) 0xDB;
        ByteBuffer.wrap(expected).putShort(8, (short) 9064).putShort(12, (short) 158);
        assertArrayEquals(expected, pool.state(32).copyBytes(),
                "First physical dynamic occupant is the fresh unvisited native Robot Generator");
        assertEquals(560559104, frame.state().motion().centreX16_16());
        assertEquals(62991872, frame.state().motion().centreY16_16());
        assertEquals(8448, p.placement().status().previousChunkWord());
        assertEquals(471, p.placement().status().rightRecordIndex());
    }

    private static void replay(Prepared p, int call, Object world, long generation) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, route(call)); healthy(p, world, generation); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h);
        step(h, route(call)); healthy(p, world, generation); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        step(h, route(call)); healthy(p, world, generation); assertNativeCapture(after, h);
    }

    private static void healthy(Prepared p, Object world, long generation) {
        assertFalse(p.frame().closed()); assertSame(p.frame(), p.harness().module().requirePlayerFrame());
        assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }
}
