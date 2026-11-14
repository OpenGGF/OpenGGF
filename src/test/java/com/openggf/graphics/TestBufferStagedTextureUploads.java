package com.openggf.graphics;

import com.openggf.graphics.PatternAtlas.Entry;
import com.openggf.level.Pattern;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Random;

import static com.openggf.tools.PresentationOffscreenTestContext.LegacyProfile.CORE_41;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RED;

/**
 * Real-GL readback of the buffer-staged texture uploads: every atlas flush shape
 * (full page, exact runs, dirty rectangle, immediate tile) and every tilemap
 * upload shape must leave the texture byte-identical to its CPU source, and leave
 * no pixel-unpack buffer bound for the engine's client-memory uploads.
 * Run with {@code -Dopenggf.headless.gl=surfaceless-egl}.
 */
class TestBufferStagedTextureUploads {
    private static final int TILE = PatternAtlas.TILE_SIZE;

    @Test
    void atlasFlushShapesMatchTheCpuSource() {
        try (var context = PresentationOffscreenTestContext.openConfigured(16, 16, CORE_41)) {
            int size = 256;
            PatternAtlas atlas = new PatternAtlas(size, size);
            byte[] expected = new byte[size * size];
            try {
                atlas.init();
                // First flush after init: the fresh page is fully dirty (whole-page rectangle).
                atlas.beginBatch();
                for (int id = 0; id < 40; id++) {
                    write(atlas, expected, size, id, id + 1);
                }
                atlas.endBatch();
                assertTexture(atlas.getTextureId(0), GL_RED, expected, size, size, 1);

                // Exact runs: adjacent and isolated tiles.
                atlas.beginBatch();
                for (int id : new int[]{3, 4, 5, 17, 30, 31}) {
                    write(atlas, expected, size, id, id + 100);
                }
                atlas.endBatch();
                assertTexture(atlas.getTextureId(0), GL_RED, expected, size, size, 1);

                // Past the exact-tracking limit: dirty bounding rectangle.
                atlas.beginBatch();
                for (int id = 100; id < 100 + PatternAtlas.DIRTY_SLOT_TRACK_LIMIT + 50; id++) {
                    write(atlas, expected, size, id, id * 3);
                }
                atlas.endBatch();
                assertTexture(atlas.getTextureId(0), GL_RED, expected, size, size, 1);

                // Immediate (non-batch) single-tile writes, one staged region each.
                for (int round = 0; round < 3; round++) {
                    for (int id = 0; id < 40; id++) {
                        write(atlas, expected, size, id, id * 7 + round);
                    }
                }
                assertTexture(atlas.getTextureId(0), GL_RED, expected, size, size, 1);
                assertEquals(0, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                atlas.cleanup();
            }
        }
    }

    @Test
    void tilemapUploadShapesMatchTheCpuSource() {
        try (var context = PresentationOffscreenTestContext.openConfigured(16, 16, CORE_41)) {
            int width = 40;
            int height = 24;
            byte[] data = new byte[width * height * 4];
            Random random = new Random(11);
            random.nextBytes(data);
            byte[] expected = data.clone();
            TilemapTexture texture = new TilemapTexture();
            try {
                texture.upload(data, width, height);
                assertTexture(texture.getTextureId(), GL_RGBA, expected, width, height, 4);

                random.nextBytes(data);
                assertTrue(texture.uploadRows(data, width, height, 3, 3, 4));
                System.arraycopy(data, 3 * width * 4, expected, 3 * width * 4, 4 * width * 4);
                assertTexture(texture.getTextureId(), GL_RGBA, expected, width, height, 4);

                // A logical row range can land in another physical row range.
                assertTrue(texture.uploadRows(data, width, height, 20, 0, 2));
                System.arraycopy(data, 20 * width * 4, expected, 0, 2 * width * 4);
                assertTexture(texture.getTextureId(), GL_RGBA, expected, width, height, 4);

                random.nextBytes(data);
                assertTrue(texture.uploadColumns(data, width, height, 7, 30, 3));
                for (int row = 0; row < height; row++) {
                    System.arraycopy(data, (row * width + 7) * 4, expected, (row * width + 30) * 4, 3 * 4);
                }
                assertTexture(texture.getTextureId(), GL_RGBA, expected, width, height, 4);

                random.nextBytes(data);
                texture.upload(data, width, height);
                assertTexture(texture.getTextureId(), GL_RGBA, data, width, height, 4);
                assertEquals(0, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                texture.cleanup();
            }
        }
    }

    @Test
    void streamOrphansWhenFullAndGrowsForLargeUploads() {
        try (var context = PresentationOffscreenTestContext.openConfigured(16, 16, CORE_41)) {
            int width = 1000;
            int texture = glGenTextures();
            PixelUnpackStream stream = new PixelUnpackStream(512);
            ByteBuffer staging = MemoryUtil.memAlloc(width);
            byte[] expected = new byte[width];
            try {
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, 1, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                glBindTexture(GL_TEXTURE_2D, 0);
                // 300 bytes, then 300 more (does not fit after alignment: orphan), then 1000 (grow).
                int[][] uploads = {{0, 300}, {300, 300}, {0, 1000}, {600, 400}};
                for (int[] upload : uploads) {
                    int x = upload[0];
                    int length = upload[1];
                    staging.clear();
                    for (int i = 0; i < length; i++) {
                        expected[x + i] = (byte) (x * 31 + i * 7 + length);
                        staging.put(expected[x + i]);
                    }
                    staging.flip();
                    long offset = stream.stage(staging);
                    assertEquals(0, offset % PixelUnpackStream.OFFSET_ALIGNMENT);
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glTexSubImage2D(GL_TEXTURE_2D, 0, x, 0, length, 1, GL_RED, GL_UNSIGNED_BYTE, offset);
                    glBindTexture(GL_TEXTURE_2D, 0);
                    PixelUnpackStream.unbind();
                    assertTexture(texture, GL_RED, expected, width, 1, 1);
                }
                assertEquals(1024, stream.capacity(), "grown to the aligned largest upload");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                MemoryUtil.memFree(staging);
                stream.delete();
                glDeleteTextures(texture);
            }
        }
    }

    private static void write(PatternAtlas atlas, byte[] expected, int pageWidth, int id, int seed) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                pattern.setPixel(x, y, (byte) ((seed + x * 5 + y * 11) & 0x7F));
            }
        }
        Entry entry = atlas.cachePattern(pattern, id);
        assertNotNull(entry);
        assertEquals(0, entry.atlasIndex(), "fixture expects a single page");
        byte[] tile = new byte[TILE * TILE];
        pattern.copyInto(tile, 0);
        for (int row = 0; row < TILE; row++) {
            System.arraycopy(tile, row * TILE, expected,
                    (entry.tileY() * TILE + row) * pageWidth + entry.tileX() * TILE, TILE);
        }
    }

    private static void assertTexture(int texture, int format, byte[] expected,
            int width, int height, int bytesPerTexel) {
        ByteBuffer readback = MemoryUtil.memAlloc(width * height * bytesPerTexel);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glBindTexture(GL_TEXTURE_2D, texture);
            glGetTexImage(GL_TEXTURE_2D, 0, format, GL_UNSIGNED_BYTE, readback);
            glBindTexture(GL_TEXTURE_2D, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 4);
            byte[] actual = new byte[expected.length];
            readback.get(actual);
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != actual[i]) {
                    int texel = i / bytesPerTexel;
                    fail("texel (" + texel % width + "," + texel / width + ") byte " + i % bytesPerTexel
                            + ": expected " + expected[i] + " but was " + actual[i]);
                }
            }
        } finally {
            MemoryUtil.memFree(readback);
        }
    }
}
