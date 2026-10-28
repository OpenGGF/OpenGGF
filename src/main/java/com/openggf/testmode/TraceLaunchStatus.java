package com.openggf.testmode;

import com.openggf.trace.catalog.TraceEntry;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Process-held diagnostic for a visual trace that failed to launch or replay. */
public final class TraceLaunchStatus {

    /**
     * A held launch failure. {@code headline} is a plain-language summary chosen from the failure's
     * class (never from its message); {@code reason} stays the exact diagnostic text, which players
     * and creators search for.
     */
    public record Failure(String traceLabel, String headline, String reason) {
        public Failure {
            traceLabel = requireText(traceLabel, "traceLabel");
            headline = requireText(headline, "headline");
            reason = requireText(reason, "reason");
        }

        public Failure(String traceLabel, String reason) { this(traceLabel, GENERIC_HEADLINE, reason); }
    }

    static final String GENERIC_HEADLINE = "The trace could not be started";

    private static final AtomicReference<Failure> HELD_FAILURE = new AtomicReference<>();

    private TraceLaunchStatus() {
    }

    public static void record(TraceEntry entry, Throwable failure) {
        Objects.requireNonNull(entry, "entry");
        HELD_FAILURE.set(new Failure(catalogLabel(entry), headline(failure), diagnostic(failure)));
    }

    public static void record(TraceEntry entry, String reason) {
        Objects.requireNonNull(entry, "entry");
        HELD_FAILURE.set(new Failure(catalogLabel(entry), reason));
    }

    public static Optional<Failure> current() {
        return Optional.ofNullable(HELD_FAILURE.get());
    }

    public static void clear() {
        HELD_FAILURE.set(null);
    }

    static String catalogLabel(TraceEntry entry) {
        String identity = entry.isRun()
                ? entry.runManifest().runId()
                : entry.dir().getFileName().toString();
        return entry.gameId() + "/" + identity;
    }

    /** The headline for a failure, by the class of the failure or of a cause in its chain. */
    static String headline(Throwable failure) {
        if (failure == null) return "The trace session ended before it started";
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof java.io.IOException || cursor instanceof java.io.UncheckedIOException)
                return "A trace file could not be read";
            if (cursor instanceof OutOfMemoryError) return "There was not enough memory to load the trace";
        }
        return GENERIC_HEADLINE;
    }

    private static String diagnostic(Throwable failure) {
        if (failure == null) {
            return "The trace session ended before launch completed";
        }
        Throwable cursor = failure;
        String message = null;
        while (cursor != null) {
            if (cursor.getMessage() != null && !cursor.getMessage().isBlank()) {
                message = cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return message != null ? message : failure.getClass().getSimpleName();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
