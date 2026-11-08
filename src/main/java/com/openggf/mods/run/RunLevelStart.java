package com.openggf.mods.run;

import com.openggf.game.arena.TrackGeometry;

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
 * @param cause                  why this level became ready
 * @param maxSwapSources         ROM layout source cap for hosted swap requests
 * @param geometry               wrap and lap metadata; zero for ordinary acts
 */
@ModApi
public record RunLevelStart(RunSpec spec, String determinismFingerprint, boolean debugAssisted,
                            int levelWidth, int levelHeight, LevelStartCause cause, TrackGeometry geometry, int maxSwapSources) {
    public RunLevelStart(RunSpec spec, String determinismFingerprint, boolean debugAssisted,
            int levelWidth, int levelHeight, LevelStartCause cause, TrackGeometry geometry) {
        this(spec,determinismFingerprint,debugAssisted,levelWidth,levelHeight,cause,geometry,
                geometry.lapCount() > 0 ? 2 * (geometry.lapCount()+1) : 0);
    }
    public RunLevelStart(RunSpec spec, String determinismFingerprint, boolean debugAssisted, int levelWidth, int levelHeight, boolean deathRestart) {
        this(spec, determinismFingerprint, debugAssisted, levelWidth, levelHeight, deathRestart ? LevelStartCause.FAST_DEATH_RESTART : LevelStartCause.LAUNCH, TrackGeometry.ACT);
    }
    public boolean deathRestart() { return cause == LevelStartCause.FAST_DEATH_RESTART || cause == LevelStartCause.CHECKPOINT_RESPAWN; }
}
