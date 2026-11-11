# Hosted arena lane B implementation checkpoint

Purpose: preserve implementation evidence, rejected probes and the remaining boundary
for the Competition Time Attack lane. Inputs: the verified locked-on S3&K ROM,
S2 World REV01, native Competition owners and the
[approved design](../designs/2026-11-07-s3k-competition-time-attack-tracks-design.md).
Origin: lane B resumed at `407711a53` on branch `s3k-competition-mode`, 2026-11-07.
This records the engine lane; end-to-end network and verifier integration belong to the lead and other lanes.

## Final engine-lane qualification

The latest combined report set contains **625 passing cases**, zero failures,
errors or skips, from 68 Surefire reports (63 top-level classes).
This is a focused selection, not a full-suite or category-run pass. Production
sources last changed at `53ffeed7b`; later changes are tests and evidence only.
The final checkpoint fixture was tested at `c22d3b843`.

The unchanged 33-class S3K Competition guard passed all 363 cases, including the
previous lane's TestS3kSoloSwapItem. Native-menu/hosted equivalence and all four lap
capture cases pass on the verified locked-on ROM. Checkpoint reload now compares
every registered rewind owner's captured values through GameLoop and the direct
LevelManager owner: no owner key or gameplay field is excluded. Animation resources,
terrain descriptors, palettes and patterns compare by complete values across fresh
boots. Destructive controls prove resource frames, compact bytes, block descriptors,
collision indices and the original one-pixel pose difference cannot pass equality.

S2 monitor draws, event admission, both native fallbacks, exact streamed fields,
64 inhibited object passes/48 white passes, and the 80-step live/RecordingFrameDriver
comparison pass. The replay independently reproduces a positive native swap request.
All required S3K loading guards, hosted regressions, API checks, monitor regressions
and surfaceless EGL probes pass. Every wrapper reported missing=none for ROM inputs.
No lean test OOM occurred. The final skip classification reports zero skipped,
unclassified, required-skipped or stale-policy cases. A category-run VERDICT is not
applicable because these are direct focused wrapper runs.

| Source / selection | Lane | Result | Normal-memory compile | Shared admission wait | Test Maven execution |
| --- | --- | --- | --- | --- | --- |
| ed28ff14a / earlier combined 50 selectors | normal | 518 passed, 0 skipped | Included in Maven time | 1,404s | 120s including compile |
| 53ffeed7b / 47-class batch | lean | 454 passed, 1 oracle failure, 0 skipped | 37.838s | 589s | 16.538s |
| 53ffeed7b / 15 selectors, 16 classes | lean | 166 passed, 2 registry-contract failures, 0 skipped | 37.422s | 500s | 13.644s |
| 8ac40af4f / four affected classes | lean | 40 passed, 1 expanded-oracle failure, 0 skipped | 37.916s | 409s | 4.320s |
| 50fd9a049 / two affected classes | lean | 6 passed, 1 fixture-isolation failure, 0 skipped | 36.471s | 388s | 3.078s |
| c22d3b843 / checkpoint class | lean | 1 passed, 0 skipped | 46.511s | 490s | 2.427s |

Batch 2 additionally waited 330–360s on this worktree's build lock before compilation;
that is separate from its shared admission wait. Lean runs use normal compile memory
and 1 GiB test heaps. Historical failures and their exact corrections are retained
below. No unrelated processes were stopped and no queue locks were deleted. User
approval wait was zero; implementation/debug/orchestration time was not instrumented.

Earlier resource-constrained requests are also distinct from test execution:
the own normal post-results request was retired after approximately 3,254s queued,
with no tests executed, when the user authorized lean execution. Its lean replacement
at efa2f02a3 compiled for 94s, waited 2,741s and executed for 4.445s: 39 passed,
one checkpoint-boundary assertion failed, zero skips/OOMs. The corrected checkpoint
retry at 479ff91da compiled for 38.340s, waited 1,072s and executed for 2.196s:
one passed, zero skips/OOMs. A later lean request compiled for 95s and waited 409s
but launched no tests because committing changed its frozen tree fingerprint.
The fixed-source requests in the final table supersede that request.

