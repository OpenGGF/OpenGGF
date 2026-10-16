package com.openggf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestDisplayViewport {
    @Test void maximizedDesktopUsesSquareIntegerPixelsAndCenteredBorders() {
        assertEquals(new DisplayViewport.Bounds(320, 92, 1280, 896),
                DisplayViewport.fit(1920, 1080, 320, 224, true));
        assertEquals(new DisplayViewport.Bounds(480, 72, 2880, 2016),
                DisplayViewport.fit(3840, 2160, 320, 224, true));
    }
    @Test void fitUsesAvailableHeightWithoutStretching() {
        var bounds = DisplayViewport.fit(1920, 1080, 320, 224, false);
        assertEquals(1080, bounds.height());
        assertEquals(1542, bounds.width());
        assertEquals(189, bounds.x());
        assertEquals(0, bounds.y());
        assertTrue(Math.abs(bounds.width() / 320.0 - bounds.height() / 224.0) < 1.0 / 224);
    }
    @Test void narrowAndSubNativeWindowsDoNotCrop() {
        assertEquals(new DisplayViewport.Bounds(0, 30, 200, 140),
                DisplayViewport.fit(200, 200, 320, 224, true));
        assertEquals(new DisplayViewport.Bounds(0, 388, 800, 224),
                DisplayViewport.fit(800, 1000, 800, 224, true));
    }
    @Test void minimizedWindowHasEmptyViewport() {
        assertEquals(new DisplayViewport.Bounds(0, 0, 0, 0), DisplayViewport.fit(0, 0, 320, 224, true));
    }
}
