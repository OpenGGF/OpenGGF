package com.openggf.level.scroll;

import java.util.Objects;

/** Native inputs paired with the scroll publication whose immutable phase is being captured. */
public record ScrollProjectionContext(
        int cameraX,
        int cameraY,
        int frameCounter,
        int act,
        int viewportWidth,
        int viewportHeight,
        ScrollProjection nativeOutput) {
    public ScrollProjectionContext {
        Objects.requireNonNull(nativeOutput, "native scroll output");
    }
}
