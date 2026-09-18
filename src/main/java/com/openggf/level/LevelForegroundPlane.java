package com.openggf.level;

/**
 * Gameplay-step bridge for the AIZ2 forest-loop foreground plane ring.
 *
 * <p>ROM {@code ScreenEvents} ends in {@code DrawTilesAsYouMove}
 * (sonic3k.asm:104978, 103171), which fills Plane A from the live
 * {@code Camera_X_pos_copy} against {@code Camera_X_pos_rounded} inside the CPU
 * loop. {@code AIZ2_DoShipLoop} (sonic3k.asm:105205) retargets that baseline in
 * the same routine as its {@code $200} camera subtraction, so the engine's ring
 * must take the live camera and the live {@code Level_repeat_offset} together,
 * from the gameplay step, before any VBlank publication. Only H-scroll/VSRAM/SAT
 * are published at VInt and retained on lag frames; the wrap equals the 64-tile
 * plane width, so a retained scroll register still aliases onto the same ring
 * cells. Pairing the published camera with the live offset instead trips the
 * ring's large-jump reseed whenever the wrap lands on a lag frame (2026-09-16
 * AIZ2 forest regression). This is deliberately not a {@code LevelManager}
 * member: that surface is pinned by the Mod API signature guard.
 */
public final class LevelForegroundPlane {
    private LevelForegroundPlane() { }

    /** ROM {@code DrawTilesAsYouMove} slot: push this step's camera and repeat offset to the ring. */
    public static void drawAsYouMove(LevelManager level) {
        if (level == null || level.tilemapManager == null || level.zoneFeatureProvider == null
                || level.camera == null || !level.zoneFeatureProvider.foregroundWrapsHorizontally()) {
            return;
        }
        level.tilemapManager.setForegroundRingCamera(level.camera.getXWithShake(), level.cachedScreenWidth,
                level.zoneFeatureProvider.foregroundWorldWrapOffset());
    }
}
