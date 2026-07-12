package com.openggf.game.sonic2.dataselect;

/** Structured non-destructive fallback reported while resolving an S2 save destination. */
public record S2SaveFinding(String ownerModId, String code, String detail) {
    public S2SaveFinding(String code, String detail) { this(null, code, detail); }
    public S2SaveFinding {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must be nonblank");
        if (detail == null || detail.isBlank()) throw new IllegalArgumentException("detail must be nonblank");
    }
}
