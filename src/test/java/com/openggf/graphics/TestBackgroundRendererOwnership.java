package com.openggf.graphics;

import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Characterizes the graphics-owned GL resource lifetime before an ownership migration. */
@Isolated
class TestBackgroundRendererOwnership {
    @Test
    void noContextAndLogicOnlyHeadlessModeDoNotAllocateBackgroundResources() {
        var graphics = new GraphicsManager();
        assertNull(graphics.getBackgroundRenderer());
        graphics.initHeadless();
        assertNull(graphics.getBackgroundRenderer());
        graphics.resetState();
        assertNull(graphics.getBackgroundRenderer());
        graphics.cleanup();
    }

    @Test
    void levelResetReleasesBackgroundResourcesWhileTheContextCanCreateANewOwner() {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var context = PresentationOffscreenTestContext.open(320, 224)) {
            var graphics = new GraphicsManager();
            graphics.setGlInitialized(true);
            try {
                var first = graphics.getBackgroundRenderer();
                assertNotNull(first);
                assertTrue(first.isInitialized());
                assertSame(first, graphics.getBackgroundRenderer());
                int firstTexture = first.getFBOTextureId();
                assertTrue(glIsTexture(firstTexture));

                graphics.resetState();
                assertTrue(graphics.isGlInitialized());
                assertFalse(first.isInitialized());
                assertFalse(glIsTexture(firstTexture), "reset releases the actual native texture before reuse");

                var replacement = graphics.getBackgroundRenderer();
                assertNotNull(replacement);
                assertNotSame(first, replacement);
                assertTrue(replacement.isInitialized());
                assertTrue(glIsTexture(replacement.getFBOTextureId()));
                assertSame(replacement, graphics.getBackgroundRenderer());

                int replacementTexture = replacement.getFBOTextureId();
                graphics.cleanup();
                assertFalse(replacement.isInitialized());
                assertFalse(glIsTexture(replacementTexture));
                assertFalse(graphics.isGlInitialized());
                assertNull(graphics.getBackgroundRenderer());
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                graphics.cleanup();
            }
        }
    }

    @Test
    void aReusedGraphicsOwnerCreatesFreshResourcesInEachNativeContext() {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var graphics = new GraphicsManager();
        com.openggf.level.render.BackgroundRenderer previous = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            try (var context = PresentationOffscreenTestContext.open(320, 224)) {
                assertNull(graphics.getBackgroundRenderer());
                graphics.setGlInitialized(true);
                try {
                    var current = graphics.getBackgroundRenderer();
                    assertNotNull(current);
                    assertNotSame(previous, current);
                    assertTrue(current.isInitialized());
                    assertTrue(glIsTexture(current.getFBOTextureId()));
                    previous = current;
                } finally {
                    graphics.cleanup();
                }
                assertFalse(previous.isInitialized());
                assertNull(graphics.getBackgroundRenderer());
                assertEquals(GL_NO_ERROR, glGetError());
            }
        }
    }
}
