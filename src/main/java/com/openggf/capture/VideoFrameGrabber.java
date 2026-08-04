package com.openggf.capture;

/** Produces RGBA8888 pixels (top-left origin) for the current framebuffer. */
public interface VideoFrameGrabber extends AutoCloseable {
    int width();

    int height();

    /**
     * @return {@code width()*height()*4} bytes of RGBA pixels. An implementation
     *         may return a buffer it reuses, so the array is valid only until
     *         the next {@code grab()} — hand it straight to
     *         {@link CapturedFrame}, whose defensive copy exists for exactly
     *         this reuse, and never retain it.
     */
    byte[] grab();

    /** Releases any native buffers. Implementations must tolerate repeat calls. */
    @Override
    default void close() {
    }
}
