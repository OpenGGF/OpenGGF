package com.openggf;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Temporary authoring-window policy; it never changes gameplay or persisted display settings. */
final class StageplayWindowController {
    record Size(int width, int height) { }
    record Fit(Size minimum, Size requested) { }

    interface Window {
        Size size();
        Size available();
        boolean maximized();
        void limits(int minimumWidth, int minimumHeight);
        void aspect(int numerator, int denominator);
        void resize(Size size);
    }

    private final Window window;
    private Size previous;

    StageplayWindowController(long handle) { this(new GlfwWindow(handle)); }
    StageplayWindowController(Window window) { this.window = window; }

    /** Fullscreen/borderless modes retain their monitor dimensions. */
    void sync(boolean authoring, boolean windowed) {
        boolean requested = authoring && windowed && !window.maximized();
        if (requested && previous == null) {
            previous = window.size();
            Fit fit = fit(previous, window.available());
            window.limits(fit.minimum().width(), fit.minimum().height());
            window.aspect(16, 9);
            if (!fit.requested().equals(previous)) window.resize(fit.requested());
        } else if (!requested && previous != null) {
            // This is the only size/aspect constraint owner; the game's prior policy is unrestricted.
            window.limits(GLFW_DONT_CARE, GLFW_DONT_CARE);
            window.aspect(GLFW_DONT_CARE, GLFW_DONT_CARE);
            if (windowed && !window.maximized()) window.resize(previous);
            previous = null;
        }
    }

    /** Screen-coordinate 16:9 size, reduced only when the monitor cannot hold 1280x720. */
    static Fit fit(Size current, Size available) {
        int capacity = available.width() > 0 && available.height() > 0
                ? Math.max(1, Math.min(available.width()/16, available.height()/9)) : Integer.MAX_VALUE;
        int minimum = Math.min(80, capacity);
        int requested = Math.min(capacity, Math.max(minimum,
                Math.max((Math.max(1,current.width())+15)/16, (Math.max(1,current.height())+8)/9)));
        return new Fit(new Size(minimum*16, minimum*9), new Size(requested*16, requested*9));
    }

    private record GlfwWindow(long handle) implements Window {
        public Size size() {
            int[] width = new int[1], height = new int[1];
            glfwGetWindowSize(handle,width,height);
            return new Size(width[0],height[0]);
        }
        public Size available() {
            var monitor = DisplayWindowController.monitorForWindow(handle);
            if (monitor == null) return new Size(0,0);
            int[] width = new int[1], height = new int[1];
            glfwGetMonitorWorkarea(monitor.handle(),new int[1],new int[1],width,height);
            int[] left = new int[1], top = new int[1], right = new int[1], bottom = new int[1];
            glfwGetWindowFrameSize(handle,left,top,right,bottom);
            return new Size(Math.max(1,width[0]-left[0]-right[0]),Math.max(1,height[0]-top[0]-bottom[0]));
        }
        public boolean maximized() { return glfwGetWindowAttrib(handle,GLFW_MAXIMIZED)==GLFW_TRUE || glfwGetWindowMonitor(handle)!=NULL; }
        public void limits(int width,int height) { glfwSetWindowSizeLimits(handle,width,height,GLFW_DONT_CARE,GLFW_DONT_CARE); }
        public void aspect(int numerator,int denominator) { glfwSetWindowAspectRatio(handle,numerator,denominator); }
        public void resize(Size size) { glfwSetWindowSize(handle,size.width(),size.height()); }
    }
}
