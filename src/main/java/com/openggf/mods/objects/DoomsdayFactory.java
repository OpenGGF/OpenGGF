package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.DoomsdayMissileRecipe;
import com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Native Doomsday owner; authenticated original-record admission remains host-owned. */
@ModApi
public final class DoomsdayFactory {
    private DoomsdayFactory() { }

    public static AbstractObjectInstance endBoss(ObjectSpawn spawn, DoomsdayMissileRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != 0xB6) {
            throw new IllegalArgumentException("Doomsday end boss requires native SKL object $B6");
        }
        return new DdzEndBossObjectInstance(spawn, recipe);
    }
}
