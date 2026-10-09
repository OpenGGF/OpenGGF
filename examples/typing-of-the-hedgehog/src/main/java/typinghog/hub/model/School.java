package typinghog.hub.model;

/**
 * Typing School grading. The Typing of the Dead grades its drills A to E and opens the Final
 * Exam at C or better on all of them; the thresholds here are this mod's own design choices
 * (no ROM or reference constant), measured in net words per minute (speed times accuracy),
 * words survived, rounds won or reaction time. Engine-free.
 */
public final class School {
    private School() { }

    /** The grade a finished drill earned. */
    public static String grade(DrillSession.Result r) {
        double net = r.netWpm();
        double acc = r.accuracy();
        return switch (r.drill()) {
            case HOME_ROW, TOP_ROW, BOTTOM_ROW, NUMBERS, CAPITALS, PUNCTUATION ->
                    acc >= 0.98 && net >= 25 ? "A" : acc >= 0.95 && net >= 18 ? "B"
                            : acc >= 0.90 && net >= 12 ? "C" : acc >= 0.80 ? "D" : "E";
            case RING_RUSH -> ladder(net, 60, 45, 32, 20);
            case SUDDEN_DEATH -> ladder(r.completed(), 40, 28, 18, 10);
            case REFLEX -> {
                double rate = r.total() == 0 ? 0 : r.successes() / (double) r.total();
                double ms = r.reactionMs();
                yield rate >= 0.95 && ms <= 500 ? "A" : rate >= 0.90 && ms <= 650 ? "B"
                        : rate >= 0.80 && ms <= 800 ? "C" : rate >= 0.60 ? "D" : "E";
            }
            case LONG_WORDS -> ladder(net, 50, 38, 28, 18);
            case PHRASES -> ladder(net, 55, 42, 30, 20);
            case BOSS_TRAINING -> ladder(r.successes(), 6, 5, 4, 2);
            case WEAK_KEYS -> acc >= 0.97 && net >= 35 ? "A" : acc >= 0.94 && net >= 28 ? "B"
                    : acc >= 0.90 && net >= 20 ? "C" : acc >= 0.80 ? "D" : "E";
            case FINAL_EXAM -> acc >= 0.97 && net >= 55 ? "A" : acc >= 0.95 && net >= 42 ? "B"
                    : acc >= 0.92 && net >= 30 ? "C" : acc >= 0.85 && net >= 20 ? "D" : "E";
        };
    }

    private static String ladder(double value, double a, double b, double c, double d) {
        return value >= a ? "A" : value >= b ? "B" : value >= c ? "C" : value >= d ? "D" : "E";
    }

    /** The Final Exam opens once every drill (not lesson) has a C or better. */
    public static boolean examUnlocked(Profile profile) {
        for (Drill drill : Drill.values()) {
            if (drill.kind == Drill.DRILL && !Grades.atLeast(profile.grade(drill.name()), "C")) return false;
        }
        return true;
    }

    /** Drills still short of a C, for Tails to point at. */
    public static int drillsBelowC(Profile profile) {
        int n = 0;
        for (Drill drill : Drill.values()) {
            if (drill.kind == Drill.DRILL && !Grades.atLeast(profile.grade(drill.name()), "C")) n++;
        }
        return n;
    }

    /** Adds a finished drill to the profile: its best grade, typing totals and per-key stats. */
    public static boolean record(Profile profile, DrillSession session) {
        DrillSession.Result r = session.result();
        if (r == null) return false;
        profile.keyHits().addAll(session.hits());
        profile.keyMisses().addAll(session.misses());
        // Reflex letters measure reaction, not speed, so they stay out of lifetime WPM.
        boolean reflex = r.drill() == Drill.REFLEX;
        profile.addTyping(reflex ? 0 : r.chars(), r.errors(), r.completed(), reflex ? 0 : r.activeMinutes());
        return profile.recordGrade(r.drill().name(), r.grade());
    }
}
