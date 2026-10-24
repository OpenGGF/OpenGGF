package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
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

@RequiresRom(SonicGame.SONIC_2)
class TestStageplayEnsembleRom {
    @BeforeEach void graphics() { EngineServices.current().graphics().initHeadless(); }

    @Test void separatedPlayersKeepNearbyPlacedObjectsActiveAndGroupFollowReplays() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var monitor = library.level("s2", 0, 0).getObjects().stream()
                    .filter(p -> p.objectId() == 0x26 && p.x() >= 900 && p.x() < 1800).findFirst().orElseThrow();
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 450, 320, 224).withDurationFrames(60)
                    .withActors(List.of(actor("lead", "sonic", 100, monitor.y() - 64),
                            actor("partner", "tails", monitor.x(), monitor.y() - 64)))
                    .withPuppets(List.of(new PuppetTrack("lead", false, 0, List.of()),
                            new PuppetTrack("partner", false, 0, List.of())))
                    .withCamera(List.of(new CameraKey(0, 0, 450, .75, Interpolation.HOLD,
                            new CameraFollow(List.of("lead", "partner"), 0, 0, 0, 1, 0, 0))));
            var at10 = world.resolve(scene, StageplayTimeline.sample(scene, 10));
            assertEquals(2, at10.world().actors().size());
            assertTrue(at10.actors().getLast().x() - at10.actors().getFirst().x() > 700);
            assertTrue(SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager().getActiveObjects()
                    .stream().anyMatch(o -> o.getSpawn() != null
                            && o.getSpawn().layoutIndex() == monitor.layoutIndex()
                            && o.getSpawn().objectId() == monitor.objectId()
                            && o.getSpawn().subtype() == monitor.subtype()), "The far performer's object must really be active");
            assertTrue(at10.baseCamera().x() > 200, "Group framing follows the entire cast");
            assertEquals(.75, at10.baseCamera().zoom());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(at10, world.resolve(scene, StageplayTimeline.sample(scene, 10)));
            assertFalse(at10.world().actors().stream().anyMatch(StageplayTimeline.LiveActor::dead));
        }
    }

    private static Actor actor(String id, String character, int x, int y) {
        return new Actor(id, id, "s2", character,
                List.of(new ActorKey(0, x, y, 0, -1, false, true, 1, Interpolation.HOLD)));
    }
}
