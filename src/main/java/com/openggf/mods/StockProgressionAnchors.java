package com.openggf.mods;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical stock results-flow boundaries exposed to mod manifests.
 *
 * <p>The keys name the completed stock act, not a zone display name. Event and
 * cutscene-driven handoffs are deliberately absent. Phase 2's new-zone seam is
 * scoped to Sonic 2, so other games expose no anchors yet. The Phase 2
 * {@code ZoneProgressionPlan} must consume this inventory rather than creating a
 * second list of accepted manifest anchors.</p>
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
}
