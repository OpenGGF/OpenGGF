package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.HydrocityBossRecipe;
import com.openggf.game.sonic3k.difficulty.HydrocityEnemyRecipe;
import com.openggf.game.sonic3k.objects.HczMinibossInstance;
import com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.AbstractObjectInstance;
import java.util.Objects;

/** Supported finite presets for HCZ owners using the donor's native assets. */
@ModApi
public final class HydrocityFactory {
    private HydrocityFactory() { }
    public static AbstractBadnikInstance turboSpiker(ObjectSpawn spawn, HydrocityEnemyRecipe recipe) {
        return new TurboSpikerBadnikInstance(nativeSpawn(spawn, 0x96),
                Objects.requireNonNull(recipe, "recipe") == HydrocityEnemyRecipe.ALERT);
    }
    public static AbstractObjectInstance miniboss(ObjectSpawn spawn, HydrocityBossRecipe recipe) {
        return new HczMinibossInstance(nativeSpawn(spawn, 0x99), counter(recipe));
    }
    public static AbstractObjectInstance endBoss(ObjectSpawn spawn, HydrocityBossRecipe recipe) {
        return new HczEndBossInstance(nativeSpawn(spawn, 0x9A), counter(recipe));
    }
    private static boolean counter(HydrocityBossRecipe recipe) {
        return Objects.requireNonNull(recipe, "recipe") == HydrocityBossRecipe.COUNTER_CADENCE;
    }
    private static ObjectSpawn nativeSpawn(ObjectSpawn spawn, int expected) {
        Objects.requireNonNull(spawn, "spawn");
        if (spawn.objectId() != expected) {
            throw new IllegalArgumentException("Requires matching native object ID $" + Integer.toHexString(expected));
        }
        return spawn;
    }
}
