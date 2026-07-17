package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;

/**
 * {@code CreateBossExp00}: the subtype-zero boss-explosion controller.
 *
 * <p>The zeroed wait word underflows on the first execution, so the first
 * allocation attempt is immediate. The native $20 byte counter is decremented
 * before allocation and deletes at zero, producing 31 one-shot attempts at a
 * three-update cadence (sonic3k.asm:176659-176890).
 */
final class FbzMinibossExplosionController extends AbstractS3kBossExplosionObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    private FbzMinibossInstance boss;

    FbzMinibossExplosionController(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY(), 0xAA, 0, 0, false, 0), "FBZMinibossExplosionControl");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
    }

    private FbzMinibossExplosionController(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossExplosionControl");
    }

    @Override
    public FbzMinibossExplosionController recreateForRewind(RewindRecreateContext ctx) {
        return new FbzMinibossExplosionController(ctx.spawn());
    }

    @Override protected void afterRewindRestoreSettled() {
        if (tryServices() != null) FbzMinibossRewindLinks.settle(services().objectManager(), familySlot);
    }

    int familySlot() { return familySlot; }
    void setBoss(FbzMinibossInstance boss) { this.boss = boss; }

}
