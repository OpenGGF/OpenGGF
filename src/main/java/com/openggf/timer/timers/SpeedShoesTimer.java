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
    // ROM Obj01_ChkShoes decrements speedshoes_time from Sonic_Display, after
    // Sonic_Move. The engine TimerManager runs before sprite physics and clears
    // when the decremented tick reaches zero, so add one phase tick plus the
    // terminal tick that the ROM still applies to the just-finished movement.
    public static final int DURATION_FRAMES = ROM_DURATION_FRAMES + 2;

    private final AbstractPlayableSprite sprite;

    public SpeedShoesTimer(String code, AbstractPlayableSprite sprite) {
        super(code, DURATION_FRAMES);
        this.sprite = sprite;
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
