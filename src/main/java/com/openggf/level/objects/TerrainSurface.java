package com.openggf.level.objects;

/** Immutable live-floor query result; distance is signed relative to the query's reference Y. */
@com.openggf.game.ModApi
public record TerrainSurface(boolean foundSurface, int surfaceY, int distance, int angle) {
    @com.openggf.game.ModApi
    public enum Layer { FOREGROUND, BACKGROUND }
    @com.openggf.game.ModApi
    public enum Path { PRIMARY, SECONDARY }
}
