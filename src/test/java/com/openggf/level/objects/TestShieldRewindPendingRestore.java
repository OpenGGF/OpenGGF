package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PowerUpObject;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestShieldRewindPendingRestore {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void shieldDynamicRestoreQueuesPendingEntryThroughGenericRecreateWithoutSharedCodec() {
        ObjectManager objectManager = new ObjectManager(List.of(), null, 0, null, null);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        ShieldObjectInstance source = ObjectConstructionContext.construct(
                objectManager.services(), () -> new ShieldObjectInstance(sonic));
        source.setSlotIndex(60);
        objectManager.addDynamicObject(source);

        ObjectManagerSnapshot snapshot = objectManager.rewindSnapshottable().capture();

        objectManager.rewindSnapshottable().restore(snapshot);

        PowerUpObject restoredPowerUp = new DefaultPowerUpSpawner(objectManager)
                .spawnShield(sonic, ShieldType.BASIC);
        ShieldObjectInstance restoredShield = assertInstanceOf(ShieldObjectInstance.class, restoredPowerUp);
        assertSame(sonic, restoredShield.getPlayer());
        assertEquals(60, restoredShield.getSlotIndex(),
                "generic recreate should queue the captured dynamic entry for player refresh");
    }

    @Test
    void playerRefreshRetainsCreatorCallbackOwnerForShield() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short)100, (short)200);
        ShieldObjectInstance source = ObjectConstructionContext.construct(manager.services(), () -> new ShieldObjectInstance(player));
        manager.restoreDynamicCallbackOwner(source, "creator");
        manager.addDynamicObject(source);
        var captured = manager.rewindSnapshottable().capture();
        assertEquals("creator", captured.dynamicObjects().getFirst().ownerModId());
        manager.removeDynamicObject(source);
        // The generic Shield codec queues this captured entry for the later player refresh.
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, captured.dynamicObjects().getFirst());
        new DefaultPowerUpSpawner(manager).spawnShield(player, ShieldType.BASIC);
        assertEquals("creator", manager.rewindSnapshottable().capture().dynamicObjects().getFirst().ownerModId());
    }

    @Test
    void objectManagerRoundTripPreservesDynamicThenFixedCollisionOrderAndPartialBuild() {
        ObjectManager objectManager = new ObjectManager(List.of(), null, 0, null, null);
        TestablePlayableSprite sonic =
                new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        RewindablePublisher dynamic = new RewindablePublisher(
                new ObjectSpawn(100, 200, 1, 0, 0, false, 0));
        objectManager.addDynamicObject(dynamic);
        FixedPowerUpPublisher fixed = new FixedPowerUpPublisher();
        objectManager.addDynamicObjectAtSlot(fixed, 50);
        objectManager.registerInitialFixedDispatchObject(fixed);

        try (InitialObjectDispatchScope scope =
                     objectManager.beginInitialProcessSprites(0, sonic, List.of())) {
            objectManager.freezeInitialCollisionResponseReadView();
            objectManager.resetInitialCollisionResponseBuild();
            objectManager.processInitialDynamicSlots(scope);
            objectManager.processInitialFixedDispatchObject(scope, fixed);
            objectManager.finishInitialProcessSprites(scope);
        }
        assertEquals(1, fixed.updates);
        ObjectManagerSnapshot snapshot;
        objectManager.registerInitialFixedDispatchObject(fixed);
        try (InitialObjectDispatchScope scope =
                     objectManager.beginInitialProcessSprites(0, sonic, List.of())) {
            objectManager.freezeInitialCollisionResponseReadView();
            objectManager.resetInitialCollisionResponseBuild();
            objectManager.processInitialDynamicSlots(scope);
        }
        snapshot = objectManager.rewindSnapshottable().capture();

        assertEquals(2, snapshot.collisionResponseState().previousCollisionObjectIds().size());
        assertEquals(1, snapshot.collisionResponseState().currentCollisionBuildObjectIds().size());
        assertTrue(snapshot.collisionResponseState().usePreviousCollisionList());
        assertEquals(1, snapshot.collisionResponseState().currentCollisionBuildCursor());
        assertEquals(ObjectManagerSnapshot.CollisionBuildStage.CURRENT_BUILDING,
                snapshot.collisionResponseState().collisionBuildStage());

        objectManager.rewindSnapshottable().restore(snapshot);
        RewindablePublisher restoredDynamic =
                objectManager.activeObjectsOfType(RewindablePublisher.class).getFirst();
        FixedPowerUpPublisher restoredFixed =
                objectManager.activeObjectsOfType(FixedPowerUpPublisher.class).getFirst();

        assertEquals(List.of(restoredDynamic, restoredFixed),
                objectManager.getTouchResponseObjects());
        ObjectManagerSnapshot roundTrip = objectManager.rewindSnapshottable().capture();
        assertEquals(snapshot.collisionResponseState(), roundTrip.collisionResponseState(),
                "production capture/restore must preserve ordered previous/current ids "
                        + "and the selected read buffer");
        assertEquals(snapshot.dynamicObjectIdCounter(), roundTrip.dynamicObjectIdCounter(),
                "restoring the captured fixed power-up id must not consume a fresh ordinal");

        NullSpawnPublisher next = new NullSpawnPublisher();
        objectManager.addDynamicObject(next);
        ObjectManagerSnapshot afterNextSpawn = objectManager.rewindSnapshottable().capture();
        ObjectManagerSnapshot.DynamicObjectEntry nextEntry = afterNextSpawn.dynamicObjects()
                .stream()
                .filter(entry -> entry.className().equals(NullSpawnPublisher.class.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(snapshot.dynamicObjectIdCounter(), nextEntry.objectId().dynamicId(),
                "the next genuine spawn must mint the same id it would have without restore");
    }

    @Test
    void reversedStarsRecreationRetainsEachPlayersCapturedIdentity() {
        ObjectManager objectManager = new ObjectManager(List.of(), null, 0, null, null);
        TestablePlayableSprite p1 =
                new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        TestablePlayableSprite p2 =
                new TestablePlayableSprite("tails", (short) 140, (short) 200);
        DefaultPowerUpSpawner sourceSpawner = new DefaultPowerUpSpawner(objectManager);
        sourceSpawner.spawnInvincibilityStars(p1);
        sourceSpawner.spawnInvincibilityStars(p2);
        ObjectManagerSnapshot source = objectManager.rewindSnapshottable().capture();
        var p1Id = source.dynamicObjects().stream()
                .filter(entry -> entry.playerOwner() == p1)
                .findFirst().orElseThrow().objectId();
        var p2Id = source.dynamicObjects().stream()
                .filter(entry -> entry.playerOwner() == p2)
                .findFirst().orElseThrow().objectId();
        assertNotEquals(p1Id, p2Id);

        objectManager.rewindSnapshottable().restore(source);
        DefaultPowerUpSpawner restoredSpawner = new DefaultPowerUpSpawner(objectManager);
        ObjectInstance restoredP2 =
                (ObjectInstance) restoredSpawner.spawnInvincibilityStars(p2);
        ObjectInstance restoredP1 =
                (ObjectInstance) restoredSpawner.spawnInvincibilityStars(p1);
        ObjectManagerSnapshot reversed = objectManager.rewindSnapshottable().capture();

        assertEquals(p1Id, idFor(reversed, restoredP1));
        assertEquals(p2Id, idFor(reversed, restoredP2));
        assertNotEquals(idFor(reversed, restoredP1), idFor(reversed, restoredP2));
        assertEquals(source.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                reversed.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                "late player-bound recreation preserves the captured order, including fixed-slot aliases");
    }

    @Test
    void pendingShieldRespawnMatchesOwnerInsteadOfFifoForSameConcreteType() {
        ObjectManager objectManager = new ObjectManager(List.of(), null, 0, null, null);
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(objectManager);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 140, (short) 200);

        objectManager.enqueuePendingPlayerBoundEntry(
                ShieldObjectInstance.class, shieldEntryFor(objectManager, tails, 50));
        objectManager.enqueuePendingPlayerBoundEntry(
                ShieldObjectInstance.class, shieldEntryFor(objectManager, sonic, 60));

        PowerUpObject sonicShield = spawner.spawnShield(sonic, ShieldType.BASIC);
        ShieldObjectInstance sonicShieldObject = assertInstanceOf(ShieldObjectInstance.class, sonicShield);
        assertSame(sonic, sonicShieldObject.getPlayer(),
                "respawned shield must stay bound to the requesting player");
        assertEquals(60, sonicShieldObject.getSlotIndex(),
                "sonic must consume sonic's pending shield entry, not the FIFO head");

        PowerUpObject tailsShield = spawner.spawnShield(tails, ShieldType.BASIC);
        ShieldObjectInstance tailsShieldObject = assertInstanceOf(ShieldObjectInstance.class, tailsShield);
        assertSame(tails, tailsShieldObject.getPlayer());
        assertEquals(50, tailsShieldObject.getSlotIndex(),
                "tails' entry should remain pending until tails refreshes");
    }

    @Test
    void freshRegisteredOwnersRestoreCapturedIdsInReverseRefreshOrderAndLeaveNextOrdinal() {
        var owners = new RegisteredOwners();
        var p1 = player("sonic");
        var p2 = player("tails");
        owners.roster(p1, p2);
        var spawner = new DefaultPowerUpSpawner(owners.manager);
        spawner.spawnShield(p1, ShieldType.BASIC);
        spawner.spawnShield(p2, ShieldType.BASIC);
        var saved = owners.manager.rewindSnapshottable().capture();
        var p1Entry = saved.dynamicObjects().stream()
                .filter(e -> PlayerRefId.mainPlayer().equals(e.playerOwnerId())).findFirst().orElseThrow();
        var p2Entry = saved.dynamicObjects().stream()
                .filter(e -> PlayerRefId.sidekick(0).equals(e.playerOwnerId())).findFirst().orElseThrow();
        assertNull(p1Entry.playerOwner(), "modern snapshots contain no player incarnation reference");
        assertNull(p2Entry.playerOwner());

        var freshP1 = player("sonic");
        var freshP2 = player("tails");
        owners.roster(freshP1, freshP2);
        owners.manager.rewindSnapshottable().restore(saved);
        var restoredP2 = (ObjectInstance) spawner.spawnShield(freshP2, ShieldType.BASIC);
        var restoredP1 = (ObjectInstance) spawner.spawnShield(freshP1, ShieldType.BASIC);
        var ids = owners.manager.captureIdentityContext().requireIdentityTable();
        assertEquals(p1Entry.objectId(), ids.idFor(restoredP1));
        assertEquals(p2Entry.objectId(), ids.idFor(restoredP2));
        var restored = owners.manager.rewindSnapshottable().capture();
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", saved, restored),
                () -> RewindSnapshotDiff.diffKey("object-manager", saved, restored).toString());
        assertEquals(saved.dynamicObjectIdCounter(), restored.dynamicObjectIdCounter());

        // Generic probes retry deferred entries. No duplicate may survive to steal this real spawn.
        var next = (ObjectInstance) spawner.spawnShield(freshP1, ShieldType.BASIC);
        assertEquals(saved.dynamicObjectIdCounter(), owners.manager.captureIdentityContext()
                .requireIdentityTable().idFor(next).dynamicId());
    }

    @Test
    void capturedRegisteredOwnerMissingFromRestoredRosterFailsClosed() {
        var owners = new RegisteredOwners();
        var p1 = player("sonic");
        var p2 = player("tails");
        owners.roster(p1, p2);
        new DefaultPowerUpSpawner(owners.manager).spawnShield(p2, ShieldType.BASIC);
        var saved = owners.manager.rewindSnapshottable().capture();
        assertEquals(PlayerRefId.sidekick(0), saved.dynamicObjects().getFirst().playerOwnerId());
        owners.manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class,
                shieldEntryFor(owners.manager, player("unregistered"), 60));
        owners.roster(player("sonic"));
        var failure = assertThrows(IllegalStateException.class,
                () -> owners.manager.rewindSnapshottable().restore(saved));
        assertTrue(failure.getMessage().contains("Missing required player reference"));
        assertNull(owners.manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class),
                "even an early failed restore retires the previous pending lifetime");
    }

    @Test
    void wrongClassAndUnregisteredOwnerDoNotConsumeCapturedShield() {
        var owners = new RegisteredOwners();
        var old = player("sonic");
        owners.roster(old);
        var spawner = new DefaultPowerUpSpawner(owners.manager);
        spawner.spawnShield(old, ShieldType.BASIC);
        var saved = owners.manager.rewindSnapshottable().capture();
        var expectedId = saved.dynamicObjects().getFirst().objectId();
        var fresh = player("sonic");
        owners.roster(fresh);
        owners.manager.rewindSnapshottable().restore(saved);
        var otherClass = ObjectConstructionContext.construct(owners,
                () -> new OtherShield(fresh));
        spawner.registerObject(otherClass);
        var otherOwner = (ObjectInstance) spawner.spawnShield(player("sonic"), ShieldType.BASIC);
        var restored = (ObjectInstance) spawner.spawnShield(fresh, ShieldType.BASIC);
        var ids = owners.manager.captureIdentityContext().requireIdentityTable();
        assertEquals(expectedId, ids.idFor(restored));
        assertNotEquals(expectedId, ids.idFor(otherClass));
        assertNotEquals(expectedId, ids.idFor(otherOwner));
        assertEquals(saved.dynamicObjectIdCounter() + 2,
                owners.manager.rewindSnapshottable().capture().dynamicObjectIdCounter());
    }

    @Test
    void pendingRetryUsesEntryIdentityAndRejectsDistinctCapturedIdCollision() {
        var manager = new ObjectManager(List.of(), null, 0, null, null);
        var owner = player("sonic");
        new DefaultPowerUpSpawner(manager).spawnShield(owner, ShieldType.BASIC);
        var entry = manager.rewindSnapshottable().capture().dynamicObjects().getFirst();
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, entry);
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, entry);
        var conflicting = new ObjectManagerSnapshot.DynamicObjectEntry(entry.className(), entry.spawn(),
                entry.slotIndex(), entry.state(), entry.playerOwner(), entry.objectId(), entry.ownerModId(),
                entry.rewindableAuxiliary(), entry.playerOwnerId());
        assertThrows(IllegalStateException.class,
                () -> manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, conflicting));
        assertSame(entry, manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, entry);
        assertNull(manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
        assertThrows(IllegalStateException.class,
                () -> manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, conflicting));
    }

    @Test
    void equalLegacyPendingEntriesStayDistinctAndResetRetiresAdmission() {
        var manager = new ObjectManager(List.of(), null, 0, null, null);
        var entry = shieldEntryFor(manager, player("sonic"), 60);
        var equalCopy = new ObjectManagerSnapshot.DynamicObjectEntry(entry.className(), entry.spawn(),
                entry.slotIndex(), entry.state(), entry.playerOwner());
        assertEquals(entry, equalCopy);
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, entry);
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, equalCopy);
        assertSame(entry, manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
        assertSame(equalCopy, manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
        assertNull(manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
        manager.reset(0);
        manager.enqueuePendingPlayerBoundEntry(ShieldObjectInstance.class, entry);
        assertSame(entry, manager.consumePendingPlayerBoundEntry(ShieldObjectInstance.class));
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 100, (short) 200);
    }

    private static final class RegisteredOwners extends StubObjectServices {
        private final Camera camera = new Camera();
        private List<PlayableEntity> sidekicks = List.of();
        private final ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null,
                null, camera, this);
        void roster(TestablePlayableSprite main, PlayableEntity... others) {
            camera.setFocusedSprite(main);
            sidekicks = List.of(others);
        }
        @Override public Camera camera() { return camera; }
        @Override public List<PlayableEntity> sidekicks() { return sidekicks; }
        @Override public ObjectManager objectManager() { return manager; }
    }

    private static final class OtherShield extends ShieldObjectInstance {
        OtherShield(PlayableEntity owner) { super(owner); }
    }

    private static ObjectManagerSnapshot.DynamicObjectEntry shieldEntryFor(
            ObjectManager objectManager, TestablePlayableSprite player, int slotIndex) {
        ShieldObjectInstance source = ObjectConstructionContext.construct(
                objectManager.services(), () -> new ShieldObjectInstance(player));
        source.setSlotIndex(slotIndex);
        return new ObjectManagerSnapshot.DynamicObjectEntry(
                ShieldObjectInstance.class.getName(),
                null,
                slotIndex,
                source.captureRewindState(),
                player);
    }

    private static com.openggf.game.rewind.identity.ObjectRefId idFor(
            ObjectManagerSnapshot snapshot, ObjectInstance object) {
        return snapshot.dynamicObjects().stream()
                .filter(entry -> entry.slotIndex()
                        == ((AbstractObjectInstance) object).getSlotIndex())
                .filter(entry -> entry.playerOwner()
                        == ((PowerUpObject) object).boundPlayer())
                .findFirst()
                .orElseThrow()
                .objectId();
    }

    private static final class RewindablePublisher extends AbstractObjectInstance
            implements RewindRecreatable, TouchResponseProvider {
        private RewindablePublisher(ObjectSpawn spawn) {
            super(spawn, "RewindablePublisher");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override public int getCollisionFlags() { return 0xC7; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new RewindablePublisher(getSpawn());
        }
    }

    private static final class NullSpawnPublisher extends AbstractObjectInstance
            implements RewindRecreatable {
        private NullSpawnPublisher() {
            super(null, "NullSpawnPublisher");
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) {}
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new NullSpawnPublisher();
        }
    }

    private static final class FixedPowerUpPublisher extends AbstractObjectInstance
            implements PowerUpObject, TouchResponseProvider, RewindRecreatable {
        private int updates;
        private FixedPowerUpPublisher() {
            super(null, "FixedPowerUpPublisher");
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) { updates++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
        @Override public void destroy() { setDestroyed(true); }
        @Override public void setVisible(boolean visible) {}
        @Override public int getCollisionFlags() { return 0xC7; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new FixedPowerUpPublisher();
        }
    }
}
