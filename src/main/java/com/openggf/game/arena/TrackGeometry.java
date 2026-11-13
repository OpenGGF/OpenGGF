package com.openggf.game.arena;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public record TrackGeometry(int wrapWidthPx, int wrapHeightPx, int lapLineX, int lapCount) {
    public static final TrackGeometry ACT = new TrackGeometry(0, 0, 0, 0);
}
