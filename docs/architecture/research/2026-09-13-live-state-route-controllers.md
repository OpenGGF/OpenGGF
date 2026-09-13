# Live-state route controllers: technique and applications

Research artifact, 2026-09-13. Written on `feature/ai-route-controller-writeup`
from seven parallel code explorations, then revised against a fact-check and
an adversarial review of the draft (section 12). It proposes; nothing here is
an approved design.

The subject is the input technique behind the FBZ2 native route on
`bugfix/ai-fbz-route-tests` at `6db3634b2`. `TestFbzCompatibilityMatrix` there
runs 24 methods: 13 synchronous-transition preflights (five teams, five
widths, three donors) and 11 complete routes (five teams, four widths, two
active donors). 21 methods pass; of the 11 complete routes, 8 pass and the
640px, 800px and S1 donation routes fail. The question asked was whether the
technique has applications beyond FBZ.

## 1. Summary

| Application | Feasibility | Complexity | Impact | Recommendation |
|---|---|---|---|---|
| Shared route primitives in test scope (LTS-04) | high | low for primitives, moderate for a stage interface | high: every future zone route | primitives first; stage interface only after a second zone |
| Level-test standard adoption across zones | high for ROUTE, partial for the rest | days per act, assumptions in section 4 | medium: four of eleven obligations | pilot AIZ/HCZ, spend the rest on rewind spots and short checks |
| Rewind determinism along routes | high, machinery exists | low | medium: a stress-lane property, not the spot obligations | do, in the stress lane beside `TestRewindTorture` |
| Route smoke in local category runs and self-hosted CI | high locally, blocked on ROMs in hosted CI | low | medium: covers configurations no replay can | local first, CI with a skip assertion |
| Benchmark and audio soak input | high as committed mask logs, medium as a live controller | low for mask logs | medium-high: first completing route under widescreen and fast FM | mask-log replay, not main-scope promotion |
| Creator-content regression routes (Mod API) | medium | moderate | narrow now, fits 0.8 more than 0.7 | record-and-replay toolkit, never a prover |
| BizHawk Lua port to author BK2s | unproven | high | unclear, and a ROM run proves nothing about the engine | do not port; three prerequisites listed |

The common thread: the technique scales through shared primitives and, later,
a stage interface, not through generality. Every obstacle still costs a
hand-written rule with a cited source, and the FBZ2 class itself is still a
hybrid of live-state gates and scripted segments.

## 2. The technique

### 2.1 What it is

A route controller plays a level headlessly through the ordinary pad. Each
frame it reads live engine state, picks the stage that owns the current
obstacle, and returns a pad mask that `HeadlessTestFixture.stepFrame` feeds
into the normal input path. Milestones and invariants are asserted from live
objects and events, not from positions matched against a trace.

Evidence on the FBZ branch (`src/test/java/com/openggf/game/sonic3k/objects/TestFbzAct2TraversalPreboss.java`, 6,319 lines at `6db3634b2`):

- `FixedInputRunner` (line 716) holds the fixture, the `ObjectManager`, a
  `FrameObserver`, two identity sets swapped each frame for spawn and despawn
  observation, and a recent-frame ring buffer for diagnostics.
- `runUntil` is one per-frame loop whose mask decision is an else-if chain of
  168 arms; three further stage machines cover the descending elevator
  corridor (958), the lower crane loop and the arena-to-exit segment, and
  `runUntilMidpointApproach` (5134). The runner holds 8 private boolean
  latch fields and the class declares 58 further boolean stage locals.
- Masks are stepped by an eight-line adapter, `stepMask` (line 614).
- `RouteMilestones` (line 6022) accumulates 49 private fields into the public
  `RouteCompletionEvidence` record (6277) consumed by the matrix.

### 2.2 What a rule consists of

Every live-state rule has the same five parts:

1. an entry condition from route context plus a coarse position band, so it
   owns only the frames where its obstacle is in play;
2. a gate read from live state: a car's centre and `yVelocity()`, an Obj74
   column's `displacement()` and last sampled polarity, the AnPal_FBZ timer's
   remaining runway, a badnik's position and collision flags, P1's own speed;
