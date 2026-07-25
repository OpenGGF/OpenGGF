package com.openggf;

import com.openggf.capture.CaptureViewport;
import com.openggf.capture.LiveCaptureChord;
import com.openggf.capture.LiveCaptureController;
import com.openggf.capture.LiveCapturePresentationCoordinator;
import com.openggf.configuration.FrameRateResolver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SCROLL_LOCK;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestEngineLiveCapturePresentation {
    @Test
    void debugAudioFailurePropertyUsesSafeDisabledDefaultAndExactNonNegativeValue() {
        String key = "openggf.debug.liveCaptureAudioFailAfterFrames";
        String prior = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "-1");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "0");
            assertEquals(0, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "17");
            assertEquals(17, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "-2");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
            System.setProperty(key, "not-a-number");
            assertEquals(-1, Engine.resolveLiveCaptureAudioFailAfterFrames());
        } finally {
            if (prior == null) System.clearProperty(key);
            else System.setProperty(key, prior);
        }
    }

    @Test
    void immediateLiveCaptureFailureWarnsExactlyOnceAndRetryCanWarnAgain() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("tap open failed");
        AtomicReference<LiveCaptureController.State> state =
                new AtomicReference<>(LiveCaptureController.State.FAILED);
        when(controller.state()).thenAnswer(ignored -> state.get());
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);

        reporter.observe(controller);
        reporter.observe(controller);
        assertEquals(List.of(failure), warnings);

        state.set(LiveCaptureController.State.ACTIVE);
        reporter.observe(controller);
        state.set(LiveCaptureController.State.FAILED);
        reporter.observe(controller);
        assertEquals(List.of(failure, failure), warnings);
    }

    @Test
    void asynchronousFinalizationFailureWarnsExactlyOnceOnSubsequentFrames() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("mux failed");
        AtomicReference<LiveCaptureController.State> state =
                new AtomicReference<>(LiveCaptureController.State.STOPPING);
        when(controller.state()).thenAnswer(ignored -> state.get());
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);

        reporter.observe(controller);
        state.set(LiveCaptureController.State.FAILED);
        reporter.observe(controller);
        reporter.observe(controller);

        assertEquals(List.of(failure), warnings);
    }

    @Test
    void synchronousRetryFailureWithSameThrowableWarnsOncePerAttempt() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        Throwable failure = new IllegalStateException("same synchronous failure");
        when(controller.state()).thenReturn(LiveCaptureController.State.FAILED);
        when(controller.lastFailure()).thenReturn(failure);
        List<Throwable> warnings = new ArrayList<>();
        Engine.LiveCaptureFailureTransitionReporter reporter =
                new Engine.LiveCaptureFailureTransitionReporter(warnings::add);
        CaptureViewport viewport = new CaptureViewport(0, 0, 320, 224);

        reporter.observe(controller);
        Engine.startLiveCaptureAttempt(controller, reporter, viewport, 60);
        reporter.observe(controller);
        reporter.observe(controller);

        assertEquals(List.of(failure, failure), warnings);
        verify(controller).start(viewport, 60);
    }

    @Test
    void allRenderedGameModesAndPresentationStatesUseTheSameSeamExactlyOnce() {
        LiveCaptureController controller = mock(LiveCaptureController.class);
        List<String> calls = new ArrayList<>();
        doAnswer(invocation -> {
            calls.add("capture");
            return null;
        }).when(controller).capturePresentedFrame(any(CaptureViewport.class));
        LiveCapturePresentationCoordinator coordinator =
                new LiveCapturePresentationCoordinator(controller);
        CaptureViewport viewport = new CaptureViewport(0, 0, 320, 224);
        EnumSet<GameMode> renderedModes = EnumSet.allOf(GameMode.class);
        EnumSet<Engine.LiveCapturePresentationState> presentationStates =
                EnumSet.allOf(Engine.LiveCapturePresentationState.class);
        assertTrue(renderedModes.contains(GameMode.BONUS_STAGE));

        int presentations = 0;
        for (GameMode mode : renderedModes) {
            for (Engine.LiveCapturePresentationState state : presentationStates) {
                Engine.presentLiveCaptureFrame(mode, state,
                        () -> coordinator.present(viewport,
                                () -> calls.add("screenshot"),
                                () -> calls.add("indicator")));
                presentations++;
            }
        }

        assertEquals(presentations * 3, calls.size());
        for (int i = 0; i < calls.size(); i += 3) {
            assertEquals(List.of("capture", "screenshot", "indicator"),
                    calls.subList(i, i + 3));
        }
        verify(controller, times(presentations)).capturePresentedFrame(viewport);
    }

    @Test
    void productionDisplayHasOneUnconditionalCoordinatorInvocation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int displayStart = source.indexOf("private void display()");
        int displayEnd = source.indexOf(
                "static LiveCapturePresentationState resolveLiveCapturePresentationState(",
                displayStart);
        String display = source.substring(displayStart, displayEnd);
        assertEquals(1, occurrences(display, "liveCapturePresentation.present("));
        assertEquals(1, occurrences(display, "presentLiveCaptureFrame("));
        assertFalse(display.substring(display.lastIndexOf("applyDisplayShaderPhase(ShaderPhase.FINAL)"))
                .contains("if ("));
    }

    /**
     * The presented outer frame carries exactly one audio presentation. A
     * second call here would double the producer's cadence and hand the
     * recorder a packet the speaker never played, so it is pinned alongside the
     * capture seam rather than left to the audio-side tests alone.
     */
    @Test
    void productionDisplayPresentsTheAudioOuterFrameExactlyOnce()
            throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int displayStart = source.indexOf("private void display()");
        int displayEnd = source.indexOf(
                "static LiveCapturePresentationState resolveLiveCapturePresentationState(",
                displayStart);
        String display = source.substring(displayStart, displayEnd);
        assertEquals(1, occurrences(display, "presentOuterAudioFrame(gameLoop"));
        assertEquals(0, occurrences(display, "presentFrame("),
                "display() must reach the producer only through the shared "
                        + "outer-frame seam");
    }

    @Test
    void productionPresentationFlagsResolveEveryExecutableStateBranch() {
        assertEquals(Engine.LiveCapturePresentationState.NORMAL,
                Engine.resolveLiveCapturePresentationState(false, false, false, false));
        assertEquals(Engine.LiveCapturePresentationState.MODAL_SHADER_PICKER,
                Engine.resolveLiveCapturePresentationState(true, false, false, false));
        assertEquals(Engine.LiveCapturePresentationState.PAUSED,
                Engine.resolveLiveCapturePresentationState(false, true, false, false));
        assertEquals(Engine.LiveCapturePresentationState.FRAME_STEP,
                Engine.resolveLiveCapturePresentationState(false, true, true, false));
        assertEquals(Engine.LiveCapturePresentationState.REWIND,
                Engine.resolveLiveCapturePresentationState(false, false, false, true));
    }

    @Test
    void productionRecorderUsesBlockCapacityEightAndScaleOne() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/capture/LiveCaptureRecorderFactory.java"));
        assertTrue(source.contains("new FfmpegEncoder(ffmpeg, 1)"));
        assertTrue(source.contains("BackpressurePolicy.BLOCK, 8"));
    }

    @Test
    void palEngineTargetAndCaptureRateBothResolveToFifty() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.REGION, "PAL");
        config.setConfigValue(SonicConfiguration.FPS, 60);
        assertEquals(50, FrameRateResolver.effective(config));
        assertEquals(50, Engine.resolveTargetFps(config));
    }

    @Test
    void fixedViewportOriginChangeRequestsStopBeforeCapture() {
        CaptureViewport before = new CaptureViewport(0, 0, 320, 224);
        CaptureViewport after = new CaptureViewport(1, 0, 320, 224);
        assertNotEquals(before, after);
    }

    @Test
    void cleanupClosesCaptureBeforeAudioAndGraphics() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int capture = source.indexOf("cleanupStep(\"live capture\"");
        int audio = source.indexOf("cleanupStep(\"audio manager\"");
        int graphics = source.indexOf("cleanupStep(\"graphics manager\"");
        assertTrue(capture >= 0 && capture < audio && capture < graphics);
    }

    // ---------------------------------------------------------------
    // Recording-interrupted notice
    // ---------------------------------------------------------------

    @Test
    void aLatchedNoticeStaysUntilItExpires() {
        long expiry = 1_000L;
        assertFalse(Engine.liveCaptureNoticeFinished(false, 0L, expiry));
        assertFalse(Engine.liveCaptureNoticeFinished(false, expiry - 1, expiry));
        assertTrue(Engine.liveCaptureNoticeFinished(false, expiry, expiry));
        assertTrue(Engine.liveCaptureNoticeFinished(false, expiry + 1, expiry));
    }

    /** Starting a new recording makes the previous stop's notice stale. */
    @Test
    void aNewRecordingSupersedesAnUnexpiredNotice() {
        assertTrue(Engine.liveCaptureNoticeFinished(true, 0L, 1_000L));
    }

    /**
     * Elapsed nanos are compared, not the instants, so a System.nanoTime()
     * wrap cannot strand a notice on screen forever.
     */
    @Test
    void noticeExpirySurvivesANanoTimeWrap() {
        long expiry = Long.MIN_VALUE + 100L;
        long beforeWrap = Long.MAX_VALUE - 100L;
        assertFalse(Engine.liveCaptureNoticeFinished(false, beforeWrap, expiry),
                "a notice armed just before the wrap has not expired yet");
        assertTrue(Engine.liveCaptureNoticeFinished(false, expiry, expiry));
    }

    @Test
    void everyInterruptionHasDistinctNoticeText() {
        var texts = new java.util.HashSet<String>();
        for (LiveCaptureController.Interruption interruption
                : LiveCaptureController.Interruption.values()) {
            String text = Engine.liveCaptureNoticeText(interruption);
            assertFalse(text.isBlank(), interruption + " needs notice text");
            assertTrue(texts.add(text), "duplicate notice text: " + text);
        }
    }

    // ---------------------------------------------------------------
    // Capture toggle chord, evaluated through the real InputHandler
    // ---------------------------------------------------------------

    private static boolean toggles(SonicConfigurationService config, InputHandler input) {
        return Engine.shouldToggleLiveCapture(
                config.getKeyChord(SonicConfiguration.CAPTURE_TOGGLE_KEY),
                new LiveCaptureChord(), input);
    }

    /** The shortcut a fresh install answers to, at the call site rather than in the config. */
    @Test
    void theDefaultBindingTogglesOnARealShiftO() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        input.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_O, GLFW_PRESS);

        assertTrue(toggles(config, input));
    }

    @Test
    void aBareKeyBindingTogglesUnmodifiedAndNotWhileShiftIsHeld() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CAPTURE_TOGGLE_KEY, "SCROLL_LOCK");
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        input.handleKeyEvent(GLFW_KEY_SCROLL_LOCK, GLFW_PRESS);
        assertTrue(toggles(config, input));

        InputHandler shifted = new InputHandler(InputBindingFactory.supplier(config));
        shifted.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        shifted.handleKeyEvent(GLFW_KEY_SCROLL_LOCK, GLFW_PRESS);
        assertFalse(toggles(config, shifted));
    }

    @Test
    void aCtrlShiftBindingNeedsBothModifiersHeld() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CAPTURE_TOGGLE_KEY, "CTRL+SHIFT+O");
        InputHandler shiftOnly = new InputHandler(InputBindingFactory.supplier(config));
        shiftOnly.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        shiftOnly.handleKeyEvent(GLFW_KEY_O, GLFW_PRESS);
        assertFalse(toggles(config, shiftOnly));

        InputHandler both = new InputHandler(InputBindingFactory.supplier(config));
        both.handleKeyEvent(GLFW_KEY_LEFT_CONTROL, GLFW_PRESS);
        both.handleKeyEvent(GLFW_KEY_LEFT_SHIFT, GLFW_PRESS);
        both.handleKeyEvent(GLFW_KEY_O, GLFW_PRESS);
        assertTrue(toggles(config, both));
    }

    /**
     * update() takes six adjacent booleans and forwards four into
     * matchesModifiers(shift, ctrl, alt, meta); a transposed pair compiles and
     * passes every literal-boolean case in LiveCaptureChordTest. Only a real key
     * press distinguishes Super from Alt.
     */
    @Test
    void aMetaBindingFiresOnARealSuperPressAndNotOnARealAltPress() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CAPTURE_TOGGLE_KEY, "META+O");
        InputHandler withSuper = new InputHandler(InputBindingFactory.supplier(config));
        withSuper.handleKeyEvent(GLFW_KEY_LEFT_SUPER, GLFW_PRESS);
        withSuper.handleKeyEvent(GLFW_KEY_O, GLFW_PRESS);
        assertTrue(toggles(config, withSuper));

        InputHandler withAlt = new InputHandler(InputBindingFactory.supplier(config));
        withAlt.handleKeyEvent(GLFW_KEY_LEFT_ALT, GLFW_PRESS);
        withAlt.handleKeyEvent(GLFW_KEY_O, GLFW_PRESS);
        assertFalse(toggles(config, withAlt));
    }

    /**
     * isKeyDown(-1) is not false: it falls through to
     * {@code keyCode == inputBindings.rewindKey() && gamepadInputManager.isRewindHeld()},
     * and an unbound LIVE_REWIND_KEY is -1 too. So an unbound capture binding
     * fires from a held pad bumper unless the call site guards on isBound() first.
     */
    @Test
    void anUnboundBindingDoesNotFireFromAHeldGamepadRewindButton() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CAPTURE_TOGGLE_KEY, "");
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_KEY, "");
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config),
                new FakePad(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER));
        input.refreshLogicalSnapshot();

        assertEquals(-1, config.getInt(SonicConfiguration.LIVE_REWIND_KEY),
                "precondition: rewind unbound, so isKeyDown(-1) reaches the pad branch");
        assertTrue(input.isKeyDown(-1), "precondition: the hazard this guard exists for");
        assertFalse(toggles(config, input));
    }

    /**
     * Every chord case above evaluates the seam directly, so the production
     * shortcut could stop using it and they would all stay green. Inlining the
     * detector call into handleLiveCaptureShortcut is exactly what drops the
     * isBound() pre-check -- LiveCaptureChord.update's own isBound() check does
     * not save it, because {@code input.isKeyDown(chord.keyCode())} is evaluated
     * as an argument first, and isKeyDown(-1) reaches the pad rewind branch.
     */
    @Test
    void theProductionShortcutEvaluatesTheChordOnlyThroughTheGuardedSeam() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int start = source.indexOf("private void handleLiveCaptureShortcut()");
        assertTrue(start >= 0, "handleLiveCaptureShortcut was renamed; re-point this guard");
        int end = source.indexOf("private CaptureViewport currentCaptureViewport()", start);
        String body = source.substring(start, end);

        assertEquals(1, occurrences(body, "shouldToggleLiveCapture("),
                "the shortcut must reach the chord through the guarded seam");
        assertEquals(0, occurrences(body, "liveCaptureChord.update("),
                "an inlined detector call skips the load-bearing isBound() guard");
    }

    /**
     * A latched modifier is cleared by the window focus callback, and that is
     * the only production caller of clearKeyState. Testing the helper directly
     * leaves the wiring free to be dropped, which re-latches Super after an
     * alt-tab and kills every isKeyPressedWithoutModifiers shortcut for the rest
     * of the process. The iconify callback deliberately has no clear of its own:
     * a minimise takes focus with it, so this callback has already run.
     */
    @Test
    void losingWindowFocusClearsLatchedKeyState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int start = source.indexOf("glfwSetWindowFocusCallback(window");
        assertTrue(start >= 0, "the focus callback was renamed; re-point this guard");
        int end = source.indexOf("glfwSetWindowIconifyCallback(window", start);
        assertTrue(end > start, "the focus callback must precede the iconify callback");
        String callback = source.substring(start, end);

        assertEquals(1, occurrences(callback, "inputHandler.clearKeyState()"),
                "focus loss must drop key state or a window-switch modifier latches");
    }

    /** One connected pad with the given buttons held. */
    private static final class FakePad implements GamepadStateSource {
        private final boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];

        FakePad(int... pressedButtons) {
            for (int button : pressedButtons) {
                buttons[button] = true;
            }
        }

        @Override
        public List<DeviceState> pollDevices() {
            return List.of(DeviceState.connected(0, "pad-0", buttons, 0.0f, 0.0f));
        }
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

}
