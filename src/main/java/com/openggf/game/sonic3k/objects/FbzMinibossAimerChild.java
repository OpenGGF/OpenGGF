package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Exact closest-native-pair {@code Find_SonicTails8Way} aimer. */
final class FbzMinibossAimerChild extends AbstractObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    private enum State { INIT, WAIT_START, START_DELAY, ACTIVE }
    private static final State[] STATES = State.values();

    private FbzMinibossInstance boss;
    private int familySlot;
    private int mappingFrame = 9;
    private int stateOrdinal;
    private int timer;
    /** Latent SST x_pos/y_pos words; Child_Draw_Sprite2 renders from the root. */
    private int nativeX;
    private int nativeY;

    FbzMinibossAimerChild(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY() - 8, 0xAA, 8, 0, false, 0), "FBZMinibossAimer");
        this.boss = boss;
        familySlot = boss.getSlotIndex();
        nativeX = getSpawn().x();
        nativeY = getSpawn().y();
    }

    private FbzMinibossAimerChild(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossAimer");
        nativeX = spawn.x();
        nativeY = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss != null && boss.rootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE)) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        switch (STATES[stateOrdinal]) {
            case INIT -> stateOrdinal = State.WAIT_START.ordinal();
            case WAIT_START -> {
                if (boss != null && boss.isPlungerStarted()) {
                    stateOrdinal = State.START_DELAY.ordinal();
                    timer = 0x40;
                }
            }
            case START_DELAY -> {
                if (--timer < 0) stateOrdinal = State.ACTIVE.ordinal();
            }
            case ACTIVE -> updateAim();
        }
    }

    private void updateAim() {
        if (tryServices() == null) return;
        PlayableEntity target = closestNativePlayer(getX());
        if (target == null) return;
        int dx = (short) (target.getCentreX() - getX());
        int dy = (short) (target.getCentreY() - getY());
        mappingFrame = 9 + findSonicTails8Way(dx, dy);
    }

    PlayableEntity closestNativePlayer(int referenceX) {
        ObjectPlayerQuery query = services().playerQuery();
        PlayableEntity p1 = query.mainPlayerOrNull();
        PlayableEntity p2 = query.nativeP2OrNull();
        if (p1 == null) return p2;
        if (p2 == null) return p1;
        int d1 = Math.abs((short) (p1.getCentreX() - referenceX));
        int d2 = Math.abs((short) (p2.getCentreX() - referenceX));
        return d1 <= d2 ? p1 : p2;
    }

    PlayableEntity captureOutwardLungeTarget() {
        return services().playerQuery().mainPlayerOrNull();
    }

    /** Literal integer port of the ROM octant routine, including its horizontal-branch quirk. */
    static int findSonicTails8Way(int dx, int dy) {
        int xSide = dx > 0 ? 2 : 0;
        int ySide = dy > 0 ? 2 : 0;
        int absX = Math.abs((short) dx);
        int absY = Math.abs((short) dy);
        if (absX == absY) {
            if (xSide != 0) return ySide == 0 ? 1 : 3;
            return ySide != 0 ? 5 : 7;
        }
        if (absY < absX) {
            // In the ROM the two threshold compares accidentally inspect d2
            // (the absolute X distance) rather than the computed ratio. Normal
            // level deltas are below $8000, yielding the native left/right octants.
            return xSide != 0 ? 2 : 6;
        }
        long ratio = absY == 0 ? 0 : (((long) absX << 16) / absY);
        boolean diagonal = ratio >= 0x8000;
        if (ySide == 0) {
            if (!diagonal) return 0;
            return xSide != 0 ? 1 : 7;
        }
        if (!diagonal) return 4;
        return xSide != 0 ? 3 : 5;
    }

    int mappingFrame() { return mappingFrame; }
    static int activationWaitUpdates() { return 65; }

    @Override
    public FbzMinibossAimerChild recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossAimerChild(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }

    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }

    @Override public int getX() { return boss == null ? getSpawn().x() : boss.getX(); }
    @Override public int getY() { return boss == null ? getSpawn().y() : boss.getY() - 8; }

    @Override
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        // The ROM's carried-SST loop writes this slot's own position words even
        // though Child_Draw_Sprite2 obtains the visible coordinates from the
        // root. Preserve that latent state independently of the derived draw.
        nativeX = (nativeX + offsetX) & 0xFFFF;
        nativeY = (nativeY + offsetY) & 0xFFFF;
    }
    @Override public int getPriorityBucket() { return 2; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r = getRenderer(Sonic3kObjectArtKeys.FBZ_MINIBOSS);
        if (r != null && r.isReady()) r.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
