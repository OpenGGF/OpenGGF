package com.openggf.game.timeattack;

import com.openggf.ghost.GhostFrame;

/** Package bridge for coordinator tests without widening production-only test seams. */
public final class TimeAttackRuntimeTestBridge {
    private TimeAttackRuntimeTestBridge() {
    }

    public static void begin(TimeAttackRuntime runtime, String fingerprint) {
        runtime.beginAttemptForTest(fingerprint);
    }

    public static void tick(TimeAttackRuntime runtime, int heldMask,
                            boolean endOfLevel, GhostFrame frame) {
        runtime.tickForTest(heldMask, false, endOfLevel, -1, frame);
    }
}
