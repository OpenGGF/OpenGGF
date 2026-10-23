package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument;

import java.util.List;
import java.util.Objects;

/** Internal authoring result; only a successful, fingerprint-matching candidate may be applied. */
public record StageplayRouteResult(
        Status status,
        String message,
        String fingerprint,
        StageplayRouteRequest request,
        StageplayDocument candidate,
        List<StageplayDocument.PuppetCommand> commands,
        List<StageplayDocument.ObjectCommand> objectCommands,
        List<StageplayRoutePoint> path,
        int expandedNodes,
        int simulatedTicks,
        boolean nativeVerified) {
    public enum Status {
        VERIFIED,
        VISUAL_TERRAIN,
        UNSUPPORTED,
        SEARCH_EXHAUSTED,
        BUDGET_EXCEEDED,
        CANCELLED,
        VERIFICATION_FAILED,
        STALE_INPUT
    }

    public StageplayRouteResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(message);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(request);
        commands = List.copyOf(commands);
        objectCommands = List.copyOf(objectCommands);
        path = List.copyOf(path);
        if (expandedNodes < 0 || simulatedTicks < 0)
            throw new IllegalArgumentException("Negative route work count");
        boolean success = status == Status.VERIFIED || status == Status.VISUAL_TERRAIN;
        if (success != (candidate != null) || nativeVerified != (status == Status.VERIFIED))
            throw new IllegalArgumentException(
                    "Route candidate and verification status do not agree");
    }

    public boolean success() {
        return candidate != null;
    }

    public static StageplayRouteResult failure(
            Status status,
            String message,
            String fingerprint,
            StageplayRouteRequest request,
            int nodes,
            int ticks) {
        return failure(status, message, fingerprint, request, nodes, ticks, List.of());
    }

    /** Observed partial movement only; a failure never carries an applicable patch. */
    public static StageplayRouteResult failure(
            Status status, String message, String fingerprint, StageplayRouteRequest request,
            int nodes, int ticks, List<StageplayRoutePoint> path) {
        return new StageplayRouteResult(
                status,
                message,
                fingerprint,
                request,
                null,
                List.of(),
                List.of(),
                path,
                nodes,
                ticks,
                false);
    }
}
