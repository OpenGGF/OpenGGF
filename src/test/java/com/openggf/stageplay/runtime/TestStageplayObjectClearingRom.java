package com.openggf.stageplay.runtime;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AnimalObjectInstance;
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

@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayObjectClearingRom {
    @Test void clearObjectsRemovesReleasedAnimalsWithoutRemovingRingsAndReplays() throws Exception {
        EngineServices.current().graphics().initHeadless();
        try (var library = SceneRomLibrary.open(Map.of("s3k", Path.of(System.getProperty("s3k.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            // Use the ICZ placement verified alive before clearing in the task's real scene simulation.
            // Free animals retain their native camera-relative lifetime; arbitrary scenery is not a stable fixture.
            var animal = new ObjectActor("animal", "Released animal", "s3k", 5, 1, 0, 0, -1,
                    List.of(new ObjectKey(0, 2152, 864, 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)),
                    List.of(), ObjectSourceKind.RELEASED_ANIMAL);
            var scene = StageplayDocument.empty("s3k", 5, 1, 1936, 768, 320, 224).withDurationFrames(60)
                    .withObjectActors(List.of(animal))
                    .withWorldCommands(List.of(new WorldCommand("clear", 30, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
            var before = world.resolve(scene, StageplayTimeline.sample(scene, 20));
            assertEquals(1, SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager()
                    .getActiveObjects().stream().filter(AnimalObjectInstance.class::isInstance).count());
            assertFalse(before.world().rings().isEmpty());
            var after = world.resolve(scene, StageplayTimeline.sample(scene, 40));
            assertTrue(SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager()
                    .getActiveObjects().stream().noneMatch(AnimalObjectInstance.class::isInstance));
            assertEquals(before.world().rings(), after.world().rings(), "Clear objects preserves native ring placements and state");
            assertEquals(before, world.resolve(scene, StageplayTimeline.sample(scene, 20)));
            assertEquals(after, world.resolve(scene, StageplayTimeline.sample(scene, 40)));
        }
    }
}
