---
name: s3k-zone-bring-up
description: Deliver an S3K zone or substantial playable route end to end, from ROM inventory through implementation, route and visual verification, work highlights, and integration. Use specialist skills for isolated fixes.
---

# S3K zone delivery

Use this as the entry point for a substantial zone campaign, including resuming
an existing one. It coordinates the specialist skills; it does not replace their
ROM details or the repository's delivery policy. A zone abbreviation is a scope
to investigate, not a command to rewrite working subsystems. Validation-only
requests inspect and test existing behavior.

The [zone methodology v2](../../../docs/architecture/designs/2026-09-15-zone-methodology-v2.md)
owns the evidence model. The [level test standard](../../../docs/guide/contributing/level-test-standard.md)
owns route breadth and rewind obligations. Read the relevant sections, not the
entire historical execution log. Follow current root `AGENTS.md` for branch
safety, test selection, integration, push and cleanup; this skill adds no approval
gate, unconditional full-suite rule, or permission to delegate.

## Resume from the actual state

Find the existing analysis, design, task plan, per-act matrices and current
implementation. Reconcile their current summaries with production registrations
and recent verification. Keep historical attempts as history; do not restart a
completed slice because an old checklist is unchecked.

Establish the requested acts/routes, supported characters and teams, donors,
viewport widths, acceptance limits, and media deliverables from session context.
Resolve routine choices locally. Record consequential uncertainty in the existing
plan and continue independent work. Respect explicit deferrals such as skipping
traces; report the resulting gap instead of silently changing the task.

For a whole-zone campaign, maintain a unified external capture directory and a
moving highlights reel unless media are scoped out. For a local follow-up,
refresh affected evidence and existing reel excerpts rather than repeating the
whole campaign. Keep replay inputs and reusable probes in their repository owners;
raw captures and temporary test logs have different retention rules.

## Inventory behavior, not just object classes

Reuse `docs/architecture/research/s3k-zones/`; use
[zone analysis](../s3k-zone-analysis/SKILL.md) when substantial source analysis is
missing. For each applicable concern bind the ROM owner, engine owner, trigger,
clock/update phase, dependencies and a concrete verification target:

- Placed objects, subtypes, dynamic children, collision and participant rules.
- Player/sidekick initialization, terrain-driven movement, checkpoint respawn
  and debug checkpoint entry; not all behavior lives in object tables.
- Event/camera flow, terrain mutation, bosses, results and act/zone transitions.
- Parallax/deformation, camera copies, shake, wrap and wide viewport edges.
- AniPLC **and direct animated-art uploads**, palette cycles, darkness and fades.
- PLC/art readiness, renderer invalidation, audio, sprite buckets/slot order,
  mapping-piece priority, background priority and mask/occlusion behavior.

Inventory each act's normal and event-selected presentation modes explicitly.
Do not hide background/animation work under late “polish.” An existing class or
an `rts` handler is neither proof of a gap nor proof that the visible route works.
Implement missing or incorrect behavior only.

## Deliver and verify playable slices

1. Resolve shared state ownership before consumers: events, camera/water,
   palette, animation, layout mutation, object lifetime and rewind adapters.
2. Reproduce a defect with a focused test or probe whose expectation comes from
   the ROM branch/table or independent observation. Do not copy the proposed
   implementation into its oracle. Cover adjacent phases or participants where
   they change the branch.
3. Join the relevant systems into a traversable slice. Extend an ordinary
   controller-driven route from cold entry as work proceeds. Preserve its inputs,
   actual resolved roster and first blocker. A positioned boss success does not
   advance the cold traversal frontier.
4. Read disassembly first. Use a short native probe for a named unresolved
   behavior or presentation question, not to rediscover explicit source constants.
   Match initialization, clock phase and retained render history before comparison.
   Native state is comparison evidence, never gameplay hydration.
