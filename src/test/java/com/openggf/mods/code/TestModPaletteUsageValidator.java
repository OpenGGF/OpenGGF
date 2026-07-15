package com.openggf.mods.code;

import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneHostMetadata;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModPaletteUsageValidator {
    private static final String OWNER = "alpha";
    private static final String FINDING_CODE = "MOD_LEVEL_PALETTE_INVALID";

    @Test
    void everyOpaquePatternNibbleNeedsAClaimForItsBlockPaletteLine() {
        ModLevelDefinition definition = fixtureWithBlockPalette(2)
                .patternPixel(0, 0, 0, 5)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void everyReachableBlockChunkPatternAndNonzeroNibbleIsValidated() {
        ModLevelDefinition definition = fixtureWithBlockPalette(1)
                .fillPattern(0, 1)
                .fillPattern(1, 2)
                .fillPattern(2, 3)
                .chunkPattern(0, 0, 0, 1, 0)
                .chunkPattern(0, 1, 1, 2, 0)
                .fillChunk(1, 2, 3, 0)
                .blockChunk(0, 0, 0, 0)
                .blockChunk(1, 0, 1, 0)
                .foregroundBlocks(0)
                .backgroundBlocks(1)
                .claims(new ModPaletteClaim(1, 1, 0x000E),
                        new ModPaletteClaim(2, 2, 0x00E0),
                        new ModPaletteClaim(3, 3, 0x0E00))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));

        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(1)
                .fillPattern(0, 1)
                .fillPattern(1, 2)
                .fillPattern(2, 3)
                .chunkPattern(0, 0, 0, 1, 0)
                .chunkPattern(0, 1, 1, 2, 0)
                .fillChunk(1, 2, 3, 0)
                .blockChunk(0, 0, 0, 0)
                .blockChunk(1, 0, 1, 0)
                .foregroundBlocks(0)
                .backgroundBlocks(1)
                .claims(new ModPaletteClaim(1, 1, 0x000E),
                        new ModPaletteClaim(2, 2, 0x00E0))
                .build());
        assertTrue(failure.getMessage().contains("line=3 color=3"));
    }

    @Test
    void missingClaimRetainsEngineSuppliedOwnerAndStableFindingCode() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(2)
                .fillPattern(0, 6)
                .claims()
                .build());

        assertEquals(OWNER, failure.ownerModId());
        assertEquals(FINDING_CODE, failure.findingCode());
        assertTrue(failure.getMessage().contains("line=2 color=6"));
    }

    @Test
    void characterPaletteLineUseIsRejected() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(0)
                .fillPattern(0, 5)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("line=0 color=5"));
    }

    @Test
    void descriptorFlipsDoNotChangePaletteAssociation() {
        ModLevelDefinition definition = fixtureWithBlockPalette(1)
                .fillPattern(0, 9)
                .fillChunk(0, 0, 3, 0x1800)
                .blockChunk(0, 0, 0, 0x0C00)
                .claims(new ModPaletteClaim(3, 9, 0x0E0E))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void unusedPatternsDoNotDemandClaims() {
        ModLevelDefinition definition = fixtureWithBlockPalette(2)
                .fillPattern(0, 5)
                .fillPattern(1, 7)
                .fillChunk(1, 1, 3, 0)
                .fillBlock(1, 1, 0)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void duplicateReferencesDoNotChangeTheResult() {
        ModLevelDefinition definition = fixtureWithBlockPalette(2)
                .fillPattern(0, 5)
                .fillChunk(0, 0, 2, 0)
                .fillBlock(0, 0, 0)
                .foregroundBlocks(0, 0, 0)
                .backgroundBlocks(0, 0, 0)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void rawOutOfRangeReferencesFailBeforeArrayIndexing() {
        ModRegistrationException mapFailure = assertInvalid(fixtureWithBlockPalette(2)
                .foregroundBlocks(7)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(mapFailure.getMessage().contains("missing block 7"));

        ModRegistrationException blockFailure = assertInvalid(fixtureWithBlockPalette(2)
                .blockChunk(0, 0, 7, 0)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(blockFailure.getMessage().contains("missing chunk 7"));

        ModRegistrationException chunkFailure = assertInvalid(fixtureWithBlockPalette(2)
                .chunkPattern(0, 0, 7, 2, 0)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(chunkFailure.getMessage().contains("missing pattern 7"));
    }

    @Test
    void malformedRawRecordLengthsFailDeterministically() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(2)
                .rawPatterns(new byte[31], 1)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("pattern byte length"));
    }

    @Test
    void reachableZeroNibbleRequiresGlobalLineTwoBackdropClaim() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(1)
                .fillPattern(0, 5)
                .patternPixel(0, 3, 4, 0)
                .claims(new ModPaletteClaim(1, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("line=2 color=0"));
    }

    @Test
    void backdropClaimIsIndependentOfDescriptorPaletteLine() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(3)
                .fillPattern(0, 5)
                .patternPixel(0, 3, 4, 0)
                .claims(new ModPaletteClaim(3, 5, 0x00E0),
                        new ModPaletteClaim(3, 0, 0x0000))
                .build());

        assertTrue(failure.getMessage().contains("line=2 color=0"));
    }

    @Test
    void explicitGlobalBackdropClaimAllowsReachableZeroNibble() {
        ModLevelDefinition definition = fixtureWithBlockPalette(1)
                .fillPattern(0, 5)
                .patternPixel(0, 3, 4, 0)
                .claims(new ModPaletteClaim(1, 5, 0x00E0),
                        new ModPaletteClaim(2, 0, 0x000E))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void zeroNibblesInUnreachablePatternsDoNotDemandBackdropClaim() {
        ModLevelDefinition definition = fixtureWithBlockPalette(2)
                .fillPattern(0, 5)
                .fillPattern(1, 0)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build();

        assertDoesNotThrow(() -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    @Test
    void versionOneIsRejectedInsteadOfInventingSparseClaims() {
        ModRegistrationException failure = assertInvalid(fixtureWithBlockPalette(2)
                .formatVersion(1)
                .claims()
                .build());

        assertTrue(failure.getMessage().contains("formatVersion 2"));
    }

    @Test
    void ownerAndDefinitionAreRequired() {
        ModLevelDefinition definition = fixtureWithBlockPalette(2).build();

        assertThrows(NullPointerException.class,
                () -> ModPaletteUsageValidator.validate(null, definition));
        assertThrows(NullPointerException.class,
                () -> ModPaletteUsageValidator.validate(OWNER, null));
    }

    @Test
    void patternCountAboveGenesisDomainIsRejectedEvenWhenBytesMatch() {
        ModRegistrationException failure = assertDomainInvalid(fixtureWithBlockPalette(2)
                .patternCountWithMatchingBytes(2049)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("patternCount must be in 1..2048"));
    }

    @Test
    void chunkCountAboveFormatDomainIsRejectedEvenWhenBytesMatch() {
        ModRegistrationException failure = assertDomainInvalid(fixtureWithBlockPalette(2)
                .chunkCountWithMatchingBytes(1025)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("chunkCount must be in 1..1024"));
    }

    @Test
    void blockCountAboveMapDomainIsRejectedEvenWhenBytesMatch() {
        ModRegistrationException failure = assertDomainInvalid(fixtureWithBlockPalette(2)
                .blockCountWithMatchingBytes(257)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());

        assertTrue(failure.getMessage().contains("blockCount must be in 1..256"));
    }

    @Test
    void blockGridSideMustBeExactlyEightOrSixteen() {
        ModRegistrationException negative = assertDomainInvalid(fixtureWithBlockPalette(2)
                .blockGridSideWithMatchingBytes(-8)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(negative.getMessage().contains("blockGridSide must be 8 or 16"));

        ModRegistrationException positive = assertDomainInvalid(fixtureWithBlockPalette(2)
                .blockGridSideWithMatchingBytes(4)
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(positive.getMessage().contains("blockGridSide must be 8 or 16"));
    }

    @Test
    void dimensionsMustEachBePositiveBeforeTheirProductIsUsed() {
        ModRegistrationException pairedNegative = assertDomainInvalid(fixtureWithBlockPalette(2)
                .dimensions(-1, -1, new byte[]{0})
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(pairedNegative.getMessage().contains("width and height must be positive"));

        ModRegistrationException zero = assertDomainInvalid(fixtureWithBlockPalette(2)
                .dimensions(0, 1, new byte[0])
                .claims(new ModPaletteClaim(2, 5, 0x00E0))
                .build());
        assertTrue(zero.getMessage().contains("width and height must be positive"));
    }

    private static Fixture fixtureWithBlockPalette(int paletteLine) {
        return new Fixture().fillPattern(0, 5)
                .fillChunk(0, 0, paletteLine, 0)
                .fillBlock(0, 0, 0)
                .foregroundBlocks(0);
    }

    private static ModRegistrationException assertInvalid(ModLevelDefinition definition) {
        return assertThrows(ModRegistrationException.class,
                () -> ModPaletteUsageValidator.validate(OWNER, definition));
    }

    private static ModRegistrationException assertDomainInvalid(ModLevelDefinition definition) {
        ModRegistrationException failure = assertInvalid(definition);
        assertEquals(OWNER, failure.ownerModId());
        assertEquals(FINDING_CODE, failure.findingCode());
        return failure;
    }

    private static final class Fixture {
        private static final int PATTERN_RECORD_SIZE = 32;
        private static final int CHUNK_RECORD_SIZE = 8;
        private static final int BLOCK_GRID_SIDE = 8;
        private static final int BLOCK_RECORD_SIZE = BLOCK_GRID_SIDE * BLOCK_GRID_SIDE * 2;

        private int formatVersion = 2;
        private byte[] patterns = new byte[3 * PATTERN_RECORD_SIZE];
        private byte[] chunks = new byte[3 * CHUNK_RECORD_SIZE];
        private byte[] blocks = new byte[3 * BLOCK_RECORD_SIZE];
        private byte[] foreground = {0};
        private byte[] background;
        private int blockGridSide = BLOCK_GRID_SIDE;
        private int width = 1;
        private int height = 1;
        private int patternCount = 3;
        private int chunkCount = 3;
        private int blockCount = 3;
        private List<ModPaletteClaim> claims = List.of();

        Fixture formatVersion(int value) {
            formatVersion = value;
            return this;
        }

        Fixture claims(ModPaletteClaim... values) {
            claims = List.of(values);
            return this;
        }

        Fixture rawPatterns(byte[] values, int count) {
            patterns = values.clone();
            patternCount = count;
            return this;
        }

        Fixture patternCountWithMatchingBytes(int count) {
            patterns = Arrays.copyOf(patterns, count * PATTERN_RECORD_SIZE);
            patternCount = count;
            return this;
        }

        Fixture chunkCountWithMatchingBytes(int count) {
            chunks = Arrays.copyOf(chunks, count * CHUNK_RECORD_SIZE);
            chunkCount = count;
            return this;
        }

        Fixture blockCountWithMatchingBytes(int count) {
            blocks = Arrays.copyOf(blocks, count * blockRecordSize(blockGridSide));
            blockCount = count;
            return this;
        }

        Fixture blockGridSideWithMatchingBytes(int value) {
            blockGridSide = value;
            blocks = Arrays.copyOf(blocks, blockCount * blockRecordSize(value));
            return this;
        }

        Fixture dimensions(int width, int height, byte[] map) {
            this.width = width;
            this.height = height;
            foreground = map.clone();
            background = null;
            return this;
        }

        Fixture fillPattern(int pattern, int color) {
            Arrays.fill(patterns, pattern * PATTERN_RECORD_SIZE,
                    (pattern + 1) * PATTERN_RECORD_SIZE, (byte) ((color << 4) | color));
            return this;
        }

        Fixture patternPixel(int pattern, int x, int y, int color) {
            int offset = pattern * PATTERN_RECORD_SIZE + y * 4 + x / 2;
            int packed = Byte.toUnsignedInt(patterns[offset]);
            patterns[offset] = (byte) ((x & 1) == 0
                    ? (packed & 0x0F) | (color << 4)
                    : (packed & 0xF0) | color);
            return this;
        }

        Fixture fillChunk(int chunk, int pattern, int paletteLine, int flags) {
            for (int slot = 0; slot < 4; slot++) {
                chunkPattern(chunk, slot, pattern, paletteLine, flags);
            }
            return this;
        }

        Fixture chunkPattern(int chunk, int slot, int pattern, int paletteLine, int flags) {
            int descriptor = flags | (paletteLine << 13) | pattern;
            writeWord(chunks, chunk * CHUNK_RECORD_SIZE + slot * 2, descriptor);
            return this;
        }

        Fixture fillBlock(int block, int chunk, int flags) {
            for (int slot = 0; slot < BLOCK_GRID_SIDE * BLOCK_GRID_SIDE; slot++) {
                blockChunk(block, slot, chunk, flags);
            }
            return this;
        }

        Fixture blockChunk(int block, int slot, int chunk, int flags) {
            writeWord(blocks, block * BLOCK_RECORD_SIZE + slot * 2, flags | chunk);
            return this;
        }

        Fixture foregroundBlocks(int... values) {
            foreground = bytes(values);
            width = foreground.length;
            height = 1;
            return this;
        }

        Fixture backgroundBlocks(int... values) {
            background = bytes(values);
            return this;
        }

        ModLevelDefinition build() {
            return new ModLevelDefinition(formatVersion, "Palette Test", 0x40, 0x400,
                    blockGridSide, width, height,
                    new ModLevelDefinition.Bounds(0, 1024, 0, 224),
                    new ModLevelDefinition.Start(32, 32),
                    new ModLevelDefinition.StockMusic(1), List.of(), List.of(),
                    patterns, chunks, blocks, foreground, background,
                    new byte[16], new byte[16], new byte[1],
                    new int[chunkCount], new int[chunkCount], new byte[0][],
                    patternCount, chunkCount, blockCount, 1,
                    new ModZoneHostMetadata(ModObjectZoneSet.S3KL),
                    claims);
        }

        private static int blockRecordSize(int gridSide) {
            return Math.abs(gridSide) * Math.abs(gridSide) * 2;
        }

        private static byte[] bytes(int[] values) {
            byte[] result = new byte[values.length];
            for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
            return result;
        }

        private static void writeWord(byte[] destination, int offset, int value) {
            destination[offset] = (byte) (value >>> 8);
            destination[offset + 1] = (byte) value;
        }
    }
}
