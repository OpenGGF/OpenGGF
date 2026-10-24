package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.OuterFramePresentation;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.presentation.AudioPresentationFrameView;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

class TestAudioManagerPresentationModes {

    @ParameterizedTest
    @ValueSource(strings = {"backend", "launch", "profile", "rom", "destroy"})
    void rebuildRetainsHeldTargetProvenanceAndInjectionButInvalidatesAcknowledgment(
            String path) throws Exception {
        AudioManager audio = AudioManager.createSilent();
        try {
            audio.beginReverseAudioPresentation();
            assertTrue(audio.preparePostBoundaryReverseRelease());
            AudioLogicalSnapshot selected = audio.deferredReverseLogicalSnapshotForTesting();
            AudioPresentationProducer previous = shadowProducer(audio);
            assertNotEquals(0L, previous.transactionFingerprint()
                    .preparedSelectedRestoreIdentity().token());
            AudioManagerTestDiagnostics.failNextReverseRelease(audio);

            replaceForBaseline(audio, path);

            assertTrue(previous.isClosed());
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertTrue(audio.isReverseAudioPresentationActive());
            assertTrue(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
            assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
            assertTrue(AudioManagerTestDiagnostics.reverseReleaseFailureArmed(audio));
        } finally {
            audio.resetState();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"backend", "launch", "profile", "rom", "destroy"})
    void lifecycleInvalidatesAcknowledgmentBeforeFallibleProducerClose(String path)
            throws Exception {
        AudioManager audio = AudioManager.createSilent();
        AudioPresentationSink sink = mock(AudioPresentationSink.class);
        when(sink.sampleRate()).thenReturn(48_000);
        audio.setBackend(new InspectingBackend(sink));
        audio.beginReverseAudioPresentation();
        assertTrue(audio.preparePostBoundaryReverseRelease());
        AudioLogicalSnapshot selected = audio.deferredReverseLogicalSnapshotForTesting();
        AudioPresentationProducer previous = shadowProducer(audio);
        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        var failure = new IllegalStateException("baseline sink teardown");
        doThrow(failure).when(sink).close();
        try {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> replaceForBaseline(audio, path)));
            assertTrue(previous.isClosed());
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertTrue(audio.isReverseAudioPresentationActive());
            assertTrue(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
            assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
            assertTrue(AudioManagerTestDiagnostics.reverseReleaseFailureArmed(audio));
        } finally {
            doNothing().when(sink).close();
            audio.resetState();
        }
    }