The following sections retain the chronological implementation/evidence checkpoints;
old pending statements describe those particular attempts, not outstanding checks.

## Implemented behavior

- `6c24ce0f1`: S2/S3K monitor OFF deletes on initialization without recording a break.
  `ed28ff14a` exercises real decoded placements and their live persistence owners.
- `fde7058e4`: semantic arena provider/session contracts, solo native Match Race launch,
  scratch ROM-default records with no save authority, highest-lap race observations,
  Start masking, native geometry, read-only full-height projection and miniature ghosts.
  Both native sprite pages, cameras, culling and visibility writes keep their owners.
- The checkpoint reload goes through one public LevelManager owner, preserves native
  life loss, GAME OVER/TIME OVER decisions and banked star-post state, omits the initial
  title card, and continues the attempt ordinal with CHECKPOINT_RESPAWN. The live
  coordinator now omits both fade directions (`479ff91da`), as design section 6 requires.
- `a603beed6`: race.running reads the native timer flag and track completion is the
  fifth-lap crossing, before the native results wait. `2f1c727fc`: Competition acts
  retain Start/pause ownership even without a covering scene.
- `daeed4db8`: initialize the private miniature art bank from the already flattened
  ROM DPLC sheets. `38d221894`: project ghosts into P1's native wrapped camera image.
- `ba1cd5a45`: after native results, hold the last simulation frame while admitting
  host menu input; refuse arena retry and continue to accept leave.

`PlayableSpriteMovement` is unedited and retains CRLF. Checkpoint deaths use its
existing native life-loss branch. Existing Competition guard test sources are unedited.

## Validation and limits

The final combined selection at **`ed28ff14a`** passed **518 tests**, zero failures,
errors or skips, on Java 25. Queue wait was **1,404 seconds (23m24s)**; Maven execution,
including compilation, was **120 seconds (2m00s)**. It ended at 15:57:34 UTC.
All ROM properties were found; the wrapper reported `missing=none`.

The unchanged baseline Competition guard comprises 33 source classes and **363
passing cases**, including `TestS3kSoloSwapItem`. The combined run also includes:

- Hosted controller/launcher, fast-death and policy regressions, plus all four required
  S3K bootstrap/loading guards.
- Native menu versus HostedArenaLaunch equivalence: items on/off, 700 steps each,
  full session capture on every step, a false start, positioned death and respawn,
  and active right/jump input after respawn. A permanent negative control changes
  P1's centre by one pixel and asserts that the same session comparison fails.
- Three lap tests: native per-lap frame truncation, five boundaries, backwards crossing,
  death rollback, Start masking, CGZ vertical wrapping and cap-to-TIME_OVER.
- Checkpoint reload through GameLoop versus direct LevelManager invocation: equal
  full player, camera, game-state and checkpoint-bank captures plus act timer.
  Native life loss and CHECKPOINT_RESPAWN ordinal continuation are asserted.
  This still does not compare the entire restored world registry.
- S2/S3K native monitor OFF presence/persistence tests and a ROM-free policy test.
- Surfaceless EGL: all five arenas, row 224 drawn, a P1-relative ghost aligned with
  the projected native miniature piece, populated ROM art, full versus half-opacity
  RGB output, and no draw-time changes to the complete captured rewind registry.
- Candidate Mod API signature and pin-policy checks.

The post-results/full-pose follow-up ran in the lean lane at `efa2f02a3`: 40 tests,
39 passed and one checkpoint assertion failed, with zero skips or OOMs. The
strict snapshot exposed a wrong test invocation boundary: direct reload was
called at life loss rather than at the native respawn request. The correction
keeps every compared field. At `479ff91da`, its lean retry passed 1/1 with zero
skips or OOMs. Normal compile took 38.340s, queue wait 1,072s (17m52s), and test
Maven execution 2.196s. The other 39 follow-up tests already passed: full
PlayerPose arena equivalence, result hold/retry rejection, lap capture and
hosted controller behavior.

