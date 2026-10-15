package com.openggf.editor.hud;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * RGBA thumbnails of the loaded level library for the editor HUD: every block, chunk and
 * pattern decoded on the CPU from the same descriptors, patterns and palettes the renderer
 * uses, then uploaded as three GL textures. Index 0 is transparent so the panel chooses the
 * backdrop. Rebuilt lazily when a cheap content fingerprint changes.
 */
final class EditorTileAtlas implements AutoCloseable {
    enum Kind { BLOCK, CHUNK, PATTERN }

    record Cell(int texture, float u0, float v0, float u1, float v1) {
    }

    private static final int CHUNK_PX = 16;
    private static final int PATTERN_PX = 8;
    private static final int MAX_BLOCK_THUMB = 128;

    private final Sheet blocks = new Sheet();
    private final Sheet chunks = new Sheet();
    private final Sheet patterns = new Sheet();
    private Level level;
    private long fingerprint = Long.MIN_VALUE;
    private int patternPaletteLine = -1;
    private int framesUntilCheck;

    /** Rebuilds textures when the level or its content changed. Requires a current GL context. */
    void refresh(Level current, int paletteLineForPatterns) {
        if (current == null) return;
        boolean levelChanged = current != level;
        if (!levelChanged && paletteLineForPatterns == patternPaletteLine && framesUntilCheck-- > 0) return;
        framesUntilCheck = 20;
        long latest = fingerprint(current);
        if (!levelChanged && latest == fingerprint && paletteLineForPatterns == patternPaletteLine) return;
        level = current;
        fingerprint = latest;
        patternPaletteLine = paletteLineForPatterns;
        rebuild(current, paletteLineForPatterns);
    }

    /** Compare the content fingerprint on the next refresh, e.g. straight after an edit. */
    void requestCheck() { framesUntilCheck = 0; }

    int count(Kind kind) { return sheet(kind).count; }

    /** Pixel size of one cell in its sheet (blocks may be downsampled). */
    int cellPixels(Kind kind) { return sheet(kind).cell; }

    Cell cell(Kind kind, int index) {
        Sheet sheet = sheet(kind);
        if (sheet.texture == 0 || index < 0 || index >= sheet.count) return null;
        int column = index % sheet.columns, row = index / sheet.columns;
        float u0 = column * sheet.cell / (float) sheet.width, v0 = row * sheet.cell / (float) sheet.height;
        return new Cell(sheet.texture, u0, v0, u0 + sheet.cell / (float) sheet.width, v0 + sheet.cell / (float) sheet.height);
    }

    private Sheet sheet(Kind kind) {
        return switch (kind) {
            case BLOCK -> blocks;
            case CHUNK -> chunks;
            case PATTERN -> patterns;
        };
    }

    private void rebuild(Level source, int patternLine) {
        int[][] palettes = palettes(source);
        int chunkCount = source.getChunkCount();
        int[][] chunkPixels = new int[chunkCount][];
        for (int i = 0; i < chunkCount; i++) chunkPixels[i] = chunkPixels(source, source.getChunk(i), palettes);

        chunks.upload(chunkCount, CHUNK_PX, 64, (index, x, y) -> chunkPixels[index][y * CHUNK_PX + x]);

        int blockCount = source.getBlockCount();
        int side = blockCount > 0 ? source.getBlock(0).getGridSide() : 8;
        int blockPx = side * CHUNK_PX;
        int thumb = Math.min(blockPx, MAX_BLOCK_THUMB);
        int step = Math.max(1, blockPx / thumb);
        blocks.upload(blockCount, thumb, Math.max(1, 2048 / thumb), (index, x, y) -> {
            Block block = source.getBlock(index);
            int bx = x * step, by = y * step;
            int gs = block.getGridSide();
            int cx = bx / CHUNK_PX, cy = by / CHUNK_PX;
            if (cx >= gs || cy >= gs) return 0;
            ChunkDesc desc = block.getChunkDesc(cx, cy);
            int chunkIndex = desc.getChunkIndex();
            if (chunkIndex < 0 || chunkIndex >= chunkCount) return 0;
            int lx = bx % CHUNK_PX, ly = by % CHUNK_PX;
            if (desc.getHFlip()) lx = CHUNK_PX - 1 - lx;
            if (desc.getVFlip()) ly = CHUNK_PX - 1 - ly;
            return chunkPixels[chunkIndex][ly * CHUNK_PX + lx];
        });

        int line = Math.floorMod(patternLine, Math.max(1, palettes.length));
        patterns.upload(Math.min(source.getPatternCount(), 0x800), PATTERN_PX, 64, (index, x, y) -> {
            int value = source.getPattern(index).getPixel(x, y) & 0x0F;
            return value == 0 ? 0 : palettes[line][value];
        });
    }

