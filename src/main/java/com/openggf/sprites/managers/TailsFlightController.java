package com.openggf.sprites.managers;

import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.CanonicalAnimation;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/** Shared ROM-accurate vertical flight/swim routine for Tails. */
@com.openggf.game.ModApi
public final class TailsFlightController {
    public static final int FLIGHT_TIME = (8 * 60) / 2;

    private final AbstractPlayableSprite sprite;

    public TailsFlightController(AbstractPlayableSprite sprite) {
        this.sprite = Objects.requireNonNull(sprite, "sprite");
    }

    public boolean isActive() {
        return sprite.getDoubleJumpFlag() != 0;
    }

    public void activate() {
        int oldCentreY = sprite.getCentreY();
        int oldYRadius = sprite.getYRadius();
        int defaultYRadius = sprite.getStandYRadius();
        if (sprite.getRolling()) {
            sprite.setRolling(false);
            // Tails_Test_For_Flight loc_1515C (sonic3k.asm:28655-28672) puts
            // y_radius - default_y_radius in d1, tests Reverse_gravity_flag, and on the
            // set side runs `neg.w d0` -- the wrong register, holding nothing this site
            // uses -- before `add.w d1,y_pos(a0)`. The unroll adjustment is therefore NOT
            // inverted under reverse gravity, unlike every other unroll site
            // (loc_14DA2 :28233, loc_14FC4 :28500, loc_1527C :28748, which all negate d0
            // because d0 is the adjustment there). This build is FixBugs = 0, so the
            // write stays unconditional: the fixed branch would negate this delta and
            // keep an inverted Tails' head against the ceiling, where the shipped branch
            // shifts him by twice the radius difference instead.
            sprite.setCentreYPreserveSubpixel(
                    (short) (oldCentreY + oldYRadius - defaultYRadius));
        }
        sprite.setRollingJump(false);
        sprite.setDoubleJumpFlag(1);
        sprite.setDoubleJumpProperty((byte) FLIGHT_TIME);
        applyAnimation(false);
    }

    public void updateVertical(boolean jumpPressed, boolean carryingMainCharacter,
                               int romVisibleLevelFrameCounter) {
        int flightTime = sprite.getDoubleJumpProperty() & 0xFF;
        if ((romVisibleLevelFrameCounter & 1) != 0 && flightTime != 0) {
            flightTime = (flightTime - 1) & 0xFF;
            sprite.setDoubleJumpProperty((byte) flightTime);
        }

        int state = sprite.getDoubleJumpFlag() & 0xFF;
        int ySpeed = sprite.getYSpeed();
        if (state == 1) {
            if (jumpPressed && ySpeed >= -0x100 && flightTime != 0
                    && !(sprite.isInWater() && carryingMainCharacter)) {
                state = 2;
            }
            ySpeed += 0x08;
        } else {
            if (ySpeed < -0x100) {
                state = 1;
            } else {
                ySpeed -= 0x20;
                state = (state + 1) & 0xFF;
                if (state == 0x20) {
                    state = 1;
                }
            }
        }

        Camera camera = sprite.currentCamera();
        if (camera != null && ySpeed < 0) {
            int cameraMinY = camera.getMinY() & 0xFFFF;
            int playerY = sprite.getCentreY() & 0xFFFF;
            if (playerY <= cameraMinY + 0x10) {
                ySpeed = 0;
            }
        }

        sprite.setDoubleJumpFlag(state);
        sprite.setYSpeed((short) ySpeed);
        applyAnimation(carryingMainCharacter);
        applyAudio(romVisibleLevelFrameCounter);
    }

    public void clear() {
        sprite.setDoubleJumpFlag(0);
        sprite.setDoubleJumpProperty((byte) 0);
    }

    private void applyAnimation(boolean carryingMainCharacter) {
        CanonicalAnimation animation;
        if (sprite.isInWater()) {
            if ((sprite.getDoubleJumpProperty() & 0xFF) == 0) {
                animation = CanonicalAnimation.TAILS_SWIM_TIRED;
            } else if (carryingMainCharacter) {
                animation = CanonicalAnimation.TAILS_SWIM_CARRY;
            } else if (sprite.getYSpeed() < 0) {
                animation = CanonicalAnimation.TAILS_SWIM_ASCEND;
            } else {
                animation = CanonicalAnimation.TAILS_SWIM;
            }
        } else if ((sprite.getDoubleJumpProperty() & 0xFF) == 0) {
            animation = CanonicalAnimation.TAILS_FLY_TIRED;
        } else if (carryingMainCharacter) {
            animation = sprite.getYSpeed() < 0
                    ? CanonicalAnimation.TAILS_FLY_CARRY_ASCEND
                    : CanonicalAnimation.TAILS_FLY_CARRY;
        } else {
            animation = sprite.getYSpeed() < 0
                    ? CanonicalAnimation.TAILS_FLY_ASCEND
                    : CanonicalAnimation.TAILS_FLY;
        }

        int animationId = sprite.resolveAnimationId(animation);
        if (animationId >= 0) {
            sprite.setAnimationId(animationId);
            if (sprite.getForcedAnimationId() >= 0) {
                sprite.setForcedAnimationId(animationId);
            }
        }
    }

    private void applyAudio(int romVisibleLevelFrameCounter) {
        if (sprite.isInWater() || ((romVisibleLevelFrameCounter + 8) & 0x0F) != 0
                || !isOnScreen()) {
            return;
        }
        GameSound sound = (sprite.getDoubleJumpProperty() & 0xFF) == 0
                ? GameSound.TAILS_FLY_TIRED
                : GameSound.TAILS_FLYING;
        sprite.currentAudioManager().playSfx(sound);
    }

    private boolean isOnScreen() {
        if (sprite.hasRenderFlagOnScreenState()) {
            return sprite.isRenderFlagOnScreen();
        }
        Camera camera = sprite.currentCamera();
        return camera != null && camera.isOnScreen(sprite);
    }
}
