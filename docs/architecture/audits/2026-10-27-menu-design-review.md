# Menu design review and hill climb (2026-10-27)

Branch `feature/ai-menu-design-review`, based on develop `9a796ec60f`.
Goal: every engine-owned menu easy to use, clear to read, and able to present
complex settings neatly, at production polish. ROM-native screens keep ROM
fidelity and are not redesign targets.

## Capture method

Display-free surfaceless EGL, scripted `InputHandler` events, 320×224 logical at 3×,
run from the main checkout so ROM logos and the trace catalog resolve:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY java -Dopenggf.headless.gl=surfaceless-egl -cp <classes:deps> \
  com.openggf.tools.MasterTitleCaptureTool <out>/hub 320 3
env -u DISPLAY -u WAYLAND_DISPLAY java -Dopenggf.headless.gl=surfaceless-egl -cp <classes:deps> \
  com.openggf.tools.MenuDesignCaptureTool <out>/other 320 3 <mods-dir>
```

`MenuDesignCaptureTool` (new) covers recordings, trace replays, Mod manager, details,
text editor, Files/Capture/Advanced settings, the disclaimer, the native-mod notice and
content import. It copies mod jars to a temporary root, so the caller's mod state is
never written. Captures live outside the repository.

## Menu taxonomy

| Class | Menus | Renderer generation |
| --- | --- | --- |
| A. Hub and dialogs | Game carousel, top tabs, chips, button row, help, quit/end prompts, missing ROM card, Add your games onboarding | `MenuChrome` |
| B. Settings forms | Settings (6 tabs, Controls sub-tabs), Advanced settings, Game Setup, save/discard prompt | `MenuChrome` |
| C. Pickers and editors | Locate ROM/path browser, text/numeric keypad editor, details pages | mixed: browser `MenuChrome`; editor, details `MenuStyle` |
| D. Libraries and managers | Mod manager (+ trust/detail pages), Recordings, Trace replays, Content import | `MenuStyle` / `TextSink` |
| E. Startup notices | Legal disclaimer, native-code mod notice | bespoke black page |
| F. In-session overlays | Hold-Escape toast, rewind HUD, trace HUD, shader picker, Stageplay dialogue, Mutator Lab configuration, editor project menu | mixed; not yet captured |
| G. ROM-native | S1/S2/S3K title screens, S2 options, S3K data select, competition menus, continue/results | ROM art; fidelity, out of scope |
| H. Mod-owned | Time Attack race lobby (`racing/time-attack`), example mods | mod owners; report only |

Two shared style systems coexist: `MenuChrome` (gold header text, key-cap footers,
breathing gold focus) and the older `MenuStyle` (white header text, text-only bold
footers, cyan focus outline, `>` cursor). Classes C–E are the older generation.

## Rubric

Each criterion is scored 1–10 from the 3× captures: 5 = functional but visibly
amateur, 7 = good indie quality, 9 = production polish. Overall is a holistic score,
not an average.

## Round 0 baseline

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 6 | 7 | 7 | 7 | 5 | 6 | 7 | **6.5** |
| B. Settings | 5 | 6 | 6 | 6 | 5 | 6 | 5 | **5.5** |
| C. Pickers/editors | 4 | 4 | 5 | 5 | 3 | 3 | 4 | **4** |
| D. Libraries | 3 | 3 | 4 | 4 | 2 | 3 | 4 | **3.5** |
| E. Notices | 5 | 3 | 4 | 5 | 2 | 3 | 5 | **4** |

Measured contrast: dimmest body text (`DIM` on navy) 6.19:1; hub hint line over the
sky 4.33:1 worst case. Contrast is adequate; hierarchy, consistency and iconography
carry the deficit.

### Evidence by class

**A. Hub.** Strong carousel, clear primary START. Weaknesses: the focus "sparkle"
corner ticks read as stray pixels at 1×; the quit dialog's subtitle ("Return to your
desktop") describes the non-default option while focus sits on "Return to menu", and
its buttons switch to mixed-case bold; top tabs are text-only; page dots are 3 px.

**B. Settings.** Coherent tab/row/description structure and good restart/scope
semantics. Weaknesses: truncated labels and values ("Widescreen HUD posi...",
"Centered native posi...", "target/trace..."); raw config values (`sonic`, `tails`,
`PRESENTATION`, `(empty)`, details shows `NATIVE_4_3`); inconsistent value case
(`OFF` beside `Off`) and label case ("Censor Profanity" beside sentence case);
"Player 1 Up" repeated under a PLAYER 1 tab; key bindings are bare text, not key caps;
Game Setup's "BKSP ALREADY STOCK"; amber means changed, per-game and restart at once;
a tall mostly empty save prompt with ASCII `->`.

**C. Pickers/editors.** The keypad's action row (`ABC SPC DEL CLR DEF < > |< >| OK`) is
cryptic and visually equal to the character grid; five stacked help/status lines; text
footers instead of key caps; details page switches to a black page, white mixed-case
title and a tiny Back chip; path browser truncates the path from the left into a
temp-directory name and leaves most of the page empty.

**D. Libraries.** Mod rows are bracket tag strings
(`[OFF] [S1] Infinite Sonic [BLOCKED,TRUST REQUIRED]`; light face, bold actions and footer), truncated, with no toggles or
game badges; the subtitle is an internal statistic ("Current pattern windows: 0/128");
the action row looks like plain text; footers are two lines of bold text. Recordings
show ISO timestamps and raw frame counts; Trace replays repeat "Zone: 00 Act: 0" rows.
Content import is the best of the class (icon, clear candidate card) but still
truncates its footer ("Es...").

**E. Notices.** Pure black page with dense, tightly led centred text; the native-code
notice has no heading or warning treatment; the prompt is plain text, not a key cap.

## Proposed targets

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 8 | 9 | 8 | 8 | 8 | 9 | 9 | **8.5** |
| B. Settings | 8 | 8 | 8 | 8 | 8 | 9 | 9 | **8.5** |
| C. Pickers/editors | 8 | 8 | 8 | 8 | 8 | 9 | 8 | **8** |
| D. Libraries | 8 | 8 | 8 | 8 | 8 | 9 | 8 | **8** |
| E. Notices | 8 | 8 | 8 | 8 | 7 | 9 | 9 | **8** |

Class F is captured and scored in round 1 with a target of 8. G is unscored; H is
reported to its owners.

## Hill-climb protocol

1. Pick the change with the largest expected gain across classes, favouring shared
   `MenuChrome` components over per-screen fixes.
2. Implement, recapture both tools at 320 and 400 logical widths, rescore every class.
3. Keep a change only if its target class improves and no other class regresses;
   otherwise revert and record why below.
4. Focused regression tests for each touched screen; existing layout tests stay green.

Planned levers, in expected-gain order: migrate classes C–E onto `MenuChrome`
(header, key-cap footer, gold focus, list rows, buttons); one documented type scale and
casing rule with human display names for every setting value; a small pixel icon set
(toggle, checkbox, game badge, warning, folder/file, film, lock); quieter focus without
corner ticks; distinct status colours for changed, per-game, restart and warning;
proportional advance for the light face to end truncation.

## Rounds

| Round | Change | Commit | Scores | Kept? |
| --- | --- | --- | --- | --- |
| 0 | Baseline | `9a796ec60f` | above | — |
| 1 | Unify classes C–E on `MenuChrome`; shared page/button/toggle/tag/badge/scrollbar/icons; open-corner focus without sparkle ticks; phase-matched wide page frame | round 1 commit | below | yes |

### Round 1 scores

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 6 | 7 | 7 | 7 | 6 | 7 | 7 | **7** (+0.5) |
| B. Settings | 5 | 6 | 6 | 6 | 5 | 6.5 | 5 | **5.5** |
| C. Pickers/editors | 6 | 6.5 | 6.5 | 7 | 6 | 7.5 | 6 | **6.5** (+2.5) |
| D. Libraries | 6.5 | 7 | 6.5 | 7 | 6.5 | 8 | 6.5 | **6.5** (+3) |
| E. Notices | 7 | 7 | 7 | 7.5 | 6 | 8.5 | 7.5 | **7** (+3) |

Kept: every older-generation page now uses the gold page header, key-cap footer,
breathing gold focus and framed buttons. Mod rows are switch + game badge + name +
right-aligned status tags ordered by actionability (`TRUST REQUIRED` shows as
`NEEDS TRUST`); the idle banner says "No unsaved changes" instead of implying a saved
draft. Recordings list length and local date with a plain-language summary; the
recording option is labelled "Pause at frame", matching `PAUSED_AT_TARGET`. Traces are
named by catalog identity with kind tags and length instead of "Zone: 00 Act: 0".
The keypad draws key backgrounds, arrow and space-bar glyphs and a gold OK key; the
character count moved into the field. Notices are left-aligned pages with open
leading; the native-mod notice shows a warning and one row per disabled mod.
Wide windows (400 px) draw the page frame across the margins in the column's
checker phase, removing the earlier seam.

Regression caught during the round: narrowing the trace failure text clipped the
50-character diagnostic lines; restored to a 300 px line. Tests whose assertions
encoded the old presentation (row strings, small details title, pinned keypad rows)
were updated to the new rule rather than relaxed.

Focused validation: 563 tests across the touched screens, hub, Settings and Game
Setup with all three ROMs, 0 failures, 0 skipped.

Remaining weakest points, feeding round 2: Settings value display names and casing,
label/value truncation, raw details values (`NATIVE_4_3`), the quit dialog's subtitle
and default, key bindings as plain text, overloaded amber, Mod manager's technical
pattern-window line and the sparse trust page.
