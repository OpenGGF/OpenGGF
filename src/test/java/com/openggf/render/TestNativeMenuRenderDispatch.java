package com.openggf.render;

import com.openggf.debug.DebugState;
import com.openggf.game.GameMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;

class TestNativeMenuRenderDispatch {
    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {
            "OPTIONS", "COMPETITION_MENU", "COMPETITION", "COMPETITION_RESULTS"})
    void nativeScreensOwnTheirClearAndDrawEvenWhenOrdinaryDebugViewIsEnabled(GameMode mode) {
        var dispatcher = new EngineRenderDispatcher();
        var clear = mock(EngineRenderDispatcher.ClearActions.class);
        var draw = mock(EngineRenderDispatcher.DrawActions.class);
        dispatcher.applyClearColor(mode, clear);
        dispatcher.draw(mode, true, DebugState.PATTERNS_VIEW, draw);
        verify(clear).nativeMenu();
        verify(draw).nativeMenu();
        verify(clear, never()).level();
        verify(draw, never()).level();
        verify(draw, never()).debugPatterns();
    }
}
