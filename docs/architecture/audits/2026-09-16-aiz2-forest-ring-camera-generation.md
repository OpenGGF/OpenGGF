# AIZ2 forest-loop plane ring: camera generation regression

Status: **fix implemented on `bugfix/ai-aiz2-forest-ring-live-camera`**; evidence below.

## Report and cause

The user reported the AIZ2 forest loop before the end boss "broken again" on
`develop` (`5286a09c9`) versus `master` (`37aeb6b84`). The trace-capture oracle
(`TraceCaptureTool --trace aiz_completerun --clip frames:22400:23000`, rows where
the post-bombing camera loops `$44C0`–`$46C0`) showed the symptom is not the
`Obj_AIZ2BGTree` sprites: whole spans of the foreground plane (canopy, wide FG
trunks, grass) go blank and only the thin tree sprites remain over the burning
background. Master renders the full forest.

`git blame` on `LevelRenderer` placed the change in `ca44ebed2` (2026-09-15,
"publish S3K terrain scroll with the retained sprite table"), which switched the
persistent `$200` foreground ring's camera from the live camera to the retained
scroll presentation while the ring still consumed the live `Level_repeat_offset`.
S3K's single-player lag VBlank skips publication, so when the `$200` wrap lands
on a lag frame the ring translates its baselines by `$200` against a camera that
has not wrapped in the published generation; when publication catches up the
ring sees a `$200` jump with no offset, takes its large-jump reseed and fills
the forest entrance into visible cells.

## ROM model

`AIZ2_DoShipLoop` (`sonic3k.asm:105205`) runs from `SpecialEvents` in the CPU
loop and, on the wrap frame, sets `Level_repeat_offset`, subtracts `$200` from
camera and players, and retargets `Camera_X_pos_rounded` to the new camera
rounded down minus `$10`, all in one routine. `AIZ2SE_End` then calls
`DrawTilesAsYouMove` (`sonic3k.asm:104978`, `103171`), whose `Draw_TileColumn`
compares live `Camera_X_pos_copy` with `Camera_X_pos_rounded` and writes the one
entering column to VRAM immediately. Only H-scroll, VSRAM and the sprite table
are published at VInt and retained on lag; Plane A is 64 tiles = `$200` wide,
equal to the wrap, so a retained scroll register aliases onto the same cells.

## Fix

`LevelForegroundPlane.drawAsYouMove` runs from the gameplay step after the zone
events' layout flush (the `ScreenEvents` tail) and pushes the live camera and
the live repeat offset to `LevelTilemapManager` together. `LevelRenderer` no
longer pushes the ring camera; it consumes only published scroll state. Wrap
offsets pushed before a draw accumulate until the reconcile consumes them and
are cleared by a fresh seed. The method lives outside `LevelManager` because
that class is a pinned Mod API surface.

Rejected: keeping the push in the renderer but reading the live camera there.
It is the same ROM pairing and also restored master's frames, but it leaves
the renderer reading live gameplay state, which `ca44ebed2` removed for a
reason.

## Evidence

- `TestAiz2ForestRingCameraGeneration` reproduces the wrap-on-lag case by
  restoring the pre-wrap presentation after the wrap step: red on `5286a09c9`
  (ring camera `0x4600`, expected `0x44C0`), green with the fix.
- Trace-capture triplets (develop / fix / master) over rows 22460–22880 at
  15-row spacing: the fix matches master on every frame where develop blanked.
- Focused suite (ring, presentation, ship-loop rewind, sidekick bounds,
  background viewport): 32 tests green.
- AIZ trace replays are attributed in the commit message against a matched
  baseline run on unmodified `5286a09c9`; the fix cannot execute before the
  forest loop (`foregroundWrapsHorizontally()` gates it).
