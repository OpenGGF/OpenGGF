package com.openggf.mods.code;

import com.openggf.level.resources.IndexedPaletteUsage;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Pattern;

import java.util.Objects;

/** Validates sparse creator palette ownership against reachable indexed level art. */
public final class ModPaletteUsageValidator {
    private static final String FINDING_CODE = "MOD_LEVEL_PALETTE_INVALID";

    private ModPaletteUsageValidator() {}

    /**
     * Validates format-v2 indexed art before a host publishes the level.
     *
     * <p>The owner is supplied by the engine registration transaction rather than creator data.
     * Pattern color zero is transparent and descriptor-line-independent, so any reachable zero
     * nibble conservatively requires ownership of the global level backdrop at line 2, color 0.</p>
     */
    public static void validate(String ownerModId, ModLevelDefinition level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 2) {
            throw invalid(ownerModId, "Palette usage validation requires level formatVersion 2");
        }
        validateCanonicalDomains(ownerModId, level);

        byte[] patterns = level.patternBytes();
        byte[] chunks = level.chunkBytes();
        byte[] blocks = level.blockBytes();
        validateRawLengths(ownerModId, level, patterns, chunks, blocks);

        IndexedPaletteUsage.validate(new IndexedPaletteUsage.Input(
                level.width(), level.height(), level.blockGridSide(), level.blockCount(),
                level.chunkCount(), level.patternCount(), level::foregroundMap,
                level::backgroundMap, level::paletteClaims), patterns, chunks, blocks,
                message -> invalid(ownerModId, message));
    }

    private static void validateCanonicalDomains(String ownerModId, ModLevelDefinition level) {
        if (level.blockGridSide() != 8 && level.blockGridSide() != 16) {
            throw invalid(ownerModId, "blockGridSide must be 8 or 16");
        }
        if (level.width() <= 0 || level.height() <= 0) {
            throw invalid(ownerModId, "width and height must be positive");
        }
        long mapCells = (long) level.width() * level.height();
        if (mapCells <= 0 || mapCells > Integer.MAX_VALUE) {
            throw invalid(ownerModId, "map cell count must fit a positive int");
        }
        if (level.patternCount() < 1 || level.patternCount() > 2048) {
            throw invalid(ownerModId, "patternCount must be in 1..2048");
        }
        if (level.chunkCount() < 1 || level.chunkCount() > 1024) {
            throw invalid(ownerModId, "chunkCount must be in 1..1024");
        }
        if (level.blockCount() < 1 || level.blockCount() > 256) {
            throw invalid(ownerModId, "blockCount must be in 1..256");
        }
    }

    private static void validateRawLengths(String ownerModId, ModLevelDefinition level,
                                           byte[] patterns, byte[] chunks, byte[] blocks) {
        requireExactLength(ownerModId, "pattern", patterns.length,
                (long) level.patternCount() * Pattern.PATTERN_SIZE_IN_ROM);
        requireExactLength(ownerModId, "chunk", chunks.length,
                (long) level.chunkCount() * Chunk.CHUNK_SIZE_IN_ROM);
        long blockRecordSize = (long) level.blockGridSide() * level.blockGridSide()
                * ChunkDesc.getIndexSize();
        requireExactLength(ownerModId, "block", blocks.length,
                (long) level.blockCount() * blockRecordSize);
    }

    private static void requireExactLength(String ownerModId, String label, int actual,
                                           long expected) {
        if (expected < 0 || expected > Integer.MAX_VALUE || actual != (int) expected) {
            throw invalid(ownerModId, label + " byte length " + actual
                    + " does not match declared record count");
        }
    }

    private static ModRegistrationException invalid(String ownerModId, String message) {
        return new ModRegistrationException(ownerModId, FINDING_CODE, message, null, null);
    }

}
