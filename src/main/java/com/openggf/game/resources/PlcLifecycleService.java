package com.openggf.game.resources;

/** Game-owned mapping from semantic ROM loops to PLC service and preparation. */
public interface PlcLifecycleService extends QueueDiagnosticsProvider {
    void serviceVBlank(PlcLifecyclePhase phase);

    boolean hasPreparationBoundary(PlcLifecyclePhase phase);

    void prepareAfterLoop(PlcLifecyclePhase phase);
}
