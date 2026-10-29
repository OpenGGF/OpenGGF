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

Proportional light-face typography was deferred by the user at this round (it landed
later; see "Follow-up: proportional menu typography" below): menu text is drawn by the
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


## Handover (2026-10-28)

### Goal and how it was pursued

The owner asked for every engine-owned menu to be classified (classes A–H above),
scored on the seven-criterion rubric and improved by hill climbing toward production
polish, with full control of the design language. Targets were A/B 8.5 and C–F 8. Each
round picked the largest shared gain (favouring `MenuChrome` components over per-screen
fixes), recaptured with the offscreen tools, rescored, and kept only non-regressing
changes. Rounds 3–4 split small items across Codex `gpt-6.1-sol` workers with disjoint
file ownership; the lead owned `MenuChrome`, the capture tools, integration, the
changelog and this audit. Final scores after round 4: A 7.5, B 7.5, C 7.5, D 7.5, E 7, F 7.

Follow-up from an owner-relayed review (2026-10-28): rewind and recording plates were
legible but used too much gameplay space. The rule is now the `ui-design-language` skill
(`ef5ecd6e5e`): gameplay overlays use one row ideally and two at most, abbreviated labels,
content-sized plates and meters instead of percentages; debug and trace replay displays
are exempt. `be8ffe7a6f` applies it to `TransportHud`: `REW F 1248`, `REC F 1248`,
`PLAY 1248/3600`, `FF …`, `HOLD SHIFT+REC` with a meter, and a second row only for desync,
missing sidecar or recording error text. A one-row plate is 16 px tall against 36–50 px
before. Farrell's `5822c28de9` then polished it:
- word-space gap between label and value;
- translucent navy fill and outlined glyphs, with `!` for warnings;
- `ERR` / `STOP` titles;
- a failed BK2 verification opens the full trace panel (`RecordingReplayDiagnostics`).

That commit pins long counters as truncated to the plate budget. **Open question:** with
the 9 px word gap, an ordinary `PLAY 1248/3600` already exceeds the 144 px cap and shows
`PLAY 1248/...`. A counter-preserving alternative (never truncate the value, fit the title
around it, and show only the current frame when a playback total does not fit) was written
and dropped unpushed because it contradicts that pin. Settle it with the owner before
changing either behaviour.

### State

- Worktree `.worktrees/menu-design-review`, branch `feature/ai-menu-design-review`
  (pushed, tracks origin), base develop `9a796ec60f`. No PR exists.
- Validation of the compact plates: before `5822c28de9`, the four HUD classes passed
  15/15 with `--lean`. That commit's own validation is not recorded here. The plates were
  **not recaptured**, and guards were not rerun after round 4. Round 4 validation is
  recorded above.
- Captures (outside the repo): `$HOME/openggf-captures/menu-design-review/`, with
  `r0-baseline/` as the before set, `r4-final/` as the current menus, overlays and Mutator
  Lab, worker before/after sets in `r3-*`/`r4-*`, and `scripts/sheet.py` for contact sheets.

### Next, in suggested order

1. **Settle the counter question above, then recapture the compact plates** with `TransportHudDesignCaptureTool <ROM> <new-dir> [320|400]`
   on S1, S2 and S3K (surfaceless EGL, from the main checkout so ROMs resolve) and rescore F.
2. **Other gameplay overlays under the new budget:** the video recording indicator and the
   user pause indicator are the remaining class F gaps. The trace HUD is exempt.
3. **Iconography:** hub top tabs are text only and its page dots are tiny; the startup
   notices have no icons.
4. **Proportional typography (done; see the follow-up section below).** *Correction:*
   menus do not draw with the PNG `PixelFont` described here. Since round 1 they use
   `MenuPixelFont`, whose authored `MenuGlyphs` masks give a light 6×8 face and a
   derived bold 9×10 face; the PNG font (26×5 grid of 16×16 cells, 9×10 glyphs) remains
   the debug/gameplay font. The original leads, kept for the record:
   - A per-glyph advance table could be authored, or derived at load from each glyph's
     lit columns, behind `PixelFont.measureWidth` and the batch quad positions, so callers
     that already measure follow automatically.
   - Keep digits tabular so counters do not jitter.
   - `com.openggf.mods.ui.NameFont` already reads a proportional pure-Java BDF font
     (Fusion Pixel 10px, OFL, in `src/main/resources/fonts/`). It shows a working
     no-AWT loader and advance model, but it is `@ModApi`; do not widen that surface by
     accident.
   - Expect breakage wherever code assumes 9 px columns: the text keypad, the caret in
     the text editor, column-aligned tables, the `fit` truncation loops and tests that pin
     exact x coordinates (about 44 main-source files use `PixelFont` or
     `PixelFontTextRenderer`).
   - This is the largest remaining typography lever; every class scores 6.5–7 there.
5. **Merge preparation:** a non-draft PR to develop runs smoke and guards.

### Pitfalls met here

- T3 `delegate_task` worktrees start from the main checkout's HEAD, not this branch;
  brief workers to `git reset --hard <base>` before starting.
- The guards reject several patterns:
  - `tools -> editor` and `tools -> testmode` edges;
  - Stageplay values outside the named hosts;
  - graphics depending on game code;
  - `GameServices` calls in runtime-owned code;
  - raw GLFW or unmanaged temporary roots in test scope.
  The round 4 fixes above show the accepted shapes.
- `AudioManager` is `@ModApi`; any new public member trips the signature pins and the
  commit hook.
- Five guard failures predate this branch (listed under round 4).
- The main checkout's S3K ROM is not the canonical dump (CRC32 `0C06AA82`), so S3K results
  here are layout evidence only.
- Commits need all seven trailers. Release prose lives in `CHANGELOG.0.7.prerelease.md`
  under the existing menu entry; fold follow-ups into it rather than adding new entries.


## Follow-up: proportional menu typography (2026-10-28)

The owner asked for item 4 of the handover. Base `f347e271dc`; design by a separate
Opus 5.5 pass, implementation here.

**Font owner.** `MenuPixelFont` draws every `MenuChrome`/`MenuStyle` menu from the
`MenuGlyphs` masks. Its fixed advance came from two places: the font (`length × 6/9`)
and roughly 90 caller sites (`MenuChrome.compactWidth`/`labelWidth`, `MenuStyle.fit`
cell counts, and per-screen `length() * 6`, `/ 6` and `/ 9` arithmetic). Changing the
font alone would have desynchronised measurement from drawing.

**Rejected: crop-only advance.** Deriving advance from each mask's lit columns leaves
the monospace texture: 71 of the 94 printable masks already use all five columns
(measured on `f347e271dc`; the design brief estimated 75), so only punctuation, `I` and
a few lowercase letters would narrow. Kept instead:
- nine lowercase masks redrawn narrower for the proportional faces (`i` 1, `l` 2,
  `f`/`t`/`j` 3, `r`/`c`/`s`/`z` 4 columns; `k` was already 4); capitals, `m`/`w`,
  digits and descenders unchanged;
- advance = ink width + 1; bold ink spans the mapped columns {0,1,3,5,6} with
  2-pixel strokes, plus one spacing column;
- tabular digits (6 light, 9 bold) drawn from the cell origin; spaces 3/4 px; arrows,
  check, cross, middle dot and bullet keep a whole cell;
- quads sample only the ink columns, so draw and measure share one code-point walk,
  integral pen positions and the same fallback normalisation (a surrogate pair is one
  `?`).

**Fixed compact face preserved.** Redrawing shared rows would have changed the
just-tuned `compactMenu()` pixels even with fixed metrics. `MenuGlyphs` therefore keeps
the original rows for `Face.FIXED` and holds the proportional masks separately; a
`compact()` font uploads only the fixed atlas. `TransportHud`, `UserRecordingHud` and
`TraceHudOverlay` measure through that renderer, so `F:` counters, key chords and the
fixed 6 px cells are unchanged.

**Callers.** `MenuChrome` widths delegate to the GL-free `MenuPixelFont.textWidth`;
`MenuStyle` fits by width (longest code-point prefix plus `...`, dots for tiny widths)
and adds `wrap`, `chunks`, `marquee`, `elideMiddle` and `elideStart`. Migrated sites:
text-editor field scroll, caret and keypad centring; Settings marquee and description
wraps; path-browser and shader-picker breadcrumbs; details, launch and settings change
summaries; hub error paths, card wraps, name marquee and fitted labels; Mod manager
reading-line wraps, row names and counters (through a non-API `TextSink.width`);
content import detail; Mutator Lab wraps; editor project previews, options and buttons;
editor HUD/text fitting; native-mod notice pre-init wrap; recording and trace-picker
failure lines (width chunks that keep every character).

Validation: 52 focused menu, HUD and overlay classes with the three original ROMs and
surfaceless EGL, 468 tests, 0 failures, 1 opt-in skip (`TestEditorHudPictures`). Layout
pins that assumed fixed cells now measure with the production face, for example the
hub action bar and the hub name centring. New checks cover glyph ink bounds,
measured-width-versus-quad edges and gaps, integral vertices, symbols, fallbacks,
surrogate pairs, tabular digits, the unchanged fixed compact art and metrics, pixel fit
and wrap, and the editor caret. `-Pguards` ran 805 tests: only the four pre-existing
failures from round 4, with none in files this change touches. The PowerShell forwarder
now passes.

Captures: before/after sets at 320 and 400 from the same tool sources, compiled against
`f347e271dc` classes and against this change (S2 REV01 for ROM-backed tools). Every hub,
menu, browser, Mod manager and shader-picker image changes. All 9 transport images and
the trace, recording, Stageplay, video-indicator and pause overlays are pixel-identical,
so the fixed compact face is unchanged. The sets live outside the repository in
`$HOME/scratch/agent-tmp/codex/menu-typography-20261028/`.

### Integration with the unified playback browser

Integrated `b9a90fcd6` with the browser hill climb `eb4cc3279` and parent review
`9ab3bc181`. The obsolete `TestModeTracePicker` stays deleted; the replacement
browser, recording details and `TracePlaybackFeedback` use `MenuFieldLines` and
proportional `MenuChrome` widths. The two remaining browser/recording test stubs
now measure production metrics rather than 6/9-pixel cells.

