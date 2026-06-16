package com.openggf.tests.trace;

import com.openggf.trace.FrameComparison;

final class FrontierReplayStopper {
    private final boolean enabled;
    private final int contextRadius;
    private int firstErrorFrame = -1;

    private FrontierReplayStopper(boolean enabled, int contextRadius) {
        this.enabled = enabled;
        this.contextRadius = Math.max(0, contextRadius);
    }

    static FrontierReplayStopper fromSystemProperties() {
        int contextRadius = TraceReplayConsole.contextRadius();
        return Boolean.getBoolean("trace.frontierOnly")
                ? enabled(contextRadius)
                : disabled(contextRadius);
    }

    static FrontierReplayStopper enabled(int contextRadius) {
        return new FrontierReplayStopper(true, contextRadius);
    }

    static FrontierReplayStopper disabled(int contextRadius) {
        return new FrontierReplayStopper(false, contextRadius);
    }

    void observe(FrameComparison comparison) {
        if (!enabled || comparison == null || firstErrorFrame >= 0 || !comparison.hasError()) {
            return;
        }
        firstErrorFrame = comparison.frame();
    }

    boolean shouldStopAfterFrame(int frame) {
        return enabled && firstErrorFrame >= 0 && frame >= firstErrorFrame + contextRadius;
    }
}
