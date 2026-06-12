package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.WaterSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TestRespawnStrategies {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    static class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) { super(code, (short) 0, (short) 0); }
        @Override public void draw() {}
        @Override public void defineSpeeds() {}
        @Override protected void createSensorLines() {}
        void setPhysicsFeatureSetForTest(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }
    }

    static class GameplayWaterLineSystem extends WaterSystem {
        @Override
        public int getWaterLevelY(int zoneId, int actId) {
            return 0x0500;
        }

        @Override
        public int getGameplayWaterLevelY(int zoneId, int actId) {
            return 0x0520;
        }
    }

    @Test
    void tailsIsDefaultStrategy() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        assertInstanceOf(TailsRespawnStrategy.class, ctrl.getRespawnStrategy());
    }

    @Test
    void knucklesStrategyAlwaysBegins() {
        TestableSprite sk = new TestableSprite("knux_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        KnucklesRespawnStrategy strategy = new KnucklesRespawnStrategy(ctrl);
        // beginApproach always returns true for Knuckles
        assertTrue(strategy.beginApproach(sk, main));
    }

    @Test
    void sonicStrategyReturnsFalseWithoutLevel() {
        TestableSprite sk = new TestableSprite("sonic_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        SonicRespawnStrategy strategy = new SonicRespawnStrategy(ctrl);
        // No level loaded = no terrain = beginApproach returns false
        assertFalse(strategy.beginApproach(sk, main));
    }

    @Test
    void knucklesDropsAfterTimeout() {
        TestableSprite sk = new TestableSprite("knux_p2");
        sk.setCpuControlled(true);
        TestableSprite main = new TestableSprite("sonic");
        main.setX((short) 160);
        main.setY((short) 400);
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        KnucklesRespawnStrategy strategy = new KnucklesRespawnStrategy(ctrl);
        strategy.beginApproach(sk, main);

        // Run 180 frames (timeout)
        for (int i = 0; i < 180; i++) {
            strategy.updateApproaching(sk, main, i);
        }
        // After timeout, Knuckles should be in dropping phase
        // The next updateApproaching should not move horizontally
        // (We can't fully verify drop without physics, but we can verify
        // the approach doesn't complete before landing)
        boolean complete = strategy.updateApproaching(sk, main, 180);
        // Not complete yet — sidekick is still in air (no physics engine in unit test)
        assertFalse(complete, "Should not complete until landed");
    }
    @Test
    void tailsDoesNotCompleteFlyInWhenOnlyVerticalStepReachesTarget() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x01FF);

        assertFalse(strategy.updateApproaching(sk, main, 0),
                "ROM keeps Tails in fly-in mode when the vertical +/-1 step reaches the target this frame");
        assertEquals(0x0200, sk.getCentreY() & 0xFFFF);

        assertTrue(strategy.updateApproaching(sk, main, 1),
                "ROM exits fly-in once the pre-move vertical delta is already zero");
    }

    @Test
    void sonic2TailsRespawnPreservesExistingVelocity() {
        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        sk.setXSpeed((short) 0x041C);
        sk.setYSpeed((short) 0x0012);
        sk.setGSpeed((short) 0x041C);
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        assertTrue(strategy.beginApproach(sk, main));

        assertEquals((short) 0x041C, sk.getXSpeed(),
                "S2 TailsCPU_Respawn writes position/target fields but does not clear x_vel");
        assertEquals((short) 0x0012, sk.getYSpeed(),
                "S2 TailsCPU_Respawn writes position/target fields but does not clear y_vel");
        assertEquals((short) 0x041C, sk.getGSpeed(),
                "S2 TailsCPU_Respawn writes position/target fields but does not clear inertia");
    }

    @Test
    void tailsRespawnTargetYClampsToGameplayWaterLine() {
        GameplayModeContext mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(new GameplayWaterLineSystem(), mode.getParallaxManager(),
                mode.getTerrainCollisionManager(), mode.getCollisionSystem(),
                mode.getSpriteManager(), mode.getLevelManager());

        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setCpuControlled(true);
        sk.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        TestableSprite main = new TestableSprite("sonic");
        main.setCentreY((short) 0x0600);
        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0200);
        Arrays.fill(yHistory, (short) 0x0600);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        assertTrue(strategy.beginApproach(sk, main));
        assertFalse(strategy.updateApproaching(sk, main, 1));

        assertEquals(0x0510, strategy.diagnosticTargetY(0),
                "TailsCPU_Respawn clamps target_y to Water_Level_1-$10, not the non-oscillated base level");
    }

    @Test
    void sonic2DeadLeaderEntryUsesFlyingRoutineWithoutRespawnTeleport() {
        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        sk.setCpuControlled(true);
        sk.setCentreX((short) 0x1234);
        sk.setCentreY((short) 0x0456);
        sk.setRolling(true);
        sk.setOnObject(true);
        sk.setPushing(true);
        sk.setInWater(true);
        sk.setSpindash(true);
        sk.setSpindashCounter((short) 0x20);
        sk.setDirection(com.openggf.physics.Direction.LEFT);

        TestableSprite main = new TestableSprite("sonic");
        main.setDead(true);

        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        ctrl.hydrateFromRomCpuState(0x06, 0, 0, 0, true, 0, 0);
        ctrl.forceStateForTest(SidekickCpuController.State.NORMAL, 0);
        short preEntryX = sk.getCentreX();
        short preEntryY = sk.getCentreY();

        ctrl.update(0);

        assertEquals(SidekickCpuController.State.APPROACHING, ctrl.getState(),
                "S2 dead-Sonic branch enters TailsCPU_Flying routine 4, not the S3K auto-recovery routine");
        assertEquals(preEntryX, sk.getCentreX(),
                "TailsCPU_Normal dead-Sonic entry does not run TailsCPU_Respawn's teleport");
        assertEquals(preEntryY, sk.getCentreY());
        assertFalse(sk.getSpindash(), "ROM clears spindash_flag on dead-Sonic flight entry");
        assertEquals((short) 0, sk.getSpindashCounter());
        assertTrue(sk.getAir(), "ROM writes status=$02, leaving only Status_InAir set");
        assertFalse(sk.getRolling());
        assertFalse(sk.isOnObject());
        assertFalse(sk.getPushing());
        assertFalse(sk.isInWater());
        assertEquals(com.openggf.physics.Direction.RIGHT, sk.getDirection());
        assertTrue(sk.isObjectControlSuppressesMovement());
        assertEquals(1, ctrl.getDiagnosticJumpingFlag(),
                "S2 TailsCPU_Normal's dead-Sonic branch writes routine 4 and flight state "
                        + "without clearing Tails_CPU_jumping (s2.asm:39254-39264)");
    }

    @Test
    void sonic2RespawnRoutinePreservesCpuJumpingFlagOnFlyingEntry() {
        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        sk.setCpuControlled(true);

        TestableSprite main = new TestableSprite("sonic");
        main.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);
        main.setCentreX((short) 0x032C);
        main.setCentreY((short) 0x032C);
        main.setAir(false);
        main.setRollingJump(false);
        main.setInWater(false);
        main.setPreventTailsRespawn(false);

        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        ctrl.hydrateFromRomCpuState(0x06, 0, 0, 0, true, 0, 0);
        ctrl.forceStateForTest(SidekickCpuController.State.SPAWNING, 0);

        ctrl.update(0);

        assertEquals(SidekickCpuController.State.APPROACHING, ctrl.getState(),
                "S2 TailsCPU_Respawn writes Tails_CPU_routine=4 when the respawn gate fires");
        assertEquals(1, ctrl.getDiagnosticJumpingFlag(),
                "S2 TailsCPU_Respawn does not clear Tails_CPU_jumping "
                        + "(s2.asm:39116-39130); the normal filter clears it later only when grounded");
    }

    @Test
    void tailsRespawnBeginWritesNativeBit7FullControl() {
        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setObjectControlAllowsCpu(true);
        sk.setObjectControlSuppressesMovement(false);
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        assertTrue(strategy.beginApproach(sk, main));

        assertTrue(sk.isObjectControlled(), "Tails fly-in begins with object_control=$81");
        assertFalse(sk.isObjectControlAllowsCpu(),
                "object_control=$81 must clear stale bits-0-to-6 CPU allowance");
        assertTrue(sk.isObjectControlSuppressesMovement(),
                "object_control=$81 must suppress normal movement");
    }

    @Test
    void sonic3kTailsCatchUpRespawnClearsVelocity() {
        TestableSprite sk = new TestableSprite("tails_p2");
        sk.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        sk.setXSpeed((short) 0x041C);
        sk.setYSpeed((short) 0x0012);
        sk.setGSpeed((short) 0x041C);
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        assertTrue(strategy.beginApproach(sk, main));

        assertEquals((short) 0, sk.getXSpeed(),
                "S3K Tails_Catch_Up_Flying clears x_vel on the catch-up teleport");
        assertEquals((short) 0, sk.getYSpeed(),
                "S3K Tails_Catch_Up_Flying clears y_vel on the catch-up teleport");
        assertEquals((short) 0, sk.getGSpeed(),
                "S3K Tails_Catch_Up_Flying clears ground velocity on the catch-up teleport");
    }

    @Test
    void tailsCompletesFlyInWhenHorizontalCatchUpClosesRemainingGap() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0105);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);
        main.setXSpeed((short) 0x0500);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);

        assertTrue(strategy.updateApproaching(sk, main, 0),
                "ROM fly-in completion uses the post-horizontal residual, so closing the X gap this frame exits approach");
        assertEquals(0x0105, sk.getCentreX() & 0xFFFF);
        assertEquals(0x0200, sk.getCentreY() & 0xFFFF);
    }

    @Test
    void tailsFlyInCompletionCopiesLeaderCollisionBitsAndPriority() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x0100);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        main.setTopSolidBit((byte) 0x0E);
        main.setLrbSolidBit((byte) 0x0F);
        main.setHighPriority(true);

        sk.setTopSolidBit((byte) 0x0C);
        sk.setLrbSolidBit((byte) 0x0D);
        sk.setHighPriority(false);
        sk.setMoveLockTimer(13);
        sk.setObjectControlAllowsCpu(true);
        sk.setObjectControlSuppressesMovement(true);
        sk.setRolling(true);
        sk.setOnObject(true);
        sk.setPushing(true);
        sk.setInWater(true);
        sk.setDirection(com.openggf.physics.Direction.LEFT);
        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);

        ctrl.setInitialState(SidekickCpuController.State.APPROACHING);
        ctrl.update(0);

        assertEquals(SidekickCpuController.State.NORMAL, ctrl.getState());
        assertEquals(0x0E, sk.getTopSolidBit() & 0xFF);
        assertEquals(0x0F, sk.getLrbSolidBit() & 0xFF);
        assertTrue(sk.isHighPriority());
        assertEquals(0, sk.getMoveLockTimer(),
                "Tails_Catch_Up_Flying exit clears move_lock before normal CPU control resumes");
        assertEquals(sk.resolveAnimationId(CanonicalAnimation.WALK), sk.getAnimationId(),
                "S2 TailsCPU_Flying completion writes AniIDSonAni_Walk");
        assertTrue(sk.getAir(), "S2 TailsCPU_Flying completion writes status=$02");
        assertFalse(sk.getRolling());
        assertFalse(sk.isOnObject());
        assertFalse(sk.getPushing());
        assertFalse(sk.isInWater());
        assertEquals(com.openggf.physics.Direction.RIGHT, sk.getDirection());
        assertFalse(sk.isObjectControlled(),
                "Tails_Catch_Up_Flying exit clears object_control before normal CPU control resumes");
        assertFalse(sk.isObjectControlAllowsCpu(),
                "Tails_Catch_Up_Flying exit clears the object-control CPU allowance mirror");
        assertFalse(sk.isObjectControlSuppressesMovement(),
                "Tails_Catch_Up_Flying exit clears the object-control movement suppression mirror");
    }

    @Test
    void tailsFlyInUsesSignedHighByteOfLeaderVelocityForHorizontalBonus() {
        TestableSprite sk = new TestableSprite("tails_p2");
        TestableSprite main = new TestableSprite("sonic");
        SidekickCpuController ctrl = new SidekickCpuController(sk, main);
        TailsRespawnStrategy strategy = new TailsRespawnStrategy(ctrl);

        short[] xHistory = new short[64];
        short[] yHistory = new short[64];
        short[] inputHistory = new short[64];
        byte[] statusHistory = new byte[64];
        Arrays.fill(xHistory, (short) 0x010B);
        Arrays.fill(yHistory, (short) 0x0200);
        main.hydrateRecordedHistory(xHistory, yHistory, inputHistory, statusHistory, 16);

        sk.setCentreX((short) 0x0100);
        sk.setCentreY((short) 0x0200);
        main.setXSpeed((short) 0xFF82);

        assertFalse(strategy.updateApproaching(sk, main, 0));
        assertEquals(0x0102, sk.getCentreX() & 0xFFFF,
                "ROM uses the signed high byte of Sonic's x_vel during Tails fly-in, so 0xFF82 adds a +1 speed bonus");
    }
}

