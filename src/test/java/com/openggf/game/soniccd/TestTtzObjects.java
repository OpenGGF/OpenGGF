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
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.ttz.*;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Tidal Tempest objects on the loaded stages (their art, tables and water come from
 * the disc). Each case cites the routine it checks in {@code src/objects/r4/<object>.asm} of the scddisasm pin;
 * the native comparisons are {@link TestTtzObjectsAppearances} and the picture checks.
 */
class TestTtzObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    private void boot(CdStagePayload payload) throws Exception {
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
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
        GroundSensor.setLevelManager(GameServices.level());
        runner = new HeadlessTestRunner(sonic);
        runner.stepIdleFrames(1);
        sonic.setInvulnerableFrames(0x7000);   // keep the player out of the way of the badniks' touch
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

    private static ObjectSpawn spawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, 0);
    }

    private CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    private int floorDistance(AbstractObjectInstance o, int yRadius) {
        return ObjectTerrainUtils.checkFloorDist(o.getX(), o.getY(), yRadius).distance();
    }

    // ---- Water surface at level start (r4/main.asm:249) ----------------------------------------------

    @Test
    void levelStartCreatesTheWaterSurfaceInTheFirstDynamicSlot() throws Exception {
        boot(CdStagePayload.R41A);
        var surfaces = live(0x04).stream().filter(o -> o instanceof ObjWaterSurface).toList();
        assertEquals(1, surfaces.size(), "InitStage: move.b #4,object_spawn_pool+obj.id (r4/main.asm:249)");
        assertEquals(ObjWaterSurface.LEVEL_START_SLOT, ((AbstractObjectInstance) surfaces.getFirst()).getSlotIndex(),
                "object_spawn_pool is dynObjects, the first dynamic slot (Level/Tidal Tempest/R4 Compatibility.asm:83)");
    }

    // ---- Move wall (objects/r4/move_wall.asm) --------------------------------------------------------

    @Test
    void moveWallWaitsSlidesAndStopsThreeHundredEightyFrames() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x120;
        ObjMoveWall wall = add(new ObjMoveWall(spawn(x0, sonic.getCentreY() - 0x20, 0x45, 0)));
        runner.stepIdleFrames(1);
        assertEquals(x0, wall.getX(), "Routine0 only initialises (MoveWallObject_0_Routine0, objects/r4/move_wall.asm:21-52)");
        // Routine2 counts $3C down; the frame it reaches 0 starts Routine4 (MoveWallObject_0_Routine2, objects/r4/move_wall.asm:56-60).
        runner.stepIdleFrames(0x3B);
        assertEquals(x0, wall.getX());
        runner.stepIdleFrames(1);
        assertEquals(x0, wall.getX(), "the frame the wait ends only changes the routine");
        // Routine4 adds $8000 (half a pixel) per frame for $180 frames (MoveWallObject_0_Routine4, objects/r4/move_wall.asm:67-81).
        runner.stepIdleFrames(2);
        assertEquals(x0 + 1, wall.getX());
        runner.stepIdleFrames(0x17E);
        assertEquals(x0 + 0xC0, wall.getX());
        runner.stepIdleFrames(5);
        assertEquals(x0 + 0xC0, wall.getX(), "Routine6 stands still");
    }

    // ---- Yago (objects/r4/yago.asm) -----------------------------------------------------------------------

    @Test
    void yagoFallsToTheFloorThenCrawlsAQuarterPixelAFrame() throws Exception {
        boot(CdStagePayload.R41D);
        ObjYago yago = add(new ObjYago(spawn(sonic.getCentreX(), sonic.getCentreY() - 0x30, 0x2E, 0)));
        // Routine0 runs Routine2 in the same frame: one pixel a frame down until the floor is within 5 (yago.asm:32-48).
        int frames = 0;
        while (yago.routine() != 4 && frames++ < 200) runner.stepIdleFrames(1);
        assertEquals(4, yago.routine(), "the crawl routine follows the landing (loc_20DF2E, yago.asm:58-64)");
        assertTrue(floorDistance(yago, 0xC) < 5, "it stops when the floor distance falls below 5 (cmpi.w #5,d1; bmi, :46-47)");
        assertEquals(0x2F, yago.getCollisionFlags(), "collision $2F (:36)");
        int x0 = yago.getX();
        // Routine4 adds -$4000 (16.16) to X a frame (:66-70, :61): -0.25 reads one pixel left at once, and -1.0 after four.
        runner.stepIdleFrames(1);
        assertEquals(x0 - 1, yago.getX());
        runner.stepIdleFrames(3);
        assertEquals(x0 - 1, yago.getX());
        runner.stepIdleFrames(1);
        assertEquals(x0 - 2, yago.getX());
    }

    // ---- Mace (objects/r4/mace.asm) ---------------------------------------------------------------

    @Test
    void maceSpawnsSixPartsOnTheRotatingArm() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        add(new ObjMace(spawn(x0, y0, 0x36, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> parts = live(0x36).stream().filter(o -> o.getSpawn().subtype() >= 0xFE).toList();
        assertEquals(6, parts.size(), "five links ($FF) and the ball ($FE) (MaceObject_0_Routine0, objects/r4/mace.asm:39-117)");
        // One step of $100 makes the angle $0100; SineTable[1] = 6 and SineTable[$41] = $FF (Level/Functions (Misc).asm:80-89),
        // so the first link is at hub + (cos * 65536 >> 4, sin * 65536 >> 4) = (+15, +0) (MaceObject_0_Routine2, objects/r4/mace.asm:119-177).
        ObjectInstance first = parts.stream().min(java.util.Comparator.comparingInt(o -> ((AbstractObjectInstance) o).getSlotIndex())).orElseThrow();
        assertEquals(x0 + 15, first.getX());
        assertEquals(y0, first.getY());
        // The ball sits at radius 96: cos 255 * 65536 / 16 * 6 = 6 * 15.94 -> +95 in X, sin 6 * 6 / 16 -> +2 in Y at 1/4 scale.
        ObjectInstance ball = parts.stream().filter(o -> o.getSpawn().subtype() == 0xFE).findFirst().orElseThrow();
        assertEquals(x0 + 95, ball.getX());
        assertEquals(y0 + 2, ball.getY());
        assertTrue(ball instanceof com.openggf.level.objects.TouchResponseProvider t && t.getCollisionFlags() == 0xA9);
    }

    // ---- Shooter (objects/r4/shooter.asm) ---------------------------------------------------------

    @Test
    void shooterFiresAMissileEveryThirtyOneFrames() throws Exception {
        boot(CdStagePayload.R42A);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        add(new ObjShooter(spawn(x0, y0, 0x42, 0)));
        runner.stepIdleFrames(1);
        // The counter starts at 0, so the first frame fires (addi.w #-1; bpl, loc_20F2A6, objects/r4/shooter.asm:29-31).
        List<ObjectInstance> missiles = live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).toList();
        assertEquals(1, missiles.size());
        // Spawned at x + $20 for subtype 0, which moves left (d6 stays +$20, ShooterObject, objects/r4/shooter.asm:17-27), at y + 4,
        // then -3 pixels in the same frame (loc_20F32E, objects/r4/shooter.asm:64-70).
        assertEquals(x0 + 0x20 - 3, missiles.get(0).getX());
        assertEquals(y0 + 4, missiles.get(0).getY());
        runner.stepIdleFrames(30);
        assertEquals(1, live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count());
        runner.stepIdleFrames(1);
        assertEquals(2, live(0x42).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count(), "the 32nd frame fires again");
    }

    // ---- Water surface (objects/r4/water_surface.asm) ---------------------------------------------

    @Test
    void waterSurfaceAlternatesTwoScreenOffsetsOnTheWaterLine() throws Exception {
        boot(CdStagePayload.R42A);
        ObjWaterSurface surface = add(new ObjWaterSurface(spawn(0x100, 0x302, 0x04, 0)));
        runner.stepIdleFrames(1);
        int cam = GameServices.camera().getX();
        // First frame: toggle 0 -> 1, table entry 1 is $60: camera X + $A0 + $60 (WaterSurfaceObject_0_Routine2, objects/r4/water_surface.asm:24-34).
        assertEquals(cam + 0x100, surface.getX());
        runner.stepIdleFrames(1);
        // Second frame: toggle wraps to 0, byte_20695E[0] = $A0 sign-extended = -$60: camera X + $40.
        assertEquals(GameServices.camera().getX() + 0x40, surface.getX());
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY + 2, surface.getY(), "move.w water_y,obj.y(a0); addq.w #2 (water_surface.asm:34-37)");
    }

    // ---- Big door (objects/r4/big_door.asm) -------------------------------------------------------

    @Test
    void bigDoorStaysClosedAndSolidUntilItsSwitchIsSet() throws Exception {
        boot(CdStagePayload.R42A);
        ObjBigDoor door = add(new ObjBigDoor(spawn(GameServices.camera().getX() + 0x100, sonic.getCentreY() - 0x80, 0x25, 0)));
        runner.stepIdleFrames(5);
        assertFalse(door.isDestroyed());
        assertEquals(1, live(0x25).size(), "no panels while switch_flags[subtype] is clear");
    }


    // ---- Tonbo (objects/r4/tonbo.asm) --------------------------------------------------------------

    @Test
    void tonboFliesLeftBobsAndTurnsAfterHalfAPeriod() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjTonbo tonbo = add(new ObjTonbo(spawn(x0, y0, 0x2B, 0)));
        runner.stepIdleFrames(1);
        // Init falls into Routine2: one step of -$10000 (TonboObject_0_Routine0/2, objects/r4/tonbo.asm:21-72).
        assertEquals(x0 - 1, tonbo.getX());
        assertEquals(y0, tonbo.getY(), "phase 0: SineTable[0] = 0");
        assertEquals(0x2C, tonbo.getCollisionFlags(), "move.b #$2C,obj.collide_type(a0) (objects/r4/tonbo.asm:24)");
        runner.stepIdleFrames(1);
        // Phase 4: SineTable[4] = $19, $19 * 65536 / 16 = 1.56 pixels -> y0 + 1 (TonboObject_0_Routine2, objects/r4/tonbo.asm:51-72).
        assertEquals(x0 - 2, tonbo.getX());
        assertEquals(y0 + 1, tonbo.getY());
        // oVar36 starts at $80 and the turn happens on the frame it reaches 0: frame $80 (TonboObject_0_Routine2, objects/r4/tonbo.asm:60-69).
        runner.stepIdleFrames(0x7D);
        assertEquals(x0 - 0x7F, tonbo.getX());
        assertEquals(0, tonbo.spriteFlip() & 1);
        runner.stepIdleFrames(1);
        assertEquals(x0 - 0x80, tonbo.getX());
        assertEquals(1, tonbo.spriteFlip() & 1, "bchg #0,obj.sprite_flags(a0) (objects/r4/tonbo.asm:66-69)");
        runner.stepIdleFrames(1);
        assertEquals(x0 - 0x7F, tonbo.getX(), "the velocity is negated");
    }

    // ---- Circling spike balls (objects/r4/circle_spike_ball.asm) -----------------------------------

    @Test
    void circleSpikeBallMakesFourBallsOnOneCircle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x80;
        ObjCircleSpikeBall entry = add(new ObjCircleSpikeBall(spawn(x0, y0, 0x38, 3)));
        runner.stepIdleFrames(1);
        assertEquals(4, live(0x38).size(), "the entry and three more (objects/r4/circle_spike_ball.asm:27-60)");
        runner.stepIdleFrames(1);
        // Phase $100 -> angle 1: SineTable[1] = 6, SineTable[$41] = $FF; shifted right by the subtype (3):
        // x0 + 31, y0 + 0 (CircleSpikeBallObject_0_Routine2, objects/r4/circle_spike_ball.asm:117-131).
        assertEquals(x0 + 31, entry.getX());
        assertEquals(y0, entry.getY());
        assertEquals(0xA9, entry.getCollisionFlags());
    }

    // ---- Fan (objects/r4/fan_a_b.asm, fan_c_d.asm) ---------------------------------------------------

    @Test
    void fanVariantComesFromTheStageFile() throws Exception {
        boot(CdStagePayload.R42B);
        assertTrue(TtzObjectCatalogue.offset(CdStagePayload.R42B, "fanABMappings1") >= 0, "A/B fans in R42B");
        assertEquals(-1, TtzObjectCatalogue.offset(CdStagePayload.R42B, "fanCDMappings1"));
        assertEquals(-1, TtzObjectCatalogue.offset(CdStagePayload.R42C, "fanABMappings1"));
        assertTrue(TtzObjectCatalogue.offset(CdStagePayload.R42C, "fanCDMappings1") >= 0, "C/D fans in R42C");
    }


    // ---- Twin move block (objects/r4/twin_move_block.asm) ----------------------------------------------

    @Test
    void twinMoveBlocksMoveInOppositionThroughTheCycle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjTwinMoveBlock first = add(new ObjTwinMoveBlock(spawn(x0, y0, 0x48, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> pair = live(0x48);
        assertEquals(2, pair.size(), "the entry makes a twin $20 to its right (objects/r4/twin_move_block.asm:39-53)");
        ObjectInstance twin = pair.stream().filter(o -> o != first).findFirst().orElseThrow();
        assertEquals(x0 + 0x20, twin.getX());
        // Frame 2 loads cycle row 0 (count $80, acceleration -4, speed +$200); the move starts the frame after.
        runner.stepIdleFrames(2);
        // Speed $200 (8.8) is 2 pixels a frame, 4 less per frame ($FC): after one move y0 + 2 (objects/r4/twin_move_block.asm:70-84).
        assertEquals(y0 + 2, first.getY());
        assertEquals(y0 - 2, twin.getY(), "the twin's speed and acceleration are negated");
    }

    // ---- Crush blocks (objects/r4/crush_blocks.asm) -----------------------------------------------------

    @Test
    void crushBlocksMakeEightBlocksThatHoldThenClose() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        add(new ObjCrushBlocks(spawn(x0, y0, 0x33, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> blocks = live(0x33).stream().filter(o -> o.getSpawn().subtype() >= 0xFE).toList();
        assertEquals(8, blocks.size(), "four rows of two (objects/r4/crush_blocks.asm:44-95)");
        // The first speed is -0.5 pixel a frame (outward): after the first update the pairs have moved 0.5 pixel apart,
        // which truncates to 0 on the right-hand blocks' 16.16 positions that began on whole pixels (objects/r4/crush_blocks.asm:100-128).
        int minX = blocks.stream().mapToInt(ObjectInstance::getX).min().orElseThrow();
        int maxX = blocks.stream().mapToInt(ObjectInstance::getX).max().orElseThrow();
        runner.stepIdleFrames(0xBF);
        assertTrue(blocks.stream().mapToInt(ObjectInstance::getX).min().orElseThrow() <= minX - 0x5F,
                "after $C0 frames at 0.5 pixel the blocks are $60 pixels apart");
        assertTrue(blocks.stream().mapToInt(ObjectInstance::getX).max().orElseThrow() >= maxX + 0x5F);
    }

    // ---- Pressure elevator (objects/r4/pressure_elevator.asm) --------------------------------------------

    @Test
    void pressureElevatorWaitsForThePlayerToBeMoreThanTwoHundredAndTwentyBelow() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjPressureElevator elevator = add(new ObjPressureElevator(spawn(x0, y0, 0x49, 0)));
        runner.stepIdleFrames(3);
        // The player is $100 below: Routine2 needs player.y - $E0 > start y (objects/r4/pressure_elevator.asm:46-56), so it drops to
        // player.y - $E0 = y0 + $20 and starts falling.
        assertTrue(elevator.getY() >= y0 + 0x20, "dropped to $E0 above the player, then falling");
    }

    // ---- Geyser (objects/r4/geyser.asm) ----------------------------------------------------------------

    @Test
    void geyserSitsOnTheWaterAndThrowsADropEverySixteenFrames() throws Exception {
        boot(CdStagePayload.R41A);
        int x0 = GameServices.camera().getX() + 0x100;
        ObjGeyser geyser = add(new ObjGeyser(spawn(x0, sonic.getCentreY(), 0x2A, 0)));
        runner.stepIdleFrames(1);
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY - 0x30, geyser.getY(), "move.w water_y,obj.y(a0); subi.w #$30 (geyser.asm:44-50)");
        runner.stepIdleFrames(14);
        assertEquals(0, live(0x2A).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count());
        runner.stepIdleFrames(1);
        assertEquals(1, live(0x2A).stream().filter(o -> o.getSpawn().subtype() == 0xFF).count(),
                "addi.b #$10,obj.var_3b(a0) carries on the sixteenth frame (geyser.asm:48-51)");
    }

    // ---- Platform chain (objects/r4/platform_chain.asm) ----------------------------------------------------

    @Test
    void platformChainMakesNinePiecesOnOneCircle() throws Exception {
        boot(CdStagePayload.R42C);
        int x0 = GameServices.camera().getX() + 0x100;
        int y0 = sonic.getCentreY() - 0x100;
        ObjPlatformChain head = add(new ObjPlatformChain(spawn(x0, y0, 0x3E, 0)));
        runner.stepIdleFrames(1);
        assertEquals(9, live(0x3E).size(), "the entry and eight more (platform_chain.asm:53-82)");
        // The head starts at angle $20 and the first Routine2 call (the init falls into it) adds one: angle $21, where the
        // sine table gives sin $B9 and (at $61) cos $B0 (Level/Functions (Misc).asm:83,87); times 6 over 16 that is
        // (+66, +69) from the centre (PlatformChainObject_0_Routine2, objects/r4/platform_chain.asm:123-150).
        assertEquals(x0 + 66, head.getX());
        assertEquals(y0 + 69, head.getY());
    }

    // ---- Amenbo (objects/r4/amenbo.asm) -------------------------------------------------------------------

    @Test
    void amenboSkatesOnTheWaterAndAcceleratesToItsLimit() throws Exception {
        boot(CdStagePayload.R42B);
        int x0 = GameServices.camera().getX() + 0x100;
        ObjAmenbo amenbo = add(new ObjAmenbo(spawn(x0, sonic.getCentreY() - 0x80, 0x2C, 0)));
        runner.stepIdleFrames(1);
        int waterY = GameServices.water().getGameplayWaterLevelY(
                GameServices.level().getFeatureZoneId(), GameServices.level().getFeatureActId());
        assertEquals(waterY - 8, amenbo.getY(), "move.w water_y,obj.y(a0); subq.w #8 (amenbo.asm:155-163)");
        assertEquals(0x31, amenbo.getCollisionFlags());
        // Speed gains -$600 a frame (16.16) up to the -$18000 limit, reached on the 64th frame (amenbo.asm:53-84); the distance
        // after n frames is $600 * n * (n + 1) / 2: 41 frames cover $14.1 pixels.
        runner.stepIdleFrames(40);
        assertTrue(amenbo.getX() <= x0 - 19 && amenbo.getX() >= x0 - 21, "x after 41 frames: " + amenbo.getX());
        assertEquals(2, amenbo.routine());
        runner.stepIdleFrames(23);
        assertEquals(4, amenbo.routine(), "routine 2 ends at the -1.5 pixel limit and brakes (routine 4)");
    }
}
