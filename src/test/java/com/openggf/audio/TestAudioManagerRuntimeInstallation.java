package com.openggf.audio;

import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend installation after the split presentation runtime was removed.
 *
 * <p>There is no runtime to install any more: an {@link AudioBackend} supplies
 * SMPS sources, profile routing and the speaker sink, and the presentation
 * producer owns cadence, final PCM, history and every capture lease.
 * Replacing a backend therefore only replaces the sink (and re-realizes the
 * producer at that sink's rate); the manager-owned logical ledger that decides
 * which voices are played — audio profile, SMPS loader, donor bindings, ring
 * alternation and the command timeline — survives untouched.
 */
class TestAudioManagerRuntimeInstallation {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void backendReplacementChangesOnlyTheSinkAndPreservesLogicalVoices()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(48_000));
        audio.registerDonorSound(GameSound.RING, "s3k", 0x2B);
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.FORWARD);

        AudioPresentationSink sinkBefore = presentationSink(audio);
        assertEquals(48_000, sinkBefore.sampleRate());
        var logicalBefore = audio.captureLogicalSnapshot();

        audio.setBackend(new FixedRateNullBackend(44_100));

        AudioPresentationSink sinkAfter = presentationSink(audio);
        assertNotSame(sinkBefore, sinkAfter,
                "installing a backend installs that backend's sink");
        assertEquals(44_100, sinkAfter.sampleRate(),
                "the producer is re-realized at the new sink's rate");
        assertEquals(44_100, audio.outputSampleRate());

        var logicalAfter = audio.captureLogicalSnapshot();
        assertEquals(logicalBefore.ringLeft(), logicalAfter.ringLeft(),
                "ring alternation is manager-owned logical state");
        assertEquals(logicalBefore.donorBindings(),
                logicalAfter.donorBindings(),
                "donor SFX bindings survive a backend swap");
        assertEquals(logicalBefore.commandTimelineFrame(),
                logicalAfter.commandTimelineFrame(),
                "the logical command timeline survives a backend swap");
        assertEquals(logicalBefore.commandTimelineNextOrder(),
                logicalAfter.commandTimelineNextOrder());

        assertBackendExposesNoPresentationSurface();
    }

    @Test
    void failedDeviceInitializationInstallsNoDeviceSink() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();

        audio.setBackend(new FailingInitBackend());

        assertInstanceOf(NullAudioBackend.class, audio.getBackend(),
                "a backend that cannot initialize is replaced by the null one");
        assertInstanceOf(NoDeviceAudioSink.class, presentationSink(audio),
                "a failed device install must leave a silent no-device sink");

        // The producer is still the sole presentation owner and still presents
        // one clocked packet per outer frame.
        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        audio.presentFrame(PresentationMode.FORWARD);
        short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
        assertEquals(capture.sampleRate() / 60,
                capture.drainPresentationFrame(packet));
        capture.close();
    }

    @Test
    void stoppingCaptureDuringRewindLeavesProducerReverseOwnershipUntouched() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(48_000));

        LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(60);
        audio.beginReverseAudioPresentation();
        capture.close();

        assertTrue(
                AudioManagerTestDiagnostics.producerFingerprint(audio)
                        .reverseActive(),
                "detaching capture must not end held reverse presentation");

        audio.endReverseAudioPresentation();

        assertFalse(AudioManagerTestDiagnostics.producerFingerprint(audio)
                .reverseActive());
    }

    /**
     * The backend interface must expose no presentation ownership at all —
     * neither a runtime attachment point nor reverse/history control.
     */
    private static void assertBackendExposesNoPresentationSurface() {
        java.util.Set<String> forbidden = java.util.Set.of(
                "attachDeterministicAudioRuntime",
                "supportsDeterministicRuntimePresentation",
                "supportsLiveCapturePresentation",
                "beginReversePresentation",
                "endReversePresentation",
                "setReversePlaybackRate",
                "setRewindHistoryArmed",
                "captureLogicalSnapshot",
                "restoreLogicalSnapshot",
                "prepareLogicalRestore",
                "commitLogicalRestore",
                "discardLogicalRestore",
                "rollbackLogicalRestore",
                "playPcmSample",
                "stopPcmSample");
        for (var method : AudioBackend.class.getMethods()) {
            assertFalse(forbidden.contains(method.getName()),
                    "AudioBackend still exposes " + method.getName());
        }
    }

    private static AudioPresentationSink presentationSink(AudioManager audio)
            throws Exception {
        Field field = AudioManager.class.getDeclaredField("presentationSink");
        field.setAccessible(true);
        return (AudioPresentationSink) field.get(audio);
    }

    private static class FixedRateNullBackend extends NullAudioBackend {
        private final int outputSampleRate;

        private FixedRateNullBackend(int outputSampleRate) {
            this.outputSampleRate = outputSampleRate;
        }

        @Override
        public int outputSampleRate() {
            return outputSampleRate;
        }
    }

    private static final class FailingInitBackend extends NullAudioBackend {
        @Override
        public void init() {
            throw new IllegalStateException("no audio device available");
        }
    }
}
