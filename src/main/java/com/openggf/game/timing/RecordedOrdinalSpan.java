package com.openggf.game.timing;

import java.util.List;

/**
 * A contiguous run of recorded hardware-work ordinals between represented segments.
 *
 * <p>The optional fingerprints permit comparison with work production has already
 * claimed. They cannot select production identities, carry payload or readiness,
 * or create or release a hardware job.
 */
public record RecordedOrdinalSpan(
        long firstOrdinal, long lastOrdinal, List<String> submissionFingerprints) {

    /** Legacy spans can only cross work that production never submitted. */
    public RecordedOrdinalSpan(long firstOrdinal, long lastOrdinal) {
        this(firstOrdinal, lastOrdinal, List.of());
    }

    public RecordedOrdinalSpan {
        if (firstOrdinal < 0) {
            throw new IllegalArgumentException(
                    "recorded ordinal span must be non-negative: " + firstOrdinal);
        }
        if (lastOrdinal < firstOrdinal) {
            throw new IllegalArgumentException(
                    "recorded ordinal span must not run backward: "
                            + firstOrdinal + ".." + lastOrdinal);
        }
        submissionFingerprints = List.copyOf(submissionFingerprints);
        if (!submissionFingerprints.isEmpty()
                && lastOrdinal - firstOrdinal != submissionFingerprints.size() - 1L) {
            throw new IllegalArgumentException(
                    "recorded span fingerprints must cover every ordinal");
        }
    }

    /** The ordinal production must allocate next once the span is crossed. */
    public long nextOrdinal() {
        return Math.addExact(lastOrdinal, 1L);
    }
}
