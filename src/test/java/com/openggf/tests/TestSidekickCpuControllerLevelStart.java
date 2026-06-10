package com.openggf.tests;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertTrue(controller.getInputRight(), "Tails should generate follow input on the first gameplay tick");
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
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);

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
