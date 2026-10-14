package com.openggf.mods.objects;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrHeadInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.Objects;

/** Supported creator facade for a ROM-backed native Caterkiller Jr owner. */
@ModApi
public final class CaterkillerJrFactory {
    private CaterkillerJrFactory() { }

    /**
     * Creates the native owner inside an ordinary registered object factory.
     * The caller supplies its owned spawn; the native owner allocates and
     * reconstructs its own children through the host's normal object services.
     * Requires the donor's Caterkiller Jr and Monkey Dude art (normally AIZ).
     */
    public static AbstractBadnikInstance create(ObjectSpawn spawn, CaterkillerJrRecipe recipe) {
        return new CaterkillerJrHeadInstance(Objects.requireNonNull(spawn, "spawn"),
                Objects.requireNonNull(recipe, "recipe"));
    }
}
