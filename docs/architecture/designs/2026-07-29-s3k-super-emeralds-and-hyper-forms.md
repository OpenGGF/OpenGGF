# S3K Super Emerald Sanctuary and Hyper Forms

## Status

Approved for implementation on `feature/ai-s3k-super-emeralds`, based on local
`next` at `beb7b64c1`.

## Requirements

### Goals

- Route the appropriate Sonic 3 & Knuckles giant rings to the Hidden Palace
  Super Emerald sanctuary.
- Reproduce the sanctuary's seven-emblem conversion, selection, Special Stage,
  result, re-entry, and exit flows from the locked-on ROM.
- Disable Chaos-Emerald Super Sonic and Super Knuckles at the ROM's actual
  conversion boundary.
- Unlock complete Hyper Sonic, Super Tails, and Hyper Knuckles behavior and
  presentation after all seven Super Emeralds are collected.
- Preserve progression through saves, death/restart, level transitions, and
  rewind.
- Load every runtime asset from the user-supplied ROM.

### Non-goals

- Reworking unrelated standard HPZ story-route events, bosses, or cutscenes.
- Generalizing all transformation controllers across S1, S2, and S3K.
- Changing ordinary Sonic 3 Chaos Emerald Special Stage progression.
- Hydrating gameplay state from traces.

### Constraints

- `docs/skdisasm/sonic3k.asm` is the behavioral source of truth.
- HPZ keeps canonical engine identity `Sonic3kZoneIds.ZONE_HPZ`, while its ROM
  resources resolve through the nonlinear `$1701` sublevel.
- Shared runtime code must not branch on game names, zone names, or trace routes.
- Objects use injected `ObjectServices`, native centre coordinates, standard
  lifecycle helpers, and complete rewind recreation.
- HPZ art, mappings, palettes, animation data, PLCs, layouts, chunks, blocks,
  and form-effect art are ROM-only.

### Acceptance criteria

1. Forced negative-subtype MHZ rings enter the sanctuary even with zero to six
   Chaos Emeralds. Other ring routes match `SSEntry_CheckLevel`.
2. Sanctuary emerald state preserves the ROM domain: `0=absent`, `1=Chaos`,
   `2=gray/selectable`, `3=Super/nonselectable`.
3. When the sanctuary intro completes and at least one state-1 emerald exists,
   `Emeralds_converted_flag` becomes true before the first `1 -> 2` animation.
   Starting MHZ or merely loading HPZ does not set it.
4. Selecting pedestal `N` launches Special Stage `N`. Success changes only
   pedestal `N` from 2 to 3; failure leaves it at 2. Both return to the Saved2
   Big Ring origin level, not to HPZ (corrected 2026-07-30 — see step 6 below).
5. The sanctuary exit restores the saved originating level, player, camera,
   ring, solidity, resize, water, starpost, and physical giant-ring state.
6. Chaos-only Sonic and Knuckles cannot transform after conversion. Seven
   Super Emeralds unlock Hyper Sonic/Knuckles; Tails requires seven Super
   Emeralds for Super Tails.
7. Hyper Sonic has the ROM air dash, flash, and trailing effects. Super Tails
   has four target-seeking Super Flickies. Hyper Knuckles has the ROM
   glide-impact earthquake/flash and wall-climb shake behavior.
8. Character-specific form art, animations, palettes, physics, audio, effects,
   cleanup, and rewind restoration are correct.
9. Existing S3K loading, AIZ-to-HCZ, special-stage, save, rewind, and trace
   guards remain green.

### Assumptions and risks

- The existing generic special-stage results pipeline currently double-marks a
  Chaos Emerald after the S3K manager has already awarded a Super Emerald. The
  reward owner must become explicit so HPZ progression cannot be corrupted.
- The existing `gotEmeralds[]` plus `gotSuperEmeralds[]` representation can
  express valid states, but a canonical S3K adapter must enforce the four-state
  invariant and serialize mixed partial-conversion progress without ambiguity.
- HPZ uses custom load behavior and nonlinear ROM resource indices. A naive
  append to the standard zone list would decode the wrong level.
- New effect/Flicky objects increase rewind and object-allocation surface.
  Allocation failure and cleanup behavior must follow the disassembly.

## Exploration Synthesis

### Existing engine support

- `GameStateManager` already stores Chaos Emeralds, Super Emeralds, and
  `emeraldsConverted`, including save and rewind snapshots.
- `Sonic3kSpecialStageManager` can award Super Emeralds, but currently decides
  conversion at Special Stage initialization, which is too late.
- `Sonic3kSuperStateController` already gates Chaos-only transformations on
  `!emeraldsConverted` and requires all Super Emeralds for Tails.
- `PlayableSpriteMovement` contains the basic Hyper Sonic directional dash.
- `Sonic3kSSEntryRingObjectInstance` identifies negative subtypes and has a
  disabled HPZ request path.
- HPZ object IDs are named but have no factories: `0x79`
  `SSZHPZTeleporter`, `0xB0` `HPZMasterEmerald`, `0xB1`
  `HPZPaletteControl`, `0xB4` `HPZSuperEmerald`, and `0xB5`
  `HPZSSEntryControl`.