A combined 19-class focused run executed 173 cases with zero skips. It caught one
new error: keyboard-only Mod manager navigation supplies no text sink, and the
new wrapping code dereferenced it. The correction retains the plain sink's cell
metrics when no drawing sink exists. The affected seven-class rerun passed 109
cases with zero failures, errors or skips, including that original failing test,
Mod manager behavior, ArchUnit, the Mod API surface, menu dispatch and GL setup.
The other 172 combined cases passed before this isolated Mod manager correction.
Commands (Java 25, desktop displays unset, surfaceless EGL):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestMenuPixelFont,TestMenuStyleFit,TestMenuTextEditor,TestMenuFieldLines,TestPlaybackBrowser,TestPlaybackBrowserPresentation,TestPlaybackLibrary,TestPlaybackLauncher,TestMasterTitlePlaybackBrowser,TestMasterTitleTraceRetry,TestUserRecordingMenu,TestTracePlaybackFeedback,TestUserRecordingControls,TestChildMenuFeedback,TestMasterTitleScreenLayout,TestMasterTitleHubMessages,TestArchUnitRules,TestTransportHudPresentation,TestTraceHudOverlay' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestChildMenuFeedback,TestModManagerScreen,TestModManagerScreenHost,TestArchUnitRules,TestModApiSignatureSurface,TestRawGlfwTestSetupGuard,TestNativeMenuRenderDispatch' test
```

The first command additionally set `openggf.playback.captureDir` to the external
`playback-menu-hill-climb-20261028/combined-typography/` directory. It rendered all
20 browser/panel states at 320 and 400 pixels with synthetic catalogs; these are
UI evidence only. `combined-typography-comparison.html` compares them with the
preceding browser hill climb. The separate typography comparison is
`menu-typography-20261028/comparison.html` beside the original capture sets.

The change-based plan against pinned destination `4f150bc0a` selected 4,247 smoke
classes because menu code is shared/unclassified. The 52-class typography pass,
browser checks, combined integration selection and affected structural guards
replace that disproportionate engine-wide selection: no frame executor, physics,
ROM pipeline or gameplay state changed. No broad smoke or release full suite was
run. The four inherited full-guard failures remain as previously attributed in
the playback design; they are outside the changed inputs. Skill mirrors and
staged whitespace checks pass. A further owner-requested browser hill climb will
start only after this typography integration lands, with every rubric criterion
and browser surface targeted at 8 or better and fresh captures required.

## All-menus hill climb to 8 (2026-10-28)

The owner asked for every engine menu family, not only the Playback browser, with each
of the seven criteria and each family's overall at 8 or more. Base `64ed120d2`
(proportional typography, browser hill climb and the code-point wrap review). Scores
are subjective design judgments from 1× and 3× production captures, not measurements.

**Evidence.** `$HOME/scratch/agent-tmp/codex/all-menus-target8-20261028/` holds
`baseline/` (exact `64ed120d2`, captured before any edit), `round1/`, `round2/` and
`final/`, each as `<width>/<group>/` at native scale with nearest-neighbour `x3/`
copies and a `provenance.json` (HEAD and dirty files). Groups: `hub`, `hub-paused`
(paused-game scenes driven by a held gameplay frame; first captured in round 2),
`menus`, `browser`, `mods`, `shader`, `overlays`, `transport`, `mutators` (Mutator Lab
with `examples/example-mutators` built by `examples/build_example.py`; first captured
in round 1, whose only shared change to it was description leading), `playback`
(`TestPlaybackBrowserPresentation`, 20 states × 2 widths, synthetic catalogs) and
`final/editor-hud` (opt-in `TestEditorHudPictures`, 1600×900). `scripts/capture.py`
reproduces a set; `scripts/diff.py` lists changed and identical images;
`comparison.html` (≈300 KB, self-contained, lossless palette PNGs) shows 24
baseline/final pairs. ROM-backed tools used S2 REV01.

Capture-tool corrections: `OverlayDesignCaptureTool` drew trace and recording plates
with the debug font, not the production `compactMenu()` face, so its baseline trace
plates showed truncation (`Tab...`, `TRACE COMP...`) that the engine does not; it now
uses the compact face and adds the manual trace details page (`07b`).
`MasterTitleCaptureTool` adds the Settings reset prompt (`51`) and the window-mode
preview prompt (`52`).

### Inventory and dispositions

| Family | States captured (final) | Disposition |
| --- | --- | --- |
| A1 Hub carousel, tabs, chips, actions | hub 01–06, 38, 43, 50 | scored |
| A2 Paused-game card and zoom | hub-paused 19–24, 32–37 | scored (no `64ed` capture) |
| A3 Quit, end-game and change prompts | hub 17, hub-paused 21–22 | scored |
| A4 Missing ROM and Locate ROM | hub 39, 40, browser p01–p08 | scored |
| A5 Controls and help | hub 16, 46 | scored |
| A6 Add your games | hub 49 | scored; found/scanning states not captured (need real discovery), code-inspected |
| A7 Advanced tools page | hub 15 | scored (capture lacks editor/import handlers: 3 of 5 rows) |
| B1 Settings tabs | hub 11–13, 29, 31, 41, 44, 47, 50; menus m02, m04 | scored |
| B2 Controls subtabs | hub 14, 45 | scored |
| B3 Advanced settings | menus m05–m07 | scored |
| B4 Game Setup | hub 07, 08, 10, hub-paused 24–26 | scored |
| B5 Save, discard, reset and preview prompts | hub 09, 41, 42, 51, 52 | scored |
| C1 Path/ROM browser | browser p01–p08 | scored |
| C2 Text and keypad editor, caret | menus m03, m07, m24, m25 | scored; numeric keypad shares the renderer, not captured separately |
| C3 Details pages | menus m01 | scored |
| D1–D6 Playback library, empty, details, options, trace loading, failures | playback 01–20 | scored |
| D7–D9 Mod manager list, details/notices/order, trust/dependency | menus m12–m14, mods d01–d08 | scored |
| D10 Content import | menus m22, m23 | scored |
| E1 Legal notice, E2 native-mod notice | menus m20, m21 | scored |
| F1 Shader picker | shader 01–09, overlays 16 | scored |
| F2 Mutator Lab | mutators 17, 18 | scored |
| F3 Editor project menu | overlays 09–11 | scored |
| F4 Editor HUD (menu, wheel, properties, libraries) | editor-hud (15 pictures) | scored; own `EditorHudTheme` |
| F5 Hold-Escape toast | overlays 01–03 | scored |
| F6–F10 Transport, trace HUD/details, recording, video, pause | transport 04–25, overlays 04–08, 07b, 13–15, 19–21 | scored |
| Stageplay dialogue | overlays 12 | Stageplay presentation, 7; each cue authors its box for that face, so a font change re-flows every screenplay |
| Debug and performance panels | — | debug-exempt instruments, not menus |
| G ROM-native title/options/save/results | — | fidelity references |
| H Mod-owned (Time Attack lobby, example mods' menus, private lobby) | — | drawn through the pinned public `CreatorUi`/`NameFont` surface by their owners; not restyled |

### Score matrix (final)

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 Hub | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | crowded chip row |
| A2 Paused card | 8 | 8.5 | 8 | 8 | 8 | 8 | 8.5 | **8** | resume flash |
| A3 Prompts | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | — |
| A4 Missing ROM | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | — |
| A5 Help | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | repeated D-pad rows |
| A6 Onboarding | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | found list uncaptured |
| A7 Advanced tools | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | — |
| B1 Settings tabs | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | dense Display tab |
| B2 Controls | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | 10 px rows |
| B3 Advanced settings | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | — |
| B4 Game Setup | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | paused description box |
| B5 Save/reset/preview | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | — |
| C1 Path browser | 8 | 8 | 8 | 8 | 8.5 | 8 | 8 | **8** | — |
| C2 Text editor | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | `DEF`/`CLR` key names |
| C3 Details | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | lone Back button |
| D1–D6 Playback | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | long trace failure page |
| D7–D9 Mod manager | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | notices keep raw codes |
| D10 Import | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | — |
| E1–E2 Notices | 8 | 8 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | — |
| F1 Shader picker | 8 | 8 | 8 | 8 | 8.5 | 8 | 8 | **8** | — |
| F2 Mutator Lab | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | mod-authored Title Case names |
| F3 Editor projects | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | sparse action page |
| **F4 Editor HUD** | 7.5 | 7.5 | 7.5 | 8 | 8 | 7 | 7.5 | **7.5** | all-caps menu, truncated items |
| F5 Escape toast | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | — |
| F6–F10 Gameplay plates and trace | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | trace details density (exempt) |

Baseline at `64ed120d2` with the same evidence: Settings Gameplay/Files tabs switched to
bold rows (consistency 6.5); description lines touched (type 7); capture values raw
(`ffv1`, `0`; clarity 6.5); Settings details a prose block (`saved as NATIVE_4_3`, 6.5);
pause and video were bare debug-font text, the pause text overlapping the playback
plate (6); the toast covered the score digits (7); trace loading 6.5; Mod order plain
text (6.5); editor project menu hand-coloured (7); Mutator Lab's location line sat on
the held HUD (7); the editor HUD library title overlapped its count (6.5).

### Rounds

| Round | Commit | Kept |
| --- | --- | --- |
| 1 | `b6ff18fe7` | One light row face on every Settings tab; `DESCRIPTION_PITCH` 9; capture labels and values (FFV1, 192 MB, Automatic); structured Settings details and label-sized details columns; `PAUSE`/`VIDEO` plates in new `TOP_RIGHT` lanes replacing `LiveCaptureIndicatorRenderer`; production face in the overlay tool; Advanced rows with icons and notes; missing-ROM and launch-button glyphs; trace loading card with identity, facts and ordered steps; Mod order list; editor project menu on shared buttons and tones with the named error; keypad typing key caps; native notice panel fitted and code-point word splitting. |
| 2 | `fe0671095` | Add your games icons and notes, found-game check and badge; informational empty Playback scopes with measured wrap and no idle Details hint; Mod details value column via `TextSink.readingField` (plain sinks keep the full text); `Status: <message>` for eligibility reasons; Mutator Lab dim, location band and quiet guidance; toast right of `GAMEPLAY_HUD_RIGHT`. |
| 3 | this commit | `+N MORE` chip; header scope tags on the notices; Controls key labels with a dark base; Game Setup drops its idle status when the box is full; `UNDO` key; editor action chevrons; reset prompt matching the quit/save prompts; window-mode preview with a draining meter (`DisplaySettingsTrial.remainingFraction`); trace details in menu tones; editor HUD widths measured in the drawn case. |

Rejected or reverted:
- Humanising Mod finding and compatibility codes (`Warning: Art overlap.`). Seven
  `TestModManagerScreen` assertions pin the exact codes because creators search docs
  and logs by them; only eligibility reasons became words.
- A triangle instead of the editor form's `+`/`-` section prefix: tests and the
  survey read the prefixed text; kept the prefix in cyan.
- Ten-pixel description pitch: Game Setup's 41 px box cannot hold three lines and its
  key line; nine pixels fit every state.
- Icons on the hub's top tabs: the crowded five-tab row would overrun the logo at 320.
- Humanising "not allocated in the effective catalog": a disabled mod is not the only
  cause, so the plainer claim would be wrong.

### Remaining below target

- **Editor HUD (7.5).** Its BODY/LARGE labels are capitalised by `EditorHudCanvas`
  design, the editor menu truncates `LEVEL PALETTE (GENESIS COLO...` and shortcut
  columns are ragged. Remaining work: sentence-case menu rows, a wider or wrapped menu
  column and right-aligned shortcut caps under the editor's own theme. Only the
  measured-case overlap regression was fixed here.
- Stageplay dialogue (7) is disposed above; the found/scanning onboarding states and
  the numeric keypad lack pictures; the paused-hub and Mutator Lab sets have no `64ed`
  baseline images.

### Validation

Java 25, desktop displays unset, surfaceless EGL, three original ROM paths:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.editor.hudPictures=true \
  -Dsonic1.rom.path=... -Dsonic2.rom.path=... -Ds3k.rom.path=... -Dopenggf.playback.captureDir=<evidence>/final/playback \
  '-Dtest=<62 menu, HUD, overlay, editor, guard-adjacent classes>' test   # 633 tests, 0 failures, 0 skips
python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B             # 805 tests, only the 4 inherited failures
```

The guard failures are the inherited set recorded in the playback design: rewind
coverage `MhzEndBossInstance#objectRef#hitProxy`, S3K static debt
(`HCZWaterSkimHandler.slotRetired`, `Aiz2BossEndSequenceState.cutsceneKnucklesSlot`),
clock wording (`TestTasS3kWaterSkimLifetime`, `TestTasS3kOffscreenSolid`,
`TestTasS3kOrtExhaustion`) and assertion-free `TestTestReelExtension#bodyPasses`.
Earlier focused runs per round: 280 (7 failures from pinned old presentation, all
updated to the new documented behaviour or reverted as above), 85, 206 (1), 11, 152,
59 (1), 1, 98; each failure was resolved before the next round. No broad smoke or
release suite was run: changes are menu/overlay presentation plus one capture-tool
refactor, with no frame executor, physics, ROM pipeline or gameplay state changes.

## All-menus target 8: completion round (2026-10-28)

Base `df4cd400b`. This round finishes the editor HUD family that stayed at 7.5, captures
the states the previous round left unpictured, checks simultaneous engine-session
overlays, and inventories the Stageplay editor and repo-shipped mod menus. Scores remain
subjective design judgments from inspected production pictures, not measurements.

**Evidence.** `$HOME/scratch/agent-tmp/codex/all-menus-target8-completion-20261028/`
(`README.json` records provenance). `before/` is exact `df4cd400b` production drawing:
the editor/Stageplay HUD pictures carry only test-side additions (palette, settings and
scrolled-menu states), and the engine groups were captured with `Engine.java` and
`TransportHud.java` restored to `df4cd400b` while the capture tools already carried the
new scenes, so every new state has a true before picture. `round1/`, `round2/` and
`final/` follow; engine groups' final is `round1/` (no later engine edits). Editor HUD
pictures are window pixels at 1280x720 and 1600x900, i.e. UI scale 2 with 640x360 (the
narrowest HUD) and 800x450 UI units, not the 320 game frame. `comparison.html` (276 KB)
shows 13 before/final pairs. Hub clouds, the locate-ROM temp-folder name and a focus
pulse phase differ run to run independently of these edits.

### Added inventory

| Family | Captured states | Disposition |
| --- | --- | --- |
| A6 Add your games, pending | hub 53 checking, 55 scanning | scored; the frame is the update that starts the real `MenuLoadTask`, so it never races the reader |
| A6 Add your games, found | hub 54 (real identify of the canonical S2 file), 56 (real read-only scan of the main checkout: S1/S2/S3K found, scan limit reached) | scored; scratch configuration only, ROMs never copied or linked |
| A7 Advanced, all tools | hub 57 (editor projects and import bound); also the real Engine startup (`round1/time-attack/03-extras.png`) | scored |
| C2 Number keypads | menus m26 integer, m27 decimal | scored |
| F4 Editor HUD | 19 states x 2 window sizes (adds palette, level settings, scrolled and last menu page) | scored |
| F11 Simultaneous session status | overlays 22 toast + pause, 23 toast + stopped video + pause, 24 toast + video + pause | scored; Engine's own renderers and toast placement on a draw-only facade |
| Stageplay editor (forms and HUD chrome) | `stageplay-hud` 72 pictures (`TestStageplayHudPictures`) | scored; its forms use `EditorProjectMenuRenderer`, its HUD the editor canvas |
| Time Attack (bundled `racing/time-attack`) | `round1/time-attack` name prompt, menu, host mode, vote pools, settings (real Engine startup, isolated working directory) | scored, below target (see below) |
| Time Attack lobby, server browser, invite, private room, quick chat | not captured | need a live LAN/room session; see below |
| Example mods with menus (`act-practice`, `sonic3k-chaos-mode`, `sonic-survivors`, `starpost-valley`) | not captured | creator example content showing the Mod API; not engine product menus. Mutator Lab (`example-mutators`) stays F2 |

Stageplay dialogue remains screenplay-authored narrative artwork (7, disposed in the
previous round): it is not a menu and its boxes are authored per face. ROM-native screens
and debug/performance instruments are unchanged dispositions.

### What changed

- **Editor HUD.** `EditorHudCanvas.text` no longer capitalises BODY/LARGE text; it draws
  and measures the string it is given, so width and drawing cannot drift. Callers choose
  case by role: crumbs, chips, page titles and prompt labels pass capitals; menu rows,
  sheet fields and values are sentence case in the light face; card headings stay bold.
  The menu measures its width from the widest row (`EditorHudPainter.menuGeometry`), shows
  shortcuts as key caps in one left-aligned column, and has a scroll bar; "Level palette
  (Genesis colors)" and "Clear region / cancel paste" read whole. Object properties and
  Level settings share one field sheet: light rows in a measured label column, the
  selected value between arrows, one gold-edged selection band (the menu's), the object
  name beside the heading, and no help lines that repeated the prompt bar (the properties
  one also promised a text editor that does not exist). Authored enum values read as words
  (`NONE` to None). The palette dims the stage, numbers its four lines, shows the chosen
  colour large with its place and whether it is host owned, and shows each channel as the
  0-7 hardware step with an eight-segment meter. The prompt bar steps from bold labels to
  tighter gaps to the light face until it fits; at 640 units the world and block prompts
  had run off the right edge since before `df4cd400b`. The breadcrumb's `»` (absent from
  the bold face, drawn as `?`) is a drawn chevron. The object library names its set in
  the subtitle and moves the internal-object toggle to the prompt bar; its focused tile is
  no longer clipped at the grid's top. Workbench cells read `X 1  Y 1`.
- **Toast and top-right plates.** At 320 px the held-Escape toast (x 120-275, y 6-23)
  overlapped the `PAUSE` plate (x 261-312, y 8-24), and a stopped-video plate overlapped
  it at 320 and 400. `Engine.escapeToastRestY` now rests the toast just below any
  top-right plate it would touch; plates keep their slots and the toast keeps its slide,
  the HUD-column clearance and the top band. Plate rectangles come from the new
  `TransportHud.bounds`, measured with the same face, and visibility follows the plates'
  own rules (including the screenshot-key pause hide).
- **Add your games** shows a file or folder icon, the location being read (breadcrumb)
  and an indeterminate meter while checking or scanning, and lines found games up on one
  column. **Number keypads** name Delete, Clear and Default (Undo) in their five wide
  cells; the ten-column keyboard keeps DEL/CLR/DEF.

Rejected: pixel chevrons for the sheet's value arrows (two screen pixels tall beside
8-unit text; the light-face `<`/`>` replaced them); a comma in "Line 1, color 0" and
"1, 1" (the light and bold commas read as full stops at these sizes; reworded).

