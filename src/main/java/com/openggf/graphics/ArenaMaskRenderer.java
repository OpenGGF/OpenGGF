package com.openggf.graphics;

import java.io.IOException;
import java.io.UncheckedIOException;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Shared shader overlay, after world composition and before HUD. Draws into the
 * currently bound framebuffer (including capture FBOs); never captures framebuffer 0.
 * Owned and disposed by GraphicsManager. Drawing does not advance presentation state.
 */
public final class ArenaMaskRenderer {
    /** Internal bridge; does not add an experimental method to the published GraphicsManager API. */
    public static void enqueue(GraphicsManager graphics, ArenaMaskState state) {
        graphics.drawArenaMask(state);
    }

    private ShaderProgram shader;
    private int vao, opacityTexture;
    void draw(int width, int height, int left, int right, int noiseFrame) {
        draw(width, height, new ArenaMaskState(left, right, noiseFrame));
    }
    void draw(int width, int height, ArenaMaskState state) {
        if (!state.visible(width)) return;
        int activeTexture = glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_1D);
        int program = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        try {
            if (shader == null) {
                shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, "shaders/shader_arena_mask.frag");
                vao = glGenVertexArrays();
                opacityTexture = glGenTextures();
            }
            int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
            shader.use(); int id = shader.getProgramId();
            glUniform4f(glGetUniformLocation(id, "Viewport"), viewport[0], viewport[1], viewport[2], viewport[3]);
            glUniform2f(glGetUniformLocation(id, "LogicalSize"), width, height);
            glUniform1ui(glGetUniformLocation(id, "NoiseFrame"), state.noiseFrame());
            var opacity = org.lwjgl.BufferUtils.createFloatBuffer(width);
            for (int x = 0; x < width; x++) opacity.put(state.opacityAt(x));
            opacity.flip();
            glBindTexture(GL_TEXTURE_1D, opacityTexture);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage1D(GL_TEXTURE_1D, 0, GL_R32F, width, 0, GL_RED, GL_FLOAT, opacity);
            glUniform1i(glGetUniformLocation(id, "Opacity"), 0);
            glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST); glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            glBindVertexArray(vao); glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        } catch (IOException e) {
            cleanup(); throw new UncheckedIOException(e);
        } finally {
            glBindTexture(GL_TEXTURE_1D, oldTexture);
            org.lwjgl.opengl.GL13.glActiveTexture(activeTexture);
            glBindVertexArray(oldVao); glUseProgram(program);
            glBlendEquationSeparate(eqRgb, eqAlpha);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) glDisable(GL_BLEND);
            if (depth) glEnable(GL_DEPTH_TEST);
            if (scissor) glEnable(GL_SCISSOR_TEST);
        }
    }
    void cleanup() {
        if (shader != null) { shader.cleanup(); shader = null; }
        if (opacityTexture != 0) { glDeleteTextures(opacityTexture); opacityTexture = 0; }
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
    }
}