3. an action expressed as a mask: `steerMask` toward a target with tolerance
   and velocity-projected braking, a jump held for N frames, DOWN taps for a
   spindash charge, or neutral to wait;
4. a stage transition when the live world shows the action worked;
5. a bounded retry or wait limit that fails loudly with a diagnostic naming
   the stage owner, player state, nearby objects and recent inputs.

Constants are cited in place, and the citations are of two kinds. Some name a
ROM routine: the follower allowance uses the 17-frame Pos_table lag of
`Tails_CPU_Control` loc_13DA6; the spring wait uses `Obj_RetractingSpring`'s
4/60/4/60 cycle; ball lift accelerations come from loc_3B18C. Others name
fixture rows: the shaft jump waits for a descending car centre `$08`-`$12`
below P1 because rows 24792-24882 show the BK2's jump landing on the car top
at that phase. The mechanism in the second kind is physical (a jump of known
reach meets a car of known descent), but the numeric window was fitted to the
recording, not derived from a routine.

### 2.3 What the class is today: a hybrid

The branch is not a pure live-state controller, and the write-up should not
present it as one. A census of the class at `6db3634b2`:

- 43 comment lines cite disassembly labels or routines; 37 cite BK2 frame
  addresses or fixture rows.
- 74 literal `InputRun` entries remain. The first 2,889 frames are a verbatim
  replay of the BK2's inputs, and scripted segments survive inside the
  controlled part: the 122-frame protected spike corridor is consumed as fixed
  frames, and comments such as "Match BK2 $5DD6-$5DE2 exactly" and
  "BK2 $6B71-$6B75: exactly five neutral JUMP frames" mark others.
- Run-index constants (`SPIKE_CORRIDOR_START_RUN = 27`, `END_RUN = 39`) key
  the corridor to positions in the authored program.

The level-test standard requires that assertions are not "copied from
current engine output or fitted to a failing recording". The fitted shaft
window is a fit to a passing recording, which that sentence does not literally
address, so the branch is not in breach of it; but the residual scripted
segments and fixture-fitted windows are still the part of the class that a
recording, not the ROM, explains, and converting them is unfinished work that
belongs in any per-act cost estimate, not a solved problem.

### 2.4 What it never does, and one standing exception

- It never hydrates engine state from trace rows. Fixture rows are evidence
  for constants, never input to the engine.
- It never branches on a width, donor name, route identity or frame number,
  except for the run-index keys in section 2.3 that position the scripted
  corridor inside the authored program.
- It does not solve levels. There is no search, pathfinding or learning. A
  deviation outside every stage's gates stops the run at the first assertion,
  by design, so a silent recovery cannot hide an engine change. The corollary
  is that a deviation inside a gate's tolerance is absorbed, so a live-state
  route can recover from a genuine defect that a replay would report.

The exception is in production, not the test. The S1 donation route depends
on three `src/main` classes, `FbzS1DonationUpperLoopAssist`,
`FbzS1DonationLowerLoopAssist` and `FbzS1DonationSqueezeAssist`, dispatched by
`Sonic3kFBZEvents`, which give the spindash-less S1 profile a run-to-activate
capability replacement at three authored spots. They are semantic gates
(donation active, rules lack spindash, P1 grounded and moving left in the
approach) rather than a donor-name branch, and `fbz-compatibility.md`
documents them, but they are a per-profile gameplay carve-out added so the S1
row could proceed. Two consequences: the S1 row, when it passes, proves
compatibility with those assists, not that ordinary S1 inputs complete the
act; and the "leave it red" rule in section 11 has a precedent against it.
This document does not decide whether the assists stay. It records them as
the decision the rule would have to be reconciled with.

### 2.5 What it costs, and what is unmeasured

The FBZ2 route took the commits `f90faf0c6`, `49a28095f` and `6db3634b2`
(2026-09-12 to 2026-09-13) to convert the second and third quarters of the act
from replay into controllers, on top of an act the engine already completed,
an existing 1,486-line act-1 route class, and the `fbz_completerun` fixture as
a source of cadence evidence. Each new hazard was one loop: trace the failing
frames, find the obstacle's condition in the disassembly or the fixture, add
a gate that reads it from live objects, cite the source.

