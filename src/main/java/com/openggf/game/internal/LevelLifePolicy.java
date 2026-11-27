package com.openggf.game.internal;

/** Level rules for games that can have a living player at zero lives. */
public interface LevelLifePolicy {
    int livesAfterDeath(int lives);

    boolean canTogglePause();
}
