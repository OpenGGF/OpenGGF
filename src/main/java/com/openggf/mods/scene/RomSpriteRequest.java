package com.openggf.mods.scene;

/**
 * Where a sprite lives in the ROM: its tile art, its mapping table and, for art that is
 * streamed per frame, its DPLC table ({@code dplcAddress} {@code -1} for none). Build one with a
 * factory: {@link #of} (compressed art, no DPLC), {@link #uncompressed} (no DPLC),
 * {@link #streamed} (uncompressed with a DPLC) or {@link #compressedWithDplc}. Addresses come from the game's disassembly (labels
 * such as {@code ArtKosM_AIZ_Bloominator} and {@code Map_Bloominator}).
 *
 * <p>{@code paletteLine} is the line the object's {@code art_tile} selects (0-3); each
 * mapping piece adds its own line on top. {@code tileOffset} is subtracted from mapping
 * tile indices: use it when the mappings expect the art at a different VRAM tile than the
 * object's {@code art_tile} (it may be negative). Pieces whose tiles fall outside the art are
 * skipped. {@code artSize} is only needed for uncompressed art.
 *
 * <p>{@code mappingFrameCount} defaults to 0 (infer from the first pointer). For tables
 * whose frame data is reordered or shared, use {@link #withMappingFrameCount(int)} with
 * the number of pointers verified in the disassembly; the first pointer need not mark
 * the end of the table. Explicit counts are bounded to 1-512.
 *
 * <p>Sonic 3 &amp; Knuckles has two DPLC layouts: objects (Rhinobot, the signpost) use
 * {@link DplcLayout#OBJECT}, while the players, Tails' tails and the shields use
 * {@link DplcLayout#PLAYER}. Sonic 1 and 2 have one layout each; the field is ignored there.
 *
 * <pre>{@code
 * // Bloominator: ArtKosM_AIZ_Bloominator / Map_Bloominator, art_tile palette line 1.
 * RomSpriteRequest.of(0x367DCA, RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x3616C0, 1)
 * }</pre>
 */
@com.openggf.game.ModApi
public record RomSpriteRequest(
        int artAddress,
        Compression compression,
        int artSize,
        int mappingAddress,
        int dplcAddress,
        DplcLayout dplcLayout,
        int paletteLine,
        int tileOffset,
        int mappingFrameCount) {

    /** Retains the original constructor, with automatic mapping frame-count inference. */
    public RomSpriteRequest(int artAddress, Compression compression, int artSize, int mappingAddress,
            int dplcAddress, DplcLayout dplcLayout, int paletteLine, int tileOffset) {
        this(artAddress, compression, artSize, mappingAddress, dplcAddress, dplcLayout,
                paletteLine, tileOffset, 0);
    }

    /** Which dynamic pattern load cue layout {@code dplcAddress} uses (S3K only). */
    @com.openggf.game.ModApi
    public enum DplcLayout {
        /** {@code Perform_DPLC}: entry count minus one, then {@code (startTile << 4) | (count - 1)}. */
        OBJECT,
        /** Player and shield cues: entry count, then {@code ((count - 1) << 12) | startTile}. */
        PLAYER
    }

    /** How the tile art is stored. */
    @com.openggf.game.ModApi
    public enum Compression {
        UNCOMPRESSED,
        NEMESIS,
        KOSINSKI,
        KOSINSKI_MODULED
    }

    /**
     * Checks every field.
     *
     * @throws IllegalArgumentException for a negative art or mapping address, a
     *                                  {@code dplcAddress} below {@code -1}, a null compression or
     *                                  layout, uncompressed art without a positive size, compressed
     *                                  art with a size, or a palette line outside 0-3
     */
    public RomSpriteRequest {
        if (artAddress < 0 || mappingAddress < 0) {
            throw new IllegalArgumentException("Negative ROM address");
        }
        if (dplcAddress < -1) {
            throw new IllegalArgumentException("dplcAddress must be a ROM address, or -1 for no DPLC");
        }
        if (compression == null) {
            throw new IllegalArgumentException("compression is required");
        }
        if (dplcLayout == null) {
            throw new IllegalArgumentException("dplcLayout is required (OBJECT when there is no DPLC)");
        }
        if (compression == Compression.UNCOMPRESSED && artSize <= 0) {
            throw new IllegalArgumentException("Uncompressed art needs its size in bytes");
        }
        if (compression != Compression.UNCOMPRESSED && artSize != 0) {
            throw new IllegalArgumentException("Compressed art carries its own size; pass 0");
        }
        if (mappingFrameCount < 0 || mappingFrameCount > 512) {
            throw new IllegalArgumentException("mappingFrameCount must be 0 (infer) or 1-512");
        }
        if (paletteLine < 0 || paletteLine > 3) {
            throw new IllegalArgumentException("paletteLine must be 0-3");
        }
    }

    /** Compressed art (Nemesis, Kosinski or Kosinski Moduled) with mappings and no DPLC. */
    public static RomSpriteRequest of(int artAddress, Compression compression, int mappingAddress, int paletteLine) {
        return new RomSpriteRequest(artAddress, compression, 0, mappingAddress, -1, DplcLayout.OBJECT, paletteLine, 0);
    }

    /** Uncompressed art of {@code artSize} bytes with mappings and no DPLC. */
    public static RomSpriteRequest uncompressed(int artAddress, int artSize, int mappingAddress, int paletteLine) {
        return new RomSpriteRequest(artAddress, Compression.UNCOMPRESSED, artSize, mappingAddress, -1,
                DplcLayout.OBJECT, paletteLine, 0);
    }

    /** Uncompressed art streamed through a DPLC table (some badniks, shields). */
    public static RomSpriteRequest streamed(int artAddress, int artSize, int mappingAddress, int dplcAddress,
            DplcLayout layout, int paletteLine) {
        return new RomSpriteRequest(artAddress, Compression.UNCOMPRESSED, artSize, mappingAddress, dplcAddress, layout,
                paletteLine, 0);
    }

    /** Compressed art whose frames pick their tiles through a DPLC table. */
    public static RomSpriteRequest compressedWithDplc(int artAddress, Compression compression, int mappingAddress,
            int dplcAddress, DplcLayout layout, int paletteLine) {
        return new RomSpriteRequest(artAddress, compression, 0, mappingAddress, dplcAddress, layout, paletteLine, 0);
    }

    /** The same request with {@code tileOffset} replaced. */
    public RomSpriteRequest withTileOffset(int offset) {
        return new RomSpriteRequest(artAddress, compression, artSize, mappingAddress, dplcAddress, dplcLayout,
                paletteLine, offset, mappingFrameCount);
    }

    /** The same request with an explicit number of mapping pointers, or 0 to infer it. */
    public RomSpriteRequest withMappingFrameCount(int count) {
        return new RomSpriteRequest(artAddress, compression, artSize, mappingAddress, dplcAddress, dplcLayout,
                paletteLine, tileOffset, count);
    }

    /** True when {@code dplcAddress} names a DPLC table (it is not {@code -1}). */
    public boolean hasDplc() {
        return dplcAddress >= 0;
    }
}
