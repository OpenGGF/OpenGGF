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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"5,POSE", "60,POSE", "5,PUPPET", "60,PUPPET"})
    void releasedAnimalCanBeDirectedAfterNativeTicks(int switchFrame, ObjectMode mode) throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("s2", Path.of(System.getProperty("sonic2.rom.path"))));
             var world = SessionManager.getCurrentGameplayMode() == null ? StageplayLiveWorld.openOffline(library)
                     : StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var role = new ObjectActor("animal", "Animal", "s2", 0, 0, 0, 0, -1,
                    List.of(new ObjectKey(0, 564, 624, 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE),
                            new ObjectKey(switchFrame, 564, 624, 0, -1, false, true, Interpolation.HOLD, mode)),
                    List.of(), ObjectSourceKind.RELEASED_ANIMAL);
            var scene = StageplayDocument.empty("s2", 0, 0, 0, 480, 480, 270)
                    .withActors(List.of(new Actor("lead", "Lead", "s2", "sonic", List.of(
                            new ActorKey(0, 100, 656, 0, -1, false, true, 1, Interpolation.HOLD)))))
                    .withPuppets(List.of(new PuppetTrack("lead", true, 0, List.of())))
                    .withDurationFrames(switchFrame + 12).withObjectActors(List.of(role))
                    .withWorldCommands(List.of(new WorldCommand("empty", 0, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
            for (int frame = 0; frame < switchFrame; frame++) {
                world.resolve(scene, StageplayTimeline.sample(scene, frame));
                var manager = SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager();
                assertTrue(manager.getActiveObjects().stream().anyMatch(object ->
                                object instanceof com.openggf.level.objects.AnimalObjectInstance && !object.isDestroyed()),
                        "The cast activation window must retain the living native animal at frame " + frame);
            }
            var directed = world.resolve(scene, StageplayTimeline.sample(scene, switchFrame + 6));
            assertFalse(world.objectInspection().getFirst().parts().isEmpty());
            var objects = SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager();
            var picture = com.openggf.level.objects.ObjectDirectionAccess.forManager(objects)
                    .rewindSnapshottable().capture().directed().getFirst().template();
            assertFalse(picture.patterns().isEmpty(), "Keep actual ROM appearance");
            assertFalse(picture.palettes().isEmpty());
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(directed, world.resolve(scene, StageplayTimeline.sample(scene, switchFrame + 6)));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ObjectMode.class, names = {"PUPPET", "POSE"})
    void buzzerCapturesNativePaletteWithoutDrawingAndReplaysItsDirectedTransition(ObjectMode mode)
            throws Exception {
        transition(Sonic2ObjectIds.BUZZER, 0, 400, 600, 0, 500, 8, false, mode);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ObjectMode.class, names = {"PUPPET", "POSE"})
    void multipartBossCapturesLatestNativePaletteAfterCacheEvictionWithoutDrawing(ObjectMode mode)
            throws Exception {
        transition(Sonic2ObjectIds.EHZ_BOSS, 1, 10704, 1062, 10480, 704, 8, true, mode);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ObjectMode.class, names = {"PUPPET", "POSE"})
    void monitorCanBeTakenOverOutsideTheMainView(ObjectMode mode) throws Exception {
        transition(Sonic2ObjectIds.MONITOR, 0, 400, 600, 0, 500, 8, false, mode);
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
            boolean evictPaletteCache,
            ObjectMode mode)
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
                                            mode)),
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
            world.resolve(scene, StageplayTimeline.sample(scene, switchFrame - 1));
            var level = SessionManager.getCurrentGameplayMode().getLevelManager().getCurrentLevel();
            var nativePalettes = new java.util.ArrayList<java.util.List<Integer>>();
            for (int line = 0; line < level.getPaletteCount(); line++) {
                var palette = level.getPalette(line);
                var colors = new java.util.ArrayList<Integer>();
                for (int color = 0; color < 16; color++) colors.add((color == 0 ? 0 : 0xff000000)
                        | (palette.red(color) & 255) << 16 | (palette.green(color) & 255) << 8 | (palette.blue(color) & 255));
                nativePalettes.add(colors);
            }
            if (evictPaletteCache) {
                // Capture uses the palette at entry to this tick, before native cycling advances.
                // Cache eviction must still resolve the authoritative decoded native palette.
                EngineServices.current().graphics().clearPaletteTextures();
            }
            int endingFrame = switchFrame + 6;
            world.resolve(scene, StageplayTimeline.sample(scene, switchFrame));
            var manager = SessionManager.getCurrentGameplayMode().getLevelManager().getObjectManager();
            var picture = com.openggf.level.objects.ObjectDirectionAccess.forManager(manager)
                    .rewindSnapshottable().capture().directed().getFirst().template();
            assertFalse(picture.palettes().isEmpty());
            for (var palette : picture.palettes()) assertTrue(nativePalettes.contains(palette.argb()),
                    "Captured pre-tick palette " + palette.argb() + " must match native pre-tick palettes " + nativePalettes);
            assertTrue(picture.palettes().stream().flatMap(palette -> palette.argb().stream())
                    .anyMatch(argb -> (argb & 0xffffff) != 0), "Blank palettes do not establish native color capture");
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
                                    object -> object.type().equals("Stageplay:performer:" + mode)));
            world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals(
                    ending, world.resolve(scene, StageplayTimeline.sample(scene, endingFrame)));
        }
    }
}
