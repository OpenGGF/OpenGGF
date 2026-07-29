package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestPlcVBlankOrdering {

    @Test
    void ordinaryLevelServicesPlcBeforeEventsAndObjects() {
        List<String> calls = new ArrayList<>();
        GameModule module = mock(GameModule.class);
        PlcLifecycleService service = recordingService(calls);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(service);
        LevelManager level = mock(LevelManager.class);
        org.mockito.Mockito.doAnswer(ignored -> {
            calls.add("objects");
            return null;
        }).when(level).updateObjectPositionsWithoutTouches();

        LevelFrameTestStep.execute(context(module, calls), level, mock(Camera.class), () -> calls.add("physics"));

        assertEquals(List.of("vblank-service", "vint", "objects", "physics", "prepare"), calls);
    }

    @Test
    void vblankOnlyRowDoesNotServiceLevelPlc() {
        List<String> calls = new ArrayList<>();
        GameModule module = mock(GameModule.class);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(recordingService(calls));

        LevelFrameTestStep.serviceVBlankOnly(context(module, calls));

        assertEquals(List.of("vint"), calls);
    }

    @Test
    void completedEntryPreparesButDoesNotConsumeSuccessorUntilNextToken() {
        NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();
        queue.append(new PlcDefinition(0, List.of(
                new PlcEntry(0x100, 1), new PlcEntry(0x200, 2))),
                List.of(3, 5));
        queue.prepareHead();

        queue.servicePatterns(3);
        queue.prepareHead();

        var afterPreparation = queue.capture();
        assertEquals(5, afterPreparation.activeEntry().remainingPatterns());
        assertEquals(0, afterPreparation.queuedEntries().size());

        queue.servicePatterns(3);
        assertEquals(2, queue.capture().activeEntry().remainingPatterns());
    }

    @Test
    void orderedScanObservesAppendClearAndReplaceSynchronouslyInEitherSlotOrder() {
        MutableFacade facade = new MutableFacade();
        List<List<String>> observations = new ArrayList<>();

        facade.scan(
                () -> facade.append("append"),
                () -> observations.add(facade.snapshot()));
        assertEquals(List.of(List.of("append")), observations);

        observations.clear();
        facade.replace("seed");
        facade.scan(
                () -> observations.add(facade.snapshot()),
                facade::clear);
        assertEquals(List.of(List.of("seed")), observations);
        assertEquals(List.of(), facade.snapshot());

        observations.clear();
        facade.append("old");
        facade.scan(
                () -> facade.replace("replacement"),
                () -> observations.add(facade.snapshot()));
        assertEquals(List.of(List.of("replacement")), observations);

        observations.clear();
        facade.scan(
                () -> observations.add(facade.snapshot()),
                () -> facade.append("later"));
        assertEquals(List.of(List.of("replacement")), observations);
        assertEquals(List.of("replacement", "later"), facade.snapshot());
    }

    private static LevelFrameContext context(GameModule module, List<String> calls) {
        return new LevelFrameContext(module, null, null, NoOpBonusStageProvider.INSTANCE,
                null, null, null, null, new HardwareTimingService(),
                boundary -> {
                    if (boundary == com.openggf.game.timing.HardwareServiceBoundary.VINT_SERVICE) {
                        calls.add("vint");
                    }
                }, null);
    }

    private static PlcLifecycleService recordingService(List<String> calls) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                if (phase == PlcLifecyclePhase.ORDINARY_LEVEL) {
                    calls.add("vblank-service");
                }
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.ORDINARY_LEVEL;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                calls.add("prepare");
            }
        };
    }

    private static final class MutableFacade {
        private final List<String> queued = new ArrayList<>();

        void append(String value) {
            queued.add(value);
        }

        void clear() {
            queued.clear();
        }

        void replace(String value) {
            queued.clear();
            queued.add(value);
        }

        List<String> snapshot() {
            return List.copyOf(queued);
        }

        void scan(Runnable firstSlot, Runnable secondSlot) {
            firstSlot.run();
            secondSlot.run();
        }
    }
}