The remaining monitor/swap engine sources compile with Java 25, including all
test sources (95s normal-memory compilation, 18:32:03 UTC). Their first lean
selection (`target/lane-b/swaps-focused-lean.log`) was admitted after 409s but
rejected before test execution because committing changed the frozen source
fingerprint. It selected
TestS2RandomisedMonitorContents, TestS3kArenaSwapApplier, TestTeleportSwapApply,
TestModApiSignatureSurface and TestModApiPinPolicy. No test pass is claimed for that request.

These are focused, positioned tests. They do not certify a full player-input route,
a network race. The later full checkpoint-owner comparison is qualified above. No full suite
or category run was claimed. The final selected Surefire reports were classified:
`skipped=0 allowed=0 unclassified=0 required_skipped=0 undeclared_input=0 stale_rules=0`.
A category-run VERDICT is not applicable to this direct focused Maven selection.

At `53ffeed7b`, the two lean batches ran with 1 GiB test heaps and normal-memory
compilation. All ROMs were found. Batch 1: 455 tests, 454 passed, one failed,
zero errors/skips/OOMs; compile 37.838s, admission wait 589s, test Maven 16.538s.
Its unchanged baseline guard passed all 363 cases. Arena equivalence, lap capture,
checkpoint owners, monitor draws, native arena shoe fields, 64-pass S2 freeze,
request/fallback behavior and both EGL pixel tests passed. The replay comparison
failed only on fresh-boot SpriteAnimationSet identities at resumed step 64.
The correction compares every script field within compact opaque values, retains
all scalar bytes, and adds negative controls for both resource and scalar changes.

Batch 2: 168 tests, 166 passed, two failed, zero errors/skips/OOMs; compile
37.422s, admission wait 500s, test Maven 13.644s. It also waited 330–360s for this
worktree's build lock before compilation. Both failures were fixed-count registry
assertions: the registered sonic2.race-swap adapter changes the explicit total
from 17 to 18. The updated contract names that key and asserts its survival on
reattachment. All loading, API, frame-owner, monitor and underwater tests passed.
Both selected report sets classified with zero skips and no stale rules.

The bounded follow-up compiles and selects TestTeleportSwapApply,
TestHostedArenaLapCapture, TestCheckpointRespawnPolicy and
TestGameplayModeContextRewindRegistry. It adds a direct false-start clock assertion
through the native 120-tick penalty/reset and strengthens checkpoint comparison to
all registered rewind owners, canonicalized by value. Its results are pending.

The four-class lean follow-up at `8ac40af4f` ran 41 tests: 40 passed, one failed,
zero errors/skips/OOMs. Normal compilation took 37.916s, admission wait 409s, and
test Maven execution 4.320s. The 80-step live/replay comparison, its destructive
resource/scalar controls, the explicit 18-key registry and all four lap cases
passed. The expanded checkpoint comparison reached decoded Block identities.
Block.saveState and Chunk.saveState retain every descriptor and collision index;
the value oracle now uses those arrays, with destructive controls for a changed
block descriptor and alternate collision index. Checkpoint diagnostics collect
all differing owner keys. The affected TestTeleportSwapApply and
TestCheckpointRespawnPolicy follow-up is pending; production behavior is unchanged.

The terrain-value follow-up at `50fd9a049` ran 7 tests: 6 passed, one checkpoint
comparison failed, zero errors/skips/OOMs. Compile 36.471s, admission wait 388s,
test Maven 3.078s. All decoded terrain and gameplay owners matched. Two module
lifetime fields remained: s2-plc-art.loadEpoch 3 versus 5 and s2-title-card.frameCounter
53 versus 106. The checkpoint fixture reused its cached module across the two
boots. It now clears SessionManager and GameModuleRegistry before each boot,
matching the independent-boot setup already used by the replay test. Neither
field is omitted or rewritten. The single affected checkpoint class is queued
for a lean retry; no production change was required.

## Rejected probes and queue history

All rows in this early table used the normal lane.

