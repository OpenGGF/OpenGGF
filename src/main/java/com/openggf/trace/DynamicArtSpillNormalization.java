package com.openggf.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Rebinds recorder submission edges that spilled past a frame boundary back to
 * the object pass that produced them.
 *
 * <p>One ROM {@code RunObjects} pass submits every changed player DPLC in one
 * burst, but the recorder timestamps each submission with the wall-clock frame
 * it actually crossed: when the pass overruns the frame (a lag row), the later
 * objects' submissions carry the lag row as {@code logical_frame} and surface
 * on the following observation as {@code publication_frame} (stage-1 fixture
 * row 181: {@code ss-sonic} submitted logical 181, while {@code ss-tails} /
 * {@code ss-tails-tails} from the same pass carry logical 182 — the lag row —
 * published 183). Which objects spill depends on sub-frame 68K execution time
 * inside the pass; the ROM has no semantic notion of the split, and the engine
 * publishes each pass atomically. Like the recorder's power-on-epoch delivery
 * identities (see {@link DynamicArtIdEpoch}), the spilled edges' frame stamps
 * are observation artifacts, not ROM state, so they are compared per pass
 * rather than per publication row.
 *
 * <p>Everything else stays absolute: per-pass edge cardinality, in-pass
 * ordering (edge ordinals), owner, phase, mapping frame, and every request
 * field. A genuinely missing, duplicated, or wrongly-attributed submission
 * still fails. Completion edges are never moved: their retirement rows are
 * V-blank facts both sides model.
 */
public final class DynamicArtSpillNormalization {

    private DynamicArtSpillNormalization() {
    }

    /**
     * Returns per-row expected transfer states with spilled submission edges
     * moved to their pass row: the latest non-lag row at or before the edge's
     * recorded {@code logical_frame}. The moved edge's {@code logical_frame}
     * and {@code publication_frame} are rewritten to that pass row; unmoved
     * edges are byte-identical to the recording.
     *
     * @param states   recorded per-row transfer states, keyed by row
     * @param rowCount total observation rows
     * @param lagRow   whether a row is a recorded hardware lag row
     */
    public static Map<Integer, TraceEvent.DynamicArtTransferState> rebindSubmissionSpills(
            Map<Integer, TraceEvent.DynamicArtTransferState> states,
            int rowCount,
            IntPredicate lagRow) {
        return rebindSubmissionSpills(states, rowCount, rowCount, lagRow);
    }

    /**
     * As {@link #rebindSubmissionSpills(Map, int, IntPredicate)}, but only
     * rows before {@code rebindEndExclusive} are rebound. Once the recorded
     * {@code run_objects_end} pass bindings pace the replay (from
     * {@code SpecialStage_Started}), each pass already executes against its
     * bound observation and spilled rows need no normalization.
     */
    public static Map<Integer, TraceEvent.DynamicArtTransferState> rebindSubmissionSpills(
            Map<Integer, TraceEvent.DynamicArtTransferState> states,
            int rowCount,
            int rebindEndExclusive,
            IntPredicate lagRow) {
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(lagRow, "lagRow");

        Map<Integer, List<DynamicArtTransfer.SegmentEdge>> edgesByRow = new LinkedHashMap<>();
        java.util.Set<Integer> modifiedRows = new java.util.HashSet<>();
        // A moved submission is outstanding from its pass row up to (not
        // including) the row the recorder originally published it on.
        Map<Integer, List<Long>> earlyOutstandingByRow = new LinkedHashMap<>();
        for (Map.Entry<Integer, TraceEvent.DynamicArtTransferState> entry : states.entrySet()) {
            int row = entry.getKey();
            for (DynamicArtTransfer.SegmentEdge edge : entry.getValue().edges()) {
                int target = row;
                if (row < rebindEndExclusive
                        && "submitted".equals(edge.phase())
                        && edge.logicalFrame() != edge.publicationFrame()) {
                    target = passRowFor(edge.logicalFrame(), lagRow);
                    for (int r = target; r < row; r++) {
                        earlyOutstandingByRow
                                .computeIfAbsent(r, x -> new ArrayList<>())
                                .add(edge.transferId());
                    }
                    edge = withPassRow(edge, target);
                    modifiedRows.add(target);
                    modifiedRows.add(row);
                }
                edgesByRow.computeIfAbsent(target, r -> new ArrayList<>()).add(edge);
            }
        }

        Map<Integer, TraceEvent.DynamicArtTransferState> normalized = new LinkedHashMap<>();
        for (int row = 0; row < rowCount; row++) {
            TraceEvent.DynamicArtTransferState original = states.get(row);
            List<DynamicArtTransfer.SegmentEdge> edges = edgesByRow.get(row);
            if (original == null && edges == null) {
                continue;
            }
            List<DynamicArtTransfer.SegmentEdge> merged =
                    edges == null ? List.of() : new ArrayList<>(edges);
            if (edges != null) {
                // Recorder ordinals are allocated in submission order; sorting
                // restores the in-pass order for edges merged from spill rows.
                ArrayList<DynamicArtTransfer.SegmentEdge> sorted =
                        (ArrayList<DynamicArtTransfer.SegmentEdge>) merged;
                sorted.sort(Comparator.comparingLong(
                        DynamicArtTransfer.SegmentEdge::edgeOrdinal));
                if (modifiedRows.contains(row)) {
                    // logical_edge_index is the recorder's per-row position;
                    // rows whose membership changed get positions recomputed.
                    for (int i = 0; i < sorted.size(); i++) {
                        DynamicArtTransfer.SegmentEdge e = sorted.get(i);
                        if (e.logicalEdgeIndex() != i) {
                            sorted.set(i, withLogicalEdgeIndex(e, i));
                        }
                    }
                }
            }
            List<Long> outstanding = original != null
                    ? new ArrayList<>(original.outstandingTransferIds())
                    : new ArrayList<>();
            for (long early : earlyOutstandingByRow.getOrDefault(row, List.of())) {
                if (!outstanding.contains(early)) {
                    outstanding.add(early);
                }
            }
            normalized.put(row, new TraceEvent.DynamicArtTransferState(
                    row, merged, outstanding));
        }
        return normalized;
    }

    private static int passRowFor(int logicalFrame, IntPredicate lagRow) {
        int row = logicalFrame;
        while (row > 0 && lagRow.test(row)) {
            row--;
        }
        return row;
    }

    private static DynamicArtTransfer.SegmentEdge withLogicalEdgeIndex(
            DynamicArtTransfer.SegmentEdge edge, int index) {
        return new DynamicArtTransfer.SegmentEdge(
                edge.edgeOrdinal(),
                edge.transferId(),
                edge.phase(),
                edge.owner(),
                edge.submissionOrigin(),
                edge.mappingFrame(),
                edge.logicalFrame(),
                index,
                edge.publicationFrame(),
                edge.terminalForwarded(),
                edge.romCallbackPc(),
                edge.requests());
    }

    private static DynamicArtTransfer.SegmentEdge withPassRow(
            DynamicArtTransfer.SegmentEdge edge, int passRow) {
        return new DynamicArtTransfer.SegmentEdge(
                edge.edgeOrdinal(),
                edge.transferId(),
                edge.phase(),
                edge.owner(),
                edge.submissionOrigin(),
                edge.mappingFrame(),
                passRow,
                edge.logicalEdgeIndex(),
                passRow,
                edge.terminalForwarded(),
                edge.romCallbackPc(),
                edge.requests());
    }
}