### Score matrix (completion, changed or newly scored families)

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A6 Onboarding | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | scan-limit notice wording (real result) |
| A7 Advanced (5 rows) | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | dense five-row list at 320 |
| C2 Text and number keypads | 8 | 8 | 8 | 8 | 8 | 8 | 8 | **8** | empty cells on the integer pad's sign row |
| F4 Editor HUD | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | light prompt labels at 640 units; "Every command" repeats rows above it |
| F5/F11 Toast with session plates | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | toast slides past a plate while entering (transient) |
| Stageplay editor forms | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | long survey forms |
| Stageplay editor HUD chrome | 7.5 | 7.5 | 7.5 | 8 | 7 | 7.5 | 7.5 | **7.5** | prose key line, shortcut letters inside labels ("Direct camera C") |
| Time Attack menus | 5.5 | 6 | 6 | 6.5 | 4 | 5 | 5.5 | **5.5** | truncated subtitles and hint lines, no key caps |

Other families keep the previous final matrix; their captures in this round are
pixel-identical apart from the nondeterministic inputs above, and the Stageplay HUD's 72
pictures are identical to `df4cd400b` (its two bold labels now pass explicit capitals).
Paused-hub and Mutator Lab still have no `64ed120d2` baseline; their final states were
scored from `df4cd400b`-era captures, with no invented before images.

### Remaining below target

- **Time Attack (5.5).** Repo-owned and editable, but its views draw through the public
  scene canvas with the creator text face (wide fixed cells), so subtitles and hint lines
  truncate and keys read as prose. Reaching 8 is a dedicated redesign of eight views
  (menu, name and invite entry, settings, vote pools, profile, lobby, server browser,
  private room, quick chat) within the Mod API surface, with its behaviour tests; the
  public `NameFont`/creator contract must not change for it. Lobby, browser, invite and
  room states also need a loopback LAN fixture to be captured. Next action: a Time Attack
  UI hill climb using `TimeAttackVideoCaptureTool` (its `openTimeAttack` route is stale:
  it now reaches Time Attack only when the bundled-mod manifest from `package` is present).
- **Stageplay editor HUD chrome (7.5).** Its key line is prose and some action labels
  carry their shortcut letter; key caps and a separate shortcut column would follow the
  editor HUD's new menu. Not attempted in this round.

### Validation

Java 25, desktop displays unset, surfaceless EGL, canonical S2 REV01 path:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestDiagnosticWidescreenLayout,TestGameLoop,TestTransportHud,TestTransportHudPresentation,TestEngineLiveCapturePresentation,TestEscapeToMasterTitleController,TestMenuGameOnboarding,TestMenuTextEditor,TestMenuPathBrowser,TestEditorHudLayout,TestEditorHudControls,TestStageplayHud,TestArchitecturalSourceGuard" test
# 307 tests, 0 failures, 0 skips
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dtest=TestEditorHudLayout,TestEditorHudControls,TestStageplayHud,TestArchUnitRules,TestDiagnosticWidescreenLayout" test
# after the last painter edit: 71 tests, 0 failures, 0 skips
scripts/editor_pictures.sh final   # TestEditorHudPictures at 1280x720 and 1600x900 plus TestStageplayHudPictures: 10 tests, 0 failures, 0 skips
```

New checks: menu rows untruncated and shortcut caps within one column at 640/800/1280
units; every prompt bar fits 640 units at world, block and chunk depth with pad and
keyboard prompts; the canvas measures the string it draws; the toast clears every
simultaneous plate combination at 320 and 400 and at least one combination moves it.
The engine capture tools fail rather than picture a wrong onboarding state. No broad smoke
or release suite was run: the changes are menu/overlay presentation, one toast placement
helper and capture tools, with no frame executor, physics, ROM pipeline or playback
authority changes.

## All-menus target 8: final families (2026-10-28)

Base `efae20d69`. This round takes the two families the completion round left below 8,
the bundled Time Attack views (5.5) and the Stageplay editor HUD chrome (7.5), to 8 on
every criterion, and inventories the example mods' menus. Scores remain subjective
design judgments from inspected production pictures, not measurements.

**Evidence.** `$HOME/scratch/agent-tmp/codex/all-menus-final-families-20261028/`
(`README.json` records provenance; `comparison.html` shows 17 before/final pairs).
`before/` is `efae20d69` drawing, `round1/`, `round2/` and `final/` follow.

- **Time Attack**, 61 states at 320 and 400 logical width (1x window, nearest `x3/`
  copies), from the real Engine startup and the bundled mod: name prompt and its error,
  menu in every mode, vote pools and pool editor, settings and each editor, retry-key
  capture, profile, LAN invite entry, connecting, room password, member and host lobby
  over the lobby level, quick chat, ready and hidden-menu states, member and host invite
  (masked and revealed), room details, room access form (public, private unset, private
  set), room browser (empty, two rooms, create row) and join by code. Network states use
  a test-owned loopback fixture (`TimeAttackStates.java`): the capture runs in an
  `unshare -rn` namespace whose only interface is loopback, a real racing master server
  in its test configuration, and scripted `RaceSession` peers that host a private room,
  join the engine's room, chat, ready up and list rooms. Peers load from the test classes
  in their own class loader, so the engine resolves Time Attack only from the bundled jar
  through the normal mod pipeline (the `before/` set predates that isolation: its Time
  Attack classes came from `target/test-classes` compiled from the same `efae20d69`
  sources). The fixture paces frames at 60 Hz: unpaced frames published lobby poses
  above the host's 66/s limit and the host dropped the engine, a fixture artifact.
- **Stageplay HUD**, the opt-in `TestStageplayHudPictures` (83 pictures; the completion
  round's committed `efae20d69` set is the baseline), adding a 1280x720 cast page, camera
  and actor takes in progress, fractional zoom and pad prompts. The pad pictures set the
  input's controller presentation, the state a pad press leaves, because the picture
  fixture has no pad device.
- **Act Practice** through the real startup with the example loaded as the development
  mod (`ModStates.java`, `-Dggfmod.dev.modDir` as `ggfmod run` does).

`TimeAttackVideoCaptureTool` now takes a logical width, fails before navigating when the
classpath lacks the bundled Time Attack manifest (`package` writes it), and checks that
Extras opened the Time Attack scene rather than any mod scene.

### What changed

- **Time Attack.** A mod-owned page helper, `openggf.timeattack.ui.MenuPage`, built only
  from the public scene canvas, `NameFont`, `CompactFont` and `TextLayout`, echoes the
  engine chrome without importing it: header band with gold rule, bold title and a scope
  chip, light `NameFont` rows in a measured label column with stepper triangles, a
  gold-edged focus band, a gold-edged primary launch button, and a footer of key caps
  (`CompactFont` capitals) that drops leading hints only when they do not fit and names
  pad buttons after a pad press. Sentences wrap by measured width (a word wider than the
  line splits between graphemes, never inside a glyph). Every view uses it: menu (mode
  subtitles, game names, Characters/Round length wording, invite row with a lock, the
  status as a toned notice or the ghost facts), text entry (prompt, gold field box,
  caret, character allowance, an empty masked field reads "Nothing entered yet", a typed
  one a fixed row of dots), settings (TLS trust in amber with its meaning, retry key as a
  cap), profile (preview plate, channel meters, a legend drawn with the marks it
  explains), vote pools (Hosting tag, check boxes, scroll bar, act count and limits),
  room access (two-line options with compact switches, buttons), invite (manual invite
  wrapped whole above a help line that no longer runs under the Hide button), room
  browser (cards with routing, chat and verification facts, an informational empty
  state, page row) and room details (Players and Chat sections, newest messages that
  fit). The lobby cards keep the in-race HUD style but take a key-cap plate, wrap notices
  inside the menu card within the room its actions leave, and the host card no longer
  repeats the chat mode its own CHAT row shows. Hints name what the focused row does
  (Enter on a field row moves to the next row, so it reads Next, not Start), and once
  ready over the lobby level the keyboard hint reads Enter Play, because Enter is Start.
- **Stageplay HUD.** The prompt bar is the editor HUD's `EditorHudPainter.promptBar`
  (now package-shared with its glyphs and key caps) with a mode chip: DIRECTING, CAMERA,
  REC CAMERA/POSER/PUPPET TAKE, FRACTIONAL ZOOM or COMMANDS, then the keys for that mode
  (pad buttons after a pad press; a pad shows only what its buttons do). Panel buttons
  carry their shortcut as a cap at the right edge (or the pad glyph), Commands rows show
  real keyboard shortcuts in a cap column, `Action` labels lost their `(C)`/`(K)`/`(R)`
  suffixes, the top button reads Level editor with an F4 cap, the list counter dropped
  its prose, and take statuses no longer repeat the keys the bar shows.
- **Act Practice** (example): header band, a wrapped description, the game in a framed
  card between arrows, and key/label pairs that wrap as pairs instead of running off the
  right edge.

Rejected: a three-pixel-wide checker on the Time Attack page (invisible at 1x); drawing
the directing chip at 640 UI units (the eight directing prompts need the whole bar there,
so the chip yields; every other mode keeps it); `TextLayout.wrap` for the Act Practice
footer (it collapsed the key-label gaps and split ESC from BACK); a 3x capture window for
Time Attack evidence (see below).

### Inventory and dispositions (this round)

| Family | Captured states | Disposition |
| --- | --- | --- |
| Time Attack menu, name/text entry, settings, profile, vote pools | 30 states x 320/400 | scored |
| Time Attack LAN/room: invite entry, connecting, password, room access, lobby, quick chat, invite, details | 25 states x 320/400 (loopback fixture) | scored |
| Time Attack room browser and join by code | 6 states x 320/400 (loopback master) | scored |
| Time Attack in-race HUD and in-race quick chat | not captured | gameplay overlays, not menus; unchanged |
| Stageplay editor HUD chrome | 11 chrome states incl. takes, fractional zoom, pad prompts | scored |
| Stageplay editor forms | 72 pictures unchanged in role | stay 8 (completion round) |
| Act Practice (example, Extras scene) | 2 states | scored |
| Mutator Lab (`example-mutators`) | completion round | control, 8 |
| Other examples (`eggmans-sky`, `emerald-remix`, `flappy-tails`, `hello-scene`, `infinite-sonic`, `putt-putt-paradise`, `robotnik-tower-defense`, `second-strike`, `sitar-hero`, `slay-the-robotnik`, `sonic3k-chaos-mode`, `sonic-survivors`, `stageplay-demo`, `starfall-frontier`, `starpost-valley`, `super-sonic-bros`, `three-islands`, `typing-of-the-hedgehog`) | not reached | not scored, see below |

Only Act Practice registers an Extras title entry; every other example is a patch mod
whose own menus appear after its base game launches. In the isolated capture profile the
master title listed all three games but START opened Add your games instead of launching
the base game, so the fixture never reached those menus (`before/mods/sonic-survivors`,
`sonic3k-chaos-mode`, `starpost-valley`). They are unscored, not claimed at 8. Next
action: give the capture profile explicit per-game ROM settings, confirm START launches
the patched base game, then script each example's own menu route.

Engine observation, not changed here: with a 3x capture window the hosted lobby level
and its scene overlay drew at 1x into the bottom-left of the window, while the 1x window
draws correctly. It was seen only through the display-free capture tool; whether a real
scaled desktop window shows it was not established. Owner: hosted-run presentation
(`HostedRunController` overlay pass, `ModSceneHost.drawOver`).

### Score matrix (final families)

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Time Attack views | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | lobby cards keep the HUD's compact capitals; empty text-entry pages are sparse |
| Stageplay editor HUD chrome | 8 | 8 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | directing chip yields at 640 UI units |
| Act Practice (example) | 8 | 8 | 8 | 8 | 8 | 8 | 8.5 | **8** | a single control, so no focus state |

Before (`efae20d69`): Time Attack 5.5/6/6/6.5/4/5/5.5, overall 5.5 (cut subtitles and
hints, a revealed invite running off the right edge under the Hide button); Stageplay
chrome 7.5/7.5/7.5/8/7/7.5/7.5, overall 7.5 (prose prompt bar, shortcut letters inside
labels); Act Practice 5/5/5/6/4/5/4, overall 5 (its key line ran off the screen). Other
families keep the earlier final matrices.

### Validation

Java 25, desktop displays unset, surfaceless EGL, original ROM paths (S2 REV01 canonical):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path=... -Dsonic2.rom.path=... -Ds3k.rom.path=... \
  "-Dtest=TestAttemptInputRecording,TestAttemptRecordingVault,TestCoordinatorVoteInput,TestGhostCaptureAndPlayback,TestGhostFileCodec,TestGhostStore,TestHudTextLayout,TestInviteCodeView,TestLegacyTimeAttackSettings,TestLobbyAreas,TestLobbyInviteController,TestMenuPage,TestMinimapLayout,TestMultiplayerHudLayout,TestMultiplayerRaceCoordinator,TestNetworkEndpointPolicy,TestRaceLobbyView,TestRaceSession,TestRecordingUploader,TestRemoteGhostPresentation,TestRoomAccessView,TestServerBrowserView,TestTextEntry,TestTimeAttackAttempt,TestTimeAttackCommunicationSettings,TestTimeAttackMenuState,TestTimeAttackModPackage,TestTimeAttackQuickChat,TestTimeAttackRuntime,TestTimeAttackRuntimeMultiplayerSeams,TestTimeAttackScene,TestTimeAttackSettings,TestTimeAttackSettingsPersistence,TestTimeAttackSettingsVotePools,TestTimeAttackTimeFormat,TestTimeAttackTrackCatalog,TestTimeAttackTrackCatalogRomValidation,TestViewInput,TestVoteTrackPools,TestStageplayHud,TestEditorHudLayout,TestEditorHudControls,TestArchUnitRules,TestArchitecturalSourceGuard" test
# 534 tests, 0 failures, 0 skips
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestRaceSession,TestRaceLobbyView" test
# after the last notice wording: 66 tests, 0 failures, 0 skips
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.hudPictures=true \
  -Dsonic2.rom.path=... "-Dtest=TestStageplayHudPictures,TestStageplayHud" test
# 23 tests, 0 failures, 0 skips (writes the 83 Stageplay pictures)
```

