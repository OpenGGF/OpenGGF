package com.openggf.mods.run;

import com.openggf.game.ModApi;

/** Immutable hosted-race contract. */
@ModApi
public sealed interface RaceEvent {
    @ModApi record SwapApply(long swapId, SwapTarget target) implements RaceEvent { }
    @ModApi record SwapFallback(long swapId) implements RaceEvent { }
}
