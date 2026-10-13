package com.openggf.level.render;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.mutators.TransientMutatorPolicySource.Entry;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.ShaderProgram;
import com.openggf.mods.mutators.ScenePresentationPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.*;

/** Private world composition. Terrain filtering finishes before the sprite pass; HUD is outside it. */
public final class ScenePresentationPass {
    private ShaderProgram shader;
    private int vao, worldFbo, worldTexture, terrainFbo, terrainTexture, sceneryTexture;
    private int width, height;

    /** Immutable draw input. Resolving or drawing it never advances the presentation clock. */
    public record Frame(boolean negative, boolean mirrorX, boolean mirrorY, float scaleX, float scaleY,
                        float shakeX, float shakeY, float anchorX, float anchorY,
                        int outline, int sceneryIndex) {
        public static Frame stock(float anchorX, float anchorY) {
            return new Frame(false, false, false, 1, 1, 0, 0, anchorX, anchorY, -1, -1);
        }
        public boolean active() {
            return negative || mirrorX || mirrorY || scaleX != 1 || scaleY != 1
                    || shakeX != 0 || shakeY != 0 || outline >= 0 || sceneryIndex >= 0;
        }
        public Frame withoutScenery() {
            return new Frame(negative, mirrorX, mirrorY, scaleX, scaleY, shakeX, shakeY,
                    anchorX, anchorY, outline, -1);
        }
        /** Inverse picture transform, in logical top-left coordinates, also used by pixel tests. */
        public float[] source(float x, float y, int logicalWidth, int logicalHeight) {
            x = (x - shakeX - anchorX) / scaleX + anchorX;
            y = (y - shakeY - anchorY) / scaleY + anchorY;
            return new float[]{mirrorX ? logicalWidth - x : x, mirrorY ? logicalHeight - y : y};
        }
    }

    public static Frame resolve(List<Entry> entries, LogicalInputSnapshot input, float anchorX, float anchorY) {
        boolean negative = false, mirrorX = false, mirrorY = false;
        float sx = 1, sy = 1, shakeX = 0, shakeY = 0;
        int outline = -1, scenery = -1;
        var player = input == null ? LogicalInputSnapshot.neutral().player1() : input.player1();
        int held = player.heldMask();
        for (var entry : entries) {
            if (entry.remainingPresentations() <= 0 || !(entry.policy() instanceof ScenePresentationPolicy policy)) continue;
            double phase = Math.floorMod(entry.agePresentations(), policy.periodPresentations())
                    * (Math.PI * 2 / policy.periodPresentations());
            float amount = policy.magnitude() * .01f;
            switch (policy.mode()) {
                case NEGATIVE -> negative = !negative;
                case MIRROR_HORIZONTAL -> mirrorX = !mirrorX;
                case MIRROR_VERTICAL -> mirrorY = !mirrorY;
                case SHAKE -> {
                    shakeX += (float) Math.sin(phase * 7) * policy.magnitude();
                    shakeY += (float) Math.sin(phase * 11) * policy.magnitude();
                }
                case ZOOM -> {
                    float zoom = 1 + (float) Math.sin(phase) * amount;
                    sx *= zoom; sy *= zoom;
                }
                case INPUT_SQUASH -> {
                    boolean horizontal = (held & (AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT)) != 0;
                    boolean vertical = (held & (AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_DOWN)) != 0;
                    float deformation = horizontal == vertical ? 0 : horizontal ? amount : -amount;
                    if (player.actionHeldMask() != 0) deformation = -amount;
                    sx *= 1 + deformation; sy *= 1 - deformation;
                }
                case OUTLINE -> outline = Math.max(outline, policy.magnitude());
                case SCENERY -> scenery = policy.sceneryIndex();
            }
        }
        return new Frame(negative, mirrorX, mirrorY, clamp(sx, .92f, 1.08f), clamp(sy, .92f, 1.08f),
                clamp(shakeX, -6, 6), clamp(shakeY, -6, 6), anchorX, anchorY, outline, scenery);
    }

    private static float clamp(float value, float low, float high) { return Math.max(low, Math.min(high, value)); }

    /** One queued world's immutable routing; native producers are captured at submission time. */
    public Scope open(GraphicsManager graphics, Frame frame, ZonePictureSource.Picture scenery,
                      int logicalWidth, int logicalHeight) {
        return new Scope(graphics, frame, scenery, logicalWidth, logicalHeight);
    }

