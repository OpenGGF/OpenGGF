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
    public void offsetNativePositionWordsPreserveSubpixel(int dx, int dy) {
        x = (x + dx) & 0xFFFF; y = (y + dy) & 0xFFFF;
    }
    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) Fbz2SubbossRewindLinks.settle(services().objectManager(), familySlot);
    }
}