5. Exercise representative supported width/donor/team cases early, then the
   applicable final matrix. Assert the live roster and ROM-backed renderers,
   including after respawn and transitions. Do not manufacture unsupported donor
   products with raw debug overrides.
6. Verify capture/restore and forward replay around events, interactions, boss
   graphs, camera locks, world changes and load boundaries; test timeline isolation
   where a load intentionally clears history. Keep short checks independent of
   full routes. Review coupled ownership/ROM interpretation proportionately.
7. Inspect **moving rendered output** at native and representative wide widths:
   approach, active behavior, release and handoff. Include animation phases,
   seams, terrain shake, doors/tracks/spikes and player/child overlap. Numeric
   art or scroll assertions do not establish correct GPU output.

A positioned setup can skip switches, lighting, rising-floor sequences or spawn
history. Re-enter through the production trigger or declare the missing setup;
do not force sprite priority or alter runtime behavior to hide a capture defect.
Widescreen scenery extensions are presentation choices, not native parity fixes:
identify ROM-backed art reuse and preserve gameplay geometry/bounds. Measure
allocation/GC when it is a reported issue or an observed regression; use matched
routes and separate loop/render allocation from recording overhead.

## Route work to the existing skills

| Work | Owner |
|---|---|
| ROM labels, halves and verified addresses | [s3k-disasm-guide](../s3k-disasm-guide/SKILL.md) |
| Camera, arena, cutscene, terrain, transition | [s3k-zone-events](../s3k-zone-events/SKILL.md) |
| Scroll/deformation, water/render split | [s3k-parallax](../s3k-parallax/SKILL.md) |
| AniPLC/direct animated upload | [s3k-animated-tiles](../s3k-animated-tiles/SKILL.md) |
| AnPal cycling | [s3k-palette-cycling](../s3k-palette-cycling/SKILL.md) |
| Traversal object/badnik or boss | [s3k-implement-object](../s3k-implement-object/SKILL.md), [s3k-implement-boss](../s3k-implement-boss/SKILL.md) |
| PLC/queue/art refresh | [s3k-plc-system](../s3k-plc-system/SKILL.md) |
| Authored inputs and ordinary engine movies | [bk2-input-authoring](../bk2-input-authoring/SKILL.md), [gameplay-capture](../gameplay-capture/SKILL.md) |
| Native visual questions | Available `bizhawk-native-reference-capture` skill; otherwise the [native reference host guide](../../../tools/bizhawk/README.md) |
| Canonical native traces / replay diagnosis | [bizhawk-headless-trace](../bizhawk-headless-trace/SKILL.md), [trace-replay-bug-fixing](../trace-replay-bug-fixing/SKILL.md) |
| Render an existing replay / curate work footage | [trace-capture](../trace-capture/SKILL.md), [gameplay-highlights](../gameplay-highlights/SKILL.md) |

Load only skills required by the current slice. Canonical traces, native visual
probes and ordinary gameplay captures answer different questions.

## Close the loop

Update the owning per-act/character-route matrices and
[coverage backlog](../../../docs/status/level-test-coverage.md). Track separately:
**implemented, cold-reachable, rewind-verified, native-behavior matched, and
visually matched**. Engine visual inspection is not native pixel matching.
Attach configuration, setup, command/commit, result/skips and scope to claims.
A blocked reference capture leaves native acceptance open while independent work
continues; a movie or collection of green object tests cannot certify a zone.

Apply the repository's validation selection and baseline-attribution policy once
for the combined delivery. Fix regressions; preserve explicit inherited failures
and deferred obligations. Reconcile the plan's current summary, next actions,
discrepancies and matrix with final evidence. Preserve reusable tools and lessons
in existing catalogues, including rejected approaches whose evidence matters.

Refresh the highlights from verified final captures, keeping originals and the
chapter source map. Complete the authorized integration/push/cleanup flow and
report the delivered routes, remaining gaps, observed checks, commits and media
links. A polished reel is a work summary, not uninterrupted end-to-end evidence.
