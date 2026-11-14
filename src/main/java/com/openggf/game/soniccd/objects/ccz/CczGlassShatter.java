package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Glass shatter, object $29 ({@code ObjGlassShatter}, Level/Collision Chaos/Objects/Glass Shatter/Main.asm:7-84, legacy
 * ABI). Init awards 100 points ({@code moveq #10,d0; bsr.w AddPoints}, units of ten), spawns a 100-point sprite
 * ({@code SpawnPoints}, Level/_Objects/Explosion/Main.asm:25-39) and plays {@code Ani_GlassBreak} (tile {@code $638B}, {@code $642A} in
 * act 3; priority 3; $10 square) until its {@code $FC}, then deletes itself. It draws every frame and never despawns.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczGlassShatter extends CczObject implements ObjectVisualFacing.NonDirectional {
    public CczGlassShatter(ObjectSpawn spawn) {
        super(spawn, "Glass shatter");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {                                                    // ObjGlassShatter_Init (:21-48), falls into Animate
                routine = 2;
                priority = 3;
                width = 0x10;
                yRadius = 0x10;
                tileWord = act() == 2 ? CczObjectArt.GLASS_TILE_ACT3 : CczObjectArt.GLASS_TILE;
                var game = services().gameState();
                if (game != null) game.addScore(10 * 10);
                var objects = services().objectManager();
                if (objects != null) {
                    objects.addDynamicObject(new CdPoints(new ObjectSpawn(x, y, 0x1C, 0x80, 0, false, 0)));
                }
                animateGlass();
            }
            case 2 -> animateGlass();
            default -> setDestroyed(true);                                 // ObjGlassShatter_Delete
        }
    }

    private void animateGlass() {
        if (animator == null) animator = CczObjectArt.animator(provider(), "glassBreakAnimations", 0);
        animate();
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.GLASS_BREAK, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }
}
