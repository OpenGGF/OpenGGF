package com.openggf.game.arena;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public record SwapTarget(int centreX, int centreY, int xVel, int yVel, boolean facingLeft, boolean secondaryPath, int priorityBucket, boolean highPriority, int shoeKind, int shoeTimer, int progress) { }
