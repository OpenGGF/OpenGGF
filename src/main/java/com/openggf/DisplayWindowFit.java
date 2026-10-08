package com.openggf;

import static org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowAttrib;
import static org.lwjgl.glfw.GLFW.glfwGetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.logging.Logger;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Fits the window to the display aspect when a launch changes it (a launch profile's aspect,
 * or a module's {@code requiredDisplayAspect}, such as a mod that is laid out for 16:9).
 * Without this the window kept the size the previous aspect gave it and the new picture was
 * letterboxed inside it. A windowed window keeps its height, in whole multiples of the
 * display's pixel height, and takes the width the aspect needs, within the monitor; fullscreen
 * and maximised windows, and players who turned window autosize off, are left alone.
 */
final class DisplayWindowFit {
    private static final Logger LOG = Logger.getLogger(DisplayWindowFit.class.getName());

    private DisplayWindowFit() {
    }

    /**
     * The window size, in screen coordinates, for a {@code pixelWidth x pixelHeight} display
     * in a window now {@code windowWidth x windowHeight}: the nearest whole scale to the
     * current height, at least 1 and no more than fits a {@code monitorWidth x monitorHeight}
     * monitor (unknown when either is 0 or less).
     */
    static int[] fit(int windowWidth, int windowHeight, int pixelWidth, int pixelHeight, int monitorWidth,
            int monitorHeight) {
        int scale = Math.max(1, Math.round(windowHeight / (float) pixelHeight));
        if (monitorWidth > 0 && monitorHeight > 0) {
            scale = Math.min(scale, Math.max(1, Math.min(monitorWidth / pixelWidth, monitorHeight / pixelHeight)));
        }
        return new int[] {pixelWidth * scale, pixelHeight * scale};
    }

    /** Resizes {@code window} for a {@code pixelWidth x pixelHeight} display; returns true when it asked for a new size. */
    static boolean apply(long window, boolean autosize, int pixelWidth, int pixelHeight) {
        if (window == NULL || !autosize || pixelWidth <= 0 || pixelHeight <= 0
                || glfwGetWindowMonitor(window) != NULL || glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE) {
            return false;
        }
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == NULL ? null : glfwGetVideoMode(monitor);
        int[] size = fit(width[0], height[0], pixelWidth, pixelHeight, mode == null ? 0 : mode.width(),
                mode == null ? 0 : mode.height());
        if (size[0] == width[0] && size[1] == height[0]) {
            return false;
        }
        LOG.info("Window " + width[0] + "x" + height[0] + " -> " + size[0] + "x" + size[1] + " for the "
                + pixelWidth + "x" + pixelHeight + " display");
        glfwSetWindowSize(window, size[0], size[1]);
        return true;
    }
}
