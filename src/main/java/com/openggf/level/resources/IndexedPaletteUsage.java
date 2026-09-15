package com.openggf.level.resources;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.Function;

/** Internal reachable indexed-art validation shared by the two registration boundaries. */
public final class IndexedPaletteUsage {
    private static final int BACKDROP_LINE = 2;
    private static final int BACKDROP_COLOR = 0;
    private IndexedPaletteUsage() {}

    /** Maps and claims remain lazy so validation retains its original failure order. */
    public record Input(int width, int height, int blockGridSide, int blockCount,
                        int chunkCount, int patternCount,
                        Supplier<byte[]> foregroundMap, Supplier<Optional<byte[]>> backgroundMap,
                        Supplier<List<ModPaletteClaim>> paletteClaims) {}

    public static void validate(Input level, byte[] patterns, byte[] chunks, byte[] blocks,
                                Function<String, ? extends RuntimeException> invalid) {
        boolean[] referencedBlocks = new boolean[level.blockCount()];
        markReferencedBlocks(invalid, "foreground", level.foregroundMap().get(), level,
                referencedBlocks);
        level.backgroundMap().get().ifPresent(background -> markReferencedBlocks(invalid,
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
                    throw invalid.apply("Block " + block + " references missing chunk "
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
                    throw invalid.apply("Chunk " + chunk + " references missing pattern "
                            + pattern);
                }
                uses.add(new PatternUse(pattern, descriptor.getPaletteIndex()));
            }
        }

        Set<Cell> claims = new HashSet<>();
        for (ModPaletteClaim claim : level.paletteClaims().get()) {
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
                usesBackdrop |= validateColor(invalid, claims, use.paletteLine(), high);
                usesBackdrop |= validateColor(invalid, claims, use.paletteLine(), low);
            }
        }
        if (usesBackdrop && !claims.contains(new Cell(BACKDROP_LINE, BACKDROP_COLOR))) {
            throw invalid.apply("Unclaimed indexed color line=" + BACKDROP_LINE
                    + " color=" + BACKDROP_COLOR + " (visible level backdrop)");
        }
    }

    private static boolean validateColor(Function<String, ? extends RuntimeException> invalid,
                                         Set<Cell> claims, int paletteLine,
                                         int color) {
        if (color == 0) return true;
        if (paletteLine == 0 || !claims.contains(new Cell(paletteLine, color))) {
            throw invalid.apply("Unclaimed indexed color line=" + paletteLine
                    + " color=" + color);
        }
        return false;
    }

    private static void markReferencedBlocks(Function<String, ? extends RuntimeException> invalid,
                                             String layer, byte[] map,
                                             Input level,
                                             boolean[] referencedBlocks) {
        long expectedCells = (long) level.width() * level.height();
        if (expectedCells < 0 || expectedCells > Integer.MAX_VALUE
                || map.length != (int) expectedCells) {
            throw invalid.apply(layer + " map byte length does not match dimensions");
        }
        for (byte value : map) {
            int block = Byte.toUnsignedInt(value);
            if (block >= referencedBlocks.length) {
                throw invalid.apply(layer + " map references missing block " + block);
            }
            referencedBlocks[block] = true;
        }
    }

    private static int unsigned16(byte[] data, int offset) {
        return (Byte.toUnsignedInt(data[offset]) << 8) | Byte.toUnsignedInt(data[offset + 1]);
    }

    private record Cell(int line, int color) {}
    private record PatternUse(int patternIndex, int paletteLine) {}
}
