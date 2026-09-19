package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One flame from {@code Obj_LRZFlameThrower}, ROM routine {@code loc_44048}
 * (sonic3k.asm:89434-89448). Both thrower variants allocate it with
 * {@code AllocateObjectAfterCurrent} and the same field writes (:89290-89317, :89399-89426), so
 * one class serves both; only the parent's velocity and spawn offset differ.
 *
 * <p>Two independent timers run it, and they are not the same timer:
 * <ul>
 *   <li>{@code $24}, seeded at {@code 8} by the parent (:89316, :89425), steps
 *       {@code mapping_frame} by <b>two</b> and deletes the flame the moment that takes the frame
 *       to {@code 6} or beyond ({@code bhs.s loc_44084}). {@code subq.b}/{@code bpl} makes the
 *       first step land on the ninth frame and each later one eight frames after, because the
 *       step reloads {@code 7} rather than {@code 8}; three steps from frame 0 or 1, so a flame
 *       lives about 25 frames.</li>
 *   <li>{@code $25}, seeded from the parent's own {@code $24}, flips {@code mapping_frame} bit 0
 *       every two frames -- the two-frame flicker inside each pair.</li>
 * </ul>
 * The {@code bhs} test runs only on the {@code $24} step, so a bit-0 flip can put the frame at 7
 * for up to two frames without deleting anything; that is the ROM's own ordering.
 *
 * <p>{@code MoveSprite2} (:89444) applies {@code x_vel}/{@code y_vel} with no gravity term. The
 * flame is a hazard for its whole life: {@code collision_flags} {@code $98} and
 * {@code shield_reaction} bit 4, written once by the parent.
 */
public final class LrzFlameObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code move.w #$300,priority(a1)} (sonic3k.asm:89300, :89409). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0300);
    /** {@code move.b #$C,width_pixels(a1)} / {@code height_pixels(a1)} (:89301-89302). */
    private static final int HALF_EXTENT = 0x0C;
    /** {@code move.b #$98,collision_flags(a1)} (:89303). */
    private static final int COLLISION_FLAGS = 0x98;
    /** {@code move.b #8,$24(a0)} (:89316). */
    private static final int FRAME_STEP_RELOAD = 8;
    /** {@code move.b #7,$24(a0)} on the step (:89436). */
    private static final int FRAME_STEP_REPEAT = 7;
    /** {@code move.b #2,$25(a0)} on the flicker (:89441). */
    private static final int FLICKER_RELOAD = 2;
    /** {@code cmpi.b #6,mapping_frame(a0) / bhs.s loc_44084} (:89438-89439). */
    private static final int FRAME_LIMIT = 6;

    /** ROM {@code x_vel}/{@code y_vel}, 8.8 pixels a frame. */
    private int xVelocity;
    private int yVelocity;
    /** 16.16 positions, because {@code MoveSprite2} accumulates sub-pixels. */
    private int xPosition;
    private int yPosition;
    /** ROM {@code mapping_frame(a0)}, seeded from the parent's {@code $25}. */
    private int mappingFrame;
    /** ROM {@code $24(a0)}, the two-frame step timer. */
    private int frameStepTimer;
    /** ROM {@code $25(a0)}, the bit-0 flicker timer, seeded from the parent's {@code $24}. */
    private int flickerTimer;
    /** {@code move.b render_flags(a0),render_flags(a1)} (:89296): the parent's x/y flip. */
    private boolean flipX;
    private boolean flipY;

    /** Production constructor; every argument is a field the parent writes into the child slot. */
    public LrzFlameObjectInstance(int x, int y, int xVelocity, int yVelocity,
            int mappingFrame, int flickerTimer, boolean flipX, boolean flipY) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.AIZ_DISAPPEARING_FLOOR, 0, 0, false, 0),
                "LRZFlame");
        this.xVelocity = xVelocity;
        this.yVelocity = yVelocity;
        this.xPosition = (x & 0xFFFF) << 16;
        this.yPosition = (y & 0xFFFF) << 16;
        this.mappingFrame = mappingFrame;
        this.flickerTimer = flickerTimer;
        this.frameStepTimer = FRAME_STEP_RELOAD;
        this.flipX = flipX;
        this.flipY = flipY;
    }

    /** Probe constructor for rewind recreation and reflection-level tests. */
    public LrzFlameObjectInstance(ObjectSpawn spawn) {
        this(spawn == null ? 0 : spawn.x(), spawn == null ? 0 : spawn.y(),
                0, 0, 0, 0, false, false);
    }

    @Override
    public LrzFlameObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        int x = spawn != null ? spawn.x() : 0;
        int y = spawn != null ? spawn.y() : 0;
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzFlameObjectInstance(x, y, 0, 0, 0, 0, false, false));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // subq.b #1,$24(a0) / bpl.s loc_44060 (sonic3k.asm:89434-89435).
        frameStepTimer = (frameStepTimer - 1) & 0xFF;
        if (frameStepTimer > 0x7F) {
            frameStepTimer = FRAME_STEP_REPEAT;
            mappingFrame = (mappingFrame + 2) & 0xFF;
            if (mappingFrame >= FRAME_LIMIT) {
                // loc_44084: jmp (Delete_Current_Sprite). The flicker below does not run.
                setDestroyed(true);
                return;
            }
        }
        // subq.b #1,$25(a0) / bpl.s loc_44072 / bchg #0,mapping_frame(a0) (:89440-89443).
        flickerTimer = (flickerTimer - 1) & 0xFF;
        if (flickerTimer > 0x7F) {
            flickerTimer = FLICKER_RELOAD;
            mappingFrame ^= 1;
        }
        // jsr (MoveSprite2): the 8.8 velocity lands on the middle sixteen bits of 16.16.
        xPosition += xVelocity << 8;
        yPosition += yVelocity << 8;
        updateDynamicSpawn(getCentreX(), getCentreY());
    }

    public int getCentreX() {
        return (xPosition >> 16) & 0xFFFF;
    }

    public int getCentreY() {
        return (yPosition >> 16) & 0xFFFF;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVelocity() {
        return xVelocity;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVelocity() {
        return yVelocity;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZ2Misc,1,0) (sonic3k.asm:89299): the priority bit is clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_EXTENT;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_EXTENT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_FLAME);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), flipX, flipY);
    }
}
