package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object {@code Obj_LRZDashElevator} - object id {@code $1E} in the {@code SKL} pointer set
 * (sonic3k.asm:88381-88495, ROM {@code $42F2C}).
 *
 * <p>A solid platform that riders drive themselves. A player only latches on while its
 * {@code anim} is {@code 9} (spindash) and it is already standing on the platform; once latched it
 * keeps riding while {@code anim} is {@code 2} (roll) or {@code 9}, and any other animation, or
 * leaving the ground, releases it with {@code x_vel = 0} ({@code sub_4301C},
 * sonic3k.asm:88469-88494). A character with no spindash can therefore never start a ride.
 *
 * <p>Each rider contributes {@code 8 + spin_dash_counter}, negated when it faces right
 * ({@code Status_Facing} clear), to a per-frame accumulator. {@code loc_42F78} swaps that word into
 * the high half of a longword and shifts it right by three, so the platform's 16.16 position moves
 * by {@code accumulator / 8} pixels a frame between 0 and the subtype's travel range
 * (sonic3k.asm:88414-88424). While it moves it also drains each rider's {@code ground_vel} toward
 * zero by {@code $40} a frame, with the {@code bcc} after the add/subtract clamping at zero rather
 * than overshooting (:88478-88488).
 *
 * <p>Init (:88381-88397): the low seven subtype bits times eight are the travel range; bit 7 starts
 * the platform {@code $20} in from the bottom; and status bit 0 (the placement's flip flag) starts
 * it at the far end instead, with the base Y moved up by the whole range.
 */
public final class LrzDashElevatorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$80,priority(a0)} (sonic3k.asm:88387): bucket 1. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0080);
    /** {@code move.b #$20,width_pixels(a0)} / {@code #$10,height_pixels(a0)} (:88385-88386). */
    private static final int WIDTH_PIXELS = 0x20;
    private static final int HEIGHT_PIXELS = 0x10;
    /** {@code SolidObjectFull} arguments at {@code loc_43000} (:88462-88466). */
    private static final int SOLID_HALF_WIDTH = 0x2B;
    private static final int SOLID_HEIGHT_AIR = 0x08;
    private static final int SOLID_HEIGHT_GROUND = 0x09;
    /** {@code moveq #$20,d1} for a subtype with bit 7 set (:88390-88392). */
    private static final int HIGH_BIT_START_OFFSET = 0x20;
    /** {@code moveq #8,d0 / add.b spin_dash_counter(a1),d0} (:88483-88484), a byte add. */
    private static final int RIDER_BASE_PUSH = 8;
    /** {@code addi.w #$40,d0} / {@code subi.w #$40,d0} (:88472-88477). */
    private static final int GROUND_VELOCITY_DRAIN = 0x40;
    /** {@code cmpi.b #3,mapping_frame(a0)} (:88441): three walking frames. */
    private static final int MAPPING_FRAME_COUNT = 3;

    /** ROM {@code $30(a0)}: a 16.16 position whose high word is the pixel offset from the base. */
    private int position;
    /**
     * ROM {@code $34(a0)}: the same shape, holding the travel range as its high word. Non-final so
     * the rewind coverage guard can see it as restorable generic state; Init derives it from the
     * spawn, which {@link #recreateForRewind} replays.
     */
    private int maxPosition;
    /** ROM {@code $46(a0)}: the Y the offset is added to. Non-final for the same reason. */
    private int baseY;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /** ROM {@code $2E(a0)} and {@code $2F(a0)}: the per-player "is riding" bytes. */
    private boolean p1Riding;
    private boolean p2Riding;
    /** {@code status(a0)}'s standing bits, as the previous frame's SolidObjectFull left them. */
    private boolean p1Standing;
    private boolean p2Standing;
    private boolean p1StandingLatched;
    private boolean p2StandingLatched;
    /** This frame's accumulated 16.16 movement, kept for the animation and SFX gates. */
    private int frameDelta;

    public LrzDashElevatorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZDashElevator");

        int subtype = spawn.subtype() & 0xFF;
        int highBitOffset = 0;
        int startOffset = 0;
        // move.b subtype(a0),d0 / bpl.s loc_42F54 (sonic3k.asm:88390-88393).
        if ((subtype & 0x80) != 0) {
            highBitOffset = HIGH_BIT_START_OFFSET;
            startOffset = HIGH_BIT_START_OFFSET;
        }
        // andi.w #$7F,d0 / lsl.w #3,d0 (:88395-88396).
        int range = (subtype & 0x7F) << 3;
        this.maxPosition = range << 16;

        int base = spawn.y() & 0xFFFF;
        // bclr #0,status(a0) / beq.s loc_42F72 (:88398-88403): a flipped placement starts at the
        // far end of the travel and hangs its base Y a whole range higher.
        if ((spawn.renderFlags() & 0x1) != 0) {
            startOffset = range - highBitOffset;
            base = (base - range) & 0xFFFF;
        }
        this.position = startOffset << 16;
        this.baseY = base;
    }

    /**
     * {@code Obj_LRZDashElevator} is installed from the SKL object pointer table at ROM
     * {@code $00042F2C}; its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzDashElevatorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzDashElevatorObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // SolidObjectFull writes status(a0)'s standing bits at the tail of the ROM routine, so
        // sub_4301C at the head of the next frame sees the previous frame's bits.
        p1StandingLatched = p1Standing;
        p2StandingLatched = p2Standing;
        p1Standing = false;
        p2Standing = false;

        // moveq #0,d5 and the two sub_4301C calls (sonic3k.asm:88406-88413). Only the low word of
        // d5 is written by add.w, so the accumulator is a 16-bit quantity.
        int accumulator = 0;
        accumulator += riderPush(playerEntity, true);
        accumulator += riderPush(nativeP2OrNull(), false);
        accumulator &= 0xFFFF;

        // swap d5 / asr.l #3,d5 (:88415-88416): the word becomes the high half of a longword, so
        // the shift keeps its sign and the result is accumulator/8 pixels in 16.16.
        int delta = (accumulator << 16) >> 3;

        int next = position + delta;
        // bpl.s loc_42FA4 (:88418-88421).
        if (next < 0) {
            next = 0;
            delta = 0;
        }
        // cmp.l d1,d0 / blo.s loc_42FB0 (:88424-88428). Both operands are non-negative here, so
        // the ROM's unsigned compare and this signed one agree.
        if (next >= maxPosition) {
            next = maxPosition;
            delta = 0;
        }
        position = next;
        frameDelta = delta;

        // swap d0 / add.w $46(a0),d0 / move.w d0,y_pos(a0) (:88431-88434): a word add.
        updateDynamicSpawn(getCentreX(), (baseY + (position >> 16)) & 0xFFFF);

        advanceAnimationAndSound(levelFrameCounterOrFallback(vIntRunCount));
    }

    /**
     * {@code loc_42FB0} tail (sonic3k.asm:88435-88461). {@code Level_frame_counter+1} is the low
     * byte of the level clock, not the object's V-int count: the platform steps one walking frame
     * every fourth level frame while it is actually moving, and re-plays the conveyor loop every
     * sixteenth.
     */
    private void advanceAnimationAndSound(int levelFrameCounter) {
        int clockLowByte = levelFrameCounter & 0xFF;
        if ((clockLowByte & 3) != 0 || frameDelta == 0) {
            return;
        }
        if (frameDelta < 0) {
            // addq.b #1,mapping_frame(a0) with a wrap at 3 (:88438-88443).
            mappingFrame = mappingFrame + 1;
            if (mappingFrame >= MAPPING_FRAME_COUNT) {
                mappingFrame = 0;
            }
        } else {
            // subq.b #1,mapping_frame(a0) / bcc, else 2 (:88446-88449).
            mappingFrame = mappingFrame - 1;
            if (mappingFrame < 0) {
                mappingFrame = MAPPING_FRAME_COUNT - 1;
            }
        }
        if ((clockLowByte & 0xF) != 0) {
            return;
        }
        try {
            services().playSfx(Sonic3kSfx.CONVEYOR_PLATFORM.id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
        }
    }

    /**
     * ROM {@code sub_4301C} (sonic3k.asm:88469-88494). Returns this rider's contribution to the
     * frame accumulator, which is zero on every path but the ride itself.
     */
    private int riderPush(PlayableEntity entity, boolean isPlayerOne) {
        if (!(entity instanceof AbstractPlayableSprite player)) {
            return 0;
        }
        boolean riding = isPlayerOne ? p1Riding : p2Riding;

        if (!riding) {
            // tst.b (a2) / beq: not yet riding. The standing bit and a spindash animation are both
            // required, so a character without a spindash never starts a ride.
            boolean standing = isPlayerOne ? p1StandingLatched : p2StandingLatched;
            if (!standing || player.getAnimationId() != Sonic3kAnimationIds.SPINDASH.id()) {
                return 0;
            }
            NativePositionOps.writeXPosPreserveSubpixel(player, getCentreX());
            setRiding(isPlayerOne, true);
            return 0;
        }

        // loc_4303A (:88489-88492): leaving the ground, or any animation but roll and spindash,
        // releases the rider with a cleared x_vel.
        int animation = player.getAnimationId();
        boolean keepsRiding = !player.getAir()
                && (animation == Sonic3kAnimationIds.ROLL.id()
                || animation == Sonic3kAnimationIds.SPINDASH.id());
        if (!keepsRiding) {
            player.setXSpeed((short) 0);
            setRiding(isPlayerOne, false);
            return 0;
        }

        // loc_4305E (:88494-88510).
        NativePositionOps.writeXPosPreserveSubpixel(player, getCentreX());
        int groundVelocity = player.getGSpeed();
        if (groundVelocity != 0) {
            player.setGSpeed((short) drainGroundVelocity(groundVelocity));
        }

        // moveq #8,d0 / add.b spin_dash_counter(a1),d0: a byte add into a register whose upper
        // bits moveq already cleared.
        int push = (RIDER_BASE_PUSH + (player.getSpindashCounter() & 0xFF)) & 0xFF;
        // btst #Status_Facing,status(a1) / bne skips the negate, so facing left drives the
        // platform down and facing right drives it up.
        if (player.getDirection() != Direction.LEFT) {
            push = -push;
        }
        return push;
    }

    /**
     * {@code addi.w}/{@code subi.w #$40} followed by {@code bcc} (sonic3k.asm:88472-88482): the
     * carry out of the 16-bit operation is what clamps at zero, so a speed already inside
     * {@code $40} of zero lands exactly on zero instead of crossing it.
     */
    static int drainGroundVelocity(int groundVelocity) {
        if (groundVelocity < 0) {
            int sum = (groundVelocity & 0xFFFF) + GROUND_VELOCITY_DRAIN;
            return sum > 0xFFFF ? 0 : (short) sum;
        }
        int difference = (groundVelocity & 0xFFFF) - GROUND_VELOCITY_DRAIN;
        return difference < 0 ? 0 : (short) difference;
    }

    private void setRiding(boolean isPlayerOne, boolean riding) {
        if (isPlayerOne) {
            p1Riding = riding;
        } else {
            p2Riding = riding;
        }
    }

    private int levelFrameCounterOrFallback(int fallback) {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing()) {
            return;
        }
        if (player == nativeP2OrNull()) {
            p2Standing = true;
        } else {
            p1Standing = true;
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // move.w #$2B,d1 / #8,d2 / #9,d3 (sonic3k.asm:88462-88465).
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        // SolidObjectFull has no extra edge tolerance.
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) {
        // loc_43000 calls SolidObjectFull. Its loc_1DC98 jump-off branch
        // clears the existing standing bit and returns without a fresh contact.
        // Otherwise a rider jumping from the descending lift is lifted again
        // by SolidObject_cont's overlap correction on that same launch frame.
        return true;
    }

    @Override
    public boolean airborneRiderUnseatRequiresOwnCheckpoint(PlayableEntity player) {
        // loc_1DC98 belongs to this elevator's SolidObjectFull call. Earlier
        // objects must not consume its ride record before that early return.
        return true;
    }

    @Override
    public boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        // d4 = x_pos(a0) (sonic3k.asm:88466); the platform only ever moves vertically.
        return false;
    }

    /** ROM x_pos/y_pos are object centres. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return (baseY + (position >> 16)) & 0xFFFF;
    }

    /** ROM {@code $30(a0)}. */
    public int position() {
        return position;
    }

    /** ROM {@code $34(a0)}. */
    public int maxPosition() {
        return maxPosition;
    }

    /** ROM {@code $46(a0)}. */
    public int baseY() {
        return baseY;
    }

    public int mappingFrame() {
        return mappingFrame;
    }

    public boolean isRiding(boolean playerOne) {
        return playerOne ? p1Riding : p2Riding;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,0,0) (sonic3k.asm:88383) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_DASH_ELEVATOR);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
