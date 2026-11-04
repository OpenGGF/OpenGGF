package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * Where a route's rows came from: the producing tool, the engine revision it ran on, the source
 * it was derived from, and free notes (milestone rows, authoring history). Not part of content
 * identity.
 */
public record Provenance(String producer, String engine, String source, List<String> notes) {
    public Provenance {
        Objects.requireNonNull(producer, "producer");
        engine = engine == null ? "" : engine;
        source = source == null ? "" : source;
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}
