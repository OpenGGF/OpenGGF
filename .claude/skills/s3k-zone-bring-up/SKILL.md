---
name: s3k-zone-bring-up
description: Use when bringing up or completing an S3K zone, especially when work spans nonstandard events, objects, bosses, visual systems, route validation, or compatibility audits.
---

# S3K Zone Bring-Up Orchestrator

One-stop coordinator for complete Sonic 3 & Knuckles zone delivery. Preserve locked-on disassembly behavior first, close playable route slices second, defer expensive complete-run trace work until reachable content is broadly implemented, then audit OpenGGF compatibility modes without changing the native path.

## Agent Workflow Tooling

Use these tools/docs to ground the bring-up before and during orchestration:

- **ZoneSpecNormalizerTool** -- normalize the `s3k-zone-analysis` output into the stable 13-section layout (palette cycling vs mutation kept separate, `(not analyzed)` placeholders for gaps) before the Step 2 review gate: `mvn exec:java "-Dexec.mainClass=com.openggf.tools.ZoneSpecNormalizerTool" "-Dexec.args=<path-to-zone-analysis-spec.md>"`
- **AgentWorkflowTool** -- preflight checklist (zone-set resolution, registry status, RomOffsetFinder commands, required guards, docs) when a route blocker is an object/badnik: `mvn exec:java "-Dexec.mainClass=com.openggf.tools.AgentWorkflowTool" "-Dexec.args=object s3k MHZ 0x8A"`
- **Doc:** `docs/agent-workflow/runbooks/runbook-s3k-zone-feature.md` -- end-to-end runbook for an S3K zone feature.

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "HCZ", "LBZ", "CNZ") followed by optional flags:

- `--skip-review` -- skip the human review gate after analysis and proceed directly to feature dispatch
- `--validate-only` -- skip implementation, run only the validation skill against an already-implemented zone

Examples:
```
HCZ
LBZ --skip-review
CNZ --validate-only
```

## Related Skills

| Required sub-skill | Purpose |
|---|---|
| **s3k-zone-analysis** | Read the disassembly and produce a structured zone feature catalogue |
| **s3k-zone-events** | Implement camera locks, boss arenas, cutscenes, act transitions, and event palette mutations |
| **s3k-parallax** | Implement per-line scroll handlers and deform routines |
| **s3k-animated-tiles** | Implement AniPLC triggers, gating, and dynamic art overrides |
| **s3k-palette-cycling** | Implement AnPal handlers and validation |
| **s3k-zone-validate** | Compare stable-retro and engine visuals |
| **s3k-implement-object** | Port placed gimmicks, hazards, badniks, and dynamic children |
| **s3k-implement-boss** | Port minibosses, bosses, arenas, children, and defeat sequences |
| **s3k-plc-system** | Implement and verify level, boss, and transition art handoffs |
| **trace-replay-bug-fixing** | Run comparison-only late trace polish without hydration or carve-outs |

## S&K-Side Addresses by Default — Sonic 3 Standalone Is a Rare Fallback

When dispatching feature agents, re-iterate this rule in every prompt: **the engine is S3KL (locked-on), so ROM constants should come from the S&K half (`sonic3k.asm`, addresses < 0x200000) by default**, even when the two halves look identical. Always invoke `RomOffsetFinder` with `--game s3k`, and if a lookup returns both halves, pick the `sonic3k.asm` result. **Rare exception:** if an object has genuinely no S&K equivalent, it may reference the S3-half (`s3.asm`) asset directly — use that after verifying, rather than blocking. See `s3k-disasm-guide` for the full selection rule.

## Framework-First Rule

When feature agents implement the zone, prefer the runtime-owned stack over bespoke zone-local state, but keep the goal practical: close a playable S3K route slice. Do not launch broad architecture-only migrations unless they directly reduce risk or duplication in the target slice.

- Shared event/object/scroll state belongs in a typed `ZoneRuntimeRegistry` adapter.
- Timer-driven palette work and event-driven palette mutations should route through `PaletteOwnershipRegistry`.
- AniPLC and script-driven art uploads should use `AnimatedTileChannelGraph` and `S3kAnimatedTileChannels` where possible.
- Tile/block/chunk edits should use `ZoneLayoutMutationPipeline` (directly or via `S3kSeamlessMutationExecutor`).
- Extra overlays and frame render flags should go through `SpecialRenderEffectRegistry` / `AdvancedRenderModeController`.
- New scanline-fill math should reuse `ScrollEffectComposer`, `DeformationPlan`, `ScatterFillPlan`, and `WaterlineBlendComposer`.
- Objects must use `ObjectServices`; forced movement, participation, native coordinates, and lifecycle must use `ObjectControlState`, `ObjectPlayerQuery` / `ObjectPlayerParticipationPolicy`, `NativePositionOps`, and `ObjectLifetimeOps`.
- New runtime and object state must have deterministic reset, checkpoint/death restore, session reload, and rewind capture/relink behavior. Run both rewind coverage guards and focused capture/restore round-trip tests.

### Retained VDP Plane and Palette-Lifecycle Gate

When the disassembly incrementally redraws a plane, distinguish the full CPU level/layout cache from the native retained VDP nametable. Model the latter as a fixed 64x32-cell ring even in widescreen; viewport width must not expand the native plane or change row/column cadence. Keep event-owned delayed position, direction, mode, and act gating in typed zone state, while generic level code exposes allocation-free row/column writes. Continue the ROM's ordinary row feed during staged redraws.

Before accepting retained-plane work:

- Make dirty map invalidation layer-aware. A Plane-A runtime mutation must not rebuild or erase retained Plane B; genuine Plane-B/layout/geometry invalidations must leave retained-authoritative mode and rebuild.
- Verify retained writes and restored bytes survive the next real tilemap ensure, window preparation, upload, and render path. Test the real manager, not a no-op test double.
- Scope retained-plane ownership and rewind payloads to the acts/modes that use them. Preserve exact native ring bytes or prove deterministic reconstruction of staged and ordinary writes.
- Add a ROM-backed tall-cache capture -> mutate -> restore -> reconcile -> next-render test that proves the fixed ring and widescreen/native-plane semantics. Synthetic helper-only snapshots do not pass this gate.

