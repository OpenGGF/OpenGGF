package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * An ObjectIndex slot whose routine is {@code NullObject} (objects/null.asm:3-5: {@code move.b #0,(a0); rts}). Spawning
 * it still takes a dynamic slot (FindObjSlot), and its first turn clears the ID byte, so the slot is free again for
 * everything that spawns after it. The stage's index decides which IDs are null, so a zone registers it only for
 * the stages whose index says so; elsewhere the ID is a real routine or, in Tidal Tempest's index, the compatibility
 * stub ({@code moveq #0,d0; rts}, Level/Tidal Tempest/R4 Compatibility.asm:204-206) that leaves the object in its slot.
 */
public final class CdNullObject extends CdObject implements ObjectVisualFacing.NonDirectional {
    public CdNullObject(ObjectSpawn spawn) {
        super(spawn, "Null");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        setDestroyed(true);                  // move.b #0,(a0)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) { }
}
