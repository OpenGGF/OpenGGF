package infinite;

import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Module-owned death state: the spare lives left when Sonic died and the challenge clock
 * captured at that moment, which CONTINUE restores in place.
 */
public final class CourseSession implements RewindSnapshottable<CourseSession.Snapshot> {
    /** One life for every 100 rings; the session starts with none. */
    public static final int RINGS_PER_LIFE = 100;
    private int livesLeft = -1;
    private ChallengeClock.Snapshot resumeClock;

    /** Spare lives after the last death, or -1 while the run is alive. */
    public int livesLeft() { return livesLeft; }

    public void reset() {
        livesLeft = -1;
        resumeClock = null;
    }

    public void died(int spareLives, ChallengeClock.Snapshot clock) {
        livesLeft = Math.max(0, spareLives);
        resumeClock = clock;
    }

    /** Spends one spare life and restores the clock, easing up from 1x; returns the spare lives remaining. */
    public int resume(ChallengeClock clock) {
        int remaining = livesLeft - 1;
        if (resumeClock != null) clock.resumeFrom(resumeClock);
        reset();
        return remaining;
    }

    @Override public String key() { return "infinite-sonic:session"; }
    @Override public Snapshot capture() { return new Snapshot(livesLeft, resumeClock); }
    @Override public void restore(Snapshot snapshot) {
        livesLeft = snapshot.livesLeft();
        resumeClock = snapshot.resumeClock();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    public record Snapshot(int livesLeft, ChallengeClock.Snapshot resumeClock) { }
}
