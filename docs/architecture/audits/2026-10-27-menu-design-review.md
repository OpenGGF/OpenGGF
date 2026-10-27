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
| 1 | Unify classes C–E on `MenuChrome`; shared page/button/toggle/tag/badge/scrollbar/icons; open-corner focus without sparkle ticks; phase-matched wide page frame | `f496d6e364` | below | yes |
| 2 | Settings/Setup values and casing, bindings as keys, shared save prompt, quit wording | `191701bfeb` | below | yes |
| 3 | Delegated: path browser, Mod manager clarity, class F capture and small overlay fixes; lead: `PixelFont` growth fix | `692d35bf5d`, `f3c71bf05c`, `c98859bf7e`, `de250fca8d` | below | yes |
| 4 | Delegated: shader picker, rewind/recording HUDs, Mutator Lab; lead: Settings leaders and switches, guard fixes | `1d140fdcdd`, `98bb767abd`, `d9f5af1d8a`, `0929b5d7e9`, round 4 integration commit | below | yes |

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

### Round 2 scores

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 6.5 | 7 | 7 | 7 | 6 | 7.5 | 8 | **7.5** (+0.5) |
| B. Settings | 6.5 | 6.5 | 6.5 | 6.5 | 6.5 | 7.5 | 7 | **7** (+1.5) |
| C. Pickers/editors | 6 | 6.5 | 6.5 | 7 | 6 | 7.5 | 6.5 | **6.5** |
| D. Libraries | 6.5 | 7 | 6.5 | 7 | 6.5 | 8 | 6.5 | **6.5** |
| E. Notices | 7 | 7 | 7 | 7.5 | 6 | 8.5 | 7.5 | **7** |

Kept: one casing rule (form values in sentence case; chips, tags, tabs and key caps in
capitals). Raw values gain display names (`sonic` → Sonic, `PRESENTATION` →
Presentation, empty → None, shader `OFF` → Off); Details shows the readable value and the
stored form only when they differ. Title-case labels became sentence case; the HUD
anchor's default reads "Native (centred)", ending the only label/value truncation on the
Display tab. Under the Player 1/2 groups rows name only the button, and bindings draw as
keys; the 10 px Controls rows use an outline-free key label because full caps stacked
into one another (rejected in the same round from the capture). Settings and Game Setup
share `MenuChrome.changePrompt`: the box fits its changes, each change reads as old
value ▸ new value, and buttons use the compact face at the original hit-test row. The
quit prompt pairs "Stay in OpenGGF" (default) with amber "Quit to desktop", matching
the end-game prompt; Game Setup no longer shows a key cap for a reset that does nothing.

Focused validation: 278 settings/setup/hub/menu tests with all three ROMs; one
assertion pinned the old `On -> Off` string and now checks the drawn pair; final rerun
of that class green.


### Round 3: delegated polish and class F baseline

Proportional light-face typography is deferred by the user: menu text is drawn by the
engine's own pixel font (no Swing/AWT), so variable advance would be a hand-authored
glyph-metric change across every layout site. The smaller items went to three Codex
`gpt-6.1-sol` (reasoning high) workers with disjoint file ownership; the lead kept
`MenuChrome`, the existing capture tools, the changelog and this audit. T3 created
the child worktrees from the main checkout's HEAD rather than this branch; each child
was redirected to `191701bfeb` before starting work.

| Worker commit (cherry-picked) | Class | Result |
| --- | --- | --- |
| `9cdfa8b94a` → `692d35bf5d` | C | Breadcrumb location with middle elision, folder/file icons, `UP`/`USE`/`ROM?` tags, item count, scrollbar, empty-folder state, shared buttons, contextual Enter label; ROM check/retry page on the shared chrome. `MenuPathBrowserCaptureTool` adds populated, paged, empty, folder-only, invalid-ROM and Settings-entry scenes. |
| `09e16f9337` → `f3c71bf05c` | D | "Art slots: N of 128" replaces the pattern-window jargon on the list (creator docs explain both terms); trust/cascade confirmations become structured cards (lead line with lock/warning, affected mods by name and id, consequences, Confirm/Cancel buttons; two-accept trust and Apply-only saving unchanged); details/notices/order pages separate field labels from values and give findings tone icons; a trust-only blocker shows `NEEDS TRUST` without `BLOCKED`; a leftover "Detail scroll" banner write was removed. `ModManagerDesignCaptureTool` adds reading-page scenes. |
| `7c7cc1c5b3` → `c98859bf7e` | F | First class F inventory and captures (`OverlayDesignCaptureTool`); hold-Escape toast in sentence case, editor project menu on `MenuChrome`, navy backing plates and a two-line desync notice for the trace HUD. |

