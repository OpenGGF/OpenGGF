package paradise.model;

import java.util.Objects;

/** One terminal decision per committed observation, ordered independently of callback delivery. */
public enum GolfOutcome {
    NONE, DAMAGE, FINISH, SETTLED, LOST_BALL, WATCHDOG;

    /** Attacks and pickups are not failure candidates; the adapter reports actual damage/death. */
    public record Candidates(boolean damage, boolean death, boolean finish, boolean validSettlement,
                             boolean lostBall, boolean watchdog) { }

    public static GolfOutcome choose(Candidates candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.damage() || candidates.death()) return DAMAGE;
        if (candidates.finish()) return FINISH;
        if (candidates.validSettlement()) return SETTLED;
        if (candidates.lostBall()) return LOST_BALL;
        if (candidates.watchdog()) return WATCHDOG;
        return NONE;
    }

    public boolean isPenalty() { return this == DAMAGE || this == LOST_BALL || this == WATCHDOG; }
}
