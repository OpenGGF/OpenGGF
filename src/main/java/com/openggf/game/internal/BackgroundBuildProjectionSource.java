package com.openggf.game.internal;

/** Captured at native scroll publication, never called under a directed camera. */
public interface BackgroundBuildProjectionSource {
    BackgroundBuildProjection captureBackgroundBuild();
}
