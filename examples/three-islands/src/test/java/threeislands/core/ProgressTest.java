package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressTest {
    @Test
    void zonesOpenInOrderWithinEachIsland() {
        Progress progress = new Progress(1);
        assertTrue(progress.isOpen(Zone.GREEN_HILL));
        assertFalse(progress.isOpen(Zone.STAR_LIGHT));
        progress.clear(Zone.GREEN_HILL);
        assertTrue(progress.isOpen(Zone.STAR_LIGHT));
        assertFalse(progress.isOpen(Zone.SPRING_YARD));
        assertTrue(progress.isOpen(Zone.EMERALD_HILL), "each island starts open");
        progress.clear(Zone.STAR_LIGHT);
        progress.clear(Zone.SPRING_YARD);
        assertTrue(progress.islandComplete(Island.SOUTH));
    }

    @Test
    void joiningHeroesCatchUpWithTheParty() {
        Progress progress = new Progress(1);
        progress.hero(HeroId.SONIC).setLevel(9);
        progress.join(HeroId.TAILS);
        assertEquals(9, progress.hero(HeroId.TAILS).level());
        assertEquals(2, progress.party().size());
        progress.join(HeroId.TAILS);
        assertEquals(2, progress.party().size(), "joining twice changes nothing");
    }

    @Test
    void sevenZoneBossesReturnTheSevenEmeralds() {
        int mask = 0;
        for (Zone zone : Zone.values()) if (zone.emerald >= 0) mask |= 1 << zone.emerald;
        assertEquals(Progress.ALL_EMERALDS, mask);
    }

    @Test
    void levellingRaisesStatsAndKeepsDamageTaken() {
        Hero sonic = new Hero(HeroId.SONIC);
        sonic.setHp(10);
        int maxBefore = sonic.maxHp();
        assertEquals(1, sonic.gainXp(Hero.xpToNext(1)));
        assertEquals(10 + sonic.maxHp() - maxBefore, sonic.hp());
        assertTrue(sonic.atk() > HeroId.SONIC.baseAtk);
    }
}