The 640px, 800px and S1 routes fail at frame 5877 standing at `$08BE,$02EC`,
inside the replayed prefix. The prefix is their first blocker: a wider
viewport activates the `$0818` chain's neighbours in another phase, the
mouse that knocked the BK2 player off the chain is absent, and every later
authored input lands elsewhere. Nothing after the prefix has ever executed at
those widths or with the S1 profile, so whether the rest of the route holds
there is unmeasured, not established.

## 3. Shared route primitives

**Current state.** No generic navigation helper exists anywhere.
`HeadlessTestFixture` (578 lines, used by 260 test files) offers the builder,
`stepFrame`, `stepIdleFrames`, `sprite()`, `camera()`, `runner()`, but nothing
above raw booleans. `TestFbzAct1RouteHeadless` (1,486 lines) hard-codes
`stepFrame(false,false,false,false,false)` in loops; `TestS3kAiz1SkipHeadless`
(606) uses bare right-hold loops; `TestFbzNativeCharacterRoutes` (258)
carries a private mask list; `TestS3kCnzTeleporterRouteHeadless` (561) steps
idle frames only. About 2,900 lines of existing route tests drive input by
hand. The standard's
backlog item LTS-04 already names "minimal shared case/rewind helpers"; this
is that item, not a new layer.

**What is generic in the FBZ class.** `InputRun`, mask constants, `stepMask`,
the `FrameCheck`/`StopCondition`/`FrameObserver` contracts, the runner loop
with its swapped identity sets and diagnostic ring buffer, `steerMask` in both
forms (50 uses), `walkMask` with a ground-speed cap, `ordinaryBrakeDistancePixels`
derived from `getEffectiveRunDecel`, the spindash charge-and-release with a
computed release lead, fixed-hold jumps, generic milestone counters, and the
sidekick death/respawn audit.

**What is not.** Every hex coordinate, every latch, cage/crane/column
boarding by phase, polarity and runway gates read through
`FbzZoneRuntimeState` (27 sites), and every FBZ object type. By inspection
the large majority of the class is FBZ policy that cannot generalise; no
line-level measure of that share was made.

**Shape.** Test scope only: the helpers read `getLatchedSolidObjectInstance`,
`getSpindashCounter` and `getEffectiveRunDecel`, which is engine-internal
scaffolding, not a shipped tool. A package beside the fixture,
`src/test/java/com/openggf/tests/route/`, with a one-line entry in
`docs/agent-workflow/README.md` per the preservation guidance. Two steps:

1. Extract only the clearly generic primitives now: runner loop, mask
   program, `FrameCheck`/`StopCondition`/`FrameObserver`, steering and
   braking, jump hold, spindash charge/release, milestone observer base. A
   few hundred lines, one to two days, with the FBZ class calling them.
2. Extract a stage interface (an ordered list of stages replacing the else-if
   chain) only once a second zone exists to shape it. Extracting it from one
   6,300-line instance would fix the precedence of that instance's chain into
   an abstraction before anything else has needed it.

**Acceptance for the migration.** "Unchanged" means: for the eight green
routes, identical `RouteCompletionEvidence` fields and identical frame
numbers at every milestone before and after; for the three red routes, the
same failing frame and diagnostic. Mask-override precedence currently lives in
source order and is the main thing a refactor can silently change.

**Risks.** The runner's dozen FBZ latch fields have to move into per-stage
objects without changing when they are read. The ground-speed helpers assume
S3K rules and need checking against Sonic 1 and 2 physics before reuse there.

**Verdict.** Feasible, low effort for step 1, the prerequisite for the rest.

## 4. Level-test standard adoption

