# Native-Tails Flappy Mod Design

## Goal

Rebuild `sample-flappy` as the first destination after starting Sonic 3&K. The mod
uses the real visible Tails player and native flight behavior instead of hiding the
player behind a controller-owned bird. It remains endless by recycling a stable pool
of approaching pipes inside a short, bounded level.

This design consumes the reusable S3K mod-zone adapter specified in
`2026-07-14-s3k-mod-zone-adapter-design.md`. The old sample's Sonic 2 ROM-art intake
lesson moves to the separate `2026-07-14-rom-art-remix-sample-design.md` contract.

## Confirmed behavior

- S3K is the base game.
- A fresh no-save or new-slot launch enters Flappy before any stock level.
- Tails is the sole main player for that launch; saved/configured teams are not
  rewritten.
- Tails begins in native flight automatically.
- Jump retains native Tails flight lift, animation, and audio.
- The controller refills native flight time every frame, preventing fatigue without
  changing `TailsFlightController`.
- Player left/right input is ignored.
- Tails holds a fixed horizontal position while obstacles approach at a fixed speed.
- Touching a pipe or leaving the visible playfield through its top or bottom causes
  unconditional death.
- Each pipe cycle adds one to the ring counter, which is presented as `SCORE`.
- The stock score row is hidden; time and lives remain.
- Restart begins a fresh run with score zero, a new deterministic generation counter,
  and flight active.

## Creator-facing engine surface

The redesign adds four generic, immutable, owner-tagged contributions. There is no
forward-movement framework, camera policy, endless-world/wrapping runtime, or
flight-fatigue rule.

### Data-select initial destination

`ModZoneContribution` gains an explicit game-start marker, but initial selection is
owned by `game.dataselect`, not `ZoneProgressionPlan`. A defaulted
`DataSelectHostProfile.newGameDestination()` returns the stock `(0,0)` destination.
The mod-decorated S3K profile resolves the winning enabled game-start contribution
from its zone registry.

Game-start selection is exclusive. Contributions are evaluated in frozen effective
owner order; the last enabled contribution wins and produces a deterministic finding
for every shadowed owner. Disabling the winner reveals the preceding contribution,
or the stock destination when none remains.

The marked Flappy zone is anchorless: `insertAfter == null` means it is addressable
by tagged identity but contributes no results-successor edge. S3K's empty
`StockProgressionAnchors` inventory remains empty; `aiz1` is not promoted into a
results boundary. Disabling Flappy restores the stock AIZ1 destination through
data-select fallback, not through a synthetic progression edge.

Today `DataSelectSessionController` emits `DataSelectAction` values whose
`DataSelectActionType.NO_SAVE_START` and genuinely empty-slot
`DataSelectActionType.NEW_SLOT_START` branches both hardcode `(0,0)`. The feature
therefore rewires those two controller branches to obtain
`hostProfile.newGameDestination()`; adding the defaulted profile method alone is not
sufficient. Existing `LOAD_SLOT` and `CLEAR_RESTART` branches, level select, time
attack, and explicit direct launches retain their requested destinations.
`ZoneProgressionPlan` remains a results-boundary successor plan and gains no prepend
mechanism. Disabling Flappy restores AIZ1 automatically.

### Session team selection

A launch-team contribution selects an owner-tagged `CharacterKey` for a matching
destination. Resolution happens before sprite bootstrap and produces a session-local
team. It does not mutate `config.yaml`, the data-select choice, or the save's durable
team. Flappy selects S3K Tails as player one and no sidekicks.

The runtime validates that the character exists and is playable for the resolved
module. Failure aborts launch with an owner-attributed diagnostic rather than
silently substituting Sonic.

### Input filter

A gameplay-scoped input filter receives effective player input and may suppress
controls but cannot mutate raw controller state. Flappy removes left and right and
passes jump unchanged. It is installed in `GameplayModeContext` and is absent from
ordinary S3K sessions.

The filter runs downstream of the live or recorded/replayed logical input snapshot
and upstream of movement's effective-button decoding. Live rewind records the raw
snapshot; trace replay and rewind re-simulation feed that same snapshot back through
the deterministic filter, so they reproduce the suppression without a second input
recording format.

Horizontal position is sample-owned, not a generic engine policy. The Flappy
controller captures Tails' initial centre X, zeros X speed and ground speed, and
restores that anchor each frame to remove any residual drift. Native vertical flight
continues normally.

### HUD presentation

An immutable HUD profile controls row visibility, label art, position, warning
behavior, and metric selection. It cannot change counters. Flappy hides the stock
score row, places the existing S3K `SCORE` label at the rings-row position, and
renders the ring counter as its value. Time and lives remain in their stock
locations. The low-rings warning is disabled because the counter no longer represents
collectible-ring health.

The ring counter is run-local Flappy score. There are no collectible ring placements,
and the stock score counter is neither incremented nor persisted for the minigame.
The controller updates the ring-backed metric with `LevelState.setRings` rather than
the collectible-ring `addRings` path, so crossing 100/200 points does not award a
stock extra life or interrupt flight with extra-life music.

