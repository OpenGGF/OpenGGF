# Shared route planner: route tapes

Status: accepted design, implementation in progress on `feature/ai-shared-route-planner` (steps
1-4, partial 6) and `feature/ai-shared-route-planner-2` (step 6, from `feature/ai-weakness-pass`
at `d84b25bd8`). Produced by the architect workflow:
grounding, two candidates (base "B" and graft source "A"), a cross-judge, and a lead synthesis.
This file condenses those artefacts; it is the contract the implementation follows. Deviations
found while implementing are recorded in [Deviations](#deviations).

## Problem

OpenGGF has several route authors and no shared core:

- **Stageplay** (`com.openggf.stageplay.route`) runs a best-first search on a static-terrain
  probe, throws away the buttons it ticked, emits `PuppetCommand`s, and "qualifies" by re-running
  the steering policy in a live world. A `VERIFIED` Stageplay route has no button sequence that
  BizHawk could replay.
- **`ReferencePathInputAuthor`** runs a receding-horizon search in the production gameplay world
  and writes frozen rows, but its loop is ad hoc: raw registry restore with no admission guard,
  Start dropped, trial rows kept after a load, an S3K class imported into a shared tool.
- **The SSZ/DEZ boss authors** repeat the same capture → trial → score → restore → commit loop.
  `tests/route/RouteSteering` re-plans inside tests, and the Typing of the Hedgehog rail builder
  has its own beam search.
- **Route artefacts** (`src/test/resources/routes/s3k`, 117 `.bk2` and 112 `.script`) carry their
  launch contract (boot level, roster, width, donor, seeds, initial `V_int_run_count`) in Java
  tests and `#` comments. Eight consumers replay them through a lossy P1-only adapter.
- **Native lag.** Native S3K sometimes runs a VBlank without polling the pad. Identical rows can
  therefore diverge natively, and the engine may not model CPU timing to predict it.

The user's requirements: one planner used by Stageplay and reference pathing; heavy extension
for object goals (target, latch, ride, phase waits); routes translated into button presses that
compare against BizHawk; repair that keeps original rows where play still tracks; planning as a
fast way to author level completions, not a replacement for identical-input comparison.

## Usage

A route is a **launch contract plus an immutable tape of physical two-controller rows**. Plan
once, replay many times. Front-ends submit typed tasks to `RoutePlanner.plan`; nothing re-plans
at test time. `RouteReplay.replay` is the only qualifier: a fresh host, the frozen rows, no
correction authority.

```java
// Tests and tools: open the manifest, replay the tape.
RouteArtifact route = RouteFiles.read(Path.of("src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.route.json"));
ReplayResult result = new RouteReplay(EngineRouteHosts.withRom(rom)).replay(route, observer);

// Repair an existing route from input 1366, keeping original rows wherever play still tracks.
var repair = new RouteTask.Repair(route, reference, new RepairScope(1366, 360, 240, 24),
        AuthorHints.referenceFollowing(), SearchBudget.referenceRepair());
PlanResult plan = new RoutePlanner(EngineRouteHosts.withRom(rom)).plan(repair, Cancellation.NONE);
if (plan instanceof PlanResult.Solved solved) {
    RouteFiles.writeNew(stem, solved.route(), solved.engineEvidence());
    System.out.println(solved.repair().summary()); // kept/original rows, changed spans
}

// Author a level completion with object goals.
var bridge = ObjectTarget.select("arrival-bridge", Capability.ATTACHMENT, region, SpatialOrder.RIGHTWARD);
var program = GoalProgram.of(Guard.safe(PlayerSlot.P1),
        RouteGoals.board(PlayerSlot.P1, bridge).maxRows(1800),
        RouteGoals.ride(PlayerSlot.P1, bridge, RouteGoals.phaseIs(bridge, "resting")).maxRows(240),
        RouteGoals.cross(BoundaryKind.LEVEL_LOAD).maxRows(9000));
plan = planner.plan(new RouteTask.Author(launch, program, AuthorHints.levelRoute(), SearchBudget.levelRoute()),
        Cancellation.NONE);
```

The SSZ1 example object is the placed `$77` cutscene bridge (`SszCutsceneBridgeObjectInstance`,
placement index 12 in `Levels/SSZ/Object Pos/1.bin`, `03 20 0c 88 77 00`). It retracts and comes
to rest; it does not collapse, and it is not dynamically spawned. Both candidates' examples got
this wrong.

Files per route: `<stem>.route.json` (manifest, the single authority for launch identity) and
`<stem>.script` (the editable row source with explicit physical letters, both lanes). A `.bk2`
is derived on demand by `RouteFiles.exportBk2` or the native composer; a committed `.bk2` would be a
third copy that can drift.

## Shape

**Tape.** `PadRow(p1, p2)` holds two unsigned 8-bit physical masks (U, D, L, R, Start, A, B, C).
There is no logical jump bit and no debug or system channel. Row *i* is the *i*-th controller
poll, which is the *i*-th engine step. `InputTape` is immutable, packed and content-hashed. One
lossless conversion turns a row into the engine's recorded input at the host boundary
(`GameplayCaptureSession.step` → `RecordedInputSnapshots`). Press edges come from the previous
held row, which the host driver owns.

**Manifest.** `RouteManifest(launch, tapeHash, rowCount, checks, tracking, provenance,
qualification, nativeFacts)`. `LaunchContract` covers ROM identity by hash (never a path),
inlet (cold level, incoming prefix, positioned seed, scene entry, recording join), roster,
viewport, donor, explicit initial `V_int_run_count` and seeds. A route's content identity
excludes its qualification stamps and native facts, so adding a verdict does not change it.

**One search, one replay.** `RoutePlanner.plan(task)` runs one deterministic prefix search over a
row journal. Each node owns a parent, an immutable edge of exactly the rows the host accepted,
and an observation. Sparse checkpoints accelerate reconstruction. A chosen prefix is committed by
replaying its saved rows, never by invoking its policy again. Repair is a restriction on the same
search: original spans are mandatory edges while tracking holds, and a drift opens a bounded
editable window. `plan` returns `Solved` only after a complete cold replay of the frozen output
succeeds. `RouteReplay` is forward-only and mints `ReplayEvidence`, which has no public
constructor. `RouteFiles.writeNew` requires matching evidence.

**Goals are data.** `GoalProgram(routeGuard, legs)`; each `Leg(goal, guard, hints, maxRows)`
(graft from A). The goal is the completion test. The guard is enforced on every row of the leg,
in search and in replay: hurt, death, ring loss, unexpected boundaries, overshoot. Goals are
`Reach`, `Acquire`, `Maintain`, `WaitFor`, `Achieve`, `EnterBoundary` and `Follow`; `RouteGoals`
provides the user's words (`board`, `ride(until)`, `attach`, `hold(until)`, `awaitPhase`,
`defeat`, `cross`). Steering numbers stay private to `NativeRouteSteering`.

