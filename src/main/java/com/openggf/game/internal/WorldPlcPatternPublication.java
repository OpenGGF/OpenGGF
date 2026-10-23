package com.openggf.game.internal;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.level.resources.KosinskiModuleQueue;

/**
 * Engine-only companion to one native world's logical PLC owner.
 * The caller lends the current frame's actual pattern owner; implementations
 * must not retain a disposable context or select an ambient session.
 */
public interface WorldPlcPatternPublication {
    RewindSnapshottable<?> recipeState();

    /** Optional private producer-decision state, registered after counts and recipes. */
    default RewindSnapshottable<?> ordinaryScheduleState() { return null; }

    /** Opaque engine load attempt; neither construction nor water initialization completes it. */
    default Object beginLevelTargetLoad(LevelManager manager) { return null; }

    /** The engine load owner reports the actual installed target, including unadmitted replacements. */
    default void levelTargetInstalled(LevelManager manager, Object attempt,
                                      com.openggf.level.Level actualLevel) { }

    /** Actual normal/failed outcome; admission is not part of a rewindable producer decision. */
    default void finishLevelTargetLoad(LevelManager manager, Object attempt,
                                       com.openggf.level.Level actualLevel, boolean completed) { }

    /** Actual water owner begins selection before its original policy calls; grants no service. */
    default void beginWaterScheduleSelection(boolean freshLoad) { }

    /** Carries only the actual water owner's already evaluated identity after normal completion. */
    default void retainWaterScheduleSelection(com.openggf.level.Level actualLevel,
                                              int featureZone, int featureAct, boolean freshLoad) { }

    /** After queued DMA, once for the frame's already claimed ordinary VBlank. */
    void publishOrdinaryPatterns(LevelManager manager, KosinskiModuleQueue journal);
}
