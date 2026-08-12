package com.openggf.trace.replay.runs;

import com.openggf.game.resources.DynamicArtGapTransition;
import com.openggf.game.resources.DynamicArtDiagnosticsProvider;
import com.openggf.game.resources.DynamicArtGapDiagnosticsSnapshot;
import com.openggf.trace.DynamicArtTransfer;
import com.openggf.trace.FrameComparison;
import com.openggf.trace.TraceRunManifest;

import java.util.List;
import java.util.Objects;

/**
 * Read-only adapter that turns production lifecycle evidence into the shared
 * complete-run gap comparison. It observes work submitted by the engine and
 * never creates, completes, or releases a transfer.
 */
public final class TraceRunDynamicArtGapJournal {
    private final TraceRunManifest manifest;
    private final DynamicArtDiagnosticsProvider diagnostics;
    private long structuralOrdinal;
    private long sourceClosedOrdinal;
    private long gapOpenedOrdinal;
    private int sourceSegmentIndex = -1;
    private int transitionCountAtGapStart;
    private int gapStartMovieLogicalFrame;
    private List<DynamicArtTransfer.Descriptor> openingLedger = List.of();
    /**
     * Lifecycle state observed the instant the source segment closed, before
     * the lifecycle entered the gap.
     *
     * <p>Closing a comparison segment flushes work the segment submitted whose
     * completion falls after the segment's last compared row, and the close
     * itself appends those edges to the gap ledger. A snapshot taken after the
     * close starts counting past them, so each one is dropped from the gap's
     * compared slice and its transfer is already gone from the opening ledger
     * that resolves an edge's submission origin. A gap begins where its source
     * ended, so the opening state is taken there.
     */
    private DynamicArtGapDiagnosticsSnapshot sourceClosedSnapshot;

    public TraceRunDynamicArtGapJournal(
            TraceRunManifest manifest,
            DynamicArtDiagnosticsProvider diagnostics) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public void sourceClosed(int segmentIndex) {
        if (sourceSegmentIndex >= 0) {
            if (sourceSegmentIndex != segmentIndex) {
                throw new IllegalStateException(
                        "dynamic-art close changed source segment");
            }
            return;
        }
        sourceSegmentIndex = segmentIndex;
        sourceClosedOrdinal = ++structuralOrdinal;
        sourceClosedSnapshot = diagnostics.gapOpeningSnapshot();
    }

    public void gapOpened(int segmentIndex) {
        if (sourceSegmentIndex != segmentIndex || sourceClosedOrdinal <= 0) {
            throw new IllegalStateException(
                    "dynamic-art gap opened before its source close");
        }
        if (gapOpenedOrdinal > sourceClosedOrdinal) {
            return;
        }
        DynamicArtGapDiagnosticsSnapshot state = sourceClosedSnapshot != null
                ? sourceClosedSnapshot : diagnostics.gapSnapshot();
        gapOpenedOrdinal = ++structuralOrdinal;
        gapStartMovieLogicalFrame = state.movieLogicalFrame();
        transitionCountAtGapStart = state.transitions().size();
        openingLedger = state.ledger().stream()
                .map(TraceRunDynamicArtGapJournal::toTraceDescriptor)
                .toList();
    }

    public FrameComparison destinationOpened(int destinationSegmentIndex) {
        if (sourceSegmentIndex < 0
                || destinationSegmentIndex != sourceSegmentIndex + 1) {
            throw new IllegalStateException(
                    "dynamic-art destination opened without its adjacent gap");
        }
        List<DynamicArtGapTransition> transitions =
                diagnostics.gapSnapshot().transitions();
        List<DynamicArtGapTransition> added =
                transitions.size() >= transitionCountAtGapStart
                        ? transitions.subList(transitionCountAtGapStart,
                                transitions.size())
                        : List.of();
        TraceRunManifest.Segment source = manifest.segments().get(sourceSegmentIndex);
        TraceRunManifest.Segment destination =
                manifest.segments().get(destinationSegmentIndex);
        FrameComparison comparison = TraceRunDynamicArtGapComparator.compare(
                gapStartMovieLogicalFrame, manifest, sourceSegmentIndex,
                new TraceRunDynamicArtGapComparator.RuntimeGap(
                        source.dir(), destination.dir(),
                        new TraceRunDynamicArtGapComparator.StructuralOrder(
                                sourceClosedOrdinal, gapOpenedOrdinal,
                                ++structuralOrdinal),
                        openingLedger, added));
        sourceSegmentIndex = -1;
        sourceClosedOrdinal = 0;
        gapOpenedOrdinal = 0;
        openingLedger = List.of();
        sourceClosedSnapshot = null;
        return comparison;
    }

    /** Closes and compares the final segment-to-movie-end gap. */
    public FrameComparison terminalTailClosed(int movieFrameCount) {
        if (sourceSegmentIndex < 0
                || sourceSegmentIndex != manifest.segments().size() - 1
                || gapOpenedOrdinal <= sourceClosedOrdinal) {
            throw new IllegalStateException(
                    "dynamic-art terminal tail closed without its final source gap");
        }
        List<DynamicArtGapTransition> transitions =
                diagnostics.gapSnapshot().transitions();
        List<DynamicArtGapTransition> added =
                transitions.size() >= transitionCountAtGapStart
                        ? transitions.subList(transitionCountAtGapStart,
                                transitions.size())
                        : List.of();
        TraceRunManifest.Segment source =
                manifest.segments().get(sourceSegmentIndex);
        FrameComparison comparison =
                TraceRunDynamicArtGapComparator.compareTerminalTail(
                        gapStartMovieLogicalFrame, manifest, sourceSegmentIndex,
                        movieFrameCount,
                        new TraceRunDynamicArtGapComparator.RuntimeTerminalTail(
                                source.dir(), sourceClosedOrdinal,
                                gapOpenedOrdinal, ++structuralOrdinal,
                                openingLedger, added));
        sourceSegmentIndex = -1;
        sourceClosedOrdinal = 0;
        gapOpenedOrdinal = 0;
        openingLedger = List.of();
        sourceClosedSnapshot = null;
        return comparison;
    }

    private static DynamicArtTransfer.Descriptor toTraceDescriptor(
            DynamicArtGapDiagnosticsSnapshot.Descriptor descriptor) {
        return new DynamicArtTransfer.Descriptor(
                descriptor.transferId(), descriptor.owner(),
                descriptor.mappingFrame(), descriptor.submissionOrigin(),
                descriptor.requests().stream()
                        .map(request -> new DynamicArtTransfer.Request(
                                request.romSourceAddress(),
                                request.sourceTileIndex(),
                                request.ramSourceAddress(),
                                request.vramDestination(),
                                request.byteLength()))
                        .toList(), null);
    }
}
