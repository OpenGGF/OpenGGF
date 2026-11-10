package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.badniks.ShellcrackerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.ShellcrackerClawInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Captured placement publication is not a request for fresh scene admission. */
class TestCapturedPlacedPopulationRestore {
    private static final ObjectSpawn BODY = new ObjectSpawn(
            0x01C0, 0x0150, Sonic2ObjectIds.SHELLCRACKER, 0, 0, false, 0x0150, 7);
    private static final ObjectSpawn CONSTRUCTOR_PARENT = new ObjectSpawn(
            0x0100, 0x0120, 0x70, 0, 0, false, 0x0120, 8);
    private static final String CAPTURED_ACTOR = "captured-cast";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturedShellcrackerBodyAndAllClawsRestoreAsTheActiveAuthoredFamily(boolean clearObjects)
            throws ReflectiveOperationException {
        var fixture = new Fixture(clearObjects, BODY, ShellcrackerBadnikInstance::new);
        var original = assertInstanceOf(ShellcrackerBadnikInstance.class, fixture.original);
        var spawnClaws = ShellcrackerBadnikInstance.class.getDeclaredMethod("spawnClawPieces");
        spawnClaws.setAccessible(true);
        fixture.direction.withAuthoredAdmission(CAPTURED_ACTOR, () -> {
            assertDoesNotThrow(() -> { spawnClaws.invoke(original); });
            return null;
        });
        var originalClaws = fixture.objects.activeObjectsOfType(ShellcrackerClawInstance.class);
        assertEquals(8, originalClaws.size());
        var saved = fixture.capture();
        var objectState = fixture.objectState(saved);
        var parentEntry = objectState.slots().getFirst();
        assertEquals(8, objectState.dynamicObjects().size());
        for (var child : objectState.dynamicObjects()) {
            assertTrue(child.constructionFamily().known());
            assertEquals(parentEntry.objectId(), child.constructionFamily().parentId());
        }

        fixture.direction.removeDynamic(original);
        fixture.rewind.restore(saved);

        // Wrong branch: admitRestoredPlaced re-enters allowsObject while the
        // authored map still describes old instances. Null-parent claws do not
        // compensate for the captured body disappearing from live membership.
        var restoredParent = assertInstanceOf(ShellcrackerBadnikInstance.class,
                fixture.objects.objectQuery().resolve(parentEntry.objectId()).orElse(null),
                "the captured placed body must be active, even under clearObjects");
        assertSame(fixture.constructed.getLast(), restoredParent);
        assertNotSame(original, restoredParent);
        assertEquals(parentEntry.slotIndex(), restoredParent.getSlotIndex());
        assertEquals(9, fixture.objects.getActiveObjects().size());
        assertEquals(CAPTURED_ACTOR, fixture.direction.authoredId(restoredParent));
        for (var entry : objectState.dynamicObjects()) {
            var claw = assertInstanceOf(ShellcrackerClawInstance.class,
                    fixture.objects.objectQuery().resolve(entry.objectId()).orElse(null));
            assertFalse(originalClaws.contains(claw));
            assertEquals(entry.slotIndex(), claw.getSlotIndex());
            assertSame(restoredParent, clawParent(claw));
            assertEquals(CAPTURED_ACTOR, fixture.direction.authoredId(claw));
            assertTrue(fixture.direction.allowsObject(claw));
        }
        fixture.assertRecapturedRoster(objectState);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void constructorChildAdoptionKeepsItsCapturedPlacedParentActiveAcrossRestoreAndRecapture(boolean clearObjects) {
        var fixture = new Fixture(clearObjects, CONSTRUCTOR_PARENT, ConstructorParent::new);
        var original = assertInstanceOf(ConstructorParent.class, fixture.original);
        for (int i = 0; i < 5; i++) {
            original.child.update(i, null);
        }
        var target = fixture.capture();
        var objectState = fixture.objectState(target);
        var parentEntry = objectState.slots().getFirst();
        var childEntry = objectState.dynamicObjects().getFirst();
        assertEquals(1, objectState.dynamicObjects().size());
        assertTrue(childEntry.constructionFamily().known());
        assertEquals(parentEntry.objectId(), childEntry.constructionFamily().parentId());
        ConstructorParent previousParent = original;
        ConstructorChild previousChild = original.child;

        for (int repeat = 0; repeat < 3; repeat++) {
            fixture.direction.claimAuthored("future-cast", previousParent);
            var future = fixture.direction.withAuthoredAdmission("future-only", () ->
                    fixture.objects.createDynamicObject(() -> new FutureObject(
                            new ObjectSpawn(400, 200, 0x72, 0, 0, false, 200))));
            ObjectRefId futureId = fixture.objects.objectQuery().identityOf(future).orElseThrow();
            fixture.direction.removeDynamic(previousParent);
            fixture.rewind.restore(target);

            var rebuilt = assertInstanceOf(ConstructorParent.class, fixture.constructed.getLast());
            var child = assertInstanceOf(ConstructorChild.class,
                    fixture.objects.objectQuery().resolve(childEntry.objectId()).orElse(null),
                    "adoption must publish the actual constructor child, whose independent hook returns null");
            assertNotSame(previousParent, rebuilt);
            assertNotSame(previousChild, child);
            assertSame(rebuilt.child, child);
            assertSame(rebuilt, child.parent);
            assertFalse(rebuilt.isDestroyed());
            // A child can currently be adopted using a refused parent's table
            // entry. Check membership after proving actual adoption, not only links.
            assertSame(rebuilt, fixture.objects.objectQuery().resolve(parentEntry.objectId()).orElse(null),
                    "an adopted child must not be the only live member of its captured placed family");
            assertEquals(parentEntry.slotIndex(), rebuilt.getSlotIndex());
            assertEquals(childEntry.slotIndex(), child.getSlotIndex());
            assertEquals(2, fixture.objects.getActiveObjects().size());
            assertEquals(5, child.updates);
            assertEquals(CONSTRUCTOR_PARENT.x() + 0x10 + 15, child.getX());
            assertEquals(CAPTURED_ACTOR, fixture.direction.authoredId(rebuilt));
            assertEquals(CAPTURED_ACTOR, fixture.direction.authoredId(child));
            assertTrue(fixture.objects.objectQuery().resolve(futureId).isEmpty());
            assertFalse(fixture.objects.getActiveObjects().contains(future));
            assertEquals(1, fixture.direction.graph(rebuilt).stream().filter(member -> member == child).count());
            fixture.assertRecapturedRoster(objectState);
            target = fixture.capture(); // The last pass restores a real recapture, not a fabricated entry list.
            child.update(10 + repeat, null);
            previousParent = rebuilt;
            previousChild = child;
        }
    }

    @Test
    void warmedPlacedReuseRestoresTargetClaimsAndStillRejectsFreshUnclaimedPlacements() {
        var fixture = new Fixture(true, CONSTRUCTOR_PARENT, ReusableParent::new);
        fixture.objects.setRewindInPlaceRestoreEnabledForTest(true);
        var original = assertInstanceOf(ReusableParent.class, fixture.original);
        original.value = 17;
        var target = fixture.capture();
        var parentId = fixture.objectState(target).slots().getFirst().objectId();

        // One observed reconstruction without construction children is required
        // before the real in-place path becomes eligible.
        fixture.rewind.restore(target);
        var warmed = assertInstanceOf(ReusableParent.class,
                fixture.objects.objectQuery().resolve(parentId).orElseThrow());
        assertNotSame(original, warmed);
        assertEquals(2, fixture.constructed.size());
        warmed.value = 91;
        fixture.direction.claimAuthored("future-cast", warmed);
        var future = fixture.direction.withAuthoredAdmission("future-only", () ->
                fixture.objects.createDynamicObject(() -> new FutureObject(
                        new ObjectSpawn(400, 200, 0x72, 0, 0, false, 200))));
        var futureId = fixture.objects.objectQuery().identityOf(future).orElseThrow();

        fixture.rewind.restore(target);

        assertSame(warmed, fixture.objects.objectQuery().resolve(parentId).orElseThrow(),
                "this restore must actually reuse the warmed instance");
        assertEquals(2, fixture.constructed.size(), "reuse must not call the placement factory again");
        assertEquals(17, warmed.value);
        assertEquals(CAPTURED_ACTOR, fixture.direction.authoredId(warmed));
        assertTrue(fixture.objects.objectQuery().resolve(futureId).isEmpty());
        assertNull(fixture.direction.authoredId(future));
        assertEquals(List.of(warmed), new ArrayList<>(fixture.objects.getActiveObjects()));

        // This uses the ordinary placement registration path, outside explicit
        // casting. Captured publication must not reopen fresh scene admission.
        var unclaimedSpawn = new ObjectSpawn(500, 200, 0x73, 0, 0, false, 200);
        var unclaimed = new ReusableParent(unclaimedSpawn);
        fixture.objects.registerActiveObject(unclaimedSpawn, unclaimed);
        assertFalse(fixture.objects.getActiveObjects().contains(unclaimed));
        assertTrue(fixture.objects.objectQuery().identityOf(unclaimed).isEmpty());
        assertNull(fixture.direction.authoredId(unclaimed));
    }

    private static Object clawParent(ShellcrackerClawInstance claw) throws ReflectiveOperationException {
        var parent = ShellcrackerClawInstance.class.getDeclaredField("parent");
        parent.setAccessible(true);
        return parent.get(claw);
    }

    private static final class Fixture {
        final StageplayObjectLoadPolicy policy = new StageplayObjectLoadPolicy();
        final List<AbstractObjectInstance> constructed = new ArrayList<>();
        final ObjectManager objects;
        final StageplayObjectDirection direction;
        final AbstractObjectInstance original;
        final RewindRegistry rewind = new RewindRegistry();
        final String objectKey;

        Fixture(boolean clearObjects, ObjectSpawn spawn, Function<ObjectSpawn, AbstractObjectInstance> factory) {
            var graphics = new GraphicsManager();
            graphics.setHeadlessMode(true);
            var services = mock(ObjectServices.class);
            var module = mock(GameModule.class);
            when(module.getGameId()).thenReturn(GameId.S2);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
            var world = new WorldSession(module);
            when(services.worldSession()).thenReturn(world);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn source) {
                    var actor = factory.apply(source);
                    constructed.add(actor);
                    return actor;
                }
                @Override public void reportCoverage(List<ObjectSpawn> source) { }
                @Override public String getPrimaryName(int id) { return "captured placed family"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
            };
            objects = new ObjectManager(List.of(spawn), registry, -1, null, null,
                    graphics, mock(Camera.class), services);
            when(services.objectManager()).thenReturn(objects);
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            direction = ObjectDirectionAccess.forManager(objects);
            assertSame(policy, WorldSessionPolicyAccess.getService(world, StageplayObjectLoadPolicy.class));
            if (clearObjects) {
                direction.clearObjects();
            }
            original = assertInstanceOf(AbstractObjectInstance.class, direction.materializePlaced(spawn));
            direction.claimAuthored(CAPTURED_ACTOR, original);
            assertEquals(!clearObjects, direction.allowsPlacement(spawn));
            assertTrue(direction.allowsObject(original));
            // Same dependency order as gameplay composition; no early claims or
            // admission flags are fabricated for the reconstruction under test.
            WorldSessionPolicyAccess.registerRewind(world, rewind);
            var adapter = objects.rewindSnapshottable();
            objectKey = adapter.key();
            rewind.register(adapter);
            rewind.register(direction.rewindSnapshottable());
        }

        CompositeSnapshot capture() {
            return rewind.capture();
        }

        ObjectManagerSnapshot objectState(CompositeSnapshot saved) {
            return (ObjectManagerSnapshot) saved.get(objectKey);
        }

        void assertRecapturedRoster(ObjectManagerSnapshot expected) {
            var recaptured = objectState(capture());
            assertEquals(expected.slots().stream().map(ObjectManagerSnapshot.PerSlotEntry::objectId).toList(),
                    recaptured.slots().stream().map(ObjectManagerSnapshot.PerSlotEntry::objectId).toList());
            assertEquals(expected.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                    recaptured.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList());
            assertEquals(expected.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::constructionFamily).toList(),
                    recaptured.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::constructionFamily).toList());
            assertEquals(expected.dynamicObjectIdCounter(), recaptured.dynamicObjectIdCounter());
        }
    }

    private static final class ConstructorParent extends AbstractObjectInstance implements SpawnRewindRecreatable {
        final ConstructorChild child;

        ConstructorParent(ObjectSpawn spawn) {
            super(spawn, "placed-constructor-parent");
            child = spawnChild(() -> new ConstructorChild(this));
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class ConstructorChild extends AbstractObjectInstance implements RewindRecreatable {
        final ConstructorParent parent;
        int currentX;
        int updates;

        private ConstructorChild(ObjectSpawn spawn) {
            super(spawn, "adoption-only-child-probe");
            parent = null;
            currentX = spawn.x();
        }

        ConstructorChild(ConstructorParent parent) {
            super(new ObjectSpawn(parent.getX() + 0x10, parent.getY(), 0x71, 0, 0, false, parent.getY()),
                    "placed-constructor-child");
            this.parent = parent;
            currentX = spawn.x();
        }

        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) { return null; }
        @Override public void update(int vIntRunCount, PlayableEntity player) { currentX += 3; updates++; }
        @Override public int getX() { return currentX; }
        @Override public ObjectSpawn getSpawn() { return buildSpawnAt(currentX, spawn.y()); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class FutureObject extends AbstractObjectInstance implements SpawnRewindRecreatable {
        FutureObject(ObjectSpawn spawn) { super(spawn, "future-only"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class ReusableParent extends AbstractObjectInstance implements SpawnRewindRecreatable {
        int value;
        ReusableParent(ObjectSpawn spawn) { super(spawn, "reusable-placed-parent"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
