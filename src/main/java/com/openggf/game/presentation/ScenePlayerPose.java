package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** Local render-only shot pose anchored at a world-space centre supplied by scene values. */
@ModApi
public record ScenePlayerPose(String character, int centreX, int centreY, PlayerPresentationPose pose) {
    public ScenePlayerPose {
        java.util.Objects.requireNonNull(character, "character");
        java.util.Objects.requireNonNull(pose, "pose");
        if (!character.matches("[a-z][a-z0-9_-]{0,31}") || centreX < -65_536 || centreX > 65_536
                || centreY < -65_536 || centreY > 65_536) {
            throw new IllegalArgumentException("Invalid local player pose");
        }
    }
}
