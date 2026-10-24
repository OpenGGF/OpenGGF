package com.openggf.level.scroll;

/** Internal opt-in for native handlers that can freeze their camera-independent picture phase. */
public interface ScrollProjectionSource {
    ScrollProjectionPhase captureProjection(ScrollProjectionContext context);
}
