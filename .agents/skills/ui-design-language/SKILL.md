---
name: ui-design-language
description: Use when adding or changing OpenGGF menus, dialogs or in-game overlays such as rewind, recording, playback, shader picker or status plates. Defines the shared MenuChrome design language and the stricter screen-space budget for anything drawn over gameplay.
---

# UI design language

Menus and overlays share one vocabulary: `MenuChrome` colours, the native pixel
font at scale 1 with shadow, navy plates with an edge line, and authored pixel
icons. Reuse `MenuChrome` and `TransportHud` helpers; do not hand-roll colours,
frames or icons. Evidence and scoring history:
`docs/architecture/audits/2026-10-27-menu-design-review.md`.

## Menus and dialogs

- Build from `MenuChrome` page, header, footer key caps, button, toggle, tag,
  scrollbar and the breathing gold focus frame.
- Sentence case for values and descriptions; capitals only for tags, key caps
  and footer labels.
- Prompts name the consequence ("Quit to desktop"), not "OK".

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
  scale 1. Never downsample the debug font or truncate routine frame counters and
  key chords; reserve the centred native rewind lane when sizing the right plate.
- **Progress is a meter**, a 2 px bar under the row, not a text row or "50%".
- **Stay clear of the game HUD.** Score, time and rings sit top-left (y < 64);
  lives sit at y = 200. Use corner lanes, never the centre of play. Check 320 px
  and widescreen widths, with simultaneous overlays in separate lanes.
- **Exception: debug and trace replay.** Debug overlays and trace replay
  diagnostics are read while nobody is playing, so they may use dense multi-row
  panels and full words. Use native-size glyphs, wrap complete identifiers and
  values, and page long reports instead of shrinking or ellipsizing them. They
  still use `MenuChrome` colours and backing panels.
- Transport plates are owned by `com.openggf.game.presentation.TransportHud`;
  add a `Status` there rather than a new plate style.
- Verify with an offscreen capture (`TransportHudDesignCaptureTool`, see the
  `headless-rendering` skill) at 320 and 400 px.
