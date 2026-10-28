package com.openggf.game.soniccd.level;

import java.util.Objects;

/**
 * Detached, mutable engine projection of authenticated USA R11B static terrain.
 * Construction converts only immutable supplied-disc geometry and its prepared
 * palette. It starts no World, destination entry, saved-data load, scroll, PLC,
 * object walk or palette fade. The native default centre is metadata; a marked
 * time-warp entry must use its separate source-ordered restore and camera owners.
 */
public final class SonicCdPastLevel extends SonicCdLevelGeometry {
    private final CdR11bAssets assets;

    public SonicCdPastLevel(CdR11bAssets assets) {
        super(Objects.requireNonNull(assets, "assets").terrain(), assets.loadPlan().initialPalette(), 0,
                assets.loadPlan().bounds().left(), assets.loadPlan().bounds().right(),
                assets.loadPlan().bounds().top(), assets.loadPlan().bounds().bottom());
        this.assets = assets;
    }

    public CdR11bAssets assets() { return assets; }

    /** Default native centre only; live spawn2 does not read LevelStartLoc. */
    public CdR11bLoadPlan.InitialCentre initialCentre() { return assets.loadPlan().initialCentre(); }

    /** Exact period-marked records, with no executable object or ring registrations. */
    public CdObjectPlacements nativePlacements() { return assets.placements(); }
}
