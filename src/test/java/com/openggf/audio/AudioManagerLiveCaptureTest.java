package com.openggf.audio;

import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.presentation.PresentationMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Live-recording attachment over the single authoritative presentation
 * producer. There is no second runtime to switch to: {@code
 * beginLiveCaptureAudio} only attaches a non-consuming lease to the producer
 * that already owns the speaker packet.
 */
class AudioManagerLiveCaptureTest {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(2));
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @Test
    void beginsDrainsAndIdempotentlyClosesAuthoritativeProducerHandle() {
        var producerBefore = AudioManagerTestDiagnostics.producerFingerprint(audio);

        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, handle.sampleRate());
        assertEquals(1, handle.frameRate());
        assertEquals(2, handle.maxStereoFramesPerPacket());
        short[] captured = new short[4];
        assertEquals(2, handle.drainPresentationFrame(captured));
        assertEquals(0, captured[0]);
        assertEquals(0, captured[1]);
        assertEquals(0, captured[2]);
        assertEquals(0, captured[3]);
        assertEquals(2, handle.totalStereoFrames());
        assertEquals(new AudioFrameClock.Snapshot(2, 1, 2, 0),
                handle.clockSnapshot());

        handle.close();
        handle.close();
        var producerAfter = AudioManagerTestDiagnostics.producerFingerprint(audio);
        assertEquals(producerBefore.voiceIdentities(), producerAfter.voiceIdentities(),
                "attach/detach must preserve registry identity and state");
        assertNotEquals(producerBefore.clock(), producerAfter.clock(),
                "only the explicitly presented frame advances producer time");

        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void repeatedAttachAndDetachNeverReplacesTheProducerOrItsVoices() {
        var before = AudioManagerTestDiagnostics.producerFingerprint(audio);

        for (int attempt = 0; attempt < 3; attempt++) {
            LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
            var attached = AudioManagerTestDiagnostics.producerFingerprint(audio);
            assertEquals(before.captureCount() + 1, attached.captureCount(),
                    "exactly one live lease is attached");
            assertEquals(before.voiceIdentities(), attached.voiceIdentities(),
                    "attaching a live lease must not rebind voices");
            assertEquals(before.clock(), attached.clock(),
                    "attaching a live lease must not move the producer clock");
            handle.close();
            var detached = AudioManagerTestDiagnostics.producerFingerprint(audio);
            assertEquals(before.captureCount(), detached.captureCount());
            assertEquals(before.voiceIdentities(), detached.voiceIdentities());
            assertEquals(before.clock(), detached.clock(),
                    "detaching a live lease must not flush or move the clock");
        }
    }

    @Test
    void rejectsSecondSimultaneousHandle() {
        LiveCaptureAudioHandle first = audio.beginLiveCaptureAudio(1);

        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));

        first.close();
    }

    @Test
    void handleRejectsDrainAfterClose() {
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        handle.close();

        assertThrows(IllegalStateException.class,
                () -> handle.drainPresentationFrame(new short[4]));
    }

    @Test
    void handleRejectsDrainAfterTheProducerItLeasedIsReplaced() {
        LiveCaptureAudioHandle replacedHandle = audio.beginLiveCaptureAudio(1);

        // Installing a backend rebuilds the presentation producer, which owns
        // every lease attached to it.
        audio.setBackend(new FixedRateNullBackend(2));

        assertThrows(IllegalStateException.class,
                () -> replacedHandle.drainPresentationFrame(new short[4]));

        LiveCaptureAudioHandle replacementHandle = audio.beginLiveCaptureAudio(1);
        replacedHandle.close();
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1));
        replacementHandle.close();
    }

    private static final class FixedRateNullBackend extends NullAudioBackend {
        private final int outputSampleRate;

        private FixedRateNullBackend(int outputSampleRate) {
            this.outputSampleRate = outputSampleRate;
        }

        @Override
        public int outputSampleRate() {
            return outputSampleRate;
        }
    }
}
