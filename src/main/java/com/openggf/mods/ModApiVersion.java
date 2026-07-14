package com.openggf.mods;

public final class ModApiVersion {
    /**
     * Published compiled-mod compatibility version.
     *
     * <p>The reconciled surface lineage is a single chain 1.1.0 -&gt; 1.2.0 -&gt;
     * 2.0.0 -&gt; 2.1.0:
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
     *       1.2 surface, which semver classifies as a major-version break. Retained
     *       as a closed historical baseline.</li>
     *   <li><b>2.1.0</b> ({@code mod-api-signatures-2.1.txt}) — an additive minor
     *       bump over 2.0.0 that publishes the ROM-art intake surface for Sonic 2
     *       patch mods: {@code ModContext.registerRomObjectArt}, the
     *       {@code RomArtRequest} and {@code RomArtCompression} value types. No
     *       existing 2.0 signature was removed or changed. This is the currently
     *       published surface.</li>
     * </ul>
     *
     * <p>The compatibility checks verify the full chain rather than collapsing it:
     * 1.1 -&gt; 1.2 is asserted additive, 1.2 -&gt; 2.0 is asserted to be a
     * declared breaking transition, and 2.0 -&gt; 2.1 is asserted additive, so each
     * step's changes are never silently absorbed into an undocumented jump. See
     * {@code docs/architecture/mod-api-compatibility.md} ("Mod API 2.0.0 breaking
     * transition" and "Mod API 2.1.0 additive bump") for the migration notes.
     */
    public static final SemanticVersion CURRENT = new SemanticVersion(2, 1, 0);

    private ModApiVersion() {
    }
}
