package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.mods.scene.SceneRomArt;
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
 * @param romLibrary  multi-ROM content for mixed-ROM scenes, or null
 * @param gameplay    launches gameplay runs from the scene, or null when the scene cannot
 */
public record SceneServices(
        AudioManager audio,
        SceneRomArt romArt,
        Path storageRoot,
        MouseMapper mouse,
        Runnable toGameTitle,
        Runnable toMasterTitle,
        SceneRomLibrary romLibrary,
        com.openggf.mods.scene.SceneGameplay gameplay) {

    /** Legacy running-ROM scenes do not need a multi-ROM library. */
    public SceneServices(AudioManager audio, SceneRomArt romArt, Path storageRoot,
            MouseMapper mouse, Runnable toGameTitle, Runnable toMasterTitle) {
        this(audio, romArt, storageRoot, mouse, toGameTitle, toMasterTitle, null, null);
    }

    /** Scenes that cannot launch gameplay runs. */
    public SceneServices(AudioManager audio, SceneRomArt romArt, Path storageRoot,
            MouseMapper mouse, Runnable toGameTitle, Runnable toMasterTitle, SceneRomLibrary romLibrary) {
        this(audio, romArt, storageRoot, mouse, toGameTitle, toMasterTitle, romLibrary, null);
    }

    /** Window-to-logical mouse transform. Returns {@code {x, y, inside ? 1 : 0}}. */
    @FunctionalInterface
    public interface MouseMapper {
        int[] map(double windowX, double windowY);
    }
}
