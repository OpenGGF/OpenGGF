package sitarhero.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Actual authored-route and journal behavior; no engine, native assets or testing framework. */
public final class WorldTourChecks {
    private WorldTourChecks() { }
    public static void main(String[] args) {
        immutableMetadata(); completePartitionAndNativeOrder(); completeInstalledTours();
        everyMainActGatesAndSideGigsNeverGate(); completedEligibleResultsOnly();
        sharedProgressAndSeparateHighScores(); canonicalMembership(); boundedSeenScenes(); atomicPersistence();
        System.out.println("9 authored world-tour checks passed");
    }
    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value) { if (!value) throw new AssertionError(); }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid metadata was accepted");
    }
    private static void immutable(Runnable action) {
        try { action.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Authored metadata was mutable");
    }
    private static PerformanceResult result(String song, String role, String difficulty) {
        var session = new RhythmSession(new Chart(List.of(new ChartNote(100, 100, 1, false, -1, 0)), 1_000, 50), false, 1_000);
        session.input(100, 1, 0, true, false, false); session.advance(1_000);
        return PerformanceResult.from(song, role, difficulty, session);
    }
    private static PerformanceResult clear(String song) { return result(song, "SITAR", "MEDIUM"); }
    private static CareerTour tour(String game) {
        return CareerTours.all().stream().filter(tour -> tour.game().equals(game)).findFirst().orElseThrow();
    }
    private static List<String> required(String game) {
        return switch (game) {
            case "s1" -> List.of("green-hill", "marble", "spring-yard", "labyrinth", "star-light", "scrap-brain",
                    "final-zone", "s1-ending", "s1-credits");
            case "s2" -> List.of("emerald-hill", "chemical-plant", "aquatic-ruin", "casino-night", "hill-top", "mystic-cave",
                    "oil-ocean", "metropolis", "sky-chase", "wing-fortress", "death-egg", "final-battle-s2", "ending-s2", "credits-s2");
            case "s3k" -> List.of("angel-island-1", "angel-island-2", "hydrocity-1", "hydrocity-2", "marble-garden-1", "marble-garden-2",
                    "carnival-night-1", "carnival-night-2", "icecap-1", "icecap-2", "launch-base-1", "launch-base-2",
                    "mushroom-hill-1", "mushroom-hill-2", "flying-battery-1", "flying-battery-2", "sandopolis-1", "sandopolis-2",
                    "lava-reef-1", "lava-reef-2", "sky-sanctuary", "death-egg-1", "death-egg-2", "s3k-final-boss", "doomsday",
                    "s3k-ending", "s3k-credits");
            default -> throw new AssertionError(game);
        };
    }

    public static void immutableMetadata() {
        var songs = new ArrayList<>(List.of("green-hill"));
        var sides = new ArrayList<>(List.of("s1-special-stage"));
        var world = new CareerWorld("s1-green-hill", "Green Hill", songs, sides);
        songs.clear(); sides.clear(); eq(List.of("green-hill"), world.requiredSongIds()); eq(List.of("s1-special-stage"), world.sideSongIds());
        immutable(() -> world.requiredSongIds().clear()); immutable(() -> world.sideSongIds().clear());
        var worlds = new ArrayList<>(List.of(world));
        var tour = new CareerTour("s1", "s1", "Sonic 1", "South Island", worlds); worlds.clear(); eq(1, tour.worlds().size());
        immutable(() -> tour.worlds().clear()); immutable(() -> CareerTours.all().clear());
        invalid(() -> new CareerWorld("bad\nid", "Bad", List.of("green-hill"), List.of()));
        invalid(() -> new CareerWorld("s1-empty", "Empty", List.of(), List.of()));
        invalid(() -> new CareerWorld("s1-duplicate", "Duplicate", List.of("green-hill", "green-hill"), List.of()));
        invalid(() -> new CareerWorld("s1-duplicate", "Duplicate", List.of("green-hill"), List.of("green-hill")));
        invalid(() -> new CareerTour("s1", "s1", "Sonic 1", "South Island", List.of(world, world)));
        invalid(() -> new CareerTour("s2", "s2", "Sonic 2", "West Side", List.of(world)));
        invalid(() -> new CareerTour("s1", "s1", "Sonic 1", "South Island", List.of()));
        var repeatedSong = new CareerWorld("s1-marble", "Marble", List.of("green-hill"), List.of());
        invalid(() -> new CareerTour("s1", "s1", "Sonic 1", "South Island", List.of(world, repeatedSong)));
    }

    public static void completePartitionAndNativeOrder() {
        List<CareerTour> tours = CareerTours.all(); eq(3, tours.size()); eq(List.of("s1", "s2", "s3k"), tours.stream().map(CareerTour::id).toList());
        eq(List.of("s1-green-hill", "s1-marble", "s1-spring-yard", "s1-labyrinth", "s1-star-light", "s1-scrap-brain", "s1-finale", "s1-encore"),
                tour("s1").worlds().stream().map(CareerWorld::id).toList());
        eq(List.of("s2-emerald-hill", "s2-chemical-plant", "s2-aquatic-ruin", "s2-casino-night", "s2-hill-top", "s2-mystic-cave", "s2-oil-ocean",
                "s2-metropolis", "s2-sky-chase", "s2-wing-fortress", "s2-death-egg", "s2-finale", "s2-encore"),
                tour("s2").worlds().stream().map(CareerWorld::id).toList());
        eq(List.of("s3k-angel-island", "s3k-hydrocity", "s3k-marble-garden", "s3k-carnival-night", "s3k-icecap", "s3k-launch-base", "s3k-mushroom-hill",
                "s3k-flying-battery", "s3k-sandopolis", "s3k-lava-reef", "s3k-sky-sanctuary", "s3k-death-egg", "s3k-finale", "s3k-encore"),
                tour("s3k").worlds().stream().map(CareerWorld::id).toList());
        var assigned = new HashSet<String>(); int sideCount = 0;
        for (CareerTour tour : tours) {
            eq(required(tour.game()), tour.worlds().stream().flatMap(world -> world.requiredSongIds().stream()).toList());
            for (CareerWorld world : tour.worlds()) {
                for (List<String> ids : List.of(world.requiredSongIds(), world.sideSongIds())) for (String id : ids) {
                    yes(assigned.add(id)); eq(tour.game(), SongCatalog.all().stream().filter(song -> song.id().equals(id)).findFirst().orElseThrow().game());
                }
                sideCount += world.sideSongIds().size();
            }
        }
        eq(29, sideCount); eq(79, assigned.size()); eq(79, SongCatalog.all().size());
        eq(new HashSet<>(SongCatalog.all().stream().map(SongSpec::id).toList()), assigned);
    }

    public static void completeInstalledTours() {
        String[] games = {"s1", "s2", "s3k"};
        for (int subset = 0; subset < 8; subset++) {
            var installed = new ArrayList<String>();
            for (int i = 0; i < 3; i++) if ((subset & (1 << i)) != 0) installed.add(games[i]);
            var available = SongCatalog.available(installed);
            eq(installed, CareerTours.available(available).stream().map(CareerTour::game).toList());
            for (CareerTour tour : CareerTours.available(available)) {
                var journal = new CareerJournal();
                for (CareerWorld world : tour.worlds()) {
                    yes(journal.unlocked(tour, world));
                    for (String id : world.requiredSongIds()) yes(journal.record(clear(id), true));
                }
                yes(journal.complete(tour)); eq(required(tour.game()).size(), journal.cleared(tour));
            }
        }
        var incomplete = new ArrayList<>(SongCatalog.all()); incomplete.removeIf(song -> song.id().equals("hydrocity-2"));
        eq(List.of("s1", "s2"), CareerTours.available(incomplete).stream().map(CareerTour::game).toList());
        var optionalMissing = new ArrayList<>(SongCatalog.all()); optionalMissing.removeIf(song -> song.id().equals("s1-boss"));
        eq(3, CareerTours.available(optionalMissing).size());
        eq(required("s1"), CareerTours.available(optionalMissing).getFirst().worlds().stream().flatMap(world -> world.requiredSongIds().stream()).toList());
        var wrongGame = new ArrayList<>(incomplete);
        wrongGame.add(new SongSpec("hydrocity-2", "Wrong donor", "s1", 1, 600, 0, 0));
        eq(List.of("s1", "s2"), CareerTours.available(wrongGame).stream().map(CareerTour::game).toList());
    }

    public static void everyMainActGatesAndSideGigsNeverGate() {
        for (CareerTour tour : CareerTours.all()) {
            var journal = new CareerJournal();
            for (CareerWorld world : tour.worlds()) for (String id : world.sideSongIds()) yes(journal.record(clear(id), true));
            eq(0, journal.cleared(tour)); yes(!journal.complete(tour));
            for (int w = 0; w < tour.worlds().size(); w++) {
                CareerWorld world = tour.worlds().get(w); yes(journal.unlocked(tour, world));
                for (int s = 0; s < world.requiredSongIds().size(); s++) {
                    eq(s, journal.cleared(world)); yes(!journal.complete(world));
                    if (w + 1 < tour.worlds().size()) yes(!journal.unlocked(tour, tour.worlds().get(w + 1)));
                    yes(journal.record(clear(world.requiredSongIds().get(s)), true));
                }
                yes(journal.complete(world)); eq(world.requiredSongIds().size(), journal.cleared(world));
            }
            yes(journal.complete(tour)); eq(required(tour.game()).size(), journal.cleared(tour));
            var mainOnly = new CareerJournal();
            for (String id : required(tour.game())) mainOnly.record(clear(id), true);
            yes(mainOnly.complete(tour));
            for (CareerWorld world : tour.worlds()) for (String id : world.sideSongIds()) yes(!mainOnly.cleared(id));
        }
    }

    public static void completedEligibleResultsOnly() {
        var journal = new CareerJournal(); var valid = clear("green-hill");
        // The scene classifies modes/completion; this checks the actual explicit eligibility seam.
        yes(!journal.record(valid, false)); yes(!journal.cleared("green-hill"));
        var notes = new ArrayList<ChartNote>();
        for (int i = 0; i < 40; i++) notes.add(new ChartNote(200 + i * 200, 200 + i * 200, 1, false, -1, 0));
        var failed = new RhythmSession(new Chart(notes, 9_000, 500), false, 1_000); failed.advance(9_000);
        yes(failed.failed()); yes(!journal.record(PerformanceResult.from("green-hill", "SITAR", "MEDIUM", failed), true));
        var empty = new RhythmSession(new Chart(List.of(), 1_000, 50), false, 1_000); empty.advance(1_000);
        yes(!journal.record(PerformanceResult.from("green-hill", "SITAR", "MEDIUM", empty), true));
        yes(!journal.record(clear("unknown-song"), true)); yes(!journal.record(null, true));
        yes(!journal.record(new PerformanceResult("green-hill", "SITAR", "MEDIUM", 5_000, 0, 0, 0, false, false), true));
        yes(journal.record(valid, true)); yes(!journal.record(valid, true)); yes(journal.cleared("green-hill"));
        yes(!journal.cleared("unknown-song")); yes(!journal.cleared((String) null));
    }

    public static void sharedProgressAndSeparateHighScores() {
        var journal = new CareerJournal(); var profile = new PlayerProfile();
        profile.importLegacyScore("green-hill", "SITAR", "MEDIUM", 5_000); String original = profile.encode();
        yes(journal.record(result("angel-island-1", "SITAR", "EASY"), true));
        yes(journal.record(result("angel-island-2", "BONGOS", "EXPERT"), true));
        var tour = tour("s3k"); eq(2, journal.cleared(tour.worlds().getFirst())); yes(journal.unlocked(tour, tour.worlds().get(1)));
        for (String role : List.of("SITAR", "BONGOS", "SYNTH", "HARP")) for (String difficulty : List.of("EASY", "MEDIUM", "HARD", "EXPERT"))
            yes(!journal.record(result("angel-island-1", role, difficulty), true));
        eq(2, journal.cleared(tour)); eq(original, profile.encode());
        var loaded = new PlayerProfile(); loaded.read(original); eq(original, loaded.encode()); eq(5_000L, loaded.best("green-hill", "SITAR", "MEDIUM").orElseThrow().score());
    }

    public static void canonicalMembership() {
        var journal = new CareerJournal(); CareerTour s1 = tour("s1"), s2 = tour("s2");
        yes(!journal.unlocked(s1, s2.worlds().getFirst()));
        CareerWorld original = s1.worlds().getFirst();
        var copy = new CareerWorld(original.id(), original.title(), original.requiredSongIds(), original.sideSongIds());
        yes(journal.unlocked(s1, copy));
        var forged = new CareerWorld(original.id(), original.title(), List.of("s1-boss"), List.of());
        var forgedTour = new CareerTour(s1.id(), s1.game(), s1.title(), s1.subtitle(), List.of(forged));
        journal.record(clear("s1-boss"), true);
        yes(!journal.unlocked(s1, forged)); yes(!journal.unlocked(forgedTour, forged));
        yes(!journal.complete(forged)); yes(!journal.complete(forgedTour)); eq(0, journal.cleared(forged)); eq(0, journal.cleared(forgedTour));
        yes(!journal.unlocked(s1, s1.worlds().getLast()));
        eq(0, journal.cleared((CareerWorld) null)); eq(0, journal.cleared((CareerTour) null));
        yes(!journal.complete((CareerWorld) null)); yes(!journal.complete((CareerTour) null));
        yes(!journal.unlocked(null, original)); yes(!journal.unlocked(s1, null));
    }

    public static void boundedSeenScenes() {
        var journal = new CareerJournal(); var scenes = new HashSet<String>();
        for (CareerTour tour : CareerTours.all()) {
            for (CareerWorld world : tour.worlds()) scenes.add(world.id() + "-intro");
            scenes.add(tour.id() + "-outro");
        }
        eq(38, scenes.size());
        for (String scene : scenes) { yes(!journal.seen(scene)); yes(journal.markSeen(scene)); yes(journal.seen(scene)); yes(!journal.markSeen(scene)); }
        String saved = journal.encode();
        for (int i = 0; i < 1_000; i++) { yes(!journal.markSeen("unknown-" + i)); yes(!journal.seen("unknown-" + i)); }
        for (String id : List.of("s1-intro", "s1-boss-intro", "s1-green-hill-outro", "", "x".repeat(1_024))) yes(!journal.markSeen(id));
        yes(!journal.markSeen(null)); yes(!journal.seen(null)); eq(saved, journal.encode());
        var restored = new CareerJournal(); restored.read(saved); eq(saved, restored.encode());
        for (String scene : scenes) yes(restored.seen(scene));
    }

    public static void atomicPersistence() {
        var journal = new CareerJournal(); journal.record(clear("green-hill"), true); journal.markSeen("s1-green-hill-intro");
        String valid = journal.encode(); var restored = new CareerJournal(); restored.read(valid); eq(valid, restored.encode());
        yes(restored.cleared("green-hill")); yes(restored.seen("s1-green-hill-intro"));
        var malformed = new ArrayList<>(List.of("", "sitar-career=2\nclear=marble\n", valid + "sitar-career=1\n",
                "sitar-career=1\nclear=marble\nseen=unknown\n", valid + "clear=green-hill\n", valid + "seen=s1-green-hill-intro\n",
                valid + "clear=unknown\n", valid + "seen=s1-intro\n", valid + "clear=green-hill|SITAR|EASY\n",
                valid + "clear=\n", valid + "marble\n", valid + "unexpected=1\n", valid + "\nseen=s2-outro\n",
                "x".repeat(16_385), valid + "clear=" + "x".repeat(97) + "\n", "sitar-career=1\n" + "clear=marble\n".repeat(129)));
        for (String corrupt : malformed) { journal.read(corrupt); eq(valid, journal.encode()); }
        journal.read(null); eq(valid, journal.encode());
        restored.read(valid.replace("\n", "\r\n")); eq(valid, restored.encode());
        var full = new CareerJournal();
        for (SongSpec song : SongCatalog.all()) full.record(clear(song.id()), true);
        for (CareerTour tour : CareerTours.all()) {
            for (CareerWorld world : tour.worlds()) full.markSeen(world.id() + "-intro");
            full.markSeen(tour.id() + "-outro");
        }
        restored.read(full.encode()); eq(full.encode(), restored.encode());
        for (CareerTour tour : CareerTours.all()) yes(restored.complete(tour));
        restored.read(new CareerJournal().encode()); yes(!restored.cleared("green-hill")); yes(!restored.seen("s1-green-hill-intro"));
    }
}
