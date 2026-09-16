package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.boss.AbstractBossChild;

import java.util.List;

/** ROM {@code ChildObjDat_6BDE6 -> loc_6B50C/loc_6B56C/loc_6B598}. */
final class HczEndBossBubbleParticle extends AbstractBossChild implements RewindRecreatable {
    private static final int WAIT_FRAMES = 0x1F;
    private static final int HORIZONTAL_ACCELERATION = 0x80;

    private boolean initialized;
    private boolean ending;
    private int waitTimer;
    private int xFixed;
    private int xVel;
    private int mappingFrame;

    HczEndBossBubbleParticle(HczEndBossInstance boss) {
        // HCZEndBossBubble_ObjData priority $280 (sonic3k.asm:142188-142191), applied by
        // HCZEndBossBubble_Init's SetUp_ObjAttributes (sonic3k.asm:141315-141317).
        super(boss, "HCZEndBossBubbleParticle", RenderPriority.fromS3kWord(0x280), 0);
    }

    @Override
    public boolean isHighPriority() {
        // HCZEndBossBubble_ObjData art make_art_tile(ArtTile_Bubbles,0,1) sets bit 15
        // (sonic3k.asm:142190).
        return true;
    }

    @Override
    public HczEndBossBubbleParticle recreateForRewind(RewindRecreateContext ctx) {
        HczEndBossInstance restoredBoss = HczEndBossRewindLinks.nearestBoss(ctx);
        return restoredBoss == null ? null : new HczEndBossBubbleParticle(restoredBoss);
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
        if (!initialized) {
            initializeFromRandom(column);
            return;
        }

        accelerateToward(column.getX());
        if (!ending && column.isWaterChildShutdownActive()) {
            ending = true;
            waitTimer = WAIT_FRAMES;
        }
        if (ending && --waitTimer < 0) {
            ObjectLifetimeOps.expireDynamic(this);
        }
        updateDynamicSpawn();
    }

    private void initializeFromRandom(HczEndBossWaterColumn column) {
        int random = services().rng().nextRaw();
        currentX = column.getX() + (byte) random;
        currentY = column.getWaterSurfaceYForChildren() + ((random >>> 16) & 0x1F);
        mappingFrame = (random >>> 16) & 3;
        xFixed = currentX << 8;
        waitTimer = WAIT_FRAMES;
        initialized = true;
        updateDynamicSpawn();
    }

    private void accelerateToward(int targetX) {
        xVel += currentX < targetX ? HORIZONTAL_ACCELERATION : -HORIZONTAL_ACCELERATION;
        xFixed += xVel;
        currentX = xFixed >> 8;
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
