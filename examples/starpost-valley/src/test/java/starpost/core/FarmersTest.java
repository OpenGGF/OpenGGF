package starpost.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FarmersTest {
    private final Catalog catalog = new Catalog();

    @Test
    void sonicsSpinIsTheStrongestAndTillsItsWholeRoll() {
        assertTrue(Farmers.dashSpeed(Farmers.SONIC) > Farmers.dashSpeed(Farmers.KNUCKLES));
        assertTrue(Farmers.dashSpeed(Farmers.KNUCKLES) > Farmers.dashSpeed(Farmers.TAILS));
        assertEquals(Integer.MAX_VALUE, Farmers.dashTills(Farmers.SONIC));
        assertEquals(3, Farmers.dashTills(Farmers.TAILS));
    }

    @Test
    void tailsWatersTwoPlotsAChargeAndOnlyKnucklesPunchesRocks() {
        assertEquals(2, Farmers.waterReach(Farmers.TAILS));
        assertEquals(1, Farmers.waterReach(Farmers.SONIC));
        assertTrue(Farmers.punchesRocks(Farmers.KNUCKLES));
        assertFalse(Farmers.punchesRocks(Farmers.SONIC));
        assertFalse(Farmers.punchesRocks(Farmers.TAILS));
    }

    @Test
    void knucklesIsTooHeavyForTheLoopsFullPayoff() {
        assertEquals(30, Farmers.lapBonus(Farmers.SONIC, true));
        assertEquals(5, Farmers.lapBonus(Farmers.TAILS, false));
        assertEquals(15, Farmers.lapBonus(Farmers.KNUCKLES, true));
        assertEquals(2, Farmers.lapBonus(Farmers.KNUCKLES, false));
    }

    @Test
    void onlyKnucklesDigsThingsUpAndEveryFindIsReal() {
        Game sonic = new Game(catalog, 7);
        sonic.farmer = Farmers.SONIC;
        for (int i = 0; i < 200; i++) {
            assertNull(Farmers.dig(sonic));
        }
        Game knuckles = new Game(catalog, 7);
        knuckles.farmer = Farmers.KNUCKLES;
        int finds = 0;
        for (int i = 0; i < 800; i++) {
            String found = Farmers.dig(knuckles);
            if (found == null) {
                continue;
            }
            finds++;
            if (found.startsWith("rings:")) {
                int n = Integer.parseInt(found.substring(6));
                assertTrue(n >= 5 && n <= 20, found);
            } else {
                assertTrue(catalog.hasItem(found), found);
            }
        }
        assertTrue(finds > 800 / Farmers.DIG_ODDS / 2 && finds < 800 / Farmers.DIG_ODDS * 2, "about one in eight: " + finds);
    }
}
