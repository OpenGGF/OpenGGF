package typinghog.hub;

import java.util.List;
import java.util.Set;
import typinghog.core.Lexicon;
import typinghog.hub.model.Drill;
import typinghog.hub.model.DrillSession;
import typinghog.hub.model.Grades;
import typinghog.hub.model.Handoff;
import typinghog.hub.model.KeyTally;
import typinghog.hub.model.Profile;
import typinghog.hub.model.ResultMerge;
import typinghog.hub.model.School;
import typinghog.hub.model.SchoolMaterial;
import typinghog.hub.model.StoryBook;
import typinghog.hub.model.TextArt;
import typinghog.hub.model.WordMenu;
import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

/**
 * Behaviour checks for the hub's engine-free rules (profile codec, story progression, typed
 * menus, Typing School drills and grades, result merging); run by the engine's
 * TestTypingHedgehogHub, or directly with {@code main}.
 */
public final class HubChecks {
    public static void main(String[] args) {
        profileRoundTripsAndSurvivesDamage();
        recordsKeepTheBestTen();
        storyOpensStopsInOrderAndSkipsUnbuiltActs();
        storyFileParses();
        typedMenusFollowAndSelect();
        resultsMergeIntoTheProfile();
        bossRushRaisesStarsAndEndless();
        handoffsRoundTrip();
        attractDemosPlayThemselves();
        suddenDeathEndsOnTheFirstError();
        ringRushRunsSixtySeconds();
        reflexTimesLettersAndMisses();
        bossTrainingRoundsAndQuiz();
        gradesAndFinalExamUnlock();
        weakKeysFollowLifetimeMisses();
        lessonsUseTheirRows();
        textArtParses();
        System.out.println("17 hub checks passed");
    }

