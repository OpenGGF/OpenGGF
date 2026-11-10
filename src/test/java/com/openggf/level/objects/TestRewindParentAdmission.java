package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
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
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

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

/** Real policy admission must agree with the identities offered to child recreation. */
class TestRewindParentAdmission {
    private static final ObjectSpawn BODY_SPAWN = new ObjectSpawn(
            0x01C0, 0x0150, Sonic2ObjectIds.SHELLCRACKER, 0, 0, false, 0x0150, 7);
    private static final String ACTOR_ID = "authored-shellcracker";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeRestoredClawsNeverKeepAnUnadmittedParent(boolean clearObjects) throws Exception {
        var fixture = new Fixture(clearObjects);

        ShellcrackerBadnikInstance rebuilt = fixture.restoreFresh();

        List<ShellcrackerClawInstance> claws = fixture.claws();
        assertEquals(8, claws.size(), "captured claws retain their independent lifetime");
        if (!clearObjects) {
            assertSame(rebuilt, fixture.objects.objectQuery().resolve(fixture.parentId).orElse(null));
        }
        for (ShellcrackerClawInstance claw : claws) {
            assertTrue(fixture.direction.allowsObject(claw),
                    "a surviving authored claw remains eligible for a native routine");
            Object parent = parentOf(claw);
            if (clearObjects) {
                assertNull(parent, "a refused body must not remain the claw's parent");
            } else {
                assertSame(rebuilt, parent, "ordinary scene admission must retain the exact rebuilt owner");
                assertTrue(fixture.objects.getActiveObjects().contains(parent),
                        "active claw retains an inactive parent: clearObjects=" + clearObjects
                                + ", activeClaws=" + claws.size()
                                + ", parentDestroyed=" + ((ObjectInstance) parent).isDestroyed());
            }
        }
        if (clearObjects) {
            var state = ShellcrackerClawInstance.class.getDeclaredField("state");
            state.setAccessible(true);
            for (ShellcrackerClawInstance claw : claws) {
                assertFalse(claw.isDestroyed());
                claw.update(0, null); // Consume the captured init-only execution.
                claw.update(1, null); // Ordinary parent loss enters independent fall.
                assertEquals("FALLING", state.get(claw).toString());
                // Bounded liveness, without pinning transition-row motion or gravity order.
                for (int vIntRunCount = 2; vIntRunCount < 82 && !claw.isDestroyed(); vIntRunCount++) {
                    claw.update(vIntRunCount, null);
                }
                assertTrue(claw.isDestroyed(), "a null-parent piece must expire through its falling lifetime");
            }
        }
    }

    private static Object parentOf(ShellcrackerClawInstance claw) throws ReflectiveOperationException {
        var field = ShellcrackerClawInstance.class.getDeclaredField("parent");
        field.setAccessible(true);
        return field.get(claw);
    }

    private static final class Fixture {
        final StageplayObjectLoadPolicy policy = new StageplayObjectLoadPolicy();
        final List<ShellcrackerBadnikInstance> constructedBodies = new ArrayList<>();
        final ObjectManager objects;
        final StageplayObjectDirection direction;
        final ShellcrackerBadnikInstance original;
        final ObjectRefId parentId;
        final RewindRegistry rewind = new RewindRegistry();
        final CompositeSnapshot captured;

        Fixture(boolean clearObjects) throws ReflectiveOperationException {
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
                @Override
                public ObjectInstance create(ObjectSpawn spawn) {
                    var body = new ShellcrackerBadnikInstance(spawn);
                    constructedBodies.add(body);
                    return body;
                }

                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "Shellcracker"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
            };
            objects = new ObjectManager(List.of(BODY_SPAWN), registry, -1, null, null,
                    graphics, mock(Camera.class), services);
            when(services.objectManager()).thenReturn(objects);
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            direction = ObjectDirectionAccess.forManager(objects);
            assertSame(policy, WorldSessionPolicyAccess.getService(world, StageplayObjectLoadPolicy.class));
            if (clearObjects) {
                direction.clearObjects();
            }

            // Public explicit casting may re-admit a canonical placement while
            // native placement scans remain suppressed by clearObjects.
            original = assertInstanceOf(ShellcrackerBadnikInstance.class,
                    direction.materializePlaced(BODY_SPAWN));
            direction.claimAuthored(ACTOR_ID, original);
            assertEquals(!clearObjects, direction.allowsPlacement(BODY_SPAWN));
            assertTrue(direction.allowsObject(original));
            parentId = objects.objectQuery().identityOf(original).orElseThrow();
            var spawn = ShellcrackerBadnikInstance.class.getDeclaredMethod("spawnClawPieces");
            spawn.setAccessible(true);
            // This is the production factory and its real spawnChild path,
            // inside the public authored-admission scope, not fabricated family data.
            direction.withAuthoredAdmission(ACTOR_ID, () -> {
                assertDoesNotThrow(() -> { spawn.invoke(original); });
                return null;
            });
            assertEquals(8, claws().size());
            for (var claw : claws()) {
                assertSame(original, parentOf(claw));
            }

            // Match gameplay composition: world policy restores before objects,
            // then direction rebinds authored identities after object reconstruction.
            WorldSessionPolicyAccess.registerRewind(world, rewind);
            var objectAdapter = objects.rewindSnapshottable();
            rewind.register(objectAdapter);
            var directionAdapter = direction.rewindSnapshottable();
            rewind.register(directionAdapter);
            captured = rewind.capture();
            var savedPolicy = (StageplayObjectLoadPolicy.Snapshot) captured.get(policy.rewindAdapter().key());
            assertEquals(clearObjects, savedPolicy.clearObjects());
            var savedDirection = (StageplayObjectDirection.Snapshot) captured.get(directionAdapter.key());
            assertEquals(ACTOR_ID, savedDirection.authored().get(parentId));
            var savedObjects = (ObjectManagerSnapshot) captured.get(objectAdapter.key());
            assertEquals(8, savedObjects.dynamicObjects().size());
            for (var child : savedObjects.dynamicObjects()) {
                assertEquals(ShellcrackerClawInstance.class.getName(), child.className());
                assertNotNull(child.constructionFamily());
                assertTrue(child.constructionFamily().known());
                assertEquals(parentId, child.constructionFamily().parentId());
                assertEquals(ACTOR_ID, savedDirection.authored().get(child.objectId()));
            }
        }

        ShellcrackerBadnikInstance restoreFresh() {
            direction.removeDynamic(original);
            assertTrue(objects.getActiveObjects().isEmpty());
            rewind.restore(captured);
            assertEquals(2, constructedBodies.size(), "fresh restore must run the actual placement factory");
            ShellcrackerBadnikInstance rebuilt = constructedBodies.getLast();
            assertNotSame(original, rebuilt);
            assertFalse(rebuilt.isDestroyed(), "the suspected rejected owner is still an alive object");
            return rebuilt;
        }

        List<ShellcrackerClawInstance> claws() {
            return objects.getActiveObjects().stream()
                    .filter(ShellcrackerClawInstance.class::isInstance)
                    .map(ShellcrackerClawInstance.class::cast)
                    .toList();
        }
    }
}
