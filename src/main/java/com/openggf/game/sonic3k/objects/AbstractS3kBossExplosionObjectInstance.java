package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Exact real-SST subtype-zero CreateBossExplosion controller shared by FBZ bosses. */
abstract class AbstractS3kBossExplosionObjectInstance extends AbstractObjectInstance {
    protected int familySlot;
    protected int x;
    protected int y;
    protected int remaining = 0x20;
    protected int waitCounter;

    AbstractS3kBossExplosionObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        waitCounter = (short) (waitCounter - 1);
        if (waitCounter >= 0) return;

        remaining = (remaining - 1) & 0xFF;
        if (remaining == 0) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        waitCounter = 2;
        ObjectManager manager = services().objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return;

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

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }

    public void offsetNativePositionWordsPreserveSubpixel(int dx, int dy) {
        x = (x + dx) & 0xFFFF;
        y = (y + dy) & 0xFFFF;
    }

    @Override public final void appendRenderCommands(List<GLCommand> commands) { }
}
