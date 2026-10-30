package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.GamepadStateSource;
import com.openggf.game.GameMode;
import com.openggf.game.LegalDisclaimerScreen;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.graphics.shaderlib.DisplayShaderPickerController;
import com.openggf.graphics.shaderlib.DisplayShaderPresetRef;
import com.openggf.graphics.shaderlib.DisplayShaderSelectionModel;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Drives Engine's actual live advance phase, with real input, fade and audio owners. */
@Isolated
class TestEngineFrameAdvance {
    private Engine engine;
    private InputHandler input;
    private SonicConfigurationService config;
    private AudioManager audio;
    private FadeManager fade;
    private UiRenderPipeline pipeline;
    private ObservedDisclaimer screen;
    private final Pads pads = new Pads();
    private final List<PresentationMode> audioModes = new ArrayList<>();

    @BeforeEach
    void open() throws Exception {
        TestEnvironment.resetEngineOnly();
        EngineContext original = EngineServices.current();
        config = original.configuration();
        config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        audio = original.audio();
        audio.setBackend(new NullAudioBackend());
        audio.resetState();
        // Only the GL resource owner is substituted. No window, GL context, ROM or device.
        GraphicsManager graphics = mock(GraphicsManager.class);
        fade = new FadeManager();
        pipeline = new UiRenderPipeline(graphics);
        pipeline.setFadeManager(fade);
        when(graphics.getUiRenderPipeline()).thenReturn(pipeline);
        when(graphics.getFadeManager()).thenReturn(fade);
        engine = new Engine(new EngineContext(config, graphics, audio, original.roms(),
                original.profiler(), original.debugOverlay(), original.playbackDebug(),
                original.romDetection(), original.crossGameFeatures()));
        input = new InputHandler(InputBindingFactory.supplier(config), pads);
        engine.setInputHandler(input);
        engine.getGameLoop().setGameMode(GameMode.LEGAL_DISCLAIMER);
        engine.getGameLoop().setAudioPresentationProbe(mode -> {
            assertFalse(input.isPhysicalKeyPressed(config.getInt(SonicConfiguration.P1_A)),
                    "the existing input owner must finish before outer audio");
            audioModes.add(mode);
        });
        screen = new ObservedDisclaimer();
        replace("legalDisclaimerScreen", screen);
    }

    @AfterEach
    void close() {
        if (engine != null) engine.getGameLoop().closePresence();
        Engine.clearGlobalInstance();
        TestEnvironment.resetEngineOnly();
    }

    @Test
    void freshPadStepDecisionReachesCaptureInTheSameFrameAndDoesNotRepeatWhileHeld() {
        engine.getGameLoop().toggleUserPause();
        pads.step = true;

        var stepped = engine.advanceFrame();
        var held = engine.advanceFrame();

        assertTrue(stepped.frameStepRequested(), "capture must see the pad step that GameLoop sees now");
        assertFalse(held.frameStepRequested(), "an acknowledged pad step cannot repeat on the next picture");
        assertEquals(2, pads.polls, "each ordinary frame transfers one sample to GameLoop");
        assertEquals(List.of(PresentationMode.SILENT, PresentationMode.SILENT), audioModes);
    }

    @Test
    void pickerAcknowledgesTheFreshPadSampleSoItsActionCannotLeakToTheNextModePass() throws Exception {
        installPicker(GLFW_KEY_F6);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        pads.b = true;

        assertTrue(engine.advanceFrame().displayInputConsumed());
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_RELEASE);
        engine.advanceFrame();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        assertTrue(engine.advanceFrame().displayInputConsumed());
        engine.advanceFrame();

