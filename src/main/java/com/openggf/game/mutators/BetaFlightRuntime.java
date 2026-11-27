package com.openggf.game.mutators;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * World-owned beta flight for a glide-ability leader: the leftover player-object flight from the
 * Sonic 3 Winter CES cartridge (labelled "Sonic 3 CES 12/22/93"). The routine was read statically
 * from that dump; the cartridge never references the object, so it was never observed running.
 * Addresses below are that build's ROM offsets (no disassembly of it exists in this repository).
 *
 * <p>The build's third player object is a copy of Sonic's (its animation table matches S&amp;K
 * {@code AniKnuckles}). Its airborne mode at $17014 branches on {@code double_jump_flag}: zero runs
 * Sonic's ordinary air path, non-zero runs a flight path at $17044 built from Tails' flight
 * ({@code Tails_Move_FlySwim}, final S3 s3.asm:24874-24926) without its flight timer, sound or
 * pose selection. Movement code stays in {@code PlayableSpriteMovement}; this runtime owns the
 * admission policy, the one captured bit saying the current {@code double_jump_flag} belongs to
 * beta flight rather than the native glide, and the routine's arithmetic.
 */
public final class BetaFlightRuntime implements RewindSnapshottable<BetaFlightRuntime.Snapshot> {
    /** CES $1794C entry: horizontal speed is raised to at least $400 in the direction of travel. */
    static final int ENTRY_MIN_SPEED = 0x400;
    /** CES $1708A / $17068: a flap starts and continues only while y_vel >= -$100. */
    static final int FLAP_SPEED_LIMIT = -0x100;
    /** CES $170B0: descending flight adds 8 per frame; there is no other gravity on this path. */
    static final int DESCENT_ACCELERATION = 0x08;
    /** CES $17070: each flap frame subtracts $20. */
    static final int FLAP_ACCELERATION = 0x20;
    /** CES $1707A: double_jump_flag counts 2..$1F during a flap, then returns to 1. */
    static final int FLAP_END_STATE = 0x20;
    /** CES $170D0 writes anim $20 every flight frame (S&amp;K AniKnuckles20 is byte-identical). */
    public static final int FLIGHT_ANIMATION = 0x20;

    /** CES $17872: the copied jump raises Knuckles' $600 launch to $700 while Super_Sonic_Knux_flag is set. */
    static final int SUPER_JUMP = 0x700;

    /**
     * Admitted policy: whether beta flight is on, whether its steering is fixed to act on x_vel,
     * whether the CES Super jump applies, and which persisted leader keys may use it.
     */
    public record Policy(boolean active, boolean fixedSteering, boolean superJump, Predicate<String> leaderKeys) {
        public static final Policy STOCK = new Policy(false, false, false, key -> false);
        public Policy {
            Objects.requireNonNull(leaderKeys, "leaderKeys");
        }
    }

    private final Supplier<Policy> policies;
    private SpriteManager roster;
    private boolean flying;

