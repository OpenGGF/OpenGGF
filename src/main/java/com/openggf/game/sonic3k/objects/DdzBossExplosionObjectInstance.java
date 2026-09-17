package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_826CC} / {@code loc_826E2} (sonic3k.asm:174707-174756): an {@code Obj_BossExplosion1}
 * sprite without its sound that drifts with {@code x_vel $100} decelerating by {@code $100} a frame
 * and the cluster's {@code dy >> 4} as {@code y_vel}. On an exit cluster it also follows the wrap
 * offset and the camera delta. {@code AniRaw_BossExplosion} ends it.
 */
final class DdzBossExplosionObjectInstance extends AbstractDdzObjectInstance {
    private int xPos;
    private int yPos;
    private short xVel = 0x100;
    private short yVel;
    private final boolean followCamera;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzBossExplosionObjectInstance(int x, int y, int yVel, boolean followCamera) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, followCamera ? 1 : 0, 0, false, 0), "DDZBossExplosion", null);
        xPos = (x & 0xFFFF) << 16;
        yPos = (y & 0xFFFF) << 16;
        this.yVel = (short) yVel;
        this.followCamera = followCamera;
        S3kRawAnimation.set(animation, Sonic3kConstants.ANI_RAW_BOSS_EXPLOSION_ADDR);
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzBossExplosionObjectInstance(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), 0, spawn.subtype() != 0);
    }

    @Override
    public DdzBossExplosionObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzBossExplosionObjectInstance(ctx.spawn().x(), ctx.spawn().y(), 0, ctx.spawn().subtype() != 0);
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
        if (followCamera) {
            xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
            xPos += DdzObjectSupport.cameraDelta(services()) << 16;
        }
        boolean[] ended = {false};
        bossExplosionAnimation().animateMultiDelay(animation, () -> ended[0] = true);
        if (ended[0]) {
            goDelete();
            return;
        }
        xVel = (short) (xVel - 0x100);
        xPos += xVel << 8;
        yPos += yVel << 8;
    }

    private S3kRawAnimation bossExplosionAnimation() {
        try {
            return S3kRawAnimation.load(services().romReader(), Sonic3kConstants.ANI_RAW_BOSS_EXPLOSION_ADDR, 0x10);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("AniRaw_BossExplosion", ex);
        }
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable()) {
            return;
        }
        var rm = services().renderManager();
        PatternSpriteRenderer renderer = rm == null ? null : rm.getBossExplosionRenderer();
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false);
        }
    }


}
