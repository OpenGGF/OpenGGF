package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
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
 * The projectile {@code Obj_LRZShootingTrigger} allocates, ROM routine {@code loc_42EE8}
 * (sonic3k.asm:88336-88346). It shares the trigger's own {@code Map_LRZShootingTrigger} mappings
 * and draws frame 1.
 *
 * <p>{@code MoveSprite2} applies {@code x_vel} and {@code y_vel} with no gravity term, and both are
 * {@code $200} at birth (:88325-88326), so the shot travels diagonally down and away at one pixel
 * every two frames on each axis. The parent's {@code status} bit 0 - the placement's flip flag -
 * negates only {@code x_vel} (:88327-88330).
 *
 * <p>{@code tst.b render_flags(a0) / bpl} deletes it the first frame the previous render pass left
 * it off-screen (:88337-88338, :88345).
 */
public final class LrzShootingTriggerProjectileInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code move.w #$300,priority(a1)} (sonic3k.asm:88318). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0300);
    /** {@code move.b #4,width_pixels(a1)} / {@code height_pixels(a1)} (:88319-88320). */
    private static final int HALF_SIZE = 4;
    /** {@code move.w #$200,x_vel(a1)} / {@code y_vel(a1)} (:88325-88326), 16.16 pixels a frame. */
    private static final int SPEED = 0x200;
    /** {@code move.b #1,mapping_frame(a1)} (:88324). */
    private static final int MAPPING_FRAME = 1;

    /** ROM {@code x_vel(a1)} / {@code y_vel(a1)}. Non-final so rewind sees restorable state. */
    private int xVelocity;
    private int yVelocity;
    /** 16.16 positions, since {@code MoveSprite2} accumulates sub-pixels. */
    private int xPosition;
    private int yPosition;

    /** Production constructor; {@code flipped} is the parent's {@code status} bit 0. */
    public LrzShootingTriggerProjectileInstance(ObjectSpawn spawn, boolean flipped) {
        super(spawn, "LRZShootingTriggerProjectile");
        this.xVelocity = flipped ? -SPEED : SPEED;
        this.yVelocity = SPEED;
        this.xPosition = (spawn.x() & 0xFFFF) << 16;
        this.yPosition = (spawn.y() & 0xFFFF) << 16;
    }

    /** Probe constructor for rewind recreation and reflection-level tests. */
    public LrzShootingTriggerProjectileInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    @Override
    public LrzShootingTriggerProjectileInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzShootingTriggerProjectileInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // tst.b render_flags(a0) / bpl.s loc_42F00 -> Delete_Current_Sprite (sonic3k.asm:88337,
        // 88345). The engine's own off-screen self-delete keeps the spawn respawnable, which is
        // right here: the parent re-allocates a fresh shot every period.
        if (!isWithinSolidContactBounds()) {
            setDestroyedByOffscreen();
            return;
        }
        // jsr (MoveSprite2): x_pos += x_vel, y_pos += y_vel, no gravity.
        xPosition += xVelocity;
        yPosition += yVelocity;
        updateDynamicSpawn((xPosition >> 16) & 0xFFFF, (yPosition >> 16) & 0xFFFF);
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVelocity() {
        return xVelocity;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVelocity() {
        return yVelocity;
    }

    public int getCentreX() {
        return (xPosition >> 16) & 0xFFFF;
    }

    public int getCentreY() {
        return (yPosition >> 16) & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        // move.b #$98,collision_flags(a1) (sonic3k.asm:88321): a harmful projectile.
        return 0x98;
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
        // make_art_tile(ArtTile_LRZMisc,0,0) (sonic3k.asm:88317) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_SHOOTING_TRIGGER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), false, false);
    }
}
