package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.features.HCZWaterSkimHandler;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Slot/lifetime owner for HCZ's interactive water-skim SST record.
 *
 * <p>The ROM installs {@code Obj_HCZWaterSplash} subtype 1 at
 * {@code Dynamic_object_RAM+2}, absolute SST slot 5, during HCZ level init
 * (sonic3k.asm:7839-7841). Its player-facing state is coordinated by
 * {@link com.openggf.game.sonic3k.features.HCZWaterSkimHandler}; this object
 * keeps the native slot occupied so later {@code AllocateObject} calls scan
 * the same SST window as the ROM.
 */
public final class HCZWaterSkimSlotObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, ObjectVisualFacing.NonDirectional {

    private static final ObjectSpawn SLOT_SPAWN =
            new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

    public HCZWaterSkimSlotObjectInstance() {
        this(SLOT_SPAWN);
    }

    public HCZWaterSkimSlotObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HCZWaterSkim");
    }

    @Override
    public HCZWaterSkimSlotObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HCZWaterSkimSlotObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed() || player == null) return;
        // HCZWaterSplash_SkimMain copies P1's X, processes BOTH participants,
        // then HCZWaterSplash_DisplaySkim calls Sprite_OnScreen_Test
        // (sonic3k.asm:75360-75404). The handler has already run at each
        // player's post-physics boundary; being active does not exempt the
        // native owner from this tail. P2's position is not the range anchor.
        coarseXCullViewport(player.getCentreX());
        if (isDestroyed()) HCZWaterSkimHandler.retireSlot();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The handler renders the player-following splash at the native routine's
        // post-player position; the slot owner itself has no independent sprite.
    }

    @Override
    public boolean isPersistent() {
        // Own the post-participant range tail instead of manager pre-culling
        // the dummy spawn position. This does not mean an immortal SST slot.
        return true;
    }
}
