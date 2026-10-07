package sitarhero.story;

import sitarhero.model.Roster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Story/display contracts, using the external creator sources rather than copied fixtures. */
public final class StoryChecks {
    private StoryChecks() { }

    private static List<String> worlds(String game) {
        return switch (game) {
            case "s1" -> List.of("green-hill", "marble", "spring-yard", "labyrinth", "star-light", "scrap-brain", "finale", "encore");
            case "s2" -> List.of("emerald-hill", "chemical-plant", "aquatic-ruin", "casino-night", "hill-top", "mystic-cave", "oil-ocean",
                    "metropolis", "sky-chase", "wing-fortress", "death-egg", "finale", "encore");
            case "s3k" -> List.of("angel-island", "hydrocity", "marble-garden", "carnival-night", "icecap", "launch-base", "mushroom-hill",
                    "flying-battery", "sandopolis", "lava-reef", "sky-sanctuary", "death-egg", "finale", "encore");
            default -> throw new IllegalArgumentException(game);
        };
    }

    private static List<String> ids(String game) {
        var ids = new ArrayList<String>();
        for (String world : worlds(game)) ids.add(game + "-" + world + "-intro");
        ids.add(game + "-outro");
        return ids;
    }

    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void invalid(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid content accepted");
    }
    private static void display(String text, int max) {
        yes(!text.isBlank() && text.length() <= max, "Display length: " + text);
        yes(text.chars().allMatch(c -> c >= 32 && c <= 126), "Unsupported glyph: " + text);
        // Production cards wrap words to three lines of 50 glyphs. Long individual words
        // or uneven word boundaries must not turn an otherwise <=150-character line into four.
        int rows = 1, width = 0;
        for (String word : text.split(" ")) {
            yes(word.length() <= 50, "Unwrappable word: " + word);
            if (width == 0) width = word.length();
            else if (width + 1 + word.length() <= 50) width += 1 + word.length();
            else { rows++; width = word.length(); }
        }
        yes(rows <= 3, "More than three card lines: " + text);
    }

    public static void allScriptsForEveryRomSubset() {
        int count = 0;
        for (String game : List.of("s1", "s2", "s3k")) count += ids(game).size();
        eq(38, count);
        int scenes = 0;
        for (int bits = 1; bits < 8; bits++) {
            var games = new ArrayList<String>();
            if ((bits & 1) != 0) games.add("s1");
            if ((bits & 2) != 0) games.add("s2");
            if ((bits & 4) != 0) games.add("s3k");
            for (String game : games) for (String id : ids(game)) {
                var base = CareerStory.scene(id, null, games);
                eq(id, base.id());
                yes(base.lines().size() >= 4 && base.lines().size() <= 8, id);
                for (Roster performer : Roster.availablePerformers(games)) {
                    var scene = CareerStory.scene(id, performer, games);
                    eq(id, scene.id());
                    eq(scene, CareerStory.scene(id, performer, List.copyOf(games)));
                    display(scene.title(), 50);
                    yes(scene.lines().size() >= 4 && scene.lines().size() <= 8, id + " line count");
                    for (var line : scene.lines()) {
                        display(line.text(), 150);
                        yes(line.speaker() == null ? line.action() : line.speaker().available(games), id + " missing donor");
                    }
                    scenes++;
                }
            }
        }
        eq(869, scenes);
    }

    public static void performerVariationAndGuestDonors() {
        var all = List.of("s1", "s2", "s3k");
        for (String game : all) for (String id : ids(game)) {
            var baseline = CareerStory.scene(id, null, all);
            var variants = new HashSet<CareerStory.Scene>();
            for (Roster performer : Roster.values()) {
                var scene = CareerStory.scene(id, performer, all);
                yes(!scene.equals(baseline), id + " missing performer staging: " + performer);
                yes(scene.lines().stream().anyMatch(line -> line.speaker() == performer), id + " missing selected actor");
                variants.add(scene);
            }
            eq(7, variants.size());
        }
        // S2's robot can be a guest on an S1 stage, but never materialises from an S1-only installation.
        String id = "s1-green-hill-intro";
        var guest = CareerStory.scene(id, Roster.SILVER_SONIC, List.of("s1", "s2"));
        yes(guest.lines().stream().anyMatch(line -> line.speaker() == Roster.SILVER_SONIC), "Missing available guest");
        eq(CareerStory.scene(id, null, List.of("s1")), CareerStory.scene(id, Roster.SILVER_SONIC, List.of("s1")));
        eq(CareerStory.scene(id, null, List.of("s1")), CareerStory.scene(id, Roster.TAILS, List.of("s1")));
    }

