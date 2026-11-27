package com.openggf.game.soniccd.flow;

import com.openggf.GameLoopRewindTestAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestCdStageLoadRewind {
    private static final int HISTORY_FRAMES = 8;

    @BeforeEach
    void enableLiveRewind() {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_F10);
        config.setSessionOverride(SonicConfiguration.REWIND_BUDGET_SCOPE, "act");
        config.setSessionOverride(SonicConfiguration.REWIND_BUDGET_SECONDS, "unlimited");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
    }

    @AfterEach
    void resetEnvironment() {
        TestEnvironment.resetAll();
    }

    @Test
    void heldRewindStopsAtTimeWarpArrival() throws Exception {
        try (var game = new CdFlowHarness(null)) {
            recordSourceHistory(game);
            game.place(0x1200, 0xB0, 0x1160, 0x69);
            var player = game.player();
            player.setAir(true);
            player.setXSpeed((short) 0x600);
            player.setGSpeed((short) 0x600);
            var time = game.timeTravel();
            time.touchTimePost(false);
            game.until(() -> time.warpTimer() != 0, 2, CdFlowHarness.RIGHT);
            // Advance the post timer to its trigger. The player routine still saves the carry and
            // requests the restart, and the program host runs the cutscene and loads R11D.
            while (time.warpTimer() < 210) {
                time.vIntTick(false);
            }
            assertHeldRewindStopsAtArrival(game, CdStagePayload.R11D);
        }
    }

    @Test
    void heldRewindStopsAtNextActArrival() throws Exception {
        try (var game = new CdFlowHarness(null)) {
            recordSourceHistory(game);
            // Approach the disc's signpost with fewer than 50 rings so results lead directly to act 2.
            game.place(9728, 489, 9568, 393);
            game.until(() -> game.row().timerPaused(), 360, CdFlowHarness.RIGHT);
            assertHeldRewindStopsAtArrival(game, CdStagePayload.R12A);
        }
    }

    private static void recordSourceHistory(CdFlowHarness game) {
        game.newGame();
        game.until(() -> !game.row().ctrlLocked() && !game.row().fading(), 900, 0);
        game.idle(HISTORY_FRAMES);
        var source = controller(game);
        assertTrue(source != null && source.currentFrame() > source.earliestAvailableFrame(),
                "Palmtree Panic act 1 Present must have rewindable history");
    }

    private static void assertHeldRewindStopsAtArrival(CdFlowHarness game, CdStagePayload destination) {
        game.until(() -> game.loop.getCurrentGameMode() == GameMode.LEVEL
                && game.level() != null && game.level().payload() == destination
                && !game.row().ctrlLocked() && !game.row().fading()
                && controller(game) != null, 2400, 0);
        var arrival = state(game);
        assertEquals(0, controller(game).earliestAvailableFrame(), "the destination timeline starts at zero");

        game.idle(HISTORY_FRAMES);
        assertTrue(controller(game).currentFrame() > arrival.rewindFrame(), "the destination records new history");

        var input = game.loop.getInputHandler();
        input.handleKeyEvent(GLFW_KEY_F10, GLFW_PRESS);
        try {
            game.idle(controller(game).currentFrame());
            var floor = state(game);
            game.idle(HISTORY_FRAMES);
            assertEquals(new StageState(0, destination, floor.time(), floor.x(), floor.y(), floor.rings(),
                            arrival.timeTravel()),
                    state(game), "holding rewind past the floor leaves the destination at its start");
        } finally {
            input.handleKeyEvent(GLFW_KEY_F10, GLFW_RELEASE);
        }
    }

    private static RewindController controller(CdFlowHarness game) {
        return GameLoopRewindTestAccess.owner(game.loop).getRewindController();
    }

    private static StageState state(CdFlowHarness game) {
        var row = game.row();
        return new StageState(controller(game).currentFrame(), row.payload(), row.time(),
                row.x(), row.y(), row.rings(), game.timeTravel().capture());
    }

    private record StageState(int rewindFrame, CdStagePayload payload, long time, int x, int y, int rings,
                              CdTimeTravel.Snapshot timeTravel) { }
}
