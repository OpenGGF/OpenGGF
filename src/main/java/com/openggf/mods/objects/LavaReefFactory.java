package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.LavaReefHazardRecipe;
import com.openggf.game.sonic3k.objects.LrzFlameThrowerObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Supported native LRZ2 owner; the native actor retains solids, flames and art. */
@ModApi
public final class LavaReefFactory {
    private LavaReefFactory() { }

    public static AbstractObjectInstance flameThrower(ObjectSpawn spawn, LavaReefHazardRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != 0x29) {
            throw new IllegalArgumentException("Lava Reef flame thrower requires native object $29");
        }
        return new LrzFlameThrowerObjectInstance(spawn, recipe == LavaReefHazardRecipe.ECHO_JET);
    }
}
