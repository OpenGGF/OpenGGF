package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.ClamerRecipe;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/**
 * Supported creator facade for Carnival Night native owners. Root adds the
 * inventory root and compiled signature pin for this annotated type.
 *
 * <p>Each method constructs the native owner for an original record of the
 * exact native object id only; the recipe selects a bounded native preset.
 */
@ModApi
public final class CarnivalNightFactory {
    private CarnivalNightFactory() { }

    /**
     * Creates a native Clamer (object {@code $A3}) with a finite preset.
     * Springs, projectile child and rewind identity stay with the native owner.
     *
     * @throws IllegalArgumentException when the spawn is not a native Clamer record
     */
    public static AbstractObjectInstance clamer(ObjectSpawn spawn, ClamerRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != Sonic3kObjectIds.CLAMER) {
            throw new IllegalArgumentException("Clamer recipe requires native object $A3, got $"
                    + Integer.toHexString(spawn.objectId()));
        }
        return new ClamerObjectInstance(spawn, recipe);
    }
}
