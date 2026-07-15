package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One native arm-chain interpolation segment. */
public final class FbzEndBossChainLinkChild extends AbstractFbzEndBossChild {
    private FbzEndBossArmChild arm;
    private FbzEndBossJointChild joint;
    private int armIndex;
    private int linkIndex;
    private int priorityBucket = 4;
    private boolean initialized;

    FbzEndBossChainLinkChild(FbzEndBossInstance boss, FbzEndBossArmChild arm,
                             FbzEndBossJointChild joint, int linkIndex) {
        this(boss, Integer.parseInt(arm.rewindRole().substring(4)), linkIndex, arm, joint);
    }
    private FbzEndBossChainLinkChild(FbzEndBossInstance boss, int armIndex, int linkIndex,
                                     FbzEndBossArmChild arm, FbzEndBossJointChild joint) {
        super(boss, "link:" + armIndex + ":" + linkIndex, "FBZEndBossChainLink",
                armIndex * 4 + linkIndex);
        this.arm = arm; this.joint = joint; this.armIndex = armIndex; this.linkIndex = linkIndex;
        if (joint != null) { this.x = joint.getX(); this.y = joint.getY(); }
    }
    public FbzEndBossChainLinkChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "link:0:0", "FBZEndBossChainLink");
    }

    public static int nativeCount() { return 8; }
    public static int nativeRootTargetX(int rootX, int armIndex) {
        return rootX + (armIndex == 0 ? -0x1C : 0x1C);
    }
    public static boolean nativeFlipX(int armIndex) { return armIndex != 0; }
    @Override public void update(int frameCounter, PlayableEntity player) {
        if (arm == null || joint == null || arm.isDestroyed() || joint.isDestroyed()) {
            com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this); return;
        }
        if (!initialized) { initialized = true; return; }
        int numerator = linkIndex + 1;
        int headX = nativeRootTargetX(boss.getX(), armIndex);
        int headY = boss.getY() + 2;
        x = joint.getX() + ((headX - joint.getX()) * numerator >> 2);
        y = joint.getY() + ((headY - joint.getY()) * numerator >> 2);
        if ((byte) boss.angle() > 0) priorityBucket = 3 - linkIndex;
    }
    @Override public int getPriorityBucket() { return priorityBucket; }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(2, x, y, nativeFlipX(armIndex), false);
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossChainLinkChild(ctx.spawn());
    }
}
