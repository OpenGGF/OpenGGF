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

- S3K fades lines 1-3 of the level palette in during its first LevelLoop frames
  (`Level/loc_64DC`, sonic3k.asm:7906-7910); the hold follows the spawn step, so an S3K
  countdown shows the player and HUD line over a black act, which fades in as control returns.
  Skipping that fade would change `Palette_fade_timer`, which zone events read.
- S3K AIZ1 as Sonic (or Sonic and Tails) replays the plane intro on every restart from the
  start, as the ROM does without a star post (`SpawnLevelMainSprites`, sonic3k.asm:8143-8158).

## Verification

See the delivery report: focused ROM-backed tests for S1, S2 and S3K
(`TestFastDeathRestart`), HUD, policy and Time Attack runtime tests, required S3K tests,
hosted-run and Mod API pin tests, a stock-death trace run and `-Pguards`. Offscreen captures
were kept in the task directory outside the repository.
