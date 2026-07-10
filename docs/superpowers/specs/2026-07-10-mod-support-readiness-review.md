# Mod Support Design Readiness Review

**Date:** 2026-07-10
**Status:** ready for implementation
**Branch baseline:** `next`

## Requirements

The reviewed set must provide an implementable Phase 0–4 path for additive and
standalone mods, preserve deterministic/rewind behavior, bound every hostile input,
and keep existing stock behavior unchanged when external content is absent. Every
phase is implemented directly on `next` per the 2026-07-10 user directive; no other
integration branch or merge-back flow applies to this design family or the adopted
KiS2 prerequisite.

## Exploration Synthesis

Repository inspection confirmed the relevant ownership seams: engine-owned module
resolution, gameplay-session state, object-service injection, level resource plans,
rewind class recreation, presentation audio pumping, editor copy-on-write mutations,
and `ChunkDesc` collision encoding. Reviews treated the existing code as the source of
truth where an earlier design sketch disagreed with a live interface.

The authoritative document chain is:

1. `2026-07-09-mod-support-design.md` for product scope and phase boundaries.
2. `2026-07-10-mod-support-format-security-contracts.md` for serialized formats,
   hostile-input limits, keys, semantic versions, class identity, and TMX semantics.
3. Each phase design for architecture and each matching phase plan for executable
   task order and verification gates.
4. The Phase 0 amendments where they supersede the adopted KiS2 artifacts.

## Architecture Decision Summary

- The process-effective mod catalog is immutable; manager changes are pending and
  restart-required.
- `ModRuntime` owns classloaders for the boot lifetime, while registration plans,
  entrypoints, patches, and external-content views are recreated per session.
- Patches carry explicit built-in/mod ownership and resolve through an engine-owned,
  dependency-ordered `ModuleResolutionService`; no static registry is introduced.
- Manifest v1, namespaced keys, editor envelope versions, baked containers, level
  exports, playable sheets, API inspection, semantic ranges, and TMX import all have
  exact schemas and bounds in the shared contract.
- Mod objects retain namespaced identities through placement and rewind; dynamic
  recreation resolves `(ownerModId, binaryClassName)` through the owner loader.
- Audio is decoded/prepared before session installation, is bounded by immutable
  injected limits, and is forcibly absent at every deterministic entry seam.
- TMX import reserves a genuinely blank pattern/chunk/block zero hierarchy and emits
  explicit primary/secondary collision modes, so imported profiles are active rather
  than decorative metadata.

## Feature Design Coverage

| Phase | Ready scope |
|---|---|
| 0 | Patch composition, non-ROM load sources, bounded asset roots, editor spawn/collision editing, versioned saves |
| 1 | Strict data-only catalog, pending manager state, streamed music packs, deterministic external-content policy |
| 2 | Trusted code loading, curated API, objects/art/zones, bytecode validation, CLI/export workflow |
| 3 | Playable characters, unified title entries, standalone no-ROM modules, namespaced progression/audio |
| 4 | Exact finite TMX import, creator handbook/samples, deferred-backlog triage |

## Original-Spec Traceability

| Original promise | Owning delivery | End-to-end proof |
|---|---|---|
| Drop one jar into `mods/`; inspect without executing code | Phase 1 Tasks 1–2 | Strict main/audio manifests, bounded scanner, valid/invalid catalog entries |
| Enable, disable, reorder, inspect, and persist mods | Phase 1 Tasks 3, 5, 10 | Restart-required pending state, dependency-constrained screen tests, badges/details |
| Compose enabled additive patches without changing stock behavior | Phase 0 A; Phase 2 Tasks 2/7 | Owner/dependency ordering, failure matrix, mods-off trace parity |
| Load creator assets without treating them as ROM fallback | Phase 0 B; Phase 2 Tasks 6/11/12 | Bounded roots, source byte parity, in-memory level fixture |
| Author spawns/collision and export a complete level | Phase 0 C; Phase 2 Tasks 14–15 | Stock palette, undo/COW/rewind, versioned saves, full export golden |
| Music packs and stock music replacement | Phase 1 Tasks 4, 6–9 | S1/S2/S3K packs, real presentation pump, loops/jingles/rewind |
| Trusted Java mods, objects/badniks, reskins, and an additive zone | Phase 2 | Direct-only loaders, closed/Javadoc API, bytecode validation, samples and S2 save/disable |
| Mod characters and no-ROM standalone games | Phase 3 | Character and standalone samples, title→boot→save→Continue, music/SFX/terminal tests |
| Creator CLI/SDK and primary editor workflow | Phase 2 Tasks 16–18; Phase 4 docs | Unedited scaffold compile/package, converters, dev run, maintained samples |
| Tiled import, handbook/gallery, and GUI decision | Phase 4 | Hardened golden TMX fixture, link/sample CI, reviewed evaluation |

Refinements made during repository reconnaissance preserve the product intent while
changing unsafe or inaccurate mechanics: tagged object keys replace runtime byte-id
allocation; arbitrary creator failures abort only the current launch/session instead
of pretending to hot-unload mutated code; ASM validates jars before class loading;
SDK sources remain in-module but ship as the separate promised attached artifact;
MP3 is explicitly deferred. Phase 2's complete
new-zone proof is S2, while S1/S3K zone adapters and base-game SFX overrides remain
original-scope commitments: Phase 4 must schedule them or obtain an explicit human
scope-change decision.

## Implementation Plan Readiness

Plans name prerequisites, branch targets, files and interfaces, red/green tests,
failure behavior, commit boundaries, and phase integration gates. Cross-phase types
are introduced by their owning phase and consumed later without silently changing
serialized versions. The sole new dependency is pinned ASM 9.9.1 in Phase 2 for
method-body/static-field validation that reflection cannot perform.

## End-to-End Review

Three independent review tracks covered foundations/editor, Phase 1 runtime/audio,
and Phases 2–4. Findings were applied and re-reviewed in repeated clean-room passes.
The final passes independently reported `READY` with no implementation-blocking
contradictions. The loops specifically closed lifecycle, dependency, classloader,
rewind, pattern allocation, save migration, deterministic-entry, hostile-input,
manifest-version, TMX palette, blank-tile, and collision-mode gaps.
An additional original-HEAD traceability pass then closed API-version continuity,
audio/catalog timing, owner-tagged character/zone saves, transactional zone/module
registration, runtime fault ownership, SDK artifact publication, sample ownership,
and direct-on-`next` execution. Its three final reviewers also independently reported
`READY`.

## Integration Report

This was a design-readiness task only: no engine source, build configuration, or test
fixture was implemented. The document set is intentionally uncommitted for human
inspection. Final validation consists of git/diff hygiene, branch-baseline scans,
cross-document stale-term scans, Markdown fence balance, and the independent reviewer
verdicts recorded above.

## Human Review Checklist

- Confirm the phased product scope and trusted-code model remain desired.
- Begin Phase 0 directly on `next`; do not implement later-phase sketches ahead of their
  owning prerequisites.
- Treat the shared format/security contract as authoritative when older task prose
  says to choose or invent a representation.
- Preserve the plans' tests and integration gates when right-sizing commits.
