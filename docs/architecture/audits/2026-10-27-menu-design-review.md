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
