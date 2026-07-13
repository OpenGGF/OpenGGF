package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** {@code ChildObjDat_6FAB0}: the fixed FBZ Egg Prison visual and full solid. */
final class FbzMinibossPrisonChild extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider {
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x23, 0x20, 0x1C);

    @RewindTransient(reason = "structural root link restored from stable family slot")
    private FbzMinibossInstance boss;
    private int familySlot;
    private int x;
    private int y;
    private int mappingFrame = 1;

    FbzMinibossPrisonChild(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY(), 0xAA, 0, 0, false, 0),
                "FBZMinibossPrison");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
        this.x = boss.getX();
        this.y = boss.getY();
    }

    private FbzMinibossPrisonChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossPrison");
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!isInRangeAt(x)) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean skipsCpuSidekickWhenRenderFlagOffScreen() {
        return true;
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        return true;
    }

    @Override
    public FbzMinibossPrisonChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossPrisonChild(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }

    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 0x20;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0x28;
    }

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
    }
}
