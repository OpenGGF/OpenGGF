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
    private final CdProgrammeReader programmeReader;
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
        this(profile, identity, image, blocks, tiles, terrain, loadPlan, placements, sonic, player, shield, null);
    }

    /** Actual-media loader only; the component constructor retains no shared source custody. */
    CdR11aAssets(CdR11aProfile profile, Identity identity, CdMmdImage image, NemesisRead blocks, NemesisRead tiles,
                   CdR11aTerrain terrain, CdR11aLoadPlan loadPlan, CdObjectPlacements placements,
                   CdSonicVisualAssets sonic, CdR11aPlayerAssets player, CdShieldArt shield, CdProgrammeReader programmeReader) {
        if(programmeReader!=null) {
            programmeReader.requireOriginalR11Image(image);
            if(sonic!=programmeReader.sonic())throw new IllegalArgumentException("Foreign original R11A Sonic source product");
            programmeReader.requireOriginalR11TerrainProducts(blocks,tiles,terrain);
        }
        this.programmeReader = programmeReader;
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
        if (paletteCycle == null) { paletteCycle = CdR11aPaletteAssets.load(this, programmeReader); }
        return paletteCycle;
    }

    /** Pure origin gate for component/regional compatibility; it is not source authority. */
    boolean hasRetainedStandardLoadingSource() { return programmeReader!=null; }
    /** The actual USA aggregate's SAME authenticated Reader-cached STANDARD DATA.
     * No media reopen, Frame, source lease, clear, Ready or invocation permit is created.
     * Legacy/component/regional aggregates keep their existing separate factories. */
    public CdLevelLoadingSource retainedStandardLoadingSource()throws IOException {
        if(programmeReader==null)throw new UnsupportedOperationException("No retained original R11A standard loading DATA");
        requireProgrammeReader(programmeReader);
        return programmeReader.levelLoadingSource();
    }

    /** Package-only exact source relation; public metadata and equal readers cannot substitute. */
    CdProgrammeReader internalReader() {
        if(programmeReader==null)throw new UnsupportedOperationException("No retained original R11A programme reader");
        return programmeReader;
    }
    void requireProgrammeReader(CdProgrammeReader actual)throws IOException {
        if(actual==null || actual!=programmeReader)throw new IllegalArgumentException("Foreign original R11A programme reader");
        if(!actual.stage().equals(identity.stage()) || actual.file()!=identity.file()
                || !actual.mmdSha().equals(identity.mmdSha256()) || !actual.discSha().equals(identity.observedWholeDiscSha256()))
            throw new IllegalArgumentException("Original R11A reader and aggregate identity differ");
        profile.verifyProgram(identity.file(),identity.domain(),image);
        actual.requireOriginalR11Image(image);
    }

    private CdR11aLoadingArtInputs loadingInputs;
    /** Original finite source binding only; no decode, current bytes, service or admission. */
    public synchronized CdR11aLoadingArtInputs loadingArtInputs()throws IOException{
        if(loadingInputs==null)loadingInputs=CdR11aLoadingArtInputs.bind(this,image);return loadingInputs;
    }

    private CdR11aStartupSource startup;
    public synchronized CdR11aStartupSource startupObjects()throws IOException{if(startup==null)startup=CdR11aStartupSource.bind(this,image);return startup;}

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
