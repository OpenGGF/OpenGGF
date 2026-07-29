package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.resources.PlcVBlankService;
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
        PlcVBlankService service = () -> calls.add("vblank-service");
        when(module.getGameService(PlcVBlankService.class)).thenReturn(service);
        LevelManager level = mock(LevelManager.class);
        org.mockito.Mockito.doAnswer(ignored -> {
            calls.add("objects");
            return null;
        }).when(level).updateObjectPositionsWithoutTouches();

        LevelFrameStep.execute(context(module, calls), level, mock(Camera.class), () -> calls.add("physics"));

        assertEquals(List.of("vblank-service", "vint", "objects", "physics"), calls);
    }

    @Test
    void vblankOnlyRowDoesNotServiceLevelPlc() {
        List<String> calls = new ArrayList<>();
        GameModule module = mock(GameModule.class);
        when(module.getGameService(PlcVBlankService.class)).thenReturn(() -> calls.add("vblank-service"));

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
}
