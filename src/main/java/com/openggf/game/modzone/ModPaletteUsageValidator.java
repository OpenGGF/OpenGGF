package com.openggf.game.modzone;

import com.openggf.level.resources.IndexedPaletteUsage;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Pattern;

import java.util.Objects;

/** Validates sparse creator palette ownership against reachable indexed level art. */
public final class ModPaletteUsageValidator {
    private static final String FINDING_CODE = "MOD_LEVEL_PALETTE_INVALID";

    private ModPaletteUsageValidator() {}

    public static void validate(String ownerModId, ModZoneLevelData level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 2) {
            throw invalid(ownerModId, "Palette usage validation requires level formatVersion 2");
        }
        if (level.blockGridSide() != 8 && level.blockGridSide() != 16) {
            throw invalid(ownerModId, "blockGridSide must be 8 or 16");
        }
        if (level.width() <= 0 || level.height() <= 0) {
            throw invalid(ownerModId, "width and height must be positive");
        }
        byte[] patterns = level.patternBytes();
        byte[] chunks = level.chunkBytes();
        byte[] blocks = level.blockBytes();
        requireExactLength(ownerModId, "pattern", patterns.length,
                (long) level.patternCount() * Pattern.PATTERN_SIZE_IN_ROM);
        requireExactLength(ownerModId, "chunk", chunks.length,
                (long) level.chunkCount() * Chunk.CHUNK_SIZE_IN_ROM);
        int blockRecordSize = level.blockGridSide() * level.blockGridSide()
                * ChunkDesc.getIndexSize();
        requireExactLength(ownerModId, "block", blocks.length,
                (long) level.blockCount() * blockRecordSize);

        IndexedPaletteUsage.validate(new IndexedPaletteUsage.Input(
                level.width(), level.height(), level.blockGridSide(), level.blockCount(),
                level.chunkCount(), level.patternCount(), level::foregroundMap,
                level::backgroundMap, level::paletteClaims), patterns, chunks, blocks,
                message -> invalid(ownerModId, message));
    }

    private static void requireExactLength(String owner, String label, int actual, long expected) {
        if (expected < 0 || expected > Integer.MAX_VALUE || actual != (int) expected) {
            throw invalid(owner, label + " byte length " + actual
                    + " does not match declared record count");
        }
    }

    private static ModZoneRegistrationException invalid(String owner, String message) {
        return new ModZoneRegistrationException(owner, FINDING_CODE, message, null, null);
    }

}
