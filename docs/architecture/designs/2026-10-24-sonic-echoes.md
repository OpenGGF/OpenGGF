# Sonic Echoes: a time-loop puzzle mod for Sonic 2

Task: design and build a Braid-style time-loop puzzle game as an OpenGGF mod, on a feature
branch in the main checkout (no worktrees), implemented by delegated agents with the lead
agent as designer and orchestrator; install it locally when complete.
Base: `97eb86f186` (`develop`). Branch: `feature/ai-sonic-echoes`.
Source project: `examples/sonic-echoes` (patch mod, `baseGame: s2`).

## Pitch

*Robotnik's Echo Engine has caught Sonic in a loop.* Every puzzle room is a short, fenced
stretch of a real Sonic 2 act. You get a few seconds. When the loop ends the world snaps back
to its first frame, and **the run you just played keeps playing beside you as an Echo** — a
real, physical Sonic driven through the ROM's own physics by your recorded inputs. Echoes
stand on pressure plates, launch you off Hill Top's seesaws, bounce badniks out of your path
and spring springs. Solve the room by choreographing a team of yourselves.

The hook is honesty: echoes replay **inputs, not positions**. If the live Sonic changes the
world an echo depended on, the echo does something different. That is not a bug, it is the
game's second mechanic, the **Paradox**.

## Core loop

1. A room loads. The **loop clock** (per-room length, 8–20 s) starts on the live Sonic's first
   input, so standing still to think is free.
