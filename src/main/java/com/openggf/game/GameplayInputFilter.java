package com.openggf.game;

import com.openggf.control.PlayerInputState;

/** Deterministic transformation applied to one recorded/replayed logical input snapshot. */
@ModApi
@FunctionalInterface
public interface GameplayInputFilter {
    GameplayInputFilter IDENTITY = input -> input;

    /**
     * Filters one raw snapshot. Constructing the returned {@link PlayerInputState} re-derives
     * legacy jump bits from its action masks, so filters must preserve the intended action masks.
     */
    PlayerInputState filter(PlayerInputState rawSnapshot);
}
