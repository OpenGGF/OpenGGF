package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real ROM geometry proposals must survive a fresh complete native world, without probe hydration.
 */
class TestStageplayPlayerRouteRom {
  @BeforeEach
  void graphics() {
    EngineServices.current().graphics().initHeadless();
  }

  @Test
  @RequiresRom(SonicGame.SONIC_1)
  void ghzRouteIsQualifiedByNativePlayerInputs() throws Exception {
    verify("s1", "sonic1.rom.path");
  }

  @Test
  @RequiresRom(SonicGame.SONIC_2)
  void ehzRouteIsQualifiedAndPreservesOutsideCommands() throws Exception {
    verify("s2", "sonic2.rom.path");
  }

  @Test
  @RequiresRom(SonicGame.SONIC_3K)
  void aizRouteIsQualifiedByNativePlayerInputs() throws Exception {
    verify("s3k", "s3k.rom.path");
  }

  @Test
  @RequiresRom(SonicGame.SONIC_2)
  void nativeShotEntryAndJumpEdgesMatchThePrivateProbeAtEveryTick() throws Exception {
    try (var library =
            SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
        var resources = new StageplayResources(library, false)) {
      retireInitialWorld(library);
      var scene =
          hillScene()
              .withPuppets(
                  List.of(
                      new PuppetTrack(
                          "hero",
                          true,
                          0,
                          List.of(
                              new PuppetCommand(
                                  "held-on-entry", 30, PuppetCommandKind.JUMP, 0, 0, 0, 12),
                              new PuppetCommand(
                                  "fresh-after-landing",
                                  50,
                                  PuppetCommandKind.JUMP,
                                  0,
                                  0,
                                  0,
                                  12)))));
      var actor = scene.actors().getFirst();
      var anchor = actor.keys().getLast();
      var expected = new java.util.ArrayList<PlayablePuppet.Observation>();
      try (var probe =
          resources.createRouteProbe(scene.set(), actor, anchor, scene.puppets().getFirst())) {
        assertTrue(
            probe.sample().airborne(),
            "Native shot entry has not landed before its first story tick");
        for (int frame = 30; frame < 80; frame++) {
          probe.tick(false, false, frame < 42 || frame >= 50 && frame < 62, frame);
          expected.add(probe.sample());
        }
      }
      try (var world = StageplayLiveWorld.openOffline(library)) {
        resources.setLiveWorld(world);
        for (int frame = 0; frame < 80; frame++) {
          var live =
              resources.resolveFrame(
                  scene, com.openggf.stageplay.StageplayTimeline.sample(scene, frame));
          if (frame < 30) continue;
          var privateState = expected.get(frame - 30);
          var pose = live.actors().getFirst();
          var physical = live.puppets().getFirst();
          assertEquals(privateState.x(), pose.x(), 1.0 / 65536, "X at frame " + frame);
          assertEquals(privateState.y(), pose.y(), 1.0 / 65536, "Y at frame " + frame);
          assertEquals(
              privateState.xSpeed() / 256.0, physical.velocityX(), "X speed at frame " + frame);
          assertEquals(
              privateState.ySpeed() / 256.0, physical.velocityY(), "Y speed at frame " + frame);
          assertEquals(privateState.airborne(), physical.airborne(), "Air state at frame " + frame);
        }
      }
      assertTrue(
          expected.subList(0, 12).stream().anyMatch(PlayablePuppet.Observation::onGround),
          "Holding jump on airborne entry must allow native landing");
      assertTrue(
          expected.get(20).airborne(),
          "A released/repressed native jump must take off after landing");
    }
  }

