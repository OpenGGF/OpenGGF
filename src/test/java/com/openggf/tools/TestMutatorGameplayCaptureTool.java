package com.openggf.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMutatorGameplayCaptureTool {
    private final String originalBackend = System.getProperty(SurfacelessEglContext.PROPERTY);

    @AfterEach
    void restoreBackend() {
        if (originalBackend == null) System.clearProperty(SurfacelessEglContext.PROPERTY);
        else System.setProperty(SurfacelessEglContext.PROPERTY, originalBackend);
    }

    @Test
    void observationDriverRejectsDefaultAndUnknownDisplayRoutesBeforeCreatingASession() {
        System.clearProperty(SurfacelessEglContext.PROPERTY);
        assertThrows(IllegalArgumentException.class, MutatorGameplayCaptureTool::requireDisplayFreeContext);
        System.setProperty(SurfacelessEglContext.PROPERTY, "x11");
        assertThrows(IllegalArgumentException.class, MutatorGameplayCaptureTool::requireDisplayFreeContext);
        System.setProperty(SurfacelessEglContext.PROPERTY, SurfacelessEglContext.SURFACELESS);
        assertDoesNotThrow(MutatorGameplayCaptureTool::requireDisplayFreeContext);
    }
}
