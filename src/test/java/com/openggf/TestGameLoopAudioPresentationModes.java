package com.openggf;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.AudioStream;
import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.runtime.AudioOutputFifo;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.runtime.StreamBackedDeterministicAudioRuntime;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new SixHertzBackend());
        StreamBackedDeterministicAudioRuntime runtime =
                new StreamBackedDeterministicAudioRuntime(
                        new AudioFrameClock(6, 1),
                        new AudioOutputFifo(12));
        runtime.setMusicStream(new SequenceStream(
                1, 11, 2, 12, 3, 13, 4, 14, 5, 15, 6, 16));
        installRuntime(runtime);
        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(1);

        loop.step();
        loop.presentOuterFrame(false, false);

        verify(input, times(9)).refreshLogicalSnapshot();
        short[] actual = new short[12];
        assertEquals(6, capture.drainPresentationFrame(actual));
        assertArrayEquals(
                new short[]{1, 11, 2, 12, 3, 13, 4, 14, 5, 15, 6, 16},
                actual);
        assertEquals(6, capture.totalStereoFrames());
        assertEquals(6, capture.clockSnapshot().totalSamplesProduced());
        assertEquals(6, runtime.lastProducedFrames());
        assertEquals(1, AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames());
        capture.close();
    }

    @Test
    void heldRewindDrivesReverseThroughRealBoundary() throws Exception {
        CountingRuntime runtime = installCountingRuntime();
        AudioManager.getInstance().beginReverseAudioPresentation();

        loop.presentOuterFrame(false, false);

        assertEquals(0, runtime.advances,
                "reverse consumes history rather than advancing synthesis");
        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(
                AudioManager.getInstance()).reverseFrames());
    }

    private CountingRuntime installCountingRuntime() throws Exception {
        CountingRuntime runtime = new CountingRuntime();
        installRuntime(runtime);
        return runtime;
    }

    private static void installRuntime(DeterministicAudioRuntime runtime)
            throws Exception {
        Method setter = AudioManager.class.getDeclaredMethod(
                "setDeterministicAudioRuntime",
                DeterministicAudioRuntime.class);
        setter.setAccessible(true);
        setter.invoke(AudioManager.getInstance(), runtime);
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

    private static final class SixHertzBackend extends NullAudioBackend {
        @Override
        public int outputSampleRate() {
            return 6;
        }
    }

    private static final class SequenceStream implements AudioStream {
        private final short[] samples;
        private int cursor;

        private SequenceStream(int... samples) {
            this.samples = new short[samples.length];
            for (int i = 0; i < samples.length; i++) {
                this.samples[i] = (short) samples[i];
            }
        }

        @Override
        public int read(short[] buffer) {
            int count = Math.min(buffer.length, samples.length - cursor);
            System.arraycopy(samples, cursor, buffer, 0, count);
            cursor += count;
            return count;
        }

        @Override
        public boolean isComplete() {
            return cursor >= samples.length;
        }
    }
}
