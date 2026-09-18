package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM object {@code Obj_LRZCorkscrew} - object id {@code $15} in the {@code SKL} pointer set
 * (sonic3k.asm:87494-87513, {@code Obj_LRZCorkscrew} at ROM {@code $0004224E}). One act 1
 * placement.
 *
 * <p>It has no mappings and no {@code art_tile}: Init writes only {@code width_pixels} and the
 * routine pointer (:87495-87496), so the object is invisible and exists purely to drive the player
 * along a path the level art already shows.
 *
 * <p><b>Capture</b> ({@code sub_42278}, :87525-87543). The box is {@code $20} wide and {@code $20}
 * tall around the placement, but the two tests are written differently: the horizontal one is
 * {@code addi.w #$10 / sub.w x_pos(a0) / bcs} - an <em>unsigned</em> borrow test - followed by a
 * signed {@code bge #$20}, while the vertical one is a plain signed {@code bgt #$20}, so the
 * vertical band includes its far edge and the horizontal one does not. On top of that the player
 * must be free of any other {@code object_control}, on the ground, and moving right
 * ({@code tst.w ground_vel / bmi}); a rider slower than {@code $600} is sped up to it.
 *
 * <p><b>The ride</b> ({@code loc_423D0}, :87613-87620). {@code $30(a0)} and {@code $34(a0)} are
 * per-player <em>longs</em>, and the ROM reads them two ways: {@code add.l} accumulates
 * {@code ground_vel} shifted left by eight, while every {@code cmpi.w}/{@code move.w} on
 * {@code (a2)} reads the <em>high</em> word. So the high word is the ride parameter, advancing by
 * {@code ground_vel / $100} a frame, and the exits are "the long went negative" (backwards) and
 * "the high word reached {@code $700}" (forwards). {@code ground_vel} climbs {@code $10} a frame to
 * a cap of {@code $1000}.
 *
 * <p><b>Position</b> (:87622-87646). X is {@code GetSineCosine(param >> 1)} multiplied by
 * {@code $4800} with the high word of the product taken ({@code muls.w} then {@code swap}), added
 * to the placement X. Y indexes a 128-byte table, or a 64-byte one once the parameter passes
 * {@code $600}, and the add is a <em>byte</em> add into a word whose low seven bits were just
 * masked off - so the table value lands in the low byte and the {@code $80} step stays in place.
 * Both velocities are then the frame's own displacement shifted left by eight, which is what keeps
 * the camera and the sidekick following correctly.
 *
 * <p><b>Both exits eject the player moving left.</b> {@code loc_42368} and {@code loc_42396} each
 * do {@code neg.w ground_vel(a1)} (:87591, :87607), and {@code ground_vel} is positive for the
 * whole ride, so leaving either end turns the player around. The forwards exit also restores
 * {@code top_solid_bit} {@code $E} and {@code lrb_solid_bit} {@code $F} and sets
 * {@code Status_Facing}; the backwards exit sets {@code Status_InAir} instead.
 *
 * <p>{@code move.w #1,anim(a1)} is a <em>word</em> write over {@code anim} and {@code prev_anim}
 * (:87589), so the player leaves with {@code anim} 0 - the walk - and {@code prev_anim} 1, not with
 * {@code anim} 1.
 *
 * <p><b>{@code FixBugs = 0} branch</b> (:87513-87519). The shipped ROM advances {@code d6} from
 * Player 1's standing bit to Player 2's with {@code addq.b}, which leaves the high half of
 * {@code d6} dirty after Player 1's {@code Perform_Player_DPLC} has run; the fixed branch would
 * {@code moveq} the whole register. The dirty value is what the shipped game does, and
 * {@code CnzWireCageObjectInstance} already models the same bug for its own object, so the standing
 * bit is kept per player here rather than derived from a shared register.
 */
public final class LrzCorkscrewObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.b #$D0,width_pixels(a0)} (sonic3k.asm:87495). */
    private static final int WIDTH_PIXELS = 0xD0;
    /** {@code addi.w #$10,d0} and {@code cmpi.w #$20,d0} (:87527-87534). */
    private static final int CAPTURE_HALF_SPAN = 0x10;
    private static final int CAPTURE_SPAN = 0x20;
    /** {@code cmpi.w #$600,ground_vel(a1)} (:87540). */
    private static final int MINIMUM_RIDE_SPEED = 0x600;
    /** {@code addi.w #$10,ground_vel(a1)} up to {@code cmpi.w #$1000} (:87618-87620). */
    private static final int RIDE_ACCELERATION = 0x10;
    private static final int MAXIMUM_RIDE_SPEED = 0x1000;
    /** {@code cmpi.w #$700,(a2)} (:87616): the forwards exit, on the accumulator's high word. */
    private static final int RIDE_END = 0x700;
    /** {@code cmpi.w #$600,(a2)} (:87635): which Y table the second half uses. */
    private static final int SECOND_TABLE_THRESHOLD = 0x600;
    /** {@code muls.w #$4800,d0} (:87627). */
    private static final int X_AMPLITUDE = 0x4800;
    /** {@code divu.w #$16,d0} (:87652). */
    private static final int ANIMATION_DIVISOR = 0x16;
    /** {@code move.b #$E,top_solid_bit(a1)} / {@code #$F,lrb_solid_bit(a1)} (:87600-87601). */
    private static final int EXIT_TOP_SOLID_BIT = 0x0E;
    private static final int EXIT_LRB_SOLID_BIT = 0x0F;

    /** {@code RawAni_4247E} at ROM {@code $4247E}, 12 bytes; byte-identical in the ROM image. */
    private static final int[] ANIM_4247E = {
        0xEF, 0xFA, 0xF9, 0xF8, 0xF7, 0xF6, 0xF5, 0xF4, 0xF3, 0xF2, 0xF1, 0xF0,
    };

    /** {@code byte_4248A} at ROM {@code $4248A}, 128 bytes: the Y profile for the first half. */
    private static final int[] TABLE_4248A = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x0F, 0x10, 0x12,
        0x13, 0x13, 0x14, 0x14, 0x16, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17,
        0x18, 0x18, 0x18, 0x18, 0x19, 0x1B, 0x1C, 0x1E, 0x1F, 0x21, 0x22, 0x24, 0x25, 0x27, 0x28, 0x2A,
        0x2B, 0x2D, 0x2E, 0x30, 0x31, 0x33, 0x34, 0x36, 0x37, 0x39, 0x3A, 0x3C, 0x3D, 0x3F, 0x40, 0x42,
        0x43, 0x45, 0x46, 0x48, 0x49, 0x4B, 0x4C, 0x4E, 0x4F, 0x51, 0x52, 0x54, 0x55, 0x57, 0x58, 0x5A,
        0x5B, 0x5D, 0x5E, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60,
        0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,
        0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
    };

    /** {@code byte_4250A} at ROM {@code $4250A}, 64 bytes: the flatter profile past {@code $600}. */
    private static final int[] TABLE_4250A = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x0F, 0x10, 0x12,
        0x13, 0x13, 0x14, 0x14, 0x16, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17,
        0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x1A, 0x1A, 0x1A, 0x1B, 0x1B, 0x1B, 0x1C, 0x1C, 0x1C,
        0x1D, 0x1D, 0x1D, 0x1D, 0x1E, 0x1E, 0x1E, 0x1F, 0x1F, 0x1F, 0x20, 0x20, 0x20, 0x21, 0x21, 0x21,
    };

    /** ROM {@code $30(a0)} and {@code $34(a0)}: the two per-player ride accumulators. */
    private int p1Accumulator;
    private int p2Accumulator;
    /** The object's own {@code status} standing bits, one per player. */
    private boolean p1Riding;
    private boolean p2Riding;

    public LrzCorkscrewObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZCorkscrew");
    }

    /**
     * {@code Obj_LRZCorkscrew} sits at ROM {@code $0004224E} (sonic3k.lst); its whole code block
     * lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzCorkscrewObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzCorkscrewObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // sub_42262 (sonic3k.asm:87505-87519): Player 1 against $30(a0), then Player 2 against
        // $34(a0). Delete_Sprite_If_Not_In_Range is the engine's generic off-screen handling.
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

    /** {@code sub_42278} head (sonic3k.asm:87525-87575). */
    private void tryCapture(AbstractPlayableSprite player, boolean isPlayerOne) {
        // move.w x_pos(a1),d0 / addi.w #$10,d0 / sub.w x_pos(a0),d0 / bcs -> an unsigned borrow,
        // then a signed bge against $20.
        int dx = (player.getCentreX() + CAPTURE_HALF_SPAN) - getCentreX();
        if (dx < 0 || dx >= CAPTURE_SPAN) {
            return;
        }
        // move.w y_pos(a1),d0 / sub.w y_pos(a0),d0 / addi.w #$10,d0 / cmpi.w #$20,d0 / bgt:
        // signed and inclusive of $20, unlike the horizontal test.
        int dy = (player.getCentreY() - getCentreY()) + CAPTURE_HALF_SPAN;
        if (dy > CAPTURE_SPAN) {
            return;
        }
        if (player.isObjectControlled() || player.getAir()) {
            return;
        }
        int groundVelocity = player.getGSpeed();
        // tst.w ground_vel(a1) / bmi: only a rider already moving right can be caught.
        if (groundVelocity < 0) {
            return;
        }
        if (groundVelocity < MINIMUM_RIDE_SPEED) {
            player.setGSpeed((short) MINIMUM_RIDE_SPEED);
        }

        // loc_422E6 (:87554-87575).
        setRiding(isPlayerOne, true);
        setAccumulator(isPlayerOne, 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.applyStandingRadii(false);
        player.setOnObject(true);
        player.setJumping(false);
        player.setFlipAngle(0);
        player.setFlipType(0);
        player.setFlipsRemaining(0);
        player.setDoubleJumpFlag(0);
        player.setDirection(Direction.RIGHT);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        setInteractSlot(player);
    }

    /** {@code loc_423D0} through {@code loc_42438} (sonic3k.asm:87613-87660). */
    private void advanceRide(AbstractPlayableSprite player, boolean isPlayerOne) {
        int accumulator = accumulator(isPlayerOne);
        // move.w ground_vel(a1),d0 / ext.l d0 / lsl.l #8,d0 / add.l d0,(a2).
        accumulator += ((int) (short) player.getGSpeed()) << 8;
        if (accumulator < 0) {
            // bmi.s loc_42368: the long went negative, so the rider is leaving the way it came.
            releaseBackwards(player, isPlayerOne);
            return;
        }
        int parameter = (accumulator >>> 16) & 0xFFFF;
        if (parameter >= RIDE_END) {
            // cmpi.w #$700,(a2) / bhs.s loc_42396 reads the accumulator's HIGH word.
            setAccumulator(isPlayerOne, accumulator);
            releaseForwards(player, isPlayerOne);
            return;
        }
        setAccumulator(isPlayerOne, accumulator);

        int groundVelocity = player.getGSpeed();
        if (groundVelocity < MAXIMUM_RIDE_SPEED) {
            player.setGSpeed((short) (groundVelocity + RIDE_ACCELERATION));
        }

        applyRidePosition(player, parameter);
    }

    /** {@code loc_423F0} onwards (sonic3k.asm:87622-87660). */
    private void applyRidePosition(AbstractPlayableSprite player, int parameter) {
        int angle = (parameter >> 1) & 0xFFFF;

        // bclr #7,art_tile(a1) then, when ((param >> 1) + $40) stays positive as a BYTE, bset it
        // again: the rider is drawn in front on the near half of the turn (:87623-87629).
        player.setHighPriority((((angle & 0xFF) + 0x40) & 0xFF) < 0x80);

        int previousX = player.getCentreX();
        // muls.w #$4800,d0 / swap d0: the high word of the signed product.
        int xOffset = (TrigLookupTable.sinHex(angle & 0xFF) * X_AMPLITUDE) >> 16;
        int newX = (getCentreX() + xOffset) & 0xFFFF;
        NativePositionOps.writeXPosPreserveSubpixel(player, newX);
        player.setXSpeed((short) (((short) (newX - previousX)) << 8));

        int[] table = parameter < SECOND_TABLE_THRESHOLD ? TABLE_4248A : TABLE_4250A;
        int previousY = player.getCentreY();
        // move.w (a2),d0 / lsr.w #2,d0 / move.w d0,d1 / andi.w #$7F,d1 / andi.w #$FF80,d0 /
        // add.b (a3,d1.w),d0 - a BYTE add into the word whose low seven bits were just cleared.
        int stepped = (parameter >> 2) & 0xFFFF;
        int index = stepped & 0x7F;
        int base = stepped & 0xFF80;
        int lowByte = ((base & 0xFF) + table[index % table.length]) & 0xFF;
        int yOffset = (base & 0xFF00) | lowByte;
        int newY = (getCentreY() + yOffset) & 0xFFFF;
        NativePositionOps.writeYPosPreserveSubpixel(player, newY);
        player.setYSpeed((short) (((short) (newY - previousY)) << 8));

        // moveq #0,d0 / move.w (a2),d0 / lsr.w #1,d0 / andi.w #$FF,d0 / divu.w #$16,d0 gives a
        // quotient of 0-11, which indexes the twelve raw DPLC frames.
        int quotient = ((angle & 0xFF) / ANIMATION_DIVISOR) % ANIM_4247E.length;
        player.setMappingFrame(ANIM_4247E[quotient]);
    }

    /** {@code loc_42368} (sonic3k.asm:87582-87593). */
    private void releaseBackwards(AbstractPlayableSprite player, boolean isPlayerOne) {
        releaseCommon(player, isPlayerOne);
        player.setAir(true);
    }

    /** {@code loc_42396} (sonic3k.asm:87599-87610). */
    private void releaseForwards(AbstractPlayableSprite player, boolean isPlayerOne) {
        player.setTopSolidBit((byte) EXIT_TOP_SOLID_BIT);
        player.setLrbSolidBit((byte) EXIT_LRB_SOLID_BIT);
        releaseCommon(player, isPlayerOne);
        player.setDirection(Direction.LEFT);
    }

    /**
     * The tail both exits share. {@code neg.w ground_vel(a1)} on a speed that was positive for the
     * whole ride is what turns the rider around at either end.
     */
    private void releaseCommon(AbstractPlayableSprite player, boolean isPlayerOne) {
        setRiding(isPlayerOne, false);
        setAccumulator(isPlayerOne, 0);
        player.setOnObject(false);
        ObjectControlState.none().applyTo(player);
        // move.w #1,anim(a1) is a WORD write, so anim becomes 0 and prev_anim 1.
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        int reversed = -player.getGSpeed();
        player.setGSpeed((short) reversed);
        player.setXSpeed((short) reversed);
        player.setYSpeed((short) 0);
    }

    /** {@code move.w a0,interact(a1)} (sonic3k.asm:87554). */
    private void setInteractSlot(AbstractPlayableSprite player) {
        try {
            player.setInteractSlotIndex(getSlotIndex());
        } catch (Exception ignored) {
            // A probe-constructed corkscrew has no slot to point the rider at.
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

    /** {@code GetSineCosine(param >> 1) * $4800}, high word: the X offset from the placement. */
    static int rideXOffset(int parameter) {
        return (TrigLookupTable.sinHex((parameter >> 1) & 0xFF) * X_AMPLITUDE) >> 16;
    }

    /** The Y offset the two profile tables and the byte add produce for a ride parameter. */
    static int rideYOffset(int parameter) {
        int[] table = parameter < SECOND_TABLE_THRESHOLD ? TABLE_4248A : TABLE_4250A;
        int stepped = (parameter >> 2) & 0xFFFF;
        int base = stepped & 0xFF80;
        int lowByte = ((base & 0xFF) + table[(stepped & 0x7F) % table.length]) & 0xFF;
        return (base & 0xFF00) | lowByte;
    }

    /** {@code RawAni_4247E} indexed by {@code ((param >> 1) & $FF) / $16}. */
    static int rideMappingFrame(int parameter) {
        return ANIM_4247E[(((parameter >> 1) & 0xFF) / ANIMATION_DIVISOR) % ANIM_4247E.length];
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
        // Init writes no mappings and no art_tile (sonic3k.asm:87495-87496): the corkscrew is
        // invisible and the level art draws the shape the player is driven along.
    }
}
