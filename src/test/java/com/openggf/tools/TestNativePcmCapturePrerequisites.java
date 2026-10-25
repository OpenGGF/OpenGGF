package com.openggf.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;
import org.opentest4j.TestAbortedException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** ROM-free controls for the live PCM fixture's graphics admission boundary. */
class TestNativePcmCapturePrerequisites {
    @TempDir Path output;

    @Test
    void nonGraphicsBootFailureIsNotConvertedToASkip() {
        var failure = new IllegalStateException("capture did not boot into LEVEL mode: DATA_SELECT");
        try (var contexts = mockStatic(PresentationOffscreenTestContext.class);
             var sessions = mockConstruction(GameplayCaptureSession.class, (session, context) ->
                     doThrow(failure).when(session).boot(any(), anyInt(), anyInt(), any()))) {
            var prerequisite = mock(PresentationOffscreenTestContext.class);
            contexts.when(() -> PresentationOffscreenTestContext.openConfigured(
                    320, 224, PresentationOffscreenTestContext.LegacyProfile.DEFAULT)).thenReturn(prerequisite);

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> TestNativePcmCaptureLive.runCapture(arguments())));
            verify(prerequisite).close();
            assertEquals(1, sessions.constructed().size());
            verify(sessions.constructed().getFirst()).close();
        }
    }

    @Test
    void unavailableDefaultGraphicsPrerequisiteAbortsBeforeCapture() {
        String incoming = System.getProperty("openggf.headless.gl");
        System.clearProperty("openggf.headless.gl");
        // The real configured helper sees glfwInit() return false. No window or display is opened.
        try (var glfw = mockStatic(GLFW.class);
             var sessions = mockConstruction(GameplayCaptureSession.class)) {
            var abort = assertThrows(TestAbortedException.class,
                    () -> TestNativePcmCaptureLive.runCapture(arguments()));
            assertTrue(abort.getMessage().contains("Hidden OpenGL unavailable"));
            assertTrue(sessions.constructed().isEmpty(), "capture must not start after failed admission");
        } finally {
            restoreBackend(incoming);
        }
    }

    @Test
    void explicitlyRequestedEglFailureFailsBeforeCapture() {
        String incoming = System.getProperty("openggf.headless.gl");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        // The real explicit-EGL helper rejects unavailable GLFW null-platform services.
        try (var glfw = mockStatic(GLFW.class);
             var sessions = mockConstruction(GameplayCaptureSession.class)) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> TestNativePcmCaptureLive.runCapture(arguments()));
            assertTrue(failure.getMessage().contains("GLFW null platform unavailable"));
            assertTrue(sessions.constructed().isEmpty());
        } finally {
            restoreBackend(incoming);
        }
    }

    private GameplayCaptureTool.Arguments arguments() {
        return GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--rom", output.resolve("unused.gen").toString(),
                "--zone", "aiz", "--act", "1", "--frames", "1", "--no-video",
                "--out-dir", output.toString()});
    }

    private static void restoreBackend(String incoming) {
        if (incoming == null) System.clearProperty("openggf.headless.gl");
        else System.setProperty("openggf.headless.gl", incoming);
    }
}
