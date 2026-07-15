package com.openggf.mods;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical stock results-flow boundaries exposed to mod manifests.
 *
 * <p>The keys name the completed stock act, not a zone display name. Event and
 * cutscene-driven handoffs are deliberately absent. Sonic 2 exposes its
 * results-driven boundaries and defaults additive sequencing to {@code mtz3}.
 * Sonic 3&amp;K intentionally exposes no stock results-driven anchors: its additive
 * zones may still be published under stable keys, but remain unsequenced until
 * entered explicitly. {@code ZoneProgressionPlan} must consume this inventory
 * rather than creating a second list of accepted manifest anchors.</p>
 */
public final class StockProgressionAnchors {
    private static final Map<String, Set<String>> BY_GAME = Map.of(
            "s1", Set.of(),
            "s2", Set.of("ehz2", "cpz2", "arz2", "cnz2", "htz2", "mcz2", "ooz2", "mtz3"),
            "s3k", Set.of());

    private StockProgressionAnchors() {
    }

    public static boolean contains(String gameId, String anchor) {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(anchor, "anchor");
        return anchorsFor(gameId).contains(anchor);
    }

    public static Set<String> anchorsFor(String gameId) {
        Set<String> anchors = BY_GAME.get(Objects.requireNonNull(gameId, "gameId"));
        if (anchors == null) {
            throw new IllegalArgumentException("Unknown stock game id: " + gameId);
        }
        return anchors;
    }

    /** Returns the host-owned default insertion boundary, when one is sequenced. */
    public static Optional<String> defaultAnchorFor(String gameId) {
        anchorsFor(gameId);
        return "s2".equals(gameId) ? Optional.of("mtz3") : Optional.empty();
    }
}
