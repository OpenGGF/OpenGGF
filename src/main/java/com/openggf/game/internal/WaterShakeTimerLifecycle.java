package com.openggf.game.internal;

import com.openggf.game.DynamicWaterHandler;

/**
 * Internal water-handler extension for writes associated with its shake timer.
 * WaterSystem invokes these edges at the existing handler/countdown boundary;
 * the game owner decides what those edges publish. No callback runs on restore.
 */
public interface WaterShakeTimerLifecycle extends DynamicWaterHandler {
    void onShakeTimerStarted();
    void onShakeTimerExpired();
}
