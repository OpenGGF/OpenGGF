package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.AizLogBridgeRecipe;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.AizCollapsingLogBridgeObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Supported creator facade for an original AIZ log placement's brittle variant. */
@ModApi
public final class AizLogBridgeFactory {
    private AizLogBridgeFactory() { }

    /**
     * Keeps the offered placement identity and the native solid, rider and debris owners.
     * Requires the donor's ordinary AIZ log art. Scripted burning drawbridges are excluded.
     */
    public static AbstractObjectInstance create(ObjectSpawn spawn, AizLogBridgeRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE) {
            throw new IllegalArgumentException("Brittle support requires an original AIZ collapsing-log placement");
        }
        if ((spawn.subtype() & 0x80) != 0) {
            throw new IllegalArgumentException("Brittle support cannot replace a scripted burning drawbridge");
        }
        return new AizCollapsingLogBridgeObjectInstance(spawn, recipe.supportTicks());
    }
}
