package com.openggf.graphics;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;


import static org.junit.jupiter.api.Assertions.*;

/** Private appearance rows: composition from the live line, residency and the 64-row budget. */
@Isolated
class TestAppearancePalettes {
    private static final int RED = 0x00E;
    private static final int GREEN = 0x0E0;

    /** A stock character line: index i holds a distinct blue so every entry differs. */
    private static Palette stockLine() {
        Palette palette = new Palette();
        for (int i = 0; i < 16; i++) colour(palette, i, (i % 8) << 9 | 0x020);
        return palette;
    }

    private static void colour(Palette palette, int index, int word) {
        palette.getColor(index).fromSegaFormat(word);
    }

    private static Palette.Color md(int word) {
        Palette.Color colour = new Palette.Color();
        colour.fromSegaFormat(word);
        return colour;
    }

    private static void assertColour(int word, PaletteView view, int index) {
        Palette.Color expected = md(word);
        assertEquals(expected.r, view.red(index), "red " + index);
        assertEquals(expected.g, view.green(index), "green " + index);
        assertEquals(expected.b, view.blue(index), "blue " + index);
    }

    private static boolean same(PaletteView a, PaletteView b, int index) {
        return a.red(index) == b.red(index) && a.green(index) == b.green(index) && a.blue(index) == b.blue(index);
    }

    /** The look under test: entries 2 and 3 red, entry 4 green. */
    private static final int LOOK_MASK = 1 << 2 | 1 << 3 | 1 << 4;

    private static Palette lookColours() {
        Palette colours = new Palette();
        colour(colours, 2, RED);
        colour(colours, 3, RED);
        colour(colours, 4, GREEN);
        return colours;
    }

    private static AppearancePalettes.Block acquire(GraphicsManager graphics, PaletteView stock, boolean liveSource) {
        return AppearancePalettes.acquire(graphics, null, 0, stock, liveSource, lookColours(), LOOK_MASK);
    }

    @Test
    void composesTheLiveLineAndKeepsRuntimeWritesVisible() {
        RenderContext.reset();
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Palette live = stockLine();
            Palette line1 = new Palette();
            colour(line1, 5, 0x0AA);
            graphics.cachePaletteTexture(live, 0);
            graphics.cachePaletteTexture(line1, 1);

            var block = acquire(graphics, stockLine(), true);
            assertNotNull(block);
            RenderContext context = block.context();
            assertTrue(context.getPaletteLineBase() >= RenderContext.LINES_PER_CONTEXT);
            assertTrue(context.getPaletteLineBase() + 4 <= 64, "inside the bounded bank range");

            PaletteView row = block.composedLine(0);
            assertColour(RED, row, 2);
            assertColour(RED, row, 3);
            assertColour(GREEN, row, 4);
            for (int i : new int[] {0, 1, 5, 6, 15}) assertTrue(same(live, row, i), "untouched entry " + i);
            assertColour(0x0AA, block.composedLine(1), 5);

            // A Super cycle rewrites entry 3 on the shared line and uploads it: the private row
            // shows the runtime colour there and keeps the look everywhere else.
            colour(live, 3, 0x0EE);
            graphics.cachePaletteTexture(live, 0);
            assertColour(0x0EE, row, 3);
            assertColour(RED, row, 2);
            // When the cycle restores the stock colour, the look returns.
            colour(live, 3, 3 << 9 | 0x020);
            graphics.cachePaletteTexture(live, 0);
            assertColour(RED, row, 3);

            // The shared line itself is never recoloured.
            assertSame(live, graphics.paletteTextures().cachedPaletteLine(0));
            assertColour(2 << 9 | 0x020, live, 2);

            // Lines 1-3 follow their own uploads.
            colour(line1, 5, 0x0CC);
            graphics.cachePaletteTexture(line1, 1);
            assertColour(0x0CC, block.composedLine(1), 5);

            // Each private row fades with the row it mirrors.
            int base = context.getPaletteLineBase();
            assertEquals(0, graphics.paletteTextures().appearancePaletteRows().fadeLine(base));
            assertEquals(1, graphics.paletteTextures().appearancePaletteRows().fadeLine(base + 1));
            assertEquals(base + 4, graphics.paletteTextures().appearancePaletteRows().fadeLine(base + 4));

            block.release();
            assertNull(block.context());
            assertTrue(graphics.paletteTextures().appearancePaletteRows().isEmpty());
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }

    @Test
    void anotherCharactersStockLineIsComposedFromItsOwnColours() {
        RenderContext.reset();
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Palette shared = new Palette();
            for (int i = 0; i < 16; i++) colour(shared, i, 0x0E0);
            graphics.cachePaletteTexture(shared, 0);
            Palette knuckles = stockLine();
            var block = acquire(graphics, knuckles, false);
            PaletteView row = block.composedLine(0);
            assertColour(RED, row, 2);
            assertTrue(same(knuckles, row, 7), "non-overridden entries come from the ghost's stock line");
            colour(shared, 7, 0x0EE);
            graphics.cachePaletteTexture(shared, 0);
            assertTrue(same(knuckles, row, 7), "the shared line's writes belong to another character");
            block.release();
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }

