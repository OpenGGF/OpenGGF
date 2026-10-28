package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Menu typography with two native pixel sizes: one authored family in light 6x8 cells and
 * a derived bold weight in 9x10 cells, with lowercase descenders in both.
 * Menu lettering is proportional: each glyph advances by its ink width plus one spacing
 * column, digits stay tabular, and native symbols keep a whole cell. {@link #compact()}
 * keeps the tuned fixed 6x8 cells that the transport and trace overlays measure.
 * Fractional caller scales select light lettering; full sizes use integer multiples.
 * Neither path resamples glyph artwork or adds an outline or shadow.
 */
public final class MenuPixelFont extends PixelFont {
    private enum Batch { NONE, COMPACT, FULL }
    private final boolean compactOnly;

    public MenuPixelFont() { this(false); }

    private MenuPixelFont(boolean compactOnly) { this.compactOnly = compactOnly; }

    /** Fixed native 6x8 lettering; scale 1 selects authored pixels, never a resized debug font. */
    public static MenuPixelFont compact() { return new MenuPixelFont(true); }

    public int nativeLineHeight(float scale) {
        return compactOnly ? MenuGlyphs.HEIGHT : glyphLineHeight(scale);
    }

    private TexturedQuadRenderer menuRenderer;
    // The light-weight atlas: fixed cells for compact(), proportional masks otherwise.
    // Both carry the solid texel used by rectangles and symbols.
    private int compactTexture;
    private int fullTexture;
    private boolean batching;
    private Batch activeBatch = Batch.NONE;
    private float[] vertices = new float[TexturedQuadRenderer.COLORED_QUAD_FLOATS * 64];
    private int quads;

    @Override
    public void init(String fontPngPath, TexturedQuadRenderer renderer) throws IOException {
        cleanup();
        menuRenderer = Objects.requireNonNull(renderer, "renderer");
        // Keep the inherited signature for existing menu callers. Both native
        // weights come from MenuGlyphs; the gameplay PNG resource is independent.
        try {
            compactTexture = upload(MenuGlyphs.ATLAS_WIDTH, MenuGlyphs.ATLAS_HEIGHT,
                    compactOnly ? MenuGlyphs.atlasRgba() : MenuGlyphs.proportionalAtlasRgba());
            if (!compactOnly)
                fullTexture = upload(MenuGlyphs.FULL_ATLAS_WIDTH, MenuGlyphs.FULL_ATLAS_HEIGHT,
                        MenuGlyphs.fullAtlasRgba());
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    /** Cell width of the selected native weight, in projection pixels; tabular digits use it. */
    public static int glyphAdvance(float scale) {
        return scale < 1 ? MenuGlyphs.ADVANCE : MenuGlyphs.FULL_ADVANCE * integerScale(scale);
    }

    /** Actual cell height of the selected native font, in projection pixels. */
    public static int glyphLineHeight(float scale) {
        return scale < 1 ? MenuGlyphs.HEIGHT : MenuGlyphs.FULL_HEIGHT * integerScale(scale);
    }

    private static int integerScale(float scale) {
        return Math.max(1, Math.round(scale));
    }

    private static MenuGlyphs.Face face(boolean fixed, float scale) {
        if (fixed) return MenuGlyphs.Face.FIXED;
        return scale >= 1 ? MenuGlyphs.Face.BOLD : MenuGlyphs.Face.LIGHT;
    }

    private static int multiplier(MenuGlyphs.Face face, float scale) {
        return face == MenuGlyphs.Face.BOLD ? integerScale(scale) : 1;
    }

    /**
     * Width of the widest line of proportional menu lettering, in projection pixels,
     * including each glyph's trailing spacing column. Needs no GL context.
     */
    public static int textWidth(String text, float scale) {
        MenuGlyphs.Face face = face(false, scale);
        return measure(text, face, multiplier(face, scale));
    }

    /** Pen advance of one code point of proportional menu lettering, in projection pixels. */
    public static int advance(int codePoint, float scale) {
        MenuGlyphs.Face face = face(false, scale);
        return MenuGlyphs.advance(face, codePoint) * multiplier(face, scale);
    }

    private static int measure(String text, MenuGlyphs.Face face, int multiplier) {
        if (text == null) return 0;
        int widest = 0, pen = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\r' || codePoint == '\n') {
                if (codePoint == '\r' && i < text.length() && text.charAt(i) == '\n') i++;
                widest = Math.max(widest, pen);
                pen = 0;
                continue;
            }
            pen += MenuGlyphs.advance(face, codePoint) * multiplier;
        }
        return Math.max(widest, pen);
    }

    @Override
    public int measureWidth(String text, float scale) {
        MenuGlyphs.Face face = face(compactOnly, scale);
        return measure(text, face, multiplier(face, scale));
    }

    @Override
    public int measureWidth(String text) {
        return measureWidth(text, 1);
    }

    @Override
    public void drawText(String text, int x, int y, float r, float g, float b, float a) {
        drawText(text, (float) x, (float) y, 1, r, g, b, a);
    }

    @Override
    public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
        drawText(text, (float) x, (float) y, scale, r, g, b, a);
    }

    @Override
    public void drawText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        if (menuRenderer == null || text.isEmpty()) return;
        int originX = Math.round(x);
        int originY = Math.round(y);
        MenuGlyphs.Face face = face(compactOnly, scale);
        boolean full = face == MenuGlyphs.Face.BOLD;
        int cellWidth = face.cellWidth;
        int cellHeight = face.cellHeight;
        int atlasWidth = full ? MenuGlyphs.FULL_ATLAS_WIDTH : MenuGlyphs.ATLAS_WIDTH;
        int atlasHeight = full ? MenuGlyphs.FULL_ATLAS_HEIGHT : MenuGlyphs.ATLAS_HEIGHT;
        int multiplier = multiplier(face, scale);
        selectBatch(full ? Batch.FULL : Batch.COMPACT);
        // Drawing and measure() share the same code-point walk and advances.
        int pen = 0, line = 0;
        for (int i = 0; i < text.length(); ) {
            int source = text.codePointAt(i);
            i += Character.charCount(source);
            if (source == '\r' || source == '\n') {
                if (source == '\r' && i < text.length() && text.charAt(i) == '\n') i++;
                pen = 0; line++; continue;
            }
            int glyphX = originX + pen;
            pen += MenuGlyphs.advance(face, source) * multiplier;
            if (MenuGlyphs.symbol(source)) {
                drawSymbol((char) source, glyphX, originY + line * cellHeight * multiplier,
                        cellWidth, cellHeight, multiplier, r, g, b, a);
                selectBatch(full ? Batch.FULL : Batch.COMPACT);
                continue;
            }
            char character = MenuGlyphs.normalized(source);
            int inkWidth = MenuGlyphs.inkWidth(face, character);
            if (inkWidth == 0) continue;
            int index = character - 32;
            int sourceX = (index % MenuGlyphs.COLUMNS) * cellWidth + MenuGlyphs.inkLeft(face, character);
            int sourceY = (index / MenuGlyphs.COLUMNS) * cellHeight;
            ensureCapacity(quads + 1);
            TexturedQuadRenderer.writeColoredQuadVerticesAtOffset(vertices,
                    quads * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                    glyphX, 224 - originY - (line + 1) * cellHeight * multiplier,
                    inkWidth * multiplier, cellHeight * multiplier,
                    (float) sourceX / atlasWidth,
                    1f - (float) (sourceY + cellHeight) / atlasHeight,
                    (float) (sourceX + inkWidth) / atlasWidth,
                    1f - (float) sourceY / atlasHeight,
                    r, g, b, a);
            quads++;
        }
        if (!batching) flushActiveBatch();
    }

    /** Native arrow heads and stems; never route control icons through ASCII substitutions. */
    private void drawSymbol(char arrow, int x, int y, int cellWidth, int cellHeight, int scale,
                           float r, float g, float b, float a) {
        int[] rows = switch (arrow) {
            case '\u2190' -> new int[]{0, 4, 8, 31, 8, 4, 0};
            case '\u2191' -> new int[]{4, 10, 21, 4, 4, 4, 4};
            case '\u2192' -> new int[]{0, 4, 2, 31, 2, 4, 0};
            case '\u2193' -> new int[]{4, 4, 4, 4, 21, 10, 4};
            case '✓' -> new int[]{0, 1, 2, 20, 8, 0, 0};
            case '✗' -> new int[]{0, 17, 10, 4, 10, 17, 0};
            case '·' -> new int[]{0, 0, 0, 4, 0, 0, 0};
            case '•' -> new int[]{0, 0, 14, 14, 14, 0, 0};
            default -> throw new IllegalArgumentException("Unsupported menu symbol");
        };
        int left = x + (cellWidth - 5) * scale / 2;
        int top = y + (cellHeight - 7) * scale / 2;
        for (int row = 0; row < rows.length; row++) {
            int bits=rows[row];
            for (int column=0;column<5;) {
                if ((bits & (1 << (4-column)))==0) { column++; continue; }
                int first=column++; while(column<5 && (bits & (1 << (4-column)))!=0) column++;
                fillRect(left+first*scale,top+row*scale,(column-first)*scale,scale,r,g,b,a);
            }
        }
    }

    @Override
    public void beginMegaBatch() {
        // Flush rather than discard if callers open an adjacent batch accidentally.
        flushActiveBatch();
        batching = true;
    }

    @Override
    public void endMegaBatch() {
        flushActiveBatch();
        batching = false;
    }

    /** Draw a solid rectangle in the same top-left coordinate system as text. */
    public void fillRect(int x, int y, int width, int height, float r, float g, float b, float a) {
        if (menuRenderer == null || width <= 0 || height <= 0) return;
        // Sampling the centre of the atlas's white texel lets shapes and compact
        // text share one ordered submission, without texture switches or glyph bleed.
        selectBatch(Batch.COMPACT);
        ensureCapacity(quads + 1);
        TexturedQuadRenderer.writeColoredQuadVerticesAtOffset(vertices,
                quads++ * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                x, 224 - y - height, width, height,
                MenuGlyphs.SOLID_U, MenuGlyphs.SOLID_V, MenuGlyphs.SOLID_U, MenuGlyphs.SOLID_V,
                r, g, b, a);
        if (!batching) flushActiveBatch();
    }

    // Reused native-grid geometry: the static checkerboard needs one submission,
    // regardless of the number of checks or the current text batching mode.
    private record CheckerGeometry(int start, int width, float[] vertices, int quads) { }
    private final CheckerGeometry[] checkerCache = new CheckerGeometry[4];
    private int nextCheckerSlot;

    public void drawCheckerboard(int startX, int width) {
        if (menuRenderer == null || width <= startX) return;
        selectBatch(Batch.COMPACT);
        CheckerGeometry geometry = null;
        for (CheckerGeometry cached : checkerCache) {
            if (cached != null && cached.start() == startX && cached.width() == width) { geometry = cached; break; }
        }
        if (geometry == null) {
            int checkerQuads = 0;
            int capacity = 11 * ((width - startX + 31) / 32 + 1);
            float[] checkerVertices = new float[capacity * TexturedQuadRenderer.COLORED_QUAD_FLOATS];
            for (int y = 29, row = 0; y < 198; y += 16, row++) {
                for (int x = startX + (row & 1) * 16; x < width; x += 32) {
                    int height = Math.min(15, 198 - y);
                    TexturedQuadRenderer.writeColoredQuadVerticesAtOffset(checkerVertices,
                            checkerQuads++ * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                            x, 224 - y - height, Math.min(15, width - x), height,
                            MenuGlyphs.SOLID_U, MenuGlyphs.SOLID_V, MenuGlyphs.SOLID_U, MenuGlyphs.SOLID_V,
                            .05f, .115f, .28f, 1);
                }
            }
            geometry = new CheckerGeometry(startX, width, checkerVertices, checkerQuads);
            checkerCache[nextCheckerSlot] = geometry;
            nextCheckerSlot = (nextCheckerSlot + 1) % checkerCache.length;
        }
        ensureCapacity(quads + geometry.quads());
        System.arraycopy(geometry.vertices(), 0, vertices,
                quads * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                geometry.quads() * TexturedQuadRenderer.COLORED_QUAD_FLOATS);
        quads += geometry.quads();
        if (!batching) flushActiveBatch();
    }

    private void selectBatch(Batch next) {
        if (activeBatch == next) return;
        flushActiveBatch();
        activeBatch = next;
    }

    private void flushActiveBatch() {
        if (quads != 0) {
            menuRenderer.drawColoredTextureBatch(activeBatch == Batch.FULL ? fullTexture : compactTexture,
                    vertices, quads);
            quads = 0;
        }
        activeBatch = Batch.NONE;
    }

    private void ensureCapacity(int quadCount) {
        int required = quadCount * TexturedQuadRenderer.COLORED_QUAD_FLOATS;
        if (required > vertices.length)
            vertices = Arrays.copyOf(vertices, Math.max(required, vertices.length * 2));
    }

    @Override
    public void cleanup() {
        // Cleanup deliberately drops queued text; it must not draw while tearing down GL.
        batching = false;
        activeBatch = Batch.NONE;
        quads = 0;
        PngTextureLoader.deleteTexture(compactTexture);
        PngTextureLoader.deleteTexture(fullTexture);
        compactTexture = 0;
        fullTexture = 0;
        menuRenderer = null;
    }

    private static int upload(int width, int height, byte[] rgba) {
        ByteBuffer pixels = MemoryUtil.memAlloc(rgba.length);
        int texture = 0;
        try {
            pixels.put(rgba).flip();
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return texture;
        } catch (RuntimeException | Error failure) {
            PngTextureLoader.deleteTexture(texture);
            throw failure;
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
            MemoryUtil.memFree(pixels);
        }
    }
}
