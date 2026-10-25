package com.openggf;

import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.RomDetectionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.scene.host.ModSceneHost;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import openggf.timeattack.mp.InviteCodeView;
import openggf.timeattack.ui.FakeViewInput;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/** Production scene pixels and Engine activation with an injected window presentation. */
@Isolated
class TestEngineSceneFocusPresentation {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;
    @TempDir Path directory;

    private final class Visit implements AutoCloseable {
        final PresentationOffscreenTestContext context = PresentationOffscreenTestContext.openConfigured(
                WIDTH, HEIGHT, PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        final ModSceneHost host;
        final SceneFocusPresentation presentation = new SceneFocusPresentation();
        final InputHandler input = new InputHandler(InputBindingFactory.standaloneSupplier(),
                GamepadStateSource.noop(), System::nanoTime);
        final InviteCodeView invite = new InviteCodeView(() -> new InviteCodeView.Snapshot(
                "7K3 M9X", 600_000, 1, true, true, true, ""), action -> {}, cue -> {}, () -> 0);
        final List<String> events = new ArrayList<>();
        final float[] projection = new Matrix4f().ortho(0, WIDTH, 0, HEIGHT, -1, 1).get(new float[16]);
        final int[] viewport = {0, 0, WIDTH, HEIGHT};
        RgbaImage front;
        int draws;
        int updates;
        int swaps;

        Visit() { this(new ModSceneHost()); }

        Visit(ModSceneHost host) {
            this.host = host;
            var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    owners -> new ModStateSaveResult.Saved(), owners -> {});
            host.open(ModContextTestAccess.ownedScene("privacy", () -> new ModScene() {
                @Override public void enter(SceneContext context) {}
                @Override public void update(SceneContext context) { updates++; }
                @Override public void draw(SceneContext context, SceneCanvas canvas) {
                    draws++;
                    invite.draw(canvas);
                }
                @Override public void focusLost(SceneContext context) {
                    events.add("hide");
                    invite.hide();
                }
            }, boundary), new SceneServices(null, null, directory, null, null, () -> {}), WIDTH, HEIGHT);
            glDisable(GL_DITHER);
        }

        void scene(boolean overlay) {
            clear(0x247C45);
            if (overlay) host.drawOver(WIDTH, HEIGHT, projection, viewport);
            else host.draw(projection, viewport);
        }

        boolean frame(BooleanSupplier display) {
            return presentation.displayAndSwap(display, this::swap, host::renderedSubmissions);
        }

        void swap() {
            int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
                front = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
                swaps++;
                events.add("swap");
            } finally {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            }
        }

        void reveal(boolean overlay) {
            scene(overlay);
            RgbaImage masked = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
            new FakeViewInput().tapEnter(keys -> invite.update(keys, SceneMouse.none()));
            assertTrue(frame(() -> { scene(overlay); return true; }));
            assertTrue(codePixelsDiffer(masked, front), "Revealed invite glyphs must reach the presented frame");
            assertTrue(Arrays.stream(front.pixels()).distinct().count() > 4, "A blank control cannot prove withdrawal");
            events.clear();
        }

        boolean activate(boolean active, boolean iconify) {
            return Engine.applyWindowActivation(active, input,
                    () -> events.add("pause"), () -> events.add("resume"),
                    () -> {
                        if (iconify) host.windowIconifyChanged(!active);
                        else host.windowFocusChanged(active);
                    }, () -> presentation.withdraw(this::swap));
        }

        @Override public void close() {
            try { host.cleanup(); }
            finally { context.close(); }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void losingFocusOrIconifyingWithdrawsPresentedInvitesWithoutAnotherFrame(boolean iconify) {
        try (var visit = new Visit()) {
            visit.reveal(false);
            int draws = visit.draws;
            visit.input.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);

            assertTrue(visit.activate(false, iconify));

            assertNeutral(visit.front);
            assertEquals(List.of("hide", "pause", "swap"), visit.events);
            assertEquals(draws, visit.draws, "The focus callback must not reenter creator drawing");
            assertEquals(0, visit.updates, "Withdrawing presented pixels must not advance the scene");
            assertFalse(visit.input.isAnyModifierDown());
            visit.events.clear();
            assertTrue(visit.activate(false, iconify));
            assertEquals(List.of("pause"), visit.events, "Duplicate inactive callbacks cannot present again");
            assertFalse(visit.activate(true, iconify));
            assertEquals(List.of("pause", "resume"), visit.events);
        }
    }

