package com.openggf.game.rewind;

/**
 * Presentation-only probe restores (the NATIVE widescreen dormant-object bake), without
 * adding members to the RewindRegistry mod API.
 */
public final class RewindPresentationProbeAccess {
    private RewindPresentationProbeAccess() { }

    /** Restores the snapshot taken before a presentation probe; see the registry method. */
    public static void restoreAfterProbe(RewindRegistry registry, CompositeSnapshot snapshot) {
        registry.restoreAfterPresentationProbe(snapshot);
    }

    /** True while a probe restore runs, so host callbacks can skip user-rewind side effects. */
    public static boolean isProbeRestore(RewindRegistry registry) {
        return registry.isPresentationProbeRestore();
    }
}
