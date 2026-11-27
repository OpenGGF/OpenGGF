package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdTitleCard;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCdStageDemos {
    private record Position(int x, int y) { }
    private record Observation(CdStagePayload stage, Position spawn, boolean showsTitleCard, Position afterPlayback, int rings,
                               int returnedAtLevelFrame, GameMode returnedMode) { }

    @Test
    void idleTitlePlaysEachOrdinaryDemo() throws Exception {
        List<Observation> observations = new ArrayList<>();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            boot.setBootToTitle(true);
            var module = new SonicCdGameModule();
            GameLoop loop = boot.boot(disc, module, 0, 0);
            var flow = module.getGameService(CdSceneCoordinator.class).flow();
            for (var demo : CdStageDemo.values()) {
                // Leave the opening and special-stage playback out of this stage test.
                flow.titleExited(CdProgramFlow.EXIT_DEMO);
                stepUntil(loop, 2400, () -> loop.getCurrentGameMode() == GameMode.LEVEL);
                var level = GameServices.level();
                var stage = (CdLevel) level.getCurrentLevel();
                var player = GameServices.sprites().getMainPlayable();
                var spawn = new Position(player.getCentreX(), player.getCentreY());
                boolean titleCard = level.getObjectManager().getActiveObjects().stream()
                        .anyMatch(CdTitleCard.class::isInstance);
                int sampleFrame = demo == CdStageDemo.R82A ? 120 : 360;
                stepUntil(loop, 500, () -> level.getFrameCounter() == sampleFrame);
                var afterPlayback = new Position(player.getCentreX(), player.getCentreY());
                int rings = player.getRingCount();

                boolean naturalEnd = demo != CdStageDemo.R82A;
                if (!naturalEnd) {
                    pressStart(loop);
                }
                stepUntil(loop, naturalEnd ? 2200 : 90, () -> loop.getCurrentGameMode() == GameMode.TITLE_SCREEN);
                observations.add(new Observation(stage.payload(), spawn, titleCard, afterPlayback, rings,
                        level.getFrameCounter(), loop.getCurrentGameMode()));
            }
        }

        // Observations from an uninterrupted USA disc idle rotation, sampled
        // at iteration 360 (R11A and R43C) and iteration 120 (R82A).
        assertEquals(List.of(
                new Observation(CdStagePayload.R11A, new Position(64, 1899), true, new Position(796, 1820),
                        1, 2025, GameMode.TITLE_SCREEN),
                new Observation(CdStagePayload.R43C, new Position(976, 940), false, new Position(1578, 1254),
                        0, 2006, GameMode.TITLE_SCREEN),
                new Observation(CdStagePayload.R82A, new Position(6947, 1484), false, new Position(7014, 1452),
                        0, 120, GameMode.TITLE_SCREEN)), observations);
    }

    @Test
    void titleIdleTimeoutStartsPalmtreePanicDemo() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            GameLoop loop = idleUntilFirstDemo(boot, disc);
            assertEquals(CdStageDemo.R11A, loop.getCurrentGameMode() == GameMode.LEVEL
                    && GameServices.level().getCurrentLevel() instanceof CdLevel stage ? stage.demo() : null);
        }
    }

    @Test
    void startDuringDemoTitleCardLetsPlaybackContinue() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            GameLoop loop = idleUntilFirstDemo(boot, disc);
            stepUntil(loop, 60, () -> !GameServices.fade().isActive());
            var level = GameServices.level();
            if (loop.getCurrentGameMode() != GameMode.LEVEL || GameServices.fade().isActive()
                    || !(level.getCurrentLevel() instanceof CdLevel stage)
                    || stage.demo() != CdStageDemo.R11A || !stage.flags().ctrlLocked) {
                throw new IllegalStateException("Expected a visible Palmtree Panic demo with controls locked");
            }
            int beforeStart = level.getFrameCounter();
            pressStart(loop);
            assertEquals(beforeStart + 1, level.getFrameCounter(), "Start must let the demo play its next frame");
        }
    }

    private static GameLoop idleUntilFirstDemo(HeadlessGameBoot boot, CdDiscDataSource disc) throws Exception {
        boot.setBootToTitle(true);
        GameLoop loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
        stepUntil(loop, 9000, () -> loop.getCurrentGameMode() == GameMode.LEVEL);
        return loop;
    }

    private static void pressStart(GameLoop loop) {
        var input = loop.getInputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
        try {
            loop.step();
        } finally {
            input.clearLogicalOverride();
        }
    }

    private static void stepUntil(GameLoop loop, int max, BooleanSupplier reached) {
        for (int step = 0; step < max && !reached.getAsBoolean(); step++) loop.step();
    }
}
