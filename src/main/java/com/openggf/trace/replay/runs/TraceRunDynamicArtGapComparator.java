package com.openggf.trace.replay.runs;

import com.openggf.game.resources.DynamicArtDiagnosticsSnapshot;
import com.openggf.game.resources.DynamicArtGapTransition;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.FieldComparison;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.Severity;
import com.openggf.trace.TraceRunManifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Comparison-only validator for production dynamic-art activity between run
 * segments. It reconstructs the runtime ledger from immutable observations;
 * recorded gaps never submit, complete, or release production work.
 */
public final class TraceRunDynamicArtGapComparator {

    private static final String PREFIX = "run_gap.";

    private TraceRunDynamicArtGapComparator() {
    }

    /** Monotonic lifecycle-event ordinals supplied by the run adapter. */
    public record StructuralOrder(
            long sourceClosed,
            long gapOpened,
            long destinationOpened) {
    }

    /** Read-only runtime evidence captured across one adjacent segment gap. */
    public record RuntimeGap(
            String sourceSegmentDir,
            String destinationSegmentDir,
            StructuralOrder structuralOrder,
            List<DynamicArtTransfer.Descriptor> openingLedger,
            List<DynamicArtGapTransition> transitions) {
        public RuntimeGap {
            sourceSegmentDir = Objects.requireNonNull(
                    sourceSegmentDir, "sourceSegmentDir");
            destinationSegmentDir = Objects.requireNonNull(
                    destinationSegmentDir, "destinationSegmentDir");
            structuralOrder = Objects.requireNonNull(
                    structuralOrder, "structuralOrder");
            openingLedger = List.copyOf(Objects.requireNonNull(
                    openingLedger, "openingLedger"));
            transitions = List.copyOf(Objects.requireNonNull(
                    transitions, "transitions"));
        }
    }

    /**
     * Compares one source-to-destination gap. Schema 1 deliberately emits only
     * structural fields because that manifest schema has no gap payload.
     */
    public static FrameComparison compare(
            int frame,
            TraceRunManifest manifest,
            int sourceSegmentIndex,
            RuntimeGap actual) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(actual, "actual");
        if (sourceSegmentIndex < 0
                || sourceSegmentIndex + 1 >= manifest.segments().size()) {
            throw new IllegalArgumentException(
                    "sourceSegmentIndex has no adjacent destination: "
                            + sourceSegmentIndex);
        }

        TraceRunManifest.Segment source =
                manifest.segments().get(sourceSegmentIndex);
        TraceRunManifest.Segment destination =
                manifest.segments().get(sourceSegmentIndex + 1);
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        compareStructure(fields, source, destination, actual);
        if (manifest.runSchema() == 1) {
            return new FrameComparison(frame, fields);
        }
        if (manifest.runSchema() != 2) {
            throw new IllegalArgumentException(
                    "unsupported run schema " + manifest.runSchema());
        }

