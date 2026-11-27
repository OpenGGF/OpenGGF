package com.openggf.game.internal;

import com.openggf.game.GameModule;

/** Native sprite-table producer order; independent of game names and presentation width. */
public interface SpriteTableBuildProfile {
    static SpriteTableBuildProfile forModule(GameModule module) {
        if (module instanceof SpriteTableBuildProfile profile) return profile;
        if (module == null) return null;
        var service = module.getGameService(SpriteTableBuildProfile.class);
        if (service != null) return service;
        // Decorators and engine-owned projections may only expose the GameModule surface.
        return NativeGameModuleLineage.root(module) instanceof SpriteTableBuildProfile profile ? profile : null;
    }

    enum PlacedRings {
        OBJECT_SLOTS,
        OBJECT_SLOTS_WITH_LAYOUT_MARGINS,
        BEFORE_OBJECT_BUCKETS
    }
    enum PlacedRingY { OBJECT_MAPPING, MASK_11_BEFORE_BIAS, BIAS_BEFORE_SCREEN_WRAP }
    PlacedRings placedRingEmission();
    default PlacedRingY placedRingY() { return PlacedRingY.OBJECT_MAPPING; }
    default boolean lostRingHighPriority() { return true; }
}
