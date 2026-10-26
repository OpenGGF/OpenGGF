# TAS test reel developer tool — 2026-10-26

Implementation source base: `1b93271b8fe9bf92311eaf3ee003fe69834b5284`.
Owned branch: `feature/ai-tas-test-reel-20261026`. This bounded slice owns the
recorder, census/stitch driver, three opt-in test adapters and focused safety
checks. TAS classification, physics investigation and the parent's shared prose
remain outside its scope. No runtime gameplay code or assertions change.

## Delivered architecture and case census

[Usage and adapter contract](../../../tools/test_reel/README.md) describes the
commands. The driver inventories methods changed by the explicit original TAS,
scoring/popup and verdict commits, including S2's `391b671e3` from the dated
matrix. It expands every CSV/value invocation, retains written parameter literals
and normalizes converted arguments. Reused existing tests are excluded from the
new/extended census. Multiple subscenarios inside one ordinary test remain one
JUnit identity, not invented independent invocations.

The settled source has **235 static invocations in 60 classes**. Including the
committed, unmerged S3K follow-up
`57e89fb6c5e2a0a68691892b641f9c27033b37b0` as an explicit inventory-only source
overlay gives **247 invocations in 62 classes**. This adds 12 cases and two
classes, including additions in the existing terrain class. No executable files
were copied from the active S3K worktree and its branch was not merged here.
Per-file source refs mark this union as planning evidence rather than a tested
checkout. Capture rejects overlay files absent from the selected checkout.

There are **24 adapter-covered invocations**: 21 S1 movement controls, two S2
manual-P2 controls and one S3K pause limitation. The expanded inventory has
**223 invocations without adapters**: **44 reviewed non-drawable cases**, plus
**179 awaiting drawable-state/adapter review**. Reviewed cases include pure
scoring ledgers, mocked popup/draw consumers, synthetic object contacts, an open
null-terrain oil column and parser capability assertions. A missing adapter is
not proof that a case is inherently non-drawable. Each remains in the manifest;
no placeholder footage is generated. Native-parity limitations keep their real
current-outcome assertions and are not reclassified by recording.

`TestReelExtension` records actual Jupiter unique IDs, converted arguments and
outcomes when the test body executes. Static ordinal estimates remain separate
from those runtime IDs. Test setup/disabled cases without a body record retain
an explicit unknown execution outcome. The recorder writes frames only at opted-in
existing step boundaries and never runs the test twice. `GameplayCaptureSession`
adapters delegate once to the actual input/step and production renderer. The S1
headless adapter opens EGL over its existing loaded world and uses native sprite
priority; it neither reloads a related level nor advances extra ticks. Its guard
rejects changed player position, velocity, air/roll state or animation during
presentation. PNG writing uses `ScreenshotCapture`; structured FFmpeg commands
normalize lossless segments and encode the final silent MP4 once.

Each retained source run contains the exact queued command, source commit plus
dirty diff, original ROM identity, per-case JUnit manifest, raw input/state JSONL
and PNGs. New runs also retain source file hashes/fingerprint. The combined
manifest and edit map name source/output frame intervals and native 60-Hz timing.
The initial v1 preview predates per-file fingerprints; combining it requires an
explicit legacy option and preserves that provenance limit and its original run.
Later batches can be combined without recording unchanged cases again. Duplicate
revisions require explicit later-preference; changed source-class fingerprints
require an explicit new capture. The all-case path is `capture --select '.'`
after adapter expansion and coordinated resource planning.

## Evidence and verification

The durable media root is the caller's external `${TASK_DIR}`. No ROM, frame,
video, diagnostic log or local-machine path is committed. All footage remains
local; the parent explicitly excluded public Media publication.

- Initial representative command: `test_reel.py capture` with selectors
  `TestTasS1Movement#rollingJumpLandingHasOneFrameElevatedRejumpWindow`,
  `TestTasS2ManualTails#`, and `TestTasS3kPauseClock#`, original absolute S1/S2/S3K
  ROM properties, Java 25, explicit surfaceless EGL, and both display variables
  unset. Its exact expanded queued command is retained in `representative-v1/run.json`.
  **5 actual invocations, 0 failures/errors/skips**, Maven execution 31.879 s.
  Frames are 44/43 (S1), 6/6 (S2), 65 (S3K), **164 total / 2.733 s**.
  `preview.mp4` probes as **960×720, 60/1**, with five chapters and successful
  full error-fatal decode. Inspected state and first/middle/last images show
  the player, actual P2 input/kill versus neutral control, and actual S3K
  loading/fade and pause state. The first S3K frame is a real loading/fade,
  not fabricated blackout filler. This initial S1 excerpt is superseded by the
  corrected headless capture below; its sources remain identifiable.
