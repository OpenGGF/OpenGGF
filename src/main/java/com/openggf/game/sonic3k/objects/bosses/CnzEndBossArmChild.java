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
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One of the four {@code ChildObjDat_6EDD4 -> loc_6E95A} orbiting arms. */
final class CnzEndBossArmChild extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    @RewindTransient(reason = "Structural boss graph link recreated with the parent.")
    private final CnzEndBossInstance boss;
    private int centreX;
    private int centreY;
    private int angle;
    private int angularStep = 1;
    private int speedTimer = 0x40;
    private int frame = 1;
    private static final int[] ANGLE_X = {
            0,1,2,3,4,5,6,8,9,10,11,12,13,14,15,16,
            17,18,19,20,21,22,23,24,24,25,26,27,28,29,30,30,
            31,32,33,33,34,35,35,36,37,37,38,38,39,39,40,40,
            41,41,41,42,42,42,43,43,43,43,44,44,44,44,44,44
    };

    CnzEndBossArmChild(CnzEndBossInstance boss, int phase) {
        super(new ObjectSpawn(boss.getCentreX(), boss.getCentreY() + 8, 0, phase, 0, false, 0),
                "CNZEndBossArm");
        this.boss = boss;
        this.angle = phase & 0xFF;
    }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        CnzEndBossInstance restoredBoss = CnzEndBossRewindLinks.boss(ctx);
        return restoredBoss == null ? null : new CnzEndBossArmChild(restoredBoss, ctx.spawn().subtype());
    }
    @Override public int getX() { return centreX - 8; }
    @Override public int getY() { return centreY - 0x10; }
    public int getCentreX() { return centreX; }
    public int getCentreY() { return centreY; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss.isDestroyed() || boss.defeatStarted()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        CnzEndBossInstance.Routine routine = boss.nativeRoutine();
        if (routine.ordinal() >= CnzEndBossInstance.Routine.ALIGN.ordinal()) {
            if (--speedTimer < 0) {
                speedTimer = 0x40;
                if (routine == CnzEndBossInstance.Routine.ALIGN
                        || (routine == CnzEndBossInstance.Routine.CHARGE && !boss.magneticFieldActive())) {
                    angularStep = Math.min(4, angularStep + 1);
                } else if (routine == CnzEndBossInstance.Routine.DESCEND
                        || routine == CnzEndBossInstance.Routine.ASCEND) {
                    angularStep = Math.max(1, angularStep - 1);
                }
            }
            angle = (angle + angularStep) & 0xFF;
        }
        int offsetX = angleXOffset(angle);
        if (boss.facingRight()) offsetX = -offsetX;
        centreX = boss.getCentreX() + offsetX;
        centreY = boss.getCentreY() + 8;
        frame = frameForAngle(angle);
        updateDynamicSpawn(centreX, centreY);
    }

    private static int angleXOffset(int angle) {
        int a = angle & 0xFF;
        int quadrant = a >>> 6;
        return switch (quadrant) {
            case 0 -> ANGLE_X[a];
            case 1 -> ANGLE_X[0x7F - a];
            case 2 -> -ANGLE_X[a & 0x3F];
            default -> -ANGLE_X[0xFF - a];
        };
    }

    private static int frameForAngle(int angle) {
        int a = angle & 0xFF;
        if (a < 0x30) return 1;
        if (a < 0x58) return 8;
        if (a < 0xA8) return 2;
        if (a < 0xD0) return 8;
        return 1;
    }

    @Override public int getCollisionFlags() { return 0x9E; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getPriorityBucket() { return (angle + 0x40 & 0x80) == 0 ? 4 : 5; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(frame, centreX, centreY, false, false);
    }
}
