package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.HiddenPalaceDuelRecipe;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Supported native personal duel; native copy, defeat and story owners remain intact. */
@ModApi
public final class HiddenPalaceFactory {
    private HiddenPalaceFactory() { }

    public static AbstractObjectInstance duel(ObjectSpawn spawn, HiddenPalaceDuelRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != 0x82 || spawn.subtype() != 0x28) {
            throw new IllegalArgumentException("Hidden Palace duel requires native CutsceneKnuckles $82/$28");
        }
        return new CutsceneKnucklesHpzInstance(spawn, recipe);
    }
}
