package openggf.timeattack;

/** Immutable HUD snapshot consumed by the overlay each frame. */
public record TimeAttackHudState(boolean active, int elapsedDisplayFrames, int bestTimeFrames,
                                 int lastSplitDelta, boolean finished, boolean newBest,String saveNotice) {
    public TimeAttackHudState(boolean active,int elapsedDisplayFrames,int bestTimeFrames,
            int lastSplitDelta,boolean finished,boolean newBest) {
        this(active,elapsedDisplayFrames,bestTimeFrames,lastSplitDelta,finished,newBest,"");
    }
    /** No attempt in progress: nothing is drawn. */
    public static TimeAttackHudState inactive() {
        return new TimeAttackHudState(false, 0, -1, Integer.MIN_VALUE, false, false);
    }
}
