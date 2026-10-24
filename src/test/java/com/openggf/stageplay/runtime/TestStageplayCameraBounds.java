package com.openggf.stageplay.runtime;

import com.openggf.level.Level;
import com.openggf.stageplay.StageplayTimeline.CameraPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayCameraBounds {
    private static Level level(int minX, int maxX, int minY, int maxY) {
        var level = mock(Level.class);
        when(level.getMinX()).thenReturn(minX);
        when(level.getMaxX()).thenReturn(maxX);
        when(level.getMinY()).thenReturn(minY);
        when(level.getMaxY()).thenReturn(maxY);
        return level;
    }

    @Test void nativeMaximaAreCameraOriginsRatherThanTerrainDimensions() {
        var level = level(0, 4096, 0, 768);
        assertEquals(new CameraPose(4096, 768, 1),
                StageplayLiveWorld.boundedCamera(new CameraPose(5000, 832, 1), level, 320, 224));
        assertEquals(new CameraPose(0, 0, 1),
                StageplayLiveWorld.boundedCamera(new CameraPose(-10, -20, 1), level, 320, 224));
    }

    @Test void tallWideAndZoomedViewsFitTheSameNativeWorldEnvelope() {
        var level = level(0, 4096, 0, 768);
        assertEquals(new CameraPose(4016, 608, 1),
                StageplayLiveWorld.boundedCamera(new CameraPose(5000, 1000, 1), level, 400, 384));
        assertEquals(new CameraPose(3776, 544, .5),
                StageplayLiveWorld.boundedCamera(new CameraPose(5000, 1000, .5), level, 320, 224));
        assertEquals(new CameraPose(4256, 880, 2),
                StageplayLiveWorld.boundedCamera(new CameraPose(5000, 1000, 2), level, 320, 224));
    }

    @Test void fractionalOriginsAndNegativeNativeTopBoundsRemainAvailable() {
        var view = new CameraPose(100.25, -128.5, 1.25);
        assertEquals(view, StageplayLiveWorld.boundedCamera(view, level(0, 4096, -256, 2048), 320, 224));
    }

    @Test void oversizedViewsAnchorAtTheNativeMinimumAndInvertedIntervalsHaveNoUpperClamp() {
        assertEquals(new CameraPose(16, -256, 1),
                StageplayLiveWorld.boundedCamera(new CameraPose(100, 500, 1),
                        level(16, 256, -256, 256), 1000, 1000));
        assertEquals(new CameraPose(100, 500, 1),
                StageplayLiveWorld.boundedCamera(new CameraPose(100, 500, 1),
                        level(16, -1, -256, -257), 320, 224));
    }

    @Test void evaluationStillRejectsInvalidAuthoredCameraCoordinatesAndZoom() {
        var level = level(0, 4096, 0, 768);
        assertThrows(IllegalArgumentException.class, () ->
                StageplayLiveWorld.boundedCamera(new CameraPose(Double.NaN, 0, 1), level, 320, 224));
        assertThrows(IllegalArgumentException.class, () ->
                StageplayLiveWorld.boundedCamera(new CameraPose(0, 0, .01), level, 320, 224));
    }
}
