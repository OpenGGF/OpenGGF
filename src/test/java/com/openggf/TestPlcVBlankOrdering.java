package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
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

        LevelFrameStep.execute(context(module, calls), level, mock(Camera.class), () -> calls.add("physics"));

        assertEquals(List.of("vblank-service", "vint", "objects", "physics", "prepare"), calls);
    }

    @Test
    void vblankOnlyRowDoesNotServiceLevelPlc() {
        List<String> calls = new ArrayList<>();
        GameModule module = mock(GameModule.class);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(recordingService(calls));

        LevelFrameStep.serviceVBlankOnly(context(module, calls));

        assertEquals(List.of("vint"), calls);
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
}