New checks: every menu mode fits 320 and 400 with rows above the footer and keys naming
what the focused row does; a long status draws whole; the page helper wraps without
splitting glyphs, keeps Back while dropping only overflowing hints, and draws caps inside
their measured width; lobby cards keep their rows inside the card with a wrapped notice;
every Stageplay mode's prompts fit 640 and 800 UI units (with the chip outside plain
directing), pad prompts name pad buttons, and Commands rows carry the real shortcuts with
none left in labels. The test canvas's `shows` finds text in every face, so masking
checks (passwords, invites) now cover `NameFont` and compact capitals too. No broad smoke
or release suite was run: changes are mod-owned menu presentation, two HUD helpers, an
example scene, capture tooling and wording, with no gameplay, network protocol, physics
or ROM pipeline changes; the parent owns delivery-wide checks.

## Lead verification and delivery scope (2026-10-28)

The owner explicitly selected **engine and bundled menus only** when asked whether
this pass should also redesign the 18 optional example games. Those optional game
UIs remain uncaptured/unscored; they do not block this engine-menu delivery and are
not claimed at 8. ROM-native screens retain fidelity and authored narrative dialogue
is not an interactive menu. The engine families in the matrices above, including
editor and Stageplay authoring chrome, and the bundled Time Attack views now each
score at least 8 on all seven criteria and overall in Opus's capture-based design
judgment. Act Practice was also improved along the way. These are subjective scores,
not measured guarantees of usability.

The lead integrated the completed local commits by fast-forward through `c9ecf1b49`;
there were no additional source conflicts. The completed focused runs on their exact
source states remain the menu evidence: 633 cross-family cases at `df4cd400b`, 307
editor/overlay cases plus a 71-case final affected rerun and 10 picture cases for
`efae20d69`, and 534 Time Attack/Stageplay cases with a 66-case final wording rerun,
23 Stageplay picture/control cases and two Act Practice cases for `c9ecf1b49`.
These overlap; they are not added into a claimed distinct total. Their final reports
had no failures or skips. The four inherited full-guard failures remain as attributed
above; no broad smoke or release suite was claimed.

### Scaled-capture correction

The reported 3x lobby issue was reproduced in the capture host at a 960x672 framebuffer
and 320x224 logical screen. Both GL and cached viewports were correctly 960x672, but
sprites and the hosted menu occupied native-sized coordinates in its bottom-left.
`HeadlessGameBoot(width, height, context)` had installed a framebuffer-sized local
matrix override. `Engine` installed its own logical projection source afterwards,
but the local override takes precedence. This is a capture composition-root error,
not evidence of a desktop Engine scaling defect.

`TimeAttackVideoCaptureTool` now clears that boot override after Engine installs its
presentation source. A new ROM-free EGL regression exercises both 320 and 400 logical
widths at 3x: it requires the live Engine matrix and verifies that a logical top-right
overlay rectangle reaches the physical top-right pixel. The focused command passed
52 cases with zero failures, errors or skips on `c9ecf1b49` plus this correction:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestTimeAttackVideoCaptureTool,TestModApiSignatureSurface,TestRawGlfwTestSetupGuard,TestArchUnitRules' test
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package
```

The package command passed with tests explicitly skipped and prepared the bundled
Time Attack manifest/jar. The production startup/loopback lobby fixture was then
recaptured at 3x: sprites and menu now fill their correct logical positions throughout
the framebuffer. Before/corrected PNGs and exact capture provenance are retained under
`all-menus-final-families-20261028/scaling-check/` beside the main evidence. The probe
stopped deliberately after `ta30-lobby-member` with its `finally` cleanup; that expected
probe stop is not a test failure or a suite pass. A preliminary mixed-classpath probe
failed to open Time Attack because the package resources belonged to a different
code root; it supplied no corrected-frame evidence and was replaced by the actual
parent package. The mirrored headless-rendering skill records the matrix-ownership
pitfall.

The final change-based plan against pinned destination `64ed120d2` selected 4,248 smoke
classes because shared menus are unclassified. The completed screen-specific behavior,
pixel and structural checks above replace that disproportionate engine-wide run for
this presentation-only delivery. The actual code states and opt-in skips are recorded
per round rather than presenting these checks as a full-suite pass. Instruction/skill
mirrors and staged whitespace checks were also verified.

## Hill climb toward 9 (2026-10-28)

Base `a694db926` (the pushed target-8 delivery). The owner authorised a pass over the
engine and bundled menu families only (the 18 optional example-game UIs stay excluded)
toward 9 on every criterion: dense screens, crowded states, interaction polish, motion
and native-resolution finishing. Scores remain subjective design judgments from
inspected production pictures, not measurements or human usability results.

**Evidence.** `$HOME/scratch/agent-tmp/codex/menu-target9-20261028/` with `baseline/`
(exact `a694db926`: every engine capture group at 320 and 400, Time Attack 61 states x
2 widths, editor HUD at 1280x720 and 1600x900, 83 Stageplay pictures, 20 Playback states
x 2 widths), `baseline-motion/` (the toast slide sequence below, captured with the
unchanged `Engine`), `round1/`-`round3/` and `final/`, each with `provenance.json`.
`scripts/` holds the reusable capture drivers adapted from the target-8 tasks
(`capture.py`, `ta_capture.py` with `TimeAttackStates.java`, `pictures.sh`, and
`round.sh`, which runs package, engine tools, the loopback Time Attack fixture and the
queued picture tests strictly in sequence). The first baseline Time Attack run at 400
stopped after 34 pictures because a concurrent queued picture run in the same worktree
rewrote `target/test-classes` under it; 400 was rerun alone on the unchanged tree.
`OverlayDesignCaptureTool` gains scenes 25/26: every frame of the hold toast sliding in
over the VIDEO and PAUSE plates and sliding out after release.

### Ranked deficits (baseline pixels)

| Rank | State | Deficit | Expected benefit |
| --- | --- | --- | --- |
| 1 | Toast entering over VIDEO + PAUSE (`baseline-motion/320/overlays/25-*` f02-f04) | the toast slid down through both plates for three frames before resting below them | no transient overlap: the rule held only at rest |
| 2 | Controls and help (`hub/16`, `46`) | six unordered rows; on a pad three identical `D-PAD U/D` caps for three destinations | reading order (move, choose, shortcuts), no repeated caps |
| 3 | Hub with crowded chips (`hub/38`) | `+3 MORE` hid rewind, debug and mods with nowhere to read them | the whole setup readable without opening Game Setup |
| 4 | Stageplay directing at 640 units | the mode chip yielded so eight prompts fit | the mode named in every state and width |
| 5 | Editor HUD menu end (`menu-end`) | "Every command" repeated Copy/Cut/Paste/Delete/Fill/Flood/Clear/Save/Export rows listed above with shortcuts; "Toggle v flip" | each command once, readable labels |
| 6 | Time Attack lobby (`ta30`, `ta47`) | action rows in compact capitals (READY UP, LEAVE ROOM), "Full Chat" in Title Case on Settings | menu rows match the rest of the mode |
| 7 | Controls bindings (`hub/45`) | 10 px pitch: 9 px key labels stacked into a ladder | separated rows |
| 8 | Paused card (`hub-paused/19`, `24`) | "Aspect, rewind, debug and controls apply on resume" vs "Rewind, debug and aspect apply when you resume" | one phrasing |
| 9 | Mutator Lab options (`mutators/18`) | "Applies at Resume" in the band and on every row | a calmer page |
| 10 | Trace failure pages (`playback/04`, `17`, `18`) | "ACKNOWLEDGE" names no consequence | the page says where you go |
| 11 | Mod details (`mods/d02`, `d03`) | labels kept a colon inside an aligned column; Settings details do not | one label-column style |
| 12 | Editor projects (`overlays/09`) | Close before Help, unlike every other page's back-last order | consistent footers |

### What changed

- **Toast motion.** `Engine.escapeToastOrigin` gives the toast's corner for each presence
  value. On the top row it still slides down from above; displaced below a top-right plate
  it slides in from the right edge along its resting row. `escapeToastRestY` now also
  requires that row to be clear between the toast and the right edge: the shorter
  "Returning to games" message rested at y 26 beside the lower PAUSE plate at 320, so a
  side slide would have crossed it (the new test failed on exactly that before the fix).
  `MenuChrome.holdToast` gains an x-offset overload.
- **Controls and help** draws Move, Choose and Shortcuts section captions (the Game Setup
  caption style), sentence-case actions, `Tab`, `M` and `F1` shortcuts for the keyboard, and
  for a pad a single Up/Down row with two quiet note lines (Mods: in the top tabs; Game
  Setup: below the game). The legend draws the amber marker it explains.
- **Hub.** With chips cut to `+N MORE`, the card summary names the hidden ones in sentence
  form ("Also rewind on, debug on, 2 mods"), using the same chip phrasing as the Start
  summary (`HubSetupChips.phrase`); if that line would not fit, the old summary returns.
  The paused card's Game Setup hint reads "Aspect, rewind and debug apply on resume".
- **Settings Controls** rows step 11 px from y 67: two pixels between key labels, the first
  band clear of the group tabs and the last clear of Reset bindings.
- **Editor HUD** menu: the generated list is "More commands" and omits actions already
  listed with shortcuts; flip commands read "Toggle horizontal flip" / "vertical".
- **Stageplay HUD**: the mode chip moved from the prompt bar to the top bar beside Level
  editor (the level editor already shows mode chips there), so it shows in every mode at
  640 and 800 units and the prompt bar keeps its full width.
- **Time Attack**: lobby action rows draw in `NameFont` sentence case (Ready up, Start round
  (waiting), Rule < By size >, Chat < Full chat >, Name and look, Room invite, Leave room);
  an empty chat reads "No messages yet" in the row face; `MenuPage.sentence` gives Settings
  "Full chat" to match the lobby and room browser.
- **Smaller fixes**: trace failure footer `Back to list`; Mod details aligned labels without
  the colon (the reading line keeps "Label: value" for plain sinks and tests); editor
  project footer Help before Close; Mutator Lab options band shows the location.

Rejected or not attempted:
- Shedding the Stageplay `C Camera` prompt (its cap is already on the Direct camera button)
  to make room for the chip at 640: it saves 62 of the 76 units needed (measured with the
  production `promptFit`), so it could not keep all other prompts.
- Lobby notices and status labels in `NameFont`: at 320 the host card's notice room is 45 px
  after nine 12 px actions; a 13 px face ellipsizes the two-line "No short code: share the
  LAN invite." notice, so notices and status tags (WAITING FOR, FULL CHAT) stay compact
  capitals.
- Humanising "contains code - trust required (press accept twice)": catalog data pinned by
  `TestEffectiveCatalogBuilder` and `TestModManagerScreen`; changing it is a Mod-manager
  wording pass, not a presentation fix.
- Per-action notes on the editor project page: `EditorProjectMenu.Action` is shared by
  Stageplay, import and editor callers; a note field is a model change beyond this pass.
- Reducing the white confirm flash at the start of the resume zoom: frames 35-37 read as one
  continuous motion with no plate overlap; no defect found.

### Interaction and motion observations

All interaction evidence is scripted production input (real `InputHandler` key and pad
events, real screen owners), not human study. Covered by the capture tools and focused
tests: hub navigation and paused-card resume/end (MasterTitleCaptureTool), Controls
subgroup switching and rebind row focus (`TestEngineSettingsScreen`), locating a ROM
(MenuPathBrowserCaptureTool, `TestMenuPathBrowser` in earlier rounds), finding and
launching recordings and traces and recovering from a failed launch
(`TestPlaybackBrowser`: the new case moves to the second trace, fails twice and dismisses
with Enter and then Escape; the same row stays selected and the browser stays open),
save/discard prompts (hub 09/41/42), editor menu and Stageplay modes including pad prompts
(`TestEditorHudControls`, `TestStageplayHud`), and the Time Attack lobby through a real
loopback room with scripted peers. Motion: the toast's ten entry frames and its exit after
release at 320 and 400 (`final/<w>/overlays/25-*`, `26-*`) never touch either plate; the
geometry test checks every 1 % presence step at 320, 400 and 528 for every plate
combination and message. Resume zoom (`hub-paused/32-37`) and the hub slide (`02`) were
inspected frame by frame. Focus movement and list scrolling were inspected only as the
existing before/after frames, not as sequences.

### Score matrix (toward 9)

Changed or re-inspected families; every other family keeps the final target-8 matrix (8 on
every criterion and overall) with unchanged pixels apart from the known nondeterministic
inputs (hub clouds, temp-folder names, focus pulse phase).

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Evidence / what still blocks 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 Hub | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 9 | **8.5** | `hub/38` names hidden chips; chips still overflow to `+N MORE` at 320 |
| A2 Paused card | 8 | 8.5 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | one phrasing for apply-on-resume; two muted lines under the buttons remain |
| A5 Help | 8.5 | 9 | 8.5 | 8 | 8.5 | 9 | 9 | **8.5** | grouped reading order, no repeated caps; pad page leaves the lower third empty |
| B2 Controls | 8 | 8 | 8.5 | 8 | 8 | 8 | 8.5 | **8** | separated 11 px rows; eight dense rows remain at 320 |
| D1-D6 Playback | 8 | 8 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | failure names its consequence; long reasons still read as one paragraph |
| D7-D9 Mod manager | 8 | 8 | 8 | 8 | 8 | 8.5 | 8 | **8** | one label-column style; catalog status text unchanged |
| F2 Mutator Lab | 8 | 8.5 | 8 | 8 | 8 | 8 | 8.5 | **8** | no triple timing line; mod-authored Title Case |
| F3 Editor projects | 8 | 8 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | back-last footer; the action page is still sparse |
| F4 Editor HUD | 8 | 8 | 8 | 8 | 8 | 8.5 | 9 | **8.5** | each command once; generated labels stay mechanical ("Increment selected subtype") |
| F5/F11 Toast with plates | 8.5 | 8.5 | 9 | 8.5 | 8 | 8.5 | 9 | **8.5** | clear in motion and at rest; the progress edge still crosses a glyph mid-fill |
| Stageplay editor HUD chrome | 8 | 8.5 | 8.5 | 8 | 8 | 9 | 9 | **8.5** | chip at every width, matching the editor's top bar; dense prompt bar at 640 |
| Time Attack views | 8.5 | 8 | 8 | 8 | 8 | 8.5 | 8.5 | **8** | lobby rows in the row face, one chat-mode case; status tags and notices stay compact capitals |

No family reached 9 overall in this pass. The remaining blockers are structural rather
than polish: the 320-wide hub cannot show every chip; the lobby's 224-line card cannot
hold sentence-case notices beside nine actions without a layout redesign (for example a
two-column action grid or a notice row above the cards); the editor project page needs
per-action notes in the shared `EditorProjectMenu.Action` model; generated editor
command labels need authored names; and trace failure reasons are single long values
from their owners. Each is a viable next action with a named owner above.

### Validation

Java 25, desktop displays unset, surfaceless EGL, canonical S2 REV01 where ROM-backed:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=... \
  "-Dtest=TestEngineSettingsScreen,TestMasterTitleHubMessages,TestHubSetupChips,TestDiagnosticWidescreenLayout,TestGameLoop,TestMenuRenderAlignment,TestEditorProjectMenu,TestEditorHudControls,TestEditorHudLayout,TestStageplayHud,TestModManagerScreen,TestTracePlaybackFeedback,TestPlaybackBrowserPresentation,TestRaceLobbyView,TestRaceSession" test
# round 1: 313 tests, 3 failures (toast side path, Stageplay chip, lobby mode text), each fixed;
# rerun of the four affected classes: 149 tests, 0 failures, 0 skips
python3 tools/testing/maven_queue.py -Dmse=off ... "-Dtest=TestMasterTitleHubMessages,TestMutatorCatalogueScreen,TestMutatorWorldIntegration,TestMutatorSessionState,TestRaceLobbyView,TestMenuPage,TestTimeAttackSettings,TestTimeAttackScene,TestServerBrowserView,TestRoomAccessView,TestTimeAttackCommunicationSettings" test
# 144 tests, 1 failure (settings chat-mode case), fixed; rerun 2 tests, 0 failures
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestEditorCommandPalette,TestEditorHudControls,TestEditorHudLayout,TestStageplayHud,TestEditorProjectMenu" test   # 51, 0 failures
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestPlaybackBrowser" test   # 12, 0 failures
scripts/round.sh final   # package -DskipTests, all capture tools, Time Attack 61 x 2, picture tests 10 + 1, 0 skips
# final state, one run: the 26 changed/consumer classes above plus TestModApiSignatureSurface,
# TestArchUnitRules, TestArchitecturalSourceGuard and TestTimeAttackVideoCaptureTool
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=... "-Dtest=<those 30 classes>" test
# 571 tests, 0 failures, 0 errors, 0 skips
```

