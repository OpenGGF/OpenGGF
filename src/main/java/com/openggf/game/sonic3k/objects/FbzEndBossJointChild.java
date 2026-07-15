package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Arm anchor from ChildObjDat_70EF4. */
public final class FbzEndBossJointChild extends AbstractFbzEndBossChild {
    private FbzEndBossArmChild arm;
    private int armIndex;
    private boolean linksSpawnAttempted;

    FbzEndBossJointChild(FbzEndBossInstance boss, FbzEndBossArmChild arm) {
        this(boss, Integer.parseInt(arm.rewindRole().substring(4)), arm);
    }
    private FbzEndBossJointChild(FbzEndBossInstance boss, int armIndex, FbzEndBossArmChild arm) {
        super(boss, "joint:" + armIndex, "FBZEndBossJoint", armIndex);
        this.arm = arm;
        this.armIndex = armIndex;
        if (arm != null) {
            this.x = arm.getX();
            this.y = arm.getY() - 0x20;
        }
    }
    public FbzEndBossJointChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "joint:0", "FBZEndBossJoint");
    }

    public static int nativeCount() { return 2; }
    FbzEndBossChainLinkChild spawnLink(int index) {
        FbzEndBossChainLinkChild child = spawnChild(() -> new FbzEndBossChainLinkChild(boss, arm, this, index));
        return child == null || child.getSlotIndex() < 0 ? null : child;
    }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!linksSpawnAttempted) {
            linksSpawnAttempted = true;
            for (int i = 0; i < 4; i++) {
                FbzEndBossChainLinkChild link = spawnLink(i);
                if (link == null) break;
                boss.attach(link);
            }
        }
        if (arm == null || arm.isDestroyed()) {
            com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this); return;
        }
        x = arm.getX();
        y = arm.getY() + FbzEndBossInstance.circleOffset2(boss.angle());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(2, x, y,
                FbzEndBossChainLinkChild.nativeFlipX(armIndex), false);
    }
    @Override public int getPriorityBucket() { return (byte) boss.angle() > 0 ? 4 : 6; }
    @Override public boolean isHighPriority() { return (byte) boss.angle() >= 0; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossJointChild(ctx.spawn());
    }
}
