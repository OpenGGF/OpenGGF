# Putt Putt Paradise implementation plan

Subtitle: **Sonic 2 Mini Golf**

Date: 2026-10-05

Status: locally delivered with focused acceptance verified and inherited suite failures recorded; retained branch/worktree, no merge or push

Spec: [Putt Putt Paradise design](../designs/2026-10-05-sonic-mini-golf.md)

Research checkout: `ac120c5f277d50c9964ffda68224d67095d072e4`, local
`feature/ai-putt-putt-paradise`, based on `develop` at `fc4729c375`.

**Goal:** deliver a JVM code mod in which Sonic or Tails is putted/chipped through
full Emerald Hill acts 1 and 2, with practice, alternating local two-player
competition, and host-authoritative IP/port play.

**Architecture:** one live ROM-backed course, with an independent saved course
for each golfer. A mod-owned state machine runs aiming and meters on a separate
presentation tick while the course is held. Bounded engine capabilities supply
course checkpoints, coherent launches, contact observations and read-only views.
The host simulates every online shot; guests render presentation values using
their own ROM assets. Neither networking nor survey rendering owns gameplay.

**Tech stack:** Java 21, the mutable Mod API 0.7.0 candidate, existing S2 loading,
physics, rewind, audio and rendering; JDK framed TCP for the external mod's
direct transport; Jupiter and the queued Maven/testing tools for verification.

**Execution:** carry out the tasks in dependency order using the applicable
implementation skill. This document does not start implementation. Keep this
plan and subsequent concept changes on the retained local branch/worktree;
do not merge, push, or remove them without a later user instruction changing
that flow. Pin the actual pre-implementation base before executable edits.

Run the Task 3a rendering feasibility spike after Task 3 and before Tasks 4–5
commit to the presentation API. Task 8 then completes that proven path for guests.

## Constraints and review focus

- Sonic 2 World REV01 supplies all runtime art, terrain, collision and sound.
  Do not package ROM bytes, use disassembly assets at runtime, or create ROM
  links for tests. Discover an existing absolute ROM path and verify its identity.
- Preserve native S2 spindash charge/release sounds and the sound driver's
  innate pitch ladder. Golf adds charge timing and launch power, not a new
  pitch curve, playback-rate multiplier, or audio-driver behavior.
- Up/Down adjusts elevation continuously from putt to increasingly steep chip;
  Left/Right sets direction. First A ducks and starts the meter; two subsequent
  timed A presses build power. The second charge commits one stroke, followed
  by feedback, a brief pause and automatic release. No additional launch press.
- Keep the character rolling through flight, springs, landings and low-speed
  movement until valid settlement. No steering, jumping, flying or sidekick AI.
- A damaging contact, pit or declared lost ball restores the entire pre-shot
  course and costs the committed stroke plus one penalty. It ends the turn.
  Course rollback must not roll back the match ledger.
- Crossing a swept finish gate completes a hole at any speed. Remove the EHZ2
  boss/capsule dependency, arena locks and boss music, as well as stock results
  and progression. Keep both full acts.
- Implement semantic shared contracts through injected, session-owned services
  and ModContext transactions/fault boundaries. No golf-name checks in shared
  runtime code, new gameplay logic in `Engine.java`, or mod access to internals
  through singleton shortcuts.
- Prioritize adversarial state transitions: character/roster restore, snapshot
  aliasing, frozen V-int and PLC clocks, support-relative settlement, exactly-once
  shots, stale network frames and complete guest course visibility.

## What exists and what needs adding

Source paths below are relative to `src/main/java/com/openggf/` unless a
repository prefix is shown. Proposed interfaces and files are implementation
work, not claims about the current API.

| Existing seam | Evidence and limitation | Planned use |
| --- | --- | --- |
| `game/patch/GamePatch`, `DelegatingGameModule` | Infinite Sonic is an external ROM-game decorator | Register an S2-only mod and scope all overrides to its active session |
| `GameplayPolicyProvider`, `GameplayInputFilter` | `LevelManager.installGameplayInputFilter()` resolves the destination policy; creator contributions are destination/owner constrained | Install a tested EHZ patch filter through the module; do not assume tagged additive-zone registration applies |
| `GameModule.gameplayStepsPerFrame()` | Interactive budget clamps to 1..32 | Add a distinct hold admission, rather than returning zero |
| `LevelFrameStep`, `LevelIterationAdmissionController` | Native pause retains V-int/service activity; interactive and headless paths share the canonical step sequence | Add a hold before any authoritative clock or service admission; preserve native pause semantics |
| `GameplayModeContext` / `RewindRegistry` | Registered subsystem capture/restore and post-restore reconciliation exist | Build scoped, validated in-memory course checkpoints on these adapters |
| `SpriteManager.rewindSnapshottable()` | Restores state into sprites by code; missing main characters are not generally recreated | Save roster identity and recreate/rebind the correct character before applying course state |
| `PlayableSpriteMovement` / `NativePositionOps` | Native spindash and radius/contact transitions exist; low-level setters alone are insufficient | Add a coherent launch/pose capability and sustained shot rolling policy |
| `HudProfile`, `TitleScreenProvider` | HUD profile is row-oriented; title provider has update/draw hooks | Reuse title lifecycle, add a bounded overlay for meters/arc/score and text entry where needed |
| `SpritePieceRenderer`, `SpriteSatEntry`, `GraphicsManager` | Rendering resolves sprite pieces, virtual patterns and priority; these are not a wire schema | Project stable ROM-art references and immutable scene values before GPU submission |
| `RaceHostServer` / `com.openggf.net.*` | Direct WebSocket racing exists, with an engine-free protocol boundary | Keep golf transport/protocol in the mod; do not extend race ghosts into gameplay snapshots |

The first implementation milestone must prove hold, checkpoint isolation and a
useful launched shot with loaded EHZ data. The online milestone must prove
object-complete guest rendering before claiming online play. A failed proof is
a concrete blocker to record in the design, not a reason to quietly cut scope.

## Proposed shared capability contracts

Use the following names and ownership boundaries unless inspection finds an
equivalent supported contract. Put public types under `com.openggf.game.mode`,
`com.openggf.game.rewind` and `com.openggf.game.presentation`; keep implementations
inside the owning managers/session. Annotate every recursively exposed engine
type with `@ModApi`. Default stock behavior remains one ordinary simulation step.

| Proposed contract | Shape and responsibility |
| --- | --- |
| `GameplayModeExtension` | `presentationTick(ModePresentationTick)`, `admission()` returning `RUN` or `HOLD`, `afterCommittedStep(ModeStepObservation)`, `drawOverlay(ModeOverlayCanvas)`, `close()`; one instance per resolved world session |
| `GameModule.createGameplayModeExtension(ModeServices)` | Default no-op; forward through `DelegatingGameModule`; composition root supplies bounded services and wraps creator callbacks in the existing fault boundary |
| `ModePresentationTick` | Logical input edges/held directions, recorded mode commands and a mode-iteration ordinal; no level/V-int clock mutation. Live recording, replay and offline capture use the same coordinator |
| `ModeServices` | Checkpoints, playable launch/pose commands, read-only scene capture/presentation and scoped progression policy; no unrestricted engine/renderer object access |
| `CourseCheckpointService` | `capture()` returns opaque `CourseCheckpoint`; `restore(checkpoint)` runs only at a committed boundary; validates session generation, level identity and complete adapter layout before mutation |
| `GameModule.courseRewindAdapters()` | Default empty; module-owned per-course adapters join engine course capture. Existing `rewindAdapters()` contains mode/match state; ordinary debug rewind captures both domains |
| `PlayableLaunchControl` | `setAimPose(direction, DUCK/NEUTRAL)`, `beginCharge()`, `addCharge()`, `release(LaunchVector, ShotMotionPolicy)` and `settle()`; engine owns coherent radii, animation, sensors, fractions, velocity and attachment transitions |
| `ModeStepObservation` | Previous/current native centre, velocity, support observation and typed damage/death/attack candidates after the canonical committed step; one terminal arbiter chooses the result. No inferred penalty from ring count or raw collision overlap |
| `SupportObservation` | Stable support identity, contact normal, terrain/object distinction, support displacement and relative velocity; read existing solid/support ownership after inline resolution |
| `ScenePresentationFrame` | Immutable revisioned terrain view changes, scroll/deformation/animation phase, ROM-art references, ordered sprite pieces and visibility, overlay values and sequenced native sound cues |
| `SceneViewPresenter` / `ModeOverlayCanvas` | Render a supplied view transform and bounded drawing primitives using locally resolved assets, without touching authoritative camera, objects, collision, RNG or clocks |

`CourseCheckpoint` is an in-memory capability tied to a world/level generation,
not a serialized `CompositeSnapshot`. Its immutable payload excludes mode/match
adapters, so mode rewind state may reference golfer/pre-shot checkpoints without
a recursive snapshot graph. Handles retain their payload directly; ordinary
rewind history keeps references as needed and garbage collection frees unreachable
ones. Do not use a mutable global handle lookup or discard a handle still referenced
by rewind history. Account for their payload in the existing rewind memory budget.

Course restore must restore/rebind the roster, registered managers, art/palette
state and object/support references coherently. Penalty/handoff are mode-timeline
events: keep the current mode ordinal and past debug history, discard stale cached
future state and capture the replacement course at the completed mode boundary.
Do not use the existing seamless-load history reset for these operations. Replaying
a handoff executes the recorded mode event once and never applies the other
golfer's old inputs to the restored world. Replay preserves its recorded suffix;
only new forward branching discards future input history. An act load creates a new generation
and deliberately resets history; test isolation on both sides, not cross-act seeking.
A checkpoint from an old hole/session fails before any partial mutation; missing
required domains are errors rather than silently skipped.

Course restore is a controller-owned boundary, not just `registry.restore`:
the audio timeline lives outside that registry. Keep audio stamps on the current
mode ordinal, discard queued effects belonging to the abandoned course branch,
stop transient charge/hurt/death effects and resynchronize EHZ music before the
next turn. A native hit cue already heard is not re-enqueued. Ordinary debug rewind
continues to restore its audio keyframes and mode-to-audio mapping.

Hold gates authoritative V-int, executed-frame and level counters, timers, RNG,
physics, object loading/culling, camera follow, world events, palette/pattern
animation and PLC/hardware service admission. Input, meters, network processing,
overlay animation and native audio playback may advance on presentation ticks.
Queue completion can be received while held but cannot publish world/art changes
until an admitted step. Startup/title/load boundaries finish before golf captures
its first playable lie. HOLD wins over native pause admission. In golf, Start opens
the mode menu/room pause, which holds both the course and shot-feedback timers;
online pause/resume is host coordinated. Native Start pause remains unchanged in
stock sessions. No aiming-menu path may enter native V-blank pause servicing.

Offline golf records one logical mode iteration per presented tick, whether its
admission is HOLD or RUN. Ordering is: establish the input/audio ordinal; run the
mode tick once; admit at most one canonical course step; arbitrate outcomes; record
the completed mode boundary. A held row advances mode state/history and native
audio presentation, but no gameplay clocks or PLC services. `LiveRewindStepper`
and capture drivers replay that coordinator, not an unconditional level step.
Record any mode command not reconstructible from controller rows in an owned
sidecar; do not change trace comparison data into input or modify the V5 contract.
For an explicitly launched offline golf BK2 capture, one input row supplies one
mode iteration, including held aiming rows. Stock movies/traces retain their
existing stepping behavior. Online competitive rewind remains unavailable.

Duck/spindash animation and dust while held are render-only pose phases using
native S2 mappings/timing. They do not tick native players/dust objects, allocate
world children, consume RNG or advance course animation clocks. Record their phase
in mode state and include it in scene projection. This also supplies the guest's
local pre-acceptance pose. Stamp each held-tick native sound request on that tick's
audio ordinal; service the existing sound driver without changing its pitch ladder.
Menu/disconnect pause freezes feedback scheduling and uses the existing audio-pause
policy, separately from ordinary live-meter HOLD.

## Mod layout and state machine

Create `examples/putt-putt-paradise/` with `build.py`, `README.md`,
`src/main/resources/META-INF/openggf-mod.yaml` and source package `paradise`:

- `PuttPuttParadiseMod`, `GolfModule`, `GolfCourseDefinition`, `GolfRules`:
  registration, S2 decoration, the two ROM-backed acts and immutable rule constants.
- `GolfSession`, `MatchState`, `GolferState`, `GolfMode`, `ShotSelection`,
  `ShotPower`, `SettlementDetector`, `ShotResolution`: turn/shot decisions,
  terminal arbitration and mode rewind values.
- `GolfMenu`, `GolfOverlay`, `DeparturePreview`: launch choices and presentation.
- `net/GolfWire`, `GolfTransport`, `GolfHost`, `GolfClient`: bounded value codec,
  socket lifecycle and command/presentation ownership; no engine dependencies in
  `GolfWire` or `GolfTransport`.

Flow: `LOBBY -> TURN_OPEN -> AIM -> CHARGE_ONE -> CHARGE_TWO -> PRE_RELEASE
-> WATCH -> RESOLVE -> TURN_OPEN`, with `HOLE_RESULT`, `MATCH_RESULT` and
`DISCONNECTED` branches. Practice has one golfer and a selected act; competition
plays both acts. Record two saved courses, active player, starter, scores,
finished/DNF flags, charge values/stage, presentation timers, committed-shot ID,
pre-shot checkpoint, pose/dust phase and dwell/watchdog state in mode rewind. World state belongs
to course checkpoints. Network connections and socket objects never enter snapshots.

Capture the neutral lie's course at TURN_OPEN, before aim/charge pose commands.
The second accepted charge is the only stroke-commit transition: retain that
checkpoint as the pre-shot course, increment strokes once and seal the selection.
Repeated A, retransmission and result polling cannot repeat that edge.
Failure restores that course, retains the stroke, adds one penalty and advances
the turn. Success saves the evolving course/lie for that golfer. Hole-finished
players are skipped; reverse the first starter for act 2. Lowest summed strokes
and penalties wins, ties draw, and concession is DNF and loses the match.

`ShotResolution` is the sole terminal arbiter. At a completed course/mode boundary,
choose the first applicable outcome in this order: damaging hit/death; swept finish;
valid settlement; pending explicit lost ball; watchdog expiry. Thus damage wins
over finish in the same step, and a valid finish/settlement wins over a timeout.
Ignore lost-ball input for an already resolved shot. Resolve at most once per shot
ID, with one ledger/result transition. This intentionally uses committed-step
precedence, not an unimplemented claim about sub-frame collision chronology.

## Task 1 — establish the external mod and executable proof harness

**Create:** the manifest/build script, `PuttPuttParadiseMod`, minimal `GolfModule`,
`GolfRules`; `src/test/java/com/openggf/mods/code/TestPuttPuttParadise.java`.
**Reference:** `examples/infinite-sonic/build.py`, its manifest and
`src/test/java/com/openggf/mods/code/TestInfiniteSonic.java`.

- [ ] Pin the pre-implementation SHA, confirm the retained local workspace and
  hooks, read applicable launch/content contracts and discover the real S2 ROM.
- [ ] Compile/package the actual external source in the test harness, load through
  `ModContext`/`GgfModCli`, and assert namespaced owner/manifest resolution. Use the
  same production launch path where feasible; test helpers do not grant runtime
  permission to use unannotated internals or `GameServices` from mod code.
- [ ] Load full ROM EHZ1 as Sonic and as Tails. Assert S2 root/resolved module and
  data source, no sidekick, expected act geometry, and no activation in stock S1/S3K
  or an unrelated S2 session. Reject unsupported donor/team settings explicitly.
  The supported golf profile is native S2 physics/art with one active golfer;
  duplicate character choices refer to independent turns, not two live sprites.
- [ ] Keep build output under this worktree's `target/putt-putt-paradise/` and the
  jar code/manifest-only. Adapt queued build/package steps from Infinite Sonic.

**Check:** `TestPuttPuttParadise` bootstrap cases with the discovered S2 path,
mod package validation and inspection that no ROM/assets were bundled. This
establishes the harness, not a playable-golf claim.

## Task 2 — add held-course admission with live presentation ticks

**Create:** proposed mode interfaces and `TestGameplayModeHold` under
`src/test/java/com/openggf/game/`.
**Modify:** `GameModule`, `game/patch/DelegatingGameModule`,
`game/session/GameplayModeContext`, `GameLoop`, `LevelIterationAdmissionController`,
`LevelFrameStep`, live rewind recording/input/stepper/controller owners and
`src/test/java/com/openggf/tests/HeadlessTestRunner.java`.
**Audit all entry points:** `GameLoop.step()`/`stepPresentationFrame()`,
`GameLoopTitleCardLifecycle`, `TraceSessionLauncher`, `tools/RecordingFrameDriver`
and `tools/HeadlessGameBoot`. Setup/load/stock-trace paths have explicit exemptions;
every golf-capable path must enter the coordinator before advancing clocks/PLC.

- [ ] Add a session-owned extension/no-op default and owner-wrapped callbacks.
  Route interactive and headless mode ticks through one shared coordinator.
  Sample input once per presentation tick; consume A edges once, including across
  a held-to-running transition. Keep accelerated ordinary play and stock native
  Start pause behavior intact. Golf Start is handled by the extension; HOLD takes
  precedence over native pause and direct-step/capture calls cannot bypass it.
  Keep the coordinator out of the large `GameLoop` body and pass its source guard.
- [ ] Gate HOLD before authoritative counters and hardware/PLC admission, not
  merely before sprite movement. Prove no course snapshot key changes over
  600 held idle ticks while the meter and native sound playback advance. Explicit
  boundary pose/checkpoint commands may change their declared fields; test those
  separately and do not allow them to advance course clocks or world objects.
- [ ] Test pending work cannot publish during HOLD, first resumed tick advances
  exactly once, and load/setup/trace/movie/rewind paths retain their documented
  boundaries. An extension fault holds or exits cleanly rather than freeing input
  into stock platformer movement.
- [ ] Register presentation-stage state for debug rewind. Test capture/restore in
  each charge/pre-release stage and replay the same mode-input suffix. Use a small
  deterministic test extension with synthetic charge stages before `GolfMode`
  exists; later reuse the tests with the actual mod rather than retaining a second
  production state machine.
- [ ] Define and implement the mode-iteration input/audio recording contract above.
  Test real live-rewind seeking to a non-keyframe inside a 600-tick HOLD, across
  automatic release and across penalty/handoff; replay must reproduce meter,
  pose, edges and command timing without advancing the frozen course. Distinguish
  retained past debug history from discarded future cache and act-load reset.
