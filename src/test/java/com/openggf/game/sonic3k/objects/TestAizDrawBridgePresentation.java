package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestAizDrawBridgePresentation {
    @AfterEach void reset() { Aiz2BossEndSequenceState.reset(); }

    @Test void flatBridgeContainsOnlyTheTwelveNativeChildLogs() throws Exception {
        var bridge = AizDrawBridgeObjectInstance.createCutsceneOverride();
        int[] x = (int[]) field(bridge, "pieceX");
        int[] y = (int[]) field(bridge, "pieceY");
        assertEquals(12, x.length);
        for (int i = 0; i < 12; i++) {
            assertEquals(0x4B48 - 16 * (i + 1), x[i]);
            assertEquals(0x218, y[i]);
        }
        assertTrue(Arrays.stream(x).noneMatch(v -> v == 0x4B48 || v == 0x4B48 - 208),
                "Neither extra endpoint may paint over the cliff");
    }

    @Test void collapseAllocatesOneDelayedPuffForEachNativeLog() throws Exception {
        var services = new TestObjectServices().withIsolatedObjectManager();
        var bridge = AizDrawBridgeObjectInstance.createCutsceneOverride();
        bridge.setServices(services);
        Aiz2BossEndSequenceState.pressButton();
        bridge.update(0, null);
        var children = services.objectManager().getActiveObjects();
        assertEquals(12, children.stream().filter(o -> o.getClass().getSimpleName().equals("FallingBridgeSegment")).count());
        var puffs = children.stream().filter(o -> o.getClass().getSimpleName().equals("BridgeExplosion")).toList();
        assertEquals(12, puffs.size());
        int[] delays = {8,16,12,14,6,10,4,2,8,16,12,14};
        for (int i = 0; i < puffs.size(); i++) {
            assertEquals(delays[i], field(puffs.get(i), "delay"));
            assertEquals(0x4B48 - 16 * (i + 1), puffs.get(i).getX());
            assertEquals(2, puffs.get(i).getPriorityBucket());
            assertTrue(puffs.get(i).isHighPriority());
        }
    }

    @Test void puffWaitsForSignedUnderflowThenShowsFiveFramesAndExpires() throws Exception {
        var puff = new AizDrawBridgeObjectInstance.BridgeExplosion(100, 200, 2);
        for (int entry = 0; entry < 2; entry++) {
            puff.update(entry, null);
            assertEquals(false, field(puff, "active"));
        }
        for (int visibleEntry = 0; visibleEntry < 35; visibleEntry++) {
            puff.update(visibleEntry + 2, null);
            assertEquals(true, field(puff, "active"));
            int expectedFrame = visibleEntry < 3 ? 0 : 1 + (visibleEntry - 3) / 8;
            assertEquals(expectedFrame, field(puff, "mappingFrame"));
            assertFalse(puff.isDestroyed());
            assertEquals(100, puff.getX());
            assertEquals(200, puff.getY());
        }
        puff.update(37, null);
        assertTrue(puff.isDestroyed());
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
}
