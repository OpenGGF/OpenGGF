package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * The authoritative description of a route next to its rows. Codecs never infer launch identity
 * from a filename or movie header.
 */
public record RouteManifest(LaunchContract launch, ContentHash tapeHash, int rowCount, Provenance provenance,
                            List<QualificationStamp> qualification, NativeFacts nativeFacts) {
    public static final String FORMAT = "openggf-route/1";

    public RouteManifest {
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(tapeHash, "tapeHash");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(nativeFacts, "nativeFacts");
        qualification = List.copyOf(qualification);
        if (rowCount < 0) throw new IllegalArgumentException("negative row count");
    }

    public RouteManifest withQualification(List<QualificationStamp> stamps) {
        return new RouteManifest(launch, tapeHash, rowCount, provenance, stamps, nativeFacts);
    }

    public RouteManifest withNativeFacts(NativeFacts facts) {
        return new RouteManifest(launch, tapeHash, rowCount, provenance, qualification, facts);
    }

    public RouteManifest withProvenance(Provenance next) {
        return new RouteManifest(launch, tapeHash, rowCount, next, qualification, nativeFacts);
    }
}
