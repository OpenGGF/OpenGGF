package com.openggf.level;

import com.openggf.graphics.SpriteMotionInternalAccess;
import com.openggf.sprites.managers.SpriteManager;
import java.util.Objects;

/** The live window's explicit opt-in bridge. Offline consumers keep using the native renderer. */
public final class LevelMotionAccess {
    private LevelMotionAccess() { }
    public static void observeService(LevelManager level, SpriteManager sprites, boolean active) {
        if (level != null) level.spritePresentationRenderer().observeMotionService(sprites, active);
    }
    /** The callback includes the scene flush. Every installed projection ends with this picture. */
    public static void present(LevelManager level, PictureSelection selection, Runnable draw) {
        Objects.requireNonNull(selection); Objects.requireNonNull(draw);
        if (level == null) { draw.run(); return; }
        var renderer = level.spritePresentationRenderer();
        if (renderer.motionScope) throw new IllegalStateException("Nested level motion picture");
        renderer.motionScope = true;
        if (selection instanceof PictureSelection.Latest) renderer.motionHistory.presentedLatest();
        renderer.motionOffer = renderer.motionHistory.offer(selection);
        try { draw.run(); }
        finally {
            renderer.motionOffer = null;
            renderer.motionScope = false;
            renderer.clearMotionDraw();
            if (level.graphicsManager != null) SpriteMotionInternalAccess.clearProjection(level.graphicsManager);
        }
    }
    public static void cut(LevelManager level, MotionCut cut) {
        Objects.requireNonNull(cut);
        if (level != null) {
            var renderer = level.spritePresentationRenderer();
            if (renderer != null && renderer.motionPackets.active) renderer.motionCuts.put(cut, ++renderer.motionCutOrdinal);
        }
    }
}
