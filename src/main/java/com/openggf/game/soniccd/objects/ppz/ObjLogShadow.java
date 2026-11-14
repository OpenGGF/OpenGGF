package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Log shadow, object $33 ({@code ObjLogShadow}, Palmtree Panic/Objects/Log Shadow/Main.asm:7-64): a static
 * sprite like scenery with priority 6, $10 wide and $10 high, mapping frame = subtype, base tile by
 * time zone and act (Main.asm:41-64, {@link PpzObjectArt#logShadowTiles}; a good future's row
 * is the fourth), palette line 2. It ends in {@code CheckObjDespawn}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjLogShadow extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private int mapFrame;

    public ObjLogShadow(ObjectSpawn spawn) {
        super(spawn, "Log shadow");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine != 0 || PpzStage.bossAct(services())) return;
        var provider = artProvider();
        if (provider == null) return;
        routine = 2;
        priority = 6;
        mapFrame = spawn.subtype();                     // move.b oSubtype(a0),oMapFrame(a0)
        width = 0x10;
        yRadius = 0x10;
        var tiles = PpzObjectArt.logShadowTiles(provider);
        tileWord = tiles.isEmpty() ? 0 : tiles.getFirst();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || tileWord == 0) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_LOG_SHADOW, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, hFlip(), vFlip());
    }

    public int mapFrame() { return mapFrame; }
}
