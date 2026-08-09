# S3K AIZ Miniboss Napalm Evidence Design

Date: 2026-08-09

## Goal

Close the evidence gap around the Knuckles-only AIZ miniboss FallingShot route
without using trace rows as runtime input and without changing gameplay unless a
production-route test proves a defect.

The implementation under review is the native object graph rooted at
`Obj_AIZMiniboss`: the boss sets parent bit 1, three existing flame-barrel
children consume it with subtype delays, each barrel allocates a flare followed
by a FallingShot, and each floor impact allocates seven staggered
`BossExplosionHitbox` children.

## Sources of truth

The shipped-ROM path (`FixBugs = 0`) is defined by these
`docs/skdisasm/sonic3k.asm` routines and tables:

- `AIZMiniboss_SetFlameDelay` and `AIZMiniboss_StartFlameAttack`
  (`137296-137311`): Knuckles sets parent bit 1 at the start of the 30-frame
  flame delay.
- `AIZMiniboss_LockCameraAndFade`, `AIZMiniboss_StartDropMusic`, and
  `AIZMiniboss_PrepareShortSwing` (`136779-136812`, `137290-137302`): the
  literal `Obj_Wait` counters `#180`, `#$AF`, and `#20` pre-decrement through
  zero, so their following-routine wait bodies dispatch 181, 176, and 21 times
  before the respective callbacks. These literal/callback semantics, rather
  than labels such as "180-frame wait", define the production-route oracle.
- `AIZMiniboss_BarrelController` (`137401-137448`): existing barrel subtypes
  0, 2, and 4 latch the shared bit, wait 0, 16, and 32 entries, and allocate the
  flare/FallingShot pair with `AllocateObjectAfterCurrent`.
- `AIZMiniboss_FallingShot` (`137451-137535`): `$98` collision, `$60` rise,
  8-entry pause, camera-relative drop tables, per-barrel `$39` advance, `$400`
  fall, `ObjHitFloor_DoRoutine`, and seven after-current explosion allocations.
- `BossExplosionHitbox` and `ChildObjDat_690D8` (`137537-137581`,
  `137910-137925`): subtype delays 0 through 12, seven fixed offsets, `$97`
  collision only while the animation routine publishes `Draw_And_Touch`.

The comparison-only native capture is
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/aiz_3`.
Its metadata identifies Knuckles, AIZ act 2, BK2 offset 20647, and native
BizHawk headless recorder 3.0. Relevant independent observations are:

- barrel slots 15, 17, and 20 carry subtypes 0, 2, and 4;
- flare/FallingShot pairs appear at frames 4486, 4502, and 4518, preserving
  flare-before-shot and the 16-entry subtype spacing;
- the first impact at frame 4647 is `(0x10E4,0x0506)` and allocates seven
  explosion children after the projectile slot, skipping occupied slots;
- the second impact at frame 4694 is `(0x1134,0x0504)` and allocates subtypes
  0, 2, 4, 6, 8, 10, and 12 in ascending after-current slot order;
- the second explosion set enters routine 4 four entries apart by subtype and
  its subtype-12 through subtype-0 members remain present through frames 4716,
  4720, 4724, 4728, 4732, 4736, and 4740 respectively.

These values are test or documentation oracles only. No parser, fixture path,
frame number, zone identifier, or movie identifier enters production behavior.

## Production-route test

Add one ROM-backed test class that loads AIZ2 (zone AIZ, zero-based act index
1) through `SharedLevel`, thereby installing the real ROM-decoded collision layout used by
`ObjectTerrainUtils`. It then creates an isolated S3K `ObjectManager` with the
real `AizMinibossInstance` in a real dynamic SST slot, a fixed camera matching
the native arena, and an `AizZoneRuntimeState` selecting Knuckles. The manager,
boss state machine, barrel children, allocation path, projectile motion, and
terrain checks are all production code. Isolation removes unrelated route
objects and player damage without substituting for any state owned by the
napalm graph.

The test observes, but never writes, the following behavior:

1. The parent activation bit changes only after the real trigger and the
   exact following-routine dispatch counts implied by the ROM literals:
   `#180 -> 181`, `#$AF -> 176`, and `#20 -> 21`. The test observes each routine
   transition and proves the activation bit is absent on the preceding entry.
