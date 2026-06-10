package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
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

        void setPhysicsFeatureSetForTest(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }
    }

    private static final class LiveRideObject extends AbstractObjectInstance {
        private LiveRideObject(int objectId) {
            super(new ObjectSpawn(0x1200, 0x0800, objectId, 0, 0, false, 0), "LiveRideObject");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // Test sentinel only.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No rendering needed for this test sentinel.
        }
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
    void negativeCtrl2LockReportsClearedLogicalWordOnceEndingPoseObjectControlOwnsSidekick() {
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
    void groundedFollowNudgeClearsQueuedLateContactBridge() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        tails.setAir(false);
        tails.setDirection(Direction.LEFT);
        tails.setCentreX((short) 0x1B9A);
        tails.setCentreY((short) 0x07B0);
        tails.setGSpeed((short) 0xFEF8);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1B70);
        Arrays.fill(yHistory, (short) 0x07B0);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setControllerInt(controller, "pendingGroundedFollowNudge", -1);
        setControllerInt(controller, "pendingGroundedFollowNudgeFrame", 0x1158);

        controller.update(0x1159);

        assertEquals(0x1B99, tails.getCentreX() & 0xFFFF,
                "ROM loc_13E0A applies the grounded follow nudge immediately "
                        + "(sonic3k.asm:26717-26724)");
        assertEquals(0, controller.consumePendingGroundedFollowNudge(1),
                "Once the immediate ROM nudge has run, the engine's late-contact "
                        + "bridge must not apply a second queued nudge before CNZCylinder capture");
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
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1976);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        sonic.setOnObject(true);
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
    void releasedAizIntroMarkerSuppressesFirstNormalMovementPulse() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x138A);
        tails.setCentreY((short) 0x041F);
        tails.setGSpeed((short) 0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x138A);
        Arrays.fill(yHistory, (short) 0x041F);
        int historyPos = 20;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        controller.releaseDormantMarkerForLevelEvent();
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
        setControllerState(controller, SidekickCpuController.State.NORMAL);

        controller.update(0x0483);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputRight(),
                        "the release-side object frame can run before the delayed RIGHT pulse is visible"),
                () -> assertTrue(controller.consumeSkipPhysicsThisFrame(),
                        "AIZ1 release suppresses movement/physics for the release-side object frame"));

        inputHistory[historyPos - 16] = AbstractPlayableSprite.INPUT_RIGHT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        controller.update(0x0484);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputRight(),
                        "ROM loc_13DD0 generates RIGHT again on the next object frame"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "only the release-side object frame is suppressed; frame 1445 consumes "
                                + "the freshly generated follow input normally"));
    }

    @Test
    void releasedAizIntroMarkerConsumesSuppressionOnFlightToNormalTransition() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        sonic.setCentreX((short) 0x138A);
        sonic.setCentreY((short) 0x041F);
        tails.setCentreX((short) 0x138A);
        tails.setCentreY((short) 0x041F);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x138A);
        Arrays.fill(yHistory, (short) 0x041F);
        int historyPos = 20;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.DORMANT_MARKER);
        controller.releaseDormantMarkerForLevelEvent();
        setControllerState(controller, SidekickCpuController.State.FLIGHT_AUTO_RECOVERY);

        controller.update(0x05A4);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputRight(),
                        "flight recovery transition does not run normal follow AI on the handoff tick"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "ROM still runs the normal airborne gravity step on the flight-to-normal handoff"));

        inputHistory[historyPos - 16] = AbstractPlayableSprite.INPUT_RIGHT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        controller.update(0x05A5);

        Assertions.assertAll(
                () -> assertTrue(controller.getInputRight(),
                        "the first normal tick after the handoff sees the delayed RIGHT pulse"),
                () -> assertFalse(controller.consumeSkipPhysicsThisFrame(),
                        "suppression must not leak into the first normal movement tick"));
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
                () -> assertEquals(AbstractPlayableSprite.STATUS_PUSHING,
                        diagnostics.pushBypassStatus() & 0xFF),
                () -> assertTrue(diagnostics.skipFollowSteering()));
    }

    @Test
    void normalPushBypassKeepsRomPushBitAcrossIsolatedEngineClearOnJumpCadence() {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(sonic3kWithSidekickContext(true));
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
            Arrays.fill(xHistory, (short) 0x1D40);
            Arrays.fill(yHistory, (short) 0x03C0);
            Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setLatchedSolidObjectId(0x03);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            controller.update(0x097F);
            tails.setPushing(false);
            controller.update(0x0980);

            Assertions.assertAll(
                    () -> assertTrue(controller.getInputJump(),
                            "ROM loc_13DD0 tests Tails' current Status_Push before loc_13E9C's frame gate"),
                    () -> assertTrue(controller.getInputJumpPress(),
                            "AIZ frame 2721 reaches loc_13E9C at Level_frame_counter=0x0980 even when the engine "
                                    + "has an isolated cleared push flag on that CPU tick"));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void objectOrderPushAutoJumpUsesFreshDelayedInputOnFirstAirborneTick() {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(sonic3kWithSidekickContext(true));
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
            Arrays.fill(xHistory, (short) 0x1D40);
            Arrays.fill(yHistory, (short) 0x03C0);
            Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setLatchedSolidObjectId(0x03);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            controller.update(0x097F);
            tails.setPushing(false);
            controller.update(0x0980);

            int nextHistoryPos = 21;
            int romDelaySlot = nextHistoryPos - SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES;
            int staleObjectOrderSlot = nextHistoryPos - 17;
            inputHistory[romDelaySlot] = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;
            inputHistory[staleObjectOrderSlot] = 0;
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, nextHistoryPos);
            tails.setAir(true);
            tails.setRolling(true);

            controller.update(0x0981);

            SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
            Assertions.assertAll(
                    () -> assertFalse("airborne_push_handoff".equals(diagnostics.followBranch()),
                            "S3K AIZ F2722 falls through loc_13DD0 after the push auto-jump; "
                                    + "it consumes the next 16-frame Stat_table Ctrl_1 word rather than "
                                    + "a stale object-order bridge sample (sonic3k.asm:26696-26729,28330-28401)."),
                    () -> assertEquals(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES,
                            diagnostics.followDelayFrames()),
                    () -> assertTrue(controller.getInputRight(),
                            "The fresh delayed word carries RIGHT+JUMP at AIZ F2722."),
                    () -> assertTrue(controller.getInputJump(),
                            "The jump hold from loc_13E9C remains live on the airborne follow frame."));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void normalPushGraceSuppressesGroundedFollowPulseInsideAizObjectBand() throws Exception {
        GameModule previous = GameModuleRegistry.getBootstrapDefault();
        try {
            installStandaloneGameModule(sonic3kWithSidekickContext(true));
            setCurrentZoneAct(0, 0);
            clearLoadedLevelForFeatureZoneFallback();

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setCentreX((short) 0x1CED);
            tails.setCentreY((short) 0x03C0);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x1D40);
            Arrays.fill(yHistory, (short) 0x03C0);
            Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setOnObject(true);
            sonic.setAir(false);
            sonic.setLatchedSolidObjectId(0x04);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            tails.setPushing(true);
            controller.update(0x0971);
            tails.setPushing(false);
            controller.update(0x0972);

            Assertions.assertAll(
                    () -> assertFalse(controller.getInputLeft()),
                    () -> assertFalse(controller.getInputRight(),
                            "S3K loc_13DD0 (sonic3k.asm:26702-26705) bypasses follow steering only while "
                                    + "Status_Push is currently set; AIZ object ordering keeps the engine-side "
                                    + "bridge active only while the vertical delta is still in the local object band"),
                    () -> assertFalse(controller.getInputJump(),
                            "Non-cadence bridge frames should not force loc_13E9C's jump press"));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void groundedPushGracePreservesCurrentDelayedControlWord() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1CE0);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D20);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = 0;
        statusHistory[4] = 0;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x13DE);
        tails.setPushing(false);
        controller.update(0x13DF);

        assertTrue(controller.getInputRight(),
                "CNZ grounded release mirrors ROM loc_13DD0: d4 tests the delayed Status_Push byte, "
                        + "but the already-loaded d1 Ctrl_2 word is preserved for the cylinder/P2 and "
                        + "Tails_InputAcceleration_Path paths (sonic3k.asm:26696-26705,26775-26785,"
                        + "67656-67672,27798-27805,28103-28122)");
    }

    @Test
    void s3kHurtRoutineRecordPosKeepsPreviousLogicalInputForFollowerHistory() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

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

    @Test
    void cnzDoorSupportGraceFallsThroughToFollowLeftNudgeWhenPushIsClear() {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            tails.setAir(false);
            tails.setOnObject(true);
            tails.setLatchedSolidObjectId(0x3C);
            tails.setCentreX((short) 0x0FEE);
            tails.setCentreY((short) 0x0231);
            tails.setDirection(Direction.LEFT);
            tails.setGSpeed((short) 0xFFF4);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x0FBE);
            Arrays.fill(yHistory, (short) 0x0231);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            tails.setPushing(true);
            controller.update(0x2310);
            tails.setPushing(false);
            controller.update(0x2311);

            assertEquals(0x0FED, tails.getCentreX() & 0xFFFF,
                    "With current Status_Push clear, CNZ Door support must fall through to "
                            + "FollowLeft and apply the -1 x_pos nudge (sonic3k.asm:26702-26724; "
                            + "Obj3C Door SolidObjectFull at sonic3k.asm:66249-66258)");
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void groundedPushGraceUsesCurrentControlWordOutsideAizObjectOrderBridge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setCentreX((short) 0x1CED);
        tails.setCentreY((short) 0x03C0);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        int historyPos = 20;
        inputHistory[3] = 0;
        inputHistory[4] = AbstractPlayableSprite.INPUT_RIGHT;
        statusHistory[3] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        statusHistory[4] = AbstractPlayableSprite.STATUS_ON_OBJECT;
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, historyPos);
        sonic.setOnObject(true);
        sonic.setAir(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0971);
        tails.setPushing(false);
        controller.update(0x0972);

        assertTrue(controller.getInputRight(),
                "Outside the AIZ object-order bridge, S3K loc_13DD0 keeps the already-loaded d1 "
                        + "Ctrl_2 word even when the delayed status has Status_OnObj. This fixture's "
                        + "current sample carries RIGHT; MGZ F1466 is the companion case where d1 is "
                        + "zero and the older RIGHT sample must not be re-read (sonic3k.asm:"
                        + "26696-26705,26775-26785).");
    }

    @Test
    void s3kFrameStartPushBypassesFollowSteeringAfterEngineSideClear() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1F35);
        tails.setCentreY((short) 0x049D);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0);
        tails.setXSpeed((short) 0x03B2);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x2175);
        Arrays.fill(yHistory, (short) 0x049D);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        tails.captureOnObjectAtFrameStart();
        tails.setPushing(false);
        controller.update(0x0C02);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "AIZ F3074 reads Tails' ROM Status_Push before the engine-side "
                                + "interact/touch path clears the local push flag. loc_13DD0 bypasses "
                                + "FollowRight with delayed stat/input both zero (sonic3k.asm:26702-26705)."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertFalse(controller.getInputRight(),
                        "The bypass preserves the already-loaded zero Ctrl_2 word until the next "
                                + "delayed RIGHT sample arrives."));
    }

    @Test
    void s3kClearedPushGraceStillAllowsGroundedFollowRightInput() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1F35);
        tails.setCentreY((short) 0x049D);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1F85);
        Arrays.fill(yHistory, (short) 0x038B);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0AE0);
        tails.setPushing(false);
        controller.update(0x0AE1);
        tails.setPushing(false);
        controller.update(0x0AE2);

        assertTrue(controller.getInputRight(),
                "At AIZ F3075, Tails' current Status_Push is clear, so ROM loc_13DD0 "
                        + "(sonic3k.asm:26702-26705) falls through to FollowRight; "
                        + "Tails_InputAcceleration_Path then consumes Ctrl_2_logical RIGHT "
                        + "and adds Acceleration_P2=$000C (sonic3k.asm:27798-27805,"
                        + "28103-28122)");
    }

    @Test
    void s3kLocalPushGraceNearAizSpikedLogsStillFallsThroughToFollowRight() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x2070);
        tails.setCentreY((short) 0x055F);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0xFFE2);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x20D4);
        Arrays.fill(yHistory, (short) 0x0505);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_LEFT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x2829);
        tails.setPushing(false);
        controller.update(0x282A);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ F10586 has only stale engine push grace near the spiked logs. "
                                + "ROM loc_13DD0 sees current Status_Push clear and falls through "
                                + "to FollowRight (sonic3k.asm:26702-26729)."),
                () -> assertFalse(diagnostics.skipFollowSteering(),
                        "Stale local grace outside the AIZ object-order bridge must not suppress "
                                + "FollowRight."),
                () -> assertTrue(controller.getInputRight(),
                        "FollowRight overrides the delayed LEFT control word when dx >= $30 "
                                + "(sonic3k.asm:26729-26740)."),
                () -> assertFalse(controller.getInputLeft()));
    }

    @Test
    void s3kFreshLowSpeedLocalPushGracePreservesInputWithoutNudgingNearAizSpikedLogs() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x210D);
        tails.setCentreY((short) 0x0541);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0042);
        tails.setXSpeed((short) 0x0040);
        tails.setYSpeed((short) 0x000C);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x218C);
        Arrays.fill(yHistory, (short) 0x0559);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 15);

        controller.update(0x29E0);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ2 F10720 falls through FollowRight with the delayed RIGHT input, "
                                + "but the local side-contact grace is still low-speed."),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "The early low-speed local push grace must not manufacture the +1 x_pos "
                                + "nudge before ROM's rebound threshold is reached."));
    }

    @Test
    void s3kAgedLocalPushGraceBelowTargetBypassesFollowSteeringNearAizSpikedLogs() throws Exception {
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
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 12);

        tails.setPushing(false);
        controller.update(0x29E4);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("grace_push_bypass", diagnostics.followBranch(),
                        "AIZ2 F10724 has stale local push grace with the delayed target at/below Tails. "
                                + "ROM still sees live Status_Push and branches through loc_13DD0."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertFalse(controller.getInputRight(),
                        "The preserved Ctrl_2 sample is zero, so Tails must not manufacture "
                                + "a FollowRight acceleration pulse before physics."));
    }

    @Test
    void s3kFreshLocalPushGraceBelowTargetResumesFollowSteeringAfterReboundRefresh() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x210D);
        tails.setCentreY((short) 0x0541);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0080);
        tails.setXSpeed((short) 0x007D);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x21A9);
        Arrays.fill(yHistory, (short) 0x0559);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) AbstractPlayableSprite.STATUS_UNDERWATER);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 13);
        controller.update(0x29F1);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ2 F10738 has a freshly refreshed local push grace after the rebound, "
                                + "but ROM current Status_Push is clear, so Ctrl_2 falls "
                                + "through normal FollowRight (sonic3k.asm:26702-26729)."),
                () -> assertFalse(diagnostics.skipFollowSteering()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F10738 has ROM current Status_Push clear after the rebound refresh, "
                                + "so FollowRight applies the +1 x_pos nudge."),
                () -> assertFalse(controller.getInputLeft()));
    }

    @Test
    void s3kHighSpeedAgedLocalPushGraceResumesFollowSteeringNearAizSpikedLogs() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x210F);
        tails.setCentreY((short) 0x0541);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0098);
        tails.setXSpeed((short) 0x0095);
        tails.setYSpeed((short) 0x001D);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x21AB);
        Arrays.fill(yHistory, (short) 0x0559);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 11);

        controller.update(0x29F3);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ2 F10739 has high-speed rebound motion; ROM current Status_Push is clear "
                                + "and no longer takes the local grace bypass."),
                () -> assertFalse(diagnostics.skipFollowSteering()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertTrue(controller.getInputDown()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "The high-speed rebound path applies FollowRight's +1 x_pos nudge."));
    }

    @Test
    void s3kLowRemainingLocalPushGraceExpiresToFollowSteeringNearAizSpikedLogs() throws Exception {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(sonic3kWithSidekickContext(true));
            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setObjectControlled(false);
            tails.setCentreX((short) 0x2117);
            tails.setCentreY((short) 0x0543);
            tails.setDirection(Direction.RIGHT);
            tails.setGSpeed((short) 0x0006);
            tails.setXSpeed((short) 0x0005);
            tails.setYSpeed((short) 0x0001);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x21AA);
            Arrays.fill(yHistory, (short) 0x0559);
            Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                    | AbstractPlayableSprite.INPUT_DOWN));
            Arrays.fill(statusHistory, (byte) AbstractPlayableSprite.STATUS_ON_OBJECT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
            setNormalPushingGraceFrames(controller, 3);

            controller.update(0x29FC);

            SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
            Assertions.assertAll(
                    () -> assertEquals("follow_steering", diagnostics.followBranch(),
                            "AIZ2 F10748 is past the ROM-visible local push bridge; Ctrl_2 falls "
                                    + "through normal FollowRight even though the engine grace counter "
                                    + "will report two frames left after the CPU step."),
                    () -> assertFalse(controller.usedObjectOrderGracePushBypassThisFrame()),
                    () -> assertFalse(diagnostics.skipFollowSteering()),
                    () -> assertTrue(controller.getInputRight()),
                    () -> assertTrue(controller.getInputDown()),
                    () -> assertEquals(1, diagnostics.appliedFollowNudge()));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kStaleCurrentPushFarBelowTargetFallsThroughToFollowLeftAfterAizReload() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x31CA);
        tails.setCentreY((short) 0x065E);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0);
        tails.setXSpeed((short) 0x007A);
        tails.setYSpeed((short) 0);
        tails.setPushing(true);
        tails.setInWater(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3171);
        Arrays.fill(yHistory, (short) 0x025C);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x37DE);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ2 F14302 has no ROM-visible current Status_Push at Tails_CPU_Control; "
                                + "the stale engine push bit must not take loc_13DD0's bypass "
                                + "(sonic3k.asm:26702-26729)."),
                () -> assertFalse(diagnostics.skipFollowSteering()),
                () -> assertTrue(controller.getInputLeft(),
                        "FollowLeft overrides the delayed RIGHT input when dx <= -$30."),
                () -> assertFalse(controller.getInputRight()),
                () -> assertEquals(AbstractPlayableSprite.INPUT_LEFT,
                        controller.getDiagnosticGeneratedHeldInput()
                                & (AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT),
                        "The ROM-visible Ctrl_2_Logical byte must follow the real FollowLeft branch."));
    }

    @Test
    void s3kUnderwaterCurrentPushPulseBypassesFollowLeftAfterAizReload() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x31CA);
        tails.setCentreY((short) 0x065E);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0);
        tails.setXSpeed((short) 0xFEBE);
        tails.setYSpeed((short) 0);
        tails.setPushing(true);
        tails.setInWater(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x316D);
        Arrays.fill(yHistory, (short) 0x025C);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_DOWN);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x37D7);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "AIZ2 F14295 has ROM-visible Status_Push at Tails_CPU_Control: "
                                + "the underwater side-contact pulse reaches loc_13DD0 before "
                                + "Tails_InputAcceleration_Path clears Status_Push "
                                + "(sonic3k.asm:26702-26705,27798-27805)."),
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
    void s3kCurrentPushFarBelowTargetBypassesFollowRightAfterAizIntro() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1F35);
        tails.setCentreY((short) 0x049D);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0);
        tails.setXSpeed((short) 0xFF18);
        tails.setYSpeed((short) 0);
        tails.setPushing(true);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x2235);
        Arrays.fill(yHistory, (short) 0x038C);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0AE4);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                        "AIZ F3074/F3077 has ROM-visible Status_Push at Tails_CPU_Control; "
                                + "loc_13DD0 must skip FollowRight even when dx is far "
                                + "(sonic3k.asm:26702-26729)."),
                () -> assertTrue(diagnostics.skipFollowSteering()),
                () -> assertFalse(controller.getInputLeft()),
                () -> assertFalse(controller.getInputRight()),
                () -> assertEquals(0,
                        controller.getDiagnosticGeneratedHeldInput()
                                & (AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT)));
    }

    @Test
    void s3kLocalPushGracePreservesFollowInputWithoutNudgingIntoAizIntroSpringWall() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x2835);
        tails.setCentreY((short) 0x0320);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x000C);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x28DF);
        Arrays.fill(yHistory, (short) 0x02E2);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0E86);
        tails.setPushing(false);
        controller.update(0x0E87);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch(),
                        "AIZ F4008 has no provider-owned object-order bridge, so Ctrl_2 still falls "
                                + "through the normal FollowRight path instead of a push-bypass branch."),
                () -> assertTrue(controller.getInputRight(),
                        "ROM preserves the delayed RIGHT control word and Tails_InputAcceleration_Path "
                                + "adds Acceleration_P2=$000C (sonic3k.asm:26712-26741,27798-27805,"
                                + "28103-28122)."),
                () -> assertFalse(controller.getInputLeft()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "The engine-side stale push grace represents a ROM-visible Status_Push timing "
                                + "bridge here; applying the +1 follow nudge manufactures a side contact "
                                + "with the AIZ intro spring wall before the ROM does."));
    }

    @Test
    void s3kHighRemainingFastLeaderLocalGraceStillSuppressesIntroSpringWallNudge() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x2835);
        tails.setCentreY((short) 0x0320);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x000C);
        tails.setXSpeed((short) 0x000C);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x292E);
        Arrays.fill(yHistory, (short) 0x0305);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x040E);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 15);

        controller.update(0x0FBE);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "AIZ F4030 still has high remaining local push grace near the intro "
                                + "spring wall. ROM keeps RIGHT acceleration but the spring-wall "
                                + "side contact owns the positional response, so FollowRight must "
                                + "not add a separate +1 x_pos nudge."));
    }

    @Test
    void s3kFastLeaderTinyDxKeepsAccelerationWithoutFollowNudge() throws Exception {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            installEmptyObjectManager();
            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setObjectControlled(false);
            tails.setCentreX((short) 0x02B5);
            tails.setCentreY((short) 0x02FF);
            tails.setDirection(Direction.RIGHT);
            tails.setGSpeed((short) 0x0018);
            LiveRideObject rideObject = new LiveRideObject(0x07);
            rideObject.setSlotIndex(36);
            GameServices.level().getObjectManager().addDynamicObject(rideObject);
            tails.setLatchedSolidObject(0x07, rideObject);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x02B7);
            Arrays.fill(yHistory, (short) 0x02BA);
            Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setGSpeed((short) 0x0600);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            controller.update(0x1524);

            SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
            Assertions.assertAll(
                    () -> assertEquals("leader_fast", diagnostics.followBranch()),
                    () -> assertTrue(controller.getInputRight()),
                    () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                            "AIZ2 F5708 takes the fast-leader path with dx=$0002 and a live "
                                    + "interact slot. The delayed RIGHT control word still accelerates "
                                    + "Tails, but applying the +1 nudge moves him into the spring-wall "
                                    + "push one frame before ROM."));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s2FastLeaderTinyDxStillAppliesFollowNudgeWhenLocalGraceIsPresent() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x02B5);
        tails.setCentreY((short) 0x02FF);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0018);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x02B7);
        Arrays.fill(yHistory, (short) 0x02BA);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0600);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 15);

        controller.update(0x1524);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "S2 has no S3K lead/grace bridge. The shared fast-leader branch must "
                                + "still run FollowRight's +1 x_pos nudge when only stale local "
                                + "engine grace is present."),
                () -> assertEquals(0x02B6, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderLocalPushGraceMiddleDxKeepsAccelerationWithoutFollowNudge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x02B5);
        tails.setCentreY((short) 0x02FF);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0018);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x02EB);
        Arrays.fill(yHistory, (short) 0x02B2);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0600);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x152E);
        tails.setPushing(false);
        controller.update(0x152F);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "AIZ2 F5719 remains inside the local push-grace vertical band with dx=$0036. "
                                + "ROM keeps the delayed RIGHT control word for acceleration but does "
                                + "not apply the +1 follow nudge before the spring-wall solid contact."));
    }

    @Test
    void s3kLocalPushGracePreservesFollowInputWithoutNudgingIntoAiz2VineWall() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x07B5);
        tails.setCentreY((short) 0x037D);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0030);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x07D6);
        Arrays.fill(yHistory, (short) 0x033B);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0325);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x16F2);
        tails.setPushing(false);
        controller.update(0x16F3);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("follow_steering", diagnostics.followBranch()),
                () -> assertTrue(controller.getInputRight()),
                () -> assertEquals(0, diagnostics.appliedFollowNudge(),
                        "AIZ2 F6171 keeps the delayed RIGHT control word for acceleration, but the "
                                + "engine-side stale push bridge represents ROM Status_Push=$20 and "
                                + "must not add the +1 follow nudge before solid resolution."));
    }

    @Test
    void s3kLeaderOnObjectLocalPushGraceStillAppliesFollowNudge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x0EC0);
        tails.setCentreY((short) 0x047E);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x00C0);
        sonic.setOnObject(true);
        sonic.captureOnObjectAtFrameStart();

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0EF1);
        Arrays.fill(yHistory, (short) 0x02FA);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_JUMP
                | AbstractPlayableSprite.INPUT_UP));
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_PUSHING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0600);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x1BFC);
        tails.setPushing(false);
        controller.update(0x1BFD);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_on_object", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F7164 has ROM current Status_Push clear on the leader-on-object branch, "
                                + "so stale local push grace must not suppress the +1 follow nudge."));
    }

    @Test
    void s3kFastLeaderPastSnapThresholdStillNudgesDuringLocalPushGrace() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x0EC2);
        tails.setCentreY((short) 0x047E);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x00CC);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0EF6);
        Arrays.fill(yHistory, (short) 0x02FD);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_PUSHING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0600);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x1BFD);
        tails.setPushing(false);
        controller.update(0x1BFE);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F7165 has dx=$0034, beyond S3K's $30 follow snap threshold; ROM "
                                + "applies the +1 follow nudge even with delayed Status_Push bits."));
    }

    @Test
    void s3kFastLeaderTinyDxStillNudgesWithoutInteractionSlot() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x13FB);
        tails.setCentreY((short) 0x0420);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x033C);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x13FE);
        Arrays.fill(yHistory, (short) 0x041C);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0600);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x04C9);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ F1514 has no latched interact slot, so the fast-leader path still "
                                + "applies ROM's +1 x_pos nudge (sonic3k.asm:26734-26741)."),
                () -> assertEquals(0x13FC, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderTinyDxStillNudgesAtLowLocalGraceWithoutLiveObjectNearAizBossRunout() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x332C);
        tails.setCentreY((short) 0x01FD);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0018);
        tails.setXSpeed((short) 0x0018);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3339);
        Arrays.fill(yHistory, (short) 0x01FC);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0547);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 13);

        controller.update(0x3725);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F14423 has a tiny fast-leader dx and no live interaction slot; "
                                + "ROM applies FollowRight's +1 x_pos nudge despite stale local "
                                + "grace from the earlier contact."),
                () -> assertEquals(0x332D, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderPastSnapThresholdStillNudgesAtLowLocalGraceWithoutLiveObjectNearAizBossRunout()
            throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x3336);
        tails.setCentreY((short) 0x01FD);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x004E);
        tails.setXSpeed((short) 0x004E);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3367);
        Arrays.fill(yHistory, (short) 0x01FA);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0547);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 5);

        controller.update(0x372E);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F14432 has dx=$0031 just beyond S3K's snap threshold, shallow "
                                + "vertical separation, and no live interaction slot; ROM current "
                                + "Status_Push is clear and FollowRight applies the +1 x_pos nudge."),
                () -> assertEquals(0x3337, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderMidDxLocalGraceNudgesAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x3693);
        tails.setCentreY((short) 0x01DF);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0006);
        tails.setXSpeed((short) 0x0006);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3709);
        Arrays.fill(yHistory, (short) 0x01D5);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x06A8);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 14);

        controller.update(0x3DBE);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15814 has dx=$0076, shallow vertical separation, and no live "
                                + "interaction slot; ROM current Status_Push is clear and "
                                + "FollowRight still applies the +1 x_pos nudge."),
                () -> assertEquals(0x3694, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderBoundaryDxLocalGraceKeepsNudgingAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x3695);
        tails.setCentreY((short) 0x01DF);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0012);
        tails.setXSpeed((short) 0x0012);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3716);
        Arrays.fill(yHistory, (short) 0x01CC);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0674);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 12);

        controller.update(0x3DC0);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15816 has dx=$0081 with ROM current Status_Push clear and no live "
                                + "interaction slot. The stale engine-local grace should not suppress "
                                + "FollowRight's +1 x_pos nudge."),
                () -> assertEquals(0x3696, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderLargeDxDelayedInputLocalGraceKeepsNudgingAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x3699);
        tails.setCentreY((short) 0x01DE);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0024);
        tails.setXSpeed((short) 0x0024);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3726);
        Arrays.fill(yHistory, (short) 0x01B9);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0674);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 9);

        controller.update(0x3DC3);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15819 has dx=$008E, delayed RIGHT input, ROM current Status_Push "
                                + "clear, and no live interaction slot. FollowRight still applies "
                                + "the +1 x_pos nudge while local engine grace is stale."),
                () -> assertEquals(0x369A, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderWideDxModerateGapLocalGraceKeepsNudgingAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x369B);
        tails.setCentreY((short) 0x01DE);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0030);
        tails.setXSpeed((short) 0x0030);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x372C);
        Arrays.fill(yHistory, (short) 0x01AC);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0674);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 7);

        controller.update(0x3DC5);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15821 has dx=$0091, dy=$FFCE, delayed RIGHT input, and no live "
                                + "ROM push bit. The stale local grace should still fall through "
                                + "FollowRight's +1 x_pos nudge."),
                () -> assertEquals(0x369C, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderLateLocalGraceWideGapKeepsNudgingAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x369F);
        tails.setCentreY((short) 0x01DE);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0042);
        tails.setXSpeed((short) 0x0042);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3731);
        Arrays.fill(yHistory, (short) 0x0197);
        Arrays.fill(inputHistory, (short) (AbstractPlayableSprite.INPUT_RIGHT
                | AbstractPlayableSprite.INPUT_DOWN));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0674);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 5);

        controller.update(0x3DD0);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15824 has dx=$0093, dy=$FFB9, delayed RIGHT input, and only "
                                + "late stale local push grace left (displayed as grace=4 after the "
                                + "counter tick). With no live interaction slot, ROM current "
                                + "Status_Push is clear and FollowRight still applies the +1 x_pos "
                                + "nudge."),
                () -> assertEquals(0x36A0, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kSmallDxLocalGraceFollowLeftNudgesAfterAiz2Reload() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x37A6);
        tails.setCentreY((short) 0x015E);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0xFFEE);
        tails.setXSpeed((short) 0xFFEE);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3787);
        Arrays.fill(yHistory, (short) 0x0159);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_LEFT);
        Arrays.fill(statusHistory, (byte) AbstractPlayableSprite.STATUS_FACING_LEFT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setOnObject(true);
        sonic.captureOnObjectAtFrameStart();

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 14);

        controller.update(0x3C2D);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals(-1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15405 has dx=$FFE1 inside S3K's snap threshold, delayed LEFT input, "
                                + "and shallow vertical separation. ROM falls through FollowLeft and "
                                + "applies the -1 x_pos nudge even while engine local push grace is live."),
                () -> assertEquals(0x37A5, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kSmallDxLocalGraceFollowLeftKeepsNudgingAfterAiz2ReloadInputClears() throws Exception {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x37A2);
        tails.setCentreY((short) 0x015E);
        tails.setDirection(Direction.LEFT);
        tails.setGSpeed((short) 0xFFDC);
        tails.setXSpeed((short) 0xFFDC);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x3786);
        Arrays.fill(yHistory, (short) 0x015A);
        Arrays.fill(statusHistory, (byte) AbstractPlayableSprite.STATUS_FACING_LEFT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setOnObject(true);
        sonic.captureOnObjectAtFrameStart();

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);
        setNormalPushingGraceFrames(controller, 11);

        controller.update(0x3C30);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals(-1, diagnostics.appliedFollowNudge(),
                        "AIZ2 F15408 has no delayed LEFT input left, but Tails is still moving left "
                                + "with dx=$FFE4 inside S3K's snap threshold. ROM still applies the "
                                + "-1 x_pos FollowLeft nudge while local grace is live."),
                () -> assertEquals(0x37A1, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderTinyDxStillNudgesWhenAirborneRollingBelowTarget() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setRolling(true);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x17E0);
        tails.setCentreY((short) 0x03E1);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) -0x0038);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x17E1);
        Arrays.fill(yHistory, (short) 0x02B4);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) (AbstractPlayableSprite.STATUS_IN_AIR
                | AbstractPlayableSprite.STATUS_ROLLING));
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x061A);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0909);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ F2313 is airborne+rolling with dx=$0001 and a large upward target gap; "
                                + "ROM still applies the fast-leader +1 x_pos nudge."),
                () -> assertEquals(0x17E1, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderTinyDxStillNudgesWhenFollowerAlreadyFastBelowTarget() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setRolling(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1863);
        tails.setCentreY((short) 0x0460);
        tails.setDirection(Direction.RIGHT);
        tails.setGSpeed((short) 0x0499);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x1873);
        Arrays.fill(yHistory, (short) 0x02C3);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        Arrays.fill(statusHistory, (byte) AbstractPlayableSprite.STATUS_IN_AIR);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
        sonic.setGSpeed((short) 0x0602);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0923);

        SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
        Assertions.assertAll(
                () -> assertEquals("leader_fast", diagnostics.followBranch()),
                () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                        "AIZ F2339 is grounded/non-rolling with a fast follower; ROM still applies "
                                + "the +1 x_pos nudge instead of treating it like the later low-speed "
                                + "spring-wall contact shape."),
                () -> assertEquals(0x1864, tails.getCentreX() & 0xFFFF));
    }

    @Test
    void s3kFastLeaderLiveSlotStillNudgesWhenDxIsOutsideTinyContactRange() throws Exception {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(new Sonic3kGameModule());
            installEmptyObjectManager();
            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setObjectControlled(false);
            tails.setCentreX((short) 0x2899);
            tails.setCentreY((short) 0x02E4);
            tails.setDirection(Direction.RIGHT);
            tails.setGSpeed((short) 0x0240);
            LiveRideObject rideObject = new LiveRideObject(0x07);
            rideObject.setSlotIndex(39);
            GameServices.level().getObjectManager().addDynamicObject(rideObject);
            tails.setLatchedSolidObject(0x07, rideObject);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x28B2);
            Arrays.fill(yHistory, (short) 0x02E6);
            Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setGSpeed((short) 0x0600);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            controller.update(0x10B5);

            SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
            Assertions.assertAll(
                    () -> assertEquals("leader_fast", diagnostics.followBranch()),
                    () -> assertEquals(1, diagnostics.appliedFollowNudge(),
                            "AIZ F4277 has dx=$0019 on the fast-leader path; even with an "
                                    + "engine live-slot surrogate, ROM still applies the +1 x_pos "
                                    + "nudge (sonic3k.asm:26734-26741)."));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kCurrentPushOutsideProviderBridgeSkipsSteeringWithoutAutoJumpBypass() {
        GameModule previous = GameModuleRegistry.getCurrent();
        try {
            installStandaloneGameModule(sonic3kWithSidekickContext(false));

            TestableSprite sonic = new TestableSprite("sonic");
            TestableSprite tails = new TestableSprite("tails_p2");
            tails.setCpuControlled(true);
            tails.setAir(false);
            tails.setCentreX((short) 0x3693);
            tails.setCentreY((short) 0x01E0);
            tails.setDirection(Direction.LEFT);
            tails.setGSpeed((short) 0);

            short[] xHistory = new short[64];
            short[] yHistory = new short[64];
            short[] inputHistory = new short[64];
            byte[] statusHistory = new byte[64];
            Arrays.fill(xHistory, (short) 0x36AF);
            Arrays.fill(yHistory, (short) 0x01DE);
            sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);
            sonic.setOnObject(true);
            sonic.setAir(false);
            sonic.setGSpeed((short) 0x07C0);

            SidekickCpuController controller = new SidekickCpuController(tails, sonic);
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
            controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

            tails.setPushing(true);
            controller.update(0x3C89);

            SidekickCpuController.NormalStepDiagnostics diagnostics = controller.getLatestNormalStepDiagnostics();
            Assertions.assertAll(
                    () -> assertEquals("current_push_bypass", diagnostics.followBranch(),
                            "S3K loc_13DD0 still bypasses follow steering on live Status_Push. "
                                    + "This f15803 shape has no provider-owned object-order bridge, so a "
                                    + "live push flag may skip FollowLeft/FollowRight but must not force "
                                    + "loc_13E9C's jump gate (sonic3k.asm:26702-26741,26775-26785)."),
                    () -> assertTrue(diagnostics.skipFollowSteering()),
                    () -> assertFalse(controller.getInputJumpPress(),
                            "The non-bridge push flag must not manufacture loc_13E9C's auto-jump/spindash release."));
        } finally {
            installStandaloneGameModule(previous);
        }
    }

    @Test
    void s3kFarTargetPushGraceDoesNotBypassAutoJumpHeightAndDistanceGates() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x1FB5);
        tails.setCentreY((short) 0x0480);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x2235);
        Arrays.fill(yHistory, (short) 0x038C);
        Arrays.fill(inputHistory, (short) AbstractPlayableSprite.INPUT_RIGHT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0B3F);
        tails.setPushing(false);
        controller.update(0x0B40);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputJump(),
                        "AIZ F3169 has only the engine-side push grace left while dy is outside "
                                + "the local object band; ROM does not branch from loc_13DD0 to "
                                + "loc_13E9C and must still pass the distance/height gates "
                                + "(sonic3k.asm:26702-26705,26760-26783)"),
                () -> assertFalse(controller.getInputJumpPress(),
                        "ROM keeps Tails grounded here instead of applying Tails_Jump's -$680 y_vel"),
                () -> assertTrue(controller.getInputRight(),
                        "With no push-bypass autojump, FollowRight leaves Ctrl_2_logical RIGHT and "
                                + "grounded Tails movement consumes Acceleration_P2=$000C "
                                + "(sonic3k.asm:27798-27805,28103-28122)"));
    }

    @Test
    void s3kLocalPushGraceOutsideAizObjectOrderDoesNotBypassAutoJumpGates() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setAir(false);
        tails.setObjectControlled(false);
        tails.setCentreX((short) 0x094C);
        tails.setCentreY((short) 0x03D8);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0980);
        Arrays.fill(yHistory, (short) 0x03D8);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        tails.setPushing(true);
        controller.update(0x0A3F);
        tails.setPushing(false);
        controller.update(0x0A40);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputJump(),
                        "MGZ F2623 has only engine-side push grace left. ROM loc_13DD0 "
                                + "tests current Status_Push, falls through to the normal "
                                + "distance/height gates, and does not apply Tails_Jump "
                                + "(sonic3k.asm:26702-26705,26760-26783,28531-28538)"),
                () -> assertFalse(controller.getInputJumpPress(),
                        "The stale local grace must not manufacture loc_13E9C's jump press "
                                + "outside the AIZ object-order bridge"));
    }

    @Test
    void airborneCurrentPushStillSkipsFollowNudge() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
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
    void normalPushBypassAutoJumpAllowsFirstAirborneFollowSteeringOutsideAizBridge() {
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
        Arrays.fill(xHistory, (short) 0x1D40);
        Arrays.fill(yHistory, (short) 0x03C0);
        Arrays.fill(statusHistory, AbstractPlayableSprite.STATUS_ON_OBJECT);
        sonic.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 20);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        controller.forceStateForTest(SidekickCpuController.State.NORMAL, 20);

        controller.update(0x0980);
        Assertions.assertAll(
                () -> assertTrue(controller.getInputJump(),
                        "ROM loc_13DD0 reaches loc_13E9C on a live push-bypass cadence"),
                () -> assertTrue(controller.getInputJumpPress(),
                        "The push-bypass writes the jump press before Tails enters the rolling airborne routine"));

        tails.setAir(true);
        tails.setRolling(true);
        tails.setPushing(false);
        controller.update(0x0981);

        Assertions.assertAll(
                () -> assertFalse(controller.getInputLeft()),
                () -> assertTrue(controller.getInputRight(),
                        "The first airborne tick after a push-bypass jump resumes "
                                + "follow steering immediately. MGZ1 F1472 carries RIGHT (input=7808) "
                                + "and Tails_InputAcceleration_Freespace applies +$18 x_vel "
                                + "(sonic3k.asm:26712-26741,28330-28401)."),
                () -> assertTrue(controller.getInputJump(),
                        "The held jump from loc_13E9C remains live during the one-frame airborne handoff"));

        controller.update(0x0982);

        assertTrue(controller.getInputRight(),
                "Follow steering should remain live on the next airborne tick as well");
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
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
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
            tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
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
    void s3kCatchUpFlightOnlyBlocksOnLeaderObjectControlSignBit() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

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

    private static GameModule sonic3kWithSidekickContext(boolean objectOrderContext) {
        LevelEventProvider provider = new LevelEventProvider() {
            @Override public void initLevel(int zone, int act) {}
            @Override public void update() {}

            @Override
            public boolean isSidekickObjectOrderFollowSteeringContext(
                    AbstractPlayableSprite sidekick,
                    AbstractPlayableSprite effectiveLeader) {
                return objectOrderContext;
            }

            @Override
            public boolean isSidekickDoorSupportGraceFollowSteeringContext(
                    AbstractPlayableSprite sidekick,
                    ObjectInstance ridingObject) {
                return false;
            }

        };
        return new Sonic3kGameModule() {
            @Override
            public LevelEventProvider getLevelEventProvider() {
                return provider;
            }
        };
    }

    private static void installStandaloneGameModule(GameModule module) {
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
    }

    private static void installEmptyObjectManager() throws Exception {
        Field field = GameServices.level().getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.level(), new ObjectManager(List.of(), null, 0, null, null));
    }

    private static void setNormalPushingGraceFrames(SidekickCpuController controller, int frames) throws Exception {
        Field field = SidekickCpuController.class.getDeclaredField("normalPushingGraceFrames");
        field.setAccessible(true);
        field.setInt(controller, frames);
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

}
