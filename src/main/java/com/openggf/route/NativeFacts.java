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
public record NativeFacts(Optional<NativeEntry> entry, List<LagFact> lag, String exportBlocker) {
    public static final NativeFacts NONE = new NativeFacts(Optional.empty(), List.of());

    public NativeFacts(Optional<NativeEntry> entry, List<LagFact> lag) { this(entry, lag, ""); }

    public NativeFacts {
        Objects.requireNonNull(exportBlocker, "exportBlocker");
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
     * row 0 (legacy) or the declared gameplay boundary. The exported tape is witness
     * {@code [0,prefixRows)} plus engine {@code [engineAnchorRows,end)}. Both counts name
     * completed rows, so the next offered row is the first gameplay input. The boundary
     * is an observed owner transition, never a fitted offset. {@code movie} is a BK2,
     * input log or frozen .script; {@code inputHash} binds exactly its prefix rows.
     */
    public record NativeEntry(String movie, int prefixRows, ContentHash inputHash,
                              int engineAnchorRows, String anchorBoundary) {
        public NativeEntry(String movie, int prefixRows, ContentHash inputHash) {
            this(movie, prefixRows, inputHash, 0, "");
        }
        public NativeEntry {
            Objects.requireNonNull(movie, "movie");
            Objects.requireNonNull(inputHash, "inputHash");
            Objects.requireNonNull(anchorBoundary, "anchorBoundary");
            if (engineAnchorRows < 0) throw new IllegalArgumentException("negative engine anchor");
            if (engineAnchorRows > 0 && anchorBoundary.isBlank())
                throw new IllegalArgumentException("an engine splice needs an observable anchor boundary");
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
