package com.openggf.game.sonic2.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import com.openggf.graphics.QuadRenderer;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class Sonic2SpecialStageShaderStateTest {
    @Test
    void renderFailureRestoresCurrentProgramAndVertexArray() throws Exception {
        SpecialStageBackgroundRenderer renderer =
                new SpecialStageBackgroundRenderer(new GraphicsManager());
        HScrollBuffer hScroll = mock(HScrollBuffer.class);
        ParallaxShaderProgram shader = mock(ParallaxShaderProgram.class);
        QuadRenderer quad = mock(QuadRenderer.class);
        doThrow(new IllegalStateException("draw failure")).when(quad)
                .draw(anyFloat(), anyFloat(), anyFloat(), anyFloat());
        setField(renderer, "initialized", true);
        setField(renderer, "hScrollBuffer", hScroll);
        setField(renderer, "shader", shader);
        setField(renderer, "quadRenderer", quad);
        setField(renderer, "fboTextureId", 72);

        try (MockedStatic<GL11> gl11 = mockStatic(GL11.class);
             MockedStatic<GL13> gl13 = mockStatic(GL13.class);
             MockedStatic<GL14> gl14 = mockStatic(GL14.class);
             MockedStatic<GL20> gl20 = mockStatic(GL20.class);
             MockedStatic<GL30> gl30 = mockStatic(GL30.class)) {
            stubGl(gl11, gl13, gl14, gl20, gl30);
            assertThrows(IllegalStateException.class, () -> renderer.renderWithShader(0));
            gl20.verify(() -> GL20.glUseProgram(17));
            gl30.verify(() -> GL30.glBindVertexArray(23));
        }
    }

    private static void stubGl(MockedStatic<GL11> gl11, MockedStatic<GL13> gl13,
                               MockedStatic<GL14> gl14, MockedStatic<GL20> gl20,
                               MockedStatic<GL30> gl30) {
        gl11.when(() -> GL11.glGetIntegerv(eq(GL11.GL_VIEWPORT), any(int[].class)))
                .thenAnswer(invocation -> fill(invocation.getArgument(1), 0, 0, 320, 224));
        gl11.when(() -> GL11.glGetIntegerv(eq(GL11.GL_SCISSOR_BOX), any(int[].class)))
                .thenAnswer(invocation -> fill(invocation.getArgument(1), 1, 2, 3, 4));
        gl11.when(() -> GL11.glGetFloatv(eq(GL11.GL_COLOR_CLEAR_VALUE), any(float[].class)))
                .thenAnswer(invocation -> fill(invocation.getArgument(1), 0, 0, 0, 0));
        gl11.when(() -> GL11.glIsEnabled(anyInt())).thenReturn(false);
        gl11.when(() -> GL11.glGetInteger(anyInt())).thenReturn(1);
        gl11.when(() -> GL11.glViewport(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i -> null);
        gl11.when(() -> GL11.glScissor(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i -> null);
        gl11.when(() -> GL11.glEnable(anyInt())).thenAnswer(i -> null);
        gl11.when(() -> GL11.glDisable(anyInt())).thenAnswer(i -> null);
        gl11.when(() -> GL11.glClearColor(anyFloat(), anyFloat(), anyFloat(), anyFloat()))
                .thenAnswer(i -> null);
        gl11.when(() -> GL11.glBindTexture(anyInt(), anyInt())).thenAnswer(i -> null);
        gl13.when(() -> GL13.glGetInteger(GL13.GL_ACTIVE_TEXTURE)).thenReturn(GL13.GL_TEXTURE0);
        gl13.when(() -> GL13.glActiveTexture(anyInt())).thenAnswer(i -> null);
        gl14.when(() -> GL14.glBlendFuncSeparate(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> null);
        gl20.when(() -> GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM)).thenReturn(17);
        gl20.when(() -> GL20.glUseProgram(anyInt())).thenAnswer(i -> null);
        gl20.when(() -> GL20.glBlendEquationSeparate(anyInt(), anyInt())).thenAnswer(i -> null);
        gl30.when(() -> GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)).thenReturn(23);
        gl30.when(() -> GL30.glBindVertexArray(anyInt())).thenAnswer(i -> null);
    }

    private static <T> T fill(T array, int a, int b, int c, int d) {
        if (array instanceof int[] values) {
            values[0] = a;
            values[1] = b;
            values[2] = c;
            values[3] = d;
        } else if (array instanceof float[] values) {
            values[0] = a;
            values[1] = b;
            values[2] = c;
            values[3] = d;
        }
        return array;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
