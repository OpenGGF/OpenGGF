package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.graphics.shaderlib.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.SuspensionTestAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.*;

/** Actual Engine display/capture handoff through the shared configured context; no phase substitute. */
@Isolated
class TestEngineSuspensionRendering {
    @TempDir Path directory;

    @Test void capturesSceneBeforeOverlaysAndResumesAfterPublishedZoom() throws Exception {
        var entryServices = EngineServices.current();
        try (var context = PresentationOffscreenTestContext.openConfigured(
                704, 512, PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            System.out.println("Owned suspension GL: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
            var config = SonicConfigurationService.createStandalone(directory);
            config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
            config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            config.resolveDisplayAspect();
            var audio = AudioManager.createSilent();
            assertInstanceOf(NullAudioBackend.class, audio.getBackend());
            var graphics = new GraphicsManager();
            var engine = new Engine(new EngineContext(config, graphics, audio, mock(RomManager.class),
                    mock(PerformanceProfiler.class), mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                    mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class)));
            var input = new InputHandler();
            set(engine, "inputHandler", input);
            engine.getGameLoop().setInputHandler(input);
            var camera = mock(Camera.class);
            set(engine, "camera", camera);
            set(engine, "windowWidth", 704); set(engine, "windowHeight", 512);
            set(engine, "viewportX", 32); set(engine, "viewportY", 24);
            set(engine, "viewportWidth", 640); set(engine, "viewportHeight", 448);
            var gameplay = mock(GameplayModeContext.class);
            when(gameplay.isGameplayRuntimeReady()).thenReturn(true);
            when(gameplay.getRunPolicy()).thenReturn(GameplayRunPolicy.stock());
            var world = mock(WorldSession.class);
            when(gameplay.getWorldSession()).thenReturn(world);
            when(gameplay.getCamera()).thenReturn(camera);
            var module = mock(GameModule.class);
            var zones = mock(ZoneRegistry.class);
            when(world.getGameModule()).thenReturn(module);
            when(module.getGameId()).thenReturn(GameId.S2);
            when(module.getZoneRegistry()).thenReturn(zones);
            var provider = mock(LevelSelectProvider.class);
            when(module.getLevelSelectProvider()).thenReturn(provider);
            when(provider.getState()).thenReturn(LevelSelectProvider.State.ACTIVE);
            var draws = new AtomicInteger();
            doAnswer(invocation -> { draws.incrementAndGet(); scene(); return null; }).when(provider).draw();
            var overlay = new AtomicBoolean(true);
            MasterTitleScreen title = null;
            try (var sessions = mockStatic(SessionManager.class)) {
                sessions.when(SessionManager::getCurrentWorldSession).thenReturn(world);
                sessions.when(SessionManager::getCurrentGameplayMode).thenReturn(gameplay);
                graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
                graphics.setViewport(32, 24, 640, 448);
                var ui = spy(new UiRenderPipeline(graphics));
                doAnswer(invocation -> { if (overlay.get()) rectangle(32, 24, 640, 448, 1, 1, 1); return null; })
                        .when(ui).renderFadePass();
                set(graphics, "uiRenderPipeline", ui);
                when(gameplay.getFadeManager()).thenReturn(graphics.getFadeManager());
                var lifecycle = new com.openggf.game.resources.PlcFrameLifecycleCoordinator(module);
                when(gameplay.plcFrameLifecycle()).thenReturn(lifecycle);
                var pipeline = graphics.getDisplayShaderPipeline();
                assertTrue(pipeline.activate(new DisplayShaderPreset("suspension-scene-control", ShaderPhase.SCENE,
                        List.of(new DisplayShaderPass(null, """
                                uniform sampler2D Texture;
                                uniform vec2 OutputSize;
                                void main() {
                                    vec4 c = texture2D(Texture, gl_FragCoord.xy / OutputSize);
                                    gl_FragColor = vec4(c.g, c.b, c.r, 1.0);
                                }
                                """, GlslShape.FRAGMENT_ONLY, 1, ScaleType.SOURCE, false, WrapMode.CLAMP_TO_EDGE)))));
                engine.getGameLoop().changeGameModeWithoutRewindBoundary(GameMode.LEVEL_SELECT);
                engine.getGameLoop().suspension.leave();
                assertTrue(engine.getGameLoop().suspension.preparing());

                call(engine, "displayFrame"); // update freezes; real draw/flush/SCENE/capture/overlay

                assertFalse(engine.getGameLoop().suspension.parked());
                assertTrue(engine.getGameLoop().suspension.preparing());
                assertFalse(SuspensionTestAccess.<Boolean>get(visit(engine), "suspendThumbnailRequested"));
                SuspendedGame.Thumbnail thumbnail = get(visit(engine), "pendingSuspendThumbnail");
                assertNotNull(thumbnail);
                assertEquals(320, thumbnail.width()); assertEquals(224, thumbnail.height());
                assertThumbnail(thumbnail, 40, 30, 0x0000ff); // top-left red rotated to blue
                assertThumbnail(thumbnail, 280, 30, 0xff0000);
                assertThumbnail(thumbnail, 40, 194, 0xff00ff);
                assertThumbnail(thumbnail, 280, 194, 0xffff00);
                var contaminated = ScreenshotCapture.captureFramebufferRegion(32, 24, 640, 448);
                assertEquals(0xffffff, contaminated.argb(80, 60) & 0xffffff,
                        "negative control: the later fade-pass effect visibly contaminates final pixels");
                engine.getGameLoop().step();
                assertFalse(engine.getGameLoop().suspension.parked(), "another loop step cannot park");
                assertEquals(1, draws.get());
                overlay.set(false);
                pipeline.dispose(); // the captured SCENE result must not be transformed a second time in the hub

                call(engine, "displayFrame"); // only this next display entry consumes readiness
                assertTrue(engine.getGameLoop().suspension.parked());
                assertEquals(1, draws.get(), "no new gameplay draw after publication");
                title = get(engine, "masterTitleScreen");
                assertNotNull(title);
                assertTrue(title.hasSuspendedGame());
                int zoomTicks = get(title, "SUSPEND_ZOOM_TICKS");
                for (int i = 0; i < zoomTicks + 2; i++) call(engine, "displayFrame");
                title.setStateForTest(MasterTitleScreen.State.ACTIVE);
                input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS);
                call(engine, "displayFrame");
                input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE);
                for (int i = 0; i < zoomTicks; i++) {
                    call(engine, "displayFrame");
                    assertTrue(engine.getGameLoop().suspension.parked());
                }
                var fullSize = ScreenshotCapture.captureFramebufferRegion(32, 24, 640, 448);
                assertPixel(fullSize, 80, 60, 0x0000ff);
                assertPixel(fullSize, 560, 60, 0xff0000);
                assertPixel(fullSize, 80, 388, 0xff00ff);
                assertPixel(fullSize, 560, 388, 0xffff00);
                assertEquals(1, draws.get(), "full-size zoom pixels are the saved frame, not a live redraw");
                call(engine, "displayFrame"); // update emits the request after full-size publication
                assertTrue(engine.getGameLoop().suspension.parked());
                call(engine, "displayFrame");
                assertFalse(engine.getGameLoop().suspension.parked());
                assertNull(get(visit(engine), "suspendedGame"));
                assertEquals(GameMode.LEVEL_SELECT, engine.getCurrentGameMode());
                assertEquals(2, draws.get(), "gameplay redraw starts only after that published opportunity");
            } finally {
                MasterTitleScreen current = get(engine, "masterTitleScreen");
                if (current != null) current.cleanup();
                // Production resume already cleans the old title; do not dispose its textures twice.
                graphics.cleanup();
                audio.destroy();
                Engine.clearGlobalInstance();
            }
        } finally {
            EngineServices.configure(entryServices);
        }
        assertSame(entryServices, EngineServices.current());
    }

    private static void scene() {
        rectangle(32, 248, 320, 224, 1, 0, 0);
        rectangle(352, 248, 320, 224, 0, 1, 0);
        rectangle(32, 24, 320, 224, 1, 1, 0);
        rectangle(352, 24, 320, 224, 0, 1, 1);
    }

    private static void rectangle(int x, int y, int width, int height, float red, float green, float blue) {
        glEnable(GL_SCISSOR_TEST); glScissor(x, y, width, height);
        glClearColor(red, green, blue, 1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
    }

    private static void assertThumbnail(SuspendedGame.Thumbnail image, int x, int y, int rgb) {
        int offset = (y * image.width() + x) * 4;
        int actual = (Byte.toUnsignedInt(image.rgba()[offset]) << 16)
                | (Byte.toUnsignedInt(image.rgba()[offset + 1]) << 8) | Byte.toUnsignedInt(image.rgba()[offset + 2]);
        assertEquals(rgb, actual, "captured logical pixel " + x + "," + y);
    }

    private static void assertPixel(RgbaImage image, int x, int y, int rgb) {
        assertEquals(rgb, image.argb(x, y) & 0xffffff, "published full-size pixel " + x + "," + y);
    }
}
