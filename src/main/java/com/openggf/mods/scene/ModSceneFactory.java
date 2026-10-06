package com.openggf.mods.scene;

/**
 * Creates a fresh {@link ModScene} each time the scene opens (for example after returning
 * from the master title), so no state leaks between visits.
 */
@com.openggf.game.ModApi
@FunctionalInterface
public interface ModSceneFactory {
    ModScene create();
}
