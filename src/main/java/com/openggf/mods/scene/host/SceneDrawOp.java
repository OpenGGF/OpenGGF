package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneImage;

/**
 * One recorded canvas call: a textured axis-aligned quad. Source coordinates are image
 * pixels; destination coordinates are logical screen pixels (y down). A null image means a
 * solid fill. {@code clip} is {@code {x, y, w, h}} or null. Engine-internal.
 */
record SceneDrawOp(
        SceneImage image,
        float u0, float v0, float u1, float v1,
        float x0, float y0, float x1, float y1,
        int tint,
        int flash,
        int[] clip) {
}