    public final class Scope {
        private final GraphicsManager graphics;
        private final Frame frame;
        private final ZonePictureSource.Picture scenery;
        private final int logicalWidth, logicalHeight;
        private State saved;
        private boolean terrainOpen;
        private Scope(GraphicsManager graphics, Frame frame, ZonePictureSource.Picture scenery,
                      int logicalWidth, int logicalHeight) {
            this.graphics = graphics; this.frame = frame; this.scenery = scenery;
            this.logicalWidth = logicalWidth; this.logicalHeight = logicalHeight;
        }
        public GLCommandable beginCommand() { return command(this::begin); }
        public GLCommandable beginTerrainCommand() { return command(this::beginTerrain); }
        public GLCommandable endTerrainCommand() { return command(this::endTerrain); }
        public GLCommandable endCommand() { return command(this::end); }
        private GLCommandable command(Runnable action) {
            return new GLCommandable() {
                @Override public void execute(int cx, int cy, int cw, int ch) {
                    try { action.run(); }
                    catch (RuntimeException | Error failure) {
                        try { abort(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                        throw failure;
                    }
                }
                @Override public void unwindAfterFailure(int cx, int cy, int cw, int ch) { abort(); }
                @Override public void discard() { abort(); }
            };
        }
        private void begin() {
            saved = new State(graphics);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0); glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            ensureResources(saved.viewport[2], saved.viewport[3]);
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, saved.drawFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, worldFbo);
            glBlitFramebuffer(saved.viewport[0], saved.viewport[1], saved.viewport[0] + width,
                    saved.viewport[1] + height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_FRAMEBUFFER, worldFbo); glViewport(0, 0, width, height);
            graphics.setViewport(0, 0, width, height);
            if (scenery != null) drawScenery(scenery, logicalWidth, logicalHeight);
            PatternRenderCommand.resetFrameState();
        }
        private void beginTerrain() {
            if (frame.outline() < 0) return;
            terrainOpen = true; glBindFramebuffer(GL_FRAMEBUFFER, terrainFbo);
            glDisable(GL_SCISSOR_TEST); glClearColor(0, 0, 0, 0); glColorMask(true, true, true, true);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        private void endTerrain() {
            if (!terrainOpen) return;
            terrainOpen = false;
            glBindFramebuffer(GL_FRAMEBUFFER, worldFbo); glViewport(0, 0, width, height);
            drawTexture(terrainTexture, Frame.stock(frame.anchorX(), frame.anchorY()), logicalWidth,
                    logicalHeight, frame.outline(), true, saved.clearColor);
        }
        private void end() {
            try {
                endTerrain();
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved.drawFbo);
                glViewport(saved.viewport[0], saved.viewport[1], width, height);
                drawTexture(worldTexture, frame, logicalWidth, logicalHeight, -1, false, saved.clearColor);
            } finally { abort(); }
        }
        private void abort() {
            if (saved == null) return;
            State restore = saved; saved = null; terrainOpen = false;
            try { restore.restore(graphics); } finally { PatternRenderCommand.resetFrameState(); }
        }
    }

    /** Immediate equivalent for offscreen clients; production native commands use queued scopes. */
    public void render(GraphicsManager graphics, Frame frame, ZonePictureSource.Picture scenery,
                       Runnable terrain, Runnable sprites, int cameraX, int cameraY, int logicalWidth, int logicalHeight) {
        Scope scope = open(graphics, frame, scenery, logicalWidth, logicalHeight);
        try {
            scope.begin(); scope.beginTerrain();
            graphics.executeCapturedCommands(terrain, cameraX, cameraY, logicalWidth, logicalHeight);
            scope.endTerrain();
            graphics.executeCapturedCommands(sprites, cameraX, cameraY, logicalWidth, logicalHeight);
            scope.end();
        } finally { scope.abort(); }
    }

    private void ensureResources(int newWidth, int newHeight) {
        if (newWidth < 1 || newHeight < 1) throw new IllegalArgumentException("Empty scene viewport");
        if (shader == null) {
            try { shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, "shaders/shader_scene_presentation.frag"); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            vao = glGenVertexArrays();
        }
        if (worldFbo != 0 && newWidth == width && newHeight == height) return;
        destroyBuffers(); width = newWidth; height = newHeight;
        try {
            worldTexture = texture(width, height); worldFbo = framebuffer(worldTexture);
            terrainTexture = texture(width, height); terrainFbo = framebuffer(terrainTexture);
            sceneryTexture = glGenTextures();
        } catch (RuntimeException | Error failure) { destroyBuffers(); throw failure; }
    }

    private static int texture(int width, int height) {
        int texture = glGenTextures();
        try {
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            return texture;
        } catch (RuntimeException | Error failure) { glDeleteTextures(texture); throw failure; }
    }

