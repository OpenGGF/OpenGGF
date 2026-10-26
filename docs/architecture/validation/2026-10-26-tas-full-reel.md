# Full TAS test reel and unsupported-trick repair assessment

The requested scope is every new or extended TAS/scoring invocation in the
249-case inventory at base `94b2a5bf1daeaaad550ff7b8091a8361d6a9094e`.
Use actual fixture frames at 60 Hz; genuinely non-drawable cases receive a single
labelled editorial card with their real JUnit outcome. This is local developer
footage. No gameplay repairs or public media publication are part of this pass.

## Shared recorder qualification

API commit `4ffa30ed51aa0c3ba93d33073682bebf57989399` adds component frame recording
and exact-input/mode headless observations. Six lifecycle/API tests passed,
zero failures/errors/skips (Maven 2:04, finished 17:33:49Z).

Image inspection of S1 captures found missing capsule, animal, monitor and giant
ring art despite passing gameplay assertions. `GraphicsManager.init()` replaces
the CPU-populated headless atlas; renderer allocation caches then skip uploading
their unchanged bases. Uploading only level patterns leaves native object tiles
absent. Reinitializing player art would also reset animation and is rejected.

The test-only `TestReelPatternTransfer` retains cached ROM pixel versions, logical
tile IDs, aliases and range registrations before GL initialization, then uploads
them to the new atlas. This includes HUD, ring, water and sidekick allocations,
without invoking asset loaders, PLC work, animation or gameplay updates. Alias
versions with contradictory cached pixels fail explicitly rather than inventing
an upload order. Physical GPU slot numbers may change; logical tile references
and alias sharing are preserved.