  @Test
  @RequiresRom(SonicGame.SONIC_2)
  void shotEntryHillRouteSurvivesFullColdQualification() throws Exception {
    try (var library =
            SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
        var resources = new StageplayResources(library, false)) {
      retireInitialWorld(library);
      var scene = hillScene();
      var request = new StageplayRouteRequest("hero", 30, 400, 624, 300);
      var result =
          StageplayRoutePlanner.plan(
              resources, library, scene, request, "native-entry-hill", () -> false);
      assertEquals(StageplayRouteResult.Status.VERIFIED, result.status(), result.message());
      assertEquals(300, result.path().size());
      assertTrue(result.path().getLast().grounded());
      assertEquals(400, result.path().getLast().x(), 6);
      assertEquals(624, result.path().getLast().y(), 12);
      assertEquals(scene.actors(), result.candidate().actors());
      assertNull(SessionManager.getCurrentWorldSession());
    }
  }

  private static StageplayDocument hillScene() {
    var source = new SetSource("s2", 0, 0, "", "", WorldPolicy.TERRAIN_ONLY);
    var actor =
        new Actor(
            "hero",
            "Sonic",
            "s2",
            "sonic",
            List.of(
                new ActorKey(0, 100, 656, 0, -1, false, true, 1, Interpolation.HOLD),
                new ActorKey(30, 100, 656, 0, -1, false, true, 1, Interpolation.HOLD)));
    return StageplayDocument.empty("s2", 0, 0, 0, 528, 640, 224)
        .withSet(source)
        .withDurationFrames(330)
        .withCamera(
            List.of(
                new CameraKey(0, 0, 528, 1, Interpolation.HOLD),
                new CameraKey(30, 0, 528, 1, Interpolation.HOLD)))
        .withSceneChanges(List.of(new SceneChange("route-shot", 30, source, Transition.CUT, 0)))
        .withActors(List.of(actor))
        .withPuppets(List.of(new PuppetTrack("hero", true, 0, List.of())));
  }

  private static void retireInitialWorld(SceneRomLibrary library) {
    if (SessionManager.getCurrentGameplayMode() != null) {
      try (var initial =
          StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
        initial.reset();
      }
    }
  }

  private static void verify(String game, String property) throws Exception {
    try (var library = SceneRomLibrary.open(Map.of(game, Path.of(System.getProperty(property))));
        var resources = new StageplayResources(library, false)) {
      // The ROM extension owns this initial session. Adopt and retire exactly that session.
      if (SessionManager.getCurrentGameplayMode() != null) {
        try (var initial =
            StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
          initial.reset();
        }
      }
      var source = new SetSource(game, 0, 0, "", "", WorldPolicy.TERRAIN_ONLY);
      var anchor = discoverFlatNativeWalk(resources, library, source);
      var actor =
          new Actor(
              "hero",
              "Sonic",
              game,
              "sonic",
              List.of(
                  new ActorKey(
                      0, anchor.x(), anchor.y(), 0, -1, false, true, 1, Interpolation.HOLD)));
      var after = new PuppetCommand("outside", 160, PuppetCommandKind.STOP, 0, 0, 0, 0);
      var replaced =
          new PuppetCommand(
              "replace", 12, PuppetCommandKind.MOVE_TO, anchor.x() - 24, anchor.y(), 1, 0);
      var scene =
          StageplayDocument.empty(
                  game, 0, 0, Math.max(0, anchor.x() - 100), anchor.y() - 120, 320, 224)
              .withSet(source)
              .withDurationFrames(180)
              .withActors(List.of(actor))
              .withPuppets(List.of(new PuppetTrack("hero", true, 0, List.of(replaced, after))));
      var request = new StageplayRouteRequest("hero", 0, anchor.x() + 48, anchor.y(), 150);
      var result =
          StageplayRoutePlanner.plan(
              resources, library, scene, request, "rom-route-fixture", () -> false);
      assertEquals(StageplayRouteResult.Status.VERIFIED, result.status(), result.message());
      assertTrue(result.nativeVerified());
      assertNull(
          SessionManager.getCurrentWorldSession(),
          "Cold qualification must retire its owned world");
      assertEquals(
          scene.actors(), result.candidate().actors(), "Planning cannot rewrite the actor anchor");
      assertEquals(after, result.candidate().puppets().getFirst().commands().getLast());
      assertFalse(result.candidate().puppets().getFirst().commands().contains(replaced));
      var last = result.path().getLast();
      assertTrue(last.grounded());
      assertEquals(request.targetX(), last.x(), 6);
      assertEquals(request.targetY(), last.y(), 12);
      assertEquals(150, result.path().size());
      assertTrue(
          result.simulatedTicks() >= 151,
          "All native replay ticks and its assembly scan are charged");
      assertTrue(result.simulatedTicks() <= request.maxTicks());
      // One search tick leaves no budget for the full cold replay; it cannot claim success.
      var limited =
          new StageplayRouteRequest(
              "hero", 0, request.targetX(), request.targetY(), 150, 1024, 1, 60000, 1, null, 1);
      assertEquals(
          StageplayRouteResult.Status.BUDGET_EXCEEDED,
          StageplayRoutePlanner.plan(
                  resources, library, scene, limited, "rom-route-fixture", () -> false)
              .status());
    }
  }

