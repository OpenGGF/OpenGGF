# OpenGGF 0.7.prerelease — Development changelog

Unreleased. The earlier development themes remain in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Gameplay and presentation

- **Colour expansion:** Settings → Display offers a saved Full/Limited range
  independently of the existing colour profile. Full keeps current output;
  Limited caps palette RGB at 238 after fades and profile conversion, including
  creator colours, with live normal and underwater palette refresh. The palette
  backdrop and headless gameplay captures use the same saved setting.

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

- **S3K save slots:** A file saved by the native SaveGame (act results, Knuckles'
  Lava Reef exit and both Hidden Palace exits) now resumes at the level that exit
  leads to, as the original does, instead of the level it was saved in. The
  data-select menu reads slots from the configured save root.

- **S3K bonus stages:** Gumball bumpers disappear when hit, springs are fully
  solid and crumble in the same pass, gumballs drop from the container with the
  machine's native random seed, ejected balls also push the second player, and a
  companion that drops into the exit is removed as in the original (it rejoins
  on return). Pachinko orb rewards follow the level frame counter. All three bonus
  traces now replay without errors. The Slot cage keeps re-centring the machine
  through the goal wind-down, so the machine stays where the original shows it,
  the Gumball background stays pinned as in the original instead of scrolling
  with the camera, and Pachinko's background survives rewind.

- **S3K animated scenery:** Angel Island, Hydrocity, Carnival Night, IceCap,
  Launch Base, Mushroom Hill, Sandopolis and Lava Reef animated tiles change on
  the original frame instead of one frame early, through the same once-per-frame
  upload as the hardware's VBlank transfer queue, including through rewind. Angel
  Island 2's first tree is restored on every frame before the scroll point, as in
  the original. Hydrocity 1's background waterline follows the original routine:
  at the waterline both repair strips load and the other animations pause, and far
  from it the last drawn strip stays.

- **S3K special stage results:** The results card sits on the locked-on game's
  grey backdrop instead of white, matching native frames of won and failed stages.

- **S3K zone title cards:** Every level entry that passes through the game's level
  routine fades the level in from black over its first 21 frames, with the player,
  HUD and card already at full colour. This covers zone changes, new games from the
  data select, restarts, special and bonus stage returns, and loads without a card.
  Previously the level stayed black and then appeared at full brightness. The data
  select no longer fades the whole screen, card included, back in on top. Seamless
  act changes still do not fade, as in the original. The score, time and ring
  counters are drawn over the card's red banner, as on hardware. Direct boots, new
  games, restarts and bonus stage entries and returns now hand control over with
  the card still holding for its last 22 frames, as zone changes already did, instead
  of keeping the player frozen for 90 frames. Restarts and bonus returns no longer
  fade the card in from black.

- **S3K objects and bosses:** The Angel Island end boss starts its defeat at the
  native hit check of whichever phase it is in. When the object table is full,
  pending placements wait for the next camera block crossing, which restores the
  Hydrocity fan timing. Ending reunion loads use measured native decompression
  costs, and the credits start on the original frame.

- **S3K level entry:** Captures that omit the title presentation complete the
  destination's deferred player assembly before its animation and art-loading
  tail, allowing ordinary movement after a fresh zone handoff. Publication also
  restores the destination ring count after the title boundary temporarily
  retains the source count, including rewind across that boundary. Ordinary
  checkpoint reloads restore post activation before the first object pass.
  Opted-in checkpoint invitations defer shared art until native queue admission
  and reuse that work for their marker and bonus stars. Development
  mod captures retain the accepted loader when restoring creator-owned child graphs.
  AIZ rewind preserves completed miniboss spawn history after the native results
  owner retires.
  Admitted title and save-menu ROM patterns survive the following level overlays,
  retaining unwritten spans while newer zero/art uploads supersede them.
  Prepared installation rejects detached art owners and stale generations.
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
  overlap band, retaining the relative landing write before floor reset; a
  companion standing on the moving cutscene bridge waits without the edge-balance
  pose, because the bridge sets its own native status bit 7. In zones that use
  sprite-table masking (Gumball, Sandopolis, Lava Reef, Hidden Palace and Sky
  Sanctuary), Tails' body again covers his tails as in the native sprite table.
  Its clouds and collapsing columns retain native foreground priority, and
  the HUD RINGS label uses its dedicated ROM R glyph.
  Teleporter beams retain the selected ROM art bank through recreation,
  including SSZ transport priority and whole-sky rewind publication.
  Slot bonus-stage layouts, player starts, prize/reel tables, background
  deformation and cell animations load from the runtime ROM, with no copied asset
  fallback; panel publication retains its allocation-free path. The Slot Machine
  now rewinds completely, and its dedicated sprite uses the host mappings, pattern
  banks and a fresh tail, so the goal fade shows the native Sonic roll tiles on
  Knuckles and keeps Tails' last body tiles; the configured donor art returns on exit.
  Its rotating layout is now displayed with the sprite table of its own frame,
  behind the player, cage and rewards, and the goal starts its wind-down on the
  following frame. The goal fade dims the palette in the native red, green, blue
  steps every third frame before the level reload fades the rest, the capture
  palette cycle uses the correct colour table and changes mode a frame later,
  and all three bonus stages show the lives counter. Gumball and Glowing Spheres
  background animations follow the native camera-driven art and appear on the
  native frame.
  Its arrival controller retains the native light divider and ROM palette cycle,
  admits the P2 release helper through the native continuation scan and retires
  after the final light write. Tails-led teams preserve their native branch.
  Initial touch publication follows each object's native range/deletion tail,
  and a full object-table reset retires both collision buffers before slot reuse.
  Identity-keyed participant contact maps encode stable team IDs through
  recreation while preserving their live associations and ordered-map behavior.

