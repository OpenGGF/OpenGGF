# Act Practice: a title entry that launches stock runs

The smallest use of the [title entry and gameplay run](../../docs/modding/guides/gameplay-runs.md)
seams. It adds **Act Practice** to the OpenGGF master title. Pick a game, press Start, and play
its first act exactly as the shipped game plays it. A timer runs from your first input to the
signpost or capsule, **R** retries, and every retry races a ghost of your previous attempt.
Your best time per game is saved.

```
src/main/resources/META-INF/openggf-mod.yaml   baseGame: any, entry point
src/main/java/practice/ActPracticeMod.java     registers the title entry
src/main/java/practice/PracticeScene.java      the menu: picks a game, launches runs, shows results
src/main/java/practice/PracticeHost.java       the run host: timing, ghost, overlay, retry key
```

Unlike Time Attack, practice runs keep special stages and live rewind
(`new GameplayRunPolicy(true, true, RETURN_TO_HOST, true, false)`); they still hand control back
to the scene when the act is complete. The run itself contains no mod content, so it is the
stock game; the host only observes it, draws over it and asks for retries.

## Build and run

```bash
python3 examples/build_example.py act-practice --run
```

`TestActPracticeExample` builds the example through `ggfmod`, opens its entry and drives a run.
