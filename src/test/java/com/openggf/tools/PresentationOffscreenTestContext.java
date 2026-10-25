package com.openggf.tools;

import org.junit.jupiter.api.Assumptions;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Owns the native context and GL capabilities for component pixel tests. */
public final class PresentationOffscreenTestContext implements AutoCloseable {
    /** Requirements for the legacy hidden-window path when no backend is selected. */
    public enum LegacyProfile { DEFAULT, CORE_41 }

    private final SurfacelessEglContext egl;
    private final long window;
    private boolean closed;

    private PresentationOffscreenTestContext(SurfacelessEglContext egl, long window) {
        this.egl = egl;
        this.window = window;
    }

    public static boolean requested() {
        return SurfacelessEglContext.requested();
    }

    /**
     * Honors the configured backend. Explicit EGL must execute or fail without a
     * display fallback; an unconfigured host retains the legacy prerequisite skips.
     * Callers must release their GL resources before closing this context.
     */
    public static PresentationOffscreenTestContext openConfigured(
            int width, int height, LegacyProfile legacyProfile) {
        if (legacyProfile == null) throw new NullPointerException("legacyProfile");
        // Keep this outside the legacy assumption boundary: neither an unsupported
        // backend nor an unavailable explicitly requested EGL context is a skip.
        if (requested()) return open(width, height);
        return openLegacyOrAbort(width, height, legacyProfile);
    }

    /** Opens the existing core-profile, display-free EGL context unconditionally. */
    public static PresentationOffscreenTestContext open(int width, int height) {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_NULL);
        if (!glfwInit()) {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            throw new IllegalStateException("GLFW null platform unavailable; no display fallback");
        }
        SurfacelessEglContext context = null;
        try {
            context = SurfacelessEglContext.create(width, height, true);
            GL.createCapabilities();
            return new PresentationOffscreenTestContext(context, NULL);
        } catch (RuntimeException | Error failure) {
            closeFailedEglContext(context, failure);
            throw failure;
        }
    }

    static void closeFailedEglContext(SurfacelessEglContext context, Throwable failure) {
        suppressCleanupFailure(failure, () -> GL.setCapabilities(null));
        if (context != null) suppressCleanupFailure(failure, context::close);
        suppressCleanupFailure(failure, org.lwjgl.glfw.GLFW::glfwTerminate);
        suppressCleanupFailure(failure, () -> glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM));
    }

    private static void suppressCleanupFailure(Throwable original, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error failure) {
            if (failure != original) original.addSuppressed(failure);
        }
    }

    private static PresentationOffscreenTestContext openLegacyOrAbort(
            int width, int height, LegacyProfile profile) {
        boolean initialized = false;
        long window = NULL;
        try {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
            initialized = glfwInit();
            if (!initialized) throw new IllegalStateException("GLFW unavailable for a hidden OpenGL context");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            if (profile == LegacyProfile.CORE_41) {
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            }
            window = glfwCreateWindow(width, height, "Owned component pixel test", NULL, NULL);
            if (window == NULL) throw new IllegalStateException("Hidden OpenGL context unavailable: " + profile);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            return new PresentationOffscreenTestContext(null, window);
        } catch (RuntimeException | LinkageError unavailable) {
            try {
                if (window != NULL) {
                    try {
                        GL.setCapabilities(null);
                    } catch (LinkageError missingOpenGl) {
                        // GL's static initializer itself can fail on an unsupported
                        // default host. Still retire GLFW and preserve the skip.
                        unavailable.addSuppressed(missingOpenGl);
                    }
                    glfwMakeContextCurrent(NULL);
                    glfwDestroyWindow(window);
                }
            } finally {
                if (initialized) glfwTerminate();
            }
            return Assumptions.abort("Hidden OpenGL unavailable: " + unavailable.getMessage());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            GL.setCapabilities(null);
            if (egl != null) {
                egl.close();
            } else {
                glfwMakeContextCurrent(NULL);
                glfwDestroyWindow(window);
            }
        } finally {
            glfwTerminate();
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
    }
}
