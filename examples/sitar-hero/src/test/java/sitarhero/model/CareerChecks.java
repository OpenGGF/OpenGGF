package sitarhero.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Real career/save/practice behavior, compiled without the engine or a test library. */
public final class CareerChecks {
    private CareerChecks() { }

    private static PerformanceResult result(String song, String role, String difficulty,
                                            long score, int hits, int notes) {
        return new PerformanceResult(song, role, difficulty, score, hits, notes, hits, false, hits == notes && notes > 0);
    }
    private static PerformanceResult clear(String song) { return result(song, "SITAR", "MEDIUM", 500, 8, 10); }
    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value) { if (!value) throw new AssertionError(); }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid value was accepted");
    }
    private static List<SongSpec> songs(int count) {
        var songs = new ArrayList<SongSpec>();
        String[] games = {"s1", "s2", "s3k"};
        for (int i = 0; i < count; i++) songs.add(new SongSpec("song-" + i, "Song " + i, games[i % 3], 1, 600, 0, 0));
        return List.copyOf(songs);
    }

    public static void attainableStarsAndGrades() {
        int[][] thresholds = {{0, 1}, {49, 1}, {50, 2}, {69, 2}, {70, 3}, {84, 3}, {85, 4}, {94, 4}, {95, 5}, {100, 5}};
        String[] grades = {"F", "D", "C", "B", "A", "S"};
        for (int[] row : thresholds) {
            var r = result("green-hill", "SITAR", "EXPERT", 0, row[0], 100);
            eq(row[1], r.stars()); eq(grades[row[1]], r.grade()); eq(row[0] / 100.0, r.accuracy()); yes(r.cleared());
        }
        var failed = new PerformanceResult("green-hill", "BONGOS", "EASY", 10, 99, 100, 99, true, false);
        eq(0, failed.stars()); eq("F", failed.grade()); yes(!failed.cleared());
        var empty = result("green-hill", "SITAR", "MEDIUM", 0, 0, 0);
        eq(0.0, empty.accuracy()); eq(0, empty.stars()); yes(!empty.cleared()); yes(!empty.fullCombo());
    }

    public static void resultValidationAndFinalSessionSnapshot() {
        invalid(() -> result("bad\nsong", "SITAR", "MEDIUM", 1, 1, 1));
        invalid(() -> result("green-hill", "GUITAR", "MEDIUM", 1, 1, 1));
        invalid(() -> result("green-hill", "SITAR", "IMPOSSIBLE", 1, 1, 1));
        invalid(() -> result("green-hill", "sitar", "MEDIUM", 1, 1, 1));
        invalid(() -> result("green-hill", "SITAR", "MEDIUM", -1, 1, 1));
        invalid(() -> result("green-hill", "SITAR", "MEDIUM", 1, 2, 1));
        invalid(() -> result("green-hill", "SITAR", "MEDIUM", 1, -1, 1));
        invalid(() -> new PerformanceResult("green-hill", "SITAR", "MEDIUM", 1, 1, 1, 2, false, true));
        invalid(() -> new PerformanceResult("green-hill", "SITAR", "MEDIUM", 1, 1, 2, 1, false, true));
        invalid(() -> new PerformanceResult("green-hill", "SITAR", "MEDIUM", 1, 1, 1, 1, true, true));
        invalid(() -> result("green-hill", "SITAR", "MEDIUM", 1, 0, 1_000_001));
        Chart chart = new Chart(List.of(new ChartNote(100, 100, 1, false, -1, 0)), 1_000, 50);
        var s = new RhythmSession(chart, false, 1_000);
        invalid(() -> PerformanceResult.from("green-hill", "SITAR", "MEDIUM", s));
        s.input(100, 1, 0, true, false, false); s.advance(1_000);
        var snapshot = PerformanceResult.from("green-hill", "SITAR", "MEDIUM", s);
        eq(50L, snapshot.score()); eq(1, snapshot.hits()); eq(1, snapshot.notes()); eq(1, snapshot.bestStreak());
        yes(snapshot.fullCombo()); yes(snapshot.cleared()); eq(5, snapshot.stars());
    }

    public static void independentRecordsAndAttemptTotals() {
        var p = new PlayerProfile();
        yes(p.record(result("green-hill", "SITAR", "EASY", 500, 8, 10)));
        yes(p.record(result("green-hill", "SITAR", "EXPERT", 300, 9, 10)));
        yes(p.record(result("green-hill", "BONGOS", "EASY", 200, 10, 10)));
        yes(!p.record(result("green-hill", "SITAR", "EASY", 100, 7, 10)));
        eq(3, p.clears()); eq(4L, p.attempts()); eq(1_100L, p.totalScore());
        eq(500L, p.best("green-hill", "SITAR", "EASY").orElseThrow().score());
        eq(300L, p.best("green-hill", "SITAR", "EXPERT").orElseThrow().score());
        yes(p.best("green-hill", "HARP", "EASY").isEmpty());
        var failed = new PerformanceResult("green-hill", "SITAR", "EASY", 9_999, 1, 10, 1, true, false);
        yes(!p.record(failed)); yes(!p.record(result("empty", "SITAR", "EASY", 0, 0, 0)));
        eq(4L, p.attempts()); eq(1_100L, p.totalScore());
    }

    public static void querySelectionValidationAlsoAppliesToEmptyTours() {
        var p = new PlayerProfile(); var tour = new CareerProgress(p);
        invalid(() -> p.best("green-hill", "GUITAR", "MEDIUM"));
        invalid(() -> p.bestStars("green-hill", "SITAR", "expert"));
        invalid(() -> p.importLegacyScore("green-hill", "SITAR", "MEDIUM", -1));
        invalid(() -> tour.unlocked(List.of(), "green-hill", "SITAR", "unknown"));
        invalid(() -> tour.cleared(List.of(), "GUITAR", "MEDIUM"));
        invalid(() -> tour.complete(List.of(), "SITAR", "unknown"));
    }

    public static void lowerScoreCanImproveStarsWithoutInventingAnAttempt() {
        var p = new PlayerProfile();
        var scoreWinner = result("green-hill", "SITAR", "MEDIUM", 1_000, 70, 100);
        var starWinner = result("green-hill", "SITAR", "MEDIUM", 200, 95, 100);
        yes(p.record(scoreWinner)); yes(p.record(starWinner));
        eq(scoreWinner, p.best("green-hill", "SITAR", "MEDIUM").orElseThrow());
        eq(starWinner, p.bestStars("green-hill", "SITAR", "MEDIUM").orElseThrow());
        var saved = new PlayerProfile(); saved.read(p.encode());
        eq(scoreWinner, saved.best("green-hill", "SITAR", "MEDIUM").orElseThrow());
        eq(starWinner, saved.bestStars("green-hill", "SITAR", "MEDIUM").orElseThrow());
        yes(!saved.record(scoreWinner)); eq(3L, saved.attempts()); eq(2_200L, saved.totalScore());
    }

    public static void deterministicRoundTripAndLegacyScoreMigration() {
        var p = new PlayerProfile();
        yes(p.importLegacyScore("green-hill", "SITAR", "MEDIUM", 5_000));
        eq(5_000L, p.best("green-hill", "SITAR", "MEDIUM").orElseThrow().score());
        eq(0, p.clears()); eq(0L, p.attempts()); eq(0L, p.totalScore());
        eq(0, p.bestStars("green-hill", "SITAR", "MEDIUM").orElseThrow().stars());
        yes(!p.importLegacyScore("green-hill", "SITAR", "MEDIUM", 4_000));
        p.record(clear("green-hill")); p.record(clear("chemical-plant"));
        eq(5_000L, p.best("green-hill", "SITAR", "MEDIUM").orElseThrow().score());
        eq(3, p.bestStars("green-hill", "SITAR", "MEDIUM").orElseThrow().stars());
        eq(2, p.clears());
        var loaded = new PlayerProfile(); loaded.read(p.encode()); eq(p.encode(), loaded.encode());
        eq(2, loaded.clears()); eq(2L, loaded.attempts()); eq(1_000L, loaded.totalScore());
        loaded.read("5000"); eq(p.encode(), loaded.encode()); // parent parses old per-song text, never as a profile
        var reversed = new PlayerProfile(); reversed.record(clear("chemical-plant")); reversed.record(clear("green-hill"));
        reversed.importLegacyScore("green-hill", "SITAR", "MEDIUM", 5_000); eq(p.encode(), reversed.encode());
        var fresh = new PlayerProfile(); loaded.read(fresh.encode()); eq(0, loaded.clears()); eq(0L, loaded.attempts());
    }

    public static void corruptionCannotPartiallyReplaceExistingProgress() {
        var p = new PlayerProfile(); p.record(clear("green-hill"));
        String valid = p.encode();
        for (String corrupt : List.of("", "sitar-profile=2\nattempts=0\ntotal-score=0\n", valid.replace("SITAR", "GUITAR"),
                valid.replace("MEDIUM", "medium"), valid.replace("false", "FALSE"), valid.replace("500,", "-500,"),
                valid.replace("8,10,8", "11,10,8"), valid.replace("total-score=500", "total-score=9223372036854775808"),
                valid.replace("attempts=1", "attempts=-1"), valid + "attempts=2\n", valid.replace("attempts=1\n", ""),
                valid + valid.substring(valid.indexOf("record=")), valid.replace("false,false", "true,false"),
                valid.replace("8,10,8,false,false", "8,10,8,false,true"), "x".repeat(262_145))) {
            p.read(corrupt); eq(valid, p.encode());
        }
        p.read(null); eq(valid, p.encode());
        var extras = new StringBuilder(valid);
        for (int i = 0; i < 1_020; i++) extras.append("future-").append(i).append("=ignored\n");
        p.read(extras.toString()); eq(valid, p.encode()); // exactly 1,024 lines including header/counters/record
        extras.append("future=too-many\n");
        var empty = new PlayerProfile(); empty.read(extras.toString()); eq(0, empty.clears());
        empty.read(valid + "future=" + "x".repeat(513)); eq(0, empty.clears());
        var crlf = new PlayerProfile(); crlf.read(valid.replace("\n", "\r\n")); eq(valid, crlf.encode());
    }

    public static void boundedRecordsAndSaturatingTotals() {
        var full = new PlayerProfile();
        for (int i = 0; i < 192; i++) yes(full.record(clear("song-" + i)));
        yes(!full.record(clear("song-192"))); eq(192, full.clears()); eq(192L, full.attempts());
        var restored = new PlayerProfile(); restored.read(full.encode()); eq(192, restored.clears());
        var huge = new PlayerProfile(); huge.record(result("a", "SITAR", "MEDIUM", Long.MAX_VALUE, 1, 1));
        huge.record(result("b", "SITAR", "MEDIUM", 10, 1, 1)); eq(Long.MAX_VALUE, huge.totalScore());
        var loaded = new PlayerProfile(); loaded.read(huge.encode().replace("attempts=2", "attempts=9223372036854775807"));
        loaded.record(clear("c")); eq(Long.MAX_VALUE, loaded.attempts()); eq(Long.MAX_VALUE, loaded.totalScore());
        var rejected = new PlayerProfile();
        String extraRow = "record=extra|SITAR|MEDIUM|500,8,10,8,false,false|500,8,10,8,false,false\n";
        rejected.read(full.encode() + extraRow); eq(0, rejected.clears());
    }

    public static void sequentialTiersAndWholeTourCompletion() {
        List<SongSpec> songs = songs(12); var p = new PlayerProfile(); var tour = new CareerProgress(p);
        yes(tour.unlocked(songs, "song-0", "SITAR", "MEDIUM")); yes(tour.unlocked(songs, "song-2", "SITAR", "MEDIUM"));
        yes(!tour.unlocked(songs, "song-3", "SITAR", "MEDIUM")); yes(!tour.unlocked(songs, "unknown", "SITAR", "MEDIUM"));
        p.record(clear("song-0")); yes(!tour.unlocked(songs, "song-3", "SITAR", "MEDIUM"));
        p.record(clear("song-2")); yes(tour.unlocked(songs, "song-5", "SITAR", "MEDIUM"));
        yes(!tour.unlocked(songs, "song-6", "SITAR", "MEDIUM"));
        yes(!tour.unlocked(songs, "song-3", "BONGOS", "MEDIUM")); yes(!tour.unlocked(songs, "song-3", "SITAR", "EXPERT"));
        eq(2, tour.cleared(songs, "SITAR", "MEDIUM")); yes(!tour.complete(songs, "SITAR", "MEDIUM"));
        yes(tour.venue(songs, "song-0").equals(tour.venue(songs, "song-2")));
        yes(!tour.venue(songs, "song-0").equals(tour.venue(songs, "song-3")));
        for (SongSpec song : songs) p.record(clear(song.id()));
        eq(12, tour.cleared(songs, "SITAR", "MEDIUM")); yes(tour.complete(songs, "SITAR", "MEDIUM"));
        var outOfOrder = new PlayerProfile(); outOfOrder.record(clear("song-3")); outOfOrder.record(clear("song-4"));
        yes(!new CareerProgress(outOfOrder).unlocked(songs, "song-6", "SITAR", "MEDIUM"));
        var legacy = new PlayerProfile(); legacy.importLegacyScore("song-0", "SITAR", "MEDIUM", 9_999);
        legacy.record(clear("song-0")); legacy.record(clear("song-1"));
        yes(new CareerProgress(legacy).unlocked(songs, "song-3", "SITAR", "MEDIUM"));
    }

    public static void everyRomSubsetCanFinishWithoutMissingSongGates() {
        List<SongSpec> library = songs(12); String[] games = {"s1", "s2", "s3k"};
        for (int subset = 1; subset < 8; subset++) {
            var available = new ArrayList<SongSpec>();
            for (SongSpec song : library) for (int game = 0; game < 3; game++)
                if (song.game().equals(games[game]) && (subset & (1 << game)) != 0) available.add(song);
            for (String role : List.of("SITAR", "BONGOS", "SYNTH", "HARP"))
                for (String difficulty : List.of("EASY", "MEDIUM", "HARD", "EXPERT")) {
                    var p = new PlayerProfile(); var tour = new CareerProgress(p);
                    for (SongSpec song : available) {
                        yes(tour.unlocked(available, song.id(), role, difficulty));
                        p.record(result(song.id(), role, difficulty, 10, 1, 1));
                    }
                    eq(available.size(), tour.cleared(available, role, difficulty)); yes(tour.complete(available, role, difficulty));
                }
        }
        var p = new PlayerProfile(); var tour = new CareerProgress(p);
        yes(!tour.complete(List.of(), "SITAR", "MEDIUM"));
        for (int count : List.of(1, 2, 4, 5, 7, 13)) {
            var available = songs(count);
            for (SongSpec song : available) {
                yes(tour.unlocked(available, song.id(), "SITAR", "MEDIUM")); p.record(clear(song.id()));
            }
            yes(tour.complete(available, "SITAR", "MEDIUM"));
        }
    }

    public static void duplicateSongsDoNotAwardExtraClearsOrUnlocks() {
        List<SongSpec> songs = songs(4); var duplicate = new ArrayList<>(songs); duplicate.addFirst(songs.getFirst());
        var p = new PlayerProfile(); p.record(clear("song-0")); var tour = new CareerProgress(p);
        eq(1, tour.cleared(duplicate, "SITAR", "MEDIUM")); yes(!tour.unlocked(duplicate, "song-3", "SITAR", "MEDIUM"));
        p.record(clear("song-1")); yes(tour.unlocked(duplicate, "song-3", "SITAR", "MEDIUM"));
    }

    public static void noFailResolvesAllMissesAndCanRecoverAudioAndStreak() {
        var notes = new ArrayList<ChartNote>();
        for (int i = 0; i < 40; i++) notes.add(new ChartNote(200 + i * 200, 200 + i * 200, 1, false, -1, 0));
        Chart chart = new Chart(notes, 9_000, 500);
        var normal = new RhythmSession(chart, false, 1_000);
        var practice = new RhythmSession(chart, false, 1_000, true);
        normal.advance(9_000); yes(normal.failed()); yes(normal.misses() < 40);
        practice.advance(9_000); yes(!practice.failed()); yes(practice.finished()); eq(40, practice.misses());
        eq(0, practice.streak()); eq(0.0, practice.rock()); yes(!practice.partAudible());
        for (int i = 0; i < 40; i++) eq((byte) 2, practice.status(i));
        var recovering = new RhythmSession(chart, false, 1_000, true);
        recovering.advance(6_901); eq(34, recovering.misses()); eq(0.0, recovering.rock());
        recovering.input(7_000, 1, 0, true, false, false); eq(1, recovering.hits()); eq(1, recovering.streak());
        eq(50L, recovering.score()); yes(recovering.partAudible()); yes(recovering.rock() > 0);
        recovering.input(7_020, 1, 0, true, false, false); eq(0, recovering.streak()); yes(!recovering.partAudible());
        recovering.advance(9_000); yes(!recovering.failed()); eq(39, recovering.misses()); eq(1, recovering.bestStreak());
        var explicitNormal = new RhythmSession(chart, false, 1_000, false); explicitNormal.advance(9_000);
        eq(normal.misses(), explicitNormal.misses()); eq(normal.position(), explicitNormal.position()); yes(explicitNormal.failed());
    }
}
