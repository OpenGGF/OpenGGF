package com.openggf.game;

import com.openggf.control.LogicalInputSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Detached, immutable current-world observation. Coordinates are native player centres; velocities are signed 8.8. */
@ModApi
public record GameplayObservation(long worldGeneration, long visitGeneration, long activePresentations,
        long nativeTicks, Phase phase, int zone, int act, int viewportWidth, int viewportHeight,
        Optional<Player> leader, List<Player> team, LogicalInputSnapshot input, LevelLoadCause loadCause) {
    public GameplayObservation {
        Objects.requireNonNull(phase); Objects.requireNonNull(leader); Objects.requireNonNull(input);
        Objects.requireNonNull(loadCause); team = List.copyOf(team);
    }
    @ModApi public enum Phase { ACTIVE, HELD, PAUSED, SETUP, TRANSITION, STAGE, REWIND, CLOSED }
    @ModApi public record Player(String character, int x, int y, int xSpeed, int ySpeed, int groundSpeed,
            int rings, boolean airborne, boolean dead, boolean hurt, boolean attached,
            boolean controlLocked, boolean inWater, String shield) {
        public Player { Objects.requireNonNull(character); Objects.requireNonNull(shield); }
    }
}
