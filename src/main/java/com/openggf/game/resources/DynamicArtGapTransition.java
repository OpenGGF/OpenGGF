package com.openggf.game.resources;

import java.util.List;
import java.util.Objects;

/** Immutable read-only production transition recorded between compared segments. */
public record DynamicArtGapTransition(
        GapEdge edge,
        List<Long> beforeOutstandingTransferIds,
        List<Long> afterOutstandingTransferIds) {

    public DynamicArtGapTransition {
        edge = Objects.requireNonNull(edge, "edge");
        beforeOutstandingTransferIds =
                List.copyOf(beforeOutstandingTransferIds);
        afterOutstandingTransferIds =
                List.copyOf(afterOutstandingTransferIds);
    }

    /** Run-wide gap edge without segment-local cursor fields. */
    public record GapEdge(
            long edgeOrdinal,
            long transferId,
            String phase,
            String owner,
            int mappingFrame,
            int movieLogicalFrame,
            int gapEdgeIndex,
            List<DynamicArtDiagnosticsSnapshot.Request> requests) {
        public GapEdge {
            requests = List.copyOf(requests);
        }
    }
}
