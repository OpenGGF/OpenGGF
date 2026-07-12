package com.openggf.game.sonic2;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelConstants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class TestSonic2LevelInMemoryConstruction {

    @TempDir
    Path temp;

    @Test
    void constructsExactV1ShapedLevelWithoutARom() throws Exception {
        Files.write(temp.resolve("patterns.bin"), new byte[Pattern.PATTERN_SIZE_IN_ROM]);
        Files.write(temp.resolve("chunks.bin"), new byte[8]);
        Files.write(temp.resolve("blocks.bin"), new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);

        try (ModAssetRoot assets = ModAssetRoot.directory(
                temp, temp, ModInputLimits.production(), DirectoryAccess.TEST)) {
            LevelResourcePlan plan = plan(assets);
            byte[][] palettes = paletteLines();
            byte[] heights = profiles((byte) 0, (byte) 7);
            byte[] widths = profiles((byte) 0, (byte) 9);
            byte[] angles = {(byte) 0x00, (byte) 0x40};
            byte[] foreground = {1, 2, 3, 4};
            byte[] background = {5, 6, 7, 8};
            ObjectSpawn object = new ObjectSpawn(
                    0x123, 0x234, 0, 7, 2, true, 0xA234, 12,
                    "example-mod", "example-mod:walker");
            RingSpawn ring = new RingSpawn(0x345, 0x456, 34);
            RingSpriteSheet ringSheet = new RingSpriteSheet(
                    new Pattern[0], List.of(), 1, 8, 0, 0);

            Sonic2Level level = Sonic2Level.inMemoryBuilder(11, plan)
                    .layout(2, 2, foreground, background)
                    .paletteLines(palettes)
                    .solidProfiles(heights, widths, angles)
                    .collisionIndices(new int[]{1}, new int[]{0})
                    .boundaries(0x10, 0x2345, -128, 0x456)
                    .spawns(List.of(object), List.of(ring), ringSheet)
                    .build();

            assertEquals(11, level.getZoneIndex());
            assertEquals(1, level.getPatternCount());
            assertEquals(1, level.getChunkCount());
            assertEquals(9, level.getBlockCount());
            assertEquals(2, level.getSolidTileCount());
            assertEquals(1, level.getChunk(0).getSolidTileIndex());
            assertEquals(0, level.getChunk(0).getSolidTileAltIndex());
            assertEquals(7, level.getSolidTile(1).getHeightAt((byte) 5));
            assertEquals(9, level.getSolidTile(1).getWidthAt((byte) 5));
            assertEquals((byte) 0x40, level.getSolidTile(1).getAngle());
            assertEquals(2, level.getMap().getWidth());
            assertEquals(2, level.getMap().getHeight());
            assertEquals(2, level.getMap().getLayerCount());
            assertEquals(4, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 1)));
            assertEquals(8, Byte.toUnsignedInt(level.getMap().getValue(1, 1, 1)));
            assertEquals(255, Byte.toUnsignedInt(level.getPalette(3).colors[1].r));
            assertEquals(0x10, level.getMinX());
            assertEquals(0x2345, level.getMaxX());
            assertEquals(-128, level.getMinY());
            assertEquals(0x456, level.getMaxY());
            assertEquals(List.of(object), level.getObjects());
            assertEquals(List.of(ring), level.getRings());
            assertSame(ringSheet, level.getRingSpriteSheet());
        }
    }

    @Test
    void rejectsHostileDimensionAndExactLengthMismatches() throws Exception {
        try (ModAssetRoot assets = assetRootWithMinimalResources()) {
            LevelResourcePlan plan = plan(assets);
            Sonic2Level.InMemoryBuilder builder = validBuilder(plan);

            assertThrows(IllegalArgumentException.class,
                    () -> builder.layout(2, 2, new byte[3], new byte[4]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.layout(0, 2, new byte[0], new byte[0]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.layout(Integer.MAX_VALUE, 2, new byte[0], new byte[0]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.paletteLines(new byte[3][Palette.PALETTE_SIZE_IN_ROM]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.paletteLines(new byte[][]{new byte[32], new byte[32], new byte[31], new byte[32]}));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.solidProfiles(new byte[17], new byte[16], new byte[1]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.solidProfiles(new byte[16], new byte[16], new byte[2]));
            assertThrows(IllegalArgumentException.class,
                    () -> builder.solidProfiles(new byte[257 * 16], new byte[257 * 16], new byte[257]));
        }
    }

    @Test
    void rejectsV1ResourceCountsAboveFormatCaps() throws Exception {
        Files.write(temp.resolve("patterns.bin"), new byte[2049 * Pattern.PATTERN_SIZE_IN_ROM]);
        Files.write(temp.resolve("chunks.bin"), new byte[8]);
        Files.write(temp.resolve("blocks.bin"), new byte[LevelConstants.BLOCK_SIZE_IN_ROM]);
        try (ModAssetRoot assets = ModAssetRoot.directory(
                temp, temp, ModInputLimits.production(), DirectoryAccess.TEST)) {
            assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets)).build());
        }
    }

    @Test
    void appliesProductionMapAndSpawnCapsAtExactBoundaries() throws Exception {
        try (ModAssetRoot assets = assetRootWithMinimalResources()) {
            ModInputLimits limits = ModInputLimits.production();
            Sonic2Level.InMemoryBuilder builder = Sonic2Level.inMemoryBuilder(11, plan(assets));

            assertDoesNotThrow(() -> builder.layout(
                    limits.maxMapWidth(), 1, new byte[limits.maxMapWidth()], null));
            assertThrows(IllegalArgumentException.class, () -> builder.layout(
                    limits.maxMapWidth() + 1, 1, new byte[0], null));
            assertDoesNotThrow(() -> builder.layout(
                    1, limits.maxMapHeight(), new byte[limits.maxMapHeight()], null));
            assertThrows(IllegalArgumentException.class, () -> builder.layout(
                    1, limits.maxMapHeight() + 1, new byte[0], null));

            int cellsWidth = limits.maxMapWidth();
            int cellsHeight = Math.toIntExact(limits.maxMapCells() / cellsWidth);
            assertEquals(limits.maxMapCells(), (long) cellsWidth * cellsHeight);
            assertDoesNotThrow(() -> builder.layout(
                    cellsWidth, cellsHeight, new byte[Math.toIntExact(limits.maxMapCells())], null));
            assertThrows(IllegalArgumentException.class, () -> builder.layout(
                    cellsWidth, cellsHeight + 1, new byte[0], null));

            ObjectSpawn object = new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
            RingSpawn ring = new RingSpawn(0, 0, 1);
            RingSpriteSheet sheet = new RingSpriteSheet(new Pattern[0], List.of(), 1, 8, 0, 0);
            assertDoesNotThrow(() -> builder.spawns(
                    java.util.Collections.nCopies(limits.maxLevelObjects(), object),
                    java.util.Collections.nCopies(limits.maxLevelRings(), ring), sheet));
            assertThrows(IllegalArgumentException.class, () -> builder.spawns(
                    java.util.Collections.nCopies(limits.maxLevelObjects() + 1, object), List.of(), sheet));
            assertThrows(IllegalArgumentException.class, () -> builder.spawns(
                    List.of(), java.util.Collections.nCopies(limits.maxLevelRings() + 1, ring), sheet));
        }
    }

    @Test
    void rejectsMissingPatternChunkAndMapReferencesIncludingRawBlockZero() throws Exception {
        byte[] invalidChunk = new byte[8];
        invalidChunk[1] = 1;
        writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], invalidChunk,
                new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
        try (ModAssetRoot assets = openAssetRoot()) {
            assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets)).build());
        }

        byte[] invalidBlockZero = new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM];
        invalidBlockZero[1] = 1;
        writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[8], invalidBlockZero);
        try (ModAssetRoot assets = openAssetRoot()) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> validBuilder(plan(assets)).build());
            assertTrue(error.getMessage().contains("Block 0"),
                    "raw block zero must be checked before runtime sanitization");
        }

        writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[8],
                new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
        try (ModAssetRoot assets = openAssetRoot()) {
            assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets))
                    .layout(1, 1, new byte[]{9}, new byte[]{0})
                    .build());
        }
    }

    @Test
    void absentOptionalBackgroundBecomesAnExactBlankSecondLayer() throws Exception {
        try (ModAssetRoot assets = assetRootWithMinimalResources()) {
            Sonic2Level level = Sonic2Level.inMemoryBuilder(11, plan(assets))
                    .layout(2, 1, new byte[]{4, 5}, null)
                    .paletteLines(paletteLines())
                    .solidProfiles(profiles((byte) 0), profiles((byte) 0), new byte[]{0})
                    .collisionIndices(new int[]{0}, new int[]{0})
                    .boundaries(0, 128, 0, 128)
                    .spawns(List.of(), List.of(), new RingSpriteSheet(
                            new Pattern[0], List.of(), 1, 8, 0, 0))
                    .build();

            assertEquals(5, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 0)));
            assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(1, 1, 0)));
        }
    }

    @Test
    void builderSnapshotsAllCallerOwnedArrays() throws Exception {
        try (ModAssetRoot assets = assetRootWithMinimalResources()) {
            byte[] foreground = {3};
            byte[] background = {4};
            byte[][] palettes = paletteLines();
            byte[] heights = profiles((byte) 5);
            byte[] widths = profiles((byte) 6);
            byte[] angles = {7};
            int[] primary = {0};
            int[] secondary = {0};

            Sonic2Level.InMemoryBuilder builder = Sonic2Level.inMemoryBuilder(11, plan(assets))
                    .layout(1, 1, foreground, background)
                    .paletteLines(palettes)
                    .solidProfiles(heights, widths, angles)
                    .collisionIndices(primary, secondary)
                    .boundaries(0, 128, 0, 128)
                    .spawns(List.of(), List.of(), new RingSpriteSheet(
                            new Pattern[0], List.of(), 1, 8, 0, 0));

            foreground[0] = 99;
            background[0] = 99;
            palettes[3][3] = 0;
            heights[0] = 99;
            widths[0] = 99;
            angles[0] = 99;
            primary[0] = 99;
            secondary[0] = 99;

            Sonic2Level level = builder.build();
            assertEquals(3, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
            assertEquals(4, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
            assertEquals(255, Byte.toUnsignedInt(level.getPalette(3).colors[1].r));
            assertEquals(5, level.getSolidTile(0).getHeightAt((byte) 0));
            assertEquals(6, level.getSolidTile(0).getWidthAt((byte) 0));
            assertEquals(7, level.getSolidTile(0).getAngle());
            assertEquals(0, level.getChunk(0).getSolidTileIndex());
            assertEquals(0, level.getChunk(0).getSolidTileAltIndex());
        }
    }

    @Test
    void rejectsCollisionCountAndOutOfRangeProfileReferences() throws Exception {
        try (ModAssetRoot assets = assetRootWithMinimalResources()) {
            LevelResourcePlan plan = plan(assets);

            assertThrows(IllegalArgumentException.class,
                    () -> validBuilder(plan).collisionIndices(new int[0], new int[]{0}).build());
            assertThrows(IllegalArgumentException.class,
                    () -> validBuilder(plan).collisionIndices(new int[]{2}, new int[]{0}).build());
            assertThrows(IllegalArgumentException.class,
                    () -> validBuilder(plan).collisionIndices(new int[]{-1}, new int[]{0}));
            assertThrows(IllegalArgumentException.class,
                    () -> validBuilder(plan).collisionIndices(new int[]{0x1_0000}, new int[]{0}));
        }
    }

    @Test
    void modOnlyResourceLoaderRefusesRomOperationsExplicitly() {
        ResourceLoader loader = ResourceLoader.forModAssetsOnly();
        IOException error = assertThrows(IOException.class,
                () -> loader.loadSingle(LoadOp.kosinskiBase(0x1234)));
        assertTrue(error.getMessage().contains("ROM"));
    }

    @Test
    void modOnlyResourceLoaderRejectsMixedPlanBeforeReadingAnyAsset() throws Exception {
        Files.write(temp.resolve("one.bin"), new byte[]{7});
        ModInputLimits oneByte = ModInputLimits.loweringBuilder()
                .maxAssetBytes(1)
                .maxModValidationBytes(1)
                .build();
        try (ModAssetRoot assets = ModAssetRoot.directory(
                temp, temp, oneByte, DirectoryAccess.TEST)) {
            ResourceLoader loader = ResourceLoader.forModAssetsOnly();
            IOException error = assertThrows(IOException.class, () -> loader.loadWithOverlays(List.of(
                    LoadOp.modAssetBase(assets, "one.bin"), LoadOp.kosinskiAppend(0)), 0));
            assertTrue(error.getMessage().contains("ROM"));
            assertArrayEquals(new byte[]{7}, assets.readBounded("one.bin", 1),
                    "preflight must not consume the mod validation budget");
        }
    }

    @Test
    void crossKindRomSourceFailsBeforeAnyEarlierModAssetRead() throws Exception {
        Path constrained = Files.createDirectory(temp.resolve("constrained"));
        Files.write(constrained.resolve("patterns.bin"), new byte[Pattern.PATTERN_SIZE_IN_ROM]);
        ModInputLimits exactPatternBudget = ModInputLimits.loweringBuilder()
                .maxAssetBytes(Pattern.PATTERN_SIZE_IN_ROM)
                .maxModValidationBytes(Pattern.PATTERN_SIZE_IN_ROM)
                .build();
        try (ModAssetRoot assets = ModAssetRoot.directory(
                constrained, constrained, exactPatternBudget, DirectoryAccess.TEST)) {
            LevelResourcePlan mixed = LevelResourcePlan.builder()
                    .addPatternOp(LoadOp.modAssetBase(assets, "patterns.bin"))
                    .addChunkOp(LoadOp.kosinskiBase(0x1111))
                    .addBlockOp(LoadOp.kosinskiBase(0x1234))
                    .setPrimaryCollision(LoadOp.kosinskiBase(0x5678))
                    .build();

            IOException error = assertThrows(IOException.class, () -> validBuilder(mixed).build());
            assertTrue(error.getMessage().contains("ROM"));
            assertArrayEquals(new byte[Pattern.PATTERN_SIZE_IN_ROM],
                    assets.readBounded("patterns.bin", Pattern.PATTERN_SIZE_IN_ROM),
                    "whole-plan preflight must leave earlier asset budget untouched");
        }
    }

    @Test
    void hostileCountsAndLateReferencesPublishNoGraphicsCaches() throws Exception {
        GraphicsManager graphics = mock(GraphicsManager.class);
        when(graphics.isGlInitialized()).thenReturn(true);
        try (MockedStatic<GameServices> services = mockStatic(GameServices.class)) {
            services.when(GameServices::graphics).thenReturn(graphics);

            writeResources(new byte[2049 * Pattern.PATTERN_SIZE_IN_ROM], new byte[8],
                    new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
            try (ModAssetRoot assets = openAssetRoot()) {
                assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets)).build());
            }

            writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM],
                    new byte[1025 * 8], new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
            try (ModAssetRoot assets = openAssetRoot()) {
                assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets)).build());
            }

            writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[8],
                    new byte[257 * LevelConstants.BLOCK_SIZE_IN_ROM]);
            try (ModAssetRoot assets = openAssetRoot()) {
                assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets)).build());
            }

            writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[8],
                    new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
            try (ModAssetRoot assets = openAssetRoot()) {
                assertThrows(IllegalArgumentException.class, () -> validBuilder(plan(assets))
                        .layout(1, 1, new byte[]{9}, null)
                        .build());
            }

            verify(graphics, never()).cachePatternTexture(any(Pattern.class), anyInt());
            verify(graphics, never()).cachePaletteTexture(any(Palette.class), anyInt());
        }
    }

    @Test
    void validConstructionPublishesGraphicsOnlyAfterValidation() throws Exception {
        GraphicsManager graphics = mock(GraphicsManager.class);
        when(graphics.isGlInitialized()).thenReturn(true);
        try (MockedStatic<GameServices> services = mockStatic(GameServices.class);
             ModAssetRoot assets = assetRootWithMinimalResources()) {
            services.when(GameServices::graphics).thenReturn(graphics);
            Sonic2Level level = validBuilder(plan(assets)).build();
            assertNotNull(level);
            verify(graphics, times(1)).cachePatternTexture(any(Pattern.class), anyInt());
            verify(graphics, times(4)).cachePaletteTexture(any(Palette.class), anyInt());
        }
    }

    private ModAssetRoot assetRootWithMinimalResources() throws IOException {
        writeResources(new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[8],
                new byte[9 * LevelConstants.BLOCK_SIZE_IN_ROM]);
        return openAssetRoot();
    }

    private void writeResources(byte[] patterns, byte[] chunks, byte[] blocks) throws IOException {
        Files.write(temp.resolve("patterns.bin"), patterns);
        Files.write(temp.resolve("chunks.bin"), chunks);
        Files.write(temp.resolve("blocks.bin"), blocks);
    }

    private ModAssetRoot openAssetRoot() throws IOException {
        return ModAssetRoot.directory(temp, temp, ModInputLimits.production(), DirectoryAccess.TEST);
    }

    private static LevelResourcePlan plan(ModAssetRoot assets) {
        return LevelResourcePlan.builder()
                .addPatternOp(LoadOp.modAssetBase(assets, "patterns.bin"))
                .addChunkOp(LoadOp.modAssetBase(assets, "chunks.bin"))
                .addBlockOp(LoadOp.modAssetBase(assets, "blocks.bin"))
                .build();
    }

    private static Sonic2Level.InMemoryBuilder validBuilder(LevelResourcePlan plan) {
        return Sonic2Level.inMemoryBuilder(11, plan)
                .layout(1, 1, new byte[]{0}, new byte[]{0})
                .paletteLines(paletteLines())
                .solidProfiles(profiles((byte) 0), profiles((byte) 0), new byte[]{0})
                .collisionIndices(new int[]{0}, new int[]{0})
                .boundaries(0, 128, 0, 128)
                .spawns(List.of(), List.of(), new RingSpriteSheet(
                        new Pattern[0], List.of(), 1, 8, 0, 0));
    }

    private static byte[][] paletteLines() {
        byte[][] lines = new byte[4][Palette.PALETTE_SIZE_IN_ROM];
        lines[3][3] = 0x0E;
        return lines;
    }

    private static byte[] profiles(byte... values) {
        byte[] profiles = new byte[values.length * 16];
        for (int i = 0; i < values.length; i++) {
            java.util.Arrays.fill(profiles, i * 16, (i + 1) * 16, values[i]);
        }
        return profiles;
    }
}
