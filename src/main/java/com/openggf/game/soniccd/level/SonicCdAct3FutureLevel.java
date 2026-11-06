package com.openggf.game.soniccd.level;

import java.util.Objects;

/** Detached original R13C/D geometry; construction performs no programme or World admission. */
public final class SonicCdAct3FutureLevel extends SonicCdLevelGeometry {
    private final CdR13FutureAssets assets;

    public SonicCdAct3FutureLevel(CdR13FutureAssets assets) {
        super(Objects.requireNonNull(assets, "actual Act3 aggregate").terrain(), assets.initialPalette(), 2,
                assets.bounds().left(), assets.bounds().right(), assets.bounds().top(), assets.bounds().bottom());
        this.assets = assets;
    }

    public CdR13FutureAssets assets() { return assets; }
    public CdR11bLoadPlan.InitialCentre initialCentre() { return assets.initialCentre(); }
    public CdObjectPlacements nativePlacements() { return assets.placements(); }
}
