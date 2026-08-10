package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Three one-time cover leaves, loc_6F10E. */
final class FbzMinibossCoverChild extends AbstractObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    private static final int[] INITIAL_TIMERS = {0x20, 0x20, 0x40};
    private static final int[] WAIT_UPDATES = {33, 33, 65};
    private static final int[] X_VELOCITIES = {-0x40, 0x40, 0};
    private static final int[] Y_VELOCITIES = {0, 0, 0x40};
    private FbzMinibossInstance boss;
    private int familySlot;
    private int coverIndex;
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int timer;
    private boolean moving;
    private boolean finished;

    FbzMinibossCoverChild(FbzMinibossInstance boss, int coverIndex, int dx, int dy) {
        super(new ObjectSpawn(boss.getX() + dx, boss.getY() + dy, 0xAA, coverIndex * 2, 0, false, 0),
                "FBZMinibossCover");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
        this.coverIndex = coverIndex;
        x = getSpawn().x(); y = getSpawn().y(); xFixed = x << 8; yFixed = y << 8;
    }

    private FbzMinibossCoverChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossCover");
        x = spawn.x(); y = spawn.y(); xFixed = x << 8; yFixed = y << 8;
    }

    static int[] waitUpdates() { return WAIT_UPDATES.clone(); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (boss != null && boss.rootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (finished) return;
        if (!moving) {
            if (boss == null || !boss.isPlungerStarted()) return;
            moving = true;
            timer = INITIAL_TIMERS[coverIndex];
            return; // loc_6F150 is setup-only; MoveSprite2 starts next update.
        }
        xFixed += X_VELOCITIES[coverIndex];
        yFixed += Y_VELOCITIES[coverIndex];
        x = xFixed >> 8; y = yFixed >> 8;
        if (--timer < 0) finished = true;
    }

    @Override public FbzMinibossCoverChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossCoverChild(ctx.spawn());
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
        xFixed = (x << 8) | (xFixed & 0xFF);
        yFixed = (y << 8) | (yFixed & 0xFF);
    }
    @Override public int getPriorityBucket() { return 2; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (r != null && r.isReady()) r.drawFrameIndex(coverIndex + 1, x, y, false, false);
    }
}