Treat `Target_palette` and live `Normal_palette` as distinct surfaces. A target write is incomplete until the engine's fade lifecycle actually consumes it or safely materializes the same colors while the screen is opaque. Test the first visible frame and target/normal rewind ownership; registry metadata without a production consumer is not implementation.

### Carrier and Dynamic-Family Gate

For carriers, grabs, chains, and forced movement, keep participant state keyed by playable identity and scalable to the configured sidekick count. Never index mutable state by current list order or a fixed native-slot-sized array. Preserve native P1/P2 branch order through `ObjectPlayerParticipationPolicy`, and use allocation-free `ObjectPlayerQuery` iteration in per-frame multi-object hot paths.

For parent/child families, encode every child role, phase, radius, delay, and special flag in captured recreation metadata. A shared parent `ObjectSpawn` is not sufficient. Verify the real `ObjectManager` graph with capture -> remove/diverge -> restore: exact child count/config, settled parent relink despite recreation order, no duplicate respawn, and child lifetime following the restored parent. Constructor-only rewind probes do not pass this gate.

### Boss Oracle and Publication Gate

Before dispatching a boss, require its analysis to classify the damage source and collision-bearing object for every phase. Scripted child/self-impact damage requires negative player-hit coverage proving player attacks do not change health or invoke an invented shield branch.

Expand repeated and nested child tables once per actual owner, and report direct, steady, transient, and peak live counts. Inventory every forward, reverse, cyclic, terminal, and cross-link. For each allocation site, distinguish primitive/search direction, partial-prefix behavior, rollback, retry, independence from sibling tables, and same-sweep first-tick eligibility. Require allocation-failure tests at every ordinal plus a real `ObjectManager` rewind round trip proving exact cyclic topology, no missing-suffix healing, and no duplicate reconstruction.

Treat every installed callback/routine pointer as a separate update state. Record whether the installing routine returns, falls through, tail-jumps to movement/wait, or invokes the callback in the same sweep. Test signed byte/word timers at the last non-firing and exact firing calls, including asymmetric sibling initial timers. Port byte-angle comparisons as unsigned 8-bit operations and preserve endpoint-visible reflection. Audit movement axes against the called helper's register contract instead of assuming conventional sine/cosine screen axes.

Render priority, collision flags, solidity, and visibility are routine-owned mutable state. Test their transition callback, defeat clear, and converted-slot behavior; hiding a boss render frame does not remove its solid provider. Defeat flicker must execute the native move/gravity/delete helper and toggle actual draw eligibility, not just retain a cosmetic boolean.

For graph-linked `RewindRecreatable` objects, provide a parent-free probe/recreate shell when the generic probe builder cannot construct the live signature. Do not resolve parent/sibling references in phase 1. Restore role/family scalars first, then relink in `afterRewindRestoreSettled`; clear stale links before rebuilding only the captured contiguous prefix, and close a cycle only when its real terminal role exists.

Trace defeat publication through the complete reachable chain -- boss, sign/controller, results, global/event write, and transition consumer. Never assign an event flag to the boss merely because defeat eventually causes it. Classify participant policy separately for activation, targeting, contact/hazard, damage authority, forced movement, and completion. Trace standing/status/control-bit activation gates to their exact native slot rather than inheriting the participation of a shared solid routine; preserve P1-only, nearest-native-P1/P2, and all-player operations distinctly under multi-sidekicks.

For `Obj_EndSignControl` families, distinguish the immediate end-of-level-in-effect write from the later sign allocation and still later results/act-complete publication. Preserve the exact allocation primitive (`AllocateObject`, after-current `CreateChild6`, etc.), check allocation success before publishing child existence, and keep the converted boss slot alive/non-solid only as long as the native controller remains alive. The route test must execute the real sign/results handoff; a synthetic boss-local event flag or substitute sign controller fails this gate.

### Signed S3K Mapping-Offset Gate

Treat every relative `dc.w` in an S3K mapping frame-pointer table as a signed 16-bit displacement from the table base. Audit negative/backward pointers and shared-frame references explicitly; Java zero-extension can turn a valid backward reference into a runaway address and allocation/OOM failure. When the first frame pointer cannot prove the pointer-table length, require a disassembly-verified explicit frame count instead of inferring one from that pointer.

Before accepting any new or changed mapping registration:

- Add a real-ROM table-shape regression covering exact frame count and representative piece count, dimensions, and tile indices, including a backward/shared reference when present. Fix the generic decoder or verified table metadata; do not add heap/memory workarounds or object-specific address special cases.
- Run the complete ROM-conditional S3K art crawler and `TestPatternSpriteRendererCorruptionGuard`, not only the focused object test. The crawler must fail boundedly on malformed offsets rather than running away or exhausting memory.

### AnPal Gameplay-State Gate

Do not assume an `AnPal` entry point writes palette colors. Trace every write and the complete consumer graph first; if it mutates gameplay state, place that state with its actual runtime owner rather than forcing it through `PaletteOwnershipRegistry` or hiding it in the palette cycler.

Preserve the exact native state domain. A ROM bit remains a bit unless the disassembly proves additional values; do not invent a richer enum, intermediate phase, or convenience state. Preserve the exact frame phase too: determine whether the routine reads before or after the counter increment and whether consumers observe the value before or after object updates.

Before accepting an AnPal-backed state transition:

