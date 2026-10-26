package com.openggf.level.objects;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import com.openggf.level.TransitionSstOccupant;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestObjectManagerLifecycle {

    @BeforeEach
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void untypedWorldDoesNotPreventObjectManagerConstruction() {
        var module = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
        var world = org.mockito.Mockito.mock(com.openggf.game.session.WorldSession.class);
        org.mockito.Mockito.when(world.getGameModule()).thenReturn(module);
        var services = new TestObjectServices().withWorldSession(world);

        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null,
                null, null, services);

        assertTrue(manager.getActiveObjects().isEmpty());
        assertThrows(IllegalStateException.class, () -> manager.stageplayPlacementIdentity(
                new ObjectSpawn(100, 80, 1, 0, 0, false, 80, 0)));
    }

    @Test
    public void testPersistentObjectsRemainActive() {
        ObjectSpawn persistentSpawn = new ObjectSpawn(0, 0, 0x01, 0, 0, false, 0);
        ObjectSpawn tempSpawn = new ObjectSpawn(400, 0, 0x02, 0, 0, false, 0);

        List<ObjectSpawn> spawns = List.of(persistentSpawn, tempSpawn);
        TestRegistry registry = new TestRegistry(Set.of(persistentSpawn));
        ObjectManager manager = new ObjectManager(spawns, registry, 0, null, null);

        manager.reset(0);
        manager.update(0, null, null, 1);
        assertEquals(2, manager.getActiveObjects().size());

        // Placement updates are streamed for the next frame; run one tick to apply.
        manager.update(2000, null, null, 2);
        manager.update(2000, null, null, 3);

        assertEquals(1, manager.getActiveObjects().size());
        assertTrue(manager.getActiveObjects().contains(registry.instances.get(persistentSpawn)));
        assertTrue(registry.unloadedInstances.contains(registry.instances.get(tempSpawn)));
    }

    @Test
    public void postCameraPlacementCreatesNonCounterGapSpawnImmediatelyWithoutUpdatingIt() {
        ObjectSpawn gapSpawn = new ObjectSpawn(0x02C0, 0, 0x03, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = new ObjectManager(List.of(gapSpawn), registry, 0, null, null);

        manager.reset(0);
        assertEquals(0, registry.createCount,
                "Gap spawn should start outside the initial [0x0000,0x0280) window");

        manager.postCameraPlacementUpdate(0x0080);

        assertEquals(1, registry.createCount,
                "S2/S3K post-camera placement should materialize gap spawns in the current frame");
        assertTrue(manager.getActiveObjects().contains(registry.instances.get(gapSpawn)),
                "Gap spawn instance should exist immediately after post-camera placement");
        assertEquals(0, registry.instances.get(gapSpawn).updateCount,
                "Post-camera placement should not execute the newly created object until next frame");
    }

    @Test
    public void execThenLoadPlacementMaterializesNewSpawnsAfterObjectExecution() {
        ObjectSpawn streamedSpawn = new ObjectSpawn(0x02C0, 0, 0x03, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = new ObjectManager(List.of(streamedSpawn), registry, 0, null, null);
        manager.enableExecThenLoadPlacement();

        manager.reset(0);
        assertEquals(0, registry.createCount,
                "Spawn should start outside the initial [0x0000,0x0280) window");

        manager.update(0x0080, null, null, 1);

        assertEquals(1, registry.createCount,
                "ObjPosLoad should materialize the newly streamed spawn after ExecuteObjects");
        assertEquals(0, registry.instances.get(streamedSpawn).updateCount,
                "New ObjPosLoad instances should not execute until the following frame");

        manager.update(0x0080, null, null, 2);

        assertEquals(1, registry.instances.get(streamedSpawn).updateCount);
    }

    @Test
    public void s2InitialAndRuntimeLoadsBothBypassVerticalFilter() {
        Camera camera = GameServices.camera();
        camera.setMinY((short) 0);
        camera.setY((short) 0);

        ObjectSpawn highYSpawn = new ObjectSpawn(0x0200, 0x0700, 0x03, 0, 0, false, 0x0700);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = new ObjectManager(List.of(highYSpawn), registry, 0, null, null);
        manager.enableExecThenLoadPlacement();

        manager.reset(0);
        assertEquals(1, registry.createCount,
                "S2 ObjectsManager_Init uses the same X-only load window as its runtime passes");
        assertEquals(0, registry.instances.get(highYSpawn).updateCount,
                "Reset materialization creates the object without executing it");

        manager.update(0, null, null, 1);

        assertEquals(1, registry.createCount,
                "The already-materialized object must not be recreated by the runtime load pass");
        assertEquals(1, registry.instances.get(highYSpawn).updateCount);

        manager.update(0, null, null, 2);

        assertEquals(2, registry.instances.get(highYSpawn).updateCount);
    }

    @Test
    public void bonusReturnRespawnStateSuppressesInitiallyVisibleSpawnBeforeMaterialization() {
        ObjectSpawn originalSpawn = new ObjectSpawn(0x0200, 0, 0x03, 0, 0, true, 0x8000);
        ObjectManager originalManager = new ObjectManager(
                List.of(originalSpawn), new TrackingRegistry(), 0, null, null);
        originalManager.reset(0);
        originalManager.markRemembered(originalSpawn);
        PersistentRespawnState savedRespawnState = originalManager.capturePersistentRespawn();

        ObjectSpawn reloadedSpawn = new ObjectSpawn(0x0200, 0, 0x03, 0, 0, true, 0x8000);
        TrackingRegistry reloadedRegistry = new TrackingRegistry();
        ObjectManager reloadedManager = new ObjectManager(
                List.of(reloadedSpawn), reloadedRegistry, 0, null, null);

        reloadedManager.reset(0, savedRespawnState);

        assertEquals(0, reloadedRegistry.createCount,
                "Bonus-return respawn bits must be restored before the fresh manager materializes its initial window");
        assertTrue(reloadedManager.getActiveObjects().isEmpty(),
                "A remembered non-stay-active spawn must remain absent after a bonus-return reload");
    }

    @Test
    public void transitionOccupantSnapshotIsFreshMutableAndDetachedFromManagerStorage() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        List<ObjectInstance> unloaded = new ArrayList<>();
        ObjectInstance first = new TestInstance(new ObjectSpawn(1, 0, 1, 0, 0, false, 0), true, unloaded);
        ObjectInstance second = new TestInstance(new ObjectSpawn(2, 0, 2, 0, 0, false, 0), true, unloaded);
        ObjectInstance appended = new TestInstance(new ObjectSpawn(3, 0, 3, 0, 0, false, 0), true, unloaded);
        manager.addDynamicObject(first);
        manager.addDynamicObject(second);

        List<TransitionSstOccupant> snapshot = manager.snapshotPersistentTransitionOccupants();
        snapshot.sort((left, right) -> Integer.compare(
                right.identity().getSpawn().objectId(), left.identity().getSpawn().objectId()));
        snapshot.removeIf(occupant -> occupant.identity() == first);
        snapshot.add(new TransitionSstOccupant(appended, -1));

        List<TransitionSstOccupant> freshSnapshot = manager.snapshotPersistentTransitionOccupants();
        assertEquals(List.of(first, second), freshSnapshot.stream()
                        .map(TransitionSstOccupant::identity).toList(),
                "Mutating a transition snapshot must not alter ObjectManager storage");
        assertEquals(((AbstractObjectInstance) first).getSlotIndex(), freshSnapshot.get(0).originalSlot());
        assertEquals(((AbstractObjectInstance) second).getSlotIndex(), freshSnapshot.get(1).originalSlot());
        assertFalse(freshSnapshot.stream().anyMatch(occupant -> occupant.identity() == appended));
    }

    @Test
    public void persistentRespawnStateDefensivelyOwnsCanonicalArrayComponents() {
        long[] remembered = {0x12L, 0x34L};
        long[] stayActive = {0x56L};
        PersistentRespawnState state = new PersistentRespawnState(remembered, stayActive);

        remembered[0] = 0;
        stayActive[0] = 0;
        assertArrayEquals(new long[]{0x12L, 0x34L}, state.rememberedBits(),
                "Constructor inputs must be cloned");
        assertArrayEquals(new long[]{0x56L}, state.stayActiveBits(),
                "Constructor inputs must be cloned");

        long[] rememberedView = state.rememberedBits();
        long[] stayActiveView = state.stayActiveBits();
        rememberedView[1] = 0;
        stayActiveView[0] = 0;
        assertArrayEquals(new long[]{0x12L, 0x34L}, state.rememberedBits(),
                "Accessor results must be cloned");
        assertArrayEquals(new long[]{0x56L}, state.stayActiveBits(),
                "Accessor results must be cloned");
    }

    @Test
    public void resyncRetiresPlacedSlotsWithoutUnloadAndPreservesLiveDynamics() {
        var f = new ResyncFixture();
        var first = f.first();
        var second = f.second();
        int firstSlot = first.getSlotIndex();
        int secondSlot = second.getSlotIndex();
        var retained = f.dynamic(0x74);
        var retainedSecond = f.dynamic(0x75);
        var before = f.snapshot();
        assertTrue(firstSlot >= 4 && secondSlot > firstSlot);
        assertTrue(retained.getSlotIndex() > secondSlot);

        f.manager.resyncSpawnList(List.of());

        assertEquals(-1, first.getSlotIndex());
        assertEquals(-1, second.getSlotIndex());
        assertEquals(0, first.unloadCount + first.removalCount);
        assertEquals(0, second.unloadCount + second.removalCount);
        assertFalse(f.manager.getActiveObjects().contains(first));
        assertFalse(f.manager.getActiveObjects().contains(second));
        assertTrue(f.manager.getActiveObjects().contains(retained));
        assertTrue(f.manager.getActiveObjects().contains(retainedSecond));
        assertEquals(before.dynamicObjects().stream().map(e -> e.objectId()).toList(),
                f.snapshot().dynamicObjects().stream().map(e -> e.objectId()).toList());
        assertEquals(before.dynamicObjects().stream().map(e -> e.slotIndex()).toList(),
                f.snapshot().dynamicObjects().stream().map(e -> e.slotIndex()).toList());
        assertEquals(firstSlot, f.manager.firstFreeDynamicSlot());

        var replacement = f.dynamic(0x76);
        assertEquals(firstSlot, replacement.getSlotIndex());
        f.manager.releaseSlot(first); // A stale editor reference must not free its replacement.
        assertEquals(secondSlot, f.manager.firstFreeDynamicSlot());
        var secondReplacement = f.dynamic(0x77);
        assertEquals(secondSlot, secondReplacement.getSlotIndex());
        f.manager.releaseSlot(second);
        assertTrue(f.manager.firstFreeDynamicSlot() > retainedSecond.getSlotIndex());
        assertEquals(firstSlot, replacement.getSlotIndex());
        assertEquals(secondSlot, secondReplacement.getSlotIndex());
    }

    @Test
    public void resyncReleasesOnlyUnconsumedChildReservations() {
        var f = new ResyncFixture();
        var first = f.first();
        var second = f.second();
        int firstSlot = first.getSlotIndex();
        int[] firstReservations = f.manager.allocateChildSlots(f.firstSpawn, 3);
        int consumedSlot = firstReservations[0];
        int firstUnused = firstReservations[1];
        int secondUnused = firstReservations[2];
        var consumedChild = new ResyncProbe(ResyncFixture.spawn(0x72));
        f.manager.addDynamicObjectToReservedSlot(consumedChild, f.firstSpawn, 0);
        assertEquals(-1, firstReservations[0], "the reservation must really transfer to a live child");
        var retained = f.dynamic(0x74);
        int retainedSlot = retained.getSlotIndex();
        int[] secondReservations = f.manager.allocateChildSlots(f.secondSpawn, 2).clone();
        var before = f.snapshot();
        assertEquals(consumedSlot, consumedChild.getSlotIndex());
        assertEquals(2, before.childSpawns().size());

        f.manager.resyncSpawnList(List.of());

        var after = f.snapshot();
        assertTrue(after.childSpawns().isEmpty());
        assertEquals(-1, first.getSlotIndex());
        assertEquals(-1, second.getSlotIndex());
        assertEquals(consumedSlot, consumedChild.getSlotIndex());
        assertEquals(retainedSlot, retained.getSlotIndex());
        assertTrue(f.manager.getActiveObjects().contains(consumedChild));
        assertTrue(f.manager.getActiveObjects().contains(retained));
        assertEquals(before.dynamicObjects().stream().map(e -> e.objectId()).toList(),
                after.dynamicObjects().stream().map(e -> e.objectId()).toList());
        assertEquals(before.dynamicObjects().stream().map(e -> e.slotIndex()).toList(),
                after.dynamicObjects().stream().map(e -> e.slotIndex()).toList());
        assertEquals(0, first.unloadCount + first.removalCount + second.unloadCount + second.removalCount);
        assertEquals(firstSlot, f.manager.firstFreeDynamicSlot());
        assertEquals(firstUnused, f.manager.allocateSlotAfter(consumedSlot - 1),
                "the consumed child SST must remain occupied");
        assertEquals(secondUnused, f.manager.allocateSlotAfter(firstUnused));
        assertEquals(secondReservations[0], f.manager.allocateSlotAfter(secondUnused),
                "the unrelated retained dynamic SST must remain occupied");
        assertEquals(secondReservations[1], f.manager.allocateSlotAfter(secondReservations[0]));
    }

    @Test
    public void resyncFirstFreeAndAfterCurrentAdmissionsReplayTwiceWithFreshOwners() {
        var f = new ResyncFixture();
        int retiredFirstSlot = f.first().getSlotIndex();
        var owner = f.dynamic(0x74);
        owner.allocatePair = true;
        ObjectRefId ownerId = f.manager.captureIdentityContext().requireIdentityTable().idFor(owner);
        var replacementSpawn = ResyncFixture.spawn(0x71);
        f.manager.resyncSpawnList(List.of(replacementSpawn));
        assertEquals(retiredFirstSlot, f.manager.firstFreeDynamicSlot());
        f.manager.setRewindInPlaceRestoreEnabledForTest(false);
        var before = f.snapshot();

        f.frame(1);
        assertEquals(retiredFirstSlot, f.role(0x71).getSlotIndex());
        var lower = f.role(0x72);
        var upper = f.role(0x73);
        assertTrue(lower.getSlotIndex() < owner.getSlotIndex());
        assertTrue(upper.getSlotIndex() > owner.getSlotIndex());
        assertEquals(0, lower.updateCount, "first-free behind the cursor waits for the next pass");
        assertEquals(1, upper.updateCount, "after-current admission runs during this actual pass");
        var afterFirst = f.snapshot();
        f.frame(2);
        assertEquals(1, lower.updateCount);
        assertEquals(2, upper.updateCount);
        var afterSecond = f.snapshot();

        for (int cycle = 0; cycle < 2; cycle++) {
            f.manager.rewindSnapshottable().restore(before);
            f.same(before);
            assertTrue(owner != f.manager.captureIdentityContext().requireIdentityTable().resolveObject(ownerId, true),
                    "the replay must use a recreated owner, not a warmed live instance");
            f.frame(1);
            f.same(afterFirst);
            assertEquals(0, f.role(0x72).updateCount);
            assertEquals(1, f.role(0x73).updateCount);
            f.frame(2);
            f.same(afterSecond);
        }
    }

    private static final class ResyncFixture {
        final ObjectSpawn firstSpawn = new ObjectSpawn(0x100, 0x100, 0x70, 0, 0, false, 0x100, 1);
        final ObjectSpawn secondSpawn = new ObjectSpawn(0x120, 0x100, 0x70, 0, 0, false, 0x100, 2);
        final Camera camera = new Camera();
        final ObjectManager manager;

        ResyncFixture() {
            var registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new ResyncProbe(spawn); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int objectId) { return "resync-probe"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
            };
            ObjectManager[] boundManager = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return boundManager[0]; }
                @Override public Camera camera() { return camera; }
            };
            manager = new ObjectManager(List.of(firstSpawn, secondSpawn), registry, 0, null, null,
                    null, camera, services);
            boundManager[0] = manager;
            manager.enableExecThenLoadPlacement();
            manager.reset(0);
            manager.preloadInitialSpawnsForHydration();
            assertTrue(first() != null && second() != null, "materialize real placed owners before resync");
        }

        static ObjectSpawn spawn(int objectId) {
            return new ObjectSpawn(0x180, 0x100, objectId, 0, 0, false, 0x100);
        }
        ResyncProbe first() { return (ResyncProbe) manager.getActiveObjectForRewind(firstSpawn); }
        ResyncProbe second() { return (ResyncProbe) manager.getActiveObjectForRewind(secondSpawn); }
        ResyncProbe dynamic(int objectId) { return manager.createDynamicObject(() -> new ResyncProbe(spawn(objectId))); }
        ResyncProbe role(int objectId) {
            return manager.getActiveObjects().stream().filter(ResyncProbe.class::isInstance)
                    .map(ResyncProbe.class::cast).filter(value -> value.getSpawn().objectId() == objectId)
                    .findFirst().orElseThrow();
        }
        void frame(int frame) { manager.update(0, null, List.of(), frame, false); }
        ObjectManagerSnapshot snapshot() { return manager.rewindSnapshottable().capture(); }
        void same(ObjectManagerSnapshot expected) {
            var actual = snapshot();
            assertTrue(RewindSnapshotDiff.diffKey("object-manager", expected, actual).isEmpty(),
                    () -> RewindSnapshotDiff.diffKey("object-manager", expected, actual).toString());
        }
    }

    public static final class ResyncProbe extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int updateCount;
        private boolean allocatePair;
        private boolean allocatedPair;
        private int unloadCount;
        private int removalCount;

        public ResyncProbe(ObjectSpawn spawn) { super(spawn, "resync-probe"); }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public void onUnload() { unloadCount++; }
        @Override protected void onRemovedFromObjectManager() { removalCount++; }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            updateCount++;
            if (allocatePair && !allocatedPair) {
                allocatedPair = true;
                spawnFreeChild(() -> new ResyncProbe(ResyncFixture.spawn(0x72)));
                spawnAfterCurrentSibling(() -> new ResyncProbe(ResyncFixture.spawn(0x73)));
            }
        }
    }

    private static final class TestRegistry implements ObjectRegistry {
        private final Set<ObjectSpawn> persistentSpawns;
        private final Map<ObjectSpawn, ObjectInstance> instances = new IdentityHashMap<>();
        private final List<ObjectInstance> unloadedInstances = new ArrayList<>();

        private TestRegistry(Set<ObjectSpawn> persistentSpawns) {
            this.persistentSpawns = persistentSpawns;
        }

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            boolean persistent = persistentSpawns.contains(spawn);
            TestInstance instance = new TestInstance(spawn, persistent, unloadedInstances);
            instances.put(spawn, instance);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Test";
        }
    }

    private static final class TrackingRegistry implements ObjectRegistry {
        private final Map<ObjectSpawn, TrackingObjectInstance> instances = new IdentityHashMap<>();
        private int createCount;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            createCount++;
            TrackingObjectInstance instance = new TrackingObjectInstance(spawn);
            instances.put(spawn, instance);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Tracking";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_2;
        }
    }

    private static final class TestInstance extends AbstractObjectInstance {
        private final ObjectSpawn spawn;
        private final boolean persistent;
        private final List<ObjectInstance> unloadedInstances;

        private TestInstance(ObjectSpawn spawn, boolean persistent, List<ObjectInstance> unloadedInstances) {
            super(spawn, "TestInstance");
            this.spawn = spawn;
            this.persistent = persistent;
            this.unloadedInstances = unloadedInstances;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public boolean isPersistent() {
            return persistent;
        }

        @Override
        public void onUnload() {
            unloadedInstances.add(this);
        }
    }

    private static final class TrackingObjectInstance extends AbstractObjectInstance {
        private int updateCount;

        private TrackingObjectInstance(ObjectSpawn spawn) {
            super(spawn, "TrackingObject");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updateCount++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
