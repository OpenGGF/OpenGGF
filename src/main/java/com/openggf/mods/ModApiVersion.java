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
     * bounded semantic ROM music with consumed-sample playback, section parts,
     * and bounded pitch-gliding ROM-part cues that preserve the song clock.
     * Full-song and selected-part synthesis expose cancellable host jobs with
     * progress, a ten-minute duration cap and a 256 MiB stereo PCM budget.
     * Scene-owned direct peer text messaging is asynchronous and bounded, with
     * socket/thread ownership and lifetime kept in the engine.
     * Power-up rules expose explicit invincibility-expiry music ownership for modes
     * with continuous music. Owned patch scenes can play their validated audio-manifest
     * SFX by local name without numeric ids or creator-supplied ownership.
     * Scene audio can play any supplied ROM's song as the base driver's music through
     * the cross-game donor route, under a donor registration scoped to the scene.
     * Scene visits can suspend into owned registered acts with typed launch/result,
     * native centre spawn overrides and once-only frame-boundary exit consumption.
     * Entry/exit isolate rewind timelines while preserving the scene and its ROM library.
     * Creator helpers include character specifications and lifecycle hooks, decoded
     * placement transforms, owner storage and service bundles, named single-act and
     * multi-act zone factories, shared UI/input and ROM-qualified scene art, including
     * the players' S3K dash dust. Object art may name LogicalRom.S1 with S1 mapping
     * parsing even in an S3K-base mod; the legacy call retains Sonic 2 semantics.
     * NativePositionOps and the solid-platform provider/params are candidate creator contracts:
     * act controllers can reset native positions and provide rewindable top-solid decks.
     * Camera framing can cap the visible right edge independently of native player walls.
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
