# JUnit test reel

Record the **test that actually runs**, then hard-cut its native-duration frames.
This developer tool does not replay a related route, drive gameplay from trace
rows, invent a drawable scene for a logic test, change assertions, or classify TAS
support. Video is silent, 60 Hz, with nearest-neighbour 3× scaling and a persistent
game/class/method/parameter label. A one-tick scenario stays one tick.

## Inventory first

Run from the repository root with Python 3.10+. The checked-in scope lists the
explicit original TAS, scoring/popup and verdict commits, including the original
S2 test commit from the dated matrix. It excludes tests merely reused by those
matrices and unrelated work on the same date.

```bash
python3 tools/test_reel/test_reel.py inventory --source HEAD --out /absolute/task/inventory.json
```

Each entry is a **static source expansion**, not an assertion that JUnit ran.
The census includes every new/extended method in the explicit commits and all
its CSV/value controls. Integral hex values are normalized to JUnit's actual
argument strings, while the written literals are retained. Unsupported providers,
overloaded identities, missing methods and custom CSV settings stop inventory
rather than silently omitting cases. New provider types need a reviewed inventory
adapter. Helpers are not standalone test cases; multiple subscenarios within one
ordinary `@Test` remain one JUnit invocation.

Append later committed work with `--add-commit <sha>`. Normally those commits must
be ancestors of `--source`. To inspect committed follow-ups on another branch
without merging them, add `--include-unmerged`: the manifest pins each overlay
file to that SHA and marks its cases `unmerged_source`. Capture refuses an overlay
whose source is absent from this tree. This is a planning inventory, not a tested
source union. The initial S3K follow-up is
`57e89fb6c5e2a0a68691892b641f9c27033b37b0`; add it after parent integration, or
use that explicit overlay option while waiting. No worktree is changed.

## Record a bounded selection

Use Java 25 and the original absolute ROM files. Output roots must be new so stale
PNGs cannot extend a clip. All Maven test execution goes through the repository
queue. The tool unsets both display variables and requires explicit EGL; it never
falls back to the desktop. FFmpeg is only needed for stitching, not normal tests.

```bash
JAVA_HOME=/path/to/jdk25 LUA_BIN=/path/to/lua5.4 \
python3 tools/test_reel/test_reel.py capture \
  --inventory /absolute/task/inventory.json \
  --select 'TestTasS1Movement#rollingJumpLandingHasOneFrameElevatedRejumpWindow' \
  --select 'TestTasS2ManualTails#' --select 'TestTasS3kPauseClock#' \
  --s1-rom /absolute/original/s1.gen --s2-rom /absolute/original/s2.gen \
  --s3k-rom /absolute/original/s3k.gen --kis2-rom /absolute/original/kis2.gen \
  --out /absolute/task/run-001
```

`--select` is a repeatable regular expression over inventory IDs. Surefire selects
methods, so selecting one parameter invocation deliberately expands to **all**
invocations of that method and records that expanded selection. `--select '.'`
is the expandable all-case path; coordinate its cost before using it. Prefer
bounded method groups while adapters are being developed. A nonzero test exit
still produces the available invocation records and an honest manifest; it is
not turned into success. Setup failures/disabled tests without an invocation
record remain `not-executed`, outcome `unknown`; inspect the Maven result to
attribute them. An assertion pass in a named limitation test does not mean native
parity. No ordinary test opens a recorder unless `openggf.testReel.dir` is set.

`run.json` retains the exact queued command, original ROM CRC32/SHA-1, base commit,
dirty source diff, source file hashes and fingerprint. Each `case.json` retains the
actual JUnit unique ID, converted arguments, display name, outcome and frame count.
`state.jsonl` maps every PNG to actual inputs, player coordinates/velocities,
camera, clocks and mode. The current adapter has a 320×224 source contract.
Original ROM bytes and ROM-derived frames stay local.

Inventory and execution source identities are separate. `fixture_sha256` (also
exposed as `inventory_fixture_sha256`) describes the inventory's source class;
`capture_fixture_sha256` comes only from the actual run's
`source_file_hashes[source_file]`. A dirty class B recorded against committed
inventory A keeps both hashes and cannot be combined into A. Use a target
inventory declaring B to combine that capture. The combiner rereads the retained
run metadata, so a stale per-case hash claim cannot override it. Missing actual
hashes fail closed; the explicit legacy exception below leaves them **null**, not
equal to the inventory hash.

Before capture, selected parameter arguments/ordinals must still match the working
source. A changed provider requires a regenerated inventory; it cannot silently
reuse old labels. Runtime reconciliation and combination also check actual JUnit
arguments and invocation ordinals. New runs check selected source-class hashes
again after Maven and reject identity attribution if those files changed while
queued/running. Older recordings retain the limits of their original snapshots.

