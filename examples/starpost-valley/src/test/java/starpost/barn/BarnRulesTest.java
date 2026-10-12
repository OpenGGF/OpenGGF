package starpost.barn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import starpost.core.Calendar;
import starpost.core.Capsule;
import starpost.core.Catalog;
import starpost.core.Farm;
import starpost.core.Game;
import starpost.core.Kind;
import starpost.core.Machine;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.core.Recipe;
import starpost.core.SaveCodec;
import starpost.core.SaveSection;
import starpost.core.Skills;

/** Animals, buildings, artisan machines and Flicky Roosts without a ROM, and their saves. */
class BarnRulesTest {
    private final Catalog catalog = new Catalog();

    /** A game in a season with the barn and skills installed, the farm's grass ploughed up (no grazing). */
    private Game game(int season) {
        Game game = new Game(catalog, 21);
        game.calendar.set(1, season, 3, Calendar.DAY_START);
        game.sections.add(new Skills());
        game.sections.add(new Barn());
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLUMNS; c++) {
                game.farm.raw(r, c).tilled = true;
                game.farm.raw(r, c).cover = Plot.GRASS;
            }
        }
        game.rings = 100000;
        return game;
    }

    private static Barn barn(Game game) {
        return game.section(Barn.class);
    }

    /** One night of the barn's work with today's weather fixed (Game.sleep would roll it). */
    private static void night(Game game, int weather) {
        game.calendar.nextDay();
        game.weather = weather;
        barn(game).nextDay(game);
    }

    @Test
    void theHatcheryBundlesNameRealGoods() {
        for (String id : List.of("cucky_egg", "pocky_fluff", "hill_truffle", "ice_egg")) {
            assertTrue(catalog.hasItem(id), id);
            assertEquals(Kind.ANIMAL_GOOD, catalog.item(id).kind(), id);
        }
        assertTrue(new Capsule().chambers(catalog).stream().anyMatch(c -> c.id().equals("hatchery") && c.bundles().size() == 2));
    }

    @Test
    void animalsNeedTheirHouseRoomAndRings() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        assertNotNull(barn.buy(game, "cucky"), "no coop yet");
        barn.coop = 1;
        for (int i = 0; i < 4; i++) {
            assertNull(barn.buy(game, i % 2 == 0 ? "cucky" : "pecky"));
        }
        assertNotNull(barn.buy(game, "cucky"), "a small coop holds four");
        barn.coop = 2;
        assertNull(barn.buy(game, "cucky"), "a big one holds eight");
        assertNotNull(barn.buy(game, "pocky"), "no pen yet");
        assertNull(barn.buy(game, "rocky"), "Rocky lives in the farm pond");
        assertNull(barn.buy(game, "rocky"));
        assertNotNull(barn.buy(game, "rocky"), "the pond holds two");
        game.rings = 10;
        barn.pen = 1;
        assertEquals("NOT ENOUGH RINGS", barn.buy(game, "pocky"));
        assertEquals(7, barn.animals.size());
        assertEquals(7, barn.animals.stream().map(a -> a.name).distinct().count(), "every name its own");
    }

    @Test
    void aFedCuckyLaysDailyAndAHungryOneDoesNot() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        barn.coop = 1;
        barn.buy(game, "cucky");
        Animal cucky = barn.animals.get(0);
        cucky.affection = Animal.MAX_AFFECTION;
        barn.coopFeed = 30;
        for (int day = 0; day < 6; day++) {
            night(game, Game.RAIN);
        }
        int laid = barn.coopGoods.getOrDefault("cucky_egg", 0);
        assertTrue(laid >= 4, "grown after two days, then an egg most days: " + laid);
        assertEquals(24, barn.coopFeed, "a fibre a day from the hopper (no grazing in the rain)");
        barn.coopFeed = 0;
        night(game, Game.RAIN);
        assertFalse(cucky.fed, "an empty hopper");
        int before = barn.coopGoods.getOrDefault("cucky_egg", 0);
        night(game, Game.RAIN);
        assertEquals(before, barn.coopGoods.getOrDefault("cucky_egg", 0), "nothing the day after going hungry");
        assertTrue(cucky.affection < Animal.MAX_AFFECTION - 20, "and it minded");
    }

    @Test
    void animalsGrazeOpenGrassOnDryDaysOutsideWinter() {
        Game game = game(Calendar.SUMMER);
        Barn barn = barn(game);
        barn.pen = 1;
        barn.buy(game, "pocky");
        barn.penFeed = 10;
        for (int c = 0; c < 8; c++) {
            game.farm.plot(0, c).tilled = false;
        }
        night(game, Game.SUN);
        assertTrue(barn.animals.get(0).fed);
        assertEquals(10, barn.penFeed, "grazed: the hopper untouched");
        night(game, Game.RAIN);
        assertEquals(9, barn.penFeed, "kept in by the rain");
        game.calendar.set(1, Calendar.WINTER, 3, Calendar.DAY_START);
        night(game, Game.SUN);
        assertEquals(8, barn.penFeed, "no grazing in winter");
        assertEquals(8, Barn.grassPlots(game), "the open grass grazers share, four plots each");
    }

    @Test
    void theHopperTakesFibreAndTheCoopsTakesSunflowers() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        game.inventory.add(catalog.item("fibre"), 5);
        game.inventory.add(catalog.item("sunflower"), 2);
        assertEquals(5, barn.fill(game, Animals.PEN));
        assertEquals(2, game.inventory.total("sunflower"), "the pen's hopper takes no sunflowers");
        game.inventory.add(catalog.item("fibre"), 4);
        assertEquals(4 + 2 * Barn.SUNFLOWER_FEED, barn.fill(game, Animals.COOP));
        assertEquals(0, game.inventory.total("sunflower"));
        barn.coopFeed = Barn.MAX_FEED;
        game.inventory.add(catalog.item("fibre"), 3);
        assertEquals(0, barn.fill(game, Animals.COOP), "a full hopper");
    }

    @Test
    void pockiesFluffEveryThreeDaysAndShepherdsEveryTwo() {
        assertEquals(3, fluffDays(false));
        assertEquals(2, fluffDays(true));
    }

    private int fluffDays(boolean shepherd) {
        Game game = game(Calendar.SPRING);
        if (shepherd) {
            game.section(Skills.class).announce(Skills.FARMING, 10, "shepherd");
        }
        Barn barn = barn(game);
        barn.pen = 1;
        barn.buy(game, "pocky");
        Animal pocky = barn.animals.get(0);
        pocky.age = 10;
        pocky.since = 0;
        pocky.affection = Animal.MAX_AFFECTION;
        barn.penFeed = 100;
        int first = -1;
        for (int day = 1; day <= 12; day++) {
            int before = barn.penGoods.getOrDefault("pocky_fluff", 0);
            night(game, Game.RAIN);
            if (barn.penGoods.getOrDefault("pocky_fluff", 0) > before) {
                if (first >= 0) {
                    return day - first;
                }
                first = day;
            }
        }
        return -1;
    }

    @Test
    void peckiesLayOnlyInWinterAndPickiesDigOnlyOnDryDaysOutsideIt() {
        Game game = game(Calendar.WINTER);
        Barn barn = barn(game);
        barn.coop = 1;
        barn.pen = 1;
        barn.buy(game, "pecky");
        barn.buy(game, "picky");
        for (Animal a : barn.animals) {
            a.age = 10;
            a.affection = Animal.MAX_AFFECTION;
        }
        barn.coopFeed = 50;
        barn.penFeed = 50;
        for (int r = 0; r < Farm.ROWS; r++) {
            game.farm.plot(r, 0).tilled = false;     // grass for truffles
        }
        for (int d = 0; d < 6; d++) {
            night(game, Game.SUN);
        }
        assertTrue(barn.coopGoods.getOrDefault("ice_egg", 0) >= 2, "winter's eggs");
        assertTrue(barn.truffles.isEmpty(), "no truffles in winter");
        game.calendar.set(1, Calendar.FALL, 3, Calendar.DAY_START);
        barn.coopGoods.clear();
        for (int d = 0; d < 4; d++) {
            night(game, Game.RAIN);
        }
        assertTrue(barn.truffles.isEmpty(), "nor in the rain");
        assertEquals(0, barn.coopGoods.getOrDefault("ice_egg", 0), "no Ice Eggs outside winter");
        for (int d = 0; d < 4; d++) {
            night(game, Game.SUN);
        }
        assertFalse(barn.truffles.isEmpty(), "dug up on dry days");
        for (String spot : barn.truffles) {
            assertTrue(spot.endsWith(".0"), "only in open grass: " + spot);
        }
        String spot = barn.truffles.iterator().next();
        String[] rc = spot.split("\\.");
        assertTrue(barn.pickTruffle(game, Integer.parseInt(rc[0]), Integer.parseInt(rc[1])));
        assertEquals(1, game.inventory.total("hill_truffle"));
        assertFalse(barn.truffles.contains(spot));
    }

    @Test
    void rockyFishesThePondAndHandsItOver() {
        Game game = game(Calendar.SUMMER);
        Barn barn = barn(game);
        barn.buy(game, "rocky");
        Animal rocky = barn.animals.get(0);
        rocky.age = 10;
        rocky.affection = Animal.MAX_AFFECTION;
        for (int d = 0; d < 3 && rocky.holding == null; d++) {
            night(game, Game.SUN);
        }
        assertNotNull(rocky.holding, "a catch by now");
        assertEquals(Kind.FISH, catalog.item(rocky.holding).kind());
        assertTrue(new starpost.fishing.FishTable().get(rocky.holding).spots() % 2 == 1, "a pond fish");
        assertTrue(rocky.fed, "he feeds himself");
        String id = barn.takeCatch(game, rocky);
        assertEquals(1, game.inventory.total(id));
        assertNull(rocky.holding);
        assertNull(barn.takeCatch(game, rocky));
    }

    @Test
    void pettingRaisesAffectionOnceADayAndNeglectLowersIt() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        barn.coop = 1;
        barn.buy(game, "cucky");
        Animal a = barn.animals.get(0);
        a.affection = 500;
        assertTrue(barn.pet(game, a));
        assertEquals(515, a.affection);
        assertFalse(barn.pet(game, a), "once a day");
        barn.coopFeed = 10;
        night(game, Game.RAIN);
        assertEquals(515, a.affection, "petted and fed: steady");
        assertFalse(a.petted, "a new day");
        night(game, Game.RAIN);
        assertEquals(511, a.affection, "no pet: a little less");
        game.section(Skills.class).announce(Skills.FARMING, 10, "cuddler");
        barn.pet(game, a);
        assertEquals(541, a.affection, "a Cuddler's pet counts double");
        barn.coop = 2;
        night(game, Game.RAIN);
        assertEquals(544, a.affection, "a big coop is cosier");
        assertEquals(2, a.hearts());
    }

    @Test
    void goodsCollectIntoTheMonitors() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        barn.coopGoods.put("cucky_egg", 3);
        assertEquals(3, barn.collect(game, Animals.COOP));
        assertEquals(3, game.inventory.total("cucky_egg"));
        assertTrue(barn.coopGoods.isEmpty());
    }

    // ------------------------------------------------------------------ machines

    @Test
    void theMachinesTakeTheirOwnInputsAndWorkOvernight() {
        Game game = game(Calendar.SPRING);
        game.farm.plot(1, 1).object = Artisan.JAR;
        game.inventory.add(catalog.item("ring_radish"), 2);
        game.inventory.add(catalog.item("palm_wood"), 2);
        assertEquals(Artisan.wants(Artisan.JAR), Artisan.use(game, 1, 1, Artisan.JAR, "palm_wood"));
        assertNull(game.farm.machine(1, 1));
        assertTrue(Artisan.use(game, 1, 1, Artisan.JAR, "ring_radish").startsWith("LOADED"));
        assertEquals(1, game.inventory.total("ring_radish"));
        Machine work = game.farm.machine(1, 1);
        assertEquals("jar_ring_radish", work.output());
        assertEquals(catalog.item("ring_radish").price() * 2 + 50, catalog.item("jar_ring_radish").price());
        assertTrue(Artisan.use(game, 1, 1, Artisan.JAR, "ring_radish").contains("3 DAYS"), "working: no second load");
        assertEquals(1, game.inventory.total("ring_radish"));
        for (int d = 0; d < 2; d++) {
            game.calendar.nextDay();
            assertFalse(game.farm.machine(1, 1).ready(game.calendar));
        }
        game.calendar.nextDay();
        assertTrue(Artisan.use(game, 1, 1, Artisan.JAR, null).startsWith("GOT"));
        assertEquals(1, game.inventory.total("jar_ring_radish"));
        assertNull(game.farm.machine(1, 1));
        assertEquals(Kind.ARTISAN, catalog.item("jar_ring_radish").kind());
    }

    @Test
    void eachMachinesRecipes() {
        assertEquals(new Artisan.Job("spring_yard_fizz", 1, 2), Artisan.job(catalog, Artisan.KEG, "spring_yard_hops"));
        assertEquals(new Artisan.Job("fizz_marble_grape", 1, 5), Artisan.job(catalog, Artisan.KEG, "marble_grape"));
        assertNull(Artisan.job(catalog, Artisan.KEG, "ring_radish"), "not a fruit");
        assertEquals(new Artisan.Job("hill_cloth", 1, 1), Artisan.job(catalog, Artisan.LOOM, "pocky_fluff"));
        assertNull(Artisan.job(catalog, Artisan.LOOM, "fibre"));
        assertEquals(new Artisan.Job(Artisan.OIL, 1, 1), Artisan.job(catalog, Artisan.PRESS, "sunflower"));
        assertEquals(new Artisan.Job("truffle_oil", 1, 2), Artisan.job(catalog, Artisan.PRESS, "hill_truffle"));
        assertNotNull(Artisan.job(catalog, Artisan.JAR, "totem_leek"), "forage too");
        assertNull(Artisan.job(catalog, Artisan.JAR, "fishing_rod"));
        assertNull(Artisan.job(catalog, Artisan.JAR, "jar_ring_radish"), "a jar is not jarred again");
        for (String machine : List.of(Artisan.JAR, Artisan.KEG, Artisan.LOOM, Artisan.PRESS)) {
            assertEquals(PlaceableDef.Role.MACHINE, catalog.placeable(machine).role());
        }
    }

    @Test
    void sunflowerOilHurriesAMachineByADay() {
        Game game = game(Calendar.SPRING);
        game.farm.plot(0, 0).object = Artisan.KEG;
        game.inventory.add(catalog.item("marble_grape"), 1);
        game.inventory.add(catalog.item(Artisan.OIL), 9);
        Artisan.use(game, 0, 0, Artisan.KEG, "marble_grape");
        int ready = game.farm.machine(0, 0).readyDay();
        assertEquals("OILED: A DAY SOONER", Artisan.use(game, 0, 0, Artisan.KEG, Artisan.OIL));
        assertEquals(ready - 1, game.farm.machine(0, 0).readyDay());
        for (int i = 0; i < 6; i++) {
            Artisan.use(game, 0, 0, Artisan.KEG, Artisan.OIL);
        }
        assertEquals(game.calendar.dayNumber() + 1, game.farm.machine(0, 0).readyDay(), "never sooner than tomorrow");
        assertEquals(9 - 4, game.inventory.total(Artisan.OIL));
    }

    // ------------------------------------------------------------------ roosts

    @Test
    void aRoostPicksRipeCropsInReachOnItsRowIntoItsBasket() {
        Game game = game(Calendar.SPRING);
        game.farm.open(Farm.COLUMNS);
        int roost = 20;
        game.farm.plot(2, roost).object = BarnContent.ROOST;
        for (int c = roost - 8; c <= roost + 8; c++) {
            for (int r = 1; r <= 2; r++) {
                if (c != roost) {
                    Plot p = game.farm.plot(r, c);
                    p.crop = "ring_radish";
                    p.age = catalog.crop("ring_radish").days();
                }
            }
        }
        barn(game).nextDay(game);
        int reach = BarnContent.ROOST_REACH;
        assertEquals(2 * reach, game.farm.chest(2, roost, PlaceableDef.ROOST_BASKET).total("ring_radish"));
        for (int c = roost - 8; c <= roost + 8; c++) {
            if (c != roost) {
                assertEquals(Math.abs(c - roost) > reach, game.farm.plot(2, c).crop != null, "row 2 column " + c);
                assertNotNull(game.farm.plot(1, c).crop, "other rows are left alone");
            }
        }
        assertEquals(2 * reach, barn(game).harvested.size(), "the Flickies' morning trips");
        assertFalse(catalog.recipes().stream().filter(r -> r.product().equals(BarnContent.ROOST))
                .map(Recipe::unlock).findFirst().orElse("").isEmpty(), "the roost waits for its Capsule flag");
    }

    // ------------------------------------------------------------------ saves

    @Test
    void theBarnMachinesAndBasketsSurviveASave() {
        Game game = game(Calendar.SPRING);
        Barn barn = barn(game);
        barn.coop = 2;
        barn.pen = 1;
        barn.coopFeed = 33;
        barn.buy(game, "cucky");
        barn.buy(game, "rocky");
        barn.animals.get(1).holding = "ring_carp";
        barn.coopGoods.put("cucky_egg", 4);
        barn.truffles.add("3.7");
        game.farm.plot(1, 1).object = Artisan.LOOM;
        game.inventory.add(catalog.item("pocky_fluff"), 1);
        Artisan.use(game, 1, 1, Artisan.LOOM, "pocky_fluff");
        game.farm.plot(2, 9).object = BarnContent.ROOST;
        game.farm.chest(2, 9, PlaceableDef.ROOST_BASKET).add(catalog.item("sunflower"), 5);
        String text = SaveCodec.encode(game);
        Game loaded = SaveCodec.decode(catalog, text, List.<SaveSection>of(new Skills(), new Barn()));
        assertNotNull(loaded);
        assertEquals(text, SaveCodec.encode(loaded), "writes back identically");
        Barn back = loaded.section(Barn.class);
        assertEquals(2, back.coop);
        assertEquals(33, back.coopFeed);
        assertEquals("ring_carp", back.animals.get(1).holding);
        assertEquals(4, back.coopGoods.get("cucky_egg"));
        assertTrue(back.truffles.contains("3.7"));
        assertEquals("hill_cloth", loaded.farm.machine(1, 1).output());
        assertEquals(5, loaded.farm.chest(2, 9, PlaceableDef.ROOST_BASKET).total("sunflower"));
    }

    @Test
    void damagedBarnValuesAreClampedOrDroppedAndMalformedOnesRejectTheSave() {
        Map<String, String> in = new java.util.TreeMap<>();
        in.put("buildings", "7,-2");
        in.put("feed", "99999,5");
        in.put("animal.1", "cucky,NUG!GET?,5000,1,0,3,0,");
        in.put("animal.2", "dragon,SMAUG,10,1,0,3,0,");
        in.put("animal.3", "pocky,BUN,10,1,0,3,0,");
        in.put("animal.4", "rocky,FINN,-40,1,0,3,0,palm_wood");
        in.put("goods.coop.cucky_egg", "500");
        in.put("goods.coop.palm_wood", "5");
        in.put("truffle.9.9", "1");
        in.put("truffle.1.2", "1");
        Barn barn = new Barn();
        barn.load(in, catalog);
        assertEquals(2, barn.coop);
        assertEquals(0, barn.pen);
        assertEquals(Barn.MAX_FEED, barn.coopFeed);
        assertEquals(2, barn.animals.size(), "no dragons, and no Pocky without a pen");
        assertEquals("NUGGET", barn.animals.get(0).name);
        assertEquals(Animal.MAX_AFFECTION, barn.animals.get(0).affection);
        assertEquals(0, barn.animals.get(1).affection);
        assertNull(barn.animals.get(1).holding, "Rocky holds fish, not wood");
        assertEquals(Barn.MAX_GOODS, barn.coopGoods.get("cucky_egg"));
        assertFalse(barn.coopGoods.containsKey("palm_wood"));
        assertEquals(1, barn.truffles.size());
        Game game = game(Calendar.SPRING);
        String text = SaveCodec.encode(game).replace("s.barn.buildings=0,0", "s.barn.buildings=one,two");
        assertTrue(text.contains("buildings=one"));
        assertNull(SaveCodec.decode(catalog, text, List.<SaveSection>of(new Barn())), "a malformed number rejects the save");
        String forged = SaveCodec.encode(game) + "machine.0.0=ring_radish,jar_ring_radish,1,4\n";
        assertNull(SaveCodec.decode(catalog, forged, List.<SaveSection>of(new Barn())).farm.machine(0, 0),
                "work only loads into a placed machine");
    }
}
