package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.level.Level;
import com.openggf.level.render.PlaneRasterizer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production loader -> actual Level adapter; no synthetic known-media identity or native boot. */
class TestSonicCdLevelRetail {
    @ParameterizedTest(name = "{0} R11A engine Level reads")
    @EnumSource(CdReleaseVariant.class)
    void suppliedRegionalMediaFeedsIndependentMutableEngineGeometryAfterDiscClose(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied-media checks");
        String directory = switch (variant) {
            case USA -> "USA";
            case JAPAN -> "Japan";
            case EUROPE -> "Europe";
        };
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve(directory))) {
            var cues = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured region must contain exactly one supplied CUE");
            cue = cues.getFirst();
        }
        CdR11aAssets assets;
        try (var source = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty())) {
            assets = CdR11aAssetLoader.load(source, variant, CdStageKey.parse("R11A"));
        }
        var first = new SonicCdLevel(assets);
        var second = new SonicCdLevel(assets);
        Level level = first;
        assertSame(assets, first.assets());
        assertEquals(variant, first.assets().identity().variant());
        assertEquals(64, first.initialCentre().centreX());
        assertEquals(0x76B, first.initialCentre().centreY());
        assertEquals(0, level.getMinX());
        assertEquals(0x2897, level.getMaxX());
        assertEquals(0, level.getMinY());
        assertEquals(0x710, level.getMaxY());
        assertEquals(0x60, first.assets().loadPlan().bounds().cameraCentreY());
        assertEquals(800, level.getPatternCount());
        assertEquals(804, level.getChunkCount());
        assertEquals(122, level.getBlockCount());
        assertEquals(256, level.getSolidTileCount());
        assertEquals(256, level.getBlockPixelSize());
        assertEquals(16, level.getChunksPerBlockSide());
        assertEquals(42, level.getLayerWidthBlocks(0));
        assertEquals(6, level.getLayerWidthBlocks(1));
        assertEquals(8, level.getLayerHeightBlocks(0));
        assertEquals(8, level.getLayerHeightBlocks(1));
        assertTrue(level.getObjects().isEmpty(), "Native data is not an executable registration");
        assertTrue(level.getRings().isEmpty(), "No native ring-object expansion is ported");
        assertNull(level.getRingSpriteSheet());
        assertSame(assets.placements(), first.nativePlacements());
        assertEquals(497, first.nativePlacements().records().size());
        assertEquals(227, first.nativePlacements().forPeriod(CdPeriod.PRESENT).size());
        // Both subtypes, raw Y flags, saved bits and period flags survive by retaining the native owner.
        for (var row : first.nativePlacements().records()) {
            assertEquals(row.packedY() & 0xF000, row.nativeYFlags());
            assertEquals(row.packedY() & 0x4000, row.flipX() ? 0x4000 : 0);
            assertEquals(row.packedY() & 0x8000, row.flipY() ? 0x8000 : 0);
            assertEquals(row.encodedId() & 0x80, row.hasSavedFlag() ? 0x80 : 0);
        }

        var nativeData = assets.terrain();
        for (int id = 0; id < level.getPatternCount(); id++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(nativeData.pixel(id, x, y), level.getPattern(id).getPixel(x, y));
                }
            }
        }
        for (int id = 0; id < level.getChunkCount(); id++) {
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    assertEquals(nativeData.blockWord(id, x, y).rawWord(), level.getChunk(id).getPatternDesc(x, y).get());
                }
            }
            assertEquals(id == 0 ? 0 : nativeData.collision().idForBlock(id), level.getChunk(id).getSolidTileIndex());
            assertEquals(level.getChunk(id).getSolidTileIndex(), level.getChunk(id).getSolidTileAltIndex());
        }
        for (int id = 0; id < level.getSolidTileCount(); id++) {
            assertEquals(nativeData.collision().angle(id), Byte.toUnsignedInt(level.getSolidTile(id).getAngle()));
            for (int sample = 0; sample < 16; sample++) {
                assertEquals(nativeData.collision().height(id, sample), level.getSolidTile(id).getHeightAt((byte) sample));
                assertEquals(nativeData.collision().width(id, sample), level.getSolidTile(id).getWidthAt((byte) sample));
            }
        }
        for (int color = 0; color < 64; color++) {
            int cram = assets.loadPlan().colorWord(color);
            var engine = level.getPalette(color / 16).getColor(color % 16);
            assertEquals((((cram >> 1) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(engine.r));
            assertEquals((((cram >> 5) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(engine.g));
            assertEquals((((cram >> 9) & 7) * 255 + 3) / 7, Byte.toUnsignedInt(engine.b));
        }
        for (var plane : CdR11aTerrain.Plane.values()) {
            int layer = plane == CdR11aTerrain.Plane.FOREGROUND ? 0 : 1;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 64; x++) {
                    var cell = nativeData.layout().nativeCell(plane, x, y);
                    assertEquals(cell.chunkId(), Byte.toUnsignedInt(level.getMap().getValue(layer, x, y)));
                    if (cell.blank()) { continue; }
                    for (int cy = 0; cy < 16; cy++) {
                        for (int cx = 0; cx < 16; cx++) {
                            var word = nativeData.chunkWord(cell.chunkId(), cx, cy);
                            var desc = level.getBlock(cell.chunkId()).getChunkDesc(cx, cy);
                            assertEquals(word.renderBlockIndex(), desc.getChunkIndex());
                            assertEquals(word.flipX(), desc.getHFlip());
                            assertEquals(word.flipY(), desc.getVFlip());
                            assertEquals(word.solidity(), (desc.get() >>> 12) & 3);
                            assertEquals(word.solidity(), (desc.get() >>> 14) & 3);
                            // Boundary evidence for THIS referenced retail geometry, not a widened index contract.
                            assertEquals(word.renderBlockIndex(), word.collisionBlockIndex());
                        }
                    }
                }
            }
        }
        int unresolvedDrawingCells = 0;
        int distinctCollisionWords = 0;
        for (int id = 1; id <= nativeData.chunkCount(); id++) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    var word = nativeData.chunkWord(id, x, y);
                    if (word.renderBlockIndex() != word.collisionBlockIndex()) { distinctCollisionWords++; }
                    if (word.renderBlockIndex() >= level.getChunkCount()) {
                        unresolvedDrawingCells++;
                        int chunkId = id, cx = x, cy = y;
                        assertThrows(UnsupportedOperationException.class,
                                () -> level.getBlock(chunkId).getChunkDesc(cx, cy));
                    } else {
                        assertEquals(word.renderBlockIndex(), level.getBlock(id).getChunkDesc(x, y).getChunkIndex());
                    }
                }
            }
        }
        // Independent bounded supplied-media calculation, recorded in engine-level-adapter.md.
        assertEquals(55, unresolvedDrawingCells);
        assertEquals(128, distinctCollisionWords);
        assertEquals(0, level.getBlock(0).getChunkDesc(0, 0).get());
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) { assertEquals(0, level.getPattern(0).getPixel(x, y)); }
        }
        assertNativeRaster(level, nativeData, assets.loadPlan().initialPalette().copy());
        // Same authenticated source, two worlds; no shared mutable graph or native-byte edits.
        int sourcePixel = nativeData.pixel(0, 0, 0);
        first.getPattern(0).setPixel(0, 0, (byte) 9);
        first.getChunk(1).getPatternDesc(0, 0).set(0xFFFF);
        first.getBlock(1).getChunkDesc(0, 0).set(0xFFFF);
        first.getSolidTile(1).heights[0] ^= 1;
        first.getPalette(0).getColor(0).r ^= 1;
        first.getMap().setValue(0, 0, 0, (byte) 0);
        assertEquals(sourcePixel, second.getPattern(0).getPixel(0, 0));
        assertEquals(sourcePixel, nativeData.pixel(0, 0, 0));
        assertEquals(nativeData.blockWord(1, 0, 0).rawWord(), second.getChunk(1).getPatternDesc(0, 0).get());
        assertEquals(nativeData.chunkWord(1, 0, 0).renderBlockIndex(), second.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(nativeData.collision().height(1, 0), second.getSolidTile(1).getHeightAt((byte) 0));
        assertEquals(nativeData.layout().cell(CdR11aTerrain.Plane.FOREGROUND, 0, 0).chunkId(),
                Byte.toUnsignedInt(second.getMap().getValue(0, 0, 0)));
    }

    private static void assertNativeRaster(Level level, CdR11aTerrain terrain, byte[] palette) {
        // First nonblank FG cell, all 256 block placements, using the existing CPU renderer.
        int mapX = -1, mapY = -1;
        find:
        for (int y = 0; y < level.getLayerHeightBlocks(0); y++) {
            for (int x = 0; x < level.getLayerWidthBlocks(0); x++) {
                if (level.getMap().getValue(0, x, y) != 0) { mapX = x; mapY = y; break find; }
            }
        }
        assertTrue(mapX >= 0, "R11A must have real foreground geometry");
        int id = Byte.toUnsignedInt(level.getMap().getValue(0, mapX, mapY));
        int backdrop = 0xFF102030;
        int[] actual = PlaneRasterizer.rasterize(level, (layer, index, layoutX) -> level.getPattern(index),
                0, mapX * 256, mapY * 256, 256, 256, backdrop);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                assertEquals(TestSonicCdLevelGeometry.nativePixel(terrain, palette, id, x / 16, y / 16,
                        x % 16, y % 16, backdrop), actual[y * 256 + x]);
            }
        }
    }
}
