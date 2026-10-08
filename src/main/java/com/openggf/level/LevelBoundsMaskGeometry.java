package com.openggf.level;

/** Selects presentation bounds without changing native camera/player constraints. */
final class LevelBoundsMaskGeometry {
    private LevelBoundsMaskGeometry() { }

    record Bounds(int minX, int maxX) { }

    static Bounds select(LevelManager level, int currentMaxX) {
        // The ROM shared boss gate (loc_85D06/loc_85D28) first ratchets a
        // temporary boundary with the camera. loc_85D36 installs the final arena
        // only when reached. In widescreen those intermediate boundaries are
        // visible: mask the current playable interval, even if Sonic has already
        // entered the destination. Masking _unkFAB4/6 early concealed space he
        // could still return to and created competing current/destination fades.
        // Keep inverted domains intact for the renderer's no-op rule.
        return new Bounds(level.camera.getMinX(), currentMaxX);
    }
}
