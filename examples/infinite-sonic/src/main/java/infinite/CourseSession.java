package infinite;

import com.openggf.game.rewind.RewindSnapshottable;

/**
 * Module-owned run state that outlives a level reload: the next score extra life, the lives
 * left after a death and, once Continue is chosen, the score and clock to resume with.
 */
public final class CourseSession implements RewindSnapshottable<CourseSession.Snapshot> {
    /** An extra life every 50,000 points, as REV01 AddPoints does on Japanese consoles. */
    public static final int SCORE_PER_LIFE = 50_000;
    private int nextLifeScore = SCORE_PER_LIFE;
    private int livesLeft = -1;
    private boolean continuing;
    private int resumeScore;
    private ChallengeClock.Snapshot resumeClock;

    public int nextLifeScore() { return nextLifeScore; }
    /** Lives remaining after the last death, or -1 while the run is alive. */
    public int livesLeft() { return livesLeft; }
    public boolean continuing() { return continuing; }

    /** A fresh run: score and extra-life threshold start over. */
    public void reset() {
        nextLifeScore = SCORE_PER_LIFE;
        livesLeft = -1;
        continuing = false;
        resumeScore = 0;
        resumeClock = null;
    }

    /** Returns true when {@code score} crosses the next threshold, advancing it. */
    public boolean awardsLife(int score) {
        if (score < nextLifeScore) return false;
        nextLifeScore += SCORE_PER_LIFE;
        return true;
    }

    public void died(int remaining, int score, ChallengeClock.Snapshot clock) {
        livesLeft = Math.max(0, remaining);
        resumeScore = score;
        resumeClock = clock;
    }

    public void requestContinue() { continuing = livesLeft > 0 && resumeClock != null; }

    /** Applied by the level override: a continue keeps the speed it died at. */
    public void prepareClock(ChallengeClock clock) {
        if (continuing) clock.restore(resumeClock);
        else clock.reset();
    }

    /** Consumed by the new controller; returns the score to keep or -1 for a fresh run. */
    public int consumeContinueScore() {
        if (!continuing) return -1;
        continuing = false;
        livesLeft = -1;
        resumeClock = null;
        return resumeScore;
    }

    @Override public String key() { return "infinite-sonic:session"; }
    @Override public Snapshot capture() {
        return new Snapshot(nextLifeScore, livesLeft, continuing, resumeScore, resumeClock);
    }
    @Override public void restore(Snapshot snapshot) {
        nextLifeScore = snapshot.nextLifeScore();
        livesLeft = snapshot.livesLeft();
        continuing = snapshot.continuing();
        resumeScore = snapshot.resumeScore();
        resumeClock = snapshot.resumeClock();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    public record Snapshot(int nextLifeScore, int livesLeft, boolean continuing, int resumeScore,
            ChallengeClock.Snapshot resumeClock) { }
}
