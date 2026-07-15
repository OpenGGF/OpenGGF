package com.openggf.mods.code;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates sparse creator palette ownership against reachable indexed level art. */
public final class ModPaletteUsageValidator {
    private static final String FINDING_CODE = "MOD_LEVEL_PALETTE_INVALID";
    private static final int BACKDROP_LINE = 2;
    private static final int BACKDROP_COLOR = 0;

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

        boolean[] referencedBlocks = new boolean[level.blockCount()];
        markReferencedBlocks(ownerModId, "foreground", level.foregroundMap(), level,
                referencedBlocks);
        level.backgroundMap().ifPresent(background -> markReferencedBlocks(ownerModId,
                "background", background, level, referencedBlocks));

        boolean[] referencedChunks = new boolean[level.chunkCount()];
        int blockRecordSize = level.blockGridSide() * level.blockGridSide()
                * ChunkDesc.getIndexSize();
        for (int block = 0; block < referencedBlocks.length; block++) {
            if (!referencedBlocks[block]) continue;
            int start = block * blockRecordSize;
            for (int offset = 0; offset < blockRecordSize; offset += ChunkDesc.getIndexSize()) {
                ChunkDesc descriptor = new ChunkDesc(unsigned16(blocks, start + offset));
                int chunk = descriptor.getChunkIndex();
                if (chunk >= referencedChunks.length) {
                    throw invalid(ownerModId, "Block " + block + " references missing chunk "
                            + chunk);
                }
                referencedChunks[chunk] = true;
            }
        }

        List<PatternUse> uses = new ArrayList<>();
        for (int chunk = 0; chunk < referencedChunks.length; chunk++) {
            if (!referencedChunks[chunk]) continue;
            int start = chunk * Chunk.CHUNK_SIZE_IN_ROM;
            for (int offset = 0; offset < Chunk.CHUNK_SIZE_IN_ROM;
                 offset += PatternDesc.getIndexSize()) {
                PatternDesc descriptor = new PatternDesc(unsigned16(chunks, start + offset));
                int pattern = descriptor.getPatternIndex();
                if (pattern >= level.patternCount()) {
                    throw invalid(ownerModId, "Chunk " + chunk + " references missing pattern "
                            + pattern);
                }
                uses.add(new PatternUse(pattern, descriptor.getPaletteIndex()));
            }
        }

        Set<Cell> claims = new HashSet<>();
        for (ModPaletteClaim claim : level.paletteClaims()) {
            claims.add(new Cell(claim.line(), claim.color()));
        }

        boolean usesBackdrop = false;
        for (PatternUse use : uses) {
            int start = use.patternIndex() * Pattern.PATTERN_SIZE_IN_ROM;
            int end = start + Pattern.PATTERN_SIZE_IN_ROM;
            for (int offset = start; offset < end; offset++) {
                int packed = Byte.toUnsignedInt(patterns[offset]);
                int high = packed >>> 4;
                int low = packed & 0x0F;
                usesBackdrop |= validateColor(ownerModId, claims, use.paletteLine(), high);
                usesBackdrop |= validateColor(ownerModId, claims, use.paletteLine(), low);
            }
        }
        if (usesBackdrop && !claims.contains(new Cell(BACKDROP_LINE, BACKDROP_COLOR))) {
            throw invalid(ownerModId, "Unclaimed indexed color line=" + BACKDROP_LINE
                    + " color=" + BACKDROP_COLOR + " (visible level backdrop)");
        }
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

    private static boolean validateColor(String ownerModId, Set<Cell> claims, int paletteLine,
                                         int color) {
        if (color == 0) return true;
        if (paletteLine == 0 || !claims.contains(new Cell(paletteLine, color))) {
            throw invalid(ownerModId, "Unclaimed indexed color line=" + paletteLine
                    + " color=" + color);
        }
        return false;
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

    private static void markReferencedBlocks(String ownerModId, String layer, byte[] map,
                                             ModLevelDefinition level,
                                             boolean[] referencedBlocks) {
        long expectedCells = (long) level.width() * level.height();
        if (expectedCells < 0 || expectedCells > Integer.MAX_VALUE
                || map.length != (int) expectedCells) {
            throw invalid(ownerModId, layer + " map byte length does not match dimensions");
        }
        for (byte value : map) {
            int block = Byte.toUnsignedInt(value);
            if (block >= referencedBlocks.length) {
                throw invalid(ownerModId, layer + " map references missing block " + block);
            }
            referencedBlocks[block] = true;
        }
    }

    private static int unsigned16(byte[] data, int offset) {
        return (Byte.toUnsignedInt(data[offset]) << 8) | Byte.toUnsignedInt(data[offset + 1]);
    }

    private static ModRegistrationException invalid(String ownerModId, String message) {
        return new ModRegistrationException(ownerModId, FINDING_CODE, message, null, null);
    }

    private record Cell(int line, int color) {}
    private record PatternUse(int patternIndex, int paletteLine) {}
}
