package com.openggf.game.arena;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public record RaceClock(boolean running, int currentLap, int completedLaps, Failure failure) {
    @ModApi public enum Failure { NONE, GAME_OVER, TIME_OVER }
    public static final RaceClock ACT = new RaceClock(true, 0, 0, Failure.NONE);
}
