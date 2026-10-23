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

    /** Accepts the authenticated loader result; there is no ROM or reference-asset fallback. */
    public SonicCdLevel(CdR11aAssets assets) {
        super(Objects.requireNonNull(assets, "assets").terrain(), assets.loadPlan().initialPalette(),
                assets.loadPlan().bounds());
        this.assets = assets;
    }

    /** Immutable source identity, native geometry/words and load prerequisites, never live engine state. */
    public CdR11aAssets assets() { return assets; }

    /** Native object centre coordinates from LevelStartLoc, not a camera origin or sprite top-left. */
    public CdR11aLoadPlan.InitialCentre initialCentre() { return assets.loadPlan().initialCentre(); }

    /**
     * Complete native ordered placement stream, including period flags and subtype2.
     * It is deliberately not converted to executable ObjectSpawn or RingSpawn records.
     */
    public CdObjectPlacements nativePlacements() { return assets.placements(); }
}
