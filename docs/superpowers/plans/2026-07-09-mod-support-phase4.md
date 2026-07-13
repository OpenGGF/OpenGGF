# Mod Support Phase 4 (Polish) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tiled import, the creator handbook + CI-built sample gallery, the deferred-backlog triage document, and the GUI-tooling recommendation.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase4-design.md` and
`docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md`.

## CONTINGENCY PREAMBLE

Authored 2026-07-09 with Phases 0–3 unlanded. Marker **[P2]/[P3]** tasks re-verify landed interfaces first (Phase 2's export-directory shape and `ggfmod` CLI, Phase 3's samples); structural divergence → STOP and update this plan. Do not begin until Phases 0–3 have merged.

**Landed re-verification (2026-07-13, `next`):** Phase 2's exact root-level
`level.json` plus GPTN/GCHK/GBLK/GMAP, GSHG/GSWD/GSAN, dual GCOL, and GPAL shape
is unchanged; `GgfModCli` exposes the shared `convert level --from-export` path and
validated deterministic `package`. The five checked-in sources are the generated
music pack under `docs/modding/samples/phase4-gallery-music-pack`, data-only reskin
`sample-reskin-src`, badnik+zone `sample-mod-src`, character
`sample-character-src`, and standalone game `sample-standalone-src`. Phase 3's final
creator-guide commit also pre-positioned the music source and detailed character/
standalone pages. That is additive landed documentation, not a format, CLI, or sample
ownership divergence; Tasks 3–4 restructure/link/build those sources in place.

## Global Constraints

- JUnit 5 only; never `git add -A`; no new singletons; **no new Maven dependencies.** TMX parsing uses the JDK's built-in StAX (`javax.xml.stream`) — zero new dependencies. The resolved feature floor is CSV layer encoding (base64 → clear error), **embedded AND external `.tsx` tilesets** (external is Tiled's default output).
- Commit trailers per repo policy; the docs tasks set `Guide`/`Agent-Docs` trailers on merit.
- **Execution branch (user directive 2026-07-10):** implement and commit directly on
  the existing `next` worktree; do not create a phase branch or merge-back commit.

---

### Task 1: TMX parser + mapping rules **[P2]**

**Files:**
- Create: `src/main/java/com/openggf/tools/modsdk/TmxLevelImporter.java` (+ small value records)
- Test: `src/test/java/com/openggf/tools/modsdk/TestTmxLevelImporter.java`

**Contract:** implement the exact finite orthogonal TMX domain and hardened StAX/path rules in the cross-phase format/security spec. The map's real parent is the import root. Disable DTD/entities/XInclude/external schemas. Enforce contained relative TSX/image paths; one 16×16 zero-margin/spacing tileset with exact columns/tilecount/image geometry and `firstgid=1`; zero layer offsets; exact layers; integral point-only centre-coordinate markers with zero size/rotation and map bounds; typed object properties; collision-primary/ALT identity; stable row-major first-seen dedup; and exact Phase 2 export bytes.

CLI is exactly `--from-tmx <map> --palette <GPAL file> [--solid-tiles <profile-dir>]
--out <dir>`. Test lowest exact palette-line and duplicate-index selection, alpha
0/255 rules, opaque-index-0 rejection, profile count equality, raw collision GIDs
0/1 plus a custom profile GID, defaults, FG/BG H/V flips, rejection of every
collision-layer flip bit, primary/secondary `NO_COLLISION`/`ALL_SOLID` raw descriptor
bits (ALT absent copies primary), headless floor/wall collision, and modern-class/
legacy-type precedence and conflict. Reserve and golden-test blank pattern/chunk/block
0 before first-seen dedup, including first-used-nonblank, empty FG/BG, missing BG, and
invisible-collision fixtures; limits and counts include the reservations.

- [x] Steps: re-verify Phase 2 export shape → failing tests for every accepted-domain rule plus `../`, absolute/symlink escape, XXE, oversized counts/images, duplicate layers, multiple tilesets, firstgid, offsets, and typed-property errors → implement → assert repeat conversion byte equality and pinned golden SHA-256 → PASS → commit (`feat: ggfmod Tiled tmx level import`).

---

