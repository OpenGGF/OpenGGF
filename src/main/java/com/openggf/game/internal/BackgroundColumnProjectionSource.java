package com.openggf.game.internal;

/** Optional internal game-owned capture of a native background's column remapping recipe. */
public interface BackgroundColumnProjectionSource {
    BackgroundColumnProjection captureBackgroundColumns();
}
