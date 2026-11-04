package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.io.IOException;
import java.util.Objects;

/**
 * Original USA R12A terrain and player-source metadata. Pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Data (Act 2 Present).asm:11-20,45-47,273-334; Scroll (Act 2 Present).asm:21-35,44-45,139-140;
 * Palette Data (Act 2 Present).asm:7-25,48-59. Complete native blobs were matched to original R12A;
 * the actual loader at$203494 selects$20F09E, and layout loader$203510 selects$23B5F8.
 * These original-file extents are not runtime, programme-transition or World admission.
 */
public final class CdR12aProfile {
    public static final int FILE_BYTES = 0x40000;
    private static final String MMD_SHA256 = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    private static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);

    public enum Asset {
        LEVEL_CHUNKS(0x10000, 54272), LEVEL_COLLISION(0x3B274, 900),
        LAYOUT_FG(0x3B640, 170), LAYOUT_BG(0x3B6EA, 22),
        LEVEL_BLOCKS(0x3B8FA, 4600), ART_LEVEL_TILES(0x3CAF2, 10722),
        COL_ANGLE_MAP(0x39174, 256), COL_HEIGHT_MAP(0x39274, 4096), COL_WIDTH_MAP(0x3A274, 4096),
        LEVEL_DATA_INDEX(0xF09E, 16), LEVEL_LAYOUTS(0x3B5F8, 72), LEVEL_COL_INDEX(0x1A9A, 32),
        CAM_BOUNDS(0x2954, 12), LEVEL_START_LOC(0x2A18, 4),
        PALETTE_TABLE(0x5A4, 48), PAL_SONIC(0x6D4, 32), PAL_LEVEL(0x6F4, 96),
        // Complete source extents, including MiniAnims'20 unused bytes and ArcTan's trailing00.
        // The pinned gfx/r1/player.unc already contains its real final00; no read padding is added.
        ART_SONIC(0x20000, 69472), MAP_SPR_SONIC(0x30F60, 3004), DPLC_SONIC(0x31B1C, 1636),
        ANI_SONIC(0x582E, 578), SONIC_MINI_ANIMATIONS(0x57DE, 80), SONIC_RAMP_CHUNKS(0x45A4, 6),
        SPEC_CHUNKS(0x2A1C, 4), SINE_TABLE(0x872, 640), ARC_TAN_TABLE(0xB58, 258);
        private final int offset, length;
        Asset(int offset, int length) { this.offset = offset; this.length = length; }
        public int fileOffset() { return offset; }
        public int length() { return length; }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS + offset; }
    }

    public record FileRange(CdNativeFile file, CdMmdImage.Domain domain, int fileOffset, int length) {
        public FileRange {
            if (file != CdNativeFile.R12A || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || fileOffset < 0 || length < 0 || fileOffset > FILE_BYTES || length > FILE_BYTES - fileOffset)
                throw new IllegalArgumentException("Not a bounded R12A original-file range");
        }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS + fileOffset; }
        public int endExclusive() { return fileOffset + length; }
        public CdAssetBytes read(CdMmdImage image) {
            var source = Objects.requireNonNull(image).nativeSlice(domain, nativeAddress(), length);
            byte[] bytes = new byte[length]; source.get(bytes); return CdAssetBytes.copyOf(bytes);
        }
    }

    private CdR12aProfile() { }
    public static CdR12aProfile forStage(CdReleaseVariant variant, CdStageKey stage) {
        Objects.requireNonNull(variant); Objects.requireNonNull(stage);
        if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12A.stage()))
            throw new UnsupportedOperationException("Only original USA R12A asset metadata is reviewed");
        return new CdR12aProfile();
    }
    public CdReleaseVariant variant() { return CdReleaseVariant.USA; }
    public CdStageKey stage() { return CdStagePayload.R12A.stage(); }
    public CdNativeFile file() { return CdNativeFile.R12A; }
    public CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
    public String expectedMmdSha256() { return MMD_SHA256; }
    public FileRange range(Asset asset) {
        Objects.requireNonNull(asset); return new FileRange(file(), domain(), asset.fileOffset(), asset.length());
    }
    /** The shared Java reader's real one-byte lookahead is not manufactured padding or a native cursor claim. */
    public FileRange readerRange(Asset asset) {
        Objects.requireNonNull(asset);
        return switch (asset) {
            case LEVEL_BLOCKS -> range(asset);
            case ART_LEVEL_TILES -> new FileRange(file(), domain(), asset.fileOffset(), asset.length() + 1);
            default -> throw new IllegalArgumentException("R12A asset has no reviewed Nemesis reader: " + asset);
        };
    }
    public void verifyProgram(CdNativeFile file, CdMmdImage.Domain domain, CdMmdImage image) throws IOException {
        if (file != file() || domain != domain()) throw new IllegalArgumentException("R12A profile/file/domain mismatch");
        Objects.requireNonNull(image);
        if (!originalShape(image) || !MMD_SHA256.equals(CdR11aProfile.sha256(image.readFile(0, image.fileBytes()))))
            throw new IOException("Original USA R12A content, header or file extent differs");
    }
    static boolean originalShape(CdMmdImage image) {
        return image.fileBytes() == FILE_BYTES && HEADER.equals(image.header())
                && image.fileBackedCopyBytes() == 0 && image.unresolvedCopyBytes() == 0;
    }
}
