package com.openggf.sprites.playable;

import com.openggf.audio.GameSound;

/** Internal ability-sound routing for an explicitly owned native performer. Ordinary players keep their native route. */
public interface PlayerAbilityAudio {
    void playAbilitySound(GameSound sound);

    static void play(AbstractPlayableSprite player, GameSound sound) {
        play(player,sound,player.currentAudioManager());
    }

    static void play(AbstractPlayableSprite player, GameSound sound, com.openggf.audio.AudioManager ordinaryAudio) {
        if (player.getPowerUpSpawner() instanceof PlayerAbilityAudio owner) owner.playAbilitySound(sound);
        else ordinaryAudio.playSfx(sound);
    }
}
