package com.openggf.audio;

import com.openggf.data.Rom;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.output.AudioPresentationSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        // destroy(), not resetState(): only a genuine teardown retires a live
        // lease, so this is what guarantees a clean start for each case.
        audio.destroy();
        audio.resetState();
        audio.setBackend(new FixedRateNullBackend(2));
    }

    @AfterEach
    void tearDown() {
        audio.destroy();
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
    void aLeaseIsCarriedAcrossAProducerRebuildRatherThanRetired() {
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);

        // Installing a backend rebuilds the presentation producer. It replaces
        // the output device, which is not a reason to end a recording of what
        // the engine is playing: entering gameplay does exactly this, and
        // retiring the lease here left a recording started on the master title
        // screen silent from the moment the player started a game.
        audio.setBackend(new FixedRateNullBackend(2));
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, handle.drainPresentationFrame(new short[4]),
                "the carried lease keeps receiving packets from the new producer");
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1),
                "the manager still knows it holds that lease, so a second"
                        + " attach is refused rather than silently added");

        handle.close();
        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void matchedSourceChangeCarriesCaptureClockAndReinstallKeepsProducerExact()
            throws ReflectiveOperationException {
        audio.setBackend(new FixedRateNullBackend(3));
        var profile = new AudioTestFixtures.StubAudioProfile(new AudioTestFixtures.StubSmpsLoader());
        Rom first = new Rom();
        Rom second = new Rom();
        AudioManagerInternalAccess.installMatchedSource(audio, first, profile);
        try (var capture = audio.beginLiveCaptureAudio(2)) {
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(1, capture.drainPresentationFrame(new short[4]));
            assertEquals(1, capture.totalStereoFrames());
            assertEquals(new AudioFrameClock.Snapshot(3, 2, 1, 1), capture.clockSnapshot());
            Object before = AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class);
            assertNotNull(before);

            AudioManagerInternalAccess.installMatchedSource(audio, second, profile);

            Object replacement = AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class);
            assertNotNull(replacement);
            assertNotSame(before, replacement);
            // A new lease starts its own count, retaining the prior fractional phase.
            assertEquals(new AudioFrameClock.Snapshot(3, 2, 0, 1), capture.clockSnapshot());
            assertEquals(1, capture.totalStereoFrames());
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(2, capture.drainPresentationFrame(new short[4]));
            assertEquals(3, capture.totalStereoFrames());
            assertEquals(new AudioFrameClock.Snapshot(3, 2, 2, 0), capture.clockSnapshot());
            var retained = AudioManagerTestDiagnostics.producerFingerprint(audio);
            Object retainedProducer = AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class);
            AudioManagerInternalAccess.installMatchedSource(audio, second, profile);
            assertSame(retainedProducer, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class));
            assertEquals(retained, AudioManagerTestDiagnostics.producerFingerprint(audio));
            assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(2));
            audio.presentFrame(PresentationMode.FORWARD);
            assertEquals(1, capture.drainPresentationFrame(new short[4]));
            assertEquals(4, capture.totalStereoFrames());
            assertEquals(new AudioFrameClock.Snapshot(3, 2, 3, 1), capture.clockSnapshot());
        }
    }

    /**
     * The lease is carried only where a rebuild is in flight. A handle whose
     * producer went away for any other reason must still refuse to read from
     * it, rather than quietly returning packets from a dead producer.
     *
     * <p>{@code destroy()} is that teardown. {@code resetState()} is not: it is
     * also the mode-transition reset {@code Engine.exitMasterTitleScreen} runs
     * on the way into gameplay, and retiring the lease there is what left a
     * recording started on the master title screen silent for the rest of the
     * session.
     */
    @Test
    void aLeaseRetiredByTeardownStillRejectsDrains() {
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);

        audio.destroy();

        assertThrows(IllegalStateException.class,
                () -> handle.drainPresentationFrame(new short[4]));
    }

    /**
     * A mode transition rebuilds the presentation; it does not end a recording
     * of the window.
     */
    @Test
    void aLeaseIsCarriedAcrossResetStateRatherThanRetired() {
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);

        audio.resetState();
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, handle.drainPresentationFrame(new short[4]),
                "the carried lease keeps receiving packets from the new producer");
        handle.close();
    }

    /**
     * The live lease follows the same retirement rule as the offline lease:
     * the manager's view is retired only after the producer accepts the
     * detach. This drives {@code closeLiveCaptureAudio} directly through
     * {@code handle.close()} on a foreign thread, which is the one path
     * {@code AudioManagerCaptureModeTest}'s off-owner-thread cases never
     * reach — they go through {@code endCaptureMode()} (the offline lease) and
     * {@code destroy()} (which retires the handle before any close).
     *
     * <p>Marking the handle closed or clearing the manager's reference before
     * the producer accepted would orphan a lease that keeps receiving every
     * presented packet, let the next attach succeed with a second lease, and
     * make the owner-thread retry a no-op so the orphan could never be
     * detached.
     */
    @Test
    void offOwnerThreadCloseKeepsTheLiveLeaseAttachedAndRetryable()
            throws Exception {
        int leasesBefore = AudioManagerTestDiagnostics
                .producerFingerprint(audio).captureCount();
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        assertEquals(leasesBefore + 1, AudioManagerTestDiagnostics
                .producerFingerprint(audio).captureCount());

        Throwable[] offThreadFailure = new Throwable[1];
        Thread offOwnerThread = new Thread(() -> {
            try {
                handle.close();
            } catch (Throwable failure) {
                offThreadFailure[0] = failure;
            }
        }, "not-the-producer-owner");
        offOwnerThread.start();
        offOwnerThread.join();

        assertInstanceOf(IllegalStateException.class, offThreadFailure[0],
                "the producer refuses an off-owner-thread detach");
        assertEquals(leasesBefore + 1, AudioManagerTestDiagnostics
                        .producerFingerprint(audio).captureCount(),
                "the refused detach left the lease attached to the producer");
        assertThrows(IllegalStateException.class,
                () -> audio.beginLiveCaptureAudio(1),
                "the manager must still know it holds that lease, so a second"
                        + " lease is rejected rather than silently attached");

        handle.close();

        assertEquals(leasesBefore, AudioManagerTestDiagnostics
                        .producerFingerprint(audio).captureCount(),
                "a retry on the owner thread releases it");
        LiveCaptureAudioHandle reattached = audio.beginLiveCaptureAudio(1);
        reattached.close();
    }

    @Test
    void invalidLiveRateIsValidatedOnlyAfterRealization() {
        RuntimeException realizationFailure = new IllegalStateException("realization refused");
        audio.setBackend(new NullAudioBackend() {
            @Override public AudioPresentationTuning presentationTuning() {
                assertTrue(Thread.holdsLock(audio));
                throw realizationFailure;
            }
        });
        assertSame(realizationFailure, assertThrows(IllegalStateException.class,
                () -> audio.beginLiveCaptureAudio(0)));
        audio.setBackend(new FixedRateNullBackend(2));
        assertThrows(IllegalArgumentException.class, () -> audio.beginLiveCaptureAudio(0));
        audio.beginLiveCaptureAudio(1).close();
    }

    @Test
    void closedMetadataDoesNotAcquireTheManagerMonitorAndClosedDrainWinsOverNull() throws Exception {
        LiveCaptureAudioHandle handle = audio.beginLiveCaptureAudio(1);
        assertThrows(NullPointerException.class, () -> handle.drainPresentationFrame(null));
        handle.close();
        CountDownLatch metadataRead = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try {
                assertEquals(2, handle.sampleRate());
                assertEquals(1, handle.frameRate());
                assertEquals(2, handle.maxStereoFramesPerPacket());
            } catch (Throwable problem) {
                failure.set(problem);
            } finally {
                metadataRead.countDown();
            }
        }, "capture-metadata-reader");
        try {
            synchronized (audio) {
                reader.start();
                assertTrue(metadataRead.await(5, TimeUnit.SECONDS),
                        "metadata remains readable while another thread holds the manager monitor");
            }
        } finally {
            reader.join(5_000);
        }
        assertFalse(reader.isAlive());
        assertNull(failure.get());
        assertThrows(IllegalStateException.class, () -> handle.drainPresentationFrame(null));
    }

    @Test
    void producerRealizationKeepsTheCallingEntryPointsMonitorCoverage() {
        AtomicReference<Boolean> locked = new AtomicReference<>();
        audio.setBackend(new NullAudioBackend() {
            @Override public AudioPresentationTuning presentationTuning() {
                locked.set(Thread.holdsLock(audio));
                return AudioPresentationTuning.DEFAULT;
            }
        });
        audio.presentationFrameRate();
        assertEquals(Boolean.TRUE, locked.get());
        audio.resetState();
        locked.set(null);
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(Boolean.FALSE, locked.get(),
                "producer realization outside the synchronized compatibility API stays unlocked");
    }

    @Test
    void pendingRealizationFailureKeepsAdmissionAndExplicitCloseCancelsTheCarry() {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        audio.setBackend(new NullAudioBackend() {
            @Override public int outputSampleRate() { return 2; }
            @Override public AudioPresentationTuning presentationTuning() {
                RuntimeException problem = failure.getAndSet(null);
                if (problem != null) throw problem;
                return AudioPresentationTuning.DEFAULT;
            }
        });
        LiveCaptureAudioHandle live = audio.beginLiveCaptureAudio(1);
        assertEquals(2, live.drainPresentationFrame(new short[4]));
        audio.resetState();
        RuntimeException realizationFailure = new IllegalStateException("pending realization");
        failure.set(realizationFailure);
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(0),
                "duplicate admission wins over both realization and invalid live rate");
        assertSame(realizationFailure, assertThrows(IllegalStateException.class,
                audio::presentationFrameRate), "duplicate rejection did not consume realization");
        assertEquals(2, live.totalStereoFrames(), "failed realization adds no carried total");
        assertThrows(IllegalStateException.class, () -> live.drainPresentationFrame(new short[4]));

        live.close();
        LiveCaptureAudioHandle replacement = audio.beginLiveCaptureAudio(1);
        assertEquals(2, replacement.drainPresentationFrame(new short[4]));
        assertEquals(2, replacement.totalStereoFrames(), "the replacement starts a new recording");
        assertThrows(IllegalStateException.class, () -> live.drainPresentationFrame(null));
        replacement.close();
    }

    @Test
    void failedRebuildThenDestroyKeepsThePendingRecordingForLaterRealization() {
        RuntimeException teardownFailure = new IllegalStateException("rebuild sink teardown");
        AudioPresentationSink sink = new AudioPresentationSink() {
            @Override public int sampleRate() { return 2; }
            @Override public void accept(AudioPresentationFrameView frame) { }
            @Override public void onReverseBoundary() { }
            @Override public void close() {
                assertFalse(Thread.holdsLock(audio), "launch replacement has no method-wide manager lock");
                throw teardownFailure;
            }
        };
        audio.setBackend(new NullAudioBackend() {
            @Override public int outputSampleRate() { return 2; }
            @Override public AudioPresentationSink createPresentationSink(
                    Consumer<Throwable> failure, Consumer<String> warning) { return sink; }
        });
        LiveCaptureAudioHandle live = audio.beginLiveCaptureAudio(1);
        audio.beginCaptureMode(2, audio.presentationFrameRate());
        assertEquals(2, live.drainPresentationFrame(new short[4]));

        assertSame(teardownFailure, assertThrows(IllegalStateException.class,
                () -> audio.setBackendForLaunch(new FixedRateNullBackend(2))));
        assertThrows(IllegalArgumentException.class, () -> audio.beginCaptureMode(2, 0),
                "accepted close retires offline admission even though the rebuild failed");
        assertThrows(IllegalStateException.class, () -> live.drainPresentationFrame(new short[4]));
        audio.destroy();
        assertThrows(IllegalStateException.class, () -> audio.beginLiveCaptureAudio(1),
                "destroy does not cancel an already-pending carry");
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(2, live.drainPresentationFrame(new short[4]));
        assertEquals(4, live.totalStereoFrames(), "the old segment is counted once on successful rebind");
        assertEquals(new AudioFrameClock.Snapshot(2, 1, 2, 0), live.clockSnapshot(),
                "clock snapshots stay segment-local even when totals are cumulative");
        live.close();
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
