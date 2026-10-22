package com.openggf.stageplay.runtime;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsPresentationViewport;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.ShaderProgram;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.util.FboHelper;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Stageplay-only world composition. Native viewport tiles retain the native renderer's
 * 224-line buffers; only the completed opaque picture is filtered. Text and transitions
 * are drawn by the host after this scope. No gameplay owner is ticked or resized.
 */
public final class StageplayWorldPresentation implements AutoCloseable {
    public static final int MAX_SOURCE_SIDE = 4096;
    public static final long MAX_SOURCE_PIXELS = 8L * 1024 * 1024;
    private ShaderProgram shader;
    private int vao;
    private FboHelper.FboHandle source, tile;
    private int sourceWidth, sourceHeight, tileWidth, tileHeight;
    private boolean drawing;

    /** Draw all world layers, including posed cast, inside one native viewport. Flush before returning. */
    @FunctionalInterface
    public interface WorldDraw {
        void draw(int cameraX, int cameraY, int viewportWidth, int viewportHeight);
    }

    /** Resource preflight is independent of a graphics context. */
    public static void validate(int width, int height, double zoom) {
        if (width < 1 || height < 1 || !Double.isFinite(zoom) || zoom < .05 || zoom > 64)
            throw new IllegalArgumentException("Invalid Stageplay world dimensions or zoom");
        double w = Math.ceil(width / zoom), h = Math.ceil(height / zoom);
        if (w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE || w * h > MAX_SOURCE_PIXELS)
            throw new IllegalArgumentException("Stageplay world view exceeds bounded composition: "
                    + (long) w + "x" + (long) h + "; maximum side " + MAX_SOURCE_SIDE
                    + ", maximum source pixels " + MAX_SOURCE_PIXELS);
    }

    public void draw(GraphicsManager graphics, LevelManager level, int x, int y, double zoom,
                     int width, int height, Runnable nativeDraw) {
        draw(graphics, level, x, y, zoom, width, height, (cx, cy, w, h) -> nativeDraw.run());
    }

    public void draw(GraphicsManager graphics, LevelManager level, int x, int y, double zoom,
                     int width, int height, WorldDraw worldDraw) {
        validate(width, height, zoom);
        int nativeWidth = LevelSpritePresentation.viewportWidth(level);
        int nativeHeight = LevelSpritePresentation.viewportHeight(level);
        int rasterWidth = (int) Math.ceil(width / zoom);
        int rasterHeight = (int) Math.ceil(height / zoom);
        LevelSpritePresentation.withCinematicView(level, x, y, rasterWidth, rasterHeight, () -> {
            if (zoom == 1 && width == nativeWidth && height == nativeHeight) {
                // Native-size output still uses the one full-view directed scroll sample.
                LevelSpritePresentation.withCinematicTile(level, 0, 0, nativeWidth, nativeHeight,
                        () -> worldDraw.draw(x, y, nativeWidth, nativeHeight));
                return;
            }
            drawTiles(graphics, x, y, zoom, width, height, nativeWidth, nativeHeight,
                    (cx, cy, w, h) -> LevelSpritePresentation.withCinematicTile(level, cx - x, cy - y, w, h,
                            () -> worldDraw.draw(cx, cy, w, h)));
        });
    }

