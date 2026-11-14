package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Boulder, object $1B ({@code ObjBoulder}, Level/_Objects/Boulder/Main.asm:7-51): a still solid
 * box, oWidth and oYRadius $10, priority 4, tile word from SetObjectTileID $B by secondary
 * subtype. Each frame it calls SolidObject while the retained on-screen bit is set, then draws
 * and runs CheckObjDespawn. The init routine falls through into the main routine.
 */
public final class CdBoulder extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private boolean resolvedTile;

    public CdBoulder(ObjectSpawn spawn) {
        super(spawn, "Boulder");
        // ObjBoulder_Init: priority 4, oWidth $10, oYRadius $10, frame 0 (Boulder/Main.asm:26-33).
        this.priority = 4;
        this.width = 0x10;
        this.yRadius = 0x10;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!resolvedTile) {
            // moveq #$B,d0; jsr SetObjectTileID (Boulder/Main.asm:34-35).
            CdObjectArtProvider provider = artProvider();
            if (provider != null) {
                tileWord = provider.objectTileId(CommonObjectArt.TILE_ID_BOULDER, subtype2());
                resolvedTile = true;
            }
        }
        // ObjBoulder_Main: tst.b oSprFlags; bpl .End; SolidObject (Boulder/Main.asm:41-48).
        if (renderFlagOnScreen()) solidObject(Entry.SOLID);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!resolvedTile || tileWord < 0) return;
        drawFrame(CommonObjectArt.boulderKey(tileWord), 0, hFlip(), vFlip());
    }
}
