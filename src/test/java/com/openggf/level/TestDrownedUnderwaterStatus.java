package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * S3K's AirCountdown_ReduceAir installs routine $C, and that dispatch
 * (loc_125AC/loc_15810) never reaches Sonic_Water/Tails_Water, so a drowning
 * player keeps Status_Underwater wherever it sinks or is carried
 * (docs/skdisasm/sonic3k.asm:21998, 26242, 24692-24707, 29314-29330).
 * S1 and S2 keep the control routine, whose water check still runs
 * ("01 Sonic.asm":82; s2.asm:36251).
 */
class TestDrownedUnderwaterStatus {
    private static final int WATER_Y = 0x0700;
    /** The per-player water dispatch; it reads no level state. */
    private static final LevelWaterCoordinator WATER = new LevelWaterCoordinator(null);

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void s3kDrownedPlayerKeepsUnderwaterStatusAboveTheSurface() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic3k.Sonic3kGameModule());
        Tails tails = drownedUnderwater(GameRules.SONIC_3K);

        tails.setCentreY((short) (WATER_Y - 0x20));
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater(), "routine $C does not run Tails_Water");

        tails.setObjectControlSuppressesMovement(true);
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater(), "an object holding the drowned player does not change that");
    }

    @Test
    void s1AndS2DrownedPlayersStillRunTheirWaterCheck() {
        assertLeavesWater(new com.openggf.game.sonic1.Sonic1GameModule(), GameRules.SONIC_1);
        assertLeavesWater(new com.openggf.game.sonic2.Sonic2GameModule(), GameRules.SONIC_2);
    }

    @Test
    void deadPlayersKeepUnderwaterStatusInEveryGame() {
        // The death routines never call the water check: S3K loc_12390/loc_1578E
        // (sonic3k.asm:24518-24533, 29263-29289), S2 Obj01_Dead (s2.asm:38255-38270),
        // S1 Sonic_Death ("01 Sonic.asm":1967-1985).
        assertDeadKeepsWater(new com.openggf.game.sonic1.Sonic1GameModule(), GameRules.SONIC_1);
        assertDeadKeepsWater(new com.openggf.game.sonic2.Sonic2GameModule(), GameRules.SONIC_2);
        assertDeadKeepsWater(new com.openggf.game.sonic3k.Sonic3kGameModule(), GameRules.SONIC_3K);
    }

    private static void assertDeadKeepsWater(GameModule module, GameRules rules) {
        TestEnvironment.configureGameModuleFixture(module);
        Tails tails = new Tails("tails_p2", (short) 0x2F49, (short) (WATER_Y + 0x20));
        assertSame(rules, tails.getGameRules(), "the configured game owns the drowning rule");
        tails.setCpuControlled(true);
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater(), "precondition: below the surface");
        tails.setDead(true);
        tails.setCentreY((short) (WATER_Y - 0x40));
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater(), "routine 6 does not run the water check");
        tails.setObjectControlSuppressesMovement(true);
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater());
        TestEnvironment.resetAll();
    }

    private static void assertLeavesWater(GameModule module, GameRules rules) {
        TestEnvironment.configureGameModuleFixture(module);
        Tails tails = drownedUnderwater(rules);
        tails.setCentreY((short) (WATER_Y - 0x20));
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertFalse(tails.isInWater(), "the control routine's water check clears Status_Underwater");
        TestEnvironment.resetAll();
    }

    private static Tails drownedUnderwater(GameRules rules) {
        Tails tails = new Tails("tails_p2", (short) 0x2F49, (short) (WATER_Y + 0x20));
        assertSame(rules, tails.getGameRules(), "the configured game owns the drowning rule");
        tails.setCpuControlled(true);
        WATER.updatePlayableWaterState(tails, WATER_Y);
        assertTrue(tails.isInWater(), "precondition: below the surface");
        assertTrue(tails.applyDrownDeath());
        assertTrue(tails.isDrowningPreDeath());
        return tails;
    }
}
