package com.openggf.level;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Native BG-only comparator for the complete-cast tall/directed-camera review. */
class TestStageplayNativeBackgroundProjection {
    private static final int WIDTH = 320, HEIGHT = 224;

    @AfterEach void restoreLogicGraphics() { GameServices.graphics().initHeadless(); }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void ghzTallBackdropExtendsNativeBottomWithoutStackingAnotherSky() throws Exception {
        compare("s1", "sonic1.rom.path", 0, 0, 10496, 592);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void ehzRaisedGroupCameraUsesNativeBackdropWithoutSecondCloudStrip() throws Exception {
        compare("s2", "sonic2.rom.path", 0, 0, 0, 400);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void iczBottomBandIsComparedWithNativeAtTheSameCamera() throws Exception {
        compare("s3k", "s3k.rom.path", 5, 1, 17296, 1376);
    }

    private static void compare(String game, String property, int zone, int act, int x, int y) throws Exception {
        Path rom = Path.of(System.getProperty(property));
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        try (var boot = new HeadlessGameBoot(WIDTH, HEIGHT)) {
            boot.setBootToTitle(true); boot.boot(rom, zone, act);
            try (var library = SceneRomLibrary.open(Map.of(game, rom));
                 var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
                 var grabber = new GlReadPixelsGrabber(WIDTH, HEIGHT)) {
                var scene = StageplayDocument.empty(game, zone, act, x, y, WIDTH, HEIGHT).withDurationFrames(90);
                var resolved = world.resolve(scene, StageplayTimeline.sample(scene, 60));
                assertEquals(x, resolved.camera().x(), "Director framing is independent of gameplay camera-origin bounds");
                assertEquals(y, resolved.camera().y(), "ICZ ship framing above ROM camera minimum must remain authored");
                var nativeCamera = GameServices.camera().capture();
                for (var view : java.util.List.of(new StageplayTimeline.CameraPose(-100, -256, 1),
                        new StageplayTimeline.CameraPose(x, 10000, 1)))
                    assertEquals(view, world.boundedCamera(view), "Above/below-layout framing is still a bounded film raster");
                assertThrows(IllegalArgumentException.class,
                        () -> world.boundedCamera(new StageplayTimeline.CameraPose(1_048_577, 0, 1)));
                assertEquals(nativeCamera, GameServices.camera().capture(), "Director validation never changes native locks");
                var level = GameServices.level();
                var renderer = level.spritePresentationRenderer();
                level.camera.setX((short) x); level.camera.setY((short) y);
                level.camera.setXCopy((short) x); level.camera.setYCopy((short) y);
                level.parallaxManager.update(zone, act, level.camera, level.frameCounter, level.level);
                // Initialize the ordinary renderer's current mode/resources first.
                level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.tilesOnly());
                GameServices.graphics().flush();
                drawBackground(renderer, null);
                byte[] nativePicture = grabber.grab().clone();
                writeEvidence(game + "-native", nativePicture);
                var displayed = field("currentScrollPresentation");
                var tile = field("cinematicScroll");
                LevelSpritePresentation.withCinematicView(level, x, y, WIDTH, 384, () -> {
                    LevelSpritePresentation.withCinematicTile(level, 0, 0, WIDTH, HEIGHT, () -> {
                        drawBackground(renderer, read(tile, renderer));
                        byte[] first = grabber.grab().clone();
                        assertArrayEquals(nativePicture, first,
                                "Directed camera must preserve the native 224-line backdrop at the same camera");
                        writeEvidence(game + "-directed-top", first);
                    });
                    LevelSpritePresentation.withCinematicTile(level, 0, 224, WIDTH, HEIGHT, () -> {
                        drawBackground(renderer, read(tile, renderer));
                        byte[] lower = grabber.grab().clone();
                        for (int row = 0; row < 160; row++) for (int column = 0; column < WIDTH; column++) {
                            int offset = ((HEIGHT - 1 - row) * WIDTH + column) * 4;
                            int lastNative = column * 4; // GL readback is bottom-up.
                            for (int channel = 0; channel < 4; channel++)
                                assertEquals(nativePicture[lastNative + channel], lower[offset + channel],
                                        "Extra backdrop rows extend the native edge: " + column + "," + row);
                        }
                        writeEvidence(game + "-directed-lower", lower);
                    });
                });
                displayed.set(renderer, null);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                GameServices.graphics().cleanup();
            }
        }
    }

    private static void drawBackground(LevelRenderer renderer, Object scroll) {
        Field displayed = field("currentScrollPresentation");
        Object previous = read(displayed, renderer);
        try {
            displayed.set(renderer, scroll);
            PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(), () -> {
                GameServices.level().setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                renderer.renderBackgroundShader(new ArrayList<>());
                GameServices.graphics().flush();
            });
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        } finally {
            try { displayed.set(renderer, previous); }
            catch (IllegalAccessException error) { throw new AssertionError(error); }
        }
    }

    private static Field field(String name) {
        try {
            Field field = LevelRenderer.class.getDeclaredField(name);
            field.setAccessible(true); return field;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object read(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    /** Optional task-owned stills; ordinary runs create no evidence files. */
    private static void writeEvidence(String name, byte[] rgba) {
        String destination = System.getProperty("openggf.stageplay.backgroundEvidence");
        if (destination == null) return;
        try {
            Path directory = Path.of(destination); Files.createDirectories(directory);
            var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int offset = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
                image.setRGB(x, y, 0xff000000 | (rgba[offset] & 255) << 16
                        | (rgba[offset + 1] & 255) << 8 | rgba[offset + 2] & 255);
            }
            ImageIO.write(image, "png", directory.resolve(name + ".png").toFile());
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
