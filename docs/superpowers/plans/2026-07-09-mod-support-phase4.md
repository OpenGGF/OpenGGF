# Mod Support Phase 4 (Polish) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tiled import, the creator handbook + CI-built sample gallery, the deferred-backlog triage document, and the GUI-tooling recommendation.

**Spec:** `docs/superpowers/specs/2026-07-09-mod-support-phase4-design.md`.

## CONTINGENCY PREAMBLE

Authored 2026-07-09 with Phases 0–3 unlanded. Marker **[P2]/[P3]** tasks re-verify landed interfaces first (Phase 2's export-directory shape and `ggfmod` CLI, Phase 3's samples); structural divergence → STOP and update this plan. Do not begin until Phases 0–3 have merged.

## Global Constraints

- JUnit 5 only; never `git add -A`; no new singletons; **no new Maven dependencies.** TMX parsing uses the JDK's built-in StAX (`javax.xml.stream`) — zero new dependencies. Feature floor per the spec's resolved open question: CSV layer encoding (base64 → clear error), **embedded AND external `.tsx` tilesets** (external is Tiled's default output).
- Commit trailers per repo policy; the docs tasks set `Guide`/`Agent-Docs` trailers on merit.
- **Branch:** `feature/ai-mod-support-phase4` off `develop`.

---

### Task 1: TMX parser + mapping rules **[P2]**

**Files:**
- Create: `src/main/java/com/openggf/tools/modsdk/TmxLevelImporter.java` (+ small value records)
- Test: `src/test/java/com/openggf/tools/modsdk/TestTmxLevelImporter.java`

**Contract (spec §A, as revised):** JDK StAX parse of `.tmx` (CSV layers; embedded + external `.tsx` tilesets); tileset image → chunk sheet (16×16, quantized against the required palette input) with the `convert art` error catalog; layers identified by name (case-insensitive `FG`/`BG`/`COLLISION`; other tile layers, base64 encoding, diagonal-flip gids, or non-block-multiple map dimensions → errors); gid H/V flip bits → `ChunkDesc` flips; **dedup identity includes collision** (chunk = pattern content + collision value; block = full cell grid incl. desc bits — no silent merges); `COLLISION` tile id assigns the primary solid-tile index, alt = same in v1; `--solid-tiles <file>` companion for the heightmap table, else the two-entry default (0 empty / 1 full-solid, `COLLISION` ids restricted to {0,1}); object layer `type` = id or namespaced key + `subtype`/`respawnTracked`, `ring` → ring spawns, point `type=start` → start position (default 128,128 + warning); boundaries from map dimensions. Output = the Phase 2 export-directory shape (Task 14's `FullLevelExporter` output) [P2 — re-verify the landed shape first].

- [ ] Steps: re-verify Phase 2 export shape → failing tests per mapping rule (chunk emission incl. collision-keyed dedup, external-tsx resolution, flips, layer/dimension/encoding errors, solid-tiles default vs companion, objects/rings/start, boundaries) → implement → PASS → commit (`feat: ggfmod Tiled tmx level import` with `Changelog: n/a: covered by final phase-4 changelog entry in this branch`).

---

### Task 2: `convert level --from-tmx` wiring + end-to-end fixture **[P2]**

- [ ] Wire the importer into `GgfModCli convert level` behind `--from-tmx <file>`; end-to-end test: a fixture `.tmx` (checked into test resources) converts and the result loads headless through the Phase 2 `ModZoneLoader` (level non-null, spawns present, collision as authored). The docs page (Task 3) carries the collision-finishing-in-editor caveat.
- [ ] Commit (`feat: convert level --from-tmx end to end` with `Changelog: n/a: covered by final phase-4 changelog entry in this branch`).

---

### Task 3: Creator handbook restructure **[P2][P3]**

- [ ] Restructure `docs/modding/` per spec §B: index; quickstarts in effort order (music pack → reskin → object → zone → character → standalone); format references citing the authoritative code constants (container versions, manifest fields, `ModLevelDefinition`, audio manifest); the archetype/trust/id-semantics pages; a troubleshooting page enumerating `ggfmod validate`'s finding catalog [P2 — enumerate from the landed validator]. Re-verify every doc claim against landed code — these pages are the creator contract.
- [ ] Link-checker script (`tools`-style or a plain test walking `docs/modding/**` for dead relative links) run in the default suite.
- [ ] Commit (`docs: mod creator handbook` — `Guide: n/a` unless a GUIDE.md mapping exists, check `.githooks/run-policy`).

---

### Task 4: Sample gallery CI **[P1][P2][P3]**

- [ ] Promote the phase acceptance samples to `docs/modding/samples/` index entries; add a CI-runnable test (`TestSampleModsPackage`) that builds each sample source via `ggfmod package` and asserts the jars scan/validate cleanly — format drift breaks visibly. Re-verify each sample's landed location first: Phases 2/3 created source dirs under `src/test/resources/mods/*-src/`, but **Phase 1 built its music-pack jars programmatically in `@TempDir` — the music-pack sample must be AUTHORED here** (a small checked-in source dir exercising the Phase 1 manifest + audio manifest), not merely indexed.
- [ ] Commit (`test: CI-built mod sample gallery`).

---

### Task 5: Deferred-backlog triage document

- [ ] Write `docs/modding/BACKLOG.md`: **first step is the sweep the spec mandates** — grep every mod-support spec/plan for defer/out-of-scope/parked/follow-on markers (the spec §C list seeds, the sweep completes; verify each item's parked-status against the landed phases — some may have been absorbed already). Then per item: demand evidence, cost-from-spec note, verdict (schedule / keep parked / drop). Items verdicted "schedule" get a one-line pointer to "needs its own plan" — nothing is implemented here.
- [ ] Review-gate this document with the repo's spec review loop (a reviewer subagent pass until clean), then commit (`docs: mod support deferred-backlog triage`).

---

### Task 6: GUI tooling recommendation

- [ ] Write `docs/modding/GUI_TOOLING_EVALUATION.md`: in-engine panels vs external studio vs CLI-only, judged on observed creator friction from the samples (and any adopter feedback available at execution time); rough costs; a recommendation. Explicitly no build commitment. Review-gate like Task 5, then commit (`docs: mod GUI tooling evaluation`).

---

### Task 7: Changelog + wrap-up

- [ ] CHANGELOG one phase-4 entry covering the Tiled import (Tasks 1/2's commits carried justified `n/a` trailers pointing here); this commit stages `CHANGELOG.md` with `Changelog: updated`; CLAUDE.md/AGENTS.md pointer updates (`Agent-Docs: updated`); full suite + S3K must-keep-green.

---

## Execution notes

- Tasks 1–2 are the only engine/tool code; 3–6 are documentation deliverables with review gates instead of test gates.
- Merge flow: `superpowers:finishing-a-development-branch`; README release-log note on merge.
