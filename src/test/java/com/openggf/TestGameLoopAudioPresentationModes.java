package com.openggf;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        var presented = new ArrayList<PresentationMode>();
        loop.setAudioPresentationProbe(presented::add);
        loop.setGameMode(mode);

        loop.presentOuterFrame(false, false);

        var parity = AudioManagerTestDiagnostics.shadowParitySnapshot(
                AudioManager.getInstance());
        assertEquals(1, parity.presentedFrames(), mode.name());
        assertEquals(1, parity.forwardFrames(), mode.name());
        assertEquals(java.util.List.of(PresentationMode.FORWARD), presented,
                mode.name());
    }

    @Test
    void modalPickerAndFrameStepDriveRealBoundaryAsSilence() throws Exception {
        loop.presentOuterFrame(true, false);
        loop.presentOuterFrame(false, true);

        var parity = AudioManagerTestDiagnostics.shadowParitySnapshot(
                AudioManager.getInstance());
        assertEquals(2, parity.presentedFrames());
        assertEquals(2, parity.silentFrames());
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
        AudioManager audio = AudioManager.getInstance();
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.FPS, 1);
        audio.setBackend(new SixHertzBackend());
        LiveCaptureAudioHandle capture =
                AudioManagerTestDiagnostics.attachPresentationCapture(audio, 1);

        loop.step();
        loop.presentOuterFrame(false, false);

        verify(input, times(9)).refreshLogicalSnapshot();
        short[] actual = new short[12];
        assertEquals(6, capture.drainPresentationFrame(actual));
        assertArrayEquals(new short[12], actual);
        assertEquals(6, capture.totalStereoFrames());
        assertEquals(6, capture.clockSnapshot().totalSamplesProduced());
        assertEquals(1, AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames());
        capture.close();
    }

    @Test
    void heldRewindDrivesReverseThroughRealBoundary() throws Exception {
        AudioManager.getInstance().beginReverseAudioPresentation();

        loop.presentOuterFrame(false, false);

        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(
                AudioManager.getInstance()).reverseFrames());
    }

    private static void replaceField(
            Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class SixHertzBackend extends NullAudioBackend {
        @Override
        public int outputSampleRate() {
            return 6;
        }
    }

}
