package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.RecordedOrdinalSpan;

import java.util.Map;
import java.util.Objects;

/**
 * Run-level index of the recorded hardware-work ordinals that fall in the
 * spans between structural segments.
 *
 * <p>A recorder observes hardware completions across the whole movie, but a
 * run fixture only represents the segments it compares. The spans in between
 * — special-stage results, level reload, a locked level intro — still advanced
 * the ROM's hardware ledger, so the next segment's recorded ordinals start
 * above where the previous segment's ended. Some entry loops submit no work;
 * others reproduce and claim the complete resource batch independently.
 *
 * <p>Per segment boundary and kind, this holds the first/last ordinal and their
 * fingerprints. Fingerprints only verify claimed work; nothing here creates,
 * prepares or completes hardware work.
 */
public final class HardwareTimingInterstitialSpans {

    private static final HardwareTimingInterstitialSpans EMPTY =
            new HardwareTimingInterstitialSpans(Map.of());

    private final Map<Integer, Map<HardwareWorkKind, RecordedOrdinalSpan>> spansByBoundary;

    HardwareTimingInterstitialSpans(
            Map<Integer, Map<HardwareWorkKind, RecordedOrdinalSpan>> spansByBoundary) {
        Objects.requireNonNull(spansByBoundary, "spansByBoundary");
        this.spansByBoundary = spansByBoundary.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }

    /** No interstitial sidecar was recorded; every handoff behaves as before. */
    public static HardwareTimingInterstitialSpans empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return spansByBoundary.isEmpty();
    }

    /**
     * The ordinal spans the recording consumed after the given segment index
     * and before the next one. An index with no recorded span returns an empty
     * map, which leaves the handoff completely unchanged.
     */
    public Map<HardwareWorkKind, RecordedOrdinalSpan> spansAfterSegment(int segmentIndex) {
        return spansByBoundary.getOrDefault(segmentIndex, Map.of());
    }

    public record InitializationSlice(Map<HardwareWorkKind, RecordedOrdinalSpan> prefix,
                                      Map<HardwareWorkKind, RecordedOrdinalSpan> remainder) { }

    /** Splits ordinal receipts around dedicated input; provenance frame fields are never read. */
    public static InitializationSlice splitInitializationReadiness(
            Map<HardwareWorkKind, RecordedOrdinalSpan> remaining, HardwareTimingSchedule readiness) {
        var prefix = new java.util.EnumMap<HardwareWorkKind, RecordedOrdinalSpan>(HardwareWorkKind.class);
        var remainder = new java.util.EnumMap<HardwareWorkKind, RecordedOrdinalSpan>(HardwareWorkKind.class);
        remainder.putAll(remaining);
        for (HardwareWorkKind kind : HardwareWorkKind.values()) {
            var edges = readiness.edges().stream().filter(edge -> edge.kind() == kind).toList();
            if (edges.isEmpty()) continue;
            var span = remaining.get(kind);
            long first = edges.getFirst().ordinal(), last = edges.getLast().ordinal();
            if (span == null || span.submissionFingerprints().isEmpty() || first < span.firstOrdinal() || last > span.lastOrdinal()
                    || last - first + 1 != edges.size())
                throw new IllegalArgumentException("initialization readiness is outside the remaining ordinal span for " + kind);
            int start = Math.toIntExact(first - span.firstOrdinal());
            int end = Math.toIntExact(last - span.firstOrdinal() + 1);
            for (int index = 0; index < edges.size(); index++) {
                if (edges.get(index).ordinal() != first + index
                        || !edges.get(index).submissionFingerprint().equals(span.submissionFingerprints().get(start + index)))
                    throw new IllegalArgumentException("initialization readiness fingerprint/order differs from ordinal receipts");
            }
            if (start > 0) prefix.put(kind, new RecordedOrdinalSpan(span.firstOrdinal(), first - 1,
                    span.submissionFingerprints().subList(0, start)));
            remainder.remove(kind);
            if (last < span.lastOrdinal()) remainder.put(kind, new RecordedOrdinalSpan(last + 1, span.lastOrdinal(),
                    span.submissionFingerprints().subList(end, span.submissionFingerprints().size())));
        }
        return new InitializationSlice(Map.copyOf(prefix), Map.copyOf(remainder));
    }
}