**What the standard asks.** `docs/guide/contributing/level-test-standard.md`
requires, per implemented act: five widths, native plus every donor, every
main character with separate routes where progression differs, five team
shapes, five lifecycle cases, and eleven behavioural obligations of which
ROUTE is one. REWIND is mandatory with seven spot kinds, each needing capture,
restore and forward replay compared against a recorded state, reported
independently and never dependent on an earlier step. The standard refuses to
put local assertions behind long traversals: short scenarios go to the
ordinary lane, exhaustive routes to a deeper lane, and complete routes use
representative configurations by default.

**Backlog state.** `docs/status/level-test-coverage.md`: LTS-01 documented,
LTS-02 in progress, everything else pending; zero per-act matrices exist
across an estimated 26 zones and 55 gameplay acts. The plan
(`docs/architecture/plans/2026-09-13-level-test-standardisation.md`) names FBZ
1/2, AIZ 1/2, HCZ 1/2, S1 GHZ3 and S2 CPZ2 as phase-2 pilots and shared
helpers as phase 3. Prerequisite enforcement and generated coverage reports
are not installed.

**What FBZ2 already satisfies.** ROUTE in full for eight configurations;
OBJECT, CAMERA, BOSS and LOAD evidence through the completion record; all
three matrix axes (teams, widths, donors). On develop the matrix's exhaustive
routes sit behind `slow-suite` and `fbz-route` tags and the `-Pfbz-routes`
profile; the branch at `6db3634b2` predates those tags and will need them
re-applied on merge. Short independent checks are already in the ordinary
lane, which is the standard's preferred shape.

**What it does not.** No route-milestone rewind (FBZ does have about a dozen
rewind classes for its objects, events and bosses, two of which the standard
lists as worked examples, but they are independent scenarios, not the route).
No per-character routes: the matrix varies followers, not the leader, and the
Knuckles and Tails leader routes live in a separate class. No PRESENT or
ORACLE evidence; those stay with the complete-run trace fixture.

**The gap elsewhere.** No other zone has anything comparable. Every other
traversal test seeds a position and steps short bursts; long-range
correctness comes only from BK2 replay, and only FBZ varies width, donor or
team at all. Zones with no route or traversal test class by filename (replay
classes excluded): S3K MHZ, SOZ, LRZ, SSZ, DDZ, DEZ; S1 SYZ, LZ, SLZ, SBZ; S2
CPZ, MTZ, MCZ, OOZ, SCZ, WFZ, DEZ.

**Cost.** The primitives are one to two days. The real cost is route
authoring, dominated by tuning each hazard gate against live geometry. "Several
days per act" is the honest order of magnitude only under the assumptions the
FBZ work enjoyed: the engine already completes the act; a BK2 fixture exists
for that act to supply cadence and phase evidence (every S3K and S1 zone has a
complete-run segment, and S2 has `ehz1_fullrun` plus per-zone segments
including `cpz2`, so the CPZ2 pilot is covered); and the act has no residual
scripted segments to convert. FBZ2's own conversion is not finished on that
last point (section 2.3). Without the fixture the number is unknown. A
controller answers ROUTE and contributes to OBJECT, CAMERA, BOSS and LOAD,
about four of the eleven obligations; it answers none of the seven REWIND
spots (the largest missing block) and none of PRESENT or ORACLE. No per-act
matrix closes on a controller alone.

**Risks.** Every adaptive gate is a place a genuine engine defect could be
recovered from instead of reported, so gates need cited bounds and the
short-lane checks stay the primary detectors. A failure deep in a long
traversal voids every assertion after it, which is why the standard keeps
local checks in the short lane. Act timers are a budget: FBZ2 allows 36,000
frames, controllers that wait on polarity cycles spend it, and the branch only
fits because it dropped 12,121 scripted frames. Gate constants are tied to
object cadences and can break on unrelated physics work.

**Verdict.** The technique is the right shape for the ROUTE obligation and the
matrix axes, and the primitives in section 3 are the phase-3 shared helper
the plan expects. Pilot controllers on the plan's AIZ and HCZ acts only, and
spend the rest of the backlog effort on rewind spots and short
width-by-donor lifecycle checks, which the technique does not supply.

## 5. Rewind determinism along routes

