package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Detached, mutable engine view of authenticated R11A static terrain.
 *
 * <p>Construct one per session from {@link CdR11aAssetLoader}'s result. Native CD
 * blocks become engine Chunks; native CD chunks become 256px engine Blocks.
 * {@link #assets()} remains immutable provenance, separate from all live engine
 * edits. Construction neither retains an open disc nor publishes graphics.
 *
 * <p>This is a static presentation adapter, not a playable stage. The native
 * drawing/collision masks differ (0x3FF/0x7FF); engine descriptors carry the
 * drawing index only. Original words and collision indices remain available in
 * {@code assets().terrain()}. Native collision lookup, loop remapping, player,
 * object/ring execution, PLCs, animation and scrolling are not implemented here.
 * Engine object/ring lists contain no executable registrations; that does not
 * mean the native placement stream is empty.
 */
public final class SonicCdLevel extends SonicCdLevelGeometry {
    private final CdR11aAssets assets;
    private final CdLevelLoadingSource loadingSource;

    /** Accepts the authenticated loader result; there is no ROM or reference-asset fallback. */
    public SonicCdLevel(CdR11aAssets assets) {
        super(Objects.requireNonNull(assets, "assets").terrain(), assets.loadPlan().initialPalette(),
                assets.loadPlan().bounds());
        this.assets = assets;
        this.loadingSource=CdLevelLoadingSource.fromOriginalR11(assets);
    }

    /** Immutable source identity, native geometry/words and load prerequisites, never live engine state. */
    public CdR11aAssets assets() {
        if(assets==null)throw new UnsupportedOperationException("Actual "+loadingSource.stage().nativeId()+" geometry is not an R11 asset aggregate");
        return assets;
    }
    public CdLevelLoadingSource loadingSource(){return loadingSource;}

    /** DATA conversion only; the private Module/LevelManager assembly supplies runtime authority. */
    public static SonicCdLevel fromLoadingSource(CdLevelLoadingSource source)throws java.io.IOException {
        return new SonicCdLevel(Objects.requireNonNull(source),source.terrain());
    }
    private SonicCdLevel(CdLevelLoadingSource source,CdStageTerrain terrain) {
        super(terrain,sourcePalette(source),source.nativeZoneActWord()>>>8,source.bounds().left(),
                source.bounds().right(),source.bounds().top(),source.bounds().bottom(),source.stage().nativeId());
        loadingSource=source;
        assets=source.hasOriginalR11Aggregate()?source.originalR11Assets():null;
    }
    private static CdAssetBytes sourcePalette(CdLevelLoadingSource source) {
        var player=source.playerPalette();var level=source.levelPalette();
        if(player.length()!=32||level.length()!=96)throw new IllegalArgumentException("Actual complete player/stage palette source required");
        byte[] palette=new byte[player.length()+level.length()];
        System.arraycopy(player.copy(),0,palette,0,player.length());
        System.arraycopy(level.copy(),0,palette,player.length(),level.length());
        return CdAssetBytes.copyOf(palette);
    }

    /** Native object centre coordinates from LevelStartLoc, not a camera origin or sprite top-left. */
    public CdR11aLoadPlan.InitialCentre initialCentre() { return loadingSource.initialCentre(); }

    /**
     * Complete native ordered placement stream, including period flags and subtype2.
     * It is deliberately not converted to executable ObjectSpawn or RingSpawn records.
     */
    public CdObjectPlacements nativePlacements() { return loadingSource.placements(); }
}
