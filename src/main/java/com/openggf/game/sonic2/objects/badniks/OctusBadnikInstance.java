package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Octus (0x4A) - octopus badnik from Oil Ocean Zone.
 * Waits submerged at ground level, rises when the player approaches within
 * 128 pixels, fires a horizontal bullet at its peak altitude, hovers briefly,
 * then descends back to its starting position.
 * Based on disassembly Obj4A (lines 59860-60026).
 */
public class OctusBadnikInstance extends AbstractBadnikInstance implements RewindRecreatable {

    private enum State {
        WAIT_FOR_PLAYER,    // routine_secondary 0: check player distance
        DELAY_BEFORE_RISE,  // routine_secondary 2: countdown 0x20 frames
        MOVING_UP,          // routine_secondary 4: rise with decel
        HOVERING,           // routine_secondary 6: hover 60 frames, bullet fired
        MOVING_DOWN         // routine_secondary 8: descend back to start
    }

    private static final int COLLISION_SIZE_INDEX = 0x0A; // From disassembly collision_flags $A (s2.asm:59905)
    private static final int DETECT_RANGE = 0x80; // 128 pixels
    private static final int RISE_DELAY = 0x20; // 32 frames
    private static final int INITIAL_Y_VEL = -0x200; // Rise speed
    private static final int Y_ACCEL = 0x10; // Deceleration/acceleration per frame
    private static final int HOVER_DURATION = 60; // 60 frames hovering
    private static final int BULLET_X_VEL = 0x200; // Bullet speed
    private static final int BULLET_DELAY = 0x0F; // 15 frames stationary before moving
    private static final int INIT_FLOOR_Y_RADIUS = 0x0B;

    private static final SpriteAnimationSet ANIMATIONS = createAnimations();

    private int startY;
    private boolean xFlip;
    private State state;
    private int timer;
    private final SubpixelMotion.State motionState;
    private boolean bulletFired;
    private final ObjectAnimationState animationState;

