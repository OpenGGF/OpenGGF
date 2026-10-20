package com.openggf.level;

import com.openggf.game.rewind.snapshot.ParallaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestParallaxRewindSnapshot {

    private static final class StatefulHandler extends com.openggf.level.scroll.AbstractZoneScrollHandler
            implements com.openggf.level.scroll.ScrollOutputRebuilder {
        private record LogicalState(int ticks) { }
        int ticks, rebuilds, initializations, updates;
        @Override public void init(int act, int x, int y) {
            initializations++;
            ticks = 0;
        }
        @Override public void update(int[] output, int x, int y, int frame, int act) {
            updates++;
            ticks++;
            java.util.Arrays.fill(output, ticks);
        }
        @Override public Object captureRewindState() { return new LogicalState(ticks); }
        @Override public void restoreRewindState(Object state) {
            if (state instanceof LogicalState saved) ticks = saved.ticks();
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
        assertEquals(0, handler.initializations);
        assertEquals(1, handler.rebuilds);
        assertEquals(0, pm.getHScroll()[0]);
        pm.update(37, 0, camera, 0);
        assertEquals(1, handler.ticks);
        assertEquals(1, handler.initializations);
        assertEquals(1, pm.getHScroll()[0]);
    }

    @Test
    void preInitLogicalStateAndCursorSurviveLaterInitializationAndReconciliation() {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        handler.ticks = 17; // Explicit pre-init logical state, not a null-marker surrogate.
        var camera = new com.openggf.camera.Camera();
        var before = pm.capture();
        pm.update(37, 0, camera, 0);
        pm.update(37, 0, camera, 1);
        assertEquals(1, handler.initializations);
        assertEquals(2, handler.updates);

        for (int cycle = 0; cycle < 2; cycle++) {
            pm.restore(before);
            pm.rebuildDerivedOutput(37, 0, camera, 0, null);
            assertEquals(before, pm.capture(), "restore/rebuild must retain the pending init cursor and data");
            assertEquals(17, pm.getHScroll()[0]);
            assertEquals(1 + cycle, handler.initializations, "reconciliation must not initialize");
            assertEquals(2 + cycle, handler.updates, "the output rebuilder is not a native update");
            pm.update(37, 0, camera, 0);
            assertEquals(2 + cycle, handler.initializations, "the next ordinary pass owns first init again");
            assertEquals(1, handler.ticks);
        }
    }

    @Test
    void initializedCursorAndLogicalStateSurviveAReinitializedFutureWithoutAnotherInit() {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 0, camera, 0);
        var before = pm.capture();
        pm.resetZoneState();
        pm.update(37, 0, camera, 1);
        pm.update(37, 0, camera, 2);
        assertEquals(2, handler.initializations);
        assertEquals(3, handler.updates);

        pm.restore(before);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(before, pm.capture());
        assertEquals(1, handler.ticks);
        assertEquals(2, handler.initializations);
        assertEquals(3, handler.updates);
        pm.update(37, 0, camera, 1);
        assertEquals(2, handler.initializations, "an initialized restore must not erase captured state via init");
        assertEquals(2, handler.ticks);
    }

    @Test
    void injectedLevelOwnerSelectsTheLoadedHandlerBeforeFirstInit() throws Exception {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        var provider = mock(com.openggf.game.ScrollHandlerProvider.class);
        when(provider.getHandler(37)).thenReturn(handler);
        org.mockito.Mockito.doAnswer(call -> {
            handler.init(call.getArgument(1), call.getArgument(2), call.getArgument(3));
            return null;
        }).when(provider).initForZone(37, 2, 0, 0);
        setProvider(pm, provider);
        var owner = mock(LevelManager.class);
        owner.parallaxManager = pm;
        when(owner.getCurrentLevel()).thenReturn(mock(Level.class));
        when(owner.getCurrentZone()).thenReturn(37);
        when(owner.getCurrentAct()).thenReturn(2);
        bindOwner(pm, owner); // Declared private binding inlet; production load invokes this same guard.
        handler.ticks = 23;
        var before = pm.capture();
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 2, camera, 0);
        assertEquals(1, handler.ticks);
        pm.restore(before);
        pm.rebuildDerivedOutput(37, 2, camera, 0, null);
        assertEquals(before, pm.capture());
        assertEquals(23, handler.ticks);
        assertEquals(1, handler.initializations);
        assertEquals(1, handler.updates);
        pm.update(37, 2, camera, 0);
        assertEquals(2, handler.initializations);
        assertEquals(1, handler.ticks);
    }

    @Test
    void foreignLevelCannotReplaceAnExplicitContributedSelection() throws Exception {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        var foreign = mock(LevelManager.class);
        foreign.parallaxManager = new ParallaxManager();
        when(foreign.getCurrentLevel()).thenReturn(mock(Level.class));
        when(foreign.getCurrentZone()).thenReturn(99);
        when(foreign.getCurrentAct()).thenReturn(3);
        bindOwner(pm, foreign);
        handler.ticks = 29;
        var before = pm.capture();
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 0, camera, 0);
        pm.restore(before);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(before, pm.capture());
        assertEquals(29, handler.ticks);
        assertEquals(1, handler.initializations);
        assertEquals(1, handler.updates);
    }

    @Test
    void legacyMarkerDoesNotReinitializeAnAlreadyInitializedHandler() {
        var pm = new ParallaxManager();
        var handler = new StatefulHandler();
        pm.installContributedHandler(37, handler);
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 0, camera, 0);
        pm.restore(new ParallaxSnapshot());
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(1, handler.ticks);
        pm.update(37, 0, camera, 1);
        assertEquals(1, handler.initializations);
        assertEquals(2, handler.ticks);
    }

    @Test
    void flatContributedReconciliationRetainsItsPendingCursor() {
        var pm = new ParallaxManager();
        pm.installContributedHandler(37, null);
        var before = pm.capture();
        var camera = new com.openggf.camera.Camera();
        pm.update(37, 0, camera, 0);
        pm.restore(before);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(before, pm.capture());
        assertEquals(0, pm.getHScroll()[0]);
    }

    @Test
    void statelessFallbackRebuildDoesNotConsumeItsPendingInitialization() {
        var pm = new ParallaxManager();
        int[] initializations = {0};
        var handler = new com.openggf.level.scroll.AbstractZoneScrollHandler() {
            @Override public void init(int act, int x, int y) { initializations[0]++; }
            @Override public void update(int[] output, int x, int y, int frame, int act) {
                java.util.Arrays.fill(output, x);
            }
        };
        pm.installContributedHandler(37, handler);
        var camera = new com.openggf.camera.Camera();
        camera.setX((short) 123);
        var before = pm.capture();
        pm.update(37, 0, camera, 0);
        assertEquals(1, initializations[0]);
        pm.restore(before);
        pm.rebuildDerivedOutput(37, 0, camera, 0, null);
        assertEquals(before, pm.capture());
        assertEquals(123, pm.getHScroll()[0]);
        assertEquals(1, initializations[0]);
        pm.update(37, 0, camera, 0);
        assertEquals(2, initializations[0]);
    }

    @Test
    void logicalFallbackWaitsForItsFirstNativeDispatchDuringColdReconciliation() {
        var pm = new ParallaxManager();
        class LogicalHandler extends com.openggf.level.scroll.AbstractZoneScrollHandler {
            int logical = 17, initializations, updates;
            @Override public void init(int act, int x, int y) { initializations++; logical = 0; }
            @Override public void update(int[] output, int x, int y, int frame, int act) {
                updates++;
                vscrollFactorBG = 77; // Publish a nonzero future scene value before each restore.
                java.util.Arrays.fill(output, ++logical);
            }
            @Override public Object captureRewindState() { return logical; }
            @Override public void restoreRewindState(Object state) { logical = (Integer) state; }
        }
        var handler = new LogicalHandler();
        pm.installContributedHandler(37, handler);
        var before = pm.capture();
        var camera = new com.openggf.camera.Camera();
        for (int cycle = 0; cycle < 2; cycle++) {
            pm.update(37, 0, camera, cycle);
            assertEquals(77, pm.getVscrollFactorBG(), "the future scene must really publish nonzero BG");
            assertEquals(1 + cycle, handler.initializations);
            assertEquals(1 + cycle, handler.updates);
            pm.restore(before);
            for (int rebuild = 0; rebuild < 2; rebuild++) {
                pm.rebuildDerivedOutput(37, 0, camera, 0, null);
                assertEquals(before, pm.capture());
                assertEquals(17, handler.logical);
                assertEquals(0, pm.getHScroll()[0]);
                assertEquals(0, pm.getVscrollFactorBG(), "cold-flat output must not retain future BG");
                assertEquals(1 + cycle, handler.initializations);
                assertEquals(1 + cycle, handler.updates,
                        "cold reconciliation must not call the native fallback update");
            }
        }
        pm.update(37, 0, camera, 0);
        assertEquals(3, handler.initializations);
        assertEquals(3, handler.updates);
        assertEquals(1, handler.logical);
    }

    private static void setProvider(ParallaxManager pm, com.openggf.game.ScrollHandlerProvider provider)
            throws Exception {
        var field = ParallaxManager.class.getDeclaredField("scrollProvider");
        field.setAccessible(true);
        field.set(pm, provider);
    }

    private static void bindOwner(ParallaxManager pm, LevelManager candidate) throws Exception {
        var method = ParallaxManager.class.getDeclaredMethod("bindLoadedLevelOwner", LevelManager.class);
        method.setAccessible(true);
        method.invoke(pm, candidate);
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
