package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Aquis (0x50) - seahorse badnik from Oil Ocean Zone.
 * Waits until on-screen, chases the player with acceleration, fires downward
 * projectiles, and escapes after 3 shooting cycles. Has a wing child object
 * that follows the body.
 * Based on disassembly Obj50 (lines 60044-60310).
 */
public class AquisBadnikInstance extends AbstractBadnikInstance {

    private enum State {
        WAIT_FOR_SCREEN,  // routine_secondary 0: idle until on-screen
        CHASE,            // routine_secondary 2: follow player with accel
        SHOOTING,         // routine_secondary 4: wait then fire projectile
        ESCAPE            // routine_secondary 6: fly away
    }

    private static final int COLLISION_SIZE_INDEX = 0x0A; // collision_flags $0A
    private static final int WIDTH_PIXELS = 0x10;
    private static final int CHASE_ACCEL = 0x10;           // +-0x10 per frame
    private static final int MAX_CHASE_SPEED = 0x100;      // cap speed 0x100
    private static final int CHASE_TIMER = 0x80;           // 128 frames
    private static final int SHOOT_DELAY = 0x20;           // 32 frames
    private static final int INITIAL_SHOTS = 3;
    private static final int BULLET_X_VEL = 0x300;
    private static final int BULLET_Y_VEL = 0x200;
    private static final int BULLET_X_OFFSET = 0x10;
    private static final int BULLET_Y_OFFSET = 0x0A;
    private static final int ESCAPE_X_VEL = -0x200;
    private static final int POST_SHOT_Y_VEL = -0x100;
    private static final int WING_X_OFFSET = 0x0A;
    private static final int WING_Y_OFFSET = -6;

    private static final SpriteAnimationSet ANIMATIONS = createAnimations();
    private static final SpriteAnimationSet WING_ANIMATIONS = createWingAnimations();

    private State state;
    private int timer;
    private int shotsRemaining;
    private boolean shootingFlag; // prevents double-fire per shooting phase
    private final SubpixelMotion.State motionState;
    private final ObjectAnimationState animationState;

    // Wing child state
    private final ObjectAnimationState wingAnimationState;
    private boolean wingDestroyed;

    public AquisBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Aquis", Sonic2BadnikConfig.DESTRUCTION);
        this.state = State.WAIT_FOR_SCREEN;
        this.timer = 0;
        this.shotsRemaining = INITIAL_SHOTS;
        this.shootingFlag = false;
        this.motionState = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.facingLeft = (spawn.renderFlags() & 0x01) != 0;
        this.animationState = new ObjectAnimationState(ANIMATIONS, 0, 0);
        this.wingAnimationState = new ObjectAnimationState(WING_ANIMATIONS, 0, 1);
        this.wingDestroyed = false;
        // Bug 1 fix: ROM Obj50_Init writes move.w #-$100, x_vel(a0) immediately
        // after the standard init block (s2.asm:60100).
        this.xVelocity = -0x100;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = closestPlayer(playerEntity);
        switch (state) {
            case WAIT_FOR_SCREEN -> updateWaitForScreen();
            case CHASE -> updateChase(player);
            case SHOOTING -> updateShooting(player);
            case ESCAPE -> updateEscape();
        }

