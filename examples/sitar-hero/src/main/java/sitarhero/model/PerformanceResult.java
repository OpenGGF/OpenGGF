package sitarhero.model;

import java.util.Objects;

/** Immutable finished-attempt data; scene modes determine eligibility for profile recording. */
public record PerformanceResult(String songId, String role, String difficulty, long score,
                                int hits, int notes, int bestStreak, boolean failed, boolean fullCombo) {
    public PerformanceResult {
        validateKey(songId, role, difficulty);
        if (score < 0 || notes < 0 || notes > 1_000_000 || hits < 0 || hits > notes
                || bestStreak < 0 || bestStreak > hits
                || fullCombo && (failed || notes == 0 || hits != notes || bestStreak != notes))
            throw new IllegalArgumentException("Invalid performance totals");
    }

    static void validateKey(String songId, String role, String difficulty) {
        if (songId == null || !songId.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("Invalid song, instrument or difficulty");
        validateSelection(role, difficulty);
    }

    static void validateSelection(String role, String difficulty) {
        if (role == null || difficulty == null) throw new IllegalArgumentException("Missing instrument or difficulty");
        boolean validRole = switch (role) { case "SITAR", "BONGOS", "SYNTH", "HARP" -> true; default -> false; };
        boolean validDifficulty = switch (difficulty) { case "EASY", "MEDIUM", "HARD", "EXPERT" -> true; default -> false; };
        if (!validRole || !validDifficulty) throw new IllegalArgumentException("Invalid instrument or difficulty");
    }

    /** Snapshot only a finished session. A combo means all note heads formed one uninterrupted streak. */
    public static PerformanceResult from(String songId, String role, String difficulty, RhythmSession session) {
        Objects.requireNonNull(session, "session");
        if (!session.finished()) throw new IllegalArgumentException("Performance is still running");
        int notes = session.chart().notes().size();
        boolean combo = !session.failed() && notes > 0 && session.hits() == notes && session.bestStreak() == notes;
        return new PerformanceResult(songId, role, difficulty, session.score(), session.hits(), notes,
                session.bestStreak(), session.failed(), combo);
    }

    /** Fraction of note heads hit; empty score-only legacy records have zero accuracy. */
    public double accuracy() { return notes == 0 ? 0 : (double) hits / notes; }

    /** A clear earns one star, then 50%, 70%, 85% and 95% earn two through five stars. */
    public int stars() {
        if (!cleared()) return 0;
        // Integer comparisons keep the exact percentage boundaries attainable.
        long percent = (long) hits * 100;
        if (percent >= (long) notes * 95) return 5;
        if (percent >= (long) notes * 85) return 4;
        if (percent >= (long) notes * 70) return 3;
        if (percent >= (long) notes * 50) return 2;
        return 1;
    }

    /** S/A/B/C/D follow the five-to-one-star bands; failure or no playable notes is F. */
    public String grade() {
        return switch (stars()) {
            case 5 -> "S"; case 4 -> "A"; case 3 -> "B"; case 2 -> "C"; case 1 -> "D"; default -> "F";
        };
    }

    public boolean cleared() { return !failed && notes > 0; }
}
