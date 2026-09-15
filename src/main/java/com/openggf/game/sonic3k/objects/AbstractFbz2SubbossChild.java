package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/** Shared scalar/link shell only; every FBZ2 subboss child retains its native lifetime. */
abstract class AbstractFbz2SubbossChild extends AbstractObjectInstance {
    protected Fbz2SubbossInstance root;
    protected int familySlot;
    protected int x;
    protected int y;

    AbstractFbz2SubbossChild(ObjectSpawn spawn, String name) {
        super(spawn, name);
        x = spawn.x(); y = spawn.y();
    }

    void attach(Fbz2SubbossInstance root) { this.root = root; }
    int familySlot() { return familySlot; }
    Fbz2SubbossInstance root() { return root; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    // CreateChild1/3_Normal retain ObjDat_FBZ2Subboss's art_tile bit 15;
    // SetUp_ObjAttributes3 changes the SAT bucket, not the VDP priority bit.
    // The Robotnik/EggRobo child installs the same high bit via ObjDat3_703BC.
    @Override public boolean isHighPriority() { return true; }
    // These child routines use Draw_Sprite while attached. Their own event/escape
    // branches delete them; a generic distance check can remove the control panel
    // and Robotnik before the approaching camera reaches their side of the room.
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    public void offsetNativePositionWordsPreserveSubpixel(int dx, int dy) {
        x = (x + dx) & 0xFFFF; y = (y + dy) & 0xFFFF;
    }
    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) Fbz2SubbossRewindLinks.settle(services().objectManager(), familySlot);
    }
}
