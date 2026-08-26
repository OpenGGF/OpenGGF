package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

class SpecialStageOwnedFramebufferTest {
    @Test
    void partialCreationRetainsNamesForAOneShotCleanupRetry() {
        try (MockedStatic<GL11> gl11 = mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = mockStatic(GL13.class);
             MockedStatic<GL30> gl30 = mockStatic(GL30.class)) {
            gl11.when(GL11::glGenTextures).thenReturn(22);
            gl30.when(GL30::glGenFramebuffers).thenReturn(11);
            gl30.when(GL30::glGenRenderbuffers).thenReturn(33);
            gl11.when(() -> GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)).thenReturn(7);
            gl30.when(() -> GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER))
                    .thenReturn(GL30.GL_FRAMEBUFFER_COMPLETE);
            gl30.when(() -> GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 7))
                    .thenThrow(new IllegalStateException("restore once"))
                    .thenAnswer(invocation -> null);

            SpecialStageOwnedFramebuffer owner = new SpecialStageOwnedFramebuffer(256, 256,
                    GL11.GL_REPEAT);
            assertThrows(IllegalStateException.class, owner::create);
            assertFalse(owner.isPublished());
            assertTrue(owner.hasPendingOwnership());
            assertEquals(11, owner.fboId());
            assertEquals(22, owner.textureId());
            assertEquals(33, owner.depthId());

            owner.cleanup();
            assertFalse(owner.hasPendingOwnership());
            owner.cleanup();
        }
    }
}
