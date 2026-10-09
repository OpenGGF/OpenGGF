package com.openggf.game.run;

import com.openggf.game.ModApi;

/**
 * Delivered when the run's level has loaded and before its first step: on launch and after
 * every retry.
 *
 * @param spec                   the run being played
 * @param determinismFingerprint identifies the simulation (engine build and ROM) so a host can
 *                               tell whether two recordings are comparable
 * @param debugAssisted          a debug aid (overlay, debug mode) was already active at load
 * @param levelWidth             width of the act's foreground layout in pixels
 * @param levelHeight            height of the act's foreground layout in pixels
 */
@ModApi
public record RunLevelStart(RunSpec spec, String determinismFingerprint, boolean debugAssisted,
                            int levelWidth, int levelHeight) {
}
