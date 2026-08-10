package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Solid plunger; literal status bit 3 gives native P1 exclusive start authority. */
final class FbzMinibossPlungerChild extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable,
        RomWorldPositionedObject {
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 8, 0xD);
    private FbzMinibossInstance boss;
    private int familySlot;
    private int x;
    private int baseY;
    private int y;
    private boolean p1Standing;
    private boolean defeatDropApplied;

    FbzMinibossPlungerChild(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY() - 0x24, 0xAA, 6, 0, false, 0), "FBZMinibossPlunger");
        this.boss = boss; familySlot = boss.getSlotIndex(); x = getSpawn().x(); baseY = y = getSpawn().y();
    }

    private FbzMinibossPlungerChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossPlunger");
        x = spawn.x(); baseY = y = spawn.y();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (boss == null) return;
        if (boss.isDefeated()) {
            if (!defeatDropApplied) {
                defeatDropApplied = true;
                y += 8;
            }
            if (!isInRange()) ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        boolean depressed = p1Standing || boss.rootBit(FbzMinibossInstance.ROOT_ARM_RETURNED);
        if (depressed) {
            boss.setRootBit(FbzMinibossInstance.ROOT_FIGHT_STARTED);
            y = baseY + 4;
        } else {
            boss.clearRootBit(FbzMinibossInstance.ROOT_FIGHT_STARTED);
            y = baseY;
        }
    }
    void onStandingContact(PlayableEntity player, boolean standing) {
        if (boss == null || tryServices() == null) return;
        if (services().playerQuery().mainPlayerOrNull() == player) {
            p1Standing = standing;
        }
    }
    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        onStandingContact(player, contact.standing());
    }
    @Override public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
        if (tryServices() != null && services().playerQuery().mainPlayerOrNull() == player) p1Standing = false;
    }
    @Override public SolidObjectParams getSolidParams() { return SOLID_PARAMS; }
    @Override public boolean usesInclusiveRightEdge() { return true; }
    @Override public boolean skipsCpuSidekickWhenRenderFlagOffScreen() { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public FbzMinibossPlungerChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossPlungerChild(ctx.spawn());
    }
    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }
    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        x = (x + offsetX) & 0xFFFF;
        y = (y + offsetY) & 0xFFFF;
    }
    @Override public void afterRomWorldTransitionOffset(int offsetX, int offsetY) {
        baseY = (baseY + offsetY) & 0xFFFF;
    }
    @Override public int getPriorityBucket() { return 5; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (r != null && r.isReady()) r.drawFrameIndex(8, x, y, false, false);
    }
}
