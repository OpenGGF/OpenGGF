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
  and data values (`getForestLoopBgWrapPeriod()`, `getForestLoopBgOrigin()`);
  shared BG code consumes those, it does not test `zone == AIZ`.

## Implementation

### Renderer quantities involved (precise)

The BG window is governed by **three distinct quantities** that the fix must keep
coherent — conflating them is why a naive "set a `$200` period" change would not
remove the seam:

- **`bgTilemapBaseX`** — the 16px-aligned BG window origin, set in
  `LevelManager.applyBackgroundTilemapWindowSelection()` from
  `floorDiv(bgCameraX, 16) * 16` (`LevelManager.java:1826`). This *jumps* by the
  wrap distance when the camera renormalizes.
- **`currentBgPeriodWidth`** — the rendered tilemap *cache* width (the wrapped
  cache size the shader wraps the visible window within; default
  `VDP_BG_PLANE_WIDTH_PX`), set from `parallaxManager.getBgPeriodWidth()`
  (`LevelManager.java:1815-1817`, `LevelTilemapManager.java:351`).
- **`bgXQueryOffset`** — the offset into the **source BG layout** the tilemap is
  built from. Critically, today this wraps at **`bgContiguousWidthPx`** (the
  contiguous BG data width), not at any loop period
  (`LevelTilemapManager.java:365-367`):
  `bgXQueryOffset = ((bgTilemapBaseX % bgContiguousWidthPx) + bgContiguousWidthPx) % bgContiguousWidthPx`.

Therefore: if the fix only narrows `currentBgPeriodWidth` to `$200` but leaves the
source query wrapping at `bgContiguousWidthPx`, a `$200` camera renormalization
still shifts `bgTilemapBaseX` (and thus `bgXQueryOffset`) by `$200` in source
layout space, landing on different source columns unless `bgContiguousWidthPx` is
itself a `$200`-multiple *and* the source content is `$200`-periodic. The seam
remains. The fix must normalize the **source query** to the loop period from a
loop origin (see Phase 2 step 3).

### Phase 1 — Confirm the mechanism (go/no-go gate)

A short instrumentation/inspection spike, *before* any behavioral change, to
establish three facts:

1. **Window-vs-deform propagation.** Confirm that during the battleship loop the
   BG tilemap window (`bgTilemapBaseX` via `getBgCameraX()`) jumps by the wrap
   distance on renormalization while the per-line deform (`smoothScrollX`) does
   not — i.e. that the seam is the window/deform phase break described above.
   Method: log `getBgCameraX()`, `bgTilemapBaseX`, `bgXQueryOffset`,
   `smoothScrollX`, and the sampled source `mapX` across a wrap frame in a
   headless AIZ2 run.
2. **Forest repeat period and loop origin.** Determine the AIZ2 forest BG
   layout's true horizontal repeat period in the engine's level-2 (BG) map data
   **and the world-X loop origin** from which that period repeats, and
   cross-check both against the ROM's Plane B nametable fill for the forest loop.
   Expected period `$200`, but **verified, not assumed**; the origin is needed so
   the modulo normalization in Phase 2 anchors correctly.
3. **Loop-phase boundary.** Pin down exactly when the `$200`-periodic loop is
   active. The existing `isBattleshipForestFrontPhaseActive()` predicate
   (`Sonic3kAIZEvents.java:1760`, spanning camera `$4380..$4880`) is
   priority/display-bucket oriented and is **not** the same interval as the
   looping Plane B source — the loop only begins once `onBattleshipComplete()`
   switches `battleshipWrapX` to `$46C0`. Confirm the precise start (and end) of
   the looping phase so the render-window override is not applied during the
   non-looping forest handoff.

**Gate:** if the engine's AIZ2 forest BG layout is not cleanly periodic at the
ROM `$200` from a stable origin (e.g. the imported layout's forest strip has a
different period, or `bgContiguousWidthPx` cannot be reconciled), surface that
finding and decide before proceeding: align to the actual verified period, or, if
the layout genuinely cannot be made periodic without re-importing data, fall back
(and the discrepancy stays, documented). Do **not** force a `$200` wrap onto
non-`$200` data.

### Phase 2 — Core fix

