# Putt Putt Paradise implementation plan

Subtitle: **Sonic 2 Mini Golf**

Date: 2026-10-05

Status: implementation plan only; no golf implementation delivered

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
| `ModePresentationTick` | Logical input edges/held directions plus a presentation-tick ordinal; no level/V-int clock mutation. Recorded offline test ticks can drive the same callback |
| `ModeServices` | Checkpoints, playable launch/pose commands, read-only scene capture/presentation and scoped progression policy; no unrestricted engine/renderer object access |
| `CourseCheckpointService` | `capture()` returns opaque `CourseCheckpoint`; `restore(checkpoint)` runs only at a committed boundary; validates session generation, level identity and complete adapter layout before mutation |
| `GameModule.courseRewindAdapters()` | Default empty; module-owned per-course adapters join engine course capture. Existing `rewindAdapters()` contains mode/match state; ordinary debug rewind captures both domains |
| `PlayableLaunchControl` | `setAimPose(direction, DUCK/NEUTRAL)`, `beginCharge()`, `addCharge()`, `release(LaunchVector, ShotMotionPolicy)` and `settle()`; engine owns coherent radii, animation, sensors, fractions, velocity and attachment transitions |
| `ModeStepObservation` | Previous/current native centre, velocity, support observation and typed damage/death/attack outcome after the canonical committed step. No inferred penalty from ring count or raw collision overlap |
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
state and object/support references coherently. It must fork/reset replay history
at the restored boundary so previous-player input cannot run into the new lie.
Mode restore, penalty restore and an act load are distinct operations with distinct
ledger/timeline behavior. A checkpoint from an old hole/session fails before any
partial mutation; missing required domains are errors rather than silently skipped.

Hold gates authoritative V-int, executed-frame and level counters, timers, RNG,
physics, object loading/culling, camera follow, world events, palette/pattern
animation and PLC/hardware service admission. Input, meters, network processing,
overlay animation and native audio playback may advance on presentation ticks.
Queue completion can be received while held but cannot publish world/art changes
until an admitted step. Startup/title/load boundaries finish before golf captures
its first playable lie. Trace/movie-owned stepping retains its existing contract;
offline golf tests explicitly supply mode ticks rather than deriving them from
trace comparison rows.

## Mod layout and state machine

Create `examples/putt-putt-paradise/` with `build.py`, `README.md`,
`src/main/resources/META-INF/openggf-mod.yaml` and source package `paradise`:

- `PuttPuttParadiseMod`, `GolfModule`, `GolfCourseDefinition`, `GolfRules`:
  registration, S2 decoration, the two ROM-backed acts and immutable rule constants.
- `GolfSession`, `MatchState`, `GolferState`, `GolfMode`, `ShotSelection`,
  `ShotPower`, `SettlementDetector`: turn/shot decisions and mode rewind values.
- `GolfMenu`, `GolfOverlay`, `DeparturePreview`: launch choices and presentation.
- `net/GolfWire`, `GolfTransport`, `GolfHost`, `GolfClient`: bounded value codec,
  socket lifecycle and command/presentation ownership; no engine dependencies in
  `GolfWire` or `GolfTransport`.

Flow: `LOBBY -> TURN_OPEN -> AIM -> CHARGE_ONE -> CHARGE_TWO -> PRE_RELEASE
-> WATCH -> RESOLVE -> TURN_OPEN`, with `HOLE_RESULT`, `MATCH_RESULT` and
`DISCONNECTED` branches. Practice has one golfer and a selected act; competition
plays both acts. Record two saved courses, active player, starter, scores,
finished/DNF flags, charge values/stage, presentation timers, committed-shot ID,
pre-shot checkpoint and dwell/watchdog state in mode rewind. World state belongs
to course checkpoints. Network connections and socket objects never enter snapshots.

Capture the neutral lie's course at TURN_OPEN, before aim/charge pose commands.
The second accepted charge is the only stroke-commit transition: retain that
checkpoint as the pre-shot course, increment strokes once and seal the selection.
Repeated A, retransmission and result polling cannot repeat that edge.
Failure restores that course, retains the stroke, adds one penalty and advances
the turn. Success saves the evolving course/lie for that golfer. Hole-finished
players are skipped; reverse the first starter for act 2. Lowest summed strokes
and penalties wins, ties draw, and concession is DNF and loses the match.

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
`LevelFrameStep` and `src/test/java/com/openggf/tests/HeadlessTestRunner.java`.

