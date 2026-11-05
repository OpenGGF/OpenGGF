package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure actual-USA R12A LoadShieldArt source values, N1ecc5f3a Powerup/Main.asm:157-186.
 * Pointer20 selects five original source extents; native copy is1024 bytes, never a padded1152.
 * Missing Game/TimeOver Asset rows are fixed bounded original-source slices, not a public reader.
 * No CPU tail, resident bank, art decode, PLC, clock, current-region or World authority exists here.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
public final class CdR12aPowerupArtSources implements CdPowerupArtRuntime.RawSources {
    public enum Source {
        SHIELD(0x344E8,1152,CdR12aProfile.Asset.ART_SHIELD),
        INV_STARS(0x34968,1152,CdR12aProfile.Asset.ART_INV_STARS),
        TIME_STARS(0x34DE8,992,CdR12aProfile.Asset.ART_TIME_STARS),
        GAME_OVER(0x33D3C,1024,null), TIME_OVER(0x3397C,960,null),
        DIAGONAL_SPRING(0x351C8,780,CdR12aProfile.Asset.ART_DIAGONAL_SPRING);
        private final int offset,length; private final CdR12aProfile.Asset asset;
        Source(int offset,int length,CdR12aProfile.Asset asset){this.offset=offset;this.length=length;this.asset=asset;}
        public CdR12aProfile.FileRange range(){return new CdR12aProfile.FileRange(CdNativeFile.R12A,CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,length);}
    }
    /** Values and bounds only, not media or invocation authentication. */
    public record SourceSegment(Source source,int sourceRelativeOffset,int length) {
        public SourceSegment {
            Objects.requireNonNull(source,"source");
            if(sourceRelativeOffset<0 || length<0 || sourceRelativeOffset>source.length || length>source.length-sourceRelativeOffset)
                throw new IllegalArgumentException("Powerup segment exceeds its canonical raw source");
        }
    }
    public record RawCopy(int slot,Source source,CdR12aProfile.FileRange consumedRange,CdAssetBytes bytes,List<SourceSegment> segments) {
        public RawCopy {
            Objects.requireNonNull(source);Objects.requireNonNull(consumedRange);Objects.requireNonNull(bytes);segments=List.copyOf(segments);
            if(slot<0 || slot>=5 || ORDER[slot]!=source || consumedRange.fileOffset()!=source.offset || consumedRange.length()!=1024 || bytes.length()!=1024)
                throw new IllegalArgumentException("Not one finite1024-byte Powerup source copy");
            int cursor=consumedRange.fileOffset();
            for(var segment:segments){
                if(cursor!=segment.source().offset+segment.sourceRelativeOffset())throw new IllegalArgumentException("Powerup source segments are not natively adjacent");
                cursor+=segment.length();
            }
            if(cursor!=consumedRange.endExclusive())throw new IllegalArgumentException("Powerup copy has a source coverage gap");
        }
    }
    private static final Source[] ORDER={Source.SHIELD,Source.INV_STARS,Source.TIME_STARS,Source.GAME_OVER,Source.TIME_OVER};
    private final CdR12aTerrainAssets.Identity identity;
    private final CdAssetBytes index;
    private final Map<Source,CdAssetBytes> views;
    private final List<RawCopy> copies;
    private CdR12aPowerupArtSources(CdR12aTerrainAssets.Identity identity,CdAssetBytes index,Map<Source,CdAssetBytes> views,List<RawCopy> copies) {
        this.identity=identity;this.index=index;this.views=Map.copyOf(views);this.copies=List.copyOf(copies);
    }
    public static CdR12aPowerupArtSources fromAssets(CdR12aObjectAssets owner) {
        requireIdentity(owner);return owner.powerupArtSources();
    }
    private static CdR12aTerrainAssets.Identity requireIdentity(CdR12aObjectAssets owner) {
        var identity=Objects.requireNonNull(Objects.requireNonNull(owner,"actual R12A owner").identity(),"identity");
        if(identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12A || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !CdStagePayload.R12A.stage().equals(identity.stage()))throw new IllegalArgumentException("Powerup sources require actual original USA R12A");
        return identity;
    }
    /** Only the actual owner's private original image reaches this package-local source constructor. */
    static CdR12aPowerupArtSources fromOwner(CdR12aObjectAssets owner,CdMmdImage image) {
        var identity=requireIdentity(owner);Objects.requireNonNull(image,"actual original image");
        try {owner.profile().verifyProgram(CdNativeFile.R12A,CdMmdImage.Domain.ORIGINAL_WORD_RAM,image);}
        catch(IOException invalid){throw new IllegalArgumentException("Powerup original R12A image differs",invalid);}
        var index=owner.view(CdR12aProfile.Asset.SHIELD_ART_INDEX);requireIndex(index);
        var views=new EnumMap<Source,CdAssetBytes>(Source.class);
        for(var source:Source.values()) {
            var range=source.range();CdAssetBytes bytes;
            if(source.asset!=null) {
                if(!range.equals(owner.profile().range(source.asset)))throw new IllegalArgumentException("Powerup profile/source span differs");
                bytes=owner.view(source.asset);
            } else bytes=range.read(image);
            if(bytes.length()!=source.length)throw new IllegalArgumentException("Powerup source extent differs");
            views.put(source,bytes);
        }
        var copies=new ArrayList<RawCopy>();
        for(int slot=0;slot<5;slot++) {
            var source=ORDER[slot];var range=new CdR12aProfile.FileRange(CdNativeFile.R12A,CdMmdImage.Domain.ORIGINAL_WORD_RAM,source.offset,1024);
            List<SourceSegment> segments=switch(source) {
                case TIME_STARS -> List.of(new SourceSegment(source,0,992),new SourceSegment(Source.DIAGONAL_SPRING,0,32));
                case TIME_OVER -> List.of(new SourceSegment(source,0,960),new SourceSegment(Source.GAME_OVER,0,64));
                default -> List.of(new SourceSegment(source,0,1024));
            };
            byte[] output=new byte[1024];int cursor=0;
            for(var segment:segments) {
                System.arraycopy(views.get(segment.source()).copy(),segment.sourceRelativeOffset(),output,cursor,segment.length());cursor+=segment.length();
            }
            copies.add(new RawCopy(slot,source,range,CdAssetBytes.copyOf(output),segments));
        }
        return new CdR12aPowerupArtSources(identity,index,views,copies);
    }
    /** Pure finite format validation, also used by constructed-negative source tests; no owner is created. */
    static void requireIndex(CdAssetBytes index) {
        Objects.requireNonNull(index,"actual ShieldArtIndex");if(index.length()!=20)throw new IllegalArgumentException("ShieldArtIndex must contain exactly five LONGs");
        for(int slot=0;slot<5;slot++)if(index.unsignedLong(4*slot)!=Integer.toUnsignedLong(ORDER[slot].range().nativeAddress()))
            throw new IllegalArgumentException("ShieldArtIndex pointer/tag/order differs at slot "+slot);
    }
    public CdR12aTerrainAssets.Identity identity(){return identity;}
    public CdAssetBytes index(){return index;}
    public CdAssetBytes sourceView(Source source){return views.get(Objects.requireNonNull(source));}
    public List<RawCopy> copies(){return copies;}
    public RawCopy descriptor(int slot){return copies.get(slot);}
    @Override public CdAssetBytes copy(int slot){return descriptor(slot).bytes();}
}
