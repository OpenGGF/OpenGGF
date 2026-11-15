package com.openggf.game.soniccd.release;

import com.openggf.game.soniccd.CdReleaseVariant;

import java.net.URI;
import java.util.Objects;

/**
 * One known retail content record, independent of console environment.
 * Multiple content records may share a software variant. A match establishes
 * neither physical pressing provenance nor BIOS, hardware or gameplay certification.
 * No numeric revision is inferred when the primary record supplies none.
 */
public record CdRetailRelease(String key, CdReleaseVariant variant, String normalizedSha256, URI reference) {
    public CdRetailRelease {
        Objects.requireNonNull(key);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(normalizedSha256);
        Objects.requireNonNull(reference);
        if (key.isBlank() || !normalizedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid retail content descriptor");
        }
    }
}
