package com.openggf.trace;

/** Severity of a single field divergence. */
@com.openggf.game.ModApi
public enum Severity {
    MATCH,   // No divergence
    WARNING, // Within tolerance but not exact
    ERROR    // Exceeds tolerance
}

