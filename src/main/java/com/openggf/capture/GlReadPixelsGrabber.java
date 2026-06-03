package com.openggf.capture;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * {@link VideoFrameGrabber} that reads the back buffer via {@code glReadPixels}.
 * Returns RGBA bytes in OpenGL bottom-up order (no Java-side flip) — ffmpeg's
 * {@code vflip} corrects orientation. MUST be called on the GL thread.
 */
public final class GlReadPixelsGrabber implements VideoFrameGrabber {

    private final int width;
    private final int height;

    public GlReadPixelsGrabber(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }

    @Override
    public byte[] grab() {
        ByteBuffer buf = MemoryUtil.memAlloc(width * height * 4);
        try {
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            byte[] out = new byte[width * height * 4];
            buf.get(out);            // tight copy, bottom-up as GL provides
            return out;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
