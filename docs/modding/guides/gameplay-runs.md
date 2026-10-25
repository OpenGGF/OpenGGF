# Title entries and gameplay runs

A mod can add one entry to the OpenGGF master title and, from that entry's scene, launch
**stock gameplay runs**: an act of Sonic 1, 2 or 3&K played exactly as the shipped game plays
it, observed and timed by your code. The bundled Time Attack mod is built entirely on this.

This is a candidate Mod API (0.7 line): signatures may still change before publication.

## Register a title entry

```java
public final class DrillsMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerTitleEntry("Practice Drills", DrillsScene::new);
    }
}
```

- One entry per mod. Any patch mod may register one, including `baseGame: any` mods.
  A title entry, startup scene or concrete stock-game patch gives an `any` package a
  code role that may share objects, object art, previews and service bundles across
  games, alongside an optional mutator catalogue. Catalogue-only packages cannot
  publish that runtime content. Per-game patches need no scene; zones, characters,
  ROM-art intake, launch teams, input/HUD policies and decoded level patches still
  require a single base game.
- The entry never replaces a game's title screen. The master title shows **EXTRAS** and a
  chooser for registered entries, including when only one mod contributes an entry.
- The scene opens without a loaded game, so ROM art (`SceneContext.art().rom()`) is not
  available to title-entry scenes; draw with `SceneCanvas` text and shapes or PNGs from your mod.
- `exitToMasterTitle()` (and `exitToGameTitle()`) return to the master title.
- While a scene takes keyboard text (a name, an address, chat), return `true` from
  `ModScene.capturesTextInput()`: the engine then holds back its global keyboard shortcuts
  (display profile, shaders, capture) so typing cannot trigger them.

## Launch a run

```java
RunSpec spec = new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct());
RunHandle handle = ctx.gameplay().launch(spec, myHost);
```

- `ctx.gameplay().availableGames()` lists the games whose ROMs are configured, and
  `ctx.gameplay().determinismFingerprint(gameId)` gives the fingerprint a run of that game will
  report, before any run starts (empty without the ROM) — use it to match rooms or recordings.
- The character must be a stock character of that game.
- The scene is suspended while the run plays (no `update`/`draw`) and is resumed through
  `ModScene.resumed(ctx, reason)` when it ends: `ACT_COMPLETED`, `LEFT`, `ABORTED` (the player
  quit or the host failed) or `LOAD_FAILED`.
- The run is a stock, non-saving session. It contains **no mod gameplay content** — not yours and
  not any other enabled mod's — so its simulation is the shipped game's. Your code takes part only
  through the `RunHost` callbacks and the `GameplayRunPolicy`.

### A fenced part of the act

`new RunSpec(game, zone, act, character, policy, area)` holds the run in a `RunArea`: every load
of the act (launch, retry and a death reload) places the character at the area's spawn (ROM
centre coordinates), and the engine holds the camera inside the area's bounds (the camera's
top-left corner) on every frame, after the act's own level events, so the character cannot leave,
as at a boss arena. The character's walls follow the camera as in the shipped game: 16 pixels
inside the left bound, near the right edge of a native 320-pixel screen at the right bound, and a
fall below the screen at the bottom bound is a death. The act's own events still run against the
held bounds; Sonic 1's `SignpostArtLoad`, for one, locks the left boundary once the camera is
within `$100` of the right bound, so keep a Sonic 1 area's camera X range at most `$100` wide.
The five-argument constructor plays the whole act from its usual start.

### A run behind your scene

