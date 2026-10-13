package com.openggf.mods.mutators;

import java.util.Objects;

/** Gameplay input only. Delay is measured in admitted native ticks, never UI/render calls. */
@com.openggf.game.ModApi
public record InputControlPolicy(Mode mode, int delayTicks) implements MutatorPolicy {
    public InputControlPolicy {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.DELAY ? delayTicks < 6 || delayTicks > 9 : delayTicks != 0)
            throw new IllegalArgumentException("Input delay must be 6..9 ticks, or zero for other modes");
    }
    public InputControlPolicy(Mode mode) { this(mode, mode == Mode.DELAY ? 6 : 0); }
    @Override public MutatorCapability capability() { return MutatorCapability.INPUT_CONTROL; }
    @com.openggf.game.ModApi
    public enum Mode { REVERSE_HORIZONTAL, LANDING_JUMP, DELAY, NO_FRESH_JUMP }
}
