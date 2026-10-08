package com.openggf.tools.challenge;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.openggf.graphics.WindowIconLoader;
import org.lwjgl.system.JNI;
import org.lwjgl.system.linux.X11;

/**
 * Host window startup that never waits for the desktop to map the window.
 *
 * <p>On X11, {@code glfwShowWindow} (and a visible {@code glfwCreateWindow})
 * waits for a VisibilityNotify. That wait only times out while the X queue is
 * empty: when the window manager withholds the map while delivering other
 * events (ConfigureNotify), it spins at full CPU and never returns. A locked
 * KDE Plasma Wayland session reproduced it on 2026-10-08 for GLFW, the host
 * and a plain Xlib window alike (multigame validation, startup section). The
 * host therefore issues the same {@code XMapWindow} without that wait and holds
 * its title clock until the window is actually viewable. Other platforms keep
 * {@code glfwShowWindow}.
 */
final class ChallengeWindow {
    private ChallengeWindow() {}

    static long create(int width, int height, String title) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        long window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL)
            throw new IllegalStateException("The game window could not be created");
        WindowIconLoader.apply(window);
        return window;
    }

    /** Asks the desktop to show the window; returns at once on every platform. */
    static void requestShow(long window) {
        if (glfwGetPlatform() == GLFW_PLATFORM_X11) {
            long display = glfwGetX11Display();
            long handle = glfwGetX11Window(window);
            long map = X11.getLibrary().getFunctionAddress("XMapWindow");
            long flush = X11.getLibrary().getFunctionAddress("XFlush");
            if (display != NULL && handle != NULL && map != NULL && flush != NULL) {
                JNI.invokePNI(display, handle, map);
                JNI.invokePI(display, flush);
                return;
            }
        }
        glfwShowWindow(window);
    }

    static boolean shown(long window) {
        return glfwGetWindowAttrib(window, GLFW_VISIBLE) == GLFW_TRUE;
    }
}