- Audit every entry-point gate before the mutation, including `Palette_fade_timer` and any act, mode, or event guard. Determine from the disassembly whether a qualifying counter edge suppressed by a gate is skipped permanently or deferred for catch-up; do not infer deferred work merely because the counter continues advancing.
- Test at least two consecutive qualifying edges, including the value observed by every consumer on each edge. A single transition can conceal an invented third state or an off-by-one phase.
- Test the actual frame pipeline around counter increment, gated AnPal dispatch, and object updates rather than calling the state helper in isolation. The integrated test must prove two qualifying edges, suppression on a gated edge, and the native catch-up or no-catch-up result after the gate clears.
- Inventory present and planned consumers across the complete zone object graph (for example, later Blaster-family work). Expose shared typed runtime state/API at the real owner; do not place the contract in the first object family that happens to consume it.
- Capture and restore the exact state and counter phase through rewind; verify death/restart, checkpoint, and session reload behavior when those lifecycles can cross the transition.

### Shared Timer and Callback Oracle Gate

Before scheduling object-family work, require the object analysis to trace every tail-jumped shared routine, callback/function pointer field, field width, and competing consumer before naming a timer or animation semantic. Calculate native signed predecrement edges exactly: `subq.w #1` plus `bmi` fires on update `N+1`, after the word passes from zero to `$FFFF`. When raw animation `$F4` and `Obj_Wait` share a callback, compare both paths from the real entry state and implement the earliest reachable path.

Treat every explicitly addressed global counter as a separate clock domain. In particular, `ObjectInstance.update(...)` receives ObjectManager's free-running VBla-style counter; it is not an alias for the Process_Sprites-visible `(Level_frame_counter+1)`. When a cadence, movement, or allocation gate reads the latter, resolve `LevelManager.getFrameCounter()+1` through injected services and retain the update argument only as an isolated-test fallback. Require a RED test in which the two clocks select opposite branches, and cover periodic child allocation as well as parent movement so a dephased hazard cannot disappear while player physics still looks exact.

Preserve the native reachability of shared tails. For each sign/status branch before a common movement, terrain, touch-list, draw, or delete label, record whether that branch falls through, branches around it, or tail-jumps into it. A flattened state machine must not execute a floor/target impact helper on a rising path that jumps directly to collision publication. Test the last call entering with a negative word separately from the first call entering with zero/nonnegative state; a gravity add that reaches zero does not retroactively change which native branch was taken.

Require a focused RED test across the last non-firing and exact firing updates, plus the competing consumer's later boundary. If the code, comments, and disassembly oracle disagree, pause implementation for independent disassembly adjudication; do not weaken the expectation or choose the locally convenient interpretation.

### Seamless Same-Event Transition Gate

Before implementing an in-place act or section reload, trace the whole native frame and loader continuation, not only the transition request. Record the exact publisher and consumer, whether the reload completes synchronously inside the current `ScreenEvent`/background-event call, which event tail still runs after it, and which loader phases execute later that frame or on the next frame. An asynchronous request is not equivalent unless it preserves that same in-call continuation and every later phase boundary.

Drive that chain through its production owners and the real frame dispatcher. The acceptance test must make the real publisher reach the exact native event-routine state, then observe consume/clear, synchronous reload, and the disassembly-selected post-reload tail in the same call; prove every non-owning event state does not consume it. Directly setting the flag and calling a helper/event method is only a unit test, never route evidence.

Model four independent sets: state that survives reload, entities whose original logical slots survive, entities eligible for the native coordinate-offset scan, and placement/ring state that is deliberately reinitialized. Never use the offset scan as a carry filter. If the native load leaves object RAM live but the engine must rebuild it, capture every live fixed/SST occupant at its original slot, including occupants outside the offset range and occupants that fail the offset predicate. Rebind the complete owner/child/link graph first; then apply the range-and-bit eligibility test, native centre-coordinate/subpixel-preserving offset exactly once, and separate anchor/origin/target hooks exactly once. Keep the request's offset range start-inclusive/end-exclusive and distinct from survival/carry policy.

Restoring a fixed occupant is incomplete unless it re-enters the native global `Process_Sprites` order. Keep the processed SST range separate from the allocatable dynamic window: fixed slot 3 must execute before allocatable slot 4, fixed slots after that window must retain their global order, and destruction/unload/replacement must use the real slot rather than slotless fallback semantics. Prove lower-slot replacements wait for the next pass while higher unvisited slots remain same-pass eligible, without changing allocator pressure or the transition offset range.

Inventory the concrete live families at publication: ordinary placed SST occupants, fixed occupants, boss/event roots and children, results/title owners, player power-ups, slotless children through their owner, and inside/outside-range plus render-bit-pass/fail cases. Seed representative real instances in `ObjectManager` and assert identity, exact slot, graph, centre coordinates, and subpixels after reload. A policy predicate, callback counter, synthetic carrier, or inherited no-op offset hook does not prove native mutation.

Preserve ownership and publication order exactly:

- Change current level identity only at the native reload point; keep apparent/presentation identity unchanged until its later native owner publishes it.
- Keep title-card creation/conversion and music stop/restore with their actual results/title routines. Do not show a reload title, change music early, or let a broad transition callback substitute for those owners.
- Audit explicit transition PLC/art work against later loader aliases and prove each upload occurs only at its native owner. Observe the production PLC scheduler/VRAM request and final palette surfaces through the full reload, including exact IDs, counts, order, and target-versus-normal ownership; selector/helper-only tests do not pass.
- Capture live camera position, copy coordinates, current bounds, target bounds, and destination full bounds before restoring fields. Read every camera setter's implementation first: a `current` setter may also overwrite its target. Shift only the disassembly-selected current/copy axes and live bounds, preserve each other field independently, and assert them through the real `Camera` immediately after reload and after the first later worker update. Use the actual viewport when later windowing or locking depends on screen width.

Test the loader cadence through the real frame pipeline. A ring loader reached after screen events can reinitialize and window from the post-offset camera in the transition frame; object placement cannot do so until the next frame if sprite processing has already run. Prove both boundaries, fresh respawn state, and absence of restart, fade, title, duplicate art upload, or premature music work.

