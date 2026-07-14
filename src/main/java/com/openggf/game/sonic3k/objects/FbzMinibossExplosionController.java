package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomWorldPositionedObject;

import java.util.List;

/**
 * {@code CreateBossExp00}: the subtype-zero boss-explosion controller.
 *
 * <p>The zeroed wait word underflows on the first execution, so the first
 * allocation attempt is immediate. The native $20 byte counter is decremented
 * before allocation and deletes at zero, producing 31 one-shot attempts at a
 * three-update cadence (sonic3k.asm:176659-176890).
 */
final class FbzMinibossExplosionController extends AbstractObjectInstance
        implements RewindRecreatable, RomWorldPositionedObject {
    private FbzMinibossInstance boss;
    private int familySlot;
    private int x;
    private int y;
    private int remaining = 0x20;
    private int waitCounter;

    FbzMinibossExplosionController(FbzMinibossInstance boss) {
        super(new ObjectSpawn(boss.getX(), boss.getY(), 0xAA, 0, 0, false, 0),
                "FBZMinibossExplosionControl");
        this.boss = boss;
        this.familySlot = boss.getSlotIndex();
        this.x = boss.getX();
        this.y = boss.getY();
    }

    private FbzMinibossExplosionController(ObjectSpawn spawn) {
        super(spawn, "FBZMinibossExplosionControl");
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        waitCounter = (short) (waitCounter - 1);
        if (waitCounter >= 0) {
            return;
        }

        remaining = (remaining - 1) & 0xFF;
        if (remaining == 0) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        waitCounter = 2;

        ObjectManager manager = services().objectManager();
        if (manager == null) {
            return;
        }

        // CreateChild6 reserves the slot before CreateBossExplosion consumes
        // Random_Number or plays SFX. Allocation failure is a one-shot failure.
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) {
            return;
        }

        int random = services().rng().nextRaw();
        int explosionX = x + (random & 0x3F) - 0x20;
        int explosionY = y + ((random >>> 16) & 0x3F) - 0x20;
        S3kBossExplosionChild child;
        try {
            child = ObjectConstructionContext.with(services(), slot,
                    () -> new S3kBossExplosionChild(explosionX, explosionY));
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
        ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        services().playSfx(Sonic3kSfx.EXPLODE.id);
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

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void offsetNativePositionWordsPreserveSubpixel(int offsetX, int offsetY) {
        x = (x + offsetX) & 0xFFFF;
        y = (y + offsetY) & 0xFFFF;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Controller-only SST entry.
    }
}
