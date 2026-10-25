package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationDependencyResolver;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcm;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.SampleBackedVoice;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.presentation.AudioPresentationForwardService;
import com.openggf.audio.runtime.AudioFrameClock;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

class TestAudioPresentationSnapshotParity {
    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void logicalSnapshotCapturesAndRestoresPresentationFlagsAtSameBoundary() {
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.SILENT);

        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        AudioPresentationSnapshot expected = selected.presentation();
        assertTrue(expected.speedShoesEnabled());
        assertEquals(3, expected.speedMultiplier());
        assertFalse(expected.ringLeft());

        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.setSpeedShoes(false);
        audio.setSpeedMultiplier(1);
        audio.resetRingSound();
        audio.presentFrame(PresentationMode.SILENT);
        assertNotEquals(expected, audio.captureLogicalSnapshot().presentation());

        audio.restoreLogicalSnapshot(selected);

        assertEquals(expected, audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void heldReverseDefersPresentationRestoreUntilRelease() {
        audio.toggleMute(ChannelType.FM, 0);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();

        audio.toggleMute(ChannelType.FM, 1);
        audio.presentFrame(PresentationMode.SILENT);
        AudioPresentationSnapshot disturbed =
                audio.captureLogicalSnapshot().presentation();

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        assertEquals(disturbed,
                audio.captureLogicalSnapshot().presentation(),
                "held reverse must preserve the active history/cursor state");

        audio.endReverseAudioPresentation();
        assertEquals(selected.presentation(),
                audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void preparedAThenSelectedBPublishesBLedgerWithAPresentation() {
        AudioLogicalSnapshot a = prepareHeldFlagTarget();
        var prepared = audio.releaseStateForTesting().producer();
        AudioLogicalSnapshot live = audio.captureLogicalSnapshot();
        AudioLogicalSnapshot b = new AudioLogicalSnapshot(false, 41, 3, 17,
                live.presentation(), Set.of("selected-b"), Set.of(
                new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                        GameSound.JUMP, "selected-b", 0xA7)));
        audio.restoreLogicalSnapshot(b);

        assertSame(b, audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertProducerStateExactly(prepared, audio.releaseStateForTesting().producer(),
                "selection replaces manager metadata without preparing B");
        assertDeepSnapshotEquals(live, audio.captureLogicalSnapshot());
        int before = audio.logicalRestorePublicationsForTesting();
        assertTrue(audio.endReverseAudioPresentation());

        AudioLogicalSnapshot published = audio.captureLogicalSnapshot();
        assertDeepSnapshotEquals(a.presentation(), published.presentation());
        assertFalse(published.ringLeft());
        assertEquals(41, published.commandTimelineFrame());
        assertEquals(3, published.commandTimelineNextOrder());
        assertEquals(b.donorBindings(), published.donorBindings());
        assertEquals(before + 1, audio.logicalRestorePublicationsForTesting());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
    }

    @Test
    void replayAfterPreparationDiscardsOldPreparationAndReleaseRestoresReplayedTarget() {
        AudioLogicalSnapshot a = prepareHeldFlagTarget();
        AudioLogicalSnapshot live = audio.captureLogicalSnapshot();
        var before = audio.releaseStateForTesting().producer();
        int publications = audio.logicalRestorePublicationsForTesting();
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertNotEquals(0L, before.selectedRestoreIdentity().token());
        assertNotEquals(0L, before.selectedRestoreResolverIdentity().token());
        assertNotEquals(0L, before.preparedSelectedRestoreIdentity().token());
        audio.replayTimelineCommandLogically(new AudioCommand.SetSpeedMultiplier(5));
        AudioLogicalSnapshot replayed = audio.deferredReverseLogicalSnapshotForTesting();

        assertNotEquals(a, replayed);
        assertEquals(5, replayed.presentation().speedMultiplier());
        assertEquals(a.commandEntryCount(), replayed.commandEntryCount());
        assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        var absent = new AudioPresentationProducer.IdentityFingerprint(0L);
        assertEquals(new AudioPresentationProducer.TransactionFingerprint(
                        before.clock(), before.history(), before.smpsSessionIdentity(),
                        before.voiceIdentities(), before.reverseCursor(),
                        absent, absent, absent,
                        before.releaseCrossfadeRemaining(), before.lastReverseLeft(),
                        before.lastReverseRight(), before.historyArmed(), before.reverseActive(),
                        before.reverseFrameOutput(), before.hasLastReverseFrame(),
                        before.captureCount(), before.forwardTiming()),
                audio.releaseStateForTesting().producer(),
                "detached replay restores every live producer field and discards only the old selection");
        assertDeepSnapshotEquals(live, audio.captureLogicalSnapshot());
        assertEquals(publications, audio.logicalRestorePublicationsForTesting());
        assertTrue(audio.endReverseAudioPresentation());

        AudioLogicalSnapshot published = audio.captureLogicalSnapshot();
        assertDeepSnapshotEquals(replayed.presentation(), published.presentation());
        assertNotEquals(a.presentation().speedMultiplier(), published.presentation().speedMultiplier());
        assertEquals(replayed.ringLeft(), published.ringLeft());
        assertEquals(live.commandEntryCount(), published.commandEntryCount());
        assertEquals(replayed.commandTimelineFrame(), audio.commandTimeline().currentFrame());
        assertEquals(replayed.commandTimelineNextOrder(), audio.commandTimeline().nextOrder());
        assertEquals(publications + 1, audio.logicalRestorePublicationsForTesting());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
        assertFalse(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        var released = audio.releaseStateForTesting().producer();
        assertEquals(0L, released.selectedRestoreIdentity().token());
        assertEquals(0L, released.selectedRestoreResolverIdentity().token());
        assertEquals(0L, released.preparedSelectedRestoreIdentity().token());
    }

    @Test
    void cursorRefreshRetainsPresentationEntryCountAndAcknowledgment() {
        AudioLogicalSnapshot a = prepareHeldFlagTarget();
        var before = audio.releaseStateForTesting().producer();
        audio.beginCommandTimelineFrame(73);
        audio.commandTimeline().record(new AudioCommand.ResetRingAlternation(false));
        audio.beginCommandTimelineFrame(73);
        AudioLogicalSnapshot refreshed = audio.deferredReverseLogicalSnapshotForTesting();

        assertNotEquals(a, refreshed);
        assertSame(a.presentation(), refreshed.presentation());
        assertEquals(a.commandEntryCount(), refreshed.commandEntryCount());
        assertEquals(73, refreshed.commandTimelineFrame());
        assertEquals(1, refreshed.commandTimelineNextOrder());
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertProducerStateExactly(before, audio.releaseStateForTesting().producer(),
                "cursor refresh must not invalidate or replace preparation");
        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(73, audio.commandTimeline().currentFrame());
        assertEquals(1, audio.commandTimeline().nextOrder());
        assertDeepSnapshotEquals(a.presentation(), audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void clearHistoryClearsProducerTokenButRetainsManagerTargetAndAcknowledgment() {
        AudioLogicalSnapshot a = prepareHeldFlagTarget();
        AudioLogicalSnapshot live = audio.captureLogicalSnapshot();
        var before = audio.releaseStateForTesting().producer();
        assertNotEquals(0L, before.preparedSelectedRestoreIdentity().token());
        audio.clearPcmHistory();
        var cleared = audio.releaseStateForTesting().producer();

        assertSame(a, audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
        assertEquals(0L, cleared.selectedRestoreIdentity().token());
        assertEquals(0L, cleared.preparedSelectedRestoreIdentity().token());
        assertTrue(cleared.reverseActive());
        assertEquals(before.reverseCursor(), cleared.reverseCursor());
        assertDeepSnapshotEquals(live, audio.captureLogicalSnapshot());
        int publications = audio.logicalRestorePublicationsForTesting();
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        assertEquals(0L, audio.releaseStateForTesting().producer()
                .preparedSelectedRestoreIdentity().token());
        assertTrue(audio.endReverseAudioPresentation());
        assertDeepSnapshotEquals(live.presentation(), audio.captureLogicalSnapshot().presentation());
        assertEquals(publications + 1, audio.logicalRestorePublicationsForTesting());
        assertEquals(a.commandTimelineFrame(), audio.commandTimeline().currentFrame());
        assertNull(audio.deferredReverseLogicalSnapshotForTesting());
    }

    @Test
    void negativeTimelineOrderPartiallyPublishesAfterSuccessfulProducerReturn() {
        AudioLogicalSnapshot a = prepareHeldFlagTarget();
        // The producer holds A; the manager will publish this otherwise valid ledger.
        AudioLogicalSnapshot invalid = new AudioLogicalSnapshot(false, 999, -1,
                a.commandEntryCount(), a.presentation(), Set.of("unpublished"),
                Set.of(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                        GameSound.JUMP, "unpublished", 0xAA)));
        AudioLogicalSnapshot live = audio.captureLogicalSnapshot();
        audio.restoreLogicalSnapshot(invalid);
        int before = audio.logicalRestorePublicationsForTesting();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                audio::endReverseAudioPresentation);
        assertEquals("nextOrder must be non-negative", failure.getMessage());
        var producer = audio.releaseStateForTesting().producer();
        assertFalse(producer.reverseActive());
        assertNull(producer.reverseCursor());
        assertEquals(0L, producer.preparedSelectedRestoreIdentity().token());
        assertEquals(before + 1, audio.logicalRestorePublicationsForTesting());
        AudioLogicalSnapshot partial = audio.captureLogicalSnapshot();
        assertDeepSnapshotEquals(a.presentation(), partial.presentation());
        assertFalse(partial.ringLeft());
        assertEquals(live.commandTimelineFrame(), partial.commandTimelineFrame());
        assertEquals(live.commandTimelineNextOrder(), partial.commandTimelineNextOrder());
        assertEquals(live.donorBindings(), partial.donorBindings());
        assertSame(invalid, audio.deferredReverseLogicalSnapshotForTesting());
        assertTrue(audio.isReverseAudioPresentationActive());
        assertTrue(AudioManagerTestDiagnostics.reversePreparationAcknowledged(audio));
    }

    @Test
    void eagerAndHeldRestoreSelectedOptionalForwardServiceAndSharePublicationCounter() {
        AudioRequestService service = mock(AudioRequestService.class);
        var selectedService = mock(AudioPresentationForwardService.Snapshot.class);
        var liveService = mock(AudioPresentationForwardService.Snapshot.class);
        AtomicReference<AudioPresentationForwardService.Snapshot> current =
                new AtomicReference<>(selectedService);
        when(service.snapshot()).thenAnswer(ignored -> current.get());
        doAnswer(call -> { current.set(call.getArgument(0)); return null; })
                .when(service).restore(any());
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                new AudioTestFixtures.StubSmpsLoader()) {
            @Override public AudioRequestService createAudioRequestService() { return service; }
        });
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        int before = audio.logicalRestorePublicationsForTesting();
        current.set(liveService);
        audio.restoreLogicalSnapshot(selected);
        verify(service).restore(selectedService);
        assertSame(selectedService, current.get());
        assertEquals(before + 1, audio.logicalRestorePublicationsForTesting());
        current.set(liveService);
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        assertSame(selected, audio.deferredReverseLogicalSnapshotForTesting());
        assertSame(liveService, current.get());
        assertSame(liveService, audio.captureLogicalSnapshot().forwardServiceSnapshot());
        assertEquals(before + 1, audio.logicalRestorePublicationsForTesting());
        assertTrue(audio.endReverseAudioPresentation());
        verify(service, times(2)).restore(same(selectedService));
        verify(service, times(2)).restore(any());
        assertSame(selectedService, current.get());
        assertSame(selectedService, audio.captureLogicalSnapshot().forwardServiceSnapshot());
        assertEquals(before + 2, audio.logicalRestorePublicationsForTesting());
        assertDeepSnapshotEquals(selected.presentation(), audio.captureLogicalSnapshot().presentation());
    }

    @Test
    void eagerPreTryServiceSnapshotFailureEscapesUnchangedWithoutRollback() {
        AudioRequestService service = mock(AudioRequestService.class);
        var state = mock(AudioPresentationForwardService.Snapshot.class);
        when(service.snapshot()).thenReturn(state);
        installOptionalService(service);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        var before = AudioManagerTestDiagnostics.producerFingerprint(audio);
        int publications = audio.logicalRestorePublicationsForTesting();
        RuntimeException failure = new IllegalStateException("pre-try snapshot identity");
        when(service.snapshot()).thenThrow(failure);
        clearInvocations(service);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> audio.restoreLogicalSnapshot(selected)));

        verify(service).snapshot();
        verify(service, never()).restore(any());
        assertProducerStateExactly(before, AudioManagerTestDiagnostics.presentationField(audio,
                        "shadowProducer", com.openggf.audio.presentation.AudioPresentationProducer.class)
                        .transactionFingerprint(),
                "pre-try failure never enters producer restore or rollback");
        assertEquals(publications, audio.logicalRestorePublicationsForTesting());
    }

    @Test
    void eagerOrdinaryRestoreFailureReturnsFalseWhilePublicVoidHidesIt() {
        AudioRequestService service = mock(AudioRequestService.class);
        var state = mock(AudioPresentationForwardService.Snapshot.class);
        when(service.snapshot()).thenReturn(state);
        installOptionalService(service);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.toggleMute(ChannelType.FM, 0);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        RuntimeException failure = new IllegalStateException("ordinary restore identity");
        doThrow(failure).doNothing().doThrow(failure).doNothing()
                .when(service).restore(state);
        int publications = audio.logicalRestorePublicationsForTesting();

        assertFalse(AudioManagerTestDiagnostics.restoreLogicalSnapshotNow(audio, selected, false));
        audio.restoreLogicalSnapshot(selected);

        assertDeepSnapshotEquals(before, audio.captureLogicalSnapshot());
        verify(service, times(4)).restore(state);
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(publications, audio.logicalRestorePublicationsForTesting());
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "cause", "suppressed", "rollback"})
    void eagerDiagnosticDiscoveryRunsAfterRollbackAndPreservesTraversalIdentity(String placement) {
        AudioRequestService service = mock(AudioRequestService.class);
        var selectedService = mock(AudioPresentationForwardService.Snapshot.class);
        var liveService = mock(AudioPresentationForwardService.Snapshot.class);
        when(service.snapshot()).thenReturn(selectedService);
        installOptionalService(service);
        audio.registerDonorSound(GameSound.JUMP, "selected", 0xA1);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.registerDonorSound(GameSound.JUMP, "live", 0xA2);
        audio.beginCommandTimelineFrame(23);
        audio.playSfx(GameSound.RING);
        audio.toggleMute(ChannelType.FM, 1);
        audio.presentFrame(PresentationMode.SILENT);
        when(service.snapshot()).thenReturn(liveService);
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        int publications = audio.logicalRestorePublicationsForTesting();
        var wanted = new AudioDiagnosticObserverException(new IllegalStateException("wanted"));
        var competitor = new AudioDiagnosticObserverException(new IllegalStateException("competitor"));
        RuntimeException primary;
        RuntimeException rollback;
        switch (placement) {
            case "node" -> {
                primary = wanted;
                primary.getCause().addSuppressed(competitor);
                primary.addSuppressed(competitor);
                rollback = new IllegalStateException("rollback");
            }
            case "cause" -> {
                RuntimeException causeBranch = new IllegalStateException("cause branch");
                causeBranch.addSuppressed(wanted);
                primary = new IllegalStateException("primary", causeBranch);
                primary.addSuppressed(competitor);
                rollback = new IllegalStateException("rollback");
            }
            case "suppressed" -> {
                primary = new IllegalStateException("primary");
                primary.addSuppressed(new IllegalStateException("first subtree", wanted));
                primary.addSuppressed(competitor);
                rollback = new IllegalStateException("rollback");
            }
            case "rollback" -> {
                primary = new IllegalStateException("primary");
                primary.addSuppressed(new IllegalStateException("earlier ordinary suppressed"));
                rollback = new IllegalStateException("rollback", wanted);
            }
            default -> throw new AssertionError(placement);
        }
        Throwable[] originalSuppressed = primary.getSuppressed();
        List<String> events = new ArrayList<>();
        doAnswer(call -> {
            events.add("selected-service");
            assertSame(selectedService, call.getArgument(0));
            assertDeepSnapshotEquals(selected.presentation(), audio.captureLogicalSnapshot().presentation());
            throw primary;
        }).doAnswer(call -> {
            events.add("rollback-service");
            assertSame(liveService, call.getArgument(0));
            // Logical ledger restoration must precede even a failing service rollback.
            assertDeepSnapshotEquals(before, audio.captureLogicalSnapshot());
            throw rollback;
        }).when(service).restore(any());

        assertSame(wanted, assertThrows(AudioDiagnosticObserverException.class,
                () -> audio.restoreLogicalSnapshot(selected)));

        assertEquals(List.of("selected-service", "rollback-service"), events);
        Throwable[] expected = java.util.Arrays.copyOf(originalSuppressed, originalSuppressed.length + 1);
        expected[originalSuppressed.length] = rollback;
        assertArrayEquals(expected, primary.getSuppressed(), "rollback appends its exact failure last");
        assertEquals(publications, audio.logicalRestorePublicationsForTesting(),
                "no inner restore succeeds and no callback resets the shared counter");
        assertDeepSnapshotEquals(before, audio.captureLogicalSnapshot());
    }

