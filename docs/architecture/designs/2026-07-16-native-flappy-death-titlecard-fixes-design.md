# Native Flappy Death and Title-Card Fixes

## Goal

Preserve the stock Sonic 3 & Knuckles death sequence when native Tails dies in
the maintained Flappy sample, and ensure the initial S3K title card sees the
custom level's composed palette before its first render.

## Death-state correction

`AbstractPlayableSprite.applyDeath(...)` currently assigns the `dead` field
directly. That bypasses `setDead(true)`, whose established state-transition
contract clears active Tails flight. A flying Tails therefore retains a live
`TailsFlightController` after the stock `-0x700` death velocity is installed,
and flight logic continues changing that velocity on subsequent frames.

All instant-death entry points already converge on `applyDeath(...)`. It will
enter death through `setDead(true)` instead of assigning the field. The stock
death velocity, camera freeze, sound, gravity, countdown, life decrement, and
level restart remain unchanged. This is an engine lifecycle correction, not a
Flappy-specific trajectory policy.

## Initial custom-zone palette publication

An in-memory `Sonic3kLevel` correctly composes its character and sparse creator
palette values without touching runtime services. That service-free constructor
contract remains intact. Unlike the ROM-backed construction path, however, the
composed palette is not necessarily resident on the GPU before the pre-level
title card draws.

`LevelManager` already installs `S3kCustomZonePaletteBridge` while initializing
object/HUD art. Immediately after installing a non-null custom-zone bridge, it
will begin one palette-ownership frame, submit the bridge's character, creator,
and host-HUD claims, and resolve them into the current level palettes. Resolution
also uploads dirty lines when OpenGL is active. This occurs before the later
title-card request, so title-card tile `$500` can use line 0 color 6 (`$000E`,
red) rather than a stale palette texture. Normal per-frame palette ownership is
unchanged and begins fresh at the next frame boundary.

Stock levels have no custom-zone bridge and do not enter this path.

## Verification

- Extend the Tails-flight movement tests so `applyCrushDeath()` from active
  flight clears flight while retaining `y_vel = -$700`, then applies the normal
  non-flight gravity step.
- Extend the maintained Flappy integration test to assert that custom-zone
  palette ownership has already resolved immediately after level load, before
  any gameplay frame, and that title-card line 0 color 6 is the S3K red word.
- Keep the existing restart, HUD, creator-palette, and rewind tests green.
- Update the native-Flappy guide and changelog, rebuild the sample mod, run the
  focused suites and full `mvn package`, then merge into `next` and repeat the
  full package gate there.