    private static void eq(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void yes(boolean value, String what) {
        if (!value) throw new AssertionError(what);
    }

    // ---- profile ------------------------------------------------------------------------------

    public static void profileRoundTripsAndSurvivesDamage() {
        Profile p = new Profile();
        p.difficulty(LaunchIntent.Difficulty.EXPERT);
        p.tricky(true);
        p.lore(false);
        p.voice(LaunchIntent.Voice.CLEAN);
        p.clear("s2", 0, 1);
        p.awardEmerald("s2", 0, 1);
        p.raiseStars("s1", 0, 2);
        p.raiseStars("s1", 0, 2);
        p.recordGrade("RING_RUSH", "B");
        p.keyHits().addEncoded("a:10,_:4");
        p.keyMisses().addEncoded("q:3");
        p.addTyping(500, 20, 90, 2.0);
        p.file(Profile.actTable("s2", 0, 1, LaunchIntent.Difficulty.EXPERT), new Profile.Entry(9000, "A", 61.5));
        Profile back = Profile.decode(p.encode() + "future.field=kept\n");
        eq(LaunchIntent.Difficulty.EXPERT, back.difficulty());
        eq("standard,tricky", back.dictionaries());
        eq(LaunchIntent.Voice.CLEAN, back.voice());
        eq(LaunchIntent.Voice.OFF, Profile.decode("version=1\nvoice=false\n").voice());
        eq(LaunchIntent.Voice.MEGADRIVE, Profile.decode("version=1\nvoice=true\n").voice());
        eq(LaunchIntent.Voice.MEGADRIVE, new Profile().voice());
        yes(back.isCleared("s2", 0, 1), "clear survives");
        eq(1, back.emeraldCount("s2"));
        eq(2, back.stars("s1", 0, 2));
        eq("B", back.grade("RING_RUSH"));
        eq(10L, back.keyHits().get('a'));
        eq(4L, back.keyHits().get(' '));
        eq(3L, back.keyMisses().get('q'));
        eq(9000L, back.records(Profile.actTable("s2", 0, 1, LaunchIntent.Difficulty.EXPERT)).getFirst().score());
        eq(50.0, back.lifetimeWpm());
        yes(back.encode().contains("future.field=kept"), "unknown keys are kept for newer hubs");

        Profile damaged = Profile.decode("version=1\ndifficulty=WARP\ncapitals=true\nstars.s1:0:2=lots\n"
                + "record.act.s1.0.2.NORMAL=100/A/1.0,garbage,50/B/x\nlife.minutes=NaN\nkeys.hits=a:5,zz:9,b:-3\n");
        eq(LaunchIntent.Difficulty.NORMAL, damaged.difficulty());
        yes(damaged.capitals(), "good lines survive bad neighbours");
        eq(0, damaged.stars("s1", 0, 2));
        eq(1, damaged.records("act.s1.0.2.NORMAL").size());
        eq(0.0, damaged.lifeMinutes());
        eq(5L, damaged.keyHits().total());
        eq(LaunchIntent.Difficulty.NORMAL, Profile.decode(null).difficulty());
        eq(LaunchIntent.Difficulty.NORMAL, Profile.decode("\u0000\u0001 nonsense").difficulty());
    }

    public static void recordsKeepTheBestTen() {
        Profile p = new Profile();
        String table = "act.s1.0.2.NORMAL";
        for (int i = 1; i <= 12; i++) p.file(table, new Profile.Entry(i * 100, "C", 30));
        eq(10, p.records(table).size());
        eq(1200L, p.records(table).getFirst().score());
        eq(0, p.file(table, new Profile.Entry(50, "E", 5)));
        eq(1, p.file(table, new Profile.Entry(5000, "S", 90)));
        eq(3, p.file(table, new Profile.Entry(1150, "B", 40)));
        eq(500L, p.records(table).getLast().score());
    }

    // ---- story --------------------------------------------------------------------------------

    public static void storyOpensStopsInOrderAndSkipsUnbuiltActs() {
        StoryBook book = StoryBook.parse("s1 0 2 GHZ Green Hill\ns1 1 2 MZ Marble\ns1 2 2 SYZ Spring Yard\n"
                + "s1 3 2 LZ Labyrinth\ns2 0 1 EHZ Emerald Hill\n");
        List<StoryBook.Stop> s1 = book.chapter("s1");
        eq(4, s1.size());
        Set<String> built = Set.of("s1:0:2", "s1:2:2", "s1:3:2");
        java.util.function.Predicate<StoryBook.Stop> playable = stop -> built.contains(stop.key());
        Set<String> none = Set.of();
        eq(StoryBook.State.OPEN, StoryBook.state(s1, 0, none, playable));
        eq(StoryBook.State.UNDER_CONSTRUCTION, StoryBook.state(s1, 1, none, playable));
        eq(StoryBook.State.LOCKED, StoryBook.state(s1, 2, none, playable));
        Set<String> greenHill = Set.of("0:2");
        eq(StoryBook.State.CLEARED, StoryBook.state(s1, 0, greenHill, playable));
        eq(StoryBook.State.OPEN, StoryBook.state(s1, 2, greenHill, playable));
        eq(StoryBook.State.LOCKED, StoryBook.state(s1, 3, greenHill, playable));
        eq(2, StoryBook.resumeIndex(s1, greenHill, playable));
        eq(0, StoryBook.resumeIndex(s1, none, playable));
        eq("Emerald Hill", book.find("s2", 0, 1).title());
        yes(book.find("s2", 0, 0) == null, "act 1 is not a stop");
        try {
            StoryBook.parse("s4 0 0 NWZ Nowhere");
            throw new AssertionError("unknown game accepted");
        } catch (IllegalArgumentException expected) { }
    }

    /** The shipped story file: every game's zones in order with valid indices. */
    public static void storyFileParses() {
        String text = read("hub/story.txt");
        StoryBook book = StoryBook.parse(text);
        eq(7, book.chapter("s1").size());
        eq(11, book.chapter("s2").size());
        eq(13, book.chapter("s3k").size());
        eq("Green Hill", book.chapter("s1").getFirst().title());
        eq("The Doomsday", book.chapter("s3k").getLast().title());
        eq("DDZ", book.chapter("s3k").getLast().code());
        for (String game : List.of("s1", "s2", "s3k")) {
            int previous = -1;
            for (StoryBook.Stop stop : book.chapter(game)) {
                yes(stop.zone() > previous, game + " zones ascend: " + stop);
                previous = stop.zone();
            }
        }
    }

    // ---- menus --------------------------------------------------------------------------------

    public static void typedMenusFollowAndSelect() {
        WordMenu menu = new WordMenu(List.of(WordMenu.Item.of("Story"), WordMenu.Item.of("Arcade"),
                WordMenu.Item.of("Boss Rush", "boss rush", "rush"), WordMenu.Item.of("Typing School", "school"),
                WordMenu.Item.of("Records")));
        eq(WordMenu.Outcome.MOVED, menu.type("s"));
        eq(0, menu.cursor());
        eq(WordMenu.Outcome.MOVED, menu.type("c"));
        eq(3, menu.cursor());
        eq(2, menu.matched(3));
        eq(WordMenu.Outcome.SELECTED, menu.type("hool"));
        eq(3, menu.selected());
        eq("", menu.buffer());
        eq(WordMenu.Outcome.SELECTED, menu.type("STORY"));
        eq(0, menu.selected());
        // A slip restarts from the slipped key when it begins another word.
        menu.type("sto");
        eq(WordMenu.Outcome.MOVED, menu.type("a"));
        eq(1, menu.cursor());
        eq("a", menu.buffer());
        eq(WordMenu.Outcome.REJECTED, menu.type("zz"));
        eq("", menu.buffer());
        eq(WordMenu.Outcome.SELECTED, menu.type("boss rush"));
        eq(2, menu.selected());
        eq(WordMenu.Outcome.SELECTED, menu.type("rush"));
        menu.type("rec");
        menu.backspace();
        eq("re", menu.buffer());
        menu.move(1);
        eq("", menu.buffer());
        eq(0, menu.cursor());
    }

    // ---- results ------------------------------------------------------------------------------

    private static ActResult result(LaunchIntent.Mode mode, boolean cleared, long score, boolean emerald) {
        return new ActResult(mode, "s1", 0, 2, cleared, score, 40.0, 0.95, 12, 30, 1, 2, 9, "B", 1000, emerald,
                "a:100,b:100", "q:10");
    }

    public static void resultsMergeIntoTheProfile() {
        Profile p = new Profile();
        LaunchIntent hard = Handoff.intent(LaunchIntent.Mode.STORY, "s1", 0, 2, p, 1, 7);
        p.difficulty(LaunchIntent.Difficulty.HARD);
        hard = Handoff.intent(LaunchIntent.Mode.STORY, "s1", 0, 2, p, 1, 7);
        p.difficulty(LaunchIntent.Difficulty.EASY);
        var first = ResultMerge.merge(p, result(LaunchIntent.Mode.STORY, true, 8000, true), hard);
        eq(LaunchIntent.Difficulty.HARD, first.difficulty());
        eq(1, first.place());
        yes(first.firstClear(), "first clear");
        yes(first.newEmerald(), "emerald");
        yes(p.isCleared("s1", 0, 2), "progress saved");
        eq(1, p.emeraldCount("s1"));
        eq(200L, p.keyHits().total());
        eq(10L, p.keyMisses().get('q'));
        eq(40.0, Math.round(p.lifetimeWpm() * 10) / 10.0);
        var again = ResultMerge.merge(p, result(LaunchIntent.Mode.ARCADE, true, 3000, false), null);
        eq(LaunchIntent.Difficulty.EASY, again.difficulty());
        yes(!again.firstClear(), "arcade does not touch the story");
        var failed = ResultMerge.merge(p, result(LaunchIntent.Mode.STORY, false, 99999, false), hard);
        eq(0, failed.place());
        eq(8000L, failed.previousBest());
        eq(3L, p.actsPlayed());
        eq(2L, p.actsCleared());
    }

    public static void bossRushRaisesStarsAndEndless() {
        Profile p = new Profile();
        eq(1, ResultMerge.merge(p, result(LaunchIntent.Mode.BOSS_RUSH, true, 500, false), null).stars());
        eq(2, ResultMerge.merge(p, result(LaunchIntent.Mode.BOSS_RUSH, true, 500, false), null).stars());
        eq(2, ResultMerge.merge(p, result(LaunchIntent.Mode.BOSS_RUSH, false, 500, false), null).stars());
        var endless = ResultMerge.merge(p, result(LaunchIntent.Mode.ENDLESS, false, 4321, false), null);
        eq(1, endless.place());
        eq(Profile.endlessTable("s1", 0, 2, LaunchIntent.Difficulty.NORMAL), endless.table());
        yes(!p.isCleared("s1", 0, 2), "rush and endless never clear story stops");
    }

    public static void handoffsRoundTrip() {
        var pending = new Handoff.Pending(LaunchIntent.Mode.ARCADE, "s3k", 1, 1);
        eq(pending, Handoff.Pending.decode(pending.encode()));
        yes(Handoff.Pending.decode("game=s9\nmode=STORY") == null, "damaged pending ignored");
        yes(Handoff.Pending.decode(null) == null, "missing pending ignored");
        var rush = new Handoff.Rush("s2", List.of("0:1", "1:1", "2:1"), 0, 0);
        var after = Handoff.Rush.decode(rush.won(700).encode());
        eq(List.of("1:1", "2:1"), after.remaining());
        eq(1, after.nextZone());
        eq(700L, after.score());
        eq(1, after.won());
        Profile p = new Profile();
        p.capitals(true);
        p.noFail(true);
        p.voice(LaunchIntent.Voice.OFF);
        LaunchIntent intent = Handoff.intent(LaunchIntent.Mode.ENDLESS, "s2", 3, 1, p, 0, 99);
        eq(intent, LaunchIntent.decode(intent.encode()));
        yes(intent.encode().contains("voice=OFF"), "the voice choice travels in the intent");
        eq(LaunchIntent.Voice.MEGADRIVE, LaunchIntent.decode("mode=STORY\ngame=s1\n").voice());
        eq(LaunchIntent.Voice.MEGADRIVE, LaunchIntent.decode("mode=STORY\nvoice=LOUD\n").voice());
        eq(1, intent.bossStars());
        yes(intent.capitals() && intent.noFail(), "options travel with the intent");
        yes(Handoff.teamMatches("s2", "sonic", Handoff.sidekicks("s2")), "team constant matches itself");
        yes(!Handoff.teamMatches("s2", "knuckles", List.of()), "another main character relaunches");
        yes(Handoff.seed(1, 5) != Handoff.seed(2, 5), "seeds change per launch");
    }

    public static void attractDemosPlayThemselves() {
        Profile p = new Profile();
        p.difficulty(LaunchIntent.Difficulty.EXPERT);
        p.capitals(true);
        p.voice(LaunchIntent.Voice.CLEAN);
        LaunchIntent demo = Handoff.demoIntent(LaunchIntent.Mode.BOSS_RUSH, "s3k", 12, 0, p, 5);
        yes(demo.demo() && demo.noFail(), "a demo types for itself and never fails");
        eq(LaunchIntent.Difficulty.NORMAL, demo.difficulty());
        yes(!demo.capitals(), "demos keep the default typing rules");
        eq(LaunchIntent.Voice.CLEAN, demo.voice());
        eq(demo, LaunchIntent.decode(demo.encode()));
        yes(!Handoff.intent(LaunchIntent.Mode.ARCADE, "s3k", 12, 0, p, 1, 5).demo(), "real plays are not demos");
        eq(LaunchIntent.Mode.ARCADE, Handoff.demoMode(p.nextDemo()));
        eq(LaunchIntent.Mode.BOSS_RUSH, Handoff.demoMode(p.nextDemo()));
        eq(LaunchIntent.Mode.ARCADE, Handoff.demoMode(p.nextDemo()));
        eq(3L, Profile.decode(p.encode()).demos());
        eq(-1, Handoff.demoPick(42, 0));
        for (long seed = -50; seed < 50; seed++) yes(Handoff.demoPick(seed * 0x9E3779B97F4A7C15L, 3) >= 0
                && Handoff.demoPick(seed * 0x9E3779B97F4A7C15L, 3) < 3, "pick in range");
        var pending = new Handoff.Pending(LaunchIntent.Mode.ARCADE, "s3k", 12, 0, true);
        eq(pending, Handoff.Pending.decode(pending.encode()));
        yes(!Handoff.Pending.decode("mode=STORY\ngame=s1\nzone=0\nact=2\n").demo(), "older pending files are plays");
    }

    // ---- school -------------------------------------------------------------------------------

    private static DrillSession session(Drill drill, List<DrillSession.Item> items) {
        DrillSession s = new DrillSession(drill, items, 42);
        for (int i = 0; i < DrillSession.READY_ROWS; i++) s.tick();
        eq(DrillSession.Phase.PLAYING, s.phase());
        return s;
    }

    /** Types one character every {@code rows} rows, as a player would. */
    private static void typeAt(DrillSession s, String text, int rows) {
        for (char c : text.toCharArray()) {
            for (int i = 0; i < rows; i++) s.tick();
            s.type(String.valueOf(c));
        }
    }

    private static List<DrillSession.Item> words(String... words) {
        return java.util.Arrays.stream(words).map(w -> DrillSession.Item.word(w, false, false)).toList();
    }

    public static void suddenDeathEndsOnTheFirstError() {
        DrillSession s = session(Drill.SUDDEN_DEATH, words("ring", "jump", "spin", "dash"));
        s.type("ring");
        s.tick();
        s.type("jum");
        eq(DrillSession.Phase.PLAYING, s.phase());
        var events = s.type("x");
        eq(DrillSession.Signal.FINISHED, events.getLast().signal());
        eq(DrillSession.Phase.FINISHED, s.phase());
        yes(s.result().failed(), "sudden death failed");
        eq(1, s.result().completed());
        eq("E", s.result().grade());
        eq(1L, s.misses().get('p'));
    }

    public static void ringRushRunsSixtySeconds() {
        DrillSession s = session(Drill.RING_RUSH, words("ring", "ring", "ring", "ring", "ring", "ring"));
        s.type("ringring");
        for (int i = 0; i < 60 * 60 - 1; i++) s.tick();
        eq(DrillSession.Phase.PLAYING, s.phase());
        s.tick();
        eq(DrillSession.Phase.FINISHED, s.phase());
        eq(2, s.result().completed());
        eq("E", s.result().grade());
    }

    public static void reflexTimesLettersAndMisses() {
        List<DrillSession.Item> letters = List.of(DrillSession.Item.letter("K"), DrillSession.Item.letter("Q"));
        DrillSession s = session(Drill.REFLEX, letters);
        int guard = 0;
        while (s.current() == null && guard++ < 100) s.tick();
        yes(s.current() != null, "a letter appears after its pause");
        for (int i = 0; i < 15; i++) s.tick();
        s.type("k");
        eq(1, s.successes());
        guard = 0;
        while (s.current() == null && guard++ < 100) s.tick();
        for (int i = 0; i < DrillSession.REFLEX_WINDOW; i++) s.tick();
        eq(DrillSession.Phase.FINISHED, s.phase());
        eq(1, s.successes());
        eq(1L, s.misses().get('q'));
        eq(250.0, Math.round(s.result().reactionMs() * 10) / 10.0);
        eq("E", s.result().grade());
    }

    public static void bossTrainingRoundsAndQuiz() {
        List<DrillSession.Item> rounds = List.of(DrillSession.Item.practice("spin to win"),
                DrillSession.Item.talkBack("You fool"),
                DrillSession.Item.quiz("Who flies the Tornado?", List.of("Tails", "Knuckles", "Amy")));
        DrillSession s = session(Drill.BOSS_TRAINING, rounds);
        s.type("spin tx");
        eq(0, s.successes());
        eq(1, s.targets().size());
        eq(0, s.targets().getFirst().progress());
        s.type("spin to win");
        eq(1, s.successes());
        s.type("you fool");
        eq(2, s.successes());
        eq(3, s.targets().size());
        s.type("knuckles");
        eq(2, s.successes());
        eq(DrillSession.Phase.FINISHED, s.phase());
        eq("D", s.result().grade());
        DrillSession timeout = session(Drill.BOSS_TRAINING, List.of(DrillSession.Item.talkBack("too slow")));
        for (int i = 0; i < DrillSession.TALK_BACK_ROWS; i++) timeout.tick();
        eq(DrillSession.Phase.FINISHED, timeout.phase());
        eq(0, timeout.successes());
    }

    public static void gradesAndFinalExamUnlock() {
        Profile p = new Profile();
        yes(!School.examUnlocked(p), "exam starts locked");
        for (Drill d : Drill.values()) if (d.kind == Drill.DRILL) p.recordGrade(d.name(), "C");
        yes(School.examUnlocked(p), "C everywhere opens the exam");
        yes(!p.recordGrade("REFLEX", "D"), "a worse grade never replaces a better one");
        yes(p.recordGrade("REFLEX", "A"), "a better grade does");
        p = new Profile();
        for (Drill d : Drill.values()) if (d.kind == Drill.DRILL) p.recordGrade(d.name(), d == Drill.PHRASES ? "D" : "A");
        p = Profile.decode(p.encode());
        yes(!School.examUnlocked(p), "one D keeps it locked");
        eq(1, School.drillsBelowC(p));
        yes(Grades.atLeast("S", "C") && !Grades.atLeast(null, "E"), "grade order");

        // A perfect quick lesson grades A; the same text full of errors does not.
        DrillSession clean = session(Drill.HOME_ROW, words("asdf", "jkl", "flash"));
        typeAt(clean, "asdfjklflash", 6);
        eq("A", clean.result().grade());
        yes(clean.result().wpm() > 100, "one key every six rows is 120 WPM: " + clean.result().wpm());
        DrillSession sloppy = session(Drill.HOME_ROW, words("asdf", "jkl", "flash"));
        typeAt(sloppy, "aqqqsdfjklflash", 6);
        eq("D", sloppy.result().grade());
        Profile school = new Profile();
        yes(School.record(school, clean), "a first grade is recorded");
        eq("A", school.grade("HOME_ROW"));
        eq(12L, school.keyHits().total());
        yes(school.lifeMinutes() > 0, "lessons add typing time");
    }

    public static void weakKeysFollowLifetimeMisses() {
        KeyTally hits = new KeyTally().addEncoded("a:100,e:100,t:100,p:10,m:20");
        KeyTally misses = new KeyTally().addEncoded("p:10,m:8,a:1");
        String weak = SchoolMaterial.weakKeys(hits, misses, 5);
        eq('p', weak.charAt(0));
        eq('m', weak.charAt(1));
        eq(5, weak.length());
        eq("qzxjk", SchoolMaterial.weakKeys(new KeyTally(), new KeyTally(), 5));
        Lexicon lexicon = new Lexicon().pool("common", "apple\npump\nmap\nsun\ntree\npaper\nlamp\nmop\n");
        var material = new SchoolMaterial(lexicon, "");
        var items = material.items(Drill.WEAK_KEYS, new java.util.SplittableRandom(3), hits, misses);
        eq(15, items.size());
        for (var item : items) yes(item.display().matches(".*[pm].*|.*[qzxjk].*"), "weak words use weak keys: " + item);
    }

    public static void lessonsUseTheirRows() {
        Lexicon lexicon = new Lexicon().pool("common", "flask\nsalad\nquote\npower\nzebra\nmax\nhello\nask\n");
        var material = new SchoolMaterial(lexicon, "[home]\nasdf\n[top]\ntype\n[numbers]\n1991\n"
                + "[tails.REFLEX]\nFast!\n");
        var random = new java.util.SplittableRandom(1);
        for (var item : material.items(Drill.HOME_ROW, random, new KeyTally(), new KeyTally())) {
            yes(item.display().matches("[asdfghjkl ]+"), "home row only: " + item.display());
        }
        for (var item : material.items(Drill.TOP_ROW, random, new KeyTally(), new KeyTally())) {
            yes(item.display().matches("[qwertyuiopasdfghjkl ]+"), "top row words: " + item.display());
        }
        for (var item : material.items(Drill.NUMBERS, random, new KeyTally(), new KeyTally())) {
            yes(item.display().matches(".*\\d.*"), "numbers: " + item.display());
        }
        eq(30, material.items(Drill.REFLEX, random, new KeyTally(), new KeyTally()).size());
        eq("Fast!", material.tails("REFLEX", 4));
        eq("", material.tails("MISSING", 0));
    }

    public static void textArtParses() {
        var art = TextArt.parse("# test\npalette\nK 000000\nW FFFFFF 128\nsprite dot\n.W\nKWK\n\nsprite two\nK\n");
        eq(3, art.get("dot").width());
        eq(2, art.get("dot").height());
        eq(0x80FFFFFF, art.get("dot").argb()[1]);
        eq(0, art.get("dot").argb()[2]);
        eq(1, art.get("two").width());
        try {
            TextArt.parse("palette\nK 000000\nsprite bad\nKZ\n");
            throw new AssertionError("unknown colour accepted");
        } catch (IllegalArgumentException expected) { }
        for (String file : List.of("hub/art.txt", "hub/logo.txt")) {
            yes(!TextArt.parse(read(file)).isEmpty(), file + " has sprites");
        }
    }

    /** A resource from the mod's source tree (run from the repository or the example's folder). */
    private static String read(String path) {
        for (String root : List.of("examples/typing-of-the-hedgehog/src/main/resources/", "src/main/resources/")) {
            java.nio.file.Path file = java.nio.file.Path.of(root + path);
            if (java.nio.file.Files.isRegularFile(file)) {
                try {
                    return java.nio.file.Files.readString(file);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        }
        throw new AssertionError("Cannot find " + path + " from " + java.nio.file.Path.of("").toAbsolutePath());
    }
}
