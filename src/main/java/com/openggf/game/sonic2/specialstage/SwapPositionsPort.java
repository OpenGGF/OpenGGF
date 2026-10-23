package com.openggf.game.sonic2.specialstage;

import java.util.Objects;

/** Native shared depth-order byte; competition does not need an ordinary stage manager. */
interface SwapPositionsPort {
    void set(boolean value);
    boolean get();
    void toggle();

    static SwapPositionsPort ordinary(Sonic2SpecialStageManager owner) {
        Objects.requireNonNull(owner, "owner");
        return new SwapPositionsPort() {
            @Override public void set(boolean value) { owner.setSwapPositionsFlag(value); }
            @Override public boolean get() { return owner.getSwapPositionsFlag() != 0; }
            @Override public void toggle() { owner.toggleSwapPositionsFlag(); }
        };
    }
}
