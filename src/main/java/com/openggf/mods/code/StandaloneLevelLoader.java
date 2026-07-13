package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.level.Level;
import com.openggf.level.rings.RingSpriteSheet;

import java.io.IOException;

/** Supported creator facade for loading a primary standalone baked level. */
@com.openggf.game.ModApi
public final class StandaloneLevelLoader {
    private StandaloneLevelLoader() { }

    public static Level load(ModAssetRoot assets, BakedLevelRef level,
                             String ownerModId, RingSpriteSheet rings) throws IOException {
        return ModZoneLoader.loadStandalone(assets, level, ownerModId, rings);
    }
}
