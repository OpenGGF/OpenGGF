package com.openggf.stageplay.route;

import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectRouteRom {
    @BeforeEach void graphics() { EngineServices.current().graphics().initHeadless(); }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void motobugVisualWalkUsesGhzCollisionWithoutRunningItsNativeRoutine() throws Exception {
        // Authored sample foot offset: Moto_Main (_incObj/40 Badnik - Moto Bug.asm:21-32), obHeight=$E.
        verify("s1", 0, 0, 0x40, 14, "sonic1.rom.path", 0);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void shellcrackerVisualWalkUsesMtzCollisionWithoutAttackingOrSpawningClaws() throws Exception {
        // Authored sample foot offset: Obj9F_Init (s2.asm:75616-75625), y_radius=$C.
        verify("s2", 7, 0, 0x9F, 12, "sonic2.rom.path", 0);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void rhinobotVisualWalkUsesAizCollisionWithoutCharging() throws Exception {
        // Authored sample foot offset: Rhinobot_Init (sonic3k.asm:182480-182499), y_radius=$10.
        // Map_Offscreen is deliberately empty until Obj_WaitOffscreen releases and
        // the separate Rhinobot_Init dispatch installs the body. A Free lead-in
        // prepares that real graph; only the later Puppet interval is routed.
        // loc_85B02 (sonic3k.asm:180362-180392), Rhinobot_Init above.
        verify("s3k", 0, 0, 0x8D, 16, "s3k.rom.path", 8);
    }

    private static void verify(String game, int zone, int act, int objectId, int foot, String property, int freeLead) throws Exception {
        try (var library = SceneRomLibrary.open(Map.of(game, Path.of(System.getProperty(property))));
             var resources = new StageplayResources(library, false)) {
            var source = new SetSource(game, zone, act, "", "", WorldPolicy.LIVE);
            var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                    : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
            resources.setLiveWorld(world);
            var entryClear = new WorldCommand("clear-objects-0", 0, WorldCommandKind.CLEAR_OBJECTS,
                    "", ObjectTarget.none());
            var opening = StageplayDocument.empty(game, zone, act, 0, 0, 320, 224)
                    .withSet(source).withDurationFrames(180).withWorldCommands(List.of(entryClear));
            resources.resolveFrame(opening, StageplayTimeline.sample(opening, 0));
            var terrain = resources.routeTerrainProbe(SessionManager.getCurrentGameplayMode().getLevelManager());
            var spawns = library.level(game, zone, act).getObjects().stream().filter(p -> p.objectId() == objectId).limit(24).toList();
            assertFalse(spawns.isEmpty(), "Sample must use a real native object context");
            StageplayRouteResult chosen = null;
            var rng = GameServices.rngOrNull(); long seed = rng == null ? 0 : rng.getSeed();
            var audio = EngineServices.current().audio().commandTimeline();
            long audioFrame = audio.currentFrame(); int audioCount = audio.entryCount();
            for (ObjectSpawn spawn : spawns) {
                var floor = terrain.floor(spawn.x(), spawn.y() + foot, 0);
                if (!floor.found()) continue;
                for (int direction : new int[]{1, -1}) {
                    int targetX = spawn.x() + direction * 24;
                    if (targetX < 0 || targetX > 32767) continue;
                    var target = terrain.floor(targetX, floor.y(), 0);
                    if (!target.found()) continue;
                    var puppetAnchor = new ObjectKey(freeLead, spawn.x(), floor.y() - foot, 0, -1, false, true,
                            Interpolation.HOLD, ObjectMode.PUPPET);
                    var keys = freeLead == 0 ? List.of(puppetAnchor) : List.of(
                            new ObjectKey(0, spawn.x(), floor.y() - foot, 0, -1, false, true,
                                    Interpolation.HOLD, ObjectMode.FREE), puppetAnchor);
                    var actor = new ObjectActor("walker", "Walker", game, zone, act, objectId, spawn.subtype(), -1,
                            keys, List.of());
                    var scene = StageplayDocument.empty(game, zone, act, spawn.x() - 100, floor.y() - 120, 320, 224)
                            .withSet(source).withDurationFrames(180).withObjectActors(List.of(actor))
                            .withWorldCommands(List.of(entryClear));
                    // Explicit visual clearance is author metadata, not a native hitbox adapter.
                    var request = new StageplayRouteRequest("walker", freeLead, targetX, target.y() - foot, 150,
                            1024, 2000, 15000, 1, new StageplayRouteFootprint(2, foot, foot, 0), .75);
                    var result = StageplayObjectRoutePlanner.plan(scene, request, "rom-fixture", terrain, () -> false);
                    if (result.success()) { chosen = result; break; }
                }
                if (chosen != null) break;
            }
            assertNotNull(chosen, "A bounded 24px walk near one native sample must have a continuous clear floor");
            if (rng != null) assertEquals(seed, rng.getSeed(), "Read-only route planning cannot execute native RNG consumers");
            assertEquals(audioFrame, audio.currentFrame()); assertEquals(audioCount, audio.entryCount());
            assertFalse(chosen.nativeVerified(), "Object visual routes never claim native player/controller certification");
            resources.validate(chosen.candidate());
            if (freeLead > 0) {
                var prepared = resources.resolveFrame(chosen.candidate(), StageplayTimeline.sample(chosen.candidate(), freeLead - 1));
                assertTrue(prepared.world().objects().stream().anyMatch(o -> o.objectId() == objectId),
                        "The native Free root must initialize before its graph is frozen for visual routing");
                assertEquals(freeLead, chosen.objectCommands().getFirst().frame());
                assertEquals(ObjectMode.FREE, chosen.candidate().objectActors().getFirst().keys().getFirst().mode());
            }
            int end = chosen.objectCommands().getLast().frame();
            var observed = resources.resolveFrame(chosen.candidate(), StageplayTimeline.sample(chosen.candidate(), end));
            var rendered = observed.world().objects().stream().filter(o -> o.type().equals("Stageplay:walker:PUPPET")).findFirst().orElseThrow();
            var last = chosen.path().getLast();
            assertEquals(Math.round(last.x()), rendered.x()); assertEquals(Math.round(last.y()), rendered.y());
            // S1 Ring_Main creates real parent/child ring routines, unlike the later
            // games' separate ring manager: Ring_Main (_incObj/25, 37 Rings.asm:46-91).
            // CLEAR_OBJECTS deliberately preserves those independent ring owners.
            assertTrue(observed.world().objects().stream().allMatch(o -> o.type().startsWith("Stageplay:")
                            || game.equals("s1")
                                    && o.objectId() == com.openggf.game.sonic1.constants.Sonic1ObjectIds.RING
                                    && (o.type().equals("Sonic1RingInstance")
                                            || o.layoutIndex() >= 0 && o.type().equals("Ring"))),
                    "Only the suspended graph and independent native ring routines may survive;"
                            + " Free badniks/children must not: " + observed.world().objects());
            assertEquals(library.level(game, zone, act).getRings().size(), observed.world().rings().size(),
                    "An object clear must retain the separate native ring population");
            assertTrue(observed.world().rings().stream().noneMatch(com.openggf.stageplay.StageplayTimeline.LiveRing::collected),
                    "A visual object route cannot collect rings");
            resources.resolveFrame(chosen.candidate(), StageplayTimeline.sample(chosen.candidate(), 0));
            assertEquals(observed, resources.resolveFrame(chosen.candidate(), StageplayTimeline.sample(chosen.candidate(), end)));
            if (freeLead > 0) {
                // Exercise the exact editor/CLI facade too: it must cold-replay the
                // native prefix, then qualify the suspended graph on every tick.
                world.close();
                try (var qualifier = new StageplayResources(library, false)) {
                    var qualified = StageplayRoutePlanner.plan(qualifier, library, chosen.candidate(),
                            chosen.request(), "rom-fixture", () -> false);
                    assertEquals(StageplayRouteResult.Status.VISUAL_TERRAIN, qualified.status(), qualified.message());
                    assertFalse(qualified.nativeVerified());
                    assertEquals(chosen.candidate().objectActors().getFirst().keys(),
                            qualified.candidate().objectActors().getFirst().keys());
                    assertEquals(chosen.request().maxFrames(), qualified.path().size());
                    assertNull(SessionManager.getCurrentWorldSession(), "Qualification must retire its native world");
                }
            }
        }
    }
}
