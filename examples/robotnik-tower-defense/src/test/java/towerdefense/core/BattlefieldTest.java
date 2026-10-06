package towerdefense.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Behavioral checks for the siege, independent of the engine and ROM. */
class BattlefieldTest {
    @Test void buyingUpgradingAndSellingConserveScrap() {
        Battlefield b = new Battlefield();
        int money = b.scrap();
        assertTrue(b.build(0, Catalog.SNALE));
        assertEquals(money - Catalog.defense(Catalog.SNALE).cost(), b.scrap());
        assertFalse(b.build(0, Catalog.MORTAR));
        int cost = b.upgradeCost(0);
        assertTrue(b.upgrade(0));
        assertEquals(2, b.tower(0).level());
        assertEquals(money - Catalog.defense(Catalog.SNALE).cost() - cost, b.scrap());
        int refund = b.sellValue(0);
        assertTrue(b.sell(0));
        assertEquals(money - Catalog.defense(Catalog.SNALE).cost() - cost + refund, b.scrap());
        assertNull(b.tower(0));
    }

    @Test void invalidAndUnaffordableActionsAreAtomic() {
        Battlefield b = new Battlefield();
        assertFalse(b.build(-1, 0));
        assertFalse(b.build(10, 0));
        assertFalse(b.build(0, 99));
        assertFalse(b.upgrade(0));
        assertTrue(b.build(0, Catalog.EGG_ROBO));
        int before = b.scrap();
        assertFalse(b.build(1, Catalog.EGG_ROBO));
        assertEquals(before, b.scrap());
        assertNull(b.tower(1));
    }

    @Test void preparationDoesNotAdvanceWaveOrSpawnBirds() {
        Battlefield b = new Battlefield();
        for (int i = 0; i < 600; i++) b.update();
        assertEquals(0, b.wave());
        assertTrue(b.birds().isEmpty());
        assertEquals(Battlefield.PREP, b.phase());
        assertFalse(b.bomb());
        assertTrue(b.startWave());
        assertFalse(b.startWave());
        b.update();
        assertFalse(b.birds().isEmpty());
    }

    @Test void undefendedBirdsPeckUntilTheDoorFalls() {
        Battlefield b = new Battlefield();
        b.startWave();
        for (int i = 0; i < 20000 && !b.finished(); i++) b.update();
        assertEquals(Battlefield.LOST, b.phase());
        assertEquals(0, b.doorHp());
        int tick = b.ticks();
        for (int i = 0; i < 100; i++) b.update();
        assertEquals(tick, b.ticks());
        assertFalse(b.build(0, 0));
    }

    @Test void repairIsOnlyPaidForActualBetweenWaveDamage() {
        Battlefield b = new Battlefield();
        assertFalse(b.repair());
        assertEquals(150, b.scrap());
        b.doorHp = 150;
        assertTrue(b.repair());
        assertEquals(190, b.doorHp());
        assertEquals(125, b.scrap());
        b.startWave();
        assertFalse(b.repair());
        assertEquals(125, b.scrap());
    }

    @Test void antiAirIgnoresGroundBirdsAndKillsFlyers() {
        Battlefield b = combat(Catalog.BUGGERNAUT);
        Flicky ground = bird(b, Catalog.PICKETER, 72, 148);
        Flicky air = bird(b, Catalog.FLYER, 72, 80);
        double groundHp = ground.hp;
        for (int i = 0; i < 50; i++) b.update();
        assertEquals(groundHp, ground.hp);
        assertTrue(air.hp < air.maxHp);
    }

    @Test void mortarSplashHitsNeighboursButNotAir() {
        Battlefield b = combat(Catalog.MORTAR);
        Flicky a = bird(b, Catalog.PICKETER, 70, 148);
        Flicky near = bird(b, Catalog.PICKETER, 78, 148);
        Flicky air = bird(b, Catalog.FLYER, 74, 80);
        b.update();
        assertTrue(a.hp < a.maxHp);
        assertTrue(near.hp < near.maxHp);
        assertEquals(air.maxHp, air.hp);
    }

    @Test void piercingShotsBypassShieldArmor() {
        Battlefield ordinary = combat(Catalog.SNALE);
        Battlefield piercing = combat(Catalog.SPIKER);
        Flicky a = bird(ordinary, Catalog.SHIELD, 70, 148);
        Flicky p = bird(piercing, Catalog.SHIELD, 70, 148);
        ordinary.update();
        piercing.update();
        assertTrue(a.maxHp - a.hp < Catalog.defense(Catalog.SNALE).damage());
        assertEquals(Catalog.defense(Catalog.SPIKER).damage(), p.maxHp - p.hp, 0.001);
    }

    @Test void orbinautSlowsTheWholeNearbyPicket() {
        Battlefield b = combat(Catalog.ORBINAUT);
        Flicky a = bird(b, Catalog.PICKETER, 70, 148);
        Flicky c = bird(b, Catalog.PICKETER, 82, 148);
        b.update();
        assertTrue(a.slowTicks > 0);
        assertTrue(c.slowTicks > 0);
        double x = a.x;
        b.update();
        assertTrue(a.x - x < a.speed);
    }

