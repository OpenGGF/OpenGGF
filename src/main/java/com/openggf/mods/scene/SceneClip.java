package com.openggf.mods.scene;

/** Immutable canvas clip in that canvas's logical coordinates. */
@com.openggf.game.ModApi
public record SceneClip(int x, int y, int width, int height) {
    public SceneClip {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative clip size");
        Math.addExact(x, width); Math.addExact(y, height);
    }
}
