package com.openggf.audio.rewind;

import com.openggf.audio.GameAudioProfile;

@com.openggf.game.ModApi
public sealed interface AudioCommand {
    @com.openggf.game.ModApi
    enum MusicRoute {
        BASE_SMPS,
        DONOR_SMPS,
        FALLBACK_WAV,
        SYSTEM_COMMAND
    }

    @com.openggf.game.ModApi
    enum SfxRoute {
        BASE_SMPS_ID,
        BASE_SMPS_NAME,
        DONOR_SMPS,
        FALLBACK_NAME,
        RING_RESOLVED
    }

    @com.openggf.game.ModApi
    enum RestoreCause {
        EXPLICIT,
        SMPS_FADE_IN_COMMAND
    }

    @com.openggf.game.ModApi
    record PlayMusic(int musicId, MusicRoute route, boolean override,
                     String donorGameId,
                     GameAudioProfile.MusicDuringOverridePolicy
                             musicDuringOverridePolicy,
                     GameAudioProfile.MusicOverrideRetriggerPolicy
                             overrideRetriggerPolicy) implements AudioCommand {
        public PlayMusic(int musicId, MusicRoute route, boolean override,
                         String donorGameId,
                         GameAudioProfile.MusicDuringOverridePolicy
                                 musicDuringOverridePolicy) {
            this(musicId, route, override, donorGameId,
                    musicDuringOverridePolicy,
                    GameAudioProfile.MusicOverrideRetriggerPolicy.IGNORE);
        }

        public PlayMusic(int musicId, MusicRoute route, boolean override,
                         String donorGameId) {
            this(musicId, route, override, donorGameId,
                    GameAudioProfile.MusicDuringOverridePolicy
                            .REPLACE_IMMEDIATELY,
                    GameAudioProfile.MusicOverrideRetriggerPolicy.IGNORE);
        }
    }

    @com.openggf.game.ModApi
    record PlayNamespacedMusic(com.openggf.audio.StreamedMusicPort.TrackRef track)
            implements AudioCommand {
        public PlayNamespacedMusic { java.util.Objects.requireNonNull(track, "track"); }
    }

    @com.openggf.game.ModApi
    record PlayNamespacedSfx(com.openggf.audio.StreamedMusicPort.SfxRef sfx)
            implements AudioCommand {
        public PlayNamespacedSfx { java.util.Objects.requireNonNull(sfx, "sfx"); }
    }

    @com.openggf.game.ModApi
    record PlaySfx(int sfxId, String sfxName, SfxRoute route, float pitch,
                   String donorGameId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record FadeOutMusic(
            int steps,
            int delay,
            GameAudioProfile.SystemCommandDuringOverridePolicy
                    systemCommandDuringOverridePolicy) implements AudioCommand {
        public FadeOutMusic(int steps, int delay) {
            this(steps, delay,
                    GameAudioProfile.SystemCommandDuringOverridePolicy.APPLY);
        }
    }

    @com.openggf.game.ModApi
    record StopMusic(
            GameAudioProfile.SystemCommandDuringOverridePolicy
                    systemCommandDuringOverridePolicy) implements AudioCommand {
        public StopMusic() {
            this(GameAudioProfile.SystemCommandDuringOverridePolicy.APPLY);
        }
    }

    @com.openggf.game.ModApi
    record StopAllSfx() implements AudioCommand {}

    @com.openggf.game.ModApi
    record StopSmpsSfx(int sourceCommandId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record SilencePsg(int sourceCommandId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record RetainGlobalStop(int sourceCommandId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record PlaySegaPcm(
            int sourceCommandId, byte[] pcm, int sourceRate)
            implements AudioCommand {
        public PlaySegaPcm {
            pcm = pcm.clone();
            if (sourceRate <= 0) {
                throw new IllegalArgumentException(
                        "sourceRate must be positive");
            }
        }

        @Override
        public byte[] pcm() {
            return pcm.clone();
        }
    }

    @com.openggf.game.ModApi
    record StopRawPcm() implements AudioCommand {}

    @com.openggf.game.ModApi
    record StopSegaPcmAndRetainGlobalStop(int sourceCommandId)
            implements AudioCommand {}

    @com.openggf.game.ModApi
    record ReferenceLimitation(int sourceCommandId, String reason)
            implements AudioCommand {
        public ReferenceLimitation {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "reference limitation reason is required");
            }
        }
    }

    @com.openggf.game.ModApi
    record EndMusicOverride(int musicId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record RestoreMusic(RestoreCause cause) implements AudioCommand {}

    @com.openggf.game.ModApi
    record SetSpeedShoes(boolean enabled) implements AudioCommand {}

    @com.openggf.game.ModApi
    record SetSpeedMultiplier(int multiplier) implements AudioCommand {}

    @com.openggf.game.ModApi
    record ChangeMusicTempo(int dividingTiming) implements AudioCommand {}

    @com.openggf.game.ModApi
    record ResetRingAlternation(boolean ringLeft) implements AudioCommand {}
}
