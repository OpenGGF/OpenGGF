package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayActivationPolicy {
    @Test void processOwnersWithoutASpatialSpawnKeepTheirNativeLifetime() {
        var policy = new StageplayActivationPolicy();
        var process = mock(ObjectInstance.class, CALLS_REAL_METHODS);
        assertFalse(policy.keeps(process));
        policy.setWindows(List.of(new StageplayActivationPolicy.Window(0,0,320,224)));
        assertFalse(policy.keeps(process));
        verify(process, never()).getX();
        verify(process, never()).getY();
    }

    @Test void disjointWindowsDoNotActivateTheGapAndRestoreWithoutAliasing() {
        var policy = new StageplayActivationPolicy();
        var windows = new java.util.ArrayList<>(List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224),
                new StageplayActivationPolicy.Window(6000, 0, 320, 224)));
        policy.setWindows(windows); windows.clear();
        assertTrue(policy.loads(spawn(2100, 1)));
        assertTrue(policy.loads(spawn(6100, 2)));
        assertFalse(policy.loads(spawn(4100, 3)));
        policy.considered(java.util.Set.of(1));
        var saved = policy.rewindAdapter().capture();
        policy.setWindows(List.of()); assertFalse(policy.enabled());
        policy.rewindAdapter().restore(saved);
        assertTrue(policy.loads(spawn(6100, 2)));
        assertFalse(policy.entered(1));
        assertTrue(policy.entered(2));
        assertThrows(UnsupportedOperationException.class, () -> saved.windows().clear());
        policy.retire(); assertFalse(policy.enabled());
    }

    @Test void supplementalPlacementsUseNativeRoutinesAndSharedSlotsWithoutMovingTheCamera() {
        var f = new Fixture(true, List.of(spawn(2100, 1), spawn(4100, 2), spawn(6100, 3)));
        f.policy.setWindows(List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224),
                new StageplayActivationPolicy.Window(6000, 0, 320, 224)));
        f.objects.update(0, null, List.of(), 0, false);
        var active = f.objects.getActiveObjects().stream().map(o -> (NativeObject) o).toList();
        assertEquals(List.of(2100, 6100), active.stream().map(NativeObject::getX).sorted().toList());
        assertTrue(active.stream().allMatch(o -> o.updates == 1));
        assertEquals(2, active.stream().map(NativeObject::getSlotIndex).distinct().count());
        verify(f.camera, never()).setX(anyShort());
        active.getFirst().cull();
        assertFalse(active.getFirst().isDestroyed(), "A geometric cull includes the supplemental window");
        active.getFirst().setDestroyed(true);
        assertTrue(active.getFirst().isDestroyed(), "Native damage/destruction is never suppressed");
        f.policy.setWindows(List.of());
        active.getLast().setDestroyedByOffscreen();
        assertTrue(active.getLast().isDestroyed());
    }

    @Test void ordinaryGameplayDoesNotLoadSupplementalObjectsOrSuppressNativeRetirement() {
        var f = new Fixture(false, List.of(spawn(2100, 1)));
        f.objects.update(0, null, List.of(), 0, false);
        assertTrue(f.objects.getActiveObjects().isEmpty());
        var object = f.objects.createDynamicObject(() -> new NativeObject(spawn(100, -1)));
        object.setDestroyedByOffscreen();
        assertTrue(object.isDestroyed());
        assertNull(f.objects.stageplayActivation);
    }

    @Test void clearingStillFiltersSupplementalAdmissionAndBudgetFailureIsActionable() {
        var removed = new Fixture(true, List.of(spawn(2100, 1)));
        removed.policy.setWindows(List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224)));
        ObjectDirectionAccess.forManager(removed.objects).clearObjects();
        removed.objects.update(0, null, List.of(), 0, false);
        assertTrue(removed.objects.getActiveObjects().isEmpty());

        var many = java.util.stream.IntStream.range(0, 256).mapToObj(i -> spawn(2100, i)).toList();
        var full = new Fixture(true, many);
        full.policy.setWindows(List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224)));
        var failure = assertThrows(IllegalStateException.class,
                () -> full.objects.update(0, null, List.of(), 0, false));
        assertTrue(failure.getMessage().contains("native object slot budget"));
        assertTrue(failure.getMessage().contains("clear objects"));
    }

    @Test void supplementalAdmissionsInvalidateAWarmedPopulationAndRespectNativeOnscreenRetirement() {
        var f = new Fixture(true, List.of(spawn(2100, 1)));
        f.objects.update(0, null, List.of(), 0, false);
        assertTrue(f.objects.getActiveObjects().isEmpty());
        f.objects.drawLowPriority();
        f.policy.setWindows(List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224)));
        f.objects.update(0, null, List.of(), 0, false);
        assertEquals(1, f.objects.getActiveObjects().size());
        var admitted = (NativeObject) f.objects.getActiveObjects().iterator().next();
        f.objects.drawLowPriority();
        assertEquals(1, admitted.draws, "A warmed empty draw bucket must be invalidated immediately");
        admitted.setDestroyedByOffscreen();
        assertTrue(admitted.isDestroyed(), "Semantic respawnable retirement is never intercepted, even remotely");

        f.policy.setWindows(List.of(new StageplayActivationPolicy.Window(0, 0, 320, 224)));
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        var nativeObject = f.objects.createDynamicObject(() -> new NativeObject(spawn(100, -1)));
        nativeObject.setDestroyedByOffscreen();
        assertTrue(nativeObject.isDestroyed(), "A routine retiring an on-screen part must keep its native behavior");
    }

    @Test void nativeCounterAdmissionPreservesLowerBitsAndRewindWithoutMovingEitherCursor() {
        var near = tracked(100, 0); var far = tracked(2100, 1);
        var placement = new ObjectPlacementController(List.of(near, far), () -> 320);
        var policy = new StageplayActivationPolicy();
        placement.setStageplayActivation(policy);
        placement.enableCounterBasedRespawn(); placement.reset(0);
        var before = placement.captureRewindState(0, Integer.MIN_VALUE);
        assertTrue(placement.admitStageplayPlacement(far, true));
        assertEquals(2, placement.getCounterForSpawn(far));
        placement.setCounterStateBit(far, 0);
        var saved = placement.captureRewindState(0, Integer.MIN_VALUE);
        var savedPolicy = policy.rewindAdapter().capture();
        assertEquals(before.cursorIndex(), saved.cursorIndex());
        assertEquals(before.leftCursorIndex(), saved.leftCursorIndex());
        assertEquals(before.fwdCounter(), saved.fwdCounter());
        assertEquals(before.bwdCounter(), saved.bwdCounter());
        assertEquals(0x81, saved.objState()[2] & 0xFF, "Shipped bset-before-create plus object-owned low bit");
        placement.setCounterStateBit(far, 2);
        placement.restoreRewindState(saved); policy.rewindAdapter().restore(savedPolicy);
        assertTrue(placement.isCounterStateBitSet(far, 0));
        assertFalse(placement.isCounterStateBitSet(far, 2));
        assertFalse(placement.admitStageplayPlacement(far, true), "Native loaded bit must not be cleared to force a spawn");
        assertTrue(policy.ownsCounter(1, 2));
    }

    @Test void returningRemoteWindowsRearmRespawnableUnloadsButNeverRememberedKills() {
        var far = tracked(2100, 1);
        var f = new Fixture(true, List.of(far));
        f.objects.enableCounterBasedRespawn(); f.objects.reset(0);
        var window = List.of(new StageplayActivationPolicy.Window(2000, 0, 320, 224));
        f.policy.setWindows(window);
        f.objects.update(0, null, List.of(), 0, false);
        var original = f.objects.getActiveObjects().iterator().next();
        assertEquals(1, ((AbstractObjectInstance) original).getRespawnStateIndex());
        f.policy.setWindows(List.of()); f.objects.update(0, null, List.of(), 1, false);
        assertTrue(f.objects.getActiveObjects().isEmpty());
        f.policy.setWindows(window); f.objects.update(0, null, List.of(), 2, false);
        assertEquals(1, f.objects.getActiveObjects().size());
        var reloaded = f.objects.getActiveObjects().iterator().next();
        assertNotSame(original, reloaded);
        ((AbstractObjectInstance) reloaded).setDestroyed(true);
        f.objects.update(0, null, List.of(), 3, false);
        f.policy.setWindows(List.of()); f.objects.update(0, null, List.of(), 4, false);
        f.policy.setWindows(window); f.objects.update(0, null, List.of(), 5, false);
        assertTrue(f.objects.getActiveObjects().isEmpty(), "Returning a window cannot resurrect native killed state");
    }

    @Test void explicitPlacedActorBindsNativeCounterAndCannotRestartKilledState() {
        var far = tracked(2100, 1);
        var f = new Fixture(true, List.of(far));
        f.objects.enableCounterBasedRespawn(); f.objects.reset(0);
        var object = (AbstractObjectInstance) f.objects.materializeStageplayPlacement(far);
        assertEquals(1, object.getRespawnStateIndex());
        f.objects.setSpawnStateBit(far, 3);
        assertTrue(f.objects.isSpawnStateBitSet(far, 3));
        object.setDestroyed(true);
        assertThrows(IllegalStateException.class, () -> f.objects.materializeStageplayPlacement(far),
                "A still-indexed dead owner cannot be handed back before its cleanup pass");
        f.objects.update(0, null, List.of(), 0, false);
        assertTrue(f.objects.getActiveObjects().isEmpty(), "The native destruction dispatcher must retire the owner");
        var state = f.objects.rewindSnapshottable().capture().placement();
        assertEquals(0x88, state.objState()[1] & 0xFF,
                "Offscreen pre-routine culling cannot erase a published kill or its native lower bits");
        var failure = assertThrows(IllegalStateException.class, () -> f.objects.materializeStageplayPlacement(far));
        assertTrue(failure.getMessage().contains("fresh unplaced object"));
    }

    @Test void initialClearRetainsCanonicalCountersWithoutConstructingObjectsAndAllowsFirstExplicitAdmission() {
        var near = tracked(100, 0); var far = tracked(2100, 1);
        var f = new Fixture(true, List.of(near, far), List.of(), true);
        f.objects.enableCounterBasedRespawn(); f.objects.reset(0);
        assertTrue(f.objects.getActiveObjects().isEmpty(), "A retained respawn layout must not construct cleared AI");
        var object = (AbstractObjectInstance) f.objects.materializeStageplayPlacement(near);
        assertEquals(1, object.getRespawnStateIndex(), "A skipped native bset belongs to first construction, not a kill");
        assertEquals(1, f.objects.getActiveObjects().size());
        var remote = (AbstractObjectInstance) f.objects.materializeStageplayPlacement(far);
        assertEquals(2, remote.getRespawnStateIndex(), "Filtered constructor lists cannot renumber native counters");
        assertEquals(2, f.objects.getActiveObjects().size());
        f.objects.setSpawnStateBit(far, 3);
        assertTrue(f.objects.isSpawnStateBitSet(far, 3));
    }

    @Test void nativeCounterAliasingAndFixBugsZeroFailureDriftAreExplicitBounds() {
        var entries = java.util.stream.IntStream.range(0, 257).mapToObj(i -> tracked(100 + i * 10, i)).toList();
        var placement = new ObjectPlacementController(entries, () -> 320);
        placement.setStageplayActivation(new StageplayActivationPolicy());
        placement.enableCounterBasedRespawn(); placement.reset(0);
        var alias = assertThrows(IllegalStateException.class,
                () -> placement.admitStageplayPlacement(entries.getLast(), true));
        assertTrue(alias.getMessage().contains("respawn counter conflict"));
        assertTrue(alias.getMessage().contains("split the shot"));

        var far = tracked(2100, 2);
        var drift = new ObjectPlacementController(List.of(tracked(100, 0), tracked(640, 1), far), () -> 320);
        drift.setStageplayActivation(new StageplayActivationPolicy());
        drift.enableCounterBasedRespawn(); drift.reset(0);
        drift.updateAndLoad(128, (spawn, counter) -> false);
        var failure = assertThrows(IllegalStateException.class, () -> drift.admitStageplayPlacement(far, true));
        assertTrue(failure.getMessage().contains("FixBugs=0 counter drift"));
    }

    private static ObjectSpawn tracked(int x, int index) {
        return new ObjectSpawn(x, 80, 1, 0, 0, true, 80, index);
    }

    private static ObjectSpawn spawn(int x, int index) {
        return new ObjectSpawn(x, 80, 1, 0, 0, false, 80, index);
    }

    private static final class Fixture {
        final StageplayActivationPolicy policy = new StageplayActivationPolicy();
        final Camera camera = mock(Camera.class);
        final ObjectServices services = mock(ObjectServices.class);
        final ObjectManager objects;
        Fixture(boolean enabled, List<ObjectSpawn> spawns) { this(enabled, spawns, spawns, false); }
        Fixture(boolean enabled, List<ObjectSpawn> canonical, List<ObjectSpawn> spawns, boolean clear) {
            var graphics = new GraphicsManager(); graphics.setHeadlessMode(true);
            var module = mock(GameModule.class);
            if (enabled) when(module.getGameService(WorldSessionPolicyProvider.class))
                    .thenReturn(WorldSessionPolicyProvider.compose(world -> policy,
                            clear ? world -> new StageplayObjectLoadPolicy(true, java.util.Set.of()) : null));
            if (enabled) when(module.getGameService(NativePlacementSource.class)).thenReturn(() -> canonical);
            var world = new WorldSession(module);
            when(services.worldSession()).thenReturn(world);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.camera()).thenReturn(camera);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            when(camera.getWidth()).thenReturn((short) 320); when(camera.getHeight()).thenReturn((short) 224);
            var registry = new ObjectRegistry() {
                public ObjectInstance create(ObjectSpawn spawn) { return new NativeObject(spawn); }
                public void reportCoverage(List<ObjectSpawn> source) { }
                public String getPrimaryName(int id) { return "native test"; }
            };
            objects = new ObjectManager(spawns, registry, 0, null, null, graphics, camera, services);
            when(services.objectManager()).thenReturn(objects);
        }
    }
    private static class NativeObject extends AbstractObjectInstance {
        int updates, draws;
        NativeObject(ObjectSpawn spawn) { super(spawn, "native test"); }
        public void update(int clock, PlayableEntity player) { updates++; }
        void cull() { coarseXCullViewport(getX()); }
        public void appendRenderCommands(List<GLCommand> commands) { draws++; }
    }
}
