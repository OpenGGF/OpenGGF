# Hosted arena lane B implementation checkpoint

Purpose: preserve implementation evidence, rejected probes and the remaining boundary
for the Competition Time Attack lane. Inputs: the verified locked-on S3&K ROM,
S2 World REV01, native Competition owners and the
[approved design](../designs/2026-11-07-s3k-competition-time-attack-tracks-design.md).
Origin: lane B resumed at `407711a53` on branch `s3k-competition-mode`, 2026-11-07.
This records the engine lane; end-to-end network and verifier integration belong to the lead and other lanes.

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
a network race or complete checkpoint-world equivalence. No full suite
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

## Rejected probes and queue history

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
canonical snapshots over 80 steps. Their results are pending, not assumed. The replay comparison also independently
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

No source under net/** or racing/**, S2 competition, shared competition,
NativeCompetitionRenderer or LevelRenderer was edited. PlayableSpriteMovement
remains unedited with CRLF intact.

Other owners must carry the round's frozen swapEnabled into both live and replay
policy construction; the engine never infers it from current peers. The
AttemptReplayHarness must inject the recorded event through RaceSwapApplier.queueForStep,
consume request/outcome observations, and call the single checkpoint reload owner.
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
