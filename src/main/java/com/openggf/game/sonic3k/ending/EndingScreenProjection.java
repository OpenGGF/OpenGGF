package com.openggf.game.sonic3k.ending;

import com.openggf.camera.Camera;
import com.openggf.camera.NativeViewportFraming;

/** Derived projection for native render_flags bit2-clear sprites with the VDP $80 bias.
 * The renderer subtracts Camera_*_pos_copy plus shake, which can differ from
 * the physical camera throughout Ending_ScreenEvent. Never alter either word.
 */
public final class EndingScreenProjection {
    private EndingScreenProjection() { }
    public static int nativeHorizontalOrigin(Camera camera) {
        return camera.getXWithShake() + NativeViewportFraming.inset(camera.getWidth());
    }
    public static int xOffset(Camera camera) { return nativeHorizontalOrigin(camera) - 0x80; }
    public static int yOffset(Camera camera) { return camera.getYWithShake() - 0x80; }
    public static int worldX(Camera camera, int nativeX) { return xOffset(camera) + nativeX; }
    public static int worldY(Camera camera, int nativeY) { return yOffset(camera) + nativeY; }
}
