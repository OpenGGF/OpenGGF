package com.openggf.graphics;

import org.lwjgl.glfw.GLFW;

/**
 * Maps the mouse from window coordinates (GLFW screen units) to the logical screen (up to
 * 800 by 224 pixels) through the letterboxed GL viewport, for menus and mod scenes that want
 * pointer input. Engine-internal.
 */
public final class LogicalMouse {
    private LogicalMouse() {
    }

    /**
     * {@code {x, y, inside}} in logical pixels ({@code inside} is 1 when the point is on the
     * logical screen, 0 in the letterbox), or null without a window.
     */
    public static int[] map(long window, GraphicsManager graphics, double windowX, double windowY, int logicalWidth,
            int logicalHeight) {
        if (window == 0 || graphics == null) {
            return null;
        }
        int[] ww = new int[1];
        int[] wh = new int[1];
        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetWindowSize(window, ww, wh);
        GLFW.glfwGetFramebufferSize(window, fw, fh);
        return map(ww[0], wh[0], fw[0], fh[0], graphics.getViewportX(), graphics.getViewportY(),
                graphics.getViewportWidth(), graphics.getViewportHeight(), windowX, windowY, logicalWidth, logicalHeight);
    }

    /** Pure mapping seam shared by the native window path and coordinate regression tests. */
    static int[] map(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight,
                     int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                     double windowX, double windowY, int logicalWidth, int logicalHeight) {
        double scaleX = windowWidth > 0 ? framebufferWidth / (double) windowWidth : 1.0;
        double scaleY = windowHeight > 0 ? framebufferHeight / (double) windowHeight : 1.0;
        double fx = windowX * scaleX;
        // Framebuffer y grows upwards from the bottom; window y grows downwards.
        double fy = framebufferHeight - windowY * scaleY;
        int vw = Math.max(1, viewportWidth);
        int vh = Math.max(1, viewportHeight);
        int x = (int) Math.floor((fx - viewportX) * logicalWidth / vw);
        int y = (int) Math.floor((viewportY + vh - fy) * logicalHeight / vh);
        boolean inside = x >= 0 && y >= 0 && x < logicalWidth && y < logicalHeight;
        return new int[] {x, y, inside ? 1 : 0};
    }
}
