package starpost.fishing;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.state.SnapshotRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Capsule;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.Kind;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;
import starpost.core.Skills;

/** Fishing without a ROM: who bites where and when, the Bubble Bar's physics, the line, catches and the save. */
class FishingRulesTest {
    private final Catalog catalog = new Catalog();
    private final FishTable table = new FishTable();

    private static FishTable.Waters waters(int spot, int season, int minutes, int weather, boolean aurora, int depth,
            Set<String> flags) {
        return new FishTable.Waters(spot, season, minutes, weather, aurora, depth, flags, Set.of());
    }

    private Set<String> ids(FishTable.Waters w) {
        Set<String> out = new java.util.HashSet<>();
        for (FishDef def : table.candidates(w)) {
            out.add(def.id());
        }
        return out;
    }

    @Test
    void theTableHasSixteenFishAndFiveBadniksAndTheBundlesIds() {
        long fish = table.all().stream().filter(d -> !d.isBadnik()).count();
        long badniks = table.all().stream().filter(FishDef::isBadnik).count();
        assertEquals(16, fish);
        assertEquals(5, badniks);
        for (String id : List.of("bubble_bass", "loop_pike", "ring_carp", "chopper_shell", "jaws_fin")) {
            assertNotNull(table.get(id), id);
            assertTrue(catalog.hasItem(id), id + " is an item");
            assertEquals(Kind.FISH, catalog.item(id).kind(), id + " sells as a catch");
        }
        assertEquals(FishTable.JUNK, "robo_cola");
        assertTrue(catalog.hasItem(Fishing.HAT));
    }

    @Test
    void theReefChamberCanNowBeFilled() {
        Capsule capsule = new Capsule();
        assertTrue(capsule.chambers(catalog).stream().anyMatch(c -> c.id().equals("reef") && c.bundles().size() == 2),
                "both Reef bundles name known catches");
    }

    @Test
    void spotsSeasonsHoursAndWeatherDecideWhoBites() {
        Set<String> pond = ids(waters(FishDef.POND, Calendar.SPRING, 9 * 60, Game.SUN, false, 50, Set.of()));
        assertTrue(pond.containsAll(Set.of("spring_minnow", "bubble_bass", "ring_carp", "checker_perch")));
        assertFalse(pond.contains("loop_pike"), "the Loop Pike lives in the lake");
        assertFalse(pond.contains("snow_smelt"), "winter's fish");
        assertFalse(pond.contains("drizzle_trout"), "only in the rain");
        assertTrue(ids(waters(FishDef.POND, Calendar.SPRING, 9 * 60, Game.RAIN, false, 50, Set.of())).contains("drizzle_trout"));
        assertTrue(ids(waters(FishDef.POND, Calendar.WINTER, 9 * 60, Game.SNOW, false, 50, Set.of())).contains("snow_smelt"));
        Set<String> lakeDay = ids(waters(FishDef.LAKE, Calendar.SUMMER, 10 * 60, Game.SUN, false, 40, Set.of()));
        assertTrue(lakeDay.contains("loop_pike"));
        assertFalse(lakeDay.contains("starlight_eel"), "only after dark");
        assertTrue(ids(waters(FishDef.LAKE, Calendar.SUMMER, 21 * 60, Game.SUN, false, 40, Set.of())).contains("starlight_eel"));
        assertFalse(ids(waters(FishDef.LAKE, Calendar.SUMMER, 21 * 60, Game.SUN, false, 40, Set.of()))
                .contains("aurora_angelfish"));
        assertTrue(ids(waters(FishDef.LAKE, Calendar.SUMMER, 21 * 60, Game.SUN, true, 40, Set.of()))
                .contains("aurora_angelfish"), "the Emerald Aurora");
        assertFalse(lakeDay.contains("blastoid_cannon"), "storms only");
        assertTrue(ids(waters(FishDef.LAKE, Calendar.SUMMER, 10 * 60, Game.STORM, false, 60, Set.of()))
                .contains("blastoid_cannon"));
        assertTrue(ids(waters(FishDef.POND, Calendar.SUMMER, 10 * 60, Game.SWARM, false, 50, Set.of()))
                .contains("scrap_sucker"), "the morning after a swarm");
    }

