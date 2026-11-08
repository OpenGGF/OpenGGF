package openggf.timeattack;

/** Hub-clock start gate. Native neon: blank, 3, 2, 1, then GO, each 60 steps. */
public final class MassStartRelease {
    public static final int ARENA_LEAD_FRAMES = 240;
    public static final long START_TOLERANCE_MILLIS = 2000;
    private MassStartRelease() { }
    public static long releaseAt(long scheduledGoMillis, boolean arena) {
        return scheduledGoMillis - (arena ? ARENA_LEAD_FRAMES * 1000L / 60 : 0);
    }
    public static boolean released(long localMillis, long hubOffsetMillis, long scheduledGoMillis, boolean arena) {
        return localMillis + hubOffsetMillis >= releaseAt(scheduledGoMillis, arena);
    }
    public static boolean withinStartTolerance(long hubArrivalMillis, long scheduledGoMillis) {
        return hubArrivalMillis <= scheduledGoMillis + START_TOLERANCE_MILLIS;
    }
}