- [ ] Prove render-only charge/dust feedback and correctly stamped native rev
  requests through interactive, direct-step, replay and golf BK2 capture entry
  points. Check pause/resume during each feedback stage. Do not advance a dust
  object or run player physics merely to animate charging.

**Check:** new hold tests plus existing `TestInGamePause`, frame/rewind boundary
tests affected by the edit, and the API checks below. This is a shared timing
change and requires normal change-based validation at final verification.

## Task 3 — prove independent full-course checkpoints

**Create:** `CourseCheckpoint`, `CourseCheckpointService`, engine implementation
owned by `GameplayModeContext`; `TestCourseCheckpointIsolation` under
`src/test/java/com/openggf/game/rewind/`.
**Modify:** `RewindRegistry`, composition-root adapter registration,
`GameModule`/`DelegatingGameModule`, `SpriteManager` roster recreation as needed,
and owning snapshot adapters only where the audit demonstrates a missing field.

- [ ] Inventory actual registered EHZ domains: level/layout mutation, terrain and
  tilemaps, placed/dynamic objects and links, respawn/loading window, rings/lost
  rings, player class/roster/pose/subpixels/sensors/support, camera bounds/history,
  game/level/V-int/sprite clocks, timers, RNG, events, animation/oscillation,
  parallax, palettes, dynamic art/PLC work and world/session metadata. Decide
  audio restore ownership without treating sound-driver time as a physics clock.
- [ ] Capture only at committed boundaries with an engine-defined course domain.
  Both course and mode adapters remain in ordinary debug rewind. Audit retained
  snapshot values for mutable arrays, manager references and cross-player aliases;
  enforce immutability/copying where required rather than assuming a record is safe.
- [ ] Recreate the native Sonic/Tails roster through its registered factory before
  restoring values; rebind camera, renderer/art and contacts in the established
  restore order. Keep RNG restore after recreation side effects. Support all four
  character pairings, including identical codes without sharing course state.
- [ ] Restore A after advancing B and compare all captured course domains, then
  run a matched input suffix against uninterrupted A. Repeat after rings, badnik
  destruction, bridge/platform changes, terrain mutations, queued art, springs
  and moving support. Check capture -> restore -> capture closure as well.
- [ ] Test stale session/hole/layout handles fail before mutation, restored
  timelines exclude the other golfer's inputs, and a mode snapshot containing
  checkpoint handles is acyclic and stays within the rewind memory policy.
  A failed restore ends/holds the match with a clear error, never a partial lie.
- [ ] Add controller-level course-replacement boundaries for handoff/penalty and
  explicit audio reconciliation. Preserve prior mode keyframes and checkpoint
  handles within the memory budget; test debug seeking across both operations.
  Only real act/session loads invalidate the previous generation and timeline.

**Exit gate:** full state and forward replay agree for both characters. Do not
continue to competitive turn switching with coordinate-only save/restore.

### Task 3a — early presentation and guest-session feasibility spike

**Create:** the first `ScenePresentationFrame`/`SceneViewPresenter` prototype and
`TestGolfScenePresentation` under `src/test/java/com/openggf/game/presentation/`.
Task 8 extends these into production guest presentation. This spike runs before
Tasks 4–5 freeze launch/pose/overlay contracts, not after the local game is finished.

- [ ] Inventory all render paths reachable in full EHZ golf, including both player
  bodies, Tails' tails, render-only charge dust, placed objects and dynamic children
  such as explosions, animals, score pieces and lost rings. Inspect custom
  `GLCommand` actions as well as ordinary prepared sprite pieces; neither opaque
  callbacks nor atlas indices alone can serve as a portable scene value.
- [ ] Capture emitted piece/primitive values before GPU submission. Add a ROM-art
  provenance registry mapping local pattern allocations to stable ROM source/
  recipe identities and animation phase. Cover every reachable custom render
  path with a bounded value adapter or fail the spike with concrete evidence.
- [ ] Bootstrap a guest resource session through the normal ROM act load, then
  hold it permanently after setup and substitute `SceneViewPresenter` for stock
  drawing. Its locally created players/objects never simulate or supply guest
  scene state; received values never restore those objects or collision. This
  reuses held admission rather than assuming a new asset-only load API exists.
  Presentation asset decoding/residency is separate from the held world's PLC
  runner, so resolving guest art cannot admit a gameplay/hardware service step.
- [ ] Render transmitted view/scroll/deformation values without invoking mutable
  camera-follow or background-deformation handlers. Survey uses the same pure
  transform over terrain/current presentation. Separate render-cache/art setup
  from authoritative gameplay state in invariance assertions.
- [ ] Prove local-ROM art resolution, character swap, platform/bridge changes and
  object removal with two independent presenters. Then choose the final scene
  schema and reuse it in Task 5. Record unsupported render paths as blockers.

**Exit gate:** capture point, art identity and permanently held guest-resource
session all work on both acts; no premature finalization of the presentation API.

## Task 4 — coherent putt/chip launch and settlement

**Create:** `PlayableLaunchControl`, immutable launch/contact values;
`ShotPower`, `SettlementDetector`, `ShotResolution`; focused tests `TestPlayableLaunchControl`
under `src/test/java/com/openggf/sprites/managers/`.
**Modify:** `PlayableSpriteMovement`, `AbstractPlayableSprite`, relevant semantic
movement/landing rules, `NativePositionOps` only where required, and solid support
observation at the existing `ObjectManager`/latched-contact boundary.

- [ ] Begin with direct test selections: flat/slope putts, left/right, zero/maximum
  chip and useful weak/full power. Convert the local supporting tangent and
  outward normal into ROM fixed-point launch velocity. Define bounded shared
  power/elevation constants in `GolfRules`; assert monotonic usable power rather
  than inheriting the stock spindash table's high minimum speed. Record measured
  tuning in the design; do not tune shipped native movement paths.
- [ ] Reuse native charge animation and `SPINDASH_CHARGE` requests. Map the two
  timing values to a finite charge-feedback sequence with intervals that preserve
  the driver's native consecutive-rev behavior; release with native release SFX.
  Keep sound requests plain: no power-scaled pitch/rate. Test request sequence
  and existing S2 driver pitch behavior rather than inventing a pitch assertion
  based on the physics charge counter.
- [ ] Publish a coherent rolling launch, including sensor/radius/centre conversion,
  fractions, facing, ground/air velocity and support detachment for a chip.
  Retain native post-launch gravity, slopes, collision, loops, springs and attacks.
  A scoped shot-motion rule suppresses early unroll on landing/roll stop/spring;
  restore it in snapshots and remove it when the mode exits. A zero-speed roll
  stays curled at zero speed; it must not use pinball mode's automatic ±0x400
  speed boost. Preserve rolling radii on landing and inventory object-forced
  uncurl paths, including springs, against their owning routines. Confirm native
  pinball/object-preserved handoffs remain unchanged outside the scoped rule.
- [ ] Accept settlement only on valid floor support with low support-relative
  velocity for a configured dwell. Walls/ceilings, brief apexes, spring recontact,
  support loss or a reversing loop reset dwell. Pin the settled lie coherently;
  neutral aiming pose/duck changes must preserve floor contact.
- [ ] Add bounded shot duration/no-progress watchdog and explicit lost-ball action.
  Both use `ShotResolution` and the checkpoint rollback service, never a free
  forward teleport. Introduce a one-golfer test resolver/ledger now; Task 6 connects
  real EHZ outcomes and Task 7 supplies the competitive match ledger. Test compound
  damage/finish/settlement/timeout candidates and one result per committed shot.

**Check:** weak reversal/strong loop traversal, chips onto slopes, repeated spring
contacts, low-speed rolling, moving-support dwell and restore/forward replay;
ordinary native spindash/landing/radius tests confirm the rules are session scoped.

## Task 5 — playable practice controls, HUD, preview and survey

**Create:** `GolfMode`, `ShotSelection`, `GolfOverlay`, `DeparturePreview`, minimal
`GolfMenu`; `TestGolfShotControls` under `src/test/java/com/openggf/mods/code/`.
**Modify:** bounded mode overlay/view APIs and the owning renderer/input adapter;
extend stock-destination HUD policy only as required by the tested patch path.

- [ ] Implement Up/Down elevation clamping and Left/Right facing without walking.
  First A ducks and opens the oscillating meter. First timed A initiates charge;
  second timed A adds charge, fixes both power contributions and commits once.
  Preserve selected elevation; neither hit introduces an accuracy-error stage.
- [ ] Define meter period/curve, charge feedback cadence and pre-release delay in
  named rule constants. B cancels setup before commitment for free; after commitment
  ignore aim/cancel/extra A. Reset input edges on menus, turn changes and restores.
  Test every state transition through the same extension tick used in production.
- [ ] Render direction/type, elevation, meter stage, active player, strokes,
  penalties, status and opponent marker across supported widths. The preview
  shows only a read-only initial departure segment until first terrain collision
  or a bounded horizon; stop where object prediction is unavailable and label
  the limit. Do not advertise a complete collision/loop/bounce prediction.
- [ ] Use a presentation-only survey transform. Draw ROM terrain and known current
  course presentation; do not move `Camera`, spawn offscreen objects or recull
  the authoritative object window. State known-view limits plainly. Compare full
  state and the same launched-shot suffix before/after a long survey.
- [ ] Add practice act selection/restart and validate held meters/audio/overlay
  remain responsive. Unsupported team/donor options are unavailable and rejected
  on launch, rather than silently overridden mid-game. Introduce the minimal
  supported title-to-practice destination/character selection seam here, including
  direct act-2 start; Task 10 completes host/join text entry and menu polish.

**Check:** controls at boundary elevations/powers, held/repeated A, cancellation,
auto-release, course freeze, honest preview and survey invariance; render at
320/400/512/640/800 pixels using real configurations.

## Task 6 — full EHZ course rules, finish gates and penalties

**Create:** `GolfCourseDefinition`, scoped golf event/progression policies and
`TestGolfEhzCourseRules` under `src/test/java/com/openggf/mods/code/`.
**Reference/modify by ownership:** `Sonic2LevelEventManager`,
`game/sonic2/events/Sonic2EHZEvents`, the S2 object registry, signpost/capsule,
checkpoint/monitor placement and `GameStateManager` progression consumers.

- [ ] Survey the real ROM-loaded endpoints and traversable destination volumes.
  Record verified gate coordinates and source/reference in the design; do not
  use the EHZ2 boss spawn coordinate as an unverified finish. Check left/right,
  airborne and high-speed swept crossings, one-shot completion and no tunnelling.
- [ ] Decorate full stock EHZ layout/objects through supported level/provider seams.
  Suppress boss spawning, arena boundary changes, boss PLC/music, capsule and
  signpost/results progression. Preserve the destination terrain and ordinary
  EHZ mechanics. Keep a finish marker using available ROM art/overlay primitives.
- [ ] Supply explicit session-scoped policies disabling stock lives/time-over,
  extra lives, super/special-stage/checkpoint progression and power-altering
  monitors. Use semantic rules/providers; avoid blanket invulnerability and
  per-frame corrective writes that hide stock side effects.
- [ ] Route actual damaging hits/death to one failure outcome before stock
  respawn/reload progression. Contact hooks collect typed candidates during the
  canonical step; `ShotResolution` decides after the committed step, before the
  next death-timer/respawn step. Scoped rules prevent irreversible stock lives,
  score/progression and reload side effects; checkpoint restore reverses native
  hurt/velocity/ring/object changes. Apply the declared audio boundary policy.
  A rolled badnik attack remains successful combat.
  Rings are secondary per-golfer stats, not a shield from golf penalties or a
  tie breaker. Verify full pickups/objects/clocks restored with the stroke retained.
- [ ] Test damaging hit plus finish in one step, finish plus watchdog, settlement
  plus watchdog and a late lost-ball request. Use the declared priority regardless
  of callback ordering and publish exactly one result/turn transition.

**Check:** both full acts terminate through golf gates with no bosses, locks,
capsule dependency, stock result or accidental next act; pit/spike/badnik failure
is exactly one stroke plus one penalty; ordinary stock S2 regains its rules on exit.

## Task 7 — local alternating two-player match

**Create:** `GolfSession`, `MatchState`, `GolferState`, immutable rewind records
and `TestGolfLocalMatch` under `src/test/java/com/openggf/mods/code/`.

- [ ] Initialize each golfer's own fresh course with their selected native character
  and identical rules. Keep one live sprite and a presentation-only opponent lie.
  Save the active successful course; restore the other golfer before opening aim.
- [ ] Implement alternating strokes, skip completed players, EHZ1 then EHZ2,
  reversed act-2 starter, sum strokes+penalties, draw and concession/DNF. Give
  one logical input stream to the active owner. In local hot seat, either enabled
  controller may feed that stream; merge held masks and deduplicate action edges
  so simultaneous A presses commit once. Neither controller steers a released
  ball or controls a separate inactive sprite. Online command ownership remains
  restricted to the active assigned player.
- [ ] Make penalty restore retain the ledger, successful handoff retain each
  evolving course, debug rewind restore the complete mode+course state, and an
  act load create fresh handles/history. Do not expose competitive rewind as a
  free retry. Handle menu exits/restarts as separate practice/competition actions.
- [ ] Exercise all four character pairings through both complete acts, including
  a penalty, asymmetric pickup/badnik state, finish-order differences and an act
  boundary. Reconcile restored character art/support/camera, not just scores.

**Exit gate:** a complete two-hole local match with correct scores and no course
leakage. Local play remains available if networking is not yet ready.

## Task 8 — prove an object-complete read-only guest view

**Extend:** Task 3a's scene presentation types, guest resource-session bootstrap
and `TestGolfScenePresentation` into complete production guest presentation.
**Modify:** `level/render/SpritePieceRenderer`, sprite/object render collection,
`GraphicsManager`, level tilemap/parallax/art owners and mode view routing.

- [ ] Project render values after committed steps without object `update` or
  mutation. Include terrain changes, rings/pickups, badniks, springs, moving and
  collapsing platforms, active character, scroll/animation/deformation and draw
  order. Stable ROM-art identities resolve through local asset loading; virtual
  pattern/GPU IDs alone are not portable identities.
- [ ] Provide a render-only guest mode with local ROM decoding and no authoritative
  sprite/object physics, using the permanently held resource session proved in
  Task 3a. Resolve dynamic art residency/animation and palette
  effects from agreed ROM recipes/identities and phase values, not transferred
  pixel or ROM-art payloads. Terrain view edits never hydrate collision/gameplay.
- [ ] Use full view frames initially, with session/hole/turn/shot and monotonically
  increasing revision. A full frame explicitly replaces object visibility so
  destroyed/despawned objects cannot linger. Bound records/pieces/terrain edits;
  if a complete supported view cannot fit, report failure instead of truncating.
  Defer delta compression until profiling justifies it.
- [ ] Test a bridge collapse, destroyed badnik, collected ring, spring animation,
  terrain edit, character swap and resumed frame with two independent view
  consumers. Assert renderer activity changes no host state or guest gameplay
  state. Compare framebuffer evidence at representative same-revision frames.
- [ ] Use one agreed viewport width per online match for MVP; reject mismatches
  during readiness. Every supported width can be selected, but a guest cannot
  enlarge the authoritative object-loading window. Survey remains a view transform.

**Exit gate:** guest sees the relevant current course, including object removal
and animation, rather than only a moving character. No claim of online completion
until this proof passes with locally loaded ROM assets.

## Task 9 — framed TCP protocol and host-authoritative room

**Create:** the mod's `paradise.net` files; codec/state tests
`TestGolfProtocol` and `TestGolfNetworkMatch` under
`src/test/java/com/openggf/mods/code/`.
**Reference:** engine-free separation guarded by `TestNetIsolationRules`;
existing race transport is reference only, not a golf schema.

- [ ] Use Java 21 sockets with a length-prefixed binary frame, schema version and
  typed messages. Initial limits: 2 MiB per frame, 256 queued commands, at most
  60 presentation frames/second and a 30-second reconnect window. Measure full
  800-pixel EHZ view sizes before fixing limits; exceedance is an explicit error.
  Socket threads decode bounded immutable values only; drain commands on the
  host presentation tick. Coalesce pending presentation frames to the newest
  complete revision while retaining reliable control/score/sound-cue messages.
- [ ] Bound reliable outbound queues to 64 messages or 4 MiB, whichever is reached
  first; keep at most one pending full presentation frame plus one in-flight frame.
  A writer has a 5-second write deadline enforced by closing its owned socket.
  Overflow/deadline expiry disconnects and holds the room; no socket write or
  backpressure wait can block the simulation/presentation thread. Verify slow-reader
  memory bounds and port/worker cleanup. Revisit measured limits before finalizing.
- [ ] Define `Hello/Ready` with engine/API/mod/protocol/rules fingerprints, S2 ROM
  SHA-1, characters, selected mode and viewport. Reject mismatches before play;
  no ROM bytes/art or executable content crosses the connection.
- [ ] Define `TurnOpened`, `ShotRequest`, `ShotAccepted`, `PresentationFrame`,
  `TurnCommitted`, `Pause/Resume/Leave` and reconnect messages. `ShotRequest`
  includes match UUID, hole/turn sequence, shot sequence, player, locked direction,
  bounded elevation and two bounded charge values. Shot type is derived from
  elevation; the host derives power with `ShotPower`, never trusts claimed scores.
- [ ] Both players time meters locally. Guest commitment seals the selection and
  shows awaiting acceptance; latency does not grade timing. Host validates active
  owner/IDs/ranges on the game thread and accepts once. Repeating a shot ID with
  the same payload returns its cached acceptance/result; conflicting payload or
  stale/future turn is rejected. Retain current/last committed acknowledgements
  for bounded reconnect, not an unbounded request archive.
- [ ] Host simulates both golfers and publishes full scene frames, sequenced
  native sound cues and committed scores/next lie. Guest applies only newer
  matching revisions to its presenter; it never feeds positions into gameplay
  snapshots or native collision/physics. Play each sound cue at most once through
  the native S2 driver, with native charge cadence; no packet supplies pitch.
  Reliable cue IDs/tick offsets survive view-frame coalescing. Reconnect restores
  current sound ownership without replaying old charge cues. Guest-owned local
  charge preview must not be played again when its accepted cues arrive. Its
  local duck/charge pose is render-only mode state; replace it with authoritative
  presentation on acceptance without touching guest physics.
