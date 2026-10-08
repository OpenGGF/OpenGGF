# Flappy Tails: polished example and multi-part tutorial

Task date 2026-10-08. Branch `feature/ai-flappy-tails`, integration base
`d740b7a0f` (`origin/develop`).

## Request and decision

The request: bring Flappy Tails to the polish of the other example mods, and make an
over-the-top promo for it. Flappy Tails existed only as the minimal native gallery sample
(`sample-flappy`, 340 lines), whose July design deliberately used the engine's native Tails
player. A native S3K patch cannot reach a polished look: S3K mod zones may not inherit a stock
zone's background, parallax or events, and `registerRomObjectArt` is Sonic 2 only.

The user chose a tutorial that branches early: part 1 asks "native game or a complete scene?",
the native road demonstrates the existing sample as it is, and the scene road is developed into
the polished game and is the session goal. So:

- `sample-flappy` and its build-along are unchanged; the tutorial's Path A page runs and tours it.
- `examples/flappy-tails` is a new S3K mod scene, the scene road's finished game.
- `docs/modding/guides/flappy-tails/` is the nine-part tutorial; parts 2 to 5 have buildable
  checkpoints under `examples/flappy-tails/tutorial/`, whose shared classes must equal the
  finished example's (`TestFlappyTailsTutorial`).

## What landed

- Tails' flight is a line-for-line port of `Tails_Move_FlySwim` (with `Tails_Test_For_Flight`'s
  starting values and `Tails_Set_Flying_Animation`'s animation and sound cadence), in the ROM's
  8.8 units. Hit box from `Touch_NoInstaShield`; hurt from `HurtCharacter`; death hop from
  `Kill_Character`; blink from `Tails_Display`; ring scatter from `Obj_Bouncing_Ring`; tails
  rotor frames from `Obj_Tails_Tail_AniSelection` / `AniTails_Tail`.
- Pillars and ground are cut from each pictured act's floor with `levelStages` and
  `levelForeground`; no per-zone art. Ground cuts are curated by eye (waterfalls).
- The five pictured S3K acts form the tour; title cards play on the documented `Obj_TitleCard`
  timing; `CardFont` recovers an alphabet (all but J, Q, W, X, Z) from the zones' name cards.
- `Run` decides and reports events; `FlappyScene` presents. An `Autopilot` plans on copies of
  the real `Flight` and drives tests, attract mode and captures.

## Tried and rejected

| Approach | Evidence | Outcome |
| --- | --- | --- |
| Autopilot aiming at each gap's centre | 29 of 40 seeds crashed before 100 gates (probe, pre-commit) | Aim for the next gap as far as the coming gap allows; 0/100 crashes |
| Single-step "press now or not" planning | Equal crash costs chose pressing; Tails pinned to the ceiling (39/40 crashed) | Plan the next flap's timing (waits 0-62); ties keep waiting |
| Drop limit larger than climb limit (falling is "easy") | Remaining crashes all on drops after a floor-saving flap (seed traces) | One symmetric limit, 2/5 of the frames between gates, at most 40 px |
| `static final int[]` title-card tables | Mod validator rejects static arrays | `switch` methods |
| Splitting card names only at gap columns | MARBLE's M and A touch; M splits in two; no M recovered | Width-aware assignment using letters known from earlier names |
| Title logo as a full title card | Banner covered Tails' flight line; ZONE collided with the menu | Logo composed from card pieces; menu moved right |
| Super Tails banner at the card's height | Read "SUPER TAILS ZONE" over the next title card | Banner moved up and shortened |

## Validation

Recorded at delivery (see below).

## Promo

Recorded at delivery (see below).
