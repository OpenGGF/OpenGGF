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
 * The fireball {@code Obj_LRZFireballLauncher} allocates, ROM routine {@code loc_42C80}
 * (sonic3k.asm:88198-88211). It shares the launcher's {@code Map_LRZFireballLauncher} mappings but
 * is drawn on palette line 0 rather than the launcher's line 3, because the parent writes
 * {@code make_art_tile(ArtTile_LRZMisc,0,0)} into the child (:88179).
 *
 * <p>{@code MoveSprite2} (:88208) applies {@code x_vel} with no gravity term, and {@code x_vel} is
 * {@code $200} at birth, negated when the launcher's {@code status} bit 0 is set (:88187-88190):
 * two pixels a frame, horizontally only.
 *
 * <p>Its {@code mapping_frame} starts at the zero of a cleared slot and toggles between 0 and 1
 * every fourth frame of {@code Level_frame_counter+1} (:88199-88203) -- the level clock's low byte,
 * not this object's own age -- so every fireball on screen flickers in step.
 *
 * <p>{@code tst.b render_flags(a0) / bpl} deletes it the first frame the previous render pass left
 * it off-screen (:88204-88205, :88211).
 */
public final class LrzFireballObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code move.w #$300,priority(a1)} (sonic3k.asm:88180). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0300);
    /** {@code move.b #$C,width_pixels(a1)} / {@code #8,height_pixels(a1)} (:88181-88182). */
    private static final int HALF_WIDTH = 0x0C;
    private static final int HALF_HEIGHT = 8;
    /** {@code move.w #$200,x_vel(a1)} (:88186), 16.16 pixels a frame. */
    private static final int SPEED = 0x200;
    /** {@code move.b #$9B,collision_flags(a1)} (:88183). */
    private static final int COLLISION_FLAGS = 0x9B;
    /** {@code andi.b #3,d0} on {@code Level_frame_counter+1} (:88200). */
    private static final int ANIMATION_MASK = 3;

    /** ROM {@code x_vel(a1)}. Non-final so rewind sees restorable state. */
    private int xVelocity;
    /** 16.16 position, since {@code MoveSprite2} accumulates sub-pixels. */
    private int xPosition;
    private int yPosition;
    /** ROM {@code mapping_frame(a0)}, zero in a freshly cleared slot. */
    private int mappingFrame;

    /** Production constructor; {@code mirrored} is the launcher's {@code status} bit 0. */
    public LrzFireballObjectInstance(int x, int y, boolean mirrored) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.LBZ_PIPE_PLUG, 0, 0, false, 0),
                "LRZFireball");
        this.xVelocity = mirrored ? -SPEED : SPEED;
        this.xPosition = (x & 0xFFFF) << 16;
        this.yPosition = (y & 0xFFFF) << 16;
        this.mappingFrame = 0;
    }

    /** Probe constructor for rewind recreation and reflection-level tests. */
    public LrzFireballObjectInstance(ObjectSpawn spawn) {
        this(spawn == null ? 0 : spawn.x(), spawn == null ? 0 : spawn.y(), false);
    }

    @Override
    public LrzFireballObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        int x = spawn != null ? spawn.x() : 0;
        int y = spawn != null ? spawn.y() : 0;
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzFireballObjectInstance(x, y, false));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // move.b (Level_frame_counter+1).w,d0 / andi.b #3,d0 / bne (sonic3k.asm:88199-88200):
        // the level clock's low byte, not this object's age.
        if ((levelFrameCounterOrFallback(vIntRunCount) & ANIMATION_MASK) == 0) {
            // addq.b #1,mapping_frame(a0) / andi.b #1,mapping_frame(a0).
            mappingFrame = (mappingFrame + 1) & 1;
        }
        // tst.b render_flags(a0) / bpl -> Delete_Current_Sprite (:88204-88205, :88211).
        if (!isWithinSolidContactBounds()) {
            setDestroyedByOffscreen();
            return;
        }
        // jsr (MoveSprite2): ext.l / lsl.l #8 / add.l (sonic3k.asm:36054-36057), so the 8.8
        // velocity lines up with the middle sixteen bits of the 16.16 position. $200 is two
        // pixels a frame, not two subpixels.
        xPosition += xVelocity << 8;
        updateDynamicSpawn((xPosition >> 16) & 0xFFFF, (yPosition >> 16) & 0xFFFF);
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

    /** ROM {@code x_vel(a0)}. */
    public int xVelocity() {
        return xVelocity;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    public int getCentreX() {
        return (xPosition >> 16) & 0xFFFF;
    }

    public int getCentreY() {
        return (yPosition >> 16) & 0xFFFF;
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
        // make_art_tile(ArtTile_LRZMisc,0,0) (sonic3k.asm:88179) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_HEIGHT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_FIREBALL);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
