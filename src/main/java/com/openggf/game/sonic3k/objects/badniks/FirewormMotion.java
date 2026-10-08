package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.physics.SwingMotion;

/**
 * The swim-and-turn routine {@code Obj_Fireworm}'s head and every one of its segments run, shared
 * because the ROM shares it literally: the segment dispatch table {@code off_8F906}
 * (sonic3k.asm:196354-196359) points its routine 6 and routine 8 entries at {@code loc_8F862} and
 * {@code loc_8F89A}, the head's own labels.
 *
 * <p><b>Routine 6, {@code loc_8F862}</b> (:196296-196302) runs {@code Swing_UpAndDown_Count}. While
 * the {@code $39(a0)} half-cycle counter has not gone negative it moves with {@code MoveSprite2}
 * and advances {@code byte_8FA40} through {@code Animate_RawMultiDelay}. When the counter does go
 * negative, {@code loc_8F876} (:196304-196313) latches {@code x_vel} into {@code $44(a0)}, gives
 * {@code y_vel} the stored {@code $42(a0)}, negates {@code $42(a0)}, clears {@code $2E(a0)} and the
 * animation position, and enters routine 8.
 *
 * <p><b>Routine 8, {@code loc_8F89A}</b> (:196315-196337) animates {@code byte_8FA4D} with
 * {@code Animate_RawNoSSTMultiDelayFlipX} -- whose third entry, {@code 2|$40}, is what turns the
 * sprite round -- and walks {@code x_vel} across by {@code $10} a frame in the direction opposite
 * to the sign {@code $44(a0)} recorded. The walk stops at {@code -$100} or {@code $100}
 * ({@code loc_8F8DE}, :196339-196343), which clears the animation position and re-enters routine 6
 * through {@code loc_8F842}.
 *
 * <p><b>{@code loc_8F842}</b> (:196287-196294) is the swing seed: {@code $39 = 8},
 * {@code $3E = y_vel = $80}, {@code $40 = 8}, {@code $38} bit 0 cleared.
 */
final class FirewormMotion {

    /** {@code move.w #8,$40(a0)} (loc_8F842): the swing's per-frame acceleration. */
    static final int SWING_ACCEL = 8;
    /** {@code move.w d0,$3E(a0)} with {@code d0 = $80} (loc_8F842): the swing's speed cap. */
    static final int SWING_MAX_VEL = 0x80;
    /** {@code move.b #8,$39(a0)} (loc_8F842): half-cycles before the turn. */
    static final int SWING_HALF_CYCLES = 8;
    /** {@code move.w #-$100,$42(a0)} (loc_8F82E): the vertical kick each turn applies. */
    static final int TURN_KICK = -0x100;
    /** {@code subi.w #$10,d0} / {@code addi.w #$10,d0} (loc_8F89A). */
    static final int TURN_X_STEP = 0x10;
    /** {@code cmpi.w #-$100,d0} / {@code cmpi.w #$100,d0} (loc_8F89A). */
    static final int TURN_X_LIMIT = 0x100;

    /** ROM {@code routine(a0)} for this pair of states. */
    enum Phase { SWIM, TURN }

    private Phase phase = Phase.SWIM;
    /** ROM {@code $39(a0)}. */
    private int halfCycles;
    /** ROM {@code $38(a0)} bit 0. */
    private boolean swingDown;
    /** ROM {@code $42(a0)}. */
    private int turnKick = TURN_KICK;
    /** ROM {@code $44(a0)}: the {@code x_vel} the turn started from. */
    private int savedXVel;
    /** ROM {@code $2E(a0)} while routine 8 runs. It is written and never read: the ROM's own
     * {@code addq.w #1,$2E(a0)} at :196318 has no consumer. Kept so rewind sees the same word. */
    private int turnFrames;

    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();

    /** {@code loc_8F82E} + {@code loc_8F842}: enter routine 6 for the first time. */
    void startSwim(SubpixelMotion.State motion, int swimScriptAddress) {
        phase = Phase.SWIM;
        turnKick = TURN_KICK;
        anim.script = swimScriptAddress;
        seedSwing(motion);
    }

    /** {@code loc_8F842} alone: the seed a re-entry from {@code loc_8F8DE} reuses. */
    private void seedSwing(SubpixelMotion.State motion) {
        halfCycles = SWING_HALF_CYCLES;
        motion.yVel = SWING_MAX_VEL;
        swingDown = false;
    }

    /**
     * One dispatch of routine 6 or routine 8.
     *
     * @param toggleFlipX run when {@code byte_8FA4D}'s {@code 2|$40} entry comes up
     */
    void update(SubpixelMotion.State motion, S3kRawAnimation scripts,
            int turnScriptAddress, Runnable toggleFlipX) {
        if (phase == Phase.SWIM) {
            updateSwim(motion, scripts);
        } else {
            updateTurn(motion, scripts, turnScriptAddress, toggleFlipX);
        }
    }

    /** {@code loc_8F862} (:196296-196313). */
    private void updateSwim(SubpixelMotion.State motion, S3kRawAnimation scripts) {
        SwingMotion.Result swing =
                SwingMotion.update(SWING_ACCEL, (short) motion.yVel, SWING_MAX_VEL, swingDown);
        motion.yVel = swing.velocity();
        swingDown = swing.directionDown();
        if (swing.directionChanged()) {
            halfCycles = (halfCycles - 1) & 0xFF;
            if ((byte) halfCycles < 0) {
                // loc_8F876: the counter went negative, so turn around.
                phase = Phase.TURN;
                savedXVel = (short) motion.xVel;
                motion.yVel = turnKick;
                turnKick = -turnKick;
                turnFrames = 0;
                anim.animFrame = 0;
                anim.animFrameTimer = 0;
                return;
            }
        }
        SubpixelMotion.moveSprite2(motion);
        if (scripts != null) {
            scripts.animateMultiDelay(anim, () -> { });
        }
    }

    /** {@code loc_8F89A} (:196315-196343). */
    private void updateTurn(SubpixelMotion.State motion, S3kRawAnimation scripts,
            int turnScriptAddress, Runnable toggleFlipX) {
        if (scripts != null) {
            scripts.animateNoSstMultiDelayFlipX(anim, turnScriptAddress, () -> { }, toggleFlipX);
        }
        turnFrames = (turnFrames + 1) & 0xFFFF;
        int next;
        boolean finished;
        if (savedXVel >= 0) {
            next = (short) (motion.xVel - TURN_X_STEP);
            finished = next <= -TURN_X_LIMIT;
        } else {
            next = (short) (motion.xVel + TURN_X_STEP);
            finished = next >= TURN_X_LIMIT;
        }
        if (finished) {
            // loc_8F8DE: back to routine 6 without moving this frame.
            phase = Phase.SWIM;
            anim.animFrame = 0;
            anim.animFrameTimer = 0;
            seedSwing(motion);
            return;
        }
        motion.xVel = next;
        SubpixelMotion.moveSprite2(motion);
    }

    /** ROM {@code mapping_frame(a0)} as the scripts leave it. */
    int mappingFrame() {
        return anim.mappingFrame;
    }

    void setMappingFrame(int frame) {
        anim.mappingFrame = frame;
    }

    /** ROM {@code routine(a0)}: {@code 6} while swimming, {@code 8} while turning. */
    Phase phase() {
        return phase;
    }

    /** ROM {@code $39(a0)}. */
    int halfCycles() {
        return halfCycles;
    }

    /** ROM {@code $42(a0)}. */
    int turnKick() {
        return turnKick;
    }

    /** ROM {@code $44(a0)}. */
    int savedXVel() {
        return savedXVel;
    }
}
