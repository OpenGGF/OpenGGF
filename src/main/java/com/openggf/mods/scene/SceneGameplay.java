package com.openggf.mods.scene;

import com.openggf.game.ModApi;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;

import java.util.List;

/**
 * Launches stock gameplay runs from a scene. While a run plays the scene is suspended (no
 * update or draw); when it ends the scene is resumed through
 * {@link ModScene#resumed(SceneContext, com.openggf.mods.run.RunEndReason)}.
 * The capability belongs to one scene visit and must be used on the engine thread.
 * Exiting or closing that visit revokes retained references; a later visit gets a new one.
 */
@ModApi
public interface SceneGameplay {
    /** Stock games whose ROMs are configured and can be launched ({@code s1}, {@code s2}, {@code s3k}). */
    List<String> availableGames();

    /**
     * The determinism fingerprint a run of {@code gameId} would report in
     * {@link com.openggf.mods.run.RunLevelStart#determinismFingerprint()} (engine build and ROM),
     * available before any run starts so a host can compare or match recordings and rooms.
     * Empty when the game's ROM is not available.
     */
    default java.util.Optional<String> determinismFingerprint(String gameId) {
        return java.util.Optional.empty();
    }

    /**
     * Starts {@code spec} after the scene's current callback returns. The run is a stock,
     * non-saving session with no mod gameplay content; {@code host} observes it.
     *
     * @throws IllegalStateException if a run is already active, the scene is not active,
     *                               its visit has ended, or the caller is not on the engine thread
     */
    RunHandle launch(RunSpec spec, RunHost host);

    /**
     * Starts {@code spec} behind this scene: the scene is not suspended. While the run plays the
     * scene keeps receiving {@code update} once per level frame (before the host's
     * {@link RunHost#admitStep}) with the live controller and keyboard, and {@code draw} paints
     * over the level, its HUD and the host's overlay; the canvas is the run's screen size, and a
     * scene that wants the level visible must not {@link SceneCanvas#clear} it. Start and the
     * pause key never pause a run behind a scene: they reach the scene as ordinary input. Use
     * {@link RunHandle#holdControls} while the scene's menu owns the controller. When the run
     * ends the scene is resumed as after {@link #launch}.
     *
     * @throws IllegalStateException as {@link #launch}
     */
    default RunHandle launchBehind(RunSpec spec, RunHost host) {
        throw new UnsupportedOperationException("This scene host cannot run gameplay behind a scene");
    }
}