    @Test void levelWithSceneOverlayWithdrawsTheAlreadyPresentedInvite() {
        var previous = EngineServices.current();
        var graphics = mock(GraphicsManager.class);
        var audio = mock(AudioManager.class);
        var config = SonicConfigurationService.createStandalone(directory);
        try (var sessions = mockStatic(SessionManager.class)) {
            var loop = new GameLoop(new EngineContext(config, graphics, audio, mock(RomManager.class),
                    mock(PerformanceProfiler.class), mock(DebugOverlayManager.class), mock(PlaybackDebugManager.class),
                    mock(RomDetectionService.class), mock(CrossGameFeatureProvider.class)));
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short) WIDTH);
            when(camera.getHeight()).thenReturn((short) HEIGHT);
            SuspensionTestAccess.set(loop, "camera", camera);
            try (var visit = new Visit(loop.modSceneHost)) {
                when(graphics.isGlInitialized()).thenReturn(true);
                when(graphics.getViewportWidth()).thenReturn(WIDTH);
                when(graphics.getViewportHeight()).thenReturn(HEIGHT);
                when(graphics.getProjectionMatrixBuffer()).thenReturn(visit.projection);
                loop.setGameMode(GameMode.MOD_SCENE);
                new SceneRunLauncher(loop, host -> host, List.of("s2"),
                        spec -> fail("Focus presentation cannot start gameplay"), reason -> {})
                        .launchBehind(new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct()), new RunHost() {});
                loop.setGameMode(GameMode.LEVEL);
                visit.reveal(true);
                long submissions = visit.host.renderedSubmissions();
                assertTrue(visit.frame(() -> { clear(0x247C45); loop.renderHostedRunOverlay(); return true; }));
                assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(submissions + 1, visit.host.renderedSubmissions(), "launchBehind must actually render the scene over LEVEL");
                int draws = visit.draws;
                int swaps = visit.swaps;
                clearInvocations(graphics, audio);

                assertTrue(Engine.applyWindowActivation(false, visit.input, loop::pause, loop::resume,
                        () -> visit.host.windowFocusChanged(false), () -> visit.presentation.withdraw(visit::swap)));