Keep end-control phases separate. A later results/presentation exit may clear the gate that lets an existing controller restore every participating player's controls while the title is still active. Build a publication table naming the sole owner and exact update that writes each completion flag; negative-test every adjacent phase and remove duplicate publishers. Only the native completion consumer may start gradual live-bound workers. Port its signed field widths, pre/post arithmetic, comparisons, update order, and allocation primitive literally even when the resulting fixed-point delta looks counterintuitive. Test the real controller's allocation/failure path and first two worker updates, not only a directly constructed worker.

Treat a completed synchronous reload as a rewind hard boundary. Suppress request-scoped nested generic `LEVEL_LOAD` boundaries/snapshots while the synchronous loader runs; only the transition's intended outer boundary sequence may be visible. Verify this with the real live rewind manager/keyframe path, not a boundary enum/helper probe: no keyframe may expose a half-loaded state, and independent pre-side/post-side capture/restore must preserve current/apparent identity, original slots/links, camera copies/bounds/targets, loader phase, and title/control ownership. Do not seek across the hard boundary.

Make compatibility executable at this transition boundary. For every sidekick-count/duplicate-character, supported-width, and donor-off/donor matrix cell, traverse the full owning route -- results publisher -> real frame event dispatch -> reload -> title/control release -> completion consumer/worker -- in a fresh gameplay/session configuration. A configuration toggle followed by direct event invocation is not route coverage. Prove post-shift world bounds, camera copies, ring/placement windowing, event thresholds, native signal authority, and daisy-chain identity without early activation or unsafe gaps. Then start a fresh native-off session and rerun the same route; add no ability workaround unless a mandatory route is genuinely blocked, and never branch on a raw game name.

## Slice-First Completion Rule

A zone bring-up is successful when it advances a playable route, not when a checklist row changes state. For current work, prioritize AIZ -> HCZ continuity first, then feed CNZ, MGZ, and ICZ into the same standard.

Every bring-up plan should identify:

- Traversal blockers: doors, launchers, forced-movement paths, water/chase mechanics, boss gates, and terrain mutations required to finish the route.
- Event flow: camera locks, bounds, cutscenes, act transitions, boss/miniboss arenas, and palette mutations.
- Visual coherence: parallax, animated tiles, palette cycling, PLC/art loads, staged overlays, and render-mode state needed for the area to look recognizable.
- Parity gates: known trace blockers, object lifecycle, player/sidekick participation, coordinate semantics, rewind-relevant state, focused headless tests, and stable-retro visual validation where practical.
- Content inventory: placed IDs/subtypes/counts, shared objects, dynamic spawn graph, badniks, bosses/children, checkpoints, art/PLC/VRAM handoffs, audio cues, transitions, and placeholder status.

When route blockers involve objects or bosses, make the object contract decision explicit in the plan: `ObjectControlState` for forced/control bits, `ObjectPlayerQuery` plus `ObjectPlayerParticipationPolicy` for native slots versus OpenGGF sidekicks, `ObjectLifetimeOps` for delete/despawn/remembered-object behavior, and canonical solid/touch/lifecycle profiles through compatibility wrappers. Guard work should ratchet baselines from inventory before hard-failing new shortcuts.

## Zone Priority Order

Zones listed in recommended bring-up order. This order favors playable route closure over global checklist coverage. AIZ is already implemented and serves as the reference.

**Existing Features is a dated snapshot (as of 2026-07).** Before dispatching any feature agent, check the live ground truth instead of trusting this table: `ls src/main/java/com/openggf/game/sonic3k/scroll/` + `Sonic3kScrollHandlerProvider` registrations for parallax, and `ls src/main/java/com/openggf/game/sonic3k/events/` for per-zone event handler classes (`Sonic3k{ZONE}Events`).

| Priority | Zone | Full Name | Existing Features | Complexity Notes |
|----------|------|-----------|-------------------|------------------|
| -- | AIZ | Angel Island Zone | Events, parallax, animated tiles, palette cycling | **Reference zone** -- fully implemented |
| 1 | HCZ | Hydrocity Zone | Events, parallax, palette cycling exist | First AIZ continuation slice; water, chase, transitions, and boss/event parity |
| 2 | CNZ | Carnival Night Zone | Events, parallax, palette cycling exist | Existing trace/object work; bumpers, cylinders, miniboss, lighting, and sidekick-sensitive interactions |
| 3 | MGZ | Marble Garden Zone | Events, parallax exist | Existing runtime-state/parallax base; animated tiles, miniboss/boss flow |
| 4 | ICZ | IceCap Zone | Events, parallax, palette cycling exist | Active object work; snowboarding, ice objects, Freezer, validation of palette/PLC state |
| 5 | LBZ | Launch Base Zone | Events, parallax, palette cycling exist | Complex events (rising water, dual acts), validate existing palette cycling |
| 6 | LRZ | Lava Reef Zone | Palette cycling exists | Lava mechanics, visual payoff, validate existing palette cycling; no dedicated event handler or scroll handler yet |
| 7 | FBZ | Flying Battery Zone | Events, parallax, animated tiles, palette cycling, route objects, badniks, and bosses implemented | Complete-run trace and compatibility stabilization active; preserve seamless transition, retained-plane, carrier, and dynamic-family contracts |
| 8 | MHZ | Mushroom Hill Zone | Events, parallax exist | Time-of-season (act color changes); no palette cycling yet |
| 9 | SOZ | Sandopolis Zone | None | Time-of-day system, ghosts, complex zone |
| 10 | SSZ | Sky Sanctuary Zone | None | Short zone, unique sky mechanics |
| 11 | DEZ | Death Egg Zone | None | Death Egg mechanics, complex bosses |
| 12 | DDZ | Doomsday Zone | None | Entirely unique (flight-only boss chase), lowest priority |

Competition zones (ALZ, BPZ, DPZ, CGZ, EMZ) follow after main zones.

## Orchestration Process

### Step 1: Run Zone Analysis

Dispatch an agent with the `s3k-zone-analysis` skill for the target zone. The agent reads the disassembly and produces a structured feature catalogue.

```
Agent prompt: "Use /s3k-zone-analysis {ZONE}"
```

