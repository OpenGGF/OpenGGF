package com.openggf.sprites.managers;

import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

/**
 * Handles spindash dust animation and drawing.
 */
public class SpindashDustController {
    private static final int[] DASH_FRAMES = { 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10 };
    private static final int FRAME_DELAY = 1;
    private static final int TAILS_Y_OFFSET = -4;

    // Water-entry/exit splash frames from the shared Sonic_Dust art
    // (Ani_obj08 byte_12CA6: 0..9, delay 2 -> 3 ticks/frame). ROM writes
    // anim=1 into the fixed Sonic_Dust object (sonic3k.asm:22241,22281) rather
    // than spawning a FindFreeObj slot, so the splash rides this controller.
    private static final int[] SPLASH_FRAMES = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int SPLASH_FRAME_DELAY = 2;

    private final AbstractPlayableSprite sprite;
    private final PlayerSpriteRenderer renderer;
    private int frameIndex;
    private int frameTick;
    private int currentFrame;
    private boolean activeLastTick;

    private boolean splashActive;
    private int splashX;
    private int splashY;
    private boolean splashFacingLeft;
    private int splashFrameIndex;
    private int splashTick;

    public SpindashDustController(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer) {
        this.sprite = sprite;
        this.renderer = renderer;
        this.frameIndex = 0;
        this.frameTick = 0;
        this.currentFrame = DASH_FRAMES[0];
        this.activeLastTick = false;
    }

    /**
     * Starts the water-entry/exit splash animation on the fixed dust object.
     *
     * <p>ROM: {@code Sonic_InWater}/{@code Sonic_OutWater} write
     * {@code move.w #1<<8,anim(a6)} into the existing Sonic_Dust object. Routing
     * the splash through this controller keeps it slot-free (no FindFreeObj),
     * matching ROM Load_Sprites pressure.
     *
     * @param x          splash X (player centre)
     * @param waterY     water-surface Y
     * @param facingLeft whether the player faced left
     */
    public void triggerSplash(int x, int waterY, boolean facingLeft) {
        splashActive = true;
        splashX = x;
        splashY = waterY;
        splashFacingLeft = facingLeft;
        splashFrameIndex = 0;
        splashTick = SPLASH_FRAME_DELAY;
    }

    public void update() {
        updateSplash();
        boolean active = isActive();
        if (!active) {
            reset();
            return;
        }
        if (!activeLastTick) {
            activeLastTick = true;
            frameIndex = 0;
            frameTick = 0;
        }
        int duration = frameTick - 1;
        boolean advance = duration < 0;
        if (advance) {
            duration = FRAME_DELAY;
        }
        frameTick = duration;
        currentFrame = DASH_FRAMES[frameIndex];
        if (advance) {
            frameIndex = (frameIndex + 1) % DASH_FRAMES.length;
        }
    }

    public void draw() {
        drawSplash();
        if (!isActive() || renderer == null) {
            return;
        }
        int originX = sprite.getRenderCentreX();
        int originY = sprite.getRenderCentreY();
        if (sprite instanceof Tails) {
            originY += TAILS_Y_OFFSET;
        }
        boolean hFlip = Direction.LEFT.equals(sprite.getDirection());
        renderer.drawFrame(currentFrame, originX, originY, hFlip, false);
    }

    /** Test/diagnostic: whether the water splash animation is currently playing. */
    public boolean isSplashActive() {
        return splashActive;
    }

    private void updateSplash() {
        if (!splashActive) {
            return;
        }
        splashTick--;
        if (splashTick < 0) {
            splashTick = SPLASH_FRAME_DELAY;
            splashFrameIndex++;
            if (splashFrameIndex >= SPLASH_FRAMES.length) {
                splashActive = false;
            }
        }
    }

    private void drawSplash() {
        if (!splashActive || renderer == null
                || splashFrameIndex < 0 || splashFrameIndex >= SPLASH_FRAMES.length) {
            return;
        }
        renderer.drawFrame(SPLASH_FRAMES[splashFrameIndex], splashX, splashY, splashFacingLeft, false);
    }

    private boolean isActive() {
        return sprite != null && sprite.getSpindash() && !sprite.getAir();
    }

    private void reset() {
        activeLastTick = false;
        frameIndex = 0;
        frameTick = 0;
        currentFrame = DASH_FRAMES[0];
    }

    public RewindState captureRewindState() {
        return new RewindState(frameIndex, frameTick, currentFrame, activeLastTick);
    }

    public void restoreRewindState(RewindState state) {
        if (state == null) {
            reset();
            return;
        }
        frameIndex = state.frameIndex();
        frameTick = state.frameTick();
        currentFrame = state.currentFrame();
        activeLastTick = state.activeLastTick();
    }

    public record RewindState(
            int frameIndex,
            int frameTick,
            int currentFrame,
            boolean activeLastTick
    ) {}

    /**
     * Returns the renderer used for dust/splash animation.
     * Used by SplashObjectInstance to share the same art assets.
     */
    public PlayerSpriteRenderer getRenderer() {
        return renderer;
    }
}
