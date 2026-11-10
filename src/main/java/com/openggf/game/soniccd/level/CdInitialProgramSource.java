package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.util.Objects;

/** Immutable original-USA initial-program DATA retained from the existing same-CUE open.
 * IPX.RunR11 (IPX.asm:353-356), RunLevel549-573 and R11SubCmds650-651 at N1ecc.
 * These bytes/reader grant no invocation, scene, destination entry, FULL or Frame authority.
 * No register model: the authenticated PC-relative operand identifies one finite command row.
 */
public final class CdInitialProgramSource {
    public record Source(CdReleaseVariant variant,String wholeDiscSha256,String fileSha256,
            CdMmdHeader header,int fileBytes,int runR11RowAddress) {
        public Source {Objects.requireNonNull(variant);Objects.requireNonNull(wholeDiscSha256);Objects.requireNonNull(fileSha256);Objects.requireNonNull(header);}
    }
    private static final String IPX_SHA="6e19dbef1bd9ad2d36771dbbce575ba7b37dce6d84cbb37bbeb6a98062674447";
    private static final CdMmdHeader HEADER=new CdMmdHeader(0,0,0xFF0000,0x03FF,0xFF0000,0,0);
    private final Source source;
    private final CdMmdImage image;
    private final int rowOffset;
    private final CdProgrammeReader past;
    private final CdLevelLoadingSource pastLoading;
    private CdInitialProgramSource(Source source,CdMmdImage image,int rowOffset,CdProgrammeReader past) {
        this.source=source;this.image=image;this.rowOffset=rowOffset;this.past=past;this.pastLoading=past.levelLoadingSource();
    }
    /** Called while the existing authenticated retail materialization owns THIS media.
     * No reopen, second disc owner or load permit; JP/EU deliberately remain absent. */
    public static CdInitialProgramSource fromPrograms(CdProgramAssets actual)throws IOException {
        Objects.requireNonNull(actual,"actual same-CUE programme owner");
        var release=CdRetailReleases.find(actual.identity()).orElseThrow(()->new IOException("Initial programme requires known complete retail media"));
        if(release.variant()!=CdReleaseVariant.USA||actual.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)
            throw new IOException("Only authenticated USA initial-programme DATA is admitted");
        var entry=actual.entry(CdNativeFile.IPX);
        if(!"/IPX___.MMD;1".equals(entry.path())||entry.directory()||entry.version()!=1||entry.extentBlock()!=10235||entry.size()!=4096)
            throw new IOException("Original USA IPX fixed file extent differs");
        var image=actual.mmd(CdNativeFile.IPX);
        if(image.fileBytes()!=4096||!HEADER.equals(image.header())||image.fileBackedCopyBytes()!=3840||image.unresolvedCopyBytes()!=256
                ||!IPX_SHA.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IOException("Original USA IPX header/content/unbacked extent differs");
        // Actual USA LEA R11SubCmds(pc),a0; MOVE.W #$000,zoneAct. Read the operand,
        // never use catalogue command IDs as a replacement for the actual row bytes.
        var operand=image.fileSlice(0x4CC,14);
        if(Short.toUnsignedInt(operand.getShort(0))!=0x41FA||Short.toUnsignedInt(operand.getShort(4))!=0x33FC
                ||Short.toUnsignedInt(operand.getShort(6))!=0||operand.getInt(8)!=0x00FF1506||Short.toUnsignedInt(operand.getShort(12))!=0x6000)
            throw new IOException("Original RunR11 a0/zoneAct operands differ");
        int row=0x4CC+2+operand.getShort(2);
        if(row!=0x708)throw new IOException("Original RunR11 PC-relative row differs");
        var catalogue=actual.catalogue().orElseThrow(()->new IOException("Original initial-programme catalogue unavailable"));
        var roles=new CdStagePayload[]{CdStagePayload.R11A,CdStagePayload.R11B,CdStagePayload.R11C,CdStagePayload.R11D};
        for(int n=0;n<roles.length;n++){
            var command=CdCommand.fromId(Byte.toUnsignedInt(image.readFile(row+n,1)[0]));
            if(catalogue.stageForCommand(command).orElse(null)!=roles[n]||!catalogue.nativeLoadFiles(command).contains(roles[n].file()))
                throw new IOException("Original RunR11 row/canonical consumer relation differs");
        }
        // There is no existing R11B Reader in CdStockLaunch. Create exactly one retained
        // reader here; its own image/span/operand checks and loading cache remain authoritative.
        var past=CdProgrammeReader.open(actual,CdStagePayload.R11B.stage());
        if(!past.discSha().equals(actual.identity().normalizedSha256()))throw new IOException("Initial/Past same-CUE relation differs");
        return new CdInitialProgramSource(new Source(release.variant(),actual.identity().normalizedSha256(),IPX_SHA,HEADER,
                image.fileBytes(),0xFF0000+row-CdMmdHeader.HEADER_BYTES),image,row,past);
    }
    public Source source(){return source;}
    public void requireDisc(String actualWholeDiscSha256){if(!source.wholeDiscSha256().equals(Objects.requireNonNull(actualWholeDiscSha256)))throw new IllegalArgumentException("Foreign initial-programme disc source");past.requireRetained();}
    /** Finite actual row index only; no arbitrary native address, raw image or mutable bytes. */
    public int runR11CommandByte(int offset){if(offset<0||offset>=4)throw new IllegalArgumentException("RunR11 row offset must be0..3");return Byte.toUnsignedInt(image.readFile(rowOffset+offset,1)[0]);}
    /** Same actual reader/cache retained at materialization, never reopened by a continuation. */
    public CdLevelLoadingSource pastLoadingSource(){past.requireRetained();pastLoading.requireRetained();return pastLoading;}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
