# Competition Mode and S3K Competition Arenas in Multiplayer Time Attack: Design

**Date:** 2026-11-07
**Status:** Design, pre-implementation. It includes the user's scope amendment of the same day
(room game modes, mass start, monitor settings and teleport) and the user's answers to the first
draft's open questions: UNVERIFIED boards and a server-mediated teleport swap.
**Builds on:** [2026-07-04 multiplayer Time Attack design](2026-07-04-multiplayer-time-attack-design.md)
(ghost-only networking; each client simulates and times only its own player).
**Branch:** `feature/ai-time-attack-competition`, at develop `60aa1d658`.

## 1. Overview

Rooms gain a **game mode**:

- **Time Attack** (the default) is today's mode: a timed window, unlimited instant retries, and
  the best time counts.
- **Competition** is new: every player starts at the same scheduled moment, there are no
  restarts, and finish placement decides the round.

Players finish at different wall-clock moments, so placement is decided by **finish frames
counted from each client's own GO frame**. Network latency can therefore never change a
placement, with one deliberate exception: the server-mediated teleport swap (§8). It is the only
place where another client's state enters a simulation, and it is limited to Competition rounds
whose monitor setting can produce a teleport.

S3K Competition's five looping arenas (Azure Lake, Balloon Park, Chrome Gadget, Desert Palace and
Endless Mine) become **Competition-mode tracks**:

- Each client plays one local player in a single full 320×224 view.
- Gameplay is the shipped solo Match Race: 5 laps, the goal-marker rules, death rollback, false
  start and the 9'59"99 cap.
- Lap times, the total and the best lap are measured and ranked.

Normal acts of S2 and S3K can be raced in either mode. In Competition mode the host also picks a
monitor setting.

## 2. Decisions

