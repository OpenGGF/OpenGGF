package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.AizTreeExitRecipe;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/** Exact original-tree binding with one native release-owned lookout variant. */
@ModApi
public final class AizHollowTreeFactory {
    private AizHollowTreeFactory() { }
    public static AbstractObjectInstance create(ObjectSpawn spawn, AizTreeExitRecipe recipe) {
        return AizHollowTreeObjectInstance.withExitRecipe(spawn, recipe);
    }
}
