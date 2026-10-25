package com.openggf.sprites.playable;

import com.openggf.game.GameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.physics.Direction;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tails_CPU_Control is reached only from Tails' routine-2 control path
 * (docs/skdisasm/sonic3k.asm:26180-26190). S3K's drown installs routine $C
 * (loc_15810, sonic3k.asm:29314-29330), so a drowning CPU Tails runs no CPU
 * step: no generated input and no facing write.
 */
class TestDrownedSidekickCpuPause {

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void s3kDrowningSidekickRunsNoCpuStep() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic3k.Sonic3kGameModule());
        Sonic sonic = leaderFacingLeftFarLeft(GameRules.SONIC_3K);
        Tails tails = drowningCpuTails(GameRules.SONIC_3K);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        cpu.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        for (int frame = 0; frame < 8; frame++) {
            cpu.update(frame);
            assertFalse(cpu.getInputLeft() || cpu.getInputRight() || cpu.getInputJump());
            assertEquals(Direction.RIGHT, tails.getDirection(), "routine $C leaves the facing bit alone");
        }
    }

    @Test
    void s2DrowningSidekickStillRunsItsCpuStep() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        Sonic sonic = leaderFacingLeftFarLeft(GameRules.SONIC_2);
        Tails tails = drowningCpuTails(GameRules.SONIC_2);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        cpu.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        boolean steeredOrTurned = false;
        for (int frame = 0; frame < 8; frame++) {
            cpu.update(frame);
            steeredOrTurned |= cpu.getInputLeft() || tails.getDirection() == Direction.LEFT;
        }
        // S2 keeps routine 2 while drowning (s2.asm:42172), so TailsCPU_Control still runs.
        assertTrue(steeredOrTurned);
    }

    private static Sonic leaderFacingLeftFarLeft(GameRules rules) {
        Sonic sonic = new Sonic("sonic", (short) 0x2E00, (short) 0x0300);
        sonic.setGameRulesForTest(rules);
        sonic.setDirection(Direction.LEFT);
        for (int i = 0; i < 70; i++) {
            sonic.recordFollowerHistoryForTick();
            sonic.endOfTick();
        }
        return sonic;
    }

    private static Tails drowningCpuTails(GameRules rules) {
        Tails tails = new Tails("tails_p2", (short) 0x2F49, (short) 0x0400);
        tails.setGameRulesForTest(rules);
        tails.setCpuControlled(true);
        tails.setDirection(Direction.RIGHT);
        assertTrue(tails.applyDrownDeath());
        return tails;
    }
}