| # | Decision | Choice |
|---|---|---|
| D1 | Room modes | `TIME_ATTACK` (default) and `COMPETITION`, held in `RoundConfig.mode`. One mode per round. The host's vote pool must be legal for the mode. |
| D2 | Track eligibility | S2 and S3K normal acts: either mode. S3K arenas: **Competition only**. S2 2P VS zones: out of scope. S1: **Time Attack only**, because S1 has no 2P mode to draw Competition rules from. |
| D3 | Track identity | Track keys become `game:zone:act` for acts and **`game:arena:N`** for arenas, where N is the `Comp_ZoneList` menu index (0 ALZ, 1 BPZ, 2 CGZ, 3 DPZ, 4 EMZ). They are parsed in one place, a new `net.protocol.TrackKey`. `RunSpec` gains `TrackKind {ACT, ARENA}`. The kind travels into `RoundConfig`, recordings, ghost headers, profiles and verifier jobs. |
| D4 | Arena rules | The native **solo Match Race**: `Config(MATCH_RACE, arena, items, participantCount=1)` (`S3kCompetitionSession.java:68-77`). This is a ROM path: P2 never pressed on the menu, `loc_AA54 (sonic3k.asm:13612-13621)`. Match Race never calls the record save `sub_37430 (sonic3k.asm:74676-74724)`. As a second guard, the host still receives a no-op save consumer. No cartridge records are written by any hosted run. |
| D5 | Arena monitor setting | The ROM switch `Competition_items`: 0 means the item set is live, `sub_36FAA (sonic3k.asm:74302-74304)`; the menu toggles it, `Obj_Competition_AB00 (sonic3k.asm:13696-13697)`. **Competition items** sets it ON, which is the menu default because RAM is cleared to 0. **Off** sets it OFF. *Normal* and *randomised* do not apply, because arenas have no monitors (decoded layouts hold only the `Obj_2PItem` cluster). |
| D6 | Presentation | One full-screen view of P1. The ROM camera, both native sprite pages (whose culling writes object visibility) and P2's empty view keep running unchanged. The extra 112 rows are a read-only projection: display only. |
| D7 | Timing authority | Each client's own frames. **Clock start** is the GO frame. On arenas that is the native neon GO, `sub_371BE (sonic3k.asm:74437-74457)`. On acts in Competition mode it is the first step after the mass-start release. In Time Attack mode it stays the first input. `timeFrames = finishFrame − clockStartFrame`, the same equation the host and verifier already check. The wire field `firstInputFrame` is renamed `clockStartFrame`. |
| D8 | Lap capture | A lap closes on the step where the session's **highest lap** rises. Raw `lap` is not used, because it falls on backward crossings and deaths. A lap's frame count is the steps since the previous boundary, so the laps partition `[clockStart, finish)` exactly as the ROM's `Timer` does. Best lap is the minimum of the five lap frame counts; the ROM has no best lap. Display uses the ROM per-lap centisecond truncation, `byte_37E52 (sonic3k.asm:74906-…)`, and the total is shown as the ROM shows it: the sum of truncated laps. |
| D9 | Ranking | Placement and boards use **frames**. Frames are displayed as ROM centiseconds. Equal frames are a tie, as `Result.winner` treats equal times (`S3kCompetitionSession.java:133-142`). A ROM display total can differ by up to about 3 frames from frame order (§5.3). |
| D10 | Mass start | The host schedules the start instant S in hub time, using the existing `RoundStart.countdownEndsAtHubMillis` and the client's median NTP-lite offset (`ClientRaceSession.java:330-345`). Clients load during the countdown and hold. Acts release at S. Arenas release at S minus the native countdown lead, so the neon GO lands on S. |
| D11 | Competition attempts | One attempt per participant per round, with no retries. A death follows the game's own rules (§6). Leaving, game over, time over or the ROM cap is a **DNF**. `AttemptStart` must reach the hub within a start tolerance after S (default 2 s), otherwise the run is unranked as a **DNS**. The host pause is disabled; the pause key opens a non-pausing race menu. |
| D12 | Round end | The round ends at the earliest of: (a) everyone finished or out; (b) first accepted finish plus the **cutoff** (host setting, default 60 s; precedent: S2 VS `Loser_Time_Left = $3C3C`, `loc_40FE4 (s2.asm:88079-88106)`); (c) the hard window (the track's time limit plus 30 s). A finish counts only if `timeFrames ≤ winnerFrames + cutoff×60`. Messages keep the existing `FINISH_GRACE_MILLIS` to arrive. |
| D13 | Monitor setting, normal acts | Competition mode only, chosen by the host: NORMAL / RANDOMISED / COMPETITION_ITEMS / OFF, mapped per game in §7. Time Attack mode is always NORMAL. The setting is part of `GameplayRunPolicy`, so objects read it through `services().runPolicy()`. It is bound into the recording hash. |
| D14 | Teleport | A **server-mediated swap** (user decision, §8), in Competition mode only:<br>• The breaker's client requests a swap at the break step. The host picks the nearest running rival by progress, preferring the one ahead, and sends **one `TeleportSwap` command to both clients**.<br>• Each client applies the other's hub-observed state at its next step boundary.<br>• Each application is a recorded input event bound into the recording hash, and the verifier checks it against the host's swap log.<br>• With no eligible partner, the ROM's solo outcome applies: invincibility on S2 acts (`s2.asm:25766-25772`), and the empty-slot shoe swap on arenas.<br>The S3K arena swap item (`loc_3652A`) uses the same mechanism, with its own swapped fields. |
| D15 | Live positions | Estimates for display, never used for placement. They come from a 4-bit **progress ordinal** in ghost-frame byte 5, bits 4-7 (the arena's current lap, or an act's highest star post + 1, clamped to 15). The ordinal is mirrored by the hub into the roster status byte's high nibble. Within an ordinal, progress is wrap-aware distance along the lap (arenas) or x / level width (acts). Byte 6 bits 4-6, reserved today, carry the swap-relevant pose: bit 4 is the secondary collision path, and bits 5-6 are the arena shoe kind (§8.3). Bit 7 stays reserved. The 2026-07-04 design held these bits for a mapping-frame extension; no character needs one today. |
| D16 | Ghost art | Every player in a round runs the same track, so the **local track picks the art family**. In an arena, ghosts draw with the ROM miniature art and mappings, the same family as `S3kMiniatureSprite`. `GhostPose` is unchanged. |
| D17 | Standings visibility | User decision. **Unverified rooms publish the Competition finish order, lap times and best lap, clearly labelled UNVERIFIED.** Verified rooms remain the only trusted boards: `PENDING` → `VERIFIED` as today. This deliberately amends the trust decision of the 2026-07-04 security design, which `RoomHost.java:212-216` implements by never publishing unverified standings. The amendment is scoped to Competition-mode placement boards (§8.7). **Time Attack-mode standings in unverified rooms:** the user's decision covers Competition only. This design recommends the same UNVERIFIED publication for Time Attack boards, for consistency: one room should not label one mode's board and hide the other's. It is flagged for the lead and kept behind the same host switch, so it can land with this work or be held back. |
| D18 | Protocol | Version 9 (`Protocol.java:12`). The strict codec is kept, with no compatibility path; clients, hosts and master upgrade together. |
| D19 | Solo arena practice | **Not in v1.** Arenas are reached only through Competition rooms. A later solo mode would use the native `TIME_ATTACK` mode with mod-owned `.ggfghost` bests, and never cartridge records. |
| D20 | Death time on Competition-mode acts | Executed steps from release, with **no star-post refund** (§5, §6). |
| D21 | S3K act monitors | RANDOMISED and COMPETITION_ITEMS are **unavailable** on S3K acts, because the S3K ROM has no 2P monitor rule (§3.2). |
| D22 | S2 COMPETITION_ITEMS | The user's set, teleport plus Robotnik. It is drawn from `Level_frame_counter` bit 0 on the `Obj2E_Init` pattern (§7). The one-bit draw and the Robotnik member are invented and recorded in known discrepancies. |

Lead decisions 1 and 2 and the retry part of 4 are superseded by D1-D5 and D11. Decision 6 is
covered by D4 and D19. The first draft's open questions Q1-Q6 are closed by D2, D17, D14, D19,
D20 and D21-D22.

## 3. ROM semantics this design preserves

### 3.1 Arenas (S3K)

All `sonic3k.asm` lines are at the skdisasm pin `8b2cf3c4`.

- **Lap window.** `sub_36DFC (sonic3k.asm:74115-74249)` sets the side flag first. A crossing then
  needs `marker_y − $38 ≤ y < marker_y + $14`, `|dx| < $20` and `top_solid_bit == $0C`.
- **Lap events.** Engine owner: `S3kCompetitionSession.observeGoal` (`S3kCompetitionSession.java:379-427`).
  - The first in-window rightward crossing after GO only sets lap = 1.
  - A crossing with `lap > highest` books lap `lap−1`.
  - A backward crossing does `lap--`.
  - A death respawn does `lap--` via `notifyRespawn`, `loc_124C8 (sonic3k.asm:24662-24690)`.
  - A race is the start crossing plus five completed laps. Lap 1 includes the run-up from GO.
- **False start.** A crossing before GO with no laps locks controls for 120 ticks, resets the
  arena and replays the countdown, `loc_36E9C (sonic3k.asm:74176-74205)`. In solo play only the
  local player is reset.
- **Clock.** It is ticked only by the time-display object, so lag and pause never tick,
  `loc_37D7C (sonic3k.asm:74817-74838)`. GO is neon mapping frame 3, `sub_371BE`.
  - The marker runs before the display, so the crossing frame becomes frame 1 of the next lap
    and the laps partition the frames exactly.
  - Engine: `tickGoalTimer` (`S3kCompetitionSession.java:342-350`) and `frameTime`
    (`:575-578`, `sub_B0E4 (sonic3k.asm:14231-14237)`).
- **Cap.** At 9'59"99 the clock is clamped and the player is stopped. This is a DNF, and no record
  is written.
- **Match Race end.** It waits until P1 has stopped, then 120 frames, then the level select,
  `loc_372D0 (sonic3k.asm:74531-74554)`. The hosted run treats `requestResults` as the end of its
  live phase.
- **Solo is first-class.** P2's slot is never spawned, `loc_6B94 (sonic3k.asm:8441-8464)`. Traps
  hit the player who dropped them.
- **Swap item.** `loc_3652A (sonic3k.asm:73480-73525)` swaps P1's and P2's `status_secondary` and
  `speed_shoes_timer` unconditionally. It then reloads both players' `Character_Speeds` and
  carries the speed triple across for whichever player now has shoes.
  - Solo, P1 therefore takes the cleared P2 slot's bytes: it loses its shoes and returns to its
    character speeds.
  - The engine's `swapShoes` returns early when solo (`S3kCompetitionSession.java:460-461`). That
    is a **probable parity gap**: lane B confirms it with a ROM-backed test and fixes it under the
    existing guard before the swap mechanism builds on it.
  - The swap moves no position. It exchanges only shoe status, timer and speeds.

### 3.2 Normal acts

- **S2 2P monitor.** In 2P mode a monitor shows a "?" icon, `Obj26_Init (s2.asm:25574-25577)`. Its
  contents are drawn when it breaks, `Obj2E_Init (s2.asm:25756-25774)`:
  - `(Level_frame_counter & 7) + 1`, giving contents 1-8.
  - `Two_player_items` ≠ 0 ("teleport only", `OptionScreen_Choices (s2.asm:12250-12253)`)
    forces 8.
  - Teleport becomes invincibility (7) unless `Update_HUD_timer + Update_HUD_timer_2P == 2`.
  - The RNG source is the act's own `Level_frame_counter`: deterministic per client, with no
    shared seed.
  - Content table: `Obj2E_Types (s2.asm:25817-25827)`. Entry 0 ("static") jumps to
    `robotnik_monitor`, so there is no inert content.
- **S2 teleport.**
  - `teleport_monitor (s2.asm:26003-26011)` refuses while either player is dead or respawning
    (routine ≥ 6).
  - `swap_players (s2.asm:26016-26153)` exchanges, through `teleport_swap_table (s2.asm:26169-26190)`:
    - each player's whole object RAM from `x_pos` to the end of the SST, which covers position,
      velocities, inertia, status, routine, angle, collision path bits, `status_secondary` and the
      invincibility, shoe and air timers. The object ID, art and mappings stay put.
    - `Sonic_Speeds`/`Tails_Speeds`.
    - every camera block (position, boundaries, bias, delay, scroll flags, difference).
    - the object, ring and bumper manager windows, and the position record buffer.
    - loaded object blocks.
  - It then swaps the per-player latches of plane switchers, pinball switchers, point givers and
    launcher springs. It re-parents both shields and invincibility stars, resets non-rolling radii
    to standing, and swaps the standing bits of any object the players stand on. It lowers both
    cameras by `$180`.
  - It sets `Teleport_timer = $40` and `Teleport_flag`. While the flag is set, `RunObjects` and
    `ScrollHoriz` do nothing (`RunObjects (s2.asm:29805-29807)`, `ScrollHoriz (s2.asm:18041-18044)`),
    so for 64 frames the act is frozen for both players. `Vint_Level (s2.asm:698-712)` counts the
    timer down and flashes the palette white.
- **S2 1-ups.** `sonic_1up (s2.asm:25842-25847)` credits `Life_count`. `tails_1up (s2.asm:25853-25858)`
  credits `Life_count_2P`, so it has no effect on a solo run, whichever character is playing.
- **S3K has no random monitor.** `Obj_Monitor (sonic3k.asm:40487)` branches on `Competition_mode`
  only for Tails' roll, `SolidObject_Monitor_Tails (sonic3k.asm:40628-40635)`. Content 0 also
  gives Eggman, `off_1D87C (sonic3k.asm:40795-40805)`.

## 4. Architecture

### 4.1 Track model and catalog (mod)

`TimeAttackTrackCatalog.Track` gains:
- `kind` (`ACT`/`ARENA`)
- `lapCount` (5 for arenas, 0 for acts; the hard-coded `Competition_total_laps`,
  `Obj_2PGoalMarker (sonic3k.asm:73992-74047)`)
- `modes`, the set of permitted room modes.

Arena entries are built per call, like the existing lists (no static state;
`TimeAttackTrackCatalog.java:36`). They permit Sonic, Tails and Knuckles. Labels come from the ROM
zone names.

`eligibleFor(game, mode)` replaces `eligibleFor(game)`. `VotePool` text admits `arena:N` beside
`z:a`. `VoteTrackPools.rejection` rejects:
- an arena in a Time Attack pool
- a pool with no tracks legal for its mode
- for S1, Competition (D2).

### 4.2 Wire identity (net, engine-free)

`TrackKey` is a new record in `com.openggf.net.protocol` with `parse`/`format` and kind
`ACT|ARENA`. It replaces:
- the regexes `ControlMessage.java:626-631` and `ControlJsonCodec.java:735`
- the key splitting in `HostRoundEngine.java:487-541`
- the fields compared by `RoomHost.trackAllowed` (`RoomHost.java:1601-1611`).

`RoundConfig(gameId, zone, act, windowSeconds, characterPolicy, lockedCharacter)`
(`ControlMessage.java:48-50`) gains:
- `trackKind`
- `mode` (`TIME_ATTACK|COMPETITION`)
- `monitors` (`NORMAL|RANDOM|ITEMS|OFF`)
- `cutoffSeconds`.

These are semantic tokens with no game names in `net`. The full message list is in §9.

### 4.3 Launch (engine)

`RunSpec` (`RunSpec.java:21-37`) gains `TrackKind kind`. For `ARENA`, `zone` is the arena index and
`act` is 0. `GameplayRunPolicy` (`GameplayRunPolicy.java:33-40`) gains `MonitorRule monitors` and a
`DeathRestart.FAST_FROM_CHECKPOINT` value (§6).

| Step | Owner | Change |
|---|---|---|
| Validate | `SceneRunLauncher.start` (`SceneRunLauncher.java:71-106`) | `ARENA` requires a game whose module provides a `HostedArenaProvider`, and that provider's characters. |
| Open | `Engine.launchHostedRunSession` (`Engine.java:2531-2580`) | One branch: `kind == ARENA` → `HostedArenaLaunch.open(spec, …)`, a new helper in `com.openggf`. The helper resolves `rootModule.getGameService(HostedArenaProvider.class)`, opens the arena as a native screen in `GameMode.COMPETITION` and announces level ready. ROM detection, the deterministic module and `noSave` stay shared. |
| Provider | new `com.openggf.game.arena` | `HostedArenaProvider`, `HostedArenaSession` (`step(PlayerInputState)` returns `ArenaStep(PlayerPose, RaceClock)`; `geometry()`; `localPlayer()`; `resultsRequested()`; `close()`) and `HostedArenaRequest(arenaIndex, character, itemsEnabled)`. These contracts are semantic and contain no game names (AGENTS invariant 2). |
| S3K impl | new `game/sonic3k/competition/hosted/*` | Builds an `S3kCompetitionNativeHost`. Production uses the shared-loop constructor (`S3kCompetitionNativeHost.java:124-128`); the verifier uses the explicit-PLC-token constructor (`:131-146`). Both get a scratch ROM-default `S3kCompetitionRecords`, a no-op save consumer and the solo `Views` (§4.6). The helper calls `openArena(new Launch(new Config(MATCH_RACE, arena, items, 1), [character]))` (`:190-224`) after the outer frame, as `S3kCompetitionScreen.afterFrame` does. It steps through `stepArena` (`:366-395`). |
| Step hook | `HostedRunController` | New `admitArenaStep`, `beforeArenaStep` and `afterArenaStep(ArenaStep)`. These reuse the hold, cover and control-hold logic of `admitStep`/`beforeStep`/`afterStep` (`HostedRunController.java:291-386`), but take the pose and race clock from `ArenaStep` instead of `RecordingMainPlayerResolver`. The arena screen calls them, so the hosted hooks for competition never enter GameLoop's LEVEL branch (`GameLoop.java:1754-1785`). GameLoop's existing native-screen dispatch (`GameLoop.java:1376-1380`) already admits the PLC token. GameLoop and Engine.draw changes are limited to delegate calls; both are size-ratcheted under `-Pguards`. |
| Input | arena screen | P1 logical input with **Start cleared** and released per `holdsPlayerInput`. The host owns Start, as in LEVEL (`HostedRunController.java:129-131`). The native pause planner therefore never pauses a hosted race, and the recording and the verifier feed the same masked input. The competition player uses up, down, left, right and jump only (`JUMP=0x70`, `S3kCompetitionPlayer.java:15`), so the 5-bit `RunStep.heldMask` is lossless. |
| End | arena screen | `resultsRequested()` ends the live phase: the run holds on its last frame behind the race overlay until the host leaves. Retry is refused for arenas. |
| Lock-outs | unchanged | Live rewind is off in COMPETITION (`LiveRewindManager.isRewindableMode`). `isolatedAct()` keeps the editor and debug lock-outs. Debug use marks the step `debugAssisted`, which taints the attempt. `armForLaunch` keeps refusing trace, test and playback modes. |

`AttemptReplayHarness` (`AttemptReplayHarness.java:83-160`) branches on the recording's kind:
- `ARENA` opens the same `HostedArenaSession` headless and steps it once per recorded mask.
- `ACT` keeps `RecordingFrameDriver`, under the recorded policy. Competition-mode deaths need the
  checkpoint reload of §6.

Neither branch has a single-player special case in gameplay code. The one-participant case is the
ROM's own `participantCount == 1` path.

### 4.4 Run step and lap capture

`RunStep` (`RunStep.java:20-22`) gains `RaceClock race`, a new `@ModApi` record:
- `running` (arena: `timerRunning(0)`; act: always true)
- `currentLap`
- `completedLaps` (arena: the session's highest lap; act: 0)
- `failure` (`NONE`, `GAME_OVER`, `TIME_OVER`)

`RunStep.actComplete` means **track finished**. For an arena that is
`finished(0) && lap(0) > TOTAL_LAPS`, so the cap counts as `TIME_OVER`, not as a finish
(`S3kCompetitionSession.java:336-350, 503-529`).

The S3K adapter reads `lap`, `highestLap` and `finished` through package-private accessors added
to `S3kCompetitionSession`. There is **no gameplay change** to the session.

`TimeAttackAttempt` (`TimeAttackAttempt.java:21-39`) gets a `TimingRule`:
- `FIRST_INPUT` (Time Attack, today)
- `RELEASE` (acts in Competition mode: frame 0)
- `RACE_CLOCK` (arenas: the first step with `race.running`).

The attempt does the following:
- It records `lapFrames[k] = n − boundary(k−1)` on the step n where `completedLaps` reaches k.
- It records star-post splits as today.
- It finishes on `actComplete`.
- It voids with the failure reason.

For `RELEASE` and `RACE_CLOCK` the attempt **survives level reloads**: `onLevelReady` with cause
`CHECKPOINT_RESPAWN` continues the same attempt (§6).

### 4.5 Mass start (round flow)

1. **Configure.** The owner sends `RoundConfigure` with `mode=COMPETITION`. The host freezes one
   `RaceLaunch` per present participant (`RoomHost.launchFor`, `RoomHost.java:1487-1491`). In
   competition the countdown is host-set, with a 10 s default (today's `COUNTDOWN_MILLIS = 3000`,
   `HostRoundEngine.java:22`, is too short to load an arena), and S is its end.
2. **Load and hold.** Clients load on `RaceLaunch` and hold while the phase is `COUNTDOWN`
   (`MultiplayerRaceCoordinator.java:286-300`).
   - On acts the release is at S, and the mod draws the 3-2-1 countdown (`CountdownCues`).
   - On arenas the release is at S − L/60 s, where L is the native lead from the first step to GO,
     derived from the neon script (`loc_3713A (sonic3k.asm:74391-74436)`: 60 blank frames, then
     3, 2, 1, GO at 60 frames each). The mod draws only "START" until release, and the ROM's own
     neon countdown is the visible 3-2-1-GO.
3. **Start check.** `AttemptStart(attemptId)` is sent at the release step, not at GO, so a native
   false start (which delays GO by about 6 s) is not mistaken for a late start. A start arriving
   after S plus the tolerance is DNS.
4. **Stream.** Ghost streaming, validation and aggregation work as today, with wrap support (§4.7).
5. **Finish.** `AttemptFinish` carries the clock-start, lap and split frames. The first accepted
   finish arms the cutoff, and the host broadcasts `RaceCutoff(endsAtHubMillis)`.
6. **Round end.** At D12, `RoundEnd(finalStandings)` goes out. Placements in verified rooms update
   from `PENDING` to `VERIFIED` through the existing verdict path (`HostRoundEngine.java:234-314`).

**Joins and disconnects:**
- A member who joins after S − 2 s gets no launch. It waits in the lobby level and sees the
  standings panel.
- A member who disconnects before finishing is DNF. A finished result is kept, as today.
- `AttemptReset` in competition means a forfeit (DNF); it never starts a new attempt.
- A player-host disconnect dissolves the room, as today.

**Time Attack rooms are unchanged.**

### 4.6 Full-screen arena view (S3K, presentation only)

The solo `S3kCompetitionNativeHost.Views` is injected through the existing constructor
(`S3kCompetitionNativeHost.java:59-77`). It does three things:

1. It delegates `prepareNativeSpritePages` and `publishNativeSpritePages` unchanged, so the
   112-line native pages and their visibility writeback still run.
2. It draws one `NativeCompetitionRenderer.View(rasterStart 0, rasterHeight 224)`
   (`NativeCompetitionRenderer.java:25-38`), built from:
   - **Camera:** P1's native camera, displayed with a top row of
     `clamp(camY − 56, minY, maxY + 112 − 224)`. This is the native window centred and kept
     inside the designed rows. CGZ wraps its 256-px loop instead of clamping. The native camera is
     never written, because `resolveDeadPlayer` reads P1 `Camera_Y`
     (`S3kCompetitionSession.java:360-370`).
   - **H-scroll:** a new read-only 224-line P1 projection on `S3kCompetitionScroll`.
   - **Sprites:** a new read-only 224-line projection on `S3kCompetitionSpritePages`, with the same
     sources and bucket order, **no visibility writes** and no page mutation. It is produced
     through `NativeCompetitionRenderer.prepareSprites` (`:50-57`).
3. It inserts ghosts into that projection at P1's bucket, through the gameplay ghost registry: the
   same SAT-path hook LEVEL uses (`LevelRenderer.java:2138-2146`).

The slot-0 HUD (running total, lap number, item icon) stays at its native screen position.
P2-band HUD pieces are not presented. The shared `NativeCompetitionRenderer` file is **not
changed**; the S2 parity session also needs it. If ghost translucency cannot pass through SAT
collection, the lane stops and reports. The fallback is an additive overload, flagged to the lead
before anyone touches that file.

### 4.7 Ghosts on wrapping tracks

- **Geometry.** `RunLevelStart` gains `TrackGeometry(wrapWidthPx, wrapHeightPx, lapLineX, lapCount)`.
  The values come from `xWrapMask + 1` and the CGZ vertical ratio (`S3kCompetitionSession.java:255-256`;
  `Comp_ScreenInit`, `Lockon S3/Screen Events.asm:1-25`). They are all 0 for acts.
- **Profile.** `TrackValidationProfile` (`TrackValidationProfile.java:5-6`) gains `wrapWidthPx`,
  `wrapHeightPx` and `lapCount`. `LiveLevelProfileFactory` fills them. The bundled
  `net/track-validation-profiles.json` gains the five `s3k:arena:N` entries through
  `TrackProfileExportTool`.
- **Validator.** `GhostStreamValidator` (`GhostStreamValidator.java:107-124`) measures
  `dx = min(|d|, W − |d|)` (and the same for y on CGZ). `RelevanceClassifier`
  (`RelevanceClassifier.java:16-17`) uses the same wrap distance.
- **Playback.** `RemoteGhostPlayback` unwraps deltas before extrapolating and before its snap
  thresholds, then re-wraps the result.
- **Drawing.** The mod places each ghost at the image nearest to the local player:
  `x' = localX + wrapDelta(ghostX − localX)`. This is the ROM's `(x AND mask) + $400` wrap
  (`loc_107AE (sonic3k.asm:21605-21608)`); the layout's padding columns cover the edge.
- **Art.** `GhostRenderer.slotFor` (`GhostRenderer.java:260-285`) resolves art through a
  `GhostArtSource`. The world session's source comes first; competition installs a miniature
  source with ROM art, mappings, DPLCs and palettes from `S3kCompetitionAssets`. Otherwise the
  level game's `PlayerSpriteArtProvider` is used, as today.
- **Tails.** A Tails ghost draws its body frame only, as 1P ghosts do; the separate tail object
  (`S3kMiniatureTail`) is not streamed.

### 4.8 Live positions and lap counters

Remote lap counters ride the **progress ordinal**, ghost-frame byte 5 bits 4-7, which are free
today (`GhostFrameCodec.java:17-20`):
- They cost no extra packets.
- They are covered by the stream hash and the verifier's ghost hash.
- The hub copies the latest frame's ordinal into `RosterEntry.status` bits 4-7
  (`GhostPackets.java:29-31, 84`), so far players have one too.

A control message per lap was rejected. A broadcast to every room member scales O(N²) at 256
players and adds a host relay. A new roster byte was also rejected, because it changes the binary
layout.

Position order:
1. Finished players by reported `timeFrames`.
2. Then ordinal, descending.
3. Then in-ordinal progress: arenas `((x − lapLineX) mod W) / W`; acts `x / levelWidth`.

The progress strip and the "estimated position" in `MultiplayerHudRenderer`
(`MultiplayerHudRenderer.java:163-171, 206-240`) switch to `ordinal + progress` instead of
`cellX × 64 / levelWidth`. Live positions are labelled as estimates.

### 4.9 Results and standings

| Item | Change |
|---|---|
| `AttemptFinish` | `(attemptId, timeFrames, clockStartFrame, finishFrame, lapFrames[0..5], splitFrames[0..32], inputRecordingSha256Hex, ghostStreamSha256Hex, inputRecordingRef)` |
| Host validation | `HostRoundEngine.onAttemptFinish` (`HostRoundEngine.java:342-382`) additionally checks: the lap count equals the profile's `lapCount`; every lap is at least 1 frame; the laps sum to `timeFrames`; splits strictly increase within `[clockStart, finish]`. In competition, the host also checks one attempt per participant, the start tolerance and the cutoff (D11, D12). |
| Aggregation | Time Attack: best total per participant as today, plus an **independent best lap** per participant, which may come from a different attempt (§5.3 of the research). Competition: placement by `timeFrames`, plus the round's best-lap award. Non-improving totals are still checked for a better lap. |
| Publication | `RoomHost` replaces the fixed `round.setPublishUnverifiedStandings(false)` (`RoomHost.java:212-216`) with a mode-aware rule. Verified rooms publish as today. Unverified Competition rounds publish placement, lap and best-lap boards with `verifyState = UNVERIFIED`. Unverified Time Attack rounds do the same under the recommended consistency switch (D17), which defaults to on and is flagged for the lead. Trust accrual and operator spot checks keep consuming unverified claims exactly as today; publication changes only what peers see. |
| `StandingsRow` | `(slot, displayName, character, bestTimeFrames, rank, verifyState, outcome, bestLapFrames, lapFrames)`. `verifyState` gains `UNVERIFIED` beside `PENDING`/`VERIFIED`. `outcome` is `FINISHED`, `DNF` or `DNS`. A DNF row carries `bestTimeFrames = 0` and is ranked after all finishers; the codec rule `bestTimeFrames > 0` (`ControlJsonCodec.java:866-879`) is relaxed for non-finished outcomes only. `StandingsPageRequest` gains `board` (`TOTAL`, `BEST_LAP`); `RankUpdate` gains `board`. |
| Server | `racing/server`: `MasterServer.java:143` validates pools per mode and kind. `RelayRoomManager.knownVoteTrack` (`RelayRoomManager.java:832-840`) uses `TrackKey`. `BundledProfileSource` is keyed by `TrackKey`. `VerifierWorker.decide` compares kind, mode, monitors, clock start, lap and split frames, besides time and ghost hash. RELAY rooms run the same `RoomHost`/`HostRoundEngine`, so one change covers LAN, direct and relay. |

### 4.10 Recordings and ghost files

- **Descriptor.** `AttemptStartDescriptor` becomes `(gameId, trackKind, zone, act, character,
  fingerprint, mode, monitors, deathRestart, swapEnabled)`. `swapEnabled` is frozen from the
  `RaceLaunch` (§8.2). `AttemptInputRecording` writes a format byte, version 2, so the SHA-256
  binds them. The decoder stays canonical-checked. The verifier's
  `recordedTrack.equals(job.trackKey())` includes the kind.
- **Race events.** Recording v2 appends a canonical, step-ordered event list:
  `(step u32, kind u8, swapId u32, payload)`, at most 64 events.
  - Kinds are `SWAP_APPLY` (with the full `SwapTarget`, §8.3) and `SWAP_FALLBACK`.
  - An event applies **before** the recorded step executes, both live and on replay.
  - The list is inside the hashed encoding, so `inputRecordingSha256Hex` binds every externally
    supplied event.
- **Frame cap.** `MAX_FRAMES` rises to **37,800** (10 min 30 s) in `AttemptInputRecording`,
  `GhostFileCodec` and `TimeAttackRuntime.java:442`. An arena attempt spans the native lead (about
  240 frames), up to 36,000 clock frames and the 120-frame finish wait. A competition act may add
  death animations. The mod enforces the ROM cap; the frame limit is only a bound.
- **Ghost files.** `.ggfghost` format v2 adds `trackKind`, `mode` and `lapFrames`, and keeps the v1
  reader (`GhostFileCodec.java:55-160`). Competition-mode runs write no `.ggfghost` bests: a mass
  start with deaths is not a Time Attack best. Arenas write no ghost files in v1 (D19).

## 5. Timing details

1. **Equation.** `clockStartFrame` and `finishFrame` are step indices from the spawn step 0.
   `timeFrames = finishFrame − clockStartFrame = Σ lapFrames`.
2. **Arena frames match the ROM.** The GO step is lap-1 frame 1, and the finish crossing step is
   frame 1 of the non-existent lap 6, so the arena total equals the ROM's frame partition. A
   ROM-gated test asserts, per lap, `frameTime(lapFrames[k]) == session lapTimes[k]`.
3. **Display can disagree with frame order.** The ROM truncates each lap, so the displayed total
   is up to 5 cs below the exact total. Two results 1-3 frames apart can display in the opposite
   order. Ranking stays on frames (user rule), and the results screen shows frames on its details
   page. A ROM-display tie with different frames is not a tie.
4. **Acts in Competition mode** count executed steps from release. Death reload frames produce no
   steps and are not counted, which matches the ROM pausing `Timer` across the reload. The star-post
   time refund is **not** applied to the competition clock (D20, §6). The 64 frozen steps of an S2
   teleport (§3.2) are executed steps, so they count for both swapped players.

## 6. Death without restarts

| Track | Rule |
|---|---|
| S3K arenas | ROM: respawn just left of the line, lap rolled back by one, rings cleared, clock running, no lives (`loc_124C8`). The attempt continues. |
| S2 and S3K acts, Competition mode | New `DeathRestart.FAST_FROM_CHECKPOINT`, which keeps the shipped consequences: a life is lost; the respawn is at the last star post with the ROM star-post restore (rings 0, act `Timer` restored to the star-post value, as shipped); 0 lives is GAME OVER, which is a DNF; the ROM act time over is a DNF. The reload is immediate, with no fade or title card, like today's `FAST_FROM_START` (`GameLoopContinueCoordinator.java:99-128`). It goes through a new `LevelManager.restartCurrentLevelFromCheckpointAfterDeath`. That single owner is used by both GameLoop and `AttemptReplayHarness`, so the verifier replays deaths. `RunLevelStart.deathRestart` becomes `LevelStartCause {LAUNCH, RETRY, FAST_DEATH_RESTART, CHECKPOINT_RESPAWN}`. |
| Time Attack mode | Unchanged: `FAST_FROM_START`, and a death voids the attempt. |

The fast reload replaces a fade and title card that change no gameplay state. Without it, recorded
steps and verifier frames would misalign across title-card frames, which `RecordingFrameDriver`
steps (`RecordingFrameDriver.java:221-265`) but the live run does not.

## 7. Monitor settings per game

| Setting | S2 normal acts | S3K normal acts | S3K arenas |
|---|---|---|---|
| NORMAL (default for acts) | Shipped 1P monitors. | Shipped 1P monitors. | Not offered. |
| RANDOMISED | `Obj2E_Init`'s 2P rule on the 1P layout: "?" icon, contents `(Level_frame_counter & 7) + 1`. Content 8 (teleport) uses the teleport guard (below), then the server swap (§8). `tails_1up` credits the absent P2. | **Unavailable** (D21): the S3K ROM has no randomiser (§3.2). | Not offered. |
| COMPETITION_ITEMS | The user's set (D22): content = `Level_frame_counter & 1 ? 8 (teleport) : 3 (Robotnik)`, then the teleport guard.<br>• Nearest ROM basis: `Obj2E_Init` draws from `Level_frame_counter` (`s2.asm:25759-25761`), and "teleport only" overrides the draw with a fixed content (`s2.asm:25762-25764`).<br>• Invented: the one-bit draw and Robotnik's membership. These are recorded in known discrepancies. | **Unavailable** (D21). | `Competition_items = 0`: the ROM item set (speed, slow, swap, ring, rolling Robotnik, slip trap, spring trap). The swap item uses §8. This is the default for arenas. |
| OFF | The monitor deletes itself on init without marking respawn. There is no inert ROM content, because entry 0 is Robotnik. Not a ROM state; recorded in known discrepancies. | Same as S2 (entry 0 is Eggman). | `Competition_items = $FF`: boxes stay inert in anim 7 (`Obj_2PItem (sonic3k.asm:73321-…)`). |

**Teleport guard.** The ROM keeps content 8 only while `Update_HUD_timer + Update_HUD_timer_2P == 2`,
and otherwise turns it into 7 (`s2.asm:25766-25772`). The networked model reads its two inputs as:
- the local player's timer;
- the frozen per-attempt `swapEnabled` from the descriptor, which stands in for the partner's
  timer.

`swapEnabled` is true when the round is Competition mode, the monitor setting can produce a
teleport, and the `RaceLaunch` froze at least two participants. A local constant keeps content
selection deterministic and replayable. Whether a partner exists at break time is decided later,
by the host (§8.2).

The per-game monitor objects read `services().runPolicy().monitors()`. Shared code holds no game
checks. Swap-capable settings (RANDOMISED and COMPETITION_ITEMS) are offered only on S2
**signpost** acts, so a swap can never land a player inside a boss arena's camera lock. S1 has no
Competition mode (D2). Time Attack mode always uses NORMAL monitors, so a teleport there is the
shipped 1P monitor content and no swap exists.

## 8. Teleport: server-mediated swap (Competition mode only)

This is the user's decision. It applies to S2 signpost acts with RANDOMISED or COMPETITION_ITEMS,
and to arenas with items on (the `loc_3652A` swap item). Time Attack mode never swaps.

### 8.1 Flow

1. **Break.** On the requester's step b, the ROM routine runs: `teleport_monitor` on S2, or
   `loc_3652A` on an arena.
   - With `swapEnabled`, the engine does **not** run the local swap. It raises
     `RunStep.signal = SWAP_REQUEST`.
   - The ROM's own refusal still applies first: on S2, if the requester is dead or respawning, no
     request is made, exactly as `teleport_monitor` returns.
   - The mod sends `TeleportRequest(attemptId, requestFrame = b)`.
   - The requester keeps playing; nothing changes locally until a command arrives.
2. **Select.** The host waits up to 250 ms for the requester's stream to reach frame b, then
   picks a partner (§8.2). It sends **one** `TeleportSwap(swapId, roundId, slotA, slotB, targetA,
   targetB, sentAtFrameA, sentAtFrameB)` to **both** clients:
   - `targetX` is player X's hub-observed state.
   - `sentAtFrameX` is the newest validated frame index of X's stream at send time.
   - The host logs the command (§8.5).
   - With no partner, the host sends `TeleportNoPartner(attemptId, requestFrame)` to the requester
     only.
3. **Apply.** Each client applies the **other** player's target before its next executed step n,
   through the new `RunHandle.queueRaceEvent`.
   - First it runs the local admissibility check (§8.4).
   - If the check passes, it applies `SWAP_APPLY`. If it fails, it applies `SWAP_FALLBACK`: the
     ROM solo outcome, as for `TeleportNoPartner`.
   - The client records `(n, kind, swapId, target)` in the recording and reports
     `TeleportOutcome(swapId, applyFrame = n, outcome)` to the host.
4. **Atomicity.** The two applications are **not atomic**. Each client checks and applies on its
   own timeline, so one side can apply while the other falls back. The host narrows this by
   checking eligibility before it sends. The remaining asymmetry is accepted and documented.

### 8.2 Partner selection

**Candidates** are participants who meet all of the following:
- `RUNNING`: started, and not finished, DNF or DNS;
- connected, with a stream fresh within 500 ms;
- not hurt in their latest frame, the hub's nearest proxy for "dead or respawning";
- not in a pending swap;
- not within 64 frames (`Teleport_timer = $40`) of an applied swap, measured in their own stream
  frames.

**Rule:** the nearest candidate **ahead** of the requester by race progress (ordinal, then
in-ordinal progress, §4.8). If no one is ahead, take the nearest behind. Ties go to the lower slot.

Why this rule:
- The ROM swap is a 2P duel with your only rival. In a large room your rival is your neighbour in
  the race.
- Preferring the player ahead keeps the monitor's catch-up role.
- "Nearest" bounds the disruption: a last-place player in a 256-player room cannot hurl the
  leader to the back.
- A random choice would make the outcome chaotic and turn the item into griefing at scale.

The rule is deterministic over hub state, and the log records the inputs it used.

**Edge cases:**
- **No eligible partner:** `TeleportNoPartner`, and the requester applies the ROM solo outcome:
  - S2: invincibility, the guard's own fallback (`s2.asm:25766-25772`). The icon has already risen
    as teleport, so only the effect's timing is new. This is recorded in known discrepancies.
  - Arenas: the ROM's empty-slot swap (§3.1); P1 loses its shoes.
- **Target finishes, goes DNF or disconnects after selection:** the command is already sent. The
  target's client, if still connected, fails admissibility (it is finished or dead) and falls
  back, which on S2 and arenas means its state is unchanged. The requester still moves to the
  target's logged state.
- **Concurrent requests:** the host serialises per room in arrival order. A player is in at most
  one swap. A request from a player already chosen as someone else's partner is evaluated after
  that swap, against fresh candidates.
- **Chains:** A↔B then B↔C are allowed once B's 64-frame cooldown has passed.
- **Request cap:** at most `profile.maxSwapSources` requests per attempt. This is the number of
  monitors in the act's object layout, or 2 × (laps + 1) item pickups on an arena; the bundled
  profile carries it. Requests over the cap, requests outside RUNNING, and requests in rounds
  whose settings cannot teleport are rejected and counted as violations.

### 8.3 What is swapped, and what the command carries

A ghost stream carries only pose, so the host can relay only each player's **last streamed
state**. `SwapTarget` carries:

| Field | Source |
|---|---|
| `centreX`, `centreY` | Ghost frame at `sentAtFrame` (frame b for the requester) |
| `xVel`, `yVel` | `(pos[f] − pos[f−1]) × 256`, from the hub's two newest validated frames |
| `facingLeft` | Ghost h-flip |
| `secondaryPath` | Ghost byte 6 bit 4: collision path `$E/$F` rather than `$C/$D` |
| `priorityBucket`, `highPriority` | Ghost layer byte |
| `shoeKind` (arenas) | Ghost byte 6 bits 5-6: none, fast (`$C00/$18/$80`), slow (`$300/$8/$20`) |
| `shoeTimer` (arenas) | `75 − (sentAtFrame − shoe onset frame) / 8`, from the hub's stream history; the ROM timer counts in 8-frame units (`(10*60)/8`, `sonic3k.asm:73432`) |
| `progress` | Ghost byte 5 ordinal; display only |

**S2 reconstruction.** The receiver applies, in place of `swap_players`:
- Its centre and path are set to the target's. Velocity becomes the target's, with inertia equal
  to `xVel`.
- It is made airborne and not rolling, with standing radii (the ROM's own non-rolling reset) and
  angle 0. `on_object` is cleared, so it lands through ordinary physics.
- Its camera snaps to centre on the target, through the same placement owner as the checkpoint
  respawn. The ROM's `$180` camera drop and the shared `Camera_Max_Y_Pos_Changing` are subsumed by
  this snap.
- The object and ring windows are re-initialised at the new camera, through the same owner that
  places them at a checkpoint load.
- Plane-switcher and other per-player object latches are re-derived for the local player from its
  new position, the way each object's init derives them.
- The local `Teleport_timer = $40` and `Teleport_flag` freeze and flash follow (§3.2), and the
  ROM's REV01 reset of `prev_anim`/`mapping_frame` applies.

**Not swapped**, because no stream carries them: `status_secondary` (shield, invincibility and
shoes stay with each player), the air timer, rings, lives and score. The ROM does not swap rings,
lives or score either: `Ring_count` and `Life_count` are not in the swap table.

**Arenas.** The receiver applies the `loc_3652A` field set with the partner's values:
- `status_secondary` speed-shoe bit from `shoeKind`;
- `speed_shoes_timer` from `shoeTimer`;
- `Character_Speeds` reload, then the partner's shoe triple if it now has shoes.

Position is untouched, as in the ROM. The ROM item's own animation and collision changes on the
box (`sonic3k.asm:73526-…`) run locally at the break, as today.

### 8.4 Admissibility (deterministic, recomputed on replay)

A received target is applied only if all of these hold:
- the local player is alive and not respawning (routine < 6), not object-controlled and not hurt;
- the target lies inside the layout bounds and inside the local camera boundaries, extended by
  one screen;
- a floor probe and both wall probes from the target centre, at standing radii, report no
  penetration deeper than the radius;
- (S2) the target is not inside a level-event region the local events have not reached. The
  signpost-act restriction (§7) keeps this to the act's ordinary camera range.

Otherwise the client applies `SWAP_FALLBACK` and records it. The check uses only local
simulation, so the verifier's replay reaches the same verdict.

### 8.5 Determinism and verification

- **Recorded events.** The swap is an externally supplied event. `SWAP_APPLY` and `SWAP_FALLBACK`
  are recorded at the exact step they precede and bound into the recording hash (§4.10). Replay
  applies each one before the same step.
- **Host swap log.** For every request, the host logs:
  - the decision: partner, or none;
  - the candidate set and progress values it used;
  - both targets and `sentAtFrame` values;
  - the hub time;
  - both `TeleportOutcome`s.

  In verified rooms the room host is the master (RELAY), so the log is server-side. It is attached
  to every verification job of the two attempts involved.
- **Verifier checks:**
  1. The replay raises `SWAP_REQUEST` at exactly the recorded request steps, and at no others.
  2. Every recorded `SWAP_APPLY` matches a logged `TeleportSwap` for this attempt: swapId, slots
     and payload. Every `SWAP_FALLBACK` matches a logged `TeleportNoPartner`, or a `TeleportSwap`
     whose replayed admissibility fails.
  3. Each apply step lies in `[sentAtFrame(self), sentAtFrame(self) + 180]` of the client's own
     stream.
  4. The replayed admissibility verdict equals the recorded outcome and the logged
     `TeleportOutcome`.
- **Validator.** `GhostStreamValidator` admits exactly one discontinuity per logged swap: to the
  commanded target, ±maxSpeed, within the apply window. Any other jump stays a violation.

### 8.6 Plausibility checks on the host

- Before using a stream frame as a target, the host requires it to be validated: pacing, speed,
  bounds and wrap (§4.7).
- The requester's frame-b pose must lie within `maxSpeed × (b − last validated frame)` of its last
  validated frame.
- A `TeleportOutcome` whose apply frame is outside the window, or that names an unknown swapId,
  is a violation.

### 8.7 Trust and security implications

This section amends the 2026-07-04 designs. The amendments are scoped to Competition mode.

- **Latency now affects outcomes.** Which state a swap delivers, and the step at which each side
  applies it, depend on packet timing. This is the single exception to "latency never affects
  placement" (§1). It exists only in Competition rounds whose monitor setting can teleport.
- **Modified clients can lie about positions.** A client could stream fabricated but plausible
  poses, and a partner who swaps with it receives that state.
  - The host bounds the lie to what passes stream validation.
  - In verified rooms the liar's own verification fails, because its ghost hash will not match
    its replay.
  - The swaps it sourced are then marked `TAINTED` in the log. Placements of honest players who
    received a tainted swap stand: they played the logged command faithfully. Their rows are
    annotated.
  - In unverified rooms nothing can detect the lie; the boards say UNVERIFIED.
- **Request spam is bounded** by the per-attempt cap, the cooldown and violation counting. In
  verified rooms, a request the replay does not reproduce fails the requester's verification.
- **UNVERIFIED boards (D17).** Peers now see unverified finish orders, lap times and best laps. A
  modified client can claim any time on them. The label and the separate VERIFIED boards are the
  defence, as the original v1 design intended before verified rooms existed. Trust accrual is
  unchanged.
- **Unchanged guarantees:**
  - No ROM bytes cross the wire.
  - Time Attack rounds never carry external events.
  - Swaps exist only where the monitor setting allows them.
  - Every external event is replay-checked against a server log in verified rooms.

## 9. Protocol 9 and Mod API changes

All the types below are `@ModApi` and pinned in `src/test/resources/mods/mod-api-signatures-0.7.txt`.
The 0.7 API is an unpublished candidate (`mod-api-release-policy.properties`: `currentStatus=candidate`,
`publishedBaselines=` empty), so in-place changes are allowed. AGENTS rule 8 requires the
descriptor, the `ModApiVersion` candidate Javadoc and the pins to change **together**, in one
commit.

| Type | Change |
|---|---|
| `Protocol` | `VERSION = 9`, with a history comment. |
| `ControlMessage.RoundConfig` | `+ trackKind, mode, monitors, cutoffSeconds` |
| `ControlMessage.RoomDescriptor`, `RoomSummary`, `RoomTrackUpdate`, `QuickChatBroadcast` | `+ trackKind` (and `mode` on the first two) |
| `ControlMessage.AttemptStart` | Unchanged; in competition it is sent at release. |
| `ControlMessage.AttemptFinish` | `firstInputFrame` renamed `clockStartFrame`; `+ lapFrames, splitFrames` |
| `ControlMessage.AttemptReset` | `+ reason` (`RETRY`, `LEFT`, `GAME_OVER`, `TIME_OVER`, `DISCONNECT`) |
| `ControlMessage.StandingsRow`, `StandingsPageRequest`, `StandingsPage`, `RankUpdate` | §4.9 |
| `ControlMessage.RaceCutoff` (new, host → client) | `(roundId, endsAtHubMillis)` |
| `ControlMessage.TeleportRequest` (new, client → host) | `(attemptId, requestFrame)` |
| `ControlMessage.TeleportSwap` (new, host → both clients) | `(swapId, roundId, slotA, slotB, SwapTarget targetA, SwapTarget targetB, sentAtFrameA, sentAtFrameB)` |
| `ControlMessage.TeleportNoPartner` (new, host → requester) | `(attemptId, requestFrame)` |
| `ControlMessage.TeleportOutcome` (new, client → host) | `(swapId, applyFrame, outcome)`, with outcome `APPLIED` or `FALLBACK` |
| `ControlMessage.SwapTarget` (new record) | §8.3 fields. The codec bounds every field: positions u16, velocities ±`$1000`, `shoeTimer` 0-75, enums closed. |
| `RaceLaunch` | `+ swapEnabled` (frozen; §7) |
| `TrackKey` (new) | §4.2 |
| `HostRoundEngine` | `RoundMode`; competition attempt rules; best-lap board; `FinishOutcome + outcome` |
| `TrackValidationProfile` | `+ wrapWidthPx, wrapHeightPx, lapCount, maxSwapSources` |
| `GhostFrame`, `GhostFrameCodec` | `+ progress` (0-15) in byte 5 bits 4-7; `+ secondaryPath` (byte 6 bit 4) and `shoeKind` (byte 6 bits 5-6) |
| `GhostPackets.RosterEntry` | Status high nibble = progress; size unchanged |
| `RunSpec` | `+ TrackKind kind` |
| `TrackKind`, `RaceClock`, `TrackGeometry`, `LevelStartCause`, `MonitorRule` (new) | §4.3-§4.7 |
| `RunStep` | `+ RaceClock race`; `+ RaceSignal signal` (`NONE`, `SWAP_REQUEST`); `+ RaceEventResult applied` (the event applied before this step, or none) |
| `RunHandle` | `+ queueRaceEvent(RaceEvent)`, applied before the next executed step |
| `RaceEvent`, `RaceSignal`, `RaceEventResult` (new) | `RaceEvent` is `SwapApply(swapId, SwapTarget)` or `SwapFallback(swapId)`, using the mods.run copy of the §8.3 target |
| `RunLevelStart` | `boolean deathRestart` becomes `LevelStartCause cause`; `+ TrackGeometry geometry` |
| `GameplayRunPolicy` | `+ MonitorRule monitors`; `DeathRestart.FAST_FROM_CHECKPOINT`; `competition()` factory |
| `PlayerPose.toGhostFrame` | `+ progress` |

Each new message goes into the codec direction sets, `validate*`, `TestControlCodec*` and
`TestProtocolFuzzing`. The swap messages get adversarial cases in `TestRoomHostAdversarial`:
- forged or reused swapIds;
- outcomes for another slot's swap;
- requests over the cap, while pending, in Time Attack, or with a non-teleporting setting;
- out-of-range `SwapTarget` fields;
- apply frames outside the window. The `@ModApi` pre-commit hook couples pins to declarations, and edits to
the hook itself need explicit user approval; none are planned.

## 10. Rewind and state

- Live rewind stays off for every hosted competition run: COMPETITION is not rewindable, and
  `isolatedAct()` disables rewind for acts.
- New engine state:
  - the arena step adapter's `lastCompletedLaps`, which is derived from rewindable session state
    each step and needs no adapter;
  - the solo `Views`' presentation scratch, which is transient (`@RewindTransient` if it is held on
    a captured owner).
- The race-event queue in `HostedRunController` holds at most one event, consumed before the next
  step. It is transient run-host state, never captured.
- The applied swap writes only existing player, camera and object state, which the existing
  adapters already own. The S2 teleport freeze uses the game's existing `Teleport_timer`/`Teleport_flag`
  owners. If those are not yet modelled, lane B adds them with `RewindSnapshottable` coverage.
- The session's `RewindSnapshottable` state (`S3kCompetitionSession.java:584-618`) is unchanged.
- Mod attempt and race state is host-side and not rewindable. If rewind is ever allowed, the new
  fields must join the existing adapters; the destructive-restore tests in
  `TestS3kCompetitionRosterRestore` remain the guard.

## 11. GUI (Opus lane)

This lane follows `.agents/skills/ui-design-language/SKILL.md`:
- Mod pages echo `MenuChrome` through `openggf.timeattack.ui.MenuPage`.
- The in-race HUD stays in the right column, at most 140 px wide and sized to content.
- Rows are abbreviated, with one status colour per plate, clear of the game HUD (and of the native
  arena HUD in the top-left band) and of the lives row.

| Surface | File(s) | Change |
|---|---|---|
| Room create / host settings | `TimeAttackMenuState.java` | MODE row (TIME ATTACK / COMPETITION); MONITORS row (only the legal values for the pool, §7); CUTOFF row (30/60/90/120 s); START IN row (5/10/15 s). Rows the mode does not use are hidden. |
| Track picker and vote pools | `TimeAttackMenuState.java`, `VotePoolEditorView.java` | An ARENAS group for S3K, with the ROM zone names, shown only for Competition. Kind and mode badges appear on the focused row only. |
| Server browser | `mp/ServerBrowserView.java` | A mode tag and the track label from `TrackKey`. |
| Lobby and mass start | `mp/RaceLobbyView.java`, `mp/RaceLobbyScreen.java` | A "START 0:08" plate during the countdown and a "LOADING" chip until held. On acts, the existing 3-2-1 cue. On arenas, only "START", with the native neon countdown as the visible one. A late-join notice. |
| In-race HUD | `mp/MultiplayerHudRenderer.java`, `TimeAttackHud.java`, `mp/MinimapLayout.java` | `P4/37` estimated position (dim); arenas `LAP 3/5 0'21"50` plus `BEST 0'19"83` (the native HUD already shows the total and lap number, so they are not duplicated); acts: star-post split as today; `CUT 0:42` amber plate once the cutoff is armed; lap-aware progress strip. No delta row in Competition, which has no reference ghost. |
| Race menu (non-pausing) | `mp/RaceLobbyScreen.java` | LEAVE (forfeit, confirm) and CHAT. It never pauses the run. |
| Finish wait / results | `mp/RaceLobbyView.java` (podium/results page) | Live finish order while waiting. A placement table: rank, name, character, total, outcome. Arenas add a best-lap column and a details page with all five lap splits per row. DNF and DNS rows last. **UNVERIFIED** (D17): an amber `UNVERIFIED` tag in the results and placement header, and on the in-race standings plate, in every unverified room. Rows use the existing verify-state column (`PENDING`/`VERIFIED`/`UNVERIFIED`). Tainted-swap rows (§8.7) get a dim marker that the details page explains. |
| Swap feedback | `mp/MultiplayerHudRenderer.java` | On an applied swap, a one-row cyan plate `SWAP ⇄ NAME` for 90 frames in the top-right lane, beside the ROM's own white flash and freeze. On fallback, `SWAP: NONE` and the ROM's invincibility. On the partner's screen, `SWAP ⇄ NAME` with the requester's name. The plate never covers the native arena HUD. |

## 12. Workstreams

There are four lanes with disjoint file ownership. Each lane works in its own worktree under
`.worktrees/`, branched from the integration branch, and **never builds in another lane's
worktree**. Commits carry the seven AGENTS trailers. Use `--normal` after test edits, because the
lean lane can run out of memory during test compile. Run every test command through
`maven_queue.py`.

**Sequence:**
1. **A1** lands first: contracts only, compiles green.
2. Then **A2**, **B** and **C** in parallel. **D** starts when C's state-contract commit
   (**C1**) lands.
3. **A3** follows B and C.
4. The lead runs the integration checks (§13.3).

### Lane A: contracts, protocol, hub and server (Sol)

- **Objective.**
  - **A1:** add every §9 type and field with neutral defaults at existing call sites, so the tree
    compiles; update the pins, `ModApiVersion` and the descriptor together; bump the protocol.
  - **A2:** implement:
    - `TrackKey` parsing everywhere, the competition round rules (D11, D12), lap/split validation
      and the best-lap board;
    - mode-aware UNVERIFIED publication in `RoomHost`/`HostRoundEngine` (D17, §4.9), with the
      Time Attack consistency switch;
    - `RaceCutoff`, the wrap-aware validator, relevance and playback, and the roster ordinal;
    - the **swap host side**: `TeleportRequest` handling, partner selection (§8.2), the
      `TeleportSwap`/`TeleportNoPartner`/`TeleportOutcome` flow, the swap log and taint marking,
      plausibility checks (§8.6), and the validator's logged-discontinuity admission;
    - master and relay pool rules, and `BundledProfileSource` (including `maxSwapSources`).
  - **A3:** extend `AttemptReplayHarness`:
    - arena and checkpoint-respawn branches;
    - race events applied before their recorded steps.

    Extend `VerifierWorker`: compare the §4.9 fields and apply the §8.5 swap-log checks; attach
    swap logs to verification jobs. Export the five arena profiles and the act `maxSwapSources`
    with `TrackProfileExportTool` (ROM).
- **Owns:**
  - `src/main/java/com/openggf/net/**`
  - `src/main/java/com/openggf/mods/run/**`
  - `src/main/java/com/openggf/game/session/GameplayRunPolicy.java` (declarations only)
  - `src/test/resources/mods/mod-api-signatures-0.7.txt`
  - `src/main/java/com/openggf/mods/ModApiVersion.java` and the Mod API descriptor
  - `racing/server/**`
  - `src/test/java/com/openggf/net/**`
  - `racing/README.md`
  - `docs/architecture/research/multiplayer-time-attack.md`
  - In A1 only: one-line constructor migrations in other lanes' files.
- **Inputs.** A3 needs B's `HostedArenaSession` and `restartCurrentLevelFromCheckpointAfterDeath`,
  and C's descriptor and `TimeAttackAttempt` timing rules.
- **Checks:**
  - `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestNoProvisionalModApiShims,TestNetIsolationRules" test`
  - `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=com/openggf/net/**/Test*" test`
  - `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=openggf/racing/**/Test*" test`
    (`TestAttemptReplayHarness`, `TestVerifierWorker`, `TestBundledProfileSource`,
    `TestRelayRoomManager`, `TestVerifiedRoomEndToEnd`)
  - New: `TestTrackKey`, `TestHostRoundEngineCompetition` (mass start, single attempt, start
    tolerance, cutoff in frames and wall time, DNF and DNS ordering, best-lap board),
    `TestUnverifiedPublication` (UNVERIFIED rows in both modes, switch off, trust accrual
    unchanged), `TestTeleportSwapHost` (selection rule, ties, cooldown, concurrent requests and
    chains, no partner, target finished or disconnected, cap, taint), `TestGhostStreamValidatorWrap`
    (including one admitted swap discontinuity), `TestRelevanceClassifierWrap`,
    `TestControlCodecCompetition` plus fuzz and `TestRoomHostAdversarial` swap cases.
  - In A3, `TestAttemptReplayHarnessArena` (ROM) and `TestVerifierSwapLog` (matching, forged and
    out-of-window events; fallback verdicts).
- **Stop when** A1 compiles with the pins green, A2's net and server selections pass with the new
  tests, and A3 replays an arena recording and a competition-act recording containing a death to
  the live ghost hash. If A3's arena replay cannot reach a finish with an input-only recording, stop
  and report (§13.3).

### Lane B: engine hosted arenas and competition-act rules (Sol)

- **Objective:**
  - The `com.openggf.game.arena` contracts and the S3K implementation (§4.3).
  - Read-only session accessors.
  - The solo full-screen `Views` with the two read-only projections (§4.6).
  - `GhostArtSource` and the miniature source (§4.7).
  - The arena step hooks in `HostedRunController`, `HostedArenaLaunch`, and the `Engine` and
    `SceneRunLauncher` branches (delegates only).
  - `FAST_FROM_CHECKPOINT` (§6).
  - The monitor rules in the S2 and S3K monitor objects (§7), including the teleport guard on
    `swapEnabled` and the D22 draw. S1 monitors are untouched (D2).
  - `RaceClock` production for acts (game over, time over).
  - The **swap engine side**:
    - the `SWAP_REQUEST` signal from S2 `teleport_monitor` and the arena `loc_3652A` path;
    - `RunHandle.queueRaceEvent` and `RunStep.applied`;
    - per-game swap appliers behind a semantic `RaceSwapApplier` game service: S2 reconstruction
      and S3K arena shoe swap (§8.3);
    - admissibility (§8.4) and the ROM solo fallbacks;
    - the `secondaryPath`/`shoeKind` pose capture in `NativePlayerPose`.
  - Confirm and fix the solo `swapShoes` parity gap (§3.1) with a ROM-backed test before building
    on it.
- **Owns:**
  - `src/main/java/com/openggf/game/sonic3k/competition/**`
  - new `src/main/java/com/openggf/game/arena/**`
  - `HostedRunController.java`, `HostedArenaLaunch.java` (new), `SceneRunLauncher.java`,
    `GhostRenderer.java`
  - delegate lines in `Engine.java` and `GameLoop.java`
  - `GameLoopContinueCoordinator.java`, the `LevelManager` restart owner
  - the fast-death branches of `PlayableSpriteMovement.java`
  - the S2 and S3K monitor object classes, and the S2 teleport timer/flag owners
  - `src/main/java/com/openggf/mods/run/NativePlayerPose.java` (capture body only; A1 owns the
    record declarations)
  - their tests under `src/test/java/com/openggf/**`
- **Must not touch** `game/sonic2/competition/**`, shared `game/competition/*`,
  `NativeCompetitionRenderer.java` or `LevelRenderer.java` (the S2 parity session needs them). If
  one is unavoidable, stop and tell the lead first.
- **Inputs.** A1's contracts.
- **Checks:**
  - Regression guard, unchanged and green:
    `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=com/openggf/game/sonic3k/competition/**/Test*" test`
    (all 33 classes, including `TestS3kCompetitionSession`, `TestS3kCompetitionGoalAndItems`,
    `TestS3kCompetitionNativeHost`, `TestS3CompetitionProduct`, `TestS3kCompetitionSpritePages`
    and `TestS3kCompetitionRosterRestore`).
  - Hosted-run regression:
    `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestHostedRunController,TestSceneRunLauncher,TestFastDeathRestart,TestGameplayRunPolicy,TestHostedRunActCompletionWiring" test`
  - New, ROM-gated: `TestHostedArenaEquivalence` (§13.2), `TestHostedArenaLapCapture`
    (positioned crossings: lap boundaries, `frameTime` parity, backwards crossing, death rollback,
    false start, cap → `TIME_OVER`), `TestCheckpointRespawnPolicy`,
    `TestS2RandomisedMonitorContents` (`Level_frame_counter` rule, the D22 bit-0 draw, the
    teleport guard on `swapEnabled`, `tails_1up`), `TestMonitorOffPolicy`, `TestS2SwapApplier`
    (fields set and not set per §8.3, camera snap, object-window reinit, 64-frame freeze,
    admissibility rejections), `TestS3kArenaSwapApplier` (`loc_3652A` fields, solo empty-slot
    parity), and `TestRaceEventReplayEquivalence`: a live event queue and the harness applying
    the same recorded event reach identical state.
  - GL, run with `-Dopenggf.headless.gl=surfaceless-egl`: `TestS3kSoloArenaViewPixels` (ghost
    present, 224 rows, no visibility writes).
  - Before handoff: `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
    `TestSonic3kBootstrapResolver`, `TestSonic3kDecodingUtils`.
- **Stop when** the equivalence and lap-capture tests pass on the locked-on ROM, the 33-class guard
  is green and unchanged, and a checkpoint respawn produces an identical post-reload state through
  both the GameLoop path and a direct owner call.

### Lane C: Time Attack mod runtime (Sol)

- **Objective:**
  - The catalog's kind, laps and modes (§4.1), `VotePool` and `VoteTrackPools`.
  - The `TimeAttackAttempt` timing rules, lap and split capture, and continuation across checkpoint
    respawn (§4.4).
  - In `TimeAttackRuntime`: competition policy, no retry, Start ownership, the non-pausing race
    menu state, and the arena hold and release offset.
  - The mass-start flow in `RaceSession` and `MultiplayerRaceCoordinator` (§4.5).
  - Descriptor and recording v2, `.ggfghost` v2, frame cap and `GhostStore` paths (§4.10).
  - Wrap unwrapping of ghost poses and the progress and position model (§4.7, §4.8), `LiveLevelProfileFactory`.
  - The **swap mod side**:
    - forward `SWAP_REQUEST` steps as `TeleportRequest`;
    - queue a received `TeleportSwap` (the other slot's target) or `TeleportNoPartner` through
      `RunHandle.queueRaceEvent`;
    - record the applied event in recording v2 at its step;
    - send `TeleportOutcome`;
    - expose swap-feedback state for lane D.

    `swapEnabled` comes from `RaceLaunch` into the descriptor.
  - **C1, first commit:** the data contracts lane D renders. These are `TimeAttackHudState` and
    `MultiplayerHudState` fields (lap, best lap, position estimate, cutoff, mass-start phase,
    placement rows) and the settings model for mode, monitors, cutoff and start-in.
- **Owns** `racing/time-attack/src/main/java/openggf/timeattack/**` and its tests, except lane D's
  files.
- **Inputs.** A1 contracts; B's arena runs for ROM tests (until then, use synthetic `RunStep`s).
- **Checks:**
  - `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=openggf/timeattack/**/Test*" test`
    (`TestTimeAttackAttempt`, `TestTimeAttackRuntime*`, `TestTimeAttackTrackCatalog*`,
    `TestGhostFileCodec`, `TestGhostStore`, `TestAttemptInputRecording`,
    `TestMultiplayerRaceCoordinator`, `TestRaceSession`, `TestVoteTrackPools`, `TestTimeAttackModPackage`)
  - New: `TestLapAttempt`, `TestCompetitionAttemptAcrossRespawn`, `TestMassStartRelease`
    (offset error, arena lead, start tolerance), `TestWrapGhostPlacement`,
    `TestRaceProgressEstimate`, and `TestSwapEventRecording`: events in the hash, canonical order,
    a reordered or edited event changes the hash, and applied-before-step semantics.
- **Stop when** the selection is green, including the mod packaging test, which treats warnings as
  errors, and a loopback two-client competition round in `TestMultiplayerRaceCoordinator` reaches
  `RoundEnd` with placements by frames.

### Lane D: GUI (Opus)

- **Objective.** Every surface in §11, rendering lane C's state with no logic of its own.
- **Owns:**
  - `TimeAttackMenuState.java`, `TimeAttackHud.java`, `VotePoolEditorView.java`
  - `mp/RaceLobbyView.java`, `mp/RaceLobbyScreen.java`, `mp/ServerBrowserView.java`,
    `mp/MultiplayerHudRenderer.java`, `mp/MinimapLayout.java`, `mp/HudTextLayout.java`
  - `ui/**`
  - their tests.
- **Inputs.** C1; final integration with C.
- **Checks:**
  - `python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestTimeAttackMenuState,TestRaceLobbyView,TestServerBrowserView,TestMultiplayerHudLayout,TestMinimapLayout,TestHudTextLayout,TestTimeAttackModPackage" test`
  - Offscreen captures at 320 and 400 px (`headless-rendering` skill) of: room settings, the arena
    in-race HUD over a solo arena view, the cutoff plate, the swap plate, and the results table with
    DNF rows and the UNVERIFIED tag.
- **Stop when** the layouts fit the 140-px column at both widths without overlapping the native
  arena HUD or the lives row, and the captures are attached to the handoff.

## 13. Verification

### 13.1 Focused tests per lane

These are listed in §12.

### 13.2 What proves "identical to the original"

1. **Minimal gameplay edits.** The arena session, player, objects and host gameplay paths are
   unchanged except in two places:
   - the ROM-backed `swapShoes` solo parity fix (§3.1), if lane B confirms the gap;
   - the swap applier, which runs only on a recorded external event and is a no-op when no swap
     is applied.

   Otherwise only package-private read accessors and the new `Views` and projection classes are
   added. With items off, or with no swap applied, the hosted path must stay frame-identical to
   the native one.
2. **The guard stays green and unedited:** the 33 existing S3K competition classes.
3. **`TestHostedArenaEquivalence`** (ROM) drives, with the same input script:
   - (a) the native menu path (`S3kCompetitionScreen`, solo Match Race);
   - (b) the hosted path (`HostedArenaLaunch`).

   It asserts that `session.capture()` and the player pose are equal on every frame. It also
   asserts the same with items on, with items off, and across a death and a false start.
4. **The solo view never writes gameplay.** A test runs with and without the solo view drawing
   each frame and asserts identical sprite-page visibility and session captures.

### 13.3 ROM-gated end-to-end (lead)

**Live hosted run (BPZ, the 512-px lap).** This uses positioned crossings in the style of
`TestS3CompetitionProduct#originalPositionedTimeAttackGoalRewindResultsSaveRestartAndBack`: actor
centres placed around the native goal, with ordinary frames between crossings. It asserts:
- five lap events and their frames;
- `AttemptFinish` passes host validation;
- the best lap;
- the HUD state.

**Verifier.** A real input route is preferred:
- Author it with the Stageplay route planner (user rule). Try BPZ first.
- Kill condition: no completed lap after a one-hour budget, or two planner attempts. Four earlier
  full-route candidates failed.
- If the route fails, the verifier test replays an unfinished input-only recording instead: GO, the
  start crossing, a backwards crossing and a death. It asserts the ghost hash and the lap state.
  The verified-finish gap is then recorded in `docs/status/level-test-coverage.md`.

Positioned state is never used in production replay.

**Swap end to end.** A loopback test runs a host and two headless S2 clients on an EHZ1 signpost
act (ROM), with COMPETITION_ITEMS:
- One client breaks a teleport monitor; a positioned break is acceptable for the live part.
- Assert one logged command, both outcomes, both recordings carrying the event, and the validator
  admitting exactly one discontinuity per stream.
- `AttemptReplayHarness` must reproduce both clients' post-swap states from their recordings
  against the log.
- A corrupted event payload must fail verification, so the comparison is shown to run.

**Shared-change smoke.**
`python3 tools/testing/run_categories.py --base 60aa1d658 --workers 2 --run`. State the class count
first, and report the `VERDICT` line.

## 14. Documentation obligations

- `CHANGELOG.0.8.prerelease.md`: one feature entry for Competition mode, S3K arenas and the monitor
  settings. Root `CHANGELOG.md` only through the `Changelog` trailer rules.
- Guide: `docs/guide/features.md` and `docs/guide/playing/getting-started.md`, Time Attack sections
  (modes, arenas, monitor settings, cutoff, DNF and DNS); `racing/README.md`: protocol 9, track
  keys, competition rules.
- `docs/S3K_KNOWN_DISCREPANCIES.md` (intentional only): the full-screen solo arena view replaces the
  ROM split. The display-only rows above and below the native 112-line window are an intentional
  presentation change.
- `docs/status/known-discrepancies.md`:
  - checkpoint fast reload without fade or title card in Competition-mode acts;
  - monitors OFF;
  - the 2P random-monitor rule applied to 1P acts;
  - the invented COMPETITION_ITEMS draw (D22);
  - the server-mediated swap: reconstruction from streamed pose (§8.3), non-atomic application,
    and invincibility applied at no-partner resolution rather than at icon selection.
- `docs/S3K_KNOWN_DISCREPANCIES.md` also records the networked arena swap partner. The solo
  `swapShoes` gap is fixed, not listed, unless lane B shows the engine already matches the ROM.
- `docs/architecture/designs/2026-07-04-time-attack-security-design.md`: a dated amendment note
  pointing here (§8.7). It covers UNVERIFIED Competition boards (and Time Attack boards if the
  consistency switch lands), and the swap as the one external simulation input.
- `docs/status/level-test-coverage.md`: arena hosted-run coverage and any verified-finish gap.
- `docs/architecture/research/multiplayer-time-attack.md`: refresh its header to protocol 9 and
  the competition lifecycle.
- No README release-section change, since this is not a new version theme.

## 15. Open questions

None need the user. The first draft's Q1-Q6 are closed (D2, D17, D14, D19, D20, D21-D22).

Flagged for the lead, with no answer needed: Time Attack-mode standings in unverified rooms. The
design recommends the same UNVERIFIED publication as Competition, for consistency, behind one
host switch (D17). Lane A builds the switch defaulting on; the lead may hold it off at
integration.