    @Test
    void longCastsReachTheGarAndTheBadniks() {
        assertFalse(ids(waters(FishDef.LAKE, Calendar.FALL, 10 * 60, Game.SUN, false, 30, Set.of())).contains("labyrinth_gar"));
        assertTrue(ids(waters(FishDef.LAKE, Calendar.FALL, 10 * 60, Game.SUN, false, 80, Set.of())).contains("labyrinth_gar"));
        assertFalse(ids(waters(FishDef.LAKE, Calendar.FALL, 10 * 60, Game.SUN, false, 10, Set.of())).contains("jaws_fin"));
        int shallow = badnikBites(0), deep = badnikBites(100);
        assertTrue(deep > shallow * 3 / 2, "deep casts favour the badniks: " + shallow + " vs " + deep);
    }

    private int badnikBites(int depth) {
        SnapshotRandom rng = new SnapshotRandom(99);
        int n = 0;
        for (int i = 0; i < 4000; i++) {
            String id = table.choose(waters(FishDef.LAKE, Calendar.SUMMER, 9 * 60, Game.SUN, false, depth, Set.of()), rng);
            FishDef def = table.get(id);
            if (def != null && def.isBadnik()) {
                n++;
            }
        }
        return n;
    }

    @Test
    void theRedChopperNeedsBarnabysStoryAMorningAndBitesOnce() {
        FishTable.Waters noStory = waters(FishDef.LAKE, Calendar.SUMMER, 7 * 60, Game.SUN, false, 90, Set.of());
        assertFalse(ids(noStory).contains(Fishing.RED_CHOPPER));
        Set<String> story = Set.of("red_chopper_story");
        assertTrue(ids(waters(FishDef.LAKE, Calendar.SUMMER, 7 * 60, Game.SUN, false, 90, story)).contains(Fishing.RED_CHOPPER));
        assertFalse(ids(waters(FishDef.LAKE, Calendar.SUMMER, 15 * 60, Game.SUN, false, 90, story))
                .contains(Fishing.RED_CHOPPER), "mornings only");
        FishTable.Waters landed = new FishTable.Waters(FishDef.LAKE, Calendar.SUMMER, 7 * 60, Game.SUN, false, 90, story,
                Set.of(Fishing.RED_CHOPPER));
        assertFalse(ids(landed).contains(Fishing.RED_CHOPPER), "once a game");
    }

    @Test
    void junkComesUpNowAndThenAndAlwaysFromEmptyWater() {
        SnapshotRandom rng = new SnapshotRandom(5);
        int junk = 0;
        for (int i = 0; i < 4000; i++) {
            if (FishTable.JUNK.equals(table.choose(waters(FishDef.POND, Calendar.SPRING, 9 * 60, Game.SUN, false, 50,
                    Set.of()), rng))) {
                junk++;
            }
        }
        assertTrue(junk > 4000 * (FishTable.POND_JUNK - 3) / 100 && junk < 4000 * (FishTable.POND_JUNK + 3) / 100,
                "about " + FishTable.POND_JUNK + "%: " + junk);
        FishTable.Waters nothing = waters(0, Calendar.SPRING, 9 * 60, Game.SUN, false, 50, Set.of());
        assertEquals(FishTable.JUNK, table.choose(nothing, rng), "no spot, no fish");
    }

    // ------------------------------------------------------------------ the Bubble Bar

    /** A careful player: holds while the bubble is below the catch. */
    private static int play(BubbleBar bar, SnapshotRandom rng, boolean careful) {
        for (int t = 0; t < 60 * 60; t++) {
            boolean hold = careful && bar.bubble + bar.bubbleSpeed * 6 > bar.fish;
            int result = bar.step(hold, rng);
            if (result != BubbleBar.PLAYING) {
                return result;
            }
        }
        return BubbleBar.PLAYING;
    }

