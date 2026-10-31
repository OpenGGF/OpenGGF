package com.openggf;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Owns desktop mode transitions on the GLFW thread, retaining the normal window placement. */
final class DisplayWindowController {
    enum Mode {
        WINDOWED, BORDERLESS, FULLSCREEN;
        static Mode parse(String text) {
            try { return valueOf(text); }
            catch (IllegalArgumentException | NullPointerException ignored) { return WINDOWED; }
        }
    }
    record Placement(int x, int y, int width, int height, boolean maximized) { }
    record Monitor(long handle, int x, int y, int width, int height, int refreshRate) { }

    interface Desktop {
        default void observeWindowed() { }
        Placement placement();
        Monitor monitor();
        void windowed(Placement placement);
        void fullscreen(Mode mode, Monitor monitor);
    }

    private final Desktop desktop;
    private Mode mode = Mode.WINDOWED;
    private Placement saved;
    // Each controller belongs to one window context; its first observation opens an epoch.
    private DisplayOutput output = DisplayOutput.UNKNOWN;
    private Monitor observed;

    DisplayWindowController(long window) { this(new GlfwDesktop(window)); }
    DisplayWindowController(Desktop desktop) { this.desktop = desktop; }
    boolean isWindowed() { return mode == Mode.WINDOWED; }

    /** Latest output; re-applying an unchanged mode on the same monitor keeps its epoch. */
    DisplayOutput output() { return output; }

    boolean apply(String setting) {
        boolean changed = applyMode(setting);
        Monitor monitor = desktop.monitor();
        if (changed || output.epoch() == 0 || !sameOutput(observed, monitor)) {
            output = new DisplayOutput(output.epoch() + 1, monitor == null ? 0 : Math.max(0, monitor.refreshRate()));
        }
        observed = monitor;
        return changed;
    }

    private boolean applyMode(String setting) {
        if (mode == Mode.WINDOWED) desktop.observeWindowed();
        Mode requested = Mode.parse(setting);
        if (requested == mode) return false;
        if (requested == Mode.WINDOWED) {
            desktop.windowed(saved);
        } else {
            Monitor monitor = desktop.monitor();
            if (monitor == null) return false;
            if (mode == Mode.WINDOWED) saved = desktop.placement();
            desktop.fullscreen(requested, monitor);
        }
        mode = requested;
        return true;
    }

    private static boolean sameOutput(Monitor a, Monitor b) {
        return a == null ? b == null : b != null && a.handle() == b.handle() && a.refreshRate() == b.refreshRate();
    }

    static Monitor monitorForWindow(long window) { return new GlfwDesktop(window).monitor(); }

    private static final class GlfwDesktop implements Desktop {
        private final long window;
        private Placement normal;
        GlfwDesktop(long window) {
            this.window = window;
            normal = readPlacement(false);
        }

        @Override public void observeWindowed() {
            if (glfwGetWindowAttrib(window, GLFW_MAXIMIZED) != GLFW_TRUE
                    && glfwGetWindowAttrib(window, GLFW_ICONIFIED) != GLFW_TRUE) {
                normal = readPlacement(false);
            }
        }

        @Override public Placement placement() {
            boolean maximized = glfwGetWindowAttrib(window, GLFW_MAXIMIZED) == GLFW_TRUE;
            // Restore requests are asynchronous; retain bounds observed before maximisation.
            if (maximized) glfwRestoreWindow(window);
            return new Placement(normal.x(), normal.y(), normal.width(), normal.height(), maximized);
        }

        private Placement readPlacement(boolean maximized) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, w, h);
            return new Placement(x[0], y[0], w[0], h[0], maximized);
        }

        @Override public Monitor monitor() {
            long attached = glfwGetWindowMonitor(window);
            if (attached != NULL) return describe(attached);
            long selected = glfwGetPrimaryMonitor();
            // Wayland intentionally does not expose global window positions.
            if (glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
                int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
                glfwGetWindowPos(window, x, y);
                glfwGetWindowSize(window, w, h);
                var monitors = glfwGetMonitors();
                long best = 0;
                if (monitors != null) for (int i = 0; i < monitors.limit(); i++) {
                    Monitor candidate = describe(monitors.get(i));
                    if (candidate == null) continue;
                    long area = overlap(x[0], y[0], w[0], h[0], candidate);
                    if (area > best) { best = area; selected = candidate.handle(); }
                }
            }
            return describe(selected);
        }

        private Monitor describe(long monitor) {
            var video = monitor == NULL ? null : glfwGetVideoMode(monitor);
            if (video == null) return null;
            int[] x = new int[1], y = new int[1];
            glfwGetMonitorPos(monitor, x, y);
            return new Monitor(monitor, x[0], y[0], video.width(), video.height(), video.refreshRate());
        }

        @Override public void windowed(Placement p) {
            glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
            glfwSetWindowMonitor(window, NULL, p.x(), p.y(), p.width(), p.height(), GLFW_DONT_CARE);
            if (p.maximized()) glfwMaximizeWindow(window);
        }

        @Override public void fullscreen(Mode mode, Monitor monitor) {
            boolean borderless = mode == Mode.BORDERLESS;
            glfwSetWindowAttrib(window, GLFW_AUTO_ICONIFY, borderless ? GLFW_FALSE : GLFW_TRUE);
            glfwSetWindowAttrib(window, GLFW_DECORATED, borderless ? GLFW_FALSE : GLFW_TRUE);
            // Wayland requires compositor fullscreen: ordinary windows cannot position themselves.
            boolean attach = !borderless || glfwGetPlatform() == GLFW_PLATFORM_WAYLAND;
            glfwSetWindowMonitor(window, attach ? monitor.handle() : NULL,
                    monitor.x(), monitor.y(), monitor.width(), monitor.height(), monitor.refreshRate());
        }
    }

    static long overlap(int x, int y, int width, int height, Monitor monitor) {
        long w = Math.max(0L, Math.min((long) x + width, (long) monitor.x() + monitor.width())
                - Math.max(x, monitor.x()));
        long h = Math.max(0L, Math.min((long) y + height, (long) monitor.y() + monitor.height())
                - Math.max(y, monitor.y()));
        return w * h;
    }
}
