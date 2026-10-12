package com.openggf.mods.run;

import com.openggf.game.ModApi;

/**
 * Immutable record of one executed gameplay step of a run, delivered after the engine's own
 * frame step. Held, paused, setup-only and lag-skipped frames produce no step.
 *
 * @param ordinal         steps executed since the level was last ready, starting at 0
 * @param heldMask        player 1's held buttons as admitted for this step, in the
 *                        {@code AbstractPlayableSprite.INPUT_*} bit layout (up, down, left,
 *                        right, jump in bits 0-4)
 * @param startHeld       player 1 held Start this step
 * @param player          the main character's resolved pose after the step
 * @param actComplete     the act's completion signal is raised (signpost/capsule reached)
 * @param checkpointIndex index of the last star post touched, or -1
 * @param debugAssisted   a debug shortcut was used during this step
 */
@ModApi
public record RunStep(long ordinal, int heldMask, boolean startHeld, PlayerPose player,
                      boolean actComplete, int checkpointIndex, boolean debugAssisted) {
}
