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
    /** A visit, phase or disposal boundary. Host revocation precedes creator cleanup. */
    default void onBoundary(GameplayObservation observation, TemporaryMutators mutators) { }
    /** Screen-space view only; native world/counter state and clocks remain untouched. */
    default void drawOverlay(PixelCanvas canvas, GameplayObservation observation) { }
    default void close() { }
}
