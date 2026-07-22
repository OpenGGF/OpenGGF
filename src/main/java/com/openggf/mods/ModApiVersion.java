package com.openggf.mods;

public final class ModApiVersion {
    /**
     * First published compiled-mod API baseline.
     *
     * <p>Mod API 0.7 is the first release baseline; earlier 1.x/2.x values were
     * provisional development markers and carry no compatibility promise.
     */
    public static final SemanticVersion CURRENT = SemanticVersion.parse("0.7.0");

    private ModApiVersion() {
    }
}