2. The three production barrels create one flare/FallingShot pair each at
   activation-relative entries 32, 48, and 64. Each flare slot precedes its
   projectile slot and both are allocated after that barrel's slot. The native
   capture's absolute spawn frames 4486, 4502, and 4518 remain independent
   comparison evidence, not a production clock input.
3. Every live FallingShot publishes `$98` and current/post-movement touch state.
4. Real AIZ2 terrain terminates the falling shots. The captured impact
   coordinates are explicitly fixed-fixture comparison oracles; terrain
   collision itself is proved by the independently loaded ROM layout and is
   never tuned to make those coordinates appear. Seven-child offsets are ROM
   literals, and each child is allocated after the projectile while respecting
   occupied slots.
5. Explosion `$97` windows begin in subtype-stagger order and last for the
   `AniRaw_BossExplosion` duration. Pre-animation children remain non-harmful.
6. Rewind proves graph identity as well as scalar evolution. One manager
   snapshot is captured after all three production FallingShots exist but
   before they consume their source barrels' `$39` position counters and
   facing. For every captured boss, barrel, flare, and FallingShot identity, the
   restored reference must resolve to the same restored object identity, not
   merely the geometrically nearest of the three barrels. Per-barrel counters
   and each linked projectile's subsequent position selection are compared
   after deterministic forward execution, restore, and re-execution. A second
   live snapshot covers the mixed projectile/explosion phase and compares type,
   slot, position, subtype, collision, and destruction evolution.

A companion Sonic-alone scenario drives the same real boss state machine past
the attack window and proves that neither the parent activation bit nor a
FallingShot appears. This replaces reflection-only gate evidence with a live
negative route.

## Change boundary

If these tests pass on the current implementation, production Java remains
unchanged. The work then consists of durable evidence and correcting status
claims. If a test fails, the failure must be reduced to a disassembly-owned
cause before changing production code; the failing assertion remains red until
that fix is applied. In particular, if the exact graph test proves that a
`nearestLiveObject` reconstruction can adopt the wrong barrel, the permitted
fix is to preserve the structural object reference by rewind identity (using
the existing compact-schema object-reference path or an equally narrow
two-phase relink), never to add a distance/route/frame heuristic.

No trace fixture is regenerated. No full Knuckles complete-run replay is
claimed: the repository currently has no chain test for this 68-segment run.
The native capture proves the ROM route, while the ROM-terrain production test
proves the implemented graph against the fixed oracle.

## Documentation disposition

Update `docs/status/s3k-known-bugs.md` from OPEN to resolved evidence, naming
`aiz_3` (not the AIZ1 cutscene-oriented `aiz_2`) as the miniboss capture and
retaining the explicit absence of a full run-chain parity claim. Update the
unfinished-code validation report and changelog so neither says route evidence
is still missing.

## Implementation outcome

The live boss, barrel allocation, ROM-terrain impact, and explosion-lifetime
slices passed without a gameplay timing change. The rewind slice was observed
red: after forced recreation, a FallingShot's structural `barrel` edge resolved
to another member of the three-barrel graph. The fix leaves
`AizMinibossRewindLinks` as a phase-one construction seed and makes the
projectile `parent`/`barrel` plus flare `anchor` fields phase-two captured
references through `DefaultObjectRewindPolicies`. Restored edges are asserted
by `ObjectRefId` and `assertSame` before replay, then per-barrel counters and
projectile/explosion evolution are compared forward.

No route-, frame-, zone-, or distance-based production branch was added. No
trace was regenerated, and this work does not move or claim a complete-run
frontier.
