package com.openggf.level.objects;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.level.objects.ObjectVisualFacing.Basis;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayWorldPresentation;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.openggf.level.objects.StageplayObjectDirection.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Native draw oracles: semantic direction is independent of source art and deliberate turn poses. */
class TestStageplayObjectFacingRendering {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void motobugFacesEitherWayFromEitherNativePlacementFlip() throws Exception {
        compareNativeDirections("s1", "sonic1.rom.path", 0x40, -1);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void rhinobotTurnPoseKeepsItsNativeLookBackWhileLogicalFacingChanges() throws Exception {
        compareNativeDirections("s3k", "s3k.rom.path",
                com.openggf.game.sonic3k.constants.Sonic3kObjectIds.RHINOBOT, 3);
    }

    private static void compareNativeDirections(String game, String property, int id, int pose) throws Exception {
        Path rom = Path.of(System.getProperty(property));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 560, 300);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 0, 0, settings);
            try (var library = SceneRomLibrary.open(Map.of(game, rom));
                 var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
                 var presentation = new StageplayWorldPresentation();
                 var grabber = new GlReadPixelsGrabber(320, 224)) {
                var scene = StageplayDocument.empty(game, 0, 0, 400, 200, 320, 224);
                world.resolve(scene, StageplayTimeline.sample(scene, 0));
                if (pose >= 0) {
                    // Advance the production world until its native enemy-art jobs publish.
                    // Renderer allocation alone can still expose transparent pending KosM tiles.
                    for (int frame = 1; frame < 240 && !rhinobotArtPublished(); frame++)
                        world.resolve(scene, StageplayTimeline.sample(scene, frame));
                    assertTrue(rhinobotArtPublished(), "Native Rhinobot art must publish before the pixel oracle");
                }
                GameServices.camera().setX((short) 400);
                GameServices.camera().setY((short) 200);
                var objects = GameServices.level().getObjectManager();
                var direction = ObjectDirectionAccess.forManager(objects);
                direction.clearObjects();
                byte[] empty = draw(presentation, grabber);
                var nativePictures = new EnumMap<Basis, byte[]>(Basis.class);
                var templates = new java.util.ArrayList<VisualTemplate>();
                for (int flags : List.of(0, 1)) {
                    var root = ObjectDirectionAccess.createNative(objects,
                            new ObjectSpawn(560, 300, id, 0, flags, false, 300));
                    if (pose >= 0) {
                        // Rhinobot starts as Map_Offscreen. Admit its actual native wait,
                        // sign scan, release and Init dispatch before capturing body art.
                        for (int dispatch = 0; dispatch < 3; dispatch++) {
                            objects.update(400, GameServices.sprites().getMainPlayable(), List.of(), dispatch);
                            objects.refreshPostCameraRenderState();
                        }
                    }
                    var basis = ObjectVisualFacing.resolve(root);
                    assertNotEquals(ObjectVisualFacing.Basis.NON_DIRECTIONAL, basis);
                    templates.add(direction.captureDynamic(root));
                    if (pose >= 0) setMappingFrame(root, pose);
                    byte[] pixels = draw(presentation, grabber);
                    assertFalse(java.util.Arrays.equals(empty, pixels), "Native sprite pixels must be present");
                    nativePictures.put(basis == ObjectVisualFacing.Basis.LEFT ? Basis.LEFT : Basis.RIGHT, pixels);
                    direction.removeDynamic(root);
                }
                assertEquals(2, nativePictures.size(), "Both native placement flips establish distinct logical directions");
                assertFalse(java.util.Arrays.equals(nativePictures.get(Basis.LEFT), nativePictures.get(Basis.RIGHT)));
                for (var template : templates) for (var facing : java.util.List.of(Basis.LEFT, Basis.RIGHT)) {
                    var root = direction.installVisual(template, new Direction(560, 300, facing, true, 0, pose));
                    assertArrayEquals(nativePictures.get(facing), draw(presentation, grabber),
                            game + " source=" + template.facing() + " requested=" + facing + " pose=" + pose);
                    direction.removeDynamic(root);
                }
            }
        }
    }

    private static boolean rhinobotArtPublished() {
        var sheet = GameServices.level().getObjectRenderManager().getSheet(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.RHINOBOT);
        if (sheet == null || sheet.getPatterns() == null) return false;
        for (var pattern : sheet.getPatterns()) if (pattern != null)
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                if (pattern.getPixel(x, y) != 0) return true;
        return false;
    }

    private static void setMappingFrame(ObjectInstance object, int frame) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("mappingFrame");
                field.setAccessible(true);
                field.setInt(object, frame);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        fail("Native mapping-frame owner is absent");
    }

    private static byte[] draw(StageplayWorldPresentation presentation, GlReadPixelsGrabber grabber) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        presentation.draw(GameServices.graphics(), GameServices.level(), 400, 200, 1, 320, 224, () -> {
            // Match StageplayLiveWorld: authored shot transitions own the reveal,
            // while a native S3K initial palette fade can still be black.
            com.openggf.graphics.PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> {
                GameServices.level().drawCinematicWorld(GameServices.sprites());
                GameServices.graphics().flush();
            });
        });
        return grabber.grab().clone();
    }
}
