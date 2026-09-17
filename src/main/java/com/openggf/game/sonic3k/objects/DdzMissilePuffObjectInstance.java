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
 * ROM {@code loc_8218E} / {@code loc_821C2} (sonic3k.asm:174284-174308) and the boss's
 * {@code loc_826A0} smoke: a puff with {@code word_83202} attributes (priority {@code $200}, frame
 * {@code $18}) that follows the wrap offset, plays {@code byte_832B4} with
 * {@code Animate_RawNoSSTMultiDelay} (deleting at its {@code $F4}), moves with {@code MoveSprite2} and
 * deletes outside the coarse X window.
 */
final class DdzMissilePuffObjectInstance extends AbstractDdzObjectInstance {
    private static final int PALETTE = 2;

    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzMissilePuffObjectInstance(int x, int y, int xVel, int yVel) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, 0, 0, false, 0), "DDZMissilePuff", null);
        xPos = (x & 0xFFFF) << 16;
        yPos = (y & 0xFFFF) << 16;
        this.xVel = (short) xVel;
        this.yVel = (short) yVel;
        S3kRawAnimation.set(animation, Sonic3kConstants.DDZ_ANIM_MISSILE_PUFF_ADDR);
        animation.mappingFrame = 0x18;
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzMissilePuffObjectInstance(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), 0, 0);
    }

    @Override
    public DdzMissilePuffObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzMissilePuffObjectInstance(ctx.spawn().x(), ctx.spawn().y(), 0, 0);
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
        boolean[] ended = {false};
        DdzObjectSupport.rawAnimations(services()).animateMultiDelay(animation, () -> ended[0] = true);
        if (ended[0]) {
            // $34 = Go_Delete_Sprite
            goDelete();
            return;
        }
        xPos += xVel << 8;
        yPos += yVel << 8;
        if (outOfRangeX(getX())) {
            deleteNow();
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x200);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false, PALETTE);
        }
    }


}
