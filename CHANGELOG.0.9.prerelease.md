# OpenGGF 0.9.prerelease — Development changelog

Unreleased. Work on `next` for the release after 0.8; `develop` work belongs in
[CHANGELOG.0.8.prerelease.md](CHANGELOG.0.8.prerelease.md). Add entries under the
matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).

## Gameplay and presentation

- **Sonic CD (development preview):** Sonic CD runs from the user's own USA
  disc image (a cue sheet and its tracks, named by `roms.soniccd` or found in
  the ROM folder) as
  a stock game module on the shared engine. Nothing from the disc is in the
  repository. All 70 stage programs load: seven zones; acts 1 and 2 in the
  Present, Past, Good Future and Bad Future; act 3 in both futures. Each zone
  has its own presentation, objects and boss. Sonic plays on Sonic 1's physics
  with Sonic CD's own rules (spin dash and super peel-out, the extended camera,
  the 3D ramps, per-zone player routines and the idle timer that ends in
  Sonic giving up). Time travel, special stages
  (without their results graphics), the title, stage select, Time Attack and
  backup RAM (saved in `saves/soniccd`), the opening video, both endings, FM
  effects, CD audio and PCM sound are hosted. The master
  title lists it when the disc is configured or found (`roms.default: scd` boots
  it directly) and verifies it against the USA release before starting. Not
  included: Visual Mode, D.A. Garden, the pencil test, Thank You,
  Comin' Soon, attract demos, the backup RAM manager, and the Japanese and
  European releases. Disc audio, video songs and PCM sound follow the music
  and effects volumes. The time-warp stars are drawn and Robotnik's projector
  can be destroyed in the Past (its hologram animals are not drawn).
  Accuracy is measured against native recordings made on an emulator running
  the USA disc: 26 of 28 full-act routes (start to signpost) equal native on
  every compared field to their end (146,390 of 149,633 rows), and 68 of 70
  fourteen-second stage fixtures do (56,879 of 57,049 rows). Open: R42A (water)
  and R82A, R11C and R51B (objects). Rings are compared too: the counter and
  every placed ring's position equal native on every compared row of the 28
  routes and two runs through Palmtree Panic loops, except lost rings after a hit
  in two Metallic Madness routes. Stage loads clear the state native
  clears, so playing stages in any order matches playing each alone. In normal
  play the object clock a stage starts from and the video player's disc wait
  follow measured emulator timings under `gameplay.loadTimeSimulation`
  (`FAST` and `PROFILED`; `NONE` starts the clock at 0); trace replay admits
  the V-INTs the console dropped, under two timing-contract extensions the
  owner approved on 2026-11-13. The capture tools can film Sonic CD
  (`--no-hud` leaves the HUD out; a trace clip may run past the recording's
  last row into the game's own flow, such as a time warp). Third-party code is credited in `NOTICE.md`:
  the backup-RAM format code follows superctr/buram (MIT), and the PCM chip and
  stamp graphics are translated from MAME (BSD-3-Clause); earlier code derived
  from Genesis Plus GX (non-commercial licence) was replaced, and Genesis Plus
  GX is used only as BizHawk's reference core. Most shared-engine changes are selected by
  Sonic CD alone (listed in the engine map); a few apply to every game and
  were checked against the other games' trace replays: wall-probe angle
  writes, the left headroom probe, the replay driver's lag-row pad rule and
  the speed-shoes timer skipping the turn on which a hurt state ends. See
  [game status](docs/guide/playing/game-status.md#sonic-cd-development-preview).

## Modding and tools

## Maintenance and release preparation