    public OctusBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Octus", Sonic2BadnikConfig.DESTRUCTION);
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        // Octus faces left by default; x_flip in spawn means face right
        this.facingLeft = !xFlip;
        this.state = State.WAIT_FOR_PLAYER;
        this.timer = 0;
        int snappedY = snapToFloorLikeRom(spawn.x(), spawn.y());
        this.currentY = snappedY;
        this.startY = snappedY;
        this.motionState = new SubpixelMotion.State(spawn.x(), snappedY, 0, 0, 0, 0);
        this.bulletFired = false;
        this.animationState = new ObjectAnimationState(ANIMATIONS, 0, 1);
    }

    private int snapToFloorLikeRom(int x, int y) {
        try {
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, INIT_FLOOR_Y_RADIUS);
            if (floor.foundSurface() && floor.distance() < 0) {
                return y + floor.distance();
            }
        } catch (RuntimeException ignored) {
            // Tests without a level keep the placement coordinate.
        }
        return y;
    }

    @Override
    public OctusBadnikInstance recreateForRewind(RewindRecreateContext ctx) {
        return new OctusBadnikInstance(ctx.spawn());
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case WAIT_FOR_PLAYER -> updateWaitForPlayer(player);
            case DELAY_BEFORE_RISE -> updateDelayBeforeRise();
            case MOVING_UP -> updateMovingUp();
            case HOVERING -> updateHovering();
            case MOVING_DOWN -> updateMovingDown();
        }
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        animationState.update();
        animFrame = animationState.getMappingFrame();
    }

    private void updateWaitForPlayer(AbstractPlayableSprite player) {
        if (player == null || player.isDebugMode()) {
            return;
        }
        int dx = player.getCentreX() - currentX;
        if (Math.abs(dx) < DETECT_RANGE) {
            // Determine facing based on player position
            facingLeft = isPlayerLeft(player);
            state = State.DELAY_BEFORE_RISE;
            timer = RISE_DELAY;
            animationState.setAnimId(3); // Pre-rise (antenna visible)
        }
    }

    private void updateDelayBeforeRise() {
        // ROM Obj4A_DelayBeforeMoveUp (s2.asm:59958-59967):
        //   subq.w #1, objoff_2C(a0)
        //   bmi.s +                  ; branch when timer goes NEGATIVE (after the
        //                              ; decrement), not when it hits zero
        //   rts
        // + addq.b #2, routine_secondary(a0)
        //   move.b #4, anim(a0)
        //   move.w #-$200, y_vel(a0)
        //   jmpto JmpTo19_ObjectMove ; apply -$200 y_vel via ObjectMove this frame
        timer--;
        if (timer < 0) {
            state = State.MOVING_UP;
            yVelocity = INITIAL_Y_VEL;
            animationState.setAnimId(4); // Rising animation
            // ROM falls through to ObjectMove on the transition frame, so apply
            // the initial -$200 velocity here. Without this, the Octus starts
            // 2 pixels lower than ROM throughout its rise, delaying badnik-bounce
            // hits by ~1 frame in OOZ trace replay.
            applyYMovement();
        }
    }

    private void updateMovingUp() {
        // Decelerate: y_vel starts at -0x200, add +0x10 per frame
        yVelocity += Y_ACCEL;
        applyYMovement();

        if (yVelocity >= 0) {
            // Reached peak - fire bullet and start hovering
            yVelocity = 0;
            state = State.HOVERING;
            timer = HOVER_DURATION;
            fireBullet();
        }
    }

    private void updateHovering() {
        // ROM Obj4A_Hover (s2.asm:59981-59988):
        //   subq.w #1, objoff_2C(a0)
        //   bmi.s +
        //   rts
        // + addq.b #2, routine_secondary(a0)
        //   rts
        // bmi triggers when timer goes negative, not when it reaches zero.
        timer--;
        if (timer < 0) {
            state = State.MOVING_DOWN;
            yVelocity = 0;
        }
    }

    private void updateMovingDown() {
        // Accelerate downward: +0x10 per frame
        yVelocity += Y_ACCEL;
        applyYMovement();

        if (currentY >= startY) {
            // Returned to start position - reset
            currentY = startY;
            yVelocity = 0;
            motionState.ySub = 0;
            state = State.WAIT_FOR_PLAYER;
            bulletFired = false;
            animationState.setAnimId(0); // Back to idle
            animFrame = 1; // mapping_frame = 1
        }
    }

    private void applyYMovement() {
        motionState.y = currentY;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentY = motionState.y;
    }

    private void fireBullet() {
        if (bulletFired) {
            return;
        }
        bulletFired = true;

        // Bullet fires in the direction the octus is facing
        int bulletXVel = facingLeft ? -BULLET_X_VEL : BULLET_X_VEL;
        boolean bulletHFlip = !facingLeft;

        spawnFreeChild(() -> new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.OCTUS_BULLET,
                currentX,
                currentY,
                bulletXVel,
                0,          // No vertical velocity
                false,      // No gravity
                bulletHFlip,
                BULLET_DELAY));
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.OCTUS);
        if (renderer == null) return;

        // Art faces left by default. When xFlip is set in spawn, face right.
        // facingLeft=true means default orientation (no flip needed).
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);
    }

    /**
     * Animation scripts from Ani_obj4A (disassembly lines 60030-60045).
     */
    private static SpriteAnimationSet createAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Idle/submerged - dc.b $F, 1, 0, $FF
        set.addScript(0, new SpriteAnimationScript(
                0x0F,
                List.of(1, 0),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 1: Alert (unused in final, beta leftover) - dc.b 3, 1, 2, 3, $FF
        set.addScript(1, new SpriteAnimationScript(
                3,
                List.of(1, 2, 3),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 2: Bullet projectile - dc.b 2, 5, 6, $FF
        set.addScript(2, new SpriteAnimationScript(
                2,
                List.of(5, 6),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 3: Pre-rise (antenna visible) - dc.b $F, 4, $FF
        set.addScript(3, new SpriteAnimationScript(
                0x0F,
                List.of(4),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 4: Rising - dc.b 7, 0, 1, $FE, 1
        set.addScript(4, new SpriteAnimationScript(
                7,
                List.of(0, 1),
                SpriteAnimationEndAction.LOOP_BACK,
                1));

        return set;
    }
}