    private static int framebuffer(int texture) {
        int fbo = glGenFramebuffers();
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Scene presentation framebuffer is incomplete");
            return fbo;
        } catch (RuntimeException | Error failure) { glDeleteFramebuffers(fbo); throw failure; }
    }

    private void drawScenery(ZonePictureSource.Picture picture, int logicalWidth, int logicalHeight) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(picture.width() * picture.height() * 4);
        // CPU pictures are top-to-bottom; OpenGL textures are bottom-to-top.
        for (int y = picture.height() - 1; y >= 0; y--) for (int x = 0; x < picture.width(); x++) {
            int color = picture.argb()[y * picture.width() + x];
            pixels.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >> 24));
        }
        pixels.flip(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sceneryTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, picture.width(), picture.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        drawTexture(sceneryTexture, Frame.stock(0, 0), logicalWidth, logicalHeight, -1, false, new float[]{0, 0, 0, 1});
    }

    private void drawTexture(int texture, Frame frame, int logicalWidth, int logicalHeight,
                             int outline, boolean blend, float[] backdrop) {
        glDisable(GL_DEPTH_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        if (blend) {
            glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        } else glDisable(GL_BLEND);
        shader.use(); int program = shader.getProgramId();
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
        glUniform4f(glGetUniformLocation(program, "Viewport"), viewport[0], viewport[1], viewport[2], viewport[3]);
        glUniform2f(glGetUniformLocation(program, "LogicalSize"), logicalWidth, logicalHeight);
        glUniform2f(glGetUniformLocation(program, "Anchor"), frame.anchorX(), frame.anchorY());
        glUniform2f(glGetUniformLocation(program, "Scale"), frame.scaleX(), frame.scaleY());
        glUniform2f(glGetUniformLocation(program, "Shake"), frame.shakeX(), frame.shakeY());
        glUniform2i(glGetUniformLocation(program, "Mirror"), frame.mirrorX() ? 1 : 0, frame.mirrorY() ? 1 : 0);
        glUniform1i(glGetUniformLocation(program, "Negative"), frame.negative() ? 1 : 0);
        glUniform1i(glGetUniformLocation(program, "Outline"), outline);
        glUniform4f(glGetUniformLocation(program, "Backdrop"), backdrop[0], backdrop[1], backdrop[2], backdrop[3]);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, "World"), 0);
        glBindVertexArray(vao); glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        PatternRenderCommand.resetFrameState();
    }

    public void cleanup() {
        destroyBuffers();
        if (shader != null) {
            int program = glGetInteger(GL_CURRENT_PROGRAM), ownedProgram = shader.getProgramId();
            shader.cleanup(); shader = null;
            if (program != ownedProgram) glUseProgram(program);
        }
        if (vao != 0) { glDeleteVertexArrays(vao); vao = 0; }
    }
    private void destroyBuffers() {
        if (worldFbo != 0) glDeleteFramebuffers(worldFbo);
        if (terrainFbo != 0) glDeleteFramebuffers(terrainFbo);
        if (worldTexture != 0) glDeleteTextures(worldTexture);
        if (terrainTexture != 0) glDeleteTextures(terrainTexture);
        if (sceneryTexture != 0) glDeleteTextures(sceneryTexture);
        worldFbo = terrainFbo = worldTexture = terrainTexture = sceneryTexture = 0;
    }

    /** Captures only state this pass and its nested native producers can disturb. */
    private static final class State {
        final int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE), arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING), unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),
                unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT), unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH),
                unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS), unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        final int[] viewport = new int[4], cachedViewport = new int[4], scissorBox = new int[4],
                textures = new int[7], textures1d = new int[7];
        final float[] clearColor = new float[4];
        final boolean[] colorMask = new boolean[4];
        final boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST), scissor = glIsEnabled(GL_SCISSOR_TEST),
                stencil = glIsEnabled(GL_STENCIL_TEST), cull = glIsEnabled(GL_CULL_FACE);
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
            cachedViewport[0] = graphics.getViewportX(); cachedViewport[1] = graphics.getViewportY();
            cachedViewport[2] = graphics.getViewportWidth(); cachedViewport[3] = graphics.getViewportHeight();
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
            graphics.setViewport(cachedViewport[0], cachedViewport[1], cachedViewport[2], cachedViewport[3]);
            glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            glBindVertexArray(vao); org.lwjgl.opengl.GL15.glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer); glUseProgram(program);
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer); glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength); glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
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
        private static void enabled(int capability, boolean value) { if (value) glEnable(capability); else glDisable(capability); }
    }
}
