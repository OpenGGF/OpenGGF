package com.openggf.game.internal;

import com.openggf.game.resources.PlcLifecyclePhase;

/**
 * Engine-internal opt-in for a controlled HOLD that represents a serviced native VBlank.
 * This is distinct from host/window/configuration suspension before the controller runs.
 * This interface is not part of the public Mod API.
 */
public interface ControlledVBlankOnlyOwner {
    /**
     * Selects the held row's phase after {@code beforeFrame}; this query must not perform service.
     *
     * <p>Null keeps the ordinary unserviced creator/static HOLD. Non-null phases must satisfy
     * {@code LevelFrameStep.serviceVBlankOnly}: NORMAL_PAUSE, LAG or SPECIAL_STAGE_PAUSE.
     * The lifecycle's existing blocking fade owner takes precedence over this request.
     * A serviced HOLD executes no gameplay body and completes with {@code afterTick(false)}.
     */
    PlcLifecyclePhase heldVBlankPhase();
}
