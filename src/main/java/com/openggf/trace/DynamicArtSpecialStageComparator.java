package com.openggf.trace;

import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DPLC-only comparison for special-stage trace rows.
 *
 * <p>The comparator accepts one validated expected envelope and one immutable
 * production snapshot. It owns no engine step, lifecycle, submission, or
 * completion surface.
 */
public final class DynamicArtSpecialStageComparator {

    public FrameComparison compare(
            TraceEvent.DynamicArtTransferState expected,
            DynamicArtDiagnosticsSnapshot actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        return new FrameComparison(expected.frame(), comparisonFields(expected, actual));
    }

    static Map<String, FieldComparison> comparisonFields(
            TraceEvent.DynamicArtTransferState expected,
            DynamicArtDiagnosticsSnapshot actual) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        put(fields, "dynamic_art.frame", expected.frame(), actual.frame());
        put(fields, "dynamic_art.edges",
                expected.edges().stream()
                        .map(DynamicArtTransfer.SegmentEdge::edgeOrdinal)
                        .toList(),
                actual.edges().stream()
                        .map(DynamicArtDiagnosticsSnapshot.Edge::edgeOrdinal)
                        .toList());
        put(fields, "dynamic_art.outstanding_transfer_ids",
                expected.outstandingTransferIds(),
                actual.outstandingTransferIds());

        int edgeCount = Math.max(expected.edges().size(), actual.edges().size());
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            DynamicArtTransfer.ComparisonEdge expectedEdge =
                    edgeIndex < expected.edges().size()
                            ? expected.edges().get(edgeIndex).comparisonView()
                            : null;
            DynamicArtDiagnosticsSnapshot.Edge actualEdge =
                    edgeIndex < actual.edges().size()
                            ? actual.edges().get(edgeIndex)
                            : null;
            String prefix = "dynamic_art.edge[" + edgeIndex + "].";
            put(fields, prefix + "present",
                    expectedEdge != null, actualEdge != null);
            if (expectedEdge == null || actualEdge == null) {
                continue;
            }
            put(fields, prefix + "edge_ordinal",
                    expectedEdge.edgeOrdinal(), actualEdge.edgeOrdinal());
            put(fields, prefix + "transfer_id",
                    expectedEdge.transferId(), actualEdge.transferId());
            put(fields, prefix + "phase",
                    expectedEdge.phase(), actualEdge.phase());
            put(fields, prefix + "owner",
                    expectedEdge.owner(), actualEdge.owner());
            put(fields, prefix + "submission_origin",
                    expectedEdge.submissionOrigin(), "segment");
            put(fields, prefix + "mapping_frame",
                    expectedEdge.mappingFrame(), actualEdge.mappingFrame());
            put(fields, prefix + "logical_frame",
                    expectedEdge.logicalFrame(), actualEdge.logicalFrame());
            put(fields, prefix + "logical_edge_index",
                    expectedEdge.logicalEdgeIndex(), actualEdge.logicalEdgeIndex());
            put(fields, prefix + "publication_frame",
                    expectedEdge.publicationFrame(), actualEdge.publicationFrame());
            put(fields, prefix + "terminal_forwarded",
                    expectedEdge.terminalForwarded(), actualEdge.terminalForwarded());
            compareRequests(fields, prefix,
                    expectedEdge.requests(), actualEdge.requests());
        }
        return fields;
    }

    private static void compareRequests(
            Map<String, FieldComparison> fields,
            String edgePrefix,
            List<DynamicArtTransfer.Request> expected,
            List<DynamicArtDiagnosticsSnapshot.Request> actual) {
        put(fields, edgePrefix + "request_count",
                expected.size(), actual.size());
        int requestCount = Math.max(expected.size(), actual.size());
        for (int requestIndex = 0; requestIndex < requestCount; requestIndex++) {
            DynamicArtTransfer.Request expectedRequest =
                    requestIndex < expected.size() ? expected.get(requestIndex) : null;
            DynamicArtDiagnosticsSnapshot.Request actualRequest =
                    requestIndex < actual.size() ? actual.get(requestIndex) : null;
            String prefix = edgePrefix + "request[" + requestIndex + "].";
            put(fields, prefix + "present",
                    expectedRequest != null, actualRequest != null);
            if (expectedRequest == null || actualRequest == null) {
                continue;
            }
            put(fields, prefix + "rom_source_address",
                    expectedRequest.romSourceAddress(),
                    actualRequest.romSourceAddress());
            put(fields, prefix + "source_tile_index",
                    expectedRequest.sourceTileIndex(),
                    actualRequest.sourceTileIndex());
            put(fields, prefix + "ram_source_address",
                    expectedRequest.ramSourceAddress(),
                    actualRequest.ramSourceAddress());
            put(fields, prefix + "vram_destination",
                    expectedRequest.vramDestination(),
                    actualRequest.vramDestination());
            put(fields, prefix + "byte_length",
                    expectedRequest.byteLength(),
                    actualRequest.byteLength());
        }
    }

    private static void put(
            Map<String, FieldComparison> fields,
            String name,
            Object expected,
            Object actual) {
        String expectedText = String.valueOf(expected);
        String actualText = String.valueOf(actual);
        Severity severity = expectedText.equals(actualText)
                ? Severity.MATCH : Severity.ERROR;
        fields.put(name, new FieldComparison(
                name, expectedText, actualText, severity,
                severity == Severity.MATCH ? 0 : 1));
    }
}
