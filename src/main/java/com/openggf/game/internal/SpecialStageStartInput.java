package com.openggf.game.internal;

/** A running special stage or its results consumes Start as its own action. */
public interface SpecialStageStartInput {
    boolean ownsStartInput();
}
