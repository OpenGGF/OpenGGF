package com.openggf.mods.mutators;

import com.openggf.game.ModApi;

/**
 * Immutable starting values written through each game's native counter owners. Each
 * record is one bounded request; the host decides when the boundary it names occurs.
 */
@ModApi
public sealed interface StartingStatePolicy extends MutatorPolicy permits StartingStatePolicy.Rings,
        StartingStatePolicy.Time, StartingStatePolicy.Score, StartingStatePolicy.Lives {
    /** The HUD's three-digit ring counter and the native 999-ring ceiling. */
    int MAX_RINGS = 999;
    /** 9:30: the native 10:00 time over follows 30 seconds later. */
    int MAX_TIME_SECONDS = 570;
    /** The six-digit HUD score; native counters keep units of ten. */
    int MAX_SCORE = 999_990;
    /** The two-digit native lives counter (and Sonic 3 &amp; Knuckles' own 99-life clamp). */
    int MAX_LIVES = 99;

    /**
     * Rings granted at every act start and life restart, {@code delaySeconds} of active play
     * after it. The grant marks the 100/200 extra-life thresholds it reaches as already
     * awarded, so a seeded count never pays out an extra life.
     */
    @ModApi
    record Rings(int rings, int delaySeconds) implements StartingStatePolicy {
        public Rings {
            if (rings < 0 || rings > MAX_RINGS || delaySeconds < 0 || delaySeconds > 600)
                throw new IllegalArgumentException("Starting rings require 0..999 rings and a 0..600 s delay");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.STARTING_RINGS; }
    }

    /**
     * Seconds already on the level timer at a fresh act start or a life restarted from the
     * act start. A checkpoint restart keeps the native saved checkpoint time.
     */
    @ModApi
    record Time(int seconds) implements StartingStatePolicy {
        public Time {
            if (seconds < 1 || seconds > MAX_TIME_SECONDS)
                throw new IllegalArgumentException("Starting time must be 1..570 seconds");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.STARTING_TIME; }
    }

    /** Score at the start of a new game session, in native points (multiples of ten). */
    @ModApi
    record Score(int score) implements StartingStatePolicy {
        public Score {
            if (score < 0 || score > MAX_SCORE || score % 10 != 0)
                throw new IllegalArgumentException("Starting score must be 0..999990 in units of ten");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.STARTING_SCORE; }
    }

    /** Lives at the start of a new game session, including the life being played. */
    @ModApi
    record Lives(int lives) implements StartingStatePolicy {
        public Lives {
            if (lives < 1 || lives > MAX_LIVES)
                throw new IllegalArgumentException("Starting lives must be 1..99");
        }
        @Override public MutatorCapability capability() { return MutatorCapability.STARTING_LIVES; }
    }
}
