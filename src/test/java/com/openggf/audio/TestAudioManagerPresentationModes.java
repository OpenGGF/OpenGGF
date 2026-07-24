package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioManagerPresentationModes {

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void updatePumpsSinkButNeverPresentsAnotherPacket() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        CountingRuntime obsoleteRuntime = new CountingRuntime();
        installRuntime(audio, obsoleteRuntime);

        audio.presentFrame(PresentationMode.FORWARD);
        long presented = AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames();
        audio.update();

        assertEquals(1, presented);
        assertEquals(presented, AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames());
        assertEquals(0, obsoleteRuntime.advances,
                "the promoted producer is the sole presentation clock");
    }

    @Test
    void everyExplicitModePresentsExactlyOneProducerPacket() {
        AudioManager audio = AudioManager.getInstance();

        audio.presentFrame(PresentationMode.FORWARD);
        audio.presentFrame(PresentationMode.SILENT);
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);

        var snapshot = AudioManagerTestDiagnostics.shadowParitySnapshot(audio);
        assertEquals(3, snapshot.presentedFrames());
        assertEquals(1, snapshot.forwardFrames());
        assertEquals(1, snapshot.silentFrames());
        assertEquals(1, snapshot.reverseFrames());
    }

    @Test
    void forwardAppliesCommandsBeforeRendering() {
        AudioManager audio = AudioManager.getInstance();
        audio.submitShadowRawPcmForTesting(new byte[4_000], 48_000);

        audio.presentFrame(PresentationMode.FORWARD);

        assertTrue(rawPcmCursor(audio) > 0,
                "the queued source must render in the packet that admits it");
    }

    @Test
    void silentAppliesStructuralCommandsWithoutMovingVoiceCursors() {
        AudioManager audio = AudioManager.getInstance();
        audio.submitShadowRawPcmForTesting(new byte[4_000], 48_000);

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, rawPcmCursor(audio));

        audio.stopSegaPcm();
        audio.presentFrame(PresentationMode.SILENT);
        assertNull(audio.captureLogicalSnapshot().presentation()
                .rawPcmVoiceId(),
                "structural stop must apply while presentation is silent");
    }

    @Test
    void reverseConsumesHistoryWithoutRenderingVoices() {
        AudioManager audio = AudioManager.getInstance();
        audio.setRewindHistoryArmed(true);
        audio.submitShadowRawPcmForTesting(new byte[4_000], 48_000);
        audio.presentFrame(PresentationMode.FORWARD);
        long cursor = rawPcmCursor(audio);

        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);

        assertEquals(cursor, rawPcmCursor(audio));
    }

    @Test
    void rewindPresentationControlsNeverReachRetiredRuntimeOrBackend()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioTestFixtures.RecordingAudioBackend backend =
                new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
        backend.clear();
        CountingRuntime obsoleteRuntime = new CountingRuntime();
        installRuntime(audio, obsoleteRuntime);

        audio.setRewindHistoryArmed(true);
        audio.clearPcmHistory();
        audio.beginReverseAudioPresentation();
        audio.setReversePlaybackRate(2.0);
        audio.endReverseAudioPresentation();

        assertEquals(0, backend.totalCalls());
        assertEquals(0, obsoleteRuntime.presentationControlCalls);
    }

    private static long rawPcmCursor(AudioManager audio) {
        Long rawId = audio.captureLogicalSnapshot().presentation()
                .rawPcmVoiceId();
        return audio.captureLogicalSnapshot().presentation().voices().stream()
                .filter(PresentationVoiceSnapshot.Sample.class::isInstance)
                .map(PresentationVoiceSnapshot.Sample.class::cast)
                .filter(voice -> voice.voiceId() == rawId)
                .findFirst()
                .orElseThrow()
                .sourcePositionQ32();
    }

    private static void installRuntime(
            AudioManager audio, DeterministicAudioRuntime runtime)
            throws Exception {
        Method setter = AudioManager.class.getDeclaredMethod(
                "setDeterministicAudioRuntime",
                DeterministicAudioRuntime.class);
        setter.setAccessible(true);
        setter.invoke(audio, runtime);
    }

    private static final class CountingRuntime
            implements DeterministicAudioRuntime {
        int advances;
        int presentationControlCalls;

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            advances++;
        }

        @Override
        public void beginReversePresentation() {
            presentationControlCalls++;
        }

        @Override
        public void endReversePresentation() {
            presentationControlCalls++;
        }

        @Override
        public void setReversePlaybackRate(double rate) {
            presentationControlCalls++;
        }

        @Override
        public void clearPcmHistory() {
            presentationControlCalls++;
        }
    }
}