**Current state.** The machinery exists. `RewindSnapshottable<S>`
(`src/main/java/com/openggf/game/rewind/`) is implemented by about 80
production classes; `RewindRegistry` produces and applies `CompositeSnapshot`;
`RewindController` offers `step`, `seekTo`, `stepBackward`, keyframe restore
plus forward re-simulation. `TestRewindTorture.java` is already
snapshot-play-restore-replay-compare over the S2 EHZ1 BK2, with `FixtureStepper`
(line 367) and `MovieInputSource` (399) as private adapters.
`RewindSnapshotDiff.diffKey` gives path-based leaf diffs with order-stable
object-manager handling; `RewindDeterminismAuditor.report` is the verdict
layer. No state hash utility exists and none is needed.

**What it takes.** Promote `FixtureStepper` into a shared helper, add an input
source that logs the masks a live controller produced, and call capture, seek
and compare at sampled route milestones. Low complexity.

**The one design constraint.** `InputSource.read(int frame)` is random-access
and pure, but a live controller derives masks from state that a seek has just
rewound. The controller must record its masks and replay from that log, never
re-derive them during the replay leg. That mask log is also the artifact
sections 7 and 8 reuse.

**Where it belongs.** A rewind check at route milestone N depends on the route
reaching N, which is exactly the shape the standard excludes from the spot
obligations ("do not put local assertions after a long traversal"). It is a
stress-lane property beside `TestRewindTorture`: it complements the seven
spot scenarios, which stay independent short checks, and does not satisfy
them.

**Risks.** Capture plus N-frame replay at every milestone multiplies route
runtime, so sample milestones as the torture test samples checkpoints.
Documented transient-child divergences in `docs/S3K_KNOWN_DISCREPANCIES.md`
will fire unless excluded under the standard's explicit-scoping rule. Restore
covers only registered entries, so the harness must disarm after the first
divergence as production does. The hardware-timing ledger's capture cost is
memoised (`HardwareTimingService.java:448-454`); a new checkpoint cadence
changes how often that memo is invalidated, so measure before adopting a
tight interval. Loads that clear history are timeline resets and must be
tested as such.

**Verdict.** Low cost, medium impact: a determinism soak over long play in
every matrix configuration, in the stress lane, not a substitute for the
standard's rewind spots.

## 6. Regression smoke and CI

**Current state.** `.github/workflows/ci.yml` runs only `-Psmoke` on push; the
profile (`pom.xml:176-182`) excludes `slow-suite`, `fbz-route` and other slow
groups, and a floor of 10,000 executed tests (`ci.yml:95`) guards against
silent shrinkage. `guards` and the full suite run on pull requests and
dispatch; trace replay is dispatch-only on the self-hosted `release-fixtures`
runner that holds the ROM paths. 220 `*TraceReplay` classes exist; the
frontier log records individual runs of minutes and one gate of 88 minutes
(not re-measured here), all native-mode only.
`TestFbzAct2RouteHeadless` is an ordinary untagged test. Surefire run order
is alphabetical (`pom.xml:39`), so the filesystem-order hazard does not
apply. Neither FBZ class uses `@FullReset`.

**The blocker.** ROM availability, not runtime. Hosted runners carry no ROMs,
`RequiresRomCondition` returns disabled (line 42), and the aggregate floor
never notices a handful of skipped route cases. Local category runs do get
ROMs by SHA-1 discovery.

**What it takes.** Nothing new for local use: the matrix's short checks
already sit in the ordinary suite and a full route is seconds. For CI, either
run route checks on the self-hosted runner or add a per-class skip assertion
so an absent ROM fails loudly. Verify ambient-state exposure before gating,
since the FBZ classes do not use `@FullReset`.

**Benefit.** Two things a replay cannot give: coverage of teams, widths and
donors no BK2 records, and a failure that names a stage owner and the live
objects around it. Not "better diagnostics" in general: a replay reports the
earliest observable divergence, while a route reports the first gate it
could not absorb, which may be later than the defect (section 2.4).

**Verdict.** Adopt as local category-run smoke first; promote to CI only on
the self-hosted runner, with the skip assertion in the same change.

