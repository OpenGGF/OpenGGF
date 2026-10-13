package com.openggf.level;

import com.openggf.game.rewind.snapshot.ParallaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestParallaxRewindSnapshot {

    private static final class StatefulHandler extends com.openggf.level.scroll.AbstractZoneScrollHandler
            implements com.openggf.level.scroll.ScrollOutputRebuilder {
        int ticks, rebuilds;
        @Override public void update(int[] output, int x, int y, int frame, int act) {
            ticks++;
            java.util.Arrays.fill(output, ticks);
        }
        @Override public void rebuildOutput(int[] output, int x, int y, int frame, int act) {
            rebuilds++;
            java.util.Arrays.fill(output, ticks);
        }
    }

    @Test void initialReconciliationDoesNotConsumeTheFirstNativeScrollTick() {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        var camera = new com.openggf.camera.Camera();
        var saved = pm.capture();
        pm.restore(saved); // No handler has been initialized or dispatched yet.
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(0, handler.ticks);
        assertEquals(1, handler.rebuilds);
        assertEquals(0, pm.getHScroll()[0]);
        pm.update(37, 0, camera, 0);
        assertEquals(1, handler.ticks);
        assertEquals(1, pm.getHScroll()[0]);
    }

    @Test void initializedReconciliationUsesLogicalStateWithoutAnotherDispatch() {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 0, camera, 0);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(1, handler.ticks);
        assertEquals(2, handler.rebuilds);
        assertEquals(1, pm.getHScroll()[0]);
    }

    @Test
    void roundTripTreatsParallaxAsDerivedState() {
        ParallaxManager pm = new ParallaxManager();
        ParallaxSnapshot initial = pm.capture();
        ParallaxSnapshot modified = new ParallaxSnapshot(
                7, -3,
                0x1234, 256,
                false, false, false,
                0, 0,
                (short) 0, (short) 0
        );
        pm.restore(modified);
        ParallaxSnapshot snap = pm.capture();
        assertEquals(initial, snap,
                "parallax snapshots are compact markers; dense/scalar state is recomputed after registry restore");
    }

    @Test
    void keyIsParallax() {
        assertEquals("parallax", new ParallaxManager().key());
    }

    @Test
    void legacyDenseConstructorDoesNotRetainArrays() {
        ParallaxManager pm = new ParallaxManager();
        ParallaxSnapshot snap = pm.capture();
        int[] newScroll = new int[224];
        java.util.Arrays.fill(newScroll, 999);
        ParallaxSnapshot other = new ParallaxSnapshot(
                0, 0, Integer.MIN_VALUE, 512,
                newScroll, new short[224], new short[20], new short[20],
                false, false, false, 0, 0, (short) 0, (short) 0
        );
        pm.restore(other);
        assertEquals(snap, pm.capture());
    }

    @Test
    void snapshotDoesNotRetainDenseScrollBuffers() {
        java.util.Set<String> components = java.util.Arrays.stream(ParallaxSnapshot.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(components.contains("hScroll"));
        assertFalse(components.contains("vScrollPerLineBG"));
        assertFalse(components.contains("vScrollPerColumnBG"));
        assertFalse(components.contains("vScrollPerColumnFG"));
    }
}
