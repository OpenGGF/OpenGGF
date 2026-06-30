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

    /** RGBA8888 — 4 bytes per pixel. */
    static final int BYTES_PER_PIXEL = 4;

    @Override public int width() { return width; }
    @Override public int height() { return height; }

    /**
     * The exact RGBA byte size of one grabbed frame at the given dimensions.
     * Both the read buffer and the returned array are sized from this, so it
     * is the single source of truth for the {@code grab()} byte contract.
     */
    static int frameByteSize(int width, int height) {
        return width * height * BYTES_PER_PIXEL;
    }

    /** Byte size of a frame at this grabber's configured dimensions. */
    int frameByteSize() {
        return frameByteSize(width, height);
    }

    @Override
    public byte[] grab() {
        ByteBuffer buf = MemoryUtil.memAlloc(frameByteSize());
        try {
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            byte[] out = new byte[frameByteSize()];
            buf.get(out);            // tight copy, bottom-up as GL provides
            return out;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }
}
