package com.openggf.graphics;

import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * A foreground image still being drawn registers a {@link ForegroundCoverage} owner; every
 * foreground draw asks it for the world X range it can sample before the texture is
 * uploaded. The stub owner here draws red only into the columns it is asked for, so a
 * black (undrawn) pixel anywhere on screen means a draw sampled a column nobody drew.
 */
class TestForegroundCoverageDraw {
    private static final int RED = 1;
    private static final int WIDTH_TILES = 64;  // 512 px image, wider than the 64 px picture
    private static final int HEIGHT_TILES = 8;

    /** Draws requested columns red into the resident array, as the level owner does. */
    private static final class Owner implements ForegroundCoverage {
        final TilemapGpuRenderer renderer;
        final byte[] data;
        final List<int[]> requests = new ArrayList<>();
        int coverAllCalls;

        Owner(TilemapGpuRenderer renderer, byte[] data) {
            this.renderer = renderer;
            this.data = data;
        }

        @Override
        public void coverColumns(byte[] array, int firstWorldX, int lastWorldX) {
            assertSame(data, array);
            requests.add(new int[]{firstWorldX, lastWorldX});
            for (int x = firstWorldX; x <= lastWorldX; x += 8) {
                drawColumn(Math.floorMod(x, WIDTH_TILES * 8) / 8);
            }
            drawColumn(Math.floorMod(lastWorldX, WIDTH_TILES * 8) / 8);
        }

        @Override
        public void coverAll(byte[] array) {
            assertSame(data, array);
            coverAllCalls++;
            for (int column = 0; column < WIDTH_TILES; column++) drawColumn(column);
        }

        private void drawColumn(int column) {
            byte[] red = TilemapGpuRenderer.packWindowDescriptors(new int[]{RED});
            for (int row = 0; row < HEIGHT_TILES; row++) {
                System.arraycopy(red, 0, data, (row * WIDTH_TILES + column) * 4, 4);
            }
            renderer.setForegroundTilemapColumns(data, WIDTH_TILES, HEIGHT_TILES, column, 1);
        }

        boolean drawn(int column) {
            return data[column * 4 + 3] != 0;
        }
    }

    @Test
    void foregroundDrawsAskTheOwnerForEverySampledColumnBeforeUpload() throws Exception {
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
            byte[] data = new byte[WIDTH_TILES * HEIGHT_TILES * 4];  // undrawn: transparent
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, data, WIDTH_TILES, HEIGHT_TILES);
            renderer.setPatternLookupData(new byte[]{0, 0, 0, -1, 1, 0, 0, -1}, 2);
            atlas = atlasTexture();
            palette = paletteTexture();
            Owner owner = new Owner(renderer, data);
            renderer.setForegroundCoverage(data, owner);

            // Load frame: the first draw uploads the whole (mostly undrawn) image.
            draw(renderer, atlas, palette, 100);
            assertScreenRed("first draw at X100");
            assertEquals(1, owner.requests.size());
            assertTrue(owner.requests.get(0)[0] <= 100 && owner.requests.get(0)[1] >= 163);
            assertFalse(owner.drawn(40), "a column no draw sampled stays undrawn");

            // Later frames: columns drawn by the owner reach the resident texture as column spans.
            draw(renderer, atlas, palette, 300);
            assertScreenRed("step to X300 (column upload)");
            draw(renderer, atlas, palette, 480);
            assertScreenRed("window over the right edge (the image repeats)");

            // Per-line scroll samples far from the uniform offset: lines at -150 and -20.
            int[] packed = new int[64];
            for (int line = 0; line < 64; line++) packed[line] = (line < 32 ? -150 : -20) << 16;
            renderer.enablePerLineForegroundScroll(packed);
            draw(renderer, atlas, palette, 0);
            assertScreenRed("per-line scroll");
            int[] last = owner.requests.get(owner.requests.size() - 1);
            assertTrue(last[0] <= 20 && last[1] >= 150 + 63, "per-line range " + last[0] + ".." + last[1]);

            // Per-line scroll from a texture this renderer cannot read covers everything.
            renderer.enablePerLineScroll(0, 64, 0, 0, 0);
            draw(renderer, atlas, palette, 0);
            assertEquals(1, owner.coverAllCalls);

            // A directed view publishes its own private foreground array; the native owner
            // must come back with the native array afterwards.
            try (var residency = new CinematicBackgroundResidency()) {
                TilemapGpuRenderer directed = renderer;
                renderer.withCinematicBackground(residency, () -> directed.setTilemapData(
                        TilemapGpuRenderer.Layer.FOREGROUND, new byte[data.length], WIDTH_TILES, HEIGHT_TILES));
            }
            assertSame(owner, renderer.foregroundCoverageForDiagnostics(),
                    "the native coverage owner survives a directed draw");

            // Retired owner: no more requests.
            int requests = owner.requests.size();
            renderer.setForegroundCoverage(data, null);
            draw(renderer, atlas, palette, 200);
            assertEquals(requests, owner.requests.size());

            // Another array replaces the registration.
            renderer.setForegroundCoverage(data, owner);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, data.clone(), WIDTH_TILES, HEIGHT_TILES);
            assertNull(renderer.foregroundCoverageForDiagnostics());
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            try {
                if (renderer != null) renderer.cleanup();
                if (atlas != 0) glDeleteTextures(atlas);
                if (palette != 0) glDeleteTextures(palette);
            } finally {
                if (offscreen != null) offscreen.close();
            }
        }
    }

    private static void draw(TilemapGpuRenderer renderer, int atlas, int palette, int worldX) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.render(TilemapGpuRenderer.Layer.FOREGROUND, 64, 64, 0, 0, 64, 64,
                worldX, 0, 16, 8, atlas, palette, palette, -1, true, false, false, 64);
    }

    private static void assertScreenRed(String what) {
        ByteBuffer pixels = MemoryUtil.memAlloc(64 * 64 * 4);
        try {
            glReadPixels(0, 0, 64, 64, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int offset = (y * 64 + x) * 4;
                    if ((pixels.get(offset) & 255) != 255) {
                        fail(what + ": pixel (" + x + ", " + (63 - y) + ") came from an undrawn column");
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static int atlasTexture() {
        byte[] pixels = new byte[16 * 8 * 4];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[(y * 16 + x) * 4] = (byte) (x / 8);
                pixels[(y * 16 + x) * 4 + 3] = -1;
            }
        }
        return texture(pixels, 16, 8);
    }

    private static int paletteTexture() {
        byte[] colors = new byte[16 * 4 * 4];
        for (int line = 0; line < 4; line++) {
            colors[(line * 16 + 1) * 4] = -1;
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
}
