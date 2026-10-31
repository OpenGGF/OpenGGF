package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.SidekickCpuRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.objects.EggPrisonButtonObjectInstance;
import com.openggf.game.sonic2.objects.RisingLavaObjectInstance;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.objects.IczSwingingPlatformObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuFollowParity {

    @BeforeEach
    void configureRuntime() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDownRuntime() {
        SessionManager.clear();
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override
        public void draw() {}

        @Override
        public void defineSpeeds() {}

        @Override
        protected void createSensorLines() {}

        public void setGameRulesForTest(GameRules featureSet) {
            super.setGameRulesForTest(featureSet);
        }
    }

    private static final class LiveRideObject extends AbstractObjectInstance {
        private LiveRideObject(int objectId) {
            this(objectId, 0x1200, 0x0800);
        }

        private LiveRideObject(int objectId, int x, int y) {
            super(new ObjectSpawn(x, y, objectId, 0, 0, false, 0), "LiveRideObject");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            // Test sentinel only.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No rendering needed for this test sentinel.
        }
    }

    private static final class PushingSolidObject extends AbstractObjectInstance
            implements SolidObjectProvider {
        private PushingSolidObject() {
            super(new ObjectSpawn(100, 100, 0x58, 0, 0, false, 0), "PushingSolidObject");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            // Test sentinel only.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No rendering needed for this test sentinel.
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SolidObjectParams.of(16, 8, 8);
        }
    }

    @Test
    void rawControllerHeldBitsExcludeCpuGeneratedDirections() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setCpuController(controller);

        controller.setController2Input(AbstractPlayableSprite.INPUT_DOWN, 0);
        tails.setDirectionalInputPressed(true, false, false, false);

        assertTrue(RawControllerInput.isHeld(tails, AbstractPlayableSprite.INPUT_DOWN));
        assertFalse(RawControllerInput.isHeld(tails, AbstractPlayableSprite.INPUT_UP),
                "CPU follow UP must remain in Ctrl_2_logical and not leak into raw Ctrl_2");
    }

    @Test
    void s3kFreshSpawnInitFrameResetsKinematicsWithoutRunningNormalCpu() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setGameRulesForTest(GameRules.SONIC_3K);
        sonic.setCentreX((short) 0x1200);
        sonic.setCentreY((short) 0x0320);

        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0800);
        tails.setCentreY((short) 0x0200);
        tails.setXSpeed((short) 0x0300);
        tails.setYSpeed((short) 0xFE00);
        tails.setGSpeed((short) 0x0200);
        tails.setAir(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.captureLevelStartLeaderAnchor(sonic.getCentreX(), sonic.getCentreY());

        controller.update(0x1000);

        Assertions.assertAll(
                () -> assertSame(SidekickCpuController.State.NORMAL, controller.getState(),
                        "S3K Tails_Init advances the CPU routine for the next object tick"),
                () -> assertEquals(0x11E0, tails.getCentreX() & 0xFFFF,
                        "SpawnLevelMainSprites places Player_2 at Player_1-$20"),
                () -> assertEquals(0x0324, tails.getCentreY() & 0xFFFF,
                        "SpawnLevelMainSprites places Player_2 at Player_1+4"),
                () -> assertEquals(0, tails.getXSpeed()),
                () -> assertEquals(0, tails.getYSpeed()),
                () -> assertEquals(0, tails.getGSpeed()),
                () -> assertFalse(controller.getInputRight(),
                        "The Tails_Init frame must not fall through into TailsCPU_Normal"),
                () -> assertFalse(controller.getInputLeft(),
                        "The Tails_Init frame must not synthesize follow input"),
                () -> Assertions.assertNull(controller.getLatestNormalStepDiagnostics(),
                        "No normal CPU diagnostic should be emitted on the init-only frame"));
    }

    @Test
    void negativeCtrl2LockSkipsCpuRoutineWithoutTreatingHeldP2AsHistoryInput() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.setController2Input(AbstractPlayableSprite.INPUT_LEFT, 0);
        controller.setController2SignedLocked(true);

        controller.update(19669);

        assertFalse(controller.getInputLeft(),
                "S3K Tails_Control skips Tails_CPU_Control when Ctrl_2_locked is negative "
                        + "(sonic3k.asm:26196-26205)");
        assertEquals("ctrl2_signed_lock_skip", controller.getLatestNormalStepDiagnostics().followBranch());
        assertTrue(controller.isController2SignedLocked());
    }

    @Test
    void negativeCtrl2LockPreservesLastRomVisibleCpuLogicalWordForTraceDiagnostics() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x49DA);
        Arrays.fill(yHistory, (short) 0x01AA);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        tails.setAir(true);
        tails.setRolling(true);
        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x4CC0);
        assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP);

        controller.setController2Input(AbstractPlayableSprite.INPUT_LEFT, 0);
        controller.setController2SignedLocked(true);
        controller.update(0x4CC1);

        assertFalse(controller.getInputLeft(),
                "Negative Ctrl_2_locked still skips live controller input "
                        + "(sonic3k.asm:26196-26205).");
        assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP,
                "The locked path skips Tails_CPU_Control but does not itself clear "
                        + "the ROM-visible Ctrl_2_Logical word; AIZ's capsule lock "
                        + "therefore preserves the previous delayed held jump until "
                        + "another ROM writer changes it.");
    }

    @Test
    void explicitCtrl2LogicalClearDropsSignedLockDiagnosticLatch() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0335);
        Arrays.fill(yHistory, (short) 0x05A2);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.update(0x00DA);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_RIGHT);

        controller.setController2SignedLocked(true);
        controller.clearController2LogicalLatch();
        controller.update(0x00DB);

        Assertions.assertAll(
                () -> assertEquals("ctrl2_signed_lock_skip",
                        controller.getLatestNormalStepDiagnostics().followBranch()),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_RIGHT,
                        "Objects such as MHZ1's Player_2 stopper clear Ctrl_2_logical "
                                + "when they set the signed Ctrl_2_locked byte "
                                + "(sonic3k.asm:130013-130018)."));
    }

    @Test
    void queuedNativeEndingPoseReportsClearedLogicalWordOnceObjectControlOwnsSidekick() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x49DA);
        Arrays.fill(yHistory, (short) 0x01AA);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        tails.setAir(true);
        tails.setRolling(true);
        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x4CC0);
        assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP);

        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(false);
        controller.setController2SignedLocked(true);
        controller.queueNativeEndingPoseForNextPlayerSlot();
        controller.update(0x4CC1);

        Assertions.assertAll(
                () -> assertEquals("ctrl2_signed_lock_skip",
                        controller.getLatestNormalStepDiagnostics().followBranch()),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP,
                        "Check_TailsEndPose clears Ctrl_2_locked before Set_PlayerEndingPose "
                                + "freezes Tails with object_control=$81, so the trace-visible "
                                + "Ctrl_2_logical word follows the cleared raw controller state "
                                + "(sonic3k.asm:26196-26203,181919-181988)."));
    }

    @Test
    void directEndingPosePreservesLogicalWordWhileNegativeCtrl2LockRemainsSet() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.setController2Input(AbstractPlayableSprite.INPUT_LEFT, 0);
        controller.update(0x4CC0);

        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(false);
        controller.setController2SignedLocked(true);
        controller.update(0x4CC1);

        assertEquals(AbstractPlayableSprite.INPUT_LEFT,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_LEFT,
                "A direct Set_PlayerEndingPose call does not clear Ctrl_2_locked "
                        + "or Ctrl_2_logical; only Check_TailsEndPose queues that handoff.");
    }

    @Test
    void immediateEndingPoseUnlockLeavesLogicalInputUntilTheNextPlayerSlot() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.setController2Input(AbstractPlayableSprite.INPUT_LEFT, 0);
        controller.update(0x4CC0);
        controller.setController2SignedLocked(true);
        controller.setController2Input(0, 0);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(false);
        tails.setAnimationId(0x13);
        controller.setController2SignedLocked(false);
        assertEquals(AbstractPlayableSprite.INPUT_LEFT,
                controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_LEFT,
                "Check_TailsEndPose does not write Ctrl_2_logical in the object slot");

        controller.update(0x4CC1);
        controller.recordDiagnosticPostPhysics();

        assertEquals(0, controller.getDiagnosticGeneratedHeldInput());
        assertEquals(0x13, tails.getAnimationId(),
                "the next CPU pass must not delay or overwrite the object-owned pose");
    }

    @Test
    void followRightStillNudgesPositionWhenDxIsBelowThreshold() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(11, tails.getX(),
            "ROM follow-right nudges x_pos by +1 even when |dx| < 16");
        assertFalse(controller.getInputLeft(),
            "ROM only overrides left/right input at |dx| >= 16");
        assertFalse(controller.getInputRight(),
            "ROM only overrides left/right input at |dx| >= 16");
    }

    @Test
    void followSnapThresholdPrefersTypedSidekickCpuRules() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        setGameRulesForTest(tails, withSidekickCpuRules(
                GameRules.SONIC_2,
                new SidekickCpuRules(
                        0x30,
                        GameRules.SONIC_2.sidekickCpu().sidekickDespawnX(),
                        0,
                        false,
                        true,
                        true,
                        GameRules.SONIC_2.sidekickCpu().sidekickFlyLandStatusBlockerMask(),
                        false,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        false,
                        false,
                        false)));

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0140);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        tails.setCentreX((short) 0x0118);
        tails.setCentreY((short) 0);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertFalse(controller.getInputRight(),
                "Typed SidekickCpuRules should override the legacy S2 follow snap threshold");
    }

    @Test
    void followSnapThresholdFallsBackToLegacyWhenTypedSidekickCpuGroupMissing() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        GameRules base = GameRules.SONIC_2;
        setGameRulesForTest(tails, new GameRules(
                base.playerMovement(),
                base.playerCapability(),
                base.collision(),
                base.playerAnimation(),
                base.camera(),
                base.ring(),
                base.objectInteraction(),
                null,
                base.powerUp(),
                base.drowningBubble()));

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0140);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        tails.setCentreX((short) 0x0118);
        tails.setCentreY((short) 0);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertTrue(controller.getInputRight(),
                "A null typed SidekickCpuRules group should fall back to legacy-derived S2 rules");
    }

    @Test
    void followSnapThresholdFallsBackToLegacyWhenGameRulesMissing() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        setGameRulesForTest(tails, null);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0140);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        tails.setCentreX((short) 0x0118);
        tails.setCentreY((short) 0);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertTrue(controller.getInputRight(),
                "Missing GameRules should fall back to legacy-derived S2 sidekick CPU rules");
    }

    @Test
    void followNudgeIsSuppressedWhileObjectControlBitZeroIsSet() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(true);
        tails.setObjectControlSuppressesMovement(true);
        tails.setControlLocked(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(10, tails.getX(),
                "ROM loc_13E34 gates the +/-1 follow nudge on object_control bit 0");
    }

    @Test
    void s2FollowNudgeIgnoresObjectControlBitZero() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(true);
        tails.setObjectControlSuppressesMovement(true);
        tails.setControlLocked(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(11, tails.getX(),
                "S2 TailsCPU_Normal FollowRight gates the nudge on inertia/facing only; "
                        + "the object_control bit-0 nudge gate is S3K-only");
    }

    @Test
    void s2NormalFollowUsesDirectCpuLeaderBeforeSettledThreshold() {
        TestableSprite rootSonic = new TestableSprite("sonic");
        rootSonic.setGameRulesForTest(GameRules.SONIC_2);
        rootSonic.setCentreX((short) 100);
        rootSonic.setCentreY((short) 655);

        TestableSprite sonicLeader = new TestableSprite("sonic_p3");
        sonicLeader.setCpuControlled(true);
        sonicLeader.setGameRulesForTest(GameRules.SONIC_2);
        sonicLeader.setCentreX((short) 180);
        sonicLeader.setCentreY((short) 655);
        SidekickCpuController sonicController = new SidekickCpuController(sonicLeader, rootSonic);
        sonicController.setSidekickCount(2);
        sonicController.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        TestableSprite tails = new TestableSprite("tails_p4");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setCentreX((short) 100);
        tails.setCentreY((short) 655);
        tails.setAir(false);
        tails.setDirection(Direction.RIGHT);

        short[] rootXHistory = new short[64];
        short[] rootYHistory = new short[64];
        short[] leaderXHistory = new short[64];
        short[] leaderYHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(rootXHistory, (short) 100);
        Arrays.fill(rootYHistory, (short) 655);
        Arrays.fill(leaderXHistory, (short) 180);
        Arrays.fill(leaderYHistory, (short) 655);
        rootSonic.hydrateRecordedHistory(rootXHistory, rootYHistory, inputHistory, statusHistory, 16);
        sonicLeader.hydrateRecordedHistory(leaderXHistory, leaderYHistory, inputHistory, statusHistory, 16);

        SidekickCpuController tailsController = new SidekickCpuController(tails, sonicLeader);
        tailsController.setSidekickCount(2);
        tailsController.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tailsController.update(1);

        assertTrue(tailsController.getInputRight(),
                "A direct CPU leader that has already reached NORMAL should be the follow target immediately; "
                        + "the 15-frame settled gate is only for healing broken chains");
    }

    @Test
    void s2TailsFlyInUsesDirectCpuLeaderBeforeSettledThreshold() {
        TestableSprite rootSonic = new TestableSprite("sonic");
        rootSonic.setGameRulesForTest(GameRules.SONIC_2);
        rootSonic.setCentreX((short) 100);
        rootSonic.setCentreY((short) 655);

        TestableSprite sonicLeader = new TestableSprite("sonic_p3");
        sonicLeader.setCpuControlled(true);
        sonicLeader.setGameRulesForTest(GameRules.SONIC_2);
        sonicLeader.setCentreX((short) 180);
        sonicLeader.setCentreY((short) 655);
        SidekickCpuController sonicController = new SidekickCpuController(sonicLeader, rootSonic);
        sonicController.setSidekickCount(2);
        sonicController.forceStateForTest(SidekickCpuController.State.NORMAL, 0);

        short[] rootXHistory = new short[64];
        short[] rootYHistory = new short[64];
        short[] leaderXHistory = new short[64];
        short[] leaderYHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(rootXHistory, (short) 100);
        Arrays.fill(rootYHistory, (short) 655);
        Arrays.fill(leaderXHistory, (short) 180);
        Arrays.fill(leaderYHistory, (short) 655);
        rootSonic.hydrateRecordedHistory(rootXHistory, rootYHistory, inputHistory, statusHistory, 16);
        sonicLeader.hydrateRecordedHistory(leaderXHistory, leaderYHistory, inputHistory, statusHistory, 16);

        TestableSprite tails = new TestableSprite("tails_p4");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setCentreX((short) 100);
        tails.setCentreY((short) 655);
        tails.setAir(true);

        SidekickCpuController tailsController = new SidekickCpuController(tails, sonicLeader);
        tailsController.setSidekickCount(2);
        tailsController.forceStateForTest(SidekickCpuController.State.APPROACHING, 0);

        tailsController.update(1);

        assertTrue(tails.getCentreX() > 100,
                "Tails fly-in should continue targeting its direct CPU leader once that leader is NORMAL, "
                        + "even before the chain-settled threshold elapses");
    }

    @Test
    void followNudgeStillRunsForNonBitZeroObjectControl() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 25);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        tails.setX((short) 10);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);
        tails.setObjectControlled(true);
        tails.setObjectControlAllowsCpu(true);
        tails.setObjectControlSuppressesMovement(false);
        tails.setControlLocked(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(1);

        assertEquals(11, tails.getX(),
                "ROM only blocks the follow nudge when object_control bit 0 is set");
    }

    @Test
    void s3kLeadOffsetStillAppliesWhenLeaderStandingObjectReferenceIsAirborne() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_3K);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1976);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        // The leader still points interact at the object it left, but the
        // object's own helper already cleared Status_OnObj (loc_1DC98).
        sonic.setLatchedSolidObject(0x2A, org.mockito.Mockito.mock(com.openggf.level.objects.ObjectInstance.class));
        sonic.setOnObject(false);
        sonic.setAir(true);
        sonic.setGSpeed((short) 0);

        tails.setCentreXPreserveSubpixel((short) 0x1966);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x00EC);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x06C4);

        assertEquals(0x1966, tails.getCentreX() & 0xFFFF,
                "S3K loc_13DA6 gates the $20 lead offset on Status_OnObj, not a stale standing-object reference");
    }

    @Test
    void delayedCentreHistoryDoesNotDependOnCurrentHitboxHeight() {
        TestableSprite sonic = new TestableSprite("sonic");

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sonic.setRolling(true);

        assertEquals(0x0100, sonic.getCentreX(16) & 0xFFFF,
            "Historical centre X should remain ROM-accurate after a size change");
        assertEquals(0x0200, sonic.getCentreY(16) & 0xFFFF,
            "Historical centre Y should remain ROM-accurate after a size change");
    }

    @Test
    void panicDoesNotHoldDownOrRefacingUntilGroundSpeedStops() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0x0200);

        sonic.setCentreX((short) 0x0200);
        tails.setCentreX((short) 0x0100);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

        controller.update(5);

        assertFalse(controller.getInputDown(),
                "ROM TailsCPU_Panic does not press down until inertia reaches zero");
        assertFalse(controller.getInputJump(),
                "ROM TailsCPU_Panic does not start revving while Tails is still moving");
        assertSame(Direction.LEFT, tails.getDirection(),
                "ROM TailsCPU_Panic does not reface toward Sonic until inertia reaches zero");
        assertSame(SidekickCpuController.State.PANIC, controller.getState());
    }

    @Test
    void s2PanicRevPulseEmitsJumpOnThirtyTwoFramePhase() {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(new Sonic2GameModule());
            TestableSprite sonic = new TestableSprite("sonic");
            sonic.setGameRulesForTest(GameRules.SONIC_2);
            sonic.setCentreX((short) 0x0AF6);

            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setGameRulesForTest(GameRules.SONIC_2);
            tails.setCentreX((short) 0x0ACB);
            tails.setGSpeed((short) 0);
            tails.setMoveLockTimer(0);
            tails.setAnimationId(tails.resolveAnimationId(CanonicalAnimation.DUCK));

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

            controller.update(0x0A20);

            assertTrue(controller.getInputDown(),
                    "S2 TailsCPU_Panic holds DOWN before the release phase (s2.asm:39478-39490)");
            assertTrue(controller.getInputJump(),
                    "S2 TailsCPU_Panic ORs A/B/C on phase $20 while ducking (s2.asm:39478-39490)");
            assertTrue((controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP) != 0,
                    "Trace diagnostics must expose the panic rev jump bit");
        } finally {
            GameModuleRegistry.setCurrent(previous);
            SessionManager.clear();
        }
    }

    @Test
    void panicFacesLeftWhenHorizontallyAlignedWithLeader() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0);
        tails.setSpindash(false);
        tails.setPinballMode(false);

        sonic.setCentreX((short) 0x0200);
        tails.setCentreX((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

        controller.update(5);

        assertSame(Direction.LEFT, tails.getDirection(),
                "S2/S3K TailsCPU_Panic clears facing, then sets left when Tails.x - Sonic.x >= 0; "
                        + "equality therefore faces left");
        assertTrue(controller.getInputDown());
    }

    @Test
    void inputHistoryRecordsLogicalInputRatherThanRawHeldButtons() {
        TestableSprite sonic = new TestableSprite("sonic");

        sonic.setDirectionalInputPressed(false, false, true, false);
        sonic.setJumpInputPressed(false);
        sonic.setLogicalInputState(false, false, false, true, false);

        sonic.endOfTick();

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sonic.getInputHistory(0) & 0xFFFF,
                "ROM Sonic_RecordPos stores Ctrl_1_Logical, so forced-right walkoff must record RIGHT");
    }

    @Test
    void lateObjectLogicalInputWriteUpdatesCurrentFollowerHistorySlot() {
        TestableSprite sonic = new TestableSprite("sonic");

        sonic.setLogicalInputState(false, false, false, false, false);
        sonic.endOfTick();
        sonic.writeLogicalInputAndCurrentFollowerHistory(AbstractPlayableSprite.INPUT_RIGHT, false);

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sonic.getInputHistory(0) & 0xFFFF,
                "Object/event writes to Ctrl_1_logical after Sonic_RecordPos must still update "
                        + "the current Stat_table slot for delayed Tails CPU replay");
    }

    @Test
    void normalPushBypassPreservesDelayedControlWordWhileTestingPushStatus() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1CED);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        statusHistory[4] = AbstractPlayableSprite.STATUS_PUSHING;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0971);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputLeft()),
                () -> assertEquals(true, controller.getInputRight(),
                        "ROM loc_13DD0 tests the delayed status byte in d4, but preserves the already-loaded "
                                + "Ctrl_2 word in d1 when it branches to loc_13E9C "
                                + "(sonic3k.asm:26696-26705,26775-26785; s2.asm:38939-38946)"));
    }

    @Test
    void normalPushBypassUsesSameDelayedStatusSlotAsRomD4() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setPushing(true);
        tails.setCentreX((short) 0x0A4A);
        tails.setCentreY((short) 0x0C63);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0A4A);
        Arrays.fill(yHistory, (short) 0x0C63);
        int historyPos = 20;
        inputHistory[4] = AbstractPlayableSprite.INPUT_LEFT;
        statusHistory[2] = AbstractPlayableSprite.STATUS_FACING_LEFT;
        statusHistory[3] = AbstractPlayableSprite.STATUS_PUSHING;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x099B);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals(4, diagnostics.followHistorySlot(),
                        "With historyPos=20, the ROM follow delay of 16 frames reads slot 4."),
                () -> assertEquals(0, diagnostics.recordedStatus() & 0xFF,
                        "The follow slot is intentionally clear; ROM tests this same d4 byte, not "
                                + "an adjacent older status sample."),
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "S3K loc_13DD0 loads d1/d4 from the same Stat_table entry; when live "
                                + "Status_Push is set and that d4 entry has Status_Push clear, Tails "
                                + "branches to loc_13E9C even if an adjacent older engine history slot "
                                + "contains push (sonic3k.asm:26696-26705,26775-26785)"),
                () -> assertTrue(diagnostics.skipFollowSteering()));
    }

    @Test
    void s2Obj85PreservedRollPushBypassStillGeneratesAutoJumpAndPreservesLatch() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setAir(false);
        tails.setRolling(true);
        tails.setPushing(true);
        tails.setPinballMode(true);
        tails.preserveRollingOnNextRollStop();
        tails.setCentreX((short) 0x106A);
        tails.setCentreY((short) 0x06F1);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0E5B);
        Arrays.fill(yHistory, (short) 0x0398);
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[4] = AbstractPlayableSprite.STATUS_FACING_LEFT | AbstractPlayableSprite.STATUS_IN_AIR;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0F80);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputRight()),
                () -> assertTrue(controller.getInputJump()),
                () -> assertTrue(controller.getInputJumpPress(),
                        "S2 TailsCPU_Normal branches from live Status_Push directly to "
                                + "FilterAction_Part2 and still fires the $3F auto-jump gate "
                                + "while Obj85's preserved-roll handoff is active "
                                + "(docs/s2disasm/s2.asm:39287-39300,39369-39378)."),
                () -> assertEquals(1, controller.getDiagnosticJumpingFlag()));

        controller.update(0x0F81);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputJump(),
                        "Push-bypass frames skip the auto-jump carry path, so Ctrl_2 "
                                + "returns to the delayed directional word."),
                () -> assertEquals(1, controller.getDiagnosticJumpingFlag(),
                        "The same push-bypass path also skips the grounded latch clear "
                                + "in FilterAction, so Tails_CPU_jumping remains set."));
    }

    @Test
    void delayedJumpPressReplaysConsecutiveRecordedPressBytes() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        setGameRulesForTest(tails, withSidekickCpuRules(GameRules.SONIC_2,
                GameRules.SONIC_2.sidekickCpu()));
        tails.setAir(true);
        tails.setCentreX((short) 0x1800);
        tails.setCentreY((short) 0x0400);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1800);
        Arrays.fill(yHistory, (short) 0x0400);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        int delayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        int previousDelayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES + 1);
        inputHistory[delayedSlot] = AbstractPlayableSprite.INPUT_JUMP;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        setJumpPressHistorySlot(sonic, delayedSlot, true);
        setJumpPressHistorySlot(sonic, previousDelayedSlot, true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x1241);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "The delayed held A/B/C bit is replayed from Ctrl_1_logical."),
                () -> assertTrue(controller.getInputJumpPress(),
                        "ROM replays the delayed Ctrl_1_logical press byte directly; "
                                + "a consecutive prior press byte must not suppress it "
                                + "(s2.asm:38939-38946, s2.asm:39025-39027)."),
                () -> assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                        controller.getDiagnosticGeneratedPressedInput()
                                & AbstractPlayableSprite.INPUT_JUMP));
    }

    @Test
    void s3kDelayedJumpPressUsesRecordedLowByteWhileHeldJumpRemains() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setAir(true);
        tails.setCentreX((short) 0x16E6);
        tails.setCentreY((short) 0x0A73);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0x0300);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x168C);
        Arrays.fill(yHistory, (short) 0x0A74);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_IN_AIR);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        int delayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        int previousDelayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES + 1);
        inputHistory[delayedSlot] = AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_JUMP;
        inputHistory[previousDelayedSlot] = AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_JUMP;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        setJumpPressHistorySlot(sonic, delayedSlot, false);
        setJumpPressHistorySlot(sonic, previousDelayedSlot, true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0B40);

        Assertions.assertAll(
                () -> assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                        controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP,
                        "S3K keeps the delayed held A/B/C bit live in Ctrl_2_Logical."),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedPressedInput() & AbstractPlayableSprite.INPUT_JUMP,
                        "HCZ f2894 has delayed_input=$1504: held jump remains, but the low-byte jump press "
                                + "cleared after the prior follower-history sample."),
                () -> assertFalse(controller.getInputJumpPress()));
    }

    @Test
    void s2AirborneLiveAndDelayedPushFallsThroughToFollowSteering() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setAir(true);
        tails.setPushing(true);
        tails.setCentreX((short) 0x1000);
        tails.setCentreY((short) 0x0400);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0100);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1040);
        Arrays.fill(yHistory, (short) 0x0400);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_PUSHING);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x1242);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "S2 TailsCPU_Normal only bypasses FollowRight when Tails is pushing "
                                + "and delayed Sonic is not pushing; delayed Status_Push set must fall through "
                                + "(s2.asm:38943-38975)."),
                () -> assertFalse(diagnostics.skipFollowSteering()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertFalse(controller.getInputLeft()));
    }

    @Test
    void s2RollingLivePushBypassesFollowNudgeBeforeRollSpeedClearsPush() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setAir(false);
        tails.setRolling(true);
        tails.setPushing(true);
        tails.setCentreX((short) 0x0275);
        tails.setCentreY((short) 0x0375);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0xF000);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0272);
        Arrays.fill(yHistory, (short) 0x036C);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x00CB);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "S2 TailsCPU_Normal tests live Status_Push before Tails_RollSpeed reaches "
                                + "Obj02_CheckWallsOnGround; delayed Sonic Status_Push is clear, so "
                                + "loc_1BD64 branches around FollowLeft (s2.asm:39291-39294,39633,40121)."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "The FollowLeft subq.w #1,x_pos nudge at s2.asm:39309-39314 must not run "
                                + "on the live-push branch."),
                () -> assertEquals(0x0275, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kHurtRoutineRecordPosKeepsPreviousLogicalInputForFollowerHistory() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setGameRulesForTest(GameRules.SONIC_3K);

        sonic.setLogicalInputState(false, false, false, false, true);
        sonic.setHurt(true);
        sonic.setLogicalInputState(false, false, false, true, false);
        sonic.recordFollowerHistoryForTick();

        short recorded = sonic.getInputHistory(0);
        assertFalse((recorded & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                "S3K loc_122BE hurt routine calls Sonic_RecordPos without entering "
                        + "Sonic_Control's Ctrl_1_logical refresh, so live RIGHT must not be "
                        + "written to Stat_table during hurt knockback (sonic3k.asm:21967-21975,"
                        + "24449-24467)");
        assertTrue((recorded & AbstractPlayableSprite.INPUT_JUMP) != 0,
                "The previous logical jump word should remain latched for Tails_CPU_Control's "
                        + "delayed Stat_table read (sonic3k.asm:22132,26696-26705)");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void s3kFollowSteeringReadsCurrentPushBit(boolean pushing) throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x210F);
        tails.setCentreY((short) 0x0541);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0072);
        tails.setXSpeed((short) 0x006F);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x2195);
        Arrays.fill(yHistory, (short) 0x0559);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(pushing);
        controller.update(0x29E4);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals(pushing, diagnostics.skipFollowSteering(),
                        "loc_13DD0 branches only on current Status_Push when delayed Push is clear"),
                () -> assertEquals(!pushing, controller.getInputRight(),
                        "cleared Push reaches native FollowRight"));
    }

    @Test
    void s3kClearedPushFallsThroughToFollowLeftNudge() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x3B28);
        tails.setCentreY((short) 0x0AA4);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0xFF9A);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3B13);
        Arrays.fill(yHistory, (short) 0x0AA8);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x8227);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "a clear Status_Push reaches loc_13DF2 steering"),
                () -> assertFalse(diagnostics.skipFollowSteering()),
                () -> assertEquals(-1, diagnostics.appliedFollowNudge(),
                        "loc_13E0A must apply its native one-pixel left nudge"),
                () -> assertEquals(0x3B27, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s2UnderwaterLivePushBypassDoesNotRequireS3kPulseHeuristic() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1200);
        tails.setCentreY((short) 0x0600);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0);
        tails.setXSpeed((short) 0x0008);
        tails.setYSpeed((short) 0);
        tails.setPushing(true);
        tails.setInWater(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1000);
        Arrays.fill(yHistory, (short) 0x0600);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_DOWN);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x1240);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "S2 TailsCPU_Normal branches to FilterAction_Part2 on live "
                                + "Tails Status_Push with delayed Sonic not pushing, with no "
                                + "underwater speed-pulse qualifier (s2.asm:38943-38946)."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertFalse(controller.getInputLeft()),
                () -> assertFalse(controller.getInputRight()),
                () -> assertTrue(controller.getInputDown()),
                () -> assertEquals(AbstractPlayableSprite.INPUT_DOWN,
                        controller.getDiagnosticGeneratedHeldInput()
                                & (AbstractPlayableSprite.INPUT_UP
                                | AbstractPlayableSprite.INPUT_DOWN
                                | AbstractPlayableSprite.INPUT_LEFT
                                | AbstractPlayableSprite.INPUT_RIGHT)));
    }

    @Test
    void airborneCurrentPushStillSkipsFollowNudge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setPushing(true);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) -0x000C);
        tails.setCentreX((short) 0x0A4A);
        tails.setCentreY((short) 0x0C64);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0A2A);
        Arrays.fill(yHistory, (short) 0x0C63);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_JUMP));
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x09A3);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "S3K loc_13DD0 tests live Status_Push without an air-state guard "
                                + "(sonic3k.asm:26702-26705). MGZ f2466 has status $21 and "
                                + "must branch around FollowLeft/FollowRight instead of applying "
                                + "the -1 x_pos nudge at loc_13E0A."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertEquals(0x0A4A, tails.getCentreX()));
    }

    @Test
    void airborneAutoJumpFlagCarriesHeldJumpWithoutRepeatingPress() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_3K);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setCentreX((short) 0x030F);
        tails.setCentreY((short) 0x0540);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x056C);
        Arrays.fill(yHistory, (short) 0x0593);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        int delayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        setJumpPressHistorySlot(sonic, delayedSlot, false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.hydrateFromRomCpuState(0x06, 0, 0, 0, true, 0, 0);

        controller.update(0x03CE);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "loc_13E64 carries the auto-jump latch as a held A/B/C input while airborne"),
                () -> assertFalse(controller.getInputJumpPress(),
                        "The auto-jump latch ORs only the high-byte held bits; it must not repeat "
                                + "loc_13E9C's low-byte press on the next airborne frame"),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedPressedInput() & AbstractPlayableSprite.INPUT_JUMP),
                () -> assertEquals(1, controller.getDiagnosticJumpingFlag()));
    }

    @Test
    void s2AirborneAutoJumpFlagPreservesDelayedLeaderPressByte() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setGameRulesForTest(GameRules.SONIC_2);
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_2);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setCentreX((short) 0x1675);
        tails.setCentreY((short) 0x05E7);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x16B2);
        Arrays.fill(yHistory, (short) 0x0451);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        int delayedSlot = sonic.getHistorySlotIndex(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        setJumpPressHistorySlot(sonic, delayedSlot, true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.hydrateFromRomCpuState(0x06, 0, 0, 0, true, 0, 0);

        controller.update(0x187B);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "S2 loc_1BDCE carries Tails_CPU_jumping by ORing only high-byte A/B/C held bits."),
                () -> assertTrue(controller.getInputJumpPress(),
                        "S2 TailsCPU_Normal sends the delayed Ctrl_1_Logical word unchanged; "
                                + "the loc_1BDCE high-byte carry must not clear a real low-byte "
                                + "press from Sonic_Stat_Record_Buf (s2.asm:39348-39382)."),
                () -> assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                        controller.getDiagnosticGeneratedPressedInput()
                                & AbstractPlayableSprite.INPUT_JUMP));
    }

    @Test
    void normalAutoJumpCadenceUsesInlineFrameCounterForS3kObjectOrder() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1964);
        tails.setCentreY((short) 0x041E);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1956);
        Arrays.fill(yHistory, (short) 0x03ED);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) 0x06);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        controller.update(0x06C0);

        Assertions.assertAll(
                () -> assertEquals(true, controller.getInputJump(),
                        "ROM loc_13E9C holds A/B/C when Level_frame_counter low bits are zero"),
                () -> assertEquals(true, controller.getInputJumpPress(),
                        "ROM loc_13E9C writes Ctrl_2_logical on the Level_frame_counter cadence"));
    }

    @Test
    void airborneRollingNormalStepPreservesDelayedHeldJumpDiagnosticWithoutPressEdge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setCentreX((short) 0x49E0);
        tails.setCentreY((short) 0x01AC);
        tails.setXSpeed((short) 0xFF98);
        tails.setYSpeed((short) 0xFDA8);
        tails.setGSpeed((short) 0xFF24);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x49DA);
        Arrays.fill(yHistory, (short) 0x01AA);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_JUMP);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x4CC1);

        Assertions.assertAll(
                () -> assertEquals(AbstractPlayableSprite.INPUT_JUMP,
                        controller.getDiagnosticGeneratedHeldInput() & AbstractPlayableSprite.INPUT_JUMP,
                        "TailsCPU_Normal copies the delayed Ctrl_1_Logical high-byte A/B/C hold "
                                + "into Ctrl_2_Logical even when there is no fresh low-byte press "
                                + "(sonic3k.asm:26696-26785)."),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedPressedInput() & AbstractPlayableSprite.INPUT_JUMP,
                        "A held-only delayed sample must not manufacture a fresh Ctrl_2 press bit."));
    }

    @Test
    void normalAutoJumpCadenceUsesCallerCadenceBeforeStoredLevelCounter() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            setCurrentZoneAct(3, 0);
            clearLoadedLevelForFeatureZoneFallback();

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setCentreX((short) 0x1964);
            tails.setCentreY((short) 0x041E);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x1964);
            Arrays.fill(yHistory, (short) 0x03ED);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setGameRulesForTest(GameRules.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            setLevelFrameCounter(0x06BF);
            controller.update(0x06C0);
            assertTrue(controller.getInputJumpPress(),
                    "SpriteManager calls Tails CPU after ROM Level_frame_counter has advanced; "
                            + "the caller cadence must win over the stale LevelManager copy");

            setLevelFrameCounter(0x06C0);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
            controller.update(0);
            assertTrue(controller.getInputJumpPress(),
                    "Bootstrap paths without a caller cadence still fall back to the stored "
                            + "Level_frame_counter");
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kPanicReleaseGateUsesLevelFrameCounterLowByte() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            setCurrentZoneAct(3, 0);
            clearLoadedLevelForFeatureZoneFallback();

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setGameRulesForTest(GameRules.SONIC_3K);
            tails.setPinballMode(true);
            tails.setGSpeed((short) 0x0A16);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

            setLevelFrameCounter(0x22FF);
            assertEquals(0x22FF, GameServices.level().getFrameCounter());
            controller.update(0x22FF);

            assertSame(SidekickCpuController.State.PANIC, controller.getState(),
                    "ROM TailsCPU_Panic reads the low byte at Level_frame_counter+1, not "
                            + "Level_frame_counter plus one frame (S3K sonic3k.asm:26869-26884); "
                            + "$22FF must keep DOWN held");
            assertTrue(controller.getInputDown(),
                    "The release branch does not run until the low byte is $00");

            setLevelFrameCounter(0x2300);
            controller.update(0x2300);

            assertSame(SidekickCpuController.State.NORMAL, controller.getState(),
                    "The $7F release gate clears Ctrl_2_logical and writes Tails_CPU_routine=6 at $2300");
            assertFalse(controller.getInputDown());
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kPanicRevPulseBridgesStoredPreSpriteCounter() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            setCurrentZoneAct(3, 0);
            clearLoadedLevelForFeatureZoneFallback();

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setGameRulesForTest(GameRules.SONIC_3K);
            tails.setPinballMode(true);
            tails.setGSpeed((short) 0);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);

            // LevelManager advances at the loop top as the ROM does, so its
            // counter is the ROM-visible Level_frame_counter the panic branch reads.
            setLevelFrameCounter(0x2220);
            controller.update(0);

            assertTrue(controller.getInputJumpPress(),
                    "The ROM-visible $2220 counter puts the panic spindash branch on its "
                            + "$20 phase, so it must publish its jump pulse");
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kPanicRevPulseReadsLevelCounterWithoutRetainedHistoryProjection() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            Sonic3kGameModule module = new Sonic3kGameModule();
            installStandaloneGameModule(module);
            Sonic3kTitleCardManager titleCard =
                    (Sonic3kTitleCardManager) module.getTitleCardProvider();
            setBooleanField(titleCard, "inLevelMode", true);
            setBooleanField(titleCard, "retainedResultsHeldLevelCounterOwned", true);

            TestableSprite sonic = new TestableSprite("sonic");
            sonic.prefillPositionHistoryWithCentre((short) 0, (short) 0);
            assertEquals(0x3F, sonic.getHistorySlotIndex(0));
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setGameRulesForTest(GameRules.SONIC_3K);
            tails.setPinballMode(true);
            tails.setGSpeed((short) 0);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            controller.forceStateForTest(SidekickCpuController.State.PANIC, 0);
            controller.update(0x72C0);

            assertTrue(controller.getInputJumpPress(),
                    "TailsCPU_Panic reads Level_frame_counter=$72C0 directly; the retained "
                            + "Sonic_RecordPos slot $3F must not shift its $20-phase pulse");
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kCatchUpFlightOnlyBlocksOnLeaderObjectControlSignBit() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setGameRulesForTest(GameRules.SONIC_3K);

        sonic.setCentreX((short) 0x0B78);
        sonic.setCentreY((short) 0x0325);
        sonic.setObjectControlled(true);
        sonic.setObjectControlAllowsCpu(true);

        tails.setCentreX((short) 0x7F00);
        tails.setCentreY((short) 0x0000);
        tails.setXSpeed((short) 0x0445);
        tails.setGSpeed((short) 0x0445);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.CATCH_UP_FLIGHT, 0);

        controller.update(0x1780);

        Assertions.assertAll(
                () -> assertSame(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, controller.getState(),
                        "ROM Tails_Catch_Up_Flying blocks only when Sonic object_control is negative "
                                + "(sonic3k.asm:26478-26488)"),
                () -> assertEquals(0x0B78, tails.getCentreX() & 0xFFFF),
                () -> assertEquals(0x0265, tails.getCentreY() & 0xFFFF),
                () -> assertEquals(0, tails.getXSpeed(),
                        "ROM loc_13B50 zeroes x_vel on the catch-up warp (sonic3k.asm:26503-26506)"),
                () -> assertEquals(0, tails.getGSpeed(),
                        "ROM loc_13B50 zeroes ground_vel on the catch-up warp (sonic3k.asm:26503-26506)"));
    }

    private static void setLevelFrameCounter(int value) throws Exception {
        Field frameCounter = GameServices.level().getClass().getDeclaredField("frameCounter");
        frameCounter.setAccessible(true);
        frameCounter.setInt(GameServices.level(), value);
    }

    private static void setCurrentZoneAct(int zone, int act) throws Exception {
        Field currentZone = GameServices.level().getClass().getDeclaredField("currentZone");
        currentZone.setAccessible(true);
        currentZone.setInt(GameServices.level(), zone);
        Field currentAct = GameServices.level().getClass().getDeclaredField("currentAct");
        currentAct.setAccessible(true);
        currentAct.setInt(GameServices.level(), act);
    }

    private static void clearLoadedLevelForFeatureZoneFallback() throws Exception {
        Field level = GameServices.level().getClass().getDeclaredField("level");
        level.setAccessible(true);
        level.set(GameServices.level(), null);
    }

    private static void setApparentAct(int act) throws Exception {
        Field apparentAct = GameServices.level().getClass().getDeclaredField("apparentAct");
        apparentAct.setAccessible(true);
        apparentAct.setInt(GameServices.level(), act);
    }

    private static void installStandaloneGameModule(GameModule module) {
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
    }

    private static void setBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void installEmptyObjectManager() throws Exception {
        Field field = GameServices.level().getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.level(), new ObjectManager(List.of(), null, 0, null, null));
    }

    private static void installObjectManager(ObjectManager objectManager) throws Exception {
        Field field = GameServices.level().getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.level(), objectManager);
    }

    private static void setControllerState(SidekickCpuController controller,
                                           SidekickCpuController.State state) throws Exception {
        Field stateField = SidekickCpuController.class.getDeclaredField("state");
        stateField.setAccessible(true);
        stateField.set(controller, state);
    }

    private static void setControllerInt(SidekickCpuController controller, String fieldName, int value)
            throws Exception {
        Field field = SidekickCpuController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(controller, value);
    }

    private static void setJumpPressHistorySlot(AbstractPlayableSprite sprite, int slot, boolean value)
            throws Exception {
        Field field = AbstractPlayableSprite.class.getDeclaredField("jumpPressHistory");
        field.setAccessible(true);
        byte[] history = (byte[]) field.get(sprite);
        history[slot] = (byte) (value ? 1 : 0);
    }

    private static void setGameRulesForTest(TestableSprite sprite, GameRules rules) throws Exception {
        Field field = AbstractPlayableSprite.class.getDeclaredField("gameRules");
        field.setAccessible(true);
        field.set(sprite, rules);
    }

    private static GameRules withSidekickCpuRules(GameRules base, SidekickCpuRules sidekickCpuRules) {
        return new GameRules(
                base.playerMovement(),
                base.playerCapability(),
                base.collision(),
                base.playerAnimation(),
                base.camera(),
                base.ring(),
                base.objectInteraction(),
                sidekickCpuRules,
                base.powerUp(),
                base.drowningBubble());
    }
}
