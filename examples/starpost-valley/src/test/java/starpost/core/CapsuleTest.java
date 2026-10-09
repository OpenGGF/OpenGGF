package starpost.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Great Capsule: bundles fill from the inventory, chambers reward once, saves validate. */
class CapsuleTest {
    private final Catalog catalog = new Catalog();

    private Capsule.Chamber chamber(Capsule capsule, String id) {
        return capsule.chambers(catalog).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void bundlesWithUnknownItemsAreLeftOut() {
        Capsule capsule = new Capsule();
        for (Capsule.Chamber c : capsule.chambers(catalog)) {
            for (Capsule.Bundle b : c.bundles()) {
                assertTrue(b.wants().keySet().stream().allMatch(catalog::hasItem), b.id());
            }
        }
        assertTrue(capsule.chambers(catalog).stream().anyMatch(c -> c.id().equals("pantry")));
    }

    @Test
    void thePantryFillsFromTheInventoryAndRewardsOnce() {
        Game game = new Game(catalog, 1);
        Capsule capsule = new Capsule();
        game.sections.add(capsule);
        Capsule.Chamber pantry = chamber(capsule, "pantry");
        for (Capsule.Bundle b : pantry.bundles()) {
            for (Map.Entry<String, Integer> want : b.wants().entrySet()) {
                game.inventory.add(catalog.item(want.getKey()), want.getValue() + 1);
            }
        }
        for (Capsule.Bundle b : pantry.bundles()) {
            assertTrue(capsule.deliver(game, pantry, b) > 0);
            assertEquals(0, capsule.deliver(game, pantry, b), "a full bundle takes nothing more");
        }
        assertTrue(capsule.complete(pantry));
        assertTrue(game.flags.contains("capsule_garden"));
        assertEquals(1, game.inventory.total("ring_radish"), "only what was wanted was taken");
    }

    @Test
    void theVaultTakesEachSumWholeAndOpensTheFarm() {
        Game game = new Game(catalog, 1);
        Capsule capsule = new Capsule();
        Capsule.Chamber vault = chamber(capsule, "vault");
        game.rings = 2000;
        assertEquals(0, capsule.deliver(game, vault, vault.bundles().get(0)), "not enough for the first sum");
        assertEquals(2000, game.rings);
        game.rings = 42500;
        for (Capsule.Bundle b : vault.bundles()) {
            capsule.deliver(game, vault, b);
        }
        assertEquals(0, game.rings);
        assertEquals(Farm.COLUMNS, game.farm.open());
    }

    @Test
    void gardenCropsIgnoreTheSeason() {
        Game game = new Game(catalog, 1);
        game.flags.add("capsule_garden");
        game.calendar.set(1, Calendar.SPRING, Calendar.DAYS_PER_SEASON, Calendar.DAY_START);
        Plot inside = game.farm.plot(0, 3), outside = game.farm.plot(3, 3);
        for (Plot p : List.of(inside, outside)) {
            p.cover = Plot.GRASS;
            p.tilled = true;
            p.crop = "ring_radish";
        }
        game.sleep(false);
        assertFalse(inside.dead, "the Capsule Garden keeps spring crops into summer");
        assertTrue(outside.dead);
    }

    @Test
    void savesKeepDeliveriesAndDropForgedKeys() {
        Capsule capsule = new Capsule();
        Game game = new Game(catalog, 1);
        Capsule.Chamber pantry = chamber(capsule, "pantry");
        game.inventory.add(catalog.item("ring_radish"), 1);
        capsule.deliver(game, pantry, pantry.bundles().get(0));
        Map<String, String> out = new HashMap<>();
        capsule.save(out);
        out.put("spring_crops.emerald_melon", "5");
        out.put("nonsense", "5");
        out.put("vault1.rings", "99999999");
        Capsule loaded = new Capsule();
        loaded.load(out, catalog);
        assertEquals(1, loaded.delivered(pantry.bundles().get(0), "ring_radish"));
        assertEquals(0, loaded.delivered(pantry.bundles().get(0), "emerald_melon"), "not one of the bundle's wants");
        assertEquals(0, loaded.delivered(chamber(loaded, "vault").bundles().get(0), "rings"), "out of range");
    }
}
