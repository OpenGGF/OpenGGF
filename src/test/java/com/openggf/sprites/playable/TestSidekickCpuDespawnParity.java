package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.PerObjectRewindSnapshot.SidekickCpuRewindExtra;
import com.openggf.physics.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuDespawnParity {

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
        public void defineSpeeds() {
            runHeight = 38;
            rollHeight = 28;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
            setWidth(18);
            setHeight(runHeight);
        }

        @Override
        protected void createSensorLines() {}

        void usePhysicsFeatureSet(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }

        void useS3kTailsRadii() {
            runHeight = 30;
            rollHeight = 28;
            standXRadius = 9;
            standYRadius = 15;
            rollXRadius = 7;
            rollYRadius = 14;
            setHeight(runHeight);
            restoreDefaultRadii();
        }
    }

    private static final class DestroyedRideObject extends AbstractObjectInstance {
        private DestroyedRideObject(int objectId) {
            super(new ObjectSpawn(0x1200, 0x0800, objectId, 0, 0, false, 0), "DestroyedRideObject");
            setDestroyed(true);
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

    private static final class UnloadedRideObject extends AbstractObjectInstance {
        private UnloadedRideObject(int objectId) {
            super(new ObjectSpawn(0x1200, 0x0800, objectId, 0, 0, false, 0), "UnloadedRideObject");
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
    void levelBoundsPreserveWriteOnlyMinYAcrossRewind() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setLevelBounds(null, null, 0x0400, null);

        assertEquals(0x0400, controller.getMinYBound(0x0123),
                "S2 Tails_Min_Y_pos is write-only in the ROM, but the engine should still preserve the value");

        SidekickCpuRewindExtra snapshot = controller.captureRewindState();
        controller.setLevelBounds(0x1111, 0x2222, 0x0333, 0x0444);
        controller.restoreRewindState(snapshot);

        assertEquals(0x0400, controller.getMinYBound(Integer.MIN_VALUE),
                "Sidekick min-Y bound should round-trip through rewind snapshots");
        assertEquals(Integer.MIN_VALUE, controller.getMinXBound(Integer.MIN_VALUE),
                "Unwritten min-X bound should remain unset");
        assertEquals(Integer.MIN_VALUE, controller.getMaxYBound(Integer.MIN_VALUE),
                "Unwritten max-Y bound should remain unset");
    }

    @Test
    void despawnPreservesMotionAndMatchesRomStatusReset() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setRolling(true);
        tails.setRollingJump(true);
        tails.setOnObject(true);
        tails.setPushing(true);
        tails.setDirection(Direction.LEFT);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setSubpixelRaw(0x0300, 0x2700);
        tails.setXSpeed((short) 0x041C);
        tails.setYSpeed((short) 0x0000);
        tails.setGSpeed((short) 0x041C);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn();

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x4000, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals(0x0300, tails.getXSubpixelRaw());
        assertEquals(0x2700, tails.getYSubpixelRaw());
        assertEquals((short) 0x041C, tails.getXSpeed());
        assertEquals((short) 0x0000, tails.getYSpeed());
        assertEquals((short) 0x041C, tails.getGSpeed());
        assertTrue(tails.getAir());
        assertFalse(tails.getRolling());
        assertFalse(tails.getRollingJump());
        assertFalse(tails.isOnObject());
        assertFalse(tails.getPushing());
        assertEquals(Direction.RIGHT, tails.getDirection());
        assertTrue(tails.isControlLocked());
        assertTrue(tails.isObjectControlled());
    }

    @Test
    void s2FlyingRespawnTimeoutReturnsToSpawningAtZeroMarker() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x2375);
        tails.setCentreY((short) 0x03D9);
        tails.setSubpixelRaw(0x2300, 0xE000);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0x00A8);
        tails.setGSpeed((short) 0xFFD0);
        tails.setRolling(true);
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.APPROACHING, 0);

        for (int i = 0; i < 300; i++) {
            tails.setRenderFlagOnScreen(false);
            controller.update(i);
        }

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x0000, tails.getCentreX(),
                "S2 TailsCPU_Flying timeout writes x_pos=0, not the normal $4000 despawn marker");
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals(0x2300, tails.getXSubpixelRaw());
        assertEquals(0xE000, tails.getYSubpixelRaw());
        assertTrue(tails.getAir());
        assertFalse(tails.getRolling());
        assertTrue(tails.isObjectControlled());
    }

    @Test
    void s2FlyingOffscreenCounterCarriesIntoNormalDespawnAfterApproachCompletes() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setCentreX((short) 0x2908);
        sonic.setCentreY((short) 0x0691);
        sonic.resetPositionAndStatTableHistory();

        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x2908);
        tails.setCentreY((short) 0x0682);
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.APPROACHING, 0);

        for (int i = 0; i < 15; i++) {
            tails.setRenderFlagOnScreen(false);
            controller.update(i);
            assertEquals(SidekickCpuController.State.APPROACHING, controller.getState(),
                    "The test setup should keep accumulating the S2 flying off-screen counter before landing");
        }

        tails.setRenderFlagOnScreen(false);
        controller.update(15);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState(),
                "The approach completion frame should carry the accumulated flying counter into NORMAL");

        for (int i = 16; i < 299; i++) {
            tails.setRenderFlagOnScreen(false);
            controller.update(i);
            assertEquals(SidekickCpuController.State.NORMAL, controller.getState(),
                    "S2 normal despawn should not fire before the shared counter reaches 300");
        }

        tails.setRenderFlagOnScreen(false);
        controller.update(299);

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x4000, tails.getCentreX(),
                "After TailsCPU_Flying lands, S2 TailsCPU_CheckDespawn continues the same counter and uses $4000");
        assertEquals((short) 0x0000, tails.getCentreY());
    }

    @Test
    void s3kDespawnMarkerReturnsToCatchUpFlightRoutine() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setSubpixelRaw(0x0300, 0x2700);
        tails.setXSpeed((short) 0x041C);
        tails.setYSpeed((short) 0x0020);
        tails.setGSpeed((short) 0x041C);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn();

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "S3K sub_13ECA writes Tails_CPU_routine=2, not the S2 SPAWNING approach");
        assertEquals((short) 0x7F00, tails.getCentreX(),
                "S3K despawn marker uses x_pos=$7F00");
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals(0x0300, tails.getXSubpixelRaw());
        assertEquals(0x2700, tails.getYSubpixelRaw());
        assertEquals((short) 0x041C, tails.getXSpeed());
        assertEquals((short) 0x0020, tails.getYSpeed());
        assertEquals((short) 0x041C, tails.getGSpeed());
        assertEquals(0, tails.getDoubleJumpFlag(),
                "S3K sub_13ECA clears double_jump_flag");
        assertTrue(tails.getAir());
        assertTrue(tails.isControlLocked());
        assertTrue(tails.isObjectControlled());
    }

    @Test
    void s3kDespawnMarkerClearsRollStatusWithoutRestoringRadii() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.useS3kTailsRadii();
        tails.setCpuControlled(true);
        tails.setRolling(true);
        tails.setCentreX((short) 0x1E8F);
        tails.setCentreY((short) 0x057B);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn();

        assertFalse(tails.getRolling(),
                "S3K sub_13ECA writes status=Status_InAir and clears Status_Roll");
        assertEquals(7, tails.getXRadius(),
                "S3K sub_13ECA does not restore x_radius");
        assertEquals(14, tails.getYRadius(),
                "S3K sub_13ECA does not restore y_radius");
    }

    @Test
    void levelBoundaryKillRunsTailsTouchFloorBeforeDeathState() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.useS3kTailsRadii();
        tails.setCpuControlled(true);
        tails.setRolling(true);
        tails.setRollingJump(true);
        tails.setCentreX((short) 0x2D90);
        tails.setCentreY((short) 0x0402);
        tails.setAir(true);
        tails.setPushing(true);
        tails.setAngle((byte) 0x00);
        tails.setXSpeed((short) 0x0000);
        tails.setYSpeed((short) 0x0198);
        tails.setGSpeed((short) 0x0000);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.setInitialState(SidekickCpuController.State.NORMAL);

        controller.despawn(SidekickCpuController.DespawnCause.LEVEL_BOUNDARY);

        assertEquals(SidekickCpuController.State.DEAD_FALLING, controller.getState(),
                "S3K Kill_Character leaves Tails in object routine 6 for one frame");
        // setRollingJump(true) mirrors Sonic_Jump restoring default radii while
        // leaving Status_Roll set. S3K Tails_TouchFloor computes the y_pos
        // shift from current y_radius - default_y_radius
        // (sonic3k.asm:29133-29156), so this setup has no radius delta.
        assertEquals((short) 0x0402, tails.getCentreY(),
                "Tails_TouchFloor uses the current y_radius byte, not sprite height");
        assertEquals(15, tails.getYRadius());
        assertFalse(tails.getRolling());
        assertTrue(tails.getAir(), "Kill_Character sets Status_InAir after Tails_TouchFloor");
        assertFalse(tails.getRollingJump());
        assertFalse(tails.getPushing());
        assertEquals((short) 0x0000, tails.getXSpeed());
        // ROM Kill_Character (sonic3k.asm:21149) writes y_vel=-$700, NOT zero.
        // The kill is reached via `jmp` from Tails_Check_Screen_Boundaries
        // (sonic3k.asm:28443), and Kill_Character's `rts` (sonic3k.asm:21159)
        // unwinds to the caller of Tails_Check_Screen_Boundaries
        // (e.g. Tails_Stand_Path at sonic3k.asm:27526), which then runs
        // MoveSprite_TestGravity2 (->MoveSprite2 at sonic3k.asm:36088,36053)
        // applying the negative y-velocity to y_pos in the same frame.
        // Trace AIZ F7171 records the post-shift state with y_vel=-$700
        // retained.
        assertEquals((short) -0x700, tails.getYSpeed());
        assertEquals((short) 0x0000, tails.getGSpeed());
    }

    @Test
    void s3kDeadFallWaitsForCameraYPlus100BeforeDespawnMarker() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x493F);
        tails.setCentreY((short) 0x022F);
        tails.setSubpixelRaw(0xBA00, 0xB400);
        tails.setAir(true);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) -0x0648);
        tails.setGSpeed((short) 0);
        GameServices.camera().setY((short) 0x015A);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.DEAD_FALLING, 0);
        controller.setLevelBounds(null, null, 0x015A);

        controller.update(0x47A2);

        assertEquals(SidekickCpuController.State.DEAD_FALLING, controller.getState(),
                "S3K sub_123C2 returns while y_pos <= Camera_Y_pos+$100; it does not call "
                        + "sub_13ECA yet (sonic3k.asm:24549-24565,24578)");
        assertEquals((short) 0x493F, tails.getCentreX());
        assertEquals((short) 0x022F, tails.getCentreY());
        assertEquals((short) -0x0648, tails.getYSpeed());
        assertTrue(controller.isDeferredDespawnDeadFallContinuingThisFrame(),
                "The subsequent movement phase owns the dead-fall MoveSprite_TestGravity step");
    }

    @Test
    void s3kDeadFallAppliesDespawnMarkerAfterCameraYPlus100Threshold() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x493F);
        tails.setCentreY((short) 0x0260);
        tails.setSubpixelRaw(0xBA00, 0xB400);
        tails.setAir(true);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) -0x0700);
        tails.setGSpeed((short) 0);
        GameServices.camera().setY((short) 0x015A);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.forceStateForTest(SidekickCpuController.State.DEAD_FALLING, 0);
        controller.setLevelBounds(null, null, 0x015A);

        controller.update(0x47A3);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "Once y_pos exceeds Camera_Y_pos+$100, sub_123C2 branches to sub_13ECA "
                        + "(sonic3k.asm:24565-24578,26800-26809)");
        assertEquals((short) 0x7F00, tails.getCentreX());
        assertEquals((short) -0x0007, tails.getCentreY(),
                "After sub_13ECA writes y_pos=0, loc_157C8 still runs MoveSprite_TestGravity "
                        + "with the old y_vel (sonic3k.asm:29284-29285,36032-36042)");
        assertEquals((short) -0x06C8, tails.getYSpeed());
    }

    @Test
    void s3kOffscreenDestroyedRideSlotDespawnsEvenWhenInteractIdIsUnchanged() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x12BE);
        tails.setCentreY((short) 0x08A9);
        tails.setAir(false);
        tails.setOnObject(true);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x4E, false, 0, 0);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(2262);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "S3K sub_13EFC reads a freed ride slot as a mismatch and jumps through sub_13ECA");
        assertEquals((short) 0x7F00, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertTrue(tails.getAir());
    }

    @Test
    void s3kOffscreenUnloadedRideSlotDespawnsEvenWhenInstanceWasNotDestroyed() throws Exception {
        installEmptyObjectManager();
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x142C);
        tails.setCentreY((short) 0x0AB0);
        tails.setAir(false);
        tails.setOnObject(true);
        tails.setLatchedSolidObject(0x47, new UnloadedRideObject(0x47));
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x47, false, 0, 0);
        tails.setLatchedSolidObject(0x47, new UnloadedRideObject(0x47));
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(18917);

        assertEquals(SidekickCpuController.State.CATCH_UP_FLIGHT, controller.getState(),
                "S3K sub_13EFC compares the cached interact word with a freed slot; "
                        + "counter-window unload leaves the engine instance inactive rather than destroyed "
                        + "(sonic3k.asm:26816-26833,36116-36124)");
        assertEquals((short) 0x7F00, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertEquals((short) 0x0000, tails.getYSpeed());
        assertTrue(tails.getAir());
    }

    @Test
    void s3kDiagnosticInteractDefaultsToClearedRomWord() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);

        assertEquals(0, controller.getDiagnosticInteractId(),
                "S3K Tails_CPU_interact is cleared with active play RAM and starts at word 0 "
                        + "(docs/skdisasm/sonic3k.asm:5415,7621,26816-26843)");
    }

    @Test
    void s2DiagnosticInteractKeepsUnsetSentinelUntilFirstSnapshotRefresh() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);

        assertEquals(-1, controller.getDiagnosticInteractId(),
                "S2 keeps the engine unset sentinel until TailsCPU_UpdateObjInteract seeds "
                        + "the id snapshot used by the slot-mismatch despawn guard");
    }

    @Test
    void s3kDiagnosticInteractDoesNotExposeS2ObjectIdSnapshot() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x35, false, 0, 0);

        assertEquals(0, controller.getDiagnosticInteractId(),
                "S3K Tails_CPU_interact is a pointer-word diagnostic, not the S2 "
                        + "Tails_interact_ID object-id snapshot");
    }

    private static void installEmptyObjectManager() throws Exception {
        var field = GameServices.level().getClass().getDeclaredField("objectManager");
        field.setAccessible(true);
        field.set(GameServices.level(), new ObjectManager(List.of(), null, 0, null, null));
    }

    @Test
    void s2DestroyedRideSlotDespawnsThroughFreedObjectIdMismatch() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x12BE);
        tails.setCentreY((short) 0x08A9);
        tails.setAir(false);
        tails.setOnObject(true);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x4E, false, 0, 0);
        tails.setLatchedSolidObject(0x4E, new DestroyedRideObject(0x4E));
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(2262);

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState(),
                "S2 TailsCPU_CheckDespawn compares the cached interact ID against the freed slot's id byte");
        assertEquals((short) 0x4000, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
        assertTrue(tails.getAir());
    }

    @Test
    void offscreenObjectSwitchDespawnsUsingLatchedInteractObjectId() throws Exception {
        installEmptyObjectManager();
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0545);
        tails.setCentreY((short) 0x0270);
        tails.setAir(false);
        tails.setOnObject(true);
        // Tails RODE object 0x11 via the interact slot (RideObject_SetRide,
        // s2.asm:35980-36006), so interact(a0) points at a real slot index and
        // the latched Tails_interact_ID snapshot is 0x11. Off-screen the ridden
        // object is deleted/recycled away, so the live slot now reads ROM id 0
        // (DeleteObject zeroes the object RAM, s2.asm:30324-30339). 0 != 0x11 ->
        // TailsCPU_CheckDespawn mismatch -> TailsCPU_Despawn (s2.asm:39403-39429).
        // This is the ridden-then-emptied case (interactSlotIndex >= 0), distinct
        // from a never-ridden sidekick (interactSlotIndex < 0) whose slot ROM
        // reads as the default MainCharacter id 0x01.
        UnloadedRideObject rideObj = new UnloadedRideObject(0x11);
        rideObj.setSlotIndex(0x20);
        tails.setLatchedSolidObject(0x11, rideObj);
        // The ride object is NOT added to the manager, so the live interact slot
        // dereferences to nothing (engine -1) -> ROM zeroed slot id 0.

        GameServices.camera().setX((short) 0x058C);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 90, 0x11, false, 0, 0);
        tails.setLatchedSolidObject(0x11, rideObj);
        tails.setOnObject(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(1131);

        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());
        assertEquals((short) 0x4000, tails.getCentreX());
        assertEquals((short) 0x0000, tails.getCentreY());
    }

    @Test
    void renderFlagBottomMarginKeepsDespawnTimerReset() {
        TestableSprite sonic = new TestableSprite("sonic");
        sonic.setCentreX((short) 0x0610);
        sonic.setCentreY((short) 0x0200);

        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0610);
        tails.setCentreY((short) 0x02F0);
        tails.setAir(true);

        GameServices.camera().setX((short) 0x058C);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 299, 0x11, false, 0, 0);
        tails.setRenderFlagOnScreen(true);

        controller.update(3532);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertNotEquals((short) 0x4000, tails.getCentreX());
        assertNotEquals((short) 0x0000, tails.getCentreY());
    }

    @Test
    void s3kHurtRoutineDoesNotAdvanceNormalDespawnTimer() {
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0B04);
        tails.setCentreY((short) 0x0DF2);
        tails.setAir(true);
        tails.setHurt(true);
        tails.setRenderFlagOnScreen(false);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 299, 0, false, 0, 0);
        tails.setHurt(true);
        tails.setRenderFlagOnScreen(false);

        controller.update(1910);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState(),
                "S3K Tails_Index routine 4 dispatches the hurt/object path, not Tails_Control");
        assertEquals((short) 0x0B04, tails.getCentreX());
        assertEquals((short) 0x0DF2, tails.getCentreY());
        assertTrue(tails.getAir());
        assertEquals("sidekick_hurt_object_routine",
                controller.getLatestNormalStepDiagnostics().followBranch());
    }

    @Test
    void despawnTimerUsesCachedRenderFlagInsteadOfCurrentCameraGeometry() throws Exception {
        installEmptyObjectManager();
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0700);
        tails.setCentreY((short) 0x0200);
        tails.setOnObject(true);
        // The interact(a0) slot still holds the same object 0x11 (it was NOT
        // recycled or deleted), so TailsCPU_CheckDespawn's id compare matches and
        // the off-screen path falls through to the respawn timer. Model that with
        // a live same-id object in the dereferenced slot so this test isolates the
        // render-flag-vs-camera behaviour rather than an empty-slot artifact.
        UnloadedRideObject rideObj = new UnloadedRideObject(0x11);
        rideObj.setSlotIndex(0x11);
        GameServices.level().getObjectManager().addDynamicObject(rideObj);
        tails.setLatchedSolidObject(0x11, rideObj);

        GameServices.camera().setX((short) 0x05A0);
        GameServices.camera().setY((short) 0x0200);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 299, 0x11, false, 0, 0);
        tails.setLatchedSolidObject(0x11, rideObj);
        tails.setOnObject(true);

        tails.setRenderFlagOnScreen(true);
        controller.update(1);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());

        tails.setRenderFlagOnScreen(false);
        controller.update(2);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
    }

    @Test
    void onScreenEmptyInteractSlotRefreshesCachedInteractIdToZero() throws Exception {
        installEmptyObjectManager();
        TestableSprite sonic = new TestableSprite("sonic");
        TestableSprite tails = new TestableSprite("tails_p2");
        tails.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        tails.setCpuControlled(true);
        tails.setCentreX((short) 0x0399);
        tails.setCentreY((short) 0x03C2);
        tails.setAir(false);

        UnloadedRideObject oldRide = new UnloadedRideObject(0x2B);
        oldRide.setSlotIndex(0x11);
        tails.setLatchedSolidObject(0x2B, oldRide);
        tails.setOnObject(false);
        tails.setRenderFlagOnScreen(true);

        SidekickCpuController controller = new SidekickCpuController(tails, sonic);
        controller.hydrateFromRomCpuState(6, 0, 0, 0x2B, false, 0, 0);
        tails.setLatchedSolidObject(0x2B, oldRide);
        tails.setOnObject(false);
        tails.setRenderFlagOnScreen(true);

        controller.update(372);

        assertEquals(0, controller.getDiagnosticInteractId(),
                "S2 TailsCPU_UpdateObjInteract writes id(Object_RAM[interact]) every "
                        + "non-despawning frame; a deleted slot reads id 0 "
                        + "(docs/s2disasm/s2.asm:39435-39445)");
    }
}
