package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
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
    private record Observation(CdStagePayload stage, Position spawn, Position afterPlayback, int rings,
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
                stepUntil(loop, 500, () -> level.getFrameCounter() == 360);
                var afterPlayback = new Position(player.getCentreX(), player.getCentreY());
                int rings = player.getRingCount();

                boolean naturalEnd = demo == CdStageDemo.R11A;
                if (!naturalEnd) {
                    loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                            PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
                    loop.step();
                    loop.getInputHandler().clearLogicalOverride();
                }
                stepUntil(loop, naturalEnd ? 2200 : 90, () -> loop.getCurrentGameMode() == GameMode.TITLE_SCREEN);
                observations.add(new Observation(stage.payload(), spawn, afterPlayback, rings,
                        level.getFrameCounter(), loop.getCurrentGameMode()));
            }
        }

        // Start positions come from the demo MMDs. Iteration 360 pins observable
        // playback through the shared level loop. The two Start exits stop there.
        assertEquals(List.of(
                new Observation(CdStagePayload.R11A, new Position(64, 1899), new Position(796, 1820),
                        1, 2025, GameMode.TITLE_SCREEN),
                new Observation(CdStagePayload.R43C, new Position(976, 940), new Position(1013, 940),
                        0, 360, GameMode.TITLE_SCREEN),
                new Observation(CdStagePayload.R82A, new Position(6947, 1484), new Position(7093, 1406),
                        1, 360, GameMode.TITLE_SCREEN)), observations);
    }

    private static void stepUntil(GameLoop loop, int max, BooleanSupplier reached) {
        for (int step = 0; step < max && !reached.getAsBoolean(); step++) loop.step();
    }
}