    @Test
    void aCarefulHandLandsEasyFishAndDoingNothingLosesThem() {
        int landed = 0, lazyLanded = 0;
        for (int seed = 0; seed < 40; seed++) {
            if (play(new BubbleBar(12, FishDef.SMOOTH, BubbleBar.bubbleHalf(0, false), new SnapshotRandom(seed)),
                    new SnapshotRandom(seed * 31L), true) == BubbleBar.CAUGHT) {
                landed++;
            }
            if (play(new BubbleBar(40, FishDef.MIXED, BubbleBar.bubbleHalf(0, false), new SnapshotRandom(seed)),
                    new SnapshotRandom(seed * 31L), false) == BubbleBar.CAUGHT) {
                lazyLanded++;
            }
        }
        assertTrue(landed >= 36, "a careful hand lands a minnow: " + landed + "/40");
        assertTrue(lazyLanded <= 4, "a fish swimming free of a sunk bubble gets away: " + lazyLanded + "/40");
    }

    @Test
    void harderCatchesEscapeMoreOften() {
        int easy = 0, hard = 0;
        for (int seed = 0; seed < 40; seed++) {
            if (play(new BubbleBar(15, FishDef.SMOOTH, 22, new SnapshotRandom(seed)), new SnapshotRandom(seed), true)
                    == BubbleBar.CAUGHT) {
                easy++;
            }
            if (play(new BubbleBar(90, FishDef.DART, 22, new SnapshotRandom(seed)), new SnapshotRandom(seed), true)
                    == BubbleBar.CAUGHT) {
                hard++;
            }
        }
        assertTrue(easy > hard, "easy " + easy + " vs hard " + hard);
    }

    @Test
    void tensionShrinksTheBubbleAndTheHookSetsFirst() {
        BubbleBar bar = new BubbleBar(50, FishDef.SMOOTH, 30, new SnapshotRandom(1));
        bar.bubble = BubbleBar.HEIGHT - 30;
        bar.fish = 10;
        float start = bar.progress;
        for (int t = 0; t < BubbleBar.GRACE - 1; t++) {
            bar.step(false, new SnapshotRandom(t));
            bar.fish = 10;
        }
        assertEquals(start, bar.progress, 1e-6, "no progress lost while the hook sets");
        assertTrue(bar.half() < 30, "the line pulled taut: " + bar.half());
        for (int t = 0; t < 200; t++) {
            bar.step(false, new SnapshotRandom(t));
        }
        assertTrue(bar.half() >= BubbleBar.MIN_HALF);
        assertFalse(bar.perfect);
    }

    @Test
    void theBubbleRisesWhileHeldAndSinksWhenLetGo() {
        BubbleBar bar = new BubbleBar(10, FishDef.SMOOTH, 24, new SnapshotRandom(2));
        float before = bar.bubble;
        for (int t = 0; t < 10; t++) {
            bar.step(true, new SnapshotRandom(t));
        }
        assertTrue(bar.bubble < before, "rises");
        before = bar.bubble;
        for (int t = 0; t < 30; t++) {
            bar.step(false, new SnapshotRandom(t));
        }
        assertTrue(bar.bubble > before, "sinks");
    }

    @Test
    void theChopperLeapsOnItsRomTiming() {
        BubbleBar bar = new BubbleBar(55, FishDef.CHOPPER, 24, new SnapshotRandom(3));
        int lastLanding = -1, period = -1;
        float bed = BubbleBar.HEIGHT - BubbleBar.FISH_HALF - 2;
        float top = bed;
        for (int t = 0; t < 400; t++) {
            bar.step(false, new SnapshotRandom(t));
            top = Math.min(top, bar.fish);
            if (Math.abs(bar.fish - bed) < 0.01f && t > 2) {
                if (lastLanding >= 0 && t - lastLanding > 1) {
                    period = t - lastLanding;
                }
                lastLanding = t;
            }
        }
        // Chop_ChgSpeed: up at $700, down by $18 a frame: 2 * 0x700 / 0x18 = 149.3 frames a leap.
        assertTrue(period >= 149 && period <= 151, "leap period " + period);
        assertTrue(top < BubbleBar.FISH_HALF + 10, "a full leap reaches the top: " + top);
    }