- [ ] Disconnect holds the host even during PRE_RELEASE/WATCH, freezes course
  clocks and preserves an accepted shot. Rejoin with the assigned opaque room
  token, resend authoritative state/cached receipt, then resume once both ready.
  Expiry permits a clean abandon/concession path; host exit ends the room. Close
  owned sockets, workers and queues on fault/leave/mod teardown.

**Check:** partial/combined frames, oversize/invalid ranges, slow reader, duplicate
and contradictory requests, out-of-turn input, stale frames, late acceptance,
disconnect before/after commit and during watch, reconnect without relaunch,
expiry and clean port/thread release. Two JVMs on localhost/LAN must complete
both holes. Add controllable delay/disconnect in tests; no matchmaking, NAT
traversal, host migration, account service or broad security subsystem.

## Task 10 — menus, route evidence, API packaging and completion

**Create/update:** `GolfMenu`, example README/build script; the existing design;
`docs/architecture/validation/levels/putt-putt-paradise-ehz.md` for route coverage;
`docs/status/level-test-coverage.md` links to per-act/character route matrices.
Update maintained API/creator/configuration guides and changelog where behavior
changes require them, using the documentation obligation checklist.

- [ ] Finish Practice / Local Match / Host / Join flows with independent character
  choices, practice act choice, IP/hostname+port entry, ready state, clear turn/
  waiting/paused/results indicators and clean return to stock title. Reuse title
  lifecycle; add a supported bounded text-entry/launch-selection seam if current
  callbacks cannot express act 2 or direct join. Do not hide startup requirements
  in developer-only command-line controls.
- [ ] Explain LAN/manual port-forwarding, JVM code-mod support, required ROM,
  controls, penalties, independent courses and unsupported donor/team modes.
  Test enable/disable/reload and every teardown path restores stock input,
  progression, camera/presentation/audio behavior and closes network resources.
- [ ] Deliver EHZ1/Sonic, EHZ1/Tails, EHZ2/Sonic and EHZ2/Tails route matrices under
  the [level test standard](../../guide/contributing/level-test-standard.md).
  Test actual menu widths 320/352/400/528/800; list native S2 and solo active-character
  scope, intentional rejected configurations and inherited gaps explicitly.
  All pairings need two-hole match coverage; online needs all supported common
  widths and both mixed/duplicate character behavior.
- [ ] Keep short independent checks for launch, damage/attack, loops/slopes,
  moving support, springs, finish and course handoff separate from complete
  representative routes. Capture before/active/after states and replay suffixes
  across charge, feedback, watch, penalty, restore, finish, act load and reconnect.
  Use real input-driven routes for completion claims, not teleported fixtures.
- [ ] Record render/gameplay evidence for HUD, survey and host/guest object view
  claims; use relevant capture skills. Keep durable captures outside the repository
  in an explicit task directory, link evidence, and preserve no temporary raw logs.
- [ ] Maintain `@ModApi` recursive closure, normalized signatures and SDK/Javadoc/
  sample checks. Current policy keeps descriptor/runtime at candidate `0.7.0`
  and replaces `src/test/resources/mods/mod-api-signatures-0.7.txt` in place;
  do not fabricate a release/version bump or published baseline. Recheck policy
  at execution time and update descriptor/version only if that policy changes.
- [ ] Run final combined validation once after focused fixes. Record exact
  remaining failures/skips and coverage limits. Commit locally with required
  trailers and the contributing model co-author block; leave this worktree/branch
  intact. Integration/publication requires a later user instruction.

## Verification commands and limits

These are the maintained implementation commands; completed runs and outcomes
are recorded below. Earlier task-local test names are proposal names, consolidated
into the actual test inventory in the coverage matrix. Supply `-Dsonic2.rom.path=`
with the discovered, verified absolute S2 ROM path; inspect ROM-backed skips.
Canonical S2 SHA-1 is `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, CRC32 `7B905383`.

```bash
mvn -v  # Java 21; inspection only
tools/testing/install-hooks.sh  # once in the implementation worktree
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestGolfScenePresentation,TestRewindRegistry" "-Dsonic2.rom.path=/absolute/discovered/S2.gen" test
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestModApiJavadocTool,TestModApiSdkPackager,TestGraphicsManagerHeadless,TestInfiniteSonic" "-Dsonic1.rom.path=/absolute/discovered/S1.gen" test
python3 tools/testing/maven_queue.py -Dmse=off -Pguards "-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestArchitecturalSourceGuard,TestArchitecturalReviewGuard" test
python3 examples/putt-putt-paradise/build.py
python3 tools/testing/run_categories.py --base <pre-implementation-sha>
python3 tools/testing/run_categories.py --base <pre-implementation-sha> --preflight
python3 tools/testing/run_categories.py --base <pre-implementation-sha> --run
```

ROM-backed shared proof tests and maintained sample tests also require their
discovered ROM properties when applicable (Infinite Sonic uses S1). Generate
the normalized signature snapshot with the documented `ModApiSignatureSurface`
tool against compiled classes/dependencies, then inspect the diff and run the
surface test; do not rewrite the pin merely to suppress a closure failure.

Before the broad run, inspect the selected class count, state expected cost and
stopping rule, and pass Java 21/Lua 5.4/PowerShell preflight. Shared timing,
checkpoint, rendering and public-contract edits require normal selection; do
not manually narrow it. Full ordinary normalization has measured about 24 minutes
plus 10 minutes for guards; use the runner's timeout/no-output policy and report
incomplete runs honestly. Check the
[measurement hazards](../../agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output)
before reporting results. Compare any disputed failures with bounded matched
baseline/current tests by identity, rather than repeating two broad suites.
Inspect `results.json`, including skips, then acknowledge the run to remove
consumed diagnostics. No trace/native capture run is implied by an ordinary
category pass; record domain/capture evidence separately.

The initial planning commit used Markdown structure, local links, existing paths,
design consistency, whitespace and commit-policy checks only. Implementation
validation is recorded below; that planning check established no engine feasibility.

## Implementation review checklist

- [ ] Every design acceptance scenario maps to the tasks above and future route
  evidence; full acts, both characters, pairings, practice/local/online and
  teardown all remain in scope.
- [ ] Hold precedes every gameplay clock/service; meters and native audio remain
  responsive without borrowing trace comparison data as input or authority.
- [ ] Course checkpoints contain complete immutable world state and roster/art
  identity, exclude ledger, survive mode rewind without cycles, and isolate timelines.
- [ ] Launch/radii/contact and rolling retention are coherent through slopes,
  loops/springs/support changes; penalties use actual damage outcomes exactly once.
- [ ] Guest view includes relevant course objects and locally resolved ROM art;
  survey/presentation cannot mutate simulation or widen authoritative loading.
- [ ] Accepted-shot IDs, disconnect hold, reconnect receipts and teardown bound
  network lifecycle while retaining native sound behavior and host authority.
- [ ] Public types/pins and validation commands match the maintained candidate
  policy; actual implementation evidence is kept distinct from this proposal.

Plan self-review covered the design's acceptance scenarios, current versus proposed
capabilities, task dependencies, rollback/commit boundaries, native sound ownership
and future test scope. The task lists above preserve the original planning scope;
the implementation evidence and coverage matrix below record what was delivered
and which proposed coverage axes remain unsampled. The initial review itself
made no execution claim.

## Independent review reconciliation — 2026-10-05

Claude Opus 5.5 and OpenAI Codex GPT-6.1 Sol independently reviewed `1ca6fc6a9`
read-only against the design and targeted engine source. Both found the overall
direction suitable for early feasibility work, and both identified the missing
held-tick replay contract. Opus rated that gap Critical; Sol rated it Important.
It is a required plan correction before Task 2, not an observed gameplay failure.

| Review finding | Reconciled plan treatment |
| --- | --- |
| Opus C1 / Sol I1: held-tick recording, replay and history boundaries | One mode iteration per tick; shared live/replay/capture coordinator; non-keyframe seek tests; retain past handoff/penalty history, isolate actual act loads |
| Opus I2 / M6: held animation and audio ownership | Render-only native pose/dust phases; mode-ordinal audio stamps; controller-owned course-replacement audio policy, distinct from debug audio restore |
| Opus I3: pinball velocity boost prevents settlement | Explicit zero-speed curled rule and native pinball regression coverage; audit object-forced uncurl paths |
| Opus I4: capture point and guest resource session unproved too late | Mandatory Task 3a spike before presentation API finalization; ROM-art provenance/value capture; permanently held ROM-loaded guest resource session |
| Opus I5: pause precedence and bypass entry points | Golf HOLD precedes native pause; all golf menus use HOLD even during WATCH; enumerate live/direct/headless/replay/capture entry points and explicit stock/setup exemptions |
| Opus M7 / Sol I2: outcome timing and arbitration | Post-step typed candidates, scoped suppression of irreversible stock progression, one arbiter with explicit compound-outcome priority/tests |
| Opus M8: controls, survey deformation and guest preview | B cancels before commitment; both local pads may feed one deduplicated stream; pure view transforms; guest pre-acceptance render-only pose |
| Sol M3 / M4: outbound lifecycle and staged dependencies | Bounded reliable queue/write deadline; explicit early test extension/resolver; minimal practice launch seam in Task 5 |

The reviewers' excluded topics remain outside this task: a security subsystem,
transport-choice debate, measured shot-tuning values, and execution/build evidence.
The native S2/solo-active-character scope and intentional golf physics differences
remain explicit. No further review round, engine edit, test run, merge or push is
implied by reconciling these documentation findings.

## Decisions and rejected approaches

This plan retains the design and local naming commit `ac120c5f2`; it adds no
implementation and changes no shared history. The earlier pushed concept commits
remain on develop. The following choices follow inspection at that commit:

- A separate hold is required because the existing interactive step budget has
  a minimum of one and native pause advances V-int/service state.
- A validated course checkpoint service is required because raw sprite rewind
  restores by code and does not generally recreate a different main character.
  Saving coordinates alone cannot preserve pickups, supports, clocks or objects.
- Presentation values with ROM-art identities are required because native
  snapshot object graphs and GPU/virtual pattern IDs are not a guest view format.
- Dedicated mod-owned TCP commands keep the race/ghost boundary intact. Both
  peers trust local meter timing; host-owned simulation and duplicate handling
  provide consistent turns without a new security project.
- Candidate surface additions keep version 0.7.0 under current policy; a new
  published version would misrepresent the framework's release state.
- The existing native pinball flag is unsuitable for retained rolling because it
  restores ±0x400 inertia at roll stop; the scoped golf rule must allow zero speed.
- Reusing native pause while golf menus hold the course would still service V-int;
  golf menus use held admission in every phase instead.

## Implementation delivery (local, 2026-10-05)

Pinned pre-implementation base: `d5eaa3efc24b45e7f0d25b0a3392ef61dc5352a1`.
The development checkout remains `feature/ai-putt-putt-paradise`; no integration
into `develop` or remote push is authorized. Main `develop` stays at `fc4729c37`
with unrelated user changes preserved. Earlier pushed concept history remains.
The [course coverage matrix](../validation/levels/putt-putt-paradise-ehz.md)
records both acts, characters, viewports, local pairings and remaining breadth.

### Implemented behavior and ownership

The real external creator sources compile separately and pass the normal SDK
package/loader/trust/fault boundary. Model `c6b5db4f8`, TCP primitives `39e403de7`,
menu `44df57112`, room `bbf678a86`, compiler-generated constant validation
`f6257f027`, scene capture `551eac135`, build script `45dca6927`, ROM pose recipes
`1abffc311`, owner boundaries `e97f46679`, independent pause/concession handling
`66614664a` and finite creator-worker teardown `9fecd6499` were integrated locally
from owned worker trees. The engine coordinator and full course adapter follow
in the retained branch's implementation commit.

Practice, local competition and direct TCP Host/Join use full ROM EHZ1/EHZ2.
Sonic/Tails and duplicate pairings use one active physical golfer, independent
whole-world lies and alternating turns. Two timed A locks commit one stroke;
power-dependent feedback requests native charge sounds, then releases after a
short fixed pause. Innate pitch remains in the native driver. No boss, CPU
follower, airborne steering, artificial pinball boost or stock progression runs.

One controller admission precedes native clocks, physics, objects, animations,
PLC and hardware work. HOLD is a real mode/input/audio row for interactive,
direct/headless, capture and live-rewind paths; setup is not a row. The shared
runtime restores the real logical input after neutral WATCH simulation.
Scoped rolling retention allows supported zero speed without affecting native
pinball behavior. The controller stays outside course rollback by engine-bound
adapter identity; full debug rewind includes both course and ledger. Checkpoints
reject session/act/adapter-layout mismatches before any mutation, preserve past
history and invalidate future on replacement. Actual act loads isolate timelines.

The guest renders bounded authoritative values from its permanently held local
ROM resource session. Native tiles, objects, priorities, poses and palette recipes
remain local ROM assets. A gold finish flag is a view primitive. C surveys a
bounded terrain envelope (half a viewport horizontally and 112 vertically),
without widening native object admission or moving the simulation camera.
Online snapshots coalesce; commands/receipts retain reliable bounded ordering.
Pause ownership, crossing acceptance/rejection, reconnect receipts, concession,
write deadlines and resource teardown have actual focused checks.

### Review findings, failed approaches and corrections

Opus 5.5's implementation review was static, with no execution claims. Its nine
findings were resolved and checked: activate under stock S2 Sonic+Tails launch
profiles; use session overrides and a single roster; finish entry fades during
setup; retain independent online pause owners and release rejected guest meters;
adjust standing-radius contact on character swaps; avoid restarting unchanged
music on each restore; partition checkpoints by adapter identity rather than
creator key prefixes; capture C's input latch; revert the inert controlled-left/
right FixBugs edit. Sol's integration review also drove recording brackets,
logical-input restoration, setup exclusion, player-two pause, C survey, normal
boot checks and extraction of controlled iteration from the large GameLoop.

The first artifact test failed because no implementation existed; the first HOLD
check failed because dynamic-art clocks advanced. The corrected common path
freezes those owners before admission. Initial ordinary enums/switches failed
SDK validation: only compiler-shaped immutable enum constants and switch tables
were admitted; author static object sentinels were replaced with factories.

A lost-ball bound at decoded `maxY + 128` rejected EHZ1's legitimate lower floor.
Native S2 treats decoded maxY as camera origin and checks centreY against
`maxY + $E0`; the course bottom now derives that extent before its explicit lost
margin. EHZ2's boss arena camera clamp prevented reaching ROM egg-prison X
`$2B50`. Native escape `loc_2F460` opens maxX toward `$2AB0` (REV01 bytes at
`$2F460`: `0c782ab0eeca64065478eeca`). The boss-free course opens the equivalent
end view extent before capturing any lie, keeping the ROM placement as the gate.
No stock physics constant or comparison trace row was used to fit a route.

Wider object admission legitimately changes world encounters. Native-width route
inputs failed at wider EHZ1's ninth shot (watchdog) and EHZ2's upper route (damage).
The maintained wider routes alter only user shot choices and are replayed fresh;
exploration checkpoints are not completion evidence. Full-viewport survey margins
were rejected when 800px EHZ2 end data exceeded the existing bounded tile count.
Half-width/112 margins retain the same wire bounds (800 end: 33,754 tiles,
713,983 bytes), and unpanned pixels match the ordinary capture.

Native captures caught an overlapping clear-air label, now above the HUD, and a
320px manager viewport cache after wider title selection, now rebuilt from the
resolved presentation configuration (independent of trace gameplay-camera width). The opaque checkpoint's UUID getter leaked an unaudited public type;
it uses an engine-owned Object identity token instead, with no mutable owner
cycles. A descriptor prose comment failed its strict key=value parser and was
removed; the authoritative candidate remains 0.7.0 without changing topology or
published status. MenuInput is an explicit curated candidate root.

A compatibility run exposed the scene sampler rejecting native null art slots in
Sonic 1 before any mod gameplay. The renderer already accepts these slots and
retains previously uploaded bytes; sampling now follows that behavior without
inventing fallback ROM art. A regression test first reproduces the null-slot
failure, then checks untouched residency and preserved prior samples.

Listener teardown originally lost accepted sockets after ownership transfer and
some creator worker record/value classes loaded after the classloader closed.
`9fecd6499` retains workers until completion and eagerly loads the finite wire
shape before launching I/O. All thirteen raw wire forms are exercised with the
creator loader closed immediately, without prior encoder warm-up.

### Verification evidence

All Maven invocations use `tools/testing/maven_queue.py`; category runs use its
shared queue automatically. Java 21 and absolute S2 World REV01 ROM paths were
verified; ROM SHA-1 is `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

Native OpenGL framebuffer checks loaded the validated development mod through
normal boot/trust/resolver with the stock S2 profile. Real menu, aim, survey,
duck/charge, spindash, WATCH, pause and concession inputs produced checked images
at 320 EHZ1 and 800 EHZ2. The finish-preview camera was moved for view evidence
only. Durable PNGs are outside the repository at
`${HOME}/scratch/gameplay-captures/putt-putt-paradise-20261005/`.

The final focused commands, complete route inventory, API/architecture gates,
SDK build and combined change-based run are recorded here when they complete.
The broad ordinary inventory is approximately 2,976 classes plus structural
guards, with historical cost around 24+10 minutes. Native ROM timing parity,
PCM capture, all object subtype/contact combinations, movement donors and an
exhaustive width × act rewind sweep remain outside observed coverage. The online
integration executes two real turns and terminal concession; a continuous online
two-act traversal is not separately sampled. These limits are not full stock EHZ
certification, even when the mod's maintained completion routes pass.

Focused gates completed before the final route/broad pass:

- Combined scene/model/menu/room/checkpoint/Javadoc/SDK selection: 109 checks
  passed, no skips; a separate Infinite Sonic consumer failed on 195 null art
  loads, exposing the newly introduced sampler regression before gameplay.
- Regression red: `TestGraphicsManagerHeadless#emptyRomArtSlotsKeepNativeResidencyAndDoNotInventSceneSamples`
  failed on the null sample. After correction, `TestGraphicsManagerHeadless,TestInfiniteSonic`
  passed: 220 run, 208 passed, 12 skipped (the existing
  `sonicCrossesAPlatformStretchOnSpawnedStockPlatforms` cases 11/12, 19–24 and
  33–36 without usable stock platform stretches), no failures/errors.
