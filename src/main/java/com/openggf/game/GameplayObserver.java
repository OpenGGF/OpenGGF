package com.openggf.game;

import com.openggf.mods.mutators.TemporaryMutators;
import com.openggf.mods.ui.PixelCanvas;

/** Owner-scoped observation of the ordinary game loop; never replaces native frame admission. */
@ModApi
public interface GameplayObserver {
    /** One logical presentation, including zero-tick and modal presentations. Inspect phase before advancing a clock. */
    default void onPresentation(GameplayObservation observation, TemporaryMutators mutators) { }
    /** One admitted native tick, immediately before its native consumers run. */
    default void onTick(GameplayObservation observation, TemporaryMutators mutators) { }
    /** A visit or phase boundary. Leases and pending completion jobs are revoked before this
     * callback; captured native-child cleanup follows even when the callback throws. */
    default void onBoundary(GameplayObservation observation, TemporaryMutators mutators) { }
    /** Screen-space view only; native world/counter state and clocks remain untouched. */
    default void drawOverlay(PixelCanvas canvas, GameplayObservation observation) { }
    default void close() { }
}
