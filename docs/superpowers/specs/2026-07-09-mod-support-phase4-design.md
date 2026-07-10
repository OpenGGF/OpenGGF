# Mod Support Phase 4 — Polish Design

**Branch baseline:** `next`.

**Date:** 2026-07-09
**Status:** Approved (brainstorming session)
**Parent:** `2026-07-09-mod-support-design.md` §8 Phase 4. **Depends on:** Phases
0–3 merged. Deliberately the lightest spec in the series: Phase 4 is polish and
consolidation, and half its value is triaging what earlier phases deferred
against real creator feedback.

## Goal

Round out the mod ecosystem: a Tiled import path for bulk level authoring, a
coherent creator documentation set with a maintained sample gallery, a
disciplined sweep of the deferred-item backlog, and an evidence-based
recommendation (not a build) on GUI tooling.

## A. Tiled import (`ggfmod convert level --from-tmx`)

The parent spec deferred external-editor import because the chunk/block model
maps awkwardly onto Tiled. The mapping adopted:

The exact accepted TMX domain, XML hardening, path containment, limits, deterministic
ordering, export bytes, and golden-hash tests are authoritative in
`2026-07-10-mod-support-format-security-contracts.md`: finite orthogonal 16×16 maps,
one tileset with `firstgid=1`, zero layer offsets, CSV only, required `FG`, optional
`BG`/`COLLISION`/`COLLISION_ALT`, and one optional `OBJECTS` group. DTDs, entities,
XInclude, external schemas, path/symlink escape, infinite maps, multiple tilesets, and
ambiguous typed properties are rejected.

- **Tileset = engine chunks.** The creator's Tiled tileset image is a chunk
  sheet (16×16 tiles); the converter emits the chunk library from it (patterns
  deduplicated across chunks, with the same >16-colors/line and alignment
  errors as `convert art`). Both **embedded and external (`.tsx`) tilesets**
  are accepted — external is Tiled's default output. Palette: the sheet is
  matched against required `--palette <palettes.bin>` (`GPAL` v1); every pattern uses
  the lowest exact matching line. No implicit palette generation occurs.
- **Tile layers → map + auto-derived blocks.** The engine map is block-based
  (128×128); the converter groups the Tiled grid into 8×8-chunk cells,
  deduplicates cells into a block library, and emits the block map. Layers
  are identified **by name** (case-insensitive `FG`, `BG`, `COLLISION`,
  `COLLISION_ALT`); any
  other tile layer is an error, as is a map whose dimensions are not
  block-multiples (8×8 tiles). On FG/BG only, Tiled GID H/V flip bits map to the
  engine's `ChunkDesc` H/V flips. Every flip bit on COLLISION/COLLISION_ALT and
  diagonal/hexagonal flags on every layer are errors.
- **Collision identity is part of dedup identity.** Engine collision lives on
  chunk definitions (`solidTileIndex`) and per-cell `ChunkDesc` bits, not map
  cells — so chunk dedup keys on **(pattern content + collision value)** and
  block dedup keys on the full chunk-cell grid including desc bits: two
  visually identical tiles with different `COLLISION`-layer values yield two
  chunk definitions, never a silent merge. The `COLLISION` tile id assigns the
  **primary** solid-tile index; `COLLISION_ALT` assigns secondary. When ALT is absent
  it copies primary as an explicitly path-neutral default.
- **Solid-tile profile tables:** TMX cannot carry height/width/angle profiles. The importer
  accepts optional `--solid-tiles <profile-dir>` containing exact GSHG/GSWD/GSAN
  files; absent, it emits a minimal two-entry profile set
  (index 0 = empty, index 1 = full-solid) and restricts both `COLLISION` and
  `COLLISION_ALT` tile ids to {0, 1} —
  indices are never dangling. Height/width/angle profile shaping stays outside TMX:
  creators supply SDK/profile binaries, while a future in-engine profile-shape editor
  may author them. Phase 0 only selects existing profiles/modes. The docs are explicit
  that Tiled covers layout/spawn bulk work.
  Primary/secondary collision GID 0 sets that descriptor path to `NO_COLLISION`;
  nonzero sets it to `ALL_SOLID`. Missing ALT copies primary index and mode.
  `TOP_SOLID` and `LEFT_RIGHT_BOTTOM_SOLID` authoring remain in-engine/future work.
- **Zero is truly blank.** Pattern 0, chunk 0, and block 0 are reserved canonical
  blank entries before row-major dedup; counts include them. GID 0 and a missing BG
  resolve through that hierarchy. Invisible nonzero collision derives a nonzero
  blank-pattern chunk instead of adding solidity to reserved chunk 0.
- **Object layer → spawns.** A marker uses modern Tiled `class` when nonempty,
  otherwise legacy `type`; comparison is ASCII case-insensitive. If both are nonempty
  they must compare equal or the marker is rejected. The resulting marker kind is
  `object`, `ring`, or `start`.
  An `object` has exactly one typed `stockObjectId` or `objectKey` property plus
  optional integer `subtype` and boolean `respawnTracked`; identity is never encoded
  in the type string. A `ring` becomes a ring spawn; a point `start` sets the start
  position (absent → default 128,128 with a warning).
