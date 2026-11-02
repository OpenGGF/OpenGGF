# Shared route planner: route tapes

Status: accepted design, implementation in progress on `feature/ai-shared-route-planner`
(based on `feature/ai-weakness-pass` at `0baca6ee4`). Produced by the architect workflow:
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

## Editor integration

Owner: the editor lane on `feature/ai-route-editor-support` (OpenGGF) and `feature/ai-route-tapes`
(OpenGGF-Editor). Editors are front-ends: they call only `RoutePlanner`, `RouteReplay`,
`RouteFiles` and the value types, and never construct a host in the editor process.

### Where the work runs

The engine host is process-global (`EngineRouteHosts`: one world per process), and the editor
process owns the live level session. Every job that boots a world (replay, plan, repair,
qualify-and-save) therefore runs in an isolated worker JVM, as `StageplayRouteJob` already does.
The worker is a thin tool, `com.openggf.tools.RouteEditorWorker --worker`, which composes
`EngineRouteHosts` and calls the facade. It speaks `RouteEditorProtocol` (one Base64-safe record
per line): bounded progress, path points and one result on stdout; cancel destroys the process
tree. `RouteTapeJob` is the only editor class that references the protocol, an explicitly owned
`editor -> tools` dependency.

Pure file and value work stays in the editor process: `RouteFiles.readDraft` (editors open
drafts and unqualified routes; consumers use `read`), `writeDraft`, `tapeFromScript`,
`scriptText`, `exportBk2`, tape edits and timeline layout.

`ReplayEvidence` cannot leave the worker (no public constructor, by design). So **Save** is a
worker job: the worker cold-replays the working artifact and, on a pass, calls
`RouteFiles.writeNew` or `replace`. A failed replay leaves the file untouched and the edits
unsaved in the editor, marked with the first diverging row.

### Shared engine-side model (`com.openggf.editor.route`)

Both front-ends present one model, as they do for `StageplayWorkspace`; neither owns a second
route model.

- `RouteTapeWorkspace`: the open route (stem, loaded `RouteArtifact`), the working `InputTape`
  with row-edit undo/redo (each edit one step), cursor row, selected lane and button, selection
  range, the goal program being authored, the current job and its last result. Actions and forms
  use `EditorProjectMenu`, so the desktop presents the same forms natively.
- `RouteTapeTimeline`: frontend-neutral layout of a two-lane window: for each of the 16 tracks
  (P1 and P2: Up, Down, Left, Right, A, B, C, Start), the held runs inside `[first, first+rows)`,
  plus markers: cursor, selection, first diverging row, native lag rows, repair windows
  (searched spans) and kept original slices. Painting reads values only.
- `RouteTapeOverlay`: the per-row P1/P2 centre path from the replay samples, painted in world
  coordinates with the editor camera transform, using the same ink-outlined stroke style and
  ground/air colours as `StageplayRouteOverlay`. Missing samples break the line; it never
  invents a path.
- `RouteTapeJob`: phases `IDLE, RUNNING, PASSED, FAILED, SOLVED, STOPPED, CANCELLED`, generation
  tokens and a freshness check (the working content hash at submission), as `StageplayRouteJob`.

### Features and the facade calls they use

| Feature | Facade | Where |
|---|---|---|
| Open route | `RouteFiles.read(manifest)` | editor |
| New route (launch form: ROM game, level, roster, width, initial `V_int_run_count`, seeds) | `LaunchContract`, `RomIdentity.standard`, `RouteArtifact.of` | editor |
| Edit rows (toggle, paint run, insert/delete/duplicate rows, set lane neutral) | `InputTape`, `PadRow`, `InputTape.Builder`/`slice`/`append` | editor |
| Edit as text | `RouteFiles.scriptText`, `RouteFiles.tapeFromScript` | editor |
| Replay: qualified / not qualified, first diverging row, path overlay | `RouteReplay.replay` → `ReplayResult.Passed/Failed/Unsupported` | worker |
| Save (qualify, then write) | `RouteReplay.replay`, `RouteFiles.writeNew` / `replace` | worker |
| Plan a goal program (progress, cancel) | `RoutePlanner.plan(RouteTask.Author)`, `Cancellation`, `PlanResult.Solved/Stopped` | worker |
| Goal legs | `GoalProgram`, `Leg(goal, guard, hints, maxRows)`, `RouteGoals.board/ride/attach/hold/awaitPhase/defeat/cross` | editor builds, worker plans |
| Object targets: placed or runtime-spawned | `ObjectTarget` placement and `select(alias, capability, region, order)` | editor |
| Repair from row r (kept share, searched rows) | `RouteTask.Repair`, `RepairScope`, `PlanResult.Solved.repair()` | worker |
| Native facts (display only) | `RouteManifest.nativeFacts()` (`NativeEntry`, `LagFact`), BIZHAWK `QualificationStamp` | editor |
| Export BK2 | `RouteFiles.exportBk2` | editor |