1. **Restore the ROM wrap.** In `Sonic3kAIZEvents`, remove
   `BATTLESHIP_WRAP_DIST_POST_BOMBING` (`$80`) and the post-bombing tuning so the
   post-bombing wrap subtracts the ROM `$200` at boundary `$46C0`
   (`BATTLESHIP_WRAP_X_POST_BOMBING`). The bombing-phase `$200` path is unchanged.
2. **Expose semantic signals at the owning boundary.** Add on the AIZ event /
   runtime state, gated on the *looping* phase (not the broad display-bucket
   forest-front predicate — see Phase 1 fact 3):
   - `isBattleshipForestLoopActive()` — true only while the post-bombing
     `$200`-periodic loop is actually running, defined precisely (e.g.
     `battleshipAutoScrollActive && battleshipWrapX == BATTLESHIP_WRAP_X_POST_BOMBING`,
     or a dedicated flag set in `onBattleshipComplete()` and cleared when the loop
     ends). It must **not** reuse `isBattleshipForestFrontPhaseActive()`, which
     spans a wider, non-looping interval.
   - `getForestLoopBgWrapPeriod()` — the verified period from Phase 1 (`$200`).
   - `getForestLoopBgOrigin()` — the verified world-X loop origin from Phase 1,
     so the source-query normalization anchors correctly.
3. **Route AIZ through the wrapped-BG build path during the loop.** The
   period/origin math in `LevelTilemapManager` only runs when `bgWrap` is true,
   which requires `Sonic3kZoneFeatureProvider.bgWrapsHorizontally()` — currently
   MGZ, ICZ, and CNZ-boss only, **not AIZ** (`Sonic3kZoneFeatureProvider.java:117`,
   `LevelTilemapManager.java:341`). Without this step the new math is dead code.
   Add a narrow AIZ clause to `bgWrapsHorizontally()` that is true only while
   `isBattleshipForestLoopActive()`, mirroring the existing phase-scoped
   `isCnzBossBackgroundWindowActive(zoneId)` precedent in the same method (so this
   stays a feature-provider boundary decision, not a `zone == AIZ` branch in
   shared code). Also verify `zoneRuntimeRequiresFullWidthBgTilemap()` — which
   forces full-width BG for the AIZ *intro* — does **not** fire during the
   post-bombing loop phase and suppress the wrap (`LevelTilemapManager.java:344`);
   the intro and the loop are distinct phases, but the implementer must confirm
   the loop phase reaches `bgWrap == true`.
4. **Normalize the BG source query to the loop period.** The override must act on
   the **source-layout query**, not merely the cache period. In the
   `LevelManager.applyBackgroundTilemapWindowSelection()` /
   `LevelTilemapManager` BG-build path (alongside the MGZ state-8 and CNZ
   loop-band cases), when `isBattleshipForestLoopActive()`:
   - set `currentBgPeriodWidth` to the loop period (`$200`) so the rendered cache
     and shader wrap at the loop window, **and**
   - replace the `bgXQueryOffset` computation (currently
     `bgTilemapBaseX % bgContiguousWidthPx`, `LevelTilemapManager.java:365-367`)
     with an origin-anchored modulo of the loop period:
     `bgXQueryOffset = origin + (((bgTilemapBaseX - origin) % period) + period) % period`.

   This makes a `$200` jump in `bgTilemapBaseX` map back to the **same source
   column**, so `bgTilemapBaseX`, `currentBgPeriodWidth`, the rebuilt tilemap
   data, and the shader render offset all stay coherent across the wrap. The
   override is strictly scoped to the looping phase; all other AIZ phases (intro,
   fire, forest handoff, normal) keep the existing `bgContiguousWidthPx`-wrapped
   source query untouched.
5. **Cleanup.** Delete the `$80` constant and post-bombing tuning; remove the
   `docs/S3K_KNOWN_DISCREPANCIES.md` "AIZ2 Battleship Post-Bombing Wrap Distance"
   entry (and its TOC line); add a CHANGELOG entry.

### Data flow (post-bombing forest loop, per frame)

