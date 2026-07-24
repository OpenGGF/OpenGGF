package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
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
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        long producerSamples = AudioManagerTestDiagnostics
                .producerFingerprint(audio).clock().totalSamplesProduced();
        audio.update();

        assertEquals(1, presented);
        assertEquals(presented, AudioManagerTestDiagnostics
                .shadowParitySnapshot(audio).presentedFrames());
        assertEquals(producerSamples, AudioManagerTestDiagnostics
                .producerFingerprint(audio).clock().totalSamplesProduced(),
                "device pumping must not advance the authoritative clock");
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
                        48_000, 1.0f, 1.0f)));
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
        FailingReleaseBackend backend = new FailingReleaseBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        populateReleaseState(audio);
        var selected = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        audio.restoreLogicalSnapshot(selected);
        var before = audio.releaseStateForTesting();
        var deferred = audio.deferredReverseLogicalSnapshotForTesting();
        backend.failNextPrepare = true;

        assertFalse(audio.endReverseAudioPresentation());

        assertEquals(before.logical(), audio.releaseStateForTesting().logical());
        assertEquals(before.producer(),
                audio.releaseStateForTesting().producer());
        assertSame(deferred,
                audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(audio.isReverseAudioPresentationActive());

        assertTrue(audio.endReverseAudioPresentation());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
    }

    @Test
    void producerPrepareFailureDiscardsBackendTokenAndPreservesPriorSelectionForRetry()
            throws Exception {
        FailingReleaseBackend backend = new FailingReleaseBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
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

        assertEquals(1, backend.prepareCalls);
        assertEquals(1, backend.discardCalls,
                "an unpublished backend token must be discarded");
        assertEquals(0, backend.commitCalls);
        assertEquals(0, backend.rollbackCalls,
                "rollback is reserved for a commit attempt");
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

        assertEquals(2, backend.prepareCalls);
        assertEquals(1, backend.discardCalls);
        assertEquals(1, backend.commitCalls);
        assertEquals(0, backend.rollbackCalls);
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
        FailingReleaseBackend backend = new FailingReleaseBackend();
        AudioManager audio = AudioManager.getInstance();
        audio.setBackend(backend);
        populateReleaseState(audio);
        var selected = audio.captureLogicalSnapshot();
        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        audio.restoreLogicalSnapshot(selected);
        var before = audio.releaseStateForTesting();
        var deferred = audio.deferredReverseLogicalSnapshotForTesting();
        backend.failNextCommit = true;

        audio.afterRewindRestore(7, policy);

        assertEquals(before.logical(), audio.releaseStateForTesting().logical(),
                policy.name());
        assertEquals(before.producer(),
                audio.releaseStateForTesting().producer(), policy.name());
        assertSame(deferred,
                audio.deferredReverseLogicalSnapshotForTesting(),
                policy.name());
        assertTrue(audio.isReverseAudioPresentationActive(), policy.name());
        assertEquals(1, backend.prepareCalls, policy.name());
        assertEquals(1, backend.commitCalls, policy.name());
        assertEquals(1, backend.rollbackCalls, policy.name());
        assertEquals(1, backend.discardCalls,
                "a token prepared and rolled back in this attempt is abandoned");

        audio.afterRewindRestore(7, policy);

        assertFalse(audio.isReverseAudioPresentationActive(), policy.name());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting(),
                policy.name());
        assertEquals(2, backend.prepareCalls, policy.name());
        assertEquals(2, backend.commitCalls, policy.name());
        assertEquals(1, backend.rollbackCalls, policy.name());
        assertEquals(1, backend.discardCalls, policy.name());
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
    void managerLifecycleDiscardsPreparedProducerRestoreExactlyOnce(
            ManagerProducerClosePath closePath) throws Exception {
        AudioManager audio = AudioManager.getInstance();
        AudioPresentationProducer producer = shadowProducer(audio);
        CountingSmpsDriver preparedDriver =
                prepareCountingProducerRestore(producer);

        closePath.close(audio);
        closePath.close(audio);

        assertEquals(1, preparedDriver.stopCalls,
                "manager lifecycle must route producer cleanup exactly once");
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
        audio.shadowFactoryForTesting().warmSmpsSfxAsset(
                smpsKey, smps, AudioTestFixtures.EMPTY_DAC,
                new SmpsSequencerConfig.Builder().build());
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
                                48_000, 1.0f, 1.0f)),
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
                        48_000, 1.0f, 1.0f)));
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
        assertNull(presentation.standaloneSmpsVoiceId());
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

    private static DecodedPcm registeredPcm(
            AudioManager audio, String assetId) {
        byte[] samples = new byte[2_000];
        java.util.Arrays.fill(samples, (byte) 132);
        return audio.shadowFactoryForTesting().registerUnsigned8Mono(
                assetId, samples, 48_000);
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

    private static AudioPresentationProducer shadowProducer(
            AudioManager audio) throws Exception {
        audio.captureLogicalSnapshot();
        return (AudioPresentationProducer) field(
                AudioManager.class, "shadowProducer").get(audio);
    }

    private static CountingSmpsDriver prepareCountingProducerRestore(
            AudioPresentationProducer producer) {
        SmpsDriver source = new SmpsDriver();
        PresentationVoiceSnapshot.Smps voice =
                new PresentationVoiceSnapshot.Smps(
                        1, 0, 0x81,
                        AudioSourceDescriptor.baseMusic(0x81),
                        1, source.captureSnapshot());
        AudioPresentationSnapshot selected =
                new AudioPresentationSnapshot(
                        2, List.of(voice),
                        new AudioPresentationSnapshot.MusicSlotSnapshot(
                                0x81,
                                AudioSourceDescriptor.baseMusic(0x81), 1),
                        List.of(), null, null,
                        0, 0, 0, 0,
                        false, false, false, 1, true,
                        new SmpsCoordFlagRuntimeState.Snapshot(0));
        AtomicReference<CountingSmpsDriver> recreated =
                new AtomicReference<>();
        AudioPresentationDependencyResolver resolver =
                new AudioPresentationDependencyResolver() {
                    @Override
                    public DecodedPcm resolvePcm(String assetId) {
                        throw new AssertionError("no PCM voice expected");
                    }

                    @Override
                    public SmpsCompositeVoice recreateSmps(
                            PresentationVoiceSnapshot.Smps snapshot) {
                        CountingSmpsDriver driver =
                                new CountingSmpsDriver();
                        recreated.set(driver);
                        return new SmpsCompositeVoice(
                                snapshot.voiceId(), snapshot.priority(),
                                snapshot.musicId(),
                                snapshot.sourceDescriptor(),
                                snapshot.maxStereoFrames(), driver);
                    }
                };
        producer.beginReverse(1.0);
        producer.prepareRestoreSelection(selected, resolver);
        return recreated.get();
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

    private static final class FailingReleaseBackend
            extends NullAudioBackend {
        private boolean failNextPrepare;
        private boolean failNextCommit;
        private int prepareCalls;
        private int commitCalls;
        private int discardCalls;
        private int rollbackCalls;

        @Override
        public PreparedLogicalRestore prepareLogicalRestore(
                AudioBackendLogicalSnapshot snapshot,
                SmpsDriverSnapshot.DependencyResolver resolver,
                boolean preservePresentationQueue) {
            prepareCalls++;
            if (failNextPrepare) {
                failNextPrepare = false;
                throw new IllegalStateException(
                        "injected release prepare failure");
            }
            return new Prepared(snapshot);
        }

        @Override
        public void commitLogicalRestore(PreparedLogicalRestore prepared) {
            commitCalls++;
            if (failNextCommit) {
                failNextCommit = false;
                throw new IllegalStateException(
                        "injected release commit failure");
            }
        }

        @Override
        public void rollbackLogicalRestore(PreparedLogicalRestore prepared) {
            rollbackCalls++;
        }

        @Override
        public void discardLogicalRestore(PreparedLogicalRestore prepared) {
            discardCalls++;
        }

        private record Prepared(AudioBackendLogicalSnapshot snapshot)
                implements PreparedLogicalRestore {
        }
    }

    private static final class CountingSmpsDriver extends SmpsDriver {
        private int stopCalls;

        @Override
        public void stopAll() {
            stopCalls++;
            super.stopAll();
        }
    }

    private static final class InspectingBackend extends NullAudioBackend {
        private final InspectingSink sink;

        private InspectingBackend(InspectingSink sink) {
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