## 7. Benchmark and audio soak input

**Current state.** `TraceBenchmarkTool` (`src/main/java/com/openggf/tools/`)
takes input only from a trace catalogue entry's BK2 via `Bk2MovieLoader`
(183) and `RecordingFrameDriver.setBk2Movie` (355); no BK2, no benchmark. It
has `--fm-core`, `--no-audio` and `--track-allocations`, but no width, team or
donor flag. `CompleteRunAudioTool` and the per-game parity tools likewise take
a BK2 and publish rows into a real `InputHandler` through `Bk2InputCursor`.
Unit-level audio tests bypass gameplay. The route runner and
`HeadlessTestFixture` are test scope and the POM has no test-jar, so tools
cannot depend on them.

**The cheap shape.** Do not promote the controller to main scope. Commit the
per-configuration mask log from section 5 as a route input file and let the
benchmark replay it exactly as it replays a BK2, behind a `--route` flag,
gated on `TrajectoryDigest` (`src/main/java/com/openggf/bench/`) so a
desynced log refuses to report timings. A live controller in the benchmark
would change trajectory on every engine change by design and the digest gate
would refuse nearly every cross-commit comparison; a committed log desyncs
only when the engine changes, exactly as a BK2 does today, and the controller
is the tool that regenerates it. No shipped surface grows and no
architectural guard is touched.

**Risks.** A regenerated log is a new trajectory, so benchmark history is
per-log. `loadTimeSimulation` FAST and NONE give different load-span frame
counts, so stamp the mode into the log and reject a mismatch. `--fm-core`
changes audio cost but not gameplay frames.

**Non-goals.** Route logs carry no ROM reference. They are a deterministic
play source, not an oracle, must not be presented as trace coverage, and
should not gate releases.

**Benefit.** A native BK2 replayed at 640px still exercises load spans and
rendering until gameplay diverges, so this is not the first widescreen
measurement; it is the first that completes a level under widescreen, a team
or a donor, and the first the fast FM core can be soaked against end to end.

**Verdict.** Worth doing as mask-log replay, benchmarking first, audio soaks
once the adapter exists.

## 8. Creator-content regression routes

**Current state.** A mod's route-relevant reach, per `ModBackedGamePatch.apply`
(`src/main/java/com/openggf/mods/code/ModBackedGamePatch.java:112`): new
levels with their own layout, collision, bounds, start position and
placements; decorated object factories; characters and physics profiles;
gameplay policies and input filters. Mods have no access to
`ZoneLayoutMutationPipeline` or `LevelMutationSurface`, so vanilla tiles
cannot be repainted. `TestSampleFlappyIntegration` already drives a mod-loaded
`LaunchedGameplay` with `stepFrame` and a `LogicalInputSnapshot` override and
asserts death boundaries and rewind capture/restore, so the stepping primitive
exists. `HeadlessTestFixture` has no mod-module entry point, and no
vanilla-versus-modded comparison test exists.

**What "completability" can honestly mean.** Nothing generic. A prover for
arbitrary creator levels is a search problem over Sonic physics, not an
extension of this test, and creators will not author Java stage machines with
cited bounds. The realistic product is record-and-replay: a creator plays
their level once through a recorder that writes the mask log and a few
milestone assertions (reached the exit, no death, rings floor), and the log
replays on every engine update to catch the real pain, an engine change
silently breaking a shipped level.

**Risks.** False red when a creator deliberately changes their level; runtime
cost if used as a per-mod gate; maintenance that scales with content the
project does not own.

**Verdict.** Moderate complexity (a mod-aware fixture builder plus the mask
recorder and replayer from sections 5 and 7), narrow value for 0.7 whose
gates are signature pins and sample integrations, a better fit for the 0.8
graduation theme. Never a prover, never a substitute for the pins.

## 9. BizHawk Lua port to author BK2s

