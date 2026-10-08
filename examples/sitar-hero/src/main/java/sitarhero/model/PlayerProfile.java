package sitarhero.model;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Bounded, versioned personal records. The scene submits only passed normal singleplayer
 * attempts: practice, demo and multiplayer eligibility cannot be inferred from result data.
 * Each key retains two actual attempts: the score champion and the accuracy/star champion.
 * A lower score can improve stars without fabricating a result with unrelated totals.
 */
public final class PlayerProfile {
    private static final int MAX_RECORDS = 2_048;
    private static final int MAX_LINES = 4_096;
    private static final int MAX_TEXT = 524_288;
    private static final int MAX_LINE = 512;
    private static final String HEADER = "sitar-profile=1";
    private final Map<Key, Record> records = new TreeMap<>(Comparator.comparing(Key::songId)
            .thenComparing(Key::role).thenComparing(Key::difficulty));
    private long attempts;
    private long totalScore;

    private record Key(String songId, String role, String difficulty) {
        private Key { PerformanceResult.validateKey(songId, role, difficulty); }
        static Key of(PerformanceResult result) { return new Key(result.songId(), result.role(), result.difficulty()); }
    }
    private record Record(PerformanceResult score, PerformanceResult stars) { }

    public PlayerProfile() { }

    /**
     * Count an eligible clear even if it earns no personal best. Return true if either
     * champion improves; rejected failed/empty attempts or new keys beyond capacity do
     * not affect totals. Equal scores prefer the stronger accuracy, then combo/streak.
     * Totals sum submitted eligible attempts, not best records, and saturate at Long.MAX_VALUE.
     */
    public boolean record(PerformanceResult result) {
        java.util.Objects.requireNonNull(result, "result");
        if (!result.cleared()) return false;
        Key key = Key.of(result);
        Record old = records.get(key);
        if (old == null && records.size() >= MAX_RECORDS) return false;
        attempts = add(attempts, 1);
        totalScore = add(totalScore, result.score());
        PerformanceResult score = old == null || compareScore(result, old.score()) > 0 ? result : old.score();
        PerformanceResult stars = old == null || compareStars(result, old.stars()) > 0 ? result : old.stars();
        records.put(key, new Record(score, stars));
        return old == null || !score.equals(old.score()) || !stars.equals(old.stars());
    }

    /** The score champion, including a migrated score-only legacy record if it is still higher. */
    public Optional<PerformanceResult> best(String songId, String role, String difficulty) {
        Record record = records.get(new Key(songId, role, difficulty));
        return record == null ? Optional.empty() : Optional.of(record.score());
    }

    /** Highest stars, then accuracy, combo, score and streak. Use this for clear/grade displays. */
    public Optional<PerformanceResult> bestStars(String songId, String role, String difficulty) {
        Record record = records.get(new Key(songId, role, difficulty));
        return record == null ? Optional.empty() : Optional.of(record.stars());
    }

    /** Distinct song/instrument/difficulty keys with an eligible clear; legacy scores alone do not clear. */
    public int clears() { return (int) records.values().stream().filter(record -> record.stars().cleared()).count(); }
    public long attempts() { return attempts; }
    public long totalScore() { return totalScore; }

    /**
     * Preserve a parsed legacy per-song score with no invented notes, stars, attempts or
     * clear. The scene chooses the migration difficulty and parses the old text itself.
     * Idempotent; return true only if the score champion improves or a key is created.
     */
    public boolean importLegacyScore(String songId, String role, String difficulty, long score) {
        var legacy = new PerformanceResult(songId, role, difficulty, score, 0, 0, 0, false, false);
        Key key = Key.of(legacy);
        Record old = records.get(key);
        if (old == null) {
            if (records.size() >= MAX_RECORDS) return false;
            records.put(key, new Record(legacy, legacy));
            return true;
        }
        if (compareScore(legacy, old.score()) <= 0) return false;
        records.put(key, new Record(legacy, old.stars()));
        return true;
    }

    /**
     * Version 1 schema: header, attempts=N, total-score=N, then one record line per key:
     * record=song|ROLE|DIFFICULTY|score,hits,notes,streak,failed,combo|same fields for star champion.
     * IDs are bounded ASCII tokens; booleans are literal lowercase true/false. Lexical
     * key order makes encoding deterministic. At most 2,048 records are stored.
     */
    public String encode() {
        var text = new StringBuilder(HEADER).append('\n').append("attempts=").append(attempts)
                .append('\n').append("total-score=").append(totalScore).append('\n');
        records.forEach((key, record) -> text.append("record=").append(key.songId()).append('|')
                .append(key.role()).append('|').append(key.difficulty()).append('|')
                .append(fields(record.score())).append('|').append(fields(record.stars())).append('\n'));
        return text.toString();
    }