These runs overlap; they are not a distinct total. Guards (`-Pguards`) were not rerun:
no guard-owned budget file changed, and the source guard and ArchUnit classes above passed.

New checks: the toast clears every plate at every presence step (and a displaced toast
takes the side path); Controls rows step 11 px within their bands; the editor menu lists
each region and project command once and keeps the other actions; Stageplay prompts fit
the whole bar at 640/800 with the widest chip leaving 200 units for the scene name; a
dismissed trace failure keeps the failing row with either key. No broad smoke or release
suite was run; the parent owns delivery-wide validation.


### Fresh critical review and lead repairs

The independent Opus 5.5 high review of `ea7df5c1a` (the same code tree as
`518c0535d`, integrated with the required co-author paragraph spacing) inspected
native and 3x pictures and the code without rerunning tests. Its full findings and
annotated hub/lobby pictures are in the existing evidence directory's
`review-opus55/`. It did not accept an overall 9 for any family: crowded 320-wide
hub, help, editor command sheet and Stageplay remain at 8; the toast remains 8.5.
Notices were independently judged 8.5. These remain design judgments.

The review found that eliding the Stageplay scene name also removed its trailing
unsaved `*`, made more likely by the narrower name area beside the new mode chip.
The lead now reserves the marker's measured width before fitting only the name;
the production draw uses that complete result. A regression covers elided names at
640 and 800 UI units beside the widest take chip, checks the marker and right
edge, and retains the clean/short-name controls. The lobby's chat-disabled message
now names Settings > Gameplay, and its helper comments and racing guide labels
match the current presentation.

The next implementation must address the review's structural recommendations:
remove duplicate donor/team status, make hidden status complete on START and
preserve per-game markings; simplify lobby waiting status and fix the self-row
character contrast; author grouped editor command descriptors with real shortcut
caps; place Game Setup's donor before its dependent character choice; add project
action notes and improve exact-error reading order. Copy updates do not change
searchable finding codes, verifier policy, persistence or input ownership.

The review also invalidated earlier trace-status picture claims: the overlay
capture fixture always supplied rewind, masking error/desync/complete states.
Those states need corrected captures, not inherited scores. Playback's purported
second failure page was identical to its first because the reason fit on one
page. Crowded eight-player lobby and focus/scroll sequences remain evidence gaps.
These must be repaired before the target-9 assessment. The original evidence and
scores above remain as historical records, with these limitations applying.

