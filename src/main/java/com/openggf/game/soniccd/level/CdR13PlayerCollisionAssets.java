package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.List;
import java.util.Objects;

/** Detached original USA R13C/D primary collision inputs, not player/Body/World admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Player Object Collision.asm44-47,166,200-204,398-461.
 * Actual C/D consumer operands independently authenticated; no equal R12B source is borrowed.
 * Reuses the existing unsigned63-pair decoder. No scanner/response, state, Frame, art or service is owned.
 */
public final class CdR13PlayerCollisionAssets {
    public enum View {
        TOUCH_DIMENSIONS(0x6E14,0x6E2C,126,"658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d"),
        ENEMY_POINTS(0x6C2A,0x6C42,8,"75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5");
        private final int goodOffset,badOffset,length;private final String sha;
        View(int goodOffset,int badOffset,int length,String sha){this.goodOffset=goodOffset;this.badOffset=badOffset;this.length=length;this.sha=sha;}
        public CdR13FutureAssets.FileRange range(CdNativeFile file){return new CdR13FutureAssets.FileRange(file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,bad(file)?badOffset:goodOffset,length);}
        public String sha256(){return sha;}
    }
    private final CdR13FutureAssets assets;
    private final CdR13FutureAssets.Identity identity;
    private final CdMmdImage image;
    private final CdAssetBytes[] views=new CdAssetBytes[2];
    private List<CdR11aObjectAssets.TouchSize> dimensions;
    private CdR13PlayerCollisionAssets(CdR13FutureAssets assets){
        this.assets=Objects.requireNonNull(assets,"actual original R13 aggregate");assets.requireAuthenticatedLayout();
        identity=Objects.requireNonNull(assets.identity());image=Objects.requireNonNull(assets.originalImage());
        boolean good=identity.file()==CdNativeFile.R13C&&identity.stage().equals(CdStagePayload.R13C.stage());
        boolean bad=identity.file()==CdNativeFile.R13D&&identity.stage().equals(CdStagePayload.R13D.stage());
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA||(!good&&!bad)||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||image.fileBytes()!=0x40000
                ||!expected.equals(identity.mmdSha256())||!expected.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IllegalArgumentException("Original R13 primary collision source/image differs");
        // Native Player_ObjCollide ObjColSizes LEA/indexed -2, and PC-relative EnemyPoints lookup.
        require(good?0x6AB4:0x6ACC,good?"45f900206e1445f200fe7200121a30290008":"45f900206e2c45f200fe7200121a30290008");
        require(good?0x6BCE:0x6BE6,"303b005a0c780020f7d0650a");
    }
    public static CdR13PlayerCollisionAssets fromAssets(CdR13FutureAssets assets){return new CdR13PlayerCollisionAssets(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public synchronized CdAssetBytes view(View source){Objects.requireNonNull(source);var value=views[source.ordinal()];if(value!=null)return value;
        value=source.range(identity.file()).read(image);if(!source.sha.equals(CdR11aProfile.sha256(value.copy())))throw new IllegalArgumentException("Original R13 collision table differs: "+source);views[source.ordinal()]=value;return value;}
    public synchronized List<CdR11aObjectAssets.TouchSize> touchDimensions(){if(dimensions==null)dimensions=CdR11aObjectAssets.decodeTouchDimensions(view(View.TOUCH_DIMENSIONS).copy());return dimensions;}
    public CdAssetBytes enemyPoints(){return view(View.ENEMY_POINTS);}
    private void require(int at,String hex){if(!java.util.HexFormat.of().formatHex(image.readFile(at,hex.length()/2)).equals(hex))throw new IllegalArgumentException("Original R13 collision consumer differs at "+Integer.toHexString(at));}
    private static boolean bad(CdNativeFile file){if(file==CdNativeFile.R13C)return false;if(file==CdNativeFile.R13D)return true;throw new IllegalArgumentException("Only original R13C/D collision metadata");}
}
