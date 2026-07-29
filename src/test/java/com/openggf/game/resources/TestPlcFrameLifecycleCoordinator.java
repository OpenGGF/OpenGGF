package com.openggf.game.resources;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlcFrameLifecycleCoordinator {

    @Test
    void nativeFadeKeepsOutgoingTokenAcrossCompletionCallback() {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        NativeFadeLifecycle.NativeBlockingFade fade = coordinator.beginNativeBlockingFade();
        var frame = coordinator.latchBeforeFadeUpdate();

        fade.wrapCompletion(() -> events.add("callback")).run();
        assertFalse(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        frame.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        frame.finish();

        var next = coordinator.latchBeforeFadeUpdate();
        assertTrue(next.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        next.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        next.finish();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(
                "service:PALETTE_FADE", "callback", "prepare:PALETTE_FADE",
                "service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL"), events);
    }

    @Test
    void tokenRejectsReuseDuplicatePreparationAndMissingPreparation() {
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(new ArrayList<>()));
        var missing = coordinator.latchBeforeFadeUpdate();
        missing.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, missing::finish);
        missing.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        missing.finish();
        assertThrows(IllegalStateException.class,
                () -> missing.claim(PlcLifecyclePhase.LAG));

        var duplicate = coordinator.latchBeforeFadeUpdate();
        duplicate.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
        duplicate.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class,
                () -> duplicate.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL));
        duplicate.finish();
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.PALETTE_FADE
                        || phase == PlcLifecyclePhase.ORDINARY_LEVEL;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }
}
