package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** Locally composed image. Its pixels never form part of the scene network protocol. */
@ModApi
public record SceneImage(int width, int height, int[] argb) {
    public SceneImage {
        if (width < 1 || width > 800 || height < 1 || height > 512 || argb.length != width * height) {
            throw new IllegalArgumentException("Invalid scene image");
        }
        argb = argb.clone();
    }
    @Override public int[] argb() { return argb.clone(); }
}
