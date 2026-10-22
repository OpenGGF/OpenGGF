package com.openggf.level;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Real ROM-backed native/presentation ownership across a multi-tile directed background. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayDirectedBackgroundRendering {
    private static final int WIDTH = 350, HEIGHT = 224;
    @AfterEach void restoreLogicGraphics() { GameServices.graphics().initHeadless(); }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void directedRasterAndAbortedTilesLeaveNativeRamPublicationResidencyAndPictureUntouched(int act) throws Exception {
        Path rom = Path.of(System.getProperty("s3k.rom.path"));
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        try (var boot = new HeadlessGameBoot(WIDTH, HEIGHT)) {
            boot.setBootToTitle(true); boot.boot(rom, 0, act);
            assertEquals(GL_NO_ERROR, glGetError(), "Initial native boot before any Stageplay draw");
            // The boot owns EGL, while this test owns the graphics caches it populates.
            // Close the world first, then every GraphicsManager GL object, then EGL.
            try (var graphicsOwner = new OwnedGraphicsResources(GameServices.graphics());
                 var errors = new GlErrorProbe(); var library = SceneRomLibrary.open(Map.of("s3k", rom));
                 var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode());
                 var grabber = new GlReadPixelsGrabber(WIDTH, HEIGHT)) {
                var scene = StageplayDocument.empty("s3k", 0, act, 1800, 700, WIDTH, HEIGHT).withDurationFrames(90);
                var frame = world.resolve(scene, StageplayTimeline.sample(scene, 60));
                errors.assertClean("native Stageplay world assembly and 60 logic frames");
                var level = GameServices.level();
                var renderer = level.spritePresentationRenderer();
                assertSame(com.openggf.level.scroll.DirectedScrollPresentation.ENABLED,
                        level.gameModule.getGameService(com.openggf.level.scroll.DirectedScrollPresentation.class),
                        "The real Stageplay world module opts into immutable directed projection capture");
                int directedX = 1952;
                if (act == 1) {
                    var events = ((com.openggf.game.sonic3k.Sonic3kLevelEventManager)
                            GameServices.module().getLevelEventProvider()).getAizEvents();
                    setField(events, "battleshipAutoScrollActive", true);
                    setField(events, "battleshipWrapX", 0x46c0);
                    assertTrue(events.isBattleshipForestLoopActive(), "Exercise the actual retained Plane A owner");
                    level.camera.setX((short) 0x4600); level.camera.setXCopy((short) 0x4600);
                    level.tilemapManager.setForegroundRingCamera(0x4600, WIDTH);
                    level.parallaxManager.update(0, act, level.camera, level.frameCounter, level.level);
                    directedX = 0x4680;
                }
                errors.assertClean("AIZ fixture native event/camera/parallax setup");
                drawOrdinary(level);
                errors.assertClean("first ordinary native draw before directed rendering");
                var nativeTiles = level.tilemapManager;
                var foreground = captureForeground(nativeTiles);
                if (act == 1) assertTrue(foreground.seeded());
                byte[] ordinary = grabber.grab().clone();
                errors.assertClean("ordinary framebuffer readback");
                var camera = GameServices.camera().capture();
                var tables = renderer.spriteTables.capture();
                var scroll = LevelScrollPresentation.capture(level, renderer.getCurrentAdvancedRenderFrameState());
                var objects = level.getObjectManager().rewindSnapshottable().capture();
                byte[] descriptors = level.tilemapManager.getBackgroundTilemapData().clone();
                var ring = level.tilemapManager.capturePersistentBgNametableSnapshot();
                TilemapGpuRenderer gpu = GameServices.graphics().getTilemapGpuRenderer();
                var gpuState = gpu.captureBackgroundRenderState();
                var view = new StageplayTimeline.CameraPose(directedX, 620, .5);
                var tiles = new ArrayList<String>();
                world.draw(frame, view, (x, y, width, height) -> {
                    tiles.add(x + ":" + y + ":" + width + ":" + height);
                    errors.assertClean("directed native tile " + x + "," + y);
                });
                errors.assertClean("completed directed composition and state restore");
                assertEquals(List.of(directedX + ":620:350:224", (directedX + WIDTH) + ":620:350:224",
                        directedX + ":844:350:224", (directedX + WIDTH) + ":844:350:224"), tiles,
                        "A 700x448 full raster must be sliced at one coherent camera phase, including left%16=14");
                byte[] directed = grabber.grab();
                errors.assertClean("directed framebuffer readback");
                for (int pixel = 3; pixel < directed.length; pixel += 4)
                    assertEquals(255, directed[pixel] & 255, "Completed world must be opaque before filtering");
                assertSame(nativeTiles, level.tilemapManager);
                assertForeground(foreground, level.tilemapManager);
                assertNativeOwners(level, renderer, camera, tables, scroll, objects, descriptors, ring, gpuState);
                drawOrdinary(level);
                assertArrayEquals(ordinary, grabber.grab(), "An ordinary native picture must remain byte-identical after directed drawing");
                errors.assertClean("ordinary native draw/readback after directed composition");

                int failureX = directedX;
                assertThrows(IllegalStateException.class, () -> LevelSpritePresentation.withCinematicView(level,
                        failureX, 620, 700, 448, () -> LevelSpritePresentation.withCinematicTile(level, 350, 224, WIDTH, HEIGHT, () -> {
                            level.drawCinematicWorld(GameServices.sprites());
                            throw new IllegalStateException("abort with queued private background commands");
                        })));
                errors.assertClean("aborted queued tile and private residency restore");
                assertSame(nativeTiles, level.tilemapManager);
                assertForeground(foreground, level.tilemapManager);
                assertNativeOwners(level, renderer, camera, tables, scroll, objects, descriptors, ring, gpuState);
                drawOrdinary(level);
                assertArrayEquals(ordinary, grabber.grab(), "An aborted tile cannot poison the following ordinary draw");
                errors.assertClean("ordinary native draw/readback after aborted tile");
            }
        }
    }

    private record OwnedGraphicsResources(com.openggf.graphics.GraphicsManager graphics) implements AutoCloseable {
        @Override public void close() { graphics.cleanup(); }
    }

    /** Bounded synchronous diagnostics identify the actual GL operation, not a later consumer. */
    private static final class GlErrorProbe implements AutoCloseable {
        private final List<String> messages = new ArrayList<>();
        private org.lwjgl.opengl.GLDebugMessageCallback callback;
        private boolean output, synchronous;
        GlErrorProbe() {
            if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL43) return;
            output = glIsEnabled(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT);
            synchronous = glIsEnabled(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            callback = org.lwjgl.opengl.GLDebugMessageCallback.create((source, type, id, severity, length, message, user) -> {
                if (type != org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR || messages.size() >= 12) return;
                String stack = StackWalker.getInstance().walk(frames -> frames.limit(18)
                        .map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
                messages.add(org.lwjgl.system.MemoryUtil.memUTF8(message, length) + "\n" + stack);
            });
            glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT);
            glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            org.lwjgl.opengl.GL43.glDebugMessageCallback(callback, 0);
        }
        void assertClean(String stage) {
            assertEquals(GL_NO_ERROR, glGetError(), () -> stage + "\n" + String.join("\n", messages));
        }
        @Override public void close() {
            if (callback == null) return;
            org.lwjgl.opengl.GL43.glDebugMessageCallback((org.lwjgl.opengl.GLDebugMessageCallbackI) null, 0);
            if (!output) glDisable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT);
            if (!synchronous) glDisable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            callback.free();
        }
    }

    private static void assertNativeOwners(LevelManager level, LevelRenderer renderer,
            com.openggf.game.rewind.snapshot.CameraSnapshot camera, LevelSpritePresentation.Tables.State tables,
            LevelScrollPresentation scroll, com.openggf.game.rewind.snapshot.ObjectManagerSnapshot objects,
            byte[] descriptors, com.openggf.game.rewind.snapshot.LevelTilemapSnapshot ring,
            TilemapGpuRenderer.BackgroundRenderState gpu) {
        assertEquals(camera, GameServices.camera().capture(), "All native camera registers and copies remain native-owned");
        assertEquals(tables, renderer.spriteTables.capture(), "Both pending and published SAT/scroll generations remain unchanged");
        assertEquals(scroll, LevelScrollPresentation.capture(level, renderer.getCurrentAdvancedRenderFrameState()), "No native parallax phase advances");
        var after = level.getObjectManager().rewindSnapshottable().capture();
        assertArrayEquals(objects.usedSlotsBits(), after.usedSlotsBits());
        assertEquals(objects.slots(), after.slots(), "Placed native object RAM must not be changed by a presentation camera");
        assertEquals(objects.dynamicObjects(), after.dynamicObjects(), "Dynamic native object RAM must not be changed by a presentation camera");
        assertEquals(objects.frameCounter(), after.frameCounter());
        assertEquals(objects.vblaCounter(), after.vblaCounter());
        assertArrayEquals(descriptors, level.tilemapManager.getBackgroundTilemapData(), "Native CPU descriptors must not move");
        var afterRing = level.tilemapManager.capturePersistentBgNametableSnapshot();
        assertArrayEquals(ring.descriptors(), afterRing.descriptors());
        assertEquals(ring.originXTiles(), afterRing.originXTiles()); assertEquals(ring.originYTiles(), afterRing.originYTiles());
        assertEquals(ring.alignedBgX(), afterRing.alignedBgX()); assertEquals(ring.alignedBgY(), afterRing.alignedBgY());
        assertEquals(gpu, GameServices.graphics().getTilemapGpuRenderer().captureBackgroundRenderState(), "Native GPU ring origins and generation remain unchanged");
    }

    private record ForegroundState(byte[] descriptors, boolean seeded, int cameraX, int screenWidth,
                                   int wrapOffset, int lastLeft, int lastRight) { }

    private static ForegroundState captureForeground(LevelTilemapManager tiles) throws Exception {
        return new ForegroundState(tiles.getForegroundTilemapData().clone(),
                (boolean) field(tiles, "foregroundRingSeeded"), (int) field(tiles, "foregroundRingCameraX"),
                (int) field(tiles, "foregroundRingScreenWidthPx"), (int) field(tiles, "foregroundRingWorldWrapOffset"),
                (int) field(tiles, "foregroundRingLastLeftCol"), (int) field(tiles, "foregroundRingLastRightCol"));
    }
    private static void assertForeground(ForegroundState expected, LevelTilemapManager tiles) throws Exception {
        var actual = captureForeground(tiles);
        assertArrayEquals(expected.descriptors(), actual.descriptors(), "Native Plane A descriptor history stays unchanged");
        assertEquals(expected.seeded(), actual.seeded()); assertEquals(expected.cameraX(), actual.cameraX());
        assertEquals(expected.screenWidth(), actual.screenWidth()); assertEquals(expected.wrapOffset(), actual.wrapOffset());
        assertEquals(expected.lastLeft(), actual.lastLeft()); assertEquals(expected.lastRight(), actual.lastRight());
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static void drawOrdinary(LevelManager level) {
        level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
        GameServices.graphics().flush();
    }
}
