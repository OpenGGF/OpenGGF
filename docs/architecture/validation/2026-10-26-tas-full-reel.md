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
component footage is independent of this adapter and can be reused. Final
game-specific visual qualification and assembly are still pending at this
checkpoint.

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

## Combined validation scope

The change-based plan at `7e1e53607` against pre-task destination `94b2a5bf`
selects 4,226 smoke classes because the recorder is under shared test
infrastructure. That selection is disproportionate for opt-in test-only capture
hooks: no production, POM, test-selection policy or normal test lifecycle changes
are present. Focused coverage includes the actual inventory invocations with
their capture paths, recorder inactive/lifecycle checks, real GPU transfer,
Python source/provenance/card checks, final media decode, relevant graphics
isolation guards and required S3K gates. The latter combined checks remain pending
at this checkpoint. No release-only full suite is requested or claimed.
