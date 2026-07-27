package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable indexed timing input compiled from a fixture's dedicated stream. */
public final class HardwareTimingSchedule {

    private static final HardwareTimingSchedule EMPTY = new HardwareTimingSchedule(List.of());

    private final List<HardwareCompletionEdge> edges;
    private final Map<FrameBoundary, List<HardwareCompletionEdge>> edgesByFrameAndBoundary;

    public HardwareTimingSchedule(List<HardwareCompletionEdge> edges) {
        this.edges = List.copyOf(edges);
        Map<FrameBoundary, List<HardwareCompletionEdge>> indexed = new HashMap<>();
        for (HardwareCompletionEdge edge : this.edges) {
            indexed.computeIfAbsent(new FrameBoundary(edge.rawFrame(), edge.boundary()), ignored -> new ArrayList<>())
                    .add(edge);
        }
        Map<FrameBoundary, List<HardwareCompletionEdge>> immutable = new HashMap<>();
        indexed.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        this.edgesByFrameAndBoundary = Map.copyOf(immutable);
    }

    public static HardwareTimingSchedule empty() {
        return EMPTY;
    }

    public List<HardwareCompletionEdge> edges() {
        return edges;
    }

    public List<HardwareCompletionEdge> edgesAt(int rawFrame, HardwareServiceBoundary boundary) {
        return edgesByFrameAndBoundary.getOrDefault(
                new FrameBoundary(rawFrame, Objects.requireNonNull(boundary, "boundary")), List.of());
    }

    private record FrameBoundary(int rawFrame, HardwareServiceBoundary boundary) {
    }
}
