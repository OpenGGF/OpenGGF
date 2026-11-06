package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.Objects;

/** Actual C/D background table binding, independent original-media operand roots.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Scroll Act3 includes Past:940-1043,1139-1201.
 * No art/default tile, plane state, Frame, service or World authority is supplied.
 */
public final class CdR13PlaneSources {
    private final CdR13FutureAssets assets;private final CdR13FutureAssets.Identity identity;
    private final CdR13FutureAssets.FileRange idsRange,pointersRange;private final CdAssetBytes ids,pointers;
    private CdR13PlaneSources(CdR13FutureAssets assets){
        this.assets=Objects.requireNonNull(assets,"actual C/D aggregate");assets.requireAuthenticatedLayout();identity=assets.identity();var image=assets.originalImage();
        boolean c=identity.file()==CdNativeFile.R13C;if(!c&&identity.file()!=CdNativeFile.R13D)throw new IllegalArgumentException("Only original C/D planes");
        if(!identity.mmdSha256().equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))throw new IllegalArgumentException("Retained original image differs");
        idsRange=new CdR13FutureAssets.FileRange(identity.file(),CdMmdImage.Domain.ORIGINAL_WORD_RAM,c?0x32F0:0x3308,34);
        pointersRange=new CdR13FutureAssets.FileRange(identity.file(),CdMmdImage.Domain.ORIGINAL_WORD_RAM,c?0x3312:0x332A,16);
        ids=idsRange.read(image);pointers=pointersRange.read(image);
        String expected=c?"7f301c96bbf02cd63b073201917d0c385b4f2cf75755e87fb21f433339a9ae1d":"a4b4ed50ce9d8a9e322e7059a440abb004c95c343caab15c1e62f9d076bcca35";
        if(!expected.equals(CdR11aProfile.sha256(ids.copy()))||!"2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1".equals(CdR11aProfile.sha256(pointers.copy())))throw new IllegalArgumentException("Original BG section/pointer table differs");
        // Independently authenticated LEA roots in DrawLevelBG1 and InitLevelDrawBG, not neighboring-act offsets.
        for(int at:c?new int[]{0x2F26,0x32CA}:new int[]{0x2F3E,0x32E2})if(word(image,at)!=0x41F9||longWord(image,at+2)!=Integer.toUnsignedLong(idsRange.nativeAddress()))throw new IllegalArgumentException("Actual BG consumer operand differs");
    }
    public static CdR13PlaneSources fromAssets(CdR13FutureAssets assets){return new CdR13PlaneSources(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public CdStageTerrain terrain(){return assets.terrain();}
    public CdR13FutureAssets.FileRange idsRange(){return idsRange;}public CdR13FutureAssets.FileRange pointersRange(){return pointersRange;}
    public CdAssetBytes ids(){return ids;}public CdAssetBytes pointers(){return pointers;}
    private static int word(CdMmdImage image,int at){var b=image.readFile(at,2);return (b[0]&255)<<8|(b[1]&255);}
    private static long longWord(CdMmdImage image,int at){var b=image.readFile(at,4);return (long)(b[0]&255)<<24|(long)(b[1]&255)<<16|(long)(b[2]&255)<<8|(b[3]&255);}
}
