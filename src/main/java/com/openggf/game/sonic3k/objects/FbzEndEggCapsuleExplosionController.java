package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** {@code CreateBossExp08 -> Obj_NormalExpControl} controller used by the final capsule. */
public final class FbzEndEggCapsuleExplosionController extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int remaining = 8;
    private int waitCounter;

    public FbzEndEggCapsuleExplosionController(ObjectSpawn spawn) {
        super(spawn, "FBZEndEggCapsuleExplosionController");
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (waitCounter-- > 0) return;
        if (--remaining == 0) { ObjectLifetimeOps.expireDynamic(this); return; }
        waitCounter = 2;
        ObjectManager manager = services().objectManager();
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return;
        int random = services().rng().nextRaw();
        int x = getX() + (random & 0x3F) - 0x20;
        int y = getY() + ((random >>> 16) & 0x3F) - 0x20;
        FbzEndEggCapsuleNormalExplosion child;
        try {
            child = ObjectConstructionContext.with(services(), slot,
                    () -> new FbzEndEggCapsuleNormalExplosion(new ObjectSpawn(
                            x, y, 0, 0, 0, false, 0), services()));
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
        ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
    }
    @Override public int getX(){return spawn.x();}
    @Override public int getY(){return spawn.y();}
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    /** Routine-2 normal explosion: no animal allocation, priority bit set, priority word $80. */
    public static final class FbzEndEggCapsuleNormalExplosion extends ExplosionObjectInstance {
        public FbzEndEggCapsuleNormalExplosion(ObjectSpawn spawn, ObjectServices services) {
            super(spawn.objectId(), spawn.x(), spawn.y(),
                    services == null ? null : services.renderManager(), Sonic3kSfx.BREAK.id);
        }
        @Override public boolean isHighPriority(){return true;}
        @Override public int getPriorityBucket(){return 1;}
    }
}
