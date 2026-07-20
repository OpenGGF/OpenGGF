package com.openggf.render;

import com.openggf.debug.DebugState;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEngineRenderDispatcherNativeNotice {

    @Test
    void nativeNoticeClearsBlackAndDrawsNotice() {
        EngineRenderDispatcher dispatcher = new EngineRenderDispatcher();
        TestEngineRenderDispatcher.RecordingClearActions clearActions =
                new TestEngineRenderDispatcher.RecordingClearActions();
        TestEngineRenderDispatcher.RecordingDrawActions drawActions =
                new TestEngineRenderDispatcher.RecordingDrawActions();

        dispatcher.applyClearColor(GameMode.NATIVE_MOD_NOTICE, clearActions);
        dispatcher.draw(GameMode.NATIVE_MOD_NOTICE, false, DebugState.NONE, drawActions);

        assertEquals(List.of("black"), clearActions.calls);
        assertEquals(List.of("nativeModNotice"), drawActions.calls);
    }
}
