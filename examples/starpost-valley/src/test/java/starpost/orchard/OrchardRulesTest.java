package starpost.orchard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Farm;
import starpost.core.Farmers;
import starpost.core.Game;
import starpost.core.Kind;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.core.Recipe;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;
import starpost.core.Sneakers;
import starpost.ruins.RuinsRules;

/** The sneaker tiers, the dash they widen, the three trees' rules, the ring burst and the orchard's save. */
class OrchardRulesTest {
    private final Catalog catalog = new Catalog();

    private Game game(String farmer) {
        Game game = new Game(catalog, 11);
        game.farmer = farmer;
        game.calendar.set(1, Calendar.SPRING, 1, Calendar.DAY_START);
        game.sections.add(new Orchard());
        return game;
    }

    // ------------------------------------------------------------------ sneakers

    @Test
    void eachTierWidensTheDashByTheRowsTheDesignNames() {
        assertArrayEquals(new int[] {2}, Sneakers.rowsTilled(2, Sneakers.SNEAKERS));
        assertArrayEquals(new int[] {2, 3}, Sneakers.rowsTilled(2, Sneakers.POWER), "and the row in front");
        assertArrayEquals(new int[] {2, 1, 3}, Sneakers.rowsTilled(2, Sneakers.SPEED), "both neighbours");
        assertArrayEquals(new int[] {2, 1, 3}, Sneakers.rowsTilled(2, Sneakers.CHAOS));
        assertArrayEquals(new int[] {4}, Sneakers.rowsTilled(4, Sneakers.POWER), "nothing in front of the front row");
        assertArrayEquals(new int[] {0, 1}, Sneakers.rowsTilled(0, Sneakers.SPEED));
        assertFalse(Sneakers.runsOnWater(Sneakers.SPEED));
        assertTrue(Sneakers.runsOnWater(Sneakers.CHAOS));
    }

    @Test
    void theTierIsAStoryFlagThatSavesWithTheGame() {
        Game game = game(Farmers.SONIC);
        assertEquals(Sneakers.SNEAKERS, Sneakers.tier(game));
        Sneakers.grant(game, Sneakers.SPEED);
        assertEquals(Sneakers.SPEED, Sneakers.tier(game));
        assertTrue(game.flags.contains(Sneakers.flag(Sneakers.POWER)), "the tiers below come with it");
        Game back = SaveCodec.decode(catalog, SaveCodec.encode(game));
        assertNotNull(back);
        assertEquals(Sneakers.SPEED, Sneakers.tier(back));
    }

    @Test
    void theWorkshopLadderClimbsAndTheLastPairNeedsAnEmeraldShard() {
        assertTrue(Sneakers.price(Sneakers.POWER) < Sneakers.price(Sneakers.SPEED));
        assertTrue(Sneakers.price(Sneakers.SPEED) < Sneakers.price(Sneakers.CHAOS));
        assertEquals("emerald_shard", Sneakers.material(Sneakers.CHAOS));
        for (int t = Sneakers.SNEAKERS; t <= Sneakers.CHAOS; t++) {
            assertTrue(catalog.hasItem(Sneakers.item(t)), Sneakers.item(t));
            assertTrue(catalog.hasItem(Sneakers.material(t)) || t == Sneakers.SNEAKERS, Sneakers.material(t));
            assertEquals(Kind.TOOL, catalog.item(Sneakers.item(t)).kind());
        }
    }

    @Test
    void chaosSneakersHoldTheWaterOnlyWhileTheFarmerKeepsRunning() {
        assertTrue(Sneakers.sinks(Sneakers.SPEED, 0), "without them the pond is water");
        assertFalse(Sneakers.sinks(Sneakers.CHAOS, 0));
        assertFalse(Sneakers.sinks(Sneakers.CHAOS, Sneakers.SINK_GRACE));
        assertTrue(Sneakers.sinks(Sneakers.CHAOS, Sneakers.SINK_GRACE + 1));
        assertTrue(Sneakers.fastEnough(Sneakers.WATER_RUN_SPEED));
        assertTrue(Sneakers.fastEnough(-4));
        assertFalse(Sneakers.fastEnough(2.9f));
    }

    /** Rolls a dash along a row from column 6 to the field's open edge; returns the plots it tilled. */
    private static int dash(Game game, int row) {
        Sneakers.Dash dash = new Sneakers.Dash();
        int tilled = 0;
        for (int c = Farm.STARTER_PATCH; c < game.farm.open(); c++) {
            for (int step = 0; step < 4; step++) {          // several frames over each plot
                tilled += dash.over(game, row, c).length;
            }
        }
        return tilled;
    }

