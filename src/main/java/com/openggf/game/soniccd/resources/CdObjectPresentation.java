package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePieceRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure completed CPU SAT -> screen-relative immutable picture. No graphics calls, native clocks,
 * admission, slot flags, player state, queue mutation, latching or display publication.
 * Root latches the PREVIOUS CPU SAT at completed NEXT selector-8 V-int with NOW serviced art
 * (VInt_Level, scddisasm 1ecc5f3af, Level/Main.asm:821-834); NORMAL_PAUSE also publishes,
 * PP selector-zero lag retains. This converter neither selects nor performs those transitions.
 *
 * <p>Coordinate policy is bounded normal non-interlaced geometry: SAT X 1..511, Y word 0..511,
 * each minus 128. Every active entry is retained, including outside the viewport. Other Y words
 * reject instead of inventing wrap. This is not general VDP Y/interlace/scanline emulation.</p>
 */
public final class CdObjectPresentation {
    private CdObjectPresentation() { }

    public record TilePattern(int logicalPatternId, SpritePresentation.PatternVersion version) {
        public TilePattern {
            if (logicalPatternId < 0) { throw new IllegalArgumentException("Logical pattern ID must be nonnegative"); }
            Objects.requireNonNull(version, "immutable pattern version");
        }
    }

    /**
     * Resolve only a FINAL native 11-bit pattern ID. Supply a stable source for this conversion.
     * Returned values are copied into the frame, so later DPLC/bank changes cannot alter its pixels.
     * This port does not claim source authentication, residency, a virtual bank or graphics ownership.
     */
    @FunctionalInterface public interface PatternSource { TilePattern resolve(int nativeTileId); }

    /** Pure declared pixel-bank input, not a media/residency certificate. No wrap or unowned padding. */
    public record PixelSpan(int nativeFirstTileId, int logicalFirstPatternId, CdAssetBytes bytes) {
        public PixelSpan {
            Objects.requireNonNull(bytes, "pixel bank bytes");
            int tiles = bytes.length() / 32;
            if (bytes.length() == 0 || bytes.length() % 32 != 0 || nativeFirstTileId < 0
                    || nativeFirstTileId > 2048 - tiles || logicalFirstPatternId < 0
                    || (long) logicalFirstPatternId + tiles - 1 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Pixel span must contain bounded whole native patterns and logical IDs");
            }
        }
    }

    /** Binds immutable pixels once. Overlapping physical OR logical spans reject, including equal bytes. */
    public static PatternSource declaredPixelBank(List<PixelSpan> spans) {
        var patterns = new HashMap<Integer, TilePattern>();
        var logicalIds = new HashSet<Integer>();
        for (var span : List.copyOf(spans)) {
            for (int index = 0; index < span.bytes().length() / 32; index++) {
                int nativeId = span.nativeFirstTileId() + index;
                int logicalId = span.logicalFirstPatternId() + index;
                var tile = new TilePattern(logicalId, patternVersion(span.bytes().slice(index * 32, 32)));
                if (patterns.putIfAbsent(nativeId, tile) != null || !logicalIds.add(logicalId)) {
                    throw new IllegalArgumentException("Overlapping native or logical pixel-bank binding");
                }
            }
        }
        Map<Integer, TilePattern> frozen = Map.copyOf(patterns);
        return nativeId -> {
            if (nativeId < 0 || nativeId > 0x7FF) { throw new IllegalArgumentException("Expected final 11-bit native tile ID"); }
            var tile = frozen.get(nativeId);
            if (tile == null) { throw new UnsupportedOperationException("No admitted source pixels for native tile $"
                    + Integer.toHexString(nativeId)); }
            return tile;
        };
    }

