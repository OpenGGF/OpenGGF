package com.openggf.stageplay;

/** Engine-owned execution classification; the document carries only portable policy values. */
public final class StageplayWorldPolicies {
    private StageplayWorldPolicies() { }

    public static boolean usesNativeWorld(StageplayDocument.WorldPolicy policy) {
        return policy != StageplayDocument.WorldPolicy.LEGACY_TERRAIN_ONLY;
    }

    public static boolean legacy(StageplayDocument.WorldPolicy policy) {
        return policy == StageplayDocument.WorldPolicy.LEGACY_TERRAIN_ONLY
                || policy == StageplayDocument.WorldPolicy.LEGACY_LIVE;
    }
}
