package com.openggf.tests;

import com.openggf.game.rules.GameRules;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuControllerLevelStart {
    private static final int ROM_FOLLOW_DELAY_FRAMES = 16;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void firstGameplayTickUsesRomSidekickStartPlacementAndFollowInput() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x60);
        leader.setCentreY((short) 0x290);

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x38);
        tails.setCentreY((short) 0x28C);
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        tails.setCpuController(controller);

        controller.update(1);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertEquals(0x60, leader.getCentreX(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF);
        assertEquals(0x290, leader.getCentreY(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF);
        assertEquals(0x40, tails.getCentreX() & 0xFFFF);
        assertEquals(0x294, tails.getCentreY() & 0xFFFF);
        // TailsCPU_Init sets Tails_CPU_routine to 6 and RETURNS -- it does not fall
        // through to TailsCPU_Normal (docs/s2disasm/s2.asm:39258-39268, next label
        // TailsCPU_Spawning; dispatched via TailsCPU_States :39081-39087). S3K's
        // routine-0 entry likewise ends every path in rts (sonic3k.asm:26429-26511).
        // So the init pass sets up and steers nothing; the first follow input is
        // produced on the NEXT pass, once the dispatcher reaches routine 6. This
        // assertion previously required the opposite, pinning a fall-through the
        // ROM does not perform.
        assertFalse(controller.getInputRight(),
                "TailsCPU_Init returns without steering; follow input starts on the next pass");

        controller.update(2);
        assertTrue(controller.getInputRight(),
                "the pass after init dispatches TailsCPU_Normal and steers");
    }

    @Test
    void delayedHeldDirectionDoesNotReportCtrl2PressedLogical() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x100);
        leader.setCentreY((short) 0x200);
        seedLeaderHistory(leader, AbstractPlayableSprite.INPUT_RIGHT, false);

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x100);
        tails.setCentreY((short) 0x200);
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(17);

        assertTrue(controller.getInputRight(), "Delayed held RIGHT should remain visible as Ctrl_2_Held_Logical");
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, controller.getDiagnosticGeneratedHeldInput());
        assertEquals(0, controller.getDiagnosticGeneratedPressedInput(),
                "Copied delayed held input must not masquerade as Ctrl_2_Press_Logical");
    }

    @Test
    void followSteeringOverrideReportsCtrl2PressedLogicalDirection() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x200);
        leader.setCentreY((short) 0x200);
        seedLeaderHistory(leader, 0, false);

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x100);
        tails.setCentreY((short) 0x200);
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(17);

        assertTrue(controller.getInputRight(), "Follow steering should synthesize RIGHT when the leader is ahead");
        assertFalse(controller.getInputLeft());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, controller.getDiagnosticGeneratedHeldInput());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, controller.getDiagnosticGeneratedPressedInput(),
                "ROM TailsCPU_Normal_FollowRight writes RIGHT into both Ctrl_2 logical bytes");
    }

    @Test
    void delayedDirectionalEdgeReportsCtrl2PressedLogical() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x100);
        leader.setCentreY((short) 0x200);
        seedLeaderHistory(leader, 0, false);
        recordLeaderHistory(leader, AbstractPlayableSprite.INPUT_LEFT, false);
        for (int i = 0; i < ROM_FOLLOW_DELAY_FRAMES; i++) {
            recordLeaderHistory(leader, 0, false);
        }

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x100);
        tails.setCentreY((short) 0x200);
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(17);

        assertTrue(controller.getInputLeft(), "Delayed held LEFT should remain visible as Ctrl_2_Held_Logical");
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, controller.getDiagnosticGeneratedHeldInput());
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, controller.getDiagnosticGeneratedPressedInput(),
                "Copied delayed Ctrl_1_Logical must preserve directional press edges from the stat table");
    }

    @Test
    void hurtObjectRoutineKeepsCtrl2LogicalDiagnosticLatched() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x100);
        leader.setCentreY((short) 0x200);
        seedLeaderHistory(leader, AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, false);
        recordLeaderHistory(leader, AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, true);
        for (int i = 0; i < ROM_FOLLOW_DELAY_FRAMES; i++) {
            recordLeaderHistory(leader, AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP, false);
        }

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x100);
        tails.setCentreY((short) 0x200);
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        controller.setInitialState(SidekickCpuController.State.NORMAL);
        controller.update(17);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedHeldInput());
        assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedPressedInput() & AbstractPlayableSprite.INPUT_JUMP);

        tails.setHurt(true);
        controller.update(18);

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedHeldInput(),
                "S2 Obj02_Hurt bypasses Obj02_Control, so Ctrl_2_Logical keeps its last CPU-written word");
        assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedPressedInput() & AbstractPlayableSprite.INPUT_JUMP,
                "The comparison-only pressed byte should remain latched while the hurt routine bypasses CPU writes");
    }

    @Test
    void levelBoundaryKillPreservesRomOnObjectBitOnEntryFrame() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0x29D, (short) 0x749);
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setOnObject(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setRollingJump(true);
        tails.setJumping(true);
        tails.setXSpeed((short) 0x120);
        tails.setYSpeed((short) 0x40);
        tails.setGSpeed((short) 0x120);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        tails.setCpuController(controller);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn(SidekickCpuController.DespawnCause.LEVEL_BOUNDARY);

        assertEquals(SidekickCpuController.State.DEAD_FALLING, controller.getState());
        assertTrue(tails.isOnObject(),
                "S2 KillCharacter calls Tails_ResetOnFloor_Part2, which clears in_air/pushing/rolljumping "
                        + "but does not clear Status_OnObj before setting Status_InAir");
        assertTrue(tails.getAir(), "KillCharacter sets Status_InAir after ResetOnFloor_Part2");
        assertFalse(tails.getPushing(), "ResetOnFloor_Part2 clears Status_Push");
        assertFalse(tails.getRollingJump(), "ResetOnFloor_Part2 clears Status_RollJump");
        assertFalse(tails.isJumping(), "ResetOnFloor_Part2 clears jumping");
        assertEquals(0, tails.getXSpeed(), "KillCharacter clears x_vel");
        assertEquals((short) -0x700, tails.getYSpeed(), "KillCharacter writes y_vel=-$700");
        assertEquals(0, tails.getGSpeed(), "KillCharacter clears ground_vel");
        assertEquals(0x18, tails.getAnimationId(),
                "KillCharacter publishes Tails' Death animation in the boundary-kill frame");
        assertEquals(-1, tails.getForcedAnimationId(),
                "S2 KillCharacter writes Death once; Obj02_Dead does not override later solid writes");
    }

    private static void seedLeaderHistory(TestablePlayableSprite leader, int heldMask, boolean jumpPress) {
        for (int i = 0; i < 64; i++) {
            recordLeaderHistory(leader, heldMask, jumpPress);
        }
    }

    private static void recordLeaderHistory(TestablePlayableSprite leader, int heldMask, boolean jumpPress) {
        leader.setLogicalInputState(
                (heldMask & AbstractPlayableSprite.INPUT_UP) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (heldMask & AbstractPlayableSprite.INPUT_JUMP) != 0,
                jumpPress);
        leader.endOfTick();
    }
}
