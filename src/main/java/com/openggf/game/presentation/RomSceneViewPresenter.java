package com.openggf.game.presentation;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TexturedQuadRenderer;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL13.*;

/** Renderer-owned local resources only. Incoming values cannot reach a player, object, camera or PLC service. */
final class RomSceneViewPresenter implements SceneViewPresenter {
    private final RomSceneArtCatalog art;
    private final GraphicsManager graphics;
    private final int width, height;
    private ScenePresentationFrame frame;
    private boolean closed;
    private TexturedQuadRenderer quad;
    private int texture;
    private ByteBuffer rgba;

    RomSceneViewPresenter(RomSceneArtCatalog art, GraphicsManager graphics, int width, int height) {
        this.art = art; this.graphics = graphics; this.width = width; this.height = height;
        if (width < 1 || width > 800 || height < 1 || height > 512) throw new IllegalArgumentException("Unsupported viewport");
    }

    @Override public boolean accept(ScenePresentationFrame value) {
        if (closed) throw new IllegalStateException("Scene presenter is closed");
        if (value.width() != width || value.height() != height) {
            throw new IllegalArgumentException("Scene viewport mismatch");
        }
        if (frame != null && value.revision() <= frame.revision()) return false;
        // Validate the complete locally resolved view before replacing the last good frame.
        for (var tile : value.tiles()) art.resolve(tile.art());
        frame = value;
        return true;
    }

    @Override public long revision() { return frame == null ? -1 : frame.revision(); }

    @Override public SceneImage image(int offsetX, int offsetY) {
        if (closed) throw new IllegalStateException("Scene presenter is closed");
        if (frame == null) throw new IllegalStateException("No accepted scene");
        return SceneCompositor.compose(frame, art, offsetX, offsetY);
    }

    @Override public void draw(int offsetX, int offsetY) {
        SceneImage image = image(offsetX, offsetY);
        if (graphics.isHeadlessMode()) return;
        graphics.flushPatternBatch();
        graphics.registerCommand((cameraX, cameraY, cameraWidth, cameraHeight) -> drawImage(image));
    }

    private void drawImage(SceneImage image) {
        if (closed) return;
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM), previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousActive = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        boolean previousBlend = glIsEnabled(GL_BLEND), previousDepth = glIsEnabled(GL_DEPTH_TEST);
        boolean previousScissor = glIsEnabled(GL_SCISSOR_TEST);
        try {
            if (quad == null) {
                quad = new TexturedQuadRenderer(); quad.init();
                texture = glGenTextures();
                rgba = MemoryUtil.memAlloc(width * height * 4);
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            rgba.clear();
            for (int color : image.argb()) {
                rgba.put((byte) (color >>> 16)).put((byte) (color >>> 8)).put((byte) color).put((byte) (color >>> 24));
            }
            rgba.flip(); glBindTexture(GL_TEXTURE_2D, texture);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST);
            float[] projection = graphics.getProjectionMatrixBuffer();
            if (projection == null) throw new IllegalStateException("Scene projection is not installed");
            quad.setProjectionMatrix(projection);
            // Engine scene projection is bottom-left; CPU image rows are top-down.
            quad.drawTextureRegion(texture, 0, 0, width, height, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Scene texture renderer initialization failed", e);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture); glActiveTexture(previousActive);
            glBindVertexArray(previousVao); glUseProgram(previousProgram);
            if (previousBlend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            if (previousDepth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
            if (previousScissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true; frame = null;
        if (quad != null) { quad.cleanup(); quad = null; }
        if (texture != 0) { glDeleteTextures(texture); texture = 0; }
        if (rgba != null) { MemoryUtil.memFree(rgba); rgba = null; }
    }
}
