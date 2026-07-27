package com.openggf.game.timing;

import java.util.List;
import java.util.Map;

/**
 * Narrow capability that may admit final readiness for already-submitted,
 * already-prepared production work.
 */
public interface RecordedCompletionAuthority {
    /**
     * Establishes the hardware-relative identity base for kinds whose
     * production ledger has not yet submitted work.
     *
     * <p>This is a one-time structural-session bootstrap for standalone
     * segments. It cannot renumber an existing production submission.
     */
    void initializeOrdinalBases(Map<HardwareWorkKind, Long> firstOrdinals);

    void admitRecordedCompletion(
            HardwareServiceBoundary boundary,
            HardwareWorkKind kind,
            long ordinal,
            String submissionFingerprint);

    List<PendingRecordedSubmission> pendingSubmissions();

    void endRecordedAdmission();
}