Commands below use Java 25, Lua 5.4 and unset `DISPLAY`/`WAYLAND_DISPLAY`:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com.openggf.graphics.TestTestReelPatternTransfer,com.openggf.tests.reel.TestTestReelExtension' test
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com.openggf.graphics.TestTestReelPatternTransfer' test
```

First run: 8 cases, 0 failures, 1 error, 0 skips (42.901 s, 17:51:17Z).
The new gap-alias control exposed reconstruction choosing an alias outside
allocation governance as its source. The bridge now chooses the original cached
source and recreates aliases afterward. Affected rerun: 2 passed, zero
failures/errors/skips (42.533 s, 17:53:41Z). Actual GL texture readback verifies
all five representative ranges and a gap alias. Mutating a source pattern after
capture does not alter restored cached pixels. The six unchanged API/lifecycle
tests passed in the first run.

The pre-fix S1 headless and pause footage is rejected for final assembly. Its
retained records remain provenance, not qualified video. Qualified special-stage
component footage is independent of this adapter and can be reused. The final game-specific qualification is recorded below; the rejected pixels
were never selected for final assembly.

## Complete-census assembly checks

The stitcher distinguishes recorded engine frames from requested one-frame
non-drawable cards. Every card requires a reviewed disposition and an actual
invocation/outcome. `--require-complete` additionally requires a passing outcome
and a matching executed source hash for every inventory case. Method/control
overrides preserve mixed classes; combination retains the final inventory review
instead of an earlier capture's unreviewed planning label.

Python safety checks: 16 passed using
`python3 -m unittest discover -s tools/test_reel -p 'test_test_*.py' -v`.
A bounded assembly used a real 70-frame S1 emerald invocation and an executed
zero-frame scoring-popup mock invocation. It produces 71 output frames, including
one editorial card, with the mock's engine frame count still zero. Full decode and
frame-count checks passed, and the final card was visually inspected.

The first assembly exposed a real encoder mismatch: PNG and colour-source inputs
selected different FFV1 pixel formats, producing `quant_table_index out of range`
while concatenating. It is rejected. All lossless segments now explicitly use
`bgr0`, and both source decoding during assembly and final decoding use `-xerror`.
The corrected 71-frame assembly has no decode errors.

Independent shared-tool review then identified a separate timestamp defect:
Matroska rounds a one-frame 60 Hz segment to 17 ms. Concatenated durations can
make `fps=60` duplicate/drop source frames even when a forced total count passes.
An 80-segment alternating-colour control reproduced wrong frame identity at
output frame25 (normal speed) and28 (4× hold). Assembly now discards fragment
timestamps and uses `settb=1/60,setpts=N*slow` before resampling. The same control
checks every output frame at both speeds and passes. All 17 Python tests pass,
zero skips; the real 70-frame emerald plus one-card encode/decode also passes
again with frame-index timing. Earlier check videos are superseded.

Explicit `--prefer-last` chooses the final recording for each identity before
validating selected source hashes. Superseded inputs are listed in the combined
manifest; they never satisfy the selected-source check. This permits a corrected
pause record to replace an older dirty revision, while a mismatching final
revision still fails. The regression exercises both directions.

## Repair assessment

All 29 unsupported scenario IDs and their publication assignments reconcile
against the delivered binary verdict report. They form 18 remediation groups:
1 low, 8 medium, 7 high and 2 very high complexity. Individual scoped estimates
remain in the machine-readable report; shared groups use the most demanding
affected owner. For example S1's local unsigned comparison is low, while a
cross-game movement repair is medium; pause-clock delivery is high when including
hardware/PLC/fade admission and duplicate-tick prevention.

The [repair assessment](2026-10-26-tas-supportability.md) records proposed changes,
acceptance cases and pinned engine-owner links. The external interactive/JSON
report also retains native owners, dependencies, rewind impact, confidence and
open design questions. Five negative reconciliation controls reject missing IDs,
duplicates, changed publication attribution, unknown complexity and absent native
evidence. All 46 referenced engine/document paths and line bounds were checked.
No prospective repair is promoted to supported. The S2 P2 intro transition still
needs a synchronized native causal inlet before implementation scope is firm.

## Combined validation scope

The change-based plan at `7e1e53607` against pre-task destination `94b2a5bf`
selects 4,226 smoke classes because the recorder is under shared test
infrastructure. That selection is disproportionate for opt-in test-only capture
hooks: no production, POM, test-selection policy or normal test lifecycle changes
are present. Focused coverage includes the actual inventory invocations with
their capture paths, recorder inactive/lifecycle checks, real GPU transfer,
Python source/provenance/card checks, final media decode, relevant graphics
isolation guards and required S3K gates. The combined results are recorded below. No release-only full suite is requested or claimed.


## Final source reconciliation and game captures

The final scope has 249 invocations across 62 classes: 131 drawable fixtures and
118 reviewed one-frame cards. The game branches were integrated without production
changes: S1 `e63a607a3d2dfbc1359384782913da5d88a9ada0`, S2
`92eff6cb7559781996efd4568df54ddae87237ad`, and S3K
`b2c29c7a03e4e50530d25940164444e384fa5f3e`. Their dated reports preserve exact
commands, source identities, rejected qualification attempts and state guards.

| Scope | Drawable cases | Engine frames | Card cases |
| --- | ---: | ---: | ---: |
| S1 | 61 | 2,256 | 23 |
| S2 / KiS2 | 39 | 6,539 | 19 |
| S3K | 31 | 2,312 | 36 |
| Shared scoring / popup / rewind | 0 | 0 | 40 |
| Total | 131 | 11,107 | 118 |

Accepted S1 recordings use the special-stage, non-drawable and corrected native
captures in that order; native records replace the earlier pause footage. S2 uses
only the selected expanded/final records. S3K selects the exact accepted case/run
pairs from its handoff, plus final-source card outcomes. Older failed, black or
superseded component captures are excluded. Root reconciliation rejected one S2
vertical-camera card with a stale source-class hash; it was executed again on
final bytes rather than relabelled.

The final missing-card capture at integrated `e952302cf` selected 50 invocations
across 14 classes. All 50 passed with zero failures/errors/skips, 32.094 seconds
Maven execution after 32 seconds queue waiting (18:42:48Z). It records outcomes
without adding game frames. Together with the selected game/shared captures,
all 249 identities have passing actual JUnit outcomes and matching executed
source hashes. No legacy hash exception is used. The external capture roots
retain exact queued commands, original ROM identities, arguments, state rows,
source hashes and before/after source-stability checks.

The unreachable-monitor control has 29 genuine blank readbacks. Its reviewed
non-drawable disposition selects one editorial card, while the raw 29 frames
remain intact. The edit map reports these omitted blank frames separately;
complete mode cannot silently display them in place of the requested card.
All 18 Python safety tests pass, including exact source identity, card selection,
and every-frame identity across 80 single-frame cuts at both native and 4× speed.

The final capture and assembly commands use an external `TAS_FULL` task directory:

```bash
python3 tools/test_reel/test_reel.py capture \
  --inventory "$TAS_FULL/inventory-integrated.json" \
  --select "$(cat "$TAS_FULL/missing-card-selector.txt")" \
  --s1-rom "$TAS_ROM_ROOT/s1.gen" --s2-rom "$TAS_ROM_ROOT/s2.gen" \
  --s3k-rom "$TAS_ROM_ROOT/s3k.gen" --kis2-rom "$TAS_ROM_ROOT/kis2.gen" \
  --out "$TAS_FULL/cards-final-001"
