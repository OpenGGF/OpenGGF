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
        validateAdmissionPolicies(admissionPolicies, recordedAdmissionActive);
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

    static void validateAdmissionPolicies(
            Map<HardwareWorkKind, HardwareReadinessAdmissionPolicy> admissionPolicies,
            boolean recordedAdmissionActive) {
        Objects.requireNonNull(admissionPolicies, "admissionPolicies");
        boolean anyRecorded = false;
        for (HardwareWorkKind kind : HardwareWorkKind.values()) {
            HardwareReadinessAdmissionPolicy policy = admissionPolicies.get(kind);
            if (policy == null) {
                throw new IllegalArgumentException(
                        "hardware timing snapshot policy is missing kind " + kind);
            }
            anyRecorded |= policy == HardwareReadinessAdmissionPolicy.RECORDED;
        }
        if (recordedAdmissionActive && !anyRecorded) {
            throw new IllegalArgumentException(
                    "active recorded hardware timing snapshot cannot leave every kind live");
        }
    }
}
