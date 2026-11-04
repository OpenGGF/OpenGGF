package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.timing.HardwareWorkKind;
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
 * The ending ($D01) shows no title card: Obj_TitleCardInit deletes itself
 * before queuing archives (sonic3k.asm:62148-62160). Level: still runs
 * LoadLevelLoadBlock, which queues ArtKosM_SSZ2 at tile 0 and waits at loc_7870
 * until Kos_modules_left is clear (sonic3k.asm:9737-9782), before LevelSetup's
 * Ending_ScreenInit director first runs. Both production drivers must keep
 * that queue submission and wait.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingFreshLevelTerrainWait {
    private static final int STEP_CAP = 800;
    /** ArtKosM_SSZ2, the LevelLoadBlock entry for $D01, queued at tile 0. */
    private static final int ENDING_TERRAIN = 0x1CFB44;

    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingLoadQueuesTerrainAndHoldsTheDirectorUntilTheQueueDrains(boolean recording)
            throws Exception {
        Runnable step = start(recording);
        long terrainBefore = endingTerrainJobs();
        GameServices.level().requestZoneAndAct(0x0D, 1);
        boolean terrainQueued = false;
        boolean terrainStarted = false;
        boolean released = false;
        for (int i = 0; i < STEP_CAP && !released; i++) {
            boolean terrainWait = title().capture().freshLevelTransitionMode();
            step.run();
            var after = title().capture();
            if (GameServices.level().getCurrentZone() != 0x0D) continue;
            assertFalse(title().isOverlayActive(), "the deleted ending owner has no presentation");
            var modules = S3kRuntimeArtCoordinator.current().moduleQueue();
            if (after.freshLevelTerrainStarted() && after.freshLevelTransitionMode()) {
                terrainStarted = true;
                terrainQueued |= endingTerrainJobs() > terrainBefore;
                assertTrue(director() == null || director().routine() == 0,
                        "Ending_ScreenInit's director runs only after LoadLevelLoadBlock");
                var camera = GameServices.camera();
                assertTrue(camera.getX() != 0x200 || camera.getY() != 0x200,
                        "the wait shows Get_LevelSizeStart's camera; Ending_ScreenInit's $200 follows it");
            }
            if (terrainWait && !after.freshLevelTransitionMode()) {
                released = true;
                assertFalse(modules.hasPendingPhysicalModules(),
                        "loc_7870 releases only once Kos_modules_left is clear");
            }
        }
        assertTrue(terrainQueued, "LoadLevelLoadBlock never queued the ending terrain (terrain wait seen: "
                + terrainStarted + ", released: " + released + ")");
        assertTrue(released, "ending terrain wait never released");
        for (int i = 0; i < 8 && (director() == null || director().routine() == 0); i++) step.run();
        assertEquals(2, director().routine(), "the director's first dispatch follows LevelSetup");
        assertEquals(0x200, GameServices.camera().getX() & 0xFFFF, "Ending_ScreenInit's camera");
        assertEquals(0x200, GameServices.camera().getY() & 0xFFFF, "Ending_ScreenInit's camera");
    }

    private static long endingTerrainJobs() {
        return SessionManager.getCurrentGameplayMode().hardwareTiming().capture().jobs().stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_MODULE_QUEUE
                        && job.romSourceAddress() == ENDING_TERRAIN && job.destinationAddress() == 0)
                .count();
    }

    private static EndingRescueDirectorObjectInstance director() {
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object instanceof EndingRescueDirectorObjectInstance d) return d;
        }
        return null;
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
        var fade = SessionManager.getCurrentGameplayMode().getFadeManager();
        // Headless GameLoop has no UiRenderPipeline to advance its presentation fade.
        return () -> { fade.update(); loop.step(); };
    }

    private static Sonic3kTitleCardManager title() {
        return (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();
    }
}
