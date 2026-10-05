package com.openggf.mods.scene;

import com.openggf.audio.AudioManager;
import java.nio.file.Path;

/**
 * What the engine supplies to a {@link ModSceneHost}. Every part may be null in headless
 * runs; the host then degrades (no audio, no mouse, no ROM art). Engine-internal.
 *
 * @param audio       the engine's audio manager
 * @param romArt      ROM decoding for the running game, or null
 * @param storageRoot directory under which each mod gets {@code mods/<id>/}
 * @param mouse       maps raw window coordinates to logical pixels, or null
 * @param toGameTitle starts the fade back to the base game's title
 * @param toMasterTitle starts the fade back to the master title
 */
public record SceneServices(
        AudioManager audio,
        SceneRomArt romArt,
        Path storageRoot,
        MouseMapper mouse,
        Runnable toGameTitle,
        Runnable toMasterTitle) {

    /** Window-to-logical mouse transform. Returns {@code {x, y, inside ? 1 : 0}}. */
    @FunctionalInterface
    public interface MouseMapper {
        int[] map(double windowX, double windowY);
    }
}
