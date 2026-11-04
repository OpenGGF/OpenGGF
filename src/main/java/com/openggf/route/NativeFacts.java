package com.openggf.route;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Observations of the route on native BizHawk. Comparison-only: these are never fed to the
 * engine, never used to plan and never predicted.
 *
 * @param entry the native boot witness that reaches row 0, if one exists
 * @param lag   observed lag VBlanks; each makes the composed movie repeat the previous row once
 */
public record NativeFacts(Optional<NativeEntry> entry, List<LagFact> lag) {
    public static final NativeFacts NONE = new NativeFacts(Optional.empty(), List.of());

    public NativeFacts {
        Objects.requireNonNull(entry, "entry");
        lag = List.copyOf(lag);
        int previous = -1;
        for (LagFact fact : lag) {
            if (fact.beforeRow() < previous) throw new IllegalArgumentException("lag facts out of order: " + lag);
            previous = fact.beforeRow();
        }
    }

    /**
     * A recorded movie whose rows {@code [0, prefixRows)} take native power-on to the route's
     * row 0. {@code movie} is repository-relative; {@code inputHash} binds its input rows.
     */
    public record NativeEntry(String movie, int prefixRows, ContentHash inputHash) {
        public NativeEntry {
            Objects.requireNonNull(movie, "movie");
            Objects.requireNonNull(inputHash, "inputHash");
            if (prefixRows < 0) throw new IllegalArgumentException("negative prefix");
        }
    }

    /**
     * One observed lag VBlank: native consumed a movie row without polling the pad just before
     * tape row {@code beforeRow} was polled. Stamped with the harness and route content it was
     * observed against.
     */
    public record LagFact(int beforeRow, String harness, ContentHash content) {
        public LagFact {
            Objects.requireNonNull(harness, "harness");
            Objects.requireNonNull(content, "content");
            if (beforeRow < 1) throw new IllegalArgumentException("a filler copies a previous row: " + beforeRow);
        }
    }
}
