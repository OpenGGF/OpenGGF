package com.openggf.audio.rewind;

import java.util.EnumSet;
import java.util.Objects;

@com.openggf.game.ModApi
public record AudioSourceDescriptor(
        Route route,
        Integer id,
        String name,
        String donorGameId) {

    @com.openggf.game.ModApi
    public enum Route {
        BASE_MUSIC_ID,
        BASE_SFX_ID,
        BASE_SFX_NAME,
        DONOR_MUSIC_ID,
        DONOR_SFX_ID,
        FALLBACK_MUSIC_ID,
        FALLBACK_SFX_NAME,
        STREAMED_TRACK,
        STREAMED_SFX,
        SYSTEM_COMMAND
    }

    public AudioSourceDescriptor {
        Objects.requireNonNull(route, "route");
    }

    public static AudioSourceDescriptor baseMusic(int musicId) {
        return new AudioSourceDescriptor(Route.BASE_MUSIC_ID, musicId, null, null);
    }

    public static AudioSourceDescriptor baseSfx(int sfxId) {
        return new AudioSourceDescriptor(Route.BASE_SFX_ID, sfxId, null, null);
    }

    public static AudioSourceDescriptor baseNamedSfx(String name) {
        return new AudioSourceDescriptor(Route.BASE_SFX_NAME, null,
                Objects.requireNonNull(name, "name"), null);
    }

    public static AudioSourceDescriptor donorMusic(String donorGameId, int musicId) {
        return new AudioSourceDescriptor(Route.DONOR_MUSIC_ID, musicId, null,
                Objects.requireNonNull(donorGameId, "donorGameId"));
    }

    public static AudioSourceDescriptor donorSfx(String donorGameId, int sfxId) {
        return new AudioSourceDescriptor(Route.DONOR_SFX_ID, sfxId, null,
                Objects.requireNonNull(donorGameId, "donorGameId"));
    }

    public static AudioSourceDescriptor fallbackMusic(int musicId) {
        return new AudioSourceDescriptor(Route.FALLBACK_MUSIC_ID, musicId, null, null);
    }

    public static AudioSourceDescriptor fallbackSfx(String name) {
        return new AudioSourceDescriptor(Route.FALLBACK_SFX_NAME, null,
                Objects.requireNonNull(name, "name"), null);
    }

    /**
     * A creator-supplied streamed music override. {@code name} is the
     * owner-scoped track key; creator content never allocates numeric ids, so
     * there is no id component.
     */
    public static AudioSourceDescriptor streamedTrack(String trackKey) {
        return new AudioSourceDescriptor(Route.STREAMED_TRACK, null,
                Objects.requireNonNull(trackKey, "trackKey"), null);
    }

    /** A creator-supplied presentation-only streamed one-shot. */
    public static AudioSourceDescriptor streamedSfx(String sfxKey) {
        return new AudioSourceDescriptor(Route.STREAMED_SFX, null,
                Objects.requireNonNull(sfxKey, "sfxKey"), null);
    }

    public static AudioSourceDescriptor systemCommand(int commandId) {
        return new AudioSourceDescriptor(Route.SYSTEM_COMMAND, commandId, null, null);
    }

    public static EnumSet<Route> supportedRoutes() {
        return EnumSet.allOf(Route.class);
    }
}
