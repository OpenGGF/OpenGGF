# S3K Manual Super Transformation Design

## Goal

Match Sonic 3 & Knuckles' ROM behavior for Super/Hyper activation: transformation occurs from a second jump-button press during the airborne ability window, with character-specific emerald and shield rules, while Sonic 2 retains its automatic jump trigger. The resulting state must round-trip through gameplay rewind without replaying one-shot effects.

## Reference Behavior

The authoritative paths are `Sonic_ShieldMoves` / `Sonic_CheckTransform`, `Tails_Test_For_Flight`, and `Knux_Test_For_Glide` in `docs/skdisasm/sonic3k.asm`.

- Sonic tries transformation on the second A/B/C press after elemental shield abilities have been ruled out. Fire, Lightning, and Bubble Shields take priority; a basic S2-style shield does not block transformation. Invincibility suppresses the attempt.
- Tails transforms only when he is the main/solo playable character, has all seven Super Emeralds, and has at least 50 rings. Otherwise the press starts flight. CPU Tails does not transform.
- Knuckles transforms with all seven Chaos Emeralds (Super) or all seven Super Emeralds (Hyper) and at least 50 rings. Otherwise the press starts gliding.
- Tails and Knuckles do not run through Sonic's elemental-shield dispatch, so an elemental shield does not block their transformation.

## Runtime Design

`SuperStateController` will retain Sonic 2's existing automatic airborne activation as the default. It will expose narrow protected policy hooks for automatic-trigger participation and game-specific eligibility. `Sonic3kSuperStateController` will disable the automatic trigger and own S3K character/emerald eligibility. Shared movement code will not branch on game names or zones.

`PlayableSpriteMovement` will attempt the S3K controller's explicit air-ability activation before each character's fallback ability:

1. Sonic: only after determining that no elemental shield is active; a basic shield remains eligible.
2. Tails: before flight, and only for a non-CPU/main Tails.
3. Knuckles: before glide.

If eligibility fails, existing shield, flight, or glide behavior continues unchanged. The explicit activation entry point consumes the press only when transformation actually starts.

## Rewind Design

`SuperStateController` will expose a compact immutable rewind record for its transformation phase and ring-drain counter, plus the palette and transformation timing values shared by the S2 and S3K implementations. `PlayableSpriteController.RewindState` will include this record and restore it through the already-captured aggregate player controller state.

Restore must be state assignment, not activation replay: it must not play transformation audio, restart music, drain a ring, or spawn a duplicate effect. Player-owned flags, animation frame state, shield state, and jump-input latches remain owned by the existing `PlayerRewindExtra`. Controller restore synchronizes the internal phase/timers and presentation references needed for subsequent frames to continue deterministically.

No new uncaptured runtime latch will be introduced. The focused rewind round-trip test and the project rewind coverage guards will verify this contract.

## Test Design

Regression tests will cover:

- eligible S3K Sonic does not transform from controller `update()` alone;
- eligible Sonic transforms on the second press;
- elemental shields take priority while a BASIC shield does not block transformation;
- Tails requires all Super Emeralds and otherwise enters flight;
- CPU Tails does not transform;
- Knuckles transforms before glide when eligible and glides otherwise;
- Sonic 2 retains its automatic airborne trigger;
- controller phase and timing state survive a player rewind capture/restore round trip without firing activation side effects.

Focused Maven tests will run first, followed by rewind coverage guards and the relevant playable-sprite test set. A broader build will be run if the focused suite is clean.

## Scope

This change corrects activation timing, character eligibility, shield priority, and rewind continuity. It does not add missing Super/Hyper art, attacks, or unrelated character-form behavior, and it does not introduce zone-, route-, or frame-specific exceptions.