**Current state.** Canonical capture is native, not Lua: TraceChaser's GPGX
harness replays an existing BK2 and records a trace; it does not author input.
The documented Lua contract uses `joypad.get(1)` only; `joypad.set` is never
used in this repo, and its one mention forbids it in the audio observer
(`docs/architecture/designs/2026-08-09-s1-audio-driver-parity.md:183`), so
scripted input under the headless Mono harness is unproven here. BizHawk's
own API is not in question. `AnPal_FBZ`'s RAM address, the object-pool base
and slot count, and per-slot subtype/routine/render-flag offsets are not
documented as verified values; the player SST base and a few flags are
(`docs/architecture/research/2026-04-21-s3k-trace-addresses.md`). The
submodule was not initialised in the exploration worktree, so the harness
source itself was not read.

**Why not.** The 6,300-line gate set would have no shared source of truth
with a Lua mirror, and a ROM run proves nothing about the engine: a controller
that plays the ROM successfully says nothing about engine correctness, and one
that fails on the ROM is ambiguous between a bad gate and a real divergence.

**Prerequisites if revisited, in order.** (1) Show `joypad.set` works under
the headless Mono harness and that a BK2 can be written from a scripted run;
if this fails the idea is dead. (2) Resolve and verify the missing RAM
addresses, appending to the S3K address research. (3) Prototype one segment
only, native configuration, and compare its BK2 against a hand-played one.

**Verdict.** Do not port. The recorder-migration backlog is better served by
hand-played segments through the existing harness.

## 10. Sequencing

1. Land the FBZ branch cleanly first. `6db3634b2` is clean, but the live
   worktree carries an uncommitted scratch probe with a hard-coded home path
   that must never be committed, and the develop-side `fbz-route` tags need
   re-applying on merge.
2. Extract the generic primitives (section 3, step 1) as LTS-04 and migrate
   the FBZ2 class onto them under the acceptance rule in section 3.
3. Pilot a second zone from the plan's phase-2 list (AIZ or HCZ) on those
   primitives, copying rather than abstracting the stage pattern, to price
   the per-act cost with its assumptions stated. Only then extract the stage
   interface (section 3, step 2) from two instances.
4. In parallel on the primitives: the mask-logging input source, milestone
   rewind checks in the stress lane (section 5), and local category-run
   smoke registration (section 6).
5. Convert the FBZ2 prefix and residual scripted segments (section 2.3),
   which is the first blocker for the three red routes; report what the
   rows do beyond it once they get there.
6. Mask-log replay behind `--route` on the benchmark tool (section 7), then
   audio soaks; creator record-and-replay (section 8) when the 0.8 line
   opens; BizHawk (section 9) only if prerequisite 1 is shown.

## 11. Non-goals and limits

- Routes are not accuracy evidence. Parity is measured only by trace replay
  in native configuration; a green route in widescreen says the extension did
  not break the level, nothing more.
- Routes are not generic. The per-hazard authoring cost is the price of the
  technique and does not go away; the primitives lower it, they do not remove
  it.
- A route that cannot be finished through ordinary inputs in some
  configuration is a finding about the extension, to be left red and
  reported, not a test to patch. The S1 donation assists in section 2.4 are
  the existing exception to this rule; whether they stand or the rule does is
  a decision this document raises and does not make.

## 12. Cross-review record

Two independent reviewers read the draft: a fact-check against the code at
`6db3634b2` and develop `b1f693fd0`, and an adversarial review of the
judgements. Dispositions:

- Overclaimed branch state (blocking): "21 of 24 green" counted 13 preflights;
  rewritten to 8 of 11 complete routes throughout. "The prefix is the only
  reason the three rows are red" rested on nothing measured past frame 5877;
  rewritten as "first blocker, downstream unmeasured" (sections 2.5, 10).
- The class is a hybrid (blocking): added the citation census, the surviving
  scripted segments and run-index keys, the fixture-fitted shaft window, and
  the tension with the standard's "not fitted to a recording" rule (sections
  2.2, 2.3). The reviewer counted 31 BK2 against 44 disassembly citations; a
  direct grep of the same file gives 37 against 43, and the point stands.
