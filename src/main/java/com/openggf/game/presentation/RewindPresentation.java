package com.openggf.game.presentation;

import com.openggf.game.ModApi;

/** Read-only request for the host's configured rewind effect; supplies no gameplay state. */
@ModApi
public record RewindPresentation(float intensity, float speed) {
    public static final RewindPresentation NONE = new RewindPresentation(0, 0);

    public RewindPresentation {
        if (!Float.isFinite(intensity) || intensity < 0 || intensity > 1
                || !Float.isFinite(speed) || speed < 0 || speed > 4
                || intensity > 0 && speed == 0) {
            throw new IllegalArgumentException("Invalid rewind presentation");
        }
    }
}
