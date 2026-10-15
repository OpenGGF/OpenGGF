package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.IczStalagtiteRecipe;
import com.openggf.game.sonic3k.objects.IczStalagtiteObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/**
 * Supported creator facade for IceCap native owners. Root adds the
 * inventory root and compiled signature pin for this annotated type.
 */
@ModApi
public final class IceCapFactory {
    private IceCapFactory() { }

    /**
     * Creates a native ICZ Stalagtite (object {@code $B5}) with a finite preset.
     * Solid body, falling hurt box, debris and landing stay with the native owner.
     *
     * @throws IllegalArgumentException when the spawn is not a native Stalagtite record
     */
    public static AbstractObjectInstance stalactite(ObjectSpawn spawn, IczStalagtiteRecipe recipe) {
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(recipe, "recipe");
        if (spawn.objectId() != Sonic3kObjectIds.ICZ_STALAGTITE) {
            throw new IllegalArgumentException("Stalactite recipe requires native object $B5, got $"
                    + Integer.toHexString(spawn.objectId()));
        }
        return new IczStalagtiteObjectInstance(spawn, recipe);
    }
}
