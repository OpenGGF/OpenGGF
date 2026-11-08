package com.openggf.game.arena;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public record RaceEventResult(RaceEvent event, Outcome outcome) {
    @ModApi public enum Outcome { NONE, APPLIED, FALLBACK }
    public static final RaceEventResult NONE = new RaceEventResult(null, Outcome.NONE);
}
