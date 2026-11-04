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

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;

/** Detached, authenticated original USA R12A terrain and static load data, not a playable act or programme transition. */
public final class CdR12aTerrainAssets {
    public record Identity(CdReleaseVariant variant, CdStageKey stage, CdNativeFile file, CdMmdImage.Domain domain,
                           String mmdSha256, String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant); Objects.requireNonNull(stage); Objects.requireNonNull(file);
            Objects.requireNonNull(domain); Objects.requireNonNull(mmdSha256); Objects.requireNonNull(observedWholeDiscSha256);
            if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12A.stage())
                    || file != CdNativeFile.R12A || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM)
                throw new IllegalArgumentException("Not an original USA R12A terrain identity");
        }
    }
    private final Identity identity;
    private final CdR12aProfile profile;
    private final CdMmdImage image;
    private final CdNemesisAsset.Decoded blocks, tiles;
    private final CdStageTerrain terrain;
    private final CdR12aLoadPlan plan;
    private CdR12aObjectAssets objects;
    private CdR12aTerrainAssets(Identity identity, CdR12aProfile profile, CdMmdImage image, CdNemesisAsset.Decoded blocks,
                                 CdNemesisAsset.Decoded tiles, CdStageTerrain terrain, CdR12aLoadPlan plan) {
        this.identity = identity; this.profile = profile; this.image = image; this.blocks = blocks;
        this.tiles = tiles; this.terrain = terrain; this.plan = plan;
    }
    /** Borrows the existing authenticated catalogue/CUE owner; retains only detached immutable original-file values. */
    public static CdR12aTerrainAssets fromPrograms(CdProgramAssets programs) throws IOException {
        Objects.requireNonNull(programs, "actual CD program owner");
        if (programs.header().observedVariant().orElse(null) != CdReleaseVariant.USA)
            throw new IOException("R12A terrain requires original USA media");
        var release = CdRetailReleases.find(programs.identity())
                .orElseThrow(() -> new IOException("R12A terrain requires a known complete-disc content profile"));
        if (release.variant() != CdReleaseVariant.USA) throw new IOException("R12A terrain source release differs");
        var payload = programs.catalogue().orElseThrow(() -> new IOException("R12A source catalogue unavailable"))
                .stage(CdStagePayload.R12A.stage());
        var profile = CdR12aProfile.forStage(CdReleaseVariant.USA, payload.stage());
        if (payload.file() != profile.file()) throw new IOException("R12A catalogue stage/file differs");
        var image = programs.mmd(payload.file()); profile.verifyProgram(payload.file(), profile.domain(), image);
        var blocks = decode(profile, image, LEVEL_BLOCKS, 5824, 8192, 4600);
        var tiles = decode(profile, image, ART_LEVEL_TILES, 25952, 25952, 10723);
        var terrain = CdStageTerrain.decode(CdStageTerrain.Shape.PPZ2_PRESENT, blocks.bytes().copy(), tiles.bytes().copy(),
                read(profile, image, LEVEL_CHUNKS), read(profile, image, LAYOUT_FG), read(profile, image, LAYOUT_BG),
                read(profile, image, LEVEL_COLLISION), read(profile, image, COL_ANGLE_MAP),
                read(profile, image, COL_HEIGHT_MAP), read(profile, image, COL_WIDTH_MAP));
        var plan = CdR12aLoadPlan.decode(profile, image);
        return new CdR12aTerrainAssets(new Identity(profile.variant(), profile.stage(), profile.file(), profile.domain(),
                profile.expectedMmdSha256(), programs.identity().normalizedSha256()), profile, image, blocks, tiles, terrain, plan);
    }
    /** Lazy detached metadata binding to this actual authenticated immutable image; no decoder/service runs. */
    public synchronized CdR12aObjectAssets objects() {
        if (objects == null) objects = new CdR12aObjectAssets(this, image);
        return objects;
    }
    public Identity identity() { return identity; }
    public CdR12aProfile profile() { return profile; }
    public CdStageTerrain terrain() { return terrain; }
    public CdR12aLoadPlan loadPlan() { return plan; }
    public CdNemesisAsset.Decoded blocksDecode() { return blocks; }
    public CdNemesisAsset.Decoded levelTilesDecode() { return tiles; }
    public CdAssetBytes view(CdR12aProfile.Asset asset) { return profile.range(asset).read(image); }
    private static byte[] read(CdR12aProfile profile, CdMmdImage image, CdR12aProfile.Asset asset) {
        return profile.range(asset).read(image).copy();
    }
    private static CdNemesisAsset.Decoded decode(CdR12aProfile profile, CdMmdImage image, CdR12aProfile.Asset asset,
                                                int output, int capacity, int cursor) throws IOException {
        var result = CdNemesisAsset.decode(profile.readerRange(asset).read(image).copy(), output, capacity);
        if (result.consumedInputBytes() != cursor) throw new IOException("R12A finite Java reader cursor differs: " + asset);
        return result;
    }
}
