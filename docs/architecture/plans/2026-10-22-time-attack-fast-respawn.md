# Time Attack fast death restarts (2026-10-22)

## Outcome

Time Attack has no lives. A death plays the ROM death animation; once the player has been dead
for 30 executed steps a jump press skips the rest. The act then reloads at once from its start
(star posts cleared, no title card, no life lost, never GAME OVER/TIME OVER), and a 3-2-1
countdown holds control before a fresh attempt.

User decision (2026-10-22): restart from the level start only. A restart-from-checkpoint room
setting was in the first brief and was dropped before implementation; there is no
`RoundConfig` field, solo setting or protocol change.

## Contracts

- `GameplayRunPolicy` gains `DeathRestart deathRestart` (`STOCK`, `FAST_FROM_START`) and
  `boolean livesCounter`; `withFastRestarts()` sets both. `stock()`/`isolatedAct()` keep the
  shipped death. `FAST_RESTART_SKIP_FRAMES = 30`.
- `RunLevelStart.deathRestart()`: `onLevelReady` after a fast restart.
- Death owner: `PlayableSpriteMovement.enterDeathRestartRoutine` keeps the ROM 60-step restart
  delay and drops the life decrement and GAME OVER/TIME OVER branch (S1 `Sonic_HandleDeath`,
  S2 `CheckGameOver`/`Obj01_ResetLevel`, S3K `loc_12432`/`loc_12498`).
  `skipFastRestartDeathAnimation` counts the player's own dead movement updates
  (`PlayableSpriteMovement.deathElapsedFrames`, in its rewind state) and requests the reload
  on a jump press; the count is replay-deterministic (no `V_int_run_count`, no wall clock).
  It lives in the movement controller, not the size-ratcheted `AbstractPlayableSprite`.
- Restart owner: `GameLoop.startRespawnFade` hands the death's restart to
  `GameLoopContinueCoordinator.startRespawn`, which keeps the stock fade and title-card reload
  or, under a fast policy, runs `restartFromStartAfterDeath` →
  `LevelManager.restartCurrentLevelFromStartAfterDeath`: star posts cleared, then the stock
  `restartCurrentLevelAfterDeath` (`FULL_DEATH_RELOAD`) with its title card omitted through
  `skipPendingInitialTitleCardPresentation` (the headless/tool boundary). The screen cuts to
  black and fades in; the host's level start follows.
- HUD: `LevelManager.installHudProfile` installs `HudProfileAccess.forRunPolicy`, which hides
  the lives rows when `livesCounter` is false.

## Follow-up decisions (2026-10-22, second slice)

- **Fade, then count.** After a fast reload the lead-in runs with the player's input held:
  the spawn step, then every step while `RunInput.levelFadingIn()` reports the level's own
  palette fade (S3K `Level/loc_64DC` arms `Palette_fade_timer` = $16, sonic3k.asm:7906-7910;
  the fade is untouched; read through `LevelPaletteFadeOwner`). S1/S2 report no fade, so their
  lead-in is the spawn step alone. The 3-2-1 then holds over the visible level.
  `RunHost.holdsPlayerInput()` makes `HostedRunController` suppress player 1's input for the
  admitted step (only a suppression it set is cleared) and record no buttons. Lead-in steps
  are part of the attempt with empty masks (untimed: the clock starts at first input), so the
  verifier, which replays recorded masks from the act start, reproduces them.
- **Paced release.** Lead-in frames reach a room only when control returns, with
  `AttemptStart`. An S3K lead-in is about 23 frames, above the hub's 12-frame stream lead, so
  the runtime hands over 9 at once and then one extra per 12 steps (5 per second above play,
  inside the hub's 66-per-second refill) until the backlog is gone (about 3 s); a finish
  flushes it.
- **No intros.** `GameplayRunPolicy.zoneIntros`; Time Attack runs `withoutZoneIntros()`. The S3K
  bootstrap resolver and initial title-card suppression treat it like the existing skip-intros
  setting, so AIZ1 as Sonic starts at the intro-skip start on launch and on every reload. The
  verifier replays attempts under `TimeAttackRuntime.runPolicy()` so its act starts match.
  Knuckles and Tails already take that start (`Sonic3kBootstrapResolver`'s non-Sonic branch).
- **First launch.** A solo launch has no countdown, so nothing changes. A room launch shows its
  title card and then the room countdown; whether the S3K fade overlaps that countdown depends
  on load time, and running steps then would void the attempt (`afterLevelFrame` voids
  outside the window), so room launches are unchanged.

## Timing decision

Every fast restart is a new attempt: `TimeAttackRuntime.onLevelReady` voids the running
attempt (an `AttemptReset` in rooms) and begins the next. The reloaded act's spawn step runs
first (attempt frame 0), then the countdown holds the run from `admitStep` for 180 frames,
which execute no step, are not in the attempt's input recording and are not timed (the clock
is `finishFrame − firstInputFrame` over executed steps). Frame 0 is handed to the room
listener only when the countdown ends, so `AttemptStart` (sent with the first frame) and the
host's pacing clock start when the player regains control. Host finish validation is unchanged.

## Rejected / not done

- Holding before the spawn step (the first implementation). The reloaded act then showed no player or
  objects for the whole countdown: the engine builds the sprite table only inside a level step,
  and publishing a presentation-only table from the reloaded state
  (`LevelSpritePresentation.prepare`/`publishPreparedScene`) still drew nothing. Running the
  spawn step first fixed it with no simulation change (held frames change no state either way).
- Reloading through `respawnPlayer()` (`showTitleCard=false`). It omits the card even where a
  zone suppresses it for a later cutscene, so S3K AIZ1's plane intro then failed binding its
  in-level card ("runtime-art admission lease is already consumed",
  `Sonic3kTitleCardManager.initInternal` from `CutsceneKnucklesAiz1Instance`). The stock death
  reload decides whether a card exists; only its presentation is omitted now.
- Mid-attempt checkpoint respawn (dropped by the user). Recorded for a future attempt: the hub's
  `GhostStreamValidator` would reject it as written. The respawn teleport fails the per-frame
  speed check (`|dx|,|dy| > 32`), dropping the batch and leaving a frame gap that fails
  `hasFinishEvidence`. The countdown's held frames also count against the pacing floor
  (54 frames per second of wall time after a 3 s warm-up), so a few deaths per attempt flag it.
  Supporting it needs a hub-visible respawn marker and a bounded discontinuity and pacing
  allowance, which is a protocol change.

## Known limits

- First slice (superseded by the follow-up above): the S3K countdown showed the player over a
  black act, and AIZ1 as Sonic replayed the plane intro on every restart, as the ROM does
  without a star post (`SpawnLevelMainSprites`, sonic3k.asm:8143-8158).

## Verification

See the delivery report: focused ROM-backed tests for S1, S2 and S3K
(`TestFastDeathRestart`), HUD, policy and Time Attack runtime tests, required S3K tests,
hosted-run and Mod API pin tests, a stock-death trace run and `-Pguards`. Offscreen captures
were kept in the task directory outside the repository.
