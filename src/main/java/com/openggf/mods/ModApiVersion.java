package com.openggf.mods;

public final class ModApiVersion {
    /**
     * Published compiled-mod compatibility version.
     *
     * <p>The reconciled surface lineage is a single chain 1.1.0 -&gt; 1.2.0 -&gt;
     * 2.0.0 -&gt; 2.1.0 -&gt; 2.2.0 -&gt; 2.3.0 -&gt; 2.4.0:
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
     *       existing 2.0 signature was removed or changed. Retained as a closed
     *       historical baseline.</li>
     *   <li><b>2.2.0</b> ({@code mod-api-signatures-2.2.txt}) — an additive minor
     *       bump over 2.1.0 that publishes the playable-subclass rewind hooks:
     *       the {@code PlayerRewindExtra.PlayableSubclassRewindExtra} marker
     *       interface, the {@code subclassExtra} record component/accessor, the
     *       new canonical {@code PlayerRewindExtra} constructor carrying it, and
     *       the {@code AbstractPlayableSprite} {@code captureSubclassRewindState}/
     *       {@code restoreSubclassRewindState} protected hooks. The prior 2.1
     *       canonical {@code PlayerRewindExtra} constructor is preserved verbatim
     *       as a compatibility overload. No existing 2.1 signature was removed or
     *       changed. Retained as a closed historical baseline.</li>
     *   <li><b>2.3.0</b> ({@code mod-api-signatures-2.3.txt}) — an additive minor
     *       bump over 2.2.0 that publishes host-adapted additive zones, including
     *       strict S3K level metadata, sparse palette claims, runtime profiles,
     *       and the game-module adapter hook. No existing 2.2 signature was removed
     *       or changed. Retained as a closed historical baseline.</li>
     *   <li><b>2.4.0</b> ({@code mod-api-signatures-2.4.txt}) — an additive minor
     *       bump over 2.3.0 that publishes exclusive game-start insertion and
     *       destination-scoped launch-team, deterministic input-filter, and HUD
     *       presentation policies. No existing 2.3 signature was removed or
     *       changed. This is the currently published surface.</li>
     * </ul>
     *
     * <p>The compatibility checks verify the full chain rather than collapsing it:
     * 1.1 -&gt; 1.2 is asserted additive, 1.2 -&gt; 2.0 is asserted to be a
     * declared breaking transition, and 2.0 -&gt; 2.1 -&gt; 2.2 -&gt; 2.3 -&gt; 2.4 is asserted additive
     * at each step, so each step's changes are never silently absorbed into an
     * undocumented jump. See {@code docs/architecture/mod-api-compatibility.md}
     * ("Mod API 2.0.0 breaking transition" and the 2.1.0 through 2.4.0 additive
     * bump sections) for the migration notes.
     */
    public static final SemanticVersion CURRENT = SemanticVersion.parse("2.4.0");

    private ModApiVersion() {
    }
}