## Short static level and fixed camera

The baked S3K level is a short static sky strip. Its only layout object is the
Flappy controller; `objects[]` contains no pipe placements and `rings[]` is empty.
Equal camera min/max bounds pin both axes at the initial viewport. Tails' horizontal
anchor prevents focus-driven movement, so the controller makes no forced-scroll or
camera-lead request.

The strict level format represents those values through the existing nested
`bounds` object; it does not invent `cameraMinX`, `visibleHeight`, or `game` root
keys. Because the parser requires `start` inside equal bounds, the controller's
first activation moves Tails to the configured screen-space anchor before arming
flight and boundary death, then captures that world X as the stable anchor.

The bounded-format problem therefore disappears: neither Tails nor the camera
approaches `maxX`, no world-coordinate rebasing occurs, and no wrapping runtime is
introduced. With a stationary camera, layout/dynamic object windowing cannot leave
the controller or pipes behind.

The v1 presentation trade-off is explicit: the background does not scroll. All
visible motion comes from the pipe pool. If play testing later finds the scene too
static, a ground or cloud strip may be added as an ordinary recycling mod object; it
will not become a scroll-framework dependency.

## Native flight without a new fatigue policy

On activation and restart, the controller calls the existing
`TailsFlightController.activate()` path. Every active frame it writes
`doubleJumpProperty = 0xF0` (240), the ROM-authored refill used by MGZ2 scripted
flight (`sonic3k.asm:26982-27106`) and already mirrored by
`SidekickCpuController.updateMgzBossTransitionCarryInput()`.

This preserves the native decrement, lift, state machine, animation, and audio while
preventing the property from reaching zero. Both `getTailsFlightController()` and
`setDoubleJumpProperty(byte)` are already present in the frozen Mod API 2.2 surface,
so the sample needs no ability policy, new rule record, or new accessor. Stock Tails
activation and fatigue remain unchanged outside Flappy.

## Dynamic recycling pipe pool

On its first ordinary gameplay update, the controller creates a fixed live pool of
namespaced pipe-pair objects with the creator-safe `spawnFreeChild(...)` helper. That
helper supplies construction services and adds each pipe as a free dynamic object;
it is never called from controller construction or `recreateForRewind(...)`. The
pool's compile-time size covers the engine's maximum supported viewport width
through `SUPER_32_9`, plus two lead positions; height remains pinned at 224. Resizing
therefore does not change object count or rewind state.

The pipes are independent `ObjectManager` dynamic entries, not layout placements or
children that the controller re-creates for adoption during its own rewind
reconstruction. Each captured entry carries the pipe class name, owning mod id,
slot, and stable `ObjectRefId`. Each pipe implements `RewindRecreatable`, and restore
resolves the mod-owned class through the installed `ModClassResolver` before
`recreateDynamicObject(...)` delegates to the generic
`RewindRecreateContext.dynamicEntry()` recreation path.

This is the first maintained gallery consumer of mod-owned independent dynamic
recreation; existing samples are layout-reconstructed and provide no proof for this
path. The policy/foundation delivery therefore adds a dedicated engine-level test
that loads a mod-owned class through its mod classloader, captures the dynamic entry,
restores it with the same stable identity and scalar state, and proves no duplicate
was adopted or spawned. The controller's `recreateForRewind(...)` never creates
pipes. It restores a non-final pool-initialized flag, keeps no direct pipe references,
and discovers its live owner/type-matched entries through the object manager; only a
fresh controller whose flag is false creates the pool.

After construction, pipe identity is stable. Each frame a pipe shifts left at the
sample's fixed subpixel speed. Once its right edge is left of the viewport, the
controller repositions that same live instance after the rightmost pipe plus fixed
spacing. Recycling never deletes or respawns a pipe.

Generation is deterministic without consuming shared RNG. The controller owns a
non-final integer `generationCounter`. Each recycle increments it and selects the gap
variant through a fixed lookup/permutation table indexed by
`floorMod(generationCounter, variantCount)`. A fresh run resets the counter to zero.

Each pipe owns non-final scalar state for centre X, gap variant, and
`gateConsumed`. The scoring condition is inverted from the old forward-moving model:
when an unconsumed pipe gate moves left past Tails' centre X, the controller adds one
ring and sets `gateConsumed`. Recycling selects the next variant and resets
`gateConsumed` to false, allowing exactly one score in the new cycle.

Pipes collide against Tails' real playable bounds. Pipe contact calls the existing
unconditional death path (`PlayableEntity.applyCrushDeath()`), independent of rings,
shields, invincibility, super state, or ordinary hurt rules. The controller applies
the same death when Tails reaches the visible bottom boundary or the native
top-flight clamp boundary at `cameraMinY + 0x10`. Normal death/restart lifecycle owns
lives and respawn.

## Rewind model

The minimal mutable state is:

- controller generation counter, run routine, horizontal anchor, and score-related
  coordination;
- each pipe's X position, gap variant, and gate-consumed flag; and
- the ordinary ring-backed level score and native Tails state already captured by
  engine managers.

