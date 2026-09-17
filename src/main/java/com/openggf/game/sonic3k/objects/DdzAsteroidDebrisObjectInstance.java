package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_823EE} (sonic3k.asm:174477-174500): a Doomsday asteroid fragment. It keeps the
 * asteroid's mappings and {@code art_tile}, takes {@code word_83208} (priority {@code $300},
 * frame {@code $29}), and each frame subtracts {@code _unkFAAE}, steps {@code Animate_Raw} through
 * {@code byte_832A8} (subtype 0) or {@code byte_832AE}, moves with {@code MoveSprite2} and deletes
 * outside the coarse X window.
 */
public final class DdzAsteroidDebrisObjectInstance extends AbstractDdzObjectInstance {
    private static final int ASTEROID_PALETTE = 1;

    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    private int kind;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzAsteroidDebrisObjectInstance(int x, int y, int kind, int xVel, int yVel) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, kind & 0xFF, 0, false, 0), "DDZAsteroidDebris", null);
        this.xPos = (x & 0xFFFF) << 16;
        this.yPos = (y & 0xFFFF) << 16;
        this.kind = kind & 0xFF;
        this.xVel = (short) xVel;
        this.yVel = (short) yVel;
        S3kRawAnimation.set(animation, this.kind == 0
                ? Sonic3kConstants.DDZ_ANIM_DEBRIS_SMALL_ADDR
                : Sonic3kConstants.DDZ_ANIM_DEBRIS_LARGE_ADDR);
        animation.mappingFrame = 0x29;
    }

    @Override
    public DdzAsteroidDebrisObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzAsteroidDebrisObjectInstance(ctx.spawn().x(), ctx.spawn().y(), ctx.spawn().subtype(), 0, 0);
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        DdzObjectSupport.rawAnimations(services()).animateNoSst(animation, animation.script, () -> { });
        xPos += xVel << 8;
        yPos += yVel << 8;
        if (DdzObjectSupport.outOfRangeX(services(), getX())) {
            deleteNow();
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 0xC;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0xC;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x300);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false, ASTEROID_PALETTE);
        }
    }


}
