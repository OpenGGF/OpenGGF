# AIZ2 Battleship Background Wrap Seam — Faithful Fix Design

**Date:** 2026-05-29
**Status:** Design (pending review)
**Scope:** S3K Angel Island Zone Act 2 (Flying Battery battleship auto-scroll → end-boss approach)

## Problem

During the AIZ2 battleship auto-scroll, the camera scrolls on rails and the
background is a looping forest strip. To keep camera/object coordinates bounded,
the ROM renormalizes ("wraps") the camera, both players, and active sequence
objects backward by a fixed distance once the camera reaches a wrap boundary.

The ROM uses two phases:

| Phase | Wrap boundary | Wrap distance |
|-------|---------------|---------------|
| Bombing | `$4440` | `$200` |
| Post-bombing | `$46C0` (set by the bombing-complete handler) | `$200` |

The engine currently matches the ROM during bombing (`$200`) but uses a
**`$80`** approximation post-bombing (`BATTLESHIP_WRAP_DIST_POST_BOMBING` in
`Sonic3kAIZEvents`). This divergence is documented in
`docs/S3K_KNOWN_DISCREPANCIES.md` (entry: "AIZ2 Battleship Post-Bombing Wrap
Distance"). The goal of this work is to replace the `$80` approximation with a
faithful fix so the engine uses the true ROM `$200` wrap with no visible seam,
and to remove the discrepancy entry.

The fidelity bar chosen is **"seamless-correct, not bit-exact"** (Option 2 of
the brainstorming): keep the ROM-observable state exact — the `$200`
camera/object renormalization, which is what affects physics, object positions,
and trace replay — and fix the engine-internal reason a seam appears, by the
cleanest engine-side means, without inventing a one-zone framework.

## Corrected Root-Cause Model

A prior comment/discrepancy note claimed "the ROM hides a seam with an HInt
screen split." That is **not** the actual mechanism. The ROM's `$200` wrap is
invisible *by construction*: the AIZ2 background (VDP Plane B) is a looping
forest strip whose content repeats with period `$200`, so renormalizing
camera/objects by `$200` re-samples **identical** background content. There is
nothing to hide.

The engine shows a seam at `$200` because the AIZ2 background is composed of two
independently-driven pieces that must stay phase-locked:

- **Tilemap window** (which forest blocks are drawn) — positioned by
  `ParallaxManager.getBgCameraX()`, which is camera-X-derived and therefore
  *jumps* when the camera renormalizes by the wrap distance.
- **Per-line HScroll deform** — driven by the continuous, never-wrapping
  `battleshipSmoothScrollX` (see `Sonic3kAIZEvents.getBattleshipSmoothScrollX()`,
  consumed by `SwScrlAiz`).

When the camera renormalizes, the window jumps by the wrap distance but the
deform stays continuous. If the forest layout is not periodic at the wrap
distance, the jumped window samples mismatched forest tiles → visible seam. The
current `$80` value merely shrinks the jump so it lands within a near-identical
stretch of the forest mask — a cosmetic patch, not a fix.

**Fix premise:** restore the true ROM `$200` wrap, and make the engine's AIZ2
forest BG genuinely `$200`-periodic during the post-bombing phase, so the window
jump re-samples identical content and the seam disappears — matching the ROM's
own design.

## Relevant Existing Infrastructure (survey findings)

- **BG is already modeled as a wrapping plane.** `LevelManager.getBlockAtPosition`
  wraps the horizontal sample: `wrappedX = ((x % levelWidth) + levelWidth) % levelWidth`,
  where `levelWidth` is the BG layer width.
- **Per-scanline HScroll already exists** end to end: `ScrollEffectComposer`
  writes one packed FG+BG scroll word per visible line; `BackgroundRenderer`
  uploads them to a GPU HScroll texture — the engine's analogue of the ROM's
  `H_scroll_buffer` DMA. No new line-scroll machinery is required.
- **Zone/phase-specific BG window selection already has precedent.**
  `LevelManager.applyBackgroundTilemapWindowSelection(int bgCameraX)` already
  special-cases a zone/phase (the MGZ state-8 full-width per-line BG tilemap
  path). The AIZ2 forest loop is a second case of this existing pattern, not a
  new framework.
- **AIZ already publishes custom BG scroll sources** consumed by `SwScrlAiz`
  instead of `cameraX`: intro → `AizPlaneIntroInstance.getIntroScrollOffset()`,
  fire → `getFireTransitionBgX()`, battleship → `getBattleshipSmoothScrollX()`.
  This is the established "scripted AIZ sequence drives a custom BG scroll
  source" pattern; the fix extends it, it does not invent it.
- **The AIZ intro is precedent for line-scroll deformation, not for wrapping.**
  The intro uses the same per-line/per-band BG HScroll deform machinery, but it
  never wraps the camera, so it has no seam. There is no existing seamless-wrap
  precedent to copy; AIZ2 is the only camera-wrap loop in the current slice.

## Carve-Out Compliance

The engine's no-carve-out rule forbids branching shared **physics/sidekick/trace**
code on zone id / route / frame. This design does not do that:

- Camera/object wrap stays `$200` for all phases — no physics branch.
- BG **window selection** is a rendering concern owned by the zone scroll/event
  layer, exactly like the existing MGZ state-8 case. The rule explicitly allows
  "Zone/event/object providers may expose ROM state at the owning boundary."
  The AIZ event exposes a semantic predicate (`isBattleshipForestLoopActive()`)
  and a data value (`getForestLoopBgWrapPeriod()`); shared BG code consumes
  those, it does not test `zone == AIZ`.

## Implementation

### Phase 1 — Confirm the mechanism (go/no-go gate)

A short instrumentation/inspection spike, *before* any behavioral change, to
establish two facts:

1. **Window-vs-deform propagation.** Confirm that during the battleship loop the
   BG tilemap window (via `getBgCameraX()`) jumps by the wrap distance on
   renormalization while the per-line deform (`smoothScrollX`) does not — i.e.
   that the seam is the window/deform phase break described above. Method:
   log `getBgCameraX()`, `smoothScrollX`, and the sampled BG `mapX` across a wrap
   frame in a headless AIZ2 run.
2. **Forest repeat period.** Determine the AIZ2 forest BG layout's true
   horizontal repeat period in the engine's level-2 (BG) map data, and
   cross-check it against the ROM's Plane B nametable fill for the forest loop.
   Expected `$200`, but **verified, not assumed**.

**Gate:** if the engine's AIZ2 forest BG layout is not cleanly periodic at the
ROM `$200` (e.g. the imported layout's forest strip has a different period),
surface that finding and decide before proceeding: align to the actual verified
period, or, if the layout genuinely cannot be made periodic without re-importing
data, fall back (and the discrepancy stays, documented). Do **not** force a
`$200` wrap onto non-`$200` data.

### Phase 2 — Core fix

1. **Restore the ROM wrap.** In `Sonic3kAIZEvents`, remove
   `BATTLESHIP_WRAP_DIST_POST_BOMBING` (`$80`) and the post-bombing tuning so the
   post-bombing wrap subtracts the ROM `$200` at boundary `$46C0`
   (`BATTLESHIP_WRAP_X_POST_BOMBING`). The bombing-phase `$200` path is unchanged.
2. **Expose semantic signals at the owning boundary.** Add/confirm on the AIZ
   event / runtime state:
   - `isBattleshipForestLoopActive()` — the post-bombing forest-loop predicate
     (reuse the existing forest-front phase predicate where possible).
   - `getForestLoopBgWrapPeriod()` — the verified period from Phase 1 (`$200`).
3. **Phase-lock the BG window.** In
   `LevelManager.applyBackgroundTilemapWindowSelection()` (alongside MGZ state-8),
   add the AIZ2-forest case: when the forest loop is active, wrap the BG tilemap
   window's horizontal sample at `getForestLoopBgWrapPeriod()` instead of the
   full BG layer width. This phase-locks the window to the same `$200` boundary
   the camera renormalizes on, so the window jump re-samples identical forest
   content.
4. **Cleanup.** Delete the `$80` constant and post-bombing tuning; remove the
   `docs/S3K_KNOWN_DISCREPANCIES.md` "AIZ2 Battleship Post-Bombing Wrap Distance"
   entry (and its TOC line); add a CHANGELOG entry.

### Data flow (post-bombing forest loop, per frame)

```
AIZ event:  forestLoopActive = true, period = $200,
            camera/objects renormalize by ROM $200 at $46C0
        ->  BG window selection wraps forest BG sample at $200
            (phase-locked to the camera renormalization)
        ->  per-line deform (smoothScrollX) and window jump in lockstep at $200
        ->  window re-samples identical forest tiles across the wrap
        ->  no seam; the true ROM $200 wrap is seamless
```

## Testing

- **New headless seam guard** (`TestS3kAiz2…`, ROM-gated): during the
  post-bombing forest loop, assert the BG block sampled at world X and at
  X + `$200` is identical (proves `$200` periodicity / seamlessness). This is the
  real regression guard and replaces reliance on a hand-tuned visual constant.
- **Update `TestS3kAiz2SidekickBoundsSync`:** with the ROM `$200` wrap restored,
  the post-bombing camera `maxX` lands at the true ROM value again
  (the `0x44C0`-class value the test originally asserted before the `$80`
  approximation). Reinstate the exact-value assertion (the test no longer needs
  the boundary-only relaxation introduced for the approximation), while keeping
  the sidekick-bound-mirror assertion.
- **Trace neutrality:** the AIZ trace replay frontier (`TestS3kAizTraceReplay`)
  first-error frame must be unchanged (currently f8941). Because the
  camera/object wrap returns to the ROM `$200` — *closer* to ROM, not further —
  this should not regress the frontier; verify via the stash-baseline method.
- **Full suite:** `mvn clean test` green; ArchUnit unaffected (no new shared→game
  dependency — BG window selection already depends on zone state via the existing
  MGZ precedent path).

## Risks & Mitigations

- **Forest not `$200`-periodic in engine data.** Mitigated by the Phase 1
  go/no-go gate; we align to the verified period or fall back rather than forcing
  `$200`.
- **`getBgCameraX()` derivation differs from the model.** Phase 1 confirms the
  exact propagation before any change; if the window is already driven from a
  continuous source, the fix narrows to the periodicity alignment only.
- **Visual regression in the non-looping forest-front sub-phase.** The window
  wrap-period override is gated strictly on `isBattleshipForestLoopActive()`; all
  other AIZ phases keep the full-BG-layer-width wrap, so intro/fire/normal
  scrolling are untouched.
- **Scope creep into a general looping-BG framework.** Explicitly out of scope.
  Near-term reuse is low (AIZ2 is the only camera-wrap loop in the slice). The
  change is a second case of the existing `applyBackgroundTilemapWindowSelection`
  pattern, not a new abstraction.

## Out of Scope

- Replicating the ROM's exact Plane B nametable-fill bytes (that was Option 1;
  the chosen Option 2 preserves runtime behavior without byte-exact internals).
- A general reusable "seamless camera-wrap looping background" framework.
- Any change to the bombing-phase wrap (already ROM-correct at `$200`).
