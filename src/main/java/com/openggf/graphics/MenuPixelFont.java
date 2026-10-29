package com.openggf.graphics;

import com.openggf.graphics.text.GlyphBitmap;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.LineMetrics;
import com.openggf.graphics.text.PlacedGlyph;
import com.openggf.graphics.text.ShapedLine;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Menu typography with two native pixel sizes: one authored proportional family in light 6x8 cells and
 * a derived bold weight in 9x10 cells, with lowercase descenders in both.
 * Fractional caller scales select light lettering; full sizes use integer multiples.
 * Neither path resamples glyph artwork or adds an outline or shadow.
 *
 * <p>Text is laid out by {@link HostText}: measurement and drawing use the same shaped
 * line, in this font's {@link #language()}. Native lettering comes from two static atlases;
 * fallback bitmap fonts and HarfBuzz-shaped OpenType glyphs are rasterized into
 * {@link GlyphAtlas} pages on demand. A taller script keeps the caller's cell baseline and
 * overhangs the cell; {@link #lineHeight(float)} reports the pitch that stacks it safely.
 */
public final class MenuPixelFont extends PixelFont {
    private final boolean compactOnly;

    public MenuPixelFont() { this(false); }

    private MenuPixelFont(boolean compactOnly) { this.compactOnly = compactOnly; }

    /** Fixed native 6x8 lettering; scale 1 selects authored pixels, never a resized debug font. */
    public static MenuPixelFont compact() { return new MenuPixelFont(true); }

    public int nativeLineHeight(float scale) {
        return compactOnly ? MenuGlyphs.HEIGHT : glyphLineHeight(scale);
    }

    private TexturedQuadRenderer menuRenderer;
    private int compactTexture;
    private int fullTexture;
    private GlyphAtlas glyphAtlas;
    private TextLanguage language;
    private boolean batching;
    private int activeTexture;
    private float[] vertices = new float[TexturedQuadRenderer.COLORED_QUAD_FLOATS * 64];
    private int quads;
    private int clipLeft = Integer.MIN_VALUE;
    private int clipRight = Integer.MAX_VALUE;

    @Override
    public void init(String fontPngPath, TexturedQuadRenderer renderer) throws IOException {
        cleanup();
        menuRenderer = Objects.requireNonNull(renderer, "renderer");
        // Keep the inherited signature for existing menu callers. Both native
        // weights come from MenuGlyphs; the gameplay PNG resource is independent.
        try {
            compactTexture = upload(MenuGlyphs.ATLAS_WIDTH, MenuGlyphs.ATLAS_HEIGHT,
                    compactOnly ? MenuGlyphs.atlasRgba() : MenuGlyphs.proportionalAtlasRgba());
            if (!compactOnly) fullTexture = upload(MenuGlyphs.FULL_ATLAS_WIDTH, MenuGlyphs.FULL_ATLAS_HEIGHT,
                    MenuGlyphs.fullAtlasRgba());
            glyphAtlas = new GlyphAtlas(GlyphAtlas.GL);
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    /**
     * Pins this font to {@code language}; {@code null} follows {@link HostTextContext}.
     * A pinned font suits text shown in its own language, such as a language picker.
     */
    public void setLanguage(TextLanguage language) {
        this.language = language;
    }

    /** The language every measurement and draw without an explicit language uses. */
    public TextLanguage language() {
        return language != null ? language : HostTextContext.current();
    }

    /** Host text role for a caller scale: fractional scales select the light compact face. */
    public static TextFace face(float scale) {
        return scale < 1 ? TextFace.COMPACT : TextFace.LABEL;
    }

    /** Integer pixel multiplier applied to the selected face. */
    public static int pixelScale(float scale) {
        return scale < 1 ? 1 : integerScale(scale);
    }

    private TextFace selectedFace(float scale) { return compactOnly ? TextFace.FIXED : face(scale); }

    private int selectedScale(float scale) { return compactOnly ? 1 : pixelScale(scale); }

    /** Widest line in the active menu language, with the same shaping used to draw it. */
    public static int textWidth(String text, float scale) {
        return text == null ? 0 : HostText.shared().width(text, face(scale), HostTextContext.current()) * pixelScale(scale);
    }

    /** Isolated codepoint width; contextual text must be measured as a whole. */
    public static int advance(int codePoint, float scale) {
        return textWidth(new String(Character.toChars(codePoint)), scale);
    }

    /** Native cell width (digits and symbols); proportional letters may be narrower. */
    public static int glyphAdvance(float scale) {
        return scale < 1 ? MenuGlyphs.ADVANCE : MenuGlyphs.FULL_ADVANCE * integerScale(scale);
    }

    /** Actual native cell height, in projection pixels; see {@link #lineHeight(float)} for stacking. */
    public static int glyphLineHeight(float scale) {
        return scale < 1 ? MenuGlyphs.HEIGHT : MenuGlyphs.FULL_HEIGHT * integerScale(scale);
    }

    private static int integerScale(float scale) {
        return Math.max(1, Math.round(scale));
    }

    /** Pitch between stacked lines in this font's language, never less than the native cell. */
    public int lineHeight(float scale) {
        return lineHeight(scale, language());
    }

    public int lineHeight(float scale, TextLanguage language) {
        return lineMetrics(scale, language).lineHeight();
    }

    /** Nominal cell, baseline and script overhang in projection pixels. */
    public LineMetrics lineMetrics(float scale) {
        return lineMetrics(scale, language());
    }

    public LineMetrics lineMetrics(float scale, TextLanguage language) {
        return HostText.shared().lineMetrics(selectedFace(scale), language).scaled(selectedScale(scale));
    }

    /** The shaped line this font would draw, in unscaled face pixels. */
    public ShapedLine shape(String line, float scale) {
        return shape(line, scale, language());
    }

    public ShapedLine shape(String line, float scale, TextLanguage language) {
        return HostText.shared().shape(line, selectedFace(scale), language);
    }

    /** Grapheme-safe ellipsis fit to {@code maxWidth} projection pixels. */
    public String fit(String text, int maxWidth, float scale) {
        return fit(text, maxWidth, scale, language());
    }

    public String fit(String text, int maxWidth, float scale, TextLanguage language) {
        return HostText.shared().fit(text, Math.floorDiv(maxWidth, selectedScale(scale)), selectedFace(scale), language);
    }

    /** Line-break-aware wrap to {@code maxWidth} projection pixels. */
    public List<String> wrap(String text, int maxWidth, float scale) {
        return wrap(text, maxWidth, scale, language());
    }

    public List<String> wrap(String text, int maxWidth, float scale, TextLanguage language) {
        return HostText.shared().wrap(text, Math.max(1, maxWidth / selectedScale(scale)), selectedFace(scale), language);
    }

    @Override
    public int measureWidth(String text, float scale) {
        return measureWidth(text, scale, language());
    }

    public int measureWidth(String text, float scale, TextLanguage language) {
        return text == null ? 0 : HostText.shared().width(text, selectedFace(scale), language) * selectedScale(scale);
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
        drawText(text, x, y, scale, r, g, b, a, language());
    }

    /** Draws {@code text} with its cell top at {@code y}; explicit line breaks advance one line pitch. */
    public void drawText(String text, float x, float y, float scale, float r, float g, float b, float a,
                         TextLanguage language) {
        if (menuRenderer == null || text.isEmpty()) return;
        int originX = Math.round(x);
        int originY = Math.round(y);
        TextFace role = selectedFace(scale);
        int multiplier = selectedScale(scale);
        HostText host = HostText.shared();
        List<String> lines = HostText.lines(text);
        List<ShapedLine> shaped = new ArrayList<>(lines.size());
        int pitch = lines.size() > 1 ? host.lineMetrics(role, language).lineHeight() : 0;
        for (String line : lines) {
            ShapedLine shape = host.shape(line, role, language);
            shaped.add(shape);
            if (lines.size() > 1) pitch = Math.max(pitch, shape.metrics().lineHeight());
        }
        for (int line = 0; line < shaped.size(); line++) {
            drawLine(shaped.get(line), originX, originY + line * pitch * multiplier, role == TextFace.LABEL,
                    multiplier, r, g, b, a);
        }
        if (!batching) flushActiveBatch();
    }

    /**
     * Draws a horizontal viewport over an already shaped line. The offset is in projection
     * pixels; clipping preserves the full line's joining, bidi order and glyph bearings.
     * Batch boundaries keep the clip local to this call, without changing GL scissor state.
     */
    public void drawTextWindow(ShapedLine line, int x, int y, int width, int offset, float scale,
                               float r, float g, float b, float a) {
        Objects.requireNonNull(line, "line");
        if (menuRenderer == null || width <= 0) return;
        flushActiveBatch();
        clipLeft = x;
        clipRight = x + width;
        try {
            drawLine(line, x - offset, y, selectedFace(scale) == TextFace.LABEL, selectedScale(scale), r, g, b, a);
        } finally {
            try { flushActiveBatch(); }
            finally {
                clipLeft = Integer.MIN_VALUE;
                clipRight = Integer.MAX_VALUE;
            }
        }
    }

    private void drawLine(ShapedLine line, int originX, int cellTop, boolean full, int multiplier,
                          float r, float g, float b, float a) {
        int cellWidth = full ? MenuGlyphs.FULL_ADVANCE : MenuGlyphs.ADVANCE;
        int cellHeight = full ? MenuGlyphs.FULL_HEIGHT : MenuGlyphs.HEIGHT;
        int atlasWidth = full ? MenuGlyphs.FULL_ATLAS_WIDTH : MenuGlyphs.ATLAS_WIDTH;
        int atlasHeight = full ? MenuGlyphs.FULL_ATLAS_HEIGHT : MenuGlyphs.ATLAS_HEIGHT;
        int nativeTexture = full ? fullTexture : compactTexture;
        int baselineY = cellTop + line.baseline() * multiplier;
        for (PlacedGlyph glyph : line.glyphs()) {
            int glyphX = originX + glyph.x() * multiplier;
            switch (glyph.kind()) {
                case NATIVE -> {
                    int index = glyph.glyph();
                    MenuGlyphs.Face artwork = compactOnly ? MenuGlyphs.Face.FIXED
                            : full ? MenuGlyphs.Face.BOLD : MenuGlyphs.Face.LIGHT;
                    int inkWidth = MenuGlyphs.inkWidthIndex(artwork, index);
                    if (inkWidth == 0) continue;
                    int sourceX = (index % MenuGlyphs.COLUMNS) * cellWidth + MenuGlyphs.inkLeftIndex(artwork, index);
                    int sourceY = (index / MenuGlyphs.COLUMNS) * cellHeight;
                    int top = cellTop + glyph.y() * multiplier;
                    selectBatch(nativeTexture);
                    ensureCapacity(quads + 1);
                    TexturedQuadRenderer.writeColoredQuadVerticesAtOffset(vertices,
                            quads * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                            glyphX, 224 - top - cellHeight * multiplier,
                            inkWidth * multiplier, cellHeight * multiplier,
                            (float) sourceX / atlasWidth,
                            1f - (float) (sourceY + cellHeight) / atlasHeight,
                            (float) (sourceX + inkWidth) / atlasWidth,
                            1f - (float) sourceY / atlasHeight,
                            r, g, b, a);
                    quads++;
                }
                case SYMBOL -> drawSymbol((char) glyph.glyph(), glyphX, cellTop + glyph.y() * multiplier,
                        cellWidth, cellHeight, multiplier, r, g, b, a);
                case BITMAP -> drawBitmap(glyph, glyphX, baselineY, multiplier, r, g, b, a);
                case MISSING -> drawMissing(glyphX, baselineY, glyph.advance(), multiplier, r, g, b, a);
            }
        }
    }

    private void drawBitmap(PlacedGlyph glyph, int glyphX, int baselineY, int multiplier,
                            float r, float g, float b, float a) {
        GlyphBitmap bitmap = glyph.bitmap();
        if (bitmap.empty()) return;
        if (glyphAtlas == null) glyphAtlas = new GlyphAtlas(GlyphAtlas.GL);
        GlyphAtlas.Slot slot = glyphAtlas.slot(glyph.cacheKey(), bitmap);
        if (slot == null) {
            // Page budget exhausted: draw what is queued, then start the pages again.
            flushActiveBatch();
            glyphAtlas.reset();
            slot = glyphAtlas.slot(glyph.cacheKey(), bitmap);
        }
        selectBatch(slot.texture());
        int width = bitmap.width() * multiplier;
        int height = bitmap.height() * multiplier;
        int left = glyphX + bitmap.left() * multiplier;
        int top = baselineY + (glyph.y() + bitmap.top()) * multiplier;
        float page = GlyphAtlas.PAGE_SIZE;
        ensureCapacity(quads + 1);
        TexturedQuadRenderer.writeColoredQuadVerticesAtOffset(vertices,
                quads * TexturedQuadRenderer.COLORED_QUAD_FLOATS,
                left, 224 - top - height, width, height,
                slot.x() / page, (slot.y() + slot.height()) / page,
                (slot.x() + slot.width()) / page, slot.y() / page,
                r, g, b, a);
        quads++;
    }

    /** A cap-height box: visible evidence that no bundled font can draw the cluster. */
    private void drawMissing(int x, int baselineY, int advance, int multiplier,
                             float r, float g, float b, float a) {
        int width = Math.max(3, advance - 1) * multiplier;
        int height = 7 * multiplier;
        int top = baselineY - height;
        fillRect(x, top, width, multiplier, r, g, b, a);
        fillRect(x, top + height - multiplier, width, multiplier, r, g, b, a);
        fillRect(x, top + multiplier, multiplier, height - 2 * multiplier, r, g, b, a);
        fillRect(x + width - multiplier, top + multiplier, multiplier, height - 2 * multiplier, r, g, b, a);
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

    /** Keeps queued glyphs and their clipping in the same local coordinates as the page. */
    public void withHorizontalOffset(int offset, Runnable draw) {
        if (menuRenderer == null || offset == 0) { draw.run(); return; }
        flushActiveBatch();
        menuRenderer.withHorizontalOffset(offset, () -> {
            try { draw.run(); }
            finally { flushActiveBatch(); }
        });
    }

    /** Draw a solid rectangle in the same top-left coordinate system as text. */
    public void fillRect(int x, int y, int width, int height, float r, float g, float b, float a) {
        if (menuRenderer == null || width <= 0 || height <= 0) return;
        // Sampling the centre of the atlas's white texel lets shapes and compact
        // text share one ordered submission, without texture switches or glyph bleed.
        selectBatch(compactTexture);
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
        selectBatch(compactTexture);
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

    private void selectBatch(int texture) {
        if (activeTexture == texture && quads != 0) return;
        flushActiveBatch();
        activeTexture = texture;
    }

    private void flushActiveBatch() {
        if (quads != 0 && (clipLeft != Integer.MIN_VALUE || clipRight != Integer.MAX_VALUE)) {
            int kept = 0;
            for (int quad = 0; quad < quads; quad++) {
                int source = quad * TexturedQuadRenderer.COLORED_QUAD_FLOATS;
                float left = vertices[source], right = vertices[source + 16];
                if (right <= clipLeft || left >= clipRight) continue;
                float u0 = vertices[source + 2], u1 = vertices[source + 18];
                int target = kept++ * TexturedQuadRenderer.COLORED_QUAD_FLOATS;
                if (target != source) System.arraycopy(vertices, source, vertices, target,
                        TexturedQuadRenderer.COLORED_QUAD_FLOATS);
                for (int vertex = target; vertex < target + TexturedQuadRenderer.COLORED_QUAD_FLOATS; vertex += 8) {
                    float clipped = Math.max(clipLeft, Math.min(clipRight, vertices[vertex]));
                    vertices[vertex] = clipped;
                    vertices[vertex + 2] = u0 + (u1 - u0) * (clipped - left) / (right - left);
                }
            }
            quads = kept;
        }
        if (quads != 0) {
            menuRenderer.drawColoredTextureBatch(activeTexture, vertices, quads);
            quads = 0;
        }
        activeTexture = 0;
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
        activeTexture = 0;
        quads = 0;
        if (glyphAtlas != null) glyphAtlas.reset();
        glyphAtlas = null;
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
