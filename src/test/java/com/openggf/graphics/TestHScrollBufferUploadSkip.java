package com.openggf.graphics;

import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Skipping an unchanged H-scroll upload must leave the texture holding exactly the last staged lines. */
@org.junit.jupiter.api.parallel.Isolated
class TestHScrollBufferUploadSkip {
    @Test void unchangedLinesSkipTheUploadButEveryChangeAndRecreationReachesTheTexture() {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var context = PresentationOffscreenTestContext.openConfigured(
                32, 32, PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            HScrollBuffer buffer = new HScrollBuffer();
            try {
                buffer.init();
                int[] first = lines(100);
                int[] second = lines(-37);
                buffer.upload(first);
                assertTexture(buffer, first);
                buffer.upload(first.clone());
                assertTexture(buffer, first);
                buffer.upload(second);
                assertTexture(buffer, second);
                buffer.upload(first);
                assertTexture(buffer, first);
                int[] doubled = new int[448];
                java.util.Arrays.fill(doubled, 5);
                buffer.upload(doubled);
                assertTexture(buffer, doubled);
                buffer.upload(first);
                assertTexture(buffer, first);
                // A recreated texture starts undefined, so equal data must upload again.
                buffer.cleanup();
                buffer.init();
                buffer.upload(first);
                assertTexture(buffer, first);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                buffer.cleanup();
            }
        }
    }

    private static int[] lines(int base) {
        int[] packed = new int[HScrollBuffer.VISIBLE_LINES];
        for (int i = 0; i < packed.length; i++) packed[i] = (base + i * 3) & 0xFFFF;
        return packed;
    }

    private static void assertTexture(HScrollBuffer buffer, int[] packed) {
        var texels = BufferUtils.createFloatBuffer(packed.length);
        glBindTexture(GL_TEXTURE_1D, buffer.getTextureId());
        assertEquals(packed.length, glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH));
        glGetTexImage(GL_TEXTURE_1D, 0, GL_RED, GL_FLOAT, texels);
        glBindTexture(GL_TEXTURE_1D, 0);
        for (int i = 0; i < packed.length; i++) {
            float expected = Math.max(-1f, Math.min(1f, (short) packed[i] / 32767f));
            assertEquals(expected, texels.get(i), "line " + i);
        }
    }
}
