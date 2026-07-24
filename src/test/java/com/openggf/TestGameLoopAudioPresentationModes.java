package com.openggf;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.AudioManager;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.recording.UserRecordingRuntimeControls;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestGameLoopAudioPresentationModes {
    private GameLoop loop;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        loop = new GameLoop(mock(InputHandler.class));
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void normalOuterFrameIsForward() {
        assertEquals(PresentationMode.FORWARD,
                loop.presentationModeForOuterFrame(false, false));
    }

    @Test
    void modalAndFrameStepOuterFramesAreSilent() {
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(true, false));
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(false, true));
    }

    @Test
    void ordinaryPauseOuterFrameIsSilent() {
        loop.toggleUserPause();
        assertEquals(PresentationMode.SILENT,
                loop.presentationModeForOuterFrame(false, false));
    }

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void everyProductionModeEntersTheSharedOuterFrameBoundary(GameMode mode)
            throws Exception {
        CountingRuntime runtime = installCountingRuntime();
        loop.setGameMode(mode);

        loop.presentOuterFrame(false, false);

        assertEquals(1, runtime.advances, mode.name());
        assertEquals(FrameAudioMode.NORMAL, runtime.lastMode, mode.name());
    }

    @Test
    void modalPickerAndFrameStepDriveRealBoundaryAsSilence() throws Exception {
        CountingRuntime runtime = installCountingRuntime();

        loop.presentOuterFrame(true, false);
        loop.presentOuterFrame(false, true);

        assertEquals(2, runtime.advances);
        assertEquals(FrameAudioMode.SILENT_STEP, runtime.lastMode);
    }

    @Test
    void realNineStepFastForwardHasOneOuterPresentationPacket()
            throws Exception {
        InputHandler input = mock(InputHandler.class);
        loop.setInputHandler(input);
        loop.setGameMode(GameMode.LEGAL_DISCLAIMER);
        UserRecordingRuntimeControls controls =
                mock(UserRecordingRuntimeControls.class);
        when(controls.shouldPumpFastForward()).thenReturn(true);
        replaceField(loop, "userRecordingControls", controls);
        CountingRuntime runtime = installCountingRuntime();

        loop.step();
        loop.presentOuterFrame(false, false);

        verify(input, times(9)).refreshLogicalSnapshot();
        assertEquals(1, runtime.advances);
        assertEquals(1, AudioManager.getInstance()
                .shadowParitySnapshotForTesting().presentedFrames());
    }

    @Test
    void heldRewindDrivesReverseThroughRealBoundary() throws Exception {
        CountingRuntime runtime = installCountingRuntime();
        AudioManager.getInstance().beginReverseAudioPresentation();

        loop.presentOuterFrame(false, false);

        assertEquals(0, runtime.advances,
                "reverse consumes history rather than advancing synthesis");
        assertEquals(1, AudioManager.getInstance()
                .shadowParitySnapshotForTesting().reverseFrames());
    }

    private CountingRuntime installCountingRuntime() throws Exception {
        CountingRuntime runtime = new CountingRuntime();
        Method setter = AudioManager.class.getDeclaredMethod(
                "setDeterministicAudioRuntime",
                DeterministicAudioRuntime.class);
        setter.setAccessible(true);
        setter.invoke(AudioManager.getInstance(), runtime);
        return runtime;
    }

    private static void replaceField(
            Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CountingRuntime
            implements DeterministicAudioRuntime {
        int advances;
        FrameAudioMode lastMode;

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            advances++;
            lastMode = mode;
        }

        @Override
        public boolean providesPresentationPcm() {
            return true;
        }
    }
}