- **Boundaries** derive from the map dimensions.
- Output is exactly the Phase 2 export-directory shape, so `convert level`'s
  downstream baking is shared, not forked.

## B. Creator documentation set + sample gallery

- **Declared narrowing of the parent's "docs site":** the site is the in-repo
  `docs/modding/` handbook; hosting/publishing it anywhere is a
  release-engineering decision outside this spec.
- `docs/modding/` becomes a structured handbook: an index, per-mod-type
  quickstarts (music pack → reskin → object/badnik → zone → character →
  standalone game, in effort order), format references (manifest fields, baked
  containers, `ModLevelDefinition`, audio manifest), the archetype/trust/id
  semantics pages the earlier phases mandated, and a troubleshooting page built
  from `ggfmod validate`'s finding catalog.
- **Sample gallery:** the acceptance samples from Phases 1–3 (music pack,
  reskin, badnik+zone, character, standalone game) are promoted to maintained,
  CI-built reference mods — one `docs/modding/samples/` index page linking each
  sample's source with a "what this demonstrates" blurb. CI builds them via
  `ggfmod package` so format drift breaks visibly.
- Format-reference drift control: where a format has a single authoritative
  code constant (container versions, manifest field set), the doc page cites
  the constant and the sample that exercises it; no generated-docs machinery
  in Phase 4 (YAGNI — revisit if drift actually bites).

## C. Deferred-backlog triage (a deliverable, not a wishlist)

Earlier phases deliberately parked (non-exhaustive — see the sweep rule below):
base-game streamed SFX overrides (Phase 1), **mp3
support** (explicitly deferred; decoders hard-error today), classloader
`findResource` delegation (Phase 2 — dependency-resource visibility, added only
if a real consumer appears), mod objects in stock zone layouts (Phase 2),
S1- and S3K-based mod zones beyond Phase 2's S2 flagship, pattern-window manager UI beyond the count
(Phase 2), patch stacking on standalone games plus the corresponding
`GameplayLaunchRequest` choke-point-table amendment (Phase 3), standalone roster
selection UI (Phase 3), mod-supplied title art and super-form art (Phase 3),
the conditional HUD-icon/portrait container slots (Phase 3), the Phase 3 §B6
standalone non-goals (special/bonus stages, cross-game donation, standalone
trace recording, per-standalone launch config, data-select presentation),
underwater low-pass filtering for streams (parent §4 optional polish), editor
S3K runtime re-apply (Phase 0). **The triage task's first step is a
case-insensitive sweep of the root/shared/sibling documents for defer,
out-of-scope, parked, follow-on, future, revisit, when-demanded, later, optional,
narrowing, non-goal, unsupported, and TODO markers** — the list
above seeds it, the sweep completes it. Phase 4's deliverable is a **triage
document** (`docs/modding/BACKLOG.md`): each item gets demand evidence (creator
requests, sample-mod friction), a cost-recalled-from-spec note, and a verdict
(schedule / keep parked / drop). Items that triage to "schedule" become their
own plans — Phase 4 does not implicitly absorb them.

## D. GUI tooling recommendation (evaluation only)

A short evaluation doc: in-engine panels (extending the mod manager /
editor) vs an external studio app vs staying CLI-only, judged against actual
creator friction observed with the Phase 1–3 samples and any early adopters.
Explicitly NOT a build commitment; the parent spec's "possible GUI studio" is
resolved into a recommendation with rough costs, and building anything GUI is
a post-Phase-4 decision.

## Non-goals

Tiled export (round-tripping engine levels back to `.tmx`), Tiled
tileset/pattern-level editing, generated documentation tooling, any GUI
implementation, and every §C backlog item unless its triage verdict says
otherwise (and then as its own plan, not inside Phase 4).

## Verification

- Tiled path: unit tests per mapping rule (chunk emission, block dedup,
  layer-name rules incl. unknown-layer rejection, object/ring/collision
  layers, error catalog) + one end-to-end fixture `.tmx` converted and loaded
  headless through the Phase 2 loader; hostile XML/path/count fixtures; and a repeated
  conversion with a pinned golden SHA-256.
- Gallery: CI job packaging every sample; a link-checker pass over
  `docs/modding/` (script, not infrastructure).
- The triage and GUI docs are review-gated (this repo's spec review loop), not
  test-gated.

## Resolved decisions and release note

- **Tiled version/feature floor:** resolved — CSV layer encoding (Tiled's
  default; base64 → clear error) with BOTH embedded and external `.tsx`
  tilesets (external is Tiled's default output, so rejecting it would fight
  the tool's out-of-box flow).
- **Sample-mod ownership:** samples live in-repo (CI-built) — whether they
  also ship as downloadable jars on releases is a release-engineering call
  outside this spec.