These are non-final scalars handled by compact object snapshots. Independent dynamic
entries recreate through the generic path with their captured stable IDs, then
restore those scalars. Because live pipes are repositioned rather than replaced,
ordinary forward play does not churn rewind identity. Seeking before a crossing
restores `gateConsumed=false`; seeking after it restores true.

## Art ordering and palette correctness

The global `ArtConverter` row-major defect was fixed independently before this
redesign: multi-tile pieces now bake in the column-major order consumed by native
Genesis mappings, with a non-square marker regression. The Flappy rebuild consumes
that corrected toolchain and does not modify `SpritePieceRenderer`.

The previous black bird/life icon and recoloured HUD were separate palette bugs. An
S3K custom level loads the resolved Tails palette directly into host-owned line 0;
Flappy declares only the level-line entries used by sky and pipes. It ships no
character colours.

Initial/HUD palette composition is new adapter work, not an existing registry
feature. As specified by the S3K adapter design, creator declarations are validated
against host reservations and the current direct lives-palette override is replaced
for custom zones by host HUD claims submitted through `PaletteOwnershipRegistry`.
Headless verification does not claim framebuffer pixels: GL execution is a no-op in
that mode. Exact palette-register assertions cover Tails and the life icon; HUD tests
intercept `renderPatternWithId` piece calls for `SCORE`; decoded pattern-nibble and
mapping-piece assertions cover sky and rectangular pipes. A framebuffer capture
system is outside this revision.

## ROM-art gallery contract

Native Tails means Flappy no longer calls `registerRomObjectArt`. The API remains
supported and unit-tested, but gallery policy also requires an executable consumer.
The old Sonic 2 borrowing lesson therefore moves to a dedicated maintained
`sample-rom-art-remix` project and rewritten guide, specified in
`2026-07-14-rom-art-remix-sample-design.md`. Flappy does not retain a dead or hidden
ROM-art registration merely for coverage.

## Fault boundaries and compatibility

All four policy contributions register through `ModContext` transactions and use the
creator fault boundary. Required-policy failure aborts the Flappy launch; the engine
never starts a partial version with Sonic, horizontal control, or the wrong HUD.
Controller and pipe failures remain within ordinary object creator boundaries.

Asset geometry, palette ownership, character availability, policy conflicts, and API
version are validated before session publication. Gameplay-context teardown removes
every installed policy. Removing the mod restores ordinary S3K launch, controls,
camera, Tails fatigue, and HUD behavior.

The S3K adapter publishes Mod API 2.3.0. The four later creator-facing gameplay
contributions publish the additive 2.4.0 surface while leaving 2.3 frozen. The sample
declares `>=2.4.0 <3.0.0`. This work does not add a mod GUI,
general HUD editor, arbitrary physics scripting, endless-world runtime, stock S3K
event emulation, Flappy completion sequence, or persistent high scores.

## Verification

Test-first delivery covers:

1. data-select initial destination for no-save/new-slot and stock fallback when
   disabled, with no `ZoneProgressionPlan` prepend behavior;
2. session-local Tails selection without configuration/save mutation;
3. input filtering that suppresses left/right and preserves jump;
4. fixed Tails X, zero horizontal drift, pinned camera bounds, and no forced scroll;
5. automatic native flight plus per-frame `0xF0` refill, native lift/animation/audio,
   and unchanged stock fatigue outside Flappy;
6. HUD row visibility and ring-counter-to-`SCORE` mapping;
7. controller-only layout and stable independent dynamic-pipe construction;
8. counter-derived gap order, live-instance recycling, and gate reset per cycle;
9. exactly-once scoring for multiple cycles of the same pipe identity;
10. unconditional pipe, top-boundary, and bottom-boundary death;
11. engine-level mod-classloader recreation of an independent dynamic entry with its
    captured owner, stable identity, and scalar state, without controller duplication;
12. rewind restoration before/after recycling and scoring crossings;
13. corrected column-major pipe art and unchanged native ROM rendering;
14. host character/HUD palette composition and hostile palette rejection;
15. palette-register, HUD piece-call, and decoded pattern/mapping probes for Tails,
   the life icon, HUD, sky, and pipe pieces; and
16. sample packaging, zero-finding validation, API compatibility guards, and the
    relevant S3K/mod regression suites.

## Sequenced delivery

The program is deliberately split into independently testable plans:

1. column-major converter bugfix — already completed and verified;
2. S3K original-data mod-zone adapter and palette bridge;
3. game-start, launch-team, input-filter, and HUD-profile policy surfaces, including
   the mod-owned independent-dynamic rewind proof;
4. dedicated Sonic 2 ROM-art remix sample plus guide migration; and
5. native-Tails Flappy rebuild using the landed foundation and policies, removing
   the old consumer only after its replacement contract is green.

Delivery finally rebuilds both engine artifacts, builds `sample-flappy-mod.jar`
against the new SDK, validates it, and places it in `mods/` for manual play testing.
Generated artifacts are not committed.
