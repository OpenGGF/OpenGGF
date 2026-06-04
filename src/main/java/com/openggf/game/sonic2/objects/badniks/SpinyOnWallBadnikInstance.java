package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SpinyOnWall (0xA6) - Wall-climbing variant of Spiny Badnik from CPZ.
 * Patrols vertically on walls and fires spike projectiles horizontally.
 *
 * Behavior from s2.asm disassembly (ObjA6_WallType):
 * - Patrol: Moves at y_vel = -0x40, reverses every 0x80 frames (128)
 * - Detection: Checks angle to player, attacks if within 0x60-0xC0 range
 * - Attack: Timer 0x28 (40 frames), fires at 0x14 (20 remaining), lockout 0x40 (64)
 * - Spike fire: Horizontal (x_vel = 0x300 or -0x300) based on facing direction
 *
 * Uses the same art as Spiny (ArtNem_Spiny) but different animation frames:
 * - Frames 3-4: Wall climbing (patrol)
 * - Frame 5: Attack pose
 * - Frames 6-7: Spike projectile
 */
public class SpinyOnWallBadnikInstance extends AbstractBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x0B;

    // Movement constants
    private static final int Y_VEL = 0x40;        // Movement speed (subpixels)
    private static final int MOVE_TIMER = 0x80;   // Frames before reversing (128)

    // Attack constants
    private static final int ATTACK_TIMER = 0x28;   // Attack duration (40 frames)
    private static final int FIRE_FRAME = 0x14;     // Fire at this remaining (20 frames)
    private static final int ATTACK_LOCKOUT = 0x40; // Cooldown after attack (64 frames)

    // Detection range. ROM loc_38BBA (s2.asm:76445-76449) calls
    // Obj_GetOrientationToPlayer, then: addi.w #$60,d2 / cmpi.w #$C0,d2 / blo.
    // d2 is the *signed* horizontal distance (spiny.x - closestPlayer.x) to the
    // CLOSER of MainCharacter/Sidekick. The unsigned (d2 + $60) < $C0 test
    // therefore attacks whenever the player is within [-$60, $60) horizontally.
    // There is NO vertical gate and NO facing gate in ObjA6's detection — the
    // earlier 0x80 box + dy + facing check fired the spike at the wrong frames,
    // and in CPZ1 the resulting falling spike hit CPU Tails (a hurt ROM never
    // produces). Firing direction is the spiny's fixed x_flip (loc_38C6E), not
    // the player's side.
    private static final int DETECT_X_OFFSET = 0x60; // addi.w #$60,d2
    private static final int DETECT_X_RANGE = 0xC0;  // cmpi.w #$C0,d2 / blo

    // Projectile constants - horizontal only for wall variant
    private static final int SPIKE_X_VEL = 0x300;   // Horizontal spike velocity
    private static final int SPIKE_Y_VEL = 0;       // No vertical component

    // Animation constants
    private static final int CLIMB_ANIM_DELAY = 9;  // 9-frame delay between climbing frames

    private enum State {
        PATROLLING,
        ATTACKING
    }

    private State state;
    private int moveCounter;      // Frames until direction reversal
    private int attackTimer;      // Attack state timer
    private int attackLockout;    // Frames until can attack again
    private boolean movingUp;     // Current movement direction (vertical)
    private boolean hasFired;     // Whether spike has been fired this attack
    private int ySubpixel;        // Subpixel accumulator for smooth movement

    public SpinyOnWallBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "SpinyOnWall", Sonic2BadnikConfig.DESTRUCTION);
        this.state = State.PATROLLING;
        this.moveCounter = MOVE_TIMER;
        this.attackTimer = 0;
        this.attackLockout = 0;
        this.movingUp = true;      // Start moving up (negative Y)
        this.hasFired = false;
        this.ySubpixel = 0;
        // Preserve spawn's render_flags for initial facing direction (x_flip bit)
        this.facingLeft = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case PATROLLING -> updatePatrolling(player);
            case ATTACKING -> updateAttacking(player);
        }
    }

    private void updatePatrolling(AbstractPlayableSprite player) {
        // Apply movement velocity using subpixel accumulation
        // Y_VEL = 0x40 = 64 subpixels per frame = 0.25 pixels per frame
        ySubpixel += Y_VEL;
        while (ySubpixel >= 256) {
            ySubpixel -= 256;
            if (movingUp) {
                currentY--;
            } else {
                currentY++;
            }
        }

        // Decrement move counter
        moveCounter--;
        if (moveCounter <= 0) {
            // Reverse direction
            movingUp = !movingUp;
            moveCounter = MOVE_TIMER;
        }

        // Decrement attack lockout
        if (attackLockout > 0) {
            attackLockout--;
        }

        // Check for player in attack range
        if (player != null && attackLockout == 0 && isPlayerInRange(player)) {
            startAttack();
        }
    }

    private void updateAttacking(AbstractPlayableSprite player) {
        attackTimer--;

        // Fire spike at the right moment
        if (!hasFired && attackTimer <= FIRE_FRAME) {
            fireSpike();
            hasFired = true;
        }

        // End attack when timer expires
        if (attackTimer <= 0) {
            state = State.PATROLLING;
            attackLockout = ATTACK_LOCKOUT;
            hasFired = false;
        }
    }

    /**
     * Checks if a player is within attack range, matching ROM ObjA6 loc_38BBA
     * (s2.asm:76445-76449):
     * <pre>
     *   bsr.w  Obj_GetOrientationToPlayer  ; d2 = spiny.x - closestPlayer.x (signed)
     *   addi.w #$60,d2
     *   cmpi.w #$C0,d2
     *   blo.s  loc_38BEA                   ; attack
     * </pre>
     * The signed horizontal distance is taken to the CLOSER of MainCharacter and
     * Sidekick (Obj_GetOrientationToPlayer, s2.asm:72755-72781). The detection
     * uses ONLY this horizontal band; there is no vertical gate and no facing
     * gate in the ROM.
     */
    private boolean isPlayerInRange(AbstractPlayableSprite player) {
        AbstractPlayableSprite target = closestPlayer(player);
        if (target == null) {
            return false;
        }
        // d2 = spiny.x - player.x (signed), then unsigned (d2 + $60) < $C0.
        int adjustedDx = (currentX - target.getCentreX()) + DETECT_X_OFFSET;
        return adjustedDx >= 0 && adjustedDx < DETECT_X_RANGE;
    }

    /**
     * Mirrors Obj_GetOrientationToPlayer's character selection (s2.asm:72755-72781):
     * picks the closer of MainCharacter / Sidekick by absolute horizontal distance.
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
            for (PlayableEntity sk : svc.playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
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

    private void startAttack() {
        state = State.ATTACKING;
        attackTimer = ATTACK_TIMER;
        hasFired = false;
        // Unlike regular Spiny, SpinyOnWall does NOT turn to face player
        // It fires in its current facing direction
    }

    private void fireSpike() {
        // ROM loc_38C6E (s2.asm:76509-76526): the spike spawns at the spiny's
        // exact x_pos/y_pos and moves at x_vel = $300, negated when the spiny's
        // render_flags.x_flip bit is set (facingLeft). y_vel starts at 0 and
        // Obj98_SpinyShotFall applies +$20 gravity (s2.asm:74628-74632). The
        // previous +/-8 muzzle offset is not in the ROM and shifted the spike's
        // landing point, contributing to phantom CPU-Tails hits in CPZ1.
        int xVel = facingLeft ? -SPIKE_X_VEL : SPIKE_X_VEL;

        services().objectManager().createDynamicObject(() -> new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.SPINY_SPIKE,
                currentX,
                currentY,
                xVel,
                SPIKE_Y_VEL,
                true,   // Apply gravity after firing
                false   // No initial flip
        ));
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (state == State.ATTACKING) {
            // Attack pose (frame 5) for wall variant
            animFrame = 5;
        } else {
            // Wall-climbing animation (frames 3-4, 9-frame delay)
            animFrame = 3 + ((frameCounter / CLIMB_ANIM_DELAY) & 1);
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.SPINY);
        if (renderer == null) return;

        // Flip sprite based on facing direction
        // Default sprite has body on left, legs on right (for LEFT wall, facing RIGHT)
        // When facingLeft=true (on RIGHT wall, facing LEFT), we need to H-flip
        boolean hFlip = facingLeft;
        boolean vFlip = false;
        renderer.drawFrameIndex(animFrame, currentX, currentY, hFlip, vFlip);
    }
}
