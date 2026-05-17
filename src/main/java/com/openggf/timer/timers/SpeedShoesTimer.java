package com.openggf.timer.timers;

import com.openggf.audio.GameAudioProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.timer.AbstractTimer;

/**
 * Timer for the Speed Shoes power-up effect.
 * Duration: 1200 movement frames (20 seconds @ 60fps) per SPG Sonic 2.
 * When timer expires, speed shoes are deactivated and music slows back down.
 */
public class SpeedShoesTimer extends AbstractTimer {
    public static final int ROM_DURATION_FRAMES = 0x4B0; // speedshoes_time(a0)

    private final AbstractPlayableSprite sprite;

    public SpeedShoesTimer(String code, AbstractPlayableSprite sprite) {
        super(code, durationFrames(sprite));
        this.sprite = sprite;
    }

    private static int durationFrames(AbstractPlayableSprite sprite) {
        int extraTicks = sprite != null && sprite.getPhysicsFeatureSet() != null
                ? sprite.getPhysicsFeatureSet().speedShoesTimerPrePhysicsExtraTicks()
                : 0;
        return ROM_DURATION_FRAMES + extraTicks;
    }

    @Override
    public boolean perform() {
        // Deactivate speed shoes on the sprite
        sprite.deactivateSpeedShoes();

        // Slow down the music
        var audioManager = sprite.currentAudioManager();
        GameAudioProfile audioProfile = audioManager.getAudioProfile();
        if (audioProfile != null) {
            audioManager.playMusic(audioProfile.getSpeedShoesOffCommandId());
        }
        return true;
    }
}
