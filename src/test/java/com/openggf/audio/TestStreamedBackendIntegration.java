package com.openggf.audio;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioKeyframeStore;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamedBackendIntegration {
    @Test
    void namespacedOneShotsQueueAtOwnerBoundaryAndSeventeenthVoiceStealsOldest() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort port = new RecordingPort(8_000, true);
        port.sfxSample = 5_000;
        backend.installStreamedMusicPort(port);
        backend.update();
        StreamedMusicPort.SfxRef key = new StreamedMusicPort.SfxRef("owner", "effect");
        assertFalse(backend.tryPlayStreamedSfx(new StreamedMusicPort.SfxRef("other", "effect")));
        for (int i = 0; i < 17; i++) assertTrue(backend.tryPlayStreamedSfx(key));
        assertEquals(0, port.openSfxCount, "gameplay callers must not open PCM cursors");

        backend.update();

        assertEquals(17, port.openSfxCount);
        assertEquals(Short.MAX_VALUE, backend.uploaded[0], "sixteen active voices saturate deterministically");
        assertEquals(17, port.closedSfxCount, "stolen and completed voices each close exactly once");
        backend.destroy();
    }

    @Test
    void rewindEntryDropsPendingOneShotsBeforeThePresentationOwnerCanOpenThem() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.update();
        assertTrue(backend.tryPlayStreamedSfx(new StreamedMusicPort.SfxRef("owner", "effect")));

        backend.beginReversePresentation();
        backend.update();

        assertEquals(0, port.openSfxCount);
        backend.endReversePresentation();
        backend.destroy();
    }

    @Test
    void namespacedPreflightSeesPendingInstallAndExactKeyBeforeTimelineAcceptance() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort port = new RecordingPort(8_000, true);
        StreamedMusicPort.TrackRef key = new StreamedMusicPort.TrackRef("owner", "track");
        backend.installStreamedMusicPort(port);

        assertFalse(backend.tryPlayStreamedMusic(
                new StreamedMusicPort.TrackRef("other", "track")));
        assertTrue(backend.tryPlayStreamedMusic(key));
        backend.update();

        StreamedMusicPort.State state = backend.captureLogicalSnapshot().streamedMusic();
        assertNotNull(state);
        assertEquals(key, state.track());
        assertEquals(-1, state.logicalMusicId());
    }

    @Test
    void portInstallAndReplacementAreConsumedInFifoOrderAtUpdateBoundary() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort first = new RecordingPort(8_000, true);
        RecordingPort second = new RecordingPort(8_000, true);

        backend.installStreamedMusicPort(first);
        backend.installStreamedMusicPort(second);
        int[] fallbackCount = { 0 };
        backend.playStreamedMusicOrElse(7, () -> fallbackCount[0]++);
        assertEquals(0, second.playCount, "port calls are confined to the update owner boundary");
        assertEquals(0, first.closeCount);

        backend.update();

        assertEquals(1, first.closeCount, "FIFO replacement closes the superseded lease once");
        assertEquals(0, second.closeCount);
        assertEquals(1, second.playCount);
        assertEquals(0, fallbackCount[0]);
        backend.destroy();
        assertEquals(1, second.closeCount);
    }

    @Test
    void mismatchedOutputRateIsRejectedAndClosedAtBoundary() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort wrongRate = new RecordingPort(16_000, true);
        wrongRate.throwRateAfterClose = true;
        assertThrows(IllegalArgumentException.class, () -> backend.installStreamedMusicPort(wrongRate));
        assertEquals(1, wrongRate.closeCount);
        assertDoesNotThrow(backend::update);
    }

    @Test
    void streamedForegroundSwitchUnqueuesAllPreviouslyQueuedPresentationPcm() {
        InstrumentedLwjglBackend backend = new InstrumentedLwjglBackend();
        RecordingPort streamed = new RecordingPort(8_000, true);
        streamed.sample = 100;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(1, () -> fail("override expected"));
        backend.update();
        assertEquals(3, backend.uploadCount);

        streamed.sample = 200;
        backend.playStreamedMusicOrElse(2, () -> fail("override expected"));
        backend.update();

        assertTrue(backend.allBufferClearCount > 0);
        assertEquals(6, backend.uploadCount, "new foreground must refill every initial queue buffer");
        assertEquals(200, backend.firstSample);
    }

    @Test
    void unresolvedOverrideRunsPreparedStockFallbackOnUpdateAfterInstall() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort emptyResolver = new RecordingPort(8_000, false);
        int[] fallbackCount = { 0 };
        backend.installStreamedMusicPort(emptyResolver);
        backend.playStreamedMusicOrElse(3, () -> fallbackCount[0]++);

        backend.update();

        assertEquals(1, emptyResolver.resolveCount);
        assertEquals(0, emptyResolver.playCount);
        assertEquals(1, fallbackCount[0]);
    }

    @Test
    void streamedOnlyForegroundProducesPresentationPcmAtItsFixedRate() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort streamed = new RecordingPort(8_000, true);
        streamed.forbiddenLock = backend.streamLockForTesting();
        streamed.sample = 1_234;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(4, () -> fail("prepared override should win"));

        backend.update();

        assertEquals(8_000, backend.uploadRate);
        assertNotNull(backend.uploaded);
        assertEquals(1_234, backend.uploaded[0]);
        assertEquals(1_234, backend.uploaded[1]);
        assertFalse(streamed.calledUnderForbiddenLock);
    }

    @Test
    void streamedOnlyForegroundUploadsAtInternalSynthRateWhenConfigured() {
        int internalRate = (int) Math.round(Ym2612Chip.getInternalRate());
        InstrumentedBackend backend = new InstrumentedBackend(true);
        RecordingPort streamed = new RecordingPort(internalRate, true);
        streamed.sample = 321;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(6, () -> fail("prepared override should win"));

        backend.update();

        assertEquals(internalRate, backend.uploadRate);
        assertEquals(321, backend.uploaded[0]);
    }

    @Test
    void realLwjglPumpStartsAndQueuesForStreamedOnlyForegroundWithoutOpenAl() {
        InstrumentedLwjglBackend backend = new InstrumentedLwjglBackend();
        RecordingPort streamed = new RecordingPort(8_000, true);
        streamed.sample = 777;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(5, () -> fail("prepared override should win"));

        backend.update();

        assertEquals(3, backend.initialQueueCount);
        assertEquals(3, backend.uploadCount);
        assertEquals(777, backend.firstSample);
        assertEquals(8_000, backend.lastRate);
    }

    @Test
    void resetClosesActiveAndPendingPortsAndDropsStalePlayTransitions() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort active = new RecordingPort(8_000, true);
        RecordingPort pending = new RecordingPort(8_000, true);
        int[] fallback = { 0 };
        backend.installStreamedMusicPort(active);
        backend.update();
        backend.installStreamedMusicPort(pending);
        backend.playStreamedMusicOrElse(9, () -> fallback[0]++);

        backend.resetStreamedMusicPort();
        backend.update();

        assertEquals(1, active.closeCount);
        assertEquals(1, pending.closeCount);
        assertEquals(0, pending.playCount);
        assertEquals(0, fallback[0]);
    }

    @Test
    void appAndRewindPauseReasonsApplyInFifoOrderAndFreezeMixing() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort streamed = new RecordingPort(8_000, true);
        streamed.sample = 99;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(2, () -> fail("override expected"));
        backend.pause();
        backend.beginReversePresentation();

        backend.update();

        assertEquals(StreamedMusicPort.PAUSE_APP | StreamedMusicPort.PAUSE_REWIND, streamed.pauseMask);
        assertEquals(0, streamed.mixCount);
        backend.resume();
        backend.endReversePresentation();
        backend.update();
        assertEquals(0, streamed.pauseMask);
        assertTrue(streamed.mixCount > 0);
    }

    @Test
    void deterministicPresentationDrainNeverMixesStreamedForeground() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort streamed = new RecordingPort(8_000, true);
        streamed.sample = 999;
        backend.installStreamedMusicPort(streamed);
        backend.playStreamedMusicOrElse(2, () -> fail("override expected"));
        backend.attachDeterministicAudioRuntime(new DeterministicAudioRuntime() {
            @Override public void advanceFrame(long frame, FrameAudioMode mode) { }
            @Override public boolean providesPresentationPcm() { return true; }
            @Override public int drainPcm(short[] target, int frames) {
                target[0] = 55;
                target[1] = 55;
                return frames;
            }
        });

        backend.update();

        assertEquals(0, streamed.mixCount);
        assertEquals(55, backend.uploaded[0]);
    }

    @Test
    void midFadeRateSnapshotRestoresIdenticalSubsequentStateAndPcm() {
        InstrumentedBackend expectedBackend = new InstrumentedBackend();
        RecordingPort expected = new RecordingPort(8_000, true);
        expected.sample = 400;
        expectedBackend.installStreamedMusicPort(expected);
        expectedBackend.playStreamedMusicOrElse(8, () -> fail("override expected"));
        expectedBackend.update();
        expectedBackend.setSpeedMultiplier(2);
        expectedBackend.fadeOutMusic(4, 0);
        expectedBackend.update();
        AudioBackendLogicalSnapshot snapshot = expectedBackend.captureLogicalSnapshot();

        InstrumentedBackend restoredBackend = new InstrumentedBackend();
        RecordingPort restored = new RecordingPort(8_000, true);
        restored.sample = 400;
        restoredBackend.installStreamedMusicPort(restored);
        restoredBackend.update();
        restoredBackend.restoreLogicalSnapshot(snapshot);

        expectedBackend.update();
        restoredBackend.update();
        assertArrayEquals(expectedBackend.uploaded, restoredBackend.uploaded);
        assertEquals(expectedBackend.captureLogicalSnapshot().streamedMusic(),
                restoredBackend.captureLogicalSnapshot().streamedMusic());
    }

    @Test
    void emptyResolverFallbackMatchesDirectRealSmpsPlayback() {
        AudioTestFixtures.StubSmpsData data = new AudioTestFixtures.StubSmpsData("base");
        InstrumentedBackend direct = new InstrumentedBackend();
        direct.setAudioProfile(new Sonic1AudioProfile());
        direct.prepareLogicalMusicSource(com.openggf.audio.rewind.AudioSourceDescriptor.baseMusic(0));
        direct.playSmps(data, AudioTestFixtures.EMPTY_DAC);
        direct.update();

        InstrumentedBackend fallback = new InstrumentedBackend();
        fallback.setAudioProfile(new Sonic1AudioProfile());
        fallback.playStreamedMusicOrElse(0, () -> {
            fallback.prepareLogicalMusicSource(com.openggf.audio.rewind.AudioSourceDescriptor.baseMusic(0));
            fallback.playSmps(data, AudioTestFixtures.EMPTY_DAC);
        });
        fallback.update();

        assertNotNull(fallback.musicDriverForTesting());
        assertEquals(direct.captureLogicalSnapshot().currentMusic(),
                fallback.captureLogicalSnapshot().currentMusic());
        assertNotNull(fallback.captureLogicalSnapshot().musicDriver());
        assertArrayEquals(direct.uploaded, fallback.uploaded);
    }

    @Test
    void liveProfilesApplyOverrideBlockingRestoreAndDrowningReplacementCategories() {
        for (GameAudioProfile profile : java.util.List.of(
                new Sonic1AudioProfile(), new Sonic2AudioProfile(), new Sonic3kAudioProfile())) {
            InstrumentedBackend backend = new InstrumentedBackend();
            backend.setAudioProfile(profile);
            RecordingPort port = new RecordingPort(8_000, true);
            backend.installStreamedMusicPort(port);
            backend.playStreamedMusicOrElse(1, () -> fail("base override expected"));
            backend.update();
            double savedBasePosition = port.position;

            backend.playStreamedMusicOrElse(profile.getInvincibilityMusicId(), () -> fail("invincibility override expected"));
            backend.update();
            assertEquals(1, backend.captureLogicalSnapshot().overrideStack().size());
            assertEquals(1, backend.captureLogicalSnapshot().streamedOverrideStack().size());
            assertNotNull(backend.captureLogicalSnapshot().streamedOverrideStack().getFirst());
            assertFalse(backend.captureLogicalSnapshot().sfxBlocked());

            int superId = profile.getSuperSonicMusicId();
            if (superId >= 0 && superId != profile.getInvincibilityMusicId()) {
                backend.playStreamedMusicOrElse(superId, () -> fail("super override expected"));
                backend.update();
                assertEquals(2, backend.captureLogicalSnapshot().overrideStack().size());
                assertEquals(2, backend.captureLogicalSnapshot().streamedOverrideStack().size());
            }

            backend.playStreamedMusicOrElse(profile.getExtraLifeMusicId(), () -> fail("1-up override expected"));
            backend.update();
            assertTrue(backend.captureLogicalSnapshot().sfxBlocked());
            backend.restoreMusic();
            backend.update();
            assertEquals(profile.blocksSfxDuringMusicRestoreFadeIn(),
                    backend.captureLogicalSnapshot().sfxBlocked());

            backend.playStreamedMusicOrElse(profile.getDrowningMusicId(), () -> fail("drowning replacement expected"));
            backend.update();
            assertTrue(backend.captureLogicalSnapshot().overrideStack().isEmpty());
            assertFalse(backend.captureLogicalSnapshot().sfxBlocked());
            backend.restoreMusic();
            backend.update();
            assertEquals(profile.getDrowningMusicId(), port.logicalMusicId);
            assertNotEquals(savedBasePosition, port.position, "drowning must not resume saved base state");
        }
    }

    @Test
    void audioManagerReplacementResetDestroyAndFailedInitCloseTransferredPortsOnce() {
        AudioManager manager = AudioManager.getInstance();
        manager.resetState();
        try {
            InstrumentedBackend firstBackend = new InstrumentedBackend();
            manager.setBackend(firstBackend);
            RecordingPort first = new RecordingPort(8_000, true);
            manager.installStreamedMusicPort(first);
            manager.update();

            InstrumentedBackend secondBackend = new InstrumentedBackend();
            manager.setBackend(secondBackend);
            assertEquals(1, first.closeCount);
            RecordingPort second = new RecordingPort(8_000, true);
            manager.installStreamedMusicPort(second);
            manager.update();
            manager.resetState();
            assertEquals(1, second.closeCount);

            FailingInitBackend failing = new FailingInitBackend();
            RecordingPort pending = new RecordingPort(8_000, true);
            failing.installStreamedMusicPort(pending);
            manager.setBackend(failing);
            assertEquals(1, pending.closeCount);
        } finally {
            manager.resetState();
        }
    }

    @Test
    void nestedRewindReplayBypassBalancesOnlyAtOuterScopeBoundaries() {
        AudioManager manager = AudioManager.getInstance();
        manager.resetState();
        InstrumentedBackend backend = new InstrumentedBackend();
        manager.setBackend(backend);
        try (var outer = manager.beginRewindReplay(0, 4, AudioReplayReason.SEEK)) {
            assertEquals(1, backend.bypassBeginCount);
            try (var inner = manager.beginRewindReplay(1, 3, AudioReplayReason.STEP_BACKWARD)) {
                assertEquals(1, backend.bypassBeginCount);
                assertEquals(0, backend.bypassEndCount);
            }
            assertEquals(0, backend.bypassEndCount);
        } finally {
            assertEquals(1, backend.bypassEndCount);
            manager.resetState();
        }
    }

    @Test
    void missingCurrentStreamDuringRestoreFailsClosedToSilence() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(3, () -> fail("override expected"));
        backend.update();
        AudioBackendLogicalSnapshot snapshot = backend.captureLogicalSnapshot();
        port.restoreAllowed = false;

        backend.restoreLogicalSnapshot(snapshot);

        assertFalse(port.hasSource());
        assertNull(backend.captureLogicalSnapshot().streamedMusic());
        assertFalse(backend.captureLogicalSnapshot().sfxBlocked());
    }

    @Test
    void keyframeReplayBypassesTwoStreamedOverrideResolutionsAndKeepsRestoredStream() {
        AudioManager manager = AudioManager.getInstance();
        manager.resetState();
        InstrumentedBackend backend = new InstrumentedBackend();
        manager.setBackend(backend);
        manager.setAudioProfile(new Sonic1AudioProfile());
        RecordingPort port = new RecordingPort(8_000, true);
        manager.installStreamedMusicPort(port);
        AudioKeyframeStore keyframes = new AudioKeyframeStore();
        try {
            manager.beginCommandTimelineFrame(0);
            manager.playMusic(5);
            manager.update();
            keyframes.capture(0, manager);
            manager.beginCommandTimelineFrame(1);
            manager.playMusic(6);
            manager.update();
            manager.beginCommandTimelineFrame(2);
            manager.playMusic(7);
            manager.update();
            int liveResolveCount = port.resolveCount;

            assertEquals(2, keyframes.replayTo(manager, 2, AudioReplayReason.SEEK));

            assertEquals(liveResolveCount, port.resolveCount, "replay must not consult mod resolution");
            assertEquals(5, port.logicalMusicId, "streamed foreground comes only from restored keyframe");
            manager.beginCommandTimelineFrame(3);
            manager.playMusic(8);
            manager.update();
            assertEquals(8, backend.captureLogicalSnapshot().currentMusic().id(),
                    "replay bypass must discard its pending descriptor");
        } finally {
            manager.resetState();
        }
    }

    @Test
    void backendSameIdAndKeyIsIdempotentWhileDifferentIdRestarts() {
        InstrumentedBackend backend = new InstrumentedBackend();
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(10, () -> fail("override expected"));
        backend.update();
        double afterFirst = port.position;

        backend.playStreamedMusicOrElse(10, () -> fail("override expected"));
        backend.update();
        assertTrue(port.position > afterFirst);

        backend.playStreamedMusicOrElse(11, () -> fail("override expected"));
        backend.update();
        assertEquals(1024.0, port.position);
    }

    @Test
    void nonblockingOverrideRestoresSavedFrameRateAndMidFadeState() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        InstrumentedBackend backend = new InstrumentedBackend();
        backend.setAudioProfile(profile);
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(1, () -> fail("base expected"));
        backend.update();
        backend.setSpeedMultiplier(2);
        backend.fadeOutMusic(4, 0);
        backend.update();
        StreamedMusicPort.State saved = backend.captureLogicalSnapshot().streamedMusic();

        backend.playStreamedMusicOrElse(profile.getInvincibilityMusicId(), () -> fail("inv expected"));
        backend.update();
        assertEquals(saved, backend.captureLogicalSnapshot().streamedOverrideStack().getFirst());
        backend.restoreMusic();
        backend.update();

        StreamedMusicPort.State restored = backend.captureLogicalSnapshot().streamedMusic();
        assertEquals(saved.logicalMusicId(), restored.logicalMusicId());
        assertEquals(saved.rate(), restored.rate());
        assertTrue(restored.sourceFramePosition() > saved.sourceFramePosition());
        assertTrue(restored.fade().remainingSteps() < saved.fade().remainingSteps());
    }

    @Test
    void nestedStreamedForegroundClearsOnlyJinglePauseInheritedFromStockSmps() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        InstrumentedBackend backend = new InstrumentedBackend();
        backend.setAudioProfile(profile);
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(1, () -> fail("base expected"));
        backend.update();
        backend.pause();
        backend.beginReversePresentation();
        backend.update();

        backend.prepareLogicalMusicSource(com.openggf.audio.rewind.AudioSourceDescriptor.baseMusic(
                profile.getInvincibilityMusicId()));
        backend.playSmps(new IdSmpsData(profile.getInvincibilityMusicId()), AudioTestFixtures.EMPTY_DAC,
                profile.getSequencerConfig(), true);
        assertEquals(StreamedMusicPort.PAUSE_APP | StreamedMusicPort.PAUSE_REWIND
                | StreamedMusicPort.PAUSE_JINGLE, port.pauseMask);

        backend.playStreamedMusicOrElse(profile.getExtraLifeMusicId(), () -> fail("streamed 1-up expected"));
        int mixesBeforePausedSelection = port.mixCount;
        backend.update();
        assertEquals(StreamedMusicPort.PAUSE_APP | StreamedMusicPort.PAUSE_REWIND, port.pauseMask);
        assertEquals(profile.getExtraLifeMusicId(), port.logicalMusicId);
        assertEquals(mixesBeforePausedSelection, port.mixCount);

        backend.resume();
        backend.endReversePresentation();
        backend.update();
        assertEquals(0, port.pauseMask);
        assertTrue(port.mixCount > 0);
    }

    @Test
    void restoringBaseUnderJingleRetainsActiveAppAndRewindPausesUntilTheirResume() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        InstrumentedBackend backend = new InstrumentedBackend();
        backend.setAudioProfile(profile);
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(1, () -> fail("base expected"));
        backend.update();
        backend.playSmps(new IdSmpsData(profile.getInvincibilityMusicId()), AudioTestFixtures.EMPTY_DAC,
                profile.getSequencerConfig(), true);

        backend.pause();
        backend.beginReversePresentation();
        backend.restoreMusic();
        backend.update();

        assertEquals(StreamedMusicPort.PAUSE_APP | StreamedMusicPort.PAUSE_REWIND,
                backend.captureLogicalSnapshot().streamedMusic().pauseMask());
        backend.resume();
        backend.endReversePresentation();
        backend.update();
        assertEquals(0, backend.captureLogicalSnapshot().streamedMusic().pauseMask());
    }

    @Test
    void queuedStreamedSelectionThenFadeTargetsTheNewForeground() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        InstrumentedBackend backend = new InstrumentedBackend();
        backend.setAudioProfile(profile);
        backend.playSmps(new IdSmpsData(1), AudioTestFixtures.EMPTY_DAC);
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.update();

        backend.playStreamedMusicOrElse(2, () -> fail("stream expected"));
        backend.fadeOutMusic(4, 0);
        backend.update();

        assertTrue(port.fadeActive());
        assertEquals(3, port.fade.remainingSteps());
    }

    @Test
    void queuedPlayThenStopCancelsResolvedAndFallbackForegrounds() {
        InstrumentedBackend resolvedBackend = new InstrumentedBackend();
        RecordingPort resolved = new RecordingPort(8_000, true);
        resolvedBackend.installStreamedMusicPort(resolved);
        resolvedBackend.playStreamedMusicOrElse(4, () -> fail("stream expected"));
        resolvedBackend.stopPlayback();
        resolvedBackend.update();
        assertFalse(resolved.hasSource());
        assertNull(resolvedBackend.captureLogicalSnapshot().currentMusic());

        InstrumentedBackend fallbackBackend = new InstrumentedBackend();
        fallbackBackend.setAudioProfile(new Sonic1AudioProfile());
        fallbackBackend.playStreamedMusicOrElse(4,
                () -> fallbackBackend.playSmps(new IdSmpsData(4), AudioTestFixtures.EMPTY_DAC));
        fallbackBackend.stopPlayback();
        fallbackBackend.update();
        assertNull(fallbackBackend.musicDriverForTesting());
        assertNull(fallbackBackend.captureLogicalSnapshot().currentMusic());
    }

    @Test
    void queuedStreamedJingleCanRestoreOrEndBeforeItsSelectionBoundary() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        for (boolean explicitRestore : new boolean[] { true, false }) {
            InstrumentedBackend backend = new InstrumentedBackend();
            backend.setAudioProfile(profile);
            RecordingPort port = new RecordingPort(8_000, true);
            backend.installStreamedMusicPort(port);
            backend.playStreamedMusicOrElse(1, () -> fail("base expected"));
            backend.update();
            backend.playStreamedMusicOrElse(profile.getInvincibilityMusicId(), () -> fail("jingle expected"));
            if (explicitRestore) backend.restoreMusic();
            else backend.endMusicOverride(profile.getInvincibilityMusicId());
            backend.update();
            assertEquals(1, port.logicalMusicId);
            assertTrue(backend.captureLogicalSnapshot().overrideStack().isEmpty());
        }
    }

    @Test
    void missingStreamedJingleRestoreWithSavedBaseNeverLeavesSfxBlocked() {
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        InstrumentedBackend backend = new InstrumentedBackend();
        backend.setAudioProfile(profile);
        RecordingPort port = new RecordingPort(8_000, true);
        backend.installStreamedMusicPort(port);
        backend.playStreamedMusicOrElse(1, () -> fail("base expected"));
        backend.update();
        backend.playStreamedMusicOrElse(profile.getExtraLifeMusicId(), () -> fail("1-up expected"));
        backend.update();
        AudioBackendLogicalSnapshot snapshot = backend.captureLogicalSnapshot();
        assertFalse(snapshot.overrideStack().isEmpty());
        assertTrue(snapshot.sfxBlocked());
        port.restoreAllowed = false;

        backend.restoreLogicalSnapshot(snapshot);

        assertFalse(backend.captureLogicalSnapshot().sfxBlocked());
        assertNull(backend.captureLogicalSnapshot().streamedMusic());
    }

    private static final class RecordingPort implements StreamedMusicPort {
        private final int rate;
        private final boolean resolves;
        private int playCount;
        private int resolveCount;
        private int closeCount;
        private short sample;
        private boolean source;
        private int pauseMask;
        private int mixCount;
        private int logicalMusicId;
        private double position;
        private double playbackRate = 1;
        private StreamedMusicPort.FadeState fade = StreamedMusicPort.FadeState.idle();
        private boolean restoreAllowed = true;
        private Object forbiddenLock;
        private boolean calledUnderForbiddenLock;
        private boolean throwRateAfterClose;
        private int openSfxCount;
        private int closedSfxCount;
        private short sfxSample;

        private RecordingPort(int rate, boolean resolves) {
            this.rate = rate;
            this.resolves = resolves;
        }

        @Override public int outputRate() {
            checkLock();
            if (throwRateAfterClose && closeCount > 0) throw new IllegalStateException("closed");
            return rate;
        }
        @Override public boolean hasStockOverride(int musicId) { resolveCount++; return resolves; }
        @Override public boolean isCurrentStockOverride(int musicId) {
            return source && logicalMusicId == musicId;
        }
        @Override public void playStockOverride(int musicId) {
            playCount++;
            if (source && logicalMusicId == musicId) return;
            source = true; logicalMusicId = musicId; position = 0;
        }
        @Override public boolean hasTrack(TrackRef track) {
            return resolves && new TrackRef("owner", "track").equals(track);
        }
        @Override public void playTrack(TrackRef track) {
            if (!hasTrack(track)) throw new IllegalArgumentException("missing: " + track);
            playCount++;
            source = true; logicalMusicId = -1; position = 0;
        }
        @Override public boolean hasSfx(SfxRef sfx) {
            return resolves && new SfxRef("owner", "effect").equals(sfx);
        }
        @Override public OneShot openSfx(SfxRef sfx) {
            if (!hasSfx(sfx)) throw new IllegalArgumentException("missing: " + sfx);
            openSfxCount++;
            return new OneShot() {
                private boolean complete;
                private boolean closed;
                @Override public void mixInto(short[] output, int frames) {
                    if (closed) throw new IllegalStateException("closed");
                    int left = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, output[0] + sfxSample));
                    int right = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, output[1] + sfxSample));
                    output[0] = (short) left;
                    output[1] = (short) right;
                    complete = true;
                }
                @Override public boolean complete() { return complete; }
                @Override public void close() { if (!closed) { closed = true; closedSfxCount++; } }
            };
        }
        @Override public boolean hasSource() { checkLock(); return source; }
        @Override public int mixInto(short[] output, int frames) {
            checkLock();
            if (!source || pauseMask != 0) return 0;
            mixCount++;
            position += frames * playbackRate;
            for (int i = 0; i < frames * 2; i++) output[i] += sample;
            return frames;
        }
        @Override public void pause(int reason) { pauseMask |= reason; }
        @Override public void resume(int reason) { pauseMask &= ~reason; }
        @Override public void fadeOut(int steps, int stepDelay) {
            fade = new FadeState(fade.gain(), steps, stepDelay, stepDelay, -fade.gain() / steps);
        }
        @Override public void fadeIn(int steps, int stepDelay) {
            fade = new FadeState(0, steps, stepDelay, stepDelay, 1.0f / steps);
        }
        @Override public void advanceFade() {
            if (fade.remainingSteps() == 0 || pauseMask != 0) return;
            int delay = fade.delayCounter();
            if (delay > 0) {
                fade = new FadeState(fade.gain(), fade.remainingSteps(), fade.stepDelay(),
                        delay - 1, fade.stepAmount());
                return;
            }
            int remaining = fade.remainingSteps() - 1;
            float gain = remaining == 0 ? (fade.stepAmount() > 0 ? 1 : 0)
                    : fade.gain() + fade.stepAmount();
            fade = remaining == 0 ? FadeState.idle()
                    : new FadeState(gain, remaining, fade.stepDelay(), fade.stepDelay(), fade.stepAmount());
        }
        @Override public boolean fadeActive() { return fade.remainingSteps() > 0; }
        @Override public boolean fadeAtFullGain() { return !fadeActive() && fade.gain() == 1; }
        @Override public void setSpeedMultiplier(int multiplier) { playbackRate = multiplier > 1 ? 1.25 : 1; }
        @Override public void stop() { source = false; fade = FadeState.idle(); }
        @Override public void reset() { source = false; pauseMask = 0; fade = FadeState.idle(); playbackRate = 1; }
        @Override public Optional<State> captureState() {
            checkLock();
            return source ? Optional.of(new State(new TrackRef("owner", "track"), logicalMusicId,
                    position, pauseMask, fade, playbackRate)) : Optional.empty();
        }
        @Override public boolean restoreState(State state) {
            checkLock();
            if (!restoreAllowed) return false;
            source = true; logicalMusicId = state.logicalMusicId(); position = state.sourceFramePosition();
            pauseMask = state.pauseMask(); fade = state.fade(); playbackRate = state.rate(); return true;
        }
        @Override public void close() { closeCount++; }
        private void checkLock() {
            if (forbiddenLock != null && Thread.holdsLock(forbiddenLock)) calledUnderForbiddenLock = true;
        }
    }

    private static class InstrumentedBackend extends AbstractSmpsAudioBackend {
        private short[] uploaded;
        private int uploadRate;
        private int bypassBeginCount;
        private int bypassEndCount;
        protected InstrumentedBackend() {
            this(false);
        }

        private InstrumentedBackend(boolean internalRate) {
            super(config(internalRate), null);
        }

        private static SonicConfigurationService config(boolean internalRate) {
            SonicConfigurationService config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, internalRate);
            return config;
        }

        @Override protected int getDeviceSampleRate() { return 8_000; }
        @Override protected void hookInitDevice() { }
        @Override protected void hookDestroyDevice() { }
        @Override protected void hookStartStream() { }
        @Override protected void hookStopStreamSource() { }
        @Override protected void hookUpdateStream() {
            if (hasPresentationWork()) fillBuffer(1);
        }
        @Override protected void hookStopAndClearMusicSource() { }
        @Override protected void hookStopAndUnqueueAllMusicBuffers() { }
        @Override protected void hookStopAndClearAllMusicBuffers() { }
        @Override protected void hookRestartStreamIfDry() { }
        @Override protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) {
            uploaded = pcm.clone();
            uploadRate = sampleRate;
        }
        @Override protected void hookPlayWavSfx(String sfxName, float pitch) { }
        @Override protected void hookStopAndDeleteWavSfxSources() { }
        @Override protected void hookCleanupStoppedWavSfx() { }
        @Override protected void hookPause() { }
        @Override protected void hookResume() { }
        private Object streamLockForTesting() { return streamLock; }
        @Override public void beginStreamedOverrideReplayBypass() {
            super.beginStreamedOverrideReplayBypass();
            bypassBeginCount++;
        }
        @Override public void endStreamedOverrideReplayBypass() {
            super.endStreamedOverrideReplayBypass();
            bypassEndCount++;
        }
    }

    private static final class FailingInitBackend extends InstrumentedBackend {
        @Override protected void hookInitDevice() { throw new IllegalStateException("expected init failure"); }
    }

    private static final class InstrumentedLwjglBackend extends LWJGLAudioBackend {
        private final int[] ids = { 11, 12, 13 };
        private boolean ready;
        private int queued;
        private int initialQueueCount;
        private int uploadCount;
        private short firstSample;
        private int lastRate;
        private int allBufferClearCount;

        private InstrumentedLwjglBackend() {
            super(SonicConfigurationService.createStandalone());
        }

        @Override protected int getDeviceSampleRate() { return 8_000; }
        @Override protected void ensurePresentationBuffers() { ready = true; }
        @Override protected int[] presentationBufferIds() { return ids; }
        @Override protected boolean presentationBuffersReady() { return ready; }
        @Override protected void queueInitialPresentationBuffers(int[] ids) {
            queued = ids.length;
            initialQueueCount = ids.length;
        }
        @Override protected int presentationSourceState() { return 0x1012; }
        @Override protected int queuedPresentationBufferCount() { return queued; }
        @Override protected int processedPresentationBufferCount() { return 0; }
        @Override protected int unqueuePresentationBuffer() { queued--; return ids[0]; }
        @Override protected void queuePresentationBuffer(int bufferId) { queued++; }
        @Override protected void playPresentationSource() { }
        @Override protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) {
            uploadCount++;
            firstSample = pcm[0];
            lastRate = sampleRate;
        }
        @Override protected void hookStopAndClearAllMusicBuffers() { queued = 0; allBufferClearCount++; }
        @Override protected void hookStopAndClearMusicSource() { }
        @Override protected void hookDestroyDevice() { }
        @Override protected void hookCleanupStoppedWavSfx() { }
    }

    private static final class IdSmpsData extends com.openggf.audio.smps.AbstractSmpsData {
        private IdSmpsData(int id) { super(new byte[0], id); }
        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }
}
