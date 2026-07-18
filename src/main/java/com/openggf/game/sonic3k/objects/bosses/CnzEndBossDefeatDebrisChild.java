package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** ROM {@code ChildObjDat_6EDF2}, the two flickering defeat fragments. */
final class CnzEndBossDefeatDebrisChild extends AbstractObjectInstance implements RewindRecreatable {
    @RewindTransient(reason = "Structural boss graph link recreated with the parent.")
    private final CnzEndBossInstance boss;
    private int centreX;
    private int centreY;
    private int xVelocity;
    private int yVelocity = -0x180;
    private int xSubpixel;
    private int ySubpixel;
    private int life = 0x80;

    CnzEndBossDefeatDebrisChild(CnzEndBossInstance boss, int xOffset, int xVelocity) {
        super(new ObjectSpawn(boss.getCentreX() + xOffset, boss.getCentreY(), 0,
                xOffset < 0 ? 0 : 1, 0, false, 0), "CNZEndBossDefeatDebris");
        this.boss = boss;
        this.centreX = boss.getCentreX() + xOffset;
        this.centreY = boss.getCentreY();
        this.xVelocity = xVelocity;
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        CnzEndBossInstance restoredBoss = CnzEndBossRewindLinks.boss(ctx);
        if (restoredBoss == null) return null;
        boolean left = ctx.spawn().subtype() == 0;
        return new CnzEndBossDefeatDebrisChild(restoredBoss, left ? -8 : 8, left ? -0x100 : 0x100);
    }
    @Override public int getX() { return centreX - 0x14; }
    @Override public int getY() { return centreY - 0x14; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        xSubpixel += xVelocity;
        ySubpixel += yVelocity;
        centreX += xSubpixel >> 8;
        centreY += ySubpixel >> 8;
        xSubpixel &= 0xFF;
        ySubpixel &= 0xFF;
        yVelocity += 0x18;
        if (--life <= 0) ObjectLifetimeOps.expireDynamic(this);
        updateDynamicSpawn(centreX, centreY);
    }

    @Override public int getPriorityBucket() { return 5; }
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if ((life & 2) == 0) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0x0B, centreX, centreY, false, false);
    }
}
