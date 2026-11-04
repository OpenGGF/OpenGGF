package com.openggf.game.soniccd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ordinary, unchanged five-jump controller route, measured4November2026.
 * Full/course8083->8084 compare complete represented native state BEFORE forwarding.
 * Source runObjects' increasing32..127 walk and retained images identify Mosqui51;
 * the public failure itself exposes only DYNAMIC_OBJECTS, not a typed ID/slot.
 * Native comparisons never seed an actor, camera, object, saved flag or controller.
 * No framebuffer, natural8085 return, audible output or regional-parity claim.
 */
@Isolated
class TestSonicCdPastControllerRoute {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void unchangedControllerReturns8084ReplaysAndRetainsTheNextMosquiPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        boolean naturalCollapse = false;
        for (int call = p.returned() + 1; call <= 8083; call++) {
            step(h, route(call)); healthy(p, world, generation);
            naturalCollapse |= h.module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.COLLAPSE_PLATFORM_BODY);
        }
        assertTrue(naturalCollapse, "Actual layout Collapse runs on completed ordinary rows");
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, route(8084)); healthy(p, world, generation); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h);
        step(h, route(8084)); healthy(p, world, generation); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        step(h, route(8084)); healthy(p, world, generation); assertNativeCapture(after, h);
        // Comparison-only observations from the actual completed8084 cut, not production inputs.
        assertEquals(503679488, frame.state().motion().centreX16_16());
        assertEquals(53667328, frame.state().motion().centreY16_16());
        assertEquals(310, frame.environment().globals().levelFrames());
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(h.module(), "playerPresentation"));
        assertEquals(7557, presentation.pastForegroundImage().cameraX());
        assertEquals(690, presentation.pastForegroundImage().cameraYWord());

        var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, route(8085)));
        assertEquals(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS, stopped.boundary());
        assertTrue(frame.closed()); assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        // Retained references only after failure: no guarded require/capture/restore or replay.
        var pool = p.placement().pool();
        var reached = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertEquals(2, Collections.frequency(reached, SonicCdGameModule.PastLoadCall.COLLAPSE_PLATFORM_BODY));
        assertEquals(1, Collections.frequency(reached, SonicCdGameModule.PastLoadCall.BOULDER_BODY));
        assertFalse(reached.contains(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
        assertFalse(reached.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(0x20, pool.state(32).id()); assertEquals(2, pool.state(32).routine());
        assertEquals(0x20C4A8, pool.state(32).longBits(MAPPINGS)); assertEquals(0x44BE, pool.state(32).word(TILE));
        assertEquals(0x1B, pool.state(33).id()); assertEquals(2, pool.state(33).routine());
        assertEquals(0x2094AC, pool.state(33).longBits(MAPPINGS)); assertEquals(0x346, pool.state(33).word(TILE));
        var supported = Set.of(0, 0x0A, 0x0D, 0x10, 0x12, 0x19, 0x1B, 0x20, 0x21, 0x22, 0x24, 0x3E);
        for (int whole = 32; whole < 51; whole++) assertTrue(supported.contains(pool.state(whole).id()), "Earlier physical occupant" + whole);
        byte[] expected = new byte[64]; expected[0] = 0x3D; expected[0x23] = (byte) 0xB7;
        ByteBuffer.wrap(expected).putShort(8, (short) 8115).putShort(12, (short) 856);
        assertArrayEquals(expected, pool.state(51).copyBytes(), "Fresh native placement remains unvisited at the first unowned ID");
        assertEquals(7691, frame.state().motion().centreX16_16() >>> 16);
        assertEquals(822, frame.state().motion().centreY16_16() >>> 16);
        assertEquals(7552, p.placement().status().previousChunkWord());
        assertEquals(407, p.placement().status().rightRecordIndex());
    }

    private static void healthy(Prepared p, Object world, long generation) {
        assertFalse(p.frame().closed()); assertSame(p.frame(), p.harness().module().requirePlayerFrame());
        assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }
}