| Job | Queue wait | Maven execution | Result |
| --- | --- | --- | --- |
| First guard | 1,082s | Not started | ENOSPC during queue admission |
| First hosted selection | 752s | Not started | ENOSPC during queue admission |
| Combined, earlier source | 1,647s | 182s | 495 passed; 4 explicit EGL opt-in skips |
| API checks | 1,856s | 108s | 16 passed; 0 skipped |
| Clock/pause boundary follow-up | 1,885s | 100s | 39 passed; 0 skipped |
| First ghost EGL probe | 1,393s | 99s | 1 failure; empty art bank/offscreen ghost |
| Second ghost EGL probe | 1,914s | 36.596s | 1 failure; offscreen ghost after bank fix |
| Camera-relative sky probe | 2,281s | 99s | 1 passed across all 5 arenas; 0 skipped |
| Final combined, P1-relative probe | 1,404s | 120s | 518 passed; 0 skipped |

The camera-relative sky fixture demonstrated RGB alpha support, but could hide an
image-selection defect: in ALZ, actor X1072 and native camera X1936 still place P1
at screen X160 through a 1024px loop. Raw subtraction hid a nearby ghost. The final
probe places the ghost relative to P1 and compares the prepared native SAT anchor.
`38d221894` derives a display origin from the native wrapped player anchor, including
its 12px range half extent, without changing the native camera.

Initial test compilation used BufferedImage accessors on RgbaImage; those were
corrected to the native API. Compilation then passed. Shared disk pressure cleared
externally; no queue locks, foreign files or ROMs were removed. Queue waits were
kept alive and are separate from execution above. There was no user-approval wait;
other wall time includes implementation, diagnosis and orchestration and is not
instrumented. Disposable logs and diagnostic images stay under `target/lane-b/`.

## Monitor and event engine

The frozen GameplayRunPolicy.swapEnabled component retains both older constructors.
Hosted random contents latch Level_frame_counter at the actual child initialization,
with the D22 bit-0 set and native teleport eligibility guard. Named Tails 1-up never
credits P1. Native breaks raise one consumable SWAP_REQUEST instead of applying a
solo effect. The hosted queue admits at most one external command at a time and
holds it across host-admission pauses. S2 applies after the old VBlank timer service
and before gameplay owners; arenas apply immediately before their native step.
Per-game semantic services perform deterministic admission, reconstruction and
solo fallbacks. S2's timer and native request state, and the arena request bit,
are registered rewind state. Input-transfer queues and presentation bindings are
explicitly transient.

The new focused tests check field preservation, native clocks during 64 inhibited
object passes, the white-flash phase, unsafe/dead/finished fallbacks, delayed
requests, native empty-slot arena parity and live versus RecordingFrameDriver
canonical snapshots over 80 steps. Their first-run results and subsequent oracle corrections are recorded below. The replay comparison also independently
raises the native monitor request before injecting the logged application.
The latter uses the canonical replay frame owner; it does not wire the separate
AttemptReplayHarness or validate server authorization of the event log.

## Ownership and remaining integration

Additional source integration: provider registration in Sonic3kGameModule;
semantic service/rewind registration in Sonic2GameModule; canonical LevelFrameStep
VBlank/event/freeze delegates; a compatible frozen flag in GameplayRunPolicy;
and the design-assigned NativePlayerPose capture-body update for secondary path.
The candidate 0.7 API pin adds the generated new policy constructor/accessor/
component and LevelManager placement/restart methods without changing the candidate
version. Five verified explicit EGL opt-in skip rules were added earlier. The draw-only
palette projection follow-up adds an internal PaletteDrawPresentation bridge and
a 58-line PaletteTextureOwner scope, so underwater/appearance consumers receive
the flash without changing native palette bytes or VBlank upload state. It includes
an EGL pixel test of ordinary/water/private rows and cleanup after a draw failure;
its explicit EGL opt-in has its own skip classification. LevelRenderer is untouched.

