package com.openggf.mods.scene;

/**
 * Plays the base game's music and sound effects by their driver IDs (for Sonic 3 &amp;
 * Knuckles, music 0x01 is Angel Island Act 1 and SFX 0x33 is the ring sound).
 */
@com.openggf.game.ModApi
public interface SceneAudio {
    void playMusic(int musicId);

    void playSfx(int sfxId);

    /** Fades the current music out over about a second. */
    void fadeOutMusic();

    void stopMusic();
}
