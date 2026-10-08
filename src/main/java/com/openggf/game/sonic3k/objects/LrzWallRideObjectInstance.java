package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;

import java.util.List;

/**
 * ROM object {@code Obj_LRZWallRide} -- object id {@code $16} in the {@code SKL} pointer set
 * (sonic3k.asm:87693-87795, ROM {@code $4254A}). The {@code S3KL} set spends the same id on
 * {@code Obj_LBZFlameThrower}.
 *
 * <p>Like {@link LrzCorkscrewObjectInstance} it has no mappings and no {@code art_tile}: Init writes
 * only {@code width_pixels} and the routine pointer (:87694-87695), so the object is invisible and
 * the level art draws the wall the player is swept along.
 *
 * <p><b>Capture</b> ({@code sub_42574}, :87724-87746). Both axis tests are single unsigned range
 * checks -- {@code sub}, {@code addi.w #$10}, {@code cmpi.w #$20}, {@code bhs} -- unlike the
 * corkscrew, whose horizontal and vertical tests are deliberately different kinds. The rider must
 * have no {@code object_control}, be on the ground, and be moving the object's own way.
 * {@code status(a0)} bit 0, the placement's X-flip, selects which way: clear is the rightward ride
 * with the speed floor at {@code $400}, set is the leftward one with the floor at {@code -$400} and
 * a {@code neg.w ground_vel} that makes the ride parameter accumulate forwards either way
 * (:87766-87780). Neither Lava Reef placement sets it -- act 1's is at {@code ($1E90,$5E8)} and
 * act 2's at {@code ($2280,$3D0)}, render flags {@code 0} and {@code 2} -- so the leftward branch is
 * implemented from the ROM rather than from a placement.
 *
 * <p><b>Ride</b> ({@code loc_426F8}, :87837-87889). {@code $30(a0)}/{@code $34(a0)} accumulate
 * {@code ground_vel << 7} -- one bit less than the corkscrew's {@code << 8} -- and every
 * {@code (a2)} word read takes the long's HIGH word. The two exits are "the long went negative" and
 * "the high word reached {@code $100}". While riding, {@code ground_vel} climbs by {@code $10} a
 * frame to {@code $1000}.
 *
 * <p><b>A live {@code FixBugs = 0} defect in the X velocity.</b> {@code loc_42718} loads the
 * rider's previous {@code x_pos} into {@code d2} (:87852) and then overwrites {@code d2} with the
 * ride's amplitude, {@code $165} or {@code $142} (:87859, :87862), before {@code sub.w d2,d0}
 * computes the X velocity (:87868). So {@code x_vel} is {@code (newX - amplitude) << 8}, not the
 * frame's displacement; the Y path re-loads {@code y_pos(a1)} into {@code d2} <em>after</em> using
 * it as the amplitude (:87877) and does compute a real delta. Byte-verified in the user-supplied
 * ROM at {@code $42718} ({@code 34 29 00 10 ...}) and {@code $42748} ({@code 90 42 E1 40 ...}): the
 * dead load and the constant subtraction are both what shipped. {@code object_control = $43}
 * suppresses ordinary movement, so the value is state a trace compares rather than motion, and the
 * bug-fixed branch would subtract the saved previous X instead.
 *
 * <p>Init's tail is {@code Delete_Sprite_If_Not_In_Range} (:87698), so the shared camera unload
 * applies and no custom out-of-range check is declared.
 */
