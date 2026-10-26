package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;
import com.openggf.physics.Direction;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tails_CPU_Control is reached only from Tails' routine-2 control path
 * (docs/skdisasm/sonic3k.asm:26180-26190). S3K's drown installs routine $C
 * (loc_15810, sonic3k.asm:29314-29330), so a drowning CPU Tails runs no CPU
 * step: no generated input and no facing write.
 * S2's Obj0A_ReduceAir (s2.asm:42145-42168) retains routine 2 and writes $81.
 * Obj02_Control_Joypad2 (s2.asm:38963-38970) still reaches the CPU, whose
 * TailsCPU_Normal_SonicOK (s2.asm:39273-39278) suppresses follow steering.
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
            assertEquals(0, cpu.getDiagnosticRespawnCounter(), "routine $C bypasses CPU despawn maintenance");
            assertNull(cpu.getLatestNormalStepDiagnostics(), "routine $C never enters normal CPU control");
        }
    }

    @Test
    void s2DrowningSidekickStillRunsItsCpuStep() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        Sonic sonic = leaderFacingLeftFarLeft(GameRules.SONIC_2);
        Tails tails = drowningCpuTails(GameRules.SONIC_2);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        cpu.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        assertTrue(tails.isObjectControlled(), "Obj0A_ReduceAir writes obj_control=$81");
        assertFalse(tails.isObjectControlAllowsCpu());
        for (int frame = 0; frame < 8; frame++) {
            cpu.update(frame);
            // TailsCPU_Normal_SonicOK (s2.asm:39273-39278) calls
            // TailsCPU_CheckDespawn before testing
            // obj_control's sign bit. Its offscreen counter proves CPU entry
            // independently of the suppressed steering:
            // TailsCPU_TickRespawnTimer (s2.asm:39429-39432).
            assertEquals(frame + 1, cpu.getDiagnosticRespawnCounter());
            assertEquals("object_control_bit7", cpu.getLatestNormalStepDiagnostics().followBranch());
            assertFalse(cpu.getInputLeft() || cpu.getInputRight() || cpu.getInputJump());
            assertEquals(Direction.RIGHT, tails.getDirection());
        }
    }

    @Test
    void s2OrdinarySidekickStillSteersTowardsLeader() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        Sonic sonic = leaderFacingLeftFarLeft(GameRules.SONIC_2);
        Tails tails = new Tails("tails_p2", (short) 0x2F49, (short) 0x0400);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setCpuControlled(true);
        tails.setDirection(Direction.RIGHT);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        cpu.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        boolean steeredOrTurned = false;
        for (int frame = 0; frame < 8; frame++) {
            cpu.update(frame);
            steeredOrTurned |= cpu.getInputLeft() || tails.getDirection() == Direction.LEFT;
        }
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
        tails.setRenderFlagOnScreen(false);
        assertTrue(tails.applyDrownDeath());
        return tails;
    }
}
