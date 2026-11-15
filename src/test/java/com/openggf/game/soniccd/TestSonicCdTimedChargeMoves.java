package com.openggf.game.soniccd;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sonic CD's timed charge model, look double tap, roll-entry speed floor and roll-jump
 * steering on a flat stretch of Palmtree Panic Act 1 Present: the K2 native trace shows
 * angle 0 on the ground at y=$7BC from x=$48F to $663 (rows 430-495). The start
 * position is a gentle $FC slope, so the tests move there first. Every expected value is worked
 * from scddisasm {@code src/Level/_Objects/Sonic/Main.asm} (pin 1ecc5f3a) by hand; the
 * K2 native fixture does not exercise these moves. Optional user media, as the other
 * Sonic CD disc tests.
 */
class TestSonicCdTimedChargeMoves {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private CdDiscDataSource disc;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;

    @BeforeEach
    void boot() throws Exception {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        EngineContext old = EngineServices.current();
        EngineContext injected = new EngineContext(config, old.graphics(), old.audio(), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(),
                old.crossGameFeatures());
        EngineServices.configure(injected);
        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());
        sonic = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config).mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
        // As the game boots a stage: the title card holds the controls until it leaves
        // (Level/Main.asm:272-274; Title Card/Main.asm:181-186), and the pad drives the player after that.
        CdFixtureReplay.releaseTitleCard(runner);
        runner.stepIdleFrames(2);
        sonic.setCentreX((short) 0x500);
        sonic.setCentreY((short) 0x7BC);
        runner.stepIdleFrames(4);
        assertFalse(sonic.getAir());
        assertEquals(0, sonic.getGSpeed());
        assertEquals(0, sonic.getAngle() & 0xFF, "the fixture stands on flat ground");
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (disc != null) disc.close();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
    }

    private int charge() {
        return ((PlayableSpriteMovement) sonic.getMovementManager()).timedChargeTimer();
    }

    private void step(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        runner.stepFrame(up, down, left, right, jump);
    }

    @Test
    void fullSpinDashReleasesAtTwiceTopSpeedLessRollFriction() {
        step(false, true, false, false, false);           // first Down press arms the tap timer
        step(false, true, false, false, true);            // Down held + A: .CheckSpindash arms it
        // gvel $16 -> StartRoll floor $200 -> Settle -$D = $1F3 (x_vel) -> CheckRoll floor $200. The
        // standing tail called ObjGetFloorDist (.BalanceGround), which leaves d5 = $D for .Settle
        // (Main.asm:903-904, 1140-1163; Collision Check.asm:157-169).
        assertEquals(1, charge());
        assertTrue(sonic.getRolling());
        assertFalse(sonic.getAir(), "ObjSonic_CheckJump refuses a jump while Down is held at rest");
        assertEquals(0x200, sonic.getGSpeed());
        assertEquals(0x1F3, sonic.getXSpeed());
        short xAfterArm = sonic.getCentreX();

        step(false, true, false, false, false);           // timer 2; .Spindash +75; rts; no ObjMove
        assertEquals(2, charge());
        assertEquals(0x200 + 75, sonic.getGSpeed());
        assertEquals(xAfterArm, sonic.getCentreX(), "MdRoll skips ObjMove while charging");

        for (int i = 0; i < 60; i++) step(false, true, false, false, false);
        assertEquals(45, charge(), "the spin dash timer caps at 45");
        assertEquals(0xC00, sonic.getGSpeed(), "charge speed caps at twice top speed");

        step(false, false, false, false, false);          // release at full charge
        assertEquals(0, charge());
        assertTrue(sonic.getRolling());
        assertEquals(0xC00 - 6, sonic.getGSpeed(), "MoveRoll .Settle subtracts acceleration/2");
        assertEquals(Direction.RIGHT, sonic.getDirection());
    }

    @Test
    void earlySpinDashReleaseStopsAndUncurls() {
        step(false, true, false, false, false);
        step(false, true, false, false, true);
        for (int i = 0; i < 10; i++) step(false, true, false, false, false);
        assertTrue(charge() > 0 && charge() < 45);
        step(false, false, false, false, false);
        assertEquals(0, charge());
        assertFalse(sonic.getRolling(), ".ChargeNotFull runs .StopRolling");
        assertEquals(0, sonic.getGSpeed());
        assertEquals(0, sonic.getXSpeed());
    }

    @Test
    void fullPeelOutLaunchesAtTwiceTopSpeedLessFriction() {
        step(true, false, false, false, false);           // first Up press arms the tap timer
        step(true, false, false, false, true);            // Up held + A: .CheckStartCharge
        assertEquals(1, charge());
        assertFalse(sonic.getAir(), "ObjSonic_CheckJump refuses a jump while Up is held at rest");
        assertEquals(0, sonic.getGSpeed());

        step(true, false, false, false, false);           // timer 2, .Peelout +100, rts
        assertEquals(2, charge());
        assertEquals(100, sonic.getGSpeed());
        assertEquals(0, sonic.getXSpeed(), ".Peelout returns before CalcXYVels");

        for (int i = 0; i < 40; i++) step(true, false, false, false, false);
        assertEquals(30, charge(), "the peel-out timer caps at 30");
        assertEquals(0xC00, sonic.getGSpeed());
        assertFalse(sonic.getRolling());

        step(false, false, false, false, false);          // .UnleashPeelout, then .Settle with d5 = $D
        assertEquals(0, charge());
        assertEquals(0xC00 - 0xD, sonic.getGSpeed());
        assertEquals(0xC00 - 0xD, sonic.getXSpeed());
        step(false, false, false, false, false);
        int frame = sonic.getMappingFrame() & 0xFF;
        assertEquals(0, sonic.getAnimationId() & 0xFF, "the peel-out keeps raw anim 0");
        assertTrue(frame >= 0x94 && frame <= 0x97,
                "at $A00 or more ObjSonic_Animate draws SonAni_Peelout ($94-$97), got " + Integer.toHexString(frame));
    }

    @Test
    void earlyPeelOutReleaseCancelsTheCharge() {
        step(true, false, false, false, false);
        step(true, false, false, false, true);
        for (int i = 0; i < 10; i++) step(true, false, false, false, false);
        assertTrue(sonic.getGSpeed() > 0);
        step(false, false, false, false, false);
        assertEquals(0, charge());
        assertEquals(0, sonic.getGSpeed(), ".CheckUnleashPeelout zeroes an unfinished charge");
    }

    @Test
    void lookingUpShiftsTheViewOnlyAfterADoubleTap() {
        Camera camera = GameServices.camera();
        for (int i = 0; i < 20; i++) step(true, false, false, false, false);
        assertEquals(0x60, camera.getYPosBias(), "a single held Up only plays the look-up animation");
        assertTrue(sonic.getLookingUp());

        for (int i = 0; i < 20; i++) step(false, false, false, false, false);  // let the tap timer expire
        step(true, false, false, false, false);           // tap 1: timer armed
        step(false, false, false, false, false);
        step(true, false, false, false, false);           // tap 2 inside the window: bit 7
        assertEquals(0x60, camera.getYPosBias());
        step(true, false, false, false, false);           // .LookUp
        assertEquals(0x62, camera.getYPosBias());
        for (int i = 0; i < 9; i++) step(true, false, false, false, false);
        assertEquals(0x62 + 2 * 9, camera.getYPosBias());
    }

    @Test
    void rollEntryRaisesASlowRightwardSpeedToTwo() {
        for (int i = 0; i < 12; i++) step(false, false, false, true, false);
        assertEquals(12 * 0xC, sonic.getGSpeed());
        step(false, true, false, false, false);           // Settle -$C, then CheckRoll -> StartRoll
        assertTrue(sonic.getRolling());
        assertEquals(0x200, sonic.getGSpeed(), "ObjSonic_StartRoll: below $200 and not leftward -> $200");
    }

    @Test
    void aFallingUnrolledPlayerReturnsToWalk() {
        sonic.setCentreY((short) 0x780);
        sonic.setAir(true);
        sonic.setYSpeed((short) 0x100);
        sonic.setAnimationId(0x10);                       // SonAni_Spring, as a spring writes it
        step(false, false, false, false, false);
        assertTrue(sonic.getAir());
        assertEquals(0, sonic.getAnimationId(), "ObjSonic_MdAir writes Walk once y_vel is not negative");
    }

    @Test
    void aRollJumpKeepsAirSteering() {
        for (int i = 0; i < 40; i++) step(false, false, false, true, false);
        step(false, true, false, false, false);
        assertTrue(sonic.getRolling());
        step(false, false, false, false, true);
        assertTrue(sonic.getAir());
        assertTrue(sonic.getRollingJump());
        short before = sonic.getXSpeed();
        step(false, false, true, false, true);
        assertEquals((short) (before - 0x18), sonic.getXSpeed(),
                "ObjSonic_MoveAir applies acceleration*2 with no roll-jump test");
    }
}