    private static Game cleared(Game game) {
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < game.farm.open(); c++) {
                game.farm.plot(r, c).cover = Plot.GRASS;
            }
        }
        game.momentum = game.maxMomentum = 400;
        return game;
    }

    @Test
    void sonicsDashTillsItsWholeRollAcrossEveryRowHisSneakersReach() {
        int columns = Farm.START_COLUMNS - Farm.STARTER_PATCH;
        Game plain = cleared(game(Farmers.SONIC));
        assertEquals(columns, dash(plain, 2));
        Game power = cleared(game(Farmers.SONIC));
        Sneakers.grant(power, Sneakers.POWER);
        assertEquals(columns * 2, dash(power, 2));
        assertTrue(power.farm.plot(3, 10).tilled && !power.farm.plot(1, 10).tilled, "the row in front, not behind");
        Game speed = cleared(game(Farmers.SONIC));
        Sneakers.grant(speed, Sneakers.SPEED);
        assertEquals(columns * 3, dash(speed, 2));
        assertEquals(400 - columns * 3, speed.momentum, "a point of Momentum a plot");
    }

    @Test
    void tailssThreePlotSpinBecomesThreeColumnsAsWideAsHisSneakers() {
        Game plain = cleared(game(Farmers.TAILS));
        assertEquals(3, dash(plain, 2));
        Game speed = cleared(game(Farmers.TAILS));
        Sneakers.grant(speed, Sneakers.SPEED);
        assertEquals(9, dash(speed, 2), "a 3x3 patch");
        Game edge = cleared(game(Farmers.TAILS));
        Sneakers.grant(edge, Sneakers.SPEED);
        assertEquals(6, dash(edge, 0), "the back row has one neighbour");
    }

    @Test
    void theDashRollsPastRocksStumpsAndPlacedThingsAndStopsWhenMomentumRunsOut() {
        Game game = cleared(game(Farmers.SONIC));
        Sneakers.grant(game, Sneakers.SPEED);
        game.farm.plot(1, 8).cover = Plot.ROCK;
        game.farm.plot(3, 8).cover = Plot.STUMP;
        game.farm.plot(2, 9).object = "buzz_waterer";
        Sneakers.Dash dash = new Sneakers.Dash();
        assertArrayEquals(new int[] {2}, dash.over(game, 2, 8));
        assertArrayEquals(new int[] {1, 3}, dash.over(game, 2, 9));
        game.momentum = 1;
        assertEquals(1, dash.over(game, 2, 10).length);
        assertEquals(0, dash.over(game, 2, 11).length);
    }

    @Test
    void knucklesTillsLikeSonicWithTheSameSneakers() {
        Game knuckles = cleared(game(Farmers.KNUCKLES));
        Sneakers.grant(knuckles, Sneakers.POWER);
        assertEquals((Farm.START_COLUMNS - Farm.STARTER_PATCH) * 2, dash(knuckles, 1));
    }

    // ------------------------------------------------------------------ trees

    private Orchard.Tree plant(Game game, String kind, int row, int column) {
        Plot plot = game.farm.plot(row, column);
        plot.cover = Plot.GRASS;
        plot.object = kind;
        return game.section(Orchard.class).tree(game, row, column);
    }

    /** Runs the orchard's night with the calendar at a date and the weather set. */
    private static void night(Game game, int season, int day, int weather) {
        game.calendar.set(1, season, day, Calendar.DAY_START);
        game.weather = weather;
        game.section(Orchard.class).nextDay(game);
    }

    @Test
    void saplingsArePlacedLikeFarmObjectsAndTailsBuildsThem() {
        for (String kind : List.of(Orchard.PALM, Orchard.RING_FRUIT, Orchard.CHAOS_CHERRY)) {
            assertEquals(Kind.PLACEABLE, catalog.item(kind).kind());
            assertEquals(PlaceableDef.Role.TREE, catalog.placeable(kind).role());
        }
        Map<String, Recipe> recipes = new LinkedHashMap<>();
        for (Recipe r : catalog.recipes()) {
            recipes.put(r.product(), r);
        }
        assertNull(recipes.get(Orchard.PALM).unlock());
        assertNull(recipes.get(Orchard.RING_FRUIT).unlock());
        assertEquals(Orchard.CHERRY_FLAG, recipes.get(Orchard.CHAOS_CHERRY).unlock(),
                "the Chaos Cherry's recipe is the museum's reward");
        assertEquals(Kind.FORAGE, catalog.item(Orchard.CHERRY).kind());
    }

    @Test
    void treesGrowEveryNightThroughThreeStagesWithoutWater() {
        Game game = game(Farmers.SONIC);
        Orchard.Tree palm = plant(game, Orchard.PALM, 1, 8);
        Orchard.Tree ring = plant(game, Orchard.RING_FRUIT, 2, 12);
        Orchard.Tree cherry = plant(game, Orchard.CHAOS_CHERRY, 3, 16);
        assertEquals(0, palm.stage());
        for (int night = 1; night <= 28; night++) {
            night(game, Calendar.WINTER, Math.min(28, night), Game.SNOW);
            assertEquals(night >= 14, palm.grown(), "palm after " + night);
            assertEquals(night >= 21, cherry.grown(), "cherry after " + night);
            assertEquals(night >= 28, ring.grown(), "ring fruit after " + night);
        }
        assertEquals(2, ring.stage());
        assertEquals(1, Orchard.stage(Orchard.RING_FRUIT, 14));
        assertEquals(0, Orchard.stage(Orchard.RING_FRUIT, 13));
    }

    private Orchard.Tree grown(Game game, String kind, int row, int column) {
        Orchard.Tree tree = plant(game, kind, row, column);
        tree.age = Orchard.days(kind);
        return tree;
    }

    @Test
    void thePalmBearsEveryOtherDayInSummerAndFallAndStormsShakeTwoMoreLoose() {
        Game game = game(Farmers.SONIC);
        Orchard.Tree palm = grown(game, Orchard.PALM, 1, 8);
        for (int day = 1; day <= 10; day++) {
            night(game, Calendar.SPRING, day, Game.SUN);
        }
        assertEquals(0, palm.fruit, "no coconuts in spring sun");
        night(game, Calendar.SPRING, 11, Game.STORM);
        assertEquals(Orchard.STORM_COCONUTS, palm.fruit, "a spring storm still shakes them loose");
        palm.fruit = 0;
        int bore = 0;
        for (int day = 1; day <= 10; day++) {
            int before = palm.fruit;
            night(game, Calendar.SUMMER, day, Game.SUN);
            bore += palm.fruit - before;
            palm.fruit = 0;
        }
        assertEquals(5, bore, "every other day");
        palm.fruit = 3;
        night(game, Calendar.FALL, 2, Game.STORM);
        assertEquals(5, palm.fruit, "storms fill it past the usual three");
        night(game, Calendar.FALL, 5, Game.SUN);               // a bearing day
        assertEquals(5, palm.fruit, "a calm day never takes any away");
        palm.fruit = 0;
        for (int day = 1; day <= 28; day++) {
            night(game, Calendar.WINTER, day, Game.SNOW);
        }
        assertEquals(0, palm.fruit, "nothing in winter");
    }

    @Test
    void theRingFruitTreePaysTenRingsADayFromSpringToFallAndHoldsThirty() {
        Game game = game(Farmers.SONIC);
        Orchard.Tree tree = grown(game, Orchard.RING_FRUIT, 2, 10);
        night(game, Calendar.SPRING, 2, Game.RAIN);
        assertEquals(10, tree.fruit);
        for (int day = 3; day <= 8; day++) {
            night(game, Calendar.SUMMER, day, Game.SUN);
        }
        assertEquals(30, tree.fruit, "three days' rings at most");
        Orchard orchard = game.section(Orchard.class);
        Orchard.Pick pick = orchard.pick(game, 2, 10);
        assertTrue(pick.rings());
        assertEquals(30, pick.count());
        assertEquals(0, tree.fruit);
        assertEquals(Game.START_RINGS, game.rings, "rings are caught from the burst, not paid by picking");
        night(game, Calendar.WINTER, 1, Game.SNOW);
        assertEquals(0, tree.fruit, "bare in winter");
    }

    @Test
    void theChaosCherryBearsInWinterAndMoreTheMoreAnimalsAreFree() {
        int[] totals = new int[2];
        int[] populations = {6, Game.MAX_POPULATION};
        for (int i = 0; i < 2; i++) {
            Game game = game(Farmers.SONIC);
            game.population = populations[i];
            Orchard.Tree tree = grown(game, Orchard.CHAOS_CHERRY, 2, 10);
            for (int day = 1; day <= 112; day++) {
                night(game, Calendar.WINTER, (day - 1) % 28 + 1, Game.SNOW);
                totals[i] += tree.fruit;
                tree.fruit = 0;
            }
        }
        assertEquals(112, totals[1], "a full valley: a cherry every morning, even in winter");
        assertTrue(totals[0] > 2 && totals[0] < 30, "six animals: about one morning in ten: " + totals[0]);
    }

    @Test
    void pickingKeepsWhatDoesNotFitOnTheTreeAndNothingIsPickedBeforeItGrows() {
        Game game = game(Farmers.SONIC);
        Orchard orchard = game.section(Orchard.class);
        Orchard.Tree young = plant(game, Orchard.PALM, 1, 8);
        young.fruit = 3;
        assertNull(orchard.pick(game, 1, 8), "a sapling has nothing to pick");
        Orchard.Tree palm = grown(game, Orchard.PALM, 1, 9);
        palm.fruit = 3;
        for (int i = 0; i < game.inventory.size(); i++) {
            game.inventory.set(i, "fibre", 999);
        }
        assertNull(orchard.pick(game, 1, 9));
        assertEquals(3, palm.fruit, "still on the tree");
        game.inventory.clear(5);
        Orchard.Pick pick = orchard.pick(game, 1, 9);
        assertEquals(Orchard.COCONUT, pick.item());
        assertEquals(3, pick.count());
        assertEquals(3, game.inventory.total(Orchard.COCONUT));
    }

    @Test
    void aTreeKnockedLooseIsForgottenAndANewSaplingStartsFromNothing() {
        Game game = game(Farmers.SONIC);
        Orchard orchard = game.section(Orchard.class);
        Orchard.Tree palm = grown(game, Orchard.PALM, 1, 8);
        palm.fruit = 2;
        game.farm.plot(1, 8).object = null;
        orchard.nextDay(game);
        assertNull(orchard.tree(game, 1, 8));
        assertTrue(orchard.standing(game).isEmpty());
        Orchard.Tree cherry = plant(game, Orchard.CHAOS_CHERRY, 1, 8);
        assertEquals(0, cherry.age);
        assertEquals(0, cherry.fruit);
    }

    @Test
    void theOrchardRoundTripsThroughTheSaveAndClampsDamagedValues() {
        Game game = game(Farmers.SONIC);
        Orchard.Tree palm = grown(game, Orchard.PALM, 1, 8);
        palm.fruit = 4;
        Orchard.Tree ring = plant(game, Orchard.RING_FRUIT, 3, 12);
        ring.age = 9;
        List<SaveSection> sections = List.of(new Orchard());
        Game back = SaveCodec.decode(catalog, SaveCodec.encode(game), sections);
        Orchard orchard = back.section(Orchard.class);
        assertEquals(Orchard.PALM, back.farm.plot(1, 8).object);
        assertEquals(14, orchard.tree(back, 1, 8).age);
        assertEquals(4, orchard.tree(back, 1, 8).fruit);
        assertEquals(9, orchard.tree(back, 3, 12).age);
        Orchard damaged = new Orchard();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("tree.1.8", "palm_sapling,999,999");
        keys.put("tree.9.8", "palm_sapling,1,0");
        keys.put("tree.2.2", "oak_sapling,1,0");
        damaged.load(keys, catalog);
        Map<String, String> out = new LinkedHashMap<>();
        damaged.save(out);
        assertEquals(Map.of("tree.1.8", "palm_sapling,14,5"), out);
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("tree.1.8", "palm_sapling,x,0");
        assertThrows(RuntimeException.class, () -> new Orchard().load(bad, catalog), "malformed numbers reject the save");
    }

    // ------------------------------------------------------------------ the ring burst

    @Test
    void shakenRingsFlyLikeAHitsScatteredRingsAndBlinkOutTogether() {
        RingBurst burst = new RingBurst(30, 300, 170, 44, 140, 208);
        assertEquals(30, burst.rings().size());
        boolean bounced = false;
        for (int t = 0; t < RuinsRules.LOST_RING_FRAMES - 1; t++) {
            float[] before = new float[burst.rings().size()];
            for (int i = 0; i < before.length; i++) {
                before[i] = burst.rings().get(i).height;
            }
            burst.step();
            for (int i = 0; i < burst.rings().size(); i++) {
                RingBurst.Ring r = burst.rings().get(i);
                assertTrue(r.height >= 0 && r.depth >= 140 && r.depth <= 208);
                bounced |= before[i] == 0 && r.height > 0;
            }
        }
        assertTrue(bounced, "they bounce off the field");
        assertFalse(burst.done());
        burst.step();
        assertTrue(burst.done(), "the shared 255-frame timer runs out for all of them at once");
    }

    @Test
    void ringsAreCaughtOnlyWithinReachAndTheLightningShieldPullsThemIn() {
        RingBurst far = new RingBurst(30, 300, 170, 44, 140, 208);
        RingBurst near = new RingBurst(30, 300, 170, 44, 140, 208);
        RingBurst pulled = new RingBurst(30, 300, 170, 44, 140, 208);
        int farCaught = 0, nearCaught = 0, pulledCaught = 0;
        for (int t = 0; t < 120; t++) {
            far.step();
            near.step();
            pulled.step();
            farCaught += far.collect(600, 170, 0, false);
            nearCaught += near.collect(300, 170, 0, false);
            pulledCaught += pulled.collect(300, 170, 0, true);
        }
        assertEquals(0, farCaught);
        assertTrue(nearCaught > 0 && nearCaught < 30, "standing still under the tree catches some: " + nearCaught);
        assertTrue(pulledCaught > nearCaught, "the Lightning Shield brings more in: " + pulledCaught);
    }
}
