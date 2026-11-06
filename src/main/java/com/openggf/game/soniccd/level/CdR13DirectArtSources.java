package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.Arrays;
import java.util.Objects;

/** Finite original C/D raw-life and native block-reader views, not a service or art bank.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Main.asm:599-614;
 * Palmtree Panic/Data (Act 3).asm:163-166; Load Level Data (Act 3).asm:11-22.
 * Canonical blocks are4790/4772 bytes; actual native refill requires4791/4773 genuine bytes.
 * The extra byte belongs to the retained MMD, never synthesized EOF/alignment data.
 */
public final class CdR13DirectArtSources {
    private static final String LIFE_SHA="0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a";
    private final CdR13FutureAssets assets;
    private final CdR13FutureAssets.Identity identity;
    private final CdMmdImage image;
    private final CdR13FutureAssets.FileRange blocksBlob,blocksReader,life;
    private CdAssetBytes blocksBytes,lifeBytes;

    private CdR13DirectArtSources(CdR13FutureAssets assets){
        this.assets=Objects.requireNonNull(assets,"actual original R13 assets");assets.requireAuthenticatedLayout();
        identity=assets.identity();image=Objects.requireNonNull(assets.originalImage());
        // Validate the retained original image as well as the final aggregate's identity.
        if(image.fileBytes()!=0x40000 || !identity.mmdSha256().equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IllegalArgumentException("Original R13 direct-source image differs");
        boolean good=identity.file()==CdNativeFile.R13C;
        if(!good && identity.file()!=CdNativeFile.R13D)throw new IllegalArgumentException("Only original R13C/D direct sources");
        blocksBlob=assets.range(CdR13FutureAssets.Asset.LEVEL_BLOCKS);
        blocksReader=range(0x38490,good?4791:4773);life=range(0x32F3C,384);
        if(blocksBlob.fileOffset()!=blocksReader.fileOffset() || blocksBlob.length()!=(good?4790:4772))
            throw new IllegalArgumentException("Original R13 block include differs");
        // Actual LEA Art_LifeIcon,a1 operands, independently checked in both original programmes.
        byte[] operand=image.readFile(good?0x1A6E:0x1ADA,6);
        if(!Arrays.equals(operand,new byte[]{0x43,(byte)0xF9,0,0x23,0x2F,0x3C}))
            throw new IllegalArgumentException("Original R13 LoadLifeIcon operand differs");
    }
    public static CdR13DirectArtSources fromAssets(CdR13FutureAssets assets){return new CdR13DirectArtSources(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public CdR13FutureAssets.FileRange blocksBlobRange(){return blocksBlob;}
    public CdR13FutureAssets.FileRange blocksReaderRange(){return blocksReader;}
    public CdR13FutureAssets.FileRange lifeIconRange(){return life;}
    public synchronized CdAssetBytes blocksReader(){
        if(blocksBytes==null){var bytes=blocksReader.read(image);String expected=identity.file()==CdNativeFile.R13C?"40bdab146bd75ac7c01c1052074cbcd7020fb8fc61a22798a5a936d6f85b76ba":"913d1dcc34ce97966288755305ad80fdc063e73acec2dbc5b202df8df80d3e5c";
            if(!expected.equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Original R13 native block reader differs");blocksBytes=bytes;}return blocksBytes;
    }
    public synchronized CdAssetBytes lifeIcon(){
        if(lifeBytes==null){var bytes=life.read(image);if(!LIFE_SHA.equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Original R13 life icon differs");lifeBytes=bytes;}return lifeBytes;
    }
    private CdR13FutureAssets.FileRange range(int offset,int length){return new CdR13FutureAssets.FileRange(identity.file(),CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,length);}
}
