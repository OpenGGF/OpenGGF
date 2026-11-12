package com.openggf.game.competition;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.PaletteView;
import com.openggf.util.FboHelper;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.Objects;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.*;

/**
 * Presents a composed {@link NativeMenuRaster.Picture}: CRAM fade and display
 * conversion, then one nearest-neighbour blit into the current viewport.
 */
public final class NativeMenuRasterRenderer implements AutoCloseable {
    private final GraphicsManager graphics;
    private FboHelper.FboHandle image;
    private ByteBuffer rgba;
    private boolean drawing;
    private boolean closed;

    public NativeMenuRasterRenderer(GraphicsManager graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    public void draw(NativeMenuRaster.Picture picture, PaletteView[] palettes) {
        if (closed) throw new IllegalStateException("Native menu renderer has closed");
        if (!graphics.isGlInitialized()) throw new IllegalStateException("Native menu needs an owned GL context");
        int[] pixels = picture.rgb(palettes, PaletteUploadPresentation.displayedFade(graphics),
                graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
        graphics.flushPatternBatch();
        graphics.registerCommand((cameraX, cameraY, width, height) -> drawPixels(pixels));
    }

    private void drawPixels(int[] pixels) {
        if (closed) return; // A command queued before close cannot recreate retired GL resources.
        if (drawing) throw new IllegalStateException("Nested native menu draw");
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int texture = glGetInteger(GL_TEXTURE_BINDING_2D), unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_UNPACK_ALIGNMENT), rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS), skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        int[] viewport = new int[4], scissor = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport); glGetIntegerv(GL_SCISSOR_BOX, scissor);
        boolean scissorOn = glIsEnabled(GL_SCISSOR_TEST);
        drawing = true;
        try {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1); glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0); glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            if (image == null) {
                image = FboHelper.createColorOnly(NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT, GL_CLAMP_TO_EDGE);
                if (image == null) throw new IllegalStateException("Cannot create native menu image");
                rgba = MemoryUtil.memAlloc(NativeMenuRaster.WIDTH * NativeMenuRaster.HEIGHT * 4);
            }
            rgba.clear();
            for (int y = NativeMenuRaster.HEIGHT - 1; y >= 0; y--) for (int x = 0; x < NativeMenuRaster.WIDTH; x++) {
                int rgb = pixels[y * NativeMenuRaster.WIDTH + x];
                rgba.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb).put((byte) 255);
            }
            rgba.flip();
            glBindTexture(GL_TEXTURE_2D, image.textureId());
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT,
                    GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, image.fboId());
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glBlitFramebuffer(0, 0, NativeMenuRaster.WIDTH, NativeMenuRaster.HEIGHT,
                    viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3],
                    GL_COLOR_BUFFER_BIT, GL_NEAREST);
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glBindTexture(GL_TEXTURE_2D, texture); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
            glPixelStorei(GL_UNPACK_ALIGNMENT, alignment); glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows); glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            if (scissorOn) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            drawing = false;
        }
    }

    @Override public void close() {
        if (drawing) throw new IllegalStateException("Cannot close an active native menu renderer");
        if (closed) return;
        closed = true;
        FboHelper.destroy(image); image = null;
        if (rgba != null) { MemoryUtil.memFree(rgba); rgba = null; }
    }
}
