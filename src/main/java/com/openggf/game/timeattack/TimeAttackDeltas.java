package com.openggf.game.timeattack;

/**
 * Split-ordinal delta between the live attempt and a ghost's recorded splits.
 * Splits are spawn-anchored frame numbers but the timer starts at first input
 * (spec §6.1), so deltas compare TIMED values — subtracting each side's own
 * firstInputFrame — never raw spawn-frame numbers.
 */
public final class TimeAttackDeltas {
    public static final int NO_DELTA = Integer.MIN_VALUE;

    private TimeAttackDeltas() {
    }

    /** Positive = attempt is behind the ghost at that split (in timed frames). */
    public static int deltaAtSplit(int[] attemptSplits, int attemptFirstInputFrame,
                                   int[] ghostSplits, int ghostFirstInputFrame, int splitOrdinal) {
        if (splitOrdinal < 0 || splitOrdinal >= attemptSplits.length || splitOrdinal >= ghostSplits.length) {
            return NO_DELTA;
        }
        return (attemptSplits[splitOrdinal] - attemptFirstInputFrame)
                - (ghostSplits[splitOrdinal] - ghostFirstInputFrame);
    }
}
