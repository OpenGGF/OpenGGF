---
name: ui-design-language
description: Use when adding or changing OpenGGF menus, dialogs or in-game overlays such as rewind, recording, playback, shader picker or status plates. Defines the shared MenuChrome design language and the stricter screen-space budget for anything drawn over gameplay.
---

# UI design language

Menus and overlays share one vocabulary: `MenuChrome` colours, the authored
`MenuPixelFont` lettering (light 6x8 and bold 9x10 cells from `MenuGlyphs`, not the
legacy PNG `PixelFont`), navy plates with an edge line, and authored pixel icons. Reuse `MenuChrome` and `TransportHud` helpers; do not hand-roll colours,
frames or icons. Evidence and scoring history:
`docs/architecture/audits/2026-10-27-menu-design-review.md`.

## Menus and dialogs

- Build from `MenuChrome` page, header, footer key caps, button, toggle, tag,
  scrollbar and the breathing gold focus frame.
- Sentence case for values and descriptions; capitals only for tags, key caps
  and footer labels.
- Prompts name the consequence ("Quit to desktop"), not "OK".
- Menu text is proportional: each glyph advances by its ink width plus one
  pixel, digits stay tabular (6 light, 9 bold) so counters do not jitter, spaces
  are 3/4 px, and arrows and marks keep a whole cell. Measure with
  `MenuChrome.compactWidth`/`labelWidth` (or `MenuPixelFont.textWidth`), never
  `length() * 6` or `* 9`; `COMPACT_ADVANCE`/`LABEL_ADVANCE` are cell sizes only.
- Fit, wrap and scroll by pixels with `MenuStyle.fit`/`fitLabel`, `wrap`,
  `chunks`, `marquee`, `elideMiddle` and `elideStart`; they work on whole code
  points. Do not convert a pixel budget into a character count.
- Rows and body text use the light face on every page; bold is for page
  titles, card headings and the gold launch button, never a row style that a
  page switches to because its rows happen to fit. Description boxes step
  lines by `MenuChrome.DESCRIPTION_PITCH`; wrap help sentences by measured
  width instead of hand-splitting them into fixed lines.
- Facts read as a label column: `MenuDetailsScreen` sections, `MenuFieldLines`
  and the Mod manager's aligned fields start every value after the widest
  short label. Player-facing reasons read as words; finding and diagnostic
  codes stay exact because creators search logs and docs by them.
- An empty list is information, not a warning: cyan icon, a title naming the
  missing scope and the next step. Offer only hints that do something.
- The level editor HUD (`EditorHudTheme`, window-resolution UI units) follows the
  same roles: `EditorHudCanvas` draws and measures text in the case it is given, so
  chips, captions, page titles and prompt labels pass capitals while menu rows,
  sheet fields and values stay sentence case in the light face.

## In-game overlays

Screen space during gameplay is at an absolute premium. An overlay is read at a
glance mid-action; it reports state, it does not explain it.

- **One row ideal, two rows maximum.** A second row is for an exception or the
  replay's `Tab: Details` hint. Routine filenames and "verified" stay off screen.
  Details are opened manually; failures never open the full report automatically.
- **Abbreviate.** Short capital labels: `REC`, `REW`, `PLAY`, `FF`, `HOLD`,
  `STOP`, `ERR`. Frame counts as `F:1248`, with no floating frame prefix.
  Recording hold instructions name the actual configured chord, such as
  `REC: HOLD SHIFT+O`; never substitute the action name for the bound key.
  No sentences, no full words where a
  standard abbreviation exists.
- **Icon and colour carry state; text carries the value.** One status colour per
  plate: red record/error, cyan rewind/play, gold hold, amber warning.
- **Size to content.** Plates shrink to their text; no fixed-width panels.
  Use `PixelFontTextRenderer.compactMenu()` for the authored native 6x8 face at
  scale 1: it keeps the tuned fixed 6 px cells. Transport, recording and trace
  overlays measure through that renderer, never through the proportional
  `MenuChrome` width helpers. Never downsample the debug font or truncate routine frame counters and
  key chords; reserve the centred native rewind lane when sizing the right plate.
- **Progress is a meter**, a 2 px bar under the row, not a text row or "50%".
- **Stay clear of the game HUD.** Score, time and rings sit top-left (y < 64,
  x <= `MenuChrome.GAMEPLAY_HUD_RIGHT` in the native 320 frame); lives sit at
  y = 200. Use corner lanes, never the centre of play. Transport uses the
  above-lives and bottom-right lanes; engine-session status (`PAUSE`, window
  `VIDEO`) uses the `TOP_RIGHT` slots; the hold-Escape toast starts right of the
  HUD column (`MenuChrome.holdToastX`) and rests below any top-right plate it would
  touch (`Engine.escapeToastRestY`, plates measured by `TransportHud.bounds`). Check
  320 px and widescreen widths, with simultaneous overlays in separate lanes.
- **Exception: debug and trace replay.** Debug overlays and trace replay
  diagnostics are read while nobody is playing, so they may use dense multi-row
  panels and full words. Use native-size glyphs, wrap complete identifiers and
  values, and page long reports instead of shrinking or ellipsizing them. They
  still use `MenuChrome` colours and backing panels.
- Transport plates are owned by `com.openggf.game.presentation.TransportHud`;
  add a `Status` there rather than a new plate style.
- Verify with an offscreen capture (`TransportHudDesignCaptureTool`, see the
  `headless-rendering` skill) at 320 and 400 px.
