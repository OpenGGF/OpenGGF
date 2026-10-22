package com.openggf.mods.scene;

import java.util.Objects;

/** A sampled character appendage, drawn behind the body at the same sprite origin and scale. */
@com.openggf.game.ModApi
public record SceneAccessoryPose(SceneSprite sprite, boolean flipX, boolean flipY) {
    public SceneAccessoryPose {
        Objects.requireNonNull(sprite, "sprite");
    }
}
