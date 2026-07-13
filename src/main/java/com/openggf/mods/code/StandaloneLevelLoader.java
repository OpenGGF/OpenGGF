package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.level.Level;
import com.openggf.level.rings.RingSpriteSheet;

import java.io.IOException;

/**
 * Supported creator facade for loading a primary standalone baked level.
 * The returned level is game-agnostic and requires neither a host module nor ROM.
 */
@com.openggf.game.ModApi
public final class StandaloneLevelLoader {
    private StandaloneLevelLoader() { }

    /**
     * Validates and materializes one baked level from the owning immutable asset root.
     *
     * @param assets owning mod asset snapshot
     * @param level reference to the baked {@code level.json}
     * @param ownerModId manifest owner used to namespace tagged spawns
     * @param rings sprite sheet used for authored rings
     * @return a fully materialized game-agnostic level
     */
    public static Level load(ModAssetRoot assets, BakedLevelRef level,
                             String ownerModId, RingSpriteSheet rings) throws IOException {
        return ModZoneLoader.loadStandalone(assets, level, ownerModId, rings);
    }
}