- Lifecycle safety command:
  `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=<jdk25> LUA_BIN=<lua54> python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=com.openggf.tests.reel.TestTestReelExtension' test`.
  **2 tests, 0 failures/errors/skips**, Maven execution 30.690 s. The inner
  launcher deliberately fails one of two parameterized controls; the outer
  assertions verify the failure and both recorded unique IDs/arguments, and
  verify that ordinary execution creates no recorder output. Queue waiting
  occurred before execution; exact elapsed waiting was not measured here.
- Corrected headless command: `test_reel.py capture` selecting all
  `TestTasS1Movement#` controls and
  `TestBadnikChainScoring#normalAndPoweredKillsContributeToTheSameChainInEitherOrder`,
  original absolute S1 ROM, Java 25 and explicit EGL, output `headless-v2`.
  **22 tests, 0 failures/errors/skips**, Maven execution **32.574 s**, after
  approximately 9.5 minutes of queue waiting. All 21 S1 controls produced
  **122 frames**; the scoring ledger test passed with **zero frames** and its
  non-drawable disposition. The removed art-reset call is absent and the listed
  player-field invariant guard passed every captured frame. This guard does not
  establish complete world-state purity.
- Combined corrected preview: `combine` uses the explicit source overlay census,
  the preserved v1 S2/S3K clips, and the later S1 captures with `--prefer-last
  --allow-legacy-source`; `stitch` creates `combined-v2/preview.mp4`.
  **25 actual recorded invocations: 24 rendered, one non-drawable**. The manifest
  retains all **247 static cases**; **222 were not executed** in these batches.
  The rendered reel is **199 frames / 3.317 s**, **960×720 at 60/1**, 24 chapters,
  with an exact source/output frame map and a successful complete decode. First/
  last images at all 24 cuts and the actual state rows were inspected. The
  explicitly slowed `debug-4x.mp4` export also passed: **796 frames / 13.267 s**,
  generated from the same actual frames without repeating tests.
- Final lifecycle/null-state safety rerun after the bounded parent review:
  same exact focused Java selector as above, **4 tests, 0 failures/errors/skips**,
  Maven execution **30.193 s**. It adds a real passing-body/failing-`@AfterEach`
  child and confirms `body_outcome=passed`, final JUnit `outcome=failed`, plus
  null player/object observations for a menu state. Actual outcomes are finalized
  by `TestWatcher` after per-test teardown. Older v1/v2 recordings predate this
  finalization fix; their pass outcomes are corroborated by their matching
  completed zero-failure/zero-skip Maven commands, not upgraded by inventing a
  new execution. Class-container failures remain visible in the run exit status.
- Python safety: `python3 -m unittest discover -s tools/test_reel -p 'test_test_*.py' -v`.
  Six tests pass, covering provider order/quoted braces, integral hex argument
  conversion, unknown-provider rejection, duplicate parameter identity,
  missing execution accounting and duplicate/changed-fixture combination.
- Documentation checks: new local links resolve and `git diff --check` passes.
  The workflow index has an inherited missing example-generator link; it was
  not changed by this task.

The change-based selection inspection chose all 4,222 smoke candidates because
it classifies the opt-in test utility/service and tool directory as shared or
unknown. This is disproportionate to the bounded test-only implementation and
conflicts with the explicit delegated no-broad-tests scope. No broad suite or
release-only full suite was launched. Focused lifecycle safety, affected fixture
captures and assembly/source-census checks address this slice. They are not a
full-suite or native-parity certification. The measurement-hazard catalogue was
read: command identity, real invocations, skips and source state bound the claims.

## Decisions and rejected approaches

- Rejected substituting `GameplayCaptureTool` routes for component tests. Their
  own executed fixture must supply the drawable state; otherwise retain a reason.
- Rejected silently counting one clip per class or family. Parameters and positive/
  negative controls have distinct census entries and actual JUnit invocation IDs.
- Rejected resetting player art while attaching headless presentation: inspection
  found `refreshPlayableSpriteArt()` also resets animation fields. Removed it;
  upload the already-loaded CPU graphics and retain player state instead. The
  corrected adapter explicitly guards its player invariants.
- Rejected broad all-test capture before proving the small pipeline. Batches,
  source overlays and explicit missing dispositions permit parent-led expansion.
- No padded showcase timings or music. The brief is the rapid native-time sequence;
  slower duplicated-frame output is an explicitly labelled debug option only.
