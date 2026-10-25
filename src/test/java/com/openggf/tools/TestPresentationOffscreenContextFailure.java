package com.openggf.tools;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPresentationOffscreenContextFailure {
    @Test
    void failedGlInitializationCannotPreventEglRetirementOrReplaceTheOriginalFailure() {
        var context = mock(SurfacelessEglContext.class);
        var original = new ExceptionInInitializerError("GL initialization failed");
        var cleanup = new NoClassDefFoundError("GL is unavailable after initialization failed");
        try (var gl = mockStatic(GL.class); var glfw = mockStatic(GLFW.class)) {
            gl.when(() -> GL.setCapabilities(null)).thenThrow(cleanup);

            assertDoesNotThrow(() -> PresentationOffscreenTestContext.closeFailedEglContext(context, original));

            verify(context).close();
            glfw.verify(GLFW::glfwTerminate);
            glfw.verify(() -> GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM));
            assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        }
    }

    @Test
    void everyIndependentCleanupRunsAndItsFailureIsSuppressedInOrder() {
        var context = mock(SurfacelessEglContext.class);
        var original = new IllegalStateException("capabilities failed");
        var glFailure = new IllegalStateException("capabilities cleanup failed");
        var eglFailure = new IllegalStateException("EGL cleanup failed");
        var glfwFailure = new IllegalStateException("GLFW cleanup failed");
        var hintFailure = new IllegalStateException("hint reset failed");
        doThrow(eglFailure).when(context).close();
        try (var gl = mockStatic(GL.class); var glfw = mockStatic(GLFW.class)) {
            gl.when(() -> GL.setCapabilities(null)).thenThrow(glFailure);
            glfw.when(GLFW::glfwTerminate).thenThrow(glfwFailure);
            glfw.when(() -> GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM)).thenThrow(hintFailure);

            assertDoesNotThrow(() -> PresentationOffscreenTestContext.closeFailedEglContext(context, original));

            verify(context).close();
            glfw.verify(GLFW::glfwTerminate);
            glfw.verify(() -> GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM));
            assertArrayEquals(new Throwable[]{glFailure, eglFailure, glfwFailure, hintFailure},
                    original.getSuppressed());
        }
    }
}
