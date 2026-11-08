package com.openggf.game.arena;

import com.openggf.game.arena.ArenaPose;
import com.openggf.game.arena.RaceClock;
import java.util.Objects;

/** Immutable observation of one executed native arena step. */
public record ArenaStep(ArenaPose pose, RaceClock race) {
    public ArenaStep { Objects.requireNonNull(pose); Objects.requireNonNull(race); }
}
