# OpenGGF 0.7.prerelease — Development changelog

Unreleased. The earlier development themes remain in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Gameplay and presentation

- **Main menu:** Compact and bold lettering now share one pixel-font family,
  with clear navy lettering on gold launch buttons. Holding Escape freezes a
  stock game and smoothly shrinks its picture into the paused-game card; Resume
  expands it back to full size before
  gameplay continues. Saved Game Setup preferences remain current on resume,
  and returning live options to Default releases the earlier per-game overrides.
  Display settings offer windowed, borderless and fullscreen modes, with integer
  or aspect-preserving fit scaling. Maximising and resizing retain the requested
  window bounds, including on high-DPI screens. Window-mode changes preview for
  15 seconds and revert unless confirmed.
  Settings show **RESTART** only for changed
  values awaiting a restart, clearing it when restored to the running value.
  The hub remembers the last game after navigation settles, with optional
  quiet title music. Controller hints
  follow the last device used. Compact Controls, Advanced and ROM browsing pages,
  player binding groups, conflict warnings, confirmed tab resets and save summaries
  make setup easier to review. Expert loading and rewind tuning live in Advanced;
  first-run discovery can locate one ROM or scan a folder for all three games,
  retaining partial discoveries at scan limits and explaining file-access errors.

- **Second Strike hard-mode prototype:** Native brittle supports, earned zero-ring
  bonus invitations, finite badnik/boss patterns and original-ring retention keep
  player physics unchanged. Authored AIZ and first-five-zone campaign ingredients
  are being developed, with native MHZ wind pulses, LRZ echo jets and an FBZ
  extra attack beat available as finite ingredients. Native finale ingredients
  cover HPZ feints, an SSZ returning-boss extra pair, DEZ guard detection and a
  delayed Doomsday tail missile; these remain unactivated pending qualification.
  Cold routes, character
  breadth, full later-zone authoring and visual verification remain in progress.

- **Level authoring:** Named projects create, edit and reopen complete authored
  levels for Sonic 1, Sonic 2 and Sonic 3&K using ROM-derived asset libraries.
  Atomic saves retain placements and missing dependency records; close and quit
  protect unsaved work. Start/cursor
  playtests use fresh native runtime snapshots and return to the authored
  document with selection and undo history. Host-validated exports support
  S1/S2 v1 and S3K v2, including a native results goal for S3K. Title Tools and
  the editor menu expose creation, import, properties, resizing and
  Save As; pattern browsing, descriptor controls, local collision edits, erase
  and spawn inspection complete layout authoring. A controller-first HUD drawn at
  window resolution frames the level with a brush hotbar, a full-screen library
  of ROM-art thumbnails, a tools wheel, block and chunk workbenches and button
  prompts for the pad in use. It is the sole built-in editor UI, with world-space
  editing guides underneath; a separate OpenGGF-Editor provider can replace it
  through the window-chrome extension.

- **S3K level entry:** Captures that omit the title presentation complete the
  destination's deferred player assembly before its animation and art-loading
  tail, allowing ordinary movement after a fresh zone handoff. Publication also
  restores the destination ring count after the title boundary temporarily
  retains the source count, including rewind across that boundary. Ordinary
  checkpoint reloads restore post activation before the first object pass.
  Opted-in checkpoint invitations defer shared art until native queue admission
  and reuse that work for their marker and bonus stars. The Slots bonus stage
  reads its original board and entry position from the supplied ROM. Development
  mod captures retain the accepted loader when restoring creator-owned child graphs.
  AIZ rewind preserves completed miniboss spawn history after the native results
  owner retires.

- **S3K Knuckles routes:** Locked-on Mushroom Hill deletes the standalone S&K
  intro before it can freeze the camera and take player control. Its native
  `SK_alone_flag` prerequisite is kept separate from the selected character.
  Launch Base's placed final-boss prelude now admits its native camera gate,
  retains the ship's activation timer and restores the complete child graph.
  Its carrier continues into Mushroom Hill with released player control.
  Mushroom Hill's independent Knuckles route switch survives its press actor's
  native deletion and rewinds without retaining that erased parent. Its end boss
  preserves the native health floor and escapes at health one after the hit flash,
  with character-specific speed, native arena wrapping and music-fade waits,
  and the score awarded after the clock stops at the escape threshold. Its hit
  proxy and spikes recreate from captured placement, retain the exact boss
  identity, and retire at the native escape threshold while their owner remains alive.
  Preserved cold Knuckles inputs now complete both Mushroom Hill acts and the
  ship handoff into playable Flying Battery, with world replay and load-history checks.

- **S3K endings:** Sonic and Tails continue from Death Egg or Doomsday into
  the Tornado rescue, earned Super Emerald restoration, reunion, ROM credits
  and their progress-dependent final scenes. Knuckles continues from the final
  Sky Sanctuary fight through his plane flight, island credits and terminal
  scene. Scene resources, palette and animation owners, terminal Start input
  and rewind state remain inside the native level lifecycle. Island-lift sprites
  and the Master Emerald, waterfall and torch palette cycles follow their native
  object and screen-event timing. Ending resource initialization preserves
  scroll handling in other zones. Doomsday retires
  registered followers at its native entry and rebuilds restored scroll output
  without advancing the screen event.

- **Controller authoring:** Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
