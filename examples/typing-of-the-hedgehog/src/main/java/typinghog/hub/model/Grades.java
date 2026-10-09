package typinghog.hub.model;

/**
 * Letter grades. Act ranks run S, A, B, C, D, E (the course's tally); Typing School drills run
 * A to E as The Typing of the Dead's drills do. Engine-free.
 */
public final class Grades {
    private Grades() { }

    /** True for S, A, B, C, D or E. */
    public static boolean valid(String grade) {
        return grade != null && grade.length() == 1 && "SABCDE".contains(grade);
    }

    /** Higher is better: S 6, A 5 ... E 1; 0 for anything else (no grade yet). */
    public static int rank(String grade) {
        return valid(grade) ? 6 - "SABCDE".indexOf(grade) : 0;
    }

    /** True when {@code grade} is at least {@code floor}. */
    public static boolean atLeast(String grade, String floor) {
        return rank(grade) >= rank(floor) && rank(grade) > 0;
    }

    /** The highlight colour a grade is drawn in (ARGB). */
    public static int colour(String grade) {
        return switch (grade == null ? "" : grade) {
            case "S" -> 0xFFFFE860;
            case "A" -> 0xFFFFB020;
            case "B" -> 0xFF60E070;
            case "C" -> 0xFF60B8FF;
            case "D" -> 0xFFC090FF;
            case "E" -> 0xFFFF6060;
            default -> 0xFF8890A0;
        };
    }
}
