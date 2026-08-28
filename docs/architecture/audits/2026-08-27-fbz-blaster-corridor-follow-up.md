# FBZ Blaster corridor controller follow-up

Date: 2026-08-27

## Point-in-time status

This investigation was deferred with no code committed, pushed, or integrated.
It began from final pre-restoration candidate
`e13114ad9013d9c15e10c9995a24769bb291b97d` and stayed entirely within
[`TestFbzAct2TraversalPreboss`](../../../src/test/java/com/openggf/game/sonic3k/objects/TestFbzAct2TraversalPreboss.java).
The production behavior in
[`BlasterBadnikInstance`](../../../src/main/java/com/openggf/game/sonic3k/objects/badniks/BlasterBadnikInstance.java)
matched the reviewed ROM evidence; the unresolved owner is the headless test
controller that drives the recorded route.

The regression is accepted for the general merge and remains follow-up work:
frozen-next commit `d3771e5d2344c7c917dac67f3c90c2072febd519`
reached viewport `[3]` frame 29532 at world X `$1B02`, while
`e13114ad9013d9c15e10c9995a24769bb291b97d` was hurt by the Blaster body at
frame 29143 at world X `$178F`. The user explicitly deferred the corridor fix
until after the general merge.

## ROM-state ownership model

`Obj_Blaster` patrol at `loc_894F2` in
`docs/skdisasm/sonic3k.asm` is the ordinary state that calls `MoveSprite2`.
Turn wait (`loc_89552` / `loc_8955A`), attack wait (`loc_8957A`), and attack
(`loc_895A4`) retain an `x_vel` value while the body is stationary. A body that
is actually moving away therefore requires all three semantic facts:

- routine state `PATROL`;
- positive `x_vel`; and
- right-facing state (`faceL=false`).

Velocity alone cannot own the decision. `BlasterBadnikInstance.traceDebugDetails()`
exposes the corresponding state, velocity, and facing values for diagnostics.
The body uses ENEMY collision `$0A` with `Touch_Sizes[$0A]` dimensions 16x8.
The primary child uses HURT `$98` / `Touch_Sizes[$18]` dimensions 4x4, and the
magnetic ball uses HURT `$9A` / `Touch_Sizes[$1A]` dimensions 12x12.

The successful prototype modeled four controller phases:

1. no Blaster-owned action;
2. pace a collision-enabled, away-moving `PATROL` body;
3. clear a blocking body; and
4. clear live HURT children.

Its inputs were limited to live collision-enabled objects, ROM touch geometry,
current braking distance, Blaster routine/velocity/facing, player
ground/air/rising state, and exact ceiling clearance. It used no viewport,
frame, route, or game-name identity; no hard-coded corridor bounds; no fixed
threat-lookahead distance; and no production changes.

## Exploratory patch disposition

The test-only prototype peaked at 381 insertions and 87 deletions: a 468-line
exploratory patch. It successfully demonstrated the semantic model, but it was
too large and diagnostic-heavy to land. The patch was discarded and must not
be resumed as-is. No part of it is present in the integration commit.

The focused invariant specified this ownership sequence:

1. an away-moving, collision-enabled `PATROL` body owns pacing rather than a
   jump;
2. the same body in a stationary or toward-moving phase owns the body-clear
   jump;
3. a live `$18` HURT child inherits ownership while P1 is airborne; and
4. ownership releases after P1 is grounded and the live hazards are clear.

Run evidence:

- `20260827T041215Z-p2-1da004`: valid `FAILED`, one test / one failure, with
  two grouped assertion failures. The away `PATROL` body incorrectly selected
  `CLEARING_BODY/JUMP_RIGHT`, and the projectile retained `CLEARING_BODY`
  instead of changing to `CLEARING_HURT`.
- `20260827T041409Z-p2-2ecd67`: valid `PASSED` for the transition model.
- `20260827T042159Z-p2-5dc123`: valid `PASSED` after live-controller
  integration.

## Viewport evidence

Baseline run `20260827T040504Z-p2-9d89a4` on exact `e13114ad9` was valid
`FAILED`: all five viewport rows reached their expected red frontiers.
Viewport `[3]` took Blaster-body hurt at frame 29143 with P1 at
`$178F/$05EC` and the nearby body at `$17A6/$05F1`.

Prototype run `20260827T042503Z-p2-7c7b1c` was also valid `FAILED` because the
matrix rows are frontier tests, not because the controller threw an error.
Viewport `[3]` survived the Blaster/ball corridor, reached maximum world X
`$1B2C`—past frozen-next's `$1B02`—activated trigger 7, and committed its
stage-3 handoff. Its later frontier was the existing stage-3 stall at frame
35292, with P1 at `$1AF5/$062C`.

The other viewport signatures retained their prior semantic milestones:

- `[1]`: Obj28 terrain-recovery timeout at `$1D6E/$076C`, frame 30600 rather
  than baseline frame 30610;
- `[2]`: death at frame 27356, `$06EF/$084A`, unchanged;
- `[4]`: death at frame 1455, `$0870/$026C`, unchanged; and
- `[5]`: death at frame 1291, `$0818/$026C`, unchanged.

## Follow-up boundary

A future implementation should preserve the focused ownership invariant first,
then shrink the four phases into one small nested controller. Route-loop
integration should remain one observation and one action-to-mask switch. Reuse
one collision-box/stopping-envelope helper, parse only the public Blaster debug
fields required for `PATROL + x_vel>0 + faceL=false`, and derive jump clearance
from the live touch-box top and P1 touch bottom. Do not restore corridor gates
such as `$16F0..$18D0`, `$70`, or `$A0`, and do not retain exploratory
transition history beyond compact phase/action/live-object diagnostics.

Before landing follow-up code, rerun the invariant, all viewport rows,
donor/team rows, the full compatibility matrix, and the focused controller
class. The current audit is evidence for a viable ROM-state model only; it does
not claim the deferred corridor controller is implemented.