- **S3K shields and rewind in Mushroom Hill:** A shield or invincibility stars
  carried through an act change that keeps the object table (Hydrocity, Carnival
  Night, Launch Base, Mushroom Hill and Marble Garden) stay on screen with the
  player instead of vanishing while the status remained. Rewinding in Mushroom
  Hill restores mushroom-cap bounces, curled vines, catapults, dropping platforms,
  launched Mushmeanie shells and the act 2 Knuckles cutscene's player lift
  exactly. Launch Base falling platforms keep their riders and Marble Garden
  sinking mud keeps each player's sink depth through rewind.

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
  and scene origins, and capture fades retain their production owner. Ending
  resource initialization preserves scroll handling
  in other zones. Donated Sonic 2 powered controllers restore
  palette and ring timing, current sparkle ownership and animation scripts through
  rewind, while normal Tails restores retain his body and balance profile.
  Native ending bindings restore each controller's own RAM after rebinding
  and preserve incoming physics before the scene's powered writers run. Doomsday retires
  registered followers at its native entry and rebuilds restored scroll output
  without advancing the screen event. Knuckles' flight stops Mecha Sonic's
  defeat rumble, so the island and its mask settle where the original puts
  them and no black line crosses the credits. The second credits part shows its
  names again instead of an empty sky, each entering at the bottom of the screen
  where the original draws it. The credits start on the frame the original
  starts them, and with every emerald the second part lifts the island away and
  stops new clouds where the original does. The island then scrolls out of the
  background and the original's starfield and rolling sea replace it. The credit
  names now scroll in step with the clouds, as the original's picture does. After the
  credits the closing logo takes the original's place for each ending, with its
  trademark: above Knuckles rather than across him. It fades in through white, and with
  every Super Emerald Knuckles flashes pink as he does in the original. The Hidden
  Palace teleporter pad no longer reappears under the island during Knuckles' credits:
  as in Sonic 2 and 3&K, a camera jump back into a level's first block reloads nothing
  behind the object cursor. The first scrolling pass keeps the names on the original's
  line.

- **Capture and controller tools:** Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
  An individual trace segment resolves its validated containing run's declared
  movie, preserving shared-movie priority and rejecting unsafe source aliases.

Rewind verification compares private records and complete captured opaque arrays
by content, with explicit failures when snapshot inspection cannot complete. It also
checks all captured ObjectManager gameplay cursors and contact buffers. Palette
copies retain complete raw RGB comparison.
Typed level and object-manager snapshot comparisons also accept equal untyped
content. Rewinding to a point before a level's first object-load pass reloads its
objects again (Flying Battery's act-one boss included), the Angel Island intro keeps
drawing Sonic after it is recreated, a departing scripted carry-in Tails is restored
as the same sprite, and a recreated skid-dust puff keeps its owner's art.

The AIZ fire handoff retains its native scene slots and delayed retirement
markers. Its transition floor preserves the native solid tail and prevents
false edge balancing after the coordinate change. Its miniboss results retain
that SST as the in-level title owner; real title children drive the ring reset,
display hold and retirement through their native movement and visibility latches.
Hidden-monitor conversion retains the occupied native SST through replacement
cleanup. Both special-stage return prefixes pass; broader complete-route parity
remains separate from these scoped native-owner checks.
