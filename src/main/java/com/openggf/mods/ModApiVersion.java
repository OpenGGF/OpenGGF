package com.openggf.mods;

public final class ModApiVersion {
    public static final SemanticVersion CURRENT = new SemanticVersion(1, 1, 0);
    /** Review-only Phase 3 surface; not the published runtime compatibility version. */
    public static final SemanticVersion PHASE3_CANDIDATE = new SemanticVersion(1, 2, 0);

    private ModApiVersion() {
    }
}