    @Test
    void theJawzChargesAtTwoPixelsAFrame() {
        BubbleBar bar = new BubbleBar(70, FishDef.JAWZ, 24, new SnapshotRandom(4));
        bar.step(false, new SnapshotRandom(0));
        float a = bar.fish;
        bar.step(false, new SnapshotRandom(1));
        assertEquals(2f, Math.abs(bar.fish - a), 1e-4, "Obj_Jawz: $200");
    }

    @Test
    void skillAndBarnabysLessonGrowTheBubble() {
        assertTrue(BubbleBar.bubbleHalf(5, false) > BubbleBar.bubbleHalf(0, false));
        assertTrue(BubbleBar.bubbleHalf(0, true) > BubbleBar.bubbleHalf(0, false));
        Game game = new Game(catalog, 1);
        game.sections.add(new Skills());
        float plain = Fishing.bubbleHalf(game);
        game.flags.add(Fishing.LESSON);
        assertTrue(Fishing.bubbleHalf(game) > plain);
    }

    // ------------------------------------------------------------------ the line

    @Test
    void theLineFliesWaitsBitesAndForgivesALateStrikeOnlyOnce() {
        Line line = new Line();
        SnapshotRandom rng = new SnapshotRandom(8);
        assertFalse(line.strike(), "nothing on the line yet");
        line.cast(0, 0, 50, 40, 50, 0);
        int splash = -1, bite = -1;
        for (int t = 0; t < 1000 && bite < 0; t++) {
            int event = line.step(rng);
            if (event == Line.SPLASH) {
                splash = t;
            }
            if (event == Line.BITE_NOW) {
                bite = t;
            }
        }
        assertEquals(Line.FLY_TICKS - 1, splash);
        assertTrue(bite - splash >= 45, "a wait before the bite");
        assertEquals(Line.BITE, line.state);
        for (int t = 0; t < Line.BITE_WINDOW - 1; t++) {
            assertEquals(Line.NONE, line.step(rng), "still biting after " + t);
        }
        assertEquals(Line.MISSED, line.step(rng), "too slow");
        assertEquals(Line.WAITING, line.state, "it waits for another bite");
        assertFalse(line.strike());
    }

    // ------------------------------------------------------------------ landing

    private Game game() {
        Game game = new Game(catalog, 11);
        game.sections.add(new Skills());
        game.sections.add(new FishingSection());
        return game;
    }

    @Test
    void catchesDoWhatTheySay() {
        Game game = game();
        int rings = game.rings;
        Fishing.land(game, "ring_carp", false);
        assertEquals(rings + 5, game.rings, "a ring in its mouth");
        game.waterCharges = 2;
        Fishing.land(game, "bubble_bass", false);
        assertEquals(7, game.waterCharges);
        game.waterCharges = game.waterCapacity;
        Fishing.land(game, "bubble_bass", false);
        assertEquals(game.waterCapacity, game.waterCharges, "never past the tank");
        Fishing.land(game, "scrap_sucker", false);
        assertEquals(1, game.inventory.total("scrap"));
        assertEquals(2, game.inventory.total("bubble_bass"));
        assertTrue(game.section(Skills.class).xp(Skills.FISHING) > 0);
    }

