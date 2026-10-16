package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.game.sonic3k.difficulty.MarbleGardenEnemyRecipe;
import com.openggf.game.sonic3k.objects.MgzMinibossInstance;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.AbstractObjectInstance;
import java.util.Objects;

/** Supported finite native MGZ presets; event-spawned end bosses use the same immutable recipe. */
@ModApi
public final class MarbleGardenFactory {
    private MarbleGardenFactory() { }
    public static AbstractBadnikInstance mantis(ObjectSpawn spawn, MarbleGardenEnemyRecipe recipe) {
        return new MantisBadnikInstance(nativeSpawn(spawn, 0x9D), guard(recipe));
    }
    public static AbstractBadnikInstance spiker(ObjectSpawn spawn, MarbleGardenEnemyRecipe recipe) {
        return new SpikerBadnikInstance(nativeSpawn(spawn, 0x9C), guard(recipe));
    }
    public static AbstractObjectInstance miniboss(ObjectSpawn spawn, MarbleGardenBossRecipe recipe) {
        return new MgzMinibossInstance(nativeSpawn(spawn, 0x9F), counter(recipe));
    }
    private static boolean guard(MarbleGardenEnemyRecipe recipe) {
        return Objects.requireNonNull(recipe, "recipe") == MarbleGardenEnemyRecipe.LANDING_GUARD;
    }
    private static boolean counter(MarbleGardenBossRecipe recipe) {
        return Objects.requireNonNull(recipe, "recipe") == MarbleGardenBossRecipe.COUNTER_CADENCE;
    }
    private static ObjectSpawn nativeSpawn(ObjectSpawn spawn, int expected) {
        Objects.requireNonNull(spawn, "spawn");
        if (spawn.objectId() != expected) {
            throw new IllegalArgumentException("Requires matching native object ID $" + Integer.toHexString(expected));
        }
        return spawn;
    }
}
