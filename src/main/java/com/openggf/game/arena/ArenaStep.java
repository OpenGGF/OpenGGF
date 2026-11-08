package com.openggf.game.arena;

import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RaceClock;
import java.util.Objects;

/** Immutable observation of one executed native arena step. */
public record ArenaStep(PlayerPose pose, RaceClock race) {
    public ArenaStep { Objects.requireNonNull(pose); Objects.requireNonNull(race); }
}
