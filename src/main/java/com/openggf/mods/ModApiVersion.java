package com.openggf.mods;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class ModApiVersion {
    /**
     * Current unpublished compiled-mod API candidate, including the 0.7
     * widescreen presentation, KiS2 movement/touch/checkpoint/menu handoff contracts,
     * deferred SMPS header construction, explicit dynamic solid-contact rewind binding,
     * opt-in whole-game presentation pacing, modal level input ownership, controlled level admission and opaque
     * course checkpoints, semantic native impulses, logical menu pointers, and read-only
     * ROM-backed value scenes, held idle poses, controller-owned rewind presentation values
     * and per-player button prompt labels derived from live bindings. Controlled
     * entry fade admission reports whether it advanced; checkpoint readiness includes
     * the native released title overlay's completion. Bounded scene replay and
     * session-owned actual-PCM recording support creator rewind without giving
     * it developer-history or sound-driver restore ownership. Mixed-ROM startup
     * scenes expose supplied-game art, immutable timestamped physical input and
     * bounded semantic ROM music with consumed-sample playback and section parts.
     * Owner-derived bounded native S2/S3K placement plans retain explicit native
     * rings and add local registered objects and bounded native recovery rings without
     * replacing native level owners.
     */
    public static final SemanticVersion CURRENT = SemanticVersion.parse("0.7.0");
    public static final List<SemanticVersion> SUPPORTED_CONTRACTS = List.of(CURRENT);

    public static boolean supports(VersionRange range) {
        return supports(range, SUPPORTED_CONTRACTS);
    }

    public static String supportedContractsDiagnostic() {
        return SUPPORTED_CONTRACTS.toString();
    }

    static boolean supports(VersionRange range, Collection<SemanticVersion> contracts) {
        Objects.requireNonNull(range, "range");
        return normalizedContracts(contracts).stream().anyMatch(range::contains);
    }

    static String supportedContractsDiagnostic(Collection<SemanticVersion> contracts) {
        return normalizedContracts(contracts).toString();
    }

    private static List<SemanticVersion> normalizedContracts(Collection<SemanticVersion> contracts) {
        ArrayList<SemanticVersion> normalized = new ArrayList<>(
                Objects.requireNonNull(contracts, "contracts"));
        if (normalized.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("contracts contains null");
        }
        normalized.sort(SemanticVersion::compareTo);
        return normalized.stream().distinct().toList();
    }

    private ModApiVersion() {
    }
}
