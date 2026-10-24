package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.MutableLevel;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestStageplayUnifiedWorldRom {
    @org.junit.jupiter.api.BeforeEach void logicGraphics() { EngineServices.current().graphics().initHeadless(); }

    @Test void emptyCastHasARealNativeWorldWithoutCollectingRingsAndReplays() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 640, 448).withDurationFrames(60);
            var end = world.resolve(scene, StageplayTimeline.sample(scene, 59));
            assertNotNull(end.world());
            assertTrue(end.world().actors().isEmpty());
            assertFalse(end.world().objects().isEmpty());
            assertTrue(end.world().rings().stream().noneMatch(StageplayTimeline.LiveRing::collected));
            var driver = SessionManager.getCurrentGameplayMode().getSpriteManager().getMainPlayable();
            assertTrue(driver.isHidden());
            assertTrue(driver.isTouchResponseSuppressedByObjectControl());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 59)));
        }
    }

    @Test void populatedAndClearedWorldsRunTheSameNativeDonorMovementAndWholeWorldReplay() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("s3k.rom.path") != null,
                "This mixed-donor check requires the explicit S3K ROM property");
        for (boolean clear : List.of(false, true)) {
            try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path")),
                    "s3k", Path.of(System.getProperty("s3k.rom.path"))));
                 var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                         : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
                var scene = physical("");
                if (clear) scene = scene.withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
                var end = world.resolve(scene, StageplayTimeline.sample(scene, 30));
                assertTrue(end.actors().getFirst().x() > 100, end.toString());
                var player = SessionManager.getCurrentGameplayMode().getSpriteManager().getMainPlayable();
                assertTrue(player.getGameRules().playerCapability().tailsFlightEnabled());
                assertNotNull(player.getTailsTailsController());
                assertTrue(end.puppets().getFirst().status() != StageplayTimeline.PuppetStatus.UNRESOLVED);
                world.resolve(scene, StageplayTimeline.sample(scene, 0));
                assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 30)));
                assertFalse(end.world().rings().isEmpty(), "Ring ownership is separate from object clearing");
                if (clear) assertTrue(end.world().objects().isEmpty());
                else assertFalse(end.world().objects().isEmpty());
            }
        }
    }

    @Test void authoredNativeWorldUsesAnIsolatedTerrainSnapshot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("s3k.rom.path") != null,
                "This mixed-donor check requires the explicit S3K ROM property");
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path")),
                "s3k", Path.of(System.getProperty("s3k.rom.path"))));
             var resources = new StageplayResources(library, false)) {
            var scene = physical("set.ogfproject");
            var terrain = MutableLevel.snapshot(library.level("s2", 0, 0));
            int originalObjects = terrain.getObjects().size();
            resources.registerSet(scene.set(), terrain);
            resources.setLiveWorld(StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode()));
            var actual = resources.resolveFrame(scene, StageplayTimeline.sample(scene, 30));
            assertTrue(actual.actors().getFirst().x() > 100);
            assertNotSame(terrain, SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel());
            assertEquals(originalObjects, terrain.getObjects().size());
            var before = actual;
            resources.resolveFrame(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(before, resources.resolveFrame(scene, StageplayTimeline.sample(scene, 30)));
        }
    }

    @Test void mixedDonorFlightAndGlideUseNativeAbilityStateAndReplayInPopulatedAndClearedWorlds() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("s3k.rom.path") != null,
                "This mixed-donor ability check requires the explicit S3K ROM property");
        for (boolean clear : List.of(false, true)) {
            for (String character : List.of("tails", "knuckles")) {
                try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path")),
                        "s3k", Path.of(System.getProperty("s3k.rom.path"))));
                     var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                             : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
                    var base = physical("").withDurationFrames(90);
                    if (clear) base = base.withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
                    var actor = new Actor("performer", character, "s3k", character, base.actors().getFirst().keys());
                    var kind = character.equals("tails") ? PuppetCommandKind.FLY_TO : PuppetCommandKind.GLIDE_TO;
                    var scene = new StageplayDocument(VERSION, "Native donor ability", 90, base.width(), base.height(),
                            base.set(), List.of(actor), base.camera(), List.of(), List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(new PuppetTrack("performer", true, 0, List.of(
                            new PuppetCommand("ability", 0, kind, 250, 570, .5, 0)))));
                    scene = scene.withWorldCommands(base.worldCommands());
                    var rows = new java.util.ArrayList<StageplayTimeline.Frame>();
                    for (int frame = 0; frame < 60; frame++) rows.add(world.resolve(scene, StageplayTimeline.sample(scene, frame)));
                    var performing = rows.stream().filter(row -> row.world().actors().getFirst().abilityState() != 0
                            && row.puppets().getFirst().airborne()).findFirst().orElseThrow(() -> new AssertionError(
                            "Native " + character + " ability never started (cleared=" + clear + ")"));
                    assertTrue(performing.actors().getFirst().y() < scene.actors().getFirst().keys().getFirst().y());
                    assertFalse(performing.world().actors().getFirst().dead());
                    if (character.equals("tails")) {
                        assertTrue(performing.world().actors().getFirst().abilityTimer() > 0);
                        assertNotNull(performing.puppets().getFirst().accessory());
                    }
                    var before = rows.getLast();
                    world.resolve(scene, StageplayTimeline.sample(scene, 0));
                    assertEquals(before, world.resolve(scene, StageplayTimeline.sample(scene, 59)),
                            "Ability/controller/accessory state must survive whole-world rewind");
                }
            }
        }
    }

    @Test void followerFlightRequiresItsExactScopedNativeInputAndReplays() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("s3k.rom.path") != null,
                "This mixed-donor follower check requires the explicit S3K ROM property");
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path")),
                "s3k", Path.of(System.getProperty("s3k.rom.path"))));
             var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var base = physical("");
            var lead = new Actor("lead", "Sonic", "s2", "sonic", base.actors().getFirst().keys());
            var follower = new Actor("follower", "Tails", "s3k", "tails", List.of(new ActorKey(0,
                    128, 655, 0, -1, false, true, 1, Interpolation.HOLD)));
            var scene = new StageplayDocument(VERSION, "Independent follower flight", 90, base.width(), base.height(),
                    base.set(), List.of(lead, follower), base.camera(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(new PuppetTrack("lead", true, 0, List.of()),
                    new PuppetTrack("follower", true, 0, List.of(new PuppetCommand("fly", 0,
                            PuppetCommandKind.FLY_TO, 250, 570, .5, 0)))));
            var rows = new java.util.ArrayList<StageplayTimeline.Frame>();
            for (int frame = 0; frame < 60; frame++) rows.add(world.resolve(scene, StageplayTimeline.sample(scene, frame)));
            var airborne = rows.stream().filter(row -> row.world().actors().get(1).abilityState() != 0
                    && row.puppets().get(1).airborne()).findFirst().orElseThrow(() -> new AssertionError(
                    "Scripted follower did not enter native flight"));
            assertTrue(airborne.world().actors().get(1).abilityTimer() > 0);
            assertTrue(rows.stream().noneMatch(row -> row.puppets().getFirst().velocityY() < 0),
                    "The follower's controller edges must not jump the lead");
            var sprite = SessionManager.getCurrentGameplayMode().getSpriteManager().getRegisteredSidekicks().getFirst();
            assertTrue(sprite.isCpuControlled());
            assertNull(sprite.getCpuController(), "A scripted controller must not require native follow AI");
            var end = rows.getLast();
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 59)));
        }
    }

    @Test void centredFollowZoomPreservesFramingAndFollowEasing() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))))) {
            for (double smoothing : new double[]{1, .2}) {
                var follow = new CameraFollow(List.of("hero"), 0, 0, 0, smoothing, 0, 0);
                var scene = StageplayDocument.empty("s2", 0, 0, 0, 500, 320, 224).withDurationFrames(40)
                        .withActors(List.of(new Actor("hero", "Hero", "s2", "sonic", List.of(
                                new ActorKey(0, 160, 655, 0, -1, false, true, 1, Interpolation.HOLD)))))
                        .withPuppets(List.of(new PuppetTrack("hero", true, 0, List.of())))
                        .withCamera(List.of(new CameraKey(0, 0, 500, 1, Interpolation.HOLD, follow)))
                        .withWorldCommands(List.of(new WorldCommand("clear", 0, WorldCommandKind.CLEAR_OBJECTS,
                                "", ObjectTarget.none())));
                var fixed = new java.util.ArrayList<StageplayTimeline.Frame>();
                try (var world = nativeWorld(library)) {
                    for (int frame = 0; frame < 40; frame++)
                        fixed.add(world.resolve(scene, StageplayTimeline.sample(scene, frame)));
                }
                var zoomed = scene.withCamera(List.of(new CameraKey(0, 0, 500, 1, Interpolation.LINEAR, follow),
                        new CameraKey(30, 160 - 160.0 / 3, 612 - 112.0 / 3, 3, Interpolation.HOLD, follow)));
                try (var world = nativeWorld(library)) {
                    for (int frame = 0; frame < 40; frame++) {
                        var actual = world.resolve(zoomed, StageplayTimeline.sample(zoomed, frame));
                        var reference = fixed.get(frame);
                        assertEquals(reference.actors(), actual.actors(), "Camera direction cannot alter native movement");
                        assertEquals(reference.camera().x() + 160, actual.camera().x() + 160 / actual.camera().zoom(), 1e-8);
                        assertEquals(reference.camera().y() + 112, actual.camera().y() + 112 / actual.camera().zoom(), 1e-8);
                    }
                    var ending = world.resolve(zoomed, StageplayTimeline.sample(zoomed, 39));
                    world.resolve(zoomed, StageplayTimeline.sample(zoomed, 0));
                    assertEquals(ending, world.resolve(zoomed, StageplayTimeline.sample(zoomed, 39)));
                }
            }
        }
    }

    private static StageplayLiveWorld nativeWorld(SceneRomLibrary library) throws Exception {
        return SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
    }

    private static StageplayDocument physical(String project) {
        var key = new ActorKey(0, 96, 655, 0, -1, false, true, 1, Interpolation.HOLD);
        return new StageplayDocument(VERSION, "Native donor", 60, 640, 448,
                new SetSource("s2", 0, 0, "", project),
                List.of(new Actor("tails", "Tails", "s3k", "tails", List.of(key))),
                List.of(new CameraKey(0, 0, 500, .75, Interpolation.HOLD)), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PuppetTrack("tails", true, 0,
                        List.of(new PuppetCommand("move", 0, PuppetCommandKind.MOVE_TO, 240, 655, .5, 0)))));
    }
}
