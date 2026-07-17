package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** One of the two native $A3 touch-hazard orbiting arms. */
public final class FbzEndBossArmChild extends AbstractFbzEndBossChild implements TouchResponseProvider {
    public static final int ACTIVE_COLLISION_FLAGS = 0xA3;
    private static final List<Integer> VELOCITIES = List.of(0x100, 0x100, -0x100, -0x100);
    private static final List<Integer> WAITS = List.of(7, 0, 0, 7);
    private int armIndex;
    private int dx;
    private int dy;
    private int velocityX;
    private int waitTimer;
    private int moveTimer;
    private int motionPhase;
    private boolean jointSpawnAttempted;
    private boolean debrisSpawned;

    FbzEndBossArmChild(FbzEndBossInstance boss, int armIndex, int dx, int dy) {
        super(boss, "arm:" + armIndex, "FBZEndBossArm", armIndex);
        this.armIndex = armIndex;
        this.dx = dx;
        this.dy = dy;
        this.x = boss.getX() + dx;
        this.y = boss.getY() + dy;
    }
    public FbzEndBossArmChild(com.openggf.level.objects.ObjectSpawn spawn) {
        super(spawn, "arm:0", "FBZEndBossArm");
    }

    public static int nativeCount() { return 2; }
    public static List<Integer> nativeVelocities() { return VELOCITIES; }
    public static List<Integer> nativeWaits() { return WAITS; }

    FbzEndBossJointChild spawnJoint() {
        FbzEndBossJointChild child = spawnChild(() -> new FbzEndBossJointChild(boss, this));
        return child == null || child.getSlotIndex() < 0 ? null : child;
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!jointSpawnAttempted) {
            jointSpawnAttempted = true;
            FbzEndBossJointChild joint = spawnJoint();
            if (joint != null) boss.attach(joint);
            return;
        }
        if (boss.isDismantling()) {
            if (!debrisSpawned) { debrisSpawned = true; spawnDebris(); }
            com.openggf.level.objects.ObjectLifetimeOps.expireDynamic(this); return;
        }
        switch (motionPhase) {
            case 0 -> {
                if (boss.areArmsAnchored()) {
                    motionPhase = 1;
                    return;
                }
                x = boss.getX() + dx;
                y = boss.getY() + dy;
            }
            case 1 -> {
                if (!boss.isArmTriggerActive()) return;
                // word_709FC: root render bit 0 is clear when the selected target
                // is right, selecting the +$100 half of the table.
                int tableIndex = armIndex + (boss.isFacingRight() ? 0 : 2);
                velocityX = VELOCITIES.get(tableIndex);
                waitTimer = WAITS.get(tableIndex);
                motionPhase = 2;
            }
            case 2 -> {
                if (--waitTimer < 0) {
                    moveTimer = 7;
                    motionPhase = 3;
                }
            }
            case 3 -> {
                x += velocityX >> 8;
                if (moveTimer-- <= 0) {
                    motionPhase = 1;
                    boss.clearArmTrigger();
                }
            }
            default -> throw new IllegalStateException("Unknown FBZ end-boss arm motion phase " + motionPhase);
        }
    }

    public int getCollisionFlags() { return ACTIVE_COLLISION_FLAGS; }
    @Override public int getCollisionProperty() { return 0; }

    private void spawnDebris() {
        for (FbzEndBossDebrisChild.Spec spec : FbzEndBossDebrisChild.armDebrisTable()) {
            if (spawnChild(() -> new FbzEndBossDebrisChild(new com.openggf.level.objects.ObjectSpawn(
                    x + spec.dx(), y + spec.dy(), FbzEndBossInstance.OBJECT_ID,
                    spec.frame(), armIndex, false, 0), spec.velocityX(), spec.velocityY())) == null) break;
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.FBZ_END_BOSS);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(1, x, y, !boss.isFacingRight(), false);
    }
    @Override public int getPriorityBucket() { return 5; }
    @Override public boolean isHighPriority() { return true; }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FbzEndBossArmChild(ctx.spawn());
    }
}