Lead integration fixes:
- `PixelFont.ensureBatchCapacity` replaced the vertex array when a string outgrew it,
  discarding the glyphs already queued, so any first draw longer than 16 glyphs lost
  its start (shader picker hint, recording-interrupted notice). It now copies; a
  regression test checks the first glyph after growth.
- The ROM check page tolerates root or unparsable paths when naming the chosen file.
- `TestMenuGameOnboarding` clicked the old first browser row (y 60); the hit-tests moved
  with the redesigned rows, so the test now clicks the new row (y 76). The worker's
  focused set did not include this class; the lead's combined run caught it.
- Round 1's details-page wording broke `TestEditorProjectMenu`'s `+ More` pin, outside
  round 1's focused set; the overlay worker caught and updated it.
- Editor project header and footer labels follow the capitals rule.

Class F scores (worker evidence, lead-verified on the integrated captures):

| Overlay | Overall before → after |
| --- | --- |
| Hold-Escape toast | 7 → 7.5 |
| Live rewind HUD | 4.5 |
| Trace HUD (level and special stage) | 4 → 6 |
| Shader picker | 4.5 (first-draw text restored by the font fix) |
| Stageplay dialogue | 7 |
| Mutator configuration | 6.5 |
| Editor project menu | 6 → 7 |
| Input recording/playback HUD | 3.5 |
| Video recording indicator | 6 |
| User pause | 6.5 |

### Round 3 scores

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 6.5 | 7 | 7 | 7 | 6 | 7.5 | 8 | **7.5** |
| B. Settings | 6.5 | 6.5 | 6.5 | 6.5 | 6.5 | 7.5 | 7 | **7** |
| C. Pickers/editors | 6.5 | 7.5 | 7 | 7.5 | 7.5 | 8 | 7.5 | **7.5** (+1) |
| D. Libraries | 6.5 | 8 | 7.5 | 7.5 | 7.5 | 8 | 8 | **7.5** (+1) |
| E. Notices | 7 | 7 | 7 | 7.5 | 6 | 8.5 | 7.5 | **7** |
| F. Overlays | 5.5 | 6 | 5.5 | 6 | 5 | 5.5 | 6 | **6** (new; +1 from capture baseline 5) |

Focused validation for the integrated branch: 631 tests across every touched menu,
overlay and font class with all three ROMs (one onboarding failure, attributed above and
fixed; rerun 19/19), plus 130 editor/import/Stageplay menu tests with ROMs and the opt-in
EGL `TestEditorProjectPresentation`, all passing with 0 skips.

