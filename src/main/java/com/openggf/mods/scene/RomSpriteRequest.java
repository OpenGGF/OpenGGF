package com.openggf.mods.scene;

/**
 * Where a sprite lives in the ROM: its tile art, its mapping table and, for art that is
 * streamed per frame, its DPLC table. Addresses come from the game's disassembly (labels
 * such as {@code ArtKosM_Rhinobot} and {@code Map_Rhinobot}).
 *
 * <p>{@code paletteLine} is the line the object's {@code art_tile} selects (0-3); each
 * mapping piece adds its own line on top. {@code tileOffset} is subtracted from mapping
 * tile indices: use it when the mappings expect the art at a different VRAM tile than the
 * object's {@code art_tile} (it may be negative). Pieces whose tiles fall outside the art are
 * skipped. {@code artSize} is only needed for uncompressed art.
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
        int tileOffset) {

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

    public RomSpriteRequest {
        if (artAddress < 0 || mappingAddress < 0) {
            throw new IllegalArgumentException("Negative ROM address");
        }
        if (compression == null) {
            throw new IllegalArgumentException("compression is required");
        }
        if (compression == Compression.UNCOMPRESSED && artSize <= 0) {
            throw new IllegalArgumentException("Uncompressed art needs its size in bytes");
        }
        if (paletteLine < 0 || paletteLine > 3) {
            throw new IllegalArgumentException("paletteLine must be 0-3");
        }
        if (dplcLayout == null) {
            dplcLayout = DplcLayout.OBJECT;
        }
    }

    /** Compressed art with mappings and no DPLC. */
    public static RomSpriteRequest of(int artAddress, Compression compression, int mappingAddress, int paletteLine) {
        return new RomSpriteRequest(artAddress, compression, 0, mappingAddress, -1, DplcLayout.OBJECT, paletteLine, 0);
    }

    /** Uncompressed art streamed through a DPLC table (some badniks, shields). */
    public static RomSpriteRequest streamed(int artAddress, int artSize, int mappingAddress, int dplcAddress,
            DplcLayout layout, int paletteLine) {
        return new RomSpriteRequest(artAddress, Compression.UNCOMPRESSED, artSize, mappingAddress, dplcAddress, layout,
                paletteLine, 0);
    }

    public RomSpriteRequest withTileOffset(int offset) {
        return new RomSpriteRequest(artAddress, compression, artSize, mappingAddress, dplcAddress, dplcLayout,
                paletteLine, offset);
    }

    public boolean hasDplc() {
        return dplcAddress >= 0;
    }
}
