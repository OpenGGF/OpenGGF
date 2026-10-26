package com.openggf.game.nativemenu;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeMenuController {
    @Test
    void transitionSetupRunsOnceAfterSuccessfulInputConsumption() {
        var controller = new NativeMenuController();
        var screen = new TransitionScreen();
        var input = mock(InputHandler.class);
        controller.open(screen);
        controller.beginFrame();
        screen.requestSetup = true;

        controller.update(input, false);

        assertEquals(0, screen.setups);
        controller.completeFrame();
        controller.completeFrame();
        assertEquals(1, screen.setups);
        verify(input).update();
    }

    @Test
    void failedFadedAndReplacedUpdatesCannotCompleteStaleSetup() {
        var controller = new NativeMenuController();
        var screen = new TransitionScreen();
        var input = mock(InputHandler.class);
        controller.open(screen);
        controller.beginFrame();
        screen.requestSetup = true;
        screen.failUpdate = true;
        assertThrows(IllegalStateException.class, () -> controller.update(input, false));
        controller.completeFrame();
        assertEquals(0, screen.setups);

        controller.beginFrame();
        screen.failUpdate = false;
        controller.update(input, true);
        controller.completeFrame();
        assertEquals(0, screen.setups);

        controller.beginFrame();
        controller.update(input, false);
        var replacement = new TransitionScreen();
        controller.open(replacement);
        controller.completeFrame();
        assertEquals(0, screen.setups);
        assertEquals(0, replacement.setups);
        assertFalse(screen.staged);
        verify(input, times(3)).update();
    }

    @Test
    void newFrameAndFailedInputAdvanceDiscardCompletionCandidate() {
        var controller = new NativeMenuController();
        var screen = new TransitionScreen();
        var input = mock(InputHandler.class);
        controller.open(screen);
        controller.beginFrame();
        screen.requestSetup = true;
        controller.update(input, false);
        controller.beginFrame();
        controller.completeFrame();
        assertEquals(0, screen.setups);

        doThrow(new IllegalStateException("input advance")).when(input).update();
        assertThrows(IllegalStateException.class, () -> controller.update(input, false));
        controller.completeFrame();
        assertEquals(0, screen.setups);
    }

    @Test
    void gameplayAndResultsKeepTheirOwnNativePlcLoopAuthority() {
        assertEquals(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_SELECT,
                NativeMenuController.phase(GameMode.OPTIONS));
        assertEquals(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_SELECT,
                NativeMenuController.phase(GameMode.COMPETITION_MENU));
        assertEquals(com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL,
                NativeMenuController.phase(GameMode.COMPETITION));
        assertEquals(com.openggf.game.resources.PlcLifecyclePhase.TWO_PLAYER_RESULTS,
                NativeMenuController.phase(GameMode.COMPETITION_RESULTS));
    }
    @Test
    void unavailableCompetitionDoesNotRetireTheActiveOptionsScreen() {
        var controller = new NativeMenuController();
        var options = screen(GameMode.OPTIONS);
        assertTrue(controller.open(options));
        assertFalse(controller.open(null));
        assertSame(options, controller.screen());
        verify(options, never()).reset();
    }

    @Test
    void fadingConsumesInputWithoutAdvancingNativeControls() {
        var controller = new NativeMenuController();
        var options = screen(GameMode.OPTIONS);
        var input = mock(InputHandler.class);
        controller.open(options);
        assertSame(NativeMenuScreen.Exit.NONE, controller.update(input, true));
        verify(options, never()).update(input);
        verify(options, never()).consumeExit();
        verify(input).update();
    }

    @Test
    void commandIsReadBeforeInputEdgesAreAdvancedAndReplacementRetiresOldScreen() {
        var controller = new NativeMenuController();
        var options = screen(GameMode.OPTIONS);
        var competition = screen(GameMode.COMPETITION_MENU);
        var input = mock(InputHandler.class);
        var exit = new NativeMenuScreen.Exit(NativeMenuScreen.Destination.COMPETITION, null, false);
        when(options.consumeExit()).thenReturn(exit);
        controller.open(options);
        assertSame(exit, controller.update(input, false));
        var order = inOrder(options, input);
        order.verify(options).initialize();
        order.verify(options).update(input);
        order.verify(options).consumeExit();
        order.verify(input).update();
        controller.open(competition);
        verify(options).reset();
        verify(competition).initialize();
        controller.close();
        verify(competition).reset();
        assertNull(controller.screen());
    }

    @Test
    void failedInitializationCannotLeaveAnActivePartialScreen() {
        var controller = new NativeMenuController();
        var options = screen(GameMode.OPTIONS);
        doThrow(new IllegalStateException("ROM unavailable")).when(options).initialize();
        assertThrows(IllegalStateException.class, () -> controller.open(options));
        assertNull(controller.screen());
        verify(options).reset();
    }

    private static NativeMenuScreen screen(GameMode mode) {
        var value = mock(NativeMenuScreen.class);
        when(value.mode()).thenReturn(mode);
        return value;
    }

    private static final class TransitionScreen implements NativeMenuScreen {
        boolean requestSetup, staged, failUpdate;
        int setups;
        @Override public void initialize() { staged = false; }
        @Override public void update(InputHandler input) {
            staged = requestSetup;
            if (failUpdate) throw new IllegalStateException("screen update");
        }
        @Override public void afterFrame() {
            boolean setup = staged;
            staged = false;
            if (setup) setups++;
        }
        @Override public void draw() { }
        @Override public void setClearColor() { }
        @Override public void reset() { staged = false; }
        @Override public Exit consumeExit() { return Exit.NONE; }
    }
}
