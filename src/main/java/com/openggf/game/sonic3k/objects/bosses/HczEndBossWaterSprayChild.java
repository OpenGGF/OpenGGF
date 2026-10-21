package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.boss.AbstractBossChild;

import java.util.List;

/** ROM {@code loc_6B3DE}: independently executed water-column spray child. */
final class HczEndBossWaterSprayChild extends AbstractBossChild implements RewindRecreatable {
    HczEndBossWaterSprayChild(HczEndBossInstance boss) {
        // HCZEndBossColumn_ObjData priority $80 (sonic3k.asm:142233-142234), applied by
        // HCZEndBossColumn_Init's SetUp_ObjAttributes3 (sonic3k.asm:141289-141291).
        super(boss, "HCZEndBossWaterSpray", RenderPriority.fromS3kWord(0x80), 0);
    }

    @Override
    public boolean isHighPriority() {
        // HCZEndBoss_ColumnChild is created by the platform (sonic3k.asm:141204-141205) through
        // CreateChild1_Normal, which copies the platform's art_tile (sonic3k.asm:177024):
        // make_art_tile(ArtTile_HCZEndBoss,0,1), bit 15 set (sonic3k.asm:142230).
        return true;
    }

    @Override
    public HczEndBossWaterSprayChild recreateForRewind(RewindRecreateContext ctx) {
        HczEndBossInstance restoredBoss = HczEndBossRewindLinks.nearestBoss(ctx);
        return restoredBoss == null ? null : new HczEndBossWaterSprayChild(restoredBoss);
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
        HczEndBossWaterColumn column = activeColumn();
        if (column == null || column.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        currentX = column.getX();
        currentY = column.getY();
        updateDynamicSpawn();
    }

    private HczEndBossWaterColumn activeColumn() {
        return services().objectManager().activeObjectsOfType(HczEndBossWaterColumn.class).stream()
                .filter(column -> !column.isDestroyed())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Folded into HczEndBossWaterColumn's consolidated render pass.
    }
}
