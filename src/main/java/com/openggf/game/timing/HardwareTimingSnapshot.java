package com.openggf.game.timing;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable rewind state for the production timing FIFO and admission ledger. */
public record HardwareTimingSnapshot(
        Map<HardwareWorkKind, Long> nextOrdinals,
        List<HardwareTimingJob.Snapshot> jobs,
        HardwareReadinessAdmissionPolicy admissionPolicy,
        boolean hasSubmitted,
        HardwareServiceBoundary lastServicedBoundary) {

    public HardwareTimingSnapshot {
        Objects.requireNonNull(nextOrdinals, "nextOrdinals");
        Objects.requireNonNull(jobs, "jobs");
        Objects.requireNonNull(admissionPolicy, "admissionPolicy");
        nextOrdinals = Map.copyOf(nextOrdinals);
        jobs = List.copyOf(jobs);
    }
}
