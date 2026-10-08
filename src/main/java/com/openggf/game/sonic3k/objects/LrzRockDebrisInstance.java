package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.SubpixelMotion;

import java.util.List;

/**
 * The chip {@code sub_439EC} throws off a grinding {@code Obj_LRZSpikeBall}
 * (sonic3k.asm:88998-89050, ROM {@code $43854}; {@code Map_LRZRockDebris} at ROM {@code $43B12}).
 *
 * <p>The allocation writes every field itself (:89020-89049): {@code Map_LRZRockDebris} on
 * {@code make_art_tile($0D3,2,1)}, {@code ori.b #$84,render_flags} -- which is why bit 7 is
 * already set on the creation frame and the chip does not delete itself before it is first drawn
 * -- {@code priority} zero, a {@code 4 x 4} box, and one {@code Random_Number} draw used twice:
 * the low word masked to {@code $1FF} and centred on {@code -$100} is {@code x_vel}, that value's
 * arithmetic shift right four is added to {@code x_pos} the same frame, and the same draw's HIGH
 * word masked to {@code $1FF} plus {@code -$400} is {@code y_vel}.
 *
 * <p>Its routine {@code loc_43A6C} (:89059-89070) is four frames of animation under
 * {@code MoveSprite}, which applies the {@code $38} gravity, and it deletes itself the first
 * frame {@code render_flags} bit 7 is clear -- "was drawn last frame", the same reading
 * {@link LrzFireballLauncherObjectInstance} records for {@code $1B}.
 */
public final class LrzRockDebrisInstance extends AbstractObjectInstance implements RewindRecreatable {

    /** {@code move.w #0,priority(a1)} (sonic3k.asm:89027). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0000);
    /** {@code move.b #4,width_pixels(a1)} / {@code height_pixels(a1)} (:89028-89029). */
    private static final int HALF_SIZE = 4;

    private final SubpixelMotion.State motion;
    /** ROM {@code mapping_frame(a1)}. */
    private int mappingFrame;
    /**
     * ROM {@code render_flags} bit 7. The allocation sets it (:89026), so the chip survives its
     * creation frame; afterwards only the render pass sets it.
     */
    private boolean renderedLastFrame = true;

    public LrzRockDebrisInstance(int x, int y, int xVel, int yVel) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, false, 0), "LRZRockDebris");
        this.motion = new SubpixelMotion.State(x & 0xFFFF, y & 0xFFFF, 0, 0, xVel, yVel);
    }

    @Override
    public LrzRockDebrisInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzRockDebrisInstance(ctx.spawn().x(), ctx.spawn().y(), 0, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // tst.b render_flags(a0) / bpl loc_43A88 (:89060-89061, :89068).
        if (!renderedLastFrame) {
            // jmp (Delete_Current_Sprite): a routine-spawned dynamic that never respawns.
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        renderedLastFrame = isOnScreen();
        // addq.b #1,mapping_frame(a0) / andi.b #3 (:89062-89063).
        mappingFrame = (mappingFrame + 1) & 3;
        // jsr (MoveSprite): gravity $38 (sonic3k.asm:36038).
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
        updateDynamicSpawn(motion.x, motion.y);
    }

    /** ROM {@code mapping_frame(a1)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code x_vel(a1)}. */
    public int xVel() {
        return (short) motion.xVel;
    }

    /** ROM {@code y_vel(a1)}. */
    public int yVel() {
        return (short) motion.yVel;
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile($0D3,2,1) (sonic3k.asm:89025) sets the priority bit.
        return true;
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_ROCK_DEBRIS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
