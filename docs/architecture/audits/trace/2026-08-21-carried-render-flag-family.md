# Survey: which objects publish `render_flags` bit 7 a camera step early

Surveyed at `b480ca4b8`, after
[the ARZ bubble generator fix](../../../status/trace-frontier-log.md) moved the
S2 ARZ2 segment frontier from row 1015 to 2175 by reading that flag in the ROM's
frame phase.

**Nothing landed.** Two ROM-cited conversions of `BubbleObjectInstance` were
built and both measurably regressed a fixture the ARZ2 segment cannot see. The
survey, the already-correct population, a stale-test verdict and a broken
helper contract are the round's output.

## The engine already owns this, and most objects already use it

`ObjectInstance.refreshPostCameraRenderState()` is the sanctioned publication
point: `LevelFrameStep` calls it at step 7, *after* the frame's camera step
(`LevelFrameStep.java:531-532`), which is where the ROM's `BuildSprites` sits --
after `RunObjects` and after `DeformBgLayer` publishes the camera copies
(`s2.asm:5094-5110, 15178-15179`). An object that instead assigns its flag from
inside `update()` is publishing at step 3, before that camera step, so its
verdict is taken against a camera one deform older than the one `BuildSprites`
would have used.

Classified by the *enclosing method* of each `= isWithinRenderSpriteBounds(...)`
assignment, not by field name -- the field is spelled at least eight different
ways (`romRenderOnScreen`, `romRenderFlag`, `renderOnScreen`,
`renderedOnPreviousFrame`, `drawnLastFrame`, `placeholderRenderedOnscreen`,
`waitPlaceholderRenderFlag`, `movementEnabled`), so any single-identifier survey
understates the population. A first pass of this survey reported "six classes"
from one field name; the real assignment set is 29 sites in 25 classes.

**Publishing in the post-camera hook (correct):** `GrounderRockProjectile`,
`GrounderWallInstance`, `OOZLauncherObjectInstance`, `RisingPillarObjectInstance`
(x2), `BreakableWallObjectInstance`, `CollapsingBridgeObjectInstance` (x2),
`IczFreezerObjectInstance`, `Sonic3kCollapsingPlatformObjectInstance`,
`BlastoidBadnikInstance`, `CorkeyBadnikInstance`, `Flybot767BadnikInstance`,
`JawzBadnikInstance`, `MantisBadnikInstance`, `MegaChopperBadnikInstance`,
`RibotBadnikInstance`, `SnaleBlasterBadnikInstance` -- plus
`Sonic1BubblesObjectInstance` and `LostRingObjectInstance`, which reach their
assignment through a helper the hook calls.

**Publishing from inside `update()` (candidates):**
`Sonic1CaterkillerBadnikInstance`, `Sonic1CaterkillerBodyInstance`,
`HCZWaterWallObjectInstance` (two sites), `SpikerDrillObjectInstance`,
`OrbinautBadnikInstance` (`movementEnabled`, semantics differ -- read the
[wake-phase survey](2026-08-21-s3k-wait-offscreen-wake-phase-survey.md) first),
and `BubbleObjectInstance`. `Sonic1BubblesObjectInstance` also calls its helper
from three points inside `update()` as well as from the hook, so it is mixed.

`BubbleGeneratorObjectInstance` evaluates its bounds at the top of `update()`
rather than in the hook. That is behaviourally identical for a stationary
object -- the object pass and the previous frame's post-camera step see the same
camera -- but it bypasses the shared owner and should be moved onto it as a
no-value-change refactor, not as a fix.

## The Obj1F reds are stale tests, not a family member

`CollapsingPlatformObjectInstance` was already converted, to
`isPreUpdateWithinRenderSpriteBounds`, with the same ROM citation this survey
derives independently (`s2.asm:5095-5111, 15178-15179`). The three reds in
`TestSonic2ObjectBugFixes`
(`collapsingPlatformFragmentFallUsesApproximateRenderHeight`,
`...KeepsVerticalOnlyOffscreenParentForCpuSlotRefresh`,
`...DeletesUsingFallingParentY`) are written against the pre-conversion
implementation: the third reflects on a field `verticalOnlyOffscreenTicks` that
`3f0fd4a70` removed, and errors with `NoSuchField` rather than failing an
assertion. Same lever, and the tests were left red beside the fix rather than
inverted with it. **Not inverted here.** Rewriting a red assertion so it passes
needs its own authority and its own verification of the new expectations against
the ROM; that is a commission, not a tidy-up.

## The blocker: `hasPreUpdateSnapshot()` does not mean "first frame"

Its javadoc says it is "False on an object's first frame", and callers modelling
`render_flags` rely on that to reproduce a setup row that seeds the bit SET.
Measured on `TestS2Arz2LevelSelectTraceReplay`: on a bubble's **first**
`update()` it is already `true`, for all 54 bubbles the segment creates, because
`snapshotPreUpdatePosition()` / `snapshotTouchResponseState()` run for
mid-frame-created children before their first pass. The seeded branch never
fires, so an object created off screen is destroyed on its creation frame
instead of surviving one execution. Any conversion onto that helper which
depends on the seeded value inherits this.

## What was measured, and why nothing landed

Control at `b480ca4b8`: `DebugS2Arz2Seg13CompleteEmeraldsSegmentTraceReplay`
9130 errors, first error frame 2175; `TestS2Arz2LevelSelectTraceReplay` passes
(surefire `tests="1" errors="0" failures="0"`).

| `BubbleObjectInstance` conversion | ARZ2 segment | ARZ2 level select |
|---|---|---|
| control (publishes inside `update()`) | 9130, first 2175 | pass |
| `isPreUpdateWithinRenderSpriteBounds` | 9130, first 2175 | **230 errors, first 670** `obj_s34_slot` 0x34 vs 0x33 |
| `refreshPostCameraRenderState` hook | 9130, first 2175 | **83 errors, first 1314** `obj_s2E_slot` 0x2E vs 0x11 |

Both are regressions and neither landed. Two things this pins down:

1. **The ARZ2 segment cannot measure this change.** A shadow probe carrying both
   evaluations found 28 rows where they disagree, so the path is live and the
   models genuinely differ -- and the segment's error list is byte-identical
   across the change, because it compares no field that bubble lifetime reaches.
   The level-select trace compares per-slot occupancy (`obj_sNN_slot`), which is
   exactly the field an earlier delete moves. Inertness measured on the fixture
   you happen to have is not inertness.
2. **A green fixture broke under a ROM-correct change**, which is the signature
   of a compensating pair rather than of a wrong citation. The control's
   early-published flag and something else are cancelling; the next round on this
   class should find the partner before republishing the flag, and remove both in
   one move.
