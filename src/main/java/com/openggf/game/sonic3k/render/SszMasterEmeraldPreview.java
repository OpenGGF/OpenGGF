package com.openggf.game.sonic3k.render;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.bosses.SszMasterEmerald;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;

/** ROM-backed scenery visible in widescreen before the native emerald object exists. */
public final class SszMasterEmeraldPreview {
    private SszMasterEmeraldPreview() { }

    public static boolean shouldDraw(int width, SszZoneRuntimeState state, boolean nativeObjectDrawing) {
        return width > 320 && state != null && state.actIndex() == 1
                && !state.act2EndingActive() && !nativeObjectDrawing;
    }

    public static void draw(int width, SszZoneRuntimeState state, LevelManager level, GraphicsManager graphics) {
        var manager = level.getObjectManager();
        if (manager == null || !shouldDraw(width, state,
                manager.activeObjectsOfType(SszMasterEmerald.class).stream().anyMatch(SszMasterEmerald::isDrawing))) return;
        var art = level.getObjectRenderManager();
        var renderer = art == null ? null : art.getRenderer(Sonic3kObjectArtKeys.SSZ_MASTER_EMERALD);
        if (renderer == null || !renderer.isReady()) return;
        // loc_7BA38 allocates the native object only when Mecha starts his dash;
        // loc_7C818 places it at final Camera_max_X+$100, Camera_Y+$A8. The ROM
        // cannot see that location beforehand. Widescreen can, so draw frame0 at
        // the resulting world position without allocating an SST slot, ticking
        // its palette script, submitting an early PLC or changing collision.
        // The ordinary object takes over as soon as it actually draws. No preview
        // state needs rewind capture: all gates come from the restored live world.
        int previousMask = graphics.getCurrentSpriteTileOcclusionPaletteMask();
        graphics.flushPatternBatch();
        graphics.setCurrentSpriteTileOcclusionPaletteMask(0); // ObjDat3_7D450 art_tile bit15
        graphics.beginPatternBatch();
        renderer.drawFrameIndex(0, 0x340, 0x4A8, false, false, 0);
        graphics.flushPatternBatch();
        graphics.setCurrentSpriteTileOcclusionPaletteMask(previousMask);
        graphics.beginPatternBatch();
    }
}
