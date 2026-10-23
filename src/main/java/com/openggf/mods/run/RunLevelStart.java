package com.openggf.mods.run;

import com.openggf.game.ModApi;

/**
 * Delivered when the run's level has loaded and before its first step: on launch, after
 * every retry and after every fast death restart.
 *
 * @param spec                   the run being played
 * @param determinismFingerprint identifies the simulation (engine build and ROM) so a host can
 *                               tell whether two recordings are comparable
 * @param debugAssisted          a debug aid (overlay, debug mode) was already active at load
 * @param levelWidth             width of the act's foreground layout in pixels
 * @param levelHeight            height of the act's foreground layout in pixels
 * @param deathRestart           the act reloaded from its start because the player died under a
 *                               {@link com.openggf.game.session.GameplayRunPolicy.DeathRestart#FAST_FROM_START}
 *                               policy (no title card was shown); false on launch and retries
 */
@ModApi
public record RunLevelStart(RunSpec spec, String determinismFingerprint, boolean debugAssisted,
                            int levelWidth, int levelHeight, boolean deathRestart) {
}