    private static int[] chunkPixels(Level source, Chunk chunk, int[][] palettes) {
        int[] pixels = new int[CHUNK_PX * CHUNK_PX];
        int patternCount = source.getPatternCount();
        for (int py = 0; py < 2; py++) {
            for (int px = 0; px < 2; px++) {
                PatternDesc desc = chunk.getPatternDesc(px, py);
                int patternIndex = desc.getPatternIndex();
                if (patternIndex < 0 || patternIndex >= patternCount) continue;
                Pattern pattern = source.getPattern(patternIndex);
                int[] palette = palettes[Math.floorMod(desc.getPaletteIndex(), palettes.length)];
                for (int y = 0; y < PATTERN_PX; y++) {
                    for (int x = 0; x < PATTERN_PX; x++) {
                        int sx = desc.getHFlip() ? PATTERN_PX - 1 - x : x;
                        int sy = desc.getVFlip() ? PATTERN_PX - 1 - y : y;
                        int value = pattern.getPixel(sx, sy) & 0x0F;
                        if (value != 0) pixels[(py * PATTERN_PX + y) * CHUNK_PX + px * PATTERN_PX + x] = palette[value];
                    }
                }
            }
        }
        return pixels;
    }

    /** ABGR-packed (little-endian RGBA bytes) palette lines with an opaque alpha. */
    private static int[][] palettes(Level source) {
        int lines = Math.max(1, Math.min(4, source.getPaletteCount()));
        int[][] result = new int[lines][16];
        for (int line = 0; line < lines; line++) {
            Palette palette = source.getPalette(line);
            if (palette == null) continue;
            for (int i = 0; i < Math.min(16, palette.getColorCount()); i++) {
                Palette.Color color = palette.getColor(i);
                result[line][i] = 0xFF000000 | (color.b & 255) << 16 | (color.g & 255) << 8 | (color.r & 255);
            }
        }
        return result;
    }

    /** Palette colour for UI swatches, packed as 0xRRGGBB. */
    static int paletteColor(Level source, int line, int index) {
        if (source == null || line < 0 || line >= source.getPaletteCount()) return 0;
        Palette palette = source.getPalette(line);
        if (palette == null || index < 0 || index >= palette.getColorCount()) return 0;
        Palette.Color color = palette.getColor(index);
        return (color.r & 255) << 16 | (color.g & 255) << 8 | (color.b & 255);
    }

    private static long fingerprint(Level source) {
        long hash = 1469598103934665603L;
        hash = mix(hash, source.getBlockCount());
        hash = mix(hash, source.getChunkCount());
        hash = mix(hash, source.getPatternCount());
        for (int i = 0; i < source.getBlockCount(); i++) {
            Block block = source.getBlock(i);
            int side = block.getGridSide();
            for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) hash = mix(hash, block.getChunkDesc(x, y).get());
        }
        for (int i = 0; i < source.getChunkCount(); i++) {
            Chunk chunk = source.getChunk(i);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) hash = mix(hash, chunk.getPatternDesc(x, y).get());
        }
        for (int i = 0; i < Math.min(source.getPatternCount(), 0x800); i++) {
            Pattern pattern = source.getPattern(i);
            for (int y = 0; y < PATTERN_PX; y++) {
                long row = 0;
                for (int x = 0; x < PATTERN_PX; x++) row = row << 4 | (pattern.getPixel(x, y) & 0x0F);
                hash = mix(hash, row);
            }
        }
        for (int line = 0; line < Math.min(4, source.getPaletteCount()); line++) {
            for (int i = 0; i < 16; i++) hash = mix(hash, paletteColor(source, line, i));
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }

    @Override
    public void close() {
        blocks.delete();
        chunks.delete();
        patterns.delete();
        level = null;
    }

    @FunctionalInterface
    private interface PixelSource {
        int pixel(int index, int x, int y);
    }

    private static final class Sheet {
        int texture;
        int count;
        int cell;
        int columns;
        int width;
        int height;

        void upload(int itemCount, int cellPx, int maxColumns, PixelSource source) {
            count = itemCount;
            cell = cellPx;
            columns = Math.max(1, Math.min(maxColumns, itemCount));
            int rows = Math.max(1, (itemCount + columns - 1) / columns);
            width = columns * cellPx;
            height = rows * cellPx;
            ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
            try {
                var ints = pixels.asIntBuffer();
                for (int i = 0; i < width * height; i++) ints.put(i, 0);
                for (int index = 0; index < itemCount; index++) {
                    int ox = (index % columns) * cellPx, oy = (index / columns) * cellPx;
                    for (int y = 0; y < cellPx; y++) {
                        for (int x = 0; x < cellPx; x++) ints.put((oy + y) * width + ox + x, source.pixel(index, x, y));
                    }
                }
                if (texture == 0) texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glBindTexture(GL_TEXTURE_2D, 0);
            } finally {
                MemoryUtil.memFree(pixels);
            }
        }

        void delete() {
            if (texture != 0) glDeleteTextures(texture);
            texture = 0;
            count = 0;
        }
    }
}
