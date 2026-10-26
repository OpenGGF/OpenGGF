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