    /**
     * Atomically replace this profile from a valid V1 save. Null, corrupt, unsupported or
     * oversized input leaves current progress untouched. Unknown key=value extras are
     * ignored within 4,096 total lines, 512 characters/line and 524,288 characters of text. Known
     * fields are strict; duplicate keys/records and invalid champion pairs reject the save.
     */
    public void read(String text) {
        if (text == null || text.length() > MAX_TEXT) return;
        var lines = text.lines().limit(MAX_LINES + 1L).toList();
        if (lines.isEmpty() || lines.size() > MAX_LINES || !lines.getFirst().equals(HEADER)) return;
        var parsed = new PlayerProfile();
        boolean seenAttempts = false, seenTotal = false;
        try {
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isEmpty() || line.length() > MAX_LINE) throw new IllegalArgumentException("Invalid save line");
                int separator = line.indexOf('=');
                if (separator <= 0) throw new IllegalArgumentException("Missing save field");
                String key = line.substring(0, separator), value = line.substring(separator + 1);
                switch (key) {
                    case "sitar-profile" -> throw new IllegalArgumentException("Duplicate version");
                    case "attempts" -> {
                        if (seenAttempts) throw new IllegalArgumentException("Duplicate attempts");
                        parsed.attempts = number(value); seenAttempts = true;
                    }
                    case "total-score" -> {
                        if (seenTotal) throw new IllegalArgumentException("Duplicate total score");
                        parsed.totalScore = number(value); seenTotal = true;
                    }
                    case "record" -> parsed.readRecord(value);
                    default -> { /* bounded extras allow additive metadata without discarding progress */ }
                }
            }
            if (!seenAttempts || !seenTotal || parsed.attempts < parsed.clears()) return;
        } catch (IllegalArgumentException ignored) { return; }
        records.clear(); records.putAll(parsed.records);
        attempts = parsed.attempts; totalScore = parsed.totalScore;
    }

    private void readRecord(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5 || records.size() >= MAX_RECORDS) throw new IllegalArgumentException("Invalid record row");
        Key key = new Key(parts[0], parts[1], parts[2]);
        PerformanceResult score = parse(key, parts[3]), stars = parse(key, parts[4]);
        if (records.containsKey(key) || compareScore(score, stars) < 0 || compareStars(stars, score) < 0)
            throw new IllegalArgumentException("Duplicate or inconsistent champions");
        records.put(key, new Record(score, stars));
    }

    private static PerformanceResult parse(Key key, String fields) {
        String[] values = fields.split(",", -1);
        if (values.length != 6) throw new IllegalArgumentException("Invalid result fields");
        var result = new PerformanceResult(key.songId(), key.role(), key.difficulty(), number(values[0]),
                count(values[1]), count(values[2]), count(values[3]), flag(values[4]), flag(values[5]));
        if (result.failed() || result.notes() == 0 && (result.hits() != 0 || result.bestStreak() != 0 || result.fullCombo()))
            throw new IllegalArgumentException("Ineligible saved attempt");
        return result;
    }

    private static String fields(PerformanceResult result) {
        return result.score() + "," + result.hits() + "," + result.notes() + "," + result.bestStreak()
                + "," + result.failed() + "," + result.fullCombo();
    }
    private static long number(String value) {
        if (!value.matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid number");
        return Long.parseLong(value);
    }
    private static int count(String value) {
        long count = number(value);
        if (count > 1_000_000) throw new IllegalArgumentException("Unbounded note count");
        return (int) count;
    }
    private static boolean flag(String value) {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean");
        return value.equals("true");
    }
    private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static int compareScore(PerformanceResult a, PerformanceResult b) {
        int score = Long.compare(a.score(), b.score());
        return score != 0 ? score : compareStars(a, b);
    }
    private static int compareStars(PerformanceResult a, PerformanceResult b) {
        int result = Integer.compare(a.stars(), b.stars());
        if (result == 0) result = Double.compare(a.accuracy(), b.accuracy());
        if (result == 0) result = Boolean.compare(a.fullCombo(), b.fullCombo());
        if (result == 0) result = Long.compare(a.score(), b.score());
        if (result == 0) result = Integer.compare(a.bestStreak(), b.bestStreak());
        return result;
    }
}