    @Test
    void eagerErrorAfterProducerRestoreEscapesWithoutRuntimeRollback() {
        AudioRequestService service = mock(AudioRequestService.class);
        var state = mock(AudioPresentationForwardService.Snapshot.class);
        when(service.snapshot()).thenReturn(state);
        installOptionalService(service);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.beginCommandTimelineFrame(31);
        audio.playSfx(GameSound.RING);
        audio.registerDonorSound(GameSound.JUMP, "live", 0xA2);
        audio.toggleMute(ChannelType.FM, 1);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        AssertionError failure = new AssertionError("service Error identity");
        doThrow(failure).when(service).restore(state);
        int publications = audio.logicalRestorePublicationsForTesting();

        assertSame(failure, assertThrows(AssertionError.class,
                () -> audio.restoreLogicalSnapshot(selected)));

        verify(service).restore(state);
        AudioLogicalSnapshot after = audio.captureLogicalSnapshot();
        assertDeepSnapshotEquals(selected.presentation(), after.presentation());
        assertEquals(before.ringLeft(), after.ringLeft());
        assertEquals(before.commandTimelineFrame(), after.commandTimelineFrame());
        assertEquals(before.commandTimelineNextOrder(), after.commandTimelineNextOrder());
        assertEquals(before.donorBindings(), after.donorBindings());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(publications, audio.logicalRestorePublicationsForTesting());
    }

