package com.openggf.trace;

import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DynamicArtSpillNormalization} rebinding semantics: spilled submission
 * edges move to their pass row with frame stamps rewritten, and nothing else
 * loosens — missing, extra, and wrongly-attributed submissions must still fail
 * through {@link DynamicArtSpecialStageComparator} after normalization.
 */
class TestDynamicArtSpillNormalization {

    private static final int PASS_ROW = 181;
    private static final int LAG_ROW = 182;
    private static final int SPILL_ROW = 183;

    private static DynamicArtTransfer.SegmentEdge expectedSubmitted(
            long ordinal, long transferId, String owner,
            int logicalFrame, int publicationFrame) {
        return new DynamicArtTransfer.SegmentEdge(
                ordinal, transferId, "submitted", owner, "segment",
                1, logicalFrame, 0, publicationFrame, false,
                0x33B3E,
                List.of(new DynamicArtTransfer.Request(-1, -1, 0xFF0000, 0x5CA0, 0x80)));
    }

    private static DynamicArtDiagnosticsSnapshot.Edge actualSubmitted(
            long ordinal, long transferId, String owner, int row, int rowIndex) {
        return new DynamicArtDiagnosticsSnapshot.Edge(
                ordinal, transferId, "submitted", owner,
                1, row, rowIndex, row, false,
                List.of(DynamicArtDiagnosticsSnapshot.Request.ram(
                        0xFF0000, 0x5CA0, 0x80)));
    }

    /** Rows: PASS_ROW non-lag, LAG_ROW lag, SPILL_ROW non-lag. */
    private static Map<Integer, TraceEvent.DynamicArtTransferState> normalize(
            Map<Integer, TraceEvent.DynamicArtTransferState> states) {
        return DynamicArtSpillNormalization.rebindSubmissionSpills(
                states, SPILL_ROW + 1, row -> row == LAG_ROW);
    }

    private static Map<Integer, TraceEvent.DynamicArtTransferState> spilledFixture() {
        // Recorder: sonic submitted on the pass row; tails from the same pass
        // crossed the frame boundary (logical = the lag row, published later).
        return Map.of(
                PASS_ROW, new TraceEvent.DynamicArtTransferState(
                        PASS_ROW,
                        List.of(expectedSubmitted(10, 5, "ss-sonic", PASS_ROW, PASS_ROW)),
                        List.of(5L)),
                SPILL_ROW, new TraceEvent.DynamicArtTransferState(
                        SPILL_ROW,
                        List.of(expectedSubmitted(11, 6, "ss-tails", LAG_ROW, SPILL_ROW)),
                        List.of(5L, 6L)));
    }

    private static long errorCount(FrameComparison comparison) {
        return comparison.fields().values().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .count();
    }

    @Test
    void spilledSubmissionRebindsToPassRowAndMatchesAtomicPublication() {
        Map<Integer, TraceEvent.DynamicArtTransferState> normalized =
                normalize(spilledFixture());

        TraceEvent.DynamicArtTransferState passRow = normalized.get(PASS_ROW);
        assertEquals(2, passRow.edges().size(),
                "spilled tails submission joins its pass row");
        assertEquals(List.of(10L, 11L),
                passRow.edges().stream()
                        .map(DynamicArtTransfer.SegmentEdge::edgeOrdinal).toList(),
                "in-pass ordinal order preserved");
        assertEquals(PASS_ROW, passRow.edges().get(1).logicalFrame());
        assertEquals(PASS_ROW, passRow.edges().get(1).publicationFrame());
        assertTrue(normalized.get(SPILL_ROW).edges().isEmpty(),
                "spill row no longer expects the moved submission");
        assertEquals(List.of(5L, 6L), passRow.outstandingTransferIds(),
                "moved submission is outstanding from its pass row");

        // The engine publishes the whole pass atomically on the pass row.
        DynamicArtSpecialStageComparator comparator =
                new DynamicArtSpecialStageComparator();
        FrameComparison comparison = comparator.compare(
                passRow,
                new DynamicArtDiagnosticsSnapshot(PASS_ROW, List.of(
                        actualSubmitted(10, 5, "ss-sonic", PASS_ROW, 0),
                        actualSubmitted(11, 6, "ss-tails", PASS_ROW, 1)),
                        List.of(5L, 6L)));
        assertEquals(0, errorCount(comparison),
                "atomic pass publication matches the rebased expectation: "
                        + comparison.fields().values().stream()
                                .filter(f -> f.severity() == Severity.ERROR)
                                .map(Object::toString).toList());
    }

    @Test
    void missingSubmissionStillFails() {
        Map<Integer, TraceEvent.DynamicArtTransferState> normalized =
                normalize(spilledFixture());
        FrameComparison comparison = new DynamicArtSpecialStageComparator().compare(
                normalized.get(PASS_ROW),
                new DynamicArtDiagnosticsSnapshot(PASS_ROW, List.of(
                        actualSubmitted(10, 5, "ss-sonic", PASS_ROW, 0)),
                        List.of(5L)));
        assertTrue(errorCount(comparison) > 0,
                "a genuinely missing submission must still error");
    }

    @Test
    void extraSubmissionStillFails() {
        Map<Integer, TraceEvent.DynamicArtTransferState> normalized =
                normalize(spilledFixture());
        FrameComparison comparison = new DynamicArtSpecialStageComparator().compare(
                normalized.get(PASS_ROW),
                new DynamicArtDiagnosticsSnapshot(PASS_ROW, List.of(
                        actualSubmitted(10, 5, "ss-sonic", PASS_ROW, 0),
                        actualSubmitted(11, 6, "ss-tails", PASS_ROW, 1),
                        actualSubmitted(12, 7, "ss-tails-tails", PASS_ROW, 2)),
                        List.of(5L, 6L, 7L)));
        assertTrue(errorCount(comparison) > 0,
                "an extra submission must still error");
    }

    @Test
    void wrongOwnerAttributionStillFails() {
        Map<Integer, TraceEvent.DynamicArtTransferState> normalized =
                normalize(spilledFixture());
        FrameComparison comparison = new DynamicArtSpecialStageComparator().compare(
                normalized.get(PASS_ROW),
                new DynamicArtDiagnosticsSnapshot(PASS_ROW, List.of(
                        actualSubmitted(10, 5, "ss-sonic", PASS_ROW, 0),
                        actualSubmitted(11, 6, "ss-tails-tails", PASS_ROW, 1)),
                        List.of(5L, 6L)));
        assertTrue(errorCount(comparison) > 0,
                "a wrong-owner submission must still error");
    }

    @Test
    void completionEdgesAreNeverMoved() {
        DynamicArtTransfer.SegmentEdge completed = new DynamicArtTransfer.SegmentEdge(
                12, 5, "completed", "ss-sonic", "segment",
                1, LAG_ROW, 0, SPILL_ROW, false,
                0x14AC,
                List.of(new DynamicArtTransfer.Request(-1, -1, 0xFF0000, 0x5CA0, 0x80)));
        Map<Integer, TraceEvent.DynamicArtTransferState> normalized = normalize(Map.of(
                SPILL_ROW, new TraceEvent.DynamicArtTransferState(
                        SPILL_ROW, List.of(completed), List.of())));
        assertEquals(1, normalized.get(SPILL_ROW).edges().size(),
                "completed edges stay on their recorded row");
        assertEquals(SPILL_ROW,
                normalized.get(SPILL_ROW).edges().get(0).publicationFrame());
    }
}