        assertEquals(4, pads.polls);
        assertEquals(1, screen.samples.size());
        assertEquals(InputActionMasks.ACTION_B, screen.samples.getFirst().player1().actionHeldMask());
        assertEquals(0, screen.samples.getFirst().player1().actionPressedMask(),
                "the modal consumed the earlier physical press, even without a GameLoop pass");
    }

    @Test
    void hostHandoffFeedsOnlyTheFirstFastForwardPassAndLaterPassesObserveTheirOwnPadState() throws Exception {
        var controls = mock(UserRecordingRuntimeControls.class);
        when(controls.shouldPumpFastForward()).thenReturn(true);
        Field field = GameLoop.class.getDeclaredField("userRecordingControls");
        field.setAccessible(true);
        field.set(engine.getGameLoop(), controls);
        pads.b = true;
        screen.afterUpdate = () -> pads.b = false;

        engine.advanceFrame();

        assertEquals(9, screen.samples.size());
        assertEquals(9, pads.polls, "host transfers once, rather than adding or coalescing pump samples");
        assertEquals(InputActionMasks.ACTION_B, screen.samples.getFirst().player1().actionPressedMask());
        for (var sample : screen.samples.subList(1, 9)) {
            assertEquals(0, sample.player1().actionHeldMask());
            assertEquals(0, sample.player1().actionPressedMask());
        }
        assertEquals(List.of(PresentationMode.FORWARD), audioModes);
    }

    @Test
    void ordinaryFramesAdvanceFadeInputAndAudioOnceWithoutDrawing() {
        fade.startFadeFromBlack(null);
        int action = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(action, GLFW_PRESS);

        var first = engine.advanceFrame();
        var second = engine.advanceFrame();

        assertSame(pipeline, first.uiPipeline());
        assertFalse(first.displayInputConsumed());
        assertFalse(first.frameStepRequested());
        assertFalse(second.frameStepRequested());
        assertEquals(List.of(1, 2), screen.fadeFrames,
                "detached UI fades advance before the mode's input pass");
        assertEquals(2, screen.samples.size());
        assertEquals(InputActionMasks.ACTION_A, screen.samples.getFirst().player1().actionPressedMask());
        assertEquals(0, screen.samples.getLast().player1().actionPressedMask());
        assertEquals(InputActionMasks.ACTION_A, screen.samples.getLast().player1().actionHeldMask());
        assertEquals(List.of(PresentationMode.FORWARD, PresentationMode.FORWARD), audioModes);
        var parity = AudioManagerTestDiagnostics.shadowParitySnapshot(audio);
        assertEquals(2, parity.presentedFrames());
        assertEquals(2, parity.forwardFrames());
    }

    @Test
    void pickerOpenAndCloseOwnWholeFramesWithoutReplayingAcknowledgedKeys() throws Exception {
        var picker = installPicker(GLFW_KEY_F6);
        int action = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        input.handleKeyEvent(action, GLFW_PRESS);

        var opened = engine.advanceFrame();
        assertTrue(picker.isOpen());
        assertTrue(opened.displayInputConsumed());
        assertTrue(screen.samples.isEmpty(), "opening picker must suppress the mode update");
        assertFalse(input.isPhysicalKeyPressed(action));

        input.handleKeyEvent(GLFW_KEY_F6, GLFW_RELEASE);
        engine.advanceFrame();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        var closed = engine.advanceFrame();
        assertFalse(picker.isOpen());
        assertTrue(closed.displayInputConsumed(), "closing frame still belongs to the picker");
        assertTrue(screen.samples.isEmpty());

        engine.advanceFrame();
        assertEquals(1, screen.samples.size());
        assertEquals(0, screen.samples.getFirst().player1().actionPressedMask());
        assertEquals(InputActionMasks.ACTION_A, screen.samples.getFirst().player1().actionHeldMask());
        assertEquals(List.of(PresentationMode.SILENT, PresentationMode.SILENT,
                PresentationMode.SILENT, PresentationMode.FORWARD), audioModes);
        assertEquals(4, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
    }

    @Test
    void textClaimKeepsPrintablePickerShortcutWithItsExistingOwner() throws Exception {
        var picker = installPicker(GLFW_KEY_V);
        input.captureKeyboardText();
        input.handleKeyEvent(GLFW_KEY_V, GLFW_PRESS);

        var frame = engine.advanceFrame();

        assertFalse(frame.displayInputConsumed());
        assertFalse(picker.isOpen());
        assertEquals(1, screen.samples.size());
        assertFalse(input.isPhysicalKeyPressed(GLFW_KEY_V));
        assertTrue(input.isCapturedKeyDown(GLFW_KEY_V));
        assertEquals(List.of(PresentationMode.FORWARD), audioModes);
    }

    @Test
    void frameStepDecisionSurvivesInputAcknowledgmentForCaptureAndSilentAudio() {
        engine.getGameLoop().toggleUserPause();
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);

        var stepped = engine.advanceFrame();
        assertFalse(input.isKeyPressed(GLFW_KEY_F8));
        var held = engine.advanceFrame();

        assertTrue(stepped.frameStepRequested());
        assertFalse(held.frameStepRequested(), "a held step key must not create another request");
        assertEquals(Engine.LiveCapturePresentationState.FRAME_STEP,
                Engine.resolveLiveCapturePresentationState(stepped.displayInputConsumed(),
                        engine.getGameLoop().isUserPaused(), stepped.frameStepRequested(), false));
        assertEquals(List.of(PresentationMode.SILENT, PresentationMode.SILENT), audioModes);
    }

    @Test
    void unpausedStepKeyDoesNotCreateAPausedStepPresentation() {
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
        var frame = engine.advanceFrame();

        assertFalse(frame.frameStepRequested());
        assertEquals(List.of(PresentationMode.FORWARD), audioModes);
    }

    @Test
    void recordingFastForwardKeepsNineOwnedUpdatesAndOneAudioPacket() throws Exception {
        var controls = mock(UserRecordingRuntimeControls.class);
        when(controls.shouldPumpFastForward()).thenReturn(true);
        Field field = GameLoop.class.getDeclaredField("userRecordingControls");
        field.setAccessible(true);
        field.set(engine.getGameLoop(), controls);
        config.setConfigValue(SonicConfiguration.FPS, 1);
        audio.setBackend(new NullAudioBackend() {
            @Override public int outputSampleRate() { return 6; }
        });
        int action = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(action, GLFW_PRESS);

        try (var capture = AudioManagerTestDiagnostics.attachPresentationCapture(audio, 1)) {
            engine.advanceFrame();

            assertEquals(9, screen.samples.size());
            assertEquals(InputActionMasks.ACTION_A, screen.samples.getFirst().player1().actionPressedMask());
            for (var sample : screen.samples.subList(1, 9)) {
                assertEquals(0, sample.player1().actionPressedMask());
                assertEquals(InputActionMasks.ACTION_A, sample.player1().actionHeldMask());
            }
            assertEquals(List.of(PresentationMode.FORWARD), audioModes);
            short[] packet = new short[12];
            assertEquals(6, capture.drainPresentationFrame(packet));
            assertArrayEquals(new short[12], packet);
            assertEquals(6, capture.totalStereoFrames());
            assertEquals(6, capture.clockSnapshot().totalSamplesProduced());
        }
    }

    @Test
    void reversePresentationKeepsItsExistingSingleOuterAudioBoundary() {
        audio.beginReverseAudioPresentation();

        engine.advanceFrame();

        assertEquals(List.of(PresentationMode.REVERSE), audioModes);
        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).reverseFrames());
    }

    @Test
    void pauseChangedDuringTheUpdateSelectsAudioAfterTheUpdate() {
        screen.afterUpdate = () -> engine.getGameLoop().toggleUserPause();

        var frame = engine.advanceFrame();

        assertFalse(frame.frameStepRequested());
        assertTrue(engine.getGameLoop().isUserPaused());
        assertEquals(List.of(PresentationMode.SILENT), audioModes);
    }

    @Test
    void fadeCompletionRunsBeforeTheSameFrameUpdate() {
        var order = new ArrayList<String>();
        fade.startFadeFromBlack(() -> order.add("fade"));
        for (int i = 0; i < 20; i++) fade.update();
        screen.afterUpdate = () -> order.add("update");

        engine.advanceFrame();

        assertEquals(List.of("fade", "update"), order);
        assertFalse(fade.isActive());
        assertEquals(List.of(PresentationMode.FORWARD), audioModes);
    }

    @Test
    void typedModAbortStopsBeforeAudioOrPresentationAndUsesExistingRecovery() {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> {});
        screen.afterUpdate = () -> boundary.run("owner", () -> {
            throw new IllegalStateException("callback");
        });
        var order = new ArrayList<String>();

        boolean completed = Engine.displayAndSwap(
                () -> Engine.runFrameWithModAbort(() -> {
                    engine.advanceFrame();
                    order.add("present");
                }, () -> order.add("discard"), () -> order.add("title")),
                () -> order.add("swap"));

        assertFalse(completed);
        assertEquals(List.of("discard", "title"), order);
        assertTrue(audioModes.isEmpty());
        assertEquals(0, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).presentedFrames());
    }

    @Test
    void releasedPhysicalTapsCrossTheActualHostHandoffInOrder() {
        int action = config.getInt(SonicConfiguration.P1_A);
        for (int i = 0; i < 2; i++) {
            input.handleKeyEvent(action, GLFW_PRESS);
            input.handleKeyEvent(action, GLFW_RELEASE);
        }
        engine.setInputHandler(input); // Reinstalling the same owner is not a discontinuity.
        for (int i = 0; i < 4; i++) engine.advanceFrame();
        assertEquals(List.of(InputActionMasks.ACTION_A, 0, InputActionMasks.ACTION_A, 0),
                screen.samples.stream().map(sample -> sample.player1().actionPressedMask()).toList());
        assertEquals(List.of(InputActionMasks.ACTION_A, 0, InputActionMasks.ACTION_A, 0),
                screen.samples.stream().map(sample -> sample.player1().actionHeldMask()).toList());
        assertEquals(4, pads.polls);
        assertEquals(4, audioModes.size(), "input admission inserts no extra outer audio frame");
    }

    @Test
    void repeatedTapsRemainOrderedAcrossTheExistingNinePassPump() throws Exception {
        var controls = mock(UserRecordingRuntimeControls.class);
        when(controls.shouldPumpFastForward()).thenReturn(true);
        Field field = GameLoop.class.getDeclaredField("userRecordingControls");
        field.setAccessible(true); field.set(engine.getGameLoop(), controls);
        int action = config.getInt(SonicConfiguration.P1_A);
        for (int i = 0; i < 2; i++) {
            input.handleKeyEvent(action, GLFW_PRESS); input.handleKeyEvent(action, GLFW_RELEASE);
        }
        engine.advanceFrame();
        assertEquals(9, screen.samples.size());
        assertEquals(List.of(InputActionMasks.ACTION_A, 0, InputActionMasks.ACTION_A, 0, 0, 0, 0, 0, 0),
                screen.samples.stream().map(sample -> sample.player1().actionPressedMask()).toList());
        assertEquals(List.of(PresentationMode.FORWARD), audioModes);
    }

    @Test
    void pickerRetiresAllShortTapsAndModeRetirementCannotLeakTheRemainingSequence() throws Exception {
        installPicker(GLFW_KEY_F6);
        int action = config.getInt(SonicConfiguration.P1_A);
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS);
        input.handleKeyEvent(action, GLFW_PRESS); input.handleKeyEvent(action, GLFW_RELEASE);
        assertTrue(engine.advanceFrame().displayInputConsumed());
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_RELEASE); engine.advanceFrame();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_PRESS); engine.advanceFrame();
        input.handleKeyEvent(GLFW_KEY_F6, GLFW_RELEASE); engine.advanceFrame();
        assertEquals(0, screen.samples.getLast().player1().actionPressedMask());
        input.handleKeyEvent(action, GLFW_PRESS); input.handleKeyEvent(action, GLFW_RELEASE);
        screen.afterUpdate = () -> {
            engine.getGameLoop().setGameMode(GameMode.MASTER_TITLE_SCREEN);
            engine.getGameLoop().setGameMode(GameMode.LEGAL_DISCLAIMER);
        };
        engine.advanceFrame();
        screen.afterUpdate = () -> {};
        engine.advanceFrame();
        assertEquals(0, screen.samples.getLast().player1().actionHeldMask());
        assertEquals(0, screen.samples.getLast().player1().actionPressedMask());
    }

    private DisplayShaderPickerController installPicker(int key) throws Exception {
        var picker = new DisplayShaderPickerController(
                new DisplayShaderSelectionModel(List.of(DisplayShaderPresetRef.OFF)), key);
        replace("displayShaderPickerController", picker);
        return picker;
    }

    private void replace(String name, Object value) throws Exception {
        Field field = Engine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private final class ObservedDisclaimer extends LegalDisclaimerScreen {
        private final List<LogicalInputSnapshot> samples = new ArrayList<>();
        private final List<Integer> fadeFrames = new ArrayList<>();
        private Runnable afterUpdate = () -> {};

        private ObservedDisclaimer() { super(fade); }

        @Override public void update(InputHandler input) {
            samples.add(input.logical());
            fadeFrames.add(fade.capture().frameCount());
            super.update(input);
            afterUpdate.run();
        }
    }

    private static final class Pads implements GamepadStateSource {
        boolean b, step;
        int polls;

        @Override public List<DeviceState> pollDevices() {
            polls++;
            var buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = b;
            buttons[GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER] = step;
            return List.of(DeviceState.connected(0, "pad", buttons, 0, 0));
        }
    }
}