## Stitch and extend

```bash
python3 tools/test_reel/test_reel.py stitch \
  --manifest /absolute/task/run-001/manifest.json \
  --out /absolute/task/run-001/preview.mp4
```

The output includes `preview.edit.json` (source/output frame intervals and probe
results), FFmpeg concat/chapters inputs and per-case lossless segments. The final
MP4 is encoded once, decoded in full with `-xerror`, and checked against the exact
expected frame count. Source frames, state and manifests are retained. Existing
MP4s are never overwritten. `--slow 4` is an explicit debug presentation; the
edit map records its speed factor. Default speed is always 1.

Later capture batches can be assembled without recapturing earlier cases:

```bash
python3 tools/test_reel/test_reel.py combine \
  --inventory /absolute/task/inventory.json \
  --manifest /absolute/task/run-001/manifest.json \
  --manifest /absolute/task/run-002/manifest.json \
  --out /absolute/task/combined
python3 tools/test_reel/test_reel.py stitch \
  --manifest /absolute/task/combined/manifest.json \
  --out /absolute/task/combined/preview.mp4
```

Combination checks fixture identities and rejects duplicate captures by default.
`--prefer-last` explicitly selects a corrected later revision of a duplicate.
The development-only `--allow-legacy-source` admits the initial v1 preview with
the same pinned source base and retained dirty diff, marking its missing per-file
fingerprint as a provenance limit. This option cannot bypass a known actual hash
mismatch or an incomplete new-format hash map. New recordings use file fingerprints.
Source directories remain
external references; preserve all referenced runs when moving an assembled reel.

Correct older metadata without rerunning tests or overwriting its evidence:

```bash
python3 tools/test_reel/test_reel.py reconcile \
  --inventory /absolute/task/inventory.json \
  --run-dir /absolute/task/run-001 \
  --out /absolute/task/reconciled-001
```

The new manifest references the original case directories and `run.json`. It
derives actual hashes from that retained record, not the current checkout or the
inventory. Only the initial hashless v1 run needs `--allow-legacy-source` here.
If this exposes A/B differences, keep them explicit and choose a matching target
source before combination. The corrected initial preview metadata is described
in the dated validation record; its existing video bytes were not regenerated.

## Add a real adapter

The global `TestReelExtension` does no work when recording is disabled. It wraps
actual test/template invocations after setup and before teardown, preserving test
exceptions and attaching real JUnit invocation ordinals. Body outcome is separate; a Jupiter TestWatcher finalizes the actual test outcome after per-test teardown. Loading/menu observations retain explicit nulls when no player or object manager exists. The adapter never executes
a test a second time to make a movie.

For tests already using `GameplayCaptureSession`, replace each existing
`session.step(input)` with `TestReelCapture.step(session, input)`. This delegates
exactly once, then uses the session's production render/readback path only while
recording. The S2 live P2 control and S3K pause limitation are examples.

For a loaded native 320×224 `HeadlessTestFixture`, call
`TestReelCapture.afterHeadlessFrame(fixture, up, down, left, right, jump)`
immediately after the existing step. The S1 movement helper demonstrates this.
It creates an owned EGL context over the **same** loaded world, uploads its CPU
art, draws through native sprite priority, and checks that presentation did not
change player position, velocity, angle, air/roll flags and animation fields. This is a listed-player-field guard, not proof of complete world-state purity.
It does not reload the level, reset player art/animation, add ticks or recenter
the camera. This adapter is currently verified for the S1 movement fixture only;
other worlds/events need their own focused evidence before registration.

Fixtures with component managers, mocked render consumers, custom geometry,
multiple private worlds, or non-gameplay menus need explicit adapters at their
real ownership boundary. Do not boot an unrelated level under their logic and
call it their footage. If no drawable state exists, retain the per-case outcome
and reason with zero frames. Register reviewed dispositions and available adapters
in the scope JSON; unreviewed cases remain explicit. Never turn a placeholder or
an omission into an in-engine clip.

## Focused checks

```bash
python3 -m unittest discover -s tools/test_reel -p 'test_test_*.py' -v
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/path/to/jdk25 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com.openggf.tests.reel.TestTestReelExtension' test
```

The Java check launches two parameterized child invocations, including a deliberate
failure, and asserts both actual identities/outcomes without a fabricated movie.
It also verifies that a passing body with failing `@AfterEach` is reported as failed, missing menu state stays null, and normal execution needs no recording output. Real-render
validation is separate; see the dated implementation record.
