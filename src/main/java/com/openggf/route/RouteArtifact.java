package com.openggf.route;

import java.util.List;
import java.util.Objects;

/** A manifest and the tape it describes. Content identity covers launch and rows only. */
public record RouteArtifact(RouteManifest manifest, InputTape tape) {
    public RouteArtifact {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(tape, "tape");
        if (manifest.rowCount() != tape.size() || !manifest.tapeHash().equals(tape.hash())) {
            throw new IllegalArgumentException("manifest declares " + manifest.rowCount() + " rows "
                    + manifest.tapeHash().shortForm() + " but the tape has " + tape.size() + " " + tape.hash().shortForm());
        }
    }

    /** A new, unqualified route over {@code tape}. */
    public static RouteArtifact of(LaunchContract launch, InputTape tape, Provenance provenance) {
        return new RouteArtifact(new RouteManifest(launch, tape.hash(), tape.size(), provenance, List.of(),
                NativeFacts.NONE), tape);
    }

    public LaunchContract launch() {
        return manifest.launch();
    }

    /** Hash over the canonical launch and the rows; excludes provenance, stamps and native facts. */
    public ContentHash content() {
        return RouteJson.contentHash(manifest.launch(), tape);
    }
}
