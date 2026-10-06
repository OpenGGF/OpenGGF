package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** A render-only native pose. It never changes a playable object's animation or clocks. */
@ModApi
public record PlayerPresentationPose(Kind kind, long tick, int facing) {
    @ModApi public enum Kind { NATIVE, DUCK, SPINDASH, IDLE }

    public PlayerPresentationPose {
        java.util.Objects.requireNonNull(kind, "kind");
        if (tick < 0 || (facing != -1 && facing != 1)) {
            throw new IllegalArgumentException("Invalid presentation pose");
        }
    }

    public static PlayerPresentationPose nativePose() {
        return new PlayerPresentationPose(Kind.NATIVE, 0, 1);
    }
}
