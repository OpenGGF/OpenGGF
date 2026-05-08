package com.openggf.audio;

import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
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
    void pausedFrameStepHookDelegatesToLegacyUpdateForTier0() {
        audio.advancePausedFrameStepAudio();

        assertEquals(java.util.List.of("update"), backend.calls);
    }
}
