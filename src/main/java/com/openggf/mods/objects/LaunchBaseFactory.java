package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.LbzAlarmObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/**
 * Supported creator facade for Launch Base native owners. Root adds the
 * inventory root and compiled signature pin for this annotated type.
 */
@ModApi
public final class LaunchBaseFactory {
    private LaunchBaseFactory() { }

    /**
     * Creates a native LBZ alarm (object {@code $22}) with a finite preset.
     * Touch latch, siren and escort allocation stay with the native owner.
     *
     * @throws IllegalArgumentException when the spawn is not a native alarm record
     */
    public static AbstractObjectInstance alarm(ObjectSpawn spawn, LbzAlarmRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != Sonic3kObjectIds.LBZ_ALARM) {
            throw new IllegalArgumentException("Alarm recipe requires native object $22, got $"
                    + Integer.toHexString(spawn.objectId()));
        }
        return new LbzAlarmObjectInstance(spawn, recipe);
    }
}
