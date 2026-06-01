package com.openggf.timer.timers;

import com.openggf.audio.GameAudioProfile;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.timer.AbstractTimer;

/**
 * Timer for the Speed Shoes power-up effect.
 * Duration: 1200 movement frames (20 seconds @ 60fps) per SPG Sonic 2.
 * When timer expires, speed shoes are deactivated and music slows back down.
 *
 * <p>The ROM decrement cadence is per-game (see
 * {@link com.openggf.game.PhysicsFeatureSet#speedShoesTimerDecimation()}):
 * S1/S2 use a per-frame word timer counting from {@code 0x4B0}
 * (s2.asm:36008-36025); S3K uses a byte timer counting from
 * {@code (20*60)/8 = 150} and decremented only on every 8th level frame —
 * {@code Sonic_ChkShoes} gates {@code subq.b} on
 * {@code (Level_frame_counter+1) & 7 == 0} (sonic3k.asm:22072-22078; init
 * sonic3k.asm:40818). Both expire after 1200 wall-clock frames.
 */
public class SpeedShoesTimer extends AbstractTimer {
    public static final int ROM_DURATION_FRAMES = 0x4B0; // speedshoes_time(a0)

    /**
     * Offset aligning the engine level frame counter to the ROM
     * {@code Level_frame_counter} the S3K decrement gate reads.
     *
     * <p>ROM decrements when {@code (Level_frame_counter+1) & 7 == 0} (i.e.
     * {@code Level_frame_counter & 7 == 7}). The engine's {@code LevelManager}
     * frame counter, read at the pre-physics {@code TimerManager.update()}
     * point where this timer decrements, leads ROM {@code Level_frame_counter}
     * by 2 (mod 8) — a constant seed-phase offset. So the engine decrements when
     * {@code frameCounter & 7 == 1}, i.e. {@code (frameCounter + 7) & 7 == 0}.
     *
     * <p>This offset is measured against compared trace fields (S3K CNZ speed
     * shoes via x_speed); the {@code AizFlippingBridge} {@code (frameCounter+3)&7}
     * gate is NOT a valid phase reference because it only drives an SFX, which
     * trace replay does not compare.
     */
    static final int LEVEL_FRAME_PHASE_OFFSET = 7;

    private final AbstractPlayableSprite sprite;
    /** Decrement cadence in level frames; a power of two (1 or 8). */
    private final int decimation;

    public SpeedShoesTimer(String code, AbstractPlayableSprite sprite) {
        super(code, durationTicks(sprite));
        this.sprite = sprite;
        this.decimation = decimationFor(sprite);
    }

    private static int decimationFor(AbstractPlayableSprite sprite) {
        int d = sprite != null && sprite.getPhysicsFeatureSet() != null
                ? sprite.getPhysicsFeatureSet().speedShoesTimerDecimation()
                : 1;
        return d < 1 ? 1 : d;
    }

    private static int durationTicks(AbstractPlayableSprite sprite) {
        int extraTicks = sprite != null && sprite.getPhysicsFeatureSet() != null
                ? sprite.getPhysicsFeatureSet().speedShoesTimerPrePhysicsExtraTicks()
                : 0;
        return (ROM_DURATION_FRAMES / decimationFor(sprite)) + extraTicks;
    }

    @Override
    public void decrementTick() {
        // ROM decrements only on aligned level frames (every `decimation`-th
        // frame). For decimation == 1 this is every frame, so S1/S2 are
        // unchanged. Gate on the global level frame counter so expiry lands on
        // the ROM-accurate frame (phase-correct for trace parity). With no level
        // frame context (a unit harness) fall back to per-frame.
        LevelManager levelManager = sprite != null ? sprite.currentLevelManagerIfAvailable() : null;
        if (levelManager == null
                || isDecrementFrame(levelManager.getFrameCounter(), decimation)) {
            super.decrementTick();
        }
    }

    /**
     * Whether the timer decrements on the given level frame for a decimation.
     * For {@code decimation <= 1} every frame qualifies; otherwise only frames
     * where {@code (frame + LEVEL_FRAME_PHASE_OFFSET) & (decimation-1) == 0} do.
     * {@code decimation} is assumed to be a power of two.
     */
    static boolean isDecrementFrame(int frame, int decimation) {
        if (decimation <= 1) {
            return true;
        }
        return ((frame + LEVEL_FRAME_PHASE_OFFSET) & (decimation - 1)) == 0;
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
