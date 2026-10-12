package starpost.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Placed objects: sprinkler patterns, the row crawler, chests in saves, and the workshop's recipes. */
class ObjectsTest {
    private final Catalog catalog = new Catalog();

    private Game tilledField() {
        Game game = new Game(catalog, 3);
        game.calendar.set(1, Calendar.SPRING, 5, Calendar.DAY_START);
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.START_COLUMNS; c++) {
                game.farm.plot(r, c).tilled = true;
                game.farm.plot(r, c).cover = Plot.GRASS;
            }
        }
        return game;
    }

    @Test
    void aBuzzBomberWatersTheEightPlotsAroundIt() {
        Game game = tilledField();
        game.farm.plot(2, 10).object = "buzz_waterer";
        game.farm.nextDay(catalog, Calendar.SPRING, false, game.rng);
        int watered = 0;
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.START_COLUMNS; c++) {
                if (game.farm.plot(r, c).watered) {
                    watered++;
                    assertTrue(Math.abs(r - 2) <= 1 && Math.abs(c - 10) <= 1, "only neighbours: " + r + "," + c);
                }
            }
        }
        assertEquals(8, watered);
    }

    @Test
    void theCaterkillerCrawlerWatersItsWholeRowOnly() {
        Game game = tilledField();
        game.farm.plot(0, 12).object = "caterkiller_crawler";
        game.farm.nextDay(catalog, Calendar.SPRING, false, game.rng);
        for (int c = 0; c < Farm.START_COLUMNS; c++) {
            assertEquals(c != 12, game.farm.plot(0, c).watered, "row 0 column " + c);
            assertFalse(game.farm.plot(1, c).watered);
        }
    }

    @Test
    void sprinklersRestInWinter() {
        Game game = tilledField();
        game.farm.plot(2, 10).object = "buzz_waterer";
        game.farm.nextDay(catalog, Calendar.WINTER, false, game.rng);
        assertFalse(game.farm.plot(2, 11).watered);
    }

    @Test
    void chestsAndObjectsSurviveASave() {
        Game game = tilledField();
        game.farm.plot(3, 4).object = "item_monitor";
        game.farm.chest(3, 4, 24).add(catalog.item("palm_wood"), 40);
        Game loaded = SaveCodec.decode(catalog, SaveCodec.encode(game));
        assertNotNull(loaded);
        assertEquals("item_monitor", loaded.farm.plot(3, 4).object);
        assertEquals(40, loaded.farm.chest(3, 4, 24).total("palm_wood"));
        String forged = SaveCodec.encode(game).replace("chest.3.4.0=palm_wood", "chest.0.0.0=palm_wood");
        assertEquals(0, SaveCodec.decode(catalog, forged).farm.chests.getOrDefault("0.0", new Inventory()).total("palm_wood"),
                "contents only load into a placed monitor");
    }

    @Test
    void everyRecipeIsBuildableFromKnownItems() {
        assertFalse(catalog.recipes().isEmpty());
        for (Recipe recipe : catalog.recipes()) {
            assertTrue(catalog.hasItem(recipe.product()));
            assertNotNull(catalog.placeable(recipe.product()), recipe.product() + " is placeable");
        }
    }
}
