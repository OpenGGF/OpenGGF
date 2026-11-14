package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSimulationViewport {
    @Test
    void nativeDrawModeCapsTheSimulationAtTheRomWindow() {
        for (WidescreenAspect aspect : WidescreenAspect.values()) {
            assertEquals(320, SimulationViewport.width(aspect.pixelWidth(), WidescreenDrawMode.NATIVE));
            assertEquals(aspect.pixelWidth(), SimulationViewport.width(aspect.pixelWidth(), WidescreenDrawMode.EDGE));
        }
        assertEquals(240, SimulationViewport.presentationInset(800, 320));
        assertEquals(0, SimulationViewport.presentationInset(320, 320));
    }

    @Test
    void unknownValuesFallBackToTheSafeDefaults() {
        assertEquals(WidescreenDrawMode.EDGE, WidescreenDrawMode.parse(null));
        assertEquals(WidescreenDrawMode.EDGE, WidescreenDrawMode.parse("sideways"));
        assertEquals(WidescreenDrawMode.NATIVE, WidescreenDrawMode.parse(" native "));
        assertEquals(WidescreenDormantStyle.GREYSCALE, WidescreenDormantStyle.parse(""));
        assertEquals(WidescreenDormantStyle.HIDDEN, WidescreenDormantStyle.parse("hidden"));
    }
}
