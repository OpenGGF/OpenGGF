package com.openggf.audio;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.runtime.DeterministicAudioRuntime;
import com.openggf.audio.runtime.FrameAudioMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

class TestAudioManagerRewindSuppression {
    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void suppressesPlaybackCommandsInsideReplayScope() {
        AudioTestFixtures.StubSmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        loader.musicResults.put(1, new AudioTestFixtures.StubSmpsData("music"));
        loader.sfxResults.put(2, new AudioTestFixtures.StubSmpsData("sfx"));
        loader.namedSfxResults.put("JUMP", new AudioTestFixtures.StubSmpsData("jump"));
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                loader, 0xF0, 0xF1, GameAudioProfile.SpeedMode.FRAME_MULTIPLY));
        audio.setRom(null);
        audio.setSoundMap(new EnumMap<>(GameSound.class));

        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.playMusic(1);
            audio.playMusic(0xF0);
            audio.playMusic(0xF1);
            audio.playSfx("JUMP");
            audio.playSfx(GameSound.JUMP);
            audio.playSfx(2);
            audio.playDonorSfx("s3k", 7);
            audio.playDonorMusic("s3k", 9);
            audio.fadeOutMusic();
            audio.fadeOutMusic(8, 2);
            audio.stopAllSfx();
            audio.stopMusic();
            audio.endMusicOverride(5);
            audio.changeMusicTempo(6);
            audio.restoreMusic();
            audio.setSpeedShoes(false);
            audio.setSpeedMultiplier(1);
        }

        assertEquals(0, backend.totalCalls(), "suppressed replay must not dispatch live backend commands");
    }

    @Test
    void suppressionScopeNestsUntilOuterScopeCloses() {
        AudioReplayScope outer = audio.beginRewindReplay(8, 4, AudioReplayReason.STEP_BACKWARD);
        AudioReplayScope inner = audio.beginRewindReplay(4, 3, AudioReplayReason.SEGMENT_EXPANSION);

        assertTrue(audio.isRewindReplaySuppressed());
        inner.close();
        assertTrue(audio.isRewindReplaySuppressed(), "inner close must not disable outer suppression");
        audio.playSfx("INNER_CLOSED");
        assertEquals(0, backend.totalCalls());

        outer.close();
        assertFalse(audio.isRewindReplaySuppressed());
        audio.playSfx("AUDIBLE");
        assertEquals(1, backend.totalCalls());
    }

    @Test
    void replayScopeCloseIsIdempotent() {
        AudioReplayScope scope = audio.beginRewindReplay(5, 2, AudioReplayReason.SEEK);
        scope.close();
        scope.close();

        assertFalse(audio.isRewindReplaySuppressed());
        audio.playSfx("AUDIBLE");
        assertEquals(1, backend.totalCalls());
    }

    @Test
    void suppressedRingSoundDoesNotAdvanceRingAlternation() {
        audio.setSoundMap(new EnumMap<>(GameSound.class));

        try (AudioReplayScope ignored = audio.beginRewindReplay(3, 1, AudioReplayReason.SEEK)) {
            audio.playSfx(GameSound.RING);
        }

        audio.playSfx(GameSound.RING);

        assertEquals(1, backend.totalCalls());
        assertTrue(backend.calls.get(0).contains("RING_LEFT"),
                "first audible ring after suppressed replay must still be left");
    }

    @Test
    void suppressionDoesNotBlockSetupState() {
        AudioTestFixtures.StubSmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        loader.sfxResults.put(0x90, new AudioTestFixtures.StubSmpsData("jump"));

        try (AudioReplayScope ignored = audio.beginRewindReplay(3, 1, AudioReplayReason.SEEK)) {
            audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader));
            audio.setRom(null);
            EnumMap<GameSound, Integer> map = new EnumMap<>(GameSound.class);
            map.put(GameSound.JUMP, 0x90);
            audio.setSoundMap(map);
        }

        audio.playSfx(GameSound.JUMP);

        assertEquals(1, backend.totalCalls());
        assertTrue(backend.calls.get(0).contains("jump"));
    }

    @Test
    void afterRestorePoliciesUseConservativePresentationCleanup() {
        audio.afterRewindRestore(7, AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE);
        assertEquals(0, backend.totalCalls());

        audio.afterRewindRestore(7, AudioPresentationPolicy.STOP_TRANSIENT_SFX_RESYNC_MUSIC);
        assertEquals(java.util.List.of("stopAllSfx", "restoreMusic"), backend.calls);

        backend.clear();
        audio.afterRewindRestore(7, AudioPresentationPolicy.STOP_ALL_PRESENTATION);
        assertEquals(java.util.List.of("stopAllSfx", "stopPlayback"), backend.calls);
    }

    @Test
    void pausedFrameStepHookDoesNotPollPresentationBackend() {
        audio.advancePausedFrameStepAudio();

        assertEquals(java.util.List.of(), backend.calls);
    }

    @Test
    void afterRestoreEndsReversePresentationBeforeCleanupPolicy() {
        ReverseTrackingRuntime runtime = new ReverseTrackingRuntime();
        audio.setDeterministicAudioRuntime(runtime);

        audio.beginReverseAudioPresentation();
        audio.afterRewindRestore(7, AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE);

        assertEquals(1, runtime.beginReverseCalls);
        assertEquals(0, runtime.endReverseCalls,
                "internal step-back restores must not cancel held reverse presentation");
        assertEquals(0, runtime.flushCalls);

        audio.afterRewindRestore(7, AudioPresentationPolicy.STOP_ALL_PRESENTATION);

        assertEquals(1, runtime.beginReverseCalls);
        assertEquals(1, runtime.endReverseCalls);
        assertEquals(1, runtime.flushCalls);
    }

    @Test
    void updateDuringReversePresentationDoesNotRenderNormalFrame() {
        ReverseTrackingRuntime runtime = new ReverseTrackingRuntime();
        audio.setDeterministicAudioRuntime(runtime);

        audio.beginReverseAudioPresentation();
        audio.update();

        assertEquals(0, runtime.advanceCalls,
                "reverse presentation must drain history only, not append forward PCM into history");
        assertEquals(java.util.List.of("update"), backend.calls);
    }

    @Test
    void logicalRestorePreservesPresentationQueueDuringReversePresentation() {
        PreserveFlagBackend preserveBackend = new PreserveFlagBackend();
        audio.setBackend(preserveBackend);

        AudioLogicalSnapshot snapshot = new AudioLogicalSnapshot(
                true,
                0,
                0,
                0,
                AudioBackendLogicalSnapshot.empty(),
                java.util.Set.of(),
                java.util.Set.of());

        audio.beginReverseAudioPresentation();
        audio.restoreLogicalSnapshot(snapshot);

        assertTrue(preserveBackend.lastPreservePresentationQueue);
    }

    @Test
    void reversePresentationLifecycleIsForwardedToBackend() {
        PreserveFlagBackend preserveBackend = new PreserveFlagBackend();
        audio.setBackend(preserveBackend);

        audio.beginReverseAudioPresentation();
        audio.endReverseAudioPresentation();

        assertEquals(1, preserveBackend.beginReverseCalls);
        assertEquals(1, preserveBackend.endReverseCalls);
    }

    private static final class ReverseTrackingRuntime implements DeterministicAudioRuntime {
        private int beginReverseCalls;
        private int endReverseCalls;
        private int flushCalls;
        private int advanceCalls;

        @Override
        public void advanceFrame(long frame, FrameAudioMode mode) {
            advanceCalls++;
        }

        @Override
        public boolean consumesSubmittedCommands() {
            return true;
        }

        @Override
        public void beginReversePresentation() {
            beginReverseCalls++;
        }

        @Override
        public void endReversePresentation() {
            endReverseCalls++;
        }

        @Override
        public void flushPresentationFifo() {
            flushCalls++;
        }
    }

    private static final class PreserveFlagBackend extends NullAudioBackend {
        private boolean lastPreservePresentationQueue;
        private int beginReverseCalls;
        private int endReverseCalls;

        @Override
        public void restoreLogicalSnapshot(
                AudioBackendLogicalSnapshot snapshot,
                SmpsDriverSnapshot.DependencyResolver resolver,
                boolean preservePresentationQueue) {
            lastPreservePresentationQueue = preservePresentationQueue;
        }

        @Override
        public void beginReversePresentation() {
            beginReverseCalls++;
        }

        @Override
        public void endReversePresentation() {
            endReverseCalls++;
        }
    }
}