- `-Pguards -Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestArchitecturalSourceGuard,TestArchitecturalReviewGuard`:
  103 passed, no skips. Normalized candidate pin contains 19,401 lines, no
  missing annotations or external signature leaks.
- `TestPuttPuttParadise#liveGameLoopAndBk2DriverConsumeTheSameHeldChargePauseAndReleaseRows`:
  passed 360 matching native-ball/meter/ledger rows through real GameLoop and
  BK2 driver with held charge, independent Start pause/resume and automatic
  release. An initial test assumed fixture setup was still pending; its first
  GAMEPLAY_FRAME showed the fixture had already performed that setup, and the
  assertion was corrected without changing production behavior.
- `python3 examples/putt-putt-paradise/build.py`: separately compiled and
  SDK-validated the current manifest/code-only jar at
  `target/putt-putt-paradise/putt-putt-paradise.jar`.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base d5eaa3efc24b45e7f0d25b0a3392ef61dc5352a1 --preflight`:
  Java 21, Lua 5.4 and PowerShell prerequisites passed; this runs no tests.

Final focused acceptance on the implementation tree (`S2_ROM` below denotes the
discovered absolute World REV01 path, checked against the SHA-1 above):

- `python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestGolfScenePresentation,TestRewindRegistry,TestGraphicsManagerHeadless' "-Dsonic2.rom.path=${S2_ROM}" test`:
  157 passed, no failures/errors/skips, 1 minute 25 seconds. This includes all
  20 fresh act × character × advertised viewport routes and all four complete
  alternating two-act local pairings. Each completion route retains zero penalties.
- EHZ1 uses ten shots at each width. EHZ2 uses 26 shots at 320, 37 at 352/528,
  54 at 400 and 16 at 800. The exact maintained inputs are in
  `TestPuttPuttParadise.route`; fresh full-audio fixture replay is the completion
  proof. Bounded temporary route explorers skipped PCM synthesis for search speed;
  their checkpoints and results were not used as final execution evidence.

The combined selection contains 2,976 ordinary classes and the separate structural
guard lane. It remains required because frame admission, checkpoints, rendering
and the public candidate contract are shared engine paths. Expected historical
cost is 24 minutes ordinary plus 10 minutes guards; the runner uses a 40-minute
combined invocation timeout and a 10-minute no-output timeout, excluding queue wait.

The first combined attempt on `b69b8fc3c` used the command above with default
one-worker/40-minute limits. It expired during `TestDezIncomingFinalRouteCapture`
after 2,400.89 seconds: 2,772 completed XML reports, 24,109 tests, four failures,
eleven errors and 38 skips. The ordinary lane was incomplete and the guard lane
did not start; these figures are not a completed suite result. The retained tail
showed continuing cold-route work rather than a no-output timeout. All fifteen
reported failure identities and all skips were inspected. Acknowledgment completed
and deleted the consumed diagnostics; no raw logs were archived.

The eleven errors were new null-registry failures in `TestGameLoop` title exit
cases. The new act selector now mirrors the existing zone selector's missing-
provider/registry default to act zero, while still bounding a selected act against
the real registry. Stock title routing and the actual Tails/EHZ2/400 menu launch
are checked together after this correction.

The four other failures require matched attribution at the pinned `d5eaa3efc`
baseline: sample-platformer concrete-delegate versus proxy assertion; FBZ→SOZ
restore changing `instaShieldRegistered` from false to true; MHZ2 authored Tails
and team routes missing their late-pulley ownership assertions. They are recorded
by test identity and assertion, not inferred from totals. No unrelated route
repair is included without attribution.

The 38 observed skips include twelve previously identified Infinite Sonic
platform cases, four unavailable native GL/EGL checks, opt-in benchmarks/soaks/
captures, and the maintained CPZ object-bug check. These are separate from the
mod's no-skip focused acceptance. A longer normal combined attempt with the
supported two-worker ordinary profile is required after the title fix; the
selection remains 2,976 classes and all guards, without manual narrowing.

Matched `d5eaa3efc` baseline checks used the queue in a separate owned detached
worktree. `TestGameLoop` passed all 97 cases, establishing the title regression;
after the null-registry correction those 97 plus the actual normal Tails/EHZ2/400
boot passed (98 checks, no skips). `TestFbzSandopolisTimelineHeadless` reproduces
the exact false→true insta-shield snapshot assertion at baseline. All three
`TestS3kMhzAct2AuthoredRoute` inputs executed at baseline; Sonic passed, and the
Tails/team cases reproduce the exact late-pulley ownership assertions. Their
ROM-backed baseline invocations supplied the discovered absolute S3K ROM path.

The sample-platformer case passed at baseline (one check, no skips) and failed
again in a matched current-only invocation. `e97f46679` correctly adds an outer
owner boundary for standalone rewind adapters; the old fixture peeled only the
provider wrapper and mistook the remaining proxy for the creator module. The
fixture now explicitly asserts both engine-owned wrapper types before inspecting
the actual creator module. Its original assertions forbidding creator-owned art
provider decoration and exercising real object rendering/recreation remain.
The production boundary is retained; it is not removed to satisfy reflection.
The corrected sample case plus `TestOwnerBoundGamePatch` passed all ten checks
without skips. This repair changes the fixture's boundary inspection, not
creator rendering or rewind callback ownership.

### Completed combined run and targeted repair

At `59ce8309b`, the normal combined selection completed with the supported two-
worker ordinary shape and a 65-minute per-invocation limit:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base d5eaa3efc24b45e7f0d25b0a3392ef61dc5352a1 \
  --workers 2 --max-minutes 65 --run
```

Run `20261005T220607Z-4219c50e` completed 2,974 ordinary XML reports with 25,650
checks: 30 failures, zero errors, 41 skips, in 2,065.9 seconds. The separate guard
lane completed 86 reports with 672 checks: six failures, zero errors/skips, in
199.95 seconds. This is a completed red run, not a full-suite pass. The 41 skips
were inspected: 12 unavailable Infinite Sonic platforms, four unavailable native
GL/EGL checks, opt-in capture/benchmark/soak diagnostics, and the maintained CPZ
spin-tube assumption. The mod acceptance checks did not skip.

Two new failures in `TraceCaptureBootDimensionsTest` exposed a rejected approach:
refreshing render caches from the gameplay camera incorrectly reduces 400/528px
trace presentation to 320px. Trace gameplay intentionally keeps its native-width
camera. Destination-load caches now refresh from the resolved presentation
configuration; the normal mod title still refreshes its gameplay camera before
loading. The independent native-gameplay/wide-presentation contract is retained.

Four new guard failures identified actual ownership violations: game→mods package
edges/cycle, low-level graphics→game scene dependencies, and registry constructor
self-delegation outside the session composition roots. Mod metadata hashing now
belongs to `ModSubsystem`, exposing strings to the course control. Procedural
geometry stays in graphics-owned immutable values and is converted to scene wire
values by `LoadedLevelScene`. Pattern versions sample the existing atlas upload's
CPU pixels, preserving null-upload residency and headless behavior without a new
runtime-layer dependency. Compatibility registry constructors initialize their
fields directly; the session still owns the controller-aware constructor. No
frozen guard baseline or package-edge ratchet was loosened.

Matched baseline checks use a separate detached `d5eaa3efc` worktree, the same queue
and discovered absolute ROM properties. All seven Death Egg incoming/final cases
reproduced by exact test identity and failure message, with zero errors/skips.
The four-class Lava Reef invocation ran 22 cases: 19 passed, and the Act2/team,
boss/team and Knuckles Hidden Palace failures exactly matched the current run.
The separate Tails Lava Reef invocation reproduced all four failures at input
19460, also with no errors/skips. The Mushroom Hill paired timeline assertion, wide whole-world state-byte
assertion, and wide Lava Reef boss position also exactly matched the baseline.
The remaining red identities received the same bounded attribution below. Follow-up checks cover the mod routes/scenes/network/rewind, trace
boot dimensions, atlas upload behavior and the complete structural guard lane.

The remaining attribution completed without skips. The baseline failure inventory
is 28 ordinary cases plus two guards:

| Baseline owner | Matched ordinary failures | Exact assertion / state boundary |
| --- | ---: | --- |
| FBZ→SOZ timeline | 1 | false→true insta-shield registration on restore |
| MHZ authored / paired / wide routes | 4 | Tails/team pulley ownership, actual timeline reset, wide zone-runtime bytes |
| DEZ incoming / final routes | 7 | ending centreX or death at 26706/26750/53897 |
| LRZ cold / boss / Knuckles / Tails / wide | 8 | death at 36526/19460, Hidden Palace centreX 1069→899, boss centreX 2796→524 |
| SSZ paired / solo / Tails routes | 7 | deaths at 7311/7671, Tails destination centreX 48→0, object replay at 4018 |
| S1 audio CLI environment check | 1 | safe help expected exit 0, observed exit 4 |

Every failure message matches exactly, except the SSZ Tails 800 object replay's
ordinary Java identity strings. Its full retained detail (not just the collector's
2048-character message prefix) matches the full baseline assertion after replacing
only `RewindObjectStateBlob@<hex identity>` values. Missing jet-flame/gun-arm
children, used slots 24/29 and both remapped object slots are identical; no physics
or state fields were normalized. The trace-dimension baseline ran both 400/528
cases successfully, confirming the two dimension failures were new regressions.
The owned ordinary baseline groups ran 65 cases total with 28 failures, zero errors
and zero skips; stock-title and sample-platformer attribution are recorded above.

The baseline guard invocation was:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  "-Dtest=TestArchUnitRules,TestObjectPhysicsStandardizationGuard" test
```

It ran 62 checks: two failures, zero errors/skips. The exact existing failures are
`LrzFlameObjectInstance.getShieldReactionFlags()` lacking its touch profile and
`Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_BASE` hard-coding a virtual
pattern ID inside the registered title range. These unrelated owners are retained.
The new architecture violations received the complete follow-up guard run
recorded below; no baseline failure is counted as a passing check.

The targeted repair follow-up on the `59ce8309b` tree plus the recorded ownership/
render-cache patch ran the nine mod/scene/registry/graphics classes and both
`TraceCaptureBootDimensionsTest` cases: 159 passed, zero failures/errors/skips.
This reran all 20 fresh act/character/viewport routes and all four complete local
pairings, as well as normal boot, online JVM integration and non-keyframe rewind.
The atlas follow-up ran 47 checks across slot reclamation, range registration,
lookup, dynamic ranges, dirty CPU/GPU uploads, page ordering and shadow batches;
all passed without skips. Commands used the queue and absolute ROM properties:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestGolfScenePresentation,TestRewindRegistry,TestGraphicsManagerHeadless,TraceCaptureBootDimensionsTest" \
  "-Dsonic1.rom.path=${S1_ROM}" "-Dsonic2.rom.path=${S2_ROM}" "-Ds3k.rom.path=${S3K_ROM}" test
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestPatternAtlasSlotReclamation,TestPatternAtlasRangeRegistration,TestPatternAtlasLookup,TestPatternAtlasDynamicRanges,TestPatternAtlasDirtyUploads,TestGraphicsManagerPatternAtlasPageOrdering,TestShadowBatchAtlasPages" test
```

The first command as invoked also contained the unmatched selectors
`TestPatternAtlas,TestPatternAtlasBatchUploads`; they added no checks. The 47-case
invocation above exercised the actual maintained atlas classes separately.
The ordinary broad suite was not repeated after these bounded repairs: unchanged
physics/route failures were attributed by matched tests, and the affected mod,
render/upload and rewind paths were rerun directly. The complete structural guard
follow-up is recorded separately; targeted passes are not a full-suite pass.

The complete queued `-Dmse=off -Pguards test -B` follow-up ran 672 checks:
670 passed, the two exactly matched baseline guards remained, and there were zero
errors/skips. All four new architecture failures disappeared. The separate
`-Pguards -Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy`
invocation passed 26 checks without skips. Candidate 0.7 signatures and release
policy, frozen architecture files and top-level dependency ratchets are unchanged
by the repair. Only a stale rendering-field comment was removed afterward; no
behavior changed after these checks.

Both category run directories were acknowledged and deleted after their results,
skips and attribution were recorded. The owned detached baseline checkout was
removed after verifying its exact base, completed commands, clean tracked/
untracked state and known generated links/output. The three worker checkouts and
branches had already been accounted for and removed. Provider-owned saved
conversations and the native capture directory remain preserved. Main `develop`
remains at `fc4729c375`; its dirty disassemblies and unrelated untracked files are
preserved. The remote develop ref advanced independently during the task and was
not reconciled here: the user explicitly required this concept/implementation to
remain local, without merge, push, or removal of the retained concept worktree.

The first repair commit attempt was rejected by the existing API-pin hook. The
internal `GraphicsManager.scenePatternSample` getter had accidentally published
packed atlas residency as creator API in the initial golf implementation. The
scene consumer now uses an unannotated engine presentation bridge; the getter is
package-private and `PatternVersion` returns to its original engine-only status.
The actual candidate snapshot will be regenerated to remove that lookup/type,
without changing the 0.7 candidate version/status or any published pin. This is a
contract tightening within the unpublished candidate, not a hook bypass or a
synthetic pin edit. The creator mod consumes bounded ROM-backed scene values and
never called the internal sampler.

The normalized snapshot regeneration removed exactly 16 rows: the public sampler
method and `PatternVersion`'s type/annotation/record/member rows. It added none;
19,385 canonical rows remain. The post-tightening scene/headless-graphics/online
invocation passed 52 checks without errors/skips. Its unused `TestSdkJavadoc`
selector added no checks; the real `TestModApiJavadocTool` and
`TestModApiSdkPackager` are invoked separately for the final candidate contract.

Final post-tightening contract/architecture verification ran 132 checks: 131 passed
and only the exact inherited S1 title-range assertion failed; zero errors/skips.
This invocation included the API surface/pin/release policy, all ArchUnit rules,
source budgets and architectural review guard. The actual SDK Javadoc and package
boundary classes separately passed 13 checks, zero errors/skips:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  "-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestArchUnitRules,TestArchitecturalSourceGuard,TestArchitecturalReviewGuard" test
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestModApiJavadocTool,TestModApiSdkPackager" test
```

No hook, frozen architecture baseline, release descriptor or published pin was
modified to admit the repair. The candidate pin records the real internal-sampler
API removal. The preceding completed broad run and matched baselines establish
the inherited failure inventory; the final focused runs establish the repaired
paths and narrowed candidate contract. There is no full-suite-green claim.

### Local delivery and cleanup

Production code and the final candidate contract are committed as
`9653aba62cbbb55f3f2a8b1fd3f2fa1c2fc379cb`, following the complete implementation
`b69b8fc3c` and compatibility fixture/title correction `59ce8309b`. The final
normal build at 9653 used `python3 examples/putt-putt-paradise/build.py` and passed
ordinary SDK package validation. The jar is 135,844 bytes with 83 Java classes and
only `META-INF/openggf-mod.yaml` as a non-class resource; it contains no ROM bytes
or art/music assets. SHA-256:

`5d623df9509fff5c56e652859a4318f93407c35e0891a6b4ed31e82ae80dfadd`

The artifact remains at `target/putt-putt-paradise/putt-putt-paradise.jar`; launch
with the existing example README's `--run --rom` command. The build records the
actual matching local development contract; released 0.6/native-image engines are
outside its supported scope.

The owned worker and detached baseline worktrees/branches are removed, with their
changes/results accounted for. Consumed root task logs, scratch classpath/commit
messages, category-plan output and CPU menu previews were inspected and removed;
ordinary category diagnostics had already been acknowledged. Native framebuffer
captures, the final jar, root concept worktree/branch and provider-owned histories
remain. The private temporary-agent run is closed with a lifecycle cleanup receipt
outside the repository. No task worker or queued Maven invocation remains.

Main `develop` is preserved at `fc4729c375de0273659d262f89cfb6966acc6308`, with its
original dirty submodules and unrelated untracked files. This work is local only;
there was no merge or push. Earlier concept commits already pushed before the
workspace correction remain in shared history. The ordinary suite's 28 matched
baseline failures and two inherited guards are recorded above, without a claim
that the final whole suite is green. Remaining route/parity axes are explicit in
the linked validation matrix.

### Development-launch Mod Manager follow-up (2026-10-06)

Opening Mods from the local launcher failed in `ModSubsystem.createManager`.
The development descriptor was enabled and trusted correctly; the failure came
from treating the deliberately absent persisted-state editor as a disabled
subsystem. `ggfmod run` now creates a frozen manager view of its startup state.
It exposes the active row, Details, Notices and Back; normal installed-mod
enable/disable, ordering and Apply retain their existing behavior. The frozen
editor rejects settings changes and saves and has no filesystem store. Disabled
deterministic startup still cannot create a manager.

The boot regression first reproduced the reported exception with
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestDevelopmentModBoot' test`:
two tests, one expected failure, no errors/skips. Final focused validation on
`049658d5d` plus this follow-up passed 88 tests, no failures/errors/skips:

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestDevelopmentModBoot,TestDevelopmentModSource,TestPendingModStateEditor,TestModManagerScreen,TestModManagerScreenNativeGuard,TestModManagerScreenHost,TestMasterTitleSecondaryActions,TestChildMenuFeedback,TestExternalContentPolicy,TestGgfModCliCommands' test
```

A separate contract/architecture JVM ran 69 checks, with 68 passing, no
errors/skips, and the already-recorded exact
`TestArchUnitRules.virtual_pattern_base_fields_are_backed_by_pattern_atlas_range`
failure for `Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_BASE` at
`0xd4000`. Its owning source, range declaration and assertion are unchanged
against the pre-follow-up commit. All API signature, pin, release policy and
engine-wiring checks passed; no creator API or pin changed.

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModEngineWiringSeams,TestArchUnitRules' test
```

The change-based plan against `049658d5d` selected all 2,976 ordinary classes
because the root launcher and shared manager paths use its fallback. Proportionate
focused validation replaces that run: the change is confined to manager creation
and its frozen input/render state, with direct tests of boot, navigation,
persistence refusal, deterministic suppression and the existing editable flow.
This is neither a full-suite pass nor a new native GUI capture. The local launcher
remains executable and uncommitted; Bash syntax and dry-run checks pass. The main
workspace, remote history, feature branch and worktree remain preserved.

### Aiming-guide follow-up (2026-10-06)

