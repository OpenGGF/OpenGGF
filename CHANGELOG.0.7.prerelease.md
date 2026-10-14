# OpenGGF 0.7.prerelease — Development changelog

Unreleased. The earlier development themes remain in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Gameplay and presentation

- **S3K level entry:** Captures that omit the title presentation complete the
  destination's deferred player assembly before its animation and art-loading
  tail, allowing ordinary movement after a fresh zone handoff. Publication also
  restores the destination ring count after the title boundary temporarily
  retains the source count, including rewind across that boundary.

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
  scroll handling in other zones. Donated Sonic 2 powered controllers restore
  palette and ring timing, current sparkle ownership and animation scripts through
  rewind. Native ending bindings restore each controller's own RAM after rebinding
  and preserve incoming physics before the scene's powered writers run. Doomsday retires
  registered followers at its native entry and rebuilds restored scroll output
  without advancing the screen event.

- **Controller authoring:** Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
