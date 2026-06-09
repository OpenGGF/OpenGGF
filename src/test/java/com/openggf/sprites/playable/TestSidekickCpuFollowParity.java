package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
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
