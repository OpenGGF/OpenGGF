package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.timing.HardwareWorkKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable indexed timing input compiled from a fixture's dedicated stream. */
public final class HardwareTimingSchedule {

    private static final HardwareTimingSchedule EMPTY = new HardwareTimingSchedule(1, List.of());

    private final int schema;
    private final Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> admissionPolicies;
    private final List<HardwareCompletionEdge> edges;
    private final Map<FrameBoundary, List<HardwareCompletionEdge>> edgesByFrameAndBoundary;

    public HardwareTimingSchedule(List<HardwareCompletionEdge> edges) {
        this(1, edges);
    }

    public HardwareTimingSchedule(int schema, List<HardwareCompletionEdge> edges) {
        if (schema != 1 && schema != 2) {
            throw new IllegalArgumentException("unsupported hardware timing schema: " + schema);
        }
        this.schema = schema;
        this.admissionPolicies = admissionPoliciesFor(schema);
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

    public int schema() {
        return schema;
    }

    public Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> admissionPolicies() {
        return admissionPolicies;
    }

    public List<HardwareCompletionEdge> edgesAt(int rawFrame, HardwareServiceBoundary boundary) {
        return edgesByFrameAndBoundary.getOrDefault(
                new FrameBoundary(rawFrame, Objects.requireNonNull(boundary, "boundary")), List.of());
    }

    private record FrameBoundary(int rawFrame, HardwareServiceBoundary boundary) {
    }

    private static Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy>
            admissionPoliciesFor(int schema) {
        EnumMap<HardwareWorkKind, HardwareReadinessAdmissionPolicy> policies =
                new EnumMap<>(HardwareWorkKind.class);
        policies.put(HardwareWorkKind.KOS_MODULE_QUEUE,
                HardwareReadinessAdmissionPolicy.RECORDED);
        policies.put(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                schema == 2
                        ? HardwareReadinessAdmissionPolicy.RECORDED
                        : HardwareReadinessAdmissionPolicy.LIVE);
        return Map.copyOf(policies);
    }
}
