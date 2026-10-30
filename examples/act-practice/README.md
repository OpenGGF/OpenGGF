# Act Practice: a title entry that launches stock runs

The smallest use of the [title entry and gameplay run](../../docs/modding/guides/gameplay-runs.md)
seams. It adds **Act Practice** to the OpenGGF master title. Pick a game with Left/Right, confirm
(A, C or Start on a controller, Enter on a keyboard, as in every scene), and play its first act
exactly as the shipped game plays it. A timer runs from your first input to the signpost or
capsule, **R** on the keyboard retries, and every retry races a ghost of your previous attempt.
Your best time per game is saved and shown on the game's card. A failed write keeps the best in
memory and shows an unsaved notice, with retries during the run and when it ends. Returning to
the menu retries too. Rejected best-time files remain untouched; storage retry does not require
a faster attempt.

The menu echoes the engine's menus through the public canvas and fonts only (`PracticePage`):
a navy page under a gold-ruled header, the choice as one focused card with page dots, a result
line whose icon and colour say what happened (a completed act and whether it set the best, a
failed load, an unsaved best), and key caps that name the keyboard or the controller, whichever
was pressed last. Controller caps come from `ctx.buttonLabel`: the physical buttons that produce
Genesis A and B on that pad (X and A on Xbox, Square and Cross on PlayStation), the Genesis names
when the host has no live input. If Enter is bound to B (or Escape to a confirming button) the
binding decides and the caps name the bound keys. Times read m:ss.cc, as in Time Attack.

```
src/main/resources/META-INF/openggf-mod.yaml   baseGame: any, entry point
src/main/java/practice/ActPracticeMod.java     registers the title entry
src/main/java/practice/PracticeScene.java      the menu: picks a game, launches runs, shows results
src/main/java/practice/PracticePage.java       the page look: header, focus card, notices, key caps
src/main/java/practice/PracticeHost.java       the run host: timing, ghost, overlay, retry key
```

Unlike Time Attack, practice runs keep special stages and live rewind
(`new GameplayRunPolicy(true, true, RETURN_TO_HOST, true, false, STOCK, true, true)`); they still hand control back
to the scene when the act is complete. The run itself contains no mod content, so it is the
stock game; the host only observes it, draws over it and asks for retries.

## Build and run

```bash
python3 examples/build_example.py act-practice --run
```

`TestActPracticeExample` builds the example through `ggfmod`, opens its entry and drives a run.
