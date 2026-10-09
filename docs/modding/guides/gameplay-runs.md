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

- One entry per mod. Any patch mod may register one, including `baseGame: any` mods, which
  may register nothing else but a startup scene and its display width.
- The entry never replaces a game's title screen. With one contributing mod the master title
  shows the entry under its label; with several it shows **EXTRAS** and a chooser.
- The scene opens without a loaded game, so ROM art (`SceneContext.art().rom()`) is not
  available to title-entry scenes; draw with `SceneCanvas` text and shapes or PNGs from your mod.
- `exitToMasterTitle()` (and `exitToGameTitle()`) return to the master title.

## Launch a run

```java
RunSpec spec = new RunSpec("s2", 0, 0, "sonic", GameplayRunPolicy.isolatedAct());
RunHandle handle = ctx.gameplay().launch(spec, myHost);
```

- `ctx.gameplay().availableGames()` lists the games whose ROMs are configured.
- The character must be a stock character of that game.
- The scene is suspended while the run plays (no `update`/`draw`) and is resumed through
  `ModScene.resumed(ctx, reason)` when it ends: `ACT_COMPLETED`, `LEFT`, `ABORTED` (the player
  quit or the host failed) or `LOAD_FAILED`.
- The run is a stock, non-saving session. It contains **no mod gameplay content** — not yours and
  not any other enabled mod's — so its simulation is the shipped game's. Your code takes part only
  through the `RunHost` callbacks and the `GameplayRunPolicy`.

## The run policy

`GameplayRunPolicy` is fixed when the run starts and survives retries:

| Component | `stock()` | `isolatedAct()` |
|---|---|---|
| `specialStageEntry` (giant rings, S2 star-post stars, S3K entry rings) | yes | no |
| `bonusStageEntry` (S3K star-post stars) | yes | no |
| `actCompletion` | `CONTINUE` (next act/zone/ending) | `RETURN_TO_HOST` |
| `liveRewind` | yes | no |
| `editorEntry` | yes | no |

Objects consult the policy where the shipped game would enter a stage or leave the act, before
any of their own state changes; nothing else about the simulation differs.

## The run host

All callbacks run on the engine thread inside your mod's fault boundary and must not block.
A thrown exception ends the run with `ABORTED` instead of crashing the engine.

| Callback | When |
|---|---|
| `onLevelReady(RunLevelStart)` | after the level loads, on launch and every retry; carries the spec, a determinism fingerprint (engine build + ROM), whether a debug aid was already on, and the act's size |
| `admitStep(RunInput)` | before every level step; return `false` to hold the run this frame (countdowns, lobby waits). `RunInput` exposes the logical controller state and `SceneKeys` key state |
| `afterStep(RunStep)` | after every executed step: admitted player-1 buttons, Start, the player's pose, the act-completion signal, the last star post and a debug-assist flag. Held, paused, setup-only and lag-skipped frames produce no step |
| `ghosts()` | each frame: up to eight `GhostPose`s the engine draws translucently with stock character art in the correct sprite layer |
| `drawOverlay(LevelOverlayCanvas)` | screen-space drawing above the level and HUD (use `CompactFont`) |
| `onRunEnded(RunEndReason)` | once, just before control returns to your scene |

`RunHandle` commands take effect at the next safe boundary: `retry()` reloads the starting act
and clears star-post progress, `leave()` ends the run, and `spectate(active, dx, dy)` pans the
camera within the level bounds — honoured only after the act's completion signal, so it can
never move the camera during play.

## Determinism and fairness

Because runs admit no mod content and record their policy, two players with the same engine
build and ROM produce the same simulation from the same inputs; `RunLevelStart.determinismFingerprint()`
names that pair. Record the admitted `RunStep.heldMask()` per step to replay or verify a run.
