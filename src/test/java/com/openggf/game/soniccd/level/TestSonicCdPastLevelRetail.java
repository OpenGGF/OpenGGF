package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.level.render.PlaneRasterizer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Original USA R11B -> static engine geometry. No destination boot, live art, physics or native parity. */
class TestSonicCdPastLevelRetail {
    private static CdR11bAssets assets;

    @BeforeAll
    static void loadActualPast() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for actual USA Past");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA directory must contain exactly one original CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            assets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
    }

    @Test
    void actualPastIdentityExtentsAndNativePlacementRemainDetachedFromWorldAdmission() {
        var level = new SonicCdPastLevel(assets);
        assertSame(assets, level.assets());
        assertEquals("R11B", assets.identity().stage().nativeId());
        assertEquals(CdReleaseVariant.USA, assets.identity().variant());
        assertEquals(746, level.getPatternCount());
        assertEquals(660, level.getChunkCount());
        assertEquals(107, level.getBlockCount());
        assertEquals(256, level.getSolidTileCount());
        assertEquals(256, level.getBlockPixelSize());
        assertEquals(16, level.getChunksPerBlockSide());
        assertEquals(42, level.getLayerWidthBlocks(0));
        assertEquals(4, level.getLayerHeightBlocks(0));
        assertEquals(10, level.getLayerWidthBlocks(1));
        assertEquals(2, level.getLayerHeightBlocks(1));
        assertEquals(0, level.getMinX()); assertEquals(0x2897, level.getMaxX());
        assertEquals(0, level.getMinY()); assertEquals(0x310, level.getMaxY());
        assertEquals(0x50, level.initialCentre().centreX());
        assertEquals(0x1E8, level.initialCentre().centreY());
        assertSame(assets.placements(), level.nativePlacements());
        assertEquals(497, level.nativePlacements().records().size());
        assertEquals(270, level.nativePlacements().forPeriod(CdPeriod.PAST).size());
        assertTrue(level.getObjects().isEmpty()); assertTrue(level.getRings().isEmpty());
        assertNull(level.getRingSpriteSheet());
        assertThrows(UnsupportedOperationException.class, () -> level.resolveCollisionBlockIndex(1, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> level.ensurePatternCapacity(747));
    }

    @Test
    void everyActualPatternBlockCollisionAndClearedLayoutCellUsesTheNewSource() {
        var level = new SonicCdPastLevel(assets);
        var t = assets.terrain();
        for (int i = 0; i < 746; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(t.pixel(i, x, y), level.getPattern(i).getPixel(x, y));
        for (int i = 0; i < 660; i++) {
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++)
                assertEquals(t.blockWord(i, x, y).rawWord(), level.getChunk(i).getPatternDesc(x, y).get());
            assertEquals(i == 0 ? 0 : t.collision().idForBlock(i), level.getChunk(i).getSolidTileIndex());
        }
        for (int i = 0; i < 256; i++) {
            assertEquals(t.collision().angle(i), Byte.toUnsignedInt(level.getSolidTile(i).getAngle()));
            for (int s = 0; s < 16; s++) {
                assertEquals(t.collision().height(i, s), level.getSolidTile(i).getHeightAt((byte) s));
                assertEquals(t.collision().width(i, s), level.getSolidTile(i).getWidthAt((byte) s));
            }
        }
        for (var p : CdStageTerrain.Plane.values()) {
            int layer = p == CdStageTerrain.Plane.FOREGROUND ? 0 : 1;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
                var c = t.layout().nativeCell(p, x, y);
                assertEquals(c.chunkId(), Byte.toUnsignedInt(level.getMap().getValue(layer, x, y)));
                if (x >= t.layout().columns(p) || y >= t.layout().rows(p)) assertEquals(0, c.rawByte());
                if (c.blank()) continue;
                for (int cy = 0; cy < 16; cy++) for (int cx = 0; cx < 16; cx++) {
                    var n = t.chunkWord(c.chunkId(), cx, cy);
                    var d = level.getBlock(c.chunkId()).getChunkDesc(cx, cy);
                    assertEquals(n.renderBlockIndex(), d.getChunkIndex());
                    assertEquals(n.flipX(), d.getHFlip()); assertEquals(n.flipY(), d.getVFlip());
                    assertEquals(n.solidity(), (d.get() >>> 12) & 3);
                    assertEquals(n.solidity(), (d.get() >>> 14) & 3);
                }
            }
        }
    }

    @Test
    void actualStaticCpuPixelsMatchRawNestedWordsAndPaletteForBothPlanes() throws Exception {
        var level = new SonicCdPastLevel(assets);
        var t = assets.terrain();
        byte[] blocks = t.blocks().copy(), chunks = t.chunks().copy(), patterns = t.patterns().copy();
        byte[] palette = assets.loadPlan().initialPalette().copy();
        int nonBackdrop = 0, backdrop = 0xFF102030;
        // Fixed source-space window, not a restored destination camera or an executed Past frame.
        for (int plane = 0; plane < 2; plane++) {
            int layer = plane;
            int[] pixels = PlaneRasterizer.rasterize(level, (l, i, x) -> level.getPattern(i), layer,
                    0, 256, 512, 256, backdrop);
            for (int y = 0; y < 256; y++) for (int x = 0; x < 512; x++) {
                int mapId = Byte.toUnsignedInt(level.getMap().getValue(layer, x / 256, (y + 256) / 256));
                int expected = sourcePixel(blocks, chunks, patterns, palette, mapId, x & 255, y & 255, backdrop);
                assertEquals(expected, pixels[y * 512 + x], "plane=" + layer + ", x=" + x + ", y=" + y);
                if (expected != backdrop) nonBackdrop++;
            }
            String output = System.getProperty("soniccd.past.output");
            if (output != null && !output.isBlank()) {
                Path dir = Path.of(output);
                Files.createDirectories(dir);
                Path file = dir.resolve("r11b-static-plane-" + layer + ".png");
                assertFalse(Files.exists(file), "Do not overwrite an earlier actual capture");
                var image = new java.awt.image.BufferedImage(512, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, 512, 256, pixels, 0, 512);
                assertTrue(javax.imageio.ImageIO.write(image, "png", file.toFile()));
            }
        }
        assertTrue(nonBackdrop > 0, "The real fixed window must contain decoded source pixels");
    }

    @Test
    void independentMutableViewsCannotEditSourceOrAnotherView() {
        var first = new SonicCdPastLevel(assets); var second = new SonicCdPastLevel(assets);
        var t = assets.terrain();
        int p = t.pixel(0, 0, 0), block = t.blockWord(1, 0, 0).rawWord();
        first.getPattern(0).setPixel(0, 0, (byte) ((p + 1) & 15));
        first.getChunk(1).getPatternDesc(0, 0).set(0xFFFF);
        first.getBlock(1).getChunkDesc(0, 0).set(0xFFFF);
        first.getSolidTile(1).heights[0] ^= 1;
        first.getPalette(0).getColor(0).r ^= 1;
        first.getMap().setValue(0, 0, 0, (byte) 0);
        assertEquals(p, t.pixel(0, 0, 0)); assertEquals(p, second.getPattern(0).getPixel(0, 0));
        assertEquals(block, t.blockWord(1, 0, 0).rawWord());
        assertEquals(block, second.getChunk(1).getPatternDesc(0, 0).get());
        assertEquals(t.chunkWord(1, 0, 0).renderBlockIndex(), second.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(t.collision().height(1, 0), second.getSolidTile(1).getHeightAt((byte) 0));
        assertThrows(NullPointerException.class, () -> new SonicCdPastLevel(null));
    }

    private static int sourcePixel(byte[] blocks, byte[] chunks, byte[] patterns, byte[] palette,
                                   int chunkId, int x, int y, int backdrop) {
        int w = chunkId == 0 ? 0 : word(chunks, (chunkId - 1) * 512 + ((y / 16) * 16 + x / 16) * 2);
        int bx = (x & 15) ^ ((w & 0x0800) != 0 ? 15 : 0);
        int by = (y & 15) ^ ((w & 0x1000) != 0 ? 15 : 0);
        int tile = word(blocks, (w & 0x03FF) * 8 + ((by / 8) * 2 + bx / 8) * 2);
        int tx = (bx & 7) ^ ((tile & 0x0800) != 0 ? 7 : 0);
        int ty = (by & 7) ^ ((tile & 0x1000) != 0 ? 7 : 0);
        int b = Byte.toUnsignedInt(patterns[(tile & 0x07FF) * 32 + ty * 4 + tx / 2]);
        int color = (tx & 1) == 0 ? b >>> 4 : b & 15;
        if (color == 0) return backdrop;
        int cram = word(palette, (((tile >>> 13) & 3) * 16 + color) * 2);
        int r = (((cram >>> 1) & 7) * 255 + 3) / 7;
        int g = (((cram >>> 5) & 7) * 255 + 3) / 7;
        int blue = (((cram >>> 9) & 7) * 255 + 3) / 7;
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }

    private static int word(byte[] bytes, int at) {
        return Byte.toUnsignedInt(bytes[at]) << 8 | Byte.toUnsignedInt(bytes[at + 1]);
    }
}
