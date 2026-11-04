package com.openggf.route;

import java.util.List;
import java.util.Locale;

/**
 * Where every output row came from. Original rows are counted against the original's length;
 * rows authored afresh never count as kept, even when their bytes match.
 *
 * @param firstChangedRow the first output row that differs from the original's row at the same
 *                        index (the shorter length when one tape is a prefix of the other); rows
 *                        before it are unchanged
 * @param nativeStale     the original carried native facts or a native verdict; they described the
 *                        old rows, are not carried over, and the new rows need a new native check
 */
public record RepairReport(int originalRows, int outputRows, List<Span> spans, int firstChangedRow,
                           boolean nativeStale) {
    public RepairReport {
        spans = List.copyOf(spans);
    }

    public RepairReport(int originalRows, int outputRows, List<Span> spans) {
        this(originalRows, outputRows, spans, 0, false);
    }

    /** The same spans with the original comparison filled in. */
    RepairReport comparedWith(InputTape original, InputTape output, boolean originalHadNativeEvidence) {
        int limit = Math.min(original.size(), output.size());
        int first = 0;
        while (first < limit && original.row(first).equals(output.row(first))) first++;
        return new RepairReport(originalRows, outputRows, spans, first, originalHadNativeEvidence);
    }

    /** A run of output rows and its origin. */
    public sealed interface Span {
        int outputStart();

        int length();

        /** Original rows {@code [sourceStart, sourceStart + length)} kept verbatim. */
        record Original(int sourceStart, int outputStart, int length) implements Span { }

        /** Rows of a separate reference source (not the original) reused verbatim. */
        record Reference(int sourceStart, int outputStart, int length) implements Span { }

        /** Rows the search authored. */
        record Authored(int outputStart, int length) implements Span { }
    }

    /** Original rows kept verbatim, as {@code (sourceStart, outputStart, length)} slices. */
    public List<Span.Original> originalSlices() {
        return spans.stream().filter(s -> s instanceof Span.Original).map(s -> (Span.Original) s).toList();
    }

    /** The output ranges the search authored. */
    public List<Span.Authored> searchedRanges() {
        return spans.stream().filter(s -> s instanceof Span.Authored).map(s -> (Span.Authored) s).toList();
    }

    public int keptRows() {
        return spans.stream().filter(s -> s instanceof Span.Original).mapToInt(Span::length).sum();
    }

    public int authoredRows() {
        return spans.stream().filter(s -> s instanceof Span.Authored).mapToInt(Span::length).sum();
    }

    public int referenceRows() {
        return spans.stream().filter(s -> s instanceof Span.Reference).mapToInt(Span::length).sum();
    }

    /** Kept original rows over the original's length; zero for an empty original. */
    public double keptShare() {
        return originalRows == 0 ? 0 : keptRows() / (double) originalRows;
    }

    public String summary() {
        var out = new StringBuilder(String.format(Locale.ROOT,
                "kept %d/%d original rows (%.1f%%), %d reference rows, %d authored rows, %d output rows; "
                        + "unchanged through row %d%s",
                keptRows(), originalRows, 100 * keptShare(), referenceRows(), authoredRows(), outputRows,
                firstChangedRow, nativeStale ? "; native facts are stale and were not carried over" : ""));
        for (Span span : spans) {
            out.append("\n  ");
            switch (span) {
                case Span.Original o -> out.append("original ").append(o.sourceStart()).append("..")
                        .append(o.sourceStart() + o.length() - 1).append(" -> output ").append(o.outputStart());
                case Span.Reference r -> out.append("reference ").append(r.sourceStart()).append("..")
                        .append(r.sourceStart() + r.length() - 1).append(" -> output ").append(r.outputStart());
                case Span.Authored a -> out.append("authored ").append(a.length()).append(" rows at output ")
                        .append(a.outputStart());
            }
        }
        return out.toString();
    }
}
