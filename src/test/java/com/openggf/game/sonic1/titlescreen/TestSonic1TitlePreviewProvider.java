package com.openggf.game.sonic1.titlescreen;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic1TitlePreviewProvider {

    @Test
    void sonic1PreviewSettledLoopKeepsAlternatingFinalFrames() throws Exception {
        int token = invokeIntHelper("sonic1PreviewTokenAt", 152);

        assertEquals(6, invokeIntHelper("sonic1PreviewSonicFrameAt", token));
    }

    @Test
    void sonic1PreviewUsesSettledIdleLoopFrame() {
        assertEquals(6, Sonic1TitlePreviewProvider.sonic1PreviewSonicFrameIndex());
    }

    @Test
    void sonic1PreviewUsesNativeScreenAndRuntimePlaneAOffset() {
        assertEquals(320, Sonic1TitlePreviewProvider.sonic1PreviewWidth());
        assertEquals(224, Sonic1TitlePreviewProvider.sonic1PreviewHeight());
        assertEquals(32, Sonic1TitlePreviewProvider.sonic1PlaneAX());
        assertEquals(32, Sonic1TitlePreviewProvider.sonic1PlaneAY());
    }

    private static int invokeIntHelper(String methodName, int value) throws Exception {
        Method method = Sonic1TitlePreviewProvider.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, value);
    }
}