The user reported that putt/chip dots did not reflect the selected angle. The
preview read the inactive AIM meter, which remains at zero, and selected power
100/1000 instead of a reference shot. That speed made even elevated chips fall
almost immediately. The guide now uses a stable half-power reference (speed
`0x640`, 6.25 pixels/step), with the same surface-relative facing/loft projection
as `CourseControl.launch`. It also applies the launch's standing-to-rolling centre
offset without mutating the sprite. Dots have a dark border for visibility over
clouds, remain fully within the viewport, and label the reference power. Shot
physics, the meter, score, sounds, networking and creator API are unchanged.

The initial five literal-coordinate regressions failed on the original preview.
After correcting power, seven expanded cases reproduced the independent launch
origin error. Final checks on `847e13ccd` plus this follow-up passed 36 tests,
zero failures/errors/skips, with `SONIC2_ROM_PATH` identifying the existing
verified S2 REV01 ROM (the portable variable below represents that absolute path):

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestPuttPuttParadise#aimingGuideMatchesLaunchOriginAtReferencePowerAndSelectedAngle+guideDoesNotQueuePartiallyClippedDots+chargesCommitOnceAndReleaseIntoNativeRolling+aimingHoldsEveryCourseSubsystem,TestGolfMenu,TestGolfModel' "-Dsonic2.rom.path=$SONIC2_ROM_PATH" test
python3 examples/putt-putt-paradise/build.py
```

Coverage includes Sonic/Tails, both facings, loft 0/15/45/75, declared surface
angles 0 and +/-45 degrees, widths 320/400/800, viewport clipping, unchanged
full-course snapshots across overlay rendering, native putt/chip release,
held aiming, and the model/menu regressions. The build passed the normal SDK
packaging boundary. The refreshed jar has 83 classes, only its manifest as a
non-class resource, size 135,957 bytes, and SHA-256
`5efe0e64858888ddebf02c795b560446c03a0ad0ff0ba819c118fea98eb4776e`.

Native OpenGL before/after captures are retained outside the checkout under
`~/scratch/gameplay-captures/putt-putt-paradise-guide-20261006/`: six views per
version at 320 Sonic and 800 Tails, 24 PNGs total. Their CSV ball/angle/camera states
match exactly before/after. These are explicit `GolfModule` fixtures wrapping the
ROM-backed S2 module through `HeadlessGameBoot`, then the real controller scene
and overlay render path; they are visual evidence, not another production SDK
boot or traversal claim. The inspected final captures show distinct loft arcs
and outlined markers against the clouds. The temporary probe is reproducible
from these inputs and the existing boot/render helpers and is removed.

The change-based plan against `847e13ccd` again fell back to all 2,976 ordinary
classes for the example/root-launcher paths. Proportionate focused verification
replaces that run: only creator preview geometry/rendering changed, and its
inputs, clipping, course non-mutation and native launch consumers were exercised
directly. No full-suite pass is claimed. The guide remains AIM-only and does not
predict terrain/object collision or charged power; those limits are recorded in
the act matrix and example README. All changes remain local; the executable
launcher stays uncommitted and main `develop` remains preserved.

### ROM-themed title follow-up (2026-10-06)

The user requested more Sonic theming with original ROM assets. Against local
base `508dcba4a`, the creator title now uses the existing Sonic 2 title decoder
for its landscape, four palettes, winged emblem, Sonic/Tails final portraits,
hands and animated sparkles. A half-size emblem leaves space for a red/gold
Putt Putt Paradise banner, Sonic 2 Mini Golf subtitle and blue option panels.
The horizon scrolls; the landscape repeats across the advertised wide presets.
Setup/input behavior is preserved. Re-entering the title invalidates its GPU
cache so course palettes cannot leak into it. ROM-derived art remains in memory,
with no extracted resources in the package and no new engine/API contracts.
The existing title decoder/mapping helpers are internal engine dependencies,
like the example's existing `GameServices` use, with no new compatibility promise.

The first SDK packaging check rejected computed static atlas-base fields under
the creator static-state policy. Those values now belong to the renderer
instance; the normal verifier passes without relaxing the policy. No duplicated
ROM decoder or independently baked artwork was needed.

Final focused verification completed at 01:17 BST on this base plus the follow-up:
15 tests, zero failures/errors/skips. It covers five ROM-backed viewport widths,
both character banks, the emblem and full-width background, drawn tile bounds,
steady-frame palette cache reuse, re-entry restoration, every existing menu
input/setup check, normal development-directory boot through title selection,
SDK validation and the package's ROM-free resource contract. `SONIC2_ROM_PATH`
below denotes the verified existing absolute path of the S2 World REV01 ROM.

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestGolfMenu,TestPuttPuttParadise#titleRemixesRomArtAcrossWidthsAndRestoresPalettesOnReturn+artifactPassesNormalSdkPackaging+packagedPatchUsesOnlySonicAndTailsWithoutBundlingRomAssets+normalDevelopmentBootUsesStockProfileAndTitleSelectionBeforeEntryFade' "-Dsonic2.rom.path=$SONIC2_ROM_PATH" test
python3 examples/putt-putt-paradise/build.py
```

The refreshed SDK-validated jar contains 84 classes and only its manifest as a
non-class resource: 138,989 bytes, SHA-256
`099fd07284479ed97b9e51c26d86fe63ba14c38a6c89fba0560aaadadbafca86`.
Native OpenGL captures from that jar are retained under
`~/scratch/gameplay-captures/putt-putt-paradise-title-20261006/`: seven PNGs each
at 320 and 800 pixels, showing the mode picker, four setup screens, Join editor,
and a second sparkle phase. Header pixels differ between neutral menu ticks 0
and 8. The images were inspected for readable fields and correct ROM art. These
are explicit title-provider fixtures in `HeadlessGameBoot`, not stock intro
animation parity or a separate production SDK boot claim. The temporary probe
is removed after use; retained fixture notes describe the capture inputs.

The change-based plan against `508dcba4a` falls back to all 2,976 ordinary classes
for external example/root-launcher paths. Proportionate focused verification
replaces that run: changes are confined to creator title rendering and reuse
the existing ROM-loading/cache owners. Asset loading, viewport layout, cache
lifecycle, real title inputs/launch and native graphics were exercised directly.
No full-suite pass is claimed. The work remains on the local concept/mod branch;
the executable launcher stays uncommitted and no integration or push is made.


### Spring and wall obstruction follow-up (2026-10-06)

Against local base `c20731b13`, the user requested side activation for upward
springs and a vertical chip that can advance after clearing an obstruction.
The golf mod registers the namespaced `putt-putt-paradise:up-spring` factory
through `ModContext` and tags only untagged upward Obj41 placements. Every
native placement field and the table order are preserved. `GolfUpSpring` uses
the existing ROM-backed native spring implementation; its upward impulse helper becomes protected without changing
stock execution or the published/candidate Mod API surface. Native
`Obj41_Up` / `loc_189CA` in `docs/s2disasm/s2.asm` checks top standing only.
Golf side entry recovers incoming horizontal/inertia values after native solid
separation, clears the pushing/support latches and omits the top-only +8px
Y correction. Native strength, subtype effects, art, animation and sound stay
with Obj41. The ascent may touch the housing again: momentum survives that
contact, but the upward impulse fires only once per continuous side contact.

An initial player-wide `springing` lock was rejected. The short regression
showed that all four character/facing combinations failed to fire this spring
when arriving under another spring's input lock. A per-object scalar contact
latch now resets on separation and survives recreation/capture/restore. The
test counts exactly one impulse and replays both pre-contact and active-contact
checkpoints. The original native top-contact two-bounce check remains intact.

Loft now clamps at 90 degrees in aim, overlay and wire requests. Existing
0–75-degree shots use the unchanged `CourseControl.launch` contract. Steeper
shots reuse its native curl/centre/support/sound setup at 75 degrees, then apply
the creator's chosen surface-relative velocity before the admitted physics
step. At 90 degrees, full normal speed combines with tangent bias of one
sixteenth shot speed (minimum `0x40`, 0.25px/step). While airborne and rising,
a zero X velocity retries the facing bias; collision still owns separation.
No retry occurs during descent, damage/death or native spring control, and
nonzero bounce velocities are preserved. The AIM half-power guide shares the
departure calculation and includes the small 90-degree bias. New bounds,
constants and spring/retry rules enter the direct-connect fingerprint.

A bounded code review rejected the initial native-ID registry interception.
First, omitting the inherited `Supplier<Object>` capability dropped delegated
creator fault containment; the regression exposed a raw exception instead of
`CallbackAborted`. Forwarding that capability repaired inherited callbacks,
but further production-path review showed that the golf springs themselves
remained unowned: untagged placed spawns explicitly clear their callback owner,
and an explicit-patch-only plan creates no backing content registry. Classloader
fallback only covered dynamic/inherited registrations. The final namespaced
factory uses the existing backing-first composition and trusted registry owner
lookup instead. Direct fixtures now compose that backing patch too. The normal
development loader checks owner scope on a real placed spring, then forces
fresh rewind reconstruction and checks its callback scope again. No public API,
numeric mod ID, runtime art fallback or engine ownership algorithm was added.

Initial focused red checks reproduced the 75-degree cap, invalid 90-degree
wire request and blocked spring-side entry. Subsequent checks cover real ROM
spring entry for Sonic/Tails and both facings, native top returns, 85/90-degree
release, literal guide coordinates, a 64px controlled solid wall with initial
collision stop and subsequent clearance, whole-course restore/replay, and
explicit stock rolling-side contact that must not fire. The solid fixture is
not a claim about a specific EHZ terrain wall. The existing full fresh courses
and all local character pairings passed unchanged. The two-JVM test now submits
a guest-selected 90-degree shot and checks host acceptance, physics, views,
scores, pause ownership, permanent guest HOLD and worker/port teardown.

The combined change-based plan selects all 2,976 ordinary classes plus guards.
Because this follow-up changes creator shot/contact physics and registry
composition, normal broad validation is retained. Preflight initially rejected
the default Lua version; selecting existing `/usr/bin/lua5.4` passed Java 21,
Lua 5.4 and PowerShell checks. The stated reference cost is about 24 minutes
ordinary plus 10 minutes guards. The earlier complete run actually used two
ordinary workers and a 65-minute invocation limit; this follow-up uses that
established shape, retaining the 10-minute no-output limit. An initial
single-worker/default-limit attempt (`20261006T004753Z-65cfbfe0`) was cancelled
before lane completion when that prior evidence was read. Its status was
incomplete, no final results inventory was written, and its diagnostics were
inspected and acknowledged. It contributes no passing-suite claim. Baseline
comparison, final commands/counts and packaging outcome follow below. All work remains local;
the user's executable launcher is preserved uncommitted.


The completed broad command on `c20731b13` plus this follow-up's pre-registration
candidate was:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base c20731b13 --workers 2 --max-minutes 65 --run
```

Run `20261006T004949Z-5d7e82af` completed 2,974 ordinary reports / 25,686 cases
in 2,973.22 seconds: 29 failures, zero errors and 41 inspected skips. All 87
then-current golf integration cases passed without skips, as did the two-JVM
90-degree guest shot and the 12 stock spring checks. The 28 existing ordinary
failure identities and assertions match the prior bounded attribution above;
the remaining failure was the first 320px EHZ1 native-framebuffer comparison
with 24,783 waterfall pixels differing. The 86-report guard lane completed
672 checks in 211.10 seconds, with the same two existing LrzFlame touch-profile
and S1 title virtual-pattern failures, zero errors/skips. This is a completed
red broad run, not a full-suite pass. The skips include opt-in diagnostics,
12 unavailable Infinite Sonic platform placements, four unavailable native
GL/EGL checks, the maintained CPZ assumption and missing audio-reference inputs;
none are missing-ROM golf coverage.

The waterfall pixels differ only by a four-color palette rotation. A read-only
review found that `GraphicsManager.clearPaletteTextures/resetState` preserves
`paletteUploadLatched` and pending uploads. Earlier S3K presentation can set
that latch; S2 lacks the sprite-table publication profile that releases it.
These paths are unchanged at `c20731b13`. The initial queued immediate 15-class
prefix was cancelled before execution when this owning predecessor was found.
The bounded paired comparison uses `TestSonic3kUnifiedAudioPresentationRomIntegration`
and `TestGolfScenePresentation` in one reused fork on the detached base and
candidate, with all three discovered absolute ROM properties. Its outcome,
final registration regression and packaging results are recorded next.


The paired palette reproducer ran 29 selected cases on detached `c20731b13`
and the corrected candidate. Both failed only
`TestGolfScenePresentation.locallyComposedSceneMatchesTheProductionFramebuffer[1]`
with the identical 24,783-pixel assertion, zero errors and no selected-case skips.
Source review points to preserved palette-publication latch/pending state; this
follow-up leaves that unrelated shared rendering owner unchanged. Selected XML
reports, rather than the full raw report directory, own the paired comparison:
older failed invocations can leave unrelated reports behind.

The new production-owner regression failed before the correction: one case,
one failure, zero errors/skips, expected `putt-putt-paradise`, observed null.
After namespaced registration, the final focused command was:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestGolfScenePresentation,TestSpringObjectInstance" \
  "-Dsonic2.rom.path=${S2_ROM}" test
```

It completed 161 cases with zero failures, errors or skips (91 seconds), including
86 external-mod integration cases. The full native placement fields and ordering
match the ROM in both acts; production callback scope survives fresh spring
reconstruction. All character/viewport fresh courses and local pairings,
side/top springs, vertical wall clearance, whole-state replay, guides, normal
boot, SDK validation and two-JVM online acceptance passed. This focused follow-up
covers the bounded creator registration correction after the preceding broad
run; it is not a second full engine suite. The engine's only production change
remains the already broadly checked native spring helper visibility.

The normal SDK packager built 86 classes and only `META-INF/openggf-mod.yaml`:
143,125 bytes, SHA-256
`19a5e2244cdc0567f97b7be3f81f1391aa38cba91f73f08774f74ffdae442861`.
No ROM, extracted artwork or deleted registry-interception class is bundled.
The package uses this worktree's engine classes, already built by the completed
broad/focused commands; final bootstrap metadata is refreshed after the local
commit. Main `develop` remains at `fc4729c375`; no merge or push is authorized.


### Dream Course shot follow-up (2026-10-06)

Local task base: `6c210d4821a6940ecee32b05e9e4c9f7e1dc8fd8`. The initial
bank-or-boost draft was superseded before delivery when the user instructed
“Just mimic the KDC shot mechanics.” Its owned prototype was removed. The user
also pointed out that B/C are not default keyboard bindings. Required shot
controls therefore use A and the already mapped aiming arrows.

