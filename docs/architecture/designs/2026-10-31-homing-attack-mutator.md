# Homing Attack mutator (2026-10-31)

Mutator Lab's Homing Attack gives the leader a double-jump homing strike in Sonic 1,
Sonic 2 and Sonic 3 & Knuckles. Instagib is a second mode of the same mutator.
Branch `feature/ai-homing-attack`, based on develop `599d46762`.

## Agreed behaviour

The user agreed these rules before implementation:

- One mode selector (Homing / Instagib). Sonic, Tails and Knuckles each have a
  checkbox. Only the controlled leader uses the attack; CPU sidekicks keep their
  native moves.
- A jump re-press uses the native air-ability window. A live target outranks the
  native double-jump move. With no target, the native move runs (S3K insta-shield or
  shield moves, Tails' flight, Knuckles' glide). If no native move fires, the
  fallback is a forward air dash.
- Target order: hidden progress priority, then facing (targets ahead before targets
  behind), then kind (boss, badnik, breakable wall, grab point, breakable), then
  distance. Targets ahead are found up to 160 px away and targets behind up to
  112 px. The line to a target must clear solid terrain.
- Hits are native. The player stays in the jump roll, and native touch, solid and
  defeat code resolves each contact. A hit refreshes the air action so strikes
  chain, and bosses bounce the player off with a bounded rebound.
- Where native code distinguishes a grounded roll from an airborne one, a strike
  counts as a spindash: the S1 and S3K breakable walls and the S3K Death Egg Act 2
  spike cars. Knuckles-only walls and rocks are targets only for Knuckles.
- Grab points (AIZ ride vines, the CNZ cylinder top) are approached so that the
  native grab or landing takes the player.
- Instagib: a friction-fire trail, one-hit bosses through the native defeat
  sequence, and enemies blown apart with the strike's momentum, animals included.
  The trail is cosmetic.

## Implementation

- API: `MutatorCapability.HOMING_ATTACK`, `MutatorPolicy.HomingAttack`,
  `HomingMode` and `HomingCharacter`. The 0.7 candidate pin was regenerated; only
  these additions changed.
- `HomingAttackRuntime` (game.mutators) is owned by the host `MutatorWorldState`,
  so the attack works in all three games. It is reached through
  `PlayableMutatorPolicySource.homingAttack()` from movement and through the world
  service from the touch pass and objects. Its snapshot holds only plain values. The
  target is re-resolved every tick from live objects by its last aim point, so a
  restored history never holds a stale object reference.
- `HomingTargets.classify` uses the touch pass's own category decoding
  (`ObjectTouchResponseController.nativeCategory`). Every badnik and boss
  participates without registration. S3K Touch_Special `$C0` flags stay special,
  hurt-category objects and objects with zero flags are skipped, and an enemy with a
  nonzero collision-property byte is a boss body. `HomingTargetSource` handles the
  roles that touch flags cannot express.
- `InstagibBossTarget.collapseToFinalHit()` runs just before the native attack, so
  that attack becomes the defeating one. `AbstractBossInstance` and `AbstractBossChild`
  (which forwards to its parent) implement it. So do the S3K bosses with private hit
  counters: CNZ end, LBZ mini, LBZ final 1 and 2, and SOZ end.
- `HomingAttackEffects` draws the trail and debris from frozen tiles of the sprite
  table that the renderer is about to draw. That is S3K's published SAT, or S1/S2's
  live scene capture. It owns a presentation trail bank, takes no object slot, never
  feeds gameplay, and is cleared on restore and reload.

## Boss audit (hidden priorities and exclusions)

An Explore pass checked every engine boss against the disassemblies. Using the
native category decoding removed most of the listed exclusions. Because the
classifier never reads `headPresentationRole`, BOSS-role hazard and visual children
are not targets either. Explicit roles:

| Boss | Role |
| --- | --- |
| S3K DEZ2 `DezEndBossEnemy` | Ceiling car (routine 0) priority 2; floor car (routine 2) priority 1; falling, kicked and retiring cars are not targets. Only a kicked car damages the boss (sub_7FB4E). A strike takes the grounded kick/deflect branch instead of the airborne destroy/hurt branch |
| S3K DEZ2 `DezEndBossBumper` | Never a target ($D7 deflector) |
| S3K DEZ final `DezFinalCore` / `DezFinalMouth.Button` | Core priority 2 while its flags are live; button priority 1 (opens the mouth) |
| S2 `MTZBossOrb`, S3K `SszMtzBossOrbChild` | Never targets: intact orbs hurt and struck orbs only pop |
| S3K `SozMinibossChild` head hitbox | Priority 1 while vulnerable ($D7 Touch_Special head) |

Bosses with nothing to target, because native damage comes only from their own
projectiles or from a cylinder push: LRZ end, FBZ mini, DDZ end, and S1 FZ during
combat (its escape phase uses native flags). HPZ Robotnik ship and DEZ miniboss keep
native hit counts under Instagib.

## Rejected approaches

- Classifying by `headPresentationRole() == BOSS`. That role marks every boss child,
  most of them hazards or visuals.
- Classifying by raw `$C0` category bits. S2/S3K Touch_Special bumpers and orbs
  would become bosses.
- Consuming a re-press while the air action is spent. It blocked the S3K Super
  transformation press; a spent action now leaves the press to the native moves.
- Rendering victims and the leader during the logic tick to capture their pictures.
  Player DPLC uploads would run outside the render pass, and headless worlds would
  have produced different cosmetic state. The effects sample the frame the renderer
  already prepared.

## Validation

`TestHomingAttackMutator` covers eight cases: an S2 EHZ1 Buzzer kill and chain pop,
the S2 forward dash, Homing versus Instagib boss hit counts, the S3K AIZ1
insta-shield fallback and target precedence, rewind replay of a homing flight, the
ranking order, and policy composition. `TestModApiSignatureSurface` and
`TestExampleMutatorsPackage` cover the API and package. Per-zone breakable and grab
targets, the audited boss parts and the Instagib picture have no automated
route-level coverage.
