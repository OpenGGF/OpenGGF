package com.openggf.mods.scene;

/**
 * Plays ROM music/effects by driver ID and this scene owner's declared creator SFX.
 * For Sonic 3 &amp; Knuckles, music 0x01 is Angel Island Act 1 and SFX 0x33 is the ring sound.
 */
@com.openggf.game.ModApi
public interface SceneAudio {
    void playMusic(int musicId);

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