    /** Component seam also used by the EGL pixel regression; no alternate renderer or world simulation. */
    void drawTiles(GraphicsManager graphics, int x, int y, double zoom, int width, int height,
                   int nativeWidth, int nativeHeight, WorldDraw worldDraw) {
        validate(width, height, zoom);
        if (nativeWidth < 1 || nativeHeight < 1 || nativeWidth > MAX_SOURCE_SIDE || nativeHeight > MAX_SOURCE_SIDE)
            throw new IllegalArgumentException("Invalid native Stageplay viewport");
        if (drawing) throw new IllegalStateException("Nested Stageplay world composition");
        drawing = true;
        State saved = new State(graphics);
        try {
            int w = (int) Math.ceil(width / zoom), h = (int) Math.ceil(height / zoom);
            ensureResources(w, h, nativeWidth, nativeHeight);
            glDisable(GL_SCISSOR_TEST);
            glColorMask(true, true, true, true);
            GraphicsPresentationViewport.withViewport(graphics, nativeWidth, nativeHeight, () -> {
                for (int row = 0; row < h; row += nativeHeight) {
                    for (int column = 0; column < w; column += nativeWidth) {
                        glBindFramebuffer(GL_FRAMEBUFFER, tile.fboId());
                        glViewport(0, 0, nativeWidth, nativeHeight);
                        glDisable(GL_SCISSOR_TEST);
                        glColorMask(true, true, true, true);
                        glDepthMask(true);
                        glClearColor(0, 0, 0, 1);
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        PatternRenderCommand.resetFrameState();
                        worldDraw.draw(x + column, y + row, nativeWidth, nativeHeight);
                        // Even producers that preserve transparent alpha contribute a completed
                        // world RGB picture. Alpha cannot introduce fringes during resampling.
                        glDisable(GL_SCISSOR_TEST);
                        glBindFramebuffer(GL_FRAMEBUFFER, tile.fboId());
                        glColorMask(false, false, false, true);
                        glClearColor(0, 0, 0, 1);
                        glClear(GL_COLOR_BUFFER_BIT);
                        glColorMask(true, true, true, true);
                        int copyWidth = Math.min(nativeWidth, w - column);
                        int copyHeight = Math.min(nativeHeight, h - row);
                        glBindFramebuffer(GL_READ_FRAMEBUFFER, tile.fboId());
                        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, source.fboId());
                        glBlitFramebuffer(0, nativeHeight - copyHeight, copyWidth, nativeHeight,
                                column, h - row - copyHeight, column + copyWidth, h - row,
                                GL_COLOR_BUFFER_BIT, GL_NEAREST);
                    }
                }
            });
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved.drawFbo);
            glViewport(saved.viewport[0], saved.viewport[1], saved.viewport[2], saved.viewport[3]);
            glDisable(GL_DEPTH_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST);
            glDisable(GL_CULL_FACE); glDisable(GL_BLEND);
            glColorMask(true, true, true, true);
            shader.use();
            int program = shader.getProgramId();
            glUniform4f(glGetUniformLocation(program, "Viewport"), saved.viewport[0], saved.viewport[1],
                    saved.viewport[2], saved.viewport[3]);
            glUniform2f(glGetUniformLocation(program, "SourceExtent"),
                    (float) (width / zoom / w), (float) (height / zoom / h));
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, source.textureId());
            // Texture-local state, never the palette atlas, sprite art, or text textures.
            boolean minifying = width / zoom > saved.viewport[2] || height / zoom > saved.viewport[3];
            if (minifying) glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minifying ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glUniform1i(glGetUniformLocation(program, "World"), 0);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        } catch (RuntimeException | Error failure) {
            graphics.discardQueuedCommands();
            throw failure;
        } finally {
            try { saved.restore(graphics); }
            finally { drawing = false; PatternRenderCommand.resetFrameState(); }
        }
    }

    private void ensureResources(int width, int height, int nativeWidth, int nativeHeight) {
        int maximum = glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (Math.max(Math.max(width, height), Math.max(nativeWidth, nativeHeight)) > maximum)
            throw new IllegalArgumentException("Stageplay composition exceeds this device's maximum texture size " + maximum);
        if (shader == null) {
            try { shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, "shaders/shader_stageplay_world.frag"); }
            catch (IOException error) { throw new UncheckedIOException(error); }
            vao = glGenVertexArrays();
        }
        if (source == null || sourceWidth != width || sourceHeight != height) {
            FboHelper.destroy(source);
            source = FboHelper.createColorOnly(width, height, GL_CLAMP_TO_EDGE);
            if (source == null) throw new IllegalStateException("Stageplay world framebuffer is incomplete");
            sourceWidth = width; sourceHeight = height;
        }
        if (tile == null || tileWidth != nativeWidth || tileHeight != nativeHeight) {
            FboHelper.destroy(tile);
            tile = FboHelper.createWithDepth(nativeWidth, nativeHeight, GL_CLAMP_TO_EDGE);
            if (tile == null) throw new IllegalStateException("Stageplay native tile framebuffer is incomplete");
            tileWidth = nativeWidth; tileHeight = nativeHeight;
        }
    }

    @Override public void close() {
        if (drawing) throw new IllegalStateException("Cannot close an active Stageplay presentation");
        FboHelper.destroy(source); FboHelper.destroy(tile); source = tile = null;
        if (shader != null) { shader.cleanup(); shader = null; }
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
    }

    /** Preserve the caller across native tile producers and the composition shader, including failure. */
    private static final class State {
        final int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE), arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING), unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] viewport = new int[4], scissorBox = new int[4], textures = new int[7], textures1d = new int[7];
        final float[] clearColor = new float[4];
        final boolean[] colorMask = new boolean[4];
        final boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST), scissor = glIsEnabled(GL_SCISSOR_TEST),
                stencil = glIsEnabled(GL_STENCIL_TEST), cull = glIsEnabled(GL_CULL_FACE);
        final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB),
                srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA),
                eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final boolean waterShader, underwaterBackground, spritePriority;
        State(GraphicsManager graphics) {
            waterShader = graphics.getWaterShaderProgram() != null && graphics.getShaderProgram() == graphics.getWaterShaderProgram();
            underwaterBackground = graphics.isUseUnderwaterPaletteForBackground();
            spritePriority = graphics.isUseSpritePriorityShader();
            glGetIntegerv(GL_VIEWPORT, viewport); glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            ByteBuffer mask = BufferUtils.createByteBuffer(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
            for (int i = 0; i < textures.length; i++) {
                glActiveTexture(GL_TEXTURE0 + i); textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
                textures1d[i] = glGetInteger(GL_TEXTURE_BINDING_1D);
            }
            glActiveTexture(activeTexture);
        }
        void restore(GraphicsManager graphics) {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo); glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            glDepthMask(depthWrite);
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer); glUseProgram(program);
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            graphics.setUseWaterShader(waterShader); graphics.setUseUnderwaterPaletteForBackground(underwaterBackground);
            graphics.setUseSpritePriorityShader(spritePriority);
            for (int i = 0; i < textures.length; i++) {
                glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, textures[i]); glBindTexture(GL_TEXTURE_1D, textures1d[i]);
            }
            glActiveTexture(activeTexture); glBlendEquationSeparate(eqRgb, eqAlpha);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            enabled(GL_BLEND, blend); enabled(GL_DEPTH_TEST, depth); enabled(GL_SCISSOR_TEST, scissor);
            enabled(GL_STENCIL_TEST, stencil); enabled(GL_CULL_FACE, cull);
        }
        private static void enabled(int capability, boolean enabled) { if (enabled) glEnable(capability); else glDisable(capability); }
    }
}
