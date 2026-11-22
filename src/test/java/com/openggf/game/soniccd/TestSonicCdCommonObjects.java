package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdMonitor;
import com.openggf.game.soniccd.objects.common.CdMonitorItem;
import com.openggf.game.soniccd.objects.common.CdRing;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.game.soniccd.objects.common.CdSpikes;
import com.openggf.game.soniccd.objects.common.CdSpring;
import com.openggf.game.soniccd.objects.ppz.PpzPlatform;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused behaviour of the common Sonic CD objects on the loaded R11A stage (its object art
 * and tables come from the disc). Each case cites the routine it checks; the native
 * comparisons are {@link TestSonicCdR11aObjectAppearances} and {@link TestSonicCdR11aObjectPicture}.
 */
class TestSonicCdCommonObjects {
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
        GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11A),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R11A));
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

    @Test
    void placedTimeStopMonitorFreezesTheStageUntilRestart() throws Exception {
        var levels = GameServices.level();
        levels.loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R13C),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R13C));
        CdLevel stage = (CdLevel) levels.getCurrentLevel();
        sonic.setCentreX((short) 0xB58);
        sonic.setCentreY((short) 0x346);
        GameServices.camera().updatePosition(true);
        ObjectManagerInternalAccess.refreshPlacementAfterRelocation(GameServices.level().getObjectManager());
        // This part of the disc layout has no ring placements. Keep a witness slot in the same object pass.
        CdRing ring = add(new CdRing(new ObjectSpawn(0xB90, 0x338, 0x10, 0, 0, false, 0)));
        runner.stepIdleFrames(128);
        var color = stage.getPalette(3).getColor(5);
        Set<Integer> controlColors = new HashSet<>();
        Set<Integer> controlRingFrames = new HashSet<>();
        for (int frame = 0; frame < 32; frame++) {
            runner.stepIdleFrames(1);
            controlColors.add(paletteColor(color));
            controlRingFrames.add(ring.mapFrame());
        }
        CdMonitor box = live(0x19).stream().filter(CdMonitor.class::isInstance).map(CdMonitor.class::cast)
                .filter(o -> o.getSpawn().x() == 0xBF0 && o.getSpawn().y() == 0x350
                        && o.getSpawn().subtype() == 5).findFirst().orElseThrow();
        var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var beforeBreak = rewind.capture();
        runner.stepFrame(false, true, false, false, false);
        for (int frame = 0; frame < 44; frame++) {
            runner.stepFrame(false, true, false, false, true);
        }
        for (int frame = 0; frame < 18 && box.mapFrame() != 0x11; frame++) {
            runner.stepIdleFrames(1);
        }
        int brokenFrame = box.mapFrame();
        // Eggman lowers the bottom boundary during the pickup rise. Observe the reward from the upper ledge.
        sonic.setCentreX((short) 0xBF0);
        sonic.setCentreY((short) 0x1A4);
        sonic.setGSpeed((short) 0);
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0);
        GameServices.camera().updatePosition(true);
        runner.stepIdleFrames(40);
        var afterBreak = rewind.capture();
        int frozenColor = paletteColor(color);
        int frozenRingFrame = ring.mapFrame();
        int startFrame = levels.getFrameCounter();
        Set<Integer> stoppedWords = new HashSet<>();
        Set<Integer> stoppedColors = new HashSet<>();
        Set<Integer> stoppedRingFrames = new HashSet<>();
        for (int frame = 0; frame < 401; frame++) {
            runner.stepIdleFrames(1);
            stoppedWords.add(stage.flags().timeStopWord);
            stoppedColors.add(paletteColor(color));
            stoppedRingFrames.add(ring.mapFrame());
        }
        int completedFrames = levels.getFrameCounter() - startFrame;
        rewind.restore(beforeBreak);
        int rewoundWord = stage.flags().timeStopWord;
        rewind.restore(afterBreak);
        int restoredWord = stage.flags().timeStopWord;
        levels.restartCurrentLevelAfterDeath();
        CdLevel restarted = (CdLevel) levels.getCurrentLevel();

        assertAll(
                () -> assertEquals(3, controlColors.size(), "all R13C palette phases while the box is intact"),
                () -> assertEquals(Set.of(0, 1, 2, 3), controlRingFrames, "ring completes a spin while the box is intact"),
                () -> assertEquals(0x11, brokenFrame, "Sonic breaks the disc placement while rolling into it"),
                () -> assertEquals(401, completedFrames, "freeze is observed across completed level frames"),
                () -> assertEquals(Set.of(300), stoppedWords, "the stop word does not count down"),
                () -> assertEquals(Set.of(frozenColor), stoppedColors, "PPZ palette cycling is held"),
                () -> assertEquals(Set.of(frozenRingFrame), stoppedRingFrames, "the ring keeps its mapping frame"),
                () -> assertEquals(0, rewoundWord, "rewind before the break restores the clear word"),
                () -> assertEquals(300, restoredWord, "rewind after the pickup restores the stop word"),
                () -> assertEquals(0, restarted.flags().timeStopWord, "death reload clears the stop word"));
    }

    @Test
    void palmtreePanicPlatformHoldsItsPositionDuringTimeStop() throws Exception {
        var levels = GameServices.level();
        levels.loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R13C),
                SonicCdZoneRegistry.actIndex(CdStagePayload.R13C));
        CdLevel stage = (CdLevel) levels.getCurrentLevel();
        int x = sonic.getCentreX() + 0x60;
        int y = sonic.getCentreY() - 0x80;
        // R13C includes ObjPlatform, but has no platform placement in its layout.
        PpzPlatform platform = add(new PpzPlatform(new ObjectSpawn(x, y, 0x21, 0, 0, false, 0)));
        runner.stepIdleFrames(1);

        // ObjPlatform_Subtype0X advances the angle one step per update (Level/Palmtree Panic/Objects/Platform/Main.asm:135-140).
        runner.stepIdleFrames(64);
        int movingY = platform.getY();
        stage.flags().timeStopWord = 300;
        runner.stepIdleFrames(64);
        int stoppedY = platform.getY();
        stage.flags().timeStopWord = 0;
        runner.stepIdleFrames(64);

        assertEquals(List.of(y + 32, y + 32, y), List.of(movingY, stoppedY, platform.getY()),
                "the platform holds at the crest and resumes from the same phase");
    }

    @Test
    void combineRingItemRequestsOnlyTheSignpostSound() {
        var campaign = GameServices.gameState();
        var level = GameServices.level().getLevelGamestate();
        campaign.addScore(100);
        level.setRings(17);
        int lives = campaign.getLives();
        int score = campaign.getScore();
        int rings = level.getRings();

        var item = new CdMonitorItem(0, 0, 6, 0);
        var services = mock(ObjectServices.class);
        when(services.gameState()).thenReturn(campaign);
        when(services.levelGamestate()).thenReturn(level);
        item.setServices(services);
        for (int frame = 0; frame < 64; frame++) item.update(frame, sonic);

        verify(services).playSfx(0x9D);
        long soundRequests = mockingDetails(services).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .filter(name -> name.equals("playSfx") || name.startsWith("playMusic"))
                .count();
        assertEquals(1, soundRequests, "FM_SIGNPOST is the only sound request");
        assertAll(
                () -> assertEquals(lives, campaign.getLives(), "lives"),
                () -> assertEquals(rings, level.getRings(), "rings"),
                () -> assertEquals(score, campaign.getScore(), "score"));
    }

    private static int paletteColor(com.openggf.level.Palette.Color color) {
        return (color.r & 0xFF) << 16 | (color.g & 0xFF) << 8 | (color.b & 0xFF);
    }

    @Test
    void ringGroupsUseTheStageDeltas() {
        CdLevel level = (CdLevel) GameServices.level().getCurrentLevel();
        // ObjRing_Deltas row 2 is $20,0 (Ring/Main.asm:22); the F0 entry places its second ring at $110.
        var spawn = level.getObjects().stream().filter(s -> s.objectId() == 0x10 && s.x() == 0xF0).findFirst().orElseThrow();
        var group = level.ringObjectPlacementMapping().get(spawn);
        assertEquals(2, group.size());
        assertEquals(0x110, group.get(1).x());
        assertEquals(spawn.y(), group.get(1).y());
    }

    @Test
    void monitorBreaksIntoItemAndExplosionAndGivesTenRings() {
        CdMonitor monitor = add(new CdMonitor(new ObjectSpawn(sonic.getCentreX() + 0x40, sonic.getCentreY(), 0x19, 1, 0,
                false, 0)));
        runner.stepIdleFrames(1);
        int rings = GameServices.level().getLevelGamestate().getRings();
        // Player_TouchMonitor .GoingDown: rolling and falling breaks (Player Object Collision.asm:116-120).
        sonic.setAnimationId(CdSonicAnimation.ROLL.id());
        sonic.setYSpeed((short) 0x200);
        monitor.onTouchResponse(sonic, null, 0);
        assertEquals(-0x200, sonic.getYSpeed());
        runner.stepIdleFrames(1);
        assertEquals(0x11, monitor.mapFrame(), "ObjMonitor_Break shows frame $11");
        assertEquals(1, live(0x1A).size(), "item $1A by FindObjSlot");
        assertEquals(1, live(0x18).size(), "explosion $18 by FindObjSlot");
        // The item rises from -$300 by $18 a frame: 32 frames until its velocity is non-negative.
        runner.stepIdleFrames(40);
        assertEquals(rings + 10, GameServices.level().getLevelGamestate().getRings(), ".GiveRings adds 10");
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 90"})
    void monitorOneUpWrapsTheLivesByte(int item, int startingRings) {
        var campaign = GameServices.gameState();
        NativeGameStateOps.restoreLives(campaign, 255);
        GameServices.level().getLevelGamestate().setRings(startingRings);
        add(new CdMonitorItem(sonic.getCentreX(), sonic.getCentreY(), item, 0));

        runner.stepIdleFrames(40);

        assertEquals(0, campaign.getLives(), "the 1-Up and ten-ring items both reach the byte increment");
    }

    @Test
    void hundredthPlacedRingWrapsTheLivesByte() {
        var campaign = GameServices.gameState();
        NativeGameStateOps.restoreLives(campaign, 255);
        GameServices.level().getLevelGamestate().setRings(99);
        var stage = (CdLevel) GameServices.level().getCurrentLevel();
        var spawn = stage.ringObjectPlacementMapping().keySet().stream()
                .filter(entry -> entry.x() == 0xF0).findFirst().orElseThrow();
        var ring = add(new CdRing(spawn));
        ring.update(0, sonic);

        ring.onTouchResponse(sonic, new TouchResponseResult(7, 8, 8, TouchCategory.SPECIAL), 0);

        assertEquals(0, campaign.getLives(), "CollectRing increments the lives byte at 100 rings");
    }

    /** Steps single frames until {@code done} holds (at most {@code max}); returns the count. */
    private int stepUntil(java.util.function.BooleanSupplier done, int max) {
        int frames = 0;
        while (!done.getAsBoolean() && frames < max) {
            runner.stepIdleFrames(1);
            frames++;
        }
        return frames;
    }

    /** A spawn whose top sits under Sonic's feet: oY = Sonic's Y + his Y radius + the object's. */
    private ObjectSpawn underFeet(int objectId, int subtype, int flags, int yRadius) {
        return new ObjectSpawn(sonic.getCentreX(), sonic.getCentreY() + sonic.getYRadius() + yRadius,
                objectId, subtype, flags, false, 0);
    }

    /**
     * A spawn whose top is $10 above Sonic's feet. SolidObject's .CheckFloor runs for a still
     * object and a floor under the seated feet releases the rider (Object Solid.asm:396-407),
     * so a solid that stays stood on must stand clear of the terrain.
     */
    private ObjectSpawn raised(int objectId, int yRadius) {
        return new ObjectSpawn(sonic.getCentreX(), sonic.getCentreY() + sonic.getYRadius() + yRadius - 0x10,
                objectId, 0, 0, false, 0);
    }

    @Test
    void upSpringLaunchesFromItsOwnSolidCall() {
        int standY = sonic.getCentreY();
        add(new CdSpring(underFeet(0x0A, 0, 0, 8)));
        // ObjSpring_Main_Up: SolidObject's .MoveOnObject seats Sonic at oY - (8 + $13), StandOn
        // returns d0 = 1, then .Action: y + 8, yVel = $F000 (Spring/Main.asm:257-276).
        assertTrue(stepUntil(() -> sonic.getYSpeed() != 0, 4) < 4);
        assertEquals(-0x1000, sonic.getYSpeed());
        assertEquals(standY + 8, sonic.getCentreY());
        assertTrue(sonic.getAir());
        assertFalse(sonic.isOnObject(), "bclr #3,oFlags(a1)");
    }

    @Test
    void diagonalSpringLaunchesWithCdVelocities() {
        // Yellow ($F600): mulu of sine $FF4B / cosine $B5 by $A00, lsr.l #8: -$712 and $712;
        // the X-flipped placement negates X and faces left (Spring/Main.asm:432-456).
        add(new CdSpring(underFeet(0x0A, 0x0A, 1, 0xC)));
        assertTrue(stepUntil(() -> sonic.getYSpeed() != 0, 4) < 4);
        assertEquals(-0x712, sonic.getYSpeed());
        assertEquals(-0x712, sonic.getXSpeed());
        assertEquals(Direction.LEFT, sonic.getDirection());
    }

    @Test
    void sidewaysSpringLaunchesOnlyWhenPushedInto() {
        // Sonic on the spring's right, d0 = $23 - 2 * $12 = -1: .PlayerRight. Moving left is
        // .HaltOnX, which sets bit 5 on both; .Action reads btst #5,oFlags(a0) (Spring/Main.asm:304-315).
        add(new CdSpring(new ObjectSpawn(sonic.getCentreX() - 0x11, sonic.getCentreY(), 0x0A, 4, 0, false, 0)));
        runner.stepIdleFrames(1);
        assertEquals(0, sonic.getXSpeed(), "standing still is .StopPush: no launch");
        sonic.setXSpeed((short) -0x100);
        sonic.setGSpeed((short) -0x100);
        sonic.setDirection(Direction.LEFT);
        assertTrue(stepUntil(() -> sonic.getXSpeed() > 0, 3) < 3);
        // Unflipped: x + 8 - $10, xVel = -$F000 = +$1000, bclr #0 (faces right), move lock $F (:319-342).
        assertEquals(0x1000, sonic.getXSpeed());
        assertEquals(Direction.RIGHT, sonic.getDirection());
        assertEquals(0xF, sonic.getMoveLockTimer());
        assertFalse(sonic.getPushing(), "bclr #5,oFlags(a1)");
    }

    @Test
    void spikesHurtOnlyWithoutPostHitInvulnerability() {
        add(new CdSpikes(raised(0x26, 8)));
        sonic.setInvulnerableFrames(0x40);
        runner.stepIdleFrames(3);
        assertFalse(sonic.isHurt(), "tst.w oPlayerHurt; bne .Restore (Spikes/Main.asm:66-69)");
        assertTrue(sonic.isOnObject(), "SolidObject still seats Sonic on the spikes");
        sonic.setInvulnerableFrames(0);
        assertTrue(stepUntil(() -> sonic.isHurt() || sonic.getDead(), 3) < 3, "HurtPlayer (Spikes/Main.asm:76)");
    }

    /**
     * ObjLostRing (Ring/Main.asm:317-460) is Sonic 1's Obj37 (32-ring cap, $288 start angle,
     * $18 gravity, bottom + $E0 delete), so the shared lost-ring owner hosts it with CD's ring art.
     */
    @Test
    void hurtWithRingsScattersThemThroughTheSharedLostRingOwner() {
        GameServices.level().getLevelGamestate().setRings(5);
        add(new CdSpikes(raised(0x26, 8)));
        assertTrue(stepUntil(() -> sonic.isHurt(), 3) < 3);
        runner.stepIdleFrames(1);
        assertEquals(0, GameServices.level().getLevelGamestate().getRings(), "ObjLostRing_Init: rings = 0 (:401)");
        assertEquals(5, GameServices.level().getRingManager().getActiveLostRings().size());
    }

    @Test
    void badnikExplosionMakesPointsAndRunsItsScript() {
        CdExplosion explosion = add(new CdExplosion(sonic.getCentreX(), sonic.getCentreY() - 0x30, 0, true, 4, 0));
        runner.stepIdleFrames(1);
        List<ObjectInstance> points = live(0x1C).stream().filter(o -> o instanceof CdPoints).toList();
        assertEquals(1, points.size(), "ObjExplosion_MakePoints");
        assertEquals(2, ((CdPoints) points.getFirst()).frame(), "subtype $80 | (4 >> 1)");
        // Ani_Explosion 0: delay 3 (four runs a frame), five frames, then $FC raises the routine on
        // run 21 and ObjExplosion_Done deletes on run 22 (Object Animate.asm:24-28,78-81).
        int frames = 1;
        while (!explosion.isDestroyed() && frames < 60) {
            runner.stepIdleFrames(1);
            frames++;
        }
        assertEquals(22, frames);
    }

    @Test
    void pointsRiseTwoPixelsAndLastTwentyFourFrames() {
        CdPoints points = add(new CdPoints(new ObjectSpawn(sonic.getCentreX(), 0x700, 0x1C, 0x80, 0, false, 0)));
        int frames = 0;
        while (!points.isDestroyed() && frames < 60) {
            runner.stepIdleFrames(1);
            frames++;
            if (!points.isDestroyed()) assertEquals(0x700 - 2 * frames, points.getY());
        }
        assertEquals(24, frames, "timer 24: deleted on the 24th run (HUD and Points/Main.asm:40-49)");
    }

    @Test
    void hudAndTitleCardOccupyTheirReservedSlots() {
        var objects = GameServices.level().getObjectManager().getActiveObjects();
        long hud = objects.stream().filter(o -> o instanceof AbstractObjectInstance a && o.getSpawn().objectId() == 0x1C
                && (a.getSlotIndex() == 2 || a.getSlotIndex() == 3 || a.getSlotIndex() == 5)).count();
        assertEquals(3, hud, "objHUDScoreSlot, objHUDLivesSlot, objHUDRingsSlot (Level/Main.asm:262-266)");
        assertNotNull(objects.stream().filter(o -> o instanceof AbstractObjectInstance a && a.getSlotIndex() == 4)
                .findFirst().orElse(null), "objTtlCardSlot");
        assertEquals(8, objects.stream().filter(o -> o instanceof AbstractObjectInstance a && a.getSlotIndex() >= 32
                && o.getSpawn().objectId() == 0x3C).count(), "eight title card pieces");
    }
}
