package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Modal owner callbacks precede the host pause gate in controlled production rows. */
@RequiresRom(SonicGame.SONIC_2)
class TestControlledLevelInputOverlay {
    private SharedLevel bootstrap;
    private final InputHandler input = new InputHandler();
    private final AtomicInteger overlays = new AtomicInteger();
    private final AtomicInteger ticks = new AtomicInteger();
    private boolean modal = true;

    @AfterEach void cleanup() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private GameLoop launch() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var controller = new GameplayFrameController() {
            @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot snapshot) { ticks.incrementAndGet(); return false; }
            @Override public void afterTick(CourseControl course, boolean advanced) { }
        };
        LevelInputOverlay overlay = controls -> { assertSame(input, controls); overlays.incrementAndGet(); return modal; };
        var module = new DelegatingGameModule(GameServices.module(), "test-controlled-overlay") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
            @Override public <T> T getGameService(Class<T> type) {
                return type == LevelInputOverlay.class ? type.cast(overlay) : super.getGameService(type);
            }
        };
        SessionManager.clear();
        GameModuleRegistry.setCurrent(module);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        fixture.stepIdleFrames(120);
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        ticks.set(0);
        return loop;
    }

    @Test void modalEnterReachesTheControllerWithoutAlsoTogglingHostPause() throws Exception {
        var loop = launch();
        input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY), org.lwjgl.glfw.GLFW.GLFW_PRESS);
        loop.step();
        assertFalse(loop.isUserPaused());
        assertEquals(1, overlays.get());
        assertEquals(1, ticks.get());
    }

    @Test void nonmodalEnterRetainsHostPauseAndPausedRowsStillReachTheOverlay() throws Exception {
        modal = false;
        var loop = launch();
        int pause = GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY);
        input.handleKeyEvent(pause, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        loop.step();
        assertTrue(loop.isUserPaused());
        assertEquals(1, overlays.get());
        assertEquals(0, ticks.get());
        modal = true;
        input.handleKeyEvent(pause, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        loop.step();
        input.handleKeyEvent(pause, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        loop.step();
        assertTrue(loop.isUserPaused(), "modal ownership cannot secretly unpause the host");
        assertEquals(3, overlays.get());
        assertEquals(0, ticks.get());
    }

    @Test void modalPadStartIsDispatchedOnceAndKeepsTheLogicalSnapshotAuthoritative() throws Exception {
        var loop = launch();
        var start = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
        input.setLogicalOverride(start);
        loop.step();
        assertFalse(loop.isUserPaused());
        assertEquals(1, overlays.get());
        assertEquals(1, ticks.get());
        assertSame(start, input.logical());
    }
}
