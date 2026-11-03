package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.titlecard.S3kTitleCardWorldAccess;
import com.openggf.level.LevelSetupFrameAccess;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fresh S3K Level: entries elapse two V-ints that the ROM states structurally.
 *
 * <p>The iteration that performs the load is Level's own setup frame. Level
 * installs Obj_TitleCard at loc_62B6, but loc_62CC first waits for VSync and
 * only then runs Process_Sprites (Obj_TitleCardInit queues the archives) and
 * Process_Nem_Queue_Init (sonic3k.asm:7762-7781, 62161-62205). The terrain wait
 * ends inside LoadLevelLoadBlock's loc_7870 loop, whose last iteration waits
 * for VSync before Process_Kos_Module_Queue retires the final module
 * (sonic3k.asm:9772-9782); that release iteration therefore owns a V-int.
 *
 * <p>A load issued between iterations has no setup frame: see
 * {@link TestS3kFreshTitlePlcLoadBoundary}, where the next iteration is the
 * loop's first.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshLevelSetupFrameVint {
    private static final int STEP_CAP = 600;

    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void setupFrameDefersTitleInitAndReleaseCountsLoadLoopVint(boolean recording) throws Exception {
        Runnable step = start(recording);
        GameServices.level().requestZoneAndAct(1, 0);
        int steps = 0;
        do {
            step.run();
            assertTrue(++steps < STEP_CAP, "destination never loaded inside an iteration");
        } while (!LevelSetupFrameAccess.inFreshLevelSetupFrame(GameServices.level()));
        // The loading iteration has just finished: it was Level's setup frame.
        assertEquals(1, GameServices.level().getCurrentZone());
        assertTrue(title().capture().artHandles().isEmpty(),
                "Obj_TitleCardInit has not been dispatched in Level's setup frame");
        assertNull(plc().capture().activeEntry(),
                "no Process_Nem_Queue_Init runs before loc_62CC's first Wait_VSync");
        assertFalse(plc().capture().queuedEntries().isEmpty(), "Level has queued its PLCs");
        int setupClock = GameServices.level().getObjectManager().getVblaCounter();

        step.run();
        assertFalse(LevelSetupFrameAccess.inFreshLevelSetupFrame(GameServices.level()));
        assertEquals(setupClock + 1, GameServices.level().getObjectManager().getVblaCounter(),
                "loc_62CC's first Wait_VSync is one counted V-int");
        assertEquals(4, title().capture().artHandles().size(),
                "Obj_TitleCardInit queues its four archives on the loop's first dispatch");
        assertNotNull(plc().capture().activeEntry(),
                "Process_Nem_Queue_Init arms the head in the same loc_62CC pass");

        boolean released = false;
        for (int i = 0; i < STEP_CAP && !released; i++) {
            boolean terrainWaitBefore = title().capture().freshLevelTerrainStarted()
                    && title().capture().freshLevelTransitionMode();
            int before = GameServices.level().getObjectManager().getVblaCounter();
            step.run();
            if (terrainWaitBefore && !title().capture().freshLevelTransitionMode()) {
                released = true;
                assertEquals(before + 1, GameServices.level().getObjectManager().getVblaCounter(),
                        "the loc_7870 iteration that retires the last module owns one V-int");
            }
        }
        assertTrue(released, "fresh title loop never released");
    }

    @org.junit.jupiter.api.Test
    void ownerInstalledByAnInIterationLoadWaitsForTheLoopsFirstDispatch() throws Exception {
        start(false);
        var level = GameServices.level();
        LevelSetupFrameAccess.beginIteration(level);
        level.loadZoneAndActAtFreshTitleCardBoundary(1, 0);
        assertTrue(LevelSetupFrameAccess.inFreshLevelSetupFrame(level), "a load inside an iteration is Level's setup frame");
        title().initialize(1, 0);
        S3kTitleCardWorldAccess.update(title(), level.getWorldSession());
        assertTrue(title().capture().artHandles().isEmpty(),
                "loc_62B6 installs the owner; loc_62CC has not reached Process_Sprites");
        assertTrue(title().capture().titleInitPending());
        LevelSetupFrameAccess.endIteration(level);

        LevelSetupFrameAccess.beginIteration(level);
        assertFalse(LevelSetupFrameAccess.inFreshLevelSetupFrame(level));
        S3kTitleCardWorldAccess.update(title(), level.getWorldSession());
        assertEquals(4, title().capture().artHandles().size(),
                "Obj_TitleCardInit runs on the loop's first post-VSync dispatch");
        assertFalse(title().capture().titleInitPending());
        LevelSetupFrameAccess.endIteration(level);
    }

    @org.junit.jupiter.api.Test
    void ownerInstalledOutsideAnIterationQueuesImmediately() throws Exception {
        start(false);
        var level = GameServices.level();
        level.loadZoneAndActAtFreshTitleCardBoundary(1, 0);
        assertFalse(LevelSetupFrameAccess.inFreshLevelSetupFrame(level), "a load between iterations has no setup frame");
        title().initialize(1, 0);
        assertEquals(4, title().capture().artHandles().size());
        assertFalse(title().capture().titleInitPending());
    }

    private static Runnable start(boolean recording) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        if (recording) {
            var driver = new RecordingFrameDriver(GameServices.sprites().getMainPlayable());
            return () -> driver.stepFrame(false, false, false, false, false);
        }
        var loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        return loop::step;
    }

    private static Sonic3kLevelTitlePlcService plc() {
        return com.openggf.game.session.WorldPlcLifecycleAccess.require(
                GameServices.level().getWorldSession(), Sonic3kLevelTitlePlcService.class);
    }

    private static Sonic3kTitleCardManager title() {
        return (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();
    }
}