python3 tools/test_reel/test_reel.py combine \
  --inventory "$TAS_FULL/inventory-integrated.json" \
  --manifest "$TAS_FULL/accepted-selected/manifest.json" \
  --manifest "$TAS_FULL/cards-final-001/manifest.json" \
  --out "$TAS_FULL/combined"
python3 tools/test_reel/test_reel.py stitch \
  --manifest "$TAS_FULL/combined/manifest.json" \
  --include-nondrawable-cards --require-complete \
  --out "$TAS_FULL/combined/full-test-reel.mp4"
```

## Required domain and isolation checks

At `c5bed6a32cefe0427f01e39ffe3860d559aabe5d`, the required S3K selection
passed 60 tests, zero failures/errors/skips: 33.495 seconds Maven execution,
600 seconds queue waiting, finished 18:31:07Z. This result remains applicable
after the S2/S3K capture merges: runtime and global recorder bytes are unchanged,
and these domain tests do not load the newly added per-game capture helpers.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$TAS_ROM_ROOT/s1.gen" \
  -Dsonic2.rom.path="$TAS_ROM_ROOT/s2.gen" \
  -Ds3k.rom.path="$TAS_ROM_ROOT/s3k.gen" \
  -Dkis2.rom.path="$TAS_ROM_ROOT/kis2.gen" \
  '-Dtest=com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' test
```


After all game capture branches merged at `e952302cf`, the graphics isolation
selection passed 14 tests, zero failures/errors/skips: 45.901 seconds Maven
execution, 31 seconds queue waiting, finished 18:43:34Z. This covers configured
context ownership, raw GLFW rejection, global teardown registration and restoration
of caller backend properties. No Java source changed after this run.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=com.openggf.tools.TestRawGlfwTestSetupGuard,com.openggf.tools.TestHeadlessBackendPropertyIsolation,com.openggf.tests.TestHeadlessStateTeardownGuard' test
python3 -m unittest discover -s tools/test_reel -p 'test_test_*.py' -v
```

The Python command passed all 18 tests after the blank-control disposition fix,
with no skips. Validation is bounded and focused; it is not a full-suite pass.


## Encoded result

`combined/full-test-reel.mp4` contains all 249 invocations and 249 chapters:
11,225 frames at exactly 60 Hz, 187.083333 seconds. That is 11,107 actual engine
frames plus 118 single-frame editorial cards; 29 reviewed blank control readbacks
are explicitly omitted from presentation but retained in source evidence. The
32,229,704-byte MP4 SHA-256 is
`98155a23ad968ac321828b5e2d12b7a6c235b36a41015f5cff10b87ad9b9ce9f`.

Encoding and complete `ffmpeg -xerror` decode passed. `ffprobe -count_frames`
matched all 11,225 frames; every edit interval is contiguous and every card spans
exactly one output frame. The edit map preserves actual JUnit IDs, source
intervals and capture directories. No source capture was overwritten. This is a
silent native-speed developer test compilation, not published TAS movie playback
or full-route synchronization evidence.


Final-video image inspection sampled 17 exact output frames, including the first
two cards, last card, S1 maze/capsule/roll-jump, S2 component explosion/P2/capsule/
halfpipe, S3K missile/boss/Blue Sphere/flame entry and load/fade transitions.
Native sprites and restored cached object art are visible. A component object may
leave its bounded viewport and a real fade may be black; these actual frames are
retained rather than replaced with invented scenery. Game-specific qualification
also checked every accepted drawable invocation. The final contact sheet and
its exact frame selection remain beside the external MP4.
