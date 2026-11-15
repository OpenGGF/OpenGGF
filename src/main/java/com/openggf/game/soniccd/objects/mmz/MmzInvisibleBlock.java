package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Invisible block, object $09 in the R82x and R83x indexes ({@code InvisibleBlockObject}, objects/r8/invisible_block.asm:3-37):
 * a $10 by $10 solid box. Its init writes no tile word, so DrawObject shows tile 0, which is blank; nothing is
 * drawn here. The routine ends in CheckObjectDespawn.
 */
public final class MmzInvisibleBlock extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    public MmzInvisibleBlock(ObjectSpawn spawn) {
        super(spawn, "InvisibleBlock");
        // InvisibleBlockObject_0_Routine0 (objects/r8/invisible_block.asm:19-26): oXRadius, oWidth and oYRadius $10.
        this.width = 0x10;
        this.yRadius = 0x10;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        solidObject(Entry.SOLID);                    // InvisibleBlockObject_0_Routine2 (:27-32)
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
