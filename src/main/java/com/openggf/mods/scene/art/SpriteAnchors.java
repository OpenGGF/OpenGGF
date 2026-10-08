package com.openggf.mods.scene.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;

/** Origin-aware placement shared by scenes; composite-object choreography stays with the mod. */
@com.openggf.game.ModApi
public final class SpriteAnchors {
    private SpriteAnchors() { }
    public static void feet(SceneCanvas canvas, SceneSprite sprite, float x, float groundY, SceneDraw style) {
        if (sprite == null) return;
        canvas.draw(sprite, x, groundY - (style.flipY() ? sprite.originY() : sprite.height() - sprite.originY()) * style.scaleY(), style);
    }
    public static void centre(SceneCanvas canvas, SceneSprite sprite, float x, float y, SceneDraw style) {
        if (sprite == null) return;
        float dx = (sprite.originX() - sprite.width() / 2f) * style.scaleX();
        float dy = (sprite.originY() - sprite.height() / 2f) * style.scaleY();
        canvas.draw(sprite, x + (style.flipX() ? -dx : dx), y + (style.flipY() ? -dy : dy), style);
    }
}
