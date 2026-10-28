package com.openggf.game.rewind.snapshot;

import com.openggf.game.RuntimeArtAdmissionOwnerKind;
import java.util.List;

/**
 * Snapshot of a game's PLC/object-art progress and runtime-published level-art consumers.
 *
 * <p>The epoch identifies the zone-load generation. The ordered key list records
 * consumers published dynamically after gameplay-time PLC/KosM work completes,
 * allowing a restore to remove stale consumers or deterministically rebuild them.
 * The optional runtime state records provider-owned queued art work, such as
 * S3K's direct {@code Queue_Kos_Module} requests. Providers whose art is wholly
 * zone-load-time use the compatibility constructor.
 */
public record PlcProgressSnapshot(
        int loadEpoch,
        int runtimeState,
        List<String> publishedLevelArtKeys,
        List<PendingKosModule> pendingKosModules,
        List<Long> pendingKosOrdinals,
        boolean kosSubmissionArmed,
        int titleCardTeardownTicks,
        long runtimeArtAdmissionGeneration,
        long runtimeArtAdmissionNextLeaseId,
        long runtimeArtAdmissionLeaseId,
        long runtimeArtAdmissionBatchFingerprint,
        RuntimeArtAdmissionOwnerKind runtimeArtAdmissionOwnerKind,
        boolean runtimeArtAdmissionBound,
        boolean runtimeArtAdmissionConsumed,
        long titleCardTeardownLeaseId,
        ArtSlotIdentity artSlotIdentity,
        List<ArtSlotRange> artSlotRanges) {

    public PlcProgressSnapshot {
        publishedLevelArtKeys = List.copyOf(publishedLevelArtKeys);
        pendingKosModules = List.copyOf(pendingKosModules);
        pendingKosOrdinals = List.copyOf(pendingKosOrdinals);
        artSlotRanges = List.copyOf(artSlotRanges);
        if (artSlotIdentity == null && !artSlotRanges.isEmpty()) {
            throw new IllegalArgumentException("Art-slot recipes require ROM/world provenance");
        }
        int end = 0;
        for (ArtSlotRange range : artSlotRanges) {
            if (range.destinationTile() < end) {
                throw new IllegalArgumentException("Art-slot image must be ordered and non-overlapping");
            }
            end = range.destinationTile() + range.patternCount();
        }
    }

    /** Compatibility with the original full S3K snapshot constructor. */
    public PlcProgressSnapshot(int loadEpoch, int runtimeState,
            List<String> publishedLevelArtKeys, List<PendingKosModule> pendingKosModules,
            List<Long> pendingKosOrdinals, boolean kosSubmissionArmed, int titleCardTeardownTicks,
            long runtimeArtAdmissionGeneration, long runtimeArtAdmissionNextLeaseId,
            long runtimeArtAdmissionLeaseId, long runtimeArtAdmissionBatchFingerprint,
            RuntimeArtAdmissionOwnerKind runtimeArtAdmissionOwnerKind, boolean runtimeArtAdmissionBound,
            boolean runtimeArtAdmissionConsumed, long titleCardTeardownLeaseId) {
        this(loadEpoch, runtimeState, publishedLevelArtKeys, pendingKosModules, pendingKosOrdinals,
                kosSubmissionArmed, titleCardTeardownTicks, runtimeArtAdmissionGeneration,
                runtimeArtAdmissionNextLeaseId, runtimeArtAdmissionLeaseId, runtimeArtAdmissionBatchFingerprint,
                runtimeArtAdmissionOwnerKind, runtimeArtAdmissionBound, runtimeArtAdmissionConsumed,
                titleCardTeardownLeaseId, null, List.of());
    }

    /** ROM-static object-art image; identity is provenance and never selects gameplay. */
    public PlcProgressSnapshot(int loadEpoch, ArtSlotIdentity identity, List<ArtSlotRange> ranges) {
        this(loadEpoch, 0, List.of(), List.of(), List.of(), false,
                -1, 0, 0, -1, 0, null, false, false, -1, identity, ranges);
    }

    /** Compatibility constructor for providers without dynamic publication. */
    public PlcProgressSnapshot(int loadEpoch) {
        this(loadEpoch, 0, List.of(), List.of(), List.of(), false);
    }

    public PlcProgressSnapshot(int loadEpoch, int runtimeState) {
        this(loadEpoch, runtimeState, List.of(), List.of(), List.of(), false);
    }

    public PlcProgressSnapshot(int loadEpoch, List<String> publishedLevelArtKeys) {
        this(loadEpoch, 0, publishedLevelArtKeys, List.of(), List.of(), false);
    }

    public PlcProgressSnapshot(int loadEpoch, int runtimeState,
            List<String> publishedLevelArtKeys) {
        this(loadEpoch, runtimeState, publishedLevelArtKeys, List.of(), List.of(), false);
    }

    public PlcProgressSnapshot(int loadEpoch, int runtimeState,
            List<PendingKosModule> pendingKosModules, List<Long> pendingKosOrdinals,
            boolean kosSubmissionArmed) {
        this(loadEpoch, runtimeState, List.of(), pendingKosModules, pendingKosOrdinals,
                kosSubmissionArmed);
    }

    public PlcProgressSnapshot(int loadEpoch, int runtimeState,
            List<String> publishedLevelArtKeys,
            List<PendingKosModule> pendingKosModules, List<Long> pendingKosOrdinals,
            boolean kosSubmissionArmed) {
        this(loadEpoch, runtimeState, publishedLevelArtKeys,
                pendingKosModules, pendingKosOrdinals, kosSubmissionArmed,
                -1, 0, 0, -1, 0, null, false, false, -1);
    }

    public record PendingKosModule(int sourceAddress, int destinationTile,
                                   boolean physicalPublication) {
        public PendingKosModule(int sourceAddress, int destinationTile) {
            this(sourceAddress, destinationTile, false);
        }
    }

    public record ArtSlotIdentity(String romSha1, int nativeZone) {
        public ArtSlotIdentity {
            if (romSha1 == null || !romSha1.matches("[0-9a-fA-F]{40}")
                    || nativeZone < -1 || nativeZone > 0xFFFF) {
                throw new IllegalArgumentException("Invalid art-slot ROM/world identity");
            }
            romSha1 = romSha1.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** One final contiguous run from immutable ROM-decoded art, not a write-history handle. */
    public record ArtSlotRange(int destinationTile, int sourceAddress,
                               int sourcePatternOffset, int patternCount) {
        public ArtSlotRange {
            if (destinationTile < 0 || destinationTile >= 0x800
                    || sourceAddress < 0 || sourceAddress > 0xFFFFFE
                    || sourcePatternOffset < 0 || sourcePatternOffset > 0x7FFF
                    || patternCount <= 0 || (long) destinationTile + patternCount > 0x800
                    || (long) sourcePatternOffset + patternCount > 0x7FFF) {
                throw new IllegalArgumentException("Invalid ROM art-slot range");
            }
        }
    }
}
