package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/** Real {@code Child6_CreateBossExplosion} subtype-4 controller slot. */
public final class CnzEndBossExplosionControllerChild extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int RANGE = 0x20;
    private int centreX;
    private int centreY;
    private int remaining = 0x80;
    private int interval = 2;

    CnzEndBossExplosionControllerChild(int centreX, int centreY, int subtype) {
        this(new ObjectSpawn(centreX, centreY, 0, subtype, 0, false, 0));
    }

    private CnzEndBossExplosionControllerChild(ObjectSpawn spawn) {
        super(spawn, "CNZEndBossExplosionController");
        centreX = spawn.x();
        centreY = spawn.y();
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CnzEndBossExplosionControllerChild(ctx.spawn());
    }

    @Override public int getX() { return centreX; }
    @Override public int getY() { return centreY; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (--interval >= 0) return;
        if (--remaining <= 0) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        int random = services().rng().nextRaw();
        int x = centreX + (random & (RANGE * 2 - 1)) - RANGE;
        int y = centreY + ((random >> 8) & (RANGE * 2 - 1)) - RANGE;
        services().playSfx(Sonic3kSfx.EXPLODE.id);
        spawnChild(() -> new S3kBossExplosionChild(x, y));
        interval = 2;
    }

    @Override public int getPriorityBucket() { return 5; }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