                assertNeutral(visit.front);
                assertTrue(loop.isPaused());
                assertEquals(swaps + 1, visit.swaps);
                assertEquals(draws, visit.draws);
                assertEquals(0, visit.updates);
                verify(graphics, never()).flush();
                verify(audio, never()).update();
                loop.hostedRuns.end(RunEndReason.LEFT);
            }
        } finally {
            EngineServices.configure(previous);
        }
    }

    @Test void suspendedSceneCannotLeaveItsPreviouslyPresentedRecordingVisible() {
        try (var visit = new Visit()) {
            visit.reveal(false);
            visit.host.suspend();
            int draws = visit.draws;

            visit.activate(false, false);

            assertNeutral(visit.front);
            assertTrue(visit.host.recordedFrame().isEmpty());
            visit.activate(true, false);
            assertTrue(visit.frame(() -> { clear(0); visit.host.draw(visit.projection, visit.viewport); return true; }));
            assertNeutral(visit.front);
            assertEquals(draws, visit.draws, "A suspended visit must not reconstruct the withdrawn recording");
        }
    }

    @Test void ordinaryGameplayStaysVisibleEvenWithAnOpenSuspendedVisit() {
        try (var visit = new Visit()) {
            visit.reveal(false);
            visit.host.suspend();
            assertTrue(visit.frame(() -> { clear(0x247C45); return true; }));
            RgbaImage gameplay = visit.front;
            int swaps = visit.swaps;

            visit.activate(false, false);

            assertArrayEquals(gameplay.pixels(), visit.front.pixels());
            assertEquals(swaps, visit.swaps, "An open scene alone does not make gameplay private");
        }
    }

    @Test void abortedGameplayFrameCannotReplaceTheLastPresentedSceneIdentity() {
        try (var visit = new Visit()) {
            visit.reveal(false);
            assertFalse(visit.frame(() -> { clear(0x247C45); return false; }));
            visit.host.close();

            visit.activate(false, false);

            assertNeutral(visit.front);
            assertEquals(2, visit.swaps);
        }
    }

    @Test void abortedSceneFrameCannotMakeThePresentedGameplayPrivate() {
        try (var visit = new Visit()) {
            assertTrue(visit.frame(() -> { clear(0x247C45); return true; }));
            RgbaImage gameplay = visit.front;
            assertFalse(visit.frame(() -> { visit.scene(true); return false; }));

            visit.activate(false, false);

            assertArrayEquals(gameplay.pixels(), visit.front.pixels());
            assertEquals(1, visit.swaps);
        }
    }

    @Test void recordingAndEmptyRenderingDoNotMarkGameplayAsPresentedSceneContent() {
        try (var visit = new Visit()) {
            assertTrue(visit.frame(() -> { clear(0x247C45); visit.host.draw(null, null); return true; }));
            assertEquals(0, visit.host.renderedSubmissions());
            visit.host.suspend();
            visit.host.windowFocusChanged(false);
            assertTrue(visit.frame(() -> { clear(0x247C45); visit.host.draw(visit.projection, visit.viewport); return true; }));
            assertEquals(0, visit.host.renderedSubmissions());

            visit.activate(false, false);

            assertEquals(2, visit.swaps);
            assertTrue(Arrays.stream(visit.front.pixels()).allMatch(pixel -> (pixel & 0xFFFFFF) == 0x247C45));
        }
    }

    @Test void privacyPresentationRestoresFramebufferMasksAndRendererState() {
        try (var visit = new Visit()) {
            visit.reveal(true);
            var draw = FboHelper.createColorOnly(8, 8, GL_CLAMP_TO_EDGE);
            var read = FboHelper.createColorOnly(8, 8, GL_CLAMP_TO_EDGE);
            assertNotNull(draw);
            assertNotNull(read);
            int texture = glGenTextures();
            try {
                glDrawBuffer(GL_NONE);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw.fboId());
                glBindFramebuffer(GL_READ_FRAMEBUFFER, read.fboId());
                glViewport(3, 5, 7, 11);
                glScissor(1, 2, 3, 4);
                glEnable(GL_SCISSOR_TEST);
                glColorMask(false, true, false, false);
                glClearColor(0.25f, 0.5f, 0.75f, 0.125f);
                glActiveTexture(GL_TEXTURE3);
                glBindTexture(GL_TEXTURE_2D, texture);
                long submissions = visit.host.renderedSubmissions();
                int program = glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
                int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);

                visit.activate(false, false);

                assertNeutral(visit.front);
                assertEquals(draw.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(read.fboId(), glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
                assertEquals(GL_TEXTURE3, glGetInteger(GL_ACTIVE_TEXTURE));
                assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D));
                assertEquals(program, glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM));
                assertEquals(vao, glGetInteger(GL_VERTEX_ARRAY_BINDING));
                assertEquals(submissions, visit.host.renderedSubmissions());
                int[] box = new int[4];
                glGetIntegerv(GL_VIEWPORT, box);
                assertArrayEquals(new int[]{3, 5, 7, 11}, box);
                glGetIntegerv(GL_SCISSOR_BOX, box);
                assertArrayEquals(new int[]{1, 2, 3, 4}, box);
                assertTrue(glIsEnabled(GL_SCISSOR_TEST));
                var mask = org.lwjgl.BufferUtils.createByteBuffer(4);
                glGetBooleanv(GL_COLOR_WRITEMASK, mask);
                assertArrayEquals(new byte[]{0, 1, 0, 0}, new byte[]{mask.get(0), mask.get(1), mask.get(2), mask.get(3)});
                float[] color = new float[4];
                glGetFloatv(GL_COLOR_CLEAR_VALUE, color);
                assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 0.125f}, color);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
                assertEquals(GL_NONE, glGetInteger(GL_DRAW_BUFFER));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glDrawBuffer(glGetBoolean(GL_DOUBLEBUFFER) ? GL_BACK : GL_FRONT);
                glColorMask(true, true, true, true);
                glDisable(GL_SCISSOR_TEST);
                glActiveTexture(GL_TEXTURE0);
                glDeleteTextures(texture);
                FboHelper.destroy(draw);
                FboHelper.destroy(read);
            }
        }
    }

    @Test void failedPrivacySwapRestoresItsOwnerAndCanRetryTheWithdrawal() {
        try (var visit = new Visit()) {
            visit.reveal(false);
            glColorMask(false, true, false, true);
            glEnable(GL_SCISSOR_TEST);
            var failure = new IllegalStateException("owned presentation failed");

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> visit.presentation.withdraw(() -> { throw failure; })));

            assertTrue(glIsEnabled(GL_SCISSOR_TEST));
            var mask = org.lwjgl.BufferUtils.createByteBuffer(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            assertArrayEquals(new byte[]{0, 1, 0, 1}, new byte[]{mask.get(0), mask.get(1), mask.get(2), mask.get(3)});
            visit.presentation.withdraw(visit::swap);
            assertNeutral(visit.front);
            assertEquals(2, visit.swaps);
            glColorMask(true, true, true, true);
            glDisable(GL_SCISSOR_TEST);
        }
    }

    private static void clear(int rgb) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void assertNeutral(RgbaImage frame) {
        assertNotNull(frame);
        assertTrue(Arrays.stream(frame.pixels()).allMatch(pixel -> pixel == 0xFF000000),
                "Focus loss must replace already-presented invite pixels before paused updates resume");
    }

    private static boolean codePixelsDiffer(RgbaImage first, RgbaImage second) {
        for (int y = 41; y < 70; y++) {
            for (int x = 24; x < 200; x++) {
                if (first.argb(x, y) != second.argb(x, y)) return true;
            }
        }
        return false;
    }
}
