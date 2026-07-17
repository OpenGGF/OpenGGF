# S3K Zone Bring-Up Skill Baseline

**Tested skill:** `.agents/skills/s3k-zone-bring-up/SKILL.md` before the 2026-07-12 workflow update

## Exact Prompt

```text
Use the repository's s3k-zone-bring-up skill for this request:

"Implement Flying Battery Zone completely and accurately from the locked-on S3K disassembly. Both acts, every event, object, badnik, boss, transition, and visual system are in scope. Explain the complete workflow you would follow from analysis through final acceptance. Include task ordering, artifacts, implementation gates, and validation. Do not edit files or run implementation; return the proposed workflow only."

Use only the skill and resources it explicitly requires. Do not infer additional requirements from unrelated project documents. Return a concrete ordered workflow and definition of done.
```

## Raw Agent Output

```text
1. Establish live baseline. Confirm FBZ is zone `0x04`, zone set `S3KL`, inspect current event/scroll/animation/palette registrations, and inventory existing FBZ object support. Treat the skill’s “palette placeholder/no event or scroll handler” table as dated until verified.

2. Produce `docs/s3k-zones/fbz-analysis.md` with `s3k-zone-analysis`. Read both acts’ full locked-on `sonic3k.asm` paths for:

   - FG/BG event state machines, camera bounds, transitions, miniboss/boss gates, PLC loads, palette mutations, and character branches.
   - `FBZ_Deform`, indoor/outdoor modes, scatter-fill tables, vertical scroll, and background events.
   - Both AniPLC entries and any custom runtime DMA/art overrides.
   - `AnPal_FBZ`; record its flicker-bit behavior accurately rather than inventing color-table cycling.
   - Every placed object ID from both acts, resolved through the S3KL pointer table, including gimmicks, hazards, badniks, miniboss, end boss, child objects, mappings, DPLCs, animations, and art.
   - Cross-category RAM, VRAM, palette, PLC, layout-mutation, and render-state ownership.

   Classify every object and feature as `BLOCKER`, `HIGH`, or `POLISH`, but retain all rows because full completion is required. Cite labels and verified S&K-side offsets; use an S3-half address only when no S&K equivalent exists and the runtime reference is verified.

3. Normalize the analysis with `ZoneSpecNormalizerTool` into its stable 13-section form. Required analysis artifacts are the routine/state tables, object matrix, dependency map, VRAM ownership table, palette ownership table, confidence ratings, framework-routing decisions, and risk list. No `(not analyzed)` entry may remain for final implementation dispatch.

4. Hold the human review gate. Present counts and confidence for both acts: event stages, bosses, parallax bands/modes, AniPLC scripts, palette behavior, object/badnik inventory, transitions, and cross-cutting dependencies. Implementation begins only after the catalogue is accepted; any LOW-confidence route, boss, or ownership item returns to analysis first.

5. Convert the accepted spec into an ordered delivery backlog:

   1. Runtime state and event skeleton.
   2. Traversal blockers and forced-movement gimmicks.
   3. Act 1 event flow, miniboss/boss gate, and Act 1→2 transition.
   4. Act 2 event flow, end-boss arena, defeat sequence, and exit.
   5. Common platforms, hazards, and terrain/layout mutations.
   6. Badniks and projectiles.
   7. Boss/miniboss objects, children, PLC/art handoffs, and cleanup.
   8. Parallax and indoor/outdoor background switching.
   9. Animated tiles and runtime art overrides.
   10. FBZ palette flicker behavior and event-driven palette mutations.
   11. Remaining decorative/polish objects.
   12. Integrated validation and discrepancy closure.

6. Before each object, badnik, or boss task, run the object-mode `AgentWorkflowTool` preflight with game `s3k`, zone FBZ, and its object ID. Record zone-set resolution, registry status, exact `RomOffsetFinder --game s3k` commands, required guards, and documentation impact. Each object task must explicitly define:

   - ROM routines, subtypes, mappings, DPLCs, animations, collision, movement, spawning, parent/child behavior, deletion/despawn, and remembered-placement behavior.
   - Centre-coordinate semantics for ROM `x_pos`/`y_pos`.
   - `ObjectControlState` for control/forced-movement bits.
   - `ObjectPlayerQuery` and `ObjectPlayerParticipationPolicy` for native players versus additional sidekicks.
   - `ObjectLifetimeOps` for destruction and slot lifecycle.
   - Canonical solid, touch, and lifecycle profiles.
   - Rewind-relevant mutable state.
   - Focused tests and an acceptance scenario in each act where used.

7. Implement events first through a dedicated FBZ event handler and typed `ZoneRuntimeRegistry` state. Port both FG and BG state machines exactly, including routine stride, camera thresholds, boundaries, screen/background modes, PLC timing, palette mutations, object spawns, boss flags, control locks, and both transitions. Route layout changes through `ZoneLayoutMutationPipeline`, palette writes through `PaletteOwnershipRegistry`, and overlays/render modes through the appropriate runtime registries.

   Event gate: exact headless assertions pass for every stage threshold, boundary, spawn, gate, post-boss release, Act 1→2 handoff, and final exit for every applicable character path.

8. Implement route-critical objects before optional objects. A route slice is not accepted until it can be traversed without debug movement, missing collision, placeholder objects, or event bypasses. Then complete all remaining platforms, hazards, badniks, decorative objects, and variants. Ratchet guard baselines from inventory before enabling stricter failures; do not add route-, zone-, or frame-specific carve-outs.

9. Implement miniboss and boss sequences only after their arena events, blockers, and PLC prerequisites exist. Verify spawn timing, art readiness, hit count and shield reactions, attack phases, child-object ownership, player/sidekick targeting, damage, defeat explosions, music/state changes, camera release, transition trigger, despawn, and restart behavior.

10. Implement visual systems from the accepted dependency map:

   - Parallax: port shared `FBZ_Deform` with exact 68k arithmetic, scatter-fill indices, vertical math, and indoor/outdoor mode switching via `ScrollEffectComposer`/`DeformationPlan`.
   - Background art: reproduce startup, event-driven, and runtime DMA/PLC uploads separately from deform math.
   - Animated tiles: register both acts’ AniPLC scripts through `AnimatedTileChannelGraph`; because FBZ uses the simple handler path, verify both act table entries and script priming, while preserving any discovered ownership handoffs.
   - Palette: validate and implement the locked-on flicker-bit behavior; keep timer cycling distinct from one-shot event mutations.
   - Art acceptance includes real FBZ patterns, correct palettes, no garbage tiles, no seams, and no unexplained pattern-capacity warning.

11. Develop feature work in isolated branches/worktrees as prescribed, then merge in dependency order: events/runtime state, route objects, remaining objects/badniks, bosses, parallax, animated tiles, palette behavior. Resolve shared-file conflicts additively, with event-state definitions authoritative for shared state and each visual implementation authoritative for its own registration.

12. Run verification after every merge wave:

   - Focused unit/headless tests for the changed event, object, boss, scroll, animation, or palette behavior.
   - Provider/registry routing and required guard tests.
   - Existing S3K regression tests named by the runbook.
   - `mvn package`.
   - Both-act route playthrough tests, including death/reload and transition re-entry.
   - A final full relevant test sweep.

   Any failure returns to the owning backlog item; validation does not proceed on a broken build.

13. Validate with `s3k-zone-validate`. Capture stable-retro and engine evidence for both acts at level start, every visual mode boundary, representative object/badnik encounters, miniboss/boss approach and arena, Act 1 transition, Act 2 exit, and time series for animated tiles and palette flicker. Use exact headless checks for deterministic camera/event/transition values and visual comparison for layer structure, animation cadence, art ownership, and recognizable presentation.

14. Save `docs/s3k-zones/fbz-validation.md` with per-feature `PASS/LIKELY/FAIL/SKIP`, evidence, issues, and recommendations. Full FBZ acceptance permits no `FAIL`, no required `SKIP`, and no `LIKELY` on route, event, transition, object, badnik, boss, PLC/art ownership, or other deterministic behavior. Any unavoidable rendering uncertainty must be narrowly documented with evidence.

15. Update `CHANGELOG.md` and `docs/S3K_KNOWN_DISCREPANCIES.md`; cite disassembly labels and verified S&K-side offsets for every ROM-backed table or asset, and complete the required commit trailers.

Definition of done: both acts are traversable end to end for every supported character path; every placed object ID and subtype is implemented or proven to map to an already-correct shared implementation; all badniks, minibosses, bosses, events, camera states, PLC handoffs, transitions, layout mutations, parallax modes, animated tiles, palette behavior, and render effects match the locked-on path; focused and regression tests pass; the package build passes; stable-retro validation has no unresolved required failures; and the analysis, validation, changelog, and discrepancy artifacts are complete.
```

## Rubric Result

| ID | Result | Evidence |
|---|---|---|
| R1 | FAIL | Does not explicitly audit all required handler families by name. |
| R2 | FAIL | Omits explicit placement counts, complete dynamic-spawn graph, comprehensive audio-cue inventory, and checkpoint inventory. |
| R3 | PASS | Orders work by playable act slices and traversal blockers. |
| R4 | FAIL | Names most registries but does not explicitly route through `ObjectServices` and the rewind framework. |
| R5 | FAIL | Tests follow implementation/merge; no mandatory observed RED → minimal implementation → GREEN evidence. |
| R6 | FAIL | No late complete-run trace capture/replay stage. |
| R7 | FAIL | No comparison-only trace rule or hydration/tolerance prohibition. |
| R8 | FAIL | No mandatory multi-sidekick audit. |
| R9 | FAIL | No mandatory supported-width widescreen audit. |
| R10 | FAIL | No mandatory donor-profile audit or capability-driven workaround rule. |
| R11 | FAIL | No native regression after compatibility adaptations. |
| R12 | FAIL | Definition of done omits complete audio, rewind/reset, late trace, and compatibility gates. |

**Overall verdict:** RED (1/12 assertions pass).
