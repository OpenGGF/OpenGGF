package com.openggf.game.sonic2;

import com.openggf.level.objects.ObjectServices;

/** ROM debug-placement sprites share the resident monitor-art bank. */
public final class Sonic2NativeDebugGeometry {
    private Sonic2NativeDebugGeometry() { }

    public static boolean visible(ObjectServices services) {
        Sonic2RevisionProfile profile = services.gameService(Sonic2RevisionProfile.class);
        var camera = services.camera();
        var player = camera == null ? null : camera.getFocusedSprite();
        return profile != null && profile.displaysNativeDebugGeometry()
                && player != null && player.isDebugMode();
    }

    public static void draw(ObjectServices services, int fixedMapping, int frame,
                            int x, int y, boolean xFlip, boolean yFlip) {
        var manager = services.renderManager();
        var renderer = manager == null ? null : manager.getMonitorRenderer();
        if (renderer == null || !renderer.isReady()) return;
        try {
            var frames = S2SpriteDataLoader.loadStockMappingFrames(services.romReader(), fixedMapping);
            if (frame >= 0 && frame < frames.size()) {
                renderer.drawPieces(frames.get(frame).pieces(), x, y, xFlip, yFlip);
            }
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot load native S2 debug geometry", failure);
        }
    }
}
