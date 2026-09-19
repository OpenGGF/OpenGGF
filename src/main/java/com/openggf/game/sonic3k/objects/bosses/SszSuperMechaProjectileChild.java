package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** {@code loc_7C726}/{@code sub_7D162}: Super Mecha Sonic's eight-way energy shot. */
public final class SszSuperMechaProjectileChild extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider {
    private static final int[][] VELOCITY = {
            {0, 0x400}, {0x2D4, 0x2D4}, {0x400, 0}, {0x2D4, -0x2D4},
            {0, -0x400}, {-0x2D4, -0x2D4}, {-0x400, 0}, {-0x2D4, 0x2D4}
    };
    private int posX;
    private int posY;
    private int xVel;
    private int yVel;

    private record RewindExtra(int posX, int posY, int xVel, int yVel)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSuperMechaProjectileChild(ObjectSpawn spawn) {
        super(spawn, "SSZSuperMechaProjectile");
        posX = spawn.x() << 16;
        posY = spawn.y() << 16;
        int[] velocity = VELOCITY[spawn.subtype() & 7];
        xVel = velocity[0];
        yVel = velocity[1];
    }

    @Override public SszSuperMechaProjectileChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszSuperMechaProjectileChild(ctx.spawn());
    }

    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(posX, posY, xVel, yVel));
    }

    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                             RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            posX = extra.posX(); posY = extra.posY();
            xVel = extra.xVel(); yVel = extra.yVel();
        }
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        posX += xVel << 8;
        posY += yVel << 8;
        var camera = services().camera();
        int x = getX(), y = getY();
        if (x < (camera.getX() & 0xFFFF) - 0x40
                || x > (camera.getX() & 0xFFFF) + camera.getWidth() + 0x40
                || y < (camera.getY() & 0xFFFF) - 0x40
                || y > (camera.getY() & 0xFFFF) + camera.getHeight() + 0x40) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    @Override public int getCollisionFlags() { return 0x87; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getX() { return (posX >> 16) & 0xFFFF; }
    @Override public int getY() { return (posY >> 16) & 0xFFFF; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    public int xVelForTest() { return xVel; }
    public int yVelForTest() { return yVel; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_EXTRA);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0x15, getX(), getY(), false, false, -1);
        }
    }
}