2. Play. Echoes from earlier loops replay simultaneously from frame 0.
3. The loop ends when the clock reaches zero, or when the player presses **Echo** (commit now).
   The attempt becomes the newest echo (up to the room's echo cap) and the room restores to its
   start snapshot. A live death ends the loop *without* committing the attempt.
4. Clear the room by touching the **Exit Rift** with the live Sonic.

Other verbs: **Undo** drops the newest echo, **Reset** clears every echo, **Exit** returns to
the hub. These are mod `ActionMap` actions with keyboard and pad defaults, never A/B/C
(all three jump in Sonic 2) and never Start (stock pause).

## Echo rules

- An echo is a Sonic sidekick slot whose controller is its recording, fed through the native
  controller-2 manual path so every object that reacts to player 2 in the ROM reacts to it.
  CPU follow, fly-in respawn and despawn behaviour are suppressed for echo slots.
- When an echo's recording runs out it **holds still** (released controller) for the rest of
  the loop. An echo that ends its life standing on a plate keeps holding it. This is the most
  important rule in the game; the tutorial teaches it in room 1-1.
- **Paradox:** each echo carries its recorded path. If it dies, or drifts more than 48 px from
  where it was at the same frame when recorded, it *fractures*: it flashes, bursts into
  sparkles and is gone for the rest of the loop. The HUD names the fracture ("ECHO 2
  PARADOX"). A fracture never deletes the recording; the next loop tries again.
- Echoes are coloured by slot (cyan, magenta, gold, lime, violet, in that order), drawn
  opaque with a slight shimmer and a small slot number above them. The live Sonic keeps the
  stock palette.
- Echoes do not collide with each other or the live Sonic (native Sonic 2 behaviour between
  players), but they do touch every object.

## Loop mechanics in the engine

- The **loop restore is the rewind system.** Capture a full rewind snapshot on the room's
  first ready frame; every loop restore returns to it. The stock live-rewind control is
  disabled for the mod's acts so the player cannot desynchronise a loop.
- Echo recordings, room progress and the loop count are mod state that must *survive* the
  restore. Keep them outside any registered rewind adapter (or in an adapter excluded from the
  room snapshot).
- Determinism: an echo replays the same input stream from the same snapshot, so echoes that
  are not disturbed reproduce their recorded paths frame for frame. A headless test asserts
  this zero-drift property for undisturbed echoes.

## Room objects (mod-registered)

| Object | Behaviour |
|---|---|
| **Echo Plate** | Floor pressure plate, pressed while any body (live or echo) stands on it. `hold` variant: active only while pressed. `latch:N` variant: stays active N frames after release. Each plate drives a channel (colour-coded). |
| **Echo Gate** | Solid vertical barrier on one or more channels (AND). Slides open while all its channels are active, closes otherwise; never closes on a body (it waits). |
| **Exit Rift** | Room goal. Touching it with the live Sonic clears the room. An echo touching it does nothing. Can be *sealed* to a channel. |
| **Chrono Shard** | Optional collectible, three per room. Any body may collect one, but all three must be collected in the **same loop** for the shard star. |

Native Sonic 2 objects stay native: springs, seesaws (Hill Top's Obj14 launches whoever lands
on the opposite end — a natural echo puzzle), badniks, platforms and bumpers. Each room
removes the act's own objects and places only the puzzle's layout.

Object art comes from the ROM: Sonic 2 already has button, barrier and sparkle art (for
example Obj47's switch, Obj2D's barrier, ring and invincibility-sparkle frames). Choose frames
from contact sheets, request them with `RomArtRequest` when the zone's PLCs do not load them,
and tint with palette lines the zone has to spare. Draw with primitives only as a last resort.

## Content: 3 worlds, 12 rooms

| World | Zone | Theme | Rooms (intent) |
|---|---|---|---|
| 1 | Emerald Hill | **First Echo** — learning the loop | 1-1 *Hold the Door* (1 plate, 1 gate, par 1) · 1-2 *Two Hands* (2 plates, par 2) · 1-3 *Relay* (latch plates; an echo runs over a plate on its way, par 1) · 1-4 *Escort* (badniks guard the exit path; an echo clears them, par 2) |
| 2 | Hill Top | **Seesaw Science** — echoes as launchers | 2-1 *Lift Off* (echo drops on a seesaw, par 1) · 2-2 *Double Bounce* (two seesaws chained, par 2) · 2-3 *High Ground* (plates only reachable by launch, par 2) · 2-4 *Counterweight* (a plate under one seesaw end, par 3) |
| 3 | Chemical Plant | **Paradox Plant** — the world fights back | 3-1 *Don't Touch* (your live run can break your own echo; teaches Paradox, par 2) · 3-2 *Spring Relay* (par 2) · 3-3 *Clockwork* (tight latch timings, par 3) · 3-4 *The Last Loop* (finale: four echoes, three channels, sealed rift, par 4) |

Each room fits within about two screens and has a pit-free floor or visible walls. The final
room unlocks at 24 of 36 stars. The concrete windows, spawns and layouts are chosen by
surveying the real acts (`FloorSegmentSurveyProbe`), the way Sonic Survivors chose its arenas.

**Every room ships with a reference solution**: per-loop controller scripts. A headless test
replays each solution loop by loop and asserts the room clears within par. The same solution
backs the in-game **Hint** (show the next solution echo as a translucent ghost).

## Scoring and progression

Three stars per room: **Clear**, **Par** (cleared with ≤ par echoes) and **Shards** (all three
Chrono Shards in one loop). The best echo count and the fastest final loop are saved per room
in mod storage. Rooms unlock in order within a world; world 2 opens at 3 stars, world 3 at 9.

## Presentation

- **Hub: the Echo Engine.** A Sonic 2 startup scene: world tabs, a 4-room grid with stars, best
  echo count and par, the zone's real title-card name. Starting a room launches the patched act.
- **In-room HUD** replaces SCORE/TIME/RINGS: the loop clock (`0:07.42`, flashing in the last
  3 s), echo slots as coloured pips (filled, empty, fractured), the room name and par. A thin
  **timeline** along the bottom shows each echo's recording length against the loop.
- **The rewind moment.** On loop end the screen holds for 12 frames with a palette flash and a
  short ROM sound effect, then cuts to frame 0. No long transitions: iteration speed is the game.
- **Room clear:** stars pop in one at a time with the ring and checkpoint sounds, then back to
  the hub or on to the next room.
- Music is each zone's own track.

## Delivery

- Phase 1 (foundation agent): mod skeleton, echo slots driven by recordings, loop
  capture/restore, Paradox, tints, mod objects, one EHZ tutorial room, headless tests
  (undisturbed-echo zero drift, an echo holding a gate open, Paradox fracture).
- Phase 2, in parallel with disjoint ownership: content (all 12 rooms with solutions and
  solvability tests) and presentation (hub, HUD, timeline, stars, storage, audio cues,
  README and build tooling).
- Phase 3 (lead): integration review, captures, documentation obligations, local install.

Small general engine hooks are allowed only when the mod is blocked; each must be semantic (not
Sonic Echoes-specific), keep rewind correct and carry its own focused test.

## Decisions and evidence

Recorded as phases complete: what was tried, what was rejected, and why.

### Phase 1 foundation (2026-10-24)

Implementation is uncommitted in the main checkout on `feature/ai-sonic-echoes`, based on
`97eb86f186b432722024ca419a00b901b38f5ec2`. No branch changes, worktrees, commits or pushes.
The source project is `examples/sonic-echoes`; its README documents the room format and seams.

- The patch retains EHZ1 ROM terrain and replaces its objects/rings. Room 1-1 uses camera X
  bounds 32..416 (physical right wall 800), camera Y 480..560, native spawn (96,655), plate
  (145,671), gate (440,550), and rift (620,612). The reference solution is `40:R,60:-`, commit,
  then `240:R`. A solo run is blocked; the recorded Sonic stops at (145,652) and holds the
  plate after its input ends. Native slopes, rather than fitted physics, determine that stop.
- `RoomSession` is an ordinary owner service and frame controller with no rewind adapter.
  The first presentation-ready frame captures `CourseControl.capture()` after installing
  all five physical Sonic slots and room bounds. Restores use `CourseControl.restore()`;
  controller input is fed again after restoration. Recordings, loop count and clear results
  therefore survive world restore; attempt-local channels, shards and fractures reset.
  `allowsDebugRewind=false` closes the stock live-rewind path.
- `EchoController` specializes the existing CPU-controller extension point with permanent
  manual authority. It writes the actual controller-2 held/pressed latches, exposes their
  directional/jump values to the normal sprite dispatcher, and suppresses CPU follow,
  control timeout, flight and respawn updates. No recorded position drives physics. Inactive
  or fractured slots keep their roster identity but are hidden, parked outside the room,
  and skip physics. Post-step positions only detect death or Euclidean drift >48 pixels.
- `registerLaunchTeam(ZoneKey.stock(...))` was rejected by the production registration
  contract: launch-team contributions require owner-scoped authored zones. The mod therefore
  creates its fixed room-local roster through the registered Sonic factory at first readiness,
  using the same sprite-manager sidekick registration/art initialization as bootstrap. No
  saved team configuration is changed. No engine hook or Mod API surface change was needed.
- Actions use the existing `LevelInputOverlay` physical-input channel and `ActionReducer`:
  E/Y commit, U/RB undo, R/left-stick click reset, Q/Back exit to the module title. Genesis
  A/B/C and Start are not assigned these verbs. The build script adapts the shared Survivors
  builder plus Three Islands' enabled/trusted SHA-256 installation pattern.
- Per-slot `CharacterLooks` ramps use private ordinary sidekick `RenderContext` palettes;
  live Sonic keeps stock colours. The first rendered attempt had labels but invisible echo
  bodies because the newly created palette rows had never been uploaded. Explicit palette
  publication fixes it; retained contexts reset after playable-art refresh. Cyan was verified
  beside stock Sonic with display-free EGL. The other colour presets remain bounded visual
  coverage, not five-slot screenshot certification.
- ROM art requests load Obj47's button, HTZ barrier and invincibility stars from the supplied
  ROM. Invincibility-star mapping frame 0 is deliberately empty; visible frames 1 onward
  drive rifts, shards and fracture sparkles. Plate sampling happens together before physics,
  so gate channels do not depend on placement ordering. Gates OR plates within each channel,
  AND the requested channels, and postpone closing while a body overlaps an already-open gate.
- The initial engine test compile was blocked by two wildcard-imported `Game` types in
  `DecodedLevelTransformAssertions`; one explicit `com.openggf.game.Game` import resolves it.
  Production engine code, release policy, API version and signature pins are unchanged.

The four Jupiter scenarios live in the mod's own test tree, with checkout adapter
`TestSonicEchoesFoundation` compiling production/test outputs separately and supplying the
package-scoped engine registration factory. They exercise complete course snapshots and
ROM physics, not a separate puzzle simulator. Coverage includes walking/jumping zero-drift
replay across three death-restored loops, the tutorial positive and solo-negative routes,
drift/death fractures with recording retention, state persistence, and five active echoes.
The supplied ROM was verified as SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

Focused command (Java 25 selected with `JAVA_HOME=<path-to-jdk-25>` and
Maven added to PATH from `<maven-bin>`):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonicEchoesFoundation \
  '-Dsonic2.rom.path=<absolute-path-to-sonic2-rev01.gen>' test
```

Build: `python3 examples/sonic-echoes/build.py --skip-engine` packages
`target/sonic-echoes/sonic-echoes.jar` through the SDK validator. Tests are excluded from it.
The install implementation is present; Phase 1 did not install into the user's mod state.

A production `GameplayCaptureTool` run uses surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY,
audio disabled, width 400, and the mod jar. Input `180 -;40 R;560 -;240 R` deliberately lets
the first loop time out; the next loop walks through the gate. It runs 1020 input rows and
captures four frames starting at 780 with stride 60. Durable capture output belongs under
`<task-dir>/sonic-echoes-phase1/final/`, outside the repository. A capture verifies pixels
and that bounded route; it does not prove other native object contracts or ROM parity.

Handoff constraints: the temporary entry is stock Sonic 2/EHZ1. `RoomLauncher.launch(id)`
selects data before a host requests its stock zone/act; `RoomSession` exposes room, clock,
recordings, fractures, clear echo count/time/shards and commands for the presentation phase.
`SceneContext.startAct` currently accepts only authored mod-zone destinations, while hosted
`SceneGameplay` explicitly excludes mod gameplay content: the lead must choose an explicit
hub-to-stock-patch transition seam in Phase 2. Exit currently returns to the stock title.
The full HUD, progression persistence, sound cues and 12-frame rewind presentation remain
Phase 2 work. Native objects retain their own player participation rules; seesaws have an
extended-sidekick path, but no Hill Top seesaw solution or other 11 rooms were validated here.

Final Phase 1 verification, same uncommitted source on `97eb86f186`: the focused command above
completed at 2026-10-24 13:49:58 Europe/London with **4 tests, 0 failures, 0 errors, 0 skips**.
The final main jar rebuilt successfully with **0 packaging findings**; the final EGL capture
completed at 13:50:49 with 1020 state rows and four PNGs. At its last row the live Sonic is
(600,624), past the gate at x440, while echo 1 remains at (145,652). Python build-script syntax,
`git diff --check` and the AGENTS/CLAUDE mirror check passed. No broad smoke, full suite,
other room/native-object sweep, install, commit or push was performed.

### Phase 2 presentation (2026-10-24)

Presentation implementation remains uncommitted in the main checkout on
`feature/ai-sonic-echoes`, HEAD `97eb86f186b432722024ca419a00b901b38f5ec2`.
The content agent is independently adding room resources/objects in the same tree;
this subsection records presentation coverage, not a twelve-room solvability claim.
No worktree, branch switch, commit, push or mod installation was performed.

- `EchoHub` decorates the stock Sonic 2 `TitleScreenProvider`, following Survivors.
  The initial stock intro stays intact; the mod wordmark appears once interactive.
  Start opens a three-world, two-by-two catalogue. Grid-edge horizontal navigation
  changes worlds. A/C/Start selects through `RoomLauncher` before the ordinary title
  exit reads **both** `startZoneIndex()` and `startActIndex()`. Missing resources
  remain visible as COMING SOON and cannot launch. No engine/API addition was needed.
- Clear confirmation and Q/Back submit the existing controller title request. The
  same module graph preserves progress and the return cursor; title initialization
  reopens the hub immediately on the next card. The stock title's underlying intro
  may still initialize on return; it does not delay the hub UI. Genesis B goes back
  and A/C confirms explicitly: the engine's generic menu policy uses C as back and
  therefore cannot implement this design unchanged. Results own Start through the
  `LevelInputOverlay` boolean, preventing confirmation from toggling host pause.
- Stock-act HUD profile contributions are scoped to authored mod destinations, so
  the patch uses Survivors' `LevelManager.setForceHudSuppressed(true)` seam instead.
  `EchoDisplay` retains ROM sparkle/slot labels and draws `EchoHud` through
  `LevelOverlayCanvas`. Its font is the original bitmap face used by Survivors;
  world art, echo bodies, buttons, barriers and sparkles remain ROM-backed.
  The overlay has a countdown, five coloured slot states, recording-length timeline,
  first-loop keyboard/pad strip, H/LB hint panel, Paradox banner and staged clear card.
- `RoomSession` retains its ordinary non-rewound history owner. ECHO/time-out commits
  once, death does not commit, and the native world holds for twelve controller rows
  before `CourseControl.restore()`. The restored input stream still has zero drift.
  Undo/reset remain immediate. HUD flash is a white screen-space wash, leaving ROM
  palette ownership untouched. Hints are optional UTF-8 `hint` properties, empty by
  default, cached once per room selection; absent hints use the HUD's generic text.
- S2 ROM SFX: `0xCD` blip for menu movement/back; `0xA1` checkpoint for confirmation
  and the shard star; `0xA6` spike-hit for unavailable cards; `0xBC` spindash release
  for the rewind snap; `0xC6` ring scatter for Paradox; `0xCE` ring for clear/par stars.
  Zone music continues across restores. EGL evidence intentionally disables audio;
  these captures certify pixels and cue submission, not recorded sound quality.
- `EchoProgress` uses owner-scoped `progress.txt` under
  `<SavePaths.root()>/mods/sonic-echoes/`, normally `saves/mods/sonic-echoes/`.
  Format 1 has `formatVersion=1` plus `world-room=bestEchoes,bestFrames,stars` lines.
  Echo/time minima are independent; star bits are Clear=1, Par=2, Shards=4 and OR
  across clears. Parsing is transactional. Corrupt/unsupported/unreadable existing
  files remain protected from writes; missing files start writable. Failed atomic
  writes retry every sixty controller/title rows. Unlocks derive from saved stars
  and preceding room clears, including the 24-star final-room gate.
- First visual review found LOOP 2 and Paradox banners overlapping. Paradox now
  suppresses the loop label. A first mid-loop shot also put live/recorded Sonic at
  the same point; the reviewed shot is later, showing both bodies separately.
  The hub's locked cards now include padlocks and dimmed best/par text.

Focused validation (Java 25.0.4.1 confirmed by the leased version probe):

```sh
export PATH="<maven-bin>:$PATH"
export JAVA_HOME=<path-to-jdk-25>
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSonicEchoesFoundation,TestSonicEchoesPresentation \
  '-Dsonic2.rom.path=<absolute-path-to-sonic2-rev01.gen>' test
```

The final run completed at **14:13:18 Europe/London**: **11 tests, 0 failures,
0 errors, 0 skips** (four foundation, seven presentation). Foundation still proves
undisturbed input replay has zero positional drift. Presentation covers save
round-trip/minima/retries/corruption protection, unlock rules, catalogue/zone/act
selection and B/C policy, idle clock/hint/Exit, twelve held frames then restore,
and clear stars/Start ownership/next-card return. The two adapters use temporary
save roots. The existing foundation adapter received only that isolation change;
the only presentation edit in a content-owned source is `RoomDefinition.hint()`.
The content adapter still used the default save root during concurrent work and
was observed writing `saves/mods/sonic-echoes/progress.txt`; it was left untouched.
No broad smoke, full suite or unrelated engine tests were run.

`python3 examples/sonic-echoes/build.py --skip-engine` packages the final presentation
jar at `target/sonic-echoes/sonic-echoes.jar`. Validation passes with one advisory
`NON_API_ENGINE_REFERENCE` warning for `GameServices` in the title decorator,
the same audio lookup used by Survivors. No engine source or Mod API pins changed.
Build-script syntax, whitespace and AGENTS/CLAUDE mirror checks passed.

Reviewed display-free 400x224 captures are in
`<task-dir>/sonic-echoes-phase2-presentation/`: `hub.png`, `hud.png`,
`rewind.png`, `paradox.png`, `clear.png`, and extra `return-hub.png`.
`title-launch/frames/01161.png` also shows the initial room/control strip and full
frozen clock. `title-launch/state.csv` records the actual title-to-LEVEL transition;
root `state.csv` records the room/presentation probe. PNG existence alone was not
used as evidence: the images and state rows were inspected.

The maintained capture tool used the mod jar, no audio, unset DISPLAY/WAYLAND_DISPLAY,
`-Dopenggf.headless.gl=surfaceless-egl`, an isolated `openggf.saveRoot`,
`--title-screen --interactive-pacing --width 400 --game s2 --zone ehz --act 1`,
and authored input `900 -;1 S;60 -;1 S;200 -`. Capture rows 960 and 1161 show hub
then room; the run completed at 14:14:11. A temporary production
`GameplayCaptureSession.applyMod(jar)` probe, admitted through queued `exec:exec`,
used the same EGL/audio isolation for the other shots. It walks 40R/60 released,
commits, captures six rows into rewind, drains the hold, walks 80R, and captures
separate live/cyan bodies. A **declared test perturbation** moves the cyan echo
64 pixels before a native step to trigger the real Paradox detector; that banner
shot is feedback evidence, not an organically solved Paradox-room route. Live
death then restores the loop; 240R clears and 75 held rows expose the full results.
The room clears with one echo at native frame 163 (0:02.71), all three stars.
An Exit request produces a nonblank next-card hub at capture row 702. This bounded
probe verifies module-title recovery, not the unrelated master-hub callback.

### Phase 2 content (2026-10-24)

Content implementation remains uncommitted on `feature/ai-sonic-echoes`, at base
`97eb86f186b432722024ca419a00b901b38f5ec2`. No branch switch, worktree, commit, push,
or installation. Changes owned by this slice are the twelve room resources,
`RoomDefinition`, `EchoObject`, `RoomSolutionsTest`, its checkout adapter
`TestSonicEchoesRooms`, the test-only `RoomCaptureMod` capture entrypoint, and this
subsection. `EchoController` required no change. No edits outside the assigned ownership.
The presentation agent's optional `hint()` accessor is retained.

Every room uses a stock act's ROM terrain, removes the act's objects/rings through the
existing patch, and places only the listed native/mod objects. X windows below are physical
walls; Y values are camera-origin limits. All acts are one-based in this table.

| Room | Zone / act | X window; camera Y | Par | Cap | Clock | Mechanic | Native placements | Shard star feasible |
|---|---|---|---:|---:|---|---|---|---|
| 1-1 Hold the Door | EHZ / 1 | 32–800; 480–560 | 1 | 5 | 10s (600f) | Stopped echo holds door | — | Yes, replayed |
| 1-2 Two Hands | EHZ / 1 | 32–800; 480–560 | 2 | 3 | 10s (600f) | Two held plates, AND gate | — | Yes, replayed |
| 1-3 Relay | EHZ / 1 | 32–800; 480–560 | 1 | 2 | 10s (600f) | Passing latch + held destination plate | — | Yes, replayed |
| 1-4 Escort | EHZ / 1 | 32–800; 480–560 | 2 | 3 | 10s (600f) | Jumping escort defeats guard, two held plates | Buzzer 0x4B | Yes, replayed |
| 2-1 Lift Off | HTZ / 1 | 9856–10624; 1560–1800 | 1 | 2 | 12s (720f) | Echo drops, leaves seesaw, live rides ball return | Seesaw 0x14; ball-free 0x14 ledge | Yes, replayed |
| 2-2 Double Bounce | HTZ / 1 | 9856–10624; 1280–1800 | 2 | 3 | 12s (720f) | Two successive ball-return launches | Two 0x14 launchers; ball-free 0x14 ledge | Yes, replayed |
| 2-3 High Ground | HTZ / 1 | 9856–10624; 1560–1800 | 2 | 3 | 12s (720f) | Launch-only high plate and elevated exit | 0x14 launcher + ball-free 0x14 ledges | Yes, replayed |
| 2-4 Counterweight | HTZ / 1 | 9856–10624; 1560–1800 | 3 | 4 | 12s (720f) | High plate + floor plate below seesaw left end | 0x14 launcher + ball-free 0x14 ledges | Yes, replayed |
| 3-1 Don't Touch | CPZ / 1 | 8960–9728; 800–896 | 2 | 3 | 12s (720f) | Opening a recorded obstacle early causes Paradox | — | Yes, replayed |
| 3-2 Spring Relay | CPZ / 1 | 8960–9728; 800–896 | 2 | 3 | 12s (720f) | Spring launches echo onto high plate | Spring 0x41/2; platform 0x19/5 | Yes, replayed |
| 3-3 Clockwork | CPZ / 1 | 8960–9728; 800–896 | 3 | 4 | 12s (720f) | Three timed passing latches, sealed rift | — | Yes, replayed |
| 3-4 The Last Loop | CPZ / 1 | 8960–9728; 800–896 | 4 | 5 | 12s (720f) | Three held channels + rolling fourth echo clears guard | Spiny 0xA5 | Yes, replayed |

Each resource has exactly three shards, a short `hint`, a complete `solution.N` route,
and a separate `shardSolution.N` route. The ordinary par route earns two shards; the optional
route records one more scout and collects all three in the **same final loop**. Most scouts
visit the wall behind spawn; Counterweight instead sends its extra scout upward earlier to
catch a high shard. Caps leave that extra slot (tutorial retains all five slots for the
foundation contract). Par and shard stars remain independent best-of-room achievements.
This supersedes the earlier tutorial capture's automatic three-shard result: the current
ordinary tutorial yields Clear + Par, and its authored scout route adds the shard star.
The input grammar stays `frames:RLUDJ`, including combined held jump and directions.
`naive.N` stores the Don't Touch negative example. No replay positions drive the engine.

**Native compatibility and rejected approaches.** The floor survey's long HTZ1 tunnel at
x5952–6776 / floor1471 and the HTZ2 upper shelf at x6528–7448 / floor319 were rejected after
real jumps struck low ceilings. The final HTZ1 window near x9856–10624 supports both native
ball-return launches. A falling echo changes Obj14's tilt, then leaves before the returning
ball; live Sonic waits near the centre. This avoids a sacrificial echo and keeps the reference
runs unfractured. Double Bounce asserts two separate native launch events in its final loop.
The high ledges are **native ball-free Obj14 (subtype255)**, not modded seesaw physics.
Obj14_Main (s2.asm:47489-47556) explicitly reads P1/P2 standing states; the ball return tests
both standing bits and calls Obj14_LaunchCharacter (s2.asm:47710-47743). No engine constants
or physics were changed. High Ground's plate is reached by that launch; Counterweight's
additional plate is at (10096,1858), below the left end of the seesaw centred at (10140,1780).

Spring Relay proves the second echo actually receives native upward spring velocity and
stands on the native stationary CPZ platform. Obj41_Up (s2.asm:33898-33930) independently
processes MainCharacter and Sidekick. Escort proves the second echo defeats its native
Buzzer in the final replay; The Last Loop proves the fourth echo defeats its native Spiny
in the final replay (defeat tracking resets before that loop, so the recording run's kill
cannot satisfy it). A proposed `D+R` roll was rejected: Sonic_Roll
(s2.asm:37297-37308) explicitly refuses entry while Left/Right is held. The final script
uses a released-direction `1:D` edge before continuing right.

There is an **engine follow-up, left unchanged here**: TouchResponseProvider's default
render eligibility and AbstractObjectInstance.isOnScreenForTouch still impose an X-camera
eligibility restriction for these enemies. S2's Touch_Loop / Touch_CheckCollision
(s2.asm:85209-85243) scans nonzero collision_flags without a render-flag gate. During
a late live approach, the fourth echo passed the Spiny without killing it and subsequently
fractured; keeping live Sonic close and jumping clear of the guard gives the proven route.
This is bounded certification of the authored placements and trajectories, not a claim that
all offscreen enemy/echo interactions match ROM. No mod-side damage, fake native interaction,
teleport, render-flag override, or engine workaround was added. Lead owns any general engine
accuracy correction for that restriction.

**Proof.** The mod-owned RoomSolutionsTest boots each room through actual CourseControl
capture/restore and supplied-ROM physics. Twelve Jupiter dynamic scenarios cover 24 successful
routes (par + optional shards), two zero-echo negatives per room (straight run + final authored
route), no premature clears, par/cap limits, exactly three shards, 8–20 second clocks, hints,
and no fractured echoes at either clear. Don't Touch additionally proves its naive early
plate visit fractures the recorded runner; waiting for the runner's input to end avoids it.
Clockwork additionally proves a live arrival delayed ten frames misses the latch window.
The native checks require actual Spring-animation launch velocity, two separate live seesaw
launches in Double Bounce, a spring launch for echo 2 in Spring Relay, and final-loop Buzzer /
Spiny defeats. Tests never move a body to force a result.

Final focused verification completed **2026-10-24 14:48:02 Europe/London** on the base SHA
and uncommitted content/presentation sources above: **12 tests, 0 failures, 0 errors,
0 skips**. Maven execution reported 41.477s including compilation; this is not delivery elapsed
time. Java25 was confirmed through the leased version command. Supplied ROM SHA-1 was
rechecked as `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

```sh
export PATH="<maven-bin>:$PATH"
export JAVA_HOME=<path-to-jdk-25>
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSonicEchoesRooms \
  '-Dsonic2.rom.path=<absolute-path-to-sonic2-rev01.gen>' test
```

No broad smoke or full suite was run by this slice; combined delivery validation belongs to
the lead. `RoomDefinition` adds only optional authored-loop accessors, preserving the existing
constructor and hint method. `EchoObject` adds channel-colour markers and a larger, paired-ROM-
sparkle exit silhouette; gate/plate/collection logic is unchanged. The final normal mod jar and
temporary capture jar packaged successfully. The SDK reports one existing presentation-side
NON_API_ENGINE_REFERENCE warning for EchoHub's GameServices use, zero errors.

**Rendered evidence.** All captures use GameplayCaptureTool, `--mod` with the packaged
capture entrypoint, width400, surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY and no audio.
`RoomCaptureMod` selects the authored room before stock boot using `-Dechoes.capture.room`;
it delegates the complete ordinary mod and adds only a selection patch. It is test-only and
excluded from the distributed mod jar. InputLogAuthorTool compiled and round-trip-validated
the `.script` files into `.txt` input logs. Scripts replay the actual reference loops, padding
released input to the room clock and the native 12-frame rewind hold instead of injecting
recordings. The final loop alone is rendered to video; state.csv covers the whole solution.
Outputs are outside the repo under `<task-dir>/sonic-echoes-phase2-content/`:

- `world1-final/capture.mp4`: Two Hands, 1644 state rows, 240 PNG/video frames;
  reviewed `frames/01520.png` (live body, magenta plate holder, open path), final centre (600,624).
- `world2/capture.mp4`: Double Bounce, 2144 state rows, 500 PNG/video frames;
  reviewed `01784.png`, `01964.png`, `02020.png` (both native launches and high ledge),
  `02048.png` (clear); final centre (10397,1487).
- `world3/capture.mp4`: Don't Touch, 2145 state rows, 501 PNG/video frames;
  reviewed `01964.png`, `02010.png` (two plate holders and live gate crossing),
  `02044.png` (clear); final centre (9439,1047).

All three MP4s were fully decoded with ffmpeg and have one video stream, no audio stream,
1200×672 output at60fps. State rows report no final death. These are three-room visual samples,
not visual certification of all twelve rooms or every native placement. The 24 headless routes
supply the complete room-solvability and shard evidence. Capture input files are `1-2.script` /
`1-2.txt`, `2-2.script` / `2-2.txt`, and `3-1.script` / `3-1.txt` in the same task directory.

### Phase 3 lead integration (2026-10-24)

- **Delegation.** Three Codex `gpt-6.1-sol` (medium) workers in the shared checkout: a
  foundation slice, then content and presentation in parallel with disjoint file ownership
  (content: rooms, `RoomDefinition`, `EchoObject`, `EchoController`, room tests;
  presentation: `EchoesMod`, `RoomSession`, hub/HUD/progress classes, README, build). The
  only cross-owner edit was the agreed optional `hint` accessor. No conflicts occurred.
- **Hub seam decision.** Phase 1 found that `SceneContext.startAct` accepts only authored mod
  zones and hosted `SceneGameplay` excludes mod content, so a scene hub cannot launch a
  patched stock act. Rejected: a new engine/Mod API seam for scene → stock-patch launches
  (API surface change for one mod). Chosen: the Sonic Survivors precedent, a
  `TitleScreenProvider` decorator whose `startZoneIndex`/`startActIndex` select the room.
- **Accepted repetition.** Each world reuses one surveyed window per zone (EHZ1 x32–800,
  HTZ1 x9856–10624, CPZ1 x8960–9728); rooms differ by layout, like test chambers. Other
  surveyed HTZ windows were rejected for low ceilings (content evidence above).
- **Lead polish.** Hub and clear-card stars were a bitmap-font asterisk; they are now a
  nine-pixel outlined star sprite in `EchoUi.star`.
- **Engine follow-up, not changed.** The content slice reports that enemy touch eligibility
  (`TouchResponseProvider` render eligibility / `AbstractObjectInstance.isOnScreenForTouch`)
  applies a camera-X restriction that S2's `Touch_Loop` (s2.asm:85209-85243) does not. This is
  a lead, not a confirmed discrepancy: S2 badniks also delete themselves off-screen through
  `MarkObjGone`, so a trace comparison is needed before any change. Authored solutions keep
  the live Sonic close enough that it does not matter.
- **Validation choice.** `run_categories.py --base 97eb86f186` selects every smoke category
  (3756 classes) because `examples/` paths are unclassified. The delivery changes no
  production engine code, Mod API surface or build policy; the only shared edit is one test
  helper import. Focused validation therefore replaces the broad run: the three Echoes
  adapters plus the four test classes that consume `DecodedLevelTransformAssertions`.
