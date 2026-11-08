package openggf.timeattack;

/** Immutable HUD snapshot consumed by the overlay each frame. */
public record TimeAttackHudState(boolean active, int elapsedDisplayFrames, int bestTimeFrames,
                                 int lastSplitDelta, boolean finished, boolean newBest,String saveNotice,
                                 CompetitionHudState competition) {
    public TimeAttackHudState {
        java.util.Objects.requireNonNull(competition);
    }

    public TimeAttackHudState(boolean active, int elapsedDisplayFrames, int bestTimeFrames,
            int lastSplitDelta, boolean finished, boolean newBest, String saveNotice) {
        this(active, elapsedDisplayFrames, bestTimeFrames, lastSplitDelta, finished, newBest,
                saveNotice, CompetitionHudState.inactive());
    }
    public TimeAttackHudState(boolean active,int elapsedDisplayFrames,int bestTimeFrames,
            int lastSplitDelta,boolean finished,boolean newBest) {
        this(active,elapsedDisplayFrames,bestTimeFrames,lastSplitDelta,finished,newBest,"");
    }
    /** No attempt in progress: nothing is drawn. */
    public static TimeAttackHudState inactive() {
        return new TimeAttackHudState(false, 0, -1, Integer.MIN_VALUE, false, false);
    }
}