    @Test
    void failedPreResetStopRetainsHeldLedgerUntilFullResetReachesItsClear() throws Exception {
        AudioManager audio = AudioManager.createSilent();
        NullAudioBackend backend = spy(NullAudioBackend.class);
        audio.setBackend(backend);
        audio.beginReverseAudioPresentation();
        assertTrue(audio.preparePostBoundaryReverseRelease());
        AudioLogicalSnapshot selected = audio.deferredReverseLogicalSnapshotForTesting();
        AudioPresentationProducer producer = shadowProducer(audio);
        var before = producer.transactionFingerprint();
        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        var failure = new IllegalStateException("baseline pre-reset stop");
        doThrow(failure).when(backend).stopPlayback();
        try {
            assertSame(failure, assertThrows(IllegalStateException.class, audio::resetState));
            assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertTrue(audio.isReverseAudioPresentationActive());
            assertTrue(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
            assertTrue(AudioManagerTestDiagnostics.reverseReleaseFailureArmed(audio));
            assertFalse(producer.isClosed());
            assertEquals(before, producer.transactionFingerprint());
        } finally {
            doNothing().when(backend).stopPlayback();
            audio.resetState();
        }
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(AudioManagerTestDiagnostics.postBoundaryReverseTarget(audio));
        assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertFalse(AudioManagerTestDiagnostics.reverseReleaseFailureArmed(audio));
        assertEquals(0, audio.logicalRestorePublicationsForTesting());
        assertEquals(0, audio.commandTimeline().entryCount());
        assertTrue(producer.isClosed());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void sinkReleaseFailureConsumesProducerCursorBeforeManagerPublication(
            boolean preparedEarlier, boolean suppressedDiagnostic) throws Exception {
        AudioManager audio = AudioManager.createSilent();
        AudioPresentationSink sink = mock(AudioPresentationSink.class);
        when(sink.sampleRate()).thenReturn(48_000);
        audio.setBackend(new InspectingBackend(sink));
        try {
            audio.toggleMute(ChannelType.FM, 0);
            audio.presentFrame(PresentationMode.SILENT);
            AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
            audio.toggleMute(ChannelType.FM, 1);
            audio.presentFrame(PresentationMode.SILENT);
            assertEquals(3, audio.captureLogicalSnapshot().presentation().fmMuteMask());
            audio.beginReverseAudioPresentation();
            audio.restoreLogicalSnapshot(selected);
            if (preparedEarlier) assertTrue(audio.commitDeferredReverseLogicalRestore());
            var before = audio.releaseStateForTesting().producer();
            assertTrue(before.reverseActive());
            assertTrue(before.reverseCursor() != null);
            int publications = audio.logicalRestorePublicationsForTesting();
            var ordinary = new IllegalStateException("baseline release sink");
            var diagnostic = new AudioDiagnosticObserverException(
                    new IllegalStateException("suppressed baseline diagnostic"));
            if (suppressedDiagnostic) ordinary.addSuppressed(diagnostic);
            // Begin has already crossed the sink boundary successfully.
            doThrow(ordinary).doNothing().when(sink).onReverseBoundary();

            if (suppressedDiagnostic) {
                assertSame(diagnostic, assertThrows(AudioDiagnosticObserverException.class,
                        audio::endReverseAudioPresentation));
                assertSame(diagnostic, ordinary.getSuppressed()[0]);
            } else {
                assertFalse(audio.endReverseAudioPresentation());
            }
            var partial = audio.releaseStateForTesting().producer();
            assertFalse(partial.reverseActive());
            assertNull(partial.reverseCursor());
            assertEquals(0L, partial.preparedSelectedRestoreIdentity().token());
            assertEquals(1, audio.captureLogicalSnapshot().presentation().fmMuteMask());
            assertEquals(publications, audio.logicalRestorePublicationsForTesting());
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertTrue(audio.isReverseAudioPresentationActive());
            assertEquals(preparedEarlier,
                    AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));

            assertTrue(audio.endReverseAudioPresentation());
            assertEquals(publications + 1, audio.logicalRestorePublicationsForTesting());
            assertFalse(audio.isReverseAudioPresentationActive());
            assertNull(audio.deferredReverseLogicalSnapshotForTesting());
            var retried = audio.releaseStateForTesting().producer();
            assertEquals(preparedEarlier, retried.preparedSelectedRestoreIdentity().token() == 0,
                    "only retrying an attempt-owned preparation leaves a token on the inactive producer");
            assertEquals(partial.history(), retried.history());
            assertEquals(partial.clock(), retried.clock());
            verify(sink, times(2)).onReverseBoundary();
        } finally {
            audio.resetState();
        }
    }

    @Test
    void virtualBoundaryCaptureIsSingleAndRetryProtectsFreshSourcesAfterQueuedCommand() throws Exception {
        AudioManager audio = spy(AudioManager.class);
        try {
            audio.beginReverseAudioPresentation();
            audio.submitShadowRawPcmForTesting(new byte[8_000], 48_000);
            AudioPresentationProducer producer = shadowProducer(audio);
            AtomicInteger captures = new AtomicInteger();
            doAnswer(call -> {
                assertFalse(Thread.holdsLock(audio));
                captures.incrementAndGet();
                return call.callRealMethod();
            }).when(audio).captureLogicalSnapshot();
            assertTrue(audio.preparePostBoundaryReverseRelease());
            AudioLogicalSnapshot selected = audio.deferredReverseLogicalSnapshotForTesting();
            assertTrue(selected.presentation().rawPcmVoiceId() != null);
            var token = producer.transactionFingerprint().preparedSelectedRestoreIdentity();
            // This command is queued after capture and must not select a new target.
            audio.setSpeedMultiplier(7);
            AudioManagerTestDiagnostics.failNextReverseRelease(audio);
            assertFalse(audio.endReverseAudioPresentation());
            assertTrue(audio.preparePostBoundaryReverseRelease());
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertEquals(token, producer.transactionFingerprint().preparedSelectedRestoreIdentity());
            assertEquals(1, captures.get());
            assertTrue(audio.afterRewindRestore(0, AudioPresentationPolicy.STOP_ALL_PRESENTATION));
            verify(audio, times(2)).commitDeferredReverseLogicalRestore();
            verify(audio, times(2)).endReverseAudioPresentation();
            AudioLogicalSnapshot published = audio.captureLogicalSnapshot();
            assertEquals(selected.presentation().rawPcmVoiceId(), published.presentation().rawPcmVoiceId());
            assertFalse(published.presentation().voices().isEmpty());
            assertEquals(7, published.presentation().speedMultiplier());
            assertFalse(audio.isReverseAudioPresentationActive());
        } finally {
            audio.resetState();
        }
    }

    @Test
    void virtualReleasePreparationRereadsCallbackReplacedProducer() throws Exception {
        AudioManager audio = spy(AudioManager.class);
        try {
            audio.beginReverseAudioPresentation();
            AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
            audio.restoreLogicalSnapshot(selected);
            AudioPresentationProducer original = shadowProducer(audio);
            AtomicReference<AudioPresentationProducer> replacement = new AtomicReference<>();
            doAnswer(call -> {
                assertFalse(Thread.holdsLock(audio));
                boolean prepared = (boolean) call.callRealMethod();
                audio.setBackend(new NullAudioBackend());
                replacement.set(shadowProducer(audio));
                return prepared;
            }).when(audio).commitDeferredReverseLogicalRestore();
            doAnswer(call -> {
                assertFalse(Thread.holdsLock(audio));
                return call.callRealMethod();
            }).when(audio).endReverseAudioPresentation();

            assertTrue(audio.afterRewindRestore(0, AudioPresentationPolicy.STOP_TRANSIENT_SFX));
            verify(audio).endReverseAudioPresentation();
            verify(audio).commitDeferredReverseLogicalRestore();
            assertTrue(original.isClosed());
            assertFalse(replacement.get().isClosed());
            assertFalse(replacement.get().transactionFingerprint().reverseActive());
            assertFalse(audio.isReverseAudioPresentationActive());
            assertNull(audio.deferredReverseLogicalSnapshotForTesting());
            assertEquals(1, audio.logicalRestorePublicationsForTesting());
        } finally {
            audio.resetState();
        }
    }

    @Test
    void offThreadNoOpPathsPrecedeProducerChecksAndActiveBeginRejectsBeforeLedgerMutation()
            throws Exception {
        AudioManager audio = AudioManager.createSilent();
        audio.captureLogicalSnapshot();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                audio.restoreLogicalSnapshot(null);
                assertTrue(audio.afterRewindRestore(0, null));
                assertTrue(audio.preparePostBoundaryReverseRelease());
                assertFalse(audio.commitDeferredReverseLogicalRestore());
                assertNull(audio.deferredReverseLogicalSnapshotForTesting());
                try { audio.beginReverseAudioPresentation(); }
                catch (Throwable failure) { rejected.set(failure); }
            } catch (Throwable failure) { unexpected.set(failure); }
        });
        thread.start();
        thread.join(5_000);
        assertFalse(thread.isAlive());
        try {
            assertNull(unexpected.get());
            assertTrue(rejected.get() instanceof IllegalStateException);
            assertFalse(audio.isReverseAudioPresentationActive());
            assertNull(audio.deferredReverseLogicalSnapshotForTesting());
            assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
            audio.beginReverseAudioPresentation();
            AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
            audio.restoreLogicalSnapshot(selected);
            assertTrue(audio.commitDeferredReverseLogicalRestore());
            var before = audio.releaseStateForTesting().producer();
            AtomicReference<Throwable> activeFailure = new AtomicReference<>();
            Thread active = new Thread(() -> {
                try {
                    // Manager selection and acknowledged no-op admission have no thread check.
                    audio.restoreLogicalSnapshot(selected);
                    assertTrue(audio.commitDeferredReverseLogicalRestore());
                    // The producer rejects release; the manager catches this ordinary failure.
                    assertFalse(audio.endReverseAudioPresentation());
                } catch (Throwable failure) { activeFailure.set(failure); }
            });
            active.start();
            active.join(5_000);
            assertFalse(active.isAlive());
            assertNull(activeFailure.get());
            assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
            assertTrue(audio.isReverseAudioPresentationActive());
            assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
            assertEquals(before, audio.releaseStateForTesting().producer());
        } finally { audio.resetState(); }
    }

    private static void replaceForBaseline(AudioManager audio, String path) {
        switch (path) {
            case "backend" -> audio.setBackend(new NullAudioBackend());
            case "launch" -> audio.setBackendForLaunch(new NullAudioBackend());
            case "profile" -> audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                    new AudioTestFixtures.StubSmpsLoader()));
            case "rom" -> audio.setRom(new com.openggf.data.Rom());
            case "destroy" -> audio.destroy();
            default -> throw new AssertionError(path);
        }
    }

    @Test
    void recordedReverseOwnsOuterPresentationWithoutTakingLogicalRewindOwnership() {
        AudioManager audio = AudioManager.getInstance();
        var outer = new OuterFramePresentation(audio);
        var replay = audio.recordPresentationAudio(1);
        audio.submitShadowRawPcmForTesting(new byte[8_000], 48_000);
        outer.present(false, false, false);
        var history = AudioManagerTestDiagnostics.producerFingerprint(audio).history();
        replay.beginReverse(1);
        assertFalse(audio.isReverseAudioPresentationActive());
        assertTrue(audio.isReverseAudioOutputActive());
        assertEquals(PresentationMode.REVERSE, outer.modeFor(false, false, false));
        assertEquals(PresentationMode.SILENT, outer.modeFor(false, true, false));
        assertThrows(IllegalStateException.class, audio::beginReverseAudioPresentation);
        assertFalse(audio.isReverseAudioPresentationActive(),
                "a conflicting debug begin must not publish logical rewind ownership");
        outer.present(false, false, false);
        assertEquals(1, AudioManagerTestDiagnostics.shadowParitySnapshot(audio).reverseFrames());
        replay.close();
        assertFalse(audio.isReverseAudioOutputActive());
        assertEquals(PresentationMode.FORWARD, outer.modeFor(false, false, false));
        assertEquals(history, AudioManagerTestDiagnostics.producerFingerprint(audio).history());
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void updatePumpsSinkButNeverPresentsAnotherPacket() throws Exception {
        AudioManager audio = AudioManager.getInstance();

        audio.presentFrame(PresentationMode.FORWARD);
        long presented = AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames();
        long producerSamples = AudioManagerTestDiagnostics
                .producerFingerprint(audio).clock().totalSamplesProduced();
        audio.update();

        assertEquals(1, presented);
        assertEquals(presented, AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames());
        assertEquals(producerSamples, AudioManagerTestDiagnostics
                .producerFingerprint(audio).clock().totalSamplesProduced(),
                "device pumping must not advance the authoritative clock");
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
    void rewindPresentationControlsNeverReachTheBackend()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioTestFixtures.RecordingAudioBackend backend =
                new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
        backend.clear();

        audio.setRewindHistoryArmed(true);
        audio.clearPcmHistory();
        audio.beginReverseAudioPresentation();
        audio.setReversePlaybackRate(2.0);
        audio.endReverseAudioPresentation();

        assertEquals(0, backend.totalCalls());
    }

    @Test
    void ordinaryExceptionStillClosesTapRegistryHistoryThenSink()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        InspectingSink sink = new InspectingSink(audio);
        audio.setBackend(new InspectingBackend(sink));
        audio.setRewindHistoryArmed(true);
        audio.submitShadowRawPcmForTesting(new byte[4_000], 48_000);
        audio.presentFrame(PresentationMode.FORWARD);
        sink.capture = audio.attachShadowCaptureForTesting(60);

        try {
            throw new IllegalStateException("ordinary engine failure");
        } catch (IllegalStateException expected) {
            audio.destroy();
        }

        assertTrue(sink.closed);
        assertTrue(sink.tapClosedBeforeSink);
        assertTrue(sink.registryEmptyBeforeSink);
        assertTrue(sink.historyEmptyBeforeSink);
    }

    @Test
    void resyncCleanupPopsARealOverrideAndPreservesBaseMusic()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioVoiceRegistry registry = registry(audio);
        registry.apply(new AudioPresentationCommand.ReplaceMusic(
                music(audio, 1, 0x81, "base")));
        registry.apply(new AudioPresentationCommand.PushMusicOverride(
                music(audio, 2, 0x82, "override")));
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                SampleBackedVoice.oneShot(3, 1,
                        registeredPcm(audio, "sample"),
                        48_000, 1.0f, 1.0f)));
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                SampleBackedVoice.oneShot(4, 0,
                        registeredPcm(audio, "raw"),
                        48_000, 1.0f, 1.0f), new byte[] {0}));
        audio.beginReverseAudioPresentation();

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);

        var snapshot = audio.captureLogicalSnapshot().presentation();
        assertEquals(0x81, snapshot.activeMusic().musicId());
        assertTrue(snapshot.overrideStack().isEmpty());
        assertNull(snapshot.rawPcmVoiceId());
        assertEquals(1, snapshot.voices().size(),
                "all transient sample/raw voices must be removed");
    }

    @Test
    void resyncCleanupWithoutOverridePreservesDurableMusic()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        registry(audio).apply(new AudioPresentationCommand.ReplaceMusic(
                music(audio, 1, 0x81, "base")));
        audio.beginReverseAudioPresentation();

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);

        assertEquals(0x81, audio.captureLogicalSnapshot().presentation()
                .activeMusic().musicId());
    }

    @Test
    void authoritativeResyncPopsThePersistentSessionOverride() {
        AudioManager audio = AudioManager.getInstance();
        configureSessionMusic(audio, 0x81, 0x82);
        audio.playMusic(0x81);
        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0x82, activeSmpsMusicId(audio));
        audio.beginReverseAudioPresentation();

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);

        assertEquals(0x81, activeSmpsMusicId(audio),
                "resync must pop the persistent session override, not only "
                        + "its registry handle");
    }

    @Test
    void stopAllPresentationClearsPersistentSessionAndRegistry() {
        AudioManager audio = AudioManager.getInstance();
        configureSessionMusic(audio, 0x81);
        audio.playMusic(0x81);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0x81, activeSmpsMusicId(audio));
        audio.beginReverseAudioPresentation();

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.STOP_ALL_PRESENTATION);

        var presentation = audio.captureLogicalSnapshot().presentation();
        assertNull(presentation.activeMusic());
        assertTrue(presentation.smpsLogical().sequencers().isEmpty(),
                "stop-all must clear the persistent logical driver too");
    }

    @Test
    void nullSelectionLogicalStagingUsesThePersistentSessionTransaction() {
        AudioManager audio = AudioManager.getInstance();
        configureSessionMusic(audio, 0x81);

        audio.replayTimelineCommandLogically(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));

        assertEquals(0x81, activeSmpsMusicId(audio),
                "null-selection staging must not update registry metadata alone");
    }

    @Test
    void synchronousQueuePressureDrainUsesTheSessionCompositeBoundary()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        configureSessionMusic(audio, 0x81);
        audio.playMusic(0x81);
        AudioPresentationCommandQueue queue = commands(audio);
        for (int index = queue.size();
                index < AudioPresentationCommandQueue.CAPACITY; index++) {
            queue.submit(new AudioPresentationCommand.FadeMusic(
                            index + 1, 1),
                    () -> true, ignored -> { });
        }

        audio.toggleMute(ChannelType.FM, 0);

        assertEquals(0x81, activeSmpsMusicId(audio),
                "pressure drain must apply the queued activation to the session");
        assertEquals(1, queue.size(),
                "the triggering command remains queued after the drained batch");
    }

    @Test
    void suppressedAndStopAllPoliciesMutateRealProducerStateExactly()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioVoiceRegistry registry = registry(audio);
        registry.apply(new AudioPresentationCommand.ReplaceMusic(
                music(audio, 1, 0x81, "base")));
        audio.setRewindHistoryArmed(true);
        audio.presentFrame(PresentationMode.FORWARD);
        audio.beginReverseAudioPresentation();
        long populatedEpoch = AudioManagerTestDiagnostics
                .producerFingerprint(audio).history().epoch();

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE);
        assertTrue(audio.isReverseAudioPresentationActive());
        assertEquals(1, audio.captureLogicalSnapshot().presentation()
                .voices().size());

        audio.afterRewindRestore(7,
                AudioPresentationPolicy.STOP_ALL_PRESENTATION);
        var fingerprint =
                AudioManagerTestDiagnostics.producerFingerprint(audio);
        assertFalse(audio.isReverseAudioPresentationActive());
        assertTrue(audio.captureLogicalSnapshot().presentation()
                .voices().isEmpty());
        assertTrue(fingerprint.history().epoch() > populatedEpoch);
        assertEquals(0, fingerprint.history().storedFrames());
    }

    @Test
    void reverseReleaseReportsPrepareFailureWithoutMutatingFullStateAndRetries()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
        populateReleaseState(audio);
        var selected = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        audio.restoreLogicalSnapshot(selected);
        var before = audio.releaseStateForTesting();
        var deferred = audio.deferredReverseLogicalSnapshotForTesting();
        // The producer is the only restore owner left, so the injectable
        // preparation failure is an unresolvable presentation dependency.
        presentationPcmCache(audio).clear();

        assertFalse(audio.endReverseAudioPresentation());

        assertEquals(before.logical(), audio.releaseStateForTesting().logical());
        assertEquals(before.producer(),
                audio.releaseStateForTesting().producer());
        assertSame(deferred,
                audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(audio.isReverseAudioPresentationActive());

        registeredPcm(audio, "base");
        registeredPcm(audio, "override");
        registeredPcm(audio, "sample");
        registeredPcm(audio, "raw");
        assertTrue(audio.endReverseAudioPresentation());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
    }

    @Test
    void producerPrepareFailurePreservesPriorSelectionForRetry()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
        populateReleaseState(audio);
        var selected = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        audio.restoreLogicalSnapshot(selected);
        AudioPresentationProducer producer = shadowProducer(audio);
        producer.restore(
                selected.presentation(),
                audio.shadowFactoryForTesting(), true);
        producer.prepareSelectedRestore();
        var before = audio.releaseStateForTesting();
        var deferred = audio.deferredReverseLogicalSnapshotForTesting();
        assertTrue(before.producer().selectedRestoreIdentity().token() != 0);
        assertTrue(before.producer()
                .preparedSelectedRestoreIdentity().token() != 0);
        presentationPcmCache(audio).clear();

        assertFalse(audio.endReverseAudioPresentation());

        assertEquals(before.logical(), audio.releaseStateForTesting().logical());
        assertEquals(before.producer(),
                audio.releaseStateForTesting().producer(),
                "the prior selected and prepared producer identities must survive");
        assertSame(deferred,
                audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(audio.isReverseAudioPresentationActive());

        registeredPcm(audio, "base");
        registeredPcm(audio, "override");
        registeredPcm(audio, "sample");
        registeredPcm(audio, "raw");
        assertTrue(audio.endReverseAudioPresentation());

        assertFalse(audio.isReverseAudioPresentationActive());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
    }

    @ParameterizedTest
    @EnumSource(value = AudioPresentationPolicy.class, names = {
            "STOP_TRANSIENT_SFX_RESYNC_MUSIC",
            "STOP_TRANSIENT_SFX",
            "STOP_ALL_PRESENTATION"
    })
    void mutatingPoliciesWaitForSuccessfulTransactionalReleaseAndRetry(
            AudioPresentationPolicy policy) throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
        populateReleaseState(audio);
        var selected = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        audio.restoreLogicalSnapshot(selected);
        var before = audio.releaseStateForTesting();
        var deferred = audio.deferredReverseLogicalSnapshotForTesting();
        // A release-stage failure, distinct from the dependency-resolution
        // (prepare-stage) failure that
        // producerPrepareFailurePreservesPriorSelectionForRetry covers: the
        // selection prepares successfully and the release then fails at the
        // publication step, which is the case the deleted
        // FailingReleaseBackend.failNextCommit used to inject.
        AudioManagerTestDiagnostics.failNextReverseRelease(audio);

        audio.afterRewindRestore(7, policy);

        assertEquals(before.logical(), audio.releaseStateForTesting().logical(),
                policy.name());
        assertEquals(before.producer(),
                audio.releaseStateForTesting().producer(), policy.name());
        assertSame(deferred,
                audio.deferredReverseLogicalSnapshotForTesting(),
                policy.name());
        assertTrue(audio.isReverseAudioPresentationActive(), policy.name());

        audio.afterRewindRestore(7, policy);

        assertFalse(audio.isReverseAudioPresentationActive(), policy.name());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting(),
                policy.name());
        var presentation = audio.captureLogicalSnapshot().presentation();
        switch (policy) {
            case STOP_TRANSIENT_SFX_RESYNC_MUSIC -> {
                assertEquals(0x81,
                        presentation.activeMusic().musicId());
                assertTrue(presentation.overrideStack().isEmpty());
                assertEquals(1, presentation.voices().size());
            }
            case STOP_TRANSIENT_SFX -> {
                assertEquals(0x82,
                        presentation.activeMusic().musicId());
                assertEquals(1, presentation.overrideStack().size());
                assertEquals(2, presentation.voices().size());
            }
            case STOP_ALL_PRESENTATION ->
                    assertTrue(presentation.voices().isEmpty());
            default -> throw new AssertionError(policy);
        }
    }

    @Test
    void afterRewindRestoreReportsReleaseSuccessToProductionHosts()
            throws Exception {
        assertEquals(boolean.class,
                AudioManager.class.getMethod(
                        "afterRewindRestore", int.class,
                        AudioPresentationPolicy.class).getReturnType());
    }

    private enum ManagerProducerClosePath {
        SET_BACKEND {
            @Override void close(AudioManager audio) {
                audio.setBackend(new NullAudioBackend());
            }
        },
        RESET {
            @Override void close(AudioManager audio) {
                audio.resetState();
            }
        },
        DESTROY {
            @Override void close(AudioManager audio) {
                audio.destroy();
            }
        };

        abstract void close(AudioManager audio);
    }

    @ParameterizedTest
    @EnumSource(ManagerProducerClosePath.class)
    void managerLifecycleDiscardsPreparedProducerRestoreWriteFreeExactlyOnce(
            ManagerProducerClosePath closePath) throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioPresentationProducer producer = shadowProducer(audio);
        PreparedProducerRestore prepared =
                prepareObservedProducerRestore(producer);
        assertTrue(producer.transactionFingerprint()
                        .preparedSelectedRestoreIdentity().token() != 0,
                "fixture must hold one prepared logical restore");

        closePath.close(audio);

        assertTrue(producer.isClosed());
        assertEquals(1, prepared.logicalDiscards().get(),
                "manager lifecycle must discard the prepared token once");
        assertEquals(0, prepared.ymWrites().get(),
                "prepared SMPS discard must not silence YM");
        assertEquals(0, prepared.psgWrites().get(),
                "prepared SMPS discard must not silence PSG");
        assertNull(field(AudioManager.class, "shadowProducer").get(audio));
        assertNull(field(AudioManager.class, "shadowSmpsSession").get(audio));

        closePath.close(audio);

        assertEquals(1, prepared.logicalDiscards().get(),
                "a repeated manager lifecycle call must not consume twice");
        assertEquals(0, prepared.ymWrites().get());
        assertEquals(0, prepared.psgWrites().get());
        assertNull(field(AudioManager.class, "shadowProducer").get(audio));
        assertNull(field(AudioManager.class, "shadowSmpsSession").get(audio));
    }

    @ParameterizedTest
    @EnumSource(value = AudioPresentationPolicy.class, names = {
            "STOP_TRANSIENT_SFX_RESYNC_MUSIC",
            "STOP_TRANSIENT_SFX",
            "STOP_ALL_PRESENTATION"
    })
    void queuedReverseCommandsAreAppliedInOrderBeforePolicyCleanupAndCannotResurrect(
            AudioPresentationPolicy policy) throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioVoiceRegistry registry = registry(audio);
        registry.apply(new AudioPresentationCommand.ReplaceMusic(
                music(audio, 1, 0x80, "old-base")));
        audio.beginReverseAudioPresentation();

        AudioPresentationCommandQueue commands = commands(audio);
        commands.submit(new AudioPresentationCommand.ReplaceMusic(
                        music(audio, 2, 0x81, "queued-base")),
                () -> true, registry::apply);
        commands.submit(new AudioPresentationCommand.PushMusicOverride(
                        music(audio, 3, 0x82, "queued-override")),
                () -> true, registry::apply);
        SmpsAssetKey smpsKey = new SmpsAssetKey(
                "base", SmpsAssetKey.Route.BASE_ID, 0xA0, null);
        AudioTestFixtures.StubSmpsData smps =
                new AudioTestFixtures.StubSmpsData("queued-smps");
        smps.setId(0xA0);
        audio.shadowFactoryForTesting().registerSmpsSfxAsset(
                smpsKey, 0, smps, AudioTestFixtures.EMPTY_DAC,
                new SmpsSequencerConfig.Builder().build(), false);
        commands.submit(new AudioPresentationCommand.AddSmpsSfx(
                        audio.shadowFactoryForTesting().resolveSmpsSfx(
                                4, smpsKey, 1 << 16, 0x70,
                                0, 0, 800)),
                () -> true, registry::apply);
        commands.submit(AudioPresentationCommand.StartSampleSfx.fromVoice(
                        SampleBackedVoice.oneShot(5, 1,
                                registeredPcm(audio, "queued-sample"),
                                48_000, 1.0f, 1.0f)),
                () -> true, registry::apply);
        commands.submit(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                        SampleBackedVoice.oneShot(6, 0,
                                registeredPcm(audio, "queued-raw"),
                                48_000, 1.0f, 1.0f), new byte[] {0}),
                () -> true, registry::apply);

        audio.afterRewindRestore(7, policy);

        assertEquals(0, commands.size(),
                "release cleanup must consume the complete owner queue");
        assertPolicyDurableOutcome(audio, policy);
        audio.presentFrame(PresentationMode.SILENT);
        assertPolicyDurableOutcome(audio, policy);
        audio.presentFrame(PresentationMode.FORWARD);
        assertPolicyDurableOutcome(audio, policy);
    }

    private static void populateReleaseState(AudioManager audio)
            throws Exception {
        AudioVoiceRegistry registry = registry(audio);
        registry.apply(new AudioPresentationCommand.ReplaceMusic(
                music(audio, 1, 0x81, "base")));
        registry.apply(new AudioPresentationCommand.PushMusicOverride(
                music(audio, 2, 0x82, "override")));
        registry.apply(AudioPresentationCommand.StartSampleSfx.fromVoice(
                SampleBackedVoice.oneShot(3, 1,
                        registeredPcm(audio, "sample"),
                        48_000, 1.0f, 1.0f)));
        registry.apply(AudioPresentationCommand.ReplaceRawPcm.fromVoice(
                SampleBackedVoice.oneShot(4, 0,
                        registeredPcm(audio, "raw"),
                        48_000, 1.0f, 1.0f), new byte[] {0}));
        audio.setRewindHistoryArmed(true);
        audio.presentFrame(PresentationMode.FORWARD);
    }

    private static void assertPolicyDurableOutcome(
            AudioManager audio, AudioPresentationPolicy policy) {
        var presentation = audio.captureLogicalSnapshot().presentation();
        switch (policy) {
            case STOP_TRANSIENT_SFX_RESYNC_MUSIC -> {
                assertEquals(0x81, presentation.activeMusic().musicId());
                assertTrue(presentation.overrideStack().isEmpty());
                assertEquals(1, presentation.voices().size());
            }
            case STOP_TRANSIENT_SFX -> {
                assertEquals(0x82, presentation.activeMusic().musicId());
                assertEquals(1, presentation.overrideStack().size());
                assertEquals(2, presentation.voices().size());
            }
            case STOP_ALL_PRESENTATION ->
                    assertTrue(presentation.voices().isEmpty());
            default -> throw new AssertionError(policy);
        }
        assertNull(presentation.rawPcmVoiceId());
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

    private static AudioVoiceRegistry registry(AudioManager audio)
            throws Exception {
        audio.captureLogicalSnapshot();
        return (AudioVoiceRegistry) field(
                AudioManager.class, "shadowRegistry").get(audio);
    }

    private static AudioPresentationCommandQueue commands(AudioManager audio)
            throws Exception {
        audio.captureLogicalSnapshot();
        return (AudioPresentationCommandQueue) field(
                AudioManager.class, "shadowCommands").get(audio);
    }

    private static AudioPresentationCommand.MusicVoiceEntry music(
            AudioManager audio, long voiceId, int musicId, String assetId) {
        return AudioPresentationCommand.MusicVoiceEntry.fromVoice(
                musicId, AudioSourceDescriptor.fallbackMusic(musicId),
                SampleBackedVoice.loopingMusic(
                        voiceId, registeredPcm(audio, assetId),
                        48_000, 1.0f));
    }

    private static void configureSessionMusic(
            AudioManager audio, int... musicIds) {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int musicId : musicIds) {
            AudioTestFixtures.StubSmpsData music =
                    new AudioTestFixtures.StubSmpsData(
                            "session-music-" + musicId);
            music.setId(musicId);
            loader.musicResults.put(musicId, music);
        }
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public boolean isMusicOverride(int musicId) {
                return musicId == 0x82;
            }

            @Override
            public SmpsSequencerConfig getSequencerConfig() {
                return new SmpsSequencerConfig.Builder().build();
            }
        });
        audio.setRom(new com.openggf.data.Rom());
    }

    private static int activeSmpsMusicId(AudioManager audio) {
        return audio.captureLogicalSnapshot().presentation().smpsLogical()
                .sequencers().stream()
                .filter(entry -> !entry.sfx())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no persistent SMPS music is active"))
                .source().id();
    }

    private static DecodedPcm registeredPcm(
            AudioManager audio, String assetId) {
        byte[] samples = new byte[2_000];
        java.util.Arrays.fill(samples, (byte) 132);
        return audio.shadowFactoryForTesting().registerUnsigned8Mono(
                assetId, samples, 48_000);
    }

    private static AudioPresentationProducer shadowProducer(
            AudioManager audio) throws Exception {
        audio.captureLogicalSnapshot();
        return (AudioPresentationProducer) field(
                AudioManager.class, "shadowProducer").get(audio);
    }

    private static PreparedProducerRestore prepareObservedProducerRestore(
            AudioPresentationProducer producer) {
        AudioPresentationSnapshot selected = producer.snapshot();
        AtomicInteger ymWrites = new AtomicInteger();
        AtomicInteger psgWrites = new AtomicInteger();
        AtomicInteger logicalDiscards = new AtomicInteger();
        AudioPresentationDependencyResolver resolver =
                new AudioPresentationDependencyResolver() {
                    @Override
                    public DiagnosticTransaction beginDiagnosticTransaction() {
                        return new DiagnosticTransaction() {
                            @Override
                            public void endPreparation() {
                            }

                            @Override
                            public void commit() {
                                throw new AssertionError(
                                        "prepared restore must not commit");
                            }

                            @Override
                            public void discard() {
                                logicalDiscards.incrementAndGet();
                            }
                        };
                    }

                    @Override
                    public DecodedPcm resolvePcm(String assetId) {
                        throw new AssertionError("no PCM voice expected");
                    }

                };
        producer.beginReverse(1.0);
        producer.prepareRestoreSelection(selected, resolver);
        ymWrites.set(0);
        psgWrites.set(0);
        return new PreparedProducerRestore(
                ymWrites, psgWrites, logicalDiscards);
    }

    private static DecodedPcmCache presentationPcmCache(
            AudioManager audio) throws Exception {
        AudioPresentationSourceFactory factory =
                audio.shadowFactoryForTesting();
        AudioPresentationSourceFactory.Settings settings =
                (AudioPresentationSourceFactory.Settings) field(
                        AudioPresentationSourceFactory.class, "settings")
                        .get(factory);
        return settings.pcmCache();
    }

    private record PreparedProducerRestore(
            AtomicInteger ymWrites,
            AtomicInteger psgWrites,
            AtomicInteger logicalDiscards) {
    }

    private static final class InspectingBackend extends NullAudioBackend {
        private final AudioPresentationSink sink;

        private InspectingBackend(AudioPresentationSink sink) {
            this.sink = sink;
        }

        @Override
        public AudioPresentationSink createPresentationSink(
                java.util.function.Consumer<Throwable> failureHandler,
                java.util.function.Consumer<String> warningHandler) {
            return sink;
        }
    }

    private static final class InspectingSink
            implements AudioPresentationSink {
        private final AudioManager owner;
        private LiveCaptureAudioHandle capture;
        private boolean tapClosedBeforeSink;
        private boolean registryEmptyBeforeSink;
        private boolean historyEmptyBeforeSink;
        private boolean closed;

        private InspectingSink(AudioManager owner) {
            this.owner = owner;
        }

        @Override
        public int sampleRate() {
            return 48_000;
        }

        @Override
        public void accept(AudioPresentationFrameView frame) {
        }

        @Override
        public void onReverseBoundary() {
        }

        @Override
        public void close() {
            try {
                AudioPresentationProducer producer =
                        (AudioPresentationProducer) field(
                                AudioManager.class, "shadowProducer")
                                .get(owner);
                AudioVoiceRegistry registry =
                        (AudioVoiceRegistry) field(
                                AudioPresentationProducer.class, "registry")
                                .get(producer);
                PcmHistoryRing history =
                        (PcmHistoryRing) field(
                                AudioPresentationProducer.class, "history")
                                .get(producer);
                tapClosedBeforeSink = assertThrows(
                        IllegalStateException.class,
                        () -> capture.drainPresentationFrame(
                                new short[capture.maxStereoFramesPerPacket()
                                        * 2])) != null;
                registryEmptyBeforeSink = registry.orderedVoiceCount() == 0;
                historyEmptyBeforeSink =
                        history.diagnosticSnapshot().storedFrames() == 0;
                closed = true;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

    }

    private static Field field(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