Reference: Nintendo's Kirby's Dream Course manual, sections 7–8, available at
[manual mirror](https://manuals.plus/m/67c7da172a34ce3239bc014f14a9514b42e3772456425496419e9a37805b3ddf).
Chips time the top/backspin marker, then power; putts proceed straight to power.
The power gauge rises and falls once, full power highlights pink, and expiry
makes a light shot. B remains an optional cancellation shortcut, C an optional
survey shortcut. Neither is required to shoot. Guide power changes to the
manual's full-power reference. No bank/boost choice or risk bands remain.

The control flow follows the manual; clock lengths and the spin impulse are
explicit Sonic adaptations, not claims of SNES physics parity. Chip spin scales
its departure tangent and adds one capped impulse on first unassisted ground
contact; neutral spin retains the existing native departure and rolling. All clocks,
edge latches and pending spin must rewind. Native spindash audio/pitch stays
owned by the game. The immutable local/wire shot becomes power plus signed
spin, with a new wire schema and fingerprint to reject old peers.

Verify engine-free timing, held edges, soft-shot expiry, both spin directions,
pose/HOLD behavior, pause/rewind, complete EHZ routes and two-JVM online shots.
Keep this work local, refresh the SDK package, and preserve the launcher.


The initial putt-flow regression failed on the old meter: expected `POWER`,
received `FIRST_CHARGE`. Eighteen engine-free model scenarios then passed, with
one-sweep expiry and marker/target restore included. Separate creator compilation
and signed-spin protocol checks passed. Code-drawn HUD geometry and pink-power
states passed at all five widths; the 320px queued-primitive raster was inspected
(the preview is not a native gameplay screenshot).

A read-only review caught two integration issues before delivery: guest dots
initially read the held guest world's camera/slope, and host replay initially
added a second startup charge even for guest putts. The first was replaced with
bounded authoritative TurnOpened surface/roll metadata plus accepted-scene
camera; the second schedules the extra startup request only for chips. Online
regressions now compare the authoritative guide basis, accept signed backspin,
and compare local/remote putt startup charge counts. The review also caught an
accidental TurnCommitted constructor edit while adding TurnOpened metadata;
its original signature is retained and separately compiled successfully.

The first native follow-up completed 176 cases: ten failures, one error, zero
skips. All twelve new spin/departure/landing-replay cases and the four-shot
online test passed. Eight EHZ2 routes exposed the one-unit difference between
`2 * floor(500 * phase / 60)` and `floor(1000 * phase / 60)`: the new sweep
shifted very light putt speeds. A new model regression observed expected 32,
actual 33, before correction. The single sweep now preserves the existing
two-unit power granularity; no native physics constants or per-route rules
were changed. All eighteen model scenarios then passed again.

The live/BK2 comparison's extra A during WATCH also exposed the recording
driver's synthetic forced jump, which bypassed ControlledFrameRuntime's neutral
input. RecordingFrameDriver now leaves action-edge authority with any active
session controller; stock recorded input retains its existing path. The
comparison retains that extra A as a regression. The damage/rewind authoring
was migrated to neutral chip contact then half power, instead of inadvertently
stopping the new marker at topspin. The remaining transport error was a
mismatched-engine fixture still constructing schema 1; it now uses the current
schema so the intended engine-fingerprint rejection reaches the room boundary.
The focused rerun adds stock recording input-only, hardware timing and BK2
action-edge checks with the existing absolute S3K ROM property.

The corrected focused run completed 191 cases with zero failures, errors or
skips (2m20s). Command:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestGolfScenePresentation,TestSpringObjectInstance,TestRecordingFrameDriverInputOnly,TestRecordingFrameDriverHardwareTiming,TestHeadlessTestRunnerBk2Input" \
  "-Dsonic2.rom.path=${S2_ROM}" "-Ds3k.rom.path=${S3K_ROM}" test
```

S2_ROM and S3K_ROM are the discovered existing absolute paths. The 98 external
mod cases include all act/character/viewport fresh routes, all local pairings,
contact extremes in both directions, single landing impulses with whole-state
replay, guide geometry, pause and live/BK2 equality. The two-process online
case completes four shots and checks signed backspin, authoritative guest
coordinates and matched putt startup charge counts. The additional fifteen
recording checks pass on the unchanged stock input path. Native PCM/pitch
waveforms and a continuous two-act online traversal remain outside this run.

Java 21/Lua 5.4/PowerShell preflight passed. The normal change-based plan selects
all 2,976 classes because creator example paths are unclassified. Flight and
landing rules plus the recording input boundary justify running the full
selection with two workers, a 90-minute limit and the standard ten-minute idle
limit. Prior broad failure identities/messages are retained in memory for
comparison; consumed prior diagnostics are pruned by the runner, not archived.

The normal broad command completed on the uncommitted candidate from
`6c210d4821a6940ecee32b05e9e4c9f7e1dc8fd8`:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 6c210d4821a6940ecee32b05e9e4c9f7e1dc8fd8 \
  --workers 2 --max-minutes 90 --run
```

Ordinary: 2,974 reports, 25,700 cases, 28 failures, no errors, 41 skips
(2,070 seconds). Fresh guards: 86 reports, 672 checks, two failures, no errors
or skips (212 seconds). All golf cases and the recording regressions passed
in this broad invocation. This is a completed red broad run, not a full-suite
pass. No new failure identities or first-error fields were observed against
the prior broad result recorded above.

Twenty-seven ordinary failure messages and both guard messages match the
retained baseline exactly. The remaining SSZ Tails replay failure matches test
identity, frame 4018, missing child IDs/slot fields and the stored 2,048-character
concrete prefix after normalizing RewindObjectStateBlob hash text. That hash
includes a Class reference and is not portable between JVMs; the current raw
XML message is 2,915 characters. This is a bounded failure-signature comparison,
not a claim that the complete opaque state or unretained message tail matches.
The previous order-sensitive GPU golf scene failure did not occur in this run.
Its earlier paired palette-latch evidence remains valid; no shared renderer
change was made here, so this pass does not establish that the hazard is fixed.

The 41 skips were inspected: opt-in diagnostics/captures, twelve unavailable
Infinite Sonic platform placements, four unavailable native GL/EGL checks, the
maintained CPZ spin-tube assumption, and missing/request-disabled audio
reference captures. No golf ROM coverage skipped. The broad invocation used
all three automatically discovered absolute ROM properties. Diagnostics were
inspected and deleted with `run_categories.py --acknowledge
20261006T033103Z-3e0b6bd6`; no raw output is archived.

Implementation is retained locally in `332f042ea1b7ae60e027742be807b6aa2536df21`.
The final creator sources compiled with `javac --release 21` against this
worktree's engine classes from the completed focused/broad runs, then passed
the normal `GgfModCli package` boundary. The refreshed jar contains 87 classes
and only `META-INF/openggf-mod.yaml` (147,329 bytes). The additional engine
compile requested by `build.py` was cancelled before Maven admission because
the shared queue lacked memory capacity; it is not recorded as a build pass.
The unchanged, uncommitted launcher still performs its normal queued engine
compile and bootstrap-metadata refresh when run. Main `develop` remains at
`fc4729c375`; this follow-up has no merge or push.

### Held duck follow-up (2026-10-06)

Local task base: `34e9a06af432039bac141faf23718e8a2bc3a37f`. The scene pose
projector took every script frame modulo the full frame count. In Sonic 2,
`SonAni_Duck` is `5,$4C,$4D,$FE,1`: after the entry mapping, only the final
mapping repeats. Duck projection now clamps at that final mapping after the
native entry delay. Spindash still loops; native gameplay animation, clocks,
pose values, API signatures and wire format are unchanged. Both local scene
capture and guest-local overlays consume the same corrected projector.

The new native-Down pixel regression failed in both facings at pose tick 12
on the base projector, then passed after correction. It compares the initial
entry and ten held-clock samples (including `Long.MAX_VALUE`) with native
Down input, checks both local and guest projection, and proves that the course
and accepted guest frame remain unchanged. The pixel oracle is deliberately
Sonic-only: an initial full-image Tails comparison also found pixel differences
already on the base, despite its single `$5B` duck body mapping. The cause of
those full-image differences was not isolated in this correction.
Existing Tails duck/charge and scene checks remain part of the focused run;
this correction does not certify full Tails pose-overlay pixel parity.

The inspected change-based plan selected 2,976 classes because the projector
and preserved untracked launcher are unclassified. Proportionate validation
uses the native regression and all scene/course/online mod consumers for this
bounded render-only frame-selection correction. No physics, timing authority,
mutable state or public contract changes require a second broad engine run.
The queued focused Maven request was cancelled before admission because the
machine lacked the queue's memory reservation. The installed JUnit Console
1.10.3 runner executed the real Jupiter tests with a bounded two-GiB heap,
using the existing worktree engine/dependencies and separately compiled changed
projector/test classes. Reproduction commands, with existing build output:

```sh
DUCK_CHECK=target/duck-pose-check
CONSOLE_JAR="$HOME/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.3/junit-platform-console-standalone-1.10.3.jar"
ENGINE_CP="target/test-classes:target/classes:$(cat target/putt-putt-paradise/engine-classpath.txt)"
mkdir -p "$DUCK_CHECK/classes" "$DUCK_CHECK/tmp"
javac --release 21 -cp "$ENGINE_CP:$CONSOLE_JAR" -d "$DUCK_CHECK/classes" \
  src/main/java/com/openggf/game/presentation/ScenePoseProjector.java \
  src/test/java/com/openggf/game/presentation/TestGolfScenePresentation.java
java -Xmx2g "-Djava.io.tmpdir=$DUCK_CHECK/tmp" \
  "-Dorg.lwjgl.system.SharedLibraryExtractPath=$DUCK_CHECK/tmp/lwjgl" \
  "-Dsonic2.rom.path=${S2_ROM}" \
  -cp "$DUCK_CHECK/classes:$ENGINE_CP:$CONSOLE_JAR" \
  org.junit.platform.console.ConsoleLauncher execute --include-engine junit-jupiter \
  --config junit.jupiter.execution.parallel.enabled=false \
  --config 'junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$ClassName' \
  --select-class com.openggf.game.presentation.TestGolfScenePresentation \
  --select-class com.openggf.mods.code.TestPuttPuttParadise \
  --select-class com.openggf.mods.code.TestGolfOnlineIntegration \
  --disable-banner --disable-ansi-colors --details summary --fail-if-no-tests
```

S2_ROM is the discovered existing absolute Sonic 2 REV01 path. The completed
run passed 128 cases with no failures, errors, skips or aborted cases (46.255
seconds): 29 scene checks, 98 actual mod/course checks and the two-process
online case. This is focused validation, not a new full-suite or guard pass.
The launch tree's projector was then compiled with Java 21 and its class bytes
matched the tested projector exactly. Creator sources and jar are unchanged.
The launcher remains executable, unchanged and uncommitted; work remains local.
Consumed JUnit XML/logs, probe classes and native extraction were inspected and
removed; the verified runtime projector remains in this worktree's build output.


### Shot rewind follow-up (2026-10-06)

Base `c9cd7965cfe5c465fe52ff0caf94d22f8d14e1c8`; work remains on the local
`feature/ai-putt-putt-paradise` worktree. The requested default is three rewinds
per golfer/hole and one per turn, with off/3/5/* and 1/3/* setup choices. A
rewind consumes the match allowance, refunds the pending stroke, retires that
shot ID and restores the complete pre-shot course for the same golfer/turn.
A bounded view-only recording plays backward while native physics is held;
longer shots traverse more source ticks, finishing within 90 presentation ticks.
Memory is capped at 128 samples/8 MiB, preserving both ends through decimation.

The implementation adds terminal review while an allowance remains: A keeps a
settled, penalty or finishing shot; rewind retries before scoring/turn/act
finalization. Exhausted/off allowances retain automatic resolution. R/default
rewind binding or primary bumper works live, with an A-operated Start-menu
command for all pads and Genesis movie replay. Raw input is suppressed under
logical overrides; movie rows cannot encode R/bumper. The mode disables live
developer rewind in every mode so it cannot bypass the quota. Direct diagnostic
snapshot/forward replay remains covered separately.

`CourseControl.rewindHeld()` supplies an immutable per-row value injected by
`ControlledFrameRuntime`. `InputHandler.isRewindHeld()` combines the configured
keyboard shortcut and primary bumper independently, including an unbound keyboard
key, and suppresses both under logical overrides. Generic unbound key queries
remain false, preventing phantom B/C input. Stock module stepping is unchanged.
The normalized 0.7 candidate pin adds these two boolean methods.
Policy/version stay at unpublished 0.7.0 candidate, consistent with the ordinary
candidate rewrite policy. The protocol advances to schema 3 with host-validated
rewind/keep intents, phase/budget publication, rewind receipts and monotonic
same-turn retry IDs; reconnect republishes phase and spent allowance. Matching
rewind rules are included in the rules fingerprint.

Rejected approach: static immutable `Rules` default/off objects failed normal
SDK packaging with `STATIC_STATE_UNSUPPORTED` (both fields and `<clinit>`).
Factories create the value under creator ownership instead; the validator was
not weakened. Historical route/parity fixtures select rewinds off explicitly,
while new regressions exercise default review and undo. Review routes stop at
the first actual finish rather than consuming unused route alternatives after
results. The pause-menu concession fixture now steps past the new Rewind row.

Focused verification used Java 21 `javac` for the changed engine/input and
root test classes, followed by JUnit Platform Console 1.10.3 against the worktree's
compiled engine and Maven dependency classpath. `TestGolfModel`, `TestGolfMenu`,
`TestGolfProtocol`, `TestGolfTransport`, `TestPuttPuttParadise`,
`TestGolfOnlineIntegration`, `TestModApiPinPolicy`, `TestModApiSignatureSurface`
and `TestInputHandlerLogicalSnapshot` completed **183 tests**, zero failures,
errors, skips or aborts (80.355 seconds). The existing absolute S2 World REV01
ROM path was supplied; tests compiled the actual creator sources and exercised
the normal SDK package validator. Native shot rollback covers Sonic/Tails in
both acts at width 320; finishing-shot rollback uses real Sonic routes in both
acts. Full native routes for every rewind-limit combination and other widths
are not separately certified by the pure allowance/menu matrix.

The independent review found one actionable input edge case: an unbound
keyboard rewind binding suppressed the controller bumper in the initial
`isKeyDown(configuredKey)` implementation. The dedicated semantic input query
fixes it without removing the negative-key guard. Two new input tests cover
unbound bumper/phantom-action safety and configured-key/override behavior.
The first broad run was interrupted for this fix; it is incomplete evidence,
and its diagnostics were removed automatically by the next launch.

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
c9cd7965cfe5c465fe52ff0caf94d22f8d14e1c8 --preflight` passed. Plain `lua` is 5.5,
so guard execution uses the existing 5.4 binary explicitly. The combined
`--run` selection is all 2,976 ordinary classes plus structural guards;
completed results are recorded below. The existing uncommitted launch script
is preserved, and no integration or push is performed.


The testable creator artifact was refreshed using Java 21 `javac` and the normal
`GgfModCli package --input target/putt-putt-paradise/classes --out
 target/putt-putt-paradise/putt-putt-paradise.jar` validator. Maven had already
compiled the current engine for the combined run; no second Maven command or
shared build directory was used. The jar contains 97 classes and only
`META-INF/openggf-mod.yaml` as a non-class entry (166,246 bytes); all art and
sound remain ROM-backed runtime inputs.


The combined category invocation `20261006T052424Z-cd971481` reached its
40-minute limit during `TestDezIncomingFinalRouteCapture`: 2,772 completed
ordinary reports, 24,178 cases, three failures, no errors and 38 inspected skips.
It is an **incomplete category invocation**, not a category pass. The completed
code, API pin and creator sources stayed unchanged after admission; only
validation/build evidence prose was added during execution.

To finish without repeating completed cases, the original 2,976-class plan was
compared with the actual completed report identities before compaction. The
remaining 244 candidate paths (including helpers/profile-excluded classes and
the interrupted class) were supplied to queued Maven through
`-Dsurefire.includesFile=<temporary remaining-includes.txt>`. This continuation
completed 202 reports / 1,541 cases in 24:16, with 25 failures, no errors and
three inspected skips. It also explicitly selected the two `@ArchTest`
network dependency-fence checks; these passed. No category selection policy
or original runner selection was changed.

A separate fresh JVM then ran the full structural profile:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

The queued commands supplied the existing absolute S1 REV01, S2 REV01 and S3&K
ROM properties and isolated report/tmp paths under this worktree's `target/`.
Fresh guards completed **86 reports / 672 checks**, two failures, no errors or
skips, in 3:33. Both messages exactly match the retained baseline:
`LrzFlameObjectInstance.getShieldReactionFlags()` lacks its touch profile and
`Sonic1TitleScreenManager.BACKGROUND_OVERRIDE_PATTERN_BASE` bypasses the atlas
range. The ordinary failure inventory remains the same **28 cases** recorded
above: 27 messages match exactly. SSZ Tails replay at frame 4018 matches the
retained 2,048-character prefix after replacing only `RewindObjectStateBlob`
hash text; the current assertion is 2,915 characters. This establishes the
same bounded failure signature, not equality of the unstored suffix. No new
failure identity or concrete first-error field was observed.

The 41 ordinary skips cover opt-in diagnostics/captures, unavailable native
GL/EGL paths, 12 Infinite Sonic acts without platforms, the maintained CPZ
assumption and absent audio-reference/capture requests. None of the golf
integration, controller-input or API checks skipped. All 107
`TestPuttPuttParadise` cases and both two-JVM online cases passed in the ordinary
invocation, in addition to the 183-case focused pass. This is completed
selected-check coverage through an interrupted broad invocation, its unfinished
classes and fresh guards; it is **not a green full-suite or category claim**.

The interrupted category diagnostics were inspected and acknowledged. Temporary
focused/continuation/guard XML, logs and probes are removed after consumption;
the SDK-validated local mod artifact is retained for testing. `git diff --check`,
launcher `bash -n`, builder dry-run and relative documentation-link checks passed.
The final change stays on `feature/ai-putt-putt-paradise`, with main `develop`
unchanged at `fc4729c375de0273659d262f89cfb6966acc6308`; no merge or push.


## Final polish and PR preparation (2026-10-06)

The final author-facing review found that the original course facade retained
Sonic/Tails, EHZ and 75-degree shot policy. The replacement contract exposes a
registered single-player state, an explicit module-owned level destination and
a bounded native rolling impulse. `GolfSwing` owns golf velocity/spin and native
sound requests. This removes the mod's old launch-at-75-then-overwrite workaround.
Character factories run before the live roster is replaced. Candidate 0.7 pins
are regenerated together; no published baseline or version policy changes.

The review also reproduced two defects: entering a duck after 180 aim rows
started at pose tick 181 rather than zero, and VERTEX drawing methods 4..7 were
accepted although the compositor supports 0..3. The regression-first focused
run failed both assertions with no skips. A captured creator pose clock now
starts each pose at zero and freezes during pause/room holds; scene values reject
unsupported methods before publication.

Mouse comparison uses Slay The Robotnik PR #211, source
`25b57f886aa748e27b229ff7a64393fd8a8f3e56`. Golf previously had no pointer
handling. Reuse its engine-internal `LogicalMouse` viewport mapper rather than
copying a golf-only mapper or importing its independent scene host/deck rules.
A small `MenuInput.Pointer` value lets module title screens use the same logical
coordinates. The mapper retains the same existing window entry point so the two
PRs can share it; pure coordinate tests cover letterboxing and framebuffer scale.
Logical movie input suppresses the physical pointer. Click/hover remains creator
menu policy.

Polish checklist:

- [x] General native controller operations, creator golf adapter and candidate pins.
- [x] Pose-clock and malformed-scene regressions; menu pointer integration.
- [x] Human reading path and one-shot/extension walkthrough in the example README.
- [x] Review the combined updated-develop diff and run focused and broad validation.
- [x] Capture real gameplay outside the repository, retain inputs/state/edit provenance,
      verify the reel, and prepare delivery through a develop PR without merging it.

Updated develop is `dee7a93c99f3984fc2d8cd3b425ca4315cc9c19b`.
Merge `6da29127a9a744ccea703561e6f0ddc52c70936b` reconciles the Infinite Sonic
scripted rewind hook and golf controller hook by retaining both delegations;
the release note retains the updated Infinite Sonic entry and golf's entry.
The main checkout fast-forwarded on its existing develop branch, preserving its
three dirty disassembly submodules and unrelated untracked files.

The final host review reproduced focus/keyboard pause advancing WATCH rows and
Escape's return fade stalling in AIM. `ControlledLevelIteration` now owns the
host pause/frame-step gate and services host return fades before creator work.
Start remains creator input. Both regression tests failed before the fix and
passed afterward with GameLoop, Escape, recording, PLC and signature checks:
155 tests, no failures/errors/skips. The earlier polish checks passed 243 tests
and the generic S3K controller/API follow-up passed 25 tests without skips.

Final user steering retains in-flight rewind and automatic turn passing. The
earlier terminal-review approach above is superseded: settlement/finish/penalty
resolves immediately, and charging/completed turns cannot spend an allowance.
The new settlement regression first failed with player one still active. The
creator removes its REVIEW/KEEP state and wire commands; schema 4 and the rules
fingerprint identify WATCH-only undo. Offline actual finish routes, late-input
rejection, Genesis pause-menu undo and two-JVM host/guest undo cover this policy.

Moving-video inspection found a further roster/view defect: the first Tails turn
held at camera `(86,645)` rather than the prepared `(0,560)`, placing the player
under the HUD during charging. `Camera.setFocusedSprite` initializes camera words
from sprite top-left; native physics eventually masked this when WATCH resumed.
The generic character capability now preserves the complete camera snapshot while
rebinding its target. The stock S3K regression failed first (expected `(0,912)`,
actual `(54,1037)`); local turn tests also assert the preserved prepared view.
The first broad invocation was deliberately interrupted before completion to fix
this defect; it supplies no completed broad validation claim. Its temporary
diagnostics were inspected and acknowledged. Fresh final captures and the combined
broad invocation follow the correction.

The camera correction passed the complete focused invocation:
`python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestCourseControl,TestPuttPuttParadise,TestGolfOnlineIntegration,TestCamera,TestModApiSignatureSurface'
-Dsonic2.rom.path=<absolute S2 REV01> -Ds3k.rom.path=<absolute S3&K> test`:
156 tests, zero failures/errors/skips. The new captures confirm that Tails keeps
`(0,560)` through AIM, SPIN and release feedback, with the same two-turn route
outcome. Both complete practice routes and the whole-shot rewind were recaptured
through the final engine capability. The SDK example build also passed.


The final combined run used `--base dee7a93c99f3984fc2d8cd3b425ca4315cc9c19b
--workers 2 --max-minutes 90 --run`, with Java 21, Lua 5.4 and all three existing
absolute ROM paths. Ordinary completed 2,976 reports / 25,735 cases in 2,113.25
seconds: 29 failures, no errors and 41 inspected skips. All 111 golf integration
cases and both two-JVM cases passed. Skips are the same opt-in/native/Infinite
Sonic/CPZ/audio-reference categories recorded above; none are golf ROM omissions.
Fresh guards completed 86 reports / 672 checks in 206.70 seconds: three failures,
no errors or skips. This is a completed red broad run, not a suite pass.

Two guards retain the known LrzFlame touch-profile and S1 title atlas failures.
The new guard rejects `control -> game` and `control -> graphics`: physical
pointer lookup had been placed directly in `MenuInput`. The corrected boundary
matches the existing host composition pattern used by `InputBindingFactory`:
`MenuInput` delegates to host-owned `MenuPointerRuntime`, which resolves engine
graphics and the shared mapper. The public pointer contract and mapper are
unchanged; no package-edge ratchet or guard is relaxed. Focused pointer/API tests
and the complete fresh guard lane verify this correction rather than repeating
unaffected native routes.

The S3K-to-S2 palette-order reproducer ran on pre-polish merge `6da29127a` and
the broad candidate: 31 and 32 selected cases respectively, each with only the
same 24,783-pixel framebuffer failure and no errors/skips. Its full assertion
matches exactly. The candidate scene class alone passed all 30 cases in a fresh
invocation. This retains the previously recorded graphics cleanup hazard; it
is not evidence of a mod simulation or shot defect. Updated-develop attribution
of the remaining native failures and the post-boundary guard result follow below.


Updated develop `dee7a93c99f3984fc2d8cd3b425ca4315cc9c19b` ran the 25 failed
native methods below through queued Maven with `-Dmse=off -Ptest-concurrent
-Dtest=<comma-joined selectors> test` and all three existing absolute ROM paths.
Isolated reports and temporary directories were under that checkout's `target/`.
It completed **30 cases, 28 failures, no errors/skips**. Every candidate failure
matches by class, parameterized case identity, failure type and full assertion.
Only `RewindObjectStateBlob@<hex>` identity text is normalized, as documented in
the measurement hazards. This fresh full-assertion comparison includes the
previously prefix-only SSZ assertion; no gameplay/state fields are normalized.

```text
com.openggf.tests.TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice
com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding
com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase
com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState
com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState
com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace
com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff
com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff
com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace
com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor
com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld
com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad
com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay
com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents
com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport
com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches
com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory
com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters
com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory
com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineCli#shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup
```

The baseline's separate fresh invocation,
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards
-Dtest=TestArchUnitRules,TestObjectPhysicsStandardizationGuard test`, also supplied
all three absolute ROM properties. It completed **62 checks, two failures,
no errors/skips**; both inherited guard assertions match exactly.

After extracting physical pointer lookup to the host, the queued focused command
`-Dmse=off -Dtest=TestMenuInput,TestLogicalMouse,TestGolfMenu,TestModApiSignatureSurface
test` with the absolute S2 ROM passed **34 checks with no failures/errors/skips**.
The complete fresh `-Pguards test -B` invocation, Java 21/Lua 5.4 and all three
absolute ROM paths, completed **672 checks with two baseline-matched failures,
no errors/skips**. The new package-edge assertion now passes. This focused repair
changes ownership of the same pointer computation, not shot/physics behavior or
the public API, so unaffected ordinary routes are not repeated after the completed
broad invocation. The candidate remains red on the inherited 28 ordinary cases,
the independently matched palette-order case and the two inherited guards.
No full-suite-green claim is made.

Fresh final-source captures retain the complete EHZ1 Sonic practice finish
(10 counted strokes plus a refunded rewind), EHZ2 Tails finish (25 strokes), and
a two-turn local Sonic/Tails sequence. Inputs and every state row match the
preceding corrected captures; all **7,104 native PNGs** also match byte for byte
while the provenance records the final Java source digest. The 80.5-second
30fps silent reel has 12 act-ordered chapters, full source videos, Genesis inputs,
state/events and an exact edit map outside the repository. Native sources and
the reel are fully decoded; frame count, chapter count, cadence, moving playback
and every cut are checked. The reel demonstrates practice/local play, not a
continuous online two-act completion or captured native audio.


Consumed category diagnostics are acknowledged and removed. Matched-baseline,
pointer/API and guard diagnostics are discarded after comparison. The owned
pre-polish baseline worktree is removed; its tracked/untracked tree was clean,
with only task test output, queue Python caches, a runtime-generated configuration
template and post-checkout resource links. The feature worktree and uncommitted
launcher remain for the PR; main stays on updated develop with its unrelated
changes preserved. PR creation/push is the remaining delivery action from this
prepared commit. No shared history is rewritten and no develop merge is requested.


### Final design and rewind-effect follow-up — 2026-10-06

The repeated final-polish request reviews PR #212 at `b2faa748f` against the same
updated develop base `dee7a93c`. Slay The Robotnik PR #211 remains at
`25b57f886`; its window entry point and golf's `LogicalMouse` contract align.
The main checkout remains develop and fast-forward pull reports already current.

Claude Opus 5.5 reviewed source and the cut contact sheet (not the whole movie).
Its useful design tips were a discoverable WATCH-only rewind hint, readable guide
panel, direct score totals, a scorecard without stale power values, a pure small
HUD example, and explicit hit-point/rewind beats in the reel. Its contact-sheet
inference that reverse playback was absent was superseded by the user's report:
the playback existed, but the VHS effect was missing. Raw mouse-driven shot input
is deferred: the shared menu mapper is unified, while a controller consumes
recorded logical Genesis input. Widening that contract just for optional mouse
shots would be disproportionate to this polish.

The focused source review found host/guest off-turn prompts using a stale local
shot meter. `GolfHud` now resolves authoritative phase and viewer ownership,
hides unavailable choices, and keeps these decisions external to the engine.
Menu hints reuse the shared last-intent keyboard/controller labels. The unchanged
departure calculation names its half-gravity constant and cites S2
`ObjectMoveAndFall` ($38 in native 8.8); guides still promise only departure.

The rewind regression failed on all four Sonic/Tails EHZ act cases: golf holds
physics and replays immutable scenes, so it never drives the developer rewind
effect envelope. The small optional `RewindPresentation` value now requests the
existing configured VHS pass; it grants no seek/restore/allowance operation.
Stock/null requests are NONE, host pause holds the effect, and ambient gameplay
bindings work too. The normal capture renderer uses the same pass, lifecycle and
settings. Correct semantic rewind scroll direction is used by both render paths.
Local replay and authoritative remote REWINDING choose the request in the mod.
Candidate signature pins and `ModApiVersion` are refreshed while the release descriptor is retained;
version/status stay unpublished mutable 0.7.0 candidate.

Focused verification completed with 158 tests, zero failures/errors/skips:
`maven_queue.py -Dmse=off -Dtest=TestPuttPuttParadise,TestGolfMenu,TestGolfOnlineIntegration,TestModApiSignatureSurface,TestOwnerBoundGamePatch,TestRewindVhsEffectPass,TestRewindEffectEnvelope,TestCourseControl test`, with absolute S2/S3&K ROM properties. The four-act effect regression also checks ambient binding and both host pauses; two actual peer JVMs check online HUD ownership. Candidate pin has 19,429 signatures. The full ordinary/guards selection remains 2,978 classes; preflight passed with `LUA_BIN=/usr/bin/lua5.4`. A fresh `-Dtest=TestGolfScenePresentation` invocation passed 30 checks with zero skips, avoiding the previously documented reused-fork palette hazard. The ordinary example `build.py` succeeded and SDK-validated the code-only jar. Real-input footage is being recaptured with the actual VHS pass, effect intensity/speed in its state CSV, and same-frame raw/effected images for pixel evidence. The complete change-based command is `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base dee7a93c99f3984fc2d8cd3b425ca4315cc9c19b --workers 2 --max-minutes 90 --run`.
Earlier inherited baseline failures remain recorded above; CI at `b2faa748f`
passed ordinary tests and policy but failed the two inherited guards plus an
unchanged terminology-guard subprocess timeout. No full-green claim is made.


The user then identified stationary Tails disappearing in the local-play footage.
A read-only production capture probe established the owning state: after the
Sonic-to-Tails turn switch, body mapping 0 is blank, parent animation 0 has not
run, and Obj05 is uninitialized (`mappingFrame=-1`). AIM holds the world, so it
cannot repair itself until launch. Custom duck/spindash projection also omitted
the separate appendage. This is a presentation problem, not a 2P physics gate.

The mod chooses the generic ROM-backed IDLE view only for an unmoving,
nonrolling AIM golfer, including the first frame of `openTurn`; settled balls
keep their native pose. The view clock selects native idle/duck/charge mappings
and Tails' appendage using shared internal tail scripts and the module's existing
separate-art capability. No player animation, DPLC clock or physics is advanced.
Pre-shot replay origin scenes also retain a visible idle golfer. Guest AIM preserves the host scene instead of consulting its frozen local
rolling flag; a two-JVM fourth-turn check covers a settled rolling lie. The candidate
adds only the IDLE enum value; the shared tail selector is pinned as engine
internal rather than becoming another creator operation.

The new four-roster regression failed three cases before repair (missing body
or appendage), with no errors/skips. The broader follow-up invocation was
interrupted to include this confirmed defect: 2,774 partial reports / 24,188
cases were available, with four failures, three errors and 38 skips, before
completion. Those partial diagnostics are consumed and acknowledged; they are
not validation results, and guards did not run. The combined candidate will
receive fresh change-based validation after the sprite fix is focused-tested.

The user also asked Claude Opus 5.5 to create a new public-facing promo with
transitions, information and action-led feature coverage. That separate media
work is delegated outside the repository, with a reproducible source-root
option so the repaired native captures replace the draft footage. The older
80.5-second technical reel remains separately identifiable.

The focused tail review found the guest AIM policy defect before recapture: using
its frozen local player flag would project a standing body over the host's rolling
lie. The mod now retains the authoritative scene during guest AIM. The review
also requested the native Wait loop boundary; a bounded test checks the first
and later `$FE,$1C` repeat and an interior frame without advancing the world.
The first wider focus exposed a missing engine-internal registry entry for the
new shared tail selector (168 passed, one signature-terminal pin failure, no
skips); the registry and exact pin are now synchronized.

Opus delivered a reproducible 49.97-second public promo draft, with action-first
Sonic/Tails hooks, putt/chip/spin/rewind chapters, physics montage, alternating
local shots, a separate online information card, OpenGGF context and a source
CTA. Iris, stripe, push and flash transitions preserve source cadence. The draft
is visibly labelled v4 and cannot be rendered as final with v4 inputs; state-range
hashes reject drift when swapping the corrected v5 captures. All original
geometric cards and render/verification scripts remain outside the repository.

Final focused verification after the guest correction completed 169 cases with
zero failures/errors/skips; fresh scene verification completed 37, including
the idle-loop boundary, also with no failures/errors/skips. Commands were the
queued `-Dtest=TestPuttPuttParadise,TestGolfMenu,TestGolfOnlineIntegration,TestModApiSignatureSurface,TestOwnerBoundGamePatch,TestRewindVhsEffectPass,TestRewindEffectEnvelope,TestCourseControl,TestSpriteManagerMainTailsTailsDispatch,TestTailsTailsDirectionalAnimation,TestTailsTailsFlightSelection`
and a separate `-Dtest=TestGolfScenePresentation`, using absolute matching S2
and S3&K ROM paths. The example build succeeded through its SDK boundary.
Candidate surface contains 19,430 signatures; public presentation changes and
the shared internal terminal are synchronized. Fetch confirmed develop is still
`dee7a93c99f3984fc2d8cd3b425ca4315cc9c19b`.


### Opus Extra High presentation and synchronized readiness — 2026-10-06

The completed pre-Opus combined run `20261006T100417Z-70558f13`, against
`dee7a93c`, selected all 2,978 ordinary classes. It completed 25,745 cases
with 29 failures, three errors and 41 inspected skips; fresh guards completed
672 checks with the two inherited failures and no errors/skips. The 29 failure
identities and first-error fields were unchanged from the recorded prior run;
this is not another fresh full-assertion baseline comparison. The three new
errors all rejected a comment in the strictly key=value release descriptor.
Removing that comment retained the original descriptor; the queued
`TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModApiSignatureSurface`
repair passed all 27 checks without skips. The consumed category diagnostics
were acknowledged and deleted. This records a red broad run followed by a
bounded metadata repair, not a green full-suite result.

The user then requested a concrete Opus 5.5 Extra High implementation pass on
the mod, documentation and public promo, with Slay The Robotnik as a reference.
The presentation remains external: `GolfFeedback` consumes explicit events and
returns ROM sound cues; `GolfMotion` supplies easing; `GolfCanvas`/`GolfCards`
render panels, turn cards, toasts and a results tally. The mod owns those sound
choices and timelines, animated guide brightness, the short shot trail, HUD
slides and power-lock feedback. Shared engine code gains no golf score or
transition policy. The example reading path and small extension exercises now
include these files.

Incoming competitive golfers wait for a fresh action press, separately consumed
from the next shot. The outgoing shot still settles and passes automatically.
`TurnReadiness` is captured with the mode, identifies owners even with matching
characters and keeps a confirmed turn through rewind retries. Online HANDOFF
and READY are authoritative host state; spectators cannot confirm, guest presses
wait for acceptance, and duplicate/stale ready intents cannot commit a shot or
advance a turn. Protocol 5 and its fingerprint explicitly reject other builds.
The generic `ButtonPrompts` helper derives per-player live keyboard/pad labels;
`CourseControl.buttonLabel` exposes those values to controlled modes. Menu
confirm labels retain their separate meaning. The mutable candidate adds 17
signature lines without changing the API version or release descriptor.

Capture QA rejected the first audio-enabled edit: nonzero RMS hid long constant
DC sections and weak/missing music. A matched stock capture through the same
presentation path was healthy. Course lie setup had requested music ahead of
S2's pending `Level_PlayBgm` countdown and later restarted it; burst flushing
the countdown was rejected. The retained fix leaves one native-timed entry
publication in charge, services pending entry music on held presentation rows
and prevents a later course restore from rearming it. Driver-wide SFX stops
at course/shot restore boundaries were also gating the presentation music; the
mod now leaves the sound driver running across its reverse views. Opus's
mutation checks discriminated both premature publication and missing held-row
service. Final media requires recapture from this source; earlier audio-enabled
media remains a superseded draft.

Parent verification on this final source used queued Maven with
`-Dmse=off -Dtest=TestButtonPrompts,TestMenuInput,TestGamepadInputManager,TestInputHandler,TestGolfModel,TestGolfMenu,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestCourseControl,TestModApiPinPolicy,TestModApiSignatureSurface,TestOwnerBoundGamePatch test`
and the existing absolute S2/S3&K ROM paths. It completed **244 checks, zero
failures/errors/skips**, including actual independent-JVM host/guest handoff,
ready-press separation, stale/duplicate intents, native shots, pause and rewind.
The example's 25 local README links resolve and `git diff --check` passes.
Develop has since advanced to `eaceceda440328f0fc7864deca4cd4134811d373`
with independent Infinite Sonic example changes. Final combined validation
must use that actual destination; no feature merge into develop is requested.


### Real title-route capture audit (2026-10-06)

Opus r5 switched from direct course loading to the production title route at
`b34da10e4` and exposed two faults that the older physics-matching captures
could not exercise. The engine reset the external title before reading its act,
so EHZ2 practice loaded EHZ1. Controlled LEVEL rows also bypassed the native
released title overlay, leaving its TEXT_WAIT state visible for the whole hole.
The proposed direct-load capture fallback was rejected: the final reel must
exercise the menu route, not conceal a production failure.

The retained fix reads the validated zone/act before title-provider reset and
services the normal released overlay/control-lock owner on controlled rows,
shared by live, recorded and forward-rewind stepping. The mod keeps its own
presentation and does not retain a stale launch receipt or reset the native card.
A further regression proved that an early neutral checkpoint restored entry
text on rollback. `CourseControl.presentationReady()` now includes completion
of the native overlay, so the mod captures its first reusable lie afterward.
The fade operation returns whether the row serviced an active fade, so the mod
can preserve its initial simulation boundary without reading the engine fade
manager. The regenerated unpublished pin replaces the void return with boolean;
the descriptor and candidate version remain 0.7.0. Rebuild compiled mods.

`TestPuttPuttParadise#normalDevelopmentBootUsesRealTitleRouteAndCompletesOverlayWhileAimHolds`
now drives real GameLoop menu taps and the exit fade for both practice acts.
Both initially failed with the reported wrong act/frozen overlay; after those
fixes, both failed the added rollback check. The final queued S2-ROM invocation
passed **2 checks, zero failures/errors/skips**. Headless rendering deliberately
omits the locked display while retaining the native exit tail; the rendered
reel supplies visible title-phase coverage. The spring reconstruction fixture
also republishes its collision list after manually replacing the placement
window, rather than snapshotting publishers from its abandoned window.

The combined broad run `20261006T135649Z-ccb4b6eb` on `b34da10e4` was intentionally
interrupted for these newly proven production regressions. Maven stopped,
temporary output was cleaned and the diagnostics were acknowledged. This is
incomplete validation, not a suite result. Final combined validation and the
four production-route recaptures must use the corrected source.


Wider focused validation caught the first readiness fix continuing native world
steps after the spawn had already settled. That shifted the solved route inputs
and moving-platform phases. The mod now admits the original fade/settlement
steps, holds that world while the title exits, and captures only afterward.
All **116** course tests passed again, including full route/viewport/character
coverage and alternating two-player rounds. The online world-isolation probe
now recognizes the released native title, its pending PLC commands and its art
publication as bounded entry presentation owners; it continues comparing every
simulation owner on held rows. A failed subprocess now drains its bounded
terminal output before reporting a broken pipe, preserving the actual failure.

The protocol-generation test also exposed a fast-failure listener race: a bad
peer could disconnect before the first host poll and remove its undrained socket,
losing the readable version mismatch. The regression explicitly waits for EOF
and worker retirement before the first poll and failed before the fix. The
listener now retains undrained terminal events in its existing bounded peer set;
only drained, fully retired sockets leave it. The unnecessary disconnect callback
was removed. This is mod-owned transport behavior, with no engine networking change.


Final focused verification used queued Maven with `-Dmse=off` and
`TestButtonPrompts,TestMenuInput,TestGamepadInputManager,TestInputHandler,TestGolfModel,TestGolfMenu,TestGolfTransport,TestGolfOnlineIntegration,TestPuttPuttParadise,TestCourseControl,TestModApiPinPolicy,TestModApiSignatureSurface,TestOwnerBoundGamePatch,TestStartupRouteResolver,TestTraceSuppressedRowClosure,TestInLevelTitleCardCoordinator,TestModZoneTitleCardPolicy,TestSonic2LevelInitProfile`,
with the existing absolute S2/S3&K ROM paths. It passed **270 checks with no
failures, errors or skips**, including the real title/rollback regressions,
full courses, independent-JVM netplay and the forced early version mismatch.
The signature generator reproduced all 19,447 candidate signatures in canonical
order. Preflight passed with Java 21, PowerShell and explicit
`LUA_BIN=/usr/bin/lua5.4`. A fresh fetch confirmed the destination remains
`eaceceda440328f0fc7864deca4cd4134811d373`; the main workspace's unrelated
files/submodules and the untracked launch script remain preserved.


### Reusable scene and shot-audio rewind (2026-10-06)

The user's final capture audit found forward sound during a reverse shot. The
old mod only replayed its view; treating the ROM driver as ambient forward audio
was rejected. The user's follow-up also required an easy creator-facing rewind
system. Reusing global developer reverse ownership was rejected because it would
replace its logical restore selection, depend on its history setting and risk
stopping live voices. The retained design gives the producer a bounded independent
recording of the final heard forward PCM, and exposes only an opaque `AudioReplay`
through `CourseControl.recordAudioReplay`. A session owns and closes those leases;
no PCM or producer cursor appears in creator snapshots or networking.

The old mod's view history is promoted to generic `SceneReplay`, preserving both
endpoints within 128 samples and 8 MiB through adaptive interior sampling. The
external `ShotReplay` now contains only the golf-owned duration policy. The
creator handbook includes a complete capture/hold/reverse/close/restore recipe.
The published API baselines and release descriptor remain unchanged. The mutable
0.7 pin is canonically regenerated to 19,508 signatures, including an explicit
curated root for the standalone scene helper; annotation alone did not include
that unreachable helper in SDK/Javadoc closure.

Golf records before release, budgets PAL's 72-second watchdog, presents the final
reverse-origin audio packet, then closes the clip and restores its checkpoint on
the following row. Protocol 6 sends the host's replay phase and speed. Both peers
reverse their locally heard PCM; native host collision audio is not streamed and
coalesced remote views are not an exact shared PCM cursor. This limitation is
explicit in the handbook/example. Scores, allowance and gameplay ownership remain
in the external mod; pause holds audio rather than consuming its reverse cursor.

Sol's bounded review identified three exit cases: a failed sink flush could leave
scoped ownership after session teardown; concession during reverse froze the view
while continuing the audio; and a first close failure skipped room/presenter
cleanup. Resource disposal now reports a failed flush while releasing its own
cursor and slot, without resetting global audio. Explicit stop remains retryable.
Terminal results clear the replay and audio; mode close independently attempts
all owned resources and preserves the original failure. Regressions exercise a
real session teardown, local concession, independent-JVM concession and a real
host listener's port reuse after injected sink failure. Session creation uses its
injected audio collaborator rather than a later ambient service scope.

Queued focused verification used the existing absolute S2/S3&K ROM paths:

- `TestAudioPresentationProducerRewind,TestAudioManagerPresentationModes,TestRewindHistoryArming,TestUnifiedAudioPresentationIntegration,TestGameLoopAudioPresentationModes,TestSceneReplay,TestCourseControl,TestGolfProtocol,TestGolfTransport,TestPuttPuttParadise,TestGolfOnlineIntegration`:
  233 checks, zero failures and three errors in new fixture setup (two invalid
  scene palettes and one raw schema-6 packet missing its speed byte). The fixtures
  were corrected; the producer/manager/native audio cases and all 116 then-existing
  golf course cases passed in that run.
- Corrected `TestSceneReplay,TestGolfTransport,TestGolfOnlineIntegration`: 19 checks,
  zero failures/errors/skips, including audible reverse and host-owned rate in
  two independent processes.
- Resource-disposal `TestAudioPresentationProducerRewind,TestAudioManagerPresentationModes,TestSceneReplay,TestCourseControl`:
  58 checks, zero failures/errors/skips.
- The new local/online concede tests failed before terminal cancellation, with
  active reverse audio on results. Final pin/course/rewind checks passed 24 of 25;
  the online test's last numeric diagnostic initially parsed `0.0` as an integer.
  Its corrected real two-JVM rerun passed, as did the independent failed-sink host
  listener regression. No production fix was needed for that diagnostic parse.

The previous combined run `20261006T143938Z-52e799e1` at `f3e1945fd` lost its
wrapper while Maven continued; the exact owned process was accounted for and
stopped when the new audio work superseded it. Its partial reports contained
23,315 checks with four failures, no errors and 57 skips; that is incomplete
validation. Diagnostics were inspected and acknowledged, not archived. The new
combined run must use this completed replay source and the actual destination
`eaceceda440328f0fc7864deca4cd4134811d373`, not the superseded title-only source.
Preflight passed with Java 21, PowerShell and `LUA_BIN=/usr/bin/lua5.4`.
The selection is 2,980 ordinary classes plus fresh guards. Final media must also
recapture real menu/fade/title routes and reverse PCM from this source; the
88.93-second f3 creative edit is retained only as a superseded draft.


Focused structural verification passed all 38 audio ownership checks and exposed
an earlier prompt-layer dependency added by the polish: `ButtonPrompts` shares
configuration's stateless GLFW key-name codec. Duplicating its name table or
moving its existing configuration consumers merely to avoid an edge was rejected.
The consciously audited single `control -> configuration` edge is recorded in
the dependency ratchet: the codec's package has no runtime-service dependencies.
The other focused guard failure is the already-attributed stock S1 title's
hard-coded virtual pattern base, unchanged by this work. No ownership guard is
relaxed for the audio replay implementation.

The repaired dependency-ratchet invocation completed 29 structural checks with
one unchanged, already-attributed S1 virtual-pattern-base failure and no new
failures/errors/skips. The replay-specific ownership guard remains 38/38. The
final local/online concession checks and failed-sink host port reuse passed.
All 45 checked local link paths resolve and the candidate generator reproduces
the full canonical 19,508-line pin; `git diff --check` passes.


### Reconciliation with merged Slay The Robotnik — 2026-10-06

The user reported PR #211 merged while the final `82a6854b` validation was
running. Fetch observed `develop` at `f4b40f5026988c5152eb2ae1fadaaf7c648f5618`.
Main stayed on develop and fast-forwarded from `eaceceda4`; its three dirty
reference submodules, two untracked BizHawk archives and `raiscan-0.6-thoughts.md`
were preserved. The feature and uncommitted launcher remain separate for PR #212.
No develop merge/push or shared-history rewrite is requested.

The superseded run `20261006T154608Z-19c2c5fe` selected all 2,980 ordinary
classes with two workers and fresh guards against `eaceceda4`. It was deliberately
interrupted before completion when the destination changed: 2,775 partial reports,
24,177 checks, four failures, no errors and 58 skips were present at the checkpoint.
The registered wrapper received SIGINT, returned 130 and reaped its Maven tree;
consumed diagnostics were acknowledged and deleted. These are partial observations,
not a completed suite result. A fresh, isolated eace baseline had completed 30
selected native/CLI checks (27 failures, no errors/skips) and 62 structural checks
(two failures, no errors/skips). The later base fixes both structural assertions.

The new merge reconciles five conflicts by behavior: one `LogicalMouse` window
entry point retains the same HiDPI/letterbox computation and delegates to the pure
mapping seam used by golf's coordinate tests. Scene constant/debug roots and golf's
scene/input/replay roots are retained together; the canonical generator reproduces
the union pin byte for byte, with 19,855 signatures. Mod API version/status and
published pins are unchanged. CLI prose retains development trust/read-only
manager behavior alongside direct base-game launch and the sprite inspector.
The compatibility guide retains both scene and controlled-course contracts.

The validator combines legitimate javac assertions and platform/public API enum
switches with golf's immutable creator-enum support. A single bounded bytecode
recognizer checks the complete initializer, literal switch assignments, guard
shape and writes; synthetic flags alone are not an exemption. External metadata
comes only from the platform loader or allowlisted engine resources, without
class initialization or a creator loader. A rejected creator enum present in the
jar cannot fall back to those resources. Tests retain both branches' cases and
add mixed assertions/own/JDK/API switches, seven forged-artifact mutations and
an explicit API allowlist case. The trust guide documents the resulting boundary.

The queued Java 21 `-Dmse=off -DskipTests test-compile` command succeeded. The
queued focused command used all three verified absolute ROM properties and:

```text
-Dmse=off -Dtest=TestModValidator,TestMenuInput,TestLogicalMouse,TestGolfMenu,TestMasterTitleMouse,TestModSceneHost,TestRecordingCanvas,TestSceneTextureCache,TestSceneKeys,TestSceneApiValues,TestSlayTheRobotnikExample,TestHelloSceneExample,TestDevelopmentModBoot,TestDevelopmentModSource,TestPendingModStateEditor,TestGameLoop,TestStartupRouteResolver,TestTraceSuppressedRowClosure,TestInLevelTitleCardCoordinator,TestModZoneTitleCardPolicy,TestCourseControl,TestPuttPuttParadise,TestGolfOnlineIntegration,TestAudioPresentationProducerRewind,TestAudioManagerPresentationModes,TestSceneReplay,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy test
```

It completed 543 checks with no failures, one fixture error and no skips; all 542
other checks passed. A direct javac reproduction proved that an exhaustive switch
on a nested creator enum emits no switch-map helper. Moving only the mutation
fixture's enum to the top level emits the real helper, now asserted explicitly.
The corrected queued `-Dmse=off -Dtest=TestModValidator test` invocation passed
all 23 checks, with no failures/errors/skips. This corrects the fixture rather
than relaxing the recognizer.

The owned baseline worktree fast-forwarded cleanly to `f4b40f502`. Repeating the
25 exact native/CLI method selectors listed above with `-Ptest-concurrent` and
all three absolute ROM properties completed 30 checks: 27 failures, no errors or
skips. Full failure messages are compared by identity and concrete assertion,
ignoring only the already-documented `RewindObjectStateBlob` JVM class hash.
The fresh `LUA_BIN=/usr/bin/lua5.4 ... -Dmse=off -Pguards
-Dtest=TestArchUnitRules,TestObjectPhysicsStandardizationGuard test` baseline
completed 62 checks with no failures/errors/skips. Old failure totals are not
carried over as the new base's guard status.


The merged feature's focused structural command,
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards
-Dtest=TestArchUnitRules,TestObjectPhysicsStandardizationGuard,TestArchitecturalSourceGuard,TestArchitecturalReviewGuard,TestModEngineWiringSeams,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy test`,
passed **180 checks, no failures/errors/skips**. Tool preflight passed in the
actual Java 21/Lua 5.4/PowerShell environment. The reviewed replacement plan
against `f4b40f502` selects **2,992/2,992 ordinary classes plus fresh guards**,
two ordinary workers, with a 90-minute execution limit excluding queue wait and
a 10-minute no-output limit. It is a broad validation expected to take tens of
minutes; the superseded prefix is not used as its completion evidence.

### Completed merged-source validation and public promo — 2026-10-06

Production source is `09d5ed2a22db4b759cfd955233ab35df2837b147`, reconciled
against develop `f4b40f5026988c5152eb2ae1fadaaf7c648f5618`. The complete selected
command was `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py
--base f4b40f5026988c5152eb2ae1fadaaf7c648f5618 --workers 2 --max-minutes 90
--run`. Run `20261006T163159Z-94ef3f46` selected all 2,992 ordinary classes and
fresh guards, with Java 21 and all three verified absolute ROM properties.
Ordinary completed **2,990 reports / 25,998 checks, 28 failures, no errors,
61 skips**, in 2,246.45 seconds. Fresh guards completed **86 reports / 672 checks, no failures/errors/skips**,
in 212.02 seconds. The wrapper exits 1 because ordinary testing is red, not
because validation is incomplete.

All 118 golf course cases and both actual two-JVM integration cases passed with
no skips. Each of the 27 native-route failures matches the fresh f4 develop
baseline by class, parameterized test, kind, type and full assertion. The longest
SSZ message is 2,915 characters, compared from raw completed XML before
compaction; only the owned `RewindObjectStateBlob@hex` JVM hash is normalized,
not any gameplay field. The baseline command covered the 25 listed selectors:
30 checks, 27 failures, no errors/skips. This is a matched focused baseline,
not a full baseline-suite pass.

The remaining framebuffer failure is the same 24,783-pixel waterfall palette
rotation reproduced on pre-polish `6da29127a` above. Develop f4 lacks this golf
scene API/test, so no direct new-base attribution is claimed for that case.
The queued merged-source `maven_queue.py -Dmse=off
-Dtest=TestGolfScenePresentation -Dsonic2.rom.path=<absolute S2 REV01> test`
then passed the complete **37-case scene class, no failures/errors/skips**,
in a fresh JVM. This isolates the reused-fork condition without removing the
broad failure or weakening the assertion. The complete broad invocation remains red; it is
not reported as a green suite. Inspected skips cover opt-in measurements,
soak/capture/native-reference diagnostics, unavailable GL/EGL probes and level
assumptions; none reports a missing ROM. Separate trace/native/diagnostic
profiles are not certified by ordinary selection.

The source-first example build ran once after reconciliation through the shared
queue. Independent Java 21 compilation against the capture runtime snapshot
matches all **123 classes plus the manifest byte for byte**. SDK validation exits
0 with the ten documented internal-bridge warnings, not zero warnings. Jar
SHA-256 is `55c6e3fc6f04ed58f53cb7abd6113c502b6d874a44fce6b44f8b90391607296a`.
Engine Java digest is `e54abff60866814fd2f92db14df859ce38e5e1373d0ac8dc1d87f1351c3367a7`;
golf main Java is `73d3ddc77f914eadcd6a80fd04be809381b98c12c4efb1fc79140fed5d228407`.
The canonical candidate union has 19,855 signatures; published pins/version/status
remain unchanged. All 47 checked local documentation links resolve. The
release-tree/push policy audit is checked against the prepared feature delivery.

Opus 5.5 Extra High recaptured all four scenarios from the merged build via the
real menu -> fade -> Sonic 2 title card -> LEVEL route, with a runtime snapshot
that the broad run cannot modify. EHZ1 Sonic restores the whole shot at row 400
and holes out at 4443; EHZ2 Tails retains act index 1 and holes out at 7371 in
25 strokes. The two-hole local match completes at 31291. The real two-JVM TCP
session has 4,985 rows including the guest's rewind. Native title text clears
before the first lie at row 56 and does not return after rollback. Every PNG,
WAV and state row matches the preceding 82a captures; re-rendering yields the
same MP4. The earlier f3/82a sources remain explicitly superseded evidence.

The final public film is **88.8 s / 5,328 frames / 1920x1080 H.264 at 60 Hz**,
48 kHz stereo AAC and 12 chapters. Its opening introduces OpenGGF; styled chapter
cards and a staged one-button panel explain the action; real local/online
handoffs, both holes and a full finish remain at normal speed. Five quiet card
whooshes and opener/end-card music are disclosed ROM sounds added in editing;
other action audio is the captured game output. Full decode, 80 source-picture
checks, moving-source cadence, all 16 audio-sync samples, loudness and reverse
checks pass. Audio is -15.0 LUFS / -2.1 dBTP with no clipping; its only silent
run is the game's 18-row title-card gap. Audio was checked numerically, without
a human listening assessment.

The reusable rewind helpers are verified in the actual film as well as tests:
parent stereo comparisons find **70 practice, 41 host and 41 guest interior
packets exactly equal to the recorded forward samples played backward**, with
zero integer difference. Initial crossfade packets are excluded explicitly.
Decoded-film median reverse correlation is 0.9986 / 0.9997. Parent normal-speed,
unmuted browser playback reaches the end without an error or corrupted frame;
12 of 5,328 client frames drop under the concurrent broad run. Opus's independent
browser run drops none. The complete file decode/source-cadence checks remain
independent of these browser counters.

Durable media is under
`<capture-root>/putt-putt-paradise-20261005/` (outside the checkout):

- `promo-opus-20261006/final-verified/putt-putt-paradise-promo.mp4`, SHA-256
  `5bb6b99d0437ff023710e5671ca3ed2f96b0eaea11d77c133b3182de2c5579e3`.
- `promo-opus-20261006/final-verified/putt-putt-paradise-promo-kit.zip`, SHA-256
  `80a46bde8ef7b3489ba6880b15b98c375f1580a8133d8b4de75a04a0874679ae`.
- `showcase-20261006-opus-final/` retains native PNG/WAV sources and a current
  five-role manifest that excludes superseded captures.

The 93-file kit passes its ZIP integrity check and includes capture/render/check
scripts, inputs, state/provenance, the chapter/edit maps, the rewind checker and
all three current reverse-audio reports. It contains no ROMs, engine classes,
WAVs or native PNG rows. Owned runtime/harness class snapshots and both preview
servers/tabs are removed; useful captures/drafts are retained. Consumed category diagnostics were acknowledged and removed. All owned
validation units are stopped; the clean, fully accounted baseline worktree and
its local branch are removed. Its only ignored content was generated resource
links, a runtime configuration template, Python caches and Maven/test outputs.
The open PR delivery preserves the feature branch/worktree and the user's
untracked launcher. Main remains on develop with unrelated changes preserved;
this work does not integrate or push develop.
