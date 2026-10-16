package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.DeathEggGuardRecipe;
import com.openggf.game.sonic3k.objects.badniks.SpikebonkerBadnikInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Native SKL Death Egg owners; authenticated original-record admission remains host-owned. */
@ModApi
public final class DeathEggFactory {
    private DeathEggFactory() { }

    public static AbstractBadnikInstance spikebonker(ObjectSpawn spawn, DeathEggGuardRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != 0xA4) {
            throw new IllegalArgumentException("Death Egg Spikebonker requires native SKL object $A4");
        }
        return new SpikebonkerBadnikInstance(spawn, recipe);
    }
}
