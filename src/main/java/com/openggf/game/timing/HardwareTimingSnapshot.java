package com.openggf.game.timing;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable rewind state for the production timing FIFO and admission ledger. */
public record HardwareTimingSnapshot(
        Map<HardwareWorkKind, Long> nextOrdinals,
        List<HardwareTimingJob.Snapshot> jobs,
        Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> admissionPolicies,
        boolean recordedAdmissionActive,
        boolean hasSubmitted,
        HardwareServiceBoundary lastServicedBoundary) {

    public HardwareTimingSnapshot {
        Objects.requireNonNull(nextOrdinals, "nextOrdinals");
        Objects.requireNonNull(jobs, "jobs");
        Objects.requireNonNull(admissionPolicies, "admissionPolicies");
        nextOrdinals = Map.copyOf(nextOrdinals);
        admissionPolicies = Map.copyOf(admissionPolicies);
        jobs = List.copyOf(jobs);
    }

    /** Compatibility summary for callers that only distinguish active replay from live mode. */
    public HardwareReadinessAdmissionPolicy admissionPolicy() {
        return recordedAdmissionActive
                ? HardwareReadinessAdmissionPolicy.RECORDED
                : HardwareReadinessAdmissionPolicy.LIVE;
    }
}
