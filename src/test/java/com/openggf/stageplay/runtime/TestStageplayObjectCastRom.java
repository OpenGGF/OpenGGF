package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestStageplayObjectCastRom {
    @BeforeEach void graphics() { EngineServices.current().graphics().initHeadless(); }

    private static StageplayLiveWorld world(SceneRomLibrary library) {
        return SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
    }
    private static ObjectActor actor(String id, int objectId, double x) {
        return new ObjectActor(id, id, "s2", 0, 0, objectId, 0, -1, List.of(
                new ObjectKey(0, x, 600, 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
    }

    @Test void openingClearAllowsExplicitNativeCastThenBadnikClearKeepsObjectsAndRingsAndReplays() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 320, 224).withDurationFrames(45)
                    .withObjectActors(List.of(actor("wasp", Sonic2ObjectIds.BUZZER, 100), actor("monitor", Sonic2ObjectIds.MONITOR, 160)))
                    .withWorldCommands(List.of(new WorldCommand("empty", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none()),
                            new WorldCommand("badniks", 20, WorldCommandKind.CLEAR_BADNIKS, "", ObjectTarget.none())));
            var opening = world.resolve(scene, StageplayTimeline.sample(scene, 1));
            assertTrue(opening.world().objects().stream().anyMatch(o -> o.objectId() == Sonic2ObjectIds.BUZZER));
            assertTrue(opening.world().objects().stream().anyMatch(o -> o.objectId() == Sonic2ObjectIds.MONITOR));
            assertFalse(opening.world().rings().isEmpty(), "Clear objects preserves ROM ring placements");
            var ending = world.resolve(scene, StageplayTimeline.sample(scene, 40));
            assertFalse(ending.world().objects().stream().anyMatch(o -> o.objectId() == Sonic2ObjectIds.BUZZER));
            assertTrue(ending.world().objects().stream().anyMatch(o -> o.objectId() == Sonic2ObjectIds.MONITOR));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(ending, world.resolve(scene, StageplayTimeline.sample(scene, 40)));
        }
    }

    @Test void sampleListsInactivePortablePlacementsAndRemovalPersistsAcrossForwardReplay() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 320, 224).withDurationFrames(30);
            var original = library.level("s2", 0, 0).getObjects();
            var target = original.getLast();
            var sample = world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertTrue(sample.world().objects().stream().anyMatch(o -> o.layoutIndex() == target.layoutIndex()
                    && o.objectId() == target.objectId() && o.subtype() == target.subtype()));
            scene = scene.withWorldCommands(List.of(new WorldCommand("remove", 1, WorldCommandKind.REMOVE_OBJECT, "",
                    new ObjectTarget(target.layoutIndex(), target.objectId(), target.subtype(), 0, 0))));
            var end = world.resolve(scene, StageplayTimeline.sample(scene, 25));
            assertFalse(end.world().objects().stream().anyMatch(o -> o.layoutIndex() == target.layoutIndex()
                    && o.objectId() == target.objectId() && o.subtype() == target.subtype()));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 25)));
        }
    }

    @Test void clearBadniksRemovesEveryMatchingInactivePortablePlacementAndKeepsOtherObjects() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var registry = library.module("s2").createObjectRegistry();
            var catalog = com.openggf.level.objects.ObjectRegistryCatalog.entries(registry,
                    new com.openggf.level.objects.ObjectRegistryCatalog.Context(0, 0));
            var badniks = library.level("s2", 0, 0).getObjects().stream().filter(spawn ->
                    catalog.get(spawn.objectId()).semantics().role()
                            == com.openggf.level.objects.ObjectRegistryCatalog.Role.BADNIK).toList();
            assertFalse(badniks.isEmpty());
            var anchor = badniks.getFirst();
            var scene = StageplayDocument.empty("s2", 0, 0, Math.max(0, anchor.x() - 100),
                    Math.max(0, anchor.y() - 100), 320, 224).withDurationFrames(30)
                    .withWorldCommands(List.of(new WorldCommand("clear", 1, WorldCommandKind.CLEAR_BADNIKS,
                            "", ObjectTarget.none())));
            var before = world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertTrue(before.world().objects().stream().anyMatch(object -> badniks.stream().anyMatch(spawn ->
                    object.layoutIndex() == spawn.layoutIndex() && object.objectId() == spawn.objectId()
                            && object.subtype() == spawn.subtype())));
            var after = world.resolve(scene, StageplayTimeline.sample(scene, 20));
            var remaining = after.world().objects().stream().filter(object -> badniks.stream().anyMatch(spawn ->
                    object.layoutIndex() == spawn.layoutIndex() && object.objectId() == spawn.objectId()
                            && object.subtype() == spawn.subtype())).toList();
            assertTrue(remaining.isEmpty(), () -> "Badnik placements survived clearing: " + remaining);
            var direction = com.openggf.level.objects.ObjectDirectionAccess.forManager(
                    SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager());
            for (var spawn : badniks) assertFalse(direction.allowsPlacement(spawn),
                    "Clear must suppress inactive badnik placements as well as active roots");
            var monitor = library.level("s2", 0, 0).getObjects().stream()
                    .filter(spawn -> spawn.objectId() == Sonic2ObjectIds.MONITOR).findFirst().orElseThrow();
            assertTrue(direction.allowsPlacement(monitor), "Machinery retains ordinary placement admission");
            assertEquals(before, world.resolve(scene, StageplayTimeline.sample(scene, 0)));
            assertEquals(after, world.resolve(scene, StageplayTimeline.sample(scene, 20)));
        }
    }

    @Test void inactivePlacedRoleMaterializesOnceFromThePortableTupleEvenWithInitiallyClearedLayout() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var spawn = library.level("s2", 0, 0).getObjects().stream().filter(s -> s.objectId() == Sonic2ObjectIds.MONITOR).findFirst().orElseThrow();
            var role = new ObjectActor("loaded", "Loaded", "s2", 0, 0, spawn.objectId(), spawn.subtype(), spawn.layoutIndex(), List.of(
                    new ObjectKey(0, spawn.x(), spawn.y(), 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
            var scene = StageplayDocument.empty("s2", 0, 0, Math.max(0, spawn.x() - 100), Math.max(0, spawn.y() - 100), 320, 224).withDurationFrames(20)
                    .withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none()))).withObjectActors(List.of(role));
            var frame = world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(1, frame.world().objects().stream().filter(o -> o.layoutIndex() == spawn.layoutIndex()
                    && o.objectId() == spawn.objectId() && o.subtype() == spawn.subtype()).count(), () -> {
                var context = SessionManager.getCurrentGameplayMode();
                var camera = context.getCamera();
                var active = context.getLevelManager().getObjectManager().getActiveObjects().stream()
                        .map(object -> object.getClass().getSimpleName() + ":" + object.getSpawn()
                                + "@" + object.getX() + "," + object.getY()).toList();
                return "Explicit portable placement " + spawn + "; native camera=" + camera.getX() + "," + camera.getY() + "; active=" + active + "; sampled=" + frame.world().objects();
            });
            assertFalse(SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel().getObjects().isEmpty(),
                    "Clearing uses admission policy; the native source layout stays intact");
            var ending = world.resolve(scene, StageplayTimeline.sample(scene, 10));
            assertEquals(1, ending.world().objects().stream().filter(o -> o.layoutIndex() == spawn.layoutIndex()
                    && o.objectId() == spawn.objectId() && o.subtype() == spawn.subtype()).count());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(ending, world.resolve(scene, StageplayTimeline.sample(scene, 10)));
        }
    }

    @Test void explicitPlacedAdmissionSurvivesItsFirstNativeTickInClearedWorld() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var spawn = library.level("s2", 0, 0).getObjects().stream()
                    .filter(s -> s.objectId() == Sonic2ObjectIds.MONITOR).findFirst().orElseThrow();
            var scene = StageplayDocument.empty("s2", 0, 0, spawn.x() - 100, spawn.y() - 100, 320, 224)
                    .withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            var objects = SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager();
            var direction = com.openggf.level.objects.ObjectDirectionAccess.forManager(objects);
            var root = direction.materializePlaced(spawn);
            assertTrue(objects.getActiveObjects().contains(root), "Explicit placement must be admitted before native execution");
            assertEquals(spawn, root.getSpawn());
            world.resolve(scene, StageplayTimeline.sample(scene, 1));
            assertTrue(objects.getActiveObjects().contains(root), () -> "Placement removed during first native tick: " + root.getSpawn()
                    + "; active=" + objects.getActiveObjects());
        }
    }

    @Test void releasedAnimalUsesNativeProviderAndReplaysWithoutALayoutAlias() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var animal = new ObjectActor("animal", "Animal", "s2", 0, 0, 0, 0, -1, List.of(
                    new ObjectKey(0, 100, 600, 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)),
                    List.of(), ObjectSourceKind.RELEASED_ANIMAL);
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 320, 224).withDurationFrames(30)
                    .withWorldCommands(List.of(new WorldCommand("empty", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())))
                    .withObjectActors(List.of(animal));
            var end = world.resolve(scene, StageplayTimeline.sample(scene, 20));
            assertTrue(end.world().objects().stream().anyMatch(o -> o.layoutIndex() == -1
                    && o.type().equals("AnimalObjectInstance")), end.world().objects().toString());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 20)));
        }
    }

    @Test void suppressedPlacementsRetainCanonicalNativeLayoutAndPolicyOwnsAdmission() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))))) {
            var source = library.level("s2", 0, 0);
            var target = source.getObjects().getFirst();
            var identity = com.openggf.level.objects.StageplayObjectDirection.PlacementIdentity.of("s2", 0, 0, target);
            var module = new StageplayWorldModule(com.openggf.game.GameModuleRegistry.createStockModule(com.openggf.game.GameId.S2),
                    null, Set.of(identity));
            for (int load = 0; load < 2; load++) {
                var isolated = module.transformDecodedLevel(source);
                assertTrue(source.getObjects().contains(target), "Source ROM layout remains immutable");
                assertTrue(isolated.getObjects().contains(target), "Native layout is retained; one admission policy owns suppression");
                assertTrue(module.originalObjects().contains(target));
                assertEquals(source.getRings(), isolated.getRings());
                assertSame(source, isolated, "Suppression must not wrap or rewrite the native decoded level");
            }
            var session = new com.openggf.game.session.WorldSession(module);
            var policy = com.openggf.game.session.WorldSessionPolicyAccess.getService(session,
                    com.openggf.level.objects.StageplayObjectLoadPolicy.class);
            assertEquals(Set.of(identity), policy.rewindAdapter().capture().removed());
            assertFalse(policy.rewindAdapter().capture().clearObjects());
        }
    }

    @Test void constructorPreparedBossContainsEveryManagedChildWithoutSharedNativeFactoryOrRoutine() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = world(library)) {
            var boss = new ObjectActor("boss", "Boss", "s2", 0, 1, Sonic2ObjectIds.EHZ_BOSS, 0, -1, List.of(
                    new ObjectKey(0, 100, 600, 0, -1, false, true, Interpolation.HOLD, ObjectMode.POSE)), List.of());
            var scene = StageplayDocument.empty("s2", 0, 1, 0, 500, 320, 224).withDurationFrames(20).withObjectActors(List.of(boss));
            var frame = world.resolve(scene, StageplayTimeline.sample(scene, 0));
            var inspection = world.objectInspection().getFirst();
            long childPaths = inspection.parts().stream().map(StageplayObjectInspection.Part::partId)
                    .filter(id -> id.startsWith("root/"))
                    .map(id -> id.substring(0, id.indexOf("/bucket"))).filter(id -> !id.equals("root")).distinct().count();
            assertEquals(7, childPaths, "Each native managed boss component must have a captured visual part");
            assertTrue(inspection.parts().stream().allMatch(part -> !part.mappingFrames().isEmpty()));
            assertTrue(SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager().getActiveObjects().stream()
                    .noneMatch(object -> object.getClass().getSimpleName().contains("EHZBoss")), "The final shared world owns only the visual proxy");
            assertTrue(frame.world().objects().stream().anyMatch(object -> object.type().equals("Stageplay:boss:POSE")));
            var end = world.resolve(scene, StageplayTimeline.sample(scene, 15));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 15)));
        }
    }
}
