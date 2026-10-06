package com.openggf.graphics;

import org.lwjgl.glfw.GLFW;

/**
 * Maps the mouse from window coordinates (GLFW screen units) to the logical screen (320 or
 * 400 by 224 pixels) through the letterboxed GL viewport, for menus and mod scenes that want
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
        double scaleX = ww[0] > 0 ? fw[0] / (double) ww[0] : 1.0;
        double scaleY = wh[0] > 0 ? fh[0] / (double) wh[0] : 1.0;
        double fx = windowX * scaleX;
        // Framebuffer y grows upwards from the bottom; window y grows downwards.
        double fy = fh[0] - windowY * scaleY;
        int vx = graphics.getViewportX();
        int vy = graphics.getViewportY();
        int vw = Math.max(1, graphics.getViewportWidth());
        int vh = Math.max(1, graphics.getViewportHeight());
        int x = (int) Math.floor((fx - vx) * logicalWidth / vw);
        int y = (int) Math.floor((vy + vh - fy) * logicalHeight / vh);
        boolean inside = x >= 0 && y >= 0 && x < logicalWidth && y < logicalHeight;
        return new int[] {x, y, inside ? 1 : 0};
    }
}