    public static void unknownAndUnavailableScenes() {
        for (String id : List.of("", "green-hill-intro", "s1-green-hill", "s1-outro-intro", "s3k-hidden-palace-intro", "s1-finale-outro"))
            invalid(() -> CareerStory.scene(id, Roster.SONIC, List.of("s1", "s2", "s3k")));
        for (String game : List.of("s1", "s2", "s3k")) for (String id : ids(game)) {
            invalid(() -> CareerStory.scene(id, Roster.SONIC, List.of()));
            var wrong = game.equals("s1") ? List.of("s2", "s3k") : List.of("s1");
            invalid(() -> CareerStory.scene(id, Roster.SONIC, wrong));
        }
    }

    public static void immutableValidatedContent() {
        var lines = new ArrayList<CareerStory.Line>();
        lines.add(new CareerStory.Line(null, "The house lights dim.", true));
        var scene = new CareerStory.Scene("test", "A quiet stage", lines);
        lines.clear(); eq(1, scene.lines().size());
        try { scene.lines().clear(); throw new AssertionError("Mutable scene"); }
        catch (UnsupportedOperationException expected) { }
        invalid(() -> new CareerStory.Line(Roster.SONIC, "", false));
        invalid(() -> new CareerStory.Line(Roster.SONIC, "bad\nline", false));
        invalid(() -> new CareerStory.Line(Roster.SONIC, "\u00e9", false));
        invalid(() -> new CareerStory.Line(Roster.SONIC, "x".repeat(151), false));
        invalid(() -> new CareerStory.Line(null, "A voice without a speaker.", false));
        invalid(() -> new CareerStory.Scene("", "Title", List.of()));
        invalid(() -> new CareerStory.Scene("test", "", List.of()));
    }

    private static String text(String id, Roster performer, String game) {
        return CareerStory.scene(id, performer, List.of(game)).lines().stream().map(CareerStory.Line::text)
                .reduce("", (a, b) -> a + " " + b);
    }

    public static void robotnikFinalesAndRelationshipPayoffs() {
        for (String game : List.of("s1", "s2", "s3k")) {
            var outro = CareerStory.scene(game + "-outro", Roster.ROBOTNIK, List.of(game));
            yes(outro.lines().stream().anyMatch(line -> line.speaker() == Roster.ROBOTNIK && !line.action()), "Silent playable Robotnik");
            String finale = text(game + "-finale-intro", Roster.ROBOTNIK, game);
            String end = text(game + "-outro", Roster.ROBOTNIK, game);
            yes(!finale.contains("bad playing") && !end.contains("bad playing"), "Success blamed on performer");
            yes(end.contains("show") || end.contains("concert"), "No successful show payoff");
            yes(end.contains("control") || end.contains("takeover"), "No separated scheme payoff");
        }
        String island = text("s3k-angel-island-intro", null, "s3k");
        yes(island.contains("security"), "No plausible island security pretext");
        String reef = text("s3k-lava-reef-intro", null, "s3k");
        yes(reef.contains("cable") && reef.contains("Emerald"), "Knuckles has no physical evidence");
        String ruins = text("s3k-marble-garden-intro", null, "s3k");
        yes(ruins.contains("It would make an excellent Master Emerald") && ruins.contains("I'm the venue"), "Missing earned original exchange");
        String sky = text("s2-sky-chase-intro", null, "s2");
        yes(sky.contains("Tails") || sky.contains("your lead"), "Sonic never follows Tails' decision");
    }

    public static void resultQuips() {
        var retries = new HashSet<String>(); var successes = new HashSet<String>();
        for (Roster performer : Roster.values()) {
            String retry = CareerStory.retryQuip(performer), success = CareerStory.successQuip(performer);
            display(retry, 62); display(success, 62);
            yes(!retry.equals(success), "Same outcome response: " + performer);
            retries.add(retry); successes.add(success);
        }
        eq(7, retries.size()); eq(7, successes.size());
        yes(CareerStory.successQuip(Roster.ROBOTNIK).contains("applause"), "Robotnik success dismissed");
    }

    public static void main(String[] args) {
        allScriptsForEveryRomSubset(); performerVariationAndGuestDonors(); unknownAndUnavailableScenes();
        immutableValidatedContent(); robotnikFinalesAndRelationshipPayoffs(); resultQuips();
        System.out.println("Story checks: 6 groups passed, all 38 scenes across every ROM subset and available performer.");
    }
}
