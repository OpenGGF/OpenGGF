package com.openggf;

import java.util.Objects;

/** Explicit gate selected before boot discovery and before each gameplay session. */
public record ExternalContentPolicy(ExternalContentMode mode) {
    public ExternalContentPolicy {
        Objects.requireNonNull(mode, "mode");
    }

    public boolean mayScanAtBoot() {
        return mode != ExternalContentMode.STARTUP_DETERMINISTIC;
    }

    public boolean mayUseInSession() {
        return mode == ExternalContentMode.NORMAL;
    }
}
