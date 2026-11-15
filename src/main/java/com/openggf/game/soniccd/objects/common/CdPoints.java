package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Points, object $1C with a negative subtype ({@code ObjPoints}, HUD and Points/Main.asm:15-53):
 * frame {@code subtype & $7F} over tile $6C6, rising 2 pixels a frame for 24 frames, then deleted.
 * Sonic 1's Obj29 instead rises with a velocity and gravity.
 */
public final class CdPoints extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private int timer;

    public CdPoints(ObjectSpawn spawn) {
        super(spawn, "Points");
        this.tileWord = CdObjectArtProvider.POINTS_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // ObjPoints_Init: oSprFlags |= 4; tile $6C6; frame = subtype & $7F; timer 24 (HUD and Points/Main.asm:33-40).
            routine = 2;
            timer = 24;
        }
        // ObjPoints_Main: subq.b #1,oPntsTimer; bne .Rise; jmp DeleteObject (HUD and Points/Main.asm:46-53).
        timer = (timer - 1) & 0xFF;
        if (timer == 0) {
            setDestroyed(true);
            return;
        }
        y -= 2;
    }

    public int frame() { return spawn.subtype() & 0x7F; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawFrame(CdObjectArtProvider.POINTS, frame(), false, false);
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
