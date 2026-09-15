# Sandopolis Zone: methodology v2 application plan

Date: 2026-09-15. Status: planned; implementation and native certification have
not begun under this plan. This delivery documents the agreed method and target.

## Objective and authority

Apply [methodology v2](../designs/2026-09-15-zone-methodology-v2.md) to locked-on
S3K Sandopolis Acts 1 and 2: ordinary entry and traversal, all reachable mechanics
and character branches, bosses, Act 1-to-2 lifecycle and the outgoing LRZ route.
Verify the exact outgoing destination/dispatch against ROM before implementation.
Use existing runtime support where correct; this is not a direction to rewrite it.

The [SOZ catalogue](../research/s3k-zones/soz-analysis.md) is the starting research,
not verified current coverage. The planning base is develop `44f8503b3886d99dc5b64b29b59fc7b67f47d793`.
No implementation or trace baseline was measured for this documentation change.
Pin the actual updated integration commit when execution begins.

## First execution slice: inventory and native pilot

1. Inspect production zone, event, object, scroll, palette, animation, PLC and
   transition registrations, existing tests and current discrepancy/frontier
   records. Decode both acts' actual placements, including used subtypes and
   reachable children. Separate absent factories from implemented route gaps.
2. Reverify source labels and branches in the existing analysis with the S3K
   disassembly skill. In particular, reconcile its Act 1 transition description
   of “no title card” with its Act 2 entry description of spawning `Obj_TitleCard`
   mid-fade. Determine the actual title/control lifecycle before writing an oracle.
3. Establish the native capture path with one small Act 2 light-switch/darkness
   sequence. Verify ordinary initialization, the owning clocks, fade direction,
   palette, torch presentation and relevant ghost state. Demonstrate that the
   setup can be reproduced before committing to a large capture tool.
4. Create both SOZ act matrices under `docs/architecture/validation/levels/` and
   link them from the coverage backlog. Enumerate supported character routes,
   checkpoint sets, width/donor/team obligations and rewind boundaries. Mark
   inherited gaps and unmeasured results explicitly; do not seed passing statuses.
5. Record ROM/disassembly identity, current implementation findings, exact pilot
   recipes and baseline evidence here. No trace sweep is needed merely to justify
   the user-selected target.

## Dependency-ordered route slices

The labels below come from the catalogue and must be checked against the current
locked-on source. They identify research owners, not fitted runtime constants.
Each row includes ordinary local traversal, a short independent native comparison,
representative compatibility, rewind/replay and review of the coupled boundary.

| Slice | Scope and owning reference leads | Early independent check and principal risk |
| --- | --- | --- |
| 1. Act 1 entry and desert traversal | `SOZ1_ScreenEvent`, `SOZ1_BackgroundEvent`, `AnPal_SOZ1`, `AnimateTiles_SOZ1`; placed quicksand variants, rocks, vines, platforms and badniks | Camera-driven background/animation at entry; quicksand approach, sink and escape in adjacent phases. Verify movement donor feasibility and actual production object bindings |
| 2. Act 1 arena and miniboss | `sub_55E96`, `sub_55D94`, `Obj_SOZMiniboss`; sand rise, delayed redraw/art handoff, door children | Trigger before/at/after lock, art readiness and spawn order; ordinary boss interaction through defeat and door opening; failed child allocation and rewind |
| 3. Seamless Act 2 entry | `SOZ1_BackgroundEvent`, `sub_55EFC`, `SOZ2_ScreenInit`, `SOZ2_BackgroundEvent` | Paired-player door admission versus solo/extra followers; fade, queue readiness, reload, wrap setup, title lifecycle and control release. Compare the entire short transition, not just its destination |
| 4. Act 2 darkness and traversal | `AnPal_SOZ2`, `AnimateTiles_SOZ2`, `Obj_SOZLightSwitch`, Hyudoro/capsule routines; doors, switches, wires, sand corks | Light switch immediately before/at/after a darkness step and during brightening; ghost release/character/checkpoint conditions, torch/palette agreement and held-player ownership |
| 5. Rising sand and pyramid changes | `SOZ2_ScreenEvent`, `SOZ2_BackgroundEvent`, reached `SpecialEvents` sand routine, `sub_5699A` | Moving sand, camera/background collision and terrain edits in the same frame sequence; cross the Act 2 vertical wrap with players, camera, objects and rendering; reverse approach and rewind |
| 6. Act 2 boss and exit | `sub_56706`, `sub_56A12`, `Obj_SOZEndBoss`, reached defeat/capsule/transition routines | Wall collapse/reconstruction, solids, boss hit/defeat and darkness/animation shutdown; ordinary completion into the next route, cleanup and reset isolation |

Shared state and phase contracts precede dependent objects. The Act 2 native pilot
is early research; it does not replace the dependency order or claim traversal.
Catalogue quicksand as object behavior, not water physics, unless source review
disproves that distinction. `No_Resize` is not evidence that SOZ has no events.

## Acceptance and validation

- Pin short comparison checkpoints before candidate rendering, including act
  starts, sand rise, door/transition, darkening/brightening, ghost activation,
  wrap crossing, boss wall and final exit. Declare input/setup and compared fields.
- Run native standard-width plus a wider viewport, S1 donor and extra-follower
  case at each mandatory mechanic. Expand phase, width, donor and participant
  combinations where the mechanic is sensitive; final matrices retain the full
  level-standard obligations, including S2 donation where supported.
- Exercise every checkpoint's production death/reload, repeated entry/reset,
  object/event reconstruction and forward replay. Establish whether each load
  preserves or resets the rewind timeline through its real production owner.
- Add short independently runnable boss/event tests. Cold full-act routes must
  prove access to those encounters with ordinary inputs for materially different
  character routes; setup teleports, state writes and forced hits are not traversal.
- Once both routes are broadly implemented, run applicable full-route V5 replay
  and complete native/visual coverage. Inspect skips and attribute inherited trace
  failures by identity and first differing field; a green local route does not
  override a red strict comparison.
- Execute mandatory S3K loading/bootstrap/AIZ checks, affected shared regressions,
  required structural guards and repository delivery validation. Select exact
  commands/test identities from the implemented changes, not speculative names.

## Execution record and next action

Next action is the inventory and native pilot above, followed by the first missing
Act 1 route slice. Keep commands, RED/GREEN results, review findings, resolved
catalogue contradictions, amendments and rejected approaches in this plan as work
proceeds. Reuse the existing SOZ analysis for verified ROM findings and the act
matrices for acceptance evidence. No new receipt or log format is required.

Documentation validation for this initial plan checks local links, whitespace,
policy trailers and behavioral scenarios: blocked native capture must remain
unaccepted; seeded boss evidence must not become a cold-route pass; compatibility
failure must expand the affected checks; a title-lifecycle catalogue contradiction
must be resolved from ROM before implementation. Engine behavior is unchanged.
