package com.openggf.tools;

import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceCaptureToolArgsTest {

    @BeforeEach
    void resetConfig() {
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void parsesArgsWithConfigDefaults() {
        TraceCaptureTool.Args a = TraceCaptureTool.Args.parse(
                new String[]{"--trace", "aiz1", "--scale", "2", "--fps", "30"});
        assertEquals("aiz1", a.trace());
        assertEquals(2, a.scale());
        assertEquals(30, a.fps());
        assertEquals("ffv1", a.codec());            // default from config
        assertNotNull(a.outDir());                  // default from config
        assertTrue(a.showGhosts());                 // ghosts on by default
    }

    @Test
    void noGhostsFlagDisablesGhosts() {
        TraceCaptureTool.Args a = TraceCaptureTool.Args.parse(
                new String[]{"--trace", "aiz1", "--no-ghosts"});
        assertFalse(a.showGhosts());
    }
}
