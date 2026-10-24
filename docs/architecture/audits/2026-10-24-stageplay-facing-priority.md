# Stageplay facing and directed priority — 2026-10-24

Base: `ac46111bc`, engine worktree `.worktrees/stageplay-facing`.
The Day Off source is `$DAY_OFF_PRODUCTION/scenes/build_scene.py`.

## Directed foreground occlusion

`StageplayObjectDraw.draw` flushed the enclosing sprite batch to bind its private
art/palettes, then emitted tiles without reopening a batch. Their direct commands
selected the shader at execution, after the enclosing sprite pass had disabled
the priority shader. Native low-priority pictures consequently painted over
foreground tiles. This is a batch-ownership error, independent of game, zone,
object identity, source priority bits or cast mode.

Reopen the batch while the part's native priority/mask is active, flush before
restoring that scope, and reopen the surrounding batch for subsequent sprites.
The template retains its native priority, tile attributes and palette mask.
No ROM behavior, art or GHZ-specific rule changes.

The first ordinary-publication comparison passed but did not exercise the live
cinematic command path. It was replaced by native and directed draws in the same
production cinematic view. A stock world alone cannot create that view: the
fixture must adopt the Stageplay world module's directed scroll service. Comparing
an old capture session's ordinary picture with the adopted world also compares
different publication/background owners; that was rejected as an oracle.

The final regression uses the real S1 Motobug constructor/art, four GHZ loop
positions, a frozen native graph and a separately installed baked visual. A
no-occlusion control proves that foreground actually hides sprite pixels, and
an empty picture rejects blank or stale comparison success. With the old draw
scope, the native/directed comparison differed by 688 pixels at X=5580. Reopening
the batch fixes the comparison. No ROM pixels are stored in the repository.

Validation on the base plus this change (Java 25, surfaceless EGL, S1 REV01 SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`):

```sh
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$OPENGGF_MAIN/s1.gen" \
  -Dtest=TestStageplayObjectPriorityRendering,TestStageplayObjectDirection test -B
```

Completed 2026-10-24 10:04 Europe/London: 30 tests, 0 failures/errors/skips.
This is focused validation, not broad smoke or release-suite validation.
