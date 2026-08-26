package com.openggf.game.sonic2.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class Sonic2SpecialStageBackgroundCleanupRetryTest {
    @Test
    void laterFailureWithAnExistingFailureRetainsShaderForRetry() throws Exception {
        SpecialStageBackgroundRenderer renderer = new SpecialStageBackgroundRenderer(new GraphicsManager());
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
        SpecialStageBackgroundRenderer renderer = new SpecialStageBackgroundRenderer(graphics);
        float[] prior = new float[] {4, 5, 6};
        graphics.setProjectionMatrixBuffer(prior);

        renderer.beginFBOProjection();
        float[] fbo = graphics.getProjectionMatrixBuffer();
        renderer.beginFBOProjection();
        renderer.endFBOProjection();
        org.junit.jupiter.api.Assertions.assertArrayEquals(fbo, graphics.getProjectionMatrixBuffer());
        renderer.endFBOProjection();
        org.junit.jupiter.api.Assertions.assertArrayEquals(prior, graphics.getProjectionMatrixBuffer());
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
