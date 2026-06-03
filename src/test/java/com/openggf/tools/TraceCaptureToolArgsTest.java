package com.openggf.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceCaptureToolArgsTest {
    @Test
    void parsesArgsWithConfigDefaults() {
        TraceCaptureTool.Args a = TraceCaptureTool.Args.parse(
                new String[]{"--trace", "aiz1", "--scale", "2", "--fps", "30"});
        assertEquals("aiz1", a.trace());
        assertEquals(2, a.scale());
        assertEquals(30, a.fps());
        assertEquals("ffv1", a.codec());            // default from config
        assertNotNull(a.outDir());                  // default from config
    }
}
