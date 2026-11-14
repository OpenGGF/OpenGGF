package com.openggf.graphics;

import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * A foreground layout-cell redraw republishes only its tilemap rows. Pixels prove the
 * span reaches the resident texture, that rows outside it are not re-sent, and that a
 * later full publication still replaces the whole texture.
 */
class TestForegroundTilemapRowUpload {
    private static final int RED = 1;
    private static final int GREEN = 2;

    @Test
    void rowSpanUploadsOnlyItsRowsIntoTheResidentTexture() throws Exception {
        PresentationOffscreenTestContext offscreen = null;
        TilemapGpuRenderer renderer = null;
        int atlas = 0;
        int palette = 0;
        try {
            offscreen = PresentationOffscreenTestContext.openConfigured(64, 64,
                    PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            glViewport(0, 0, 64, 64);
            renderer = new TilemapGpuRenderer();
            renderer.init("shaders/shader_tilemap.glsl");
            int[] plane = new int[64];
            Arrays.fill(plane, RED);
            byte[] data = TilemapGpuRenderer.packWindowDescriptors(plane);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, data, 8, 8);
            renderer.setPatternLookupData(new byte[]{0, 0, 0, -1, 1, 0, 0, -1,
                    2, 0, 0, -1, 3, 0, 0, -1}, 4);
            atlas = atlasTexture();
            palette = paletteTexture();

            draw(renderer, atlas, palette);
            for (int row = 0; row < 8; row++) assertRowColour(row, 255, 0, 0);

            // Rewrite rows 2..3 and row 5 in the resident array; publish only 2..3.
            byte[] green = TilemapGpuRenderer.packWindowDescriptors(new int[]{GREEN});
            for (int row : new int[]{2, 3, 5}) {
                for (int x = 0; x < 8; x++) System.arraycopy(green, 0, data, (row * 8 + x) * 4, 4);
            }
            renderer.setForegroundTilemapRows(data, 8, 8, 2, 2);
            assertEquals(2, renderer.foregroundPendingRowCountForDiagnostics());
            assertFalse(renderer.foregroundFullUploadPendingForDiagnostics());
            draw(renderer, atlas, palette);
            assertEquals(0, renderer.foregroundPendingRowCountForDiagnostics());
            for (int row = 0; row < 8; row++) {
                boolean published = row == 2 || row == 3;
                assertRowColour(row, published ? 0 : 255, published ? 255 : 0, 0);
            }

            // Separate spans stay separate uploads: rows 5 and 1 publish, rows 2..4 are not re-sent.
            byte[] blue = TilemapGpuRenderer.packWindowDescriptors(new int[]{3});
            for (int x = 0; x < 8; x++) System.arraycopy(blue, 0, data, (1 * 8 + x) * 4, 4);
            for (int x = 0; x < 8; x++) System.arraycopy(blue, 0, data, (4 * 8 + x) * 4, 4);
            renderer.setForegroundTilemapRows(data, 8, 8, 5, 1);
            renderer.setForegroundTilemapRows(data, 8, 8, 1, 1);
            assertEquals(1, renderer.foregroundPendingFirstRowForDiagnostics());
            assertEquals(2, renderer.foregroundPendingRowCountForDiagnostics());
            assertEquals(2, renderer.foregroundPendingSpanCountForDiagnostics());
            draw(renderer, atlas, palette);
            assertRowColour(1, 0, 0, 255);
            assertRowColour(4, 255, 0, 0);
            assertRowColour(5, 0, 255, 0);
            // Touching spans join; past the cap the two closest merge.
            renderer.setForegroundTilemapRows(data, 8, 8, 0, 1);
            renderer.setForegroundTilemapRows(data, 8, 8, 1, 1);
            assertEquals(1, renderer.foregroundPendingSpanCountForDiagnostics());
            assertEquals(2, renderer.foregroundPendingRowCountForDiagnostics());
            draw(renderer, atlas, palette);
            renderer.setForegroundTilemapRows(data.clone(), 8, 8, 0, 1);
            assertTrue(renderer.foregroundFullUploadPendingForDiagnostics());
            assertEquals(0, renderer.foregroundPendingRowCountForDiagnostics());
            draw(renderer, atlas, palette);
            for (int row = 0; row < 8; row++) {
                boolean greenRow = row == 2 || row == 3 || row == 5;
                boolean blueRow = row == 1 || row == 4;
                assertRowColour(row, greenRow || blueRow ? 0 : 255, greenRow ? 255 : 0, blueRow ? 255 : 0);
            }
            assertEquals(GL_NO_ERROR, glGetError());

            // Past the span cap the two closest spans merge, so no edit is ever dropped.
            byte[] tall = TilemapGpuRenderer.packWindowDescriptors(new int[8 * 32]);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, tall, 8, 32);
            draw(renderer, atlas, palette);
            for (int row = 0; row < 20; row += 2) renderer.setForegroundTilemapRows(tall, 8, 32, row, 1);
            assertEquals(8, renderer.foregroundPendingSpanCountForDiagnostics());
            assertEquals(12, renderer.foregroundPendingRowCountForDiagnostics(),
                    "ten one-row spans, two merges across one-row gaps");
            draw(renderer, atlas, palette);
            assertEquals(0, renderer.foregroundPendingSpanCountForDiagnostics());
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            // GL objects need the current context: release them before closing it.
            try {
                if (renderer != null) renderer.cleanup();
                if (atlas != 0) glDeleteTextures(atlas);
                if (palette != 0) glDeleteTextures(palette);
            } finally {
                if (offscreen != null) offscreen.close();
            }
        }
    }

    private static void draw(TilemapGpuRenderer renderer, int atlas, int palette) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.render(TilemapGpuRenderer.Layer.FOREGROUND, 64, 64, 0, 0, 64, 64,
                0, 0, 32, 8, atlas, palette, palette, -1, true, false, false, 64);
    }

    private static int atlasTexture() {
        byte[] pixels = new byte[32 * 8 * 4];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 32; x++) {
                pixels[(y * 32 + x) * 4] = (byte) (x / 8);
                pixels[(y * 32 + x) * 4 + 3] = -1;
            }
        }
        return texture(pixels, 32, 8);
    }

    private static int paletteTexture() {
        byte[] colors = new byte[16 * 4 * 4];
        for (int line = 0; line < 4; line++) {
            colors[(line * 16 + 1) * 4] = -1;
            colors[(line * 16 + 2) * 4 + 1] = -1;
            colors[(line * 16 + 3) * 4 + 2] = -1;
            for (int index = 0; index < 16; index++) colors[(line * 16 + index) * 4 + 3] = -1;
        }
        return texture(colors, 16, 4);
    }

    private static int texture(byte[] pixels, int width, int height) {
        glActiveTexture(GL_TEXTURE0);
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        ByteBuffer data = MemoryUtil.memAlloc(pixels.length);
        try {
            data.put(pixels).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        } finally {
            MemoryUtil.memFree(data);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    /** Samples the middle of tile row {@code row} (8 px rows, top-down). */
    private static void assertRowColour(int row, int red, int green, int blue) {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            int yFromTop = row * 8 + 4;
            glReadPixels(12, 63 - yFromTop, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertArrayEquals(new int[]{red, green, blue},
                    new int[]{pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255},
                    "tile row " + row);
        } finally {
            MemoryUtil.memFree(pixel);
        }
    }
}
