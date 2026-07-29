package com.openggf.game.resources;

import com.openggf.LevelFrameStep;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlcLifecycleDriverParity {

    @Test
    void logicalIterationPinsServiceFadePreparationAndBodyOrdering() {
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

    @Test
    void publicPlcFrameEntriesRequireTheCallersLatchedToken() {
        List<String> phaseOwned = List.of(
                "execute", "executeWithPause", "serviceVBlankOnly",
                "executeHardwareTimedObjectScan");
        for (var method : LevelFrameStep.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && phaseOwned.contains(method.getName())) {
                assertTrue(method.getParameterCount() > 1
                                && method.getParameterTypes()[1]
                                == PlcFrameLifecycleCoordinator.PlcLifecycleFrame.class,
                        method.toString());
            }
        }
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
