package com.openggf;

/** Process/session determinism gate for filesystem-backed external content. */
public enum ExternalContentMode {
    NORMAL,
    STARTUP_DETERMINISTIC,
    SESSION_DETERMINISTIC
}