    @Test
    void eagerRollbackReadsReplacementProducerFactoryAndServiceAfterRestoringLogicalLedger() {
        AudioRequestService original = mock(AudioRequestService.class);
        AudioRequestService middle = mock(AudioRequestService.class);
        AudioRequestService replacement = mock(AudioRequestService.class);
        var selectedService = mock(AudioPresentationForwardService.Snapshot.class);
        var liveService = mock(AudioPresentationForwardService.Snapshot.class);
        when(original.snapshot()).thenReturn(selectedService);
        when(middle.snapshot()).thenReturn(liveService);
        when(replacement.snapshot()).thenReturn(liveService);
        AtomicReference<AudioRequestService> currentService = new AtomicReference<>(original);
        for (AudioRequestService service : List.of(original, middle, replacement)) {
            when(service.ringLeft()).thenReturn(null);
        }
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(new AudioTestFixtures.StubSmpsLoader()) {
            @Override public AudioRequestService createAudioRequestService() { return currentService.get(); }
        });
        // Each real snapshot owns a different active PCM dependency. All factory
        // caches keep its shape, but sentinel sample content is local to that cache.
        java.util.function.IntFunction<byte[]> constantPcm = value -> {
            byte[] bytes = new byte[64];
            java.util.Arrays.fill(bytes, (byte) value);
            return bytes;
        };
        audio.submitShadowRawPcmForTesting(constantPcm.apply(0x90), 8_000);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        var selectedSample = (PresentationVoiceSnapshot.Sample) selected.presentation().voices().getFirst();
        assertEquals(List.of(selectedSample), selected.presentation().voices());
        assertEquals(Long.valueOf(selectedSample.voiceId()), selected.presentation().rawPcmVoiceId());
        assertEquals(0, selectedSample.sourcePositionQ32());
        assertEquals(1 << 14, selectedSample.gainQ16());
        audio.beginCommandTimelineFrame(51);
        audio.playSfx(GameSound.RING);
        audio.registerDonorSound(GameSound.JUMP, "before", 0xA3);
        audio.toggleMute(ChannelType.FM, 2);
        audio.submitShadowRawPcmForTesting(constantPcm.apply(0xA0), 8_000);
        audio.presentFrame(PresentationMode.SILENT);
        when(original.snapshot()).thenReturn(liveService);
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        var rollbackSample = (PresentationVoiceSnapshot.Sample) before.presentation().voices().getFirst();
        assertEquals(List.of(rollbackSample), before.presentation().voices());
        assertEquals(Long.valueOf(rollbackSample.voiceId()), before.presentation().rawPcmVoiceId());
        assertNotEquals(selectedSample.assetId(), rollbackSample.assetId());
        assertEquals(0, rollbackSample.sourcePositionQ32());
        assertEquals(1 << 14, rollbackSample.gainQ16());
        // Rollback restores the cursor, not the append-only command history.
        AudioLogicalSnapshot expectedRollback = new AudioLogicalSnapshot(
                before.ringLeft(), before.commandTimelineFrame(), before.commandTimelineNextOrder(),
                before.commandEntryCount() + 1, before.presentation(), before.donorGameIds(),
                before.donorBindings(), before.forwardServiceSnapshot());
        var oldProducer = AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class);
        var oldFactory = audio.shadowFactoryForTesting();
        AtomicReference<AudioPresentationSourceFactory> forwardFactory = new AtomicReference<>();
        AtomicReference<AudioPresentationSourceFactory> rollbackFactory = new AtomicReference<>();
        AtomicReference<DecodedPcm> forwardSentinel = new AtomicReference<>();
        AtomicReference<DecodedPcm> rollbackSentinel = new AtomicReference<>();
        // Read the genuinely reconstructed voice without advancing the existing
        // logical assertions. Expected PCM never comes from the current factory.
        java.util.function.Function<SampleBackedVoice, long[]> firstStereoSample = voice -> {
            var snapshot = (PresentationVoiceSnapshot.Sample) voice.snapshot();
            long[] mixed = new long[2];
            try {
                voice.mixInto(mixed, 1);
            } finally {
                voice.restore(snapshot);
            }
            assertEquals(snapshot, voice.snapshot());
            return mixed;
        };
        java.util.function.Supplier<SampleBackedVoice> restoredRawVoice = () -> {
            var registry = AudioManagerTestDiagnostics.presentationField(
                    audio, "shadowRegistry", AudioVoiceRegistry.class);
            assertEquals(1, registry.orderedVoiceCount());
            return (SampleBackedVoice) registry.orderedVoiceAt(0);
        };
        // Disconnected stale-F0 control: real recreation succeeds, but its PCM
        // is 1024 rather than the forward F1 sentinel's independently fixed 4096.
        assertArrayEquals(new long[] {1024, 1024}, firstStereoSample.apply(
                (SampleBackedVoice) oldFactory.recreateVoice(
                        new AudioPresentationCommand.SampleVoiceDescriptor(selectedSample))));
        RuntimeException primary = new IllegalStateException("replacement restore primary");
        List<String> events = new ArrayList<>();
        when(original.snapshot()).thenAnswer(call -> {
            events.add("original-pre-try-snapshot");
            currentService.set(middle);
            audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
            audio.presentFrame(PresentationMode.SILENT);
            assertNotEquals(oldProducer, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class));
            assertNotEquals(oldFactory, audio.shadowFactoryForTesting());
            var actualF1 = audio.shadowFactoryForTesting();
            forwardFactory.set(actualF1);
            forwardSentinel.set(actualF1.registerUnsigned8Mono(
                    selectedSample.assetId(), constantPcm.apply(0xC0), 8_000));
            // If rollback reuses F1, it can resolve the dependency successfully
            // but must expose this different, factory-local negative sentinel.
            actualF1.registerUnsigned8Mono(
                    rollbackSample.assetId(), constantPcm.apply(0x60), 8_000);
            assertSame(forwardSentinel.get(), actualF1.resolvePcm(selectedSample.assetId()));
            assertEquals(16384, forwardSentinel.get().sample(0, 0));
            return liveService;
        });
        doAnswer(call -> {
            events.add("middle-restore");
            assertSame(selectedService, call.getArgument(0));
            assertDeepSnapshotEquals(selected.presentation(), audio.captureLogicalSnapshot().presentation());
            assertSame(forwardFactory.get(), audio.shadowFactoryForTesting());
            assertArrayEquals(new long[] {4096, 4096}, firstStereoSample.apply(restoredRawVoice.get()),
                    "forward recreation must resolve F1 PCM, not original F0's 1024");
            currentService.set(replacement);
            audio.setBackend(new AudioTestFixtures.RecordingAudioBackend());
            audio.beginCommandTimelineFrame(99);
            audio.registerDonorSound(GameSound.JUMP, "callback", 0xA4);
            audio.resetRingSound();
            audio.toggleMute(ChannelType.PSG, 0);
            audio.presentFrame(PresentationMode.SILENT);
            var actualF2 = audio.shadowFactoryForTesting();
            rollbackFactory.set(actualF2);
            assertNotEquals(oldFactory, actualF2);
            assertNotEquals(forwardFactory.get(), actualF2);
            rollbackSentinel.set(actualF2.registerUnsigned8Mono(
                    rollbackSample.assetId(), constantPcm.apply(0x50), 8_000));
            assertSame(rollbackSentinel.get(), actualF2.resolvePcm(rollbackSample.assetId()));
            assertEquals(-12288, rollbackSentinel.get().sample(0, 0));
            // These genuine stale-receiver recreations have distinct outputs;
            // neither can satisfy the rollback F2 oracle (-3072 per channel).
            assertArrayEquals(new long[] {2048, 2048}, firstStereoSample.apply(
                    (SampleBackedVoice) oldFactory.recreateVoice(
                            new AudioPresentationCommand.SampleVoiceDescriptor(rollbackSample))));
            assertArrayEquals(new long[] {-2048, -2048}, firstStereoSample.apply(
                    (SampleBackedVoice) forwardFactory.get().recreateVoice(
                            new AudioPresentationCommand.SampleVoiceDescriptor(rollbackSample))));
            throw primary;
        }).when(middle).restore(any());
        doAnswer(call -> {
            events.add("replacement-rollback");
            assertSame(liveService, call.getArgument(0), "snapshot was captured before try on original service");
            assertDeepSnapshotEquals(expectedRollback, audio.captureLogicalSnapshot());
            assertSame(rollbackFactory.get(), audio.shadowFactoryForTesting());
            assertArrayEquals(new long[] {-3072, -3072}, firstStereoSample.apply(restoredRawVoice.get()),
                    "rollback recreation must resolve F2 PCM, not F0's 2048 or F1's -2048");
            return null;
        }).when(replacement).restore(any());
        int publications = audio.logicalRestorePublicationsForTesting();

        assertFalse(AudioManagerTestDiagnostics.restoreLogicalSnapshotNow(audio, selected, false));

        assertEquals(List.of("original-pre-try-snapshot", "middle-restore", "replacement-rollback"), events);
        assertNotEquals(oldProducer, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer", Object.class));
        assertNotEquals(oldFactory, audio.shadowFactoryForTesting());
        verify(original, never()).restore(any());
        verify(middle).restore(selectedService);
        verify(replacement).restore(liveService);
        assertDeepSnapshotEquals(expectedRollback, audio.captureLogicalSnapshot());
        assertEquals(publications, audio.logicalRestorePublicationsForTesting());
        assertEquals(new AudioCommand.ResetRingAlternation(true),
                audio.commandTimeline().entryAt(before.commandEntryCount()).command(),
                "the callback's real append survives restoration of the earlier cursor");
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test
    void eagerLateCursorRejectionRestoresLogicalStateButLosesPopulatedPcmAndReverseHistory()
            throws Exception {
        audio.registerDonorSound(GameSound.JUMP, "selected", 0xA1);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();
        audio.registerDonorSound(GameSound.JUMP, "live", 0xA2);
        audio.beginCommandTimelineFrame(61);
        audio.playSfx(GameSound.RING);
        byte[] pcm = new byte[12_000];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (i * 17);
        audio.submitShadowRawPcmForTesting(pcm, 8_000);
        audio.setRewindHistoryArmed(true);
        try (LiveCaptureAudioHandle capture = audio.attachShadowCaptureForTesting(60)) {
            short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
            for (int i = 0; i < 3; i++) {
                audio.presentFrame(PresentationMode.FORWARD);
                int frames = capture.drainPresentationFrame(packet);
                assertTrue(frames > 0);
                assertTrue(java.util.stream.IntStream.range(0, frames * 2).anyMatch(j -> packet[j] != 0));
            }
            audio.beginReverseAudioPresentation();
            audio.presentFrame(PresentationMode.REVERSE);
            int reverseFrames = capture.drainPresentationFrame(packet);
            assertTrue(java.util.stream.IntStream.range(0, reverseFrames * 2).anyMatch(j -> packet[j] != 0));
            AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
            var priorProducer = AudioManagerTestDiagnostics.producerFingerprint(audio);
            assertTrue(priorProducer.history().storedFrames() > reverseFrames);
            assertTrue(priorProducer.history().readablePcm().samples().length > 0);
            assertTrue(priorProducer.reverseActive());
            assertNotNull(priorProducer.reverseCursor());
            assertTrue(priorProducer.hasLastReverseFrame());
            int publications = audio.logicalRestorePublicationsForTesting();
            AudioLogicalSnapshot invalid = new AudioLogicalSnapshot(true, 999, -1, 0,
                    selected.presentation(), selected.donorGameIds(), selected.donorBindings());

            assertFalse(AudioManagerTestDiagnostics.restoreLogicalSnapshotNow(audio, invalid, false));

            assertDeepSnapshotEquals(before, audio.captureLogicalSnapshot());
            var after = AudioManagerTestDiagnostics.producerFingerprint(audio);
            assertEquals(0, after.history().storedFrames());
            assertEquals(0, after.history().readablePcm().samples().length);
            assertTrue(after.history().epoch() > priorProducer.history().epoch());
            assertNull(after.reverseCursor());
            assertFalse(after.reverseActive());
            assertFalse(after.hasLastReverseFrame());
            assertEquals(0, after.releaseCrossfadeRemaining());
            assertEquals(publications, audio.logicalRestorePublicationsForTesting());
            audio.presentFrame(PresentationMode.REVERSE);
            int emptyFrames = capture.drainPresentationFrame(packet);
            assertTrue(emptyFrames > 0);
            assertArrayEquals(new short[emptyFrames * 2], java.util.Arrays.copyOf(packet, emptyFrames * 2),
                    "logical rollback cannot recreate consumed/cleared reverse PCM");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true", ",true"})
    void captureSamplesCopiedBindingsThenRingCallbackThenCursorProducerAndCurrentService(
            Boolean serviceRing, boolean expectedRing) {
        AudioRequestService original = mock(AudioRequestService.class);
        AudioRequestService replacement = mock(AudioRequestService.class);
        var finalServiceSnapshot = mock(AudioPresentationForwardService.Snapshot.class);
        installOptionalService(original);
        audio.registerDonorLoader("before-route", new AudioTestFixtures.StubSmpsLoader(), AudioTestFixtures.EMPTY_DAC);
        audio.registerDonorSound(GameSound.JUMP, "before-route", 0xA1);
        audio.beginCommandTimelineFrame(10);
        audio.resetRingSound();
        AudioLogicalSnapshot before = audio.captureLogicalSnapshot();
        clearInvocations(original);
        List<String> events = new ArrayList<>();
        AtomicReference<AudioPresentationSnapshot> sampledPresentation = new AtomicReference<>();
        when(original.ringLeft()).thenAnswer(call -> {
            events.add("original-ring");
            installOptionalService(replacement);
            audio.registerDonorLoader("callback-route", new AudioTestFixtures.StubSmpsLoader(), AudioTestFixtures.EMPTY_DAC);
            audio.registerDonorSound(GameSound.JUMP, "callback-route", 0xA2);
            audio.beginCommandTimelineFrame(91);
            audio.resetRingSound();
            audio.toggleMute(ChannelType.FM, 0);
            audio.presentFrame(PresentationMode.SILENT);
            sampledPresentation.set(AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer",
                    com.openggf.audio.presentation.AudioPresentationProducer.class).snapshot());
            events.add("ring-callback-finished");
            return serviceRing;
        });
        when(replacement.snapshot()).thenAnswer(call -> {
            events.add("replacement-snapshot");
            assertEquals(1, sampledPresentation.get().fmMuteMask());
            // A later callback proves producer and cursor were already sampled.
            audio.beginCommandTimelineFrame(92);
            audio.toggleMute(ChannelType.FM, 1);
            audio.presentFrame(PresentationMode.SILENT);
            return finalServiceSnapshot;
        });

        AudioLogicalSnapshot captured = audio.captureLogicalSnapshot();

        assertEquals(List.of("original-ring", "ring-callback-finished", "replacement-snapshot"), events);
        assertEquals(before.donorBindings(), captured.donorBindings());
        assertEquals(before.donorGameIds(), captured.donorGameIds());
        assertEquals(expectedRing, captured.ringLeft(), "nullable service observation falls back to raw true");
        assertEquals(91, captured.commandTimelineFrame());
        assertEquals(1, captured.commandTimelineNextOrder());
        assertEquals(before.commandEntryCount() + 1, captured.commandEntryCount());
        assertDeepSnapshotEquals(sampledPresentation.get(), captured.presentation());
        assertEquals(1, captured.presentation().fmMuteMask());
        assertSame(finalServiceSnapshot, captured.forwardServiceSnapshot());
        verify(original, never()).snapshot();
        verify(replacement).snapshot();
        assertEquals(92, audio.commandTimeline().currentFrame());
        assertEquals(3, AudioManagerTestDiagnostics.presentationField(audio, "shadowProducer",
                com.openggf.audio.presentation.AudioPresentationProducer.class).snapshot().fmMuteMask());
        doReturn(finalServiceSnapshot).when(replacement).snapshot();
        assertEquals(Set.of(new AudioLogicalSnapshot.DonorSfxBindingSnapshot(
                GameSound.JUMP, "callback-route", 0xA2)),
                audio.captureLogicalSnapshot().donorBindings());
    }

    private void installOptionalService(AudioRequestService service) {
        when(service.ringLeft()).thenReturn(null);
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(new AudioTestFixtures.StubSmpsLoader()) {
            @Override public AudioRequestService createAudioRequestService() { return service; }
        });
    }

    private AudioLogicalSnapshot prepareHeldFlagTarget() {
        audio.beginCommandTimelineFrame(10);
        audio.toggleMute(ChannelType.FM, 0);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot a = audio.captureLogicalSnapshot();
        audio.beginCommandTimelineFrame(20);
        audio.toggleMute(ChannelType.FM, 1);
        audio.presentFrame(PresentationMode.SILENT);
        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(a);
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        return a;
    }

    @Test
    void rewindReleaseRestoresAudibleFmAndPsgMasksAndNextToggleState() {
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot selected = audio.captureLogicalSnapshot();

        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.toggleSolo(ChannelType.FM, 4);
        audio.toggleMute(ChannelType.PSG, 3);
        audio.presentFrame(PresentationMode.SILENT);

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(selected);
        audio.endReverseAudioPresentation();

        AudioPresentationSnapshot restored =
                audio.captureLogicalSnapshot().presentation();
        assertEquals(1 << 2, restored.fmMuteMask());
        assertEquals(0, restored.fmSoloMask());
        assertEquals(0, restored.psgMuteMask());
        assertEquals(1 << 1, restored.psgSoloMask());

        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.presentFrame(PresentationMode.SILENT);
        assertFalse(audio.isMuted(ChannelType.FM, 2));
        assertFalse(audio.isSoloed(ChannelType.PSG, 1));
    }

    @Test
    void deferredTargetReplayAdvancesPresentationLogicalState() {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        AudioTestFixtures.StubSmpsData music =
                new AudioTestFixtures.StubSmpsData("target-music");
        music.setId(0x81);
        AudioTestFixtures.StubSmpsData disturbedMusic =
                new AudioTestFixtures.StubSmpsData("disturbed-music");
        disturbedMusic.setId(0x82);
        AudioTestFixtures.StubSmpsData sfx =
                new AudioTestFixtures.StubSmpsData("target-sfx");
        sfx.setId(0xA0);
        loader.musicResults.put(0x81, music);
        loader.musicResults.put(0x82, disturbedMusic);
        loader.sfxResults.put(0xA0, sfx);
        com.openggf.audio.smps.SmpsSequencerConfig config =
                new com.openggf.audio.smps.SmpsSequencerConfig.Builder()
                        .build();
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return config;
            }
        });
        audio.setRom(new Rom());
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        audio.beginCommandTimelineFrame(10);
        keyframes.capture(10, audio);

        audio.beginCommandTimelineFrame(11);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        audio.resetRingSound();
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot expected = audio.captureLogicalSnapshot();

        audio.beginCommandTimelineFrame(12);
        audio.setSpeedShoes(false);
        audio.setSpeedMultiplier(1);
        audio.playMusic(0x82);
        audio.playSfx(GameSound.RING);
        audio.presentFrame(PresentationMode.SILENT);
        AudioLogicalSnapshot disturbed = audio.captureLogicalSnapshot();

        audio.beginReverseAudioPresentation();
        assertEquals(5, keyframes.replayToLogicalState(audio, 11));
        assertPresentationLogicalEquals(
                disturbed.presentation(),
                audio.captureLogicalSnapshot().presentation());
        com.openggf.audio.rewind.SmpsDriverSnapshot stagedLogical = audio
                .deferredReverseLogicalSnapshotForTesting()
                .presentation().smpsLogical();
        assertNotNull(stagedLogical);
        assertEquals(List.of(
                        com.openggf.audio.rewind.SmpsSourceDescriptor.Kind
                                .BASE_MUSIC,
                        com.openggf.audio.rewind.SmpsSourceDescriptor.Kind
                                .BASE_SFX_ID),
                stagedLogical.sequencers().stream()
                        .map(entry -> entry.source().kind()).toList());
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        audio.endReverseAudioPresentation();

        AudioLogicalSnapshot actual = audio.captureLogicalSnapshot();
        assertEquals(expected.presentation().activeMusic(),
                actual.presentation().activeMusic());
        assertTrue(actual.presentation().speedShoesEnabled());
        assertEquals(3, actual.presentation().speedMultiplier());
        assertEquals(0, actual.presentation().voices().size(),
                "session-owned SMPS state is not a presentation voice");
        assertTrue(actual.presentation().smpsLogical().sequencers().stream()
                .noneMatch(
                        com.openggf.audio.rewind.SmpsDriverSnapshot
                                .SequencerEntry::sfx),
                "reverse release removes replayed transient SFX");
        assertEquals(expected.ringLeft(), actual.ringLeft());
    }

    @Test
    void heldReplayUsesPresentationStateWhileTheSourceBackendStaysIdle() {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x80, 0x81, 0x82}) {
            AudioTestFixtures.StubSmpsData music =
                    new AudioTestFixtures.StubSmpsData("music-" + id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        AudioTestFixtures.StubSmpsData sfx =
                new AudioTestFixtures.StubSmpsData("sfx-a0");
        sfx.setId(0xA0);
        loader.sfxResults.put(0xA0, sfx);
        audio.setAudioProfile(configuredProfile(loader));
        audio.setRom(new Rom());
        AudioTestFixtures.RecordingAudioBackend liveBackend =
                new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(liveBackend);
        liveBackend.setAudioProfile(audio.getAudioProfile());
        liveBackend.clear();

        audio.beginCommandTimelineFrame(1);
        audio.playMusic(0x80);
        audio.presentFrame(PresentationMode.SILENT);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        keyframes.capture(1, audio);

        audio.beginCommandTimelineFrame(2);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.presentFrame(PresentationMode.SILENT);

        audio.beginCommandTimelineFrame(3);
        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.SILENT);
        audio.beginReverseAudioPresentation();
        assertEquals(4, keyframes.replayToLogicalState(audio, 2));
        assertEquals(0, liveBackend.totalCalls(),
                "held replay must never reach the source-construction backend");

        assertTrue(audio.commitDeferredReverseLogicalRestore());
        assertEquals(0, liveBackend.totalCalls());

        audio.endReverseAudioPresentation();
        assertEquals(0, liveBackend.totalCalls());
        AudioPresentationSnapshot released =
                audio.captureLogicalSnapshot().presentation();
        assertEquals(AudioSourceDescriptor.baseMusic(0x81),
                released.activeMusic().sourceDescriptor());
        assertTrue(released.voices().isEmpty(),
                "session SMPS state must not use ordered voice carriers");
        assertTrue(released.speedShoesEnabled());
        assertEquals(3, released.speedMultiplier());
    }

    @Test
    void reverseReleasePublicationFailureLeavesReleaseExactlyRetryable()
            throws Exception {
        arrangeSelectedReverseReleaseTarget();
        // The target was already prepared before the attempt, so the failing
        // attempt owns nothing: it must retain the pre-existing prepared
        // token rather than discarding a preparation it did not make.
        assertTrue(audio.commitDeferredReverseLogicalRestore());
        AudioManager.ReleaseStateForTesting before =
                audio.releaseStateForTesting();
        assertNotEquals(0L,
                before.producer().preparedSelectedRestoreIdentity().token(),
                "the target must already be prepared before the attempt");
        assertNotEquals(0L,
                before.producer().selectedRestoreIdentity().token(),
                "the producer must already hold the selected target");

        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        assertFalse(audio.endReverseAudioPresentation());

        assertReleaseStateExactly(before, audio.releaseStateForTesting(),
                "failed release publication must preserve identities, "
                        + "registry, history, cursor, selection and crossfade");
        assertNotNull(audio.deferredReverseLogicalSnapshotForTesting(),
                "selected target must remain available for retry");

        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(AudioSourceDescriptor.baseMusic(0x80),
                audio.captureLogicalSnapshot().presentation()
                        .activeMusic().sourceDescriptor());
        assertEquals(null,
                audio.deferredReverseLogicalSnapshotForTesting());
    }

    /**
     * The sibling case where the failing attempt is the one that prepared the
     * target. Here the release really does mutate producer state before it
     * fails — {@code prepareRestoreSelection} recreates every voice in the
     * selected snapshot, discards any prior prepared token, and publishes the
     * selection — so the rollback has actual work to undo and the exact-state
     * comparison can genuinely fail if {@code discardPreparedRestoreSelection}
     * stops running or stops clearing the whole selection.
     */
    @Test
    void reverseReleaseFailureAfterPreparingDuringTheAttemptRollsBackExactly()
            throws Exception {
        arrangeSelectedReverseReleaseTarget();
        AudioManager.ReleaseStateForTesting before =
                audio.releaseStateForTesting();
        assertEquals(0L,
                before.producer().selectedRestoreIdentity().token(),
                "the producer must hold no selection until the attempt "
                        + "prepares one");
        assertEquals(0L,
                before.producer().preparedSelectedRestoreIdentity().token(),
                "the producer must hold no prepared token until the attempt "
                        + "prepares one");

        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        assertFalse(audio.endReverseAudioPresentation());

        assertReleaseStateExactly(before, audio.releaseStateForTesting(),
                "an attempt that prepared during the failure must discard "
                        + "exactly what it prepared and nothing else");
        assertNotNull(audio.deferredReverseLogicalSnapshotForTesting(),
                "selected target must remain available for retry");

        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(AudioSourceDescriptor.baseMusic(0x80),
                audio.captureLogicalSnapshot().presentation()
                        .activeMusic().sourceDescriptor());
        assertEquals(null,
                audio.deferredReverseLogicalSnapshotForTesting());
    }

    /**
     * The other side of the release's one irreversible step. Once
     * {@code shadowProducer.endReverse(...)} has returned, the reverse cursor
     * is consumed and the prepared restore is committed; no retry can recreate
     * that session. A failure in the manager-local ledger publication that
     * follows must therefore complete the release rather than report it
     * retryable — otherwise the retry re-prepares a selection that
     * {@code endReverse} silently refuses to commit (it early-returns on an
     * inactive reverse), leaking every voice the preparation recreated and
     * leaving the ledger unpublished forever.
     */
    @Test
    void reverseReleaseFailureAfterTheProducerCommitCompletesTheRelease()
            throws Exception {
        arrangeSelectedReverseReleaseTarget();
        AudioLogicalSnapshot selected =
                audio.deferredReverseLogicalSnapshotForTesting();
        assertNotNull(selected);
        int publicationsBefore = AudioManagerTestDiagnostics
                .logicalRestorePublications(audio);

        AudioManagerTestDiagnostics.failNextReverseRelease(audio,
                AudioManager.ReverseReleaseFailurePoint
                        .AFTER_PRODUCER_COMMIT);
        assertTrue(audio.endReverseAudioPresentation(),
                "a failure after the irreversible producer commit must not be "
                        + "reported as a retryable release failure");

        assertFalse(audio.isReverseAudioPresentationActive());
        assertEquals(null, audio.deferredReverseLogicalSnapshotForTesting());
        AudioManager.ReleaseStateForTesting after =
                audio.releaseStateForTesting();
        assertFalse(after.producer().reverseActive(),
                "the producer reverse session is gone");
        assertEquals(0L, after.producer().selectedRestoreIdentity().token(),
                "no selection may be left behind for a retry to re-prepare");
        assertEquals(0L,
                after.producer().preparedSelectedRestoreIdentity().token(),
                "no prepared restore may leak its recreated voices");

        // The ledger prepared before the commit is published in full.
        assertEquals(publicationsBefore + 1, AudioManagerTestDiagnostics
                .logicalRestorePublications(audio));
        AudioLogicalSnapshot published = audio.captureLogicalSnapshot();
        assertEquals(AudioSourceDescriptor.baseMusic(0x80),
                published.presentation().activeMusic().sourceDescriptor());
        assertEquals(selected.ringLeft(), published.ringLeft());
        assertEquals(selected.commandTimelineFrame(),
                published.commandTimelineFrame());
        assertEquals(selected.commandTimelineNextOrder(),
                published.commandTimelineNextOrder());
        assertEquals(selected.donorBindings(), published.donorBindings());

        // The release is complete, so a later call is an inert no-op rather
        // than a second publication.
        assertTrue(audio.endReverseAudioPresentation());
        assertEquals(publicationsBefore + 1, AudioManagerTestDiagnostics
                .logicalRestorePublications(audio));
    }

    /**
     * Drives a held rewind up to the point where a pre-boundary logical target
     * is selected but not yet prepared, leaving the caller to decide whether
     * the release attempt or an earlier explicit commit owns the preparation.
     */
    private void arrangeSelectedReverseReleaseTarget() throws Exception {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x80, 0x81}) {
            AbstractSmpsData music = persistentRestTrack(id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        AbstractSmpsData sfx = persistentS3kSfx(0xA0);
        sfx.setId(0xA0);
        loader.sfxResults.put(0xA0, sfx);
        for (int id : new int[] {0x82, 0x83}) {
            AbstractSmpsData donorOverride = persistentS3kMusic(id);
            donorOverride.setId(id);
            loader.musicResults.put(id, donorOverride);
        }
        audio.setAudioProfile(configuredS3kProfile(loader));
        audio.setRom(new Rom());
        audio.registerDonorLoader(
                "s3k", loader, loader.loadDacData(),
                Sonic3kSmpsSequencerConfig.CONFIG);
        // A real AbstractSmpsAudioBackend so the release-state comparison can
        // still prove the source-construction backend is untouched.
        audio.setBackend(new HeadlessSmpsAudioBackend(
                SonicConfigurationService.createStandalone(),
                PerformanceProfiler.getInstance(), audio));

        audio.playMusic(0x80);
        audio.setRewindHistoryArmed(true);
        for (int frame = 0; frame < 4; frame++) {
            audio.beginCommandTimelineFrame(frame);
            audio.presentFrame(PresentationMode.FORWARD);
        }
        audio.beginCommandTimelineFrame(4);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        keyframes.capture(4, audio);
        audio.beginCommandTimelineFrame(5);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        audio.playDonorMusic("s3k", 0x82);
        audio.playDonorMusic("s3k", 0x83);
        audio.restoreMusic();
        audio.setSpeedShoes(true);
        audio.setSpeedMultiplier(3);
        audio.toggleMute(ChannelType.FM, 2);
        audio.toggleSolo(ChannelType.PSG, 1);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(23);
        audio.presentFrame(PresentationMode.FORWARD);
        AudioManager.ReleaseStateForTesting disturbedState =
                audio.releaseStateForTesting();
        assertEquals(0, audio.presentationCoordFlagHandlersForTesting()
                .state().spindashRevCounter());
        assertEquals(1 << 2,
                disturbedState.logical().presentation().fmMuteMask());
        assertEquals(1 << 1,
                disturbedState.logical().presentation().psgSoloMask());

        audio.beginReverseAudioPresentation();
        audio.presentFrame(PresentationMode.REVERSE);
        keyframes.replayToLogicalState(audio, 4);
    }

    @Test
    void snapshotRestoresTheSolePresentationCoordFlagCounter() {
        audio.captureLogicalSnapshot();
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);

        AudioLogicalSnapshot snapshot = audio.captureLogicalSnapshot();
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(55);
        audio.restoreLogicalSnapshot(snapshot);

        assertEquals(7, audio.presentationCoordFlagHandlersForTesting().state()
                .spindashRevCounter());
    }

    @Test
    void controlledReferenceRendererMatchesExactClockCursorsForOneHundredTwentyFrames() {
        final int sampleRate = 11;
        final int frameRate = 3;
        DecodedPcm pcm = ramp("parity-loop", sampleRate, 127);
        AudioPresentationDependencyResolver resolver = resolver(pcm);
        AudioVoiceRegistry reference = registry(resolver);
        reference.apply(new AudioPresentationCommand.ReplaceMusic(
                AudioPresentationCommand.MusicVoiceEntry.fromVoice(
                        0x81, AudioSourceDescriptor.baseMusic(0x81),
                        SampleBackedVoice.loopingMusic(
                                1, pcm, sampleRate, 1.0f))));
        AudioPresentationMixer referenceMixer = new AudioPresentationMixer(4);
        AudioFrameClock referenceClock =
                new AudioFrameClock(sampleRate, frameRate);

        for (int frame = 0; frame < 120; frame++) {
            referenceMixer.mix(reference,
                    referenceClock.samplesForNextFrame());
        }
        AudioPresentationSnapshot selected = reference.snapshot();
        List<short[]> expected = new ArrayList<>();
        for (int frame = 0; frame < 10; frame++) {
            int frames = referenceClock.samplesForNextFrame();
            expected.add(java.util.Arrays.copyOf(
                    referenceMixer.mix(reference, frames), frames * 2));
        }

        AudioVoiceRegistry restored = registry(resolver);
        restored.restore(selected, resolver);
        AudioPresentationMixer restoredMixer = new AudioPresentationMixer(4);
        AudioFrameClock restoredClock =
                new AudioFrameClock(sampleRate, frameRate);
        for (int frame = 0; frame < 120; frame++) {
            restoredClock.samplesForNextFrame();
        }
        for (int frame = 0; frame < 10; frame++) {
            int frames = restoredClock.samplesForNextFrame();
            assertArrayEquals(expected.get(frame), java.util.Arrays.copyOf(
                    restoredMixer.mix(restored, frames), frames * 2));
        }
        assertEquals(reference.snapshot(), restored.snapshot(),
                "exact packet sizes and durable cursors must remain equal");
    }

    @Test
    void managerProductionProducerKeepsFractionalBoundaryForOneHundredTwentyFrames()
            throws Exception {
        AudioTestFixtures.StubSmpsLoader loader =
                new AudioTestFixtures.StubSmpsLoader();
        for (int id : new int[] {0x81, 0x82}) {
            AbstractSmpsData music = persistentS3kMusic(id);
            music.setId(id);
            loader.musicResults.put(id, music);
        }
        AbstractSmpsData sfx = persistentS3kSfx(0xA0);
        sfx.setId(0xA0);
        loader.sfxResults.put(0xA0, sfx);
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return Sonic3kSmpsSequencerConfig.CONFIG;
            }
            @Override public String presentationGameId() {
                return "s3k";
            }
            @Override public void configurePresentationCoordFlagHandlers(
                    SmpsCoordFlagHandlerOwner owner) {
                owner.register("s3k", Sonic3kCoordFlagHandler::new);
            }
            @Override
            public int getInvincibilityMusicId() {
                return 0x82;
            }
        });
        audio.setRom(new Rom());
        audio.registerDonorLoader(
                "s3k", loader, loader.loadDacData(),
                Sonic3kSmpsSequencerConfig.CONFIG);
        HeadlessSmpsAudioBackend realBackend =
                new HeadlessSmpsAudioBackend(
                        SonicConfigurationService.createStandalone(),
                        PerformanceProfiler.getInstance(),
                        44_101, audio);
        audio.setBackend(realBackend);
        assertTrue(audio.getBackend() instanceof HeadlessSmpsAudioBackend);
        audio.presentationCoordFlagHandlersForTesting().state()
                .setSpindashRevCounter(7);
        audio.playMusic(0x81);
        audio.playSfx(0xA0);
        byte[] rawPcm = new byte[20_000];
        for (int index = 0; index < rawPcm.length; index++) {
            rawPcm[index] = (byte) (index * 17);
        }
        audio.submitShadowRawPcmForTesting(rawPcm, 8_000);
        try (LiveCaptureAudioHandle capture =
                     audio.attachShadowCaptureForTesting(60)) {
            short[] packet =
                    new short[capture.maxStereoFramesPerPacket() * 2];
            int packets735 = 0;
            int packets736 = 0;
            long producerFrames = audio.releaseStateForTesting()
                    .producer().clock().totalSamplesProduced();
            for (int frame = 0; frame < 120; frame++) {
                audio.beginCommandTimelineFrame(frame);
                if (frame == 5) {
                    audio.setSpeedShoes(true);
                } else if (frame == 11) {
                    audio.playDonorMusic("s3k", 0x82);
                } else if (frame == 17) {
                    audio.setSpeedMultiplier(3);
                } else if (frame == 31) {
                    audio.toggleMute(ChannelType.FM, 2);
                } else if (frame == 47) {
                    audio.toggleSolo(ChannelType.PSG, 1);
                } else if (frame == 63) {
                    assertTrue(audio.captureLogicalSnapshot().ringLeft());
                    audio.playSfx(GameSound.RING);
                } else if (frame == 71) {
                    // Re-issued after the last music change: replacing the
                    // foreground stops the SMPS SFX riding on the old music
                    // driver, as zStopAllSound does in the ROM.
                    audio.playSfx(0xA0);
                }
                audio.presentFrame(PresentationMode.FORWARD);
                long producerTotal = audio.releaseStateForTesting()
                        .producer().clock().totalSamplesProduced();
                int packetFrames = Math.toIntExact(
                        producerTotal - producerFrames);
                producerFrames = producerTotal;
                if (packetFrames == 735) {
                    packets735++;
                } else if (packetFrames == 736) {
                    packets736++;
                }
                assertEquals(packetFrames,
                        capture.drainPresentationFrame(packet),
                        "capture must neither pad nor truncate producer PCM");
                if (frame == 11) {
                    assertEquals(AudioSourceDescriptor.donorMusic(
                                    "s3k", 0x82),
                            audio.captureLogicalSnapshot().presentation()
                                    .activeMusic().sourceDescriptor(),
                            "S3K donor music must survive its first "
                                    + "production packet");
                }
            }

            AudioLogicalSnapshot boundary = audio.captureLogicalSnapshot();
            assertEquals(120,
                    audio.shadowParitySnapshot().presentedFrames());
            assertEquals(88_202, capture.totalStereoFrames());
            assertEquals(88_202,
                    audio.releaseStateForTesting().producer().clock()
                            .totalSamplesProduced());
            assertEquals(0, capture.clockSnapshot().remainder());
            assertEquals(0, audio.releaseStateForTesting().producer()
                    .clock().remainder());
            assertEquals(118, packets735);
            assertEquals(2, packets736);
            assertFalse(boundary.ringLeft(),
                    "real ring command must alternate left to right");
            assertFalse(boundary.presentation().ringLeft());
            assertEquals(0,
                    boundary.presentation().coordFlagRuntimeState()
                            .spindashRevCounter());
            assertFalse(boundary.presentation().speedShoesEnabled(),
                    "the frame-11 ordinary S3K donor load clears the old speed state");
            assertEquals(3, boundary.presentation().speedMultiplier());
            assertEquals(1 << 2, boundary.presentation().fmMuteMask());
            assertEquals(1 << 1, boundary.presentation().psgSoloMask());
            assertNotNull(boundary.presentation().activeMusic());
            assertEquals(AudioSourceDescriptor.donorMusic(
                            "s3k", 0x82),
                    boundary.presentation().activeMusic()
                            .sourceDescriptor());
            // Donor music replaces the foreground rather than saving it: only
            // the 1-up jingle owns the driver's save slot, and it is never a
            // donor track. A non-empty save slot round-trips through a snapshot
            // in TestAudioVoiceRegistry.
            assertTrue(boundary.presentation().overrideStack().isEmpty());
            assertNotNull(boundary.presentation().rawPcmVoiceId());
            assertNotNull(boundary.presentation().smpsSession());
            assertNotNull(boundary.presentation().smpsLogical());
            assertTrue(boundary.presentation().smpsLogical().sequencers()
                    .stream()
                    .anyMatch(
                            com.openggf.audio.rewind.SmpsDriverSnapshot
                                    .SequencerEntry::sfx));

            long before = capture.totalStereoFrames();
            int[] packetSizes = new int[10];
            short[][] actualPackets = new short[10][];

            for (int frame = 0; frame < 10; frame++) {
                audio.beginCommandTimelineFrame(120 + frame);
                audio.presentFrame(PresentationMode.FORWARD);
                long producerTotal = audio.releaseStateForTesting()
                        .producer().clock().totalSamplesProduced();
                packetSizes[frame] = Math.toIntExact(
                        producerTotal - producerFrames);
                producerFrames = producerTotal;
                assertEquals(packetSizes[frame],
                        capture.drainPresentationFrame(packet),
                        "capture must preserve each producer packet exactly");
                actualPackets[frame] = java.util.Arrays.copyOf(
                        packet, packetSizes[frame] * 2);
            }
            AudioPresentationSnapshot expectedAfterTen =
                    audio.captureLogicalSnapshot().presentation();
            assertEquals(7_350,
                    capture.totalStereoFrames() - before);
            assertArrayEquals(
                    new int[] {735, 735, 735, 735, 735,
                            735, 735, 735, 735, 735},
                    packetSizes);
            assertEquals(10, capture.clockSnapshot().remainder());

            audio.presentationCoordFlagHandlersForTesting().state()
                    .setSpindashRevCounter(55);
            audio.restoreLogicalSnapshot(boundary);
            assertEquals(boundary.presentation().coordFlagRuntimeState()
                            .spindashRevCounter(),
                    audio.presentationCoordFlagHandlersForTesting().state()
                            .spindashRevCounter());
            for (int frame = 0; frame < 10; frame++) {
                audio.beginCommandTimelineFrame(130 + frame);
                audio.presentFrame(PresentationMode.FORWARD);
                assertEquals(packetSizes[frame],
                        capture.drainPresentationFrame(packet));
                assertArrayEquals(actualPackets[frame],
                        java.util.Arrays.copyOf(
                                packet, packetSizes[frame] * 2),
                        "restoring the one session/logical memento must "
                                + "reproduce the next PCM packet");
            }
            assertPresentationLogicalEquals(expectedAfterTen,
                    audio.captureLogicalSnapshot().presentation());
        }
    }

    private static AudioVoiceRegistry registry(
            AudioPresentationDependencyResolver resolver) {
        return new AudioVoiceRegistry(
                new com.openggf.audio.presentation.SmpsSfxInstantiation() {
                    @Override
                    public com.openggf.audio.smps.SmpsSequencer instantiateCached(
                            com.openggf.audio.presentation.ResolvedSmpsSfxSource source,
                            com.openggf.audio.driver.SmpsDriver currentOwner) {
                        throw new AssertionError("no SFX SMPS expected");
                    }

                },
                resolver,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()),
                warning -> {
                    throw new AssertionError(warning);
                });
    }

    private static GameAudioProfile configuredProfile(
            AudioTestFixtures.StubSmpsLoader loader) {
        com.openggf.audio.smps.SmpsSequencerConfig config =
                new com.openggf.audio.smps.SmpsSequencerConfig.Builder()
                        .build();
        return new AudioTestFixtures.StubAudioProfile(loader) {
            @Override
            public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return config;
            }
        };
    }

    private static GameAudioProfile configuredS3kProfile(
            AudioTestFixtures.StubSmpsLoader loader) {
        return new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public com.openggf.audio.smps.SmpsSequencerConfig
                    getSequencerConfig() {
                return Sonic3kSmpsSequencerConfig.CONFIG;
            }
            @Override public String presentationGameId() {
                return "s3k";
            }
            @Override public boolean isMusicOverride(int musicId) {
                return musicId == 0x82 || musicId == 0x83;
            }
            @Override public boolean isSfxBlockingMusic(int musicId) {
                return musicId == 0x83;
            }
            @Override public void configurePresentationCoordFlagHandlers(
                    SmpsCoordFlagHandlerOwner owner) {
                owner.register("s3k", Sonic3kCoordFlagHandler::new);
            }
        };
    }

    private static void assertPresentationLogicalEquals(
            AudioPresentationSnapshot expected,
            AudioPresentationSnapshot actual) {
        assertEquals(expected.activeMusic(), actual.activeMusic());
        assertEquals(expected.overrideStack(), actual.overrideStack());
        assertEquals(expected.rawPcmVoiceId(), actual.rawPcmVoiceId());
        assertEquals(expected.fmMuteMask(), actual.fmMuteMask());
        assertEquals(expected.fmSoloMask(), actual.fmSoloMask());
        assertEquals(expected.psgMuteMask(), actual.psgMuteMask());
        assertEquals(expected.psgSoloMask(), actual.psgSoloMask());
        assertEquals(expected.sfxBlocked(), actual.sfxBlocked());
        assertEquals(expected.sfxBlockHeldThroughFadeIn(),
                actual.sfxBlockHeldThroughFadeIn());
        assertEquals(expected.pendingRestore(), actual.pendingRestore());
        assertEquals(expected.speedShoesEnabled(),
                actual.speedShoesEnabled());
        assertEquals(expected.speedMultiplier(), actual.speedMultiplier());
        assertEquals(expected.ringLeft(), actual.ringLeft());
        assertEquals(expected.coordFlagRuntimeState(),
                actual.coordFlagRuntimeState());
        assertEquals(expected.voices().size(), actual.voices().size());
        for (int index = 0; index < expected.voices().size(); index++) {
            PresentationVoiceSnapshot left = expected.voices().get(index);
            PresentationVoiceSnapshot right = actual.voices().get(index);
            assertEquals(left.getClass(), right.getClass());
            assertEquals((PresentationVoiceSnapshot.Sample) left,
                    (PresentationVoiceSnapshot.Sample) right);
        }
    }

    private static void assertPresentationSnapshotExactly(
            AudioPresentationSnapshot expected,
            AudioPresentationSnapshot actual) {
        assertPresentationLogicalEquals(expected, actual);
        assertEquals(expected.nextVoiceId(), actual.nextVoiceId());
    }

    private static void assertDriverSnapshotExactly(
            com.openggf.audio.rewind.SmpsDriverSnapshot expected,
            com.openggf.audio.rewind.SmpsDriverSnapshot actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
            return;
        }
        assertEquals(expected.region(), actual.region());
        assertEquals(expected.readMode(), actual.readMode());
        assertEquals(expected.continuousSfxId(),
                actual.continuousSfxId());
        assertEquals(expected.continuousSfxFlag(),
                actual.continuousSfxFlag());
        assertEquals(expected.contSfxLoopCnt(), actual.contSfxLoopCnt());
        assertEquals(expected.palUpdateCounter(),
                actual.palUpdateCounter());
        assertArrayEquals(expected.fmLockSequencerIds(),
                actual.fmLockSequencerIds());
        assertArrayEquals(expected.psgLockSequencerIds(),
                actual.psgLockSequencerIds());
        assertEquals(expected.sequencers().size(),
                actual.sequencers().size());
        for (int index = 0; index < expected.sequencers().size();
                index++) {
            var left = expected.sequencers().get(index);
            var right = actual.sequencers().get(index);
            assertEquals(left.sfx(), right.sfx());
            assertEquals(left.source(), right.source());
            assertEquals(left.fallbackVoiceSource(),
                    right.fallbackVoiceSource());
            assertDeepSnapshotEquals(left.snapshot(), right.snapshot());
        }
    }

    private static void assertReleaseStateExactly(
            AudioManager.ReleaseStateForTesting expected,
            AudioManager.ReleaseStateForTesting actual,
            String message) {
        assertEquals(expected.logical().ringLeft(),
                actual.logical().ringLeft(), message);
        assertEquals(expected.logical().commandTimelineFrame(),
                actual.logical().commandTimelineFrame(), message);
        assertEquals(expected.logical().commandTimelineNextOrder(),
                actual.logical().commandTimelineNextOrder(), message);
        assertEquals(expected.logical().commandEntryCount(),
                actual.logical().commandEntryCount(), message);
        assertPresentationSnapshotExactly(
                expected.logical().presentation(),
                actual.logical().presentation());

        AbstractSmpsAudioBackend.StateForTesting left =
                expected.backend();
        AbstractSmpsAudioBackend.StateForTesting right =
                actual.backend();
        assertSame(left.currentStream(), right.currentStream(), message);
        assertSame(left.sfxStream(), right.sfxStream(), message);
        assertSame(left.currentSmps(), right.currentSmps(), message);
        assertSame(left.musicDriver(), right.musicDriver(), message);
        assertEquals(left.currentMusic(), right.currentMusic(), message);
        assertEquals(left.currentMusicId(), right.currentMusicId(), message);
        assertEquals(left.pendingMusic(), right.pendingMusic(), message);
        assertEquals(left.sfxBlocked(), right.sfxBlocked(), message);
        assertEquals(left.pendingRestore(), right.pendingRestore(), message);
        assertEquals(left.speedShoesEnabled(),
                right.speedShoesEnabled(), message);
        assertEquals(left.speedMultiplier(),
                right.speedMultiplier(), message);
        assertEquals(left.fmUserMuteMask(),
                right.fmUserMuteMask(), message);
        assertEquals(left.fmUserSoloMask(),
                right.fmUserSoloMask(), message);
        assertEquals(left.psgUserMuteMask(),
                right.psgUserMuteMask(), message);
        assertEquals(left.psgUserSoloMask(),
                right.psgUserSoloMask(), message);
        assertEquals(left.overrideStack().size(),
                right.overrideStack().size(), message);
        for (int index = 0; index < left.overrideStack().size(); index++) {
            var leftOverride = left.overrideStack().get(index);
            var rightOverride = right.overrideStack().get(index);
            assertSame(leftOverride.stream(),
                    rightOverride.stream(), message);
            assertSame(leftOverride.sequencer(),
                    rightOverride.sequencer(), message);
            assertSame(leftOverride.driver(),
                    rightOverride.driver(), message);
            assertEquals(leftOverride.musicId(),
                    rightOverride.musicId(), message);
            assertEquals(leftOverride.descriptor(),
                    rightOverride.descriptor(), message);
            assertDriverSnapshotExactly(
                    leftOverride.driverSnapshot(),
                    rightOverride.driverSnapshot());
            assertSameElements(leftOverride.sequencers(),
                    rightOverride.sequencers(), message);
        }
        assertDriverSnapshotExactly(
                left.musicDriverSnapshot(),
                right.musicDriverSnapshot());
        assertSameElements(left.musicSequencers(),
                right.musicSequencers(), message);
        if (left.standaloneSfxDriverSnapshot() == null) {
            assertEquals(null, right.standaloneSfxDriverSnapshot(), message);
        } else {
            assertDriverSnapshotExactly(
                    left.standaloneSfxDriverSnapshot(),
                    right.standaloneSfxDriverSnapshot());
        }
        assertSameElements(left.standaloneSfxSequencers(),
                right.standaloneSfxSequencers(), message);
        assertProducerStateExactly(
                expected.producer(), actual.producer(), message);
    }

    private static void assertProducerStateExactly(
            com.openggf.audio.presentation.AudioPresentationProducer
                    .TransactionFingerprint expected,
            com.openggf.audio.presentation.AudioPresentationProducer
                    .TransactionFingerprint actual,
            String message) {
        assertEquals(expected.clock(), actual.clock(), message);
        assertEquals(expected.history(), actual.history(), message);
        assertEquals(expected.voiceIdentities(),
                actual.voiceIdentities(), message);
        assertEquals(expected.reverseCursor(),
                actual.reverseCursor(), message);
        assertEquals(expected.selectedRestoreIdentity(),
                actual.selectedRestoreIdentity(), message);
        assertEquals(expected.selectedRestoreResolverIdentity(),
                actual.selectedRestoreResolverIdentity(), message);
        assertEquals(expected.preparedSelectedRestoreIdentity(),
                actual.preparedSelectedRestoreIdentity(), message);
        assertEquals(expected.releaseCrossfadeRemaining(),
                actual.releaseCrossfadeRemaining(), message);
        assertEquals(expected.lastReverseLeft(),
                actual.lastReverseLeft(), message);
        assertEquals(expected.lastReverseRight(),
                actual.lastReverseRight(), message);
        assertEquals(expected.historyArmed(),
                actual.historyArmed(), message);
        assertEquals(expected.reverseActive(),
                actual.reverseActive(), message);
        assertEquals(expected.reverseFrameOutput(),
                actual.reverseFrameOutput(), message);
        assertEquals(expected.hasLastReverseFrame(),
                actual.hasLastReverseFrame(), message);
        assertEquals(expected.captureCount(),
                actual.captureCount(), message);
    }

    private static void assertSameElements(
            List<?> expected, List<?> actual, String message) {
        assertEquals(expected.size(), actual.size(), message);
        for (int index = 0; index < expected.size(); index++) {
            assertSame(expected.get(index), actual.get(index), message);
        }
    }

    private static void assertDeepSnapshotEquals(Object expected,
            Object actual) {
        assertDeepSnapshotEquals(expected, actual,
                new IdentityHashMap<>());
    }

    private static void assertDeepSnapshotEquals(Object expected,
            Object actual, Map<Object, Object> seen) {
        if (expected == actual) {
            return;
        }
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual));
            for (int index = 0; index < Array.getLength(expected); index++) {
                assertDeepSnapshotEquals(Array.get(expected, index),
                        Array.get(actual, index), seen);
            }
            return;
        }
        if (expected instanceof List<?> left
                && actual instanceof List<?> right) {
            assertEquals(left.size(), right.size());
            for (int index = 0; index < left.size(); index++) {
                assertDeepSnapshotEquals(left.get(index), right.get(index),
                        seen);
            }
            return;
        }
        if (!expected.getClass().isRecord()) {
            assertEquals(expected, actual);
            return;
        }
        if (seen.put(expected, actual) != null) {
            return;
        }
        for (RecordComponent component
                : expected.getClass().getRecordComponents()) {
            try {
                assertDeepSnapshotEquals(
                        component.getAccessor().invoke(expected),
                        component.getAccessor().invoke(actual), seen);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static AudioPresentationDependencyResolver resolver(
            DecodedPcm pcm) {
        return new AudioPresentationDependencyResolver() {
            @Override
            public DecodedPcm resolvePcm(String assetId) {
                assertEquals(pcm.assetId(), assetId);
                return pcm;
            }

        };
    }

    private static DecodedPcm ramp(
            String assetId, int sampleRate, int frames) {
        short[] samples = new short[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            samples[frame * 2] = (short) (frame * 13 - 500);
            samples[frame * 2 + 1] = (short) (700 - frame * 7);
        }
        return new DecodedPcm(assetId, 2, sampleRate, samples);
    }

    private static AbstractSmpsData persistentRestTrack(int id) {
        byte[] data = new byte[2_049];
        for (int index = 1; index < data.length; index += 2) {
            data[index] = (byte) 0x80;
            data[index + 1] = 0x7F;
        }
        return new AbstractSmpsData(data, 0) {
            {
                setId(id);
            }

            @Override
            protected void parseHeader() {
                channels = 1;
                fmPointers = new int[] {1};
                fmKeyOffsets = new int[] {0};
                fmVolumeOffsets = new int[] {0};
            }

            @Override
            public byte[] getVoice(int voiceId) {
                return new byte[25];
            }

            @Override
            public byte[] getPsgEnvelope(int envelopeId) {
                return new byte[] {0};
            }

            @Override
            public int read16(int offset) {
                return 0;
            }

            @Override
            public int getBaseNoteOffset() {
                return 0;
            }
        };
    }

    private static AbstractSmpsData persistentS3kMusic(int id) {
        byte[] data = new byte[0x4_000];
        setLe16(data, 0, 0x80);
        data[2] = 2;
        data[3] = 0;
        data[4] = 1;
        data[5] = (byte) 0x80;
        setLe16(data, 6, 0);
        setLe16(data, 10, 0x100);
        writePersistentS3kTrack(data, 0x100);
        Sonic3kSmpsData source = new Sonic3kSmpsData(data, 0);
        source.setId(id);
        return source;
    }

    private static AbstractSmpsData persistentS3kSfx(int id) {
        byte[] data = new byte[0x4_000];
        setLe16(data, 0, 0x80);
        data[2] = 1;
        data[3] = 1;
        data[4] = (byte) 0x80;
        data[5] = 0x02;
        setLe16(data, 6, 0x100);
        writePersistentS3kTrack(data, 0x100);
        Sonic3kSfxData source =
                new Sonic3kSfxData(data, 0, 0, 0);
        source.setId(id);
        return source;
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void writePersistentS3kTrack(
            byte[] data, int offset) {
        data[offset] = (byte) 0x80;
        data[offset + 1] = 0x7F;
        data[offset + 2] = (byte) 0xF6;
        setLe16(data, offset + 3, offset);
    }
}
