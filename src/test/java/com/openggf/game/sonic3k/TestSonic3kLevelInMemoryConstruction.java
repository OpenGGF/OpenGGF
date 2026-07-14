package com.openggf.game.sonic3k;

import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Chunk;
import com.openggf.level.LevelConstants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.mods.code.ModPaletteClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kLevelInMemoryConstruction {

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        RomManager.getInstance().setRom(null);
    }

    @Test
    void buildsS3kLevelWithoutDummyRomAddresses() throws Exception {
        Palette character = characterPalette(0x0EEE);
        ObjectSpawn object = controllerSpawn();
        RingSpawn ring = new RingSpawn(0x345, 0x456, 34);
        RingSpriteSheet ringSheet = ringSheet();
        byte[] preparedBlocks = blocks();
        preparedBlocks[1] = 1;

        Sonic3kLevel level = Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(), preparedBlocks)
                .layout(2, 1, new byte[]{0, 1}, new byte[]{1, 0})
                .characterPalette(character)
                .paletteClaims(List.of(new ModPaletteClaim(1, 0, 0x000E)))
                .solidProfiles(profiles((byte) 3, (byte) 7), profiles((byte) 4, (byte) 8),
                        new byte[]{0, 0x40})
                .collisionIndices(new int[]{0, 1}, new int[]{1, 0})
                .boundaries(0, 800, 0, 224)
                .objectZoneSet(S3kZoneSet.S3KL)
                .spawns(List.of(object), List.of(ring), ringSheet)
                .build();

        assertFalse(RomManager.getInstance().isRomAvailable());
        assertSame(character, level.getPalette(0));
        assertEquals(S3kZoneSet.S3KL, level.getObjectZoneSet());
        assertEquals(2, level.getMap().getWidth());
        assertEquals(1, level.getMap().getHeight());
        assertEquals(2, level.getMap().getLayerCount());
        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(0, 1, 0)));
        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
        assertEquals(2, level.getPatternCount());
        assertEquals(2, level.getChunkCount());
        assertEquals(2, level.getBlockCount());
        assertEquals(8, level.getChunksPerBlockSide());
        assertEquals(1, level.getChunk(1).getPatternDesc(0, 0).getPatternIndex());
        assertEquals(1, level.getBlock(0).getChunkDesc(0, 0).getChunkIndex(),
                "S3K block zero must retain prepared descriptors");
        assertEquals(1, level.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(1, level.getChunk(1).getSolidTileIndex());
        assertEquals(0, level.getChunk(1).getSolidTileAltIndex());
        assertEquals(7, level.getSolidTile(1).getHeightAt((byte) 5));
        assertEquals(8, level.getSolidTile(1).getWidthAt((byte) 5));
        assertEquals((byte) 0x40, level.getSolidTile(1).getAngle());
        assertEquals(0, level.getMinX());
        assertEquals(800, level.getMaxX());
        assertEquals(0, level.getMinY());
        assertEquals(224, level.getMaxY());
        assertEquals(List.of(object), level.getObjects());
        assertEquals(List.of(ring), level.getRings());
        assertSame(ringSheet, level.getRingSpriteSheet());
    }

    @Test
    void snapshotsEveryCallerOwnedArrayAndList() throws Exception {
        byte[] patterns = patterns();
        byte[] chunks = chunks();
        byte[] blocks = blocks();
        byte[] foreground = {1};
        byte[] background = {0};
        byte[] heights = profiles((byte) 5, (byte) 9);
        byte[] widths = profiles((byte) 6, (byte) 10);
        byte[] angles = {7, 11};
        int[] primary = {0, 1};
        int[] secondary = {1, 0};
        ArrayList<ModPaletteClaim> claims = new ArrayList<>(List.of(new ModPaletteClaim(2, 3, 0x00E0)));
        ArrayList<ObjectSpawn> objects = new ArrayList<>(List.of(controllerSpawn()));
        ArrayList<RingSpawn> rings = new ArrayList<>(List.of(new RingSpawn(4, 5, 6)));

        Sonic3kLevel.InMemoryBuilder builder = Sonic3kLevel.inMemoryBuilder(0x40, patterns, chunks, blocks)
                .layout(1, 1, foreground, background)
                .characterPalette(characterPalette(0x0EEE))
                .paletteClaims(claims)
                .solidProfiles(heights, widths, angles)
                .collisionIndices(primary, secondary)
                .boundaries(0, 128, 0, 128)
                .objectZoneSet(S3kZoneSet.SKL)
                .spawns(objects, rings, ringSheet());

        patterns[0] = 0;
        chunks[Chunk.CHUNK_SIZE_IN_ROM + 1] = 0;
        blocks[LevelConstants.BLOCK_SIZE_IN_ROM + 1] = 0;
        foreground[0] = 0;
        background[0] = 1;
        heights[SolidTile.TILE_SIZE_IN_ROM] = 0;
        widths[SolidTile.TILE_SIZE_IN_ROM] = 0;
        angles[1] = 0;
        primary[1] = 0;
        secondary[0] = 0;
        claims.clear();
        objects.clear();
        rings.clear();

        Sonic3kLevel level = builder.build();
        patterns[0] = 0x77;
        foreground[0] = 0;
        heights[SolidTile.TILE_SIZE_IN_ROM] = 0x55;

        assertEquals(1, level.getPattern(0).getPixel(0, 0));
        assertEquals(1, level.getChunk(1).getPatternDesc(0, 0).getPatternIndex());
        assertEquals(1, level.getBlock(1).getChunkDesc(0, 0).getChunkIndex());
        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
        assertEquals(9, level.getSolidTile(1).getHeightAt((byte) 0));
        assertEquals(10, level.getSolidTile(1).getWidthAt((byte) 0));
        assertEquals(11, level.getSolidTile(1).getAngle());
        assertEquals(1, level.getChunk(1).getSolidTileIndex());
        assertEquals(1, level.getChunk(0).getSolidTileAltIndex());
        assertColor(level.getPalette(2), 3, 0x00E0);
        assertEquals(1, level.getObjects().size());
        assertEquals(1, level.getRings().size());
        assertThrows(UnsupportedOperationException.class, () -> level.getObjects().clear());
        assertThrows(UnsupportedOperationException.class, () -> level.getRings().clear());
    }

    @Test
    void rejectsMalformedPreparedRecordsAndMissingReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, new byte[0], chunks(), blocks()));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, new byte[31], chunks(), blocks()));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, patterns(), new byte[7], blocks()));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(), new byte[127]));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40,
                        new byte[2049 * Pattern.PATTERN_SIZE_IN_ROM], chunks(), blocks()));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, patterns(),
                        new byte[1025 * Chunk.CHUNK_SIZE_IN_ROM], blocks()));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(),
                        new byte[257 * LevelConstants.BLOCK_SIZE_IN_ROM]));

        byte[] missingPattern = new byte[Chunk.CHUNK_SIZE_IN_ROM];
        missingPattern[1] = 1;
        assertThrows(IllegalArgumentException.class, () -> validBuilder(
                new byte[Pattern.PATTERN_SIZE_IN_ROM], missingPattern,
                new byte[LevelConstants.BLOCK_SIZE_IN_ROM]).build());

        byte[] missingChunk = new byte[LevelConstants.BLOCK_SIZE_IN_ROM];
        missingChunk[1] = 1;
        assertThrows(IllegalArgumentException.class, () -> validBuilder(
                new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[Chunk.CHUNK_SIZE_IN_ROM],
                missingChunk).build());

        assertThrows(IllegalArgumentException.class, () -> validBuilder(
                new byte[Pattern.PATTERN_SIZE_IN_ROM], new byte[Chunk.CHUNK_SIZE_IN_ROM],
                new byte[LevelConstants.BLOCK_SIZE_IN_ROM])
                .layout(1, 1, new byte[]{1}, null)
                .build());
    }

    @Test
    void rejectsCollisionCountReferencesAndSolidProfileMismatches() {
        Sonic3kLevel.InMemoryBuilder builder = validBuilder(patterns(), chunks(), blocks());
        assertThrows(IllegalArgumentException.class,
                () -> builder.solidProfiles(new byte[17], new byte[16], new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> builder.solidProfiles(new byte[16], new byte[32], new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> builder.solidProfiles(new byte[16], new byte[16], new byte[2]));
        assertThrows(IllegalArgumentException.class,
                () -> builder.solidProfiles(new byte[257 * 16], new byte[257 * 16], new byte[257]));
        assertThrows(IllegalArgumentException.class,
                () -> builder.collisionIndices(new int[]{-1}, new int[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> builder.collisionIndices(new int[]{0x1_0000}, new int[]{0}));

        assertThrows(IllegalArgumentException.class,
                () -> validBuilder(patterns(), chunks(), blocks())
                        .collisionIndices(new int[]{0}, new int[]{0, 0}).build());
        assertThrows(IllegalArgumentException.class,
                () -> validBuilder(patterns(), chunks(), blocks())
                        .collisionIndices(new int[]{0, 2}, new int[]{0, 0}).build());
    }

    @Test
    void validatesLayoutBoundsIdentityAndRequiredFields() {
        Sonic3kLevel.InMemoryBuilder builder = Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(), blocks());
        assertThrows(IllegalArgumentException.class, () -> builder.layout(0, 1, new byte[0], null));
        assertThrows(IllegalArgumentException.class, () -> builder.layout(2, 2, new byte[3], null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.layout(Integer.MAX_VALUE, 2, new byte[0], null));
        assertThrows(IllegalArgumentException.class, () -> builder.boundaries(-1, 10, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> builder.boundaries(10, 9, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> builder.boundaries(0, 10, 5, 4));
        assertThrows(IllegalArgumentException.class,
                () -> builder.boundaries(0, 10, Short.MIN_VALUE - 1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> Sonic3kLevel.inMemoryBuilder(-1, patterns(), chunks(), blocks()));
        assertThrows(NullPointerException.class, () -> builder.objectZoneSet(null));
        assertThrows(IllegalStateException.class,
                () -> Sonic3kLevel.inMemoryBuilder(0x40, patterns(), chunks(), blocks()).build());
    }

    @Test
    void composesSparseCreatorPalettesAndRejectsDuplicateClaims() throws Exception {
        Palette character = characterPalette(0x0EEE);
        Sonic3kLevel level = validBuilder(patterns(), chunks(), blocks())
                .characterPalette(character)
                .paletteClaims(List.of(
                        new ModPaletteClaim(1, 0, 0x000E),
                        new ModPaletteClaim(3, 15, 0x0E00)))
                .build();

        assertSame(character, level.getPalette(0));
        assertColor(level.getPalette(1), 0, 0x000E);
        assertColor(level.getPalette(3), 15, 0x0E00);
        assertColor(level.getPalette(1), 1, 0x0000);
        assertColor(level.getPalette(2), 0, 0x0000);
        assertThrows(IllegalArgumentException.class, () -> new ModPaletteClaim(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> validBuilder(patterns(), chunks(), blocks())
                .paletteClaims(List.of(
                        new ModPaletteClaim(2, 4, 0x000E),
                        new ModPaletteClaim(2, 4, 0x00E0))));
    }

    @Test
    void absentBackgroundCreatesBlankSecondLayerAndZoneSetIsExplicit() throws Exception {
        Sonic3kLevel level = validBuilder(patterns(), chunks(), blocks())
                .layout(2, 1, new byte[]{1, 0}, null)
                .objectZoneSet(S3kZoneSet.SKL)
                .build();

        assertEquals(1, Byte.toUnsignedInt(level.getMap().getValue(0, 0, 0)));
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(1, 0, 0)));
        assertEquals(0, Byte.toUnsignedInt(level.getMap().getValue(1, 1, 0)));
        assertEquals(S3kZoneSet.SKL, level.getObjectZoneSet());
        assertEquals(8, level.getBlock(0).getGridSide());
        assertEquals(128, level.getBlockPixelSize());
    }

    private static Sonic3kLevel.InMemoryBuilder validBuilder(byte[] patterns, byte[] chunks, byte[] blocks) {
        return Sonic3kLevel.inMemoryBuilder(0x40, patterns, chunks, blocks)
                .layout(1, 1, new byte[]{0}, new byte[]{0})
                .characterPalette(characterPalette(0x0EEE))
                .paletteClaims(List.of())
                .solidProfiles(profiles((byte) 0, (byte) 1), profiles((byte) 0, (byte) 1),
                        new byte[]{0, 0})
                .collisionIndices(new int[chunks.length / Chunk.CHUNK_SIZE_IN_ROM],
                        new int[chunks.length / Chunk.CHUNK_SIZE_IN_ROM])
                .boundaries(0, 128, 0, 128)
                .objectZoneSet(S3kZoneSet.S3KL)
                .spawns(List.of(), List.of(), ringSheet());
    }

    private static byte[] patterns() {
        byte[] patterns = new byte[2 * Pattern.PATTERN_SIZE_IN_ROM];
        patterns[0] = 0x12;
        patterns[Pattern.PATTERN_SIZE_IN_ROM] = 0x34;
        return patterns;
    }

    private static byte[] chunks() {
        byte[] chunks = new byte[2 * Chunk.CHUNK_SIZE_IN_ROM];
        chunks[Chunk.CHUNK_SIZE_IN_ROM + 1] = 1;
        return chunks;
    }

    private static byte[] blocks() {
        byte[] blocks = new byte[2 * LevelConstants.BLOCK_SIZE_IN_ROM];
        blocks[LevelConstants.BLOCK_SIZE_IN_ROM + 1] = 1;
        return blocks;
    }

    private static byte[] profiles(byte... values) {
        byte[] profiles = new byte[values.length * SolidTile.TILE_SIZE_IN_ROM];
        for (int i = 0; i < values.length; i++) {
            java.util.Arrays.fill(profiles, i * SolidTile.TILE_SIZE_IN_ROM,
                    (i + 1) * SolidTile.TILE_SIZE_IN_ROM, values[i]);
        }
        return profiles;
    }

    private static Palette characterPalette(int segaColor) {
        Palette palette = new Palette();
        byte[] bytes = new byte[Palette.PALETTE_SIZE_IN_ROM];
        bytes[0] = (byte) (segaColor >>> 8);
        bytes[1] = (byte) segaColor;
        palette.fromSegaFormat(bytes);
        return palette;
    }

    private static ObjectSpawn controllerSpawn() {
        return new ObjectSpawn(0x123, 0x234, 0, 7, 2, true, 0xA234, 12,
                "example-mod", "example-mod:controller");
    }

    private static RingSpriteSheet ringSheet() {
        return new RingSpriteSheet(new Pattern[0], List.of(), 1, 8, 0, 0);
    }

    private static void assertColor(Palette palette, int index, int segaColor) {
        Palette.Color expected = new Palette.Color();
        expected.fromSegaFormat(new byte[]{(byte) (segaColor >>> 8), (byte) segaColor}, 0);
        Palette.Color actual = palette.getColor(index);
        assertEquals(Byte.toUnsignedInt(expected.r), Byte.toUnsignedInt(actual.r));
        assertEquals(Byte.toUnsignedInt(expected.g), Byte.toUnsignedInt(actual.g));
        assertEquals(Byte.toUnsignedInt(expected.b), Byte.toUnsignedInt(actual.b));
    }
}
