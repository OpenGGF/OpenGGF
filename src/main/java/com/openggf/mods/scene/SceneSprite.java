package com.openggf.mods.scene;

/**
 * An image with an origin: the point that sits at the sprite's position, as with a Mega
 * Drive object's {@code x_pos/y_pos}. ROM sprite frames are returned this way so frames of
 * different sizes line up. {@code originX/originY} are measured from the image's top-left.
 */
@com.openggf.game.ModApi
public record SceneSprite(SceneImage image, int originX, int originY) {
    /** A sprite whose origin is its top-left corner. */
    public static SceneSprite of(SceneImage image) {
        return new SceneSprite(image, 0, 0);
    }

    public int width() {
        return image.width();
    }

    public int height() {
        return image.height();
    }
}