```
AIZ event:  forestLoopActive = true, period = $200, origin = <verified>,
            camera/objects renormalize by ROM $200 at $46C0
        ->  BG source query normalized: origin + ((bgTilemapBaseX - origin) mod $200)
            (so a $200 jump in bgTilemapBaseX maps back to the same source column)
        ->  currentBgPeriodWidth = $200 -> cache + shader wrap at the loop window
        ->  rebuilt tilemap, base offset, and per-line deform stay coherent across the wrap
        ->  window re-samples identical forest tiles -> no seam; true ROM $200 wrap is seamless
```

## Testing

- **New tilemap/renderer-level seam guard** (`TestS3kAiz2…`, ROM-gated): a raw
  `getBlockAtPosition()` comparison is **insufficient** — that path wraps at the
  full BG layer width (`LevelManager.java:1997`) and does not exercise
  `bgTilemapBaseX`, `currentBgPeriodWidth`, the rebuilt tilemap cache, or the
  shader render offset, so it cannot prove cross-wrap coherence. Instead: activate
  the forest-loop state, drive the camera to just before the `$200` wrap, build
  the BG tilemap (`ensureBackgroundTilemapData` / `LevelTilemapManager`), capture
  the built tile columns (or a small rendered pixel strip) for the visible window;
  then step across the wrap, rebuild, and assert the visible sampled columns /
  strip are identical. This proves the window, period, rebuilt cache, and offset
  are coherent across the wrap — the actual defect surface.
- **Update `TestS3kAiz2SidekickBoundsSync`:** with the ROM `$200` wrap restored,
  the post-bombing camera `maxX` lands at the true ROM value again (`0x44C0` =
  `$46C0 - $200`, the value the test asserted before the `$80` approximation).
  Reinstate the exact-value assertion (the boundary-only relaxation introduced for
  the approximation is no longer needed) while keeping the sidekick-bound-mirror
  assertion.
- **Trace acceptance (not strict neutrality):** restoring `$200` changes
  camera/player/object coordinates relative to the current `$80` approximation, so
  a correct fix may legitimately *move* the AIZ first-mismatch frame — improving it
  or exposing the next real divergence. Acceptance criterion: **no new earlier
  mismatch than the baseline (currently f8941) unless explained by the intended
  ROM-faithful `$200` wrap change.** If the frontier moves (either direction),
  update `docs/TRACE_FRONTIER_LOG.md` with the new first-error frame/field and the
  explanation. Verify via the stash-baseline method.
- **Full suite:** `mvn clean test` green; ArchUnit unaffected (no new shared→game
  dependency — BG window selection already depends on zone state via the existing
  MGZ precedent path).

## Risks & Mitigations

- **Forest not `$200`-periodic in engine data (from a stable origin).** Mitigated
  by the Phase 1 go/no-go gate; we align to the verified period/origin or fall
  back rather than forcing `$200`.
- **`getBgCameraX()` / source-query derivation differs from the model.** Phase 1
  confirms the exact propagation across `bgTilemapBaseX`, `bgXQueryOffset`, and
  `currentBgPeriodWidth` before any change; if the window is already driven from a
  continuous source, the fix narrows to the source-query normalization only.
- **Applying the override during the non-looping forest handoff.** The override
  is gated strictly on the narrow `isBattleshipForestLoopActive()` predicate
  (post-`onBattleshipComplete`, `wrapX == $46C0`), explicitly **not** the wider
  `isBattleshipForestFrontPhaseActive()` display predicate; all other AIZ phases
  (intro, fire, forest handoff, normal) keep the existing
  `bgContiguousWidthPx`-wrapped source query untouched.
- **Trace frontier may move.** Expected and acceptable per the relaxed trace
  acceptance criterion above; documented in `TRACE_FRONTIER_LOG.md` if it does.
- **Scope creep into a general looping-BG framework.** Explicitly out of scope.
  Near-term reuse is low (AIZ2 is the only camera-wrap loop in the slice). The
  change is a second case of the existing `applyBackgroundTilemapWindowSelection`
  pattern, not a new abstraction.

## Out of Scope

- Replicating the ROM's exact Plane B nametable-fill bytes (that was Option 1;
  the chosen Option 2 preserves runtime behavior without byte-exact internals).
- A general reusable "seamless camera-wrap looping background" framework.
- Any change to the bombing-phase wrap (already ROM-correct at `$200`).
