package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.boss.AbstractBossChild;

import java.util.List;

/** ROM {@code Child1_MakeRoboHead -> Obj_RobotnikHead}. */
final class HczEndBossRobotnikHead extends AbstractBossChild implements RewindRecreatable {
    private static final int Y_OFFSET = -0x1C;

    HczEndBossRobotnikHead(HczEndBossInstance boss) {
        // Child1_MakeRoboHead -> Obj_RobotnikHead applies ObjDat_RobotnikHead priority $280
        // (sonic3k.asm:136645-136648); its art make_art_tile(ArtTile_RobotnikShip,0,0) leaves
        // bit 15 clear, matching the default isHighPriority().
        super(boss, "HCZEndBossRobotnikHead", RenderPriority.fromS3kWord(0x280), 0);
    }

    @Override
    public HczEndBossRobotnikHead recreateForRewind(RewindRecreateContext ctx) {
        HczEndBossInstance restoredBoss = HczEndBossRewindLinks.nearestBoss(ctx);
        return restoredBoss == null ? null : new HczEndBossRobotnikHead(restoredBoss);
    }

    @Override
    protected boolean tracksViaChildComponents() {
        return false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!beginUpdate(vIntRunCount)) {
            return;
        }
        HczEndBossRobotnikShip ship = services().objectManager()
                .activeObjectsOfType(HczEndBossRobotnikShip.class).stream()
                .filter(candidate -> !candidate.isDestroyed())
                .findFirst()
                .orElse(null);
        if (ship == null) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        currentX = ship.getX();
        currentY = ship.getY() + Y_OFFSET;
        updateDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Folded into HczEndBossRobotnikShip's consolidated render pass.
    }
}