    @Test void organiserSpeedsComradesAndSaboteurJamsTowers() {
        Battlefield b = combat(Catalog.SNALE);
        Flicky a = bird(b, Catalog.PICKETER, 35, 148);
        bird(b, Catalog.ORGANISER, 40, 148);
        bird(b, Catalog.SABOTEUR, 72, 148);
        double x = a.x;
        b.update();
        assertTrue(a.x - x > a.speed);
        assertTrue(b.tower(0).jamTicks() > 0);
        for (int i = 0; i < 200; i++) b.update();
        assertEquals(0, b.tower(0).jamTicks());
    }

    @Test void chainShotHitsAtMostThreeBirds() {
        Battlefield b = combat(Catalog.EGG_ROBO);
        for (int i = 0; i < 5; i++) bird(b, Catalog.SHIELD, 70 + i * 3, 148);
        b.update();
        assertEquals(3, b.birds().stream().filter(f -> f.hp < f.maxHp).count());
    }

    @Test void organiserAuraAtItsBoundaryDoesNotDependOnUpdateOrder() {
        Battlefield first = combat(Catalog.SNALE);
        Battlefield second = combat(Catalog.SNALE);
        Flicky a = bird(first, Catalog.PICKETER, 190, 148);
        bird(first, Catalog.ORGANISER, 134, 148);
        bird(second, Catalog.ORGANISER, 134, 148);
        Flicky b = bird(second, Catalog.PICKETER, 190, 148);
        first.update();
        second.update();
        assertEquals(a.x, b.x, 0.000001, "aura reads frame-start positions for every member");
    }

    @Test void emergencyBombPushesBirdsBackAndHasARealCooldown() {
        Battlefield b = combat(Catalog.SNALE);
        Flicky a = bird(b, Catalog.SHIELD, 300, 148);
        assertTrue(b.bomb());
        assertTrue(a.x < 300);
        assertTrue(a.hp < a.maxHp);
        assertFalse(b.bomb());
        assertTrue(b.bombCooldown() > 0);
    }

    @Test void ordinaryPurchasesCanWinAllFifteenWavesDeterministically() {
        Battlefield first = playCampaign();
        Battlefield second = playCampaign();
        assertEquals(Battlefield.WON, first.phase());
        assertEquals(15, first.wave());
        assertEquals(first.score(), second.score());
        assertEquals(first.doorHp(), second.doorHp());
        assertEquals(first.ticks(), second.ticks());
    }

    @Test void cheapDirectFireAloneCannotIgnoreLateShieldLines() {
        Battlefield b = new Battlefield();
        while (!b.finished()) {
            for (int site = 0; site < 10; site++) {
                if (b.tower(site) == null) b.build(site, Catalog.SNALE);
                else b.upgrade(site);
            }
            b.startWave();
            int limit = 0;
            while (b.phase().equals(Battlefield.WAVE) && limit++ < 20000) {
                b.update();
                if (b.birds().stream().anyMatch(f -> f.x > 325)) b.bomb();
            }
            assertTrue(limit < 20000);
        }
        assertEquals(Battlefield.LOST, b.phase(), "shield lines require a counter in the defense mix");
    }

    private Battlefield playCampaign() {
        Battlefield b = new Battlefield();
        int[] kinds = {Catalog.SNALE, Catalog.MORTAR, Catalog.SPIKER, Catalog.ORBINAUT, Catalog.EGG_ROBO,
                Catalog.BUGGERNAUT, Catalog.SNALE, Catalog.SPIKER, Catalog.MORTAR, Catalog.EGG_ROBO};
        for (int wave = 0; wave < 15 && !b.finished(); wave++) {
            for (int site = 0; site < 10; site++) {
                if (b.tower(site) == null) b.build(site, kinds[site]);
            }
            for (int level = 2; level <= 3; level++) {
                for (int site = 0; site < 10; site++) {
                    if (b.tower(site) != null && b.tower(site).level() < level) b.upgrade(site);
                }
            }
            if (b.doorHp() < 160) b.repair();
            b.startWave();
            int limit = 0;
            while (b.phase().equals(Battlefield.WAVE) && limit++ < 20000) {
                b.update();
                if (b.birds().stream().anyMatch(f -> f.x > 325)) b.bomb();
            }
            assertTrue(limit < 20000, "wave terminates: " + b.wave());
        }
        return b;
    }

    private Battlefield combat(int kind) {
        Battlefield b = new Battlefield();
        assertTrue(b.build(0, kind));
        b.startWave();
        return b;
    }

    private Flicky bird(Battlefield b, int kind, double x, double y) {
        Flicky f = new Flicky(kind, 1, b.nextBirdId++);
        f.x = x;
        f.y = y;
        b.birds.add(f);
        return f;
    }
}
