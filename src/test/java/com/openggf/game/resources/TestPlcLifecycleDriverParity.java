package com.openggf.game.resources;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlcLifecycleDriverParity {

    @Test
    void liveAndHeadlessLogicalIterationsUseTheSameOrdering() {
        assertEquals(runRepresentativeIterations(), runRepresentativeIterations());
        assertEquals(List.of(
                "fade", "service:ORDINARY_LEVEL", "body",
                "prepare:ORDINARY_LEVEL",
                "service:PALETTE_FADE", "fade", "prepare:PALETTE_FADE",
                "body"), runRepresentativeIterations());
    }

    @Test
    void multiplePumpedStepsLatchSeparateTokensAndAdvanceFadeOnceEach() {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));

        coordinator.runLogicalIteration(
                () -> events.add("fade"), frame -> {
                    frame.claim(PlcLifecyclePhase.LAG);
                    return null;
                });
        coordinator.runLogicalIteration(
                () -> events.add("fade"), frame -> {
                    frame.claim(PlcLifecyclePhase.LAG);
                    return null;
                });

        assertEquals(List.of(
                "fade", "service:LAG", "fade", "service:LAG"), events);
    }

    private static List<String> runRepresentativeIterations() {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        coordinator.runLogicalIteration(
                () -> events.add("fade"), frame -> {
                    frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                    events.add("body");
                    frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
                    return null;
                });
        coordinator.beginNativeBlockingFade();
        coordinator.runLogicalIteration(
                () -> events.add("fade"), frame -> {
                    events.add("body");
                    return null;
                });
        return events;
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.ORDINARY_LEVEL
                        || phase == PlcLifecyclePhase.PALETTE_FADE;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }
}