- Production assists omitted (blocking): the three `FbzS1Donation*Assist`
  classes are now named as the standing exception to the "leave it red" rule
  (sections 2.4, 11).
- Benchmark shape (should-fix): replaced main-scope promotion with committed
  mask-log replay and softened "first way to measure widescreen" (section 7).
- Rewind lane (should-fix): moved to the stress lane, corrected "no rewind
  anywhere in FBZ", downgraded to medium impact (sections 4, 5).
- Diagnostics overclaim (should-fix): section 6 now states the absorbed-gate
  caveat instead of "better than a frame index".
- Premature abstraction (should-fix): the library is now two steps, with the
  stage interface deferred until a second zone exists, and "byte-identical"
  is defined (sections 3, 10).
- Cost assumptions (should-fix): stated in section 4. The reviewer's claim
  that S2 has only `ehz1_fullrun` was wrong; `src/test/resources/traces/s2/`
  holds per-zone segments including `cpz2`, so the CPZ2 pilot has a fixture.
- Re-verdict residues: the "never branches on a frame number" bullet now
  excepts the run-index keys, and the standard's fitted-recording rule is
  quoted exactly with its narrower scope stated (sections 2.3, 2.4).
- Minor: LTS-04 framing, record-and-replay creator toolkit, `joypad.set`
  wording, `FbzZoneRuntimeState` 27 sites (not 71), about 80 rewind classes
  (not 90), `MovieInputSource` at 399, develop-only `fbz-route` tags, the
  CNZ teleporter test's input style, and the replacement of unmeasurable
  counts (latches, milestone fields, "85 percent", stage count) with
  measured field counts or an explicit "by inspection", all applied.

Fact-check items confirmed without change: line numbers for the runner,
adapter, milestones and evidence record; 168 arms; 50 `steerMask` uses;
commit hashes and dates; fixture rows 24792/24882/27352/23622/23796 and the
three sidekick deaths; the standard's counts; the backlog and plan state;
the CI profile, floor and runner facts; `RequiresRomCondition`;
`HeadlessGameBoot:360`; the benchmark tool's lines and flags; the mod
mutation-surface claim; line counts of the existing route tests.

## 13. Sources

- `bugfix/ai-fbz-route-tests` at `6db3634b2`: `TestFbzAct2TraversalPreboss.java`,
  `TestFbzCompatibilityMatrix.java`,
  `docs/architecture/research/s3k-zones/fbz-outstanding-actions.md`,
  `docs/architecture/research/s3k-zones/fbz-compatibility.md`.
- `src/main/java/com/openggf/game/sonic3k/events/FbzS1Donation*Assist.java`.
- `docs/guide/contributing/level-test-standard.md`,
  `docs/status/level-test-coverage.md`,
  `docs/architecture/plans/2026-09-13-level-test-standardisation.md`,
  `docs/architecture/validation/2026-09-13-fbz-test-lanes.md`.
- `src/main/java/com/openggf/game/rewind/`, `TestRewindTorture.java`,
  `TestRewindTraceSeekDeterminism.java`, `HardwareTimingService.java`.
- `.github/workflows/ci.yml`, `pom.xml`, `tools/testing/test-categories.json`,
  `src/test/java/com/openggf/tests/rules/RequiresRomCondition.java`.
- `src/main/java/com/openggf/tools/TraceBenchmarkTool.java`,
  `src/main/java/com/openggf/tools/audio/completerun/`, `HeadlessGameBoot`,
  `Bk2InputCursor`, `src/main/java/com/openggf/bench/TrajectoryDigest.java`.
- `src/main/java/com/openggf/mods/code/`, `TestSampleFlappyIntegration.java`,
  `docs/modding/`.
- `.claude/skills/bizhawk-headless-trace/SKILL.md`,
  `docs/architecture/designs/2026-08-29-tracechaser-extraction.md`,
  `docs/architecture/designs/2026-03-26-bizhawk-trace-replay-testing-design.md`,
  `docs/architecture/designs/2026-08-09-s1-audio-driver-parity.md`,
  `docs/architecture/research/2026-04-21-s3k-trace-addresses.md`.
