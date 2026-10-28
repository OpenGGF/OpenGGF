package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.io.IOException;

/** Detached immutable R11A assets. A loaded data set is not a playable module or an execution certificate. */
public final class CdR11aAssets {
    public record Identity(CdReleaseVariant variant, CdStageKey stage, CdNativeFile file, CdMmdImage.Domain domain,
                             String mmdSha256, String observedWholeDiscSha256) { }
    public record View(CdR11aAsset asset, CdR11aProfile.FileRange range, CdAssetBytes bytes) { }
    /** The real reader input can differ from the source-blob extent due to bounded read-ahead. */
    public record NemesisRead(CdR11aAsset asset, CdR11aProfile.FileRange sourceBlobRange,
                               CdR11aProfile.FileRange readerInputRange, int consumedInputBytes, CdAssetBytes bytes) { }

    private final CdR11aProfile profile;
    private final Identity identity;
    private final CdMmdImage image;
    private final NemesisRead blocks;
    private final NemesisRead tiles;
    private final CdR11aTerrain terrain;
    private final CdR11aLoadPlan loadPlan;
    private final CdObjectPlacements placements;
    private final CdSonicVisualAssets sonic;
    private final CdR11aPlayerAssets player;
    private final CdShieldArt shield;
    private CdR11aPaletteAssets paletteCycle;

    CdR11aAssets(CdR11aProfile profile, Identity identity, CdMmdImage image, NemesisRead blocks, NemesisRead tiles,
                   CdR11aTerrain terrain, CdR11aLoadPlan loadPlan, CdObjectPlacements placements,
                   CdSonicVisualAssets sonic, CdR11aPlayerAssets player, CdShieldArt shield) {
        this.profile = profile;
        this.identity = identity;
        this.image = image;
        this.blocks = blocks;
        this.tiles = tiles;
        this.terrain = terrain;
        this.loadPlan = loadPlan;
        this.placements = placements;
        this.sonic = sonic;
        this.player = player;
        this.shield = shield;
    }

    public CdR11aProfile profile() { return profile; }
    public Identity identity() { return identity; }
    public CdR11aTerrain terrain() { return terrain; }
    public CdR11aLoadPlan loadPlan() { return loadPlan; }
    public CdObjectPlacements placements() { return placements; }
    public CdSonicVisualAssets sonic() { return sonic; }
    public CdR11aPlayerAssets player() { return player; }
    public CdShieldArt shield() { return shield; }

    /**
     * Explicit optional USA-only Ring/Mosqui data consumer. Re-verifies the actual retained MMD before decoding.
     * Does not change regional terrain loading, execute objects/PLCs, or preload a runtime art bank.
     */
    public CdR11aObjectAssets openingObjects() throws IOException {
        return CdR11aObjectAssets.load(profile, identity, image, loadPlan);
    }
    /** Cached optional authenticated USA-only final palette-cycle sources; no cycle is executed here. */
    public synchronized CdR11aPaletteAssets paletteCycle() throws IOException {
        if (paletteCycle == null) { paletteCycle = CdR11aPaletteAssets.load(profile, identity, image); }
        return paletteCycle;
    }

    public NemesisRead blocksDecode() { return blocks; }
    public NemesisRead levelTilesDecode() { return tiles; }

    /** Bounded original bytes only. A table or blob view does not imply a ported consumer/known encoding. */
    public View view(CdR11aAsset asset) {
        var range = profile.range(asset);
        return new View(asset, range, range.read(image));
    }

    /** Only the two decoded consumers in this delivery are supported, even for other known Nemesis PLC blobs. */
    public CdAssetBytes decoded(CdR11aAsset asset) {
        return switch (asset) {
            case LEVEL_BLOCKS -> blocks.bytes();
            case ART_LEVEL_TILES -> tiles.bytes();
            default -> throw new UnsupportedOperationException("Asset consumer not implemented: " + asset.nativeLabel());
        };
    }
}
