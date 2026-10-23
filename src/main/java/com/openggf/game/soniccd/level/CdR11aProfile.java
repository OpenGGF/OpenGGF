package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Reviewed R11A program-local metadata, not an address space or disc admission certificate.
 * Research: schema-v2 stage inventory, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Runtime uses only these constants and the supplied MMD; no research file is opened.
 */
public final class CdR11aProfile {
    public static final String RESEARCH_SOURCE_PIN = "1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff";
    public static final int FILE_BYTES = 0x40000;
    private static final String USA_MMD = "7aa54c36ab94dbe14068a18613c574b883ce446337bfaa9c571ec3523aff14e2";
    private static final String JP_EU_MMD = "241be9620fbad1229792fedd1604066ae3e7d943cb7cc27bc94a4a837452d9b6";

    /** Always R11A original Word RAM; tagged table longs must be decoded before making a range. */
    public record FileRange(CdNativeFile file, CdMmdImage.Domain domain, int fileOffset, int length) {
        public FileRange {
            if (file != CdNativeFile.R11A || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || fileOffset < 0 || length < 0 || fileOffset > FILE_BYTES
                    || length > FILE_BYTES - fileOffset) {
                throw new IllegalArgumentException("Not a bounded R11A original-file range");
            }
        }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS + fileOffset; }
        public int endExclusive() { return fileOffset + length; }
        public CdAssetBytes read(CdMmdImage image) {
            // nativeSlice checks both the requested domain and the actual file-backed range.
            var source = image.nativeSlice(domain, nativeAddress(), length);
            byte[] bytes = new byte[length];
            source.get(bytes);
            return CdAssetBytes.copyOf(bytes);
        }
    }

    private final CdReleaseVariant variant;

    private CdR11aProfile(CdReleaseVariant variant) { this.variant = variant; }

    public static CdR11aProfile forStage(CdReleaseVariant variant, CdStageKey stage) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(stage, "stage");
        if (!CdStagePayload.R11A.stage().equals(stage)) {
            throw new UnsupportedOperationException("Only R11A asset semantics are implemented: " + stage.nativeId());
        }
        return new CdR11aProfile(variant);
    }

    public CdReleaseVariant variant() { return variant; }
    public CdStageKey stage() { return CdStagePayload.R11A.stage(); }
    public CdNativeFile file() { return CdNativeFile.R11A; }
    public CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
    public String expectedMmdSha256() { return variant == CdReleaseVariant.USA ? USA_MMD : JP_EU_MMD; }
    public FileRange range(CdR11aAsset asset) {
        Objects.requireNonNull(asset, "asset");
        return new FileRange(file(), domain(), asset.fileOffset(variant), asset.length());
    }

    /** Identity is computed from the immutable bytes, never supplied as an asserted digest. */
    public void verifyProgram(CdNativeFile file, CdMmdImage.Domain domain, CdMmdImage image) throws IOException {
        if (file != file() || domain != domain()) {
            throw new IllegalArgumentException("R11A profile/file/address-domain mismatch");
        }
        Objects.requireNonNull(image, "image");
        var header = image.header();
        if (image.fileBytes() != FILE_BYTES || header.flags() != 0 || header.reserved() != 0
                || header.origin() != 0 || header.sizeCounter() != 0 || header.entry() != 0x200100
                || header.horizontalInterrupt() != 0x20010C || header.verticalInterrupt() != 0x200112) {
            throw new IOException("Unsupported R11A MMD header or file extent");
        }
        if (!sha256(image.readFile(0, image.fileBytes())).equals(expectedMmdSha256())) {
            throw new IOException("R11A MMD content does not match the selected regional profile");
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM has no SHA-256", exception);
        }
    }
}
