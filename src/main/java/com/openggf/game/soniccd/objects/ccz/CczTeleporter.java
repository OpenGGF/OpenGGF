package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import java.util.List;
import com.openggf.graphics.GLCommand;

/**
 * Teleporter, object $2B ({@code ObjTeleporter}, Level/Collision Chaos/Objects/Teleporter/Main.asm:7-8): the shipped routine
 * is a single {@code jmp DeleteObject}; the mappings after it are never used. The object deletes itself on its first run.
 */
public final class CczTeleporter extends CczObject implements ObjectVisualFacing.NonDirectional {
    public CczTeleporter(ObjectSpawn spawn) {
        super(spawn, "Teleporter");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        setDestroyed(true);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
