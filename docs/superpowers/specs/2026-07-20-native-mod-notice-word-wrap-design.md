# Native Mod Notice Word-Wrap Design

## Problem

`NativeModNoticeScreen` currently centers each incoming notice string without
constraining its measured width. The notice header and long mod display names can
therefore begin at a negative X coordinate and extend beyond the 320-pixel view.

## Design

The screen will prepare its rendered lines after `PixelFont` initialization so
wrapping uses the real font metrics at the actual body scale. Each semantic input
line will be greedily wrapped within a 288-pixel body width. Wrapping prefers word
boundaries; a single token wider than the body will be split at character
boundaries so every emitted line fits.

The prepared body will be capped at 12 rendered lines. If wrapping would exceed
that budget, the final visible line will be `...`. With a start Y of 40 and a
12-pixel line height, this keeps the body clear of the dismiss prompt at Y 204.
The console warning remains the complete source for all unsupported mod ids.

The wrapping helper will remain package-private on `NativeModNoticeScreen`, take
an injected string-width function, and have no OpenGL dependency. This keeps the
behavior directly testable and avoids changing the legal-disclaimer wrapper or
the published Mod API.

## Tests

Focused Jupiter tests will verify:

- long sentences wrap at word boundaries;
- oversized single tokens split and every result fits the width bound;
- short lines remain unchanged;
- output never exceeds the vertical line budget and uses `...` when truncated.

The existing native notice, render dispatcher, boot controller, Mod Manager, and
architecture suites will be rerun after implementation.