- Palette owners and several HPZ PLC/art entries already exist.

### Disassembly findings

- `Emeralds_converted_flag` has four meaningful write sites: new-game clears,
  save decode, and the sanctuary controller at `loc_90AF2`. MHZ entry and giant
  ring entry never write it.
- The sanctuary controller sets the flag after its intro signal when its scan
  finds any state-1 emerald, before the sequential conversion choreography.
- Save encoding stores two bits per emerald. Decode counts every nonzero state
  as a Chaos Emerald, every state 3 as a Super Emerald, and restores conversion
  when any state is 2 or 3.
- The first MHZ rings use negative subtypes and always route to HPZ. Later rings
  use the S3-versus-S&K level and emerald-count routing in
  `Obj_SSEntryRing`.
- `Obj_HPZSuperEmerald` uses subtype 0-6 and the four-state array directly.
  State 2 is top-solid and selectable; state 3 is visible but inert.
- Sanctuary selection writes the exact subtype into
  `Current_special_stage`. Super Emerald success performs state `2 -> 3`.

### Conflicts resolved

- Lockout does not begin at MHZ start. It begins at the sanctuary controller's
  conversion-start event when at least one Chaos Emerald is present.
- Conversion belongs to the sanctuary controller, not special-stage startup.
- Exact stage selection must not use the ordinary rotating/skip-collected
  cursor.
- HPZ must be a typed S3K resource profile, not a shared-loader carve-out.

## Architecture Decision

### Ownership and boundaries

`S3kEmeraldProgression` is the S3K semantic adapter over session-persistent
emerald state. It owns four-state transitions and invariants but persists
through `GameStateManager` so save, rewind, and Mod API state remain centralized.
It exposes queries such as `state(index)`, `beginSanctuaryConversion()`,
`convert(index)`, and `awardSuper(index)`.

`S3kSanctuaryRuntimeState` owns transient HPZ flow: originating-level return
snapshot, selected stage, intro/conversion phase, completion signal, and
sanctuary re-entry position. It is registered with the runtime state and rewind
systems. It does not own durable emerald values.

The special-stage entry request gains a game-agnostic optional forced stage and
reward kind. Ordinary callers leave these absent. The S3K sanctuary supplies an
exact stage and `SUPER_EMERALD`; generic `GameLoop` consumes the contract
without knowing HPZ or S3K. The S3K special-stage manager remains the production
reward owner and publishes the result exactly once.

HPZ loading is described by an S3K-specific nonlinear level resource descriptor:
canonical zone `0x16`, ROM resource word `$1701`, SKL object set, HPZ layout,
boundaries, start/camera values, palettes, PLCs, and music. Shared level loading
consumes the descriptor without a zone check.

### Lifecycle and data flow

1. An entry ring evaluates ROM subtype, zone-set, and emerald-count rules.
2. Normal rings request a normal Special Stage. Eligible sanctuary rings call
   `Save_Level_Data2`-equivalent capture, mark their physical ring, and request
   HPZ `$1701`.
3. HPZ loads through its resource descriptor. `HPZSSEntryControl` creates the
   Master Emerald, teleporter, and the visible subset of seven pedestal objects.
4. After the intro signal, the controller scans states in ROM order
   `[5,3,1,0,2,4,6]`. If state 1 exists, it sets the conversion flag and runs
   the immediate seven-small-Emerald orbit ceremony in parallel with the
   signed `$21F` controller countdown. The countdown is not a blank delay:
   the orbit parent rises for `$80` updates, its seven children expand and
   rotate into place, and the completion sound plays once when all seven have
   arrived. The controller then runs the sequential `1 -> 2` drops, saving
   each completed change. After each falling crystal is allocated, the
   controller immediately starts its signed `$1F` countdown in parallel with
   that child; it does not wait for the crystal's landing animation to finish
   before preparing the next camera target.
5. Standing on a state-2 pedestal locks the primary player for the native delay,
   then publishes an exact-stage Super Emerald request.
6. Success awards state `2 -> 3`; failure does not mutate progression. The
   pedestal sets `Special_bonus_entry_flag = 1` (`loc_90926`), so
   `Load_Starpost_Settings` takes `loc_2D2C2` and the results screen returns the
   player to the Saved2 originating level — not to the sanctuary. The ROM
   rebuilds HPZ during `GameMode_SpecialStageResults` only as the backdrop for
   the conversion presentation; the sanctuary itself is re-entered through
   another Big Ring. (Corrected 2026-07-30; the original step routed the results
   exit back into the playable sanctuary hub.)
7. When no state-1 or state-2 emerald remains, the centre teleporter restores
   the originating level snapshot.

### Migration and rollback

Existing saves are decoded into the canonical four-state adapter. The explicit
`emeraldsConverted` field remains supported; inconsistent old payloads are
normalized conservatively. No save schema removal is required.

The work is additive behind HPZ routing and form-state contracts. Rolling back
the feature leaves existing save fields readable by the previous build.