### Task 2: `convert level --from-tmx` wiring + end-to-end fixture **[P2]**

- [x] Wire `--from-tmx`; the fixture converts twice identically, matches its golden hash, and loads headless through `ModZoneLoader` with primary/secondary collision, tagged spawns, and boundaries asserted.
- [x] Commit (`feat: convert level --from-tmx end to end` with `Changelog: n/a: covered by final phase-4 changelog entry in this branch`).

---

### Task 3: Creator handbook restructure **[P2][P3]**

- [x] Restructure `docs/modding/` per spec §B: index; quickstarts in effort order (music pack → reskin → object → zone → character → standalone); format references citing the authoritative code constants (container versions, manifest fields, `ModLevelDefinition`, audio manifest); the archetype/trust/id-semantics pages; a troubleshooting page enumerating `ggfmod validate`'s finding catalog [P2 — enumerate from the landed validator]. Re-verify every doc claim against landed code — these pages are the creator contract.
- [x] Link-checker script (`tools`-style or a plain test walking `docs/modding/**` for dead relative links) run in the default suite.
- [x] Commit (`docs: mod creator handbook` — `Guide: n/a` unless a GUIDE.md mapping exists, check `.githooks/run-policy`).

---

### Task 4: Sample gallery CI **[P1][P2][P3]**

- [x] Inventory exactly five checked-in sources before writing the gallery: Phase 1
  music pack; Phase 2 data-only reskin and badnik+zone; Phase 3 character and
  standalone game. Structural drift or a missing source is fixed in its owning sample
  directory before this task proceeds. Promote them to `docs/modding/samples/` index
  entries; add `TestSampleModsPackage`, which builds each via `ggfmod package` and
  asserts scan/validation succeeds—format drift breaks visibly.
- [x] Commit (`test: CI-built mod sample gallery`).

---

### Task 5: Deferred-backlog triage document

- [x] Write `docs/modding/BACKLOG.md`: **first step is the sweep the spec mandates** —
  case-insensitively inventory headings/text matching defer, out-of-scope, parked,
  follow-on, future, revisit, when-demanded, later, optional, narrowing, non-goal,
  unsupported, and TODO across the root spec, every phase design/plan, and the shared
  contract. The spec §C list seeds rather than limits the sweep; reconcile duplicates
  and verify each status against landed code. Then per item: original-spec source,
  owning subsystem, demand evidence, cost/risk note, and verdict (schedule / keep
  parked / drop). Mark original-scope commitments separately: base-game SFX overrides
  and S1/S3K new-zone adapters must receive a scheduled plan or an explicit human
  scope-change decision; triage alone cannot silently park/drop them. Scheduled items
  get a pointer to a separately owned plan.
- [x] Review-gate this document with the repo's spec review loop (a reviewer subagent pass until clean), then commit (`docs: mod support deferred-backlog triage`).

---

### Task 6: GUI tooling recommendation

- [x] Write `docs/modding/GUI_TOOLING_EVALUATION.md`: in-engine panels vs external studio vs CLI-only, judged on observed creator friction from the samples (and any adopter feedback available at execution time); rough costs; a recommendation. Explicitly no build commitment. Review-gate like Task 5, then commit (`docs: mod GUI tooling evaluation`).

---

### Task 7: Changelog + wrap-up

- [x] CHANGELOG one phase-4 entry; update README release notes and AGENTS/CLAUDE pointers as relevant; run `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test`, then `mvn test`.

**Completion evidence (2026-07-13, `next`):** the required S3K gate passed 46/0/0/0;
the 14-class importer/CLI/docs/gallery/API compatibility gate passed 123/0/0/1;
a clean default suite reported 12,832 passed, 0 failures, 0 errors, and 23 skipped;
`mvn -DskipTests package` succeeded; and the exact S1 GHZ1, S2 EHZ1, and S3K AIZ
stock trace spots each passed 1/0/0/0. The generated
`docs/rewind/real-gaps.md` report was restored after verification.

---

## Execution notes

- Tasks 1–2 are the only engine/tool code; 3–6 are documentation deliverables with review gates instead of test gates.
- Completion flow: commit verified task slices directly on `next`; Task 7 carries the
  README release-log note. No merge-back step exists.
