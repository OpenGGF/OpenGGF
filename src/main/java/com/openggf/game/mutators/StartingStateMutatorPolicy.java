package com.openggf.game.mutators;

/**
 * Admitted starting values, or -1 where no mutator contributes. Engine-owned projection of
 * the creator-facing starting-state policies; native counter owners receive the writes.
 */
public record StartingStateMutatorPolicy(int rings, int ringDelaySeconds, int timeSeconds, int score, int lives) {
    public static final StartingStateMutatorPolicy STOCK = new StartingStateMutatorPolicy(-1, 0, -1, -1, -1);

    public StartingStateMutatorPolicy {
        if (rings < -1 || rings > 999 || ringDelaySeconds < 0 || ringDelaySeconds > 600
                || timeSeconds < -1 || timeSeconds == 0 || timeSeconds > 570
                || score < -1 || score > 999_990 || lives < -1 || lives == 0 || lives > 99)
            throw new IllegalArgumentException("Starting state exceeds native host bounds");
    }

    public boolean grantsRings() { return rings > 0; }
    public boolean seedsTime() { return timeSeconds > 0; }
}
