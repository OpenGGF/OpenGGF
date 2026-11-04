package com.openggf.game.sonic3k;

import com.openggf.GameLoop;
import com.openggf.BonusStageSessionTestAccess;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.NativeLevelLoadAccess;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestBonusStageReturnWaterRestore {
    private static final int ZONE_HCZ = Sonic3kZoneIds.ZONE_HCZ;
    private static final int ACT_1 = 0;
    private static final int LATE_HCZ1_CAMERA_X = 0x3600;
    private static final int HCZ1_LATE_WATER_LEVEL = 0x06A0;

    @BeforeAll
    public static void configure() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Test
    public void aiz1BonusReturnOverridesFreshIntroSuppressionWithoutSkippedOwner() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        GraphicsManager.getInstance().initHeadless();
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, ACT_1)
                .build();

        LevelManager levelManager = GameServices.level();
        Sonic3kObjectArtProvider artProvider = (Sonic3kObjectArtProvider)
                GameModuleRegistry.getCurrent().getObjectArtProvider();
        var beforeReturn = artProvider.capture();
        assertFalse(levelManager.isTitleCardRequested(),
                "fresh AIZ intro suppression should begin without a title request");
        assertEquals(-1, beforeReturn.titleCardTeardownTicks(),
                "fresh AIZ intro suppression should not create a skipped-title owner");

        levelManager.setBonusStageReturnCheckpointIndex(0);
        try {
            LevelLoadContext returnLoad = new LevelLoadContext();
            returnLoad.setShowTitleCard(true);
            levelManager.requestTitleCardIfNeeded(returnLoad);
        } finally {
            levelManager.clearBonusStageReturn();
        }

        assertTrue(levelManager.isTitleCardRequested(),
                "bonus return must preserve the mandatory title request for GameLoop");
        assertEquals(beforeReturn, artProvider.capture(),
                "requesting the real return title must not start a competing skipped owner");
    }

    @Test
    public void seamlessAiz2BonusReturnKeepsWaterAndItsFreshTarget() throws Exception {
        var config = SonicConfigurationService.getInstance();
        Object oldCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        try {
            GraphicsManager.getInstance().initHeadless();
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            var level = GameServices.level();
            level.executeActTransition(com.openggf.level.SeamlessLevelTransitionRequest.builder(
                    com.openggf.level.SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                    .targetZoneAct(0, 1).preserveLevelGamestate(true).build());
            assertEquals(1, level.getCurrentAct());
            assertEquals(0, level.getApparentAct());
            var water = GameServices.water();
            assertTrue(water.hasWater(0, 1));
            // A saved mean may differ from the new level's target during motion.
            water.setWaterLevelDirect(0, 1, 0x510);
            var saved = new com.openggf.game.BonusStageTransitionCoordinator().captureEntry(
                    level, fixture.camera(), water, fixture.sprite(),
                    GameModuleRegistry.getCurrent().getLevelEventProvider(), 0).savedState();
            var loop = new GameLoop(new InputHandler());
            var provider = GameModuleRegistry.getCurrent().getBonusStageProvider();
            BonusStageSessionTestAccess.entered(loop, provider, BonusStageType.GLOWING_SPHERE, saved);
            finishEntryTitle(loop);
            BonusStageSessionTestAccess.exited(loop, provider, saved);
            finishTitleBeforeGameplay(loop, GameMode.LEVEL);
            assertTrue(level.hasPendingFreshLevelTransitionBoundary(),
                    "LevelLoop must still own assembly before the first water/gameplay tick");

            assertEquals(0, level.getApparentAct());
            assertTrue(water.hasWater(0, 1), "CheckLevelForWater must see saved apparent AIZ1");
            assertEquals(saved.meanWaterLevel(), water.getWaterLevelY(0, 1));
            assertEquals(0x528, water.getWaterLevelTarget(0, 1),
                    "loc_2D2C2 restores mean only; loc_7932 owns the new target");
        } finally {
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldCharacter != null ? oldCharacter : "sonic");
        }
    }

    private static void finishEntryTitle(GameLoop loop) {
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
        // This ordinary fixture installs no phase-admission client. Its
        // synchronous load retains the rewindable fresh title boundary.
        assertFalse(NativeLevelLoadAccess.isPending(GameServices.level()));
        assertTrue(GameServices.level().hasPendingFreshLevelTransitionBoundary(),
                "the entry title must retain its prepared fresh boundary");
        // doEnter skips the source fade only. The real title loop must release
        // the entry boundary before a second Level: return.
        finishTitleBeforeGameplay(loop, GameMode.BONUS_STAGE);
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary(),
                "bonus entry assembly completes at title release");
    }

    @Test
    public void bonusStageReturn_restoresSavedWaterLevelBeforeReturnedGameplay() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_HCZ, ACT_1)
                .build();

        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);

        LevelManager levelManager = GameServices.level();
        WaterSystem waterSystem = GameServices.water();
        Camera camera = fixture.camera();
        AbstractPlayableSprite player = fixture.sprite();

        int initialTarget = waterSystem.getWaterLevelTarget(ZONE_HCZ, ACT_1);
        camera.setX((short) LATE_HCZ1_CAMERA_X);

        waterSystem.updateDynamic(ZONE_HCZ, ACT_1, camera.getX(), camera.getY());
        waterSystem.update();

        assertEquals(HCZ1_LATE_WATER_LEVEL, waterSystem.getWaterLevelY(ZONE_HCZ, ACT_1),
                "Sanity check: HCZ1 water should already be at the later threshold before saving");

        int zoneAndAct = (levelManager.getCurrentZone() << 8) | levelManager.getCurrentAct();
        int apparentZoneAndAct = (levelManager.getCurrentZone() << 8) | levelManager.getApparentAct();
        int ringCount = levelManager.getLevelGamestate() != null
                ? levelManager.getLevelGamestate().getRings()
                : 0;
        long timerFrames = levelManager.getLevelGamestate() != null
                ? levelManager.getLevelGamestate().getTimerFrames()
                : 0;

        BonusStageState savedState = new BonusStageState(
                zoneAndAct,
                apparentZoneAndAct,
                ringCount,
                0,
                0,
                0,
                0,
                0,
                player.getCentreX(),
                player.getCentreY(),
                camera.getX(),
                camera.getY(),
                player.getTopSolidBit(),
                player.getLrbSolidBit(),
                camera.getMaxY(),
                timerFrames,
                HCZ1_LATE_WATER_LEVEL
        );

        BonusStageProvider provider = GameModuleRegistry.getCurrent().getBonusStageProvider();

        BonusStageSessionTestAccess.entered(loop, provider, BonusStageType.GLOWING_SPHERE, savedState);
        finishEntryTitle(loop);
        BonusStageSessionTestAccess.exited(loop, provider, savedState);
        finishTitleBeforeGameplay(loop, GameMode.LEVEL);
        assertTrue(levelManager.hasPendingFreshLevelTransitionBoundary(),
                "LevelLoop must still own assembly before the first water/gameplay tick");

        assertEquals(HCZ1_LATE_WATER_LEVEL, waterSystem.getWaterLevelY(ZONE_HCZ, ACT_1),
                "Bonus-stage return restores saved water before returned gameplay");
        assertEquals(initialTarget, waterSystem.getWaterLevelTarget(ZONE_HCZ, ACT_1),
                "the saved mean must not overwrite the freshly initialized target");
    }

    private static void finishTitleBeforeGameplay(GameLoop loop, GameMode destination) {
        for (int frame = 0; frame < 2_000
                && (NativeLevelLoadAccess.isPending(GameServices.level())
                        || loop.getCurrentGameMode() == GameMode.TITLE_CARD); frame++) {
            loop.step();
        }
        assertFalse(NativeLevelLoadAccess.isPending(GameServices.level()), "native title load did not finish");
        assertEquals(destination, loop.getCurrentGameMode(), "the actual title loop must release to its destination");
        // PostTitleCardDestination completes BONUS_STAGE assembly at release;
        // LEVEL deliberately retains it for its first LevelLoop iteration.
        // Do not execute that gameplay tick before observing the saved water.
    }

}