    @Test
    void aBadnikPopsFreesAnAnimalAndTheRedChopperReturnsTheHat() {
        Game game = game();
        int population = game.population;
        Fishing.Landed landed = Fishing.land(game, "chopper_shell", true);
        assertTrue(landed.freed());
        assertEquals(population + 1, game.population);
        assertEquals(1, game.inventory.total("chopper_shell"));
        Fishing.land(game, Fishing.RED_CHOPPER, false);
        assertEquals(1, game.inventory.total(Fishing.HAT));
        assertTrue(game.flags.contains(Fishing.RED_CHOPPER_CAUGHT));
        assertTrue(Fishing.section(game).landedOnce().contains(Fishing.RED_CHOPPER));
        Fishing.land(game, FishTable.JUNK, false);
        assertEquals(1, game.inventory.total("robo_cola"));
    }

    @Test
    void anglersAndReefHandsGetMoreForCatches() {
        Game game = game();
        Skills skills = game.section(Skills.class);
        int fish = game.sellPrice(catalog.item("loop_pike"));
        int shell = game.sellPrice(catalog.item("jaws_fin"));
        skills.announce(Skills.FISHING, 5, "angler");
        assertEquals(Math.round(fish * 1.25f), game.sellPrice(catalog.item("loop_pike")));
        skills.announce(Skills.FISHING, 10, "reef_hand");
        assertEquals(Math.round(catalog.item("jaws_fin").price() * 1.25f * 1.5f), game.sellPrice(catalog.item("jaws_fin")));
        assertEquals(Math.round(fish * 1.25f), game.sellPrice(catalog.item("loop_pike")), "reef hands: badniks only");
        assertTrue(shell < game.sellPrice(catalog.item("jaws_fin")));
    }

    @Test
    void contestPointsFavourHardCatchesAndBadniks() {
        assertEquals(0, Fishing.contestPoints(null), "junk scores nothing");
        int minnow = Fishing.contestPoints(table.get("spring_minnow"));
        int gar = Fishing.contestPoints(table.get("labyrinth_gar"));
        int jaws = Fishing.contestPoints(table.get("jaws_fin"));
        assertEquals(5 + 12 / 5, minnow);
        assertTrue(gar > minnow);
        assertEquals(5 + 62 / 5 + 10, jaws, "a badnik is worth ten more");
        int winter = 0;
        for (String id : List.of("snow_smelt", "ice_cap_char", "bubble_bass")) {
            winter += Fishing.contestPoints(table.get(id));
        }
        assertTrue(winter < 41 && winter + Fishing.contestPoints(table.get("snow_smelt")) > 40,
                "the Ice Cap record of 40 takes four good winter catches");
    }

    // ------------------------------------------------------------------ the save

    @Test
    void theCollectionSurvivesASaveAndDamageIsDropped() {
        Game game = game();
        Fishing.land(game, "loop_pike", false);
        Fishing.land(game, "loop_pike", false);
        Fishing.land(game, Fishing.RED_CHOPPER, false);
        String text = SaveCodec.encode(game);
        Game loaded = SaveCodec.decode(catalog, text, List.<SaveSection>of(new Skills(), new FishingSection()));
        assertNotNull(loaded);
        assertEquals(2, Fishing.section(loaded).landed("loop_pike"));
        assertEquals(text, SaveCodec.encode(loaded), "writes back identically");
        Map<String, String> forged = new HashMap<>();
        forged.put("landed.loop_pike", "9999999");
        forged.put("landed.not_a_fish", "4");
        forged.put("landed.red_chopper_shell", "7");
        forged.put("landed.ring_carp", "lots");
        forged.put("landed.jaws_fin", "-3");
        FishingSection section = new FishingSection();
        section.load(forged, catalog);
        assertEquals(FishingSection.MAX_COUNT, section.landed("loop_pike"));
        assertEquals(0, section.landed("not_a_fish"));
        assertEquals(1, section.landed(Fishing.RED_CHOPPER), "a once-only catch is one");
        assertEquals(0, section.landed("ring_carp"));
        assertEquals(0, section.landed("jaws_fin"));
        assertEquals(2, section.species());
    }
}