## Feature Design

### Sanctuary presentation and objects

- `HPZSSEntryControl` owns intro sequencing, ROM art/PLC readiness, spawn order,
  conversion ordering, `Scroll_lock` camera pans, terminal return to camera
  X `$15A0`, player mapping/priority control, and exit enablement. It clears
  player control only after the final pan, never at the last pedestal midpoint.
  The fresh-entry lock also mirrors ROM player initialization by reverting an
  active powered form before normal-player ceremony mappings are assigned. Its
  one-shot initialization state is rewind-captured; after the controller
  advances to the exit routine, unlocking cannot re-enter initialization.
- `HPZSanctuarySmallEmeraldCeremony` owns the visible seven-Emerald orbit that
  runs during the `$21F` countdown, including its single Signpost and
  Super-Emerald sound events and the children flying away after arrival.
- `HPZSuperEmerald` owns one pedestal's ROM position, visual state, top solidity,
  fifteen-frame selection delay, and exact-stage publication.
- `SSZHPZTeleporter` uses HPZ mapping frame `$A`, palette line 0, priority
  `$180`, and the ROM slope table. The controller's centre exit checks the
  teleporter's real standing contact; there is no construction/readiness timer.
- `HPZMasterEmerald` renders frame `$B` on palette line 3 and owns the fixed
  incomplete-state green colors `$06A0/$0660`, plus completed-state
  glow/palette choreography.
- `HPZPaletteControl` swaps intro/main palettes at the ROM camera threshold via
  existing palette ownership.
- All dynamic children capture role, subtype, phase, timers, positions, and
  parent identity for rewind.

### Advanced forms

The form controller uses an explicit presentation tier:

- `NORMAL`
- `SUPER` for Chaos-Emerald Sonic/Knuckles before conversion
- `HYPER` for seven-Super-Emerald Sonic/Knuckles
- `SUPER_TAILS` for primary-player Tails with seven Super Emeralds

Eligibility remains character-specific. Presentation selection loads the
correct ROM art, mappings, animations, DPLCs, and palettes for the active
character and tier. Reversion restores the character's own normal resources.

Hyper Sonic's second airborne ability applies the ROM directional velocity,
camera delay, sound, full-screen flash, and trailing star/afterimage family.
Super Tails spawns four Super Flickies with their ROM orbit phases, nearest
eligible enemy acquisition, attack path, damage/contact behavior, and return
path. Hyper Knuckles augments the appropriate glide impact and climb contacts
with the ROM screen shake, flash, and enemy effects.

Effects exist only while their owning form is active. Ring exhaustion, death,
forced boss reversion, level unload, character replacement, and rewind remove
or reconstruct them deterministically.

### Edge cases

- Zero Chaos Emeralds: sanctuary intro runs, no conversion flag is set, no
  pedestal is selectable, and the exit is enabled.
- Mixed states: absent emeralds remain absent; only state 1 converts; state 2 is
  selectable; state 3 remains visible and inert.
- Re-entry does not replay conversion if no state 1 remains.
- Failure cannot advance a pedestal. Success cannot advance the wrong pedestal
  or award twice.
- Time attack keeps giant rings inert before any player/camera mutation.
- Sidekick Tails never independently becomes Super Tails.
- Allocation failure preserves the ROM's published prefix and retry behavior
  for form-owned object families.

## Acceptance Tests

- Ring routing matrix: negative/positive subtype, S3/S&K level classification,
  zero/seven Chaos Emeralds, zero/seven Super Emeralds, and time attack.
- HPZ descriptor: canonical `0x16` resolves `$1701`, SKL objects, correct
  resources, boundaries, start/camera, palettes, PLCs, and music.
- Conversion: `[0,1,1,0,1,0,0] -> [0,2,2,0,2,0,0]`, with the conversion flag
  asserted before the first state change.
- Pedestals: visual/solid/selectable state for all four values; every subtype
  launches the matching stage.
- Integration: MHZ ring -> HPZ -> pedestal N -> success/failure -> HPZ -> saved
  level exit.
- Save and rewind round trips for mixed states and each transition phase.
- Eligibility matrix for Sonic, Tails, and Knuckles before conversion, during
  partial Super progress, and with all Super Emeralds.
- Character-correct art/palette/animation restoration for every form.
- Hyper Sonic, Super Flicky, and Hyper Knuckles focused timing, collision,
  lifecycle, allocation-failure, and rewind tests.
- ROM art table-shape and corruption guards for every new mapping/PLC source.

## Verification

Focused tests run after each TDD slice. Integration concludes with:

```bash
mvn "-Dtest=TestSonic3kSSEntryRingFormation,TestSonic3kSuperEmeraldConversion,TestSonic3kSuperTransformationEligibility" test
mvn "-Dtest=TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard" test
mvn "-Dtest=TestGameStateManager,TestGameStateRewindSnapshot,TestRewindCoverageGuard,TestStaticStateRewindCoverageGuard" test
mvn test
mvn package
```

ROM-backed commands use the discovered S3K `.gen` path through
`-Ds3k.rom.path=...`.
