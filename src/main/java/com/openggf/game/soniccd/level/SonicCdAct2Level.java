package com.openggf.game.soniccd.level;

import java.util.Objects;

/** Actual detached R12A geometry. Construction grants no programme or World admission. */
public final class SonicCdAct2Level extends SonicCdLevelGeometry {
    private final CdR12aTerrainAssets assets;
    public SonicCdAct2Level(CdR12aTerrainAssets assets) {
        super(Objects.requireNonNull(assets).terrain(), assets.loadPlan().initialPalette(), 1,
                assets.loadPlan().bounds().left(), assets.loadPlan().bounds().right(),
                assets.loadPlan().bounds().top(), assets.loadPlan().bounds().bottom());
        this.assets = assets;
    }
    public CdR12aTerrainAssets assets() { return assets; }
    public CdR11bLoadPlan.InitialCentre initialCentre() { return assets.loadPlan().initialCentre(); }
    public CdObjectPlacements nativePlacements() { return assets.objects().placements(); }
}
