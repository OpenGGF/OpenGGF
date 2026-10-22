package com.openggf.stageplay.runtime;

import static com.openggf.stageplay.StageplayDocument.*;

import static org.junit.jupiter.api.Assertions.*;

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

/** Logic-only Free-to-directed transitions must capture current native art without a prior draw. */
@RequiresRom(SonicGame.SONIC_2)
class TestStageplayObjectCaptureRom {
    @BeforeEach
    void graphics() {
        EngineServices.current().graphics().initHeadless();
    }

    @Test
    void buzzerCapturesNativePaletteWithoutDrawingAndReplaysItsDirectedTransition()
            throws Exception {
        transition(Sonic2ObjectIds.BUZZER, 0, 100, 600, 0, 500, 8, false);
    }

    @Test
    void multipartBossCapturesLatestNativePaletteAfterCacheEvictionWithoutDrawing()
            throws Exception {
        transition(Sonic2ObjectIds.EHZ_BOSS, 1, 10704, 1062, 10480, 704, 8, true);
    }

    @Test
    void constructorPreparationEmitsNoAudioCommandsAndUnwindsSuppressionOnSuccessAndFailure()
            throws Exception {
        try (var library =
                SceneRomLibrary.open(
                        Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))))) {
            // @RequiresRom owns this fixture session. Transfer that exact disposable session
            // to the offline host before retiring it; never clear an unrelated active world.
            try (var owner =
                    SessionManager.getCurrentGameplayMode() == null
                            ? StageplayLiveWorld.openOffline(library)
                            : StageplayLiveWorld.adoptOffline(
                                    library, SessionManager.getCurrentGameplayMode())) {
                owner.reset();
            }
            assertNull(SessionManager.getCurrentWorldSession());
            var audio = EngineServices.current().audio();
            assertFalse(audio.isRewindReplaySuppressed());
            int before = audio.commandTimeline().entryCount();
            var anchor =
                    new ObjectKey(
                            0,
                            10704,
                            1062,
                            0,
                            -1,
                            false,
                            true,
                            Interpolation.HOLD,
                            ObjectMode.POSE);
            var boss =
                    new ObjectActor(
                            "boss",
                            "Boss",
                            "s2",
                            0,
                            1,
                            Sonic2ObjectIds.EHZ_BOSS,
                            0,
                            -1,
                            List.of(anchor),
                            List.of());
            assertFalse(
                    StageplayObjectTemplateBake.prepare(library, boss, anchor).parts().isEmpty());
            assertEquals(before, audio.commandTimeline().entryCount());
            assertFalse(audio.isRewindReplaySuppressed());
            assertNull(SessionManager.getCurrentWorldSession());
            var missing =
                    new ObjectActor(
                            "missing",
                            "Missing factory",
                            "s2",
                            0,
                            1,
                            0xff,
                            0,
                            -1,
                            List.of(anchor),
                            List.of());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StageplayObjectTemplateBake.prepare(library, missing, anchor));
            assertEquals(before, audio.commandTimeline().entryCount());
            assertFalse(audio.isRewindReplaySuppressed());
            assertNull(SessionManager.getCurrentWorldSession());
        }
    }

    private static void transition(
            int objectId,
            int act,
            int x,
            int y,
            int cameraX,
            int cameraY,
            int switchFrame,
            boolean evictPaletteCache)
            throws Exception {
        try (var library =
                        SceneRomLibrary.open(
                                Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
                var world =
                        SessionManager.getCurrentGameplayMode() == null
                                ? StageplayLiveWorld.openOffline(library)
                                : StageplayLiveWorld.adoptOffline(
                                        library, SessionManager.getCurrentGameplayMode())) {
            var role =
                    new ObjectActor(
                            "performer",
                            "Native performer",
                            "s2",
                            0,
                            act,
                            objectId,
                            0,
                            -1,
                            List.of(
                                    new ObjectKey(
                                            0,
                                            x,
                                            y,
                                            0,
                                            -1,
                                            false,
                                            true,
                                            Interpolation.HOLD,
                                            ObjectMode.FREE),
                                    new ObjectKey(
                                            switchFrame,
                                            x + 20,
                                            y,
                                            0,
                                            -1,
                                            false,
                                            true,
                                            Interpolation.HOLD,
                                            ObjectMode.PUPPET)),
                            List.of());
            var scene =
                    StageplayDocument.empty("s2", 0, act, cameraX, cameraY, 320, 224)
                            .withDurationFrames(switchFrame + 12)
                            .withObjectActors(List.of(role))
                            .withWorldCommands(
                                    List.of(
                                            new WorldCommand(
                                                    "empty",
                                                    0,
                                                    WorldCommandKind.CLEAR_OBJECTS,
                                                    "",
                                                    ObjectTarget.none())));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertFalse(EngineServices.current().graphics().isGlInitialized());
            if (evictPaletteCache) {
                // Decoded Level palettes remain authoritative; GPU/CPU upload residency may be
                // absent in a logic-only scene and must not determine whether direction works.
                EngineServices.current().graphics().clearPaletteTextures();
            }
            int endingFrame = switchFrame + 6;
            var ending = world.resolve(scene, StageplayTimeline.sample(scene, endingFrame));
            var inspection = world.objectInspection().getFirst();
            assertFalse(inspection.parts().isEmpty());
            assertTrue(
                    inspection.parts().stream().allMatch(part -> !part.mappingFrames().isEmpty()));
            if (objectId == Sonic2ObjectIds.EHZ_BOSS) {
                assertTrue(
                        inspection.parts().size() > 1,
                        "Boss graph must retain native managed children");
            }
            assertTrue(
                    ending.world().objects().stream()
                            .anyMatch(
                                    object -> object.type().equals("Stageplay:performer:PUPPET")));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(
                    ending, world.resolve(scene, StageplayTimeline.sample(scene, endingFrame)));
        }
    }
}