        List<DynamicArtTransfer.GapTransition> expectedTransitions =
                gapSlice(manifest, sourceSegmentIndex);
        compareRuntimeTransitions(fields, expectedTransitions, actual,
                destination);
        return new FrameComparison(frame, fields);
    }

    private static void compareStructure(
            Map<String, FieldComparison> fields,
            TraceRunManifest.Segment source,
            TraceRunManifest.Segment destination,
            RuntimeGap actual) {
        put(fields, PREFIX + "structure.source_segment",
                source.dir(), actual.sourceSegmentDir());
        put(fields, PREFIX + "structure.destination_segment",
                destination.dir(), actual.destinationSegmentDir());
        StructuralOrder order = actual.structuralOrder();
        put(fields, PREFIX + "structure.source_closed_before_gap",
                true, order.sourceClosed() < order.gapOpened());
        put(fields, PREFIX + "structure.gap_before_destination_open",
                true, order.gapOpened() < order.destinationOpened());
    }

    private static List<DynamicArtTransfer.GapTransition> gapSlice(
            TraceRunManifest manifest, int sourceSegmentIndex) {
        TraceRunManifest.Segment source =
                manifest.segments().get(sourceSegmentIndex);
        TraceRunManifest.Segment destination =
                manifest.segments().get(sourceSegmentIndex + 1);
        int sourceEnd = Math.addExact(
                source.bk2FrameOffset(), source.traceFrameCount());
        return manifest.dynamicArtGapTransitions().stream()
                .filter(transition -> transition.dynamicArtGapEdge()
                        .movieLogicalFrame() >= sourceEnd)
                .filter(transition -> transition.dynamicArtGapEdge()
                        .movieLogicalFrame() < destination.bk2FrameOffset())
                .toList();
    }

    private static void compareRuntimeTransitions(
            Map<String, FieldComparison> fields,
            List<DynamicArtTransfer.GapTransition> expectedTransitions,
            RuntimeGap actual,
            TraceRunManifest.Segment destination) {
        put(fields, PREFIX + "edge_count", expectedTransitions.size(),
                actual.transitions().size());
        List<DynamicArtTransfer.Descriptor> runtimeLedger =
                new ArrayList<>(actual.openingLedger());
        int edgeCount = Math.max(
                expectedTransitions.size(), actual.transitions().size());
        for (int index = 0; index < edgeCount; index++) {
            DynamicArtTransfer.GapTransition expected =
                    index < expectedTransitions.size()
                            ? expectedTransitions.get(index) : null;
            DynamicArtGapTransition observed =
                    index < actual.transitions().size()
                            ? actual.transitions().get(index) : null;
            String edgePrefix = PREFIX + "edge[" + index + "].";
            put(fields, edgePrefix + "present",
                    expected != null, observed != null);
            if (expected == null || observed == null) {
                if (observed != null) {
                    applyObservedEdge(runtimeLedger, observed);
                }
                continue;
            }
            compareEdge(fields, edgePrefix, expected, observed, runtimeLedger);
            applyObservedEdge(runtimeLedger, observed);
            compareAfterLedger(fields, edgePrefix, expected, observed,
                    runtimeLedger);
        }

        List<String> expectedFingerprints = fingerprints(
                destination.dynamicArtInitialLedgerDescriptors());
        List<String> actualFingerprints = fingerprints(runtimeLedger);
        put(fields, PREFIX + "destination.initial_ledger_fingerprints",
                expectedFingerprints, actualFingerprints);
        put(fields, PREFIX + "destination.initial_ledger_fingerprint",
                destination.dynamicArtInitialLedgerFingerprint(),
                DynamicArtTransfer.ledgerHash(runtimeLedger));
    }

    private static void compareEdge(
            Map<String, FieldComparison> fields,
            String prefix,
            DynamicArtTransfer.GapTransition expected,
            DynamicArtGapTransition observed,
            List<DynamicArtTransfer.Descriptor> runtimeLedger) {
        DynamicArtTransfer.GapEdge expectedEdge = expected.dynamicArtGapEdge();
        DynamicArtGapTransition.GapEdge observedEdge = observed.edge();
        String observedOrigin = observedOrigin(observedEdge, runtimeLedger);

        put(fields, prefix + "before_outstanding_transfer_ids",
                ids(runtimeLedger), observed.beforeOutstandingTransferIds());
        put(fields, prefix + "before_ledger_fingerprint",
                expected.beforeLedgerHash(),
                DynamicArtTransfer.ledgerHash(runtimeLedger));
        put(fields, prefix + "edge_ordinal",
                expectedEdge.edgeOrdinal(), observedEdge.edgeOrdinal());
        put(fields, prefix + "transfer_id",
                expectedEdge.transferId(), observedEdge.transferId());
        put(fields, prefix + "phase",
                expectedEdge.phase(), observedEdge.phase());
        put(fields, prefix + "owner",
                expectedEdge.owner(), observedEdge.owner());
        put(fields, prefix + "submission_origin",
                expectedEdge.submissionOrigin(), observedOrigin);
        put(fields, prefix + "mapping_frame",
                expectedEdge.mappingFrame(), observedEdge.mappingFrame());
        put(fields, prefix + "movie_logical_frame",
                expectedEdge.movieLogicalFrame(),
                observedEdge.movieLogicalFrame());
        put(fields, prefix + "gap_edge_index",
                expectedEdge.gapEdgeIndex(), observedEdge.gapEdgeIndex());
        put(fields, prefix + "requests", expectedEdge.requests(),
                requests(observedEdge.requests()));

        boolean expectedForwardedCompletion =
                "completed".equals(expectedEdge.phase())
                        && "segment".equals(expectedEdge.submissionOrigin());
        boolean observedForwardedCompletion =
                "completed".equals(observedEdge.phase())
                        && "segment".equals(observedOrigin);
        put(fields, prefix + "forwarded_completion",
                expectedForwardedCompletion, observedForwardedCompletion);
    }

    private static void compareAfterLedger(
            Map<String, FieldComparison> fields,
            String prefix,
            DynamicArtTransfer.GapTransition expected,
            DynamicArtGapTransition observed,
            List<DynamicArtTransfer.Descriptor> runtimeLedger) {
        put(fields, prefix + "after_outstanding_transfer_ids",
                ids(runtimeLedger), observed.afterOutstandingTransferIds());
        put(fields, prefix + "after_ledger_fingerprints",
                fingerprints(expected.afterLedgerDescriptors()),
                fingerprints(runtimeLedger));
    }

    private static String observedOrigin(
            DynamicArtGapTransition.GapEdge edge,
            List<DynamicArtTransfer.Descriptor> runtimeLedger) {
        if ("submitted".equals(edge.phase())) {
            return "run_gap";
        }
        return runtimeLedger.stream()
                .filter(descriptor -> descriptor.transferId()
                        == edge.transferId())
                .map(DynamicArtTransfer.Descriptor::submissionOrigin)
                .findFirst()
                .orElse("missing");
    }

    private static void applyObservedEdge(
            List<DynamicArtTransfer.Descriptor> runtimeLedger,
            DynamicArtGapTransition transition) {
        DynamicArtGapTransition.GapEdge edge = transition.edge();
        if ("submitted".equals(edge.phase())) {
            runtimeLedger.add(new DynamicArtTransfer.Descriptor(
                    edge.transferId(), edge.owner(), edge.mappingFrame(),
                    "run_gap", requests(edge.requests()), null));
            return;
        }
        for (int index = 0; index < runtimeLedger.size(); index++) {
            if (runtimeLedger.get(index).transferId() == edge.transferId()) {
                runtimeLedger.remove(index);
                return;
            }
        }
    }

    private static List<DynamicArtTransfer.Request> requests(
            List<DynamicArtDiagnosticsSnapshot.Request> requests) {
        return requests.stream().map(request -> new DynamicArtTransfer.Request(
                request.romSourceAddress(), request.sourceTileIndex(),
                request.ramSourceAddress(), request.vramDestination(),
                request.byteLength())).toList();
    }

    private static List<Long> ids(
            List<DynamicArtTransfer.Descriptor> descriptors) {
        return descriptors.stream()
                .map(DynamicArtTransfer.Descriptor::transferId).toList();
    }

    private static List<String> fingerprints(
            List<DynamicArtTransfer.Descriptor> descriptors) {
        return descriptors.stream()
                .map(DynamicArtTransfer.Descriptor::fingerprint).toList();
    }

    private static void put(
            Map<String, FieldComparison> fields,
            String name,
            Object expected,
            Object actual) {
        boolean match = Objects.equals(expected, actual);
        fields.put(name, new FieldComparison(
                name, String.valueOf(expected), String.valueOf(actual),
                match ? Severity.MATCH : Severity.ERROR,
                delta(expected, actual)));
    }

    private static int delta(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber
                && actual instanceof Number actualNumber) {
            long value = Math.abs(expectedNumber.longValue()
                    - actualNumber.longValue());
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        }
        return Objects.equals(expected, actual) ? 0 : 1;
    }
}
