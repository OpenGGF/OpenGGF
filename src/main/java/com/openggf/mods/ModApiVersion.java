package com.openggf.mods;

public final class ModApiVersion {
    /**
     * Published compiled-mod compatibility version.
     *
     * <p>The reconciled surface lineage is a single chain 1.1.0 -&gt; 1.2.0 -&gt;
     * 2.0.0:
     *
     * <ul>
     *   <li><b>1.1.0</b> ({@code mod-api-signatures-1.1.txt}) — closed historical
     *       baseline, retained immutably.</li>
     *   <li><b>1.2.0</b> ({@code mod-api-signatures-1.2.txt}) — a real additive
     *       minor bump over 1.1.0 that published the mod-support surface (standalone
     *       game support, character definitions, and the additive published roots).
     *       Frozen as a closed historical baseline: a strict additive superset of
     *       1.1.0.</li>
     *   <li><b>2.0.0</b> ({@code mod-api-signatures-2.0.txt}) — the deliberate
     *       breaking bump from 1.2.0. The rewind-state closure consolidation, the
     *       per-game rules-record constructor changes, and the {@code SpriteManager}
     *       overload removal removed or altered signatures that were frozen in the
     *       1.2 surface, which semver classifies as a major-version break. This is
     *       the currently published surface.</li>
     * </ul>
     *
     * <p>The compatibility checks verify the full chain rather than collapsing it:
     * 1.1 -&gt; 1.2 is asserted additive, and 1.2 -&gt; 2.0 is asserted to be a
     * declared breaking transition, so 1.2's additions are never silently absorbed
     * into a direct 1.1 -&gt; 2.0 jump. See
     * {@code docs/architecture/mod-api-compatibility.md} ("Mod API 2.0.0 breaking
     * transition") for the migration notes.
     */
    public static final SemanticVersion CURRENT = new SemanticVersion(2, 0, 0);

    private ModApiVersion() {
    }
}
