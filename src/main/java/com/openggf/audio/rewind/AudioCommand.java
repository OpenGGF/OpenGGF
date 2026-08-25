package com.openggf.audio.rewind;

import com.openggf.audio.GameAudioProfile;

public sealed interface AudioCommand {
    enum MusicRoute {
        BASE_SMPS,
        DONOR_SMPS,
        FALLBACK_WAV,
        SYSTEM_COMMAND
    }

    enum SfxRoute {
        BASE_SMPS_ID,
        BASE_SMPS_NAME,
        DONOR_SMPS,
        FALLBACK_NAME,
        RING_RESOLVED
    }

    enum RestoreCause {
        EXPLICIT,
        SMPS_FADE_IN_COMMAND
    }

    record PlayMusic(int musicId, MusicRoute route, boolean override,
                     String donorGameId,
                     GameAudioProfile.MusicDuringOverridePolicy
                             musicDuringOverridePolicy) implements AudioCommand {
        public PlayMusic(int musicId, MusicRoute route, boolean override,
                         String donorGameId) {
            this(musicId, route, override, donorGameId,
                    GameAudioProfile.MusicDuringOverridePolicy
                            .REPLACE_IMMEDIATELY);
        }
    }

    record PlaySfx(int sfxId, String sfxName, SfxRoute route, float pitch,
                   String donorGameId) implements AudioCommand {}

    record FadeOutMusic(
            int steps,
            int delay,
            GameAudioProfile.MusicDuringOverridePolicy
                    musicDuringOverridePolicy) implements AudioCommand {
        public FadeOutMusic(int steps, int delay) {
            this(steps, delay, GameAudioProfile.MusicDuringOverridePolicy
                    .REPLACE_IMMEDIATELY);
        }
    }

    record StopMusic(
            GameAudioProfile.MusicDuringOverridePolicy
                    musicDuringOverridePolicy) implements AudioCommand {
        public StopMusic() {
            this(GameAudioProfile.MusicDuringOverridePolicy
                    .REPLACE_IMMEDIATELY);
        }
    }

    record StopAllSfx() implements AudioCommand {}

    record EndMusicOverride(int musicId) implements AudioCommand {}

    record RestoreMusic(RestoreCause cause) implements AudioCommand {}

    record SetSpeedShoes(boolean enabled) implements AudioCommand {}

    record SetSpeedMultiplier(int multiplier) implements AudioCommand {}

    record ChangeMusicTempo(int dividingTiming) implements AudioCommand {}

    record ResetRingAlternation(boolean ringLeft) implements AudioCommand {}
}