Lead repair validation (exact staged code on `ea7df5c1a`, Java 25, EGL selected,
DISPLAY/WAYLAND_DISPLAY unset):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestStageplayHud,TestRaceLobbyView,TestMenuPage' test
```

47 tests passed, zero failures/errors/skips (16 Stageplay HUD, 28 lobby view,
3 shared mod-page helpers). Queue admission waited 82 seconds; Maven reported
1 minute 32 seconds execution including compilation. No broad smoke or release
suite was run for these bounded repairs. The next production capture round will
also picture a long dirty Stageplay title.

## Structural pass toward 9 (2026-10-28)

Base `e52d5ec3e` (the review's lead repairs D1, D4, D5 and D6 included and kept). Scope as
before: engine and bundled menus; the 18 optional example games, native ROM screens,
narrative dialogue and debug instruments stay excluded. This pass implements the fresh
review's structural route (R1-R8) and evidence gaps (G1-G4) rather than more copy rounds.
Scores remain subjective design judgments from inspected production pictures.

**Evidence.** `$HOME/scratch/agent-tmp/codex/menu-target9-20261028/structural/`:
`before/` (exact `e52d5ec3e`, every group at 320 and 400, Time Attack 61 x 2, editor HUD
1280x720/1600x900, Stageplay, Playback), `round1/` (hub, paused hub and Time Attack after
Round 1), `round4/` (all groups after Rounds 2-4, uncommitted tree; its overlay group stopped
on the new distinctness check, see G1), `final/` (all groups at the committed code SHA),
each with `provenance.json`, plus `gallery.html` and the adapted `scripts/`. Rounds 2 and 3
were not pictured separately: their focused tests ran per round and `round4/` pictures the
three together. `scripts/round.sh` now builds the example-mutators jar after packaging and
runs the Maven picture tests before the capture JVMs, so later source edits cannot leak into
a label (the first `before` attempt lost `com/openggf/game` classes when a dependency-build
compile failed on a work-in-progress edit; the remaining groups were recaptured after a clean
base rebuild, and the 320 groups already written came from the intact base build).

### Review findings: resolution

| Item | Resolution |
| --- | --- |
| D1, D4, D5, D6 | lead repairs in the base, kept; the long dirty Stageplay title is now pictured (`final/stageplay-hud/long-dirty-title*.png`) |
| D2 hub status | `HubSetupChips.status` names every chip cut to `+N MORE` on the card and on Start, amber runs after the legend's marker; the count is amber when it hides a per-game value; dead `startSummary` removed |
| D3 help | groups Move / Select / Shortcuts; Left/Right reads "Next or previous game, button or tab"; the pad page lists its Help button (Y); keyboard and pad tests check every hub key once and the drawn geometry at 320/400 |
| R1 | one `FROM SONIC 3&K` chip, "Team chosen on the Sonic 3&K save screen" in words; donor chip before team; exhaustive sweep over every Game Setup-reachable profile x global rewind/debug/aspect x 0/1/12 mods at 304/384 px: chips fit, none repeat, every hidden chip named on one line |
| R2 | readiness is one sentence ("Waiting for Sid" / "Waiting for 3 players" / "Everyone is ready" / "Ready is optional"); notices in `NameFont`, whole in two lines at 320 and 400; `Ready rule < >` and `Room chat < >` (Settings' own name, chosen over the review's "Chat mode"); character tags in `MUTED` (>= 4.6:1 on the self band over a white backdrop, measured) |
| R3 | `EditorCommandPalette.Descriptor` (label, group, real shortcut, context) for every action, pan excluded with its reason; the HUD menu is captioned Edit / Region / Pattern and tile / Objects / View / Project; pad controls on a pad; `HostCommand` carries its key (`Stageplay workspace` + `F4` cap, no key inside the label); every shown shortcut is pressed through the real HUD front end and `EditorInputHandler`; Ctrl+0 no longer also selects hotbar slot 10 |
| R4 | Borrow from leads Players; rows flow from the item list, so Team sits directly under its donor and Screen/Extras close up (S3K donor: Aspect 83, Mods 135; otherwise unchanged 96/148) |
| R5 / G4 | frame sequences: `seq-hub-slide`, `seq-display-down` (nine Downs, five frames each), `seq-controls-rebind` (listen, key, updated row), Playback `21-seq-dense-down-*` and `22-seq-marquee-*`, Mod `seq-trust-k01/d06/k03`, Time Attack `ta62-traverse-*`, toast `27-escape-pause-off-*` / `28-escape-pause-on-*` |
| R6 | the hold toast's progress is a 2 px meter on its bottom row (track plus gold), nothing fills behind the glyphs; a plate leaving eases the row over six frames when every row of the path is clear (`EscapeToastMotion`), a plate arriving under it moves it at once |
| R7 | project actions carry caller-authored notes (two-line 22 px rows, shared geometry for draw and pointer); import shows facts, consequence and action once each; keypad accept reads Set; Mutator Lab names the shared timing once in the band (single-line rows when nothing differs), the switch row reads Enabled, choice names keep capitals; trust copy "Contains code: switch it on, then confirm Trust and enable" (`CODE_TRUST_REQUIRED` unchanged); trace failure headline by failure class above the exact `Error` field |
| R8 | the native notice reads MODS NOT LOADED and "Your mod list keeps them on; start the Java build of OpenGGF to use them." (verified: `NativeUnsupportedMods.compute` only reads the startup intent, nothing is written) |
| G1 | the trace states carry no rewind; `07c-trace-rewind` is the deliberate control; `requireDistinct` fails the capture when the states draw the same picture. It failed once more after the rewind fix: the sample model gave errors to every state and errors outrank DESYNC, so the desync and complete states now carry none. Details open by a real Tab press on the level trace (desync and error states) and on the special-stage trace. All earlier 05/06/07 pictures are invalid as evidence |
| G2 | the Playback fixture's error wraps past one page (`final/playback/*-19-*` differs from `-18-`); `TestTracePlaybackFeedback` pages a 40-row error with Left/Right and finds every row on some page |
| G3 | eight-player host lobby with long names, chat history and the host notice (`ta63`, players page 2 `ta64`), real loopback peers; the host's per-address limit (4, `JdkHostLimits`) refused the fifth 127.0.0.1 peer, so crowd peers dial private addresses added to the namespace's own `lo` (10.77.0.2-9), still with no external route |

Also found and fixed from the new pictures and sequences: a mod trusted in the draft showed
`BLOCKED` (the trust-only check required "not trusted"); the Game Setup Team row left a blank
row; the inherited `TestEditorInteractionSafety` text-width check measured proportional text
as `length() * 9` (failing since `b9a90fcd6`, no dependency changed here) and now measures it
as drawn.

Rejected or kept unchanged, with evidence:
- Disabled `Start round (waiting)` stays focusable: the traversal pictures show Down from Ready
  up landing on it, and its label already says why it does nothing; skipping it would hide why
  the host cannot start.
- Easing the toast down when a plate arrives: `EscapeToastMotion.pathClear` is false for every
  arrival case in the plate sweep (the plate occupies the toast's row), so any slide would
  cross it; the test counts both eased and immediate cases.
- Using the left lobby playfield or a smaller face for notices: not needed; two `NameFont`
  lines fit in the freed rows.
- A generic hub fallback when the status line is too wide: replaced by a two-line break at
  phrase boundaries that the sweep proves is never needed at 320/400.

### Interaction and motion evidence (this pass)

Scripted production input only (keyboard, pad and pointer events through the real owners);
no human usability timing was measured. Sequences in `final/`: the hub slide (every frame
after Right), Settings > Display moving down nine rows (five frames per press: focus band,
list offset and thumb travel at the viewport edge), a Controls rebind (Enter, the listening
page, K, the updated row naming its other binding), the Playback dense list moving past its
window and the selected long name's marquee, the Mod trust flow (NEEDS TRUST row, TRUST AND
ENABLE page, the confirmed row), Time Attack Down from Ready up (focus lands on the dimmed
"Start round (waiting)", which says why it does nothing, then moves on), and the hold toast
with pause switched off mid-hold (eases up) and on again (moves below at once). Every step
of the toast and plates is also asserted geometrically at 320 and 400.

### Score matrix (structural pass)

Weakest captured state per family, including the new crowded fixtures and sequences. Families
this pass did not change were inspected again in `before/`/`final/` (same code); the anchor
column says what holds them where they are. No score below is an average over states.

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state / anchor |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 Hub | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `60-hub-overflow-start`: every hidden value named on Start, amber where per-game; the shelf art is fixed |
| A2 Paused card | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 | **8.5** | unchanged; two muted lines under the buttons remain |
| A3 Prompts | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | Quit/save/discard name their consequence, one focus, no extra text |
| A4 Missing ROM | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | card says what is missing and the one next action |
| A5 Help | 9 | 9 | 9 | 8.5 | 9 | 9 | 9 | **9** | `46-controller-help`: reading order, the pad's Help button, no repeated caps |
| A6 Onboarding | 8.5 | 8.5 | 8.5 | 8.5 | 9 | 8.5 | 8.5 | **8.5** | unchanged; the scan-limit result is a real outcome but one terse line |
| A7 Advanced tools | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | icon rows with one-line notes; the model the project pages now follow |
| B1 Settings tabs | 8.5 | 8.5 | 8 | 9 | 8.5 | 9 | 8.5 | **8.5** | `seq-display-down`: scrolling is clear; the Display tab is ten dense rows at 320 |
| B2 Controls | 8.5 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `seq-controls-rebind`: listening page, then the row with its conflicting binding named |
| B3 Advanced settings | 8.5 | 8.5 | 8 | 8.5 | 8.5 | 8.5 | 8.5 | **8.5** | unchanged; long expert list |
| B4 Game Setup | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `61-setup-borrow-s3k`: cause above effect, no blank row |
| B5 Save/reset/preview | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | prompts name consequences; preview countdown meter |
| C1 Path browser | 8.5 | 9 | 8.5 | 9 | 9 | 9 | 8.5 | **8.5** | unchanged; dense places/contents split at 320 |
| C2 Keypads | 8.5 | 8.5 | 8.5 | 9 | 8.5 | 9 | 9 | **8.5** | `m26`: Set names the result; the integer pad's sign row stays partly empty |
| C3 Details | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 | 9 | 8.5 | **8.5** | config-key and saved-value rows are exact by design (searchable), read as engineering |
| D1-D6 Playback | 8.5 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **8.5** | `18`/`19`: headline plus exact paged error; the dense list is busy at 320 |
| D7-D9 Mod manager | 8.5 | 8.5 | 8.5 | 9 | 8.5 | 9 | 9 | **8.5** | trust flow true end to end; notices keep exact finding codes (by design) |
| D10 Import | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `m23`: facts, consequence and action once each |
| E1-E2 Notices | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `m21`: what happened, the list, the true next step |
| F1 Shader picker | 8.5 | 8.5 | 8.5 | 9 | 9 | 9 | 8.5 | **8.5** | unchanged; long library lists page in a small panel |
| F2 Mutator Lab | 8.5 | 9 | 9 | 8.5 | 8.5 | 9 | 9 | **8.5** | `18`: timing once; mod-authored Title Case names remain the creator's |
| F3 Editor projects | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `09`: each command with its consequence |
| F4 Editor HUD | 8.5 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | grouped command sheet with real caps, pad caps on a pad; prompt bar dense at 640 units |
| F5/F11 Toast with plates | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `27`/`28`: meter only, eased or immediate by path; never crosses a plate |
| F6-F10 Plates and trace | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **8.5** | `05`-`07c` finally distinct; transport keeps fixed 6x8 caps (constraint) |
| Stageplay chrome and forms | 8.5 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **8.5** | `long-dirty-title`: marker kept; the 640-unit prompt bar stays dense |
| Time Attack views | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 | 9 | 8.5 | **8.5** | `ta63`: eight players page cleanly; chat lines elide sender and text at 320; titles use the pinned bold canvas face |

Thirteen families reach 9 overall; fourteen stay at 8.5. Where a family stays at 8.5 the
reason is named in its row: either a density at 320 that the next step would have to remove
content to fix (Settings Display, Advanced settings, path browser, Playback list, shader
library), exact diagnostic values kept on purpose (Details, Mod notices, trace plates), a
pinned creator surface (Time Attack titles, mod-authored mutator names, fixed transport caps)
or an untouched family with a minor terse line (paused card, onboarding, keypad sign row,
Stageplay prompt bar). None of these was raised by relabelling.

### Validation

Java 25, `DISPLAY`/`WAYLAND_DISPLAY` unset, surfaceless EGL, canonical S2 REV01 original by
absolute path. Per-round focused runs found and fixed: Round 1, 10 expectation failures from
the new Game Setup order and hub status (then 96 + 10 passing); Round 2, an over-strict label
check, a hotbar-slot assumption and the inherited `length() * 9` measurement; Round 3, an
off-by-one in the ease test and the stale "trust required" status assertion. One combined run
on the exact tree committed as `d8d947336`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<absolute S2 REV01> \
  "-Dtest=<53 classes: the hub, Game Setup, help and lobby owners and consumers; editor command,
  HUD, input, project and Stageplay classes; import, keypad, Mutator Lab, catalog, Mod manager,
  native notice, trace feedback, Playback, toast layout, GameLoop and Settings; plus
  TestModApiSignatureSurface, TestArchUnitRules, TestArchitecturalSourceGuard and
  TestTimeAttackVideoCaptureTool>" test
# 847 tests, 0 failures, 0 errors, 0 skips
scripts/round.sh final   # package -DskipTests, example-mutators jar, picture tests
# (TestEditorHudPictures, TestStageplayHudPictures, TestPlaybackBrowserPresentation: 10 + 1, 0 skips),
# engine capture tools at 320/400, Time Attack 67 pictures x 2
```

The per-round runs overlap the combined run; they are not a separate total. `-Pguards`, the
smoke selection and the release suite were not run (no guard budget file changed; the source
guard and ArchUnit classes above passed); the parent owns delivery-wide validation. The
overlay group was recaptured at `074898249` after a capture-tool-only fix (unpaused toast
frames had drawn the pause plate by calling its renderer directly). The Time Attack group was
rerun after two fixture-script fixes (per-address host limit; leaving with Back once the crowd
made everyone ready).

### Remaining gaps

- Fourteen families stay at 8.5, each for the reason in its row; the densest (Settings Display,
  Advanced settings, path browser, Playback list, shader library) would need removed content
  or a second page, not polish.
- Crowded Time Attack chat lines elide both sender and text at 320 (the details page carries
  them whole); the host's notice is not in the crowded picture because it clears on the next
  action (it is pictured with fewer players and unit-tested whole at 320/400).
- `EditorProjectMenu.ActionRow` gained a `note` component; the separate desktop editor repo
  (OpenGGF-Editor) consumes the action view and must follow if it constructs rows itself.
- No human usability study; sequences are scripted production input.


### Independent structural review and lead receiving repairs

Opus 5.5 high independently reviewed `48c73b03f` without source edits or tests,
using native/3x pictures, the diff and two bounded reflection probes. Its report is
`structural/review-opus55/structural-review.md` in the existing evidence directory.
It rejected the open-project page's claimed 9: 13 noted actions fit only 7 at a
time (6 with a status line), without a scroll cue. The lead added a shared thumb
and a selected/total header badge, retaining the shared row geometry and full
notes. A regression walks all 13 actions at both widths and checks their visible
selection, position and thumb, including after a status line reduces the list.
The old three-argument `ActionRow` constructor remains available with an empty note.

Other receiving repairs: lobby readiness explicitly says "you" when the local
player is among those not ready, and all-ready outranks optional readiness;
ordinary IllegalArgumentException/IllegalStateException get the generic trace
headline instead of attributing a programming error to trace data; Mod details
reflect trust in the current draft rather than issuing stale confirmation steps;
and the Mutator options caption fits before the measured shared-timing label.
Exact diagnostic text, finding codes, trust persistence and gameplay are unchanged.

The desktop editor repository independently reconstructs `HostCommand` through
its old two-argument constructor, dropping the new shortcut metadata (including
F4). That consumer presentation issue is recorded for its owner; it is outside
this task's engine-and-bundled-menu scope. No other repository was modified.
The next pass owns the review's remaining internal descriptor contexts, numeric
keypad navigation, missing-ROM recovery, onboarding completion copy, grouped
settings/editor flows, lobby chat, tag noise, and the seven missing picture states.

Receiving-repair validation (Java 25, EGL selected, desktop displays unset, no ROM
needed for these fixtures; staged repairs on `48c73b03f`):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestEditorProjectMenu,TestRaceLobbyView,TestTracePlaybackFeedback,TestModManagerScreen,TestMutatorCatalogueScreen' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestRaceLobbyView' test
```

The first attempt stopped at compilation because the new recording fixture tried
to subclass final `MenuPixelFont`; it now uses a mock for rectangle assertions.
The next run completed 109 cases: the four non-lobby classes passed all 79 with
zero skips; two lobby assertions exposed that "Waiting for you and 2 others"
wrapped, crowding the host notice. The compact sentence "Waiting: you + 2" keeps
self-awareness on one line. The affected lobby-only rerun passed all 30 cases,
zero failures/errors/skips (121 seconds queue wait, 34.083 seconds Maven time).
The passing coverage is 79 unchanged-owner cases plus the corrected 30 lobby
cases, not a sum of overlapping runs. No broad smoke or release suite was run.


## Finish pass toward 9 (2026-10-28/29)

Base `217e364e4` (the lead's receiving repairs F1-F5 kept, not redone). Scope as before: engine
and bundled menus, the bundled Time Attack mode included; the 18 optional example games, native
ROM screens, narrative dialogue and debug instruments excluded. The pass closes the independent
structural review's finish brief (`structural/review-opus55/structural-review.md`, groups A-C).
Commits: `081c1ac63` (implementation, capture tools, changelog, skill, level-editor guide),
`dc7e7eb9c` (crowded Room details), `d2c2a406c` (picture-test fix), `a513dd5fd` (repairs the
round-1 pictures showed). Scores remain subjective design judgments from inspected production
pictures; no human usability study or timing was measured.

**Evidence.** `$HOME/scratch/agent-tmp/codex/menu-target9-20261028/finish/`: `before/` (exact
`217e364e4`, built from a `git archive` export inside this worktree's own `target/`, every engine
capture group at 320 and 400 and Time Attack 70 x 2), `round1/` (`d2c2a406c`, superseded),
`final/` (clean `a513dd5fd`, every group, editor HUD at 1280x720 and 1600x900, Stageplay and
Playback), each with `provenance.json`; `README.json`, `gallery.html` (this pass, quantised) and
the lead's original-colour `lead-gallery.html`; adapted `scripts/` (`CAPTURE_WORK`/`CAPTURE_HEAD`
select the build tree). The editor HUD, Stageplay and Playback picture tests were not run on the
base export; their "before" is `structural/final` (`d8d947336`). `final/time-attack/*/ta63a` was
replaced from a fixture-only rerun at the same clean head (recorded in its provenance).

### Finish brief: resolution

| Item | Resolution | Evidence |
| --- | --- | --- |
| F1-F5 | lead repairs in the base, kept | `final/editor-hud/1280x720/project-menu-open-{320,400}-{top,import-art}.png` (the real open-project page, 13 actions, `1 / 13` and the thumb) |
| F6 | out of scope (separate desktop repository, unchanged); engine callers keep `HostCommand` shortcuts, and the palette's own playtests now carry `Shift+Tab`/`F5` | `TestEditorCommandPalette` |
| F7 | `Descriptor.Context` states region, placement-mode and paste-preview conditions (`NO_PASTE_PREVIEW` for Enter, `NO_REGION_NOT_OBJECT_MODE` for Del erase, ...); input semantics unchanged | `TestEditorHudControls.sharedKeysReachTheCommandWhoseContextMatchesEveryEditorState` walks Enter/Esc/Del through all eight states |
| I1 | numeric pads hold only real keys: Delete ends the sign row, a shared bottom row with paired cursor keys, Clear, Default, Set; the whole-number pad's spare cells are Start/End; decimal offers `e` once (typing still takes `E`); help per key | `m26b`, `m27b`; a test presses every cell by keyboard and pad and checks the exact help and the value it produces |
| I2 | Game setup and Playback on a missing ROM open Start's locator, Back restores card and button; a failed load offers Locate ROM first and "Settings > Files"; footer names LOCATE ROM; standalone keeps ACTION UNAVAILABLE | `hub/62`-`67`; keyboard, pad and pointer tests |
| I4 | no notice once every game is found; limit/unreadable sentences for some and none, fitted whole | `hub/56`; `noticeSaysOnlyWhatTheScanMissedForAllSomeAndNoGames` (limit outcomes unit-only) |
| B4 | "Chosen on the save screen", "None for this game" | `hub/61` |
| B3 | captions from the catalog section (Shaders ... Settings file), skipped by arrows and pointer; tape-coast tuning 8 px in, dimmed but editable while off ("Used only while Tape coast is on."); authored labels and help (no leaf, path, enum constant or `=`) | `m05`, `m06a`-`m06c`; all-settings walk at 320/400 with hit geometry, acronym and raw-key tests |
| B1 | Window, Picture, Widescreen, HUD; authored colour help, RGB detail in Details | `seq-display-down` recaptured |
| C3 | one Applies sentence per timing class, resume-aware from `LiveSettings` | `m06d`; every setting checked |
| F4 | Editor (with Stageplay F4), Edit (Undo, Redo first), ..., Playtest ("Playtest from cursor" Shift+Tab, "Playtest from level start" F5, the project's duplicate pair dropped from the menu), Project | `menu-keyboard{,-scrolled,-end}` at both sizes, pad `menu*`; Shift+Tab/F5 pressed through `GameLoopEditorController` |
| Stageplay | at most five prompts for the focused pane, Commands last, Tab names "Keys / cues"; C and the pad's Add actor are not repeated | `cast-720p`, `cast-pad-720p`; keys pressed through the HUD |
| F2 | the list names its shared timing once; rows are two lines only where they carry an exception; no Esc cap while play is held (Resume is the way back) | `mutators/17`, `17b`, `18b` (LOAD in gameplay) |
| TA chat | sender at most a third of the row, newest message wraps once with a 6 px continuation, card grows only then | `ta31`, `ta63`; `TestRaceLobbyChat` |
| Tags | `ROM?` and shader formats on the focused row unless the visible types differ | `p01`, `p02`, shader `04`, `05` |
| A2 | one status line (resume timing, or "Ending it loses its progress"); legend only beside Settings | `hub-paused/19`, `20b`, `20c` |
| D wrap | an identifier that fits the value column moves whole | `playback/*-18-*` |
| D mods | results name the mods and fit ("Trusted and on: Moonlight adventures, Shared art library", else a count); "none assigned until it loads"; codes unchanged | `seq-trust-k03`, `k04` |

Group C states, all pictured from production paths: open-project page (above); keyboard command
sheet (above); missing-ROM page and locator flows (`hub/62`-`67`); crowded Room details
(`ta65`, `ta66` after Up); Details after draft trust (`seq-trust-k04-details`); a LOAD mutator's
options in gameplay (`18b`); lobby self-wait (`ta31`, `ta47`; F2 unit cases). The crowded
host notice (`ta63a`) is produced by the real START owner: the fixture sets Ready rule Required,
cancels the host's ready and presses Start round, asserts "Waiting for every player to be ready"
in `RaceLobbyView` and whole in the drawn card, pictures it and restores rule and ready.

Found from the pictures and fixed: crowded Room details showed 5 of 8 players and the chat tail
from mid-message, and Up/Down did nothing (`dc7e7eb9c`: a scrolling list, every name and message
whole, opening on the newest message's first line, scrollbar and cap); Setup's footer still said
OPEN SETUP on a missing ROM; scrolled Advanced rows lost their caption context ("Start frame"),
so every label now reads on its own; the first caption touched the focus band; the mod status was
cut; one LOAD mutator left blank second lines on the others; the decimal `e` key's help described
the End key.

Rejected or kept, with evidence:
- "Art slots" in Mod details: reverted; `docs/modding/tools/scalability.md` keeps the creator
  term "Pattern windows" in Details and the manifest.
- British "colour" in the editor: kept "color"; it matches the level-editor guide and the editor's
  own pages.
- A smaller face for lobby chat: not needed; two `NameFont` lines fit the newest message.
- Opening Room details on a continuation line to fill the page: rejected; it starts on a whole
  message and leaves the remaining lines empty.

### Score matrix (finish pass)

Weakest captured state per family (paths under `finish/final/`). No score is an average.

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state / anchor |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 Hub | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `320/hub/60-hub-overflow-start` |
| A2 Paused card | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/hub-paused/19-paused-card`: one line |
| A3 Prompts | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/hub/17-quit` |
| A4 Missing ROM | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | `320/hub/65-missing-rom-not-found-page`: path, Locate ROM, Settings > Files |
| A5 Help | 9 | 9 | 9 | 8.5 | 9 | 9 | 9 | **9** | `320/hub/46-controller-help` |
| A6 Onboarding | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | `320/hub/56-add-games-found-scan`; limit outcomes unit-only |
| A7 Advanced tools | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | `320/hub/57-advanced-all-tools` |
| B1 Settings Display | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `320/hub/seq-display-down-k09-f04`: 14 lines scroll |
| B2 Controls | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `320/hub/seq-controls-rebind-*` |
| B3 Advanced settings | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `320/menus/m06c-advanced-settings-end`: a long key cap beside a long label |
| B4 Game Setup | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/hub/61-setup-borrow-s3k` |
| B5 Save/reset/preview | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | `320/hub/51-settings-reset-prompt` |
| C1 Path browser | 9 | 9 | 9 | 9 | 9 | 9 | 9 | **9** | `320/browser/p01-populated` |
| C2 Keypads | 9 | 9 | 9 | 9 | 9 | 9 | 9 | **9** | `320/menus/m27b-decimal-keypad-exponent` |
| C3 Details | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `320/menus/m06d-advanced-details`: ABOUT keeps the catalog text by design |
| D1-D6 Playback | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `playback/320-06-dense-scrolled-long-name` |
| D7-D9 Mod manager | 9 | 9 | 8.5 | 9 | 8.5 | 8.5 | 9 | **9** | `320/mods/seq-trust-k04-details`: Pattern windows (creator term) beside the list's Art slots |
| D10 Import | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/menus/m23-import-candidate` |
| E1-E2 Notices | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/menus/m21-native-mod-notice` |
| F1 Shader picker | 9 | 9 | 8.5 | 9 | 9 | 9 | 9 | **9** | `320/shader/05-focus-beside-active` |
| F2 Mutator Lab | 9 | 9 | 9 | 8.5 | 8.5 | 9 | 9 | **9** | `320/mutators/17-mutator-list`; mod-authored Title Case is the creator's |
| F3 Editor projects | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `editor-hud/1280x720/project-menu-open-400-import-art` |
| F4 Editor HUD | 9 | 9 | 8.5 | 9 | 9 | 8.5 | 9 | **9** | `editor-hud/1280x720/menu-keyboard-end`; editor "color" vs Settings "colour" |
| F5/F11 Toast | 9 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/overlays/27-*`, `28-*` |
| F6-F10 Plates and trace | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** | `320/overlays/05`-`07d`; fixed 6x8 transport caps are the contract |
| Stageplay chrome | 9 | 9 | 8.5 | 9 | 8.5 | 9 | 9 | **9** | `stageplay-hud/cast-720p` |
| Time Attack views | 9 | 9 | 8.5 | 8.5 | 8.5 | 9 | 8.5 | **8.5** | `time-attack/320/ta63-lobby-crowded`: the focused chat card's cap reads `A: OPEN` on a keyboard, where a ready lobby's Enter is Start; older card lines elide by design |

Twenty-six families reach 9; Time Attack stays 8.5 for the clarity deficit in its row (below).

### Validation

Java 25, `DISPLAY`/`WAYLAND_DISPLAY`/`LD_LIBRARY_PATH` unset, surfaceless EGL, canonical S2 REV01
original by absolute path. Per-round focused runs found and fixed expectation, fixture and code
issues (the first attempt crashed one fork in a new GL-drawing test, since replaced). One combined
run on the tree committed as `a513dd5fd`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<absolute S2 REV01> \
  "-Dtest=<41 classes: keypad, hub, missing ROM, paused card, onboarding, Game Setup, editor palette,
  HUD controls, playtest shortcuts, project menu/lifecycle, Stageplay HUD, Settings screen/draft,
  field wrapping, trace feedback, Playback, Mod manager (3), Mutator Lab, path browser, shader picker
  (2), render alignment, Time Attack chat and lobby, interaction safety, suspended settings,
  TestModApiSignatureSurface, TestArchUnitRules, TestArchitecturalSourceGuard>" test
# 560 tests, 0 failures, 0 errors, 0 skips (Maven 59.2 s after about 181 s queue wait)
finish/scripts/round.sh final   # package -DskipTests, mutators jar, picture tests (10 + 1, 0 skips),
                                # engine capture tools at 320/400, Time Attack 70 pictures x 2
```

Earlier per-round runs overlap this run and are not added to it. `-Pguards`, the smoke selection
and the release suite were not run; the parent owns delivery-wide validation. Mod API pins and
`NameFont` are unchanged.

### Remaining gaps

- Time Attack: the chat card's cap is a fixed `A: OPEN`. On a keyboard the lobby's Enter opens it
  while not ready, but once ready over the lobby level Enter is Start (it hides the menu) and
  only the A binding (Space by default) opens it; the mod cannot name that key today. Next: make
  the cap device- and state-aware once the scene input can name the bound key.
- No bundled mutator is LAUNCH-scoped, so only LOAD is pictured in gameplay.
- Onboarding's limit and unreadable outcomes are unit cases only, not pictured.
- The separate desktop editor repository still rebuilds `HostCommand` without its shortcut (F6).
- No human usability study; sequences are scripted production input.


### Lead acceptance and final Time Attack keyboard shortcut

The final Opus pass completed at `59ef59461`, based on tested code `a513dd5fd`.
The lead independently inspected original-colour production pictures for Advanced
settings, the open-project page at action 13 of 13, keyboard command groups,
post-trust Mod details, LOAD Mutator options, two-player wrapped chat and crowded
Room details/history. The corrected crowded notice was produced by the Start owner
and is no longer inferred from the misleading earlier filename. The saved 560-case
command/output and capture completion were checked against the worker timeline,
not merely its final claim.

The last concrete Time Attack gap did not require a public binding-label API.
The focused chat card now names `TAB: DETAILS` on a keyboard and `A: OPEN` on a
pad. Tab is an explicit mod-owned shortcut through the existing raw scene-key API,
so it opens Room details before or after readying without guessing a configured
A key. The ready-lobby Start/Enter owner yields to this focused shortcut even if
Tab is also bound to Start; Enter otherwise retains its Play behavior. Back returns
to the same card. Existing controller confirmation is unchanged. No public Mod API,
NameFont, signature pin or gameplay timing changed.

The finishing matrix above remains Opus's independent historical assessment:
26 families at 9 overall, Time Attack at 8.5 for that keyboard cap. The lead's
closure of the cap removes that named clarity gap. This does not turn the
individual 8.5 criterion entries into 9s: the record retains those limits rather
than presenting every component score as perfect. Scores are design judgments,
not measured guarantees or a human usability study.

The change-based plan from pinned pre-task destination `a694db926` selects 4,250
smoke classes because shared menu owners are unclassified. The completed focused
screen, flow, input, pixel, API and architectural checks cover the changed UI
owners and consumers; they replace that disproportionate engine-wide selection
for this delivery. The final shortcut is confined to the bundled mod's two input
owners and has its own focused integration coverage. No routine release-only
full suite is warranted. The four inherited guard failures documented above
remain outside this menu change; no guard budget or baseline was changed.


Final shortcut checks (Java 25, EGL selected, desktop displays unset):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestRaceLobbyView,TestRaceLobbyChat,TestTimeAttackScene,TestModApiSignatureSurface' test
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestRaceLobbyView' test
```

The first run passed 37 scene/chat/API cases, with one failure among 32 lobby-view
cases: the new fixture had not supplied a roster, so its ready event was ignored.
After supplying the local player's real roster entry, all 32 view cases passed,
zero failures/errors/skips. The full scene case opens Details with Tab while ready,
checks the lobby still owns controls, closes it and verifies Enter still returns
to play. View cases check the named cap at 320/400 before and after readying. These
are 69 affected checks across the two matched states, not 69 tests in addition to
the overlapping 560-case pass above.

The recurring multiplayer capture driver is now preserved as
`src/test/java/com/openggf/tools/TimeAttackDesignCaptureTool.java`, with its purpose,
inputs, origin and source-launch recipe. It retains a separate loader for peers
and the master server so the engine loads the actual bundled mod, rejects a
non-isolated network namespace, and closes its owned peers/server. The optional
`SHORTCUT_CAPTURE_ONLY=1` path captures four before/after-ready Tab states through
the same production owners. The workflow tools index links it. Earlier scratch
scripts and original pictures remain as provenance.


The promoted fixture initially exposed a checked reflection exception in its new
ready-state predicate at compilation; that predicate now propagates a contextual
unchecked failure. The launcher also fails before boot unless surfaceless EGL is
explicit and both desktop-display variables are unset. Final source/raw-GLFW
checks passed 92 cases (87 source guard, 5 raw setup), zero failures/errors/skips:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestArchitecturalSourceGuard,TestRawGlfwTestSetupGuard' test
```


The final build-only `maven_queue.py -Dmse=off -DskipTests package` passed at
`e716cc55b` (51.568 seconds), including validation of the actual bundled Time Attack
jar. A first capture classpath attempt omitted test-only Netty server dependencies
and failed before any pictures; the standard build-only
`dependency:build-classpath -Dmdep.outputFile=target/cp.txt` repaired that fixture
prerequisite. No production change was needed.

The promoted tool then ran with `SHORTCUT_CAPTURE_ONLY=1` in a fresh private
loopback namespace at both 320 and 400: four requested states plus four boot
controls per width, both exit zero, original-colour PNGs and nearest-neighbour
3x copies under `finish/lead-shortcut/final/time-attack/`. Its assertions open
Details using real Tab input before and after readying and require the ready
lobby menu to stay shown. Provenance records a clean `e716cc55b`, the actual
bundled jar, EGL, original ROM directory and isolated network owner. The ready
chat card visibly says `TAB: DETAILS`; no guessed A-key binding remains.

The lead accepts Time Attack at 9 overall with that concrete clarity defect
closed, alongside the other 26 overall family scores. The detailed criterion
ratings above retain their 8.5 entries; this delivery does not claim all individual
scores reached 9. Remaining coverage limits are the unit-only onboarding scan
error/limit outcomes, no bundled LAUNCH-scoped Mutator to picture, and no human
study. The separately owned desktop-editor F4-hint follow-up is unchanged.

All source changes are integrated into the authorized menu branch by fast-forward
or, for `518c0535d`, an identical-tree commit with corrected co-author paragraph
spacing (`ea7df5c1a`). No merge conflict or unrelated source repair was required.

## Hub and Dialogs spacing and icons to 9 (2026-10-29)

Base `d823ce643`. The finish matrix above kept eight criterion entries at 8.5 across A1-A7:
Hub spacing and icons, paused-card icons, prompt icons, and missing-ROM, onboarding and
Advanced spacing. The owner asked for those criteria to earn 9 by design, not by relabelling.
Scope: A1-A7 spacing and icons only; A5 Help is the control (its pictures are byte-identical
before and after at 320 and 400). Other criteria are restated exactly, not re-scored. The
historical matrices above are unchanged. Scores remain subjective design judgments from
inspected production pictures; no human usability study or timing was measured.

Commits: `ca3d04a1d` (implementation, tests, capture scene, changelog, skill) and `55d9fec81`
(inactive page-dot fill; first committed as `d6771c478`, amended only to correct a contrast
figure in its message, identical tree `2cd658107`).

**Evidence.** `$HOME/scratch/agent-tmp/codex/hub-criteria-nine-20261029/`: `before/` (exact
`d823ce643`, clean build of this worktree), `rounds/r1`, `rounds/r2` (uncommitted rounds),
`rounds/r3-ca3d04a1d` (clean, superseded by the dot fix) and `final/` (clean `d6771c478`,
tree of `55d9fec81`), each the `hub` and `hub-paused` groups of `MasterTitleCaptureTool` at 320
and 400, native 1x with nearest 3x copies and `provenance.json`; `gallery.html` (original
colours, before/final pairs at both widths for every state below); `scripts/` (`capture.py`
adapted from the finish pass, `gallery.py`, and the `crop.py`/`rows.py`/`sheet.py` inspection
helpers); bounded `logs/`. The paused scenes reuse the target-8 gameplay frame read-only.

### Deficits found (baseline pixels, 320)

| Family | State | Deficit (measured at native) |
| --- | --- | --- |
| A1 space | `hub/60`, `58`, `38` | clear rows down the details stack were 3/5/5/3/4/2/5: 2 between the status line's descenders and the legend, 3 from the card frame to the title and from the dots to Start's frame; chip capitals 2 rows above, 3 below in a 12-row pill |
| A1, A2 icons | `hub/01`, `38`, `hub-paused/19` | inactive page dots (74,89,153) measured 1.32:1 against the darkened sky and 1.39-1.72:1 against cloud, and vanished over clouds; the selected dot's 2x1 white highlight read as a minus sign |
| A2 icons | `hub-paused/19` | the PAUSED badge drew its glyph as two hand-placed 1x5 bars, outside the shared icon set and thin beside Resume's solid PLAY |
| A3 icons | `hub/17`, `hub-paused/21`, `22` | neither option had a glyph; the consequential choice was marked by amber lettering alone, while the sibling display-revert prompt marks its choices with CHECK and WARNING |
| A3 space | same | the detail sat 6 clear rows under the title but 2 above the first option's frame |
| A4 space | `hub/65`, `67`, `39` | a fixed 132-row panel: 42 empty rows between the path and Locate ROM, 85 under a one-line reason; the path broke mid-name (`.../not-pr` / `esent.gen`); Locate ROM 18 rows against 20-row buttons elsewhere; the missing-ROM card's message 16 rows from the card top and 28 from the bottom |
| A6 space | `hub/56`, `54`, `49`, `55` | the wider S3K badge put its name 6 px out of the column (x 125/125/131) despite the comment saying otherwise; one found game left 73 empty rows above Continue; Continue 25 rows tall; intro sentences on a 16-row pitch with 16 rows above the group and 40 below; scanning gaps 10/6/8/14 |
| A7 space | `hub/57` | names and notes 9 rows apart, so a descender sat one row above the note's capitals; each rule hugged the note above it (2 clear rows) against 4 to the next name |

### What changed

- **Hub rhythm** (`MasterTitleScreen`): four clear rows from the card's frame to the title, the
  title to the chips, Start's frame to the line under it, that line to the legend (a 12-row
  pitch, so four clear rows even under descenders) and the legend's capitals to the footer;
  three either side of the 6-row dot strip. `MenuChrome.CHIP_H` is 11 rows, capitals 2/2.
- **Page dots**: each dot has a dark one-pixel ring (INK at .85) with open corners and a MUTED
  fill; the selected dot is an 8-wide gold pill. Every dot reads at 3:1 or better: the fill over
  the sky (MUTED 5.04:1, gold 6.25:1), the ring over cloud (3.70-4.48:1). The hit-test rectangle
  is the drawn strip; dots stay status only.
- **Paused badge**: `MenuChrome.Icon.PAUSE`, the PLAY icon's partner, on the label's ink rows
  with three clear columns each side.
- **Quit and end prompts**: each option leads with CHECK (green) where nothing is lost and
  WARNING (amber) where something is, centred with its label as one run; the detail is the
  title's subtitle (4 clear rows under it, 5 above the first frame). The pointer uses
  `choiceOptionBounds`, the drawn rows, instead of duplicated literals.
- **ROM not found** (`errorPageLayout`, shared by drawing and the pointer): the panel fits its
  content with 9 clear rows above the headline and below the last line and sits centred under
  the game's name; `MenuStyle.wrapPath` breaks after a folder separator (else a space, else by
  width), so a file name moves whole and nothing is dropped; Locate ROM is 20 rows with 5 clear
  rows from the path and 4 to the note. The missing-ROM card centres its 40-row block (22/22).
- **Two-line action rows** (`MenuChrome.ACTION_ROW_H`, `actionRowText`): Advanced and the Add
  your games choices share 26-row rows, name and note 10 rows apart, the icon centred on the
  pair. Advanced rows step 28; each rule sits on the gap's lower row, 5 clear rows from the
  note above and the name below.
- **Add your games** (`MenuGameOnboarding.layout`, shared by drawing and the pointer): intro,
  checking/scanning, found and message states are each one group centred between header and
  footer, with named gaps (scanning 6/4/6/8). Found games share one name column and badges of
  the widest badge's width (`MenuChrome.gameBadge` with a minimum width, label centred);
  Continue is a 20-row button.
- **Capture**: `MasterTitleCaptureTool` scene `68-add-games-scan-limit` scans a scratch tree
  nine folders deep with one unreadable folder through the real picker and walker. It asserts
  the walker's own notice ("Stopped early, skipped unreadable files; scan their folder"), holds
  no game (no ROM is copied or linked) and restores the folder's permissions before cleanup.

Rejected or kept, with evidence:
- Icons on Game Setup, Playback and End game: rejected. Only the primary gold button leads with
  its verb's glyph, which keeps the hierarchy; a gear would repeat Advanced settings' icon for
  a different destination.
- Moving or removing the page dots to free rows, or shrinking the card: rejected; the card art
  is fixed and the dots carry position for 2-12 games. The dots' three-row margins are the only
  gaps below four.
- Narrowing chips by the trailing spacing column (5/5 horizontal padding): not done. It would
  change which chips fit before `+N MORE` and the `TestHubSetupChips` sweeps; the 5/6 padding
  is within the one-row balance the render-alignment tests accept.
- Centring the hub's key caps and tab labels on odd interiors: not done. They are shared by
  every footer, including the A5 control, and already sit within one row.
- DIM for inactive dots (first round): replaced. Its fill measured 2.92:1 against the sky.
- A top-anchored error panel (round 1): replaced by the centred panel; at 320 it left 59 empty
  rows below an 89-row card.
- Picturing "some games found and the scan stopped early": not possible without placing a real
  ROM in a scratch tree, which this task may not copy or link. That state is drawn in
  `TestMenuGameOnboarding` (recorded text and fills) and the "none found, stopped and skipped"
  outcome is pictured.

### Score matrix (Hub and Dialogs follow-up, 2026-10-29)

Weakest captured state per family after the change (`final/`, 320 unless noted; 400 inspected
for every row). Changed entries are marked; the rest are carried exactly from the finish matrix.

| Family | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** | Weakest state; what changed |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 Hub | 9 | 9 | **9** (was 8.5) | 9 | **9** (was 8.5) | 9 | 9 | **9** | `hub/60-hub-overflow-start` (five chips, status and legend): one 4-row rhythm with 3 around the dots; ringed dots at 3:1 or better, gold selection pill |
| A2 Paused card | 9 | 9 | 9 | 9 | **9** (was 8.5) | 9 | 9 | **9** | `hub-paused/19-paused-card`: shared PAUSE icon beside the label, PLAY's partner; ringed dots |
| A3 Prompts | 9 | 9 | 9 | 9 | **9** (was 8.5) | 9 | 9 | **9** | `hub-paused/22-paused-quit` (longest detail): CHECK and WARNING lead their choices; detail grouped with the title (space stays 9, now 4/5 instead of 6/2) |
| A4 Missing ROM | 9 | 9 | **9** (was 8.5) | 9 | 9 | 9 | 9 | **9** | `hub/65-missing-rom-not-found-page`: content-sized centred card, path broken after its folder, 20-row Locate ROM; `67` and the card's block centred too |
| A5 Help (control) | 9 | 9 | 9 | 8.5 | 9 | 9 | 9 | **9** | `hub/46-controller-help`: unchanged pixels; colour remains outside this pass |
| A6 Onboarding | 9 | 9 | **9** (was 8.5) | 9 | 9 | 9 | 9 | **9** | `hub/68-add-games-scan-limit` (new; longest notice) and `56`: centred groups, one name column, matching badges, 20-row Continue |
| A7 Advanced tools | 9 | 9 | **9** (was 8.5) | 9 | 9 | 9 | 9 | **9** | `hub/57-advanced-all-tools`: names and notes 10 apart, rules five rows from each |

Every A1-A7 spacing and icon entry is now 9; A5's colour stays 8.5. This pass does not claim
that all criteria of all families are 9: the families outside A1-A7 keep their finish-pass
entries, including their 8.5s.

### Validation

Java 25 (`/usr/lib/jvm/java-25-openjdk`), `DISPLAY`/`WAYLAND_DISPLAY`/`LD_LIBRARY_PATH` unset,
surfaceless EGL, canonical S2 REV01 original by absolute path where ROM-backed.

```bash
# 34 classes: the new TestHubDialogGeometry, onboarding, MenuStyle fit, hub messages/mouse/hub/
# suspended/layout/carousel/secondary/standalone/playback/trace-retry/quit/catalog/preview,
# HubSetupChips, render alignment, ROM locator, path browser, Game Setup panel, title input and
# import access, hub history, widescreen layout, Settings screen, suspended settings, Mod manager,
# trace feedback, GameLoop menu transitions, Playback presentation pictures, and
# TestArchitecturalSourceGuard, TestArchUnitRules, TestModApiSignatureSurface
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic2.rom.path=<absolute S2 REV01> "-Dtest=<the 34 classes>" test
# 421 tests, 1 failure, 0 errors, 0 skips (uncommitted tree before ca3d04a1d)
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestHubDialogGeometry,TestMenuGameOnboarding,TestMenuStyleFit,TestArchitecturalSourceGuard,TestArchUnitRules,TestModApiSignatureSurface' test
# at ca3d04a1d: 160 tests, 0 failures, 0 errors, 0 skips
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestHubDialogGeometry' test
# with the MUTED dot fill (tree of 55d9fec81): 13 tests, 0 failures, 0 skips
```

The one failure, `TestMasterTitleTraceRetry.rootFailureIsNotAnnouncedAgainWhenThePickerIsRecreated`
(line 104, `catalogLoad` null), is inherited: the same class fails the same assertion on an exact
`git archive d823ce643` export built in this worktree's `target/`. Since `081c1ac63` Enter on
ROM NOT FOUND opens the locator instead of returning to test mode's browser; this pass changes
only the pointer rectangle on that path. It is recorded, not repaired.

The new geometry checks were broken on purpose: restoring the old legend row, the 1-px pause
bars, icon-less prompt options, the old rule row, a fixed panel top, per-badge name positions
and an uncentred onboarding group each failed the matching check (logs `mutation*.log`); the
DIM fill fails the new sky contrast case at 2.92:1. A first lean run of the new test failed in
javac with `OutOfMemoryError` under the 1 GiB lean heap while recompiling the test tree; the
tree was compiled at the normal heap and the lean run repeated. No smoke, guards profile or
release suite was run; the parent owns delivery-wide validation. Mod API pins, `NameFont` and
ROM-native screens are unchanged.

### Remaining limits

- "Some games found, scan stopped early" is drawn in a unit test but not pictured (no ROM may be
  placed in a scratch tree); "none found, stopped and skipped" is pictured.
- Settings' save and reset prompts (B5) keep text-only options; carrying the consequence icons
  to them is a B5 follow-up outside this scope.
- The ROM-not-found page keeps the older `MenuStyle.page` header (white title) beside the gold
  `MenuChrome` headers; its type, colour and consistency entries are unchanged here.
- `develop`'s newer i18n and bidirectional menu work touches the same owners; integration is the
  parent's.
