package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.ScenePcmSource;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.soniccd.audio.CdFmSoundRequestService;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdTitleCard;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramRequest.Route;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.timeattack.CdTimeAttackScreen;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.openggf.control.InputActionMasks.ACTION_A;
import static com.openggf.control.InputActionMasks.ACTION_B;
import static com.openggf.control.InputActionMasks.ACTION_C;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

@EnabledOnOs(OS.LINUX)
class TestCdPauseExit {
    private Bk2FrameInput previous;

    @ParameterizedTest
    @ValueSource(ints = {ACTION_A, ACTION_B, ACTION_C})
    void timeAttackReturnsToItsMenu(int button) throws Exception {
        runStageOnBothInputs(Route.TIME_ATTACK, (loop, scenes) -> {
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
                    () -> assertFalse(loop.isUserPaused(), "host pause cleared"),
                    () -> assertFalse(state.isGamePaused(), "ROM pause cleared"),
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
        runStageOnBothInputs(Route.CAMPAIGN, (loop, scenes) -> {
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
                    () -> assertFalse(loop.isUserPaused(), "host pause cleared"),
                    () -> assertFalse(state.isGamePaused(), "ROM pause cleared"),
                    () -> assertFalse(checkpoint.isActive(), "checkpoint cleared"),
                    () -> assertEquals(Route.CAMPAIGN, scenes.flow().route(), "campaign still running"));
            for (int frame = 0; frame < 180 && levels.getCurrentLevel() == original; frame++) {
                step(loop, 0, false);
            }
            assertNotSame(original, levels.getCurrentLevel(), "stage reloaded");
        });
    }

    @ParameterizedTest
    // QQZ act 3 uses StageMainLoopRestart (common/main.asm:395-402).
    // Tidal Tempest act 2 uses StageMainLoopRestart (r4/main.asm:368-375).
    @CsvSource({"R11A, false", "R51A, true", "R52A, true", "R53D, false", "R42A, false"})
    void campaignPauseReloadUsesStageStart(CdStagePayload payload, boolean retainsCheckpoint) throws Exception {
        runStageOnBothInputs(Route.CAMPAIGN, payload, (loop, scenes) -> {
            var levels = GameServices.level();
            var original = (CdLevel) levels.getCurrentLevel();
            var state = GameServices.gameState();
            var oldLevelState = levels.getLevelGamestate();
            var player = GameServices.sprites().getMainPlayable();
            int lives = state.getLives();
            var checkpoint = (com.openggf.game.CheckpointState) levels.getCheckpointState();
            checkpoint.restoreFromSaved(original.startX() + 160, original.startY() - 32, 64, 128, 1);
            checkpoint.saveActTimer(900);
            checkpoint.saveRingState(32, 2);
            player.setCentreX((short) (original.startX() + 80));
            player.setRingCount(49);
            oldLevelState.setRings(49);
            oldLevelState.setRingExtraLifeFlags(2);
            oldLevelState.setTimerFrames(1200);

            step(loop, 0, true);
            step(loop, ACTION_A, false);
            for (int frame = 0; frame < 180 && levels.getCurrentLevel() == original; frame++) {
                step(loop, 0, false);
            }

            assertNotSame(original, levels.getCurrentLevel(), "stage reloaded");
            var restarted = GameServices.sprites().getMainPlayable();
            var levelState = levels.getLevelGamestate();
            var saved = (com.openggf.game.CheckpointState) levels.getCheckpointState();
            assertAll(payload.name(),
                    () -> assertFalse(levels.getObjectManager().getActiveObjects().stream()
                            .anyMatch(CdTitleCard.class::isInstance), "title card stays loaded"),
                    () -> assertEquals(original.startX(), restarted.getCentreX(), "stage-start X"),
                    () -> assertEquals(original.startY(), restarted.getCentreY(), "stage-start Y"),
                    () -> assertEquals(retainsCheckpoint ? 1 : -1, saved.getLastCheckpointIndex(),
                            "saved checkpoint number"),
                    () -> assertEquals(retainsCheckpoint ? 900 : 0, saved.getSavedTimerFrames(),
                            "saved checkpoint time"),
                    () -> assertEquals(1, levelState.getTimerFrames(), "first tick of reset clock"),
                    () -> assertEquals(0, restarted.getRingCount(), "player rings reset"),
                    () -> assertEquals(0, levelState.getRings(), "HUD rings reset"),
                    () -> assertEquals(0, levelState.getRingExtraLifeFlags(), "ring life flags reset"),
                    () -> assertEquals(lives - 1, state.getLives(), "one life spent"));
        });
    }

    @Test
    void liveStartPausesAudioAndAllowsFrameStep() throws Exception {
        runStage(Route.CAMPAIGN, false, (loop, scenes) -> {
            var state = GameServices.gameState();
            var level = GameServices.level().getLevelGamestate();
            int lives = state.getLives();
            var audio = GameServices.audio();
            class AudioProbe implements ScenePcmSource {
                boolean paused;
                @Override public void render(short[] target, int stereoFrames) { }
                @Override public void onHostPause() { paused = true; }
                @Override public boolean onHostResume() { paused = false; return true; }
            }
            var probe = new AudioProbe();
            audio.setScenePcmSource(probe);
            try {
                step(loop, ACTION_A | ACTION_B | ACTION_C, false);
                step(loop, 0, false);
                long runningTime = level.getTimerFrames();
                step(loop, 0, true);
                step(loop, 0, false);
                assertAll(
                        () -> assertTrue(loop.isUserPaused(), "live Start enters host pause"),
                        () -> assertFalse(state.isGamePaused(), "live Start leaves ROM pause clear"),
                        () -> assertTrue(probe.paused, "audio received host pause"),
                        () -> assertEquals(PresentationMode.SILENT, loop.presentationModeForOuterFrame(false, false)),
                        () -> assertEquals(runningTime, level.getTimerFrames(), "host pause freezes the level clock"),
                        () -> assertEquals(lives, state.getLives(), "unpaused A/B/C and Start spend no lives"),
                        () -> assertEquals(Route.CAMPAIGN, scenes.flow().route(), "unpaused input leaves campaign running"));

                int frameStep = GameServices.configuration().getInt(SonicConfiguration.FRAME_STEP_KEY);
                loop.getInputHandler().handleKeyEvent(frameStep, GLFW_PRESS);
                step(loop, 0, false);
                loop.getInputHandler().handleKeyEvent(frameStep, GLFW_RELEASE);
                step(loop, 0, false);
                assertEquals(runningTime + 1, level.getTimerFrames(), "frame-step runs one gameplay tick");
                step(loop, ACTION_A, true);
                assertAll(
                        () -> assertFalse(loop.isUserPaused(), "Start resumes host pause"),
                        () -> assertFalse(probe.paused, "audio received host resume"),
                        () -> assertEquals(lives, state.getLives(), "Start takes priority over A"));
            } finally {
                audio.setScenePcmSource(null);
            }
        });
    }

    @Test
    void hostPausedCampaignExitsOnLastLife() throws Exception {
        runStage(Route.CAMPAIGN, false, (loop, scenes) -> {
            var state = GameServices.gameState();
            NativeGameStateOps.restoreLives(state, 1);
            step(loop, 0, true);
            step(loop, ACTION_B, false);
            assertAll(
                    () -> assertEquals(0, state.getLives(), "last life spent"),
                    () -> assertFalse(loop.isUserPaused(), "game over releases host pause"),
                    () -> assertNull(scenes.flow().route(), "game over returns to IPX"));
        });
    }

    @ParameterizedTest
    @CsvSource({
            "R41A, CAMPAIGN, true",
            "R51A, CAMPAIGN, true",
            "R11A, CAMPAIGN, false",
            "R51A, TIME_ATTACK, false"
    })
    void pauseRestartReleasesHeldFmEffectsOnlyOnSupportedRoutes(
            CdStagePayload payload, Route route, boolean releasesEffects) throws Exception {
        runStageOnBothInputs(route, payload, (loop, scenes) -> {
            var fm = (CdFmSoundRequestService) GameServices.module().getAudioProfile().createAudioRequestService();
            step(loop, 0, true);
            assertFalse(fm.snapshot().jumpCondition(), "pause alone leaves held FM effects playing");

            step(loop, ACTION_A, false);
            assertFalse(loop.isUserPaused(), "pause action exits host pause");
            assertFalse(GameServices.gameState().isGamePaused(), "pause action exits ROM pause");
            assertEquals(releasesEffects, fm.snapshot().jumpCondition(),
                    payload + " " + route + " held FM effect release");
        });
    }

    private void runStageOnBothInputs(Route route, StageCheck check) throws Exception {
        runStageOnBothInputs(route, CdStagePayload.R11A, check);
    }

    private void runStageOnBothInputs(Route route, CdStagePayload payload, StageCheck check) throws Exception {
        runStage(route, payload, true, check);
        runStage(route, payload, false, check);
    }

    private void runStage(Route route, boolean recorded, StageCheck check) throws Exception {
        runStage(route, CdStagePayload.R11A, recorded, check);
    }

    private void runStage(Route route, CdStagePayload payload, boolean recorded, StageCheck check) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            SonicCdGameModule module = new SonicCdGameModule();
            var scenes = module.getGameService(CdSceneCoordinator.class);
            scenes.attachBackupRamHost(CdBackupRamHost.SESSION, 0);
            GameLoop loop = boot.boot(disc, module,
                    SonicCdZoneRegistry.zoneIndex(payload), SonicCdZoneRegistry.actIndex(payload));
            if (route == Route.TIME_ATTACK) {
                scenes.timeAttackSelected(1);
                scenes.consumeResume();
            } else {
                scenes.titleExited(CdProgramFlow.EXIT_NEW_GAME);
            }
            scenes.stageStartingFromHost(GameServices.gameState());
            GameServices.level().skipPendingInitialTitleCardPresentation();
            previous = null;
            try (var ignored = recorded ? RecordedInputOwnership.acquire(loop.getInputHandler()) : null) {
                var levelState = GameServices.level().getLevelGamestate();
                for (int frame = 0; frame < 360 && levelState.getTimerFrames() == 0; frame++) {
                    step(loop, 0, false);
                }
                assertTrue(levelState.getTimerFrames() > 0, "stage reached live gameplay");
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