The existing TestGameplayModeContextRewindRegistry contract was updated to name
the additional adapter and expect 18 keys; no baseline Competition guard source
changed. No source under net/** or racing/**, S2 competition, shared competition,
NativeCompetitionRenderer or LevelRenderer was edited. PlayableSpriteMovement
remains unedited with CRLF intact.

Other owners must carry the round's frozen swapEnabled into both live and replay
policy construction; the engine never infers it from current peers. The
AttemptReplayHarness must reset its applier once per attempt and inject recorded
events on the engine thread through RaceSwapApplier.queueForStep, consume
request/outcome observations, and call the single checkpoint reload owner.
S2 resolves the service through GameModule.getGameService(RaceSwapApplier.class);
arenas expose their session-scoped instance through HostedArenaSession.raceSwaps().
A rejected recorded fallback must be rechecked using the logged original target.
Network logging, hash binding, outcome authorization and complete route verification
remain outside lane B.

Intentional engine deviations are recorded in the existing discrepancy catalogues.
The lead owns the combined feature changelog, Time Attack/racing guides, security
amendment, coverage obligations and protocol/lifecycle research refresh. No changelog
or release-index change was made in this lane, as requested.

## Reproduction commands

The final combined command (50 top-level classes, 55 Surefire reports including
nested classes) was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com/openggf/game/sonic3k/competition/**/Test*,TestS3kSoloArenaViewPixels,TestHostedArenaEquivalence,TestCheckpointRespawnPolicy,TestMonitorOffPolicy,TestHostedRunController,TestSceneRunLauncher,TestFastDeathRestart,TestGameplayRunPolicy,TestHostedRunActCompletionWiring,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface,TestModApiPinPolicy' test
```

The post-results follow-up selected the four affected classes (subsequently rerun with --lean):

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestHostedArenaEquivalence,TestHostedRunController,TestHostedArenaLapCapture,TestCheckpointRespawnPolicy' test
```

Both were launched in the background with `timeout 7200`, logs under this worktree's
`target/`, and no DISPLAY for EGL. Earlier commands use the same wrapper and selectors
shown in the table's corresponding final subsets. The API-only command selects
`TestModApiSignatureSurface,TestModApiPinPolicy`; the first three EGL probes select
`TestS3kSoloArenaViewPixels` with the surfaceless property.


## Final lean reproduction

The exact 47-class first selection is reproduced by expanding the package before
calling --lean (the lean wrapper rejects wildcard selectors):

```bash
guard=$(rg --files src/test/java/com/openggf/game/sonic3k/competition -g 'Test*.java' | sed -e 's#.*/##' -e 's/\.java$//' | sort | paste -sd, -)
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dtest=$guard,TestHostedRunController,TestSceneRunLauncher,TestFastDeathRestart,TestGameplayRunPolicy,TestHostedRunActCompletionWiring,TestHostedArenaEquivalence,TestCheckpointRespawnPolicy,TestMonitorOffPolicy,TestS3kSoloArenaViewPixels,TestS2RandomisedMonitorContents,TestTeleportSwapApply,TestPaletteDrawProjectionPixels" test

env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface,TestModApiPinPolicy,TestLevelFrameHardwareTimingBoundaries,TestRecordingFrameDriverInputOnly,com.openggf.game.sonic2.objects.TestMonitorObjectInstance,TestSonic2MonitorMusicAdmission,TestSonic2CompetitionMonitorConsumer,TestMonitorIconTiming,TestLiveRewindMonitorState,TestGameplayModeContextRewindRegistry,TestUnderwaterPaletteUpload' test
```

The bounded follow-ups use the same flags and these exact selectors, respectively:

```text
-Dtest=TestTeleportSwapApply,TestHostedArenaLapCapture,TestCheckpointRespawnPolicy,TestGameplayModeContextRewindRegistry
-Dtest=TestTeleportSwapApply,TestCheckpointRespawnPolicy
-Dtest=TestCheckpointRespawnPolicy
```

Each request used timeout 7200 in the background, with logs under target/lane-b.
The merged report set and its skip classification are disposable target outputs.
No branch was pushed or merged.


## Fix lane G — 2026-11-08

The local `competition-smoke-regressions` worktree began at develop `33c3b8e56`,
then merged feature `ef7d28cd1` as `8f0f46005`. This base already contains the
TraceCapturePresentationSetup catch-signature repair; do not re-add the older
baseline's four catch-inventory differences. No excluded native competition
renderer/gameplay files were edited.

