package com.openggf.tools;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.SharedLibrary;

import java.nio.IntBuffer;

import static org.lwjgl.system.JNI.invokeI;
import static org.lwjgl.system.JNI.invokePI;
import static org.lwjgl.system.JNI.invokePPI;
import static org.lwjgl.system.JNI.invokePPP;
import static org.lwjgl.system.JNI.invokePPPI;
import static org.lwjgl.system.JNI.invokePPPP;
import static org.lwjgl.system.JNI.invokePPPPI;
import static org.lwjgl.system.JNI.invokePPPPP;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;

/**
 * Display-free GL context for offscreen capture tools: a Mesa surfaceless EGL display
 * with a pbuffer the size of the capture framebuffer, so the default framebuffer and
 * {@code GL_BACK} readback behave as they do with a hidden window. It never connects to
 * an X11 or Wayland display and never falls back to one; creation failure is fatal.
 * Selected with {@code -Dopenggf.headless.gl=surfaceless-egl}. The project depends only on
 * LWJGL core, so the handful of EGL entry points are called through its generic JNI invokers.
 */
final class SurfacelessEglContext implements AutoCloseable {
    static final String PROPERTY = "openggf.headless.gl";
    static final String SURFACELESS = "surfaceless-egl";

    private static final int EGL_PLATFORM_SURFACELESS_MESA = 0x31DD;
    private static final int EGL_NONE = 0x3038;
    private static final int EGL_SURFACE_TYPE = 0x3033, EGL_PBUFFER_BIT = 0x0001;
    private static final int EGL_RENDERABLE_TYPE = 0x3040, EGL_OPENGL_BIT = 0x0008;
    private static final int EGL_RED_SIZE = 0x3024, EGL_GREEN_SIZE = 0x3023, EGL_BLUE_SIZE = 0x3022,
            EGL_ALPHA_SIZE = 0x3021, EGL_DEPTH_SIZE = 0x3025, EGL_STENCIL_SIZE = 0x3026;
    private static final int EGL_WIDTH = 0x3057, EGL_HEIGHT = 0x3056;
    private static final int EGL_OPENGL_API = 0x30A2;
    private static final int EGL_CONTEXT_MAJOR_VERSION = 0x3098, EGL_CONTEXT_MINOR_VERSION = 0x30FB,
            EGL_CONTEXT_OPENGL_PROFILE_MASK = 0x30FD, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT = 0x1;

    private final SharedLibrary egl;
    private final long display;
    private final long surface;
    private final long context;

    static boolean requested() {
        String value = System.getProperty(PROPERTY, "");
        if (value.isEmpty()) return false;
        if (!SURFACELESS.equals(value)) {
            throw new IllegalArgumentException(PROPERTY + " supports only " + SURFACELESS + ": " + value);
        }
        return true;
    }

    /** Creates and makes current a pbuffer-backed context, or throws without any fallback. */
    static SurfacelessEglContext create(int width, int height, boolean coreProfile) {
        SharedLibrary egl = Library.loadNative(SurfacelessEglContext.class, "org.lwjgl.egl", "libEGL.so.1");
        try {
            return new SurfacelessEglContext(egl, width, height, coreProfile);
        } catch (RuntimeException | Error failure) {
            egl.free();
            throw failure;
        }
    }

    private SurfacelessEglContext(SharedLibrary egl, int width, int height, boolean coreProfile) {
        this.egl = egl;
        long getPlatformDisplay = function("eglGetPlatformDisplay");
        display = invokePPP(EGL_PLATFORM_SURFACELESS_MESA, NULL, NULL, getPlatformDisplay);
        if (display == NULL) throw failure("eglGetPlatformDisplay(surfaceless)");
        long surfaceHandle = NULL, contextHandle = NULL;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer major = stack.mallocInt(1), minor = stack.mallocInt(1);
            if (invokePPPI(display, memAddress(major), memAddress(minor), function("eglInitialize")) == 0)
                throw failure("eglInitialize");
            if (invokeI(EGL_OPENGL_API, function("eglBindAPI")) == 0) throw failure("eglBindAPI(OpenGL)");
            IntBuffer attributes = stack.ints(EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                    EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8, EGL_NONE);
            PointerBuffer config = stack.mallocPointer(1);
            IntBuffer count = stack.mallocInt(1);
            if (invokePPPPI(display, memAddress(attributes), memAddress(config), 1, memAddress(count),
                    function("eglChooseConfig")) == 0 || count.get(0) < 1) throw failure("eglChooseConfig(pbuffer)");
            surfaceHandle = invokePPPP(display, config.get(0),
                    memAddress(stack.ints(EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE)),
                    function("eglCreatePbufferSurface"));
            if (surfaceHandle == NULL) throw failure("eglCreatePbufferSurface");
            IntBuffer contextAttributes = coreProfile
                    ? stack.ints(EGL_CONTEXT_MAJOR_VERSION, 4, EGL_CONTEXT_MINOR_VERSION, 1,
                            EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT, EGL_NONE)
                    : stack.ints(EGL_CONTEXT_MAJOR_VERSION, 2, EGL_CONTEXT_MINOR_VERSION, 1, EGL_NONE);
            contextHandle = invokePPPPP(display, config.get(0), NULL, memAddress(contextAttributes),
                    function("eglCreateContext"));
            if (contextHandle == NULL) throw failure("eglCreateContext");
            if (invokePPPPI(display, surfaceHandle, surfaceHandle, contextHandle, function("eglMakeCurrent")) == 0)
                throw failure("eglMakeCurrent");
        } catch (RuntimeException | Error failure) {
            if (contextHandle != NULL) invokePPI(display, contextHandle, function("eglDestroyContext"));
            if (surfaceHandle != NULL) invokePPI(display, surfaceHandle, function("eglDestroySurface"));
            invokePI(display, function("eglTerminate"));
            throw failure;
        }
        surface = surfaceHandle;
        context = contextHandle;
    }

    @Override
    public void close() {
        try {
            invokePPPPI(display, NULL, NULL, NULL, function("eglMakeCurrent"));
            invokePPI(display, context, function("eglDestroyContext"));
            invokePPI(display, surface, function("eglDestroySurface"));
            invokePI(display, function("eglTerminate"));
        } finally {
            egl.free();
        }
    }

    private long function(String name) {
        long address = egl.getFunctionAddress(name);
        if (address == NULL) throw new IllegalStateException("Surfaceless EGL lacks " + name);
        return address;
    }

    private IllegalStateException failure(String step) {
        int error = invokeI(function("eglGetError"));
        return new IllegalStateException("Surfaceless EGL " + step + " failed (0x"
                + Integer.toHexString(error) + "); no display fallback is used");
    }
}