- [ ] Add a session-owned extension/no-op default and owner-wrapped callbacks.
  Route interactive and headless mode ticks through one shared coordinator.
  Sample input once per presentation tick; consume A edges once, including across
  a held-to-running transition. Keep accelerated ordinary play and native Start
  pause behavior intact.
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
  each charge/pre-release stage and replay the same mode-input suffix.

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

**Exit gate:** full state and forward replay agree for both characters. Do not
continue to competitive turn switching with coordinate-only save/restore.

## Task 4 — coherent putt/chip launch and settlement

**Create:** `PlayableLaunchControl`, immutable launch/contact values;
`ShotPower`, `SettlementDetector`; focused tests `TestPlayableLaunchControl`
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
  restore it in snapshots and remove it when the mode exits.
- [ ] Accept settlement only on valid floor support with low support-relative
  velocity for a configured dwell. Walls/ceilings, brief apexes, spring recontact,
  support loss or a reversing loop reset dwell. Pin the settled lie coherently;
  neutral aiming pose/duck changes must preserve floor contact.
- [ ] Add bounded shot duration/no-progress watchdog and explicit lost-ball action.
  Both use the penalty rollback path, never a free forward teleport.

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
  named rule constants. Cancel before commitment for free; after commitment
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
  on launch, rather than silently overridden mid-game.

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
  respawn/score mutation. A rolled badnik attack remains successful combat.
  Rings are secondary per-golfer stats, not a shield from golf penalties or a
  tie breaker. Verify full pickups/objects/clocks restored with the stroke retained.

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
  one logical controller to the active owner; secondary controllers cannot steer
  or double-commit a turn.
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

**Create:** proposed scene presentation types and implementation;
`TestGolfScenePresentation` under `src/test/java/com/openggf/game/presentation/`.
**Modify:** `level/render/SpritePieceRenderer`, sprite/object render collection,
`GraphicsManager`, level tilemap/parallax/art owners and mode view routing.

- [ ] Project render values after committed steps without object `update` or
  mutation. Include terrain changes, rings/pickups, badniks, springs, moving and
  collapsing platforms, active character, scroll/animation/deformation and draw
  order. Stable ROM-art identities resolve through local asset loading; virtual
  pattern/GPU IDs alone are not portable identities.
- [ ] Provide a render-only guest mode with local ROM decoding and no authoritative
  sprite/object physics. Resolve dynamic art residency/animation and palette
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
  charge preview must not be played again when its accepted cues arrive.
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
`docs/architecture/validation/2026-10-05-putt-putt-paradise.md` for actual evidence;
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
  Test real widths 320/400/512/640/800; list native S2 and solo active-character
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

These commands are for future implementation, not tests already run for this
document. New test names above are planned files. Supply `-Dsonic2.rom.path=`
with the discovered, verified absolute S2 ROM path; inspect ROM-backed skips.
Canonical S2 SHA-1 is `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, CRC32 `7B905383`.

```bash
mvn -v  # Java 21; inspection only
tools/testing/install-hooks.sh  # once in the implementation worktree
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestGameplayModeHold,TestCourseCheckpointIsolation,TestPlayableLaunchControl" test
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestPuttPuttParadise,TestGolfShotControls,TestGolfEhzCourseRules,TestGolfLocalMatch,TestGolfScenePresentation" "-Dsonic2.rom.path=/absolute/discovered/S2.gen" test
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestGolfProtocol,TestGolfNetworkMatch" "-Dsonic2.rom.path=/absolute/discovered/S2.gen" test
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestModApiSignatureSurface,TestModApiJavadocTool,TestModApiSdkPackager,TestInfiniteSonic" test
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

For this planning-only change, validate Markdown structure, local links,
referenced existing file paths, design consistency, whitespace and commit policy.
No Maven/engine test run is needed and none establishes feasibility at this stage.

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
and future test scope. The unchecked lists above track future implementation and
verification; no engine feasibility result is claimed by that review.

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
