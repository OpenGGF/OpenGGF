package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * The authoritative description of a route next to its rows. Codecs never infer launch identity
 * from a filename or movie header.
 *
 * @param checks the goal program replay evaluates; part of the route's content identity
 * @param draft  an unqualified draft: editors may save and reopen it, but every consumer that
 *               loads a route as qualified refuses it
 */
public record RouteManifest(LaunchContract launch, ContentHash tapeHash, int rowCount, GoalProgram checks,
                            boolean draft, Provenance provenance, List<QualificationStamp> qualification,
                            NativeFacts nativeFacts) {
    public static final String FORMAT = "openggf-route/1";

    public RouteManifest {
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(tapeHash, "tapeHash");
        Objects.requireNonNull(checks, "checks");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(nativeFacts, "nativeFacts");
        qualification = List.copyOf(qualification);
        if (rowCount < 0) throw new IllegalArgumentException("negative row count");
        if (draft && !qualification.isEmpty()) throw new IllegalArgumentException("a draft carries no qualification");
    }

    public RouteManifest withQualification(List<QualificationStamp> stamps) {
        return new RouteManifest(launch, tapeHash, rowCount, checks, draft, provenance, stamps, nativeFacts);
    }

    public RouteManifest withNativeFacts(NativeFacts facts) {
        return new RouteManifest(launch, tapeHash, rowCount, checks, draft, provenance, qualification, facts);
    }

    public RouteManifest withProvenance(Provenance next) {
        return new RouteManifest(launch, tapeHash, rowCount, checks, draft, next, qualification, nativeFacts);
    }

    RouteManifest asDraft(boolean value) {
        return new RouteManifest(launch, tapeHash, rowCount, checks, value, provenance,
                value ? List.of() : qualification, nativeFacts);
    }
}
