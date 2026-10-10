package com.openggf.mods.scene;

/**
 * Plays ROM music/effects by driver ID and this scene owner's declared creator SFX.
 * For Sonic 3 &amp; Knuckles, music 0x01 is Angel Island Act 1 and SFX 0x33 is the ring sound.
 */
@com.openggf.game.ModApi
public interface SceneAudio {
    void playMusic(int musicId);

    /**
     * Plays a song from any supplied stock ROM ({@code "s1"}, {@code "s2"} or {@code "s3k"})
     * as the base game's music. The running game's own songs take their normal route.
     * Another game's song plays through the same sound driver by its own game's
     * sequencer rules, as cross-game donation plays donor music, so it loops at its own
     * loop point and this scene's sound effects stay audible over it. It is the current
     * music: {@code playMusic}, {@code stopMusic} and {@code fadeOutMusic} replace, stop
     * or fade it. Like any song change, starting it stops active sound effects.
     * Leaving the scene stops another game's song that is still playing.
     *
     * @return false, changing nothing, when that game's ROM was not supplied or has no
     *         such song; hosts without this capability also return false
     * @throws IllegalArgumentException for an unknown game id or a negative music id
     */
    default boolean playMusic(String gameId, int musicId) {
        java.util.Objects.requireNonNull(gameId, "gameId");
        return false;
    }

    /**
     * Sets ROM music to 25–100 percent of its original pace (100 is normal).
     * Applies to current and subsequent songs, including native acts entered by
     * a retained scene. Note pitch, DAC sample pitch, SFX and gameplay clocks
     * are unchanged. The production host restores normal pacing on final exit.
     * Creator WAV/OGG playback is unaffected. Hosts without this capability
     * may retain the validated silent default.
     */
    default void setMusicTempoPercent(int percent) {
        if (percent < 25 || percent > 100)
            throw new IllegalArgumentException("music tempo must be between 25 and 100 percent");
    }

    void playSfx(int sfxId);

    /**
     * Plays a WAV/OGG SFX declared in this scene owner's audio manifest by local id.
     * The host supplies the owner; this cannot address another mod's assets.
     * Returns false when the clip is unavailable or playback is suppressed (including
     * headless contexts without audio). Existing contexts may retain the silent default.
     * Invalid local ids throw {@link IllegalArgumentException}.
     */
    default boolean playSfx(String localName) {
        com.openggf.game.ModKeySyntax.requireLocalName(localName);
        return false;
    }

    /** Fades the current music out over about a second. */
    void fadeOutMusic();

    void stopMusic();
}
