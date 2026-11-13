package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;

/**
 * Injected logical controller sample captured by the actual native wait owner.
 * Reading this port never advances input, admits a frame or selects a physical field.
 */
@FunctionalInterface
public interface NativeWaitInput {
    LogicalInputSnapshot sampleAfterWait();

    /** Observe the actual input owner; this never polls or advances its edge latch. */
    static NativeWaitInput from(com.openggf.control.InputHandler input) {
        java.util.Objects.requireNonNull(input, "native input owner");
        return input::logical;
    }
}
