package com.openggf.graphics;

/** Private cinematic descriptor textures; its owner closes it with the scene renderer. */
public final class CinematicBackgroundResidency implements AutoCloseable {
    final TilemapTexture texture = new TilemapTexture();
    final TilemapTexture foregroundTexture = new TilemapTexture();
    final TilemapTexture foregroundWindowTexture = new TilemapTexture();
    @Override public void close() {
        texture.cleanup();
        foregroundTexture.cleanup();
        foregroundWindowTexture.cleanup();
    }
}
