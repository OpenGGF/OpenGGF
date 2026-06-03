package com.openggf.capture;

/** Produces RGBA8888 pixels (top-left origin) for the current framebuffer. */
public interface VideoFrameGrabber {
    int width();

    int height();

    /** @return a fresh {@code width()*height()*4} byte array of RGBA pixels. */
    byte[] grab();
}
