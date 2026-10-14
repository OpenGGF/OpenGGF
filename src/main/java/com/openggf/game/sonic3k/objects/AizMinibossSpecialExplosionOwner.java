package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Real SST for AIZ's Obj_BossExplosionSpecial → CreateBossExp02 / Obj_BossExpControl2. */
final class AizMinibossSpecialExplosionOwner extends AbstractObjectInstance implements RewindRecreatable {
    private int x;
    private int y;
    private int remaining;
    private int waitCounter;
    private boolean initialized;
    private boolean pendingDelete;

    AizMinibossSpecialExplosionOwner(int x, int y) {
        this(new ObjectSpawn(x, y, 0, 0, 0, false, 0));
    }

    private AizMinibossSpecialExplosionOwner(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossSpecialExplosion");
        x = spawn.x(); y = spawn.y();
    }

    @Override public AizMinibossSpecialExplosionOwner recreateForRewind(RewindRecreateContext ctx) {
        return new AizMinibossSpecialExplosionOwner(ctx.spawn());
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) {
            setDestroyed(true);
            return;
        }
        if (!initialized) {
            initialized = true;
            // Obj_BossExplosionSpecial: physical camera words +320/2,+224/2;
            // word2E=2, subtype2. CreateBossExp02 loads count28/ranges80/control2,
            // then tail-jumps Obj_Wait on this same Init dispatch. FixBugs=0.
            x = (services().camera().getX() + 160) & 0xFFFF;
            y = (services().camera().getY() + 112) & 0xFFFF;
            waitCounter = 2;
            remaining = 0x28;
        }
        waitCounter = (short)(waitCounter - 1);
        if (waitCounter >= 0) return;
        remaining = (remaining - 1) & 0xFF;
        if (remaining == 0) {
            // Obj_BossExpControl2 → Go_Delete; the next physical pass clears.
            pendingDelete = true;
            return;
        }
        waitCounter = 2;
        var manager = services().objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, getSlotIndex());
        if (slot < 0) return; // Checked CreateChild6 failure consumes attempt, not RNG/SFX.
        try {
            var child = ObjectConstructionContext.with(services(), slot,
                    () -> S3kBossExplosionChild.createWithNativeInitSfx(x, y));
            // sub_83E90 draws Random_Number only after allocation; SWAP supplies Y.
            int random = services().rng().nextRaw();
            child.writeNativePositionWords(x + (random & 0xFF) - 0x80,
                    y + ((random >>> 16) & 0xFF) - 0x80);
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override public ObjectSpawn getSpawn() { return buildSpawnAt(x, y); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; } // Obj_Wait has no range or parent gate.
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    @Override public void onCarriedAcrossSeamlessTransition(int offsetX, int offsetY) {
        // Like the owning AIZ one-shot cutscene, this physical SST is cleared by
        // the AIZ2 level reload; it is not a surviving cross-act script owner.
        ObjectLifetimeOps.destroyLatched(this);
    }
}
