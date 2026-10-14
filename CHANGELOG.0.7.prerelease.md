# OpenGGF 0.7.prerelease — Development changelog

Unreleased. The earlier development themes remain in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Gameplay and presentation

- **Level authoring:** Named projects create, edit and reopen complete authored
  levels for Sonic 1, Sonic 2 and Sonic 3&K using ROM-derived asset libraries.
  Atomic saves retain placements and missing dependency records; close and quit
  protect unsaved work. Start/cursor
  playtests use fresh native runtime snapshots and return to the authored
  document with selection and undo history. Host-validated exports support
  S1/S2 v1 and S3K v2, including a native results goal for S3K. Title Tools and
  the editor command palette expose creation, import, properties, resizing and
  Save As; pattern browsing, descriptor controls, local collision edits, erase
  and spawn inspection complete layout authoring.

- **S3K level entry:** Captures that omit the title presentation complete the
  destination's deferred player assembly before its animation and art-loading
  tail, allowing ordinary movement after a fresh zone handoff. Publication also
  restores the destination ring count after the title boundary temporarily
  retains the source count, including rewind across that boundary.
  Native title-card objects retain their fixed slots and child lifetimes after
  omitted presentation, with exact graph restoration before gameplay placements.
  Initial touch publication follows each object's native range/deletion tail,
  and a full object-table reset retires both collision buffers before slot reuse.
  Identity-keyed participant contact maps encode stable team IDs through
  recreation while preserving their live associations and ordered-map behavior.
  Native Act3 continuations bypass the whole title graph, preventing orphan
  teardown from consuming an absent art lease. Fresh native loads clear the
  retained random seed before sprite dispatch,
  while previews and seamless continuations preserve their sequence.
  Sky Sanctuary opening bridges use the native top-contact surface and exact
  overlap band, retaining the relative landing write before floor reset.
  Slot bonus-stage layouts, player starts, prize/reel tables and cell animations
  load from the runtime ROM, with no copied asset fallback; panel publication
  retains its allocation-free path.
  Its arrival controller retains the native light divider and ROM palette cycle,
  admits the P2 release helper through the native continuation scan and retires
  after the final light write. Tails-led teams preserve their native branch.
  Initial touch publication follows each object's native range/deletion tail,
  and a full object-table reset retires both collision buffers before slot reuse.
  Identity-keyed participant contact maps encode stable team IDs through
  recreation while preserving their live associations and ordered-map behavior.

- **S3K underwater objects:** Large-bubble pickup follows persistent character
  identity for leaders and followers. Sonic clears the rolling state; Tails
  and Knuckles retain it with the ROM's literal radii and position adjustment.

- **S3K objects:** Monkey Dude retains five independent arm and hand objects,
  their physical predecessor links, native dispatch and retirement order, and
  the body's explosion state before initialization. The complete graph restores
  and replays without creating duplicate children. Its body preserves the ROM
  placement flip and collision origin independently of the player's side.
  Monkey Dude and Bloominator projectiles activate in their own initialization pass,
  use native culling bounds and retain marked slots until the next dispatch,
  including the complete object-manager culling pass.
  Rhinobot waits for its native placeholder render sign and keeps initialization
  and delayed slot retirement as separate phases, including through rewind.
  It remains dormant while vertically hidden.
  AIZ muzzle flares keep their native initialization, raw-animation and delayed
  retirement phases. Their physical parent link reads cleared or reused slots
  without borrowing a detached parent, and restores through rewind.
  Barrel shots draw at their native stationary initialization position and retain
  the cleared ROM raw-animation state through their pause and launch phases.
  Their exact live cutscene and fight parent links restore independently of
  the shot's camera-relative position.
  Barrel waits allocate the next pair on native timer underflow, before their
  signed-counter return tail.
  Common shots retain their final wait's deletion marker through the draw pass,
  clear on the next dispatch and keep their native lifetime while offscreen.
  Firing and drop-position cycling share the native byte counter, including
  signed reads and wrap, so both facing directions select their native columns.
  The cutscene explosion controller owns a real sprite-table slot, preserving
  allocation-before-RNG, both random words and native retirement through rewind.
  Its barrels select the real falling-shot family, preserving the cutscene's
  cleared collision byte in independent explosion children. Floor and final
  animation callbacks retain their sprite slots through the native draw pass
  before clearing on the following dispatch, including through rewind.

- **S3K breakable floors:** Cork's placed record becomes its first moving
  fragment, preserving checked sibling allocation, native motion and render
  retirement through rewind. Ice Cap uses the native mapping nibble and its
  separate fragment velocity table.

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
  object and screen-event timing. Paired pattern uploads wait for their actual
  decompression child and publish at the supported VInt; duplicate descriptors
  preserve FIFO ownership through rewind. Capture entry preserves native camera locks
  and scene origins, and capture fades retain their production owner. Ending resource initialization preserves scroll handling
  in other zones. Donated Sonic 2 powered controllers restore
  palette and ring timing, current sparkle ownership and animation scripts through
  rewind, while normal Tails restores retain his body and balance profile.
  Native ending bindings restore each controller's own RAM after rebinding
  and preserve incoming physics before the scene's powered writers run. Doomsday retires
  registered followers at its native entry and rebuilds restored scroll output
  without advancing the screen event.

- **Capture and controller tools:** Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
  An individual trace segment resolves its validated containing run's declared
  movie, preserving shared-movie priority and rejecting unsafe source aliases.

Rewind verification compares private records and complete captured opaque arrays
by content, with explicit failures when snapshot inspection cannot complete. It also
checks all captured ObjectManager gameplay cursors and contact buffers. Palette
copies retain complete raw RGB comparison.

The AIZ fire handoff retains its native scene slots and delayed retirement
markers. Its transition floor preserves the native solid tail and prevents
false edge balancing after the coordinate change. Its miniboss results retain
that SST as the in-level title owner; real title children drive the ring reset,
display hold and retirement through their native movement and visibility latches.
Hidden-monitor conversion retains the occupied native SST through replacement
cleanup. Both special-stage return prefixes pass; broader complete-route parity
remains separate from these scoped native-owner checks.
