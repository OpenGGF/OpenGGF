package com.openggf.trace;

import com.openggf.game.resources.QueueDiagnosticSnapshot;
import com.openggf.game.timing.HardwareTimingJob;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.trace.timing.HardwareCompletionEdge;

import java.util.List;
import java.util.Objects;

/** Comparison-only projection for native queue heartbeats sampled between loop-tail calls. */
public record LoadQueueComparisonProjection(
        List<TraceEvent.LoadQueueState> expected,
        List<QueueDiagnosticSnapshot> actual) {

    private static final String DIRECT_KIND = "s3k_kos_direct";

    public LoadQueueComparisonProjection {
        expected = List.copyOf(Objects.requireNonNull(expected, "expected"));
        actual = List.copyOf(Objects.requireNonNull(actual, "actual"));
    }

    public static LoadQueueComparisonProjection project(
            TraceData trace,
            int frame,
            List<TraceEvent.LoadQueueState> expected,
            List<QueueDiagnosticSnapshot> actual,
            HardwareTimingSnapshot timing) {
        LoadQueueComparisonProjection unchanged =
                new LoadQueueComparisonProjection(expected, actual);
        HardwareCompletionEdge edge =
                trace.unobservedDirectChildForComparisonFrame(frame);
        if (edge == null || timing == null) {
            return unchanged;
        }
        QueueDiagnosticSnapshot direct = unchanged.actual().stream()
                .filter(state -> DIRECT_KIND.equals(state.kind().wireName()))
                .findFirst()
                .orElse(null);
        if (direct == null || !direct.busy() || direct.prepared()) {
            return unchanged;
        }
        HardwareTimingJob.Snapshot job = matchingDirectJob(timing, direct, edge);
        if (job == null) {
            return unchanged;
        }
        return new LoadQueueComparisonProjection(
                unchanged.expected().stream()
                        .filter(state -> !DIRECT_KIND.equals(state.kind()))
                        .toList(),
                unchanged.actual().stream()
                        .filter(state -> !DIRECT_KIND.equals(state.kind().wireName()))
                        .toList());
    }

    private static HardwareTimingJob.Snapshot matchingDirectJob(
            HardwareTimingSnapshot timing,
            QueueDiagnosticSnapshot direct,
            HardwareCompletionEdge edge) {
        HardwareWorkHandle expectedHandle = new HardwareWorkHandle(
                edge.kind(), edge.ordinal(), edge.submissionFingerprint());
        List<HardwareTimingJob.Snapshot> matches = timing.jobs().stream()
                .filter(job -> job.kind() == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE)
                .filter(job -> !job.claimed() && !job.physicallyRetired())
                .filter(job -> job.romSourceAddress() == direct.activeSource())
                .filter(job -> job.destinationAddress() == direct.activeDestination())
                .filter(job -> job.handle().equals(expectedHandle))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
}
