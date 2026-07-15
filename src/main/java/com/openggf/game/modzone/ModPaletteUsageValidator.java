package com.openggf.game.modzone;

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

        boolean[] referencedBlocks = new boolean[level.blockCount()];
        markReferencedBlocks(ownerModId, "foreground", level.foregroundMap(), level,
                referencedBlocks);
        level.backgroundMap().ifPresent(background -> markReferencedBlocks(
                ownerModId, "background", background, level, referencedBlocks));
        boolean[] referencedChunks = new boolean[level.chunkCount()];
        for (int block = 0; block < referencedBlocks.length; block++) {
            if (!referencedBlocks[block]) continue;
            int start = block * blockRecordSize;
            for (int offset = 0; offset < blockRecordSize; offset += ChunkDesc.getIndexSize()) {
                int chunk = new ChunkDesc(unsigned16(blocks, start + offset)).getChunkIndex();
                if (chunk >= referencedChunks.length) {
                    throw invalid(ownerModId, "Block " + block + " references missing chunk " + chunk);
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
                    throw invalid(ownerModId, "Chunk " + chunk + " references missing pattern " + pattern);
                }
                uses.add(new PatternUse(pattern, descriptor.getPaletteIndex()));
            }
        }

        Set<Cell> claims = new HashSet<>();
        level.paletteClaims().forEach(claim -> claims.add(new Cell(claim.line(), claim.color())));
        boolean usesBackdrop = false;
        for (PatternUse use : uses) {
            int start = use.patternIndex() * Pattern.PATTERN_SIZE_IN_ROM;
            for (int offset = start; offset < start + Pattern.PATTERN_SIZE_IN_ROM; offset++) {
                int packed = Byte.toUnsignedInt(patterns[offset]);
                usesBackdrop |= validateColor(ownerModId, claims, use.paletteLine(), packed >>> 4);
                usesBackdrop |= validateColor(ownerModId, claims, use.paletteLine(), packed & 0x0F);
            }
        }
        if (usesBackdrop && !claims.contains(new Cell(BACKDROP_LINE, BACKDROP_COLOR))) {
            throw invalid(ownerModId, "Unclaimed indexed color line=" + BACKDROP_LINE
                    + " color=" + BACKDROP_COLOR + " (visible level backdrop)");
        }
    }

    private static boolean validateColor(String owner, Set<Cell> claims, int line, int color) {
        if (color == 0) return true;
        if (line == 0 || !claims.contains(new Cell(line, color))) {
            throw invalid(owner, "Unclaimed indexed color line=" + line + " color=" + color);
        }
        return false;
    }

    private static void markReferencedBlocks(String owner, String layer, byte[] map,
                                             ModZoneLevelData level, boolean[] referenced) {
        long expected = (long) level.width() * level.height();
        if (expected > Integer.MAX_VALUE || map.length != (int) expected) {
            throw invalid(owner, layer + " map byte length does not match dimensions");
        }
        for (byte value : map) {
            int block = Byte.toUnsignedInt(value);
            if (block >= referenced.length) {
                throw invalid(owner, layer + " map references missing block " + block);
            }
            referenced[block] = true;
        }
    }

    private static void requireExactLength(String owner, String label, int actual, long expected) {
        if (expected < 0 || expected > Integer.MAX_VALUE || actual != (int) expected) {
            throw invalid(owner, label + " byte length " + actual
                    + " does not match declared record count");
        }
    }

    private static int unsigned16(byte[] data, int offset) {
        return (Byte.toUnsignedInt(data[offset]) << 8) | Byte.toUnsignedInt(data[offset + 1]);
    }

    private static ModZoneRegistrationException invalid(String owner, String message) {
        return new ModZoneRegistrationException(owner, FINDING_CODE, message, null, null);
    }

    private record Cell(int line, int color) {}
    private record PatternUse(int patternIndex, int paletteLine) {}
}
