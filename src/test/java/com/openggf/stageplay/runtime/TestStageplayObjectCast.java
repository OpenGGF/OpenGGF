package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayDocument.Facing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.objects.StageplayObjectDirection.*;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayObjectCast {
    private static ObjectActor actor(String game, int zone, int act) {
        return new ObjectActor("object", "Object", game, zone, act, 1, 0, -1,
                List.of(new ObjectKey(0, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
    }

    @Test void nativeFreeRequiresExactSourceContextWithActionableFailure() {
        for (String game : List.of("s1", "s2", "s3k")) {
            var actor = actor(game, 2, 1);
            assertDoesNotThrow(() -> StageplayObjectCast.requireNativeContext(actor,
                    new SetSource(game, 2, 1, "", "")));
            for (var source : List.of(new SetSource(game, 3, 1, "", ""),
                    new SetSource(game, 2, 0, "", ""),
                    new SetSource(game.equals("s1") ? "s2" : "s1", 2, 1, "", ""))) {
                var error = assertThrows(IllegalArgumentException.class, () -> StageplayObjectCast.requireNativeContext(actor, source));
                assertTrue(error.getMessage().contains("native source set " + game + "/2/1"));
                assertTrue(error.getMessage().contains("Puppet/Pose"));
            }
        }
    }

    @Test void authoredMovementHasBoundedSpeedAndStopsAtDestinationWithoutOvershoot() {
        double[] position = {0, 0};
        for (int frame = 0; frame < 5; frame++) {
            var next = StageplayObjectCast.move(position[0], position[1], 3, 4, 1);
            assertTrue(StrictMath.hypot(next[0] - position[0], next[1] - position[1]) <= 1.000000001);
            position = next;
        }
        assertArrayEquals(new double[]{3, 4}, position, 1e-12);
        assertArrayEquals(new double[]{3, 4}, StageplayObjectCast.move(3, 4, 3, 4, 1));
        assertArrayEquals(new double[]{1, 0}, StageplayObjectCast.move(0, 0, 1, 0, 20));
    }

    @Test void nativeAnchorsRejectWrappedCoordinatesAndKeepSourceFlagsIndependentOfFacing() {
        var actor = actor("s2", 0, 0);
        var key = new ObjectKey(0, 100.4, 200.4, 0, -1, Facing.RIGHT, true, Interpolation.HOLD, ObjectMode.FREE);
        var spawn = StageplayObjectTemplateBake.spawn(actor, key);
        assertEquals(100, spawn.x()); assertEquals(200, spawn.y()); assertEquals(0, spawn.renderFlags());
        assertEquals(-1, spawn.layoutIndex()); assertFalse(spawn.respawnTracked());
        for (double coordinate : List.of(-1.0, 65536.0))
            assertThrows(IllegalArgumentException.class, () -> StageplayObjectTemplateBake.spawn(actor,
                    new ObjectKey(0, coordinate, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.FREE)));
    }

    @Test void inactivePlacedPuppetUsesOnlyPreparedVisualAcrossOpeningClearAndReplay() {
        var fixture = new PlacedFixture();
        var role = new ObjectActor("loaded", "Loaded", "s2", 0, 0, 1, 7, 8, List.of(
                new ObjectKey(0, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of());
        var scene = com.openggf.stageplay.StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withObjectActors(List.of(role))
                .withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none()),
                        new WorldCommand("remove", 10, WorldCommandKind.REMOVE_OBJECT, "loaded", ObjectTarget.none())));
        try (var cast = new StageplayObjectCast(fixture.context, scene, scene.set(), 0, scene.durationFrames(),
                List.of(fixture.spawn), Map.of("loaded", fixture.template), scene.objectActors().stream().collect(java.util.stream.Collectors.toMap(ObjectActor::id, a -> 0)))) {
            cast.beforeTick(0);
            assertEquals(0, fixture.constructors.get(), "Directed placement must never invoke a shared-world factory");
            assertTrue(fixture.objects.getActiveObjects().isEmpty());
            assertEquals(0, fixture.objects.getAllocatedSlotCount());
            assertEquals(8, cast.visualObservations().getFirst().layoutIndex());
            assertFalse(fixture.direction.allowsPlacement(fixture.spawn));
            var control = cast.capture(); var graph = fixture.direction.rewindSnapshottable().capture();
            var sample = cast.visualObservations();
            cast.beforeTick(10); assertTrue(cast.visualObservations().isEmpty());
            fixture.direction.rewindSnapshottable().restore(graph); cast.restore(control);
            assertEquals(sample, cast.visualObservations());
            assertEquals(0, fixture.constructors.get()); assertEquals(0, fixture.updates.get());
        }
    }

    @Test void explicitPlacedFreeReadmissionBypassesClearWithoutReopeningVanillaPlacement() {
        var fixture = new PlacedFixture();
        fixture.direction.clearObjects();
        assertFalse(fixture.direction.allowsPlacement(fixture.spawn));
        var root = fixture.direction.materializePlaced(fixture.spawn);
        assertTrue(fixture.objects.getActiveObjects().contains(root));
        assertSame(fixture.spawn, root.getSpawn());
        assertEquals(1, fixture.constructors.get());
        assertFalse(fixture.direction.allowsPlacement(fixture.spawn), "Explicit admission must not reopen the vanilla layout");
        assertSame(root, fixture.direction.materializePlaced(fixture.spawn));
        assertEquals(1, fixture.constructors.get(), "Repeated portable admission must return the same native instance");
    }

    @Test void authoredFreeBadnikOutsideSharedEnemyBaseUsesItsRegistryTraitWhenCleared() {
        var fixture = new PlacedFixture(com.openggf.game.GameId.S1);
        int bomb = com.openggf.game.sonic1.constants.Sonic1ObjectIds.BOMB;
        var role = new ObjectActor("bomb", "Bomb", "s1", 0, 0, bomb, 0, -1, List.of(
                new ObjectKey(0, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
        var scene = com.openggf.stageplay.StageplayDocument.empty("s1", 0, 0, 0, 0, 320, 224)
                .withObjectActors(List.of(role)).withWorldCommands(List.of(
                        new WorldCommand("clear", 1, WorldCommandKind.CLEAR_BADNIKS, "", ObjectTarget.none())));
        try (var cast = new StageplayObjectCast(fixture.context, scene, scene.set(), 0,
                scene.durationFrames(), List.of(fixture.spawn), Map.of(), scene.objectActors().stream().collect(java.util.stream.Collectors.toMap(ObjectActor::id, a -> 0)))) {
            cast.beforeTick(0);
            var nativeRoot = fixture.objects.getActiveObjects().stream()
                    .filter(object -> object.getSpawn().objectId() == bomb).findFirst().orElseThrow();
            assertFalse(nativeRoot instanceof AbstractBadnikInstance);
            assertEquals(com.openggf.game.mutators.ObjectHeadRole.NONE, nativeRoot.headPresentationRole());
            cast.beforeTick(1);
            assertFalse(fixture.objects.getActiveObjects().contains(nativeRoot));
            assertTrue(fixture.objects.getActiveObjects().stream()
                    .noneMatch(object -> object.getSpawn().objectId() == bomb));
        }
    }

    @Test void placedVisualReturningToFreeUsesExactOriginalPlacementInsteadOfVisualAnchor() {
        var fixture = new PlacedFixture();
        var role = new ObjectActor("loaded", "Loaded", "s2", 0, 0, 1, 7, 8, List.of(
                new ObjectKey(0, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.POSE),
                new ObjectKey(10, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
        var scene = com.openggf.stageplay.StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withObjectActors(List.of(role));
        try (var cast = new StageplayObjectCast(fixture.context, scene, scene.set(), 0, scene.durationFrames(),
                List.of(fixture.spawn), Map.of("loaded", fixture.template), scene.objectActors().stream().collect(java.util.stream.Collectors.toMap(ObjectActor::id, a -> 0)))) {
            cast.beforeTick(0); assertEquals(0, fixture.constructors.get());
            cast.beforeTick(10);
            assertEquals(1, fixture.constructors.get()); assertSame(fixture.spawn, fixture.constructed.get());
            var nativeRoot = fixture.objects.getActiveObjects().iterator().next();
            assertEquals(5000, nativeRoot.getX()); assertEquals(80, nativeRoot.getY());
            assertEquals(3, nativeRoot.getSpawn().renderFlags()); assertTrue(nativeRoot.getSpawn().respawnTracked());
            assertEquals(0xA050, nativeRoot.getSpawn().rawYWord());
            assertTrue(cast.visualObservations().isEmpty()); assertEquals(0, fixture.updates.get());
        }
    }

    @Test void nativeSourceChangeFailsBeforePlacedRolesCanMaterializeInTheDestinationAct() {
        var fixture = new PlacedFixture();
        var role = new ObjectActor("loaded", "Loaded", "s2", 0, 0, 1, 7, 8,
                List.of(new ObjectKey(0, 100, 200, 0, -1, Facing.LEFT, true, Interpolation.HOLD, ObjectMode.PUPPET)), List.of());
        var scene = com.openggf.stageplay.StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withObjectActors(List.of(role));
        try (var cast = new StageplayObjectCast(fixture.context, scene, scene.set(), 0, scene.durationFrames(),
                List.of(fixture.spawn), Map.of("loaded", fixture.template), scene.objectActors().stream().collect(java.util.stream.Collectors.toMap(ObjectActor::id, a -> 0)))) {
            cast.beforeTick(0);
            when(fixture.context.getLevelManager().getCurrentAct()).thenReturn(1);
            assertTrue(assertThrows(IllegalStateException.class, () -> cast.beforeTick(1)).getMessage().contains("use a new shot"));
            assertEquals(0, fixture.constructors.get());
        }
    }

    private static final class PlacedFixture {
        final AtomicInteger constructors = new AtomicInteger(), updates = new AtomicInteger();
        final AtomicReference<ObjectSpawn> constructed = new AtomicReference<>();
        final ObjectSpawn spawn = new ObjectSpawn(5000, 80, 1, 7, 3, true, 0xA050, 8);
        final GameplayModeContext context = mock(GameplayModeContext.class);
        final ObjectManager objects;
        final StageplayObjectDirection direction;
        final VisualTemplate template = new VisualTemplate( 5000, 80, com.openggf.level.objects.ObjectVisualFacing.Basis.RIGHT, List.of(new VisualPart("root/0", 4, false,
                0, 0, com.openggf.level.objects.ObjectVisualFacing.Basis.RIGHT, 0, List.of(new VisualPose(0, List.of(), List.of())))), List.of(), List.of(), List.of());
        PlacedFixture() { this(com.openggf.game.GameId.S2); }
        PlacedFixture(com.openggf.game.GameId game) {
            var graphics = new GraphicsManager(); graphics.setHeadlessMode(true);
            var services = mock(ObjectServices.class);
            when(services.graphicsManager()).thenReturn(graphics);
            var session = mock(com.openggf.game.session.WorldSession.class);
            var module = mock(com.openggf.game.GameModule.class);
            when(module.getGameId()).thenReturn(game);
            when(session.getGameModule()).thenReturn(module);
            when(services.worldSession()).thenReturn(session);
            when(context.getWorldSession()).thenReturn(session);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            var registry = new ObjectRegistry() {
                public ObjectInstance create(ObjectSpawn source) {
                    constructors.incrementAndGet(); constructed.set(source);
                    return new AbstractObjectInstance(source, "Side effect probe") {
                        public void update(int vIntRunCount, PlayableEntity player) { updates.incrementAndGet(); }
                        public void appendRenderCommands(List<GLCommand> commands) { }
                    };
                }
                public void reportCoverage(List<ObjectSpawn> spawns) { }
                public String getPrimaryName(int id) { return "Side effect probe"; }
            };
            objects = new ObjectManager(List.of(spawn), registry, 0, null, null, graphics, mock(Camera.class), services);
            when(services.objectManager()).thenReturn(objects);
            var level = mock(LevelManager.class);
            when(level.getObjectManager()).thenReturn(objects); when(context.getLevelManager()).thenReturn(level);
            when(context.getRewindRegistry()).thenReturn(mock(RewindRegistry.class));
            direction = ObjectDirectionAccess.forManager(objects);
        }
    }
}
