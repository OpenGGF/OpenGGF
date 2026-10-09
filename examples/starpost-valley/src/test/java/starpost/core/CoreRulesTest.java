package starpost.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The farm's rules without a ROM: the clock, growth, harvests, inventory and saves. */
class CoreRulesTest {
    private final Catalog catalog = new Catalog();

    @Test
    void aDayIsFourteenMinutesAndEndsAtTwo() {
        Calendar cal = new Calendar();
        int ticks = 0;
        while (!cal.overtime()) {
            cal.tick();
            ticks++;
        }
        assertEquals(14 * 60 * 60, ticks, "06:00 to 02:00 at ten minutes per seven seconds");
        assertEquals("2:00AM", cal.clock());
        cal.nextDay();
        assertEquals("6:00AM", cal.clock());
        assertEquals(2, cal.day());
    }

    @Test
    void seasonsRollOverAfterTwentyEightDays() {
        Calendar cal = new Calendar();
        for (int i = 0; i < Calendar.DAYS_PER_SEASON * 4; i++) {
            cal.nextDay();
        }
        assertEquals(2, cal.year());
        assertEquals(Calendar.SPRING, cal.season());
        assertEquals(1, cal.day());
    }

    @Test
    void aWateredCropRipensOnItsLastDayAndOnlyWithWater() {
        Game game = new Game(catalog, 7);
        Plot plot = game.farm.plot(0, 0);
        plot.tilled = true;
        plot.crop = "ring_radish";
        CropDef radish = catalog.crop("ring_radish");
        for (int day = 0; day < radish.days(); day++) {
            assertFalse(game.farm.ripe(catalog, plot), "not ripe before day " + radish.days());
            plot.watered = true;
            game.sleep(false);
        }
        assertTrue(game.farm.ripe(catalog, plot));
        assertEquals(4, radish.stage(plot.age), "ripe picture");
        game.farm.harvest(catalog, plot);
        assertNull(plot.crop, "a radish is used up");
        assertTrue(plot.tilled, "the soil stays tilled");
    }

    @Test
    void dryDaysDoNotGrowAndRegrowingCropsComeBack() {
        Game game = new Game(catalog, 7);
        Plot plot = game.farm.plot(1, 1);
        plot.tilled = true;
        plot.crop = "sunflower";
        game.sleep(false);
        assertEquals(0, plot.age, "unwatered: no growth");
        CropDef sunflower = catalog.crop("sunflower");
        plot.age = sunflower.days();
        game.farm.harvest(catalog, plot);
        assertEquals("sunflower", plot.crop, "regrows");
        assertEquals(sunflower.days() - sunflower.regrow(), plot.age);
    }

    @Test
    void outOfSeasonCropsDie() {
        Game game = new Game(catalog, 7);
        game.calendar.set(1, Calendar.SPRING, Calendar.DAYS_PER_SEASON, Calendar.DAY_START);
        Plot plot = game.farm.plot(0, 2);
        plot.tilled = true;
        plot.crop = "ring_radish";
        game.sleep(false);
        assertEquals(Calendar.SUMMER, game.calendar.season());
        assertTrue(plot.dead);
    }

    @Test
    void shippingPaysOvernight() {
        Game game = new Game(catalog, 7);
        int before = game.rings;
        game.ship("ring_radish", 10);
        assertEquals(10 * catalog.item("ring_radish").price(), game.shippingValue());
        int paid = game.sleep(false);
        assertEquals(before + paid, game.rings);
        assertTrue(game.shipping.isEmpty());
    }

    @Test
    void inventoryStacksFillsAndRefusesWhenFull() {
        Inventory inv = new Inventory();
        Item seeds = catalog.item("ring_radish_seeds");
        assertEquals(0, inv.add(seeds, 1500));
        assertEquals(1500, inv.total(seeds.id()));
        assertEquals(2, java.util.stream.IntStream.range(0, inv.size()).filter(i -> inv.id(i) != null).count());
        for (int i = 0; i < inv.size(); i++) {
            inv.set(i, "fibre", Inventory.MAX_STACK);
        }
        assertFalse(inv.fits(seeds, 1));
        assertEquals(1, inv.add(seeds, 1));
    }

    @Test
    void momentumIsSpentOnlyWhenThereIsEnough() {
        Game game = new Game(catalog, 7);
        game.momentum = 3;
        assertFalse(game.spend(4));
        assertEquals(3, game.momentum);
        assertTrue(game.spend(3));
        assertEquals(0, game.momentum);
    }

    @Test
    void savesRoundTripAndRejectDamage() {
        Game game = Game.fresh(catalog, 99, "knuckles");
        game.calendar.set(1, Calendar.SUMMER, 12, Calendar.DAY_START);
        game.rings = 1234;
        game.inventory.add(catalog.item("emerald_melon_seeds"), 7);
        Plot plot = game.farm.plot(2, 3);
        plot.cover = Plot.GRASS;
        plot.tilled = true;
        plot.crop = "emerald_melon";
        plot.age = 5;
        game.ship("palm_wood", 3);
        game.flags.add("met_tails");
        String text = SaveCodec.encode(game);
        Game loaded = SaveCodec.decode(catalog, text);
        assertNotNull(loaded);
        assertEquals(text, SaveCodec.encode(loaded), "a loaded save writes back identically");
        assertEquals("knuckles", loaded.farmer);
        assertEquals(5, loaded.farm.plot(2, 3).age);
        assertEquals(game.farm.raw(4, 50).cover, loaded.farm.raw(4, 50).cover, "unopened land keeps its debris");
        assertNull(SaveCodec.decode(catalog, "garbage"));
        assertNull(SaveCodec.decode(catalog, text.replace("version=1", "version=9")));
        Game clamped = SaveCodec.decode(catalog, text.replace("rings=1234", "rings=-5").replace("emerald_melon,5", "no_such_crop,5"));
        assertNotNull(clamped);
        assertEquals(0, clamped.rings);
        assertNull(clamped.farm.plot(2, 3).crop, "unknown crops are dropped");
    }
}
