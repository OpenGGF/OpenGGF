package com.openggf.graphics;

import com.openggf.util.FboHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class TestFboReleaseFailure {

    @Test
    void createColorOnlyReturnsNullAndDeletesResourcesWhenFramebufferIsIncomplete() {
        try (MockedStatic<GL11> gl11 = mockStatic(GL11.class);
             MockedStatic<GL15> gl15 = mockStatic(GL15.class);
             MockedStatic<GL30> gl30 = mockStatic(GL30.class)) {
            // The caller may be rendering into another target with an unpack PBO bound.
            // Mock every GL owner used by the helper: this test has no native context.
            gl11.when(() -> GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)).thenReturn(11);
            gl11.when(() -> GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)).thenReturn(12);
            gl11.when(() -> GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)).thenReturn(13);
            gl11.when(() -> GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING)).thenReturn(14);
            gl11.when(() -> GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)).thenReturn(15);
            gl30.when(GL30::glGenFramebuffers).thenReturn(101);
            gl11.when(GL11::glGenTextures).thenReturn(202);
            gl30.when(() -> GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER))
                    .thenReturn(GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT);

            FboHelper.FboHandle handle = FboHelper.createColorOnly(320, 224, GL12.GL_CLAMP_TO_EDGE);

            assertNull(handle, "Incomplete framebuffer creation must fail closed");
            gl30.verify(() -> GL30.glDeleteFramebuffers(101));
            gl11.verify(() -> GL11.glDeleteTextures(202));
            gl30.verify(() -> GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 11));
            gl30.verify(() -> GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 12));
            gl11.verify(() -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, 13));
            gl30.verify(() -> GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 14));
            gl15.verify(() -> GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0));
            gl15.verify(() -> GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 15));
        }
    }

    @Test
    void tilePriorityFboRemainsUninitializedWhenColorFboCreationFails() {
        try (MockedStatic<FboHelper> fboHelper = mockStatic(FboHelper.class)) {
            fboHelper.when(() -> FboHelper.createColorOnly(320, 224, GL12.GL_CLAMP_TO_EDGE))
                    .thenReturn(null);

            TilePriorityFBO priorityFbo = new TilePriorityFBO();

            assertDoesNotThrow(() -> priorityFbo.init(320, 224));
            assertFalse(priorityFbo.isInitialized());
            assertEquals(-1, priorityFbo.getTextureId());
            assertEquals(320, priorityFbo.getWidth());
            assertEquals(224, priorityFbo.getHeight());
        }
    }
}
