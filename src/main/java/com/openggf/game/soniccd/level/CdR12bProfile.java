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
 * Detached original USA R12B source metadata, not programme, World or current-art admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Act 2 Past.asm:7-91;
 * Data (Act 2 Past).asm:11-26,68-86,346-384,390-418,442-498;
 * Load Level Data.asm:11-39,57-98; Scroll (Act 2 Past).asm:44-49,143-159,398-404,539-540,1172-1201;
 * Palette Data (Act 1 Past).asm:11-29,46-59; Graphics Lists (Act 2 Past).asm:7-26.
 * Complete native blobs and actual local operands were compared to the original retail file.
 */
public final class CdR12bProfile {
    public static final int FILE_BYTES = 0x40000;
    private static final String MMD_SHA256 = "14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64";
    private static final CdMmdHeader HEADER = new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112);

    /** Original finite source spans only. PLC pointer inventory is not a qualified art-reader registry. */
    public enum Asset {
        LEVEL_CHUNKS(0x10000,54272), LEVEL_COLLISION(0x3B274,900),
        LAYOUT_FG(0x3B640,170), LAYOUT_BG(0x3B6EA,22),
        LEVEL_BLOCKS(0x3BA2C,4468), ART_LEVEL_TILES(0x3CBA0,10802),
        COL_ANGLE_MAP(0x39174,256), COL_HEIGHT_MAP(0x39274,4096), COL_WIDTH_MAP(0x3A274,4096),
        LEVEL_DATA_INDEX(0xF02A,16), LEVEL_LAYOUTS(0x3B5F8,72), LEVEL_COL_INDEX(0x1A56,32),
        CAM_BOUNDS(0x2910,12), LEVEL_START_LOC(0x29D4,4),
        PALETTE_TABLE(0x5C0,48), PAL_SONIC(0x6F0,32), PAL_LEVEL(0x710,96),
        PLC_LISTS(0xF03A,616), SECTION_DATA(0xCBC0,30), OBJECT_LAYOUTS(0x76F8,3930),
        SPEC_CHUNKS(0x29D8,4), BG_CAMERA_SECT_IDS(0x33AA,34), BG_CAMERA_SECTS(0x33CC,16),
        PALETTE_CYCLE_DATA(0x2C0,84),
        CLOUD_LINE_COUNTS(0x2B6C,8);
        private final int offset, length;
        Asset(int offset,int length) { this.offset=offset; this.length=length; }
        public int fileOffset() { return offset; }
        public int length() { return length; }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS+offset; }
    }

    /** Component metadata; constructing a range authenticates no image or native invocation. */
    public record FileRange(CdNativeFile file,CdMmdImage.Domain domain,int fileOffset,int length) {
        public FileRange {
            if(file!=CdNativeFile.R12B || domain!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || fileOffset<0 || length<0 || fileOffset>FILE_BYTES || length>FILE_BYTES-fileOffset)
                throw new IllegalArgumentException("Not a bounded R12B original-file range");
        }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS+fileOffset; }
        public int endExclusive() { return fileOffset+length; }
        public CdAssetBytes read(CdMmdImage image) {
            var source=Objects.requireNonNull(image).nativeSlice(domain,nativeAddress(),length);
            byte[] bytes=new byte[length];source.get(bytes);return CdAssetBytes.copyOf(bytes);
        }
    }

    private CdR12bProfile() { }
    public static CdR12bProfile forStage(CdReleaseVariant variant,CdStageKey stage) {
        Objects.requireNonNull(variant);Objects.requireNonNull(stage);
        if(variant!=CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12B.stage()))
            throw new UnsupportedOperationException("Only original USA R12B asset metadata is reviewed");
        return new CdR12bProfile();
    }
    public CdReleaseVariant variant() { return CdReleaseVariant.USA; }
    public CdStageKey stage() { return CdStagePayload.R12B.stage(); }
    public CdNativeFile file() { return CdNativeFile.R12B; }
    public CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
    public String expectedMmdSha256() { return MMD_SHA256; }
    public FileRange range(Asset asset) {
        Objects.requireNonNull(asset);return new FileRange(file(),domain(),asset.fileOffset(),asset.length());
    }
    /** Canonical original inputs; independent research lookahead controls require no added byte. */
    public FileRange readerRange(Asset asset) {
        Objects.requireNonNull(asset);
        return switch(asset) {
            case LEVEL_BLOCKS,ART_LEVEL_TILES -> range(asset);
            default -> throw new IllegalArgumentException("R12B asset has no reviewed Nemesis reader: "+asset);
        };
    }
    public void verifyProgram(CdNativeFile file,CdMmdImage.Domain domain,CdMmdImage image) throws IOException {
        if(file!=file() || domain!=domain())throw new IllegalArgumentException("R12B profile/file/domain mismatch");
        Objects.requireNonNull(image);
        if(!originalShape(image) || !MMD_SHA256.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IOException("Original USA R12B content, header or file extent differs");
    }
    static boolean originalShape(CdMmdImage image) {
        return image.fileBytes()==FILE_BYTES && HEADER.equals(image.header())
                && image.fileBackedCopyBytes()==0 && image.unresolvedCopyBytes()==0;
    }
}
