# OpenGGF Roadmap Proposal

This document is a proposal for how to prioritize OpenGGF after the `v0.4.20260304` release.

It is intentionally opinionated:

- Accuracy is more important than raw feature count.
- Shared engine maturity is more important than one-off hacks.
- A smaller number of release themes is better than a wide, vague backlog.

## Current Position

OpenGGF has crossed an important threshold:

- Sonic 1 is broadly playable from start to finish.
- Sonic 2 is the most complete game module and now includes ending/credits coverage.
- Sonic 3 & Knuckles has meaningful early-game support, but is still the least mature module.
- The engine now has stronger multi-game architecture, better tests, and a more credible release story than before `v0.4`.

That changes the planning problem. The project no longer needs a catch-all "prove this can work" roadmap. It needs a focused roadmap that turns broad momentum into a smaller number of high-value outcomes.

## Guiding Principles

### 1. Accuracy First

Gameplay, rendering, collision, audio, and event flow should continue to be validated against ROM behavior and disassembly references. "Looks close enough" should not be accepted as a default.

### 2. Shared Systems Before Workarounds

When multiple games need the same kind of capability, prefer improving shared providers, managers, and load pipelines instead of adding more game-specific branching in core flow.

### 3. Vertical Slices Over Shallow Coverage

A smaller number of fully coherent zones, cutscenes, and gameplay flows is more valuable than many partially implemented areas.

### 4. Release Discipline

Each release should have a narrow theme, explicit exit criteria, and documentation that matches reality.

## v0.5 Retrospective (2026-04-11)

### What the roadmap asked for

v0.5 was themed **"S3K Expansion and Shared-System Hardening"** with four priority areas:
S3K zone breadth, S3K runtime resource parity, shared load/lifecycle architecture, and release/CI
hygiene.

### What actually happened

The release became the largest modernization pass since the project was revived. It delivered:

**Delivered against v0.5 goals:**
- AIZ coverage is substantially deeper: AIZ miniboss defeat flow, signpost/results, Blue Ball
  entry/return, AIZ2 Flying Battery bombing sequence, AIZ2 end boss, post-boss capsule/cutscene
  flow, and the AIZ-to-HCZ transition are represented.
- HCZ is no longer just a load/render target: HCZ water rush, conveyor, fan, block, door, water
  skim, HCZ1 miniboss, and HCZ1-to-HCZ2 transition work landed.
- S3K bonus-stage coverage expanded across Gumball, Glowing Sphere/Pachinko, and Slots.
- S3K object/badnik coverage expanded with MegaChopper, Poindexter, Blastoid, Buggernaut, Bubbler,
  TurboSpiker, InvisibleHurtBlockH, CollapsingBridge, and related art/PLC wiring.
- Shared systems hardened significantly: two-tier services, GameRuntime ownership, LevelManager
  decomposition, MutableLevel, common base classes/utilities, trace replay infrastructure, and
  broader singleton lifecycle testing.

**Still incomplete after v0.5:**
- S3K is not yet a full-game path. Non-AIZ/HCZ zones still need object, event, scroll, boss, and
  PLC parity work.
- Bonus stages are active implementations, not pixel-perfect finished systems.
- The level editor foundation exists, but the editor remains a future release goal.

### Assessment

The shared-system hardening goal was exceeded. The S3K expansion goal was partially met for AIZ
depth but missed on zone breadth. The release pulled forward substantial v0.6 editor foundation
and v0.7 gameplay gap work that was not originally planned for v0.5.

---

## Proposed Priorities (Updated 2026-05-13)

## v0.6 Theme: S3K Playable Slice Parity

The v0.6 scope now prioritizes coherent Sonic 3 & Knuckles playable routes over broad zone
breadth or architecture work for its own sake. The engine architecture is strong enough that the
highest-value work is applying it to route closure: required objects, event flow, scroll/parallax,
animated tiles, palette/PLC state, bosses, trace blockers, rewind-relevant state, and visual
validation.

### Primary Goals

- Make the AIZ -> HCZ route reliable enough to treat as the first S3K vertical slice.
- Feed CNZ, MGZ, and ICZ work into the same slice standard, prioritizing route blockers over
  checklist-only object coverage.