Qualification status shown for the working content: **Qualified** when a passing ENGINE stamp
matches `RouteArtifact.content()`; **Stale** when stamps exist only for other content (edited
since); **Not qualified at row r** after a failed replay. A BIZHAWK stamp or lag facts show as a
separate **Native** line, labelled observations; they never feed planning or replay.

### In-engine HUD (`StageplayHud` idiom, MenuChrome, controller-first)

A **Routes** workspace beside Stageplay: the `Routes` plate beside the Stageplay plate and the
editor command sheet (F5 already starts a fresh playtest in the editor, so no function key).

- **Level view** (centre): the editor's level when it matches the route's `LevelId`, with the
  path overlay; the camera follows the path sample at the cursor row. When the level differs, a
  plate offers to load the route level. The route never changes the editor's level silently.
- **Route panel** (left): file and launch summary, qualification and native lines, repair
  summary (kept share bar, searched spans), job progress with Cancel.
- **Timeline** (bottom): P1 above P2, eight tracks each, a row ruler with run-length bars, cursor
  and markers. D-pad left/right moves the cursor (held accelerates, L/R page), up/down picks the
  track, A toggles the button at the cursor or across the selection, X starts/ends a selection,
  Y opens the row menu (insert, delete, duplicate, neutral). Keyboard mirrors it (arrows, Space,
  Shift+arrows, Insert/Delete, Ctrl+Z/Y).
- **Start** opens the route menu: Open, New, Save, Replay, Plan, Goals, Repair, Export BK2,
  Details, Level editor. Forms (launch, leg, repair scope) are `EditorProjectMenu` forms.

Built on `EditorHudCanvas` (the engine pixel font and quad renderer); no toolkit dependency.

### Desktop (OpenGGF-Editor, `StageplayChromeShell` idiom)

`RouteChromeShell` presents the same `RouteTapeWorkspace`:

- Main menu: File (Open, New, Save, Save as, Export BK2), Route (Replay, Plan, Repair, Cancel),
  View. Status at the right of the menu bar: Qualified / Not qualified / Unsaved changes.
- Dock: **Route** inspector (launch, qualification, native facts, repair report), **Goals**
  (leg list with add, edit through the shared form, move up/down, remove; target picking on the
  canvas), **Tape** timeline (draw-list grid; click toggles, drag paints a run, wheel scrolls,
  Shift+click selects), level canvas with the overlay through the same `Painter` interface.
- Shared forms render through the existing `StageplayFormView` path for `EditorProjectMenu`.

### Stageplay player routes (after planner step 3)

The Stageplay **Plan route** helper keeps its form (destination, start/speed/stop/facing, budgets)
and its review-then-Apply flow. Its result becomes a `FrozenInputTrack`: the review shows the
path overlay plus the track's two-lane rows in the same timeline component, read-only until
applied. Apply installs the track as one undo step through the existing freshness check. In the
Stageplay cue list, a frozen track is one row per bound actor (`Inputs  frames a–b`); selecting
it opens the rows in the timeline. Editing rows makes the track unqualified until re-planned or
re-qualified through the scene host. Object (visual-terrain) routes keep their existing path.

### Object goals (after planner step 4)

A leg form picks the goal word, the player and the target. Targets come from two sources:

- **Placed**: pick a placement on the canvas (layout index, object id, subtype), giving a
  placement target.
- **Runtime**: choose a capability (attachment, phase, hit points, airflow), a region drawn on
  the canvas, and a spatial order, giving a selector. This covers objects that the layout does
  not contain, such as the SSZ replica bosses that `Sonic3kSSZEvents` constructs. An optional
  **Scan at row r** asks the worker to replay to row r and list the live objects with
  capabilities, so the author can pick one by its type and position.

`awaitPhase` and `ride(until phaseIs)` offer the phase states that the chosen capability
declares. `defeat` needs a hit-point target.

### Facade gaps (requested from the planner lane)

1. **Replay observer.** `RouteReplay.replay(route, cancellation, observer)` delivering each
   post-row `RouteSample`, so one evidence-minting run also yields progress and the path overlay.
   Without it the worker replays twice (`open` for samples, `replay` for evidence).