**Output:** `docs/s3k-zones/{zone}-analysis.md` (e.g., `docs/s3k-zones/hcz-analysis.md`)

Wait for the analysis agent to complete before proceeding. The analysis spec is the input to all subsequent steps.

Do not assume `Dynamic_Resize` owns all event behavior. Explicitly audit both acts' `Dynamic_Resize`, `ScreenInit`, `ScreenEvent`, `BackgroundInit`, `BackgroundEvent`, deform/background handlers, custom animated-tile wrappers, AnPal, and any zone-specific routines reached from those entry points. For AnPal, follow writes through every consumer before classifying the routine as palette cycling; it may own gameplay state instead.

Create `docs/s3k-zones/{zone}-object-inventory.md` before implementation. Parse every locked-on placement file and record ID, subtype, per-act count, S3KL/SKL pointer resolution, route impact, placeholder/factory status, mappings/animation/art, audio cues, checkpoint interaction, allocation primitive, and test owner. Add a complete dynamic-spawn graph for event objects, projectiles, children, bosses, transitions, capsules/exits, and cleanup objects. Inventory shared placed objects as well as zone-prefixed objects.

The analysis review packet must print the numeric total placement count for each act and the numeric count for every ID/subtype row. “All objects inventoried” without counts fails the gate.

### Step 2: Human Review Gate

Present the analysis spec to the user for review. Display a summary:

The four visual/event categories are not the full zone scope. Also decide and display route events and nonstandard handlers; traversal blockers; shared and zone-specific objects, badniks, bosses, projectiles, and children; PLC/KosinskiM/VRAM handoffs; music/SFX timing; checkpoints; exits; reset/rewind behavior; and the next-zone transition.

Treat KosinskiM readiness and dependent object allocation as event architecture,
not visual polish. A ROM routine polling `Kos_modules_left` must return without
advancing its own timers until the gameplay-scoped four-entry scheduler is
actually idle. Preserve the queue's header/payload source, source-residue
alignment, evolving VRAM destination, start/DMA phase, FIFO shift, and rewind
state as specified by `s3k-plc-system`. When the unblocked routine allocates an
SST family (notably `Obj_LevelResults`), use real slots and test initial-allocation
retry separately from later partial-prefix publication; embedded arrays, fixed
waits, synthetic retirement timers, or healed missing children are false greens.
Include forced and in-place rewind graph tests so restoration neither duplicates
queued art nor loses parent/child identities.

```
Zone Analysis Complete: {ZONE} ({Full Name})
  Events:          {N stages Act 1} + {N stages Act 2} (confidence: HIGH/MEDIUM/LOW)
  Parallax:        {N bands} (confidence: HIGH/MEDIUM/LOW)
  Animated Tiles:  {N scripts} (confidence: HIGH/MEDIUM/LOW)
  Palette Cycling: {N channels} (confidence: HIGH/MEDIUM/LOW)
  Cross-cutting:   {water/shake/character paths/...}

Full spec: docs/s3k-zones/{zone}-analysis.md
Proceed with implementation? [Y/n]
```

If `--skip-review` was passed, skip this step and proceed directly to Step 3.

### Step 2.5: Green the Spec and Implementation Plan Before Dispatch

The human review flag does not waive independent review. Before any implementation worker starts, delegate a spec-compliance review of the analysis and inventory. Require a binary PASS/RED result for every locked-on handler, route event, placement count, dynamic spawn, object/badnik/boss family, visual and PLC owner, checkpoint, transition, audio cue, and lifecycle concern. Fix every RED finding and repeat with an independent reviewer until the specification is GREEN.

Then build the route-wave implementation plan and delegate a separate plan review. The reviewer must check dependency order, disjoint ownership, test-first RED commands, verification commands, integration points, trace-last ordering, visual evidence, compatibility matrices, documentation, and final acceptance gates. Fix every RED finding and repeat until the plan is GREEN. Only then dispatch implementation agents. `--skip-review` skips only the user-facing analysis pause; it never skips these two green loops.

### Step 3: Determine Feature Scope

Read the analysis spec and decide which features apply to this zone. Not every zone needs every feature -- some have `rts` stubs for AnPal (no palette cycling), some share a parallax handler with another zone, some have no animated tiles.

**Decision flowchart:**

```
For each feature category:
  1. Events (Dynamic_Resize)
     - ALWAYS applicable -- every zone has a _Resize routine
     - Check: is it just "rts" or a trivial stub? If so, create a minimal handler

  2. Parallax (Deform)
     - Check: does the zone have a unique _Deform routine?
     - If the analysis says "shares deform with {other zone}" -> SKIP (already implemented)
     - If the analysis says "unique deform" -> DISPATCH

  3. Animated Tiles (AniPLC)
     - Check: does the analysis list any AniPLC scripts?
     - If "no animated tiles" or "AniPLC routine is rts" -> SKIP
     - If scripts listed -> DISPATCH

  4. Palette Cycling (AnPal)
     - Check: does the analysis say "AnPal is rts" or "no palette cycling"?
     - If rts -> SKIP
     - If AnPal mutates non-palette state -> classify and dispatch it to the real owning feature; do not skip it or force it into palette cycling
     - If channels listed AND already implemented -> DISPATCH with --validate-only flag
     - If channels listed AND not implemented -> DISPATCH
```

Display the scope decision to the user:

```
Feature Scope for {ZONE}:
  [DISPATCH] Events       -- {N} stages, {reason}
  [DISPATCH] Parallax     -- {N} bands, unique deform routine
  [SKIP]    Animated Tiles -- AniPLC routine is rts
  [VALIDATE] Palette Cycling -- {N} channels, already implemented
```

### Step 3.5: Build Route-Driven Implementation Waves

Order work by playable dependency:

1. Typed runtime state, event ownership, collision/render modes, art/animation foundations
2. Act 1 traversal blockers/hazards, boss gate, and seamless transition
3. Act 2 traversal blockers/hazards and boss prerequisites
4. End-boss event, boss, defeat, exit, capsule, and next-zone transition
5. Remaining reachable objects/badniks and visual/audio polish
6. Stable-retro visual checkpoints, then late BizHawk complete-run trace polish
7. Multi-sidekick, widescreen, and cross-game donation audit
8. Native-parity regression with compatibility features disabled

No reachable placeholder is acceptable after wave 5. Each wave must name disjoint file ownership, dependencies, tests, verification commands, and reviewer checks.

Before wave 1, audit every zone `ScreenInit`/act-init allocation relative to the
first `Load_Sprites` pass. Event-owned startup objects must claim their native
SST slots before placement when the disassembly does, be adopted rather than
duplicated across seamless act transitions, and be reconciled after generic
object-placement resets through provider lifecycle hooks driven by native zone
state. Test fresh load, transition, reset, and rewind identities; never repair
allocation order with a trace-route or frame exception.

### Test-First Gate for Every Behavior Task

Every behavior task follows RED-GREEN-REFACTOR: write one focused failing test, run and record the expected RED failure, implement the minimum disassembly-backed behavior, run focused and regression tests to GREEN, then obtain spec-compliance and code-quality review. Do not accept tests added only after implementation. Freeze known-red trace baselines before shared changes and reject regressions by first-error frame/error/warning comparison.

For SST routines that call a solid helper and immediately consume standing or
pushing bits, the RED test must execute the real post-movement `ObjectManager`
pipeline. A `MANUAL_CHECKPOINT` owner consumes `standingNow()`/`pushingNow()`
from its returned `SolidCheckpointBatch` at the native program point; it must
not wait for `SolidObjectListener` compatibility callbacks or fall back to a
stale callback latch when a fresh entry is absent. Require exact identity-set
agreement between the checkpoint batch and the player query, failing closed on
query-only or batch-only participants before any reaction. Preserve native
P1/P2 reaction order before labelled extra-sidekick handling, and release the
exact ride owner when the reaction launches or transfers a participant. Direct
listener invocation is supplemental coverage only; see
`s3k-implement-object/rom-pitfalls.md` P64.

The disassembly review must also track live 68K data/address registers across
every helper call before translating sequential branches into independent
booleans. If a called helper overwrites a register that a later `btst`/compare
still reads, preserve the observable clobber in the smallest object-local owner
and require a complete native-slot truth table. Do not extend native register
accidents to additional engine sidekicks; see
`s3k-implement-object/rom-pitfalls.md` P65.

### Mandatory Two-Stage Review Rubric

Do not accept a green test count by itself. The independent spec reviewer must map every disassembly owner, routine state, tail call, field width, side effect, and negative edge to a test that observes the concrete production owner through its real pipeline; helper predicates, direct callbacks, or synthetic substitutes must be labelled supplemental. For transitions, score every requirement above PASS or RED, including concrete-family offsets, event state/tail, camera fields, live rewind boundaries, production PLC/palette, literal worker arithmetic/allocation, sole publication, and full-route compatibility; NOT ASSESSED is RED.

Only after spec GREEN, have a fresh quality reviewer inspect API semantics, no-op/default hooks, nested lifecycle boundaries, duplicate publication, fixed-point/signed arithmetic, allocation behavior, and whether tests could pass without the claimed side effect. Each reviewer must cite exact files/tests and evidence; fix every RED finding and repeat that review stage until GREEN before starting dependent work.

### Step 4: Dispatch Implementation Agents

Launch one agent per applicable feature in a separate worktree. Each agent receives the zone name and the path to the analysis spec.

**Dispatch prompt templates:**

**Events agent:**
```
Use /s3k-zone-events {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Events section of the analysis spec first, then implement the zone event handler.
```

**Parallax agent:**
```
Use /s3k-parallax {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Parallax section of the analysis spec first for band counts and data table locations.
```

**Animated tiles agent:**
```
Use /s3k-animated-tiles {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Animated Tiles section of the analysis spec first for script addresses and gating conditions.
```

**Palette cycling agent:**
```
Use /s3k-palette-cycling {ZONE}

Zone analysis spec: docs/s3k-zones/{zone}-analysis.md
Read the Palette Cycling section of the analysis spec first for channel definitions and counter addresses.
```

Dispatch only independent tasks within the current route wave in parallel. Use `s3k-implement-object` for every object/badnik family, `s3k-implement-boss` for each boss sequence, and `s3k-plc-system` for art handoffs. Give each worker the analysis path, inventory rows, ROM labels, file ownership, failing test, verification command, and cross-cutting state dependencies. Wait for implementation and two-stage reviews to become green before starting dependent work.

### Step 5: Merge Results

Merge the worktree branches from each feature agent into the main working branch. Since all feature agents work on different primary files (event handler class, scroll handler class, pattern animator cases, palette cycler cases), the only conflicts occur in shared files (see Section 6 below).

**Merge sequence:**
1. Merge the events worktree first (it is the most foundational -- other features may reference event state)
2. Merge parallax worktree
3. Merge animated tiles worktree
4. Merge palette cycling worktree
5. For each merge conflict in a shared file, apply the additive resolution strategy (Section 6)

### Step 6: Build Verification

Run the full build to verify compilation:

```bash
mvn package
```

If the build fails:
1. Read the compiler error
2. Identify which shared file has a conflict or which feature agent introduced an incompatibility
3. Fix the issue (most likely a missing import, duplicate constant name, or switch case ordering)
4. Re-run `mvn package`

Also run existing S3K tests to verify no regressions:

```bash
mvn test -Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

### Step 7: Validate Focused and Visual Behavior

Dispatch an agent with the `s3k-zone-validate` skill for the target zone:

```
Use /s3k-zone-validate {ZONE}
```

The validation agent captures reference screenshots from stable-retro and compares them against the engine's output for feature presence (parallax layers, palette cycling, animated tiles, camera locks). Create an explicit checkpoint manifest spanning both act starts, every visual/event mode, representative traversal objects and hazards, PLC/VRAM handoffs, bosses, act transition, exit, and next-zone handoff. Each required row must retain reference and engine evidence plus capture provenance and sidecar metadata; missing evidence is FAIL, never SKIP or an inferred PASS. Review the validation report and investigate every FAIL.

Before freezing that manifest or binding its hash into tooling, prove that every
reference state has an executable acquisition path. A natural movie frame may
own a natural-route checkpoint. A synthetic reverse crossing, exact boss/event
graph, PLC boundary, or pre-transition hook requires an implemented and
fail-closed savestate branch that asserts its preconditions and postconditions;
a coordinate-only recipe or nearest movie frame is not executable evidence.
Audit the validator's active-group accounting at the same time so historical or
superseded rows cannot be counted as current failures. Do not create a mandatory
gate that the checked-in capture tooling cannot physically satisfy.

Focused/headless tests remain the normal development loop. Do not start token-intensive complete-run trace capture/replay while major route events, reachable objects, bosses, or visual systems are placeholders.

### Step 8: Run Late Complete-Run Trace Polish

Only after both acts and all reachable content are broadly implemented, use BizHawk to record or validate the target complete-run BK2, retain its emulator/movie/ROM provenance and hashes, install the exact zone segment, and add fixture/replay tests. Derive segment boundaries and row counts from the recorder contract; never invent or duplicate a row to satisfy a planned count. Replay controller input only: trace state is comparison context and must never hydrate engine state. Move the first divergence through disassembly-backed fixes. Never add tolerance masking, zone/route/frame carve-outs, or known-trace exceptions. Update `docs/TRACE_FRONTIER_LOG.md` whenever the frontier moves or a prior green regresses.

### Step 9: Audit Compatibility Modes

After native parity and late trace/visual polish, run mandatory final audits:

- **Multi-sidekicks:** 0-3 sidekicks plus duplicate-character banks; explicitly test participant policy and shared mutable zone/event/boss state with three or more characters, plus solids/hazards, carriers, forced movement, bosses, transitions, and full-route completion.
- **Widescreen:** every supported viewport width; verify world-coordinate gates, camera locks/releases, spawning/culling, hazards, boss arenas, screen-edge transitions, and prevention of premature activation or unsafe falls.
- **Cross-game donation:** donor off plus every external donor supported by the host; complete mandatory routes and identify mechanics requiring unavailable abilities. These are fail-closed acceptance rows: a missing required donor ROM fails validation and must never silently skip its row. Add only explicit semantic capability/profile workarounds with the blocked route and rationale documented. Preserve native behavior when donation is off.

Any required Sonic 1 workaround must carry a production comment beginning `S1 donation compatibility:` while its branch remains gated by the semantic capability/profile, never by a raw game name.

Rerun the strict locked-on native suite after compatibility work with donation and extension modes disabled.

The compatibility report is invalid unless it explicitly records multi-sidekick shared-state results; for every widescreen width, world-coordinate event-threshold results, boss-arena containment, premature event/object activation, unsafe falls/deaths, and camera-lock/release behavior; and every S1 donation workaround's `S1 donation compatibility:` production-comment location plus semantic capability gate. Do not collapse these into a generic “route passed” result.

Compatibility claims must be executable at the owning feature boundary. Boss coverage, for example, should prove that extra sidekicks cannot acquire native-only activation authority, camera locks remain world-coordinate constants at wide viewports, donation-neutral traversal does not assume Spindash, and an object-manager/session reset recreates a pristine family with no static control-bit or graph leakage. Rerun the exact native route after these extension cases.

### Step 10: Final Integration, Review, and Documentation Gate

After compatibility changes, rerun the native routes, complete-run replay, and required visual checkpoints so extension fixes cannot silently regress locked-on parity. Then delegate a whole-zone spec review and, only after spec GREEN, a fresh whole-zone quality/architecture review. Fix every RED finding and repeat the relevant review stage until both are GREEN.

Update `docs/s3k-zones/{zone}-analysis.md`, `{zone}-object-inventory.md`, `{zone}-validation.md`, and `{zone}-compatibility.md`; update `docs/TRACE_FRONTIER_LOG.md`, changelog/discrepancy records, and test/RED-GREEN evidence when applicable. Feed reusable architectural or false-green lessons back into the relevant skills. Keep `.agents/skills` and `.claude/skills` mirrors byte-identical and validate every changed skill before completion.

### Complete Definition of Done

- Every applicable event entry point and nonstandard handler is implemented from the locked-on disassembly.
- Every reachable placed ID/subtype and dynamic spawn has a concrete correct factory; no reachable placeholder remains.
- Objects, badniks, bosses, transitions, art/PLC/VRAM, audio, visual systems, collision, reset, checkpoint, session, and rewind behavior have focused test-first coverage.
- Both acts and all native character routes complete without debug bypasses.
- Late complete-run replay is comparison-only and green; mandatory stable-retro checkpoints are PASS.
- Multi-sidekick, widescreen, and cross-game donation audits pass.
- Every required S1 donation workaround is capability-gated and visibly labelled `S1 donation compatibility:` in production code.
- Native locked-on tests are rerun after compatibility changes, and existing regression baselines are preserved.
- Post-compatibility trace and visual checkpoints remain green; final delegated spec and quality reviews are GREEN.
- Analysis, inventory, validation, compatibility, trace-frontier, changelog/discrepancy, and applicable mirrored skill documentation are current.

## Shared File Conflict Resolution

Five files are touched by multiple feature agents. All changes are additive (new constants, new switch cases, new registrations), so conflicts are mechanical -- resolve by combining the additions from each branch.

| File | What Each Agent Adds | Resolution |
|------|---------------------|------------|
| `Sonic3kConstants.java` | ROM address constants (events adds event-related offsets, parallax adds deform data offsets, etc.) | Combine all new `public static final` declarations. No two agents define the same constant name since they use different prefixes (`DEFORM_`, `ANIPLC_`, `ANPAL_`, etc.) |
| `Sonic3kLevelEventManager.java` | Zone dispatch case in `createEventsForZone()` or equivalent switch | Only the events agent adds a case here. If another agent also touches this file (e.g., to read event state), take both changes. |
| `Sonic3kScrollHandlerProvider.java` | Zone case returning the new scroll handler | Only the parallax agent adds a case. Merge is trivial -- add the new case to the switch. |
| `Sonic3kPatternAnimator.java` | Zone case in `resolveAniPlcAddr()` and/or `update()` | Only the animated tiles agent adds cases. Merge is trivial. |
| `Sonic3kPaletteCycler.java` | Zone case or channel updates in the cycling method | Only the palette cycling agent adds cases. Merge is trivial. |

**If a true conflict occurs** (two agents modified the same line), prefer the events agent's version for event-state-related code, and the feature-specific agent's version for its own feature code.

## Common Mistakes

1. **Skipping analysis.** Never dispatch feature agents without running `s3k-zone-analysis` first. The analysis spec is the contract between analysis and implementation -- without it, feature agents will re-derive information from the disassembly independently, leading to inconsistent interpretations and duplicated work.

2. **Not checking feature applicability.** Some zones have `rts` stubs for AnPal (no palette cycling) or share a Deform routine with another zone (no unique parallax to implement). Dispatching an agent for a non-applicable feature wastes time and may produce incorrect code. Always run Step 3's decision flowchart before dispatching.

3. **Merging without building.** After merging worktree branches, always run `mvn package` before proceeding to validation. Shared file conflicts that silently produce invalid Java (duplicate switch cases, missing imports) will only surface at compile time.

4. **Forgetting palette cycling validation for already-implemented zones.** HCZ, CNZ, ICZ, LBZ, and LRZ already have palette cycling implemented (check `Sonic3kPaletteCycler` for the current set). The palette cycling agent should run in `--validate-only` mode for these zones, verifying the existing code against the disassembly rather than reimplementing from scratch. Skipping this validation misses opportunities to catch discrepancies in the existing implementation.

5. **Dispatching all 4 feature agents unconditionally.** The decision flowchart in Step 3 exists for a reason. MGZ already has parallax implemented -- dispatching a parallax agent will create a conflicting second implementation. A zone with a proven `rts` AniPLC stub needs no animated-tile handler, while FBZ's five real AniPLC channels require implementation and validation.

6. **Ignoring cross-cutting concerns from the analysis.** The analysis spec's "Cross-Cutting Concerns" section flags water systems, screen shake, character branching, and dynamic tilemap changes. These affect multiple features (e.g., water level changes in events affect parallax water-split logic). Review cross-cutting concerns before dispatch and include relevant notes in each agent's prompt.

7. **Wrong merge order.** Events should merge first because event state variables (routine counters, boss flags) may be referenced by other features (animated tile gating, parallax mode switches). Merging parallax first and then events can create forward-reference errors if the parallax handler reads an event field that the events agent introduces.

8. **Treating four feature agents as full zone completion.** A zone is not complete without placed/dynamic objects, badniks, bosses, art/audio handoffs, checkpoints, exits, reset/rewind behavior, and route validation.

9. **Running the complete-run trace too early.** Use focused tests while major systems are absent. Save trace frontier work for late polish and keep it comparison-only.

10. **Skipping compatibility or testing it before native parity.** Prove locked-on behavior first, audit extensions second, then rerun native parity with extensions disabled.

11. **Assuming AnPal means palette cycling.** AnPal can mutate gameplay state. Audit all writes and consumers, preserve the native state domain and frame phase, and test consecutive edges plus lifecycle restoration before accepting it.

12. **Dispatching before the spec and plan are independently green.** A plausible analysis or plan is not acceptance. Loop delegated review and fixes for the spec first, then the implementation plan, before starting implementation workers.

13. **Stopping at feature-test green.** Re-run trace and visual evidence after compatibility work, loop final whole-zone reviews to GREEN, and update the durable zone, trace, discrepancy, changelog, and mirrored skill documentation.

14. **Freezing an unexecutable visual manifest.** Review every reference recipe
against the actual emulator/exporter before hash-binding it. Natural-route
capture, synthetic savestate branches, engine capture, comparison sidecars, and
active-group accounting must all exist for every mandatory row; schema-only
recipes remain planning artifacts, not acceptance tests.

15. **Treating collision angles as per-player final sensor values.** Native
`Primary_Angle`/`Secondary_Angle` are shared retained outputs: empty
`FindFloor`/`FindWall` paths do not write, grounded `Player_AnglePos` seeds
`$03/$03` (or `$00/$00` on an object), and later playables can inherit earlier
writes in the same frame. Dual-plane collision also has observable FG-write,
BG-write, and strict-FG-restore ordering that a selected final tile cannot
represent. Zone bring-up changes that expose a balance/facing frontier must use
the gameplay-scoped collision owner, explicit sensor write metadata, rewind
capture, a guaranteed all-dispatch player tail, centralized grounded AnglePos
(including spindash), wall early-return coverage, and sequential multi-sidekick
tests; never add a zone or character exception. See `trace-replay-bug-fixing` section
“Empty collision probes retain shared angle-output bytes.”

16. **Cleaning up observable P1-to-P2 register dirtiness.** Retail S3K object
routines sometimes reuse a helper-clobbered register when selecting the native
P2 standing/status bit. Trace the register through every call and tail jump.
If the clobber aliases another persistent object bit, model the intended and
aliased bits independently: each native P2 invocation reads, sets, or clears
only the bit selected by the real register value, aggregate lifetime counts
either, and rewind captures both. Keep the register scratch same-call-local,
and never apply the two-slot accident to additional sidekicks. FBZ stationary
wire cage is the canonical case: a changed non-empty P1 DPLC turns d6
`$03->$00100000->$00100001`, selecting object bit 1 instead of P2 standing bit
4; an unchanged frame returns early and selects bit 4 normally. See
`s3k-implement-object/rom-pitfalls.md` P65.
