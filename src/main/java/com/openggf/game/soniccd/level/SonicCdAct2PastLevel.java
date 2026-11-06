package com.openggf.game.soniccd.level;

import java.util.Objects;

/** Actual detached R12B geometry. Preparation grants no programme or World admission. */
public final class SonicCdAct2PastLevel extends SonicCdLevelGeometry {
    private final CdR12bTerrainAssets assets;

    public SonicCdAct2PastLevel(CdR12bTerrainAssets assets) {
        super(Objects.requireNonNull(assets).terrain(), assets.loadPlan().initialPalette(), 1,
                assets.loadPlan().bounds().left(), assets.loadPlan().bounds().right(),
                assets.loadPlan().bounds().top(), assets.loadPlan().bounds().bottom());
        this.assets = assets;
    }

    public CdR12bTerrainAssets assets() { return assets; }
    public CdR11bLoadPlan.InitialCentre initialCentre() { return assets.loadPlan().initialCentre(); }
    public CdObjectPlacements nativePlacements() { return assets.placements(); }
}
