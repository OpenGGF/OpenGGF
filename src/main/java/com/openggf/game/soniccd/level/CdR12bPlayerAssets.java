package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Detached authenticated original-USA R12B player/response byte prerequisites, not a Frame or playable act.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Act 2 Past.asm:20-30,45,91;
 * Sonic/Main.asm:135-150,797-831,2644-2662,2951-3049,3066-3089;
 * Functions (Misc).asm:69-101,143-150,175-230; Data (Act 2 Past).asm:158-169;
 * Player Object Collision.asm:40-47,155-204,398-461; Scroll (Act 2 Past).asm:131-159.
 * Runtime bytes exclusively come from the complete authenticated original disc file, never native source.
 * Common R11a-named value decoders express byte formats only; they supply no fallback source or execution.
 */
public final class CdR12bPlayerAssets {
    /** Enum-owned complete source spans. These are not PLC readers, current-art jobs or execution authority. */
    public enum Asset {
        ART_SONIC(0x20000,69472), MAP_SPR_SONIC(0x30F60,3004), DPLC_SONIC(0x31B1C,1636),
        ANI_SONIC(0x57B0,578), SONIC_MINI_ANIMATIONS(0x5760,80), SONIC_RAMP_CHUNKS(0x4526,6),
        SPEC_CHUNKS(0x29D8,4), SINE_TABLE(0x82E,640), ARC_TAN_TABLE(0xB14,258),
        TOUCH_DIMENSIONS(0x6EDC,126), ENEMY_POINTS(0x6CF2,8);
        private final int offset,length;
        Asset(int offset,int length){this.offset=offset;this.length=length;}
        public int fileOffset(){return offset;}
        public int length(){return length;}
        public int nativeAddress(){return range().nativeAddress();}
        public CdR12bProfile.FileRange range(){return new CdR12bProfile.FileRange(CdNativeFile.R12B,CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,length);}
    }

    private final CdR12bTerrainAssets.Identity identity;
    private final CdR12bProfile profile;
    private final CdMmdImage image;
    private final CdSonicVisualAssets sonic;
    private final CdR11aPlayerAssets player;
    private final List<CdR11aObjectAssets.TouchSize> dimensions;

    private CdR12bPlayerAssets(CdR12bTerrainAssets.Identity identity,CdR12bProfile profile,CdMmdImage image,
                              CdSonicVisualAssets sonic,CdR11aPlayerAssets player,List<CdR11aObjectAssets.TouchSize> dimensions){
        this.identity=identity;this.profile=profile;this.image=image;this.sonic=sonic;this.player=player;this.dimensions=dimensions;
    }

    /** Borrows the open actual CUE/catalogue owner; never closes it. Detached immutable products survive closure. */
    public static CdR12bPlayerAssets fromPrograms(CdProgramAssets programs)throws IOException{
        Objects.requireNonNull(programs,"actual CD program owner");
        if(programs.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)
            throw new IOException("R12B player assets require original USA media");
        var release=CdRetailReleases.find(programs.identity())
                .orElseThrow(()->new IOException("R12B player assets require a known complete-disc content profile"));
        if(release.variant()!=CdReleaseVariant.USA)throw new IOException("R12B player source release differs");
        var catalogue=programs.catalogue().orElseThrow(()->new IOException("R12B source catalogue unavailable"));
        if(catalogue.release()!=release.variant())throw new IOException("R12B catalogue and observed release differ");
        var payload=catalogue.stage(CdStagePayload.R12B.stage());
        if(!payload.stage().equals(CdStagePayload.R12B.stage()) || payload.file()!=CdNativeFile.R12B)
            throw new IOException("R12B catalogue stage/file differs");
        var profile=CdR12bProfile.forStage(CdReleaseVariant.USA,payload.stage());
        var image=programs.mmd(payload.file());profile.verifyProgram(payload.file(),profile.domain(),image);
        // Init's actual MOVE.L at$203E7C selects$230F60; the equal$21F200 mapping is unused here.
        // Complete Art_Sonic includes its real final00, reached by the original frame30/31 DPLC reads.
        var sonic=CdSonicVisualAssets.decode(read(image,Asset.MAP_SPR_SONIC),read(image,Asset.DPLC_SONIC),read(image,Asset.ART_SONIC));
        // Native admitted IDs reach only60 of the80 MiniAnims bytes. CalcAngle's quotient reaches256,
        // not the separate final00 at index257. Full source tails remain visible through view(Asset).
        var player=CdR11aPlayerAssets.decode(read(image,Asset.ANI_SONIC),Asset.SONIC_MINI_ANIMATIONS.range().read(image).slice(0,60).copy(),
                read(image,Asset.SONIC_RAMP_CHUNKS),read(image,Asset.SPEC_CHUNKS),read(image,Asset.SINE_TABLE),
                Asset.ARC_TAN_TABLE.range().read(image).slice(0,257).copy(),sonic);
        var dimensions=CdR11aObjectAssets.decodeTouchDimensions(read(image,Asset.TOUCH_DIMENSIONS));
        return new CdR12bPlayerAssets(new CdR12bTerrainAssets.Identity(profile.variant(),profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),programs.identity().normalizedSha256()),profile,image,sonic,player,dimensions);
    }

    public CdR12bTerrainAssets.Identity identity(){return identity;}
    public CdR12bProfile profile(){return profile;}
    public CdSonicVisualAssets sonic(){return sonic;}
    public CdR11aPlayerAssets player(){return player;}
    public CdR12bProfile.FileRange sonicArtReadRange(){return Asset.ART_SONIC.range();}
    public CdAssetBytes view(Asset asset){return Objects.requireNonNull(asset,"R12B player source").range().read(image);}
    public List<CdR11aObjectAssets.TouchSize> touchDimensions(){return dimensions;}
    private static byte[] read(CdMmdImage image,Asset asset){return asset.range().read(image).copy();}
}
