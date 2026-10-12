package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class TestNoWorldPresentation {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void realHubPresentationRunsWithoutAnActiveWorld() {
        TestEnvironment.resetAll();
        var input = new InputHandler(); var loop = new GameLoop(input);
        var screen = mock(MasterTitleScreen.class);
        loop.setMasterTitleScreenSupplier(() -> screen);
        loop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
        var retired = SessionManager.getCurrentGameplayMode();
        assertSame(retired, loop.resolveGameplayModeContext());
        SessionManager.clear();
        assertFalse(retired.isGameplayRuntimeReady());
        assertSame(retired, loop.resolveGameplayModeContext(), "Actual hub entry retains its bound retired context");
        assertNull(SessionManager.getCurrentGameplayMode());
        assertDoesNotThrow(loop::stepPresentationFrame);
        assertNull(loop.resolveGameplayModeContext());
        assertDoesNotThrow(loop::stepPresentationFrame);
        verify(screen, times(2)).update(input);
    }
}