    @Test
    void underwaterRowsUseTheWaterLineForStockEntriesAndShiftOverrides() {
        RenderContext.reset();
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Palette live = stockLine();
            graphics.cachePaletteTexture(live, 0);
            var block = acquire(graphics, stockLine(), true);
            int base = block.context().getPaletteLineBase();
            Palette water0 = new Palette();
            for (int i = 0; i < 16; i++) colour(water0, i, 0x0A0 | (i % 4) << 9);
            Palette[] water = {water0, new Palette(), new Palette(), new Palette()};

            PaletteView row = graphics.paletteTextures().appearancePaletteRows().underwaterRow(base, water, live, water0);
            assertNotNull(row);
            assertTrue(same(water0, row, 6), "stock entries take the zone's water colour");
            // Overrides follow the water's per-entry shift; a channel the stock entry lacks (red
            // here) uses the line's average shift, which is neutral for this all-blue line.
            assertColour(RED, row, 2);
            assertColour(GREEN, row, 4);
            assertTrue(same(water[1], graphics.paletteTextures().appearancePaletteRows().underwaterRow(base + 1, water, live, water0), 0));
            assertNull(graphics.paletteTextures().appearancePaletteRows().underwaterRow(base + 4, water, live, water0));
            block.release();
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }

    @Test
    void underwaterOverridesKeepPerEntryRatiosGlobalFallbackAndDonorSelection() {
        RenderContext.reset();
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Palette live = new Palette();
            live.colors[1] = new Palette.Color((byte) 200, (byte) 100, (byte) 0);
            live.colors[2] = new Palette.Color((byte) 0, (byte) 100, (byte) 200);
            Palette water = new Palette();
            water.colors[0] = new Palette.Color((byte) 255, (byte) 255, (byte) 255);
            water.colors[1] = new Palette.Color((byte) 100, (byte) 200, (byte) 80);
            water.colors[2] = new Palette.Color((byte) 30, (byte) 50, (byte) 100);
            water.colors[3] = new Palette.Color((byte) 255, (byte) 240, (byte) 200);
            Palette overrides = new Palette();
            overrides.colors[1] = new Palette.Color((byte) 221, (byte) 151, (byte) 181);
            graphics.cachePaletteTexture(live, 0);
            Palette[] waterRows = {water, new Palette(), new Palette(), new Palette()};
            var nativeBlock = AppearancePalettes.acquire(graphics, null, 0, live, true, overrides, 1 << 1);
            var otherCharacter = AppearancePalettes.acquire(graphics, null, 0, live, false, overrides, 1 << 1);
            RenderContext donor = RenderContext.getOrCreateDonor(com.openggf.game.GameId.S2);
            donor.setPalette(0, live);
            graphics.cachePaletteTexture(live, donor.getPaletteLineBase());
            var donorBlock = AppearancePalettes.acquire(graphics, donor, 0, live, true, overrides, 1 << 1);

            PaletteView nativeWater = graphics.paletteTextures().appearancePaletteRows().underwaterRow(
                    nativeBlock.context().getPaletteLineBase(), waterRows, live, water);
            assertRgb(nativeWater, 1, 110, 255, 162);
            assertRgb(nativeWater, 2, 30, 50, 100);
            for (var block : new AppearancePalettes.Block[]{otherCharacter, donorBlock}) {
                PaletteView shifted = graphics.paletteTextures().appearancePaletteRows().underwaterRow(
                        block.context().getPaletteLineBase(), waterRows, live, water);
                assertRgb(shifted, 1, 143, 188, 162);
                assertRgb(shifted, 2, 0, 125, 179);
                assertSame(shifted, graphics.paletteTextures().appearancePaletteRows().underwaterRow(
                        block.context().getPaletteLineBase(), waterRows, live, water));
            }
            live.colors[1].r = 1;
            graphics.cachePaletteTexture(live, 0);
            PaletteView changed = graphics.paletteTextures().appearancePaletteRows().underwaterRow(
                    nativeBlock.context().getPaletteLineBase(), waterRows, live, water);
            assertSame(nativeWater, changed);
            assertRgb(changed, 1, 100, 200, 80);
            assertRgb(graphics.paletteTextures().appearancePaletteRows().underwaterRow(
                    otherCharacter.context().getPaletteLineBase(), null, null, null), 1, 221, 151, 181);
            nativeBlock.release();
            otherCharacter.release();
            donorBlock.release();
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }

    private static void assertRgb(PaletteView row, int i, int red, int green, int blue) {
        assertEquals(red, row.red(i) & 255, "red " + i);
        assertEquals(green, row.green(i) & 255, "green " + i);
        assertEquals(blue, row.blue(i) & 255, "blue " + i);
    }

    @Test
    void rowsStayInsideTheBankBudgetAndSurviveAPaletteReset() {
        RenderContext.reset();
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            graphics.cachePaletteTexture(stockLine(), 0);
            var blocks = new java.util.ArrayList<AppearancePalettes.Block>();
            for (int i = 0; i < 15; i++) {
                var block = acquire(graphics, stockLine(), true);
                assertNotNull(block, "block " + i);
                blocks.add(block);
            }
            assertEquals(64, RenderContext.getTotalPaletteLines());
            assertNull(acquire(graphics, stockLine(), true),
                    "a full budget falls back to the shared rows instead of growing past 64");
            assertNull(acquire(graphics, null, true), "no stock line, no rows");

            blocks.getFirst().release();
            assertNotNull(acquire(graphics, stockLine(), true));

            // A game change discards palette textures; a block reserves fresh rows on next use.
            var survivor = blocks.get(1);
            graphics.clearPaletteTextures();
            graphics.cachePaletteTexture(stockLine(), 0);
            RenderContext context = survivor.context();
            assertNotNull(context);
            assertColour(RED, survivor.composedLine(0), 2);
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }
}
