package com.openggf.mods;

public final class ModApiVersion {
    /**
     * Published compiled-mod compatibility version.
     *
     * <p>Bumped to 2.0.0 for the deliberate breaking transition away from 1.1.0:
     * the rewind-state closure consolidation and per-game rules-record changes
     * removed or altered signatures that were frozen in the 1.1 surface, which
     * semver classifies as a major-version break. The 1.1 baseline
     * ({@code mod-api-signatures-1.1.txt}) is retained as a closed historical
     * record; the published surface is now pinned by
     * {@code mod-api-signatures-2.0.txt}. See
     * {@code docs/architecture/mod-api-compatibility.md} ("Mod API 2.0.0
     * breaking transition") for the migration notes.
     *
     * <p>Version-lineage breadcrumb: a separate in-progress branch (the
     * mod-support work in the {@code next} worktree) independently bumped this
     * constant 1.1.0 -> 1.2.0 for an unrelated additive surface change and froze
     * its own {@code mod-api-signatures-1.2.txt}. That is a different change set
     * from this breaking rewind/rules transition. Whoever reconciles this branch
     * with {@code next} must decide the final version lineage (e.g. whether the
     * merged history is 1.1 -> 1.2 -> 2.0 or otherwise) rather than assuming a
     * single "past 1.1" bump.
     */
    public static final SemanticVersion CURRENT = new SemanticVersion(2, 0, 0);

    private ModApiVersion() {
    }
}
