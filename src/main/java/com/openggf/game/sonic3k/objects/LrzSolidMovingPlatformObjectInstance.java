package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code Obj_LRZSolidMovingPlatforms} (sonic3k.asm:51012-51110), id {@code $2D} in the locked-on
 * set and 52 of Lava Reef act 2's placements.
 *
 * <p><b>The subtype is read twice, for two different things.</b> {@code lsr.w #2,d0 /
 * andi.w #$1C,d0} (:51019-51020) turns it into a byte offset into {@code byte_25826}, which has
 * only two entries -- {@code $20,$20,0} and {@code $20,$20,1} (:51005-51009) -- so bit 4 picks the
 * skin and nothing else; {@code andi.w #$F,d0} (:51026-51027) picks one of the nine movers in
 * {@code off_258BC}. Anchors are {@code $30 = x_pos} and {@code $34 = y_pos}, saved once.
 *
 * <p><b>The nine movers.</b> Index 0 is an {@code rts}: a placement that never moves. Indices 1, 2
 * (x) and 4, 5 (y) ride {@code Oscillating_table} words {@code +$0A} and {@code +$1E}, centred by
 * subtracting {@code $20} and {@code $40}. Indices 3, 6 (limit {@code $5F}) and 7, 8 (limit
 * {@code $7F}) run {@code sub_25974}'s own ramp instead. Every one of them negates its
 * displacement when {@code status} bit 0 is set, and every one writes ONE coordinate and leaves
 * the other at the anchor.
 *
 * <p><b>{@code sub_25974} is an accelerating triangle</b> (sonic3k.asm:51149-51182), and the
 * fixed point is easy to miss: {@code $36} is a word that {@code add.w} accumulates but
 * {@code cmp.b}/{@code move.b} read as a <em>byte</em>, so it is 8.8 and its high byte is the
 * displacement. {@code $40} is the 8.8 velocity, stepped by {@code 4} every frame -- an
 * acceleration, not a speed -- and {@code $3C} flips it at the limit. So the platform eases out of
 * each end rather than running at a constant rate, and its period depends on the limit.
 *
 * <p><b>The solid call saves the pre-move x on the stack</b> ({@code move.w x_pos(a0),-(sp)} at
 * :51035 and {@code move.w (sp)+,d4} at :51038, either side of the mover), so
 * {@code SolidObjectFull} carries a standing player by this frame's delta. {@code d1} is
 * {@code width_pixels + $B}, the idiom the shared reconstruction already assumes.
 */
public final class LrzSolidMovingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$180,priority(a0)} (sonic3k.asm:51016). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0180);
    /** {@code byte_25826}: both entries are {@code $20,$20}; only {@code mapping_frame} differs. */
    private static final int WIDTH_PIXELS = 0x20;
    private static final int HEIGHT_PIXELS = 0x20;
    /** {@code addi.w #$B,d1} on {@code width_pixels} (sonic3k.asm:51041). */
    private static final int SOLID_WIDTH_MARGIN = 0x0B;
    /** {@code Oscillating_table+$0A} and {@code +$1E} with their own centres (:51056-51066). */
    private static final int OSC_NEAR_OFFSET = 0x0A;
    private static final int OSC_NEAR_CENTRE = 0x20;
    private static final int OSC_FAR_OFFSET = 0x1E;
    private static final int OSC_FAR_CENTRE = 0x40;
    /** {@code move.w #$5F,d2} / {@code #$7F,d2} and their {@code subi.w} centres. */
    private static final int RAMP_SHORT_LIMIT = 0x5F;
    private static final int RAMP_SHORT_CENTRE = 0x60;
    private static final int RAMP_LONG_LIMIT = 0x7F;
    private static final int RAMP_LONG_CENTRE = 0x80;
    /** {@code addq.w #4,d1} / {@code subq.w #4,d1} on the 8.8 velocity (:51155, :51165). */
    private static final int RAMP_ACCELERATION = 4;

    /** The nine entries of {@code off_258BC}, in table order. */
    private enum Mover {
        /** {@code locret_258CE}: the placement never moves. */
        STILL,
        /** {@code loc_258D0}: x from {@code Oscillating_table+$0A}. */
        OSC_NEAR_X,
        /** {@code loc_258DC}: x from {@code Oscillating_table+$1E}. */
        OSC_FAR_X,
        /** {@code loc_25924}: x from the {@code $5F} ramp. */
        RAMP_SHORT_X,
        /** {@code loc_258FA}: y from {@code Oscillating_table+$0A}. */
        OSC_NEAR_Y,
        /** {@code loc_25906}: y from {@code Oscillating_table+$1E}. */
        OSC_FAR_Y,
        /** {@code loc_25938}: y from the {@code $5F} ramp. */
        RAMP_SHORT_Y,
        /** {@code loc_2594C}: x from the {@code $7F} ramp. */
        RAMP_LONG_X,
        /** {@code loc_25960}: y from the {@code $7F} ramp. */
        RAMP_LONG_Y
    }

    private static final Mover[] MOVERS = Mover.values();

    private Mover mover;
    /** ROM {@code mapping_frame(a0)}, from {@code byte_25826}. */
    private int mappingFrame;
    /** ROM {@code status(a0)} bit 0, the placement's x-flip. */
    private boolean mirrored;
    /** ROM {@code $30(a0)} / {@code $34(a0)}: the anchor the mover offsets from. */
    private int anchorX;
    private int anchorY;
    /** ROM {@code $36(a0)}, an 8.8 displacement whose high byte the routine reads. */
    private int rampPosition;
    /** ROM {@code $40(a0)}, the 8.8 velocity {@code sub_25974} accelerates. */
    private int rampVelocity;
    /** ROM {@code $3C(a0)}: zero while the ramp is accelerating outward. */
    private boolean rampReturning;
    /** This frame's drawn position. */
    private int currentX;
    private int currentY;

    public LrzSolidMovingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSolidMovingPlatform");
        int subtype = spawn == null ? 0 : spawn.subtype() & 0xFF;
        // lsr.w #2,d0 / andi.w #$1C,d0 indexes four-byte entries, and byte_25826 has two of them,
        // so the selector is (subtype >> 4) & 1 -- everything above bit 4 reads past the table in
        // the ROM and no Lava Reef placement does that.
        this.mappingFrame = ((subtype >> 4) & 1);
        this.mover = MOVERS[(subtype & 0x0F) % MOVERS.length];
        this.mirrored = spawn != null && (spawn.renderFlags() & 1) != 0;
        this.anchorX = spawn == null ? 0 : spawn.x() & 0xFFFF;
        this.anchorY = spawn == null ? 0 : spawn.y() & 0xFFFF;
        this.currentX = anchorX;
        this.currentY = anchorY;
        this.rampPosition = 0;
        this.rampVelocity = 0;
        this.rampReturning = false;
    }

    /**
     * {@code Obj_LRZSolidMovingPlatforms} sits at ROM {@code $0002582E} (sonic3k.lst); its whole
     * code block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0002}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0002;
    }

    @Override
    public LrzSolidMovingPlatformObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSolidMovingPlatformObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        switch (mover) {
            case STILL -> { }
            case OSC_NEAR_X -> currentX = offsetAnchorX(oscillation(OSC_NEAR_OFFSET, OSC_NEAR_CENTRE));
            case OSC_FAR_X -> currentX = offsetAnchorX(oscillation(OSC_FAR_OFFSET, OSC_FAR_CENTRE));
            case OSC_NEAR_Y -> currentY = offsetAnchorY(oscillation(OSC_NEAR_OFFSET, OSC_NEAR_CENTRE));
            case OSC_FAR_Y -> currentY = offsetAnchorY(oscillation(OSC_FAR_OFFSET, OSC_FAR_CENTRE));
            case RAMP_SHORT_X -> currentX = anchorX + ramp(RAMP_SHORT_LIMIT) - RAMP_SHORT_CENTRE;
            case RAMP_SHORT_Y -> currentY = anchorY + ramp(RAMP_SHORT_LIMIT) - RAMP_SHORT_CENTRE;
            case RAMP_LONG_X -> currentX = anchorX + ramp(RAMP_LONG_LIMIT) - RAMP_LONG_CENTRE;
            case RAMP_LONG_Y -> currentY = anchorY + ramp(RAMP_LONG_LIMIT) - RAMP_LONG_CENTRE;
        }
        currentX &= 0xFFFF;
        currentY &= 0xFFFF;
        updateDynamicSpawn(currentX, currentY);
    }

    /** {@code moveq #0,d0 / move.b (Oscillating_table+n).w,d0 / subi.w #centre,d0}. */
    private int oscillation(int offset, int centre) {
        return (OscillationManager.getByte(offset) & 0xFF) - centre;
    }

    /** {@code btst #0,status(a0) / neg.w d0 / add.w $30(a0),d0}. */
    private int offsetAnchorX(int displacement) {
        return anchorX + (mirrored ? -displacement : displacement);
    }

    private int offsetAnchorY(int displacement) {
        return anchorY + (mirrored ? -displacement : displacement);
    }

    /**
     * {@code sub_25974} (sonic3k.asm:51149-51182). The comparison is {@code cmp.b $36(a0),d2}, so
     * the limit is tested against the 8.8 accumulator's high byte, and the routine returns that
     * byte -- mirrored as {@code limit - byte}, not as {@code -byte}, when {@code status} bit 0
     * is set (:51183-51188).
     */
    private int ramp(int limit) {
        // add.w / subq.w on a word, so both accumulators wrap at sixteen bits; $40 is signed and
        // does go negative on the return half.
        if (!rampReturning) {
            rampVelocity = (short) (rampVelocity + RAMP_ACCELERATION);
            rampPosition = (rampPosition + rampVelocity) & 0xFFFF;
            if (limit <= ((rampPosition >> 8) & 0xFF)) {
                rampReturning = true;
            }
        } else {
            rampVelocity = (short) (rampVelocity - RAMP_ACCELERATION);
            rampPosition = (rampPosition + rampVelocity) & 0xFFFF;
            if (limit > ((rampPosition >> 8) & 0xFF)) {
                rampReturning = false;
            }
        }
        int displacement = (rampPosition >> 8) & 0xFF;
        return mirrored ? limit - displacement : displacement;
    }

    /** ROM {@code $36(a0)}. */
    public int rampPosition() {
        return rampPosition;
    }

    /** ROM {@code $40(a0)}. */
    public int rampVelocity() {
        return rampVelocity;
    }

    /** ROM {@code $3C(a0)}. */
    public boolean isRampReturning() {
        return rampReturning;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    public int getCentreX() {
        return currentX;
    }

    public int getCentreY() {
        return currentY;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // move.b width_pixels(a0),d1 / addi.w #$B,d1; d2 = height_pixels; d3 = d2 + 1.
        return SolidObjectParams.of(WIDTH_PIXELS + SOLID_WIDTH_MARGIN,
                HEIGHT_PIXELS, HEIGHT_PIXELS + 1);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile($090,2,0) (sonic3k.asm:51014): the priority bit is clear.
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_SOLID_MOVING_PLATFORM);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), mirrored, false);
    }
}
