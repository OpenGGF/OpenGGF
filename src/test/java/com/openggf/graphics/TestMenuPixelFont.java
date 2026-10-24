package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestMenuPixelFont {
    @Test
    void directionHintsDrawActualArrowGeometryAtBothNativeSizes() throws Exception {
        for(float scale : new float[]{.6f, 1, 2}) {
            var fixture=new Fixture();
            fixture.font.drawText("\u2191\u2193\u2190\u2192",0,0,scale,1,1,1,1);
            var draws=fixture.renderer.draws;
            assertEquals(30,draws.stream().mapToInt(Draw::quads).sum(),"Open arrowheads and long stems use native geometry, not ASCII glyph quads");
            for(var draw:draws) {
                assertEquals(11,draw.texture(),"Arrows share the solid native-grid texel");
                for(int i=0;i<draw.vertices().length;i+=8) {
                    assertEquals(MenuGlyphs.SOLID_U,draw.vertices()[i+2]);
                    assertEquals(MenuGlyphs.SOLID_V,draw.vertices()[i+3]);
                }
            }
            assertEquals(4*MenuPixelFont.glyphAdvance(scale),fixture.font.measureWidth("\u2191\u2193\u2190\u2192",scale));
        }
    }

    @Test
    void explicitLineBreaksResetTheColumnAndAdvanceOneNativeLineInBothWeights() throws Exception {
        for(float scale:new float[]{.6f,1,2}) {
            Fixture fixture=new Fixture();
            fixture.font.drawText("AB\r\nC\nD",9,13,scale,1,1,1,1);
            var draw=fixture.renderer.draws.getFirst();
            assertEquals(4,draw.quads(),"Line breaks must never become question-mark glyphs");
            int stride=TexturedQuadRenderer.COLORED_QUAD_FLOATS;
            int line=MenuPixelFont.glyphLineHeight(scale);
            assertEquals(9,draw.vertices()[2*stride]);
            assertEquals(draw.vertices()[1]-line,draw.vertices()[2*stride+1]);
            assertEquals(9,draw.vertices()[3*stride]);
            assertEquals(draw.vertices()[1]-2*line,draw.vertices()[3*stride+1]);
            assertEquals(2*MenuPixelFont.glyphAdvance(scale),fixture.font.measureWidth("AB\r\nC\nD",scale));
        }
    }

    @Test
    void fullAndCompactLetteringUseTheSameCharacterCellsWithStableNativeMetrics() throws Exception {
        Fixture fixture = new Fixture();
        fixture.font.beginMegaBatch();
        fixture.font.drawText("A", 9.4f, 12.6f, 1.3f, 1, 1, 1, 1);
        fixture.font.drawText("A", 9.4f, 12.6f, .6f, 1, 1, 1, 1);
        fixture.font.endMegaBatch();
        Draw full = fixture.renderer.draws.get(0);
        Draw compact = fixture.renderer.draws.get(1);
        assertEquals(1, full.quads());
        assertEquals(1, compact.quads());
        assertEquals(compact.vertices()[2], full.vertices()[2],
                "Changing weight must keep the authored family's glyph cell");
        assertEquals(compact.vertices()[3], full.vertices()[3]);
        assertEquals(compact.vertices()[18], full.vertices()[18]);
        assertEquals(compact.vertices()[11], full.vertices()[11]);
        assertEquals(9, full.vertices()[0]);
        assertEquals(211, full.vertices()[1]);
        assertEquals(201, full.vertices()[9]);
        assertEquals(18, full.vertices()[16]);
        assertEquals(9, MenuPixelFont.glyphAdvance(1.3f));
        assertEquals(10, MenuPixelFont.glyphLineHeight(1.3f));
    }

    @Test
    void fullLetteringRendersAllPrintablePathsPunctuationAndDescendersAtIntegerScale() throws Exception {
        Fixture fixture = new Fixture();
        String text = "C:\\ROMs\\Sonic (REV01).gen /roms/[a]_{b}-+!@#$%^&*='\";:,.?<>|`~ gjpqy";
        fixture.font.beginMegaBatch();
        fixture.font.drawText(text, 0, 0, 1.7f, 1, 1, 1, 1);
        fixture.font.endMegaBatch();
        assertFalse(fixture.renderer.draws.isEmpty(), "Full weight must submit the authored path alphabet");
        Draw draw = fixture.renderer.draws.getFirst();
        assertEquals(text.chars().filter(c -> c != ' ').count(), draw.quads(),
                "Both weights must support the same authored alphabet, including straight quotes");
        assertEquals(224, draw.vertices()[1]);
        assertEquals(204, draw.vertices()[9]);
        assertEquals(18, draw.vertices()[16]);
        for (int i = 0; i < draw.vertices().length; i += 8) {
            assertEquals(Math.round(draw.vertices()[i]), draw.vertices()[i]);
            assertEquals(Math.round(draw.vertices()[i + 1]), draw.vertices()[i + 1]);
        }
    }

    @Test
    void adjacentRectanglesBatchWithoutChangingOverlapOrderOrColour() throws Exception {
        Fixture fixture = new Fixture();
        fixture.font.beginMegaBatch();
        // More than the initial capacity, with overlapping alpha rectangles.
        for (int i = 0; i < 100; i++)
            fixture.font.fillRect(i, 20, 50, 2, i / 100f, 0, 1, .5f);
        assertTrue(fixture.renderer.draws.isEmpty());
        fixture.font.drawText("a", 0, 20, .6f, 1, 1, 1, 1);
        fixture.font.fillRect(0, 21, 50, 2, 1, 0, 0, 1);
        fixture.font.endMegaBatch();
        assertEquals(List.of(11), fixture.renderer.draws.stream().map(Draw::texture).toList());
        assertEquals(List.of(102), fixture.renderer.draws.stream().map(Draw::quads).toList());
        float[] vertices = fixture.renderer.draws.getFirst().vertices();
        for (int i = 0; i < 100; i++) {
            int offset = i * TexturedQuadRenderer.COLORED_QUAD_FLOATS;
            assertEquals(i, vertices[offset]);
            assertEquals(204, vertices[offset + 1]);
            assertEquals(i / 100f, vertices[offset + 4]);
            assertEquals(.5f, vertices[offset + 7]);
        }
        int submitted = fixture.renderer.draws.size();
        fixture.font.endMegaBatch();
        assertEquals(submitted, fixture.renderer.draws.size());
        fixture.font.fillRect(0, 0, 1, 1, 1, 1, 1, 1);
        assertEquals(submitted + 1, fixture.renderer.draws.size(), "Unbatched fills remain immediate");
    }

    @Test
    void checkerboardIsOneNativeGeometrySubmissionAndPreservesTextOrder() throws Exception {
        Fixture fixture = new Fixture();
        fixture.font.beginMegaBatch();
        fixture.font.drawText("A", 0, 0, 1f, 1, 1, 1, 1);
        fixture.font.drawCheckerboard(0, 320);
        fixture.font.drawText("b", 0, 20, .6f, 1, 1, 1, 1);
        fixture.font.endMegaBatch();
        assertEquals(List.of(7, 11), fixture.renderer.draws.stream().map(Draw::texture).toList());
        assertEquals(111, fixture.renderer.draws.get(1).quads());
        float[] before = Arrays.copyOf(fixture.renderer.draws.get(1).vertices(),
                110 * TexturedQuadRenderer.COLORED_QUAD_FLOATS);
        fixture.font.drawCheckerboard(0, 320);
        assertArrayEquals(before, fixture.renderer.draws.getLast().vertices());
        fixture.font.drawCheckerboard(160, 320);
        assertEquals(55, fixture.renderer.draws.getLast().quads());
    }

    @Test
    void compactMetricsAreNativeAtEveryFractionalScaleAndFullMetricsAreIntegerScaled() {
        MenuPixelFont font = new MenuPixelFont();
        for (float scale : new float[] {.4f, .5f, .6f, .65f, .8f, .99f}) {
            assertEquals(60, font.measureWidth("OpenGGF123", scale));
            assertEquals(6, MenuPixelFont.glyphAdvance(scale));
            assertEquals(8, MenuPixelFont.glyphLineHeight(scale));
        }
        assertEquals(18, font.measureWidth("AB", 1.3f));
        assertEquals(36, font.measureWidth("AB", 1.6f));
        assertEquals(20, MenuPixelFont.glyphLineHeight(1.6f));
    }

    @Test
    void compactQuadsUseExactSixByEightCellsAndIntegralOrigins() throws Exception {
        Fixture fixture = new Fixture();
        fixture.font.drawText("A B", 9.4f, 12.6f, .65f, 1, .7f, .2f, 1);
        assertEquals(1, fixture.renderer.draws.size());
        Draw draw = fixture.renderer.draws.getFirst();
        assertEquals(11, draw.texture());
        assertEquals(2, draw.quads());
        assertBounds(draw.vertices(), 0, 9, 211);
        assertBounds(draw.vertices(), TexturedQuadRenderer.COLORED_QUAD_FLOATS, 21, 211);
    }

    @Test
    void mixedFontAndSolidBatchesKeepSubmissionOrderAndColors() throws Exception {
        Fixture fixture = new Fixture();
        MenuPixelFont font = fixture.font;
        font.beginMegaBatch();
        font.drawText("A", 0, 0, 1f, 1, 0, 0, 1);
        font.drawText("B", 9, 0, 1f, 0, 1, 0, 1);
        assertTrue(fixture.renderer.draws.isEmpty());
        font.drawText("c", 0, 12, .6f, 0, 0, 1, 1);
        font.drawText("d", 6, 12, .8f, 1, 1, 1, .5f);
        font.fillRect(0, 20, 50, 2, 1, 0, 0, 1);
        font.drawText("e", 0, 23, .5f, 1, 1, 1, 1);
        font.drawText("A", 0, 32, 1.7f, 1, 1, 1, 1);
        font.endMegaBatch();
        assertEquals(List.of(7, 11, 7),
                fixture.renderer.draws.stream().map(Draw::texture).toList());
        assertEquals(List.of(2, 4, 1),
                fixture.renderer.draws.stream().map(Draw::quads).toList());
        assertEquals(1, fixture.renderer.draws.get(0).vertices()[4]);
        assertEquals(0, fixture.renderer.draws.get(0).vertices()[5]);
        assertEquals(0, fixture.renderer.draws.get(0).vertices()[TexturedQuadRenderer.COLORED_QUAD_FLOATS + 4]);
        assertEquals(1, fixture.renderer.draws.get(0).vertices()[TexturedQuadRenderer.COLORED_QUAD_FLOATS + 5]);
        float[] middle = fixture.renderer.draws.get(1).vertices();
        int solid = 2 * TexturedQuadRenderer.COLORED_QUAD_FLOATS;
        assertEquals(204, middle[solid + 1], "Solid stays after c/d and before e");
        assertEquals(1, middle[solid + 4]);
        assertEquals(1, middle[solid + 7]);
        for (int vertex = 0; vertex < 6; vertex++) {
            assertEquals(MenuGlyphs.SOLID_U, middle[solid + vertex * 8 + 2]);
            assertEquals(MenuGlyphs.SOLID_V, middle[solid + vertex * 8 + 3]);
        }
        assertEquals(201, middle[solid + TexturedQuadRenderer.COLORED_QUAD_FLOATS + 1]);
        int draws = fixture.renderer.draws.size();
        font.endMegaBatch();
        assertEquals(draws, fixture.renderer.draws.size(), "Completed batch is not submitted twice");
    }

    @Test
    void allPrintableAsciiHasArtworkAndAtlasCellsKeepTransparentColumnSpacing() {
        byte[] atlas = MenuGlyphs.atlasRgba();
        assertEquals(255, alpha(atlas, MenuGlyphs.SOLID_X, MenuGlyphs.SOLID_Y));
        assertEquals(0, alpha(atlas, MenuGlyphs.SOLID_X + 1, MenuGlyphs.SOLID_Y));
        for (char character = 33; character <= 126; character++) {
            int ink = 0;
            for (int y = 0; y < MenuGlyphs.HEIGHT; y++) ink |= MenuGlyphs.row(character, y);
            assertNotEquals(0, ink, "Missing glyph " + character);
        }
        for (int y = 0; y < MenuGlyphs.HEIGHT; y++) assertEquals(0, MenuGlyphs.row(' ', y));
        for (int index = 0; index < 95; index++) {
            int originX = index % MenuGlyphs.COLUMNS * 6;
            int originY = index / MenuGlyphs.COLUMNS * 8;
            for (int y = 0; y < 8; y++) assertEquals(0, alpha(atlas, originX + 5, originY + y));
            char character = (char) (index + 32);
            for (int y = 0; y < MenuGlyphs.HEIGHT; y++) {
                for (int x = 0; x < 5; x++) {
                    int expected = (MenuGlyphs.row(character, y) & (1 << (4 - x))) == 0 ? 0 : 255;
                    assertEquals(expected, alpha(atlas, originX + x, originY + y),
                            "Atlas must include every authored row for " + character);
                }
            }
        }
        assertEquals(255, alpha(atlas, ('A' - 32) % 16 * 6 + 1, ('A' - 32) / 16 * 8));
    }

    @Test
    void lowercaseDescendersReachBelowTheSharedBaselineWithinTheSameCell() {
        for (char character : "gjpqy".toCharArray()) {
            assertNotEquals(0, MenuGlyphs.row(character, 7), "Missing descender for " + character);
        }
        for (char character : "gpqy".toCharArray()) {
            assertEquals(0, MenuGlyphs.row(character, 0));
            assertEquals(0, MenuGlyphs.row(character, 1));
            assertNotEquals(0, MenuGlyphs.row(character, 2), "Lowercase bodies share the x-height");
        }
        assertNotEquals(0, MenuGlyphs.row('j', 0), "Keep the dot above the j stem");
        for (char character : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefhiklmnorstuvwxz0123456789".toCharArray()) {
            assertEquals(0, MenuGlyphs.row(character, 7), "Non-descenders keep their baseline: " + character);
        }
        assertEquals(6, MenuGlyphs.ADVANCE);
        assertEquals(8, MenuGlyphs.HEIGHT);
    }

    @Test
    void boldAtlasAddsWeightToEveryAuthoredMaskWithoutOutlineShadowOrResampling() {
        byte[] compact = MenuGlyphs.atlasRgba();
        byte[] full = MenuGlyphs.fullAtlasRgba();
        assertEquals(144 * 60 * 4, full.length);
        for (int index = 0; index < 95; index++) {
            int compactX = index % 16 * 6;
            int compactY = index / 16 * 8;
            int fullX = index % 16 * 9;
            int fullY = index / 16 * 10;
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 9; x++) {
                    boolean expectedInk = expandedInk(compact, compactX, compactY, x, y);
                    int offset = ((60 - 1 - fullY - y) * 144 + fullX + x) * 4;
                    for (int channel = 0; channel < 4; channel++) {
                        assertEquals(expectedInk ? 255 : 0, full[offset + channel] & 255,
                                "Bold " + (char) (index + 32) + " at " + x + "," + y
                                        + " must retain the authored shape and white ink");
                    }
                }
            }
        }
        for (char character : "gjpqy".toCharArray()) {
            assertNotEquals(0, MenuGlyphs.fullRow(character, 8), "Keep bold descender for " + character);
            assertEquals(0, MenuGlyphs.fullRow(character, 9));
        }
        for (char character : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefhiklmnorstuvwxz0123456789".toCharArray())
            assertEquals(0, MenuGlyphs.fullRow(character, 8), "Keep the bold baseline for " + character);
    }

    @Test
    void boldMAndWKeepThreeSeparatedStemsInsteadOfBecomingSolidBlocks() {
        byte[] atlas = MenuGlyphs.fullAtlasRgba();
        char[] characters = {'M', 'm', 'W', 'w'};
        int[] stemRows = {3, 4, 4, 5};
        for (int i = 0; i < characters.length; i++) {
            int index = characters[i] - 32;
            int originX = index % 16 * 9;
            int originY = index / 16 * 10;
            for (int x : new int[] {0, 1, 3, 4, 6, 7})
                assertEquals(255, fullAlpha(atlas, originX + x, originY + stemRows[i]),
                        "Keep all three bold stems for " + characters[i]);
            for (int x : new int[] {2, 5, 8})
                assertEquals(0, fullAlpha(atlas, originX + x, originY + stemRows[i]),
                        "Keep separated stems and trailing spacing for " + characters[i]);
        }
    }

    @Test
    void unbatchedFullTextUsesColoredBatchAndCleanupDropsPendingGeometry() throws Exception {
        Fixture fixture = new Fixture();
        fixture.font.drawText("gjpqy'\"", 0, 0, 1f, .2f, .4f, .6f, .8f);
        assertEquals(1, fixture.renderer.draws.size());
        Draw draw = fixture.renderer.draws.getFirst();
        assertEquals(7, draw.texture());
        assertEquals(7, draw.quads());
        assertEquals(.2f, draw.vertices()[4]);
        assertEquals(.8f, draw.vertices()[7]);
        fixture.font.beginMegaBatch();
        fixture.font.drawText("A", 0, 0, 1f, 1, 1, 1, 1);
        // Synthetic fixtures have no GL textures to release.
        set(MenuPixelFont.class, fixture.font, "compactTexture", 0);
        set(MenuPixelFont.class, fixture.font, "fullTexture", 0);
        fixture.font.cleanup();
        fixture.font.endMegaBatch();
        fixture.font.drawText("A", 0, 0, 1f, 1, 1, 1, 1);
        assertEquals(1, fixture.renderer.draws.size(), "Cleanup must drop queued geometry and detach the renderer");
    }

    private static boolean expandedInk(byte[] atlas, int originX, int originY, int x, int y) {
        int[] positions = {0, 1, 3, 5, 6};
        for (int sourceX = 0; sourceX < positions.length; sourceX++) {
            if ((x == positions[sourceX] || x == positions[sourceX] + 1)
                    && sourceInk(atlas, originX, originY, sourceX, y - 1)) return true;
        }
        return false;
    }

    private static int fullAlpha(byte[] atlas, int x, int topY) {
        return atlas[((60 - 1 - topY) * 144 + x) * 4 + 3] & 255;
    }

    private static boolean sourceInk(byte[] atlas, int originX, int originY, int x, int y) {
        return x >= 0 && x < 5 && y >= 0 && y < 8 && alpha(atlas, originX + x, originY + y) != 0;
    }

    @Test
    void pathsAndAsciiPunctuationRenderEveryNonSpaceCharacter() throws Exception {
        Fixture fixture = new Fixture();
        String path = "C:\\ROMs\\Sonic (REV01).gen /roms/[a]_{b}-+!@#$%^&*='\";:,.?<>|`~";
        fixture.font.drawText(path, 0, 0, .6f, 1, 1, 1, 1);
        assertEquals(path.chars().filter(c -> c != ' ').count(), fixture.renderer.draws.getFirst().quads());
        assertEquals('\'', MenuGlyphs.normalized('\u2019'));
        assertEquals('"', MenuGlyphs.normalized('\u201c'));
        assertNotEquals(MenuGlyphs.row('/', 0), MenuGlyphs.row('\\', 0));
    }

    private static int alpha(byte[] atlas, int x, int topY) {
        return atlas[((MenuGlyphs.ATLAS_HEIGHT - 1 - topY) * MenuGlyphs.ATLAS_WIDTH + x) * 4 + 3] & 255;
    }

    private static void assertBounds(float[] vertices, int offset, int minX, int topY) {
        // Colored vertex layout: x,y,u,v,r,g,b,a for each of six vertices.
        assertEquals(minX, vertices[offset]);
        assertEquals(topY, vertices[offset + 1]);
        assertEquals(topY - 8, vertices[offset + 9]);
        assertEquals(minX + 6, vertices[offset + 16]);
        for (int i = 0; i < 6; i++) {
            assertEquals(Math.round(vertices[offset + i * 8]), vertices[offset + i * 8]);
            assertEquals(Math.round(vertices[offset + i * 8 + 1]), vertices[offset + i * 8 + 1]);
        }
    }

    private record Draw(int texture, int quads, float[] vertices) { }

    private static final class RecordingRenderer extends TexturedQuadRenderer {
        private final List<Draw> draws = new ArrayList<>();
        @Override
        public void drawColoredTextureBatch(int texture, float[] vertices, int quads) {
            draws.add(new Draw(texture, quads, Arrays.copyOf(vertices, quads * COLORED_QUAD_FLOATS)));
        }
        @Override
        public void drawTexture(int texture, float x, float y, float w, float h,
                                float r, float g, float b, float a) {
            draws.add(new Draw(texture, 1, new float[] {x, y, w, h, r, g, b, a}));
        }
    }

    private static final class Fixture {
        final MenuPixelFont font = new MenuPixelFont();
        final RecordingRenderer renderer = new RecordingRenderer();
        Fixture() throws Exception {
            set(MenuPixelFont.class, font, "menuRenderer", renderer);
            set(MenuPixelFont.class, font, "compactTexture", 11);
            set(MenuPixelFont.class, font, "fullTexture", 7);
        }
    }

    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
