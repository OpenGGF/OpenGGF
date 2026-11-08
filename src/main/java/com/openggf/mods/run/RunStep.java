package com.openggf.mods.run;

import com.openggf.game.arena.RaceClock;
import com.openggf.game.arena.RaceEvent;
import com.openggf.game.arena.RaceEventResult;
import com.openggf.game.arena.RaceSignal;

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
 * @param checkpointCentreX native saved star-post centre X
 * @param checkpointCentreY native saved star-post centre Y
 */
@ModApi
public record RunStep(long ordinal, int heldMask, boolean startHeld, PlayerPose player,
                      boolean actComplete, int checkpointIndex, boolean debugAssisted,
                      RaceClock race, RaceSignal signal, RaceEventResult applied,
                      int checkpointCentreX, int checkpointCentreY) {
    public RunStep(long ordinal, int heldMask, boolean startHeld, PlayerPose player,
            boolean actComplete, int checkpointIndex, boolean debugAssisted,
            RaceClock race, RaceSignal signal, RaceEventResult applied) {
        this(ordinal,heldMask,startHeld,player,actComplete,checkpointIndex,debugAssisted,race,signal,applied,0,0);
    }
    public RunStep(long ordinal, int heldMask, boolean startHeld, PlayerPose player, boolean actComplete, int checkpointIndex, boolean debugAssisted) {
        this(ordinal, heldMask, startHeld, player, actComplete, checkpointIndex, debugAssisted, RaceClock.ACT, RaceSignal.NONE, RaceEventResult.NONE);
    }
}
