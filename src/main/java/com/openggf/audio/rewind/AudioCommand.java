package com.openggf.audio.rewind;

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
                     String donorGameId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record PlayNamespacedMusic(com.openggf.audio.StreamedMusicPort.TrackRef track)
            implements AudioCommand {
        public PlayNamespacedMusic { java.util.Objects.requireNonNull(track, "track"); }
    }

    @com.openggf.game.ModApi
    record PlaySfx(int sfxId, String sfxName, SfxRoute route, float pitch,
                   String donorGameId) implements AudioCommand {}

    @com.openggf.game.ModApi
    record FadeOutMusic(int steps, int delay) implements AudioCommand {}

    @com.openggf.game.ModApi
    record StopMusic() implements AudioCommand {}

    @com.openggf.game.ModApi
    record StopAllSfx() implements AudioCommand {}

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
