package com.openggf;

import com.openggf.graphics.shaderlib.*;

import com.openggf.control.InputHandler;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Component pixel checks; no world or audio backend is started. */
@Isolated
class TestDisplayShaderPickerPresentation {
    @Test void firstDrawHasChromeAndResourcesCanBeRetiredAndRecreatedAcrossContexts() {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Run with surfaceless EGL for pixel QA");
        try (var renderer = new DisplayShaderPickerRenderer()) {
            // Close before each context goes away, then reuse the owner in a fresh context.
            for (int width : new int[]{320, 400}) {
                try (var context = PresentationOffscreenTestContext.open(width, 224)) {
                    try {
                        var picker = new DisplayShaderPickerController(new DisplayShaderSelectionModel(List.of()), GLFW_KEY_F8);
                        var input = new InputHandler();
                        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
                        picker.update(input, DisplayShaderPresetRef.OFF);
                        glViewport(0, 0, width, 224);
                        glDisable(GL_DEPTH_TEST);
                        glDisable(GL_BLEND);
                        glBlendFunc(GL_ONE, GL_ZERO);
                        glClearColor(.3f, .5f, .2f, 1);
                        glClear(GL_COLOR_BUFFER_BIT);
                        renderer.render(picker, DisplayShaderPresetRef.OFF, "shaders", null, false,
                                SceneSurface.projection(width, 224), width);
                        assertFalse(glIsEnabled(GL_BLEND), "Picker must restore the incoming blend enable");
                        assertEquals(GL_ONE, glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB));
                        assertEquals(GL_ZERO, glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB));
                        glFinish();
                        RgbaImage image = ScreenshotCapture.captureFramebuffer(width, 224);
                        // Exact semantic regions reject a blank/stale frame: header rule, focus,
                        // no-library information icon, and untouched dimmed gameplay at the edge.
                        assertGold(image.argb(100, 35));
                        assertGold(image.argb(100, 77));
                        int info = image.argb(29, 109);
                        assertTrue((info & 255) > 180, "Empty library info icon must render in cyan");
                        int backdrop = image.argb(0, 100);
                        assertTrue(((backdrop >> 8) & 255) > 50, "Gameplay remains visible through the scrim");
                        assertTrue(((backdrop >> 8) & 255) < 125, "Gameplay is dimmed behind the picker");
                        assertEquals(GL_NO_ERROR, glGetError());
                    } finally {
                        renderer.close();
                        assertEquals(GL_NO_ERROR, glGetError(), "Cleanup must run while the context is current");
                    }
                }
            }
        }
    }

    private static void assertGold(int argb) {
        assertTrue(((argb >> 16) & 255) > 220 && ((argb >> 8) & 255) > 170 && (argb & 255) < 160,
                "Expected gold chrome, got " + Integer.toHexString(argb));
    }
}
