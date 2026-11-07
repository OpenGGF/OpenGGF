package com.openggf.game.soniccd.level;

import java.io.IOException;
import java.util.Objects;

/** Detached original R31A geometry. No boot, source service, current-art or World admission. */
public final class SonicCdR31aLevel extends SonicCdLevelGeometry {
    private final CdR31aAssets assets;

    public SonicCdR31aLevel(CdR31aAssets assets) throws IOException {
        super(Objects.requireNonNull(assets, "actual R31A aggregate").terrain(), initialPalette(assets), 1,
                assets.bounds().left(), assets.bounds().right(), assets.bounds().top(), assets.bounds().bottom());
        this.assets = assets;
    }

    public CdR31aAssets assets() { return assets; }
    public CdR11bLoadPlan.InitialCentre initialCentre() { return assets.initialCentre(); }
    public CdObjectPlacements nativePlacements() { return assets.placements(); }

    /** Main.LoadPalette IDs3/4: Sonic line0 plus native stage lines1..3. This is static source data. */
    private static CdAssetBytes initialPalette(CdR31aAssets assets) {
        byte[] bytes = new byte[128];
        System.arraycopy(assets.view(CdR31aAssets.Asset.PAL_SONIC).copy(), 0, bytes, 0, 32);
        System.arraycopy(assets.view(CdR31aAssets.Asset.PAL_LEVEL).copy(), 0, bytes, 32, 96);
        return CdAssetBytes.copyOf(bytes);
    }
}
