package com.openggf.game.sonic1.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import com.openggf.graphics.QuadRenderer;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class Sonic1SpecialStageBackgroundCleanupRetryTest {
    @Test
    void laterFailureWithAnExistingFailureRetainsShaderForRetry() throws Exception {
        Sonic1SpecialStageBackgroundRenderer renderer =
                new Sonic1SpecialStageBackgroundRenderer(new GraphicsManager());
        HScrollBuffer hScroll = mock(HScrollBuffer.class);
        ParallaxShaderProgram shader = mock(ParallaxShaderProgram.class);
        doThrow(new IllegalStateException("hscroll")).doNothing().when(hScroll).cleanup();
        doThrow(new IllegalArgumentException("shader")).doNothing().when(shader).cleanup();
        setField(renderer, "hScrollBuffer", hScroll);
        setField(renderer, "shader", shader);

        assertThrows(IllegalStateException.class, renderer::cleanup);
        assertNotNull(getField(renderer, "shader"),
                "a failed later cleanup must remain owned when an earlier failure exists");

        renderer.cleanup();
        verify(shader, org.mockito.Mockito.times(2)).cleanup();
    }

    @Test
    void nestedFboProjectionRestoresEachPriorProjectionBuffer() {
        GraphicsManager graphics = new GraphicsManager();
        Sonic1SpecialStageBackgroundRenderer renderer =
                new Sonic1SpecialStageBackgroundRenderer(graphics);
        float[] prior = new float[] {1, 2, 3};
        graphics.setProjectionMatrixBuffer(prior);

        renderer.beginFBOProjection();
        float[] fbo = graphics.getProjectionMatrixBuffer();
        renderer.beginFBOProjection();
        renderer.endFBOProjection();
        org.junit.jupiter.api.Assertions.assertArrayEquals(fbo, graphics.getProjectionMatrixBuffer());
        renderer.endFBOProjection();
        org.junit.jupiter.api.Assertions.assertArrayEquals(prior, graphics.getProjectionMatrixBuffer());
    }

    @Test
    void quadCleanupFailureRetainsOwnershipWhenEarlierCleanupAlreadyFailed() throws Exception {
        Sonic1SpecialStageBackgroundRenderer renderer =
                new Sonic1SpecialStageBackgroundRenderer(new GraphicsManager());
        HScrollBuffer hScroll = mock(HScrollBuffer.class);
        QuadRenderer quad = mock(QuadRenderer.class);
        doThrow(new IllegalStateException("hscroll")).doNothing().when(hScroll).cleanup();
        doThrow(new IllegalArgumentException("quad")).doNothing().when(quad).cleanup();
        setField(renderer, "hScrollBuffer", hScroll);
        setField(renderer, "quadRenderer", quad);
        setField(renderer, "quadRendererOwned", true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, renderer::cleanup);
        assertTrue((Boolean) getField(renderer, "quadRendererOwned"));
        assertTrue(renderer.hasCleanupPendingOwnership());

        renderer.cleanup();
        verify(quad, times(2)).cleanup();
        assertFalse((Boolean) getField(renderer, "quadRendererOwned"));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
