package com.openggf.capture;

import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static com.openggf.tools.PresentationOffscreenTestContext.LegacyProfile.DEFAULT;
import static org.lwjgl.opengl.GL11.*;
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

    @Test
    void forwardsConfiguredRegionUnchanged() {
        int[] forwarded = new int[4];
        GlReadPixelsGrabber g = new GlReadPixelsGrabber(13, 17, 320, 224,
                (x, y, width, height, target) -> {
                    forwarded[0] = x;
                    forwarded[1] = y;
                    forwarded[2] = width;
                    forwarded[3] = height;
                });

        assertEquals(320 * 224 * 4, g.grab().length);
        assertArrayEquals(new int[]{13, 17, 320, 224}, forwarded);
    }

    @Test
    void repeatedGrabsReuseOneBufferAndRefreshItsContents() {
        byte[] fill = {1};
        GlReadPixelsGrabber g = new GlReadPixelsGrabber(0, 0, 2, 1,
                (x, y, width, height, target) -> {
                    target.clear();
                    for (int i = 0; i < width * height * 4; i++) {
                        target.put(fill[0]);
                    }
                });

        byte[] first = g.grab();
        assertEquals(1, first[0]);

        fill[0] = 2;
        byte[] second = g.grab();

        assertSame(first, second,
                "the grab buffer is reused; CapturedFrame's copy is what makes that safe");
        assertEquals(2, second[0], "a reused buffer must still be refilled each grab");
        g.close();
    }

    @Test
    void closeIsIdempotent() {
        GlReadPixelsGrabber g = new GlReadPixelsGrabber(0, 0, 2, 1,
                (x, y, width, height, target) -> { });
        g.close();
        assertDoesNotThrow(g::close, "a double free of the native buffer would crash the JVM");
    }

    @Test
    void grabsOnlyNonZeroOriginViewportFromOffscreenBackBuffer() {
        try (var context = PresentationOffscreenTestContext.openConfigured(32, 24, DEFAULT);
             var grabber = new GlReadPixelsGrabber(5, 4, 16, 12)) {
            glDisable(GL_SCISSOR_TEST);
            glClearColor(1f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);

            glEnable(GL_SCISSOR_TEST);
            glScissor(5, 4, 16, 12);
            glClearColor(0f, 1f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            glDisable(GL_SCISSOR_TEST);
            glFinish();

            byte[] pixels = grabber.grab();
            assertEquals(16 * 12 * 4, pixels.length);
            for (int offset = 0; offset < pixels.length; offset += 4) {
                assertRgba(pixels, offset, 0, 255, 0, 255);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void pboReadbackPreservesBothFramesAndExternalPackBinding() {
        try (var context = PresentationOffscreenTestContext.openConfigured(32, 24, DEFAULT)) {
            if (PresentationOffscreenTestContext.requested()) {
                assertTrue(org.lwjgl.opengl.GL.getCapabilities().OpenGL21,
                        "explicit EGL must execute the PBO pixel path");
            } else {
                Assumptions.assumeTrue(org.lwjgl.opengl.GL.getCapabilities().OpenGL21,
                        "PBO readback requires OpenGL 2.1");
            }
            int external = org.lwjgl.opengl.GL15.glGenBuffers();
            try (VideoFrameGrabber grabber = GlPboFrameGrabber.create(new CaptureViewport(5, 4, 16, 12))) {
                assertTrue(grabber.deferredReadback(), "exercise PBOs rather than synchronous fallback");
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, external);
                glClearColor(1f, 0f, 0f, 1f);
                glClear(GL_COLOR_BUFFER_BIT);
                grabber.beginReadback();
                glClearColor(0f, 1f, 0f, 1f);
                glClear(GL_COLOR_BUFFER_BIT);
                grabber.beginReadback();
                byte[] pixels = new byte[16 * 12 * 4];
                grabber.grabInto(pixels);
                for (int offset = 0; offset < pixels.length; offset += 4) {
                    assertRgba(pixels, offset, 255, 0, 0, 255);
                }
                assertEquals(external, glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING));
                grabber.grabInto(pixels);
                for (int offset = 0; offset < pixels.length; offset += 4) {
                    assertRgba(pixels, offset, 0, 255, 0, 255);
                }
                assertEquals(external, glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
                org.lwjgl.opengl.GL15.glDeleteBuffers(external);
            }
        }
    }

    private static void assertRgba(byte[] pixels, int offset,
                                   int red, int green, int blue, int alpha) {
        assertEquals(red, Byte.toUnsignedInt(pixels[offset]));
        assertEquals(green, Byte.toUnsignedInt(pixels[offset + 1]));
        assertEquals(blue, Byte.toUnsignedInt(pixels[offset + 2]));
        assertEquals(alpha, Byte.toUnsignedInt(pixels[offset + 3]));
    }
}
