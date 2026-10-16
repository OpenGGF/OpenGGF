package com.openggf.game.internal;

import com.openggf.game.rewind.RewindSnapshottable;

/** Native bonus-loop state beyond the public reward accumulator snapshot. */
public interface NativeBonusRuntimeRewindOwner {
    RewindSnapshottable<?> bonusRuntimeRewindAdapter();
    void afterBonusRuntimeRewindRestore();
}
