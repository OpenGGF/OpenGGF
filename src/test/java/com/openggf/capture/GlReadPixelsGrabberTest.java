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

    @Test
    void framesAreSizedAsRgba8888() {
        GlReadPixelsGrabber g = new GlReadPixelsGrabber(320, 224);
        // RGBA8888 -> 4 bytes per pixel; grab() allocates both the read buffer
        // and the returned array from exactly this size.
        assertEquals(320 * 224 * 4, g.frameByteSize());

        // Sizing must scale with the configured dimensions, not a fixed buffer.
        GlReadPixelsGrabber small = new GlReadPixelsGrabber(8, 4);
        assertEquals(8 * 4 * 4, small.frameByteSize());
        assertEquals(small.width() * small.height() * 4, small.frameByteSize());
    }
}
