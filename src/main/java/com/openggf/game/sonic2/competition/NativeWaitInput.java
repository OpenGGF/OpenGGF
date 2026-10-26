package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;

/**
 * Injected logical controller sample captured by the actual native wait owner.
 * Reading this port never advances input, admits a frame or selects a physical field.
 */
@FunctionalInterface
public interface NativeWaitInput {
    LogicalInputSnapshot sampleAfterWait();
}
