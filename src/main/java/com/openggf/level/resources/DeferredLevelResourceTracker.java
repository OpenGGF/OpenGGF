package com.openggf.level.resources;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-load exact-once consumption tracker for deferred resource descriptors.
 */
public final class DeferredLevelResourceTracker {
    private final Set<DeferredLevelResourceDescriptor> requested;
    private final Set<DeferredLevelResourceDescriptor> consumed =
            new LinkedHashSet<>();

    DeferredLevelResourceTracker(
            List<DeferredLevelResourceDescriptor> descriptors) {
        requested = new LinkedHashSet<>(descriptors);
    }

    public static DeferredLevelResourceTracker none() {
        return DeferredLevelResourceManifest.EMPTY.newTracker();
    }

    public boolean omitIfRequested(
            DeferredLevelResourceDescriptor descriptor) {
        if (!requested.contains(descriptor)) {
            return false;
        }
        if (!consumed.add(descriptor)) {
            throw new IllegalStateException(
                    "deferred level resource was consumed more than once: "
                            + descriptor);
        }
        return true;
    }

    public void verifyFullyConsumed() {
        if (consumed.size() != requested.size()) {
            Set<DeferredLevelResourceDescriptor> missing =
                    new LinkedHashSet<>(requested);
            missing.removeAll(consumed);
            throw new IllegalStateException(
                    "deferred level resources were not consumed exactly once: "
                            + missing);
        }
    }

    public boolean isEmpty() {
        return requested.isEmpty();
    }
}
