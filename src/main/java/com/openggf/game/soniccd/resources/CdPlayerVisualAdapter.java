package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.art.SpriteArtSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CD-private, stateless conversion of decoded Sonic visuals and explicit art residency.
 * Production MMD/release authentication remains with the loader. No animation, cue
 * selection, V-int service, graphics submission, initial state or world binding occurs.
 *
 * <p>Mapping lists produced here are immutable at both levels. Pattern arrays and
 * Patterns are mutable engine data, freshly allocated on every conversion/factory
 * call; owners must not share them as immutable snapshots. The immutable source
 * assets/state are never modified and no mutable data is cached across worlds.</p>
 *
 * <p>Source pin: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * DrawObjects/.DrawSprite (Level/Object Functions.asm:327-343) selects a relative
 * frame and byte piece count; DrawSprite (:382-420) reads five-byte pieces in order.
 * DrawSprite_FlipX (:440-459) and DrawSprite_FlipY (:480-487) establish size axes.
 * This translates mapping data, not the complete native SAT/culling behavior.</p>
 */
public final class CdPlayerVisualAdapter {
    private static final int RESIDENT_TILES =
            CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES / Pattern.PATTERN_SIZE_IN_ROM;

    private CdPlayerVisualAdapter() { }

    /**
     * Preserves all 198 indexed frames, including empty frames and relative-pointer
     * aliases, and native piece order. The existing decoder owns table/body bounds.
     * Tile indices remain logical offsets; neither native $780 nor an atlas base is added.
     */
    public static List<SpriteMappingFrame> mappingFrames(CdSonicVisualAssets assets) {
        Objects.requireNonNull(assets, "assets");
        var frames = new ArrayList<SpriteMappingFrame>(assets.mappingFrames().size());
        for (var nativeFrame : assets.mappingFrames()) {
            var pieces = new ArrayList<SpriteMappingPiece>(nativeFrame.pieceCount());
            CdAssetBytes bytes = nativeFrame.bytes();
            for (int piece = 0; piece < nativeFrame.pieceCount(); piece++) {
                int offset = 1 + piece * 5;
                int size = bytes.unsignedByte(offset + 1);
                int tileWord = bytes.unsignedWord(offset + 2);
                pieces.add(new SpriteMappingPiece(
                        bytes.signedByte(offset + 4), bytes.signedByte(offset),
                        ((size >>> 2) & 3) + 1, (size & 3) + 1,
                        tileWord & 0x7FF, (tileWord & 0x800) != 0,
                        (tileWord & 0x1000) != 0, (tileWord >>> 13) & 3,
                        (tileWord & 0x8000) != 0));
            }
            frames.add(new SpriteMappingFrame(List.copyOf(pieces)));
        }
        return List.copyOf(frames);
    }

    /** All 2171 source tiles, using the decoder's actual 69471 art bytes plus its EVEN byte. */
    public static Pattern[] sourcePatterns(CdSonicVisualAssets assets) {
        return patterns(Objects.requireNonNull(assets, "assets").artReadView());
    }

    /**
     * All 24 resident tiles from the supplied complete state, including the tile
     * outside the native $2E0-byte DMA. Uses resident bytes, never pending CPU RAM;
     * dirty/lastFrame are neither serviced nor interpreted as renderer cache state.
     */
    public static Pattern[] residentPatterns(CdPlayerArtRuntime.State state) {
        return patterns(Objects.requireNonNull(state, "state").residentBank());
    }

    /**
     * Data-only bundle with a 24-tile logical bank at a caller-owned virtual base.
     * This base is an engine atlas index, not native VRAM $F000 (tile $780).
     * Palette zero is neutral for the retained per-piece attributes; delay zero and
     * null animation metadata install no CD animation or clock.
     *
     * <p>Renderer DPLC metadata is deliberately empty. Native cues remain in
     * CdSonicVisualAssets and are serviced only by CdPlayerArtRuntime, not by Draw
     * or a forced first-frame load. This factory supplies source art, not residency.
     * A caller creating a PlayerSpriteRenderer must inject its actual GraphicsManager,
     * claim the bank with PlayerResidentBanks before first Draw, and restore all
     * 24 tiles from explicit state. Renderer cache lastFrame is separate from the
     * native lastFrame byte. This method creates no renderer and performs no restore.</p>
     *
     * @throws IllegalArgumentException if the complete virtual bank would overflow
     */
    public static SpriteArtSet artSet(CdSonicVisualAssets assets, int virtualBasePatternIndex) {
        Objects.requireNonNull(assets, "assets");
        if (virtualBasePatternIndex < 0
                || virtualBasePatternIndex > Integer.MAX_VALUE - (RESIDENT_TILES - 1)) {
            throw new IllegalArgumentException("The complete CD player virtual bank must fit nonnegative indices");
        }
        return new SpriteArtSet(sourcePatterns(assets), mappingFrames(assets), List.of(),
                0, virtualBasePatternIndex, 0, RESIDENT_TILES, null, null);
    }

    private static Pattern[] patterns(CdAssetBytes bytes) {
        // Only complete, already-bounded source/resident views enter this helper.
        int tileBytes = Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] result = new Pattern[bytes.length() / tileBytes];
        for (int tile = 0; tile < result.length; tile++) {
            result[tile] = new Pattern();
            result[tile].fromSegaFormat(bytes.slice(tile * tileBytes, tileBytes).copy());
        }
        return result;
    }
}
