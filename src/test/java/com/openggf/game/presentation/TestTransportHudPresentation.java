package com.openggf.game.presentation;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.PixelFontVariant;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.ShaderProgram;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;

import static com.openggf.tools.PresentationOffscreenTestContext.LegacyProfile.CORE_41;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.*;

/** Real component pixels: no ROM, world, audio or desktop input. */
@Isolated
class TestTransportHudPresentation {
    @Test
    void platePreservesVisibleSceneryInsteadOfCoveringItWithAnOpaqueUnderlay() throws Exception {
        for (int width : new int[] {320, 400}) {
            RgbaImage green = render(width, 0, 1, 0);
            RgbaImage red = render(width, 1, 0, 0);
            // Blank lower-right interior, away from text, glyph and the frame.
            int x = width - 11, y = 213;
            assertTrue(((green.argb(x, y) >>> 8) & 255) - ((red.argb(x, y) >>> 8) & 255) >= 30,
                    "underlying scenery must remain visible through the plate");
            assertTrue(((red.argb(x, y) >>> 16) & 255) - ((green.argb(x, y) >>> 16) & 255) >= 30);
            assertEquals(0x00ff00, green.argb(width - 4, y) & 0xffffff,
                    "the overlay must not dim gameplay outside its own rectangle");
        }
    }

    @Test
    void redRecordingDotHasADarkSilhouetteWithoutLosingItsStatusColour() throws Exception {
        RgbaImage image = render(320, 0, 1, 0);
        var message = new TransportHud.Message(TransportHud.Status.RECORD, "REC", "F:1248", "", -1);
        var layout = TransportHud.layout(PixelFontTextRenderer.compactMenu(), 320,
                TransportHud.Placement.BOTTOM_RIGHT, message);
        int dotX = layout.x() + 8, dotY = layout.y() + 4;
        int red = image.argb(dotX, dotY);
        assertTrue(((red >>> 16) & 255) > 180 && ((red >>> 8) & 255) < 140, "retain red record status");
        int outline = image.argb(dotX, dotY - 1);
        assertTrue(((outline >>> 16) & 255) < 8 && ((outline >>> 8) & 255) < 12 && (outline & 255) < 32,
                "a one-pixel dark outline must separate the dot from bright scenery");
    }

    @Test
    void drainedRewindIsToldByFrameDashesAndGlyphShapeNotOnlyColour() throws Exception {
        for (int width : new int[] {320, 400}) {
            var drained = new TransportHud.Message(TransportHud.Status.REWIND_EMPTY, "REW", "0.0s", "", 0, "10.0s");
            var counting = new TransportHud.Message(TransportHud.Status.REWIND, "REW", "6.4s", "", 64, "10.0s");
            var text = PixelFontTextRenderer.compactMenu();
            var empty = TransportHud.layout(text, width, TransportHud.Placement.ABOVE_LIVES, drained);
            var full = TransportHud.layout(text, width, TransportHud.Placement.ABOVE_LIVES, counting);
            assertEquals(full.width(), empty.width(), "the reserved value keeps the plate and meter still");
            RgbaImage drainedPixels = render(width, 0, 0, 0, TransportHud.Placement.ABOVE_LIVES, drained);
            RgbaImage countingPixels = render(width, 0, 0, 0, TransportHud.Placement.ABOVE_LIVES, counting);
            int frameX = empty.x() + empty.width() / 2, frameY = empty.y();
            assertTrue(amber(drainedPixels.argb(frameX, frameY)), "drained frame carries the status colour");
            assertFalse(amber(countingPixels.argb(frameX, frameY)), "a counting frame keeps the shared edge");
            int trackY = empty.y() + empty.height() - 4, trackX = empty.x() + 4;
            assertTrue(amber(drainedPixels.argb(trackX, trackY)) && amber(drainedPixels.argb(trackX + 1, trackY)));
            assertFalse(amber(drainedPixels.argb(trackX + 2, trackY)) || amber(drainedPixels.argb(trackX + 3, trackY)),
                    "the empty track breaks into dashes");
            // Skip-to-start: the glyph's first column is a full stop bar; the rewind arrows taper there.
            int glyphX = empty.x() + 4, glyphY = empty.y() + 4;
            assertTrue(amber(drainedPixels.argb(glyphX, glyphY)) && amber(drainedPixels.argb(glyphX, glyphY + 6)));
            int cyanTop = countingPixels.argb(glyphX, glyphY);
            assertTrue((cyanTop & 255) < 100, "the rewind glyph has no stop bar");
        }
    }

    private static boolean amber(int argb) {
        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        return r > 180 && g > 100 && b < 90;
    }

    private static RgbaImage render(int width, float r, float g, float b) throws Exception {
        return render(width, r, g, b, TransportHud.Placement.BOTTOM_RIGHT,
                new TransportHud.Message(TransportHud.Status.RECORD, "REC", "F:1248", "", -1));
    }

    private static RgbaImage render(int width, float r, float g, float b, TransportHud.Placement placement,
                                    TransportHud.Message message) throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(width, 224, CORE_41)) {
            var shader = new ShaderProgram("shaders/shader_debug_color.vert", "shaders/shader_debug_color.glsl");
            var text = PixelFontTextRenderer.compactMenu();
            var projection = SceneSurface.projection(width, 224);
            text.setProjectionMatrix(projection);
            var graphics = mock(GraphicsManager.class);
            when(graphics.isGlInitialized()).thenReturn(true);
            when(graphics.getDebugShaderProgram()).thenReturn(shader);
            when(graphics.getProjectionMatrixBuffer()).thenReturn(projection);
            var commands = new ArrayList<GLCommandable>();
            doAnswer(call -> { commands.add(call.getArgument(0)); return null; }).when(graphics).registerCommand(any());
            doAnswer(call -> {
                for (var command : commands) command.execute(0, 0, width, 224);
                commands.clear();
                return null;
            }).when(graphics).flushScreenSpace();
            try {
                glViewport(0, 0, width, 224);
                glDisable(GL_DEPTH_TEST);
                glClearColor(r, g, b, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                TransportHud.render(text, graphics, width, placement, message);
                glFinish();
                assertEquals(GL_NO_ERROR, glGetError());
                return ScreenshotCapture.captureFramebuffer(width, 224);
            } finally {
                text.cleanup();
                shader.cleanup();
                GLCommand.cleanup();
            }
        }
    }
}
