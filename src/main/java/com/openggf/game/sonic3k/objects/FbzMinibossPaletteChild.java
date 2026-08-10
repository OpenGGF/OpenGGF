package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** Attack-start palette script 6FAE0: EEE/1, 644/4, six runs, then delete. */
final class FbzMinibossPaletteChild extends AbstractObjectInstance implements RewindRecreatable {
    private static final int[] COLOR_INDEX = {15};
    private static final int[] BRIGHT = {0xEEE};
    private static final int[] DARK = {0x644};
    private FbzMinibossInstance boss;
    private int familySlot;
    private int timer;
    private int step;
    private int runs;

    FbzMinibossPaletteChild(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY() - 8, 0xAA, 0, 0, false, 0), "FBZMinibossPalette");
        this.boss = boss; familySlot = boss.getSlotIndex();
    }
    private FbzMinibossPaletteChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossPalette");
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        int[] color = step == 0 ? BRIGHT : DARK;
        if (tryServices() != null && services().currentLevel() != null) {
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.FBZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, COLOR_INDEX, color);
        }
        if (timer-- > 0) return;
        if (step == 0) { step = 1; timer = 4; }
        else if (++runs >= 6) ObjectLifetimeOps.expireDynamic(this);
        else { step = 0; timer = 1; }
    }
    @Override public FbzMinibossPaletteChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossPaletteChild(ctx.spawn());
    }
    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }
    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }
    @Override public int getX() { return boss == null ? getSpawn().x() : boss.getX(); }
    @Override public int getY() { return boss == null ? getSpawn().y() : boss.getY() - 8; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
