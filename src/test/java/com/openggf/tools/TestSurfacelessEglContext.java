package com.openggf.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.glfw.GLFW;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class TestSurfacelessEglContext {
    @AfterEach
    void clearProperty() {
        System.clearProperty(SurfacelessEglContext.PROPERTY);
    }

    @Test
    void selectionIsOptInAndRejectsUnknownBackends() {
        System.clearProperty(SurfacelessEglContext.PROPERTY);
        assertFalse(SurfacelessEglContext.requested());
        System.setProperty(SurfacelessEglContext.PROPERTY, SurfacelessEglContext.SURFACELESS);
        assertTrue(SurfacelessEglContext.requested());
        System.setProperty(SurfacelessEglContext.PROPERTY, "x11");
        assertThrows(IllegalArgumentException.class, SurfacelessEglContext::requested);
    }

    @Test
    void failedBootReleasesTheNullPlatformHintBeforeAnotherOwnerInitializes() {
        System.setProperty(SurfacelessEglContext.PROPERTY, SurfacelessEglContext.SURFACELESS);
        // A failed initialization must not leave later native-window owners on the null platform.
        // Mock GLFW here: the regression never connects to a display or opens a window.
        try (var services = mockStatic(EngineServices.class);
             var glfw = mockStatic(GLFW.class)) {
            glfw.when(GLFW::glfwInit).thenReturn(false);
            assertThrows(IllegalStateException.class,
                    () -> new HeadlessGameBoot(64, 32, mock(EngineContext.class)));
            glfw.verify(() -> GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_NULL));
            glfw.verify(() -> GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_ANY_PLATFORM));
        }
    }

    @Test
    void pbufferBackBufferReadsBackWithoutADisplayConnection() {
        SurfacelessEglContext context;
        try {
            context = SurfacelessEglContext.create(64, 32, false);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            assumeTrue(false, "surfaceless EGL unavailable: " + unavailable.getMessage());
            return;
        }
        try (context) {
            GL.createCapabilities();
            glViewport(0, 0, 64, 32);
            glClearColor(0f, 1f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            glReadBuffer(GL_BACK);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            glReadPixels(63, 31, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertEquals(0, pixel.get(0) & 0xFF);
            assertEquals(255, pixel.get(1) & 0xFF);
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            GL.setCapabilities(null);
        }
    }
}
