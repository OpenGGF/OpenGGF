package com.openggf.level.objects;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.session.EngineContext;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests for {@link ObjectManager#rewindSnapshottable()}.
 *
 * <p>Exercises the capture/restore round-trip for placement-managed objects.
 * Uses a minimal {@link ObjectRegistry} that creates simple
 * {@link AbstractObjectInstance} subclasses whose mutable fields can be
 * asserted after restore.
 */
class TestObjectManagerRewindSnapshot {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    // ------------------------------------------------------------------
    // Minimal concrete object class for testing
    // ------------------------------------------------------------------

    /** Minimal subclass with a trackable "moved" position. */
    private static final class TrackableObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);

        TrackableObject(ObjectSpawn spawn) {
            super(spawn, "TrackableObject");
            updateDynamicSpawn(spawn.x(), spawn.y());
        }

        /** Simulates moving the object to a new position. */
        void moveTo(int x, int y) {
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }
    }

    /** Moving dynamic solid recreates from spawn, whose value identity changes on motion. */
    private static final class MovingDynamicSolid extends AbstractObjectInstance
            implements SolidObjectProvider, SpawnRewindRecreatable {
        MovingDynamicSolid(ObjectSpawn spawn) {
            super(spawn, "MovingDynamicSolid");
            updateDynamicSpawn(spawn.x(), spawn.y());
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) {
            updateDynamicSpawn(getX() + 1, getY());
        }

        @Override public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(16, 8, 8);
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    /** Child solid that shares its parent's layout spawn and keys its latches by instance (Rockn shell). */
    private static final class InstanceKeyedChildSolid extends AbstractObjectInstance
            implements SolidObjectProvider, SpawnRewindRecreatable {
        static final int OFFSET_X = 200;

        InstanceKeyedChildSolid(ObjectSpawn spawn) {
            super(spawn, "InstanceKeyedChildSolid");
        }

        // Like the shell, the child keeps its parent's spawn record and sits elsewhere.
        @Override public int getX() { return getSpawn().x() + OFFSET_X; }
        @Override public int getY() { return getSpawn().y(); }

        @Override public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(16, 8, 8);
        }

        @Override public boolean usesInstanceSolidStateLatchKey() { return true; }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class NullSpawnRewindable extends AbstractObjectInstance
            implements RewindRecreatable {
        NullSpawnRewindable() {
            super(null, "NullSpawnRewindable");
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) {}

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new NullSpawnRewindable();
        }
    }

    private static final class ReservedTouchPublisher extends AbstractObjectInstance
            implements TouchResponseProvider, RewindRecreatable {
        ReservedTouchPublisher(ObjectSpawn spawn) {
            super(spawn, "ReservedTouchPublisher");
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) {}
        @Override public int getCollisionFlags() { return 0x47; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new ReservedTouchPublisher(context.spawn());
        }
    }

    // ------------------------------------------------------------------
    // Minimal registry
    // ------------------------------------------------------------------

    private static final class TrackingRegistry implements ObjectRegistry {
        final Map<ObjectSpawn, TrackableObject> instances = new IdentityHashMap<>();
        int createCount;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            createCount++;
            TrackableObject obj = new TrackableObject(spawn);
            instances.put(spawn, obj);
            return obj;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {}

        @Override
        public String getPrimaryName(int objectId) {
            return "TrackableObject";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_2;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ObjectSpawn spawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x01, 0, 0, false, 0);
    }

    private static ObjectManager makeManager(List<ObjectSpawn> spawns, TrackingRegistry registry) {
        return new ObjectManager(spawns, registry, 0, null, null);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    private static final class RangeTailPublisher extends AbstractObjectInstance implements TouchResponseProvider {
        int updates;
        RangeTailPublisher() {super(spawn(100, 200), "RangeTailPublisher");}
        @Override public void update(int vIntRunCount, PlayableEntity player) {updates++;}
        @Override public boolean usesCustomOutOfRangeCheck() {return true;}
        @Override public boolean isCustomOutOfRange(int cameraX) {return true;}
        @Override public int getCollisionFlags() {return 0x47;}
        @Override public int getCollisionProperty() {return 0;}
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    @Test
    void initialDispatchDoesNotPublishAResponderDeletedByItsRangeTail() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        RangeTailPublisher publisher = new RangeTailPublisher();
        manager.addDynamicObject(publisher);
        try (var scope = manager.beginInitialProcessSprites(0, null, List.of())) {
            manager.processInitialDynamicSlots(scope);
            manager.finishInitialProcessSprites(scope);
        }
        assertEquals(1, publisher.updates, "the routine executes before its range tail");
        assertTrue(manager.objectQuery().identityOf(publisher).isEmpty());
        var snapshot = assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
        var collision = snapshot.collisionResponseState();
        assertTrue(collision.previousCollisionObjectIds().isEmpty());
        assertTrue(collision.currentCollisionBuildObjectIds().isEmpty());
        assertEquals(0, collision.currentCollisionBuildCursor());
    }

    @Test
    void fullResetRetiresBothCollisionBuffersBeforeReusingSlots() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        ReservedTouchPublisher publisher = new ReservedTouchPublisher(spawn(100, 200));
        manager.addDynamicObject(publisher);
        var list = manager.initialCollisionResponseList();
        list.captureCompletedBuild(List.of(publisher));
        list.resetCurrentBuild();
        list.addToCurrentBuild(publisher);
        var before = manager.rewindSnapshottable().capture().collisionResponseState();
        assertEquals(1, before.previousCollisionObjectIds().size());
        assertEquals(1, before.currentCollisionBuildObjectIds().size());

        manager.reset(0);
        var empty = assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
        assertEquals(ObjectManagerSnapshot.CollisionResponseState.empty(), empty.collisionResponseState());
        assertTrue(list.playerReadView().isEmpty());
        assertTrue(list.currentBuildView().isEmpty());
        ReservedTouchPublisher replacement = new ReservedTouchPublisher(spawn(120, 200));
        manager.addDynamicObject(replacement);
        list.addToCurrentBuild(replacement);
        var replaced = manager.rewindSnapshottable().capture().collisionResponseState();
        assertTrue(replaced.previousCollisionObjectIds().isEmpty());
        assertEquals(List.of(manager.objectQuery().identityOf(replacement).orElseThrow()),
                replaced.currentCollisionBuildObjectIds());
    }

    @Test
    void reservedChildTouchPublisherHasIdentityWhenCollisionListIsCaptured() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        ObjectSpawn parentSpawn = spawn(100, 200);
        manager.allocateChildSlots(parentSpawn, 1);
        ReservedTouchPublisher child = new ReservedTouchPublisher(spawn(116, 200));

        manager.addDynamicObjectToReservedSlot(child, parentSpawn, 0);
        manager.initialCollisionResponseList().addToCurrentBuild(child);

        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
    }

    @Test
    void reservedLostRingTouchPublisherHasIdentityWhenCollisionListIsCaptured() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        int slot = manager.allocateDynamicSlot();
        LostRingObjectInstance ring = LostRingObjectInstance.forTest(
                100, 200, 0, 0, 0, 0xFF);

        manager.spawnLostRingObjectAtSlot(ring, slot);
        manager.initialCollisionResponseList().addToCurrentBuild(ring);

        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
    }

    @Test
    void activeObjectRepeatedRestoreDoesNotConsumeTheNextDynamicIdentity() {
        ObjectSpawn sp = spawn(100, 200);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);
        manager.reset(0);
        manager.update(0, null, null, 1);
        ObjectManagerSnapshot source = manager.rewindSnapshottable().capture();
        int expectedNextOrdinal = source.dynamicObjectIdCounter();

        manager.rewindSnapshottable().restore(source);
        assertEquals(expectedNextOrdinal,
                manager.rewindSnapshottable().capture().dynamicObjectIdCounter());
        manager.rewindSnapshottable().restore(source);
        assertEquals(expectedNextOrdinal,
                manager.rewindSnapshottable().capture().dynamicObjectIdCounter());

        manager.addDynamicObject(new NullSpawnRewindable());
        ObjectManagerSnapshot afterSpawn = manager.rewindSnapshottable().capture();
        ObjectManagerSnapshot.DynamicObjectEntry next = afterSpawn.dynamicObjects().stream()
                .filter(entry -> entry.className().equals(NullSpawnRewindable.class.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedNextOrdinal, next.objectId().dynamicId());
    }

    @Test
    void captureRestoreRoundTrip_singleObject() {
        ObjectSpawn sp = spawn(100, 200);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        // Advance one frame to materialize the spawn
        manager.reset(0);
        manager.update(0, null, null, 1);

        // Verify object was created
        assertEquals(1, registry.createCount);
        TrackableObject original = registry.instances.get(sp);
        assertNotNull(original);

        // Move object and capture snapshot
        original.moveTo(300, 400);
        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snap.capture();

        // Verify snapshot has one slot entry with correct position
        assertEquals(1, snapshot.slots().size());
        ObjectManagerSnapshot.PerSlotEntry entry = snapshot.slots().get(0);
        assertEquals(300, entry.state().dynamicSpawnX());
        assertEquals(400, entry.state().dynamicSpawnY());
        assertTrue(entry.state().hasDynamicSpawn());

        // Advance the object further
        original.moveTo(999, 999);
        assertEquals(999, original.getX());

        // Restore
        snap.restore(snapshot);

        // After restore, find the restored instance
        Collection<ObjectInstance> active = manager.getActiveObjects();
        assertEquals(1, active.size());
        ObjectInstance restored = active.iterator().next();
        assertNotNull(restored);

        // The restored object should be at the captured position (300, 400)
        assertEquals(300, restored.getX());
        assertEquals(400, restored.getY());
    }

    @Test
    void captureRestoreRoundTrip_scalarsPreserved() {
        ObjectSpawn sp = spawn(0, 0);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        // Run 5 frames so counters advance
        for (int i = 1; i <= 5; i++) {
            manager.update(0, null, null, i);
        }

        int fc = manager.getFrameCounter();
        int vc = manager.getVblaCounter();

        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snap.capture();

        assertEquals(fc, snapshot.frameCounter());
        assertEquals(vc, snapshot.vblaCounter());

        // Run more frames to advance counters
        for (int i = 6; i <= 10; i++) {
            manager.update(0, null, null, i);
        }
        assertNotEquals(fc, manager.getFrameCounter());

        // Restore
        snap.restore(snapshot);
        assertEquals(fc, manager.getFrameCounter());
        assertEquals(vc, manager.getVblaCounter());
    }

    @Test
    void captureNoMutateRestoreIsIdempotent() {
        ObjectSpawn sp = spawn(50, 60);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        manager.update(0, null, null, 1);

        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot s1 = snap.capture();
        snap.restore(s1);
        ObjectManagerSnapshot s2 = snap.capture();

        // Scalars should match after no-mutation restore cycle
        assertEquals(s1.frameCounter(), s2.frameCounter());
        assertEquals(s1.vblaCounter(), s2.vblaCounter());
        assertEquals(s1.slots().size(), s2.slots().size());
    }

    @Test
    void restoreRecreatesObjectAtCapturedPosition() {
        ObjectSpawn sp = spawn(10, 20);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        manager.update(0, null, null, 1);

        // Capture while object is at spawn position
        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshotAtSpawn = snap.capture();
        assertEquals(1, snapshotAtSpawn.slots().size());

        // Restore — should recreate the object at spawn position
        snap.restore(snapshotAtSpawn);

        Collection<ObjectInstance> active = manager.getActiveObjects();
        assertEquals(1, active.size(), "Object should be present after restore");
        ObjectInstance restored = active.iterator().next();
        assertEquals(10, restored.getX(), "X should match captured spawn X");
        assertEquals(20, restored.getY(), "Y should match captured spawn Y");
    }

    @Test
    void restoreRebindsRidingStateToRecreatedSolidObject() {
        ObjectSpawn sp = spawn(100, 100);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        manager.update(0, null, null, 1);

        TrackableObject original = registry.instances.get(sp);
        assertNotNull(original);

        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 100);
        int maxTop = original.getSolidParams().groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (100 - 4 - maxTop + 8));
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        manager.updateSolidContacts(player);
        assertTrue(manager.isRidingObject(player), "precondition: player should be riding before capture");
        assertSame(original, manager.getRidingObject(player));

        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot ridingSnapshot = snap.capture();

        manager.clearRidingObject(player);
        assertFalse(manager.isRidingObject(player), "test must mutate riding state before restore");

        snap.restore(ridingSnapshot);

        assertTrue(manager.isRidingObject(player), "restore must preserve riding state");
        assertNotSame(original, manager.getRidingObject(player),
                "restore recreates object instances, so riding must be rebound to the recreated platform");
        assertEquals(100, manager.getRidingObject(player).getX());
        assertEquals(100, manager.getRidingObject(player).getY());
    }
    @Test
    void movingSpawnRecreatedDynamicSolidRestoresItsExecutionSlotAndRiderTwice() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        manager.reset(0);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        MovingDynamicSolid original = new MovingDynamicSolid(spawn(100, 100));
        manager.addDynamicObject(original);
        manager.update(0, null, null, 1);
        int capturedX = original.getX();
        assertEquals(101, capturedX, "precondition: dynamic solid executed and moved");
        int slot = original.getSlotIndex();
        assertTrue(slot >= 0, "precondition: dynamic solid owns a managed slot");

        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) capturedX);
        int maxTop = original.getSolidParams().groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (100 - 4 - maxTop + 8));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        manager.updateSolidContacts(player);
        assertTrue(manager.isRidingObject(player, original));

        RewindSnapshottable<ObjectManagerSnapshot> adapter = manager.rewindSnapshottable();
        ObjectManagerSnapshot captured = adapter.capture();
        assertEquals(1, captured.dynamicObjects().size());
        assertEquals(slot, captured.dynamicObjects().getFirst().slotIndex());
        ObjectSpawn capturedSpawn = captured.dynamicObjects().getFirst().spawn();
        MovingDynamicSolid previous = original;
        for (int cycle = 0; cycle < 2; cycle++) {
            manager.clearRidingObject(player);
            manager.update(0, null, null, 2);
            adapter.restore(captured);

            MovingDynamicSolid restored = manager.activeObjectsOfType(MovingDynamicSolid.class)
                    .stream().findFirst().orElseThrow();
            assertNotSame(previous, restored, "must exercise generic reconstruction on every cycle");
            assertEquals(slot, restored.getSlotIndex());
            assertEquals(capturedX, restored.getX());
            assertNotSame(capturedSpawn, restored.getSpawn(),
                    "moving dynamic spawn cannot be rebound by ObjectSpawn reference identity");
            assertSame(restored, manager.findRestoredRidingObject(null, slot),
                    "captured slot must already be wired into the execution table");
            assertTrue(manager.isRidingObject(player, restored),
                    "reconstruction must restore exact dynamic riding authority");
            assertSame(restored, manager.getRidingObject(player));
            assertEquals(captured.solidContactRiding(), adapter.capture().solidContactRiding());

            manager.update(0, null, null, 2);
            assertEquals(capturedX + 1, restored.getX(),
                    "restored dynamic must execute exactly once through its captured slot");
            previous = restored;
        }
    }

    @Test
    void instanceLatchOfChildSharingItsParentSpawnRestoresToTheChildInItsSlot() {
        ObjectSpawn parentSpawn = spawn(100, 100);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(parentSpawn), registry);
        manager.reset(0);
        manager.update(0, null, null, 1);
        assertNotNull(registry.instances.get(parentSpawn), "precondition: parent is a live layout object");
        InstanceKeyedChildSolid child = new InstanceKeyedChildSolid(parentSpawn);
        manager.addDynamicObject(child);
        manager.update(0, null, null, 2);
        int childSlot = child.getSlotIndex();
        assertTrue(childSlot >= 0, "precondition: child owns a managed slot");

        Sonic player = landOn(manager, child);
        RewindSnapshottable<ObjectManagerSnapshot> adapter = manager.rewindSnapshottable();
        ObjectManagerSnapshot captured = adapter.capture();
        var key = captured.solidContactState().standingBits().getFirst().keys().getFirst();
        assertTrue(key.instanceKey(), "precondition: the child latches by instance");
        assertEquals(childSlot, key.slotIndex());

        adapter.restore(captured);

        // activeObjects.get(spawn) answers the parent, whose latch is spawn-keyed;
        // the captured slot identifies the child that actually owned the bit.
        var restored = manager.rewindSnapshottable().capture();
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "object-manager", captured, restored));
        InstanceKeyedChildSolid restoredChild = manager.activeObjectsOfType(InstanceKeyedChildSolid.class)
                .stream().findFirst().orElseThrow();
        assertTrue(manager.hasObjectStandingBit(player, restoredChild));
    }

    @Test
    void deletingAnInstanceLatchedDynamicSolidClearsItsObjectSideLatches() {
        ObjectManager manager = makeManager(List.of(), new TrackingRegistry());
        manager.reset(0);
        InstanceKeyedChildSolid solid = new InstanceKeyedChildSolid(spawn(100, 100));
        manager.addDynamicObject(solid);
        manager.update(0, null, null, 1);
        Sonic player = landOn(manager, solid);
        assertTrue(manager.hasObjectStandingBit(player, solid), "precondition: standing latch set");

        manager.removeDynamicObject(solid);

        // Delete_Current_Sprite (docs/skdisasm/sonic3k.asm:36148-36165) zeroes the
        // whole SST slot, status(a0) latch bits included. A retained instance key
        // can never match again, yet capture would record its freed slot and a
        // restore would bind whichever object reuses that slot.
        var state = manager.rewindSnapshottable().capture().solidContactState();
        assertTrue(state.standingBits().isEmpty(), "deleted solid must not keep a standing latch");
        assertTrue(state.pushingBits().isEmpty(), "deleted solid must not keep a pushing latch");
    }

    @Test
    void deletingAnOverlappedTouchObjectRemovesItFromTheOverlapBuffers() throws Exception {
        var table = new TouchResponseTable(new com.openggf.data.RomByteReader(new byte[0x80]), 0, 0x40);
        ObjectManager manager = new ObjectManager(List.of(), new TrackingRegistry(), 0, null, table);
        manager.reset(0);
        ReservedTouchPublisher publisher = new ReservedTouchPublisher(spawn(100, 200));
        manager.addDynamicObject(publisher);
        java.lang.reflect.Field controllerField = ObjectManager.class.getDeclaredField("touchResponses");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(manager);
        java.lang.reflect.Field overlappingField = controller.getClass().getDeclaredField("overlapping");
        overlappingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var overlapping = (java.util.Set<ObjectInstance>) overlappingField.get(controller);
        overlapping.add(publisher);

        manager.removeDynamicObject(publisher);

        assertFalse(overlapping.contains(publisher),
                "a deleted SST slot carries no overlap into later touch passes");
    }

    private static Sonic landOn(ObjectManager manager, AbstractObjectInstance solid) {
        Sonic player = new Sonic("sonic", (short) 0, (short) 0);
        player.setCentreX((short) solid.getX());
        int maxTop = ((SolidObjectProvider) solid).getSolidParams().groundHalfHeight() + player.getYRadius();
        player.setCentreY((short) (solid.getY() - 4 - maxTop + 8));
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        manager.updateSolidContacts(player);
        assertTrue(manager.isRidingObject(player, solid), "precondition: player landed on the solid");
        return player;
    }

    @Test
    void registeredContactStateRebindsAllChannelsAndReplaysTwoForwardBranches() {
        ObjectSpawn spawn = spawn(100, 100);
        var owners = new ContactRoster(List.of(spawn));
        var manager = owners.manager;
        manager.reset(0);
        manager.update(0, null, List.of(), 1, false);
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var platform = owners.registry.instances.get(spawn);
        var p1 = contactPlayer("sonic");
        var p2 = contactPlayer("tails");
        owners.roster(p1, p2);
        // Declared unit state: exercise every hidden channel, including two cached angles.
        manager.solidContacts().restoreRewindState(contactState(p1, p2, spawn, platform.getSlotIndex()));
        var adapter = manager.rewindSnapshottable();
        var a = adapter.capture();
        var contact = a.solidContactState();
        assertEquals(2, contact.riding().size());
        assertEquals(2, contact.standingSnapshots().size());
        assertEquals(4, contact.headroomSnapshots().size());
        assertEquals(2, contact.standingBits().size());
        assertEquals(2, contact.pushingBits().size());
        assertEquals(2, contact.standingBitSnapshots().size());
        assertEquals(2, contact.inlineSupportedPlayerBindings().size());
        assertEquals(2, contact.forceAirOnStaleSupportLossPlayerBindings().size());
        assertTrue(contact.inlineSupportedPlayers().isEmpty());
        assertTrue(contact.forceAirOnStaleSupportLoss().isEmpty());
        assertTrue(contact.riding().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        assertTrue(contact.standingSnapshots().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        assertTrue(contact.headroomSnapshots().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        assertTrue(contact.standingBits().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        assertTrue(contact.pushingBits().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        assertTrue(contact.standingBitSnapshots().stream().allMatch(entry -> entry.player() == null && entry.playerId() != null));
        forwardContacts(manager, p1, p2);
        var b = adapter.capture();
        forwardContacts(manager, p1, p2);
        var c = adapter.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            // Reverse construction order while preserving actual roster roles; camera follows P2.
            var freshP2 = contactPlayer("tails");
            var freshP1 = contactPlayer("sonic");
            owners.roster(freshP1, freshP2);
            owners.camera.setFocusedSprite(freshP2);
            adapter.restore(a);
            assertContactWorld(a, adapter.capture());
            assertFalse(manager.isRidingObject(p1));
            assertFalse(manager.isRidingObject(p2));
            assertTrue(manager.isRidingObject(freshP1));
            assertTrue(manager.isRidingObject(freshP2));
            assertNotSame(platform, manager.getRidingObject(freshP1));
            assertTrue(manager.hasObjectStandingBit(freshP1, manager.getRidingObject(freshP1)));
            assertTrue(manager.hasObjectPushingBit(freshP2));
            assertEquals(7, manager.latestHeadroomSnapshot(freshP1, 0));
            assertEquals(11, manager.latestHeadroomSnapshot(freshP1, 0x40));
            forwardContacts(manager, freshP1, freshP2);
            assertContactWorld(b, adapter.capture());
            forwardContacts(manager, freshP1, freshP2);
            assertContactWorld(c, adapter.capture());
        }
    }

    @Test
    void missingRequiredContactRoleFailsBeforeContactMapMutation() {
        var owners = new ContactRoster(List.of());
        var p1 = contactPlayer("sonic");
        var p2 = contactPlayer("tails");
        owners.roster(p1, p2);
        var controller = owners.manager.solidContacts();
        controller.markObjectSupportThisFrame(p2);
        controller.forceAirOnStaleObjectSupportLoss(p2);
        var saved = controller.captureRewindState(owners.manager.captureIdentityContext());
        owners.roster(contactPlayer("sonic"));
        controller.reset();
        controller.markObjectSupportThisFrame(p1);
        var before = controller.captureRewindState();
        var failure = assertThrows(IllegalStateException.class,
                () -> controller.restoreRewindState(saved, owners.manager.captureIdentityContext()));
        assertTrue(failure.getMessage().contains("Missing required player reference"));
        assertEquals(before, controller.captureRewindState(), "failed role resolution must not clear contact maps");
        assertThrows(IllegalStateException.class, () -> controller.restoreRewindState(saved),
                "a structural snapshot cannot silently fall back to contextless raw restoration");
        assertEquals(before, controller.captureRewindState());
    }

    @Test
    void mixedContactMembershipBindingsRetainOrderAndStandaloneIdentity() {
        var owners = new ContactRoster(List.of());
        var p1 = contactPlayer("z-main");
        var p2 = contactPlayer("a-side");
        var standalone = contactPlayer("m-legacy");
        owners.roster(p1, p2);
        var controller = owners.manager.solidContacts();
        for (var player : List.of(p1, standalone, p2)) {
            controller.markObjectSupportThisFrame(player);
            controller.forceAirOnStaleObjectSupportLoss(player);
        }
        var legacy = controller.captureRewindState();
        assertEquals(List.of(p2, standalone, p1), legacy.inlineSupportedPlayers());
        controller.reset();
        controller.restoreRewindState(legacy);
        assertEquals(legacy, controller.captureRewindState(), "original raw overload keeps exact standalone identity");
        var context = owners.manager.captureIdentityContext();
        var structural = controller.captureRewindState(context);
        var expected = List.of(
                new ObjectManagerSnapshot.SolidContactPlayerBinding(null, PlayerRefId.sidekick(0)),
                new ObjectManagerSnapshot.SolidContactPlayerBinding(standalone, null),
                new ObjectManagerSnapshot.SolidContactPlayerBinding(null, PlayerRefId.mainPlayer()));
        assertEquals(expected, structural.inlineSupportedPlayerBindings());
        assertEquals(expected, structural.forceAirOnStaleSupportLossPlayerBindings());
        controller.reset();
        controller.restoreRewindState(structural, context);
        assertEquals(legacy, controller.captureRewindState(), "unchanged roster restores every mixed member in order");
        assertEquals(structural, controller.captureRewindState(context));
        assertThrows(IllegalArgumentException.class,
                () -> new ObjectManagerSnapshot.SolidContactPlayerBinding(p1, PlayerRefId.mainPlayer()));
        assertThrows(IllegalArgumentException.class,
                () -> new ObjectManagerSnapshot.SolidContactPlayerBinding(null, PlayerRefId.nullRef()));
    }

    @Test
    void actualRosterPlayerRolesIgnoreP2CameraFocusAndDoNotFallBackFromMissingMain() {
        var owners = new ContactRoster(List.of());
        var p1 = contactPlayer("sonic");
        var p2 = contactPlayer("tails");
        owners.roster(p1, p2);
        owners.camera.setFocusedSprite(p2);
        var ids = owners.manager.captureIdentityContext().requireIdentityTable();
        assertEquals(PlayerRefId.mainPlayer(), ids.encodePlayer(p1));
        assertEquals(PlayerRefId.sidekick(0), ids.encodePlayer(p2));
        when(owners.sprites.getMainPlayable()).thenReturn(null);
        ids = owners.manager.captureIdentityContext().requireIdentityTable();
        assertNull(ids.resolvePlayer(PlayerRefId.mainPlayer(), false));
        assertNull(ids.encodePlayer(p1));
        assertEquals(PlayerRefId.sidekick(0), ids.encodePlayer(p2), "P2 focus cannot manufacture a missing main role");
        when(owners.sprites.getMainPlayable()).thenThrow(new IllegalStateException("ambiguous actual roster"));
        assertThrows(IllegalStateException.class, owners.manager::captureIdentityContext);
    }

    private static void assertContactWorld(ObjectManagerSnapshot expected, ObjectManagerSnapshot actual) {
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", expected, actual),
                () -> RewindSnapshotDiff.diffKey("object-manager", expected, actual).toString());
    }

    private static void forwardContacts(ObjectManager manager, Sonic p1, Sonic p2) {
        manager.updateSolidContacts(p1);
        manager.updateSolidContacts(p2);
    }

    private static Sonic contactPlayer(String code) {
        var player = new Sonic(code, (short) 0, (short) 0);
        player.setCentreX((short) 100);
        player.setCentreY((short) 69);
        player.setAir(false);
        player.setOnObject(true);
        player.setPushing(true);
        return player;
    }

    private static ObjectManagerSnapshot.SolidContactState contactState(
            Sonic p1, Sonic p2, ObjectSpawn spawn, int slot) {
        var key = new ObjectManagerSnapshot.SolidContactLatchKey(spawn, slot, false);
        var latches = List.of(new ObjectManagerSnapshot.SolidContactLatchEntry(p1, List.of(key)),
                new ObjectManagerSnapshot.SolidContactLatchEntry(p2, List.of(key)));
        return new ObjectManagerSnapshot.SolidContactState(17,
                List.of(new ObjectManagerSnapshot.SolidContactRidingEntry(p1, spawn, slot, 100, 100, -1),
                        new ObjectManagerSnapshot.SolidContactRidingEntry(p2, spawn, slot, 100, 100, -1)),
                List.of(p1, p2), List.of(p1, p2),
                List.of(new ObjectManagerSnapshot.SolidContactStandingSnapshotEntry(p1, ContactKind.TOP, true, true),
                        new ObjectManagerSnapshot.SolidContactStandingSnapshotEntry(p2, ContactKind.TOP, true, true)),
                List.of(new ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry(p1, 0, 7),
                        new ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry(p1, 0x40, 11),
                        new ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry(p2, 0, 13),
                        new ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry(p2, 0x40, 19)),
                latches, latches, latches);
    }

    private static final class ContactRoster extends StubObjectServices {
        private final Camera camera = new Camera();
        private final SpriteManager sprites = mock(SpriteManager.class);
        private final TrackingRegistry registry = new TrackingRegistry();
        private final ObjectManager manager;
        private List<PlayableEntity> sidekicks = List.of();

        private ContactRoster(List<ObjectSpawn> spawns) {
            manager = new ObjectManager(spawns, registry, 0, null, null, null, camera, this);
        }

        private void roster(Sonic main, Sonic... others) {
            when(sprites.getMainPlayable()).thenReturn(main);
            sidekicks = List.of(others);
            camera.setFocusedSprite(main);
        }

        @Override public Camera camera() { return camera; }
        @Override public SpriteManager spriteManager() { return sprites; }
        @Override public List<PlayableEntity> sidekicks() { return sidekicks; }
        @Override public ObjectManager objectManager() { return manager; }
    }

}