public final class LrzWallRideObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.b #$D0,width_pixels(a0)} (sonic3k.asm:87694). */
    private static final int WIDTH_PIXELS = 0xD0;
    /** {@code addi.w #$10} / {@code cmpi.w #$20} on both axes (:87730-87736). */
    private static final int CAPTURE_HALF_SPAN = 0x10;
    private static final int CAPTURE_SPAN = 0x20;
    /** {@code cmpi.w #$400,ground_vel(a1)} (:87751-87753) and its negative twin (:87771-87773). */
    private static final int MINIMUM_RIDE_SPEED = 0x400;
    /** {@code addi.w #$10,ground_vel(a1)} up to {@code cmpi.w #$1000} (:87848-87849). */
    private static final int RIDE_ACCELERATION = 0x10;
    private static final int MAXIMUM_RIDE_SPEED = 0x1000;
    /** {@code cmpi.w #$100,(a2)} (:87844): the high word ends the ride. */
    private static final int RIDE_END = 0x100;
    /** {@code lsl.l #7,d0} (:87840): the accumulator step, one bit less than the corkscrew's. */
    private static final int ACCUMULATOR_SHIFT = 7;
    /** {@code muls.w #$A00,d0} (:87856). */
    private static final int X_AMPLITUDE = 0x0A00;
    /** {@code move.w #$165,d2} / {@code move.w #$142,d2} (:87859, :87862). */
    private static final int RIDE_AMPLITUDE_RIGHT = 0x165;
    private static final int RIDE_AMPLITUDE_LEFT = 0x142;
    /** {@code divu.w #$16,d0} (:87887). */
    private static final int ANIMATION_DIVISOR = 0x16;

    /** {@code RawAni_42792} at ROM {@code $42792}, 12 bytes; byte-identical in the ROM image. */
    private static final int[] ANIM_42792 = {
        0xEF, 0xDF, 0xE0, 0xE1, 0xE2, 0xE3, 0xF5, 0xF4, 0xF3, 0xF2, 0xF1, 0xF0,
    };

    /** {@code btst #0,status(a0)} (:87740): the placement's X-flip picks the leftward ride. */
    private boolean leftward;
    /** ROM {@code $30(a0)} / {@code $34(a0)}: the per-player ride accumulator, a long. */
    private int p1Accumulator;
    private int p2Accumulator;
    /** ROM {@code status(a0)}'s two standing bits: "this player is riding me". */
    private boolean p1Riding;
    private boolean p2Riding;

    public LrzWallRideObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZWallRide");
        this.leftward = (spawn.renderFlags() & 0x1) != 0;
    }

    /**
     * {@code Obj_LRZWallRide} is installed from the SKL object pointer table at ROM
     * {@code $0004254A} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzWallRideObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzWallRideObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // sub_4255E (sonic3k.asm:87706-87718): Player 1 against $30(a0), then Player 2 against
        // $34(a0). FixBugs = 0 there too - addq.b leaves d6 dirty after Player 1's
        // Perform_Player_DPLC - which is why the standing bit is kept per player here rather than
        // derived from one shared register, as LrzCorkscrewObjectInstance already does.
        stepPlayer(playerEntity, true);
        stepPlayer(nativeP2OrNull(), false);
    }

    private void stepPlayer(PlayableEntity entity, boolean isPlayerOne) {
        if (!(entity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (isRiding(isPlayerOne)) {
            advanceRide(player, isPlayerOne);
        } else {
            tryCapture(player, isPlayerOne);
        }
    }

    /** {@code sub_42574} head (sonic3k.asm:87728-87780). */
    private void tryCapture(AbstractPlayableSprite player, boolean isPlayerOne) {
        if (!withinCaptureBox(player)) {
            return;
        }
        if (player.isObjectControlled() || player.getAir()) {
            return;
        }
        int groundVelocity = player.getGSpeed();
        if (leftward) {
            // loc_425EA (:87766-87775): tst.w / bpl - only a rider already moving LEFT is caught.
            if (groundVelocity >= 0) {
                return;
            }
            if (groundVelocity > -MINIMUM_RIDE_SPEED) {
                player.setGSpeed((short) -MINIMUM_RIDE_SPEED);
            }
            captureCommon(player, isPlayerOne);
            // neg.w ground_vel(a1) (:87779): the ride accumulates forwards either way.
            player.setGSpeed((short) -player.getGSpeed());
            player.setDirection(Direction.LEFT);
        } else {
            // tst.w ground_vel(a1) / bmi (:87749-87750): only a rider moving RIGHT is caught.
            if (groundVelocity < 0) {
                return;
            }
            if (groundVelocity < MINIMUM_RIDE_SPEED) {
                player.setGSpeed((short) MINIMUM_RIDE_SPEED);
            }
            captureCommon(player, isPlayerOne);
            player.setDirection(Direction.RIGHT);
        }
        // move.b #$43,object_control(a1) (:87763, :87784).
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
    }

    /** Both axis tests: {@code sub}, {@code addi.w #$10}, {@code cmpi.w #$20}, unsigned {@code bhs}. */
    private boolean withinCaptureBox(AbstractPlayableSprite player) {
        int dx = ((player.getCentreX() - getCentreX()) + CAPTURE_HALF_SPAN) & 0xFFFF;
        if (dx >= CAPTURE_SPAN) {
            return false;
        }
        int dy = ((player.getCentreY() - getCentreY()) + CAPTURE_HALF_SPAN) & 0xFFFF;
        return dy < CAPTURE_SPAN;
    }

    /**
     * {@code sub_42636} (sonic3k.asm:87785-87813). The corkscrew's capture tail is a near copy of
     * this, but not the same routine: this one also does {@code bset #Status_InAir} (:87802), so a
     * wall rider is airborne for the whole ride, and it clears {@code anim} at the end.
     */
    private void captureCommon(AbstractPlayableSprite player, boolean isPlayerOne) {
        setRiding(isPlayerOne, true);
        setAccumulator(isPlayerOne, 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.applyStandingRadii(false);
        player.setOnObject(true);
        player.setAir(true);
        player.setJumping(false);
        player.setFlipAngle(0);
        player.setFlipType(0);
        player.setFlipsRemaining(0);
        player.setDoubleJumpFlag(0);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        setInteractSlot(player);
    }

    /** {@code loc_426F8} (sonic3k.asm:87837-87889). */
    private void advanceRide(AbstractPlayableSprite player, boolean isPlayerOne) {
        int accumulator = accumulator(isPlayerOne);
        // move.w ground_vel(a1),d0 / ext.l d0 / lsl.l #7,d0 / add.l d0,(a2).
        accumulator += ((int) (short) player.getGSpeed()) << ACCUMULATOR_SHIFT;
        if (accumulator < 0) {
            // bmi.s loc_426B6 (:87841).
            setAccumulator(isPlayerOne, accumulator);
            release(player, isPlayerOne);
            return;
        }
        int parameter = (accumulator >>> 16) & 0xFFFF;
        if (parameter >= RIDE_END) {
            // cmpi.w #$100,(a2) / bhs.s loc_426B6 (:87843-87844) reads the accumulator's HIGH word.
            setAccumulator(isPlayerOne, accumulator);
            release(player, isPlayerOne);
            return;
        }
        setAccumulator(isPlayerOne, accumulator);

        int groundVelocity = player.getGSpeed();
        if (groundVelocity < MAXIMUM_RIDE_SPEED) {
            player.setGSpeed((short) (groundVelocity + RIDE_ACCELERATION));
        }

        applyRidePosition(player, parameter);
    }

    /** {@code loc_42718} / {@code loc_42740} (sonic3k.asm:87851-87890). */
    private void applyRidePosition(AbstractPlayableSprite player, int parameter) {
        int angle = (parameter >>> 1) & 0xFF;
        int amplitude = leftward ? RIDE_AMPLITUDE_LEFT : RIDE_AMPLITUDE_RIGHT;

        int newX = (getCentreX() + rideXOffset(parameter, leftward)) & 0xFFFF;
        NativePositionOps.writeXPosPreserveSubpixel(player, newX);
        // sub.w d2,d0 / asl.w #8,d0 with d2 = the amplitude, not the saved previous X: the
        // FixBugs = 0 defect documented on the class.
        player.setXSpeed((short) (((newX - amplitude) & 0xFFFF) << 8));

        int previousY = player.getCentreY();
        int newY = (getCentreY() + rideYOffset(parameter, leftward)) & 0xFFFF;
        NativePositionOps.writeYPosPreserveSubpixel(player, newY);
        player.setYSpeed((short) (((newY - previousY) & 0xFFFF) << 8));

        // moveq #0,d0 / move.w (a2),d0 / lsr.w #1,d0 / andi.w #$FF,d0 / divu.w #$16,d0 gives a
        // quotient of 0-11, which indexes the twelve raw DPLC frames.
        player.setMappingFrame(ANIM_42792[(angle / ANIMATION_DIVISOR) % ANIM_42792.length]);
    }

    /** {@code loc_426B6} (sonic3k.asm:87820-87835). */
    private void release(AbstractPlayableSprite player, boolean isPlayerOne) {
        // andi.b #$80,status(a1) then bset #Status_InAir: everything but bit 7 is cleared, so the
        // rider leaves unrolled, off the object, not pushing and facing right.
        setRiding(isPlayerOne, false);
        player.setOnObject(false);
        player.setPushing(false);
        player.setRollingFlagPreserveRadii(false);
        player.setAir(true);
        player.setDirection(Direction.RIGHT);
        if (!leftward) {
            // btst #Status_Facing,status(a0) / beq (:87823-87828): the rightward ride puts the
            // rider back on the ground facing left, with its speed reversed; the leftward one
            // leaves it airborne with the speed it had.
            // bclr #Status_InAir,status(a1) is a direct bit clear, not Sonic_ResetOnFloor: the
            // ROM does not reset the item-bonus chain or claim a landing animation here.
            player.clearAirForNativeControlRestore();
            player.setDirection(Direction.LEFT);
            player.setGSpeed((short) -player.getGSpeed());
        }
        // move.w #1,anim(a1) is a WORD write, so anim becomes 0 and prev_anim 1.
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        ObjectControlState.none().applyTo(player);
        player.setXSpeed(player.getGSpeed());
        player.setYSpeed((short) 0);
    }

    private void setInteractSlot(AbstractPlayableSprite player) {
        try {
            player.setInteractSlotIndex(getSlotIndex());
        } catch (Exception ignored) {
            // A probe-constructed wall ride has no slot to point the rider at.
        }
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRiding(boolean isPlayerOne) {
        return isPlayerOne ? p1Riding : p2Riding;
    }

    private void setRiding(boolean isPlayerOne, boolean riding) {
        if (isPlayerOne) {
            p1Riding = riding;
        } else {
            p2Riding = riding;
        }
    }

    private int accumulator(boolean isPlayerOne) {
        return isPlayerOne ? p1Accumulator : p2Accumulator;
    }

    private void setAccumulator(boolean isPlayerOne, int value) {
        if (isPlayerOne) {
            p1Accumulator = value;
        } else {
            p2Accumulator = value;
        }
    }

    // ----- test and rewind accessors -------------------------------------------------------------

    /** ROM {@code $30(a0)} / {@code $34(a0)} as the long the ROM accumulates into. */
    public int accumulatorFor(boolean playerOne) {
        return accumulator(playerOne);
    }

    /** The accumulator's high word: the ride parameter every {@code (a2)} word read uses. */
    public int rideParameter(boolean playerOne) {
        return (accumulator(playerOne) >>> 16) & 0xFFFF;
    }

    public boolean isRidingFor(boolean playerOne) {
        return isRiding(playerOne);
    }

    public boolean isLeftward() {
        return leftward;
    }

    /**
     * {@code muls.w #$A00,sin(param >> 1)}, {@code asl.l #4}, {@code swap}: the high word of the
     * product after the shift, negated for a leftward placement.
     */
    static int rideXOffset(int parameter, boolean leftward) {
        int product = TrigLookupTable.sinHex((parameter >>> 1) & 0xFF) * X_AMPLITUDE;
        int offset = (short) ((product << 4) >>> 16);
        return leftward ? (short) -offset : offset;
    }

    /**
     * {@code neg.w d1 / addi.w #$100,d1 / lsr.w #1,d1 / mulu.w d2,d1 / lsr.l #8,d1}: the cosine is
     * turned into an unsigned {@code 0..$100} rise, halved, scaled by the amplitude and shifted
     * back down. Every step is unsigned, so the offset is never negative.
     */
    static int rideYOffset(int parameter, boolean leftward) {
        int cosine = TrigLookupTable.cosHex((parameter >>> 1) & 0xFF);
        int rise = ((-cosine + 0x100) & 0xFFFF) >>> 1;
        int amplitude = leftward ? RIDE_AMPLITUDE_LEFT : RIDE_AMPLITUDE_RIGHT;
        return (int) (((long) rise * amplitude) >>> 8) & 0xFFFF;
    }

    /** {@code RawAni_42792} indexed by {@code ((param >> 1) & $FF) / $16}. */
    static int rideMappingFrame(int parameter) {
        return ANIM_42792[(((parameter >>> 1) & 0xFF) / ANIMATION_DIVISOR) % ANIM_42792.length];
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return CAPTURE_SPAN;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Init writes no mappings and no art_tile (sonic3k.asm:87694-87695): the wall ride is
        // invisible and the level art draws the wall the player is driven along.
    }
}