    /**
     * Explicit USA_R11A_OPENING_DEV_PRELOADED_ART policy, not executed native PLC/section residency.
     * Snapshot the supplied immutable NOW-serviced complete 24-tile player window once, alongside
     * the actual initialArtBank Ring/Mosqui source bytes. Native object first IDs come from binding
     * metadata. Player F000/32 is the existing DPLC service's declared VRAM destination, not a slot
     * header inferred during an initial empty SAT. Caller owns stable virtual bank bases and binds
     * this state's world/source; this method makes no GraphicsManager claim or player raw-slot copy.
     */
    public static PatternSource usaR11aOpeningDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                              CdR11aObjectAssets objects,
                                                              int playerLogicalBase, int ringLogicalBase,
                                                              int mosquiLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        // State and bytes are immutable. Retain this one snapshot, never a callback to a live runtime.
        CdAssetBytes playerPixels = nowServicedPlayer.residentBank();
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, playerPixels),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes())));
    }

    /**
     * Active SAT prefix only. Reverse complete native entry order for painter overlap; no ID,
     * player/object, bucket, priority, position or provenance sort. One 4x4 native piece expands
     * to sixteen tiles here AFTER queue capacity was decided. No second cull or admission pass.
     */
    public static SpritePresentation.Frame convert(CdObjectDrawQueue.CpuSat sat, PatternSource source) {
        Objects.requireNonNull(sat, "completed CPU SAT");
        Objects.requireNonNull(source, "immutable pattern source");
        byte[] bytes = sat.bytes();
        var tiles = new ArrayList<SpritePresentation.Tile>();
        var versions = new HashMap<Integer, SpritePresentation.PatternVersion>();
        var nativeSamples = new HashMap<Integer, TilePattern>();
        for (int index = sat.spriteCount() - 1; index >= 0; index--) {
            int at = index * 8;
            int yWord = word(bytes, at), xWord = word(bytes, at + 6);
            int size = bytes[at + 2] & 255, tileWord = word(bytes, at + 4);
            if (xWord < 1 || xWord > 511 || yWord > 511) {
                throw new UnsupportedOperationException("SAT entry " + index
                        + " is outside normal coordinate policy: X=" + xWord + " Y=" + yWord);
            }
            if (size > 15) { throw new UnsupportedOperationException("SAT size high bits are outside bounded mapping policy"); }
            int width = ((size >>> 2) & 3) + 1, height = (size & 3) + 1;
            // Tile word is ALREADY the complete native ADD.W then object-flip XOR result.
            // PreparedPiece avoids preparePiece's separate base/palette combination semantics.
            var piece = new SpritePieceRenderer.PreparedPiece(xWord - 128, yWord - 128, width, height,
                    tileWord & 0x7FF, tileWord & 0x7FF, (tileWord >>> 13) & 3,
                    (tileWord & 0x800) != 0, (tileWord & 0x1000) != 0,
                    (tileWord & 0x8000) != 0, false, 0, height);
            SpritePieceRenderer.renderPreparedPiece(piece, (physicalPattern, hFlip, vFlip, palette, x, y) -> {
                // A multi-tile piece can cross $7FF; mask EACH physical pattern BEFORE bank lookup.
                int nativeId = physicalPattern & 0x7FF;
                TilePattern sample = nativeSamples.get(nativeId);
                if (sample == null) {
                    sample = source.resolve(nativeId);
                    if (sample == null) { throw new UnsupportedOperationException("Pattern source has no tile $"
                            + Integer.toHexString(nativeId)); }
                    nativeSamples.put(nativeId, sample);
                }
                var previous = versions.putIfAbsent(sample.logicalPatternId(), sample.version());
                if (previous != null && !previous.equals(sample.version())) {
                    throw new IllegalArgumentException("Conflicting pixel versions share logical pattern ID "
                            + sample.logicalPatternId() + " in one picture");
                }
                tiles.add(new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, sample.logicalPatternId(),
                        palette, hFlip, vFlip, piece.piecePriority(), x, y, 8, 8,
                        true, 0, false, 1));
            });
        }
        return new SpritePresentation.Frame(tiles, List.of(), versions);
    }

    /** Sega 32-byte pattern -> existing four groups of sixteen palette-index nibbles, no mutable Pattern. */
    public static SpritePresentation.PatternVersion patternVersion(CdAssetBytes rawTile) {
        Objects.requireNonNull(rawTile, "raw pattern bytes");
        if (rawTile.length() != 32) { throw new IllegalArgumentException("One raw Sega pattern requires exactly 32 bytes"); }
        return new SpritePresentation.PatternVersion(packWord(rawTile, 0), packWord(rawTile, 16),
                packWord(rawTile, 32), packWord(rawTile, 48));
    }

    private static long packWord(CdAssetBytes bytes, int firstPixel) {
        long packed = 0;
        for (int i = 0; i < 16; i++) {
            int pixel = firstPixel + i;
            int value = bytes.unsignedByte(pixel >>> 1);
            int nibble = (pixel & 1) == 0 ? value >>> 4 : value & 15;
            packed |= (long) nibble << (i * 4);
        }
        return packed;
    }
    private static int word(byte[] bytes, int at) { return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255); }
}
