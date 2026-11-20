package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramRequest.Route;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.timeattack.CdTimeAttackScreen;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.openggf.control.InputActionMasks.ACTION_A;
import static com.openggf.control.InputActionMasks.ACTION_B;
import static com.openggf.control.InputActionMasks.ACTION_C;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class TestCdPauseExit {
    private Bk2FrameInput previous;

    @ParameterizedTest
    @ValueSource(ints = {ACTION_A, ACTION_B, ACTION_C})
    void timeAttackReturnsToItsMenu(int button) throws Exception {
        runStage(Route.TIME_ATTACK, (loop, scenes) -> {
            var state = GameServices.gameState();
            var levels = GameServices.level();
            var checkpoint = levels.getCheckpointState();
            assertEquals(1, state.getLives());
            checkpoint.restoreFromSaved(256, 256, 0, 0, 1);

            step(loop, 0, true);
            long pausedTime = levels.getLevelGamestate().getTimerFrames();
            step(loop, 0, false);
            assertEquals(pausedTime, levels.getLevelGamestate().getTimerFrames(), "level clock during pause");
            step(loop, button, false);

            assertAll(
                    () -> assertEquals(0, state.getLives(), "remaining lives"),
                    () -> assertFalse(state.isGamePaused(), "pause cleared"),
                    () -> assertFalse(checkpoint.isActive(), "checkpoint cleared"),
                    () -> assertEquals(pausedTime, levels.getLevelGamestate().getTimerFrames(), "no extra gameplay tick"),
                    () -> assertNull(scenes.flow().route(), "the stage returned to IPX"));
            for (int frame = 0; frame < 180 && loop.getCurrentGameMode() != GameMode.OPTIONS; frame++) {
                step(loop, 0, false);
            }
            assertEquals(GameMode.OPTIONS, loop.getCurrentGameMode());
            assertInstanceOf(CdTimeAttackScreen.class, scenes.lastProgramScreen());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {ACTION_A, ACTION_B, ACTION_C})
    void campaignRestartsFromTheBeginning(int button) throws Exception {
        runStage(Route.CAMPAIGN, (loop, scenes) -> {
            var state = GameServices.gameState();
            var levels = GameServices.level();
            var checkpoint = levels.getCheckpointState();
            var original = levels.getCurrentLevel();
            int lives = state.getLives();
            checkpoint.restoreFromSaved(256, 256, 0, 0, 1);

            step(loop, 0, true);
            step(loop, button, false);

            assertAll(
                    () -> assertEquals(lives - 1, state.getLives(), "one life spent"),
                    () -> assertFalse(state.isGamePaused(), "pause cleared"),
                    () -> assertFalse(checkpoint.isActive(), "checkpoint cleared"),
                    () -> assertEquals(Route.CAMPAIGN, scenes.flow().route(), "campaign still running"));
            for (int frame = 0; frame < 180 && levels.getCurrentLevel() == original; frame++) {
                step(loop, 0, false);
            }
            assertNotSame(original, levels.getCurrentLevel(), "stage reloaded");
        });
    }

    private void runStage(Route route, StageCheck check) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            SonicCdGameModule module = new SonicCdGameModule();
            var scenes = module.getGameService(CdSceneCoordinator.class);
            scenes.attachBackupRamHost(CdBackupRamHost.SESSION, 0);
            GameLoop loop = boot.boot(disc, module, 0, 0);
            if (route == Route.TIME_ATTACK) {
                scenes.timeAttackSelected(1);
                scenes.consumeResume();
            } else {
                scenes.titleExited(CdProgramFlow.EXIT_NEW_GAME);
            }
            scenes.stageStartingFromHost(GameServices.gameState());
            GameServices.level().skipPendingInitialTitleCardPresentation();
            try (var ignored = RecordedInputOwnership.acquire(loop.getInputHandler())) {
                for (int frame = 0; frame < 40; frame++) {
                    step(loop, 0, false);
                }
                check.run(loop, scenes);
            }
        }
    }

    private void step(GameLoop loop, int action, boolean start) {
        var input = new Bk2FrameInput(0, 0, action, start, "pause exit");
        loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(input, previous));
        previous = input;
        loop.step();
    }

    @FunctionalInterface
    private interface StageCheck {
        void run(GameLoop loop, CdSceneCoordinator scenes);
    }
}
