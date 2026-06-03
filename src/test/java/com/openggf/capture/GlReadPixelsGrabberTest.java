package com.openggf.capture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlReadPixelsGrabberTest {
    @Test
    void reportsConfiguredDimensions() {
        GlReadPixelsGrabber g = new GlReadPixelsGrabber(320, 224);
        assertEquals(320, g.width());
        assertEquals(224, g.height());
    }
}