**Objects.** Owning objects implement read-only capability interfaces (graft from A), found by
`instanceof`: `AttachmentSource`, `PhaseSource` and `HitPointSource`. Facts are immutable copies
with participant, piece and generation. This covers directly constructed dynamic objects such as
the SSZ GHZ/MTZ replica bosses, which come from `Sonic3kSSZEvents`, not from the layout. Updraft
and airflow emit a passive per-tick effect at their successful effect site. Proximity,
animation or speed never prove attachment. Unknown phase stays unknown.

**Hidden tiering.** The static-terrain probe can propose cheap rows for scene shots. Those rows
are a draft for the live host, which repairs them; only the live host qualifies.

**Hosts.** A small trusted SPI: `ReplayWorld` (`observe`, `step(PadRow)`, `close`) and
`RouteWorld` (adds `capture`, `restore`, `epoch`). The opaque host checkpoint token holds the
engine snapshot, the driver's previous row and every post-boot host write, including
`GameplayCaptureSession`'s sidekick seed window. Admission combines `CourseControl`'s
owner/Level/key-layout discipline with `GameplayInputBranchTool`'s pending-assembly and
poisoned-fade refusal. A boundary invalidates every outgoing checkpoint. The row that crossed it
lives only in that trial's edge, and the contaminated host is closed and rebuilt from the cold
prefix. The host proves that its rows own the effective gameplay input. If a mod
`GameplayFrameController` could substitute input or hold the world, the launch is rejected.

