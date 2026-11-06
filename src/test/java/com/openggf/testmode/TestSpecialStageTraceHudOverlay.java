package com.openggf.testmode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;

class TestSpecialStageTraceHudOverlay {
    @Test void specialStageUsesTheSameManualNativeSizeDetails(){
        var overlay=new SpecialStageTraceHudOverlay(()->"S1 SPECIAL STAGE",()->12,()->100,()->true);
        var text=new TestTraceHudOverlay.RecordingText();
        overlay.render(text);
        assertFalse(overlay.detailsVisible());assertTrue(text.contains("Tab: Details"));
        TestTraceHudOverlay.press(overlay,GLFW_KEY_TAB);
        text.draws.clear();overlay.render(text);
        assertTrue(overlay.detailsVisible());assertTrue(text.contains("TRACE DETAILS"));
        assertTrue(text.contains("Errors: 0   Warnings: 0   Lag: 1"));
    }
}