  /**
   * Find a short flat route using native sensors and actual input attempts, rather than fitted
   * coordinates.
   */
  private static StageplayRoutePoint discoverFlatNativeWalk(
      StageplayResources resources, SceneRomLibrary library, SetSource source) {
    var terrain = resources.routeTerrain(source);
    var module = library.module(source.gameId());
    // Level limits bound the camera origin, not the bottom/right world pixels.
    // Match DetachedLevelPictures.bounds: the last native viewport contributes its
    // full 320x224 extent, clipped to the actual foreground layout. Also bound the
    // scan relative to the act's minimum: AIZ1 starts beyond world X2048.
    int left = Math.max(128, terrain.getMinX() + 64);
    int top = Math.max(128, terrain.getMinY() + 32);
    int right =
        Math.min(
            terrain.getLayerWidthBlocks(0) * terrain.getBlockPixelSize(), terrain.getMaxX() + 320);
    int bottom =
        Math.min(
            terrain.getLayerHeightBlocks(0) * terrain.getBlockPixelSize(), terrain.getMaxY() + 224);
    int maxX = Math.min(left + 2048, right - 64);
    int maxY = Math.min(top + 2048, bottom - 32);
    for (int x = left; x < maxX; x += 64) {
      for (int y = top; y < maxY; y += 64) {
        try (var probe =
            PlayablePuppet.create(
                terrain,
                module,
                source.zone(),
                source.act(),
                module,
                "sonic",
                SpriteArtSet.EMPTY,
                x,
                y,
                0,
                true,
                false)) {
          var start = probe.sample();
          if (start.angle() != 0) continue;
          for (int frame = 0; frame < 120; frame++) {
            var sample = probe.sample();
            int direction =
                StageplayMovementIntent.direction(
                    Math.floor(sample.x()),
                    sample.xSpeed(),
                    sample.runAccel(),
                    sample.runDecel(),
                    sample.maxSpeed(),
                    sample.airborne(),
                    x + 48,
                    1);
            probe.tick(direction < 0, direction > 0, false, frame);
          }
          var end = probe.sample();
          if (end.onGround()
              && Math.abs(end.x() - (x + 48)) <= 6
              && Math.abs(end.y() - start.y()) < 1
              && Math.abs(end.xSpeed()) <= 64
              && Math.abs(end.groundSpeed()) <= 64)
            return new StageplayRoutePoint(0, start.x(), start.y(), true);
        } catch (IllegalArgumentException noFloor) {
          // The bounded native floor search honestly found no valid standable start here.
        }
      }
    }
    fail(
        "No short flat native walk found for "
            + source.gameId()
            + " in world rectangle "
            + left
            + ","
            + top
            + ".."
            + maxX
            + ","
            + maxY);
    throw new AssertionError();
  }
}
