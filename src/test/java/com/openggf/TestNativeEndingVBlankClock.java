package com.openggf;

import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.graphics.FadeManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeEndingVBlankClock {
    @Test void fadeCompletionCannotGiveTheIncomingLoopTheOutgoingClockTickAgain() {
        var lifecycle = new PlcFrameLifecycleCoordinator(mock(PlcLifecycleService.class));
        var fade = new FadeManager();
        int[] clocks = {0,0};
        fade.startFadeFromBlack(lifecycle.beginNativeBlockingFade().wrapCompletion(() -> { }));
        for (int i = 0; i < 20; i++) fade.update();
        lifecycle.runLogicalIteration(frame -> frame.serviceAlternateNativeVBlankClock(() -> clocks[0]++),
                fade::update, frame -> { frame.serviceAlternateNativeVBlankClock(() -> clocks[1]++); return null; });
        assertArrayEquals(new int[]{1,0}, clocks);
        assertFalse(fade.isActive());
    }
}
