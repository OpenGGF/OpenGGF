package com.openggf.mods.scene;

import java.util.List;
import java.util.Optional;

/**
 * Text files private to one mod, kept under the engine's save directory in
 * {@code mods/<mod id>/}. Names are 1-64 characters of {@code a-z 0-9 . _ -}; each file holds
 * at most 1 MiB. Writes replace the whole file atomically, so a crash never leaves half a save.
 */
@com.openggf.game.ModApi
public interface SceneStorage {
    /** The file's text, or empty when it does not exist or cannot be read. */
    Optional<String> read(String name);

    /** Replaces the file; returns false when it could not be written. */
    boolean write(String name, String text);

    /** Deletes the file; returns true if it existed. */
    boolean delete(String name);

    /** Names of the files that exist, sorted. */
    List<String> list();
}
