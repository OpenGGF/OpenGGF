package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;

/**
 * Detached authenticated original USA R12B terrain and load metadata; no programme transition or World admission.
 * Only the two terrain streams are decoded. The 37 PLC pointers are observations, not 37 reader registrations,
 * decoder outputs, current patterns, player/Frame instances, callbacks, sound or draw authority.
 */
public final class CdR12bTerrainAssets {
    /** Metadata only. Consistent record construction cannot construct this privately minted aggregate. */
    public record Identity(CdReleaseVariant variant,CdStageKey stage,CdNativeFile file,CdMmdImage.Domain domain,
                           String mmdSha256,String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant);Objects.requireNonNull(stage);Objects.requireNonNull(file);Objects.requireNonNull(domain);
            Objects.requireNonNull(mmdSha256);Objects.requireNonNull(observedWholeDiscSha256);
            if(variant!=CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12B.stage())
                    || file!=CdNativeFile.R12B || domain!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !mmdSha256.matches("[0-9a-f]{64}") || !observedWholeDiscSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Not an original USA R12B terrain identity");
        }
    }
    private final Identity identity;
    private final CdR12bProfile profile;
    private final CdMmdImage image;
    private final CdNemesisAsset.Decoded blocks,tiles;
    private final CdStageTerrain terrain;
    private final CdR12bLoadPlan plan;
    private CdObjectPlacements placements;
    private CdR12bSectionSources sectionSources;
    private CdR12bDirectArtSources directArtSources;

    private CdR12bTerrainAssets(Identity identity,CdR12bProfile profile,CdMmdImage image,CdNemesisAsset.Decoded blocks,
                                CdNemesisAsset.Decoded tiles,CdStageTerrain terrain,CdR12bLoadPlan plan) {
        this.identity=identity;this.profile=profile;this.image=image;this.blocks=blocks;this.tiles=tiles;this.terrain=terrain;this.plan=plan;
    }
    /** Borrows the complete actual CUE/catalogue owner while open; rejection never closes its caller. */
    public static CdR12bTerrainAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs,"actual CD program owner");
        if(programs.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)
            throw new IOException("R12B terrain requires original USA media");
        var release=CdRetailReleases.find(programs.identity())
                .orElseThrow(()->new IOException("R12B terrain requires a known complete-disc content profile"));
        if(release.variant()!=CdReleaseVariant.USA)throw new IOException("R12B terrain source release differs");
        var catalogue=programs.catalogue().orElseThrow(()->new IOException("R12B source catalogue unavailable"));
        if(catalogue.release()!=release.variant())throw new IOException("R12B catalogue and observed release differ");
        var payload=catalogue.stage(CdStagePayload.R12B.stage());
        var profile=CdR12bProfile.forStage(CdReleaseVariant.USA,payload.stage());
        if(payload.file()!=profile.file())throw new IOException("R12B catalogue stage/file differs");
        var image=programs.mmd(payload.file());profile.verifyProgram(payload.file(),profile.domain(),image);
        var blocks=decode(profile,image,LEVEL_BLOCKS,5664,8192,4468);
        var tiles=decode(profile,image,ART_LEVEL_TILES,25504,25504,10802);
        var terrain=CdStageTerrain.decode(CdStageTerrain.Shape.PPZ2_PAST,blocks.bytes().copy(),tiles.bytes().copy(),
                read(profile,image,LEVEL_CHUNKS),read(profile,image,LAYOUT_FG),read(profile,image,LAYOUT_BG),
                read(profile,image,LEVEL_COLLISION),read(profile,image,COL_ANGLE_MAP),
                read(profile,image,COL_HEIGHT_MAP),read(profile,image,COL_WIDTH_MAP));
        var plan=CdR12bLoadPlan.decode(profile,image);
        return new CdR12bTerrainAssets(new Identity(profile.variant(),profile.stage(),profile.file(),profile.domain(),
                profile.expectedMmdSha256(),programs.identity().normalizedSha256()),profile,image,blocks,tiles,terrain,plan);
    }
    /** Lazy pure placement decoding from this same retained original image; no allocation or lifecycle. */
    public synchronized CdObjectPlacements placements() {
        if(placements==null)placements=CdObjectPlacements.decodeLayout(read(profile,image,OBJECT_LAYOUTS));
        return placements;
    }
    /** Lazy immutable original-source registry/views; no PLC preparation, decode or current-art claim. */
    public synchronized CdR12bSectionSources sectionSources() {
        if(sectionSources==null)sectionSources=CdR12bSectionSources.bind(this);
        return sectionSources;
    }
    /** Only enum-owned finite original-file spans; no arbitrary image/range/offset ingress. */
    CdAssetBytes sectionSourceView(CdR12bSectionSources.SourceId source,CdR12bSectionSources.ReaderKind kind) {
        return Objects.requireNonNull(source).range(Objects.requireNonNull(kind)).read(image);
    }
    /** Lazy finite raw writer views; source values confer no native/current publication authority. */
    public synchronized CdR12bDirectArtSources directArtSources() {
        if(directArtSources==null)directArtSources=CdR12bDirectArtSources.bind(this);
        return directArtSources;
    }
    CdAssetBytes directArtView(CdR12bDirectArtSources.View source) {
        return Objects.requireNonNull(source).range().read(image);
    }
    public Identity identity() { return identity; }
    public CdR12bProfile profile() { return profile; }
    public CdStageTerrain terrain() { return terrain; }
    public CdR12bLoadPlan loadPlan() { return plan; }
    public CdNemesisAsset.Decoded blocksDecode() { return blocks; }
    public CdNemesisAsset.Decoded levelTilesDecode() { return tiles; }
    public CdAssetBytes view(CdR12bProfile.Asset asset) { return profile.range(asset).read(image); }
    private static byte[] read(CdR12bProfile profile,CdMmdImage image,CdR12bProfile.Asset asset) {
        return profile.range(asset).read(image).copy();
    }
    private static CdNemesisAsset.Decoded decode(CdR12bProfile profile,CdMmdImage image,CdR12bProfile.Asset asset,
                                                int output,int capacity,int cursor) throws IOException {
        var result=CdNemesisAsset.decode(profile.readerRange(asset).read(image).copy(),output,capacity);
        if(result.consumedInputBytes()!=cursor)throw new IOException("R12B finite Java reader cursor differs: "+asset);
        return result;
    }
}
