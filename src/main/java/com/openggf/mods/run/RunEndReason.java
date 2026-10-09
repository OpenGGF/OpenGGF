package com.openggf.mods.run;

import com.openggf.game.ModApi;

/** Why a run ended. */
@ModApi
public enum RunEndReason {
    /** The act was completed and the run's policy returns control to the host. */
    ACT_COMPLETED,
    /** The host asked to leave through {@link RunHandle#leave()}. */
    LEFT,
    /** The player quit to the title, or the engine closed the run. */
    ABORTED,
    /** The run's ROM or level could not be loaded. */
    LOAD_FAILED
}