    public BetaFlightRuntime(Supplier<Policy> policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public void bindRoster(SpriteManager sprites) { roster = sprites; }

    private Policy policy() { return Objects.requireNonNull(policies.get(), "beta flight policy"); }

    private boolean leader(AbstractPlayableSprite sprite) {
        return sprite != null && roster != null && roster.getMainPlayable() == sprite;
    }

    /** True when a new air-ability press by this sprite should enter beta flight instead of the glide. */
    public boolean enabledFor(AbstractPlayableSprite sprite) {
        Policy policy = policy();
        return policy.active() && leader(sprite) && !sprite.isCpuControlled()
                && sprite.getSecondaryAbility() == SecondaryAbility.GLIDE
                && policy.leaderKeys().test(sprite.characterKey().persisted());
    }

    /**
     * True while the leader's non-zero {@code double_jump_flag} is beta flight. Ownership outlives a
     * policy change so a flight in progress never reaches the glide's state machine; it ends when the
     * native landing, object-control or hurt owners clear the flag.
     */
    public boolean owns(AbstractPlayableSprite sprite) {
        if (!flying) return false;
        if (!leader(sprite) || sprite.getDoubleJumpFlag() == 0 || sprite.getDead()) {
            flying = false;
            // Landing and object control already release the pose; death does not.
            if (sprite.getForcedAnimationId() == FLIGHT_ANIMATION) sprite.setForcedAnimationId(-1);
            return false;
        }
        return true;
    }

    /**
     * CES $17960-$179D4, reached from the jump-height routine once y_vel is past the variable-jump
     * cap and a jump button is newly pressed with {@code double_jump_flag} clear. Unrolls with the
     * default radius restored (the same y_pos correction Tails' flight entry uses), clears the
     * roll-jump bit, sets the flag to 1 and launches horizontally. y_vel is left as it was.
     */
    public void enter(AbstractPlayableSprite sprite) {
        if (sprite.getRolling()) {
            // CES $17960-$17984: bclr Status_Roll, default radii, y_pos += old - default y_radius.
            int oldCentreY = sprite.getCentreY();
            int oldYRadius = sprite.getYRadius();
            int defaultYRadius = sprite.getStandYRadius();
            sprite.setRolling(false);
            sprite.setCentreYPreserveSubpixel((short) (oldCentreY + oldYRadius - defaultYRadius));
        }
        sprite.setRollingJump(false);                      // $17988 bclr Status_RollJump
        sprite.setDoubleJumpFlag(1);                       // $1798E
        int speed = sprite.getGSpeed();                    // $17994 ground_vel, not x_vel
        if (speed == 0) {
            // $179BE: $400 toward the current facing.
            speed = sprite.getDirection() == Direction.LEFT ? -ENTRY_MIN_SPEED : ENTRY_MIN_SPEED;
        } else {
            // $1799A-$179BA: |ground_vel| raised to $400, facing follows its sign.
            int magnitude = Math.max(Math.abs(speed), ENTRY_MIN_SPEED);
            boolean left = speed < 0;
            sprite.setDirection(left ? Direction.LEFT : Direction.RIGHT);
            speed = left ? -magnitude : magnitude;
        }
        sprite.setGSpeed((short) speed);                   // $179CC
        sprite.setXSpeed((short) speed);                   // $179D0
        applyAnimation(sprite);                            // $179D4 bsr $170D0
        flying = true;
    }

    /**
     * CES $17060-$170D6, the first half of each flight frame. State 1 descends at +8 per frame;
     * a newly pressed jump button with y_vel >= -$100 starts a flap (state 2), unless the player is
     * underwater while the Tails carry flag is set. A flap subtracts $20 per frame for 30 frames
     * (state 2..$1F) or until y_vel falls below -$100. Upward motion stops at the camera's top
     * bound + $10. There is no flight timer: the copied Tails test of double_jump_property is gone.
     */
    public void stepVertical(AbstractPlayableSprite sprite, boolean jumpPressed, boolean underwater,
                             boolean carryFlagSet, Camera camera) {
        int state = sprite.getDoubleJumpFlag() & 0xFF;
        int ySpeed = sprite.getYSpeed();
        if (state == 1) {
            // $1708A: Ctrl_1_pressed_logical & A/B/C; $17094 y_vel >= -$100;
            // $1709C/$170A4 underwater with Flying_carrying_Sonic_flag ($F73E) refuses the flap.
            if (jumpPressed && ySpeed >= FLAP_SPEED_LIMIT && !(underwater && carryFlagSet)) {
                state = 2;
            }
            ySpeed += DESCENT_ACCELERATION;
        } else if (ySpeed < FLAP_SPEED_LIMIT) {
            state = 1;                                      // $17068 blt -> $17082
        } else {
            ySpeed -= FLAP_ACCELERATION;                    // $17070
            state = (state + 1) & 0xFF;                     // $17076
            if (state == FLAP_END_STATE) state = 1;         // $1707A-$17082
        }
        if (camera != null && ySpeed < 0) {
            // $170B6-$170CA: Camera_min_Y_pos + $10, signed word compare against y_pos.
            int ceilingY = (short) (camera.getMinY() + 0x10);
            if ((short) sprite.getCentreY() <= ceilingY) ySpeed = 0;
        }
        sprite.setDoubleJumpFlag(state);
        sprite.setYSpeed((short) ySpeed);
        applyAnimation(sprite);                             // $170D0
    }

    /**
     * CES $170D8-$17120: Sonic_ChgJumpDir's steering without its roll-jump test or apex drag,
     * applied to ground_vel ($1C) instead of x_vel. Only facing and ground_vel change; x_vel keeps
     * the entry launch until terrain, the level boundary or a landing rewrites it. This reads as
     * unfinished code; it is reproduced as shipped in the cartridge.
     *
     * <p>The Fixed Steering option is not in the cartridge: it runs the same arithmetic on x_vel,
     * the field Sonic_ChgJumpDir steers, so left and right accelerate him in flight. ground_vel is
     * then left alone, as in Sonic_ChgJumpDir.
     */
    public void steer(AbstractPlayableSprite sprite, boolean left, boolean right) {
        boolean fixed = policy().fixedSteering();
        int max = sprite.getMax();
        int accel = 2 * sprite.getRunAccel();               // $170DE asl.w #1,d5
        int speed = fixed ? sprite.getXSpeed() : sprite.getGSpeed();
        if (left) {
            sprite.setDirection(Direction.LEFT);
            int next = (short) (speed - accel);
            if (next > -max) speed = next;
            else if ((short) (next + accel) > -max) speed = -max;
            // Otherwise the speed was already past -max and stays unchanged.
        }
        if (right) {
            sprite.setDirection(Direction.RIGHT);
            int next = (short) (speed + accel);
            if (next < max) speed = next;
            else if ((short) (next - accel) < max) speed = max;
        }
        if (fixed) sprite.setXSpeed((short) speed);
        else sprite.setGSpeed((short) speed);
    }

    /**
     * The Super Jump option's launch. CES $17868-$17882 (the object's copy of the jump routine) loads
     * $600, raises it to $700 while Super_Sonic_Knux_flag ($FE19, Super or Hyper) is set, then
     * replaces either with $300 underwater. S&amp;K Knux_Jump (sonic3k.asm:32494-32497) has no Super
     * raise, so the native value is kept in every other case.
     */
    public int jumpImpulse(AbstractPlayableSprite sprite, int nativeJump, boolean underwater) {
        if (!policy().superJump() || !enabledFor(sprite) || !sprite.isSuperSonic() || underwater) return nativeJump;
        return SUPER_JUMP;
    }

    private static void applyAnimation(AbstractPlayableSprite sprite) {
        sprite.setForcedAnimationId(FLIGHT_ANIMATION);
        sprite.setAnimationId(FLIGHT_ANIMATION);
    }

    /** {@code Flying_carrying_Sonic_flag}: set while any Tails is carrying the leader. */
    public boolean carryFlagSet() {
        if (roster == null) return false;
        for (AbstractPlayableSprite sidekick : roster.getSidekicks()) {
            var carry = sidekick.getTailsCarryController();
            if (carry != null && carry.isCarryingMainCharacter()) return true;
        }
        return false;
    }

    public void reset() { flying = false; }

    @Override public String key() { return "beta-flight"; }

    @Override public Snapshot capture() { return new Snapshot(flying); }

    @Override public void restore(Snapshot snapshot) { flying = snapshot.flying(); }

    @Override public void resetForMissingSnapshot() { reset(); }

    public record Snapshot(boolean flying) { }
}
