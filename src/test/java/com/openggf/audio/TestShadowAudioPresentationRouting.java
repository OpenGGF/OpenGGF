package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.smps.SmpsSequencer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

class TestShadowAudioPresentationRouting {
    private final AudioManager audio = AudioManager.getInstance();

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void sixtyPresentedFramesTickShadowExactlySixtyTimes() {
        audio.setBackend(new NullAudioBackend());
        for (int frame = 0; frame < 60; frame++) {
            audio.presentShadowFrame(PresentationMode.FORWARD);
        }
        var snapshot = audio.shadowParitySnapshotForTesting();
        assertEquals(60, snapshot.presentedFrames());
        assertEquals(60, snapshot.forwardFrames());
        assertEquals(0, snapshot.silentFrames());
        assertEquals(0, snapshot.reverseFrames());
    }

    @Test
    void everyLegacyControlHasOneSameOrderShadowCommand() {
        audio.setBackend(new NullAudioBackend());
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(2);
        audio.changeMusicTempo(3);
        audio.stopAllSfx();
        audio.stopMusic();
        audio.restoreMusic();
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertEquals(6,
                audio.shadowParitySnapshotForTesting().commandCount());
    }

    @Test
    void legacyBackendRemainsAudibleOwnerAcrossShadowTicks() {
        NullAudioBackend backend = new NullAudioBackend();
        audio.setBackend(backend);
        audio.presentShadowFrame(PresentationMode.FORWARD);
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertSame(backend, audio.getBackend());
    }

    @Test
    void muteAndSoloQueriesUseShadowState() {
        audio.setBackend(new NullAudioBackend());
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.presentShadowFrame(PresentationMode.SILENT);
        assertEquals(true, audio.isMuted(ChannelType.FM, 2));
        assertEquals(true, audio.isSoloed(ChannelType.PSG, 1));
    }

    @Test
    void nineSimulationDevicePumpsProduceOneOuterPresentationPacket() {
        CountingRuntime runtime = new CountingRuntime();
        audio.setBackend(new NullAudioBackend());
        audio.setDeterministicAudioRuntime(runtime);

        for (int simulationStep = 0; simulationStep < 9; simulationStep++) {
            audio.updateLegacyDevice();
        }
        assertEquals(0, runtime.advances);

        audio.presentOuterFrame(PresentationMode.FORWARD);
        assertEquals(1, runtime.advances);
        assertEquals(FrameAudioMode.NORMAL, runtime.lastMode);
        assertEquals(1,
                audio.shadowParitySnapshotForTesting().presentedFrames());
    }

    @ParameterizedTest
    @MethodSource("presentationTunings")
    void shadowUsesTheLegacyBackendPresentationTuning(
            AudioPresentationTuning tuning) {
        audio.setBackend(new TuningBackend(tuning));

        assertEquals(tuning, audio.shadowTuningForTesting());
    }

    static Stream<AudioPresentationTuning> presentationTunings() {
        return Stream.of(
                new AudioPresentationTuning(
                        SmpsSequencer.Region.NTSC, false, false, false),
                new AudioPresentationTuning(
                        SmpsSequencer.Region.PAL, true, false, true),
                new AudioPresentationTuning(
                        SmpsSequencer.Region.NTSC, false, true, true),
                new AudioPresentationTuning(
                        SmpsSequencer.Region.PAL, true, true, false));
    }

    @Test
    void shadowConstructionFailureCannotPreventLegacyAudibleCommand() {
        FailingShadowBackend backend = new FailingShadowBackend();
        audio.setBackend(backend);

        assertDoesNotThrow(audio::stopMusic);

        assertTrue(backend.stopped,
                "legacy audible command must run despite shadow failure");
        assertEquals(1, audio.commandTimeline().entryCount(),
                "logical ordering remains recorded");
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
    }

    private static class TuningBackend extends NullAudioBackend {
        private final AudioPresentationTuning tuning;

        TuningBackend(AudioPresentationTuning tuning) {
            this.tuning = tuning;
        }

        @Override
        public AudioPresentationTuning presentationTuning() {
            return tuning;
        }
    }

    private static final class FailingShadowBackend
            extends NullAudioBackend {
        boolean stopped;

        @Override
        public AudioPresentationTuning presentationTuning() {
            throw new IllegalStateException("injected shadow failure");
        }

        @Override
        public void stopPlayback() {
            stopped = true;
        }
    }
}