`ctx.gameplay().launchBehind(spec, host)` starts the run without suspending your scene: it keeps
receiving `update` once per level frame (before `admitStep`) with the live controller and
keyboard, and its `draw` paints over the level, the HUD and your host's overlay on a canvas the
size of the run's screen. Do not `clear` the canvas if the level should show through. Start and
the pause key never pause such a run; they reach your scene. `RunHandle.holdControls(true)` gives
the run's characters a released controller while your menu owns it (the level keeps running), and
`holdControls(false)` hands it back. While your menu covers the level,
`RunHandle.setLevelCovered(true)` stops the engine drawing the stock HUD and ghost nameplates,
which would otherwise read through a translucent menu; it is presentation only (the act's timer
and everything else keep running). `RunHandle.setHudTime(frames)` shows your run's own clock in
the stock HUD's TIME instead of the act's timer (a negative value restores the act's timer); set
it from `afterStep` and the HUD shows it that frame. `RunHandle.setHudTimeHundredths(true)` adds
hundredths (`0:00.00`), as Sonic CD shows them, in the game's own HUD digits. Both are
presentation only: the act's timer, its time-over and its warning flash do not change, and both
clear when the run ends. When the run ends, `resumed` follows as usual; launching the
next run from `resumed` keeps the screen black between the two loads. Time Attack's lobby level is
built this way.

## The run policy

`GameplayRunPolicy` is fixed when the run starts and survives retries:

| Component | `stock()` | `isolatedAct()` |
|---|---|---|
| `specialStageEntry` (giant rings, S2 star-post stars, S3K entry rings) | yes | no |
| `bonusStageEntry` (S3K star-post stars) | yes | no |
| `actCompletion` | `CONTINUE` (next act/zone/ending) | `RETURN_TO_HOST` |
| `liveRewind` | yes | no |
| `editorEntry` | yes | no |
| `deathRestart` | `STOCK` | `STOCK` |
| `livesCounter` | yes | yes |
| `zoneIntros` (S3K Angel Island 1's plane intro as Sonic) | yes | yes |

`withFastRestarts()` turns any policy into one with unlimited attempts (Time Attack uses
`isolatedAct().withFastRestarts()`): `deathRestart` becomes `FAST_FROM_START` and the lives
counter is hidden. A death then takes no life and never reaches GAME OVER or TIME OVER; the
death animation plays, and once the player has been dead for
`GameplayRunPolicy.FAST_RESTART_SKIP_FRAMES` (30) executed steps a jump press cuts the rest
short. The act then reloads at once from its start, star-post progress cleared and with no
title card, and `onLevelReady` follows with `RunLevelStart.deathRestart()` set so the host can
show a countdown (hold steps from `admitStep`). Hold after the first step if the act should be
visible during the countdown: the reloaded act's player and objects are drawn from its first
step, and an S3K act also fades its own palette in over its first steps, which
`RunInput.levelFadingIn()` reports. Return `true` from `RunHost.holdsPlayerInput()` to run such
lead-in steps with the player's controller released; their `RunStep` reports no buttons. The skip counts the player's own dead steps, so
it lands on the same step when the same inputs are replayed.

`withoutZoneIntros()` starts acts where their intro cutscene would hand the player control,
on the first load and every reload (the engine's skip-intros start); Time Attack uses
`isolatedAct().withFastRestarts().withoutZoneIntros()`.

Objects consult the policy where the shipped game would enter a stage or leave the act, and the
player's death routine where it would take a life, before any of their own state changes;
nothing else about the simulation differs.

## The run host

All callbacks run on the engine thread inside your mod's fault boundary and must not block.
A thrown exception ends the run with `ABORTED` instead of crashing the engine.

| Callback | When |
|---|---|
| `onLevelReady(RunLevelStart)` | after the level loads, on launch, every retry and every fast death restart; carries the spec, a determinism fingerprint (engine build + ROM), whether a debug aid was already on, the act's size and whether this load is a fast death restart |
| `admitStep(RunInput)` | before every level step; return `false` to hold the run this frame (countdowns, lobby waits). `RunInput` exposes the logical controller state and `SceneKeys` key state |
| `afterStep(RunStep)` | after every executed step: admitted player-1 buttons, Start, the player's pose, the act-completion signal, the last star post and a debug-assist flag. Held, paused, setup-only and lag-skipped frames produce no step |
| `ghosts()` | each frame: up to eight `GhostPose`s the engine draws translucently with stock character art in the correct sprite layer. A stock `appearance` draws a desaturated ghost; any other look draws it in that player's palette. A `nameplate` floats above it in the player's name colour (`NameFont`) and fades with it |
| `drawOverlay(LevelOverlayCanvas)` | screen-space drawing above the level and HUD (use `CompactFont`) |
| `retryOnActCompletion()` | when a `RETURN_TO_HOST` run completes its act; return `true` to restart the act as a retry (`onLevelReady` follows) instead of ending the run. Defaults to `false` |
| `onRunEnded(RunEndReason)` | once, just before control returns to your scene |

At act completion a pending `leave()` or host failure still ends the run (a failure as
`ABORTED`), and a `retry()` requested on that frame restarts the act without asking.

`RunHandle` commands take effect at the next safe boundary: `retry()` reloads the starting act
and clears star-post progress, `leave()` ends the run, and `spectate(active, dx, dy)` pans the
camera within the level bounds — honoured only after the act's completion signal, so it can
never move the camera during play.
`setPlayerAppearance(PlayerAppearance)` draws the run's own character in a player's look from
the next frame: its palette line with the look's entries applied wherever the live colour is
still the stock one, so Super/Hyper cycles still show. Only that sprite changes; rings, the HUD
and objects keep their colours, and the simulation is untouched. `CharacterLooks.withBaseColour`
builds a look from one colour; `PlayerAppearance.STOCK` restores the stock palette.

`setHudProfile(HudProfile)` overrides the native HUD rows for this run, including on retries
and death reloads. Pass `null` to restore the current zone's profile; ending the run also
restores it. The run's lives policy still applies. This changes presentation only: hiding
SCORE or moving TIME does not change the score, act timer or warning rules. Debug mode
retains its stock readouts and bonus stages retain their dedicated layout.

For example, a host can hide SCORE and LIVES and move the stock TIME and RINGS rows to
`y=8` and `y=24`. Keep TIME's `maxDigits=4`: this describes its ordinary format, while the
renderer measures its extended hundredths format from the loaded separator art.
`LevelOverlayCanvas.hudOriginX()` returns the native HUD origin in screen coordinates for
that canvas width, honouring the HUD anchor setting. Add native offsets (such as `16`) to
align compact overlay text beneath the native rows. With no native HUD installed it returns
the centered 320-pixel origin; a bonus-stage HUD uses zero.

## Determinism and fairness

Because runs admit no mod content and record their policy, two players with the same engine
build and ROM produce the same simulation from the same inputs; `RunLevelStart.determinismFingerprint()`
names that pair. Record the admitted `RunStep.heldMask()` per step to replay or verify a run.