`2506f005f` moves race policy/event/value contracts to `game.arena`, translates
arena poses through the mod host's `NativePlayerPose`, and replaces the net
stack's game calls with `TrackRulesProvider` and exported catalogue data. Its
candidate API signatures move in place; the descriptor remains candidate 0.7.
Monitor OFF uses `ObjectLifetimeOps.deleteNoRespawn`, preserving deletion without
respawn marking. `fb1b8b354` extracts camera-relative race placement, uses the
hosted focus-input collaborator, and lets ghost-slot runtime errors propagate;
the checked IO boundary remains and its retired broad-catch row is pruned.

The 37 original smoke failure identities were compared with the supplied
baseline log: all 30 out-of-scope failures match baseline identities (29 assertions also match
after normalizing JVM object IDs and unordered-set rendering), and the
seven remaining identities are the assigned regressions (the broad-catch case
also contains the older baseline's TraceCapturePresentationSetup debt).

The 250-class feature/regression selection ran 2,259 tests with zero skips.
Four migration mistakes were corrected (one expected pose type and the three
policy-parser cases caused by an invalid descriptor comment); their four-class,
22-test rerun passed. All original S3K competition guards and new hosted tests
were selected. A deliberately wrong catalogue countdown comparison failed at
241 versus 240, then was restored and passed.

Golf passed in isolation and in that selection. The exact original 50-candidate
batch, using normal heap, `-Psmoke -Ptest-concurrent`, alphabetical order and the
same verified ROM/EGL environment, reproduced 24,783 mismatches twice (431 tests,
one failure each). Failure PNG analysis showed only four waterfall colors in a
cycle rotation. Temporary texture readback measured captured/uploaded ARGB:

| Palette index | Captured CPU color | Uploaded color |
| --- | --- | --- |
| 19 | ffb6dbff | ff6d92b6 |
| 20 | ff6d92b6 | ff6d92ff |
| 30 | ff6d92ff | ff92b6ff |
| 31 | ff92b6ff | ffb6dbff |

The proposed flash/solo-view change was not used as a speculative fix. The
measured cause was `LoadedLevelScene` copying the newer mutable palette while
production retained its previous upload. The repair observes a defensive color
copy at the existing palette upload owner, with live fallback only before any
upload. It never advances a publication or changes native drawing. Temporary raw
texture instrumentation was removed. The exact failing batch then passed all
431 tests with zero skips. The new focused palette control holds old colors
across a pending CPU write, rejects aliasing and observes new colors only after
publication. Final feature and guards qualification follows below.

Final feature qualification: the same selection after the palette repair passed
250 executed classes / 2,260 tests, zero failures/errors/skips, using normal heap,
verified ROM discovery and surfaceless EGL. Command:
`python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=<feature selection> test -B`.
The selection includes every net, racing/server, racing/time-attack and S3K
competition test, the named hosted/replay/swap/iteration/PLC classes, Mod API
policy/pin tests and the seven requested regression/presentation classes.
Queue wait was 348 seconds; Maven execution including compilation was 4m13s.
The exact repaired batch waited 293 seconds and executed in 1m38s. The pre-fix
reproductions waited 389/8 seconds and executed in 8.472/8.692 seconds; the
instrumented reproduction waited 29 seconds and executed in 41.157 seconds.
These timings separate queue wait from execution; they are not whole-task wall
time or a full-suite certification. Final guards are reported at handoff.


The requested final `python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B`
ran once against `656dfa996`: 103 classes,836 tests,835 passed,one failure,zero
errors/skips; queue wait301s,execution2m58s. Its sole failure was
`TestNativeImageResourceGuard.everyRuntimeResourceIsReachableFromTheNativeImageConfig`:
the new `net/track-rules.properties` lacked a native-image include. Added the
exact resource include as the guard directs; no allowance or guard changed.
The affected guard plus `TestCatalogTrackRules,TestNetIsolationRules` then
passed all five tests,zero skips (wait37s,execution32.554s). The full guards lane
was not repeated; its other835 checks passed on unchanged source. No remaining
guard failures are known. In particular,the old baseline's
TraceCapturePresentationSetup rows are absent because develop `33c3b8e56`
already repaired those owning methods before this task's setup merge.

Final source qualification is bounded feature/guard coverage,not a full smoke
or public-release full-suite claim. Task commits remain local; no delivery
merge or push was performed. The task worktree is retained for the lead.
