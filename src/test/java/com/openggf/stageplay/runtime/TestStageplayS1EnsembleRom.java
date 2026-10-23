package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
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

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestStageplayS1EnsembleRom {
    @BeforeEach void graphics() { EngineServices.current().graphics().initHeadless(); }

    @Test void authoredGhzCameraAt832AndItsShakeClampToNativeLevelBoundsWithoutRewritingKeys() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s1", Path.of(System.getProperty("sonic1.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var scene = StageplayDocument.empty("s1", 0, 0, 100, 832, 320, 224).withDurationFrames(12)
                    .withCameraEffects(List.of(new CameraEffect("edge-shake", 0, 12, 0, 40, 30, 7)));
            StageplayTimeline.Frame end = null;
            boolean outwardShake = false;
            for (int frame = 0; frame < 12; frame++) {
                var authored = StageplayTimeline.sample(scene, frame);
                outwardShake |= authored.camera().y() > authored.baseCamera().y();
                end = world.resolve(scene, authored);
                assertEquals(768, end.baseCamera().y(), "GHZ1 native bottom camera origin is 0x300");
                assertTrue(end.camera().y() <= 768, "Shake cannot reveal scenery below the native world envelope");
            }
            assertTrue(outwardShake, "The regression must exercise shake pushing against the bottom edge");
            assertEquals(832, scene.camera().getFirst().y(), "Evaluation must preserve the authored key");
            var camera = SessionManager.getCurrentGameplayMode().getCamera();
            camera.setMinX((short) 100); camera.setMaxX((short) 100);
            camera.setMinY((short) 100); camera.setMaxY((short) 100);
            assertEquals(new StageplayTimeline.CameraPose(800, 768, 1),
                    world.boundedCamera(new StageplayTimeline.CameraPose(800, 832, 1)),
                    "Runtime arena locks do not constrain directed framing");
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(end, world.resolve(scene, StageplayTimeline.sample(scene, 11)));

            var follow = scene.withCameraEffects(List.of()).withActors(List.of(actor("hero", 128, 900)))
                    .withPuppets(List.of(new PuppetTrack("hero", false, 0, List.of())))
                    .withCamera(List.of(new CameraKey(0, 100, 832, 1, Interpolation.HOLD,
                            new CameraFollow(List.of("hero"), 0, 0, 0, 1, 0, 1000))));
            assertEquals(768, world.resolve(follow, StageplayTimeline.sample(follow, 0)).baseCamera().y(),
                    "Follow offsets obey the same level envelope as authored keys");
        }
    }

    @Test void supplementalTrackedMonitorKeepsNativeRespawnIdentityAndReplaysWithPrimaryCameraFarAway() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s1", Path.of(System.getProperty("sonic1.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var original = library.level("s1", 0, 0).getObjects();
            var monitor = original.stream().filter(p -> p.objectId() == Sonic1ObjectIds.MONITOR
                    && p.respawnTracked() && p.x() > 900 && p.x() < 2000).findFirst().orElseThrow();
            var scene = StageplayDocument.empty("s1", 0, 0, 0, 0, 320, 224).withDurationFrames(35)
                    .withActors(List.of(actor("lead", 100, monitor.y() - 64),
                            actor("partner", monitor.x(), monitor.y() - 64)))
                    .withPuppets(List.of(new PuppetTrack("lead", false, 0, List.of()),
                            new PuppetTrack("partner", false, 0, List.of())));
            var at10 = world.resolve(scene, StageplayTimeline.sample(scene, 10));
            var manager = manager();
            var object = (AbstractObjectInstance) find(manager, monitor);
            assertTrue(monitor.x() > SessionManager.getCurrentGameplayMode().getCamera().getX() + 640,
                    "The tracked monitor must be supplemental, outside the primary load cursor");
            int counter = object.getRespawnStateIndex();
            int expectedCounter = (int) (1 + original.stream().filter(p -> p.respawnTracked()
                    && p.layoutIndex() < monitor.layoutIndex()).count()) & 0xFF;
            assertEquals(expectedCounter, counter, "Native OPL d2 counts the tracked layout prefix");
            ObjectSpawn admitted = placement(manager, monitor);
            assertTrue(counter >= 0, "Native constructor admission must receive obRespawnNo");
            manager.setSpawnStateBit(admitted, 6);
            assertTrue(manager.isSpawnStateBitSet(admitted, 6));
            var saved = manager.rewindSnapshottable().capture();
            manager.setSpawnStateBit(admitted, 5);
            manager.rewindSnapshottable().restore(saved);
            assertTrue(manager.isSpawnStateBitSet(admitted, 6));
            assertFalse(manager.isSpawnStateBitSet(admitted, 5));
            assertEquals(counter, ((AbstractObjectInstance) find(manager, monitor)).getRespawnStateIndex());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(at10, world.resolve(scene, StageplayTimeline.sample(scene, 10)));
            assertEquals(counter, ((AbstractObjectInstance) find(manager(), monitor)).getRespawnStateIndex());
            assertFalse(manager().isSpawnStateBitSet(placement(manager(), monitor), 6), "Backseek restores native state before the test's low-bit write");
        }
    }

    @Test void initiallyClearedWorldCanReleasePlacedPoseToNativeFreeWithTheOriginalCounter() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s1", Path.of(System.getProperty("sonic1.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var original = library.level("s1", 0, 0).getObjects();
            var monitor = original.stream().filter(p -> p.objectId() == Sonic1ObjectIds.MONITOR
                    && p.respawnTracked() && p.x() > 900 && p.x() < 2000).findFirst().orElseThrow();
            var actor = new ObjectActor("monitor", "monitor", "s1", 0, 0, monitor.objectId(), monitor.subtype(),
                    monitor.layoutIndex(), List.of(
                    new ObjectKey(0, monitor.x(), monitor.y(), 0, -1, false, true, Interpolation.HOLD, ObjectMode.POSE),
                    new ObjectKey(10, monitor.x(), monitor.y(), 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)), List.of());
            var scene = StageplayDocument.empty("s1", 0, 0, 0, 0, 320, 224).withDurationFrames(30)
                    .withSet(new SetSource("s1", 0, 0, "", ""))
                    .withObjectActors(List.of(actor));
            var posed = world.resolve(scene, StageplayTimeline.sample(scene, 5));
            assertTrue(posed.world().objects().stream().anyMatch(o -> o.type().equals("Stageplay:monitor:POSE")));
            var released = world.resolve(scene, StageplayTimeline.sample(scene, 15));
            var object = (AbstractObjectInstance) find(manager(), monitor);
            int expectedCounter = (int) (1 + original.stream().filter(p -> p.respawnTracked()
                    && p.layoutIndex() < monitor.layoutIndex()).count()) & 0xFF;
            assertEquals(expectedCounter, object.getRespawnStateIndex());
            manager().setSpawnStateBit(placement(manager(), monitor), 6);
            assertTrue(manager().isSpawnStateBitSet(placement(manager(), monitor), 6));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(released, world.resolve(scene, StageplayTimeline.sample(scene, 15)));
            assertEquals(expectedCounter, ((AbstractObjectInstance) find(manager(), monitor)).getRespawnStateIndex());
        }
    }

    private static ObjectManager manager() {
        return SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager();
    }
    private static ObjectInstance find(ObjectManager manager, ObjectSpawn target) {
        return manager.getActiveObjects().stream().filter(o -> o.getSpawn() != null
                && o.getSpawn().layoutIndex() == target.layoutIndex()
                && o.getSpawn().objectId() == target.objectId()
                && o.getSpawn().subtype() == target.subtype()).findFirst().orElseThrow();
    }
    private static ObjectSpawn placement(ObjectManager manager, ObjectSpawn target) {
        return manager.getActiveSpawns().stream().filter(spawn -> spawn.layoutIndex() == target.layoutIndex()
                && spawn.objectId() == target.objectId() && spawn.subtype() == target.subtype()).findFirst().orElseThrow();
    }
    private static Actor actor(String id, int x, int y) {
        return new Actor(id, id, "s1", "sonic", List.of(
                new ActorKey(0, x, y, 0, -1, false, true, 1, Interpolation.HOLD)));
    }
}