Next candidates, by expected gain: rewind and recording HUDs (compact integer-grid type,
backing, reserved placement; class F's weakest at 3.5-4.5), a `MenuChrome` shader picker,
Mutator Lab controls and footer, Settings hierarchy (tab/row/description density), editor
path display, and promoting the workers' breadcrumb formatter and label/value reading
lines into `MenuChrome`.

### Round 4: weakest overlays and Settings density

Workers (Codex `gpt-6.1-sol`, reasoning high), each redirected to base `de250fca8d`:

| Worker commit (cherry-picked) | Area | Result |
| --- | --- | --- |
| `890f27166b` → `98bb767abd` | Shader picker | Framed panel over the dimmed game with header, breadcrumb, filter field, folder/preset icons, `ACTIVE`/`GLSL`/`OPEN` tags, scrollbar, status line and key-cap footer; drawing moved out of `Engine` into `DisplayShaderPickerRenderer`; search text and download status kept; GL state restored and resources closed with the engine. 4.5 → 8. |
| `7329956d66` → `d9f5af1d8a` | Rewind and recording HUDs | Full-size pixel text on navy plates with transport glyphs; rewind sits above lives and recording at the lower right, never over the native score/time/rings/lives HUD (checked on S1, S2, S3K at 320 and 400); `GameLoop` delegates to `TransportHud`/`UserRecordingHud`. 4.5 / 3.5 → 7.5 / 7.5. |
| `d5b4b3f5bb` → `0929b5d7e9` | Mutator Lab | MenuStyle → MenuChrome: switches, Settings-style slider with a default mark, lifecycle tags (`RESTART NEEDED`), description box, contextual key caps; lifecycle guidance kept. 6.5 → 8. |

Lead slice `1d140fdcdd` (Settings and Game Setup): dotted label-to-value leaders, the shared
switch beside every on/off word, and the "changed from default" legend only on tabs that
show a marker. Rejected: moving Enter/F1 hints into the footer (overflows 320 px once the
unsaved count shows, dropping Change); sectioned rows (catalogue sections are too coarse).

Lead integration fixes:
- Shader names: vowel-less words read as initialisms (CRT, LCD, GLSL); Mutator rows show the
  switch before the word like Settings, and its slider Enter hint reads STEP, not a second CHANGE.
- `-Pguards` found four new failures, all from this round's and earlier rounds' tooling, and
  one more after the first fix: `tools -> editor`/`testmode` ratchet edges and Stageplay
  ownership (overlay tool moved to test scope; `MenuDesignCaptureTool` uses an inert proxy
  import host instead of naming the editor type), the audio backend bypass (tools ask `GameplayCaptureSession.usesNullAudioBackend()`, which
  compares the boot's own installed backend through `AudioManager.hasInstalledBackend`; a first
  attempt added a public `AudioManager` method, but `AudioManager` is `@ModApi`, so the commit
  hook rejected the Mod API surface change), the audio presentation
  layer inventory (`DisplayShaderPickerRenderer` moved from `graphics.shaderlib` to
  `com.openggf`), and `GameServices.graphics()` in runtime-owned `LiveRewindHudOverlay`
  (the graphics manager is now passed from `GameLoop`). Moving `MenuDesignCaptureTool` to
  test scope was tried and reverted: raw GLFW and temporary-root guards apply there.
- Both menu capture tools now delete their temporary roots; 248 + 429 leftover task
  directories in `/tmp` were removed.
- Pre-existing guard failures, identical on `9a796ec60f` (matched message hashes): S3K static
  session-state debt, assertion-free diagnostics, object-update clock terminology, rewind
  coverage baseline, and the TraceChaser PowerShell forwarder (no `pwsh` here).
- The S3K ROM in the main checkout hashes to CRC32 `0C06AA82` / SHA-1 `B711A909…`, not the
  documented `63522553`; S3K-backed results in this task are layout/menu evidence only.

Validation: combined focused set 1,226 tests with all three ROMs and surfaceless EGL (0
failures; 2 opt-in skips: shader-pack diagnostic and checkpoint-cost measurement); rerun of the
classes touched afterwards 246 (0 failures, 1 opt-in skip); `-Pguards` 805 tests with only
the five pre-existing failures above.

### Round 4 scores

| Class | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Hub | 6.5 | 7 | 7 | 7 | 6 | 7.5 | 8 | **7.5** |
| B. Settings | 6.5 | 7 | 7 | 7 | 7 | 8 | 7.5 | **7.5** (+0.5) |
| C. Pickers/editors | 6.5 | 7.5 | 7 | 7.5 | 7.5 | 8 | 7.5 | **7.5** |
| D. Libraries | 6.5 | 8 | 7.5 | 7.5 | 7.5 | 8 | 8 | **7.5** |
| E. Notices | 7 | 7 | 7 | 7.5 | 6 | 8.5 | 7.5 | **7** |
| F. Overlays | 6.5 | 7 | 6.5 | 7.5 | 7 | 7 | 7.5 | **7** (+1) |

Remaining gaps to target: hub iconography (text-only top tabs, tiny page dots), notices
iconography, typography everywhere (fixed-advance face, deferred), the trace HUD's small
debug type, the video recording indicator and the user pause indicator.