        // Update wing animation
        wingAnimationState.update();
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        animationState.update();
        animFrame = animationState.getMappingFrame();
    }

    private void updateWaitForScreen() {
        // ROM Obj50_CheckIfOnScreen (s2.asm:60600-60611) tests
        // render_flags.on_screen and advances routine_secondary to Obj50_Chase
        // the instant that bit is set. render_flags.on_screen is set by
        // Render_Sprites (the BuildSprites pass) when the object's rendered
        // bounding box overlaps the camera viewport; it is NOT contingent on
        // draw commands being emitted by any particular caller. Model it with
        // the shared frame-driven camera-bounds overlap test
        // (isWithinSolidContactBounds == cameraBounds.containsRenderSpriteBounds,
        // see AbstractObjectInstance.java:580-600), which already carries the
        // ROM's one-frame "set last frame, tested this frame" lag because the
        // cached camera bounds reflect the prior frame's Render_Sprites pass.
        // The previous draw-command-driven flag never fired under headless
        // trace replay, so the Aquis stayed frozen at spawn and never chased.
        if (isWithinSolidContactBounds()) {
            // ROM Obj50_CheckIfOnScreen (s2.asm:60607-60614) only advances
            // routine_secondary to Obj50_Chase; it does NOT initialise
            // Obj50_timer. The SST timer byte is therefore still 0 from the
            // cleared object slot, so the very first Obj50_FollowPlayer frame
            // does subq.b #1 -> 0xFF -> bmi -> Obj50_DoneFollowing immediately
            // (s2.asm:60670-60671). i.e. the Aquis fires one shot from its
            // spawn position before it ever moves. Leaving timer at 0 here (NOT
            // CHASE_TIMER) reproduces that initial stationary shooting phase;
            // setting it to 0x80 made the engine chase ~58 frames too early and
            // drift the Aquis ~41px left of the ROM by the kill frame.
            state = State.CHASE;
            timer = 0;
            animationState.setAnimId(1); // Flapping body animation
        }
    }

    private void updateChase(AbstractPlayableSprite player) {
        // ROM Obj50_FollowPlayer (s2.asm:60669-60697) decrements the timer and
        // bails to Obj50_DoneFollowing (-> Obj_MoveStop) BEFORE running
        // GetOrientationToPlayer / accel / CapSpeed / ObjectMove. Mirror that
        // ordering: on the expiry frame the object must NOT accelerate or move.
        // subq.b #1 / bmi fires when the byte underflows (0x00 -> 0xFF).
        timer = (byte) (timer - 1);
        if (timer < 0) {
            // Obj50_DoneFollowing (s2.asm:60693-60697): Obj_MoveStop clears
            // x_vel/y_vel, then routine -> Obj50_Shooting with timer = $20.
            xVelocity = 0;
            yVelocity = 0;
            state = State.SHOOTING;
            timer = SHOOT_DELAY;
            shootingFlag = false;
            animationState.setAnimId(0); // Static body
            return;
        }

        if (player != null && !player.isDebugMode()) {
            // Obj_GetOrientationToPlayer (s2.asm:72755-72781) indexes
            // Obj50_Speeds {-$10, +$10} (s2.asm:60689-60691): the object
            // accelerates TOWARD the closest player on both axes, and faces
            // toward the player on X. d2 = obj.x - player.x; player to the left
            // (d2 >= 0) -> index 0 = -$10 (accel left); player to the right
            // (d2 < 0) -> index 2 = +$10 (accel right). Y is symmetric.
            if (player.getCentreX() < currentX) {
                xVelocity -= CHASE_ACCEL;
                facingLeft = true;
            } else {
                xVelocity += CHASE_ACCEL;
                facingLeft = false;
            }

            if (player.getCentreY() < currentY) {
                yVelocity -= CHASE_ACCEL;
            } else {
                yVelocity += CHASE_ACCEL;
            }
        }

        // Obj_CapSpeed (s2.asm) clamps |x_vel|,|y_vel| to $100.
        xVelocity = clampSpeed(xVelocity, MAX_CHASE_SPEED);
        yVelocity = clampSpeed(yVelocity, MAX_CHASE_SPEED);

        applyMovement();
    }

    private void updateShooting(AbstractPlayableSprite player) {
        // Fire projectile if player is below and we haven't fired yet this phase
        if (!shootingFlag && player != null && !player.isDebugMode()) {
            if (player.getCentreY() > currentY) {
                fireProjectile();
                shootingFlag = true;
            }
        }

        // Bug 2 fix: ROM uses subq.b #1, timer / bmi (s2.asm:60275-60276).
        timer = (byte) (timer - 1);
        if (timer < 0) {
            shotsRemaining--;
            if (shotsRemaining > 0) {
                // Return to chase
                state = State.CHASE;
                timer = CHASE_TIMER;
                yVelocity = POST_SHOT_Y_VEL; // Thrust upward after shot
                shootingFlag = false;
                animationState.setAnimId(1); // Flapping body
            } else {
                // All shots used, escape
                state = State.ESCAPE;
                xVelocity = ESCAPE_X_VEL;
                yVelocity = 0;
            }
        }
    }

    private void updateEscape() {
        applyMovement();

        if (!isOnScreen(64)) {
            setDestroyed(true);
            setDestroyed(true);
            wingDestroyed = true;
        }
    }

    private void fireProjectile() {
        ObjectServices svc = tryServices();
        if (svc == null || svc.objectManager() == null) {
            return;
        }

        final int bulletX = facingLeft ? currentX - BULLET_X_OFFSET : currentX + BULLET_X_OFFSET;
        final int bulletY = currentY + BULLET_Y_OFFSET;
        final int bulletXVel = facingLeft ? -BULLET_X_VEL : BULLET_X_VEL;
        final boolean bulletHFlip = !facingLeft;

        spawnFreeChild(() -> new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.AQUIS_BULLET,
                bulletX,
                bulletY,
                bulletXVel,
                BULLET_Y_VEL,
                false,      // No gravity
                bulletHFlip));
    }

    /**
     * Bug 3 fix: ROM Obj_GetOrientationToPlayer (s2.asm:72320-72346) picks the
     * closer of MainCharacter and Sidekick by |x_pos - obX|. Engine previously
     * only used the main player. Walk the sidekick list and return the player
     * with minimum absolute X distance.
     */
    private AbstractPlayableSprite closestPlayer(PlayableEntity mainPlayer) {
        AbstractPlayableSprite best = null;
        int bestDist = Integer.MAX_VALUE;
        if (mainPlayer instanceof AbstractPlayableSprite mainSprite) {
            best = mainSprite;
            bestDist = Math.abs(mainSprite.getCentreX() - currentX);
        }
        ObjectServices svc = tryServices();
        if (svc != null) {
            for (PlayableEntity sk : svc.playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
                if (sk == mainPlayer) {
                    continue;
                }
                if (sk instanceof AbstractPlayableSprite skSprite) {
                    int dist = Math.abs(skSprite.getCentreX() - currentX);
                    if (dist < bestDist) {
                        best = skSprite;
                        bestDist = dist;
                    }
                }
            }
        }
        return best;
    }

    private void applyMovement() {
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;
    }

    private static int clampSpeed(int velocity, int max) {
        if (velocity > max) return max;
        if (velocity < -max) return -max;
        return velocity;
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

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.AQUIS);
        if (renderer == null) return;

        // Draw main body (priority 4)
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);

        // Draw wing child (priority 3, slightly in front)
        if (!wingDestroyed) {
            int wingX = facingLeft ? currentX + WING_X_OFFSET : currentX - WING_X_OFFSET;
            int wingY = currentY + WING_Y_OFFSET;
            int wingFrame = wingAnimationState.getMappingFrame();
            renderer.drawFrameIndex(wingFrame, wingX, wingY, !facingLeft, false);
        }
    }

    @Override
    protected void destroyBadnik(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        wingDestroyed = true;
        super.destroyBadnik(player);
    }

    /**
     * Animation scripts from Ani_obj50 (disassembly).
     */
    private static SpriteAnimationSet createAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Normal body (static) - dc.b $E, 0, $FF
        set.addScript(0, new SpriteAnimationScript(
                0x0E,
                List.of(0),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 1: Body with flapping - dc.b 5, 3, 4, 3, 4, 3, 4, $FF
        set.addScript(1, new SpriteAnimationScript(
                5,
                List.of(3, 4, 3, 4, 3, 4),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 2: Bullet spinning - dc.b 3, 5, 6, 7, 6, $FF
        set.addScript(2, new SpriteAnimationScript(
                3,
                List.of(5, 6, 7, 6),
                SpriteAnimationEndAction.LOOP,
                0));

        return set;
    }

    /**
     * Wing animation scripts.
     */
    private static SpriteAnimationSet createWingAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Wing flapping - dc.b 3, 1, 2, $FF
        set.addScript(0, new SpriteAnimationScript(
                3,
                List.of(1, 2),
                SpriteAnimationEndAction.LOOP,
                0));

        return set;
    }
}
