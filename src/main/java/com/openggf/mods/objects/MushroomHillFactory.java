package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.MushroomHillWindRecipe;
import com.openggf.game.sonic3k.objects.badniks.CluckoidBadnikInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Native SKL Cluckoid owner; original-record admission remains host-owned. */
@ModApi
public final class MushroomHillFactory {
    private MushroomHillFactory() { }

    public static AbstractBadnikInstance cluckoid(ObjectSpawn spawn, MushroomHillWindRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != 0x90) {
            throw new IllegalArgumentException("Mushroom Hill Cluckoid requires native SKL object $90");
        }
        return new CluckoidBadnikInstance(spawn, recipe);
    }
}
