package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.CollapsingBridgeRecipe;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import java.util.Objects;

/** Supported preset intake for the native bridge's solid, fragment and rewind owner. */
@ModApi
public final class CollapsingBridgeFactory {
    private CollapsingBridgeFactory() { }
    /** Requires the current zone's native collapsing-bridge art. No placements or tiles move. */
    public static AbstractObjectInstance create(ObjectSpawn spawn, CollapsingBridgeRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        if (spawn.objectId() != 0x0F) throw new IllegalArgumentException("Requires native collapsing bridge object ID $0F");
        return new CollapsingBridgeObjectInstance(spawn,
                Objects.requireNonNull(recipe, "recipe") == CollapsingBridgeRecipe.BRITTLE);
    }
}
