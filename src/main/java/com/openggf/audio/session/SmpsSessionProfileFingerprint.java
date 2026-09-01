package com.openggf.audio.session;

import java.util.Objects;

public record SmpsSessionProfileFingerprint(
        String baseGameId,
        long sourceGeneration,
        SmpsPhysicalPolicy.Identity physicalPolicyId,
        SmpsPhysicalDevice.Settings settings) {
    public SmpsSessionProfileFingerprint {
        Objects.requireNonNull(baseGameId, "baseGameId");
        if (sourceGeneration < 0) {
            throw new IllegalArgumentException(
                    "sourceGeneration must be non-negative");
        }
        Objects.requireNonNull(physicalPolicyId, "physicalPolicyId");
        Objects.requireNonNull(settings, "settings");
    }
}
