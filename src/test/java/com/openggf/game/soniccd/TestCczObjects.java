package com.openggf.game.soniccd;

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
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.ccz.CczAmyRose;
import com.openggf.game.soniccd.objects.ccz.CczAnimal;
import com.openggf.game.soniccd.objects.ccz.CczBumper;
import com.openggf.game.soniccd.objects.ccz.CczBumperPlatform;
import com.openggf.game.soniccd.objects.ccz.CczFlipperObject;
import com.openggf.game.soniccd.objects.ccz.CczDoor;
import com.openggf.game.soniccd.objects.ccz.CczDrain;
import com.openggf.game.soniccd.objects.ccz.CczFlipper;
import com.openggf.game.soniccd.objects.ccz.CczFlipperLaunch;
import com.openggf.game.soniccd.objects.ccz.CczDrumPlatform;
import com.openggf.game.soniccd.objects.ccz.CczFireShooter;
import com.openggf.game.soniccd.objects.ccz.CczGa;
import com.openggf.game.soniccd.objects.ccz.CczGlassShatter;
import com.openggf.game.soniccd.objects.ccz.CczIntroSpikes;
import com.openggf.game.soniccd.objects.ccz.CczMetalPlatform;
import com.openggf.game.soniccd.objects.ccz.CczMetalSonic;
import com.openggf.game.soniccd.objects.ccz.CczObjectCatalogue;
import com.openggf.game.soniccd.objects.ccz.CczPocket;
import com.openggf.game.soniccd.objects.ccz.CczTentou;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Collision Chaos objects on loaded stages (their art and tables come from the disc). Each
 * case checks values taken from the routine it cites (Level/Collision Chaos/Objects/*, objects/r3/*); the native
 * comparisons are {@link TestCczObjectsAppearances} and the frontier table. Sonic stands at the stage start and is
 * idle, so objects are placed relative to him.
 */
class TestCczObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

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
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures());
        EngineServices.configure(injected);
        GameplayModeContext gameplay = HeadlessGameBoot.openResolvedSessionForBoot(
                injected, new SonicCdGameModule(), ModuleResolutionService.LaunchPolicy.DETERMINISTIC, disc);
        GameplaySessionFactory.attachManagers(gameplay, injected);
        injected.graphics().initHeadless();
        GameModuleRegistry.setCurrent(SessionManager.requireCurrentGameModule());
        sonic = GameplayTeamBootstrap.registerActiveTeam(
                SessionManager.requireCurrentGameModule(), GameServices.sprites(), config).mainSprite();
        GameServices.camera().setFocusedSprite(sonic);
        load(CdStagePayload.R32A);
    }

    private void load(CdStagePayload payload) throws Exception {
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    private ObjectSpawn spawn(int id, int dx, int dy, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, id, subtype, 0, false, 0);
    }

    // ---- catalogue ----------------------------------------------------------------------------------------------

    @Test
    void catalogueFindsTheTablesOfTheStagesThatAssembleTheObjects() {
        // Every Collision Chaos stage assembles the platforms, badniks and effects ($23-$2F share one block in all object
        // indexes: r3/object_index_1b_1c_1d.asm, object_index_2.asm, object_index_3.asm; Object Index (Act 1 Present).asm:45-55).
        for (CdStagePayload p : CdStagePayload.values()) {
            if (!p.name().startsWith("R3")) continue;
            for (String column : List.of("metalPlatformMappings", "drumPlatformMappings", "fireShootMappings",
                    "bumperPlatformMappings", "glassBreakMappings", "gaMappings1", "gaMappings2", "pocketMappings1",
                    "pocketMappings2", "tentouMappings1", "tentouMappings2", "tentouBombMappings", "kamaKamaMappings1",
                    "kamaKamaMappings2", "kamaSickleMappings", "gaAnimations", "gaDustAnimations", "kamaKamaAnimations",
                    "kamaSickleAnimations", "tentouAnimations", "tentouBombAnimations", "pocketAnimations",
                    "glassBreakAnimations", "fireShootAnimations")) {
                assertTrue(CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
            // Only Act 1 Present links the intro objects (Object Index (Act 1 Present).asm:60-64) and only Act 3 the drain.
            boolean intro = p == CdStagePayload.R31A;
            boolean drain = p == CdStagePayload.R33C || p == CdStagePayload.R33D;
            for (String column : List.of("introSpikesMappings", "metalSonicMappings", "metalSonicAnimations",
                    "metalSonicExhaustAnimations")) {
                assertEquals(intro, CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
            for (String column : List.of("drainBlockMappings", "drainLightMappings", "drainSwitchMappings",
                    "drainBlockAnimations", "drainSwitchAnimations")) {
                assertEquals(drain, CczObjectCatalogue.offset(p, column) > 0, p + " " + column);
            }
        }
    }

    // ---- platforms ----------------------------------------------------------------------------------------------

    @Test
    void metalPlatformFollowsItsHorizontalMotionRecords() {
        // Subtype 1 starts with HorizontalStartRight: $1C frames at $300, then 8 frames at $300 losing $30 (Metal Platform/Main.asm:206-232).
        CczMetalPlatform platform = add(new CczMetalPlatform(spawn(0x23, 0x40, -0x80, 1)));
        int base = platform.xPosition();
        runner.stepIdleFrames(1);                          // routine 0 runs Init and loads record 0 (the legacy ABI does not move)
        assertEquals(base, platform.xPosition());
        assertEquals(0x1C, platform.timer());
        runner.stepIdleFrames(0x1C);                       // 28 moves of 3 pixels; the last loads record 1 in the same frame
        assertEquals(base + 3 * 0x1C, platform.xPosition());
        assertEquals(1, platform.motionStep());
        assertEquals(8, platform.timer());
        assertEquals(0x300, platform.velocity());
        runner.stepIdleFrames(8);                          // $300+$2D0+...+$1B0 = $12C0 of 8.8 over 8 frames
        assertEquals(base + 84 + 0x12C0 / 256, platform.xPosition());
        assertEquals((0x12C0 % 256) << 8, platform.xSubpixel());
        assertEquals(2, platform.motionStep());
        assertEquals(0, platform.velocity(), "record 2 is {8, -$30, 0}");
    }

    @Test
    void metalPlatformFollowsItsVerticalMotionRecords() {
        // Subtype 3: VerticalStartUp, $23 frames at -$200 (two pixels up a frame), solid called before each lookup (:294-319).
        CczMetalPlatform platform = add(new CczMetalPlatform(spawn(0x23, 0x40, -0x80, 3)));
        int base = platform.yPosition();
        runner.stepIdleFrames(1 + 0x23);
        assertEquals(base - 2 * 0x23, platform.yPosition());
        assertEquals(1, platform.motionStep());
        assertEquals(8, platform.timer());
        assertEquals(-0x200, platform.velocity());
    }

    @Test
    void drumPlatformStartsItsSolidCycleAtTheSubtypeStep() {
        // Subtype 2 enters routine 4 (Drum Platform/Main.asm:100-126). Init ends with rts; the next frame loads step 2
        // ({6, $C, 2}); each of the next 4 frames moves 6 pixels; step 3 then loads ({8, $B, 3}) in the same frame (:130-181, 238-262).
        CczDrumPlatform drum = add(new CczDrumPlatform(spawn(0x28, 0x40, -0x80, 2)));
        int base = drum.yPosition();
        runner.stepIdleFrames(2);
        assertEquals(2, drum.step());
        assertEquals(4, drum.stepTimer());
        assertEquals(2, drum.mapFrame());
        runner.stepIdleFrames(4);
        assertEquals(base + 4 * 6, drum.yPosition());
        assertEquals(3, drum.step());
        assertEquals(3, drum.mapFrame());
        assertEquals(4, drum.stepTimer());
    }

    @Test
    void bumperPlatformSlidesAndReversesAfterItsFirstLeg() {
        // Subtype $40 (bit 7 clear: Y, range $A0, first leg $50; bit 6 set: positive) moves one pixel a frame (Bumper Platform/Main.asm:33-57, 59-132).
        CczBumperPlatform platform = add(new CczBumperPlatform(spawn(0x2A, 0x60, -0x80, 0x40)));
        int base = platform.yPosition();
        runner.stepIdleFrames(0x50);
        assertEquals(base + 0x50, platform.yPosition());
        assertEquals(0xA0, platform.steps(), "the next leg is the range");
        assertEquals(-0x10000, platform.velocity());
    }

    // ---- badniks ------------------------------------------------------------------------------------------------

    @Test
    void gaWaitsHoversAndFliesWithItsCollisionTypes() {
        // Timer $3D twice, collision $25 after $3D more frames, then $33 frames until $26 and the flight (Ga/Main.asm:45-150).
        CczGa ga = add(new CczGa(spawn(0x2D, 0x80, -0x60, 0)));
        runner.stepIdleFrames(0x3D);
        assertEquals(4, ga.routine());
        assertEquals(0, ga.getCollisionFlags());
        runner.stepIdleFrames(0x3D);
        assertEquals(0x25, ga.getCollisionFlags());
        assertEquals(6, ga.routine());
        runner.stepIdleFrames(0x33);
        assertEquals(0x26, ga.getCollisionFlags());
        assertEquals(8, ga.routine());
        assertEquals(0x8000, ga.verticalVelocity());
        assertEquals(0x20, ga.timer());
        assertEquals(-0x8000, ga.horizontalVelocity(), "the player is to the left, so Ga flies left");
    }

    @Test
    void tentouBombExplodesWhenTouched() {
        // ObjTentou_Bomb_Explode turns the slot into explosion $18 (Tentou/Main.asm:423-435).
        CczTentou.Bomb bomb = add(new CczTentou.Bomb(spawn(0x2E, 0x40, -0x80, 1)));
        runner.stepIdleFrames(1);
        assertFalse(bomb.isDestroyed());
        bomb.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        assertTrue(bomb.isDestroyed());
        assertEquals(1, live(0x18).size());
    }

    @Test
    void glassShatterAwardsAHundredAndPlaysItsAnimationOnce() {
        // ObjGlassShatter_Init: AddPoints 10 (units of ten), SpawnPoints frame 0, then Ani_GlassBreak to its $FC (Glass Shatter/Main.asm:95-97).
        int score = GameServices.gameState().getScore();
        CczGlassShatter glass = add(new CczGlassShatter(spawn(0x29, 0x40, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertEquals(score + 100, GameServices.gameState().getScore());
        assertEquals(1, live(0x1C).stream().filter(o -> o.getSpawn().subtype() == 0x80).count(), "the 100-point sprite (subtype $80 | frame 0)");
        runner.stepIdleFrames(120);
        assertTrue(glass.isDestroyed(), "the animation's $FC advances to the delete routine");
    }

    // ---- pocket -------------------------------------------------------------------------------------------------

    @Test
    void pocketCapturesTheFallingPlayerAndAwardsPrizesThenReleasesHim() {
        // Capture window: $8-$18 above, $18 either side (Pocket/Main.asm:76-125); timed frames :127-244.
        int score = GameServices.gameState().getScore();
        int pockets = live(0x2F).size();                   // the stage's own machine is placed at level start
        CczPocket pocket = add(new CczPocket(spawn(0x2F, 0, 0x10, 0x80)));
        runner.stepIdleFrames(1);
        assertTrue(pocket.caught());
        assertTrue(sonic.isObjectControlled(), "bset #0,oPlayerCtrl(a6)");
        assertEquals(-0x800, sonic.getYSpeed());
        assertEquals(pocket.yPosition() - 0x10, sonic.getCentreY());
        assertEquals(1, pocket.mapFrame());
        runner.stepIdleFrames(9);                           // SpawnEffect: the 8 timer expires on the ninth decrement
        assertEquals(2, pocket.mapFrame());
        assertTrue(sonic.isHidden(), "bset #6,oPlayerCtrl(a6)");
        assertEquals(pockets + 2, live(0x2F).size(), "the machine and its glass front");
        runner.stepIdleFrames(115);                         // frames 11-125: open, award twice, close, release
        assertEquals(20, pocket.routine());
        assertEquals(5, pocket.prizes());
        assertEquals(score + 200, GameServices.gameState().getScore());
        assertFalse(sonic.isObjectControlled(), "bclr #0,var_2c");
        assertFalse(sonic.isHidden(), "bclr #6,var_2c");
        assertEquals(0, pocket.mapFrame());
    }

    // ---- fire shooter -------------------------------------------------------------------------------------------

    @Test
    void fireFlameCountsRisesAndThrowsTwoFireballs() {
        // Player within $78: timer $3C, then height 8 a frame to $20 (Fire Shooter/Main.asm:167-249).
        CczFireShooter.Flame flame = add(new CczFireShooter.Flame(spawn(0x27, 0x40, -0x80, 1)));
        int baseY = flame.yPosition();
        runner.stepIdleFrames(1);                           // Init
        assertEquals(2, flame.routine());
        runner.stepIdleFrames(0x3C);
        assertEquals(4, flame.routine());
        runner.stepIdleFrames(4);
        assertEquals(0x20, flame.height());
        assertEquals(baseY - 0x20, flame.yPosition());
        assertEquals(6, flame.routine());
        assertEquals(8, flame.timer());
        assertEquals(2, live(0x27).stream().filter(o -> o.getSpawn().subtype() == 2).count(), "two fireballs (present)");
    }

    // ---- drain (Act 3) ------------------------------------------------------------------------------------------

    @Test
    void drainSwitchSpawnsItsBlockAndLightsOneStepAtATime() throws Exception {
        load(CdStagePayload.R33C);
        CczDrain.Switch drainSwitch = add(new CczDrain.Switch(spawn(0x36, 0, 0, 0xFF)));
        runner.stepIdleFrames(1);
        assertEquals(2, live(0x36).size(), "the switch and its block at ($380,$4F8)");
        assertEquals(0x7F, drainSwitch.cooldown(), "subtype $FF starts at $80 with the player latched inside");
        sonic.setCentreX((short) (sonic.getCentreX() + 0x100));
        runner.stepIdleFrames(0x81);                        // unlatch and let the cooldown run out
        assertEquals(0, drainSwitch.cooldown());
        sonic.setCentreX((short) drainSwitch.xPosition());
        sonic.setCentreY((short) drainSwitch.yPosition());
        runner.stepIdleFrames(1);
        assertEquals(0, drainSwitch.stage(), "DrainSwitch_AdvanceStage: -2 to 0");
        assertEquals(3, live(0x36).size(), "the first indicator light, at X $380, Y $498");
        var light = live(0x36).stream().filter(o -> o.getSpawn().subtype() == 0).findFirst().orElseThrow();
        assertEquals(0x380, light.getX());
        assertEquals(0x498, light.getY());
        assertEquals(0x7F, drainSwitch.cooldown(), "set to $80, then DrainSwitch_Main counts it down in the same frame");
    }

    // ---- intro (Act 1 Present) ----------------------------------------------------------------------------------

    @Test
    void introSpikesBreakIntoThreeShardsWhenMetalSonicReachesThem() throws Exception {
        load(CdStagePayload.R31A);
        // IntroSpikesMain: Metal Sonic within width+$10 and height+$10 breaks it; frame 4 for subtype 0, shards 3, 2, 1 (objects/r3/intro.asm:627-715).
        CczIntroSpikes spikes = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        CczMetalSonic metal = add(new CczMetalSonic(spawn(0x31, 0x80, -0x40, 0)));
        runner.stepIdleFrames(2);
        assertEquals(4, spikes.routine());
        assertEquals(4, spikes.mapFrame());
        TreeSet<Integer> frames = new TreeSet<>();
        for (ObjectInstance o : live(0x30)) {
            if (o != spikes) frames.add(((CczIntroSpikes) o).mapFrame());
        }
        assertEquals(new TreeSet<>(List.of(1, 2, 3)), frames);
        assertNotNull(metal);
    }

    @Test
    void metalSonicWaitsThenFliesInAndArrives() throws Exception {
        load(CdStagePayload.R31A);
        // $3C frames of waiting, then -$10 acceleration per frame until X <= $3E0 without Amy; arrival spawns the exhaust
        // (objects/r3/intro.asm:46-99, 174-183). Starting at $3F0 it arrives after 22 moves.
        CczMetalSonic metal = add(new CczMetalSonic(new ObjectSpawn(0x3F0, sonic.getCentreY() - 0x40, 0x31, 0, 0, false, 0)));
        runner.stepIdleFrames(0x3C + 1);
        assertEquals(4, metal.routine());
        int frames = 0;
        while (!metal.arrived() && frames < 200) {
            runner.stepIdleFrames(1);
            frames++;
        }
        assertEquals(22, frames);
        assertEquals(0x3E0, metal.xPosition());
        assertEquals(6, metal.routine());
        assertEquals(1, live(0x34).size(), "the exhaust child");
    }

    // ---- pinball flipper launch (player hook) -----------------------------------------------------------------

    private record Flipper(int x, int y, int width, boolean flipped) implements CczFlipper {
        @Override public int flipperX() { return x; }
        @Override public int flipperY() { return y; }
        @Override public int flipperWidth() { return width; }
        @Override public boolean flipperFlippedHorizontally() { return flipped; }
        @Override public void flipperSwing() { }
    }

    @Test
    void flipperLaunchSpeedFollowsTheDistanceFromThePivot() {
        // ObjSonic_ChkFlipper (Level/_Objects/Sonic/Main.asm:3125-3152): force = playerX - flipperX + width, or 64 - force for a
        // flipper that is not flipped; speed = -$A00 + (-$A00 * force) / 64. Player 16 pixels right of the pivot, width $20:
        // flipped: force 48, speed -2560 - 1920; not flipped: force 64 - 48 = 16, speed -2560 - 640.
        var flipped = CczFlipperLaunch.compute(new Flipper(100, 100, 0x20, true), 116, 90);
        assertEquals(-2560 - 1920, flipped.speed());
        var plain = CczFlipperLaunch.compute(new Flipper(100, 100, 0x20, false), 116, 90);
        assertEquals(-2560 - 640, plain.speed());
        // The target point is left of and below the player: dx = -16, dy = 34: the second quadrant of the angle circle ($40-$80).
        assertTrue(plain.angle() > 0x40 && plain.angle() < 0x80, "angle " + plain.angle());
    }

    @Test
    void introObjectsStandDownWhereTheStageIndexDoesNotLinkThem() throws Exception {
        // Only Act 1 Present links $30/$31 as the intro (Object Index (Act 1 Present).asm:60-64). The other acts 1 and 2 stages
        // leave the slot ObjNull, which deletes itself (r3/object_index_1b_1c_1d.asm); Act 3 links boss parts there, so the
        // intro classes leave the slot alone until the boss lane registers them.
        load(CdStagePayload.R31B);
        CczIntroSpikes nullSlot = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertTrue(nullSlot.isDestroyed());
        load(CdStagePayload.R33C);
        CczIntroSpikes bossSlot = add(new CczIntroSpikes(spawn(0x30, 0x80, -0x40, 0)));
        runner.stepIdleFrames(1);
        assertFalse(bossSlot.isDestroyed());
        assertEquals(0, bossSlot.routine(), "no intro routine ran");
    }

    // ---- bumper, flipper, Amy, animal (second pass) ----------------------------------------------------------------

    @Test
    void bumperSlidesCountsPrizesAndThrowsThePlayerAwayAtMinusSeven() {
        // Subtype 3: range $20 + $40 = $60, first leg $30 steps of a pixel, bit 7 clear: along Y; bit 6 clear: upwards (Bumper and Flipper/Main.asm:20-65).
        CczBumper moving = add(new CczBumper(spawn(0x1D, 0x80, -0x40, 3)));
        int baseY = moving.yPosition();
        runner.stepIdleFrames(1);
        assertEquals(baseY - 1, moving.yPosition());
        assertEquals(0x2F, moving.steps());
        // A still bumper (subtype 0: no range, no velocity) directly to the player's right.
        CczBumper bumper = add(new CczBumper(spawn(0x1D, 0x20, 0, 0)));
        runner.stepIdleFrames(1);
        assertEquals(5, bumper.prizes());
        // A touch: 100 points, a 100-point sprite, and the player is thrown along CalcAngle(bumper - player) at $F900/256:
        // the bumper is directly to his right, so angle 0: X speed -$700, Y speed 0 (:66-106).
        int score = GameServices.gameState().getScore();
        bumper.onTouchResponse(sonic, null, 0);
        runner.stepIdleFrames(1);
        assertEquals(4, bumper.prizes());
        assertEquals(score + 100, GameServices.gameState().getScore());
        assertEquals(-0x700, sonic.getXSpeed());
        assertEquals(0, sonic.getYSpeed());
        assertTrue(sonic.getAir());
    }

    @Test
    void flipperSeatsTheStandingPlayerOnItsSlope() {
        // Column $20 of FlipperTopSlope is $FD (-3): the surface is 3 above oY; a player whose feet ($13 below his centre) are
        // within 8 above and 8 below it is placed on it (Bumper and Flipper/Main.asm:152-232).
        CczFlipperObject flipper = add(new CczFlipperObject(spawn(0x1E, 0, 0x14, 0)));
        int centreY = sonic.getCentreY();
        runner.stepIdleFrames(1);
        assertEquals(centreY + 0x14 - 3 - sonic.getYRadius(), sonic.getCentreY());
        assertTrue(sonic.isOnObject());
        assertEquals(flipper, CczFlipperObject.standingOn(sonic));
        flipper.flipperSwing();
        runner.stepIdleFrames(1);
        assertEquals(flipper, CczFlipperObject.standingOn(sonic), "animation 1 stops the collision test but does not release him");
    }

    @Test
    void animalsRunOnlyOnceTheProjectorIsDestroyed() {
        // CheckAnimalPrescence: in the present only CheckObjectDespawn runs until projDestroyed (Object Time Check.asm:52-81).
        CczAnimal flying = add(new CczAnimal(spawn(0x39, 0x40, -0x60, 0)));
        runner.stepIdleFrames(3);
        assertEquals(0, flying.phase(), "present, projector standing: no routine");
        com.openggf.game.soniccd.timetravel.CdTimeTravel.of(com.openggf.game.session.SessionManager.requireCurrentGameModule())
                .destroyProjector();                          // projDestroyed lives with the campaign time owner
        runner.stepIdleFrames(3);
        assertEquals(3, flying.phase(), "init sets phase 1, then it steps by 1 a frame (Animal/Main.asm:46-93)");
        CczAnimal ground = add(new CczAnimal(spawn(0x39, 0x40, -0x60, 1)));
        int baseX = ground.xPosition();
        int baseY = ground.yPosition();
        runner.stepIdleFrames(2);                            // init (rts), then the first hop frame
        assertEquals(baseX + 1, ground.xPosition());
        assertEquals(baseY - 4, ground.yPosition());
        assertEquals(-0x40000 + 0x2000, ground.yVelocity());
    }

    @Test
    void amyRunsFromThePlayerAndEmitsAHeartEveryThirtyTwoFrames() throws Exception {
        load(CdStagePayload.R31A);
        // Waits while the player is left of her; once he is at or past her X she runs ($20 acceleration) and emits hearts
        // when the byte accumulator adds $8 past $FF (objects/r3/intro.asm:271-376, 506-531).
        CczAmyRose amy = add(new CczAmyRose(spawn(0x32, 0x80, 0, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0, amy.routine());
        sonic.setCentreX((short) (amy.xPosition() + 0x10));
        runner.stepIdleFrames(1);
        assertEquals(2, amy.routine());
        runner.stepIdleFrames(1);
        assertEquals(0x20, amy.xVelocity());
        runner.stepIdleFrames(31);
        assertEquals(1, live(0x33).size(), "one heart after 32 running frames");
    }

    @Test
    void doorSlidesByTheOffsetByteWhileThePlayerIsBelowIt() {
        // Subtype $80 slides toward +X: offset += $10 a frame to $40 while the player's Y is at or below the door's, then back
        // down by $10 a frame while he is above (Level/Collision Chaos/Objects/Door/Main.asm, ObjDoor_Update).
        CczDoor door = add(new CczDoor(spawn(0x26, 0x40, -0x40, 0x80)));
        int baseX = door.xPosition();
        runner.stepIdleFrames(4);
        assertEquals(0x40, door.offset());
        assertEquals(baseX + 0x40, door.xPosition());
        assertEquals(8, door.delay(), "the open delay");
        sonic.setCentreY((short) (door.yPosition() - 0x40));
        runner.stepIdleFrames(5);                            // $30, $20, $10, 0, then the borrow of the fifth step
        assertEquals(0, door.offset());
        assertEquals(baseX, door.xPosition());
        assertEquals(0x3C, door.delay(), "subtype low nibble 0: a $3C frame close delay");
    }
}
