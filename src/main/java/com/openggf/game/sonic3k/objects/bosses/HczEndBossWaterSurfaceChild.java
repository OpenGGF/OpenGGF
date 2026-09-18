package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.boss.AbstractBossChild;

import java.util.List;

/** ROM {@code ChildObjDat_6BDCA -> loc_6B456/loc_6B47A}. */
final class HczEndBossWaterSurfaceChild extends AbstractBossChild implements RewindRecreatable {
    private int xOffset;

    HczEndBossWaterSurfaceChild(HczEndBossInstance boss, int xOffset) {
        // HCZEndBossWaterLine_ObjData priority 0 (sonic3k.asm:142171-142172), applied by
        // HCZEndBossWaterLine_Init's SetUp_ObjAttributes3 (sonic3k.asm:141255-141257).
        super(boss, "HCZEndBossWaterSurface", RenderPriority.fromS3kWord(0), 0);
        this.xOffset = xOffset;
    }

    @Override
    public boolean isHighPriority() {
        // HCZEndBoss_WaterLineChildren are created by the platform (sonic3k.asm:141121-141122)
        // or the subtype-0 debris chute (sonic3k.asm:141300) through CreateChild1_Normal, which
        // copies the creator's art_tile (sonic3k.asm:176933): both carry
        // make_art_tile(ArtTile_HCZEndBoss,0,1), bit 15 set (sonic3k.asm:142165, 142180).
        return true;
    }

    @Override
    public HczEndBossWaterSurfaceChild recreateForRewind(RewindRecreateContext ctx) {
        HczEndBossInstance restoredBoss = HczEndBossRewindLinks.nearestBoss(ctx);
        return restoredBoss == null ? null : new HczEndBossWaterSurfaceChild(restoredBoss, xOffset);
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
        currentX = column.getX() + xOffset;
        currentY = column.getWaterSurfaceYForChildren() - 4;
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