- Use the runtime-owned framework stack where it directly supports the active slice:
  `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`,
  `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and
  `AdvancedRenderModeController`.
- Keep the level editor moving as a prototype, but do not let editor polish outrank S3K route
  blockers during this release line.

### Priority Areas

#### 1. S3K Route Slice Closure

- Close AIZ -> HCZ traversal, transition, boss/event, sidekick, visual, and trace blockers.
- Treat HCZ as the first continuation slice rather than a partial zone bring-up.
- Use focused tests and stable-retro visual validation where practical.

#### 2. S3K Route-Impact Object Work

- Prioritize traversal blockers, terrain modifiers, hazards, boss/miniboss support, and high-usage
  badniks.
- Defer decorative or isolated objects until the target slice is playable.
- Use `S3K_OBJECT_CHECKLIST.md` as input to prioritization, not as the prioritization itself.

#### 3. Data Select and Save System ✅ (in progress)

- S3K data select screen with ROM-accurate rendering, save slots, and team selection — **done**.
- Cross-game donation: S1/S2 can use S3K data select while retaining their own saves — **done**.
- Save persistence with JSON + SHA256 integrity hash — **done**.
- Remaining: native selector mapping art, save-slot visual states, emerald display parity.

#### 4. Level Editor Prototype

- Wire MutableLevel into an interactive editing mode with tile placement.
- Implement enter/exit play-testing using the current `SessionManager` / `WorldSession` /
  `GameplayModeContext` ownership model.
- Basic undo/redo using MutableLevel's saveState/restoreState.

#### 5. S3K Runtime Resource Parity (continued)

- PLC, dynamic art, animated tile, palette, scroll, and render-mode improvements for the active
  route slice first.
- Reduce resource-reference warnings when they affect route visuals or gameplay clarity.

#### 6. ROM/Disassembly Tooling

- Better authoring and inspection workflows around objects, level data, and PLC data.

### Suggested Exit Criteria for v0.6

- AIZ -> HCZ can be played as a coherent route slice with required traversal objects, event flow,
  scroll/parallax, animated tiles, palette/PLC state, transitions, and documented trace/visual
  validation status.
- CNZ, MGZ, and ICZ have prioritized route blockers implemented or explicitly triaged.
- The data select and save system is functional for all three games, with S3K as the primary presentation.
- The level editor supports basic tile editing with undo/redo and play-test round-trips, as long as
  this does not displace the S3K route slice.
- S3K object coverage is broad enough in the active slices that zones feel playable, not just
  renderable.

## v0.7 Theme: Completion, Polish, and Parity Closure

This release should focus on reducing obvious gaps rather than introducing new strategic directions.

### Primary Goals

- Close high-visibility gameplay gaps across all three games.
- Improve end-to-end reliability of title, data select, save, special-stage, ending, and transition flows.
- Convert lingering TODO/FIXME areas into tested behavior or explicit deferrals.

### Priority Areas

- Remaining high-value S3K gameplay gaps (additional zones, bosses, special stage polish).
- Special stage polish where current support exists but parity is incomplete.
- Final parity passes on transitions, boss sequences, and edge-case object behavior.
- Native **Knuckles in Sonic 2** support without S3K donation. Treat Sonic & Knuckles
  lock-on to Sonic 2 as an official patch target rather than a generic cross-game
  character option: check out the s2disasm Knuckles-in-Sonic-2 branch, document the
  branch-vs-stock Sonic 2 code/data differences, and implement Knuckles from those
  differences so physics, objects, monitors/life icons, title/level-select flow, and
  trace behavior are faithful to the lock-on game. Do not infer this behavior from
  S3K donation alone.
- Documentation cleanup around what is complete, partial, or intentionally deferred.

## 1.0 Criteria

Version `1.0` should not mean "every object from every game has been implemented."

It should mean:

- Sonic 1, Sonic 2, and Sonic 3 & Knuckles each have a credible full-game path.
- The engine can support long play sessions without major flow-breaking issues.
- Physics, collision, rendering, and audio regressions are heavily covered by automated tests.
- Remaining missing content is mainly content-completion work, not foundational architecture work.
- Contributors can understand the main extension points without reverse-engineering the entire codebase first.

## Explicit Non-Goals for the Near Term

These are all valid ideas, but they should not outrank the current roadmap themes:

- Broad new experimental features that bypass ROM accuracy concerns.
- Large UI rewrites that do not unlock gameplay, tooling, or reliability.
- Expanding many new zones at once without finishing the shared systems they depend on.
- Treating S1/S2 content growth as the main release driver while S3K remains structurally immature.

## Short Version

`v0.5.20260411` establishes the S3K AIZ-to-HCZ baseline and shared architecture hardening.
`v0.6` turns that baseline into playable S3K route slices, keeps data select/save and editor work
moving without displacing route blockers, and uses shared runtime frameworks where they directly
support parity. `v0.7` focuses on completion and parity closure.
