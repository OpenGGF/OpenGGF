package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Scenery, object $32 ({@code ObjScenery}, Palmtree Panic/Objects/Scenery/Main.asm:7-57): a static
 * sprite whose mapping frame is its subtype, $10 wide and $18 high, drawn with a base tile chosen by
 * time zone and act ({@code ObjScenery_SetBaseTile}, scddisasm/src/Level/Palmtree Panic/Objects/Scenery/Main.asm:40-62; the tile list is
 * {@link PpzObjectArt#sceneryTiles}). Palette line 2 ({@code ori.w #$4000}). It ends in
 * {@code CheckObjDespawn}. Palmtree Panic act 1 Present links the routine but its object index leaves
 * ID $32 empty (Object Index (Act 1 Present).asm:50), so only the other periods and act 2 place it.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjScenery extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private int mapFrame;

    public ObjScenery(ObjectSpawn spawn) {
        super(spawn, "Scenery");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine != 0 || PpzStage.bossAct(services())) return;
        var provider = artProvider();
        if (provider == null) return;
        routine = 2;
        mapFrame = spawn.subtype();                     // move.b oSubtype(a0),oMapFrame(a0)
        width = 0x10;
        yRadius = 0x18;
        var tiles = PpzObjectArt.sceneryTiles(provider);
        tileWord = tiles.isEmpty() ? 0 : tiles.getFirst();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || tileWord == 0) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_SCENERY, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, hFlip(), vFlip());
    }

    public int mapFrame() { return mapFrame; }
}
