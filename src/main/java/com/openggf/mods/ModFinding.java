package com.openggf.mods;

import java.util.Objects;

/** Stable, renderable diagnostic produced while discovering a mod. */
public record ModFinding(ModFindingSeverity severity, String code, String message,
                         String assetPath) {
    public ModFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Finding message must not be blank");
        }
        if (!code.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("Finding code must be uppercase snake case: " + code);
        }
    }
}
