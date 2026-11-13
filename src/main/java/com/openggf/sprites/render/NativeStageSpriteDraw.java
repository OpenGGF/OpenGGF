package com.openggf.sprites.render;

import com.openggf.level.render.SpritePieceRenderer;

/** Internal native-scene bridge; does not extend the creator API. */
public final class NativeStageSpriteDraw {
    private NativeStageSpriteDraw() { }

    public static void draw(PlayerSpriteRenderer renderer, int frame, int x, int y,
                            boolean hFlip, boolean vFlip, SpritePieceRenderer.TileConsumer consumer) {
        renderer.drawNativeTiles(frame, x, y, hFlip, vFlip, consumer);
    }

    public static void draw(PlayerSpriteRenderer renderer, int frame, int x, int y,
                            SpritePieceRenderer.TileConsumer consumer) {
        renderer.drawNativeTiles(frame, x, y, consumer);
    }
}