2. **Plan progress.** A progress observer on `plan` with count snapshots (expansions, steps,
   committed rows, best row), for the job's progress line.
3. **Unqualified drafts.** `RouteFiles.writeDraft(stem, artifact)` (no evidence; writes no stamp
   for this content) or a public manifest text codec. Needed to save work in progress that does
   not yet replay, and to hand an in-memory artifact (launch, goals, tape) to the worker.
   Decision needed: are unqualified drafts allowed on disk?
4. **Goals in the manifest.** The manifest carries the `GoalProgram` (B's `checks`) through the
   canonical codec, so an authored program persists and transports with the route.
5. **Structured native divergence.** `QualificationStamp.divergedRow()` (and reason) instead of
   parsing the verdict string `diverged at row N: reason`.
6. **Repair and stop reports (step 2).** Public `RepairReport` with original rows, kept rows,
   kept share, original slices `(sourceStart, outputStart, length)` and searched output ranges;
   `Stopped` with reason and the furthest row reached.
7. **Object targets (step 4).** A public `Capability` enum, the phase states a capability
   declares, placement-target construction from editor-known placement fields, and a sample-time
   list of live objects with capabilities (instance key, optional placement, type key, centre)
   for scan-and-pick of runtime-spawned objects.
8. **Stageplay frozen tracks (step 3).** `FrozenInputTrack` exposes its tape, interval and
   lane bindings; the Stageplay route result carries the track and its observed path, so the
   existing review/Apply flow and overlay stay.
9. **Native-composed export (step 6).** If the native composer becomes public, an export that
   includes the entry prefix and lag fillers, beside the plain `exportBk2`.

### Verification

- In-engine: focused headless tests for `RouteTapeWorkspace` (open, edit, undo, stale
  qualification, job freshness), `RouteTapeTimeline` layout and `RouteTapeOverlay` geometry, the
  worker protocol with a fake launcher, and an offscreen surfaceless-EGL render of the HUD
  timeline and overlay.
- Desktop: its suite including `TestStageplayChrome`, a route-chrome test, and an offscreen
  screenshot of the route panel.

## Deviations

Recorded during implementation; each states what the sketch said, what was built, and why.

- **Editor worker transport (editor, step 1).** The first editor slice (`fceed7b1d`) handed the
  worker the original manifest path plus script text and replayed twice (playback for the path,
  then the qualifying replay). Once the facade gained `RouteCodec.formatTransport` and the replay
  observer (`3432b401d`), the worker takes the in-memory route as transport text and replays once,
  copying post-row centres from the qualifying pass; new routes no longer need a file first.
- **The worker needs GL (editor, step 1).** `EngineRouteHosts` boots a `GameplayCaptureSession`, so
  the worker opens a GL context. On Linux the job launches it with surfaceless EGL and no display
  variables (a parent's explicit `openggf.headless.gl` passes through); other platforms keep the
  engine's hidden-window default. The first slice stripped the display without selecting EGL and
  failed with "Unable to initialize GLFW" in the desktop picture run.
- **Editor Repair before goal authoring (editor, step 2).** Step 2's planner accepts only
  `RouteTask.Repair`, so the editors' first plan job is Repair: a reference from the route's saved
  rows, another manifest (its own launch), a route script or a movie (this route's launch), with
  `RepairScope` bounds and a reference window. A solved repair's rows replace the working rows as
  one undo step; its spans mark the timeline; a stop offers its best attempt. The planner builds
  the solved artifact with `RouteArtifact.of(launch, tape, provenance)`, which drops the
  original's goal program, so a repaired route that had goals shows as edited until a replay
  checks them (reported to the planner lane). Goal-program authoring waits for an author task.
- **Shared worker plumbing not extracted yet (editor, step 1).** The plan put
  `StageplayRouteJob`'s process lifecycle into one shared helper. Step 3 replaces the Stageplay
  route job's planning, so the extraction waits for it rather than churning a file the planner
  lane is about to change; `RouteTapeJob` owns its own spawn, bounded stderr tail and
  owned-child retirement meanwhile.

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
- **Repair takes an explicit `ReferencePath` (step 2).** It doubles as the tracking contract: for
  a route repair it is the route's own rows replayed on the engine it was authored against. It is
  not yet persisted in the manifest.

## Next step

Step 1: the typed two-lane tape and launch artefact, plus the shared replay-only driver. Pin it
with the existing codec tests and a hostile all-buttons, held-edge round trip before extracting
any search.
