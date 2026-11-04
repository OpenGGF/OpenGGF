package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Detached USA Palmtree Panic Act 1 Good Future terrain from the actual disc pipeline.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Act 1 Good Future.asm:7-25; Data (Act 1 Good Future).asm:11-20 and its complete
 * LevelChunks/LevelCollision/LevelLayouts/LevelBlocks/Art_LevelTiles source blobs;
 * Load Level Data.asm:11-29,57-98. Original USA loader $203358 selects index $20ECF6;
 * layout loader $2033D4 selects $23B594. Full source blobs were independently matched
 * to the original R11C file, not inferred from R11A/R11B offsets or file extensions.
 * No level execution, player, PLC service, current art or World admission is installed here.
 */
public final class CdR11cTerrainAssets {
    public record Identity(CdReleaseVariant variant, CdStageKey stage, CdNativeFile file,
                           CdMmdImage.Domain domain, String mmdSha256, String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant); Objects.requireNonNull(stage); Objects.requireNonNull(file);
            Objects.requireNonNull(domain); Objects.requireNonNull(mmdSha256); Objects.requireNonNull(observedWholeDiscSha256);
            if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R11C.stage())
                    || file != CdNativeFile.R11C || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
                throw new IllegalArgumentException("Not an original USA R11C terrain identity");
            }
        }
    }

    /** Named original-file extents; these metadata values grant no execution authority. */
    public enum Asset {
        LEVEL_CHUNKS(0x10000, 55296),
        LEVEL_COLLISION(0x3B274, 800),
        LAYOUT_FG(0x3B5DC, 170),
        LAYOUT_BG(0x3B686, 30),
        LEVEL_BLOCKS(0x3B8EE, 4590),
        ART_LEVEL_TILES(0x3CADC, 11060),
        COL_ANGLE_MAP(0x39174, 256),
        COL_HEIGHT_MAP(0x39274, 4096),
        COL_WIDTH_MAP(0x3A274, 4096);

        private final int fileOffset, length;
        Asset(int fileOffset, int length) { this.fileOffset = fileOffset; this.length = length; }
        public int fileOffset() { return fileOffset; }
        public int length() { return length; }
        public int nativeAddress() { return 0x200000 + fileOffset; }
    }

    private static final String MMD_SHA256 = "b7023364db06b554123d4da0f92319eec592ea4a14d7602ce8ec769189b9bb60";
    private static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);
    private final Identity identity;
    private final CdMmdImage image;
    private final CdNemesisAsset.Decoded blocks, levelTiles;
    private final CdStageTerrain terrain;

    private CdR11cTerrainAssets(Identity identity, CdMmdImage image, CdNemesisAsset.Decoded blocks,
                                CdNemesisAsset.Decoded levelTiles, CdStageTerrain terrain) {
        this.identity = identity; this.image = image; this.blocks = blocks; this.levelTiles = levelTiles;
        this.terrain = terrain;
    }

    /** Borrows an open authenticated disc, then retains only immutable original-file terrain values. */
    public static CdR11cTerrainAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual CD program owner");
        if (programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("R11C terrain requires original USA media");
        }
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("R11C terrain requires a known complete-disc content profile"));
        if (release.variant() != CdReleaseVariant.USA) throw new IOException("R11C terrain source release differs");
        var catalogue = programs.catalogue().orElseThrow(() -> new IOException("R11C source catalogue is unavailable"));
        if (catalogue.stage(CdStagePayload.R11C.stage()).file() != CdNativeFile.R11C) {
            throw new IOException("R11C catalogue stage/file differs");
        }
        var image = programs.mmd(CdNativeFile.R11C);
        if (image.fileBytes() != 262144 || !HEADER.equals(image.header())
                || image.fileBackedCopyBytes() != 0 || image.unresolvedCopyBytes() != 0
                || !MMD_SHA256.equals(sha256(image.readFile(0, image.fileBytes())))) {
            throw new IOException("Original USA R11C content, header or file extent differs");
        }
        var index = CdAssetBytes.copyOf(image.readFile(0xECF6, 16));
        if (index.unsignedLong(0) != 0x0323CADCL || index.unsignedLong(4) != 0x0223B8EEL
                || index.unsignedLong(8) != 0x00210000L || index.unsignedWord(12) != 0x0081
                || index.unsignedWord(14) != 0x0404) {
            throw new IOException("R11C native terrain pointers or palette metadata differ");
        }
        var blocks = decode(image, Asset.LEVEL_BLOCKS, 5792, 0x2000, 4590);
        // Keep the full genuine 11060-byte source blob. The existing Java reader consumes 10707;
        // its unconsumed real suffix is neither padding we create nor a native-read minimum claim.
        var tiles = decode(image, Asset.ART_LEVEL_TILES, 24192, 24192, 10707);
        var terrain = CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_GOOD_FUTURE,
                blocks.bytes().copy(), tiles.bytes().copy(), read(image, Asset.LEVEL_CHUNKS),
                read(image, Asset.LAYOUT_FG), read(image, Asset.LAYOUT_BG), read(image, Asset.LEVEL_COLLISION),
                read(image, Asset.COL_ANGLE_MAP), read(image, Asset.COL_HEIGHT_MAP), read(image, Asset.COL_WIDTH_MAP));
        return new CdR11cTerrainAssets(new Identity(CdReleaseVariant.USA, CdStagePayload.R11C.stage(),
                CdNativeFile.R11C, CdMmdImage.Domain.ORIGINAL_WORD_RAM, MMD_SHA256,
                programs.identity().normalizedSha256()), image, blocks, tiles, terrain);
    }

    public Identity identity() { return identity; }
    public CdStageTerrain terrain() { return terrain; }
    public CdNemesisAsset.Decoded blocksDecode() { return blocks; }
    public CdNemesisAsset.Decoded levelTilesDecode() { return levelTiles; }
    public CdAssetBytes view(Asset asset) { return CdAssetBytes.copyOf(read(image, Objects.requireNonNull(asset))); }

    private static CdNemesisAsset.Decoded decode(CdMmdImage image, Asset asset, int output, int capacity, int cursor)
            throws IOException {
        var decoded = CdNemesisAsset.decode(read(image, asset), output, capacity);
        if (decoded.consumedInputBytes() != cursor) throw new IOException("R11C finite Java reader cursor differs: " + asset);
        return decoded;
    }
    private static byte[] read(CdMmdImage image, Asset asset) { return image.readFile(asset.fileOffset, asset.length); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("Required SHA-256 is unavailable", impossible); }
    }
}