**Engine hosts need a GL context.** `EngineRouteHosts` worlds are `GameplayCaptureSession`s booted
through `HeadlessGameBoot`, which creates an OpenGL context. Any process that opens them (an
isolated editor planning or replay worker JVM, a test fork, native-check tooling that replays the
engine side) must select surfaceless EGL on Linux:
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl` with `DISPLAY`, `WAYLAND_DISPLAY` and
`LD_LIBRARY_PATH` unset. It never falls back to the shared desktop. `ReplayEvidence` stays inside
the worker; only stamps and value results leave it.

**Visibility.** Search, journal, frontier and checkpoint envelope are package-private in
`com.openggf.route`. A `.internal` subpackage cannot express that in Java. Host SPI construction
is trusted composition only. A bytecode guard checks that tests and front-ends reference only
`RoutePlanner`, `RouteReplay`, `RouteFiles` and value types.

**Determinism.** Budgets are counts (expansions, steps, captures, fresh boots, retained
checkpoints), never wall clock. A wall watchdog can cancel a search; it never selects a result.
Ties break by a stable ordinal. Live worlds have no state-equivalence merge.

## Native composition (lead override)

The judge preferred B's offer-indexed native movie. The lead overrode that with repository evidence,
taking A's composition:

- On native, a CPU-overrun lag VBlank consumes a movie row without polling the pad
  (`Level_frame_counter` repeats). If tape row *i* is offered at native movie frame *i*, every
  later poll lands one row early, permanently. That is the hazard behind
  `TestS3kHpzColdRoutes.NATIVE_LAG_FRAMES`. The SSZ1 native check on 2026-11-01 aligned exactly
  until its first lag at input 1957 and could go no further.
- `NativeCheck` therefore composes the native entry prefix, then the tape rows, plus one
  **filler row** (a copy of the previous row) at each lag VBlank a native run observed. The game
  never reads a filler row, so the polled presses are identical to the engine's.
- Observed lag VBlanks are recorded in the manifest as native facts, each stamped with its
  harness version and content hash. They are never fed to the engine, never used to plan, and
  never predicted.
- Lag phase effects remain real divergences: `V_int_run_count` advances on a lag VBlank and the
  engine's does not. Such mismatches go through B's `RequiredEdit` repair. If bounded repair
  cannot clear them, the verdict is "engine-qualified, native-diverged at row *r*".
- The per-test `NATIVE_LAG_FRAMES` lists are deleted as their routes migrate.

## Synthesis decision

Base: candidate B (`candidate-sol`), judged 23/25 against 13/25. Grafts from A: owner-implemented
capability interfaces; goal plus guard per leg; the convenience goal factories; hidden tiering.
Corrections: the SSZ bridge facts above; two committed files per route; host-owned mutable state
(sidekick seed) in the checkpoint token; mod frame-controller arbitration; package-private
internals in `com.openggf.route`; repair witnesses (`TrackingContract`). Each existing route's
track is recorded once, on the engine its rows were authored against (SSZ1: `36aaddb37`, with
the native fixture `ssz1_cold_route_native` independently confirming rows before 1366). A route
with no trustworthy track is repaired against goals or an explicit engine reference, never
against a fabricated trajectory.

Rejected:

- A's live dedup key, because hidden state makes it unsound.
- A's placed-only targets, because dynamic objects exist.
- A's `Stage.Scene(StageplayDocument)`, because it couples the core to a front-end type.
- A's `.internal` package-private split, because Java cannot express it.
- B's offer-indexed native movie, because it goes permanently off by one after any lag.
- B's registration bridge, because it has no owner and misses dynamic objects.
- B's committed `.bk2`, because it is a drift-prone third copy.

## Invariants

1. Planning never mutates gameplay, never reads trace data, never keys on game or zone names in
   shared code.
2. Rows are recorded where they are pressed; qualification is a cold replay of the frozen rows.
3. Budgets are counts. Native lag is observed, never simulated or fed to the engine.
4. Repair keeps original rows wherever play still tracks, searches only where it drifts, and
   reports the kept share against the original length.
5. Engine behaviour never changes to suit a route.

## Migration order

1. Tape, manifest, codec and replay-only driver: lossless two-lane round trip, a BK2 export that
   BizHawk can load, and host token completeness, including the sidekick seed and a hostile mod
   controller.
2. One prefix search with the guarded host. `ReferencePathInputAuthor` becomes a front-end and its
   loop is deleted. Pinned by `TestReferencePathInputAuthor`.
3. Stageplay player routes move onto rows with a `FrozenInputTrack`, updating the document version
   and Mod API pins together. Stageplay search, proposal and verification are deleted; numeric
   steering keeps one owner. Pinned by the Stageplay route tests.
4. Object capabilities and goals: the SSZ `$77` bridge, the updraft, one grab owner, one phase
   platform. The SSZ/DEZ boss authors move onto `defeat` legs.
5. Remaining loops: mod rails, the `tests/route/RouteSteering` re-planners and the lossy
   `stepFrame` route adapter.
6. Manifest the route consumers. Repair the SSZ1 routes from 1366 and the `lrz-*-hpz-ssz1*` routes
   that share the opening. Native-check them with filler-row composition.
7. One combined smoke run, owned by the lead.

The lead reordered delivery to 1-4, 6, then 5a (the `stepFrame` harnesses onto shared replay) and 5b
(the `RouteSteering` re-planners onto frozen fixtures). The mod rails stay as they are (see the
creator frame-controller question below).

## Tradeoffs accepted

- Weaker pruning and more replay work, in exchange for never merging dynamic worlds under guessed
  keys.
- Separate engine and native verdicts, in exchange for truthful claims without CPU timing.
- Explicit goal/target recipes and owner adapters, in exchange for reliable grab, ride and phase
  semantics.
- An SSZ repair may extend through bosses to the route end and keep fewer rows, in exchange for
  validating the actual suffix rather than a short apparent reconvergence.
- Process isolation and bounded snapshot retention, in exchange for protecting the editor's
  session.

## Alternatives considered

- **Keep Stageplay's planner and extract a checkpoint helper for the gameplay tools.** Too
  shallow. Every caller keeps ranking, commit and qualification policy, so duplicate loops survive.
- **A public generic search framework** with pluggable frontier, dedup and snapshot strategies.
  Callers would assemble safety policy themselves, and the shortest compiling author would skip
  the guards.
- **A predictive object and lag model.** Cheaper search, but a second copy of the collision and
  phase algorithms, plus native CPU timing promises the reimplementation boundary forbids.

## Open questions

- Snapshot capture/restore cost on AIZ→HCZ and SSZ/LRZ worlds, which should decide the production
  presets.
- Native boot witnesses for inlets other than a complete-run prefix (widths, donors, scene
  entries), which today yield `UNSUPPORTED_INLET`.
- Whether the full corrected SSZ suffix is repairable within the first declared bounds.
- Which owner facts the first native grab and phase goals need beyond the bridge and updraft.
- **Creator frame-controller integration (deferred, needs explicit user approval).** The engine host
  refuses a launch whose world runs a creator `GameplayFrameController` that substitutes native
  input or holds the world (`INPUT_NOT_OWNED`), because the frozen rows would no longer own the
  effective input. Options: keep the refusal (today); let a controller declare that it passes
  physical input through unchanged, checked per row by the host; or record the controller's
  effective output as a second tape lane the replay asserts against. Every option except the
  refusal touches the Mod API surface (a declaration on the controller contract, or a new
  observation hook), so none is implemented. The Typing of the Hedgehog rail builder keeps its own
  loop until this is decided.

## Deviations

Recorded during implementation; each states what the sketch said, what was built, and why.

- **Engine host lives in `com.openggf.tools`, not package-private in `com.openggf.route` (step 1).**
  The sketch put hosts beside the search. The engine host wraps `GameplayCaptureSession` (a
  `tools` class), and `TestArchUnitRules` forbids a new slice joining the core-runtime cycle,
  so `route -> tools` plus `tools -> route` was rejected. `EngineRouteHosts` (public composition
  point) and `EngineRouteWorld` (package-private) sit in `tools`; the core depends only on the
  recorded-input record and loader (`debug`) and the script output record (`game.recording`).
  The core stays game-, trace- and front-end-neutral; a guard pins that.
- **`InputScriptCompiler` moved into `com.openggf.route` (step 1).** Same cycle rule: the script
  grammar is the row codec's, so the codec owner holds it and `tools` consumes it.
- **Boundaries are detected by identity, not `RewindBoundary` events (step 1).** Capture hosts
  disable live rewind, so the boundary reporter is a no-op there. The engine host starts a new
  epoch when the `Level` object, the `GameMode` or the gameplay context changes, which covers the
  same loads, seamless transitions and mode exits for the purposes of checkpoint admission.
- **`RoutePlayback` added beside `RouteReplay.replay` (step 1).** Route tests need the live world
  for domain and rewind assertions as rows play. `RoutePlayback` offers only tape-index stepping
  (no clamping, no controller) and a typed read view (`view(EngineRouteHosts.EngineView.class)`)
  of the host; the guard forbids driving the host SPI from outside the core and host owners.
- **Positioned entries are `Seeds` on a `ColdLevel` inlet (step 1).** The capture host applies a
  start position as declared setup after a cold boot, so a separate `Positioned` inlet would
  duplicate the same boot. The initial `V_int_run_count` is always declared and always written.
- **The BK2 header fix is in the loader (step 1).** BizHawk's own headers are `Key value`, which
  the exporter already wrote; `Bk2MovieLoader` only parsed `Key: value`. It now accepts both. The
  derived movie also carries BizHawk's Genesis key, `SyncSettings.json` and the ROM SHA1.

- **Lead-requested facade additions (step 1, second commit).** For the editor lane:
  `RouteReplay.replay(route, cancellation, observer)` reports every post-row sample of the same
  evidence-minting run; `RouteFiles.writeDraft`/`readDraft` save unqualified work with a `draft`
  flag and no stamp, `RouteFiles.read` refuses drafts and any manifest without a passed engine
  stamp for its exact content, and `TestCommittedRoutesQualifiedGuard` rejects committed drafts;
  `RouteCodec` is the public canonical text codec (manifest, transport text with inline rows,
  launch, goal program); the manifest carries the `GoalProgram` (`checks`), which is content;
  `QualificationStamp` carries `passed`, `divergedRow` and `reason` instead of a verdict string.
  The goal vocabulary at this point is the object-free part (`Achieve` over `PlayerIn`,
  `Settled`, `LevelIs`, `All`; `EnterBoundary`; per-leg and route-wide `Guard`). Object goals join
  it in step 4.
- **Rejoin is nearest-within-tolerance, not reference progress (step 2).** Reference progress is
  the latest index within the 28 px scoring radius, so after an authored window it runs ahead of
  the matching source row by up to the radius. Fast-following source rows from there never tracks
  again (the synthetic witness showed a repair that never resumed original rows). An authored
  node now rejoins at the nearest source index within the six-pixel tracking tolerance, after the
  last source row the path used, whose source row holds the same A/B/C/Start buttons as the
  node's last row (the seam check). Literal rows must then keep tracking, and the final cold
  replay decides.
- **Incomplete checkpoints fail loudly (step 2).** Every restore compares the anchor's recorded
  observation, and every replayed edge compares its end observation, with what the host
  reproduces. A mismatch throws (an incomplete host token is a host bug), rather than returning a
  stop reason a caller might retry.
- **A rebuilt host recaptures its node (step 2).** After a trial crosses an unexpected boundary the
  host is closed and rebuilt by replaying every recorded row; the rebuilt node captures a fresh
  checkpoint, otherwise every later sibling trial would rebuild again.
- **Qualification steps are reported, not budgeted (step 2).** The search budget counts search
  steps (trials and reconstruction). The final cold replay is always run in full and reported in
  `WorkReport.qualificationSteps`.
- **A repair keeps the original's goals and drops its native facts (step 2 fix, step 3 commit).**
  The step-2 planner rebuilt a repaired route without the original's `GoalProgram`, so a repaired
  route lost its replay checks (found by the editor lane). The solved route now keeps
  `original.checks()` and the cold qualification replay evaluates them; tracking the reference is
  not enough if a goal fails. Native facts and native stamps are not carried: they describe the
  old rows. `RepairReport.firstChangedRow()` says how far the rows are unchanged and
  `nativeStale()` says the native check must be re-run.
- **Repair takes an explicit `ReferencePath` (step 2).** It doubles as the tracking contract: for
  a route repair it is the route's own rows replayed on the engine it was authored against. It is
  not yet persisted in the manifest.

- **Two expanders over one frontier (step 3).** `PrefixSearch` keeps the frontier, row journal,
  checkpoint envelope, reconstruction and budgets; an internal `Expander` decides edges, progress
  and completion. Reference following (`ReferenceExpander`, step 2's logic) and point goals
  (`PointExpander`, the former `StageplayRoutePlanner.searchPass`) are the two expanders. The
  sketch described one undifferentiated rollout engine; the point search's A*-style ranking and
  twelve-row steering edges did not fit the reference trial/commit shape without special cases.
  Both still record accepted rows and commit by literal replay.
- **Hosts may certify an equivalence key (step 3).** `RouteWorld.equivalenceKey()` defaults to
  empty (no merge). The static-terrain probe certifies every future-relevant probe field,
  including subpixels and ground speed, which is stricter than the old Stageplay key (floored
  position, no ground speed or subpixels); live worlds certify none.
- **Draft tier and qualification hosts (step 3).** `RoutePlanner.withDraftTier(probe)` searches on
  the probe and qualifies on the planner's hosts. Stageplay's qualification host
  (`StageplayRouteHosts.SceneQualifier`) installs the route's rows as the candidate document's
  `FrozenInputTrack` and replays the document from frame zero, so qualification and final
  playback read rows at the same `StageplayLiveWorld` input boundary. Hosts that need the rows to
  open a replay override `RouteWorldFactory.openColdReplay(RouteArtifact)`.
- **No live scene search yet (step 3).** The scene host replays only; escalation to a live search
  after a failed probe draft (for object goals in scenes) is deferred. A draft that fails live
  qualification reports `VERIFICATION_FAILED`, as before.
- **Run advice dropped (step 3).** The old Stageplay search ran a diagnostic Run pass after a failed
  Walk search and appended "needs Run" advice. B rejects diagnostic second searches inside the
  core; the advice is gone. A front-end could restore it by submitting a second task.
- **Shot obligations are front-end checks on the qualification replay (step 3).** Death, damage,
  timeout, set change, overshoot, skid under a gentle stop, the stroll cap and the end-of-window
  settle/facing/spindash checks run as a `RouteReplay.Observer` over the single qualification
  replay, through `RoutePlanner.plan(task, cancellation, listener, observer)`.
- **Probe-tier tests qualify on the probe (step 3).** `StageplayRoutePlanner.draft` (package-private)
  plans with `StageplayRouteHosts.probeOnly`, whose replay host is the probe itself, so focused
  tests can check the draft tier. Production never qualifies on static terrain.
- **Arrival tolerance (step 3).** The search accepts arrival at 12 raw speed units (the old search
  tolerance) and the replayed end of the window must be settled within 64 (the old verification
  tolerance), so the two numbers remain, now named in `PointGoal`.
- **Object capabilities are three engine interfaces (step 4).** `com.openggf.level.objects.observe`
  holds `PhaseSource`, `AttachmentSource` and `HitPointSource`, implemented by the owning objects and
  found with `instanceof` by the engine host. First owners: the SSZ `$77` cutscene bridge
  (waiting/retracting/extended), the FBZ disappearing platform (hidden/appearing/solid/vanishing,
  `Level_frame_counter`), the updraft (a passive `AIRFLOW` emission set only at its successful
  lift and cleared each update), the MHZ swing vine (`HANG` while its `$32` grab flag is set),
  `AbstractBossInstance` (its own hit counter and defeat flag), the DEZ miniboss (both eight-hit
  phases) and the LRZ end boss. The host copies their facts into `RouteSample.objects()` and
  `interactions()` (solid rides are `STANDING`); the core sees values only.
- **Targets are resolved per sample (step 4).** `ObjectTarget.Placement`, `Instance` and `Typed`
  (nearest live object of a type with a capability) resolve against each sample's object list
  instead of binding once into the goal cursor, as the sketch proposed. Resolution is
  deterministic (distance, then identity); a target that moves out of reach is simply absent.
- **Goal programs start after a prefix (step 4).** `GoalProgram.startRow` leaves inherited prefix
  rows unchecked, so an encounter authored after a long cold prefix replays with the same leg
  bounds it was searched with.
- **`RouteTask.Author` now means goal programs; scene shots are `RouteTask.Point` (step 4).** The
  step-3 commit named the point-goal task `Author`; B's `Author(launch, goals, ...)` is the general
  task, so the point task was renamed when the general one arrived.
- **Encounter scoring moved into the core (step 4).** The boss authors' policies and their damage
  score are `GoalExpander`'s encounter families; `SszBossInputAuthorTool` and
  `DezMinibossInputAuthorTool` keep only their encounter tables and the shared
  `EncounterRouteAuthor` front-end. MECHA2's two-phase ending is now whatever Mecha Sonic's own
  `defeated()` reports (its hit counter); the zone ending flag the old tool read is not yet an
  owner fact.
- **Repair tracks are recorded files, not a `TrackingContract` type (step 6).** A route's track is
  a gzip CSV (`row,x,y,epoch,dead`) under `src/test/resources/routes/s3k/tracks/`, its header binding
  the engine revision and the SHA-256 of the exact movie it replayed. `RouteTrackReference` reads it
  as `ReferencePath.recorded(...)`; a track for other rows is refused. `RouteRepairTool` is the
  front-end. The sixteen SSZ1 tracks were recorded on `36aaddb37` in one temporary copy-on-write tree,
  removed afterwards.
- **The `lrz-*-hpz-ssz1*` routes have no trustworthy track (step 6).** On `36aaddb37` they die before
  SSZ1 (Tails in LRZ1 at row 8444, the pair at 36000, Sonic at 44639), as on the current engine
  (8444, 36000, 6683); the frontier log has carried their campaign tests red since 2026-10-17. They
  do not share the SSZ1 opening break, and they are not repaired against those tracks.
- **The SSZ1 repairs stalled (step 6).** Every measured SSZ1 route first leaves its `36aaddb37` track
  at row 1366, as designed. Fourteen of sixteen run clean to their end there (`middle-320` dies at
  6151 and `complete-800` at 11664). The paired `complete-320` repair kept rows to about 3486, then
  stalled at row 3566 for 1248 expansions: at row 3489 the current engine lands P1 on terrain at
  Y 2540, where the `36aaddb37` run fell to the Y 2604 floor and the `$74` retracting spring at
  (2656, 2608) launched it left. That path is not reachable by local candidates. The solo-Tails
  repair stalled at row 3266 with P1 flying a steady 11-16 px off its track from row 2971; a 12 px
  rejoin tolerance (an experiment, not committed) stalled at the same row. Neither wrote a route,
  so the consumers keep their `.bk2` movies, and the two smoke reds remain red.

- **Exact re-synchronising repair (step 6, `feature/ai-shared-route-planner-2`).** Following a
  position track within six pixels cannot express what the SSZ1 routes needed. Re-measured on
  `d84b25bd8`, all sixteen routes still first leave their `36aaddb37` track at row 1366, and the
  residue that matters is sub-pixel: for solo Tails, P1's state at row 2000 equals the
  `36aaddb37` run in every field except `x_sub` (10496 there, 9728 now). Transplanting only that
  subpixel at row 2000 makes the current engine reproduce the `36aaddb37` track in every recorded
  field for all 14923 rows, so the route's rows are still right; only the state they start from
  moved. `AuthorHints.exactResync(sidekick)` with a reference carrying exact
  `ReferencePath.PlayerState`s (centre, subpixels, raw speeds, air and roll bits, per row) runs
  `ResyncExpander`: source rows are mandatory while every tracked state matches exactly; where they
  part, single-row edits (a direction held or released, the jump buttons toggled) in the next 320
  rows are tried, best first by remaining state distance, up to four per window, until 120
  consecutive rows match again. Edits replace rows, so frame-counter timers stay aligned. The
  qualification replay adds an exact check on every rejoined row. Tracks are now recorded by
  `RouteTrackRecorder` (full P1/P2 states) and the sixteen 36aaddb37 tracks were re-recorded with
  it in one temporary copy-on-write tree (positions identical to the old tracks).
- **The solo-Tails offset was the bridge, not a second engine change (step 6).** The repair's
  "steady 11-16 px from 2971" chain: the 1366 airborne release leaves a 768-subpixel `x_sub`
  residue; the wall at x 1141 re-synchronises the pixel (rows ~1900-2000) but not the subpixel; at
  2879 the residue crosses a pixel on the slope (x 4376/4377, angle 16/14); by 2898-2900 the wall
  contact zeroes `x_vel` one row apart, so the leftward air acceleration from 2900 starts a row
  later; P1 lands at 2972 instead of 2971 and the jump pressed at 2972 is lost (it fires at 2976).
  The subpixel transplant above is the evidence. There is no native solo-Tails entry movie (the
  only native SSZ1 entry is the Sonic+Tails complete run), so native evidence for 1366 is the
  pair route's fixture, where native follows the current engine.
- **An engine regression the re-synchronised routes exposed (fixed in step 6).** With the
  subpixel repaired, the pair route next parts at 3428 and solo Tails 800 at 6847, both after a
  jump off `Obj_SSZRotatingPlatform`'s carrier. Its jump branches `loc_45E90`/`loc_45FE4` leave the
  object's standing bit set; the next frame's `SolidObjectTop` takes the airborne release
  `loc_1E2E0` (sonic3k.asm:41852-41857), clearing `Status_OnObj`. The engine's `jumpOff` released
  the ride (and the standing bit) at once, so that release never ran and `Status_OnObj` stayed set;
  `36aaddb37` had cleared it through a since-deleted end-of-frame sweep. Native SSZ1
  (`hpz_completerun`, frames 3243 and 3314 at the same carrier) clears it on the next frame, so
  the current engine had regressed; a stale bit made `Player_AnglePos` skip floor following and P1
  ran flat through the slope at 3528. The fix keeps the ride for the next `SolidObjectTop`.
- **Pair routes re-synchronise the leader only (step 6).** P2 (the CPU Tails) first differs at
  1416, from P1's differing history; P1 cannot be exactly re-synchronised before then (bridge
  losses come in airborne/landing pairs and walking moves in 12-subpixel steps), and P2 never
  re-synchronises later on its own. `exactResync(false)` therefore tracks P1 exactly and lets P2
  differ; the qualification replay still checks P1 exactly on every rejoined row and the route's
  boss and boundary goals. The 800-wide pair family (`first-replica-800`, `second-replica-800`,
  `complete-800`) parts from P1 in the GHZ fight (rows ~4214-4608), where P2's different position
  changes the fight; no single-row P1 edit re-synchronises it, so those routes are not repaired.
- **Repair results (step 6).** Engine-qualified (cold replay with goals and exact states):
  solo Tails `complete-320`/`-800`, `replicas-320`/`-800`, `flight-320` (one edited row, 1495);
  solo Sonic `complete-320`, `replicas-320` (rows 1597 and 2297); pair `complete-320`, `upper-320`
  (row 1597) and `first-replica-320`, `ghz-320`, `staircase-320` (rows 1597 and 2199). Goals: the
  replica and Mecha `defeat` legs and the DEZ boundary where the route claims them; the three
  short pair routes never meet a boss on either engine, so they carry the route guard only.
  Not repaired: `middle-320` and `complete-800` (P1 already died on `36aaddb37`) and the two other
  800-wide pair routes; their movies stay as they were.
- **Facade additions (step 6), all source-compatible.** `ReferencePath.PlayerState` (record with
  `of(PlayerView)` and `distance(PlayerView)`), `ReferencePath.withStates(List, List)` and
  `hasStates()`; `AuthorHints.exactResync(boolean)`. Nothing existing changed signature;
  `ReferencePath.extract` now also records states. Tools: `RouteRepairTool.repair(..., hints, scope,
  checks, listener)`, `RouteTrackRecorder`, `RouteNativeStampTool`.
- **Native composition is a tools script, not a core `NativeCheck` (step 6).** The engine core
  cannot run BizHawk. `tools/traces/route_native_check.py` composes the entry prefix, the route's
  exported rows and one filler per observed lag frame, captures each pass with TraceChaser's
  headless harness, and compares both players with the route's engine track; lag facts and the
  verdict are then written into the manifest as native facts and a `BIZHAWK` stamp
  (`RouteNativeStampTool`). Only routes with a native entry (the Sonic+Tails complete run) can be
  checked; solo routes stay `UNSUPPORTED_INLET`. A lag frame is a row where the ROM's
  `Lag_frame_count` rose (the game resets it after each polled frame); one capture yields every lag
  up to the first input change, because a lag that consumed a route row only shifts later polls by
  one row. The SSZ1 Sonic+Tails routes lag every other frame from row 1958 (five fillers) and then
  agree with the engine in every player field through row 1968; at 1969 the engine lands P1 on
  `Obj_SSZFloatingPlatform` at zero penetration, which `SolidObjectTop_1P` (`loc_1E45A`) rejects.
  The five manifests record "engine-qualified, native-diverged at row 1969". No SSZ1 test carried a
  `NATIVE_LAG_FRAMES` list; the HPZ one stays until its routes migrate.

## Next step

Steps 1-4 are delivered. Step 6 repaired and manifested twelve of the sixteen SSZ1 routes by exact
re-synchronisation, switched their consumers to the manifests and native-checked the five
Sonic+Tails ones (diverged at 1969 on the floating platform's zero-penetration landing). Open: that
landing (owned by the solid-contact work; fixing it moves the routes off their recorded run at
1969, so they would then need a goal-authored continuation), the four unrepaired SSZ1
movies (two already died on `36aaddb37`, two need the 800-wide GHZ fight authored as a goal
program because the CPU sidekick cannot be re-synchronised), the `lrz-*-hpz-ssz1*` campaign
routes, the remaining route consumers' manifests, and steps 5a and 5b.
