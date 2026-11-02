package com.openggf.route;

import java.util.List;
import java.util.Objects;

/** A manifest and the tape it describes. Content identity covers launch, checks and rows only. */
public record RouteArtifact(RouteManifest manifest, InputTape tape) {
    public RouteArtifact {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(tape, "tape");
        if (manifest.rowCount() != tape.size() || !manifest.tapeHash().equals(tape.hash())) {
            throw new IllegalArgumentException("manifest declares " + manifest.rowCount() + " rows "
                    + manifest.tapeHash().shortForm() + " but the tape has " + tape.size() + " " + tape.hash().shortForm());
        }
    }

    /** A new, unqualified route over {@code tape} with no checks beyond its rows. */
    public static RouteArtifact of(LaunchContract launch, InputTape tape, Provenance provenance) {
        return of(launch, GoalProgram.NONE, tape, provenance);
    }

    /** A new, unqualified route over {@code tape} whose replay evaluates {@code checks}. */
    public static RouteArtifact of(LaunchContract launch, GoalProgram checks, InputTape tape, Provenance provenance) {
        return new RouteArtifact(new RouteManifest(launch, tape.hash(), tape.size(), checks, false, provenance,
                List.of(), NativeFacts.NONE), tape);
    }

    public LaunchContract launch() {
        return manifest.launch();
    }

    public GoalProgram checks() {
        return manifest.checks();
    }

    /** Hash over the canonical launch, checks and rows; excludes provenance, stamps, draft flag and native facts. */
    public ContentHash content() {
        return RouteCodec.contentHash(manifest.launch(), manifest.checks(), tape);
    }

    /** The same rows and launch with other checks (a new content). */
    public RouteArtifact withChecks(GoalProgram checks) {
        return of(launch(), checks, tape, manifest.provenance());
    }

    /** Whether a passed engine stamp exists for this exact content. */
    public boolean engineQualified() {
        ContentHash content = content();
        return !manifest.draft() && manifest.qualification().stream()
                .anyMatch(s -> s.scope() == ReplayScope.ENGINE && s.passed() && s.content().equals(content));
    }
}
