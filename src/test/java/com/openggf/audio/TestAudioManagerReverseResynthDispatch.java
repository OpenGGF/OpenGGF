package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAudioManagerReverseResynthDispatch {

    private AudioManager audio;
    private AudioTestFixtures.RecordingAudioBackend backend;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.resetState();
        backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
        AudioTestFixtures.StubSmpsLoader loader = new AudioTestFixtures.StubSmpsLoader();
        loader.namedSfxResults.put("JUMP", new AudioTestFixtures.StubSmpsData("jump"));
        loader.sfxResults.put(0xCC, new AudioTestFixtures.StubSmpsData("spring"));
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                loader, 0xF0, 0xF1, GameAudioProfile.SpeedMode.FRAME_MULTIPLY));
        audio.setRom(null);
        EnumMap<GameSound, Integer> soundMap = new EnumMap<>(GameSound.class);
        soundMap.put(GameSound.SPRING, 0xCC);
        audio.setSoundMap(soundMap);
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    private int countCallsStartingWith(String prefix) {
        return (int) backend.calls.stream().filter(c -> c.startsWith(prefix)).count();
    }

    private int countWavSfxCalls() {
        return (int) backend.calls.stream()
                .filter(c -> c.startsWith("playSfx:") || c.startsWith("playSfxPitch:"))
                .count();
    }

    @Test
    void smpsSfxRouteFiresBackendUnderBothScopes() {
        // SEEK baseline: SMPS-route SFX dispatches to backend.playSfxSmps.
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        int seekCalls = countCallsStartingWith("playSfxSmps");

        // REVERSE_RESYNTH: SMPS-route SFX must also dispatch to backend.playSfxSmps
        // so the chip state evolves to reflect SFX that fired after the keyframe.
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        }
        assertEquals(seekCalls + 1, countCallsStartingWith("playSfxSmps"),
                "REVERSE_RESYNTH SMPS SFX must mutate the chip via backend.playSfxSmps");
    }

    @Test
    void wavFallbackSfxIsSilentNoOpUnderReverseResynth() {
        int playSfxCallsBefore = countWavSfxCalls();
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        }
        assertEquals(playSfxCallsBefore, countWavSfxCalls(),
                "WAV-fallback SFX must be skipped under REVERSE_RESYNTH (spec edge case 9)");
    }

    @Test
    void gameSoundFallbackNameResolvesToSmpsUnderReverseResynth() {
        int smpsCallsBefore = countCallsStartingWith("playSfxSmps");

        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.REVERSE_RESYNTH)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "SPRING", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        }

        assertEquals(smpsCallsBefore + 1, countCallsStartingWith("playSfxSmps"),
                "REVERSE_RESYNTH must replay GameSound fallback names that resolve to ROM SMPS SFX");
        assertEquals(0, countWavSfxCalls(),
                "resolved GameSound fallback must not use the WAV fallback path");
    }

    @Test
    void wavFallbackSfxFiresNormallyUnderSeek() {
        int playSfxCallsBefore = countWavSfxCalls();
        try (AudioReplayScope ignored = audio.beginRewindReplay(10, 4, AudioReplayReason.SEEK)) {
            audio.replayTimelineCommand(new AudioCommand.PlaySfx(
                    -1, "JUMP", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        }
        assertEquals(playSfxCallsBefore + 1, countWavSfxCalls(),
                "WAV-fallback SFX continues to fire normally under non-resynth replay scopes");
    }
}
