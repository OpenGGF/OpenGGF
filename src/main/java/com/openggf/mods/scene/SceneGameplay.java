package com.openggf.mods.scene;

import com.openggf.game.ModApi;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunSpec;

import java.util.List;

/**
 * Launches stock gameplay runs from a scene. While a run plays the scene is suspended (no
 * update or draw); when it ends the scene is resumed through
 * {@link ModScene#resumed(SceneContext, com.openggf.game.run.RunEndReason)}.
 */
@ModApi
public interface SceneGameplay {
    /** Stock games whose ROMs are configured and can be launched ({@code s1}, {@code s2}, {@code s3k}). */
    List<String> availableGames();

    /**
     * Starts {@code spec} after the scene's current callback returns. The run is a stock,
     * non-saving session with no mod gameplay content; {@code host} observes it.
     *
     * @throws IllegalStateException if a run is already active
     */
    RunHandle launch(RunSpec spec, RunHost host);
}
