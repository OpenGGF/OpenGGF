# OpenGGF v0.7 Changelog

## v0.7.20261105

Analysis range: `v0.6.20260911..develop` at `0d8ffdcd6` (`6303` commits, `4592` non-merge
commits, `11820` files changed, `2319889` insertions, `104228` deletions). Most of the line
count is committed trace, audio and route fixtures rather than engine code. Work merged after
that commit is covered by the entries it added before the release was cut.

A campaign-breadth and platform release. Sonic 3 & Knuckles grows from the 0.6 Angel Island →
Hydrocity slice into a broad playable implementation of the locked-on game, through Death Egg,
The Doomsday Zone and the native Sonic, Tails and Knuckles endings and credits. Its evidence is
bounded: no character's campaign is certified complete, and its full-run trace chains are still
open (see [Known limitations](docs/changelog/v0.7-release-summary.md#known-limitations-and-release-status)).
Sonic 1 and Sonic 2 keep their complete flows and gain further native corrections, Sonic 2 adds
Knuckles in Sonic 2, and both recognise their original REV00 images. Around the games, 0.7 adds a
cartridge-shelf title hub, Game Setup and tabbed Settings, supported 352- and 400-pixel widescreen,
region-timed simulation with optional display-locked pacing and interpolation, named level-editor
projects, OpenGGF Stageplay for directing films and cutscenes, a configurable rewind budget,
translated interface drafts, and the bundled Time Attack mod with online racing. The mod framework
and its example mods are included as an unpublished preview; the Mod API publishes in 0.8.

This file is the frozen 0.7 release changelog. It folds together the earlier 0.7 development
themes, the later `CHANGELOG.0.7.prerelease.md` entries and the unreleased notes in
`CHANGELOG.md`, grouped by area with duplicates removed; the original files remain in git history
at the commit above. The website and GitHub release copy, with validation status and accepted
limitations, is [docs/changelog/v0.7-release-summary.md](docs/changelog/v0.7-release-summary.md).
Integration evidence is in the [development ledger](docs/changelog/v0.7-prerelease-detailed.md),
and trace evidence in [docs/status/trace-frontier-log.md](docs/status/trace-frontier-log.md).

### Sonic 3 & Knuckles

#### Level entry, saves, title and transitions

- **S3K level entry:** Initialize the camera with the ROM’s zero/maximum clamps,
  independently of the runtime minimum bounds. This removes the opening AIZ1
  camera offset while preserving MHZ1’s separate locked-on camera focus.
  Live zone handoffs now run the fresh title and terrain-loading lifecycle,
  restoring players before the initial sprite pass without adding a frame.
  Rewind preserves a fresh-load insta-shield's pending attack and slot state,
  leaving its registration to the next ordinary status tick. Fresh title
  boundaries retain their deferred assembly and publication state on rewind.
  Carnival Night background rebuilds preserve captured event scroll state,
  including fresh entry before its first deformation dispatch, while restoring
  the displayed scroll output without running another gameplay publication.
  Knuckles’ second-act Carnival Night event now admits its separate teleporter
  and capsule at the ROM trigger, with allocation failures preserving the native
  clamp transition. The object retains its later, independent airborne arming gate,
  and successful capsule admission appends its original raw PLC descriptor.
  HCZ hanging bars read the native raw companion-controller directions,
  preserving neutral input while the follower CPU steers. Full native saves
  clear collected giant rings at their seven existing save points, including in
  No Save games, restoring giant-ring availability in later levels. Ordinary
  persistence requests, special-stage and lives saves, and death reloads preserve
  collected rings.

- **S3K level entry:** Captures that omit the title presentation complete the
  destination's deferred player assembly before its animation and art-loading
  tail, allowing ordinary movement after a fresh zone handoff. Publication also
  restores the destination ring count after the title boundary temporarily
  retains the source count, including rewind across that boundary. Ordinary
  checkpoint reloads restore post activation before the first object pass.
  Fresh title publication retains the completed game-owned camera position.
  The initial camera-focus calculation includes the original fresh-entry offset
  for Death Egg 1, Hidden Palace's second act, and Knuckles' Carnival Night 1
  and Lava Reef 1; checkpoint entries keep their saved focus.
  Collision layout reads preserve the ROM's encoded word addressing and supported
  aliases. Hydrocity's act handoff retains unwritten 128×128 definitions through
  rewind.
  Opted-in checkpoint invitations defer shared art until native queue admission
  and reuse that work for their marker and bonus stars. Ordinary starposts retain
  their ring-based bonus invitation after all seven Chaos Emeralds are collected.
  Failed ordinary orbit allocation still saves the checkpoint without submitting
  bonus art; an admitted invitation survives ring loss and retains its selected
  bonus family through entry and rewind. Development
  mod captures retain the accepted loader when restoring creator-owned child graphs.
  AIZ rewind preserves completed miniboss spawn history after the native results
  owner retires.
  End signs allocate after the retained native boss/control slot, preserving
  later Angel Island bridge slot identities. Angel Island replaces its miniboss
  with the end-sign control in the same physical slot, polls fatal HP after the
  current routine, and preserves native wait words and independent music and
  explosion lifetimes. The Angel Island miniboss body, arm and barrels retain
  their native sprite slots through the defeat wait, continue their own animation
  and callbacks, and retire after the physical parent flag changes. Rewind
  preserves that parent header across the sign-control replacement.
  The Angel Island control creates all three gradual camera workers in native
  order, including min-Y, and releases each at its original boundary instead
  of snapping min-Y or reserving a remembered results slot.
  Admitted title and save-menu ROM patterns survive the following level overlays,
  retaining unwritten spans while newer zero/art uploads supersede them.
  IceCap's in-frame act handoff preserves known resident 16×16 pattern words
  until queued publication, with the destination's collision tables and rewind.
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
  Fresh level loads submit their Nemesis art before the title wait, and the
  first title iteration prepares that queue without dispatching the card twice.
  Title entry preserves level rebinding through callbacks while keeping the
  hardware tail outside the retained presentation owner.
  Sky Sanctuary opening bridges use the native top-contact surface and exact
  overlap band, retaining the relative landing write before floor reset; a
  companion standing on the moving cutscene bridge waits without the edge-balance
  pose, because the bridge sets its own native status bit 7. Gameplay sprite tables
  can enforce native scanline piece and pixel budgets, including partial sprites,
  offscreen consumption and overflow-armed masking. ROM HUD pieces share those
  budgets before expanded art is replayed; wider views retain native capacities.
  Angel Island foreground plants are culled after parallax, so offscreen plants
  no longer consume that capacity and hide parts of Sonic. Display settings offer
  a saved Sprite scanline limit toggle, disabled by default for normal play.
  Trace captures temporarily enable native limits for image comparison and restore
  the caller preference afterward, including failed captures.
  In zones that use
  sprite-table masking (Gumball, Sandopolis, Lava Reef, Hidden Palace and Sky
  Sanctuary), Tails' body again covers his tails as in the native sprite table.
  Its clouds and collapsing columns retain native foreground priority, and
  the HUD RINGS label uses its dedicated ROM R glyph.
  Rotating SSZ posts and their carriers retain solid support while holding
  players under positive object control, with fresh-jump release and signed
  bit-7 contact rejection preserved.
  Title retirement and departing EggRobo fly-bys submit the native enemy-art
  restoration requests, with physical publication and rewind ownership retained
  after the producer disappears. Death Egg's title owner likewise submits the
  native Spikebonker and Chainspike art batch in both acts. Incidental art producers preserve blocked
  title work and respect both FIFO limits. Scaled fly-by pixels remain incomplete.
  Teleporter beams retain the selected ROM art bank through recreation,
  including SSZ transport priority and whole-sky rewind publication.
  Slot bonus-stage layouts, player starts, prize/reel tables, background
  deformation and cell animations load from the runtime ROM, with no copied asset
  fallback; panel publication retains its allocation-free path. The Slot Machine
  now rewinds completely, and its dedicated sprite uses the host mappings, pattern
  banks and a fresh tail. Reward rings and spikes retain native signed interpolation;
  ring payouts play the ROM sparkle script with its retained animation cursor and
  sprite priority. The goal fade shows the native Sonic roll tiles on
  Knuckles and keeps Tails' last body tiles; the configured donor art returns on exit.
  Its rotating layout is now displayed with the sprite table of its own frame,
  retaining native row-major cell admission and opaque overlap order behind the
  player, cage and rewards, and the goal starts its wind-down on the
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

- Seamless reloads preserve runtime-art admission, title camera-release timing
  and independent end-state flags. Frame-boundary reloads no longer leave a
  second rewind-boundary marker or suppress the following gameplay oscillator tick.

- Tails alone enters CNZ1, and MHZ1 with Sonic 3 locked on, with the native
  fly-in: CPU-held flight to the right until landing.

- S3K keeps one sub-pixel camera X position across level loads, as the shipped
  game does, so Doomsday's autoscroll, the Death Egg escape, the Lava Reef boss
  scroll and the Hidden Palace emerald theft share it instead of each starting
  from zero. The ending keeps its start camera until its art has loaded, and
  trace segments take the recorded sub-pixel position from their level-entry RAM.
  Entry metadata preserves unsigned 32-bit camera longs as their native bits.

- S3K fresh level loads count the terrain wait's final VBlank in the live game
  loop, as the recording driver already did. The remaining load-time lag
  frames are documented, not estimated.

- **S3K fresh-level handoff:** The outgoing ring count remains visible through
  initial title publication and the destination count takes effect at release.

- **S3K save slots:** A file saved by the native SaveGame (act results, Knuckles'
  Lava Reef exit and both Hidden Palace exits) now resumes at the level that exit
  leads to, as the original does, instead of the level it was saved in. The
  data-select menu reads slots from the configured save root.

- S3K saves retain collected giant rings and Chaos Special Stage rotation after
  reload, and resume stock zones at their native starting act.

- **S3K SEGA screen:** The title opens as the ROM's does: 22 frames of fade to
  black, then the SEGA logo fades in over 22 frames in the console's own palette
  steps, and the chant starts as the fade ends. Pressing Start during the logo
  now stops the chant and runs the console's palette transition into the Sonic
  animation, instead of jumping straight to the menu or skipping the chant. The console's boot and the logo's decompression time are
  not yet included, so the logo still appears earlier than on hardware.

- **S3K zone title cards:** Every level entry that passes through the game's level
  routine fades the level in from black over its first 21 frames, with the player,
  HUD and card already at full colour. This covers zone changes, new games from the
  data select, restarts, special and bonus stage returns, and loads without a card.
  The level submits its queued art during the load and prepares the first item
  in the first title-loop step, keeping destination setup on the original frame.
  Destination setup stays black until the new card takes over presentation, and
  new zone and bonus cards discard the previous card's published picture so level
  art cannot flash through before their black backdrop appears.
  Bonus title cards load the complete native art batch, including the unused act
  number, then submit the stage's terrain art once the title is ready and Nemesis
  work clears, while retaining the bonus card's own layout.
  Hydrocity 1's direct water path publishes prepared art in three-pattern batches,
  including after rewind; replacing or failing a load cannot reuse its selection.
  Saved-origin bonus returns use that title/Nemesis handoff for the restored
  level's terrain and retain its native fixed title owner and linked children
  through their normal retirement; ordinary host loads keep their existing art ownership.
  Previously the level stayed black and then appeared at full brightness. Each fade
  step and each move of the card reaches the screen with the same frame's sprites and
  HUD, one frame after it is computed, as the original's palette and sprite uploads
  do, and a rewind shows what was on screen without creating a pending palette
  upload when the restored owner already matches that displayed fade. The data
  select no longer fades the whole screen, card included, back in on top. Seamless
  act changes still do not fade, as in the original. Hydrocity resets rings and
  time after its act-change card pieces stop sliding. The score, time and ring
  counters are drawn over the card's red banner, as on hardware. Direct boots, new
  games, restarts and bonus stage entries and returns now hand control over with
  the card still holding for its last 22 frames, as zone changes already did, instead
  of keeping the player frozen for 90 frames. Restarts and bonus returns no longer
  fade the card in from black. Bonus entry holds the source camera at the
  native restart boundary.

- S3K level select uses native zone save-card previews, and data select uses the correct Flying Battery palette.

#### Angel Island and Hydrocity

- **AIZ1 routes and rewind:** capture the hollow-tree reveal counter and intro Super Sonic
  palette timer/frame, preserving tree reveal children and palette cadence after
  restoring gameplay. Add a native route matrix with independently reported intro,
  cutscene, tree and act-reload replay checks. Route controllers complete the
  viewport and movement-donor axes using ordinary inputs and live object gates.
  Monkey Dude keeps climbing after the camera passes its initial visibility
  window and starts each animation on its native dispatch, preserving the moving
  body's collision timing and player bounce. Its five linked arm children retain
  native fractional positions and delayed angles, releasing one coconut from
  the hand's prior position instead of repeating a body-animation throw. Hollow-tree
  release uses the native fixed collision radii until landing, preserving Tails'
  first terrain contact after leaving the tree.
  The fire reload keeps its horizontal camera lock until the native reveal
  releases it, instead of easing back toward the loaded act's default bounds.
  The AIZ2 boss retains its native owner through results and the Knuckles
  cutscene, preserving button input release, the drawbridge's delayed eject,
  and captured rider state through rewind. Playable construction and level reset
  bind rules from the selected world before the first CPU dispatch, retaining
  the bootstrap fallback for pre-session construction.

- **Angel Island intro rewind:** Surviving scattered emeralds release their
  collector reference when cutscene Knuckles retires, so they no longer keep an
  invalid object reference in rewind snapshots.

- The AIZ fire handoff retains its native scene slots and delayed retirement
  markers. Its transition floor preserves the native solid tail and prevents
  false edge balancing after the coordinate change. Its miniboss results retain
  that SST as the in-level title owner; real title children drive the ring reset,
  display hold and retirement through their native movement and visibility latches.
  Hidden-monitor conversion retains the occupied native SST through replacement
  cleanup. Both special-stage return prefixes pass; broader complete-route parity
  remains separate from these scoped native-owner checks.
  S3K item monitors retain contents only when a later object slot admits them.
  A full tail leaves a remembered broken shell without an icon or reward, and
  rewind can capture that state immediately after the break.

- The AIZ2 bombing ship's two propellers are now native objects. They take
  the slots after the ship, turn at the original three-frame rate, and
  disappear when the ship's departure redraw begins. The departing ship hands
  its own slot to the small Eggman craft and starts the background-tree maker
  after it. The loop boundary moves only once that redraw completes.

- The AIZ2 background-tree maker now places all seventeen trees, one per frame,
  in the slots after itself. It retries when no slot is free and gives each tree
  its original drawing priority.

- AIZ2 ending drawbridge uses its native 12 logs, removing the extra cliff-overlapping
  endpoints, and shows staggered explosion puffs when Knuckles breaks it.
  Knuckles uses the ROM standing pose and run/jump/laugh animation cadence.

- **HCZ1 miniboss:** retain the vertical arena lock and full rocket slowdown;
  admit the arena at the native gameplay position across supported viewport widths;
  match the body, rockets and individual exhausts to ROM sprite priorities and
  flicker gates. Restore the whirlpool slowdown, bubble animation and depth,
  falling rocket debris, and bubble cleanup at the defeat handoff, including
  after rewinding the explosion sequence.

- HCZ Act 1 miniboss retains both tracked native players through vortex cooldown and sets them airborne when the water effect is deleted.

- HCZ2 capsule results publish native ending poses and submit results art on their owning dispatches; the HCZ slice replay is green.

- HCZ2 boss blades start their floor-hit raw animation from the cleared timer, restoring native water-chute launch timing.

- HCZ2 end-boss attack setup runs on the native wait-underflow pass; spray release performs its final interaction once.

- HCZ2 end-boss swing resumes from the live rise velocity and retains the native fractional Y position.

- HCZ2 end-boss spray preserves the native shared player-range cursor, preventing an extra Tails grab after Sonic is carried.

- HCZ2 end-boss suction retains the native displacement across both players, including equal-X contacts.

- HCZ2 end-boss camera release preserves the separate main-initialization dispatch,
  and its water platform starts raw animation from the fresh slot's cleared timer.

- S3K HCZ2 renders the preceding screen-shake setup result, and LRZ big doors
  finish opening off screen before unloading. Both corrections preserve Tails CPU
  respawn timing at the native visibility boundary. S3K catch-up teleports also
  clear the inherited hurt blink before sprite submission, keeping the cached
  on-screen flag and recovery-flight timer native. Checkpoint entry retires
  setup-only sidekick hiding when the native CPU skips its arrival intro.

#### Marble Garden, Carnival Night, IceCap and Launch Base

- MGZ swinging platforms use native fractional chain arithmetic and execution-order inputs instead of fitted slot/angle carry tables; spring animation starts from the native cleared timer.

- MGZ Bubbles waits until the next object pass after its offscreen continuation
  resumes, preserving native movement and collision timing. This restores Tails'
  same-frame hurt response after Sonic destroys the badnik.

- **MGZ seamless transition:** Keep the shifted camera bounds until the native
  Act 2 release instead of easing toward the newly loaded level bounds.
  The miniboss also retains its native EndSignControl slot while explosions
  continue independently, so results restore player animations on the ROM's pass.
  Results also use the native global allocator after waiting for the player to land.

- **MGZ2 to CNZ1:** Tails carries Sonic into Carnival Night after the Marble
  Garden rescue instead of leaving Sonic to fall alone. Fresh level assembly
  restores the normal carry policy on configured sidekicks.

- **MGZ2 powered rescue:** Rebuilding art for rescue Tails retains Sonic's
  Super/Hyper controller, keeping palette cycling and boss-defeat
  detransformation active.

- **Carnival Night cylinders:** Unloading an inactive cylinder no longer releases
  a player held by another cylinder.

- **IceCap Act 1 miniboss:** Restore the accelerating ice-charge flashes,
  native ice-piece spread timing, falling/flickering destruction debris and
  independent palette/signpost handoff while explosions finish.

- **ICZ2 to LBZ1:** Arm the ground-launch intro during fresh zone assembly so
  Sonic springs out of the snow and regains control.

- **S3K Launch Base:** Cup elevators now resolve each player’s solid contact before checking capture, restoring airborne side collisions and normal landing admission, and preserve native movement and animation control through the NPC Knuckles cutscene. Rolling drums preserve native rider state when unloading, after completing their final player updates. The Act1 miniboss preserves the fatal-hit dispatch before its defeat countdown, including explosion timing through rewind. Carried results now finish their twelve real children before publishing control release, then initialize the next title owner on its following dispatch. Retained title owners reset level counters only after their ROM-backed art and moving children settle, preserving the independent presentation clock. Ribot creates its visual children before its first active orbit, matching the ROM initialization return. The seamless Act 2 reload holds the inherited camera limits until the title owner releases them, then runs its gradual boundary children in their native creation pass.

- LBZ phase-one explosion showers retain their own controller through boss deletion and rewind, allowing Knuckles' Big Arm replay to finish its comparisons.

- Big Arm submits its ROM art module during native initialization.

- LBZ2 submits native EggRobo-head art during head initialization.

- LBZ2 end-boss art waits for native camera admission and retains its queue owner through camera movement and rewind.

#### Mushroom Hill and Flying Battery

- **S3K campaign work:** the promoted development baseline includes further MHZ
  and FBZ work, with physical checkpoint activation, rewind and repeated death/reload
  coverage across MHZ's accepted rosters, supported donors and five viewport presets,
  plus cold Tails completion of both acts and native-pair Act 1 completion with
  rewind-verified boss and act/zone handoffs; LBZ Big Arm, Super Emerald sanctuary/progression, and powered
  effects. Sonic, Tails and Knuckles endings continue through their progress-dependent
  credits, terminal scenes and title return. Knuckles' flight wait requests quiet
  wind on the native cadence and preserves its order before music restoration.
  Rescue and return plane attachments use rewindable native child slots;
  full-table continuation retains the overwritten slot's native residue, and
  delayed deletion follows the ROM's dispatch boundary. Sandopolis now implements its four quicksand variants, including
  per-player capture, jump release, sand-slide movement and rewindable cooldowns,
  plus spring vines with shared tension, deforming surfaces, native landing boundaries and directional launches,
  sand rocks that break under rolling landings and release their riders,
  pushable rocks that fall onto their authored tracks, loop fall-through
  controllers, both static solid platform shapes, moving and spiked pillars,
  and connected push switches and doors, including special-rock activation and correct body-over-track layering.
  Sandopolis Act 1 now has layered desert parallax, foreground and background heat shimmer, scroll-driven
  background art and cycling sand colors, with preserved static desert tiles
  and consistent background wrapping in widescreen. Both acts also implement
  swinging platforms, rappel wires, oscillator-timed spawning sand blocks, rising walls and corks, and
  Skorp, Sandworm and Rockn. Terrain-driven sand slides run after camera tracking.
  The cold sand intro, Egg Golem arena and seamless
  Act 2 entry use native event owners. The golem arena opens at the equivalent
  centered native viewport in widescreen, so the right wall cannot prevent the
  admission camera gate from firing. Widescreen views of the arena end at the native
  view's right edge, so the player stops against the screen lock rather than an
  invisible wall mid-screen; at widths above 720 pixels the signpost still drops onto
  the arena floor instead of into the golem's sand pit. The last-checkpoint debug shortcut reloads
  destination events, so skipping to the SOZ2 boss no longer retains early-room
  darkness or omits the boss background. Arena and laser sprite masks clip later
  sprites, including the opening door and sinking golem beneath the sand, hiding
  exactly the masked scanlines rather than whole 8-pixel tile rows.
  Sprite overlap follows bucket and native slot order independently of terrain
  priority, and golem dust keeps its own priority across the sand boundary.
  Path swappers seed their crossing latch with the ROM's strict greater-than test and run their first
  check at once, so SOZ2's seamless entry, which places Sonic exactly on its opening marker, clears the
  high priority he carried in from Act 1.
  Detached golem parts retain their priority, Rockn eyes follow its walking direction,
  and the rising pyramid shakes terrain and sprites together. Its widescreen source
  window spans the viewport instead of repeating the native 512-pixel cache;
  background priority passes share the same wrap period. Wider pyramid views
  repeat existing masonry past the authored edge and stop before the foreground
  wall, preserving native movement bounds. Sprite-art replay
  and player queries avoid redundant temporary maps and static-art lookups
  while preserving immutable rewind frames.
  Spiked pillars resolve native art-word carries, and high-priority background
  sand hides submerged pillars and spikes.
  Act 2 couples light switches, ghosts,
  torches and palette fades, and implements the final boss, collapsing wall,
  capsule/results and the Lava Reef transition. Post-boss background rows remain
  intact during art loading and redraw in order, including widescreen margins.
  The independent exit follower
  survives boss retirement and rewind. Mechanism state survives kept
  stage returns, retained-plane rewind preserves captured palette ownership,
  and object graphs—including promoted collapsing platforms—reconstruct
  across rewind using stable object and player identities. Live rewind preserves
  the host audio clock across repeated death/reloads. Sonic 2 and S3K companions
  copy the leader's collision plane and art priority when flight recovery ends,
  and release stale engine platform support while retaining native interaction state.
  Fixed controller replays now complete both acts from cold native Sonic + Tails
  starts through playable destinations, with whole-world rewind checks across
  traversal, boss hits, capsule/results and the Lava Reef load boundary. Compatibility acceptance follows the
  supported donor roster and checks ROM-backed art and animations for each live
  participant before and after tested loads. Broader character/donor/viewport routes,
  the lower Act 2 rock puzzle and exact native parity remain documented in the
  per-act matrices.
  Giant-ring sanctuary entry uses the same emerald ceremony, palette,
  camera, background setup, and emerald/teleporter sprite art as direct sanctuary
  loading, including the ROM’s `$1701` level identity. Offscreen giant rings wait
  for art-queue capacity when wider visibility overlaps startup loading. Entry flashes
  retire their parent rings after the native animation advance, restoring explosion
  art on the correct object pass. MHZ end-boss debris
  retains the ROM trajectory when the boss faces left, and Madmole’s submerged
  body keeps its final collision position until the ROM’s deferred deletion.
  The MHZ endboss chase and ship departure use native arena coordinates at every
  viewport width; pillar scrolling retains the ROM division remainder and keeps
  its collision helpers aligned independently of the displayed camera inset.
  The weather-machine palette fade retains its captured target colours when
  recreated by rewind during either half of the transition.
  Horizontal swing bars retain their shared per-player release/cooldown state
  after rewind; twisted vines retain both riders through object recreation. The MHZ2 Knuckles press sequence flips Sonic and Tails into the ROM floor-grab
  pose when it switches to raw mappings, selecting by character identity so solo
  Tails uses his own frames as well. Cutscene doors retain their lowered state
  when streamed out and back in, and
  boss debris follows the native initialization and flicker sequence. Act 1
  camera limits use the locked-on ROM’s height rule for all characters, and
  the Act 1 boss and its thrusters stay alive during offscreen attack phases.
  The Act 1 miniboss gate, spawn and arena repeat use native camera coordinates
  at every viewport width, retaining centered framing through the inherited
  Act 2 results lock until its boundary releases. The seamless handoff retains
  animated background art and its accumulated scroll position. Seamless act
  reloads retain sprite publication through the resource handoff.
  Its defeat loads the explosion art and finishes the full burst sequence
  across the signpost handoff. MHZ Act 2 retains the waiting Knuckles controller
  until its camera trigger opens, allowing the leaf blower to lift Sonic out of
  the blocked lower entrance. The press actor retires offscreen while its
  independent lift finishes, restores the level palette, and centers the native
  leaf span in wider views. The Act 2 endboss survives forward-window admission,
  and its encounter parts retain explicit lifetime during offscreen phases.
  Its final-hit acknowledgement, capsule results and ship controllers now retain
  the correct arena/control ownership through the actual FBZ load. The ship's
  foreground scroll split draws its body and applies the same tile priority to
  sprite occlusion. Rewind preserves the arena scroll accumulator, spike subtype and last capsule
  contact through the ship handoff. Restoring player control preserves the ROM
  interaction address in both boss and signpost flows.
  Seamless act handoffs retain fixed object owners without duplicates, avoiding
  a rewind-capture crash after the MHZ signpost. The first player update also retains
  one Insta-Shield owner after the handoff, so post-load rewind does not remove a duplicate.
  Complete routes, finales, and continuous replay chains remain gates.

- **Mushroom Hill Act 2:** Restore the fade to Knuckles’ theme when his
  leaf-blower cutscene begins. Restore the end-boss ship, pilot and capsule art,
  the dash flames' palette, and the chase's canopy, pillar and spike-ball layers.
  Correct the pillar archive address so its upload cannot corrupt ring art.
  Restore the weather-machine and defeat explosion bursts, accelerating chase
  entry, lost-ring world rebasing, and Robotnik's gravity-driven stump handoff.
  Explosion controllers restore their exact owner across fresh rewind recreation
  and retire through the shared lifetime operations.

- **S3K shields and rewind in Mushroom Hill:** A shield or invincibility stars
  carried through an act change that keeps the object table (Hydrocity, Carnival
  Night, Launch Base, Mushroom Hill and Marble Garden) stay on screen with the
  player instead of vanishing while the status remained. Rewinding in Mushroom
  Hill restores mushroom-cap bounces, curled vines, catapults, dropping platforms,
  launched Mushmeanie shells and the act 2 Knuckles cutscene's player lift
  exactly. Unordered rider sets encode stable team membership independently of
  allocation history, while ordered collections retain their encounter order.
  Launch Base falling platforms keep their riders and Marble Garden
  sinking mud keeps each player's sink depth through rewind. Rewinding to the first
  frame of a Lava Reef load replays its background setup: act 2 no longer refills
  its background or adds a second Death Egg, and Knuckles keeps act 1's background
  chunk.

- Restore upright S3K egg capsule sprite and tile priority so the MHZ2 ending ship passes behind the capsule.

- MHZ mushroom parachutes release carried players after their feet reach terrain.

- **Flying Battery routes and object behavior:** the FBZ objects whose ROM
  delete-touch check is `Sprite_OnScreen_Test2` (screw doors, elevator cars,
  magnetic spike balls and pendulums, platform blocks, propellers, spinning
  poles, spring plungers, missile launchers and wall missiles, the exit hall,
  the egg prison and capsule debris) now cull with the same width-driven
  window that loads them (`$80 + screen width + $C0`, unchanged at native
  320). At 640 and 800 pixels they were deleted on the frame they loaded, so
  the `$0B68` screw door, its `$0BC0` elevator and the rest never appeared and
  Act 2 could not be completed on those viewports. FBZ enemy art now enters
  the ROM-ordered KosM queue; cage/chain animation writes and floating-platform
  and hazard-producer clock reads match their original byte/word semantics.
  Magnetic polarity uses the current level clock, and the missile companion
  exposes its original standing-balance width. Rotating platforms decode all
  native member rows and signed radii; offscreen solid push release retains its
  original animation write during death. Fresh S3K loads now run the initial
  pattern animation pass before gameplay, as the ROM does. S3K AniPLC art now
  publishes at its DMA-serving VBlank after the animation counter advances;
  rewind preserves both presented art and queued submissions. Gameplay sprite geometry and
  HUD labels retain the prepared table until its publishing VBlank, including
  skipped drawing, camera changes and rewind. Terrain scroll and sprite-occlusion
  masks publish with that table, keeping objects aligned with moving cameras
  across S3K zones; the AIZ2 forest-loop plane ring stays on the live camera
  in the same step as `AIZ2_DoShipLoop`, like `DrawTilesAsYouMove`, so a wrap
  on a lag frame no longer blanks the foreground. Numeric HUD tiles follow their own
  VBlank updates, and mutable player art stays paired with the prepared mapping. The Act 1 boss arms
  clamp their angles and release their chain state in the original order; defeat
  preserves the native wait and score bonus before the end sign. Converted arm
  and chain debris continues independently after parent retirement and rewind,
  uses the ROM's vertical cull, and capsule fragments apply MoveSprite gravity.
  Frozen solo Tails and Knuckles routes reach playable Act 2 from cold entry,
  with whole-world rewind checks through traversal, the boss and results.
  The ending
  pose retains existing plunger support across the seamless manager replacement. Zone-owned tumble
  presentation and snake-platform standing ownership follow the original routines. The donated Sonic 1
  route clears the elevator squeeze with an ordinary run-up and timed roll.
  The Act 1 miniboss submits its native hardware-timed art job, allowing results
  to reload Act 2; results allocation and the later title-owned enemy-art batch
  retain their native dispatch boundaries. The carried title initializes on its
  next dispatch, player control restores the native idle pose, and camera easing
  targets follow the Act 2 coordinate rebase. Retained title children and camera
  workers follow their original dispatch order. Horizontal chain grab regions stay invisible,
  preventing stray descending handles from appearing along the route. Descending chain mappings
  apply the full 16-bit art-tile addition, preserving their native palette and scenery priority.
  Chains reject hurt/dead grabs,
  vertical cages keep their orbit separate from the player ground angle, and
  magnetic platforms use the native ceiling and contact boundaries. Their chains
  retain the fixed end fitting without drawing an extra platform. Disappearing
  platforms and screw doors preserve placement flips, including the door end. Top-only
  buttons and disappearing platforms use their native landing edges and level
  clock; lightning attraction avoids an extra ordinary-ring sweep. Sideways
  spikes release owned push state after their hurt callback, elevator cars
  retain their original Tails interaction bank, and the Act 2 subboss stops
  movement on its laser-ready wait transition. Its beam, walls, and machine retain
  the ROM's high sprite priority, and the room-exit plane swap uses each plane's
  own horizontal scroll and terrain source, preserves rear-plane high priority,
  and rebuilds the full terrain cache after rewind. Robotnik and the control panel
  remain loaded during the room approach; moving-terrain clouds use the correct
  screen coordinates and mapping frames. The final capsule and button keep
  established standing contacts throughout the victory pose. Results retain boss-owned
  controls and camera bounds, and results creation waits for the whole physical
  KosM queue even on allocation retries. The exit door consumes collision in its own dispatch,
  and forced exit input preserves the already-recorded follower history.
  Fresh SOZ initialization follows the ROM's Nemesis title-loading gate and
  terrain submission boundary, closing the complete FBZ strict recording. Event-owned
  background row writes retain the physical 64×32 plane, so intermediate redraws
  and rewind use the same vertical wrap. HUD warning labels use the native level
  clock independently of the elapsed timer. Both Act 2 bosses retain their
  physical exit-art jobs through readiness and rewind; moving-background LEFT
  probes translate world coordinates before the sensor mirrors its tile metric.
  The Act 1 miniboss uses the native spring-plunger artwork, neutral waiting
  eyes, and foreground priority for its cover and face sprites.
  Upright spikes use their animated FBZ art, while wall spikes retain the shared
  sideways artwork; both tile banks participate in renderer refresh. Foreground
  and background shaders retain fragment centres while scaling, preventing a
  one-pixel sampling shift caused by GPU division rounding. Retained background
  redraws use native strip counts, clipped windows, aligned column sources and
  reset scroll origins when changing modes, preserving untouched rows. Player
  sprite publication retains only the current mapping's art, so S3K main-player
  Tails keeps his tail artwork when body animations leave unused bank slots.
  Donated Tails tails and powered Sonic teammates retain independent art banks,
  including the larger Super Sonic sprite capacity. Independently animated shields
  keep per-player art, including staggered insta-shields and donor playback.
  Bent pipes retain their ROM placement flips. Stage rings retain the native
  animation timer across level-counter resets and seamless reloads, including rewind.
  S3K's TIME label uses its dedicated ROM glyph, and life-count digits use the
  palette of their containing HUD piece, including custom-zone ownership.
  Rewinding a moving dynamic platform restores its execution slot before
  rebinding the player's riding contact.

- FBZ2’s spiked-arm boss uses the shipped rotation expression and correct pilot-light
  palette, with debug geometry for its body and children. S3K Super/Hyper reversion
  retains invincibility until the next native eight-frame boundary.

- Restore the FBZ1 mini-boss eye overlay so its pre-attack palette warning is visible.

- Fix FBZ1 indoor background strips after special-stage or checkpoint reloads by
  filling the background tile ring at the restored camera height.

- **Flying Battery and Death Egg floor launchers:** Exact surface contact keeps
  a falling player airborne until the native landing window is reached.

#### Sandopolis and Lava Reef

- **S3K player abilities and Sandopolis timing:** Knuckles now breaks the
  Knuckles-only walls (HCZ, MGZ, CNZ, LBZ, MHZ and SOZ variants) when he hits them
  in mid-air, keeping his speed as the ROM does, instead of stopping against them.
  Player-controlled Tails keeps normal air gravity on the frame flight starts, so the
  climb out of a jump matches the ROM from the first flying frame. Flight ceilings
  also use the ROM’s signed, word-sized comparison, allowing Tails to ascend in
  wrapping levels such as Sky Sanctuary instead of stopping in mid-air. Sandopolis
  Sandworms wait one more frame after first appearing on screen before starting
  their emerge timer, so they surface on the ROM frame. Sandopolis now queues its
  Skorp, Sandworm and Rockn art at level start like the ROM, keeping later art
  loading in step. Flying Tails and gliding Knuckles no longer borrow Sonic's
  Insta-Shield invincibility and wider touch box, so hazards hurt them as in the ROM.
  The Sandopolis Act 1 signpost now drops on the ROM frame after the golem sinks,
  and Sandopolis and Death Egg keep their ring count after Act 1 results until the
  Act 2 title card, as in the ROM. Knuckles caught by Sandopolis falling sand while
  gliding now drops his glide pose instead of keeping it. Grabbing objects also clear
  Knuckles’ glide state, preserving their hanging poses and release jumps. After the Sandopolis Act 1
  results the camera opens gradually and the walk to the pyramid starts on the ROM
  frame instead of briefly obeying held input. CPU Tails can jump off Sandopolis
  light switches again after the Act 1 golem, and the Act 2 title card holds for its
  full ROM time. Solo Sonic now also has cold routes through both Sandopolis acts,
  including the golem, eight-hit end boss, capsule and playable Lava Reef,
  with whole-world rewind checks. Tails also has cold routes through both acts, including eight end-boss hits,
  the capsule and playable Lava Reef, with whole-world rewind coverage. Knuckles now has cold routes through both acts: a bonus visit and the golem,
  followed by the cork/rock puzzles, eight-hit end boss, capsule and playable Lava Reef,
  with whole-world rewind and load-boundary checks, including a separate 800px Act 2 route. In Sandopolis Act 2, rising players are lifted onto push switches,
  objects below the looping level's seam (such as breakable sand rocks) load when
  the camera wraps, Hyudoro ghosts appear, attack and vanish on the ROM frames and
  scatter rings when they hit, and Skorps wake a frame later so they patrol in step.
  During the final boss, Sonic lands on its arms, is hurt on the frame he lands on
  its lower shell, is no longer carried by the moving arena wall, and its laser
  fires on the ROM frame; the capsule's results appear a frame later, as in the ROM.
  After the final boss, the escaping ship falls and the camera follows at the ROM
  speed, Tails strikes his victory pose only once the ship has left the screen, and
  Tails keeps his hurt pose when a boss arm hits him.
  Across S3K, horizontal springs now launch a player who comes to rest in front of
  them and require actual terrain penetration for a falling-player landing handoff.
  Blastoid hits scatter rings, and rewinding past an opened egg capsule keeps
  its explosions going. Players balance at the ROM distance from the edges of egg
  capsules and horizontal springs, and corkscrew ramps draw the tumble frames the ROM
  shows for left-facing players.

- **S3K Lava Reef:** Act 2 turbines carry the player around their rotating drums
  and release on a fresh jump press, with separate state for Sonic and Tails and
  restored ride/cooldown state when rewinding. Both turbine variants draw their
  ROM-loaded art. The Act 1 corkscrew clears the approach slope when taking
  control, preserving its horizontal release into the lower route. Shooting-trigger
  projectiles rebound from shields and stop dealing damage after deflection.
  Both Act 2 flame-thrower variants respect fire-shield immunity.
  Horizontal buttons retain their placement orientation in both acts, pressed or released.
  Chained platforms follow their ROM paths, carry players on top
  and hurt on contact with their spiked undersides. Rewinding the miniboss preserves its arms, hit flashes and defeat
  debris without resurrecting destroyed parts; the waiting hands also retain their
  creation coordinates before their first volley. Launchers retain their in-flight balls.
  The Act 1 miniboss submits its ROM art to the runtime module queue before its
  native arm-extension delay, including the explosion art needed by its children.
  Its post-results handoff now runs the ROM’s thirteen-step palette ramp and holds
  the normal lava/crystal palette clock until the camera reaches the native release.
  The title handoff also releases both vertical camera bounds gradually, so the
  Act 2 climb stays in view instead of retaining the miniboss height restriction.
  The defeated drill transfers its slot to the signpost controller; its independent
  debris and retiring arms keep the native draw order. Debris also preserves
  its initial draw and delayed retirement; the palette helpers use the ROM’s
  first-free object allocation order. Rewinding in Act 2 keeps its current object
  graphics instead of restoring obsolete miniboss pixels; the shared art journal
  also preserves later overlapping writes and replacement level data.
  The large proximity door remembers opening through its own placement state
  and reloads at its lowered render/collision position.
  Invisible boss-approach platform generators retain their native activation
  window on widescreen, keeping the platforms on their original schedule.
  The final lava arena is centred within wide displays, with its drawn slope
  following the same world coordinates as the solid floor.
  A controller-only Sonic and Tails route now completes both acts from a cold
  Act 1 start through the boss, capsule and playable Hidden Palace without a
  death, collecting the fire shield from its placed monitor. Act 2 has81 full-world
  rewind/replay spots across traversal and its boulder handoff.
  Act 2 also draws its ROM-backed Death Egg background sprite, with continuous
  widescreen entry and preserved art-load/position state on rewind. Finite
  widescreen foregrounds no longer repeat opposite-edge terrain outside the layout,
  while independently scrolling scenery such as SSZ2’s clouds retains its native wrap.
  Act 2’s boulder cutscene carries Sonic and Tails into the boss act, preserving
  rings, time and elemental shields. Knuckles’ exit leads into Hidden Palace and
  saves progression. The boss act restores its checkpoint entry and runs the
  staged autoscroll, camera clamps, arena release and foreground destruction.
  Its cold approach runs the Death Egg flash, palette fade, rocket launches and
  descending missiles that break the path. Bonus-stage returns initialize the saved
  checkpoint before selecting the arena camera.
  Its background uses the boss act’s ROM shimmer, animated tiles and palette cycles.
  Boss-act platforms emerge behind the lava horizon and descend in front of it,
  carrying the player through the checkpoint approach. The end boss launches mines
  across the tilting lava, takes their 14 scripted hits, then sinks and releases
  the capsule, results and Hidden Palace exit. Rewinding preserves the encounter
  through defeat and results, including the floating capsule’s explosion emitter
  and the equipped shield’s update order.
  Lava Reef Acts 1 and 2 now scroll their own layered background
  instead of the generic quarter-speed fallback. Act 2 continues the cavern rows
  beyond the initial tilemap window without a horizontal cut and animates its own lava tiles
  rather than Act 1's, and the invisible lava blocks that carry every lava-floor hit
  in the zone now hurt, with a fire shield making the player immune to them.
  Both acts also run Lava Reef's own scroll-driven lava animation, two background
  channels whose frames rotate with the parallax instead of a fixed cycle, and the
  zone's scattered background rocks - hardware sprites the ROM draws itself, with no
  object behind them - are drawn again, behind the players and in front of the
  terrain layer, with a wider viewport simply showing more of them.
  Act 1's dash elevators work: charge a spindash on one and it carries you along
  its shaft, in whichever direction you are facing, and stops at the end.
  Jumping off releases the rider without a second overlap correction on the launch frame; recharging uses the whole charge rather than its fractional decay.
  The zone's doors and switches work too. Walking into the side of one of the small
  horizontal buttons opens the sliding door that shares its number, and the door stays
  open; the huge door in Act 1 grinds down out of the way, shaking the screen, when
  you approach it from the right; and the shooting triggers fire their slow diagonal
  shots until you roll into one, which bounces you back, blows the trigger up and
  opens its door.
  The small horizontal buttons are solid all the way round, so you can drop onto the top of
  one and stand there rather than falling straight through it.
  The act 1 corkscrew works: run into it with enough speed and it takes hold of you,
  sweeps you around the turn and spits you back out the way you came. Being caught by one
  now straightens you out of a roll the way the ROM does, so you leave the turn standing.
  Act 1's sinking rocks use their correct terrain artwork and are solid again: stand on one
  and it sinks smoothly under your weight, rising back once you step off. Jumping off one
  while it is still sinking now leaves
  exactly where the ROM leaves you: the pixel the block sinks on that frame is no longer added
  to the jump.
  Act 2's orbiting spike balls turn: fifty-two of them, a small one and a large one, each
  sweeping back and forth past its anchor on its own axis and its own phase. Each is only
  dangerous, and only drawn in front of the scenery, for the half of its turn that brings it
  towards you; on the other half it passes behind and cannot hurt you.
  The wall rides work in both acts: run into one with any speed at all and it takes hold,
  sweeps you up over the curve and drops you back on the floor heading the other way.
  Rocks, landed spikes, smashing platforms and intact collapsing bridges now unload outside
  the camera range, freeing object slots so later buttons and doors execute in their proper order.
  The act 1 falling spikes drop: walk directly underneath one and it lets go, hurting anything
  it lands on, and once it hits the floor it stops hurting and stays as a solid block.
  The act 1 fireball launchers spit their fireballs again, each on its own period and with
  launcher and shot artwork matching both placement flips, and the
  shooting triggers' shots now travel at the speed the ROM gives them instead of creeping.
  The act 1 lava falls pour: each one runs for part of every four-second cycle, dropping a
  blob every sixth frame using the terrain lava artwork, and every second blob carries the falling-lava sound. Fire shields
  protect against both the falling lava and launcher fireballs.
  The swinging spike balls and their chains sweep concentric circles in both acts, with act 2 using
  its own artwork.
  Act 1's smashing spike platforms work: each one accelerates down its own shaft, slams with a
  crash and a squash, holds for half a second and grinds back up a pixel at a time, hurting
  anyone it lands on and shrinking its solid box as the art compresses.
  The act 1 spike balls are in too: the big ones grind back and forth along the floor throwing
  rock chips, and the one on the swinging arm is only dangerous on half its sweep until a player
  approaches it from the left, when it breaks off the arm and rolls away downhill.
  The zone's mist-breathing badniks are in as well: each one turns to face you and breathes a
  cloud that drifts, settles on the ground and, if it catches you, clings on - dragging your speed
  down an eighth a frame and taking a ring a second until you shake it off by rocking left and
  right, or blow it apart with a spindash. Cloud contact uses its own attachment response
  without ordinary hurt or boss rebound; rolling and bubble shields prevent attachment.
  It faces the nearest player and breathes its cloud toward them. Its stalk
  reaches the ground, and its cloud grows, churns and shrinks away
  using the ROM animation instead of remaining tiny and drifting offscreen.
  The badnik itself can now be destroyed: rolling or
  jumping into its body bursts it, scatters its cloud and rebounds you, where before a rolling
  player fell straight through it.
  The zone's fire worms are in, 29 of them across the two acts: swim within a few steps of one and
  a head rises out of the lava, sets off toward you a pixel a frame and grows a four-segment tail
  behind it, each segment falling in eleven frames after the one in front and carrying its own
  flame. The chain swims up and down for eight strokes, then the head turns and the whole worm
  works its way back the other way. Only the head can be hit; the body and the flames only hurt.
  Lava Reef's bomb badniks are in, 66 of them across the two acts: each one is a solid block you
  can stand on until a player comes within a few steps, when its fuse lights, flickers faster and
  faster, and it blows itself apart into four fragments that arc away and fall. Shields deflect
  the fragments and leave them harmless as they fall away.
  Three places in act 1 now lock the background to the dome the way the ROM does: cross the line
  inside one of the three regions and the background stops following the camera, a lava surface
  rises and falls under you across the width of the room, and crossing back the other way releases
  it again. Standing on that lava burns, and a fire shield saves the lead character only -- the
  ROM checks the shield for Player 1 and not for Player 2, and the engine keeps that.
  Act 1's two rock crushers work end to end: reach one and the camera locks to its own limits, the
  crusher loads its own artwork and rumbles overhead with the screen shaking for three seconds. Hits flash its body and upper
  pieces, suppressing repeat rebounds during their recovery. Released slabs begin their shake
  countdown on the release frame, matching the original sequence. Then the rock underneath is
  cut away, collapsing slabs drop into the gap, the crusher falls through and explodes, and the
  camera eases back out to the act's own bounds. Widescreen crusher locks now use
  the centered native camera window, so the collapse can start before Sonic reaches
  the wall. Rewinding during the falling explosions preserves their remaining
  sequence and shared random-number state. A cold 800px Sonic+Tails route now
  covers both main acts, the final mine fight, capsule and playable Hidden Palace,
  with whole-world rewind and load-history checks. The
  final capsule now patrols the native arena when widescreen centres the fight,
  instead of shifting into the masked margin.
  The Lava Reef and Death Egg boss acts no longer borrow Hidden Palace's background
  scroll; Hidden Palace and the Super Emerald sanctuary keep theirs.
  Rings no longer appear at the top-left corner of every S3K act: each ROM ring list
  opens with a `(0,0)` record that the ring manager always steps over, and the engine
  was spawning it as a real ring. The boss fight's sloping lava pool now
  draws above the lava wall with the ROM's background priority and column scroll.

- **S3K Lava Reef:** the Act 1 miniboss arena and Death Egg Act 2 boss arena
  center their native camera windows in widescreen while retaining original player
  bounds. Death Egg's widescreen boss view holds X at the arena centre while Y
  continues tracking Sonic, releasing X when the exit corridor opens; 320px keeps
  the original horizontal tracking. The Act 1 miniboss arrives as an object -- the hovering drill with its
  climb, swing, drop, slam and fall-back cycle, its player tracking, and both of its articulated
  arms with their firing hands and shots. Shields deflect those shots away and
  permanently clear their damage, including across rewind. Destroyed arms peel away
  with the ROM’s staggered three-burst explosions for every part, using the registered
  LRZ1 explosion renderer and its dynamically loaded art. Retired crusher pieces become
  harmless falling debris and leave the object pool before the seamless Act 2 handoff.
  Every Act 1 placement in the zone now builds a real
  class. The two arms unroll link by link from the bottom of the screen rather than snapping out
  whole, each hand rides the end of its own arm so its shots leave the arm and not the drill, and
  the drill's slam is solid to stand on, carries the only hit box in the cycle, and takes damage:
  six hits, each followed by the palette flicker the shipped ROM actually produces, which is not
  the white flash its own data was written for -- and that flicker now lands on the palette line
  the ROM names rather than the one after it, so it tints the boss instead of whatever else shares
  the screen.
  The hands take damage too, four hits each, with their own hit ring, their own invulnerability
  window and their own blink; killing one peels its whole arm away link by link from the hand end
  first, each link bursting as it goes, instead of quietly shortening the drill's hover. Killing
  the drill itself now ends the fight: its full sequence of explosions plays as it fades out
  and breaks into eleven pieces on their own arcs,
  and hands over to the end-of-act sign and the results screen the way every other Sonic 3 &
  Knuckles miniboss does. The sign’s post follows the face’s foreground priority, keeping
  the complete sign visible. The end-of-act sign the defeat hands over to moves with the act
  change as well; before, a real defeat stopped the change dead rather than carrying into
  Act 2.
  Beating the drill now changes the act. Once the tally is over, Lava Reef loads Act 2's art
  behind the results screen and then, on a single frame, swaps the act underneath the player:
  the level, its solids and its object list are Act 2's, and the player, the camera and the
  camera's limits all shift with them, so play carries straight on into the second act instead of
  leaving you standing in the first. The switches and animated-tile counters the first act left
  behind are cleared with it.
  The fight now also starts the way the ROM starts it: the drill does nothing at all until the
  player has carried the camera into the arena, and then the music fades, the miniboss theme
  comes in two seconds later, and the camera locks onto the single arena screen the ROM pins it
  to for the whole fight. Before this the drill built its arms and began its cycle the moment it
  loaded, with the camera still free to walk out of the arena. The two camera releases that follow
  the act change are still to come.
  Act 2 is now playable straight out of the change: the arena's right-hand camera limit, which the
  act change carried across with everything else, is replaced by Act 2's own once the results are
  over, so the player walks on instead of standing against an invisible wall where the arena used
  to end.
  The fight now recolours the zone the way the ROM does: the drill's arrival and its first
  attack each load their own palette, those colours carry through the act change -- the ROM's act
  change loads no palette at all -- and Act 2 only takes its own blue crystal colours once the
  player has walked far enough into it, where the ROM swaps them. Before this, Act 2 turned blue
  the instant the act changed.
  Act 2's solid moving platforms are in too, another 52: blocks you ride that drift along one axis
  and back, some reading position from the zone's shared oscillation and some easing out of each end
  under their own acceleration, and either kind can be mirrored to start from the other side.
  Act 2's flame throwers are in, 52 of them: each one is a solid block that fires a jet of flame
  for two seconds, pauses for a length its own placement chooses, and fires again, sweeping the jet
  through a narrow fan as it goes. They come in the wall-mounted and floor-mounted kinds, either
  can be mirrored, and the flames themselves hurt, drift without slowing, and burn out.

- **S3K Fireworm:** killing a Lava Reef fireworm now retires its whole body. Its segments scatter, fall and flicker away and its flames go out, instead of leaving
  frozen body pieces or invisible fire that burns the player seconds later.
  Retired segments stay harmless and continue their fall after rewinding,
  even when their deleted head no longer exists in the restored world.

- **S3K Lava Reef domes:** the background now locks onto the dome as the player crosses each
  of the three dome thresholds, rises and falls with the lava surface inside it, and stays on
  the dome view for the eight frames the plane takes to redraw on the way out. Knuckles gets
  his own background chunk in act 1.

- LRZ corkscrews retain the shipped two-player DPLC standing-bit quirk and hold the rider pose on skipped passes.

- LRZ grinding boulders retain the native coarse-camera lifetime, preserving their contact with both players.

- The Lava Reef boss act (`$1600`) starts with the native falling intro on a
  fresh entry, for every character.

- Iwamodoki self-destruction releases standing players as in the ROM, preventing shield-protected Sonic from retaining an air-running ride.

#### Hidden Palace and Sky Sanctuary

- **S3K Hidden Palace:** the Hidden Palace data-select slot and level load now enter
  the playable Hidden Palace act (`$1601`) with its own layout, bounds and title card,
  instead of the Super Emerald sanctuary (`$1701`), which remains reachable from the
  giant ring. Hidden Palace now applies its character camera limits and Knuckles
  background layout, switches from its intro palette past the entrance, cycles its
  crystal glow, and animates its waterfall and gem tiles; the sanctuary gains the same
  glow and tile animation. Its teleporters now carry the player between floors with the charging beam, rise and
  settle, and Knuckles leaves for Sky Sanctuary Act 2 from the upper floor. Sonic and
  Tails now fight Knuckles in Hidden Palace, watch Robotnik's crane steal the Master
  Emerald and zap Knuckles, drop through the collapsing altar floor and leave for Sky
  Sanctuary on the altar teleporter while Knuckles is beamed away.
  Entering Hidden Palace (and Death Egg Act 1, or Carnival Night and Lava Reef Act 1 as
  Knuckles) now runs the player in from the left with the camera held ahead, and the
  Hidden Palace teleporter no longer catches a player running past its edge or drops
  the camera and rider out of step during the lift.
  Title cards for the shared sub-level slots follow the ROM: Hidden Palace shows its
  own name without an act number, and the Lava Reef and Death Egg boss acts show their
  zone's card. A cleared Super Emerald stage now tallies over the rebuilt sanctuary as it
  fades in from white, pans down to the pedestals, closes a ring of stars on the new
  emerald and, once all seven are held, announces "NOW SONIC CAN BE HYPER SONIC"; the
  small Chaos Emerald indicators only show emeralds that have not been converted. Results
  wait for their ROM art queues before the tally begins, and their post-tally and
  continue timers preserve the native same-pass countdown. Returns retain the resolved
  terrain art through saved-state restoration and repeat the native title-art load.
  Continuous replay verifies completed return-art jobs against their full recorded
  identities without renumbering submitted work. The 5,000-point perfect bonus
  requires collecting every ring, independently of earning the emerald, so
  non-perfect clears return without an extra tally. Leaving any S3K special-stage
  results screen now fades to black without the stage
  transition sound, as in the ROM, instead of fading to white.

- **S3K Sky Sanctuary:** Both Act 1 replica bosses now draw their ROM-backed
  Eggmobile bodies alongside Mecha Sonic’s head. The MTZ laser pair preserves
  its ROM tile priority independently of each piece’s sprite display bucket.
  MTZ orbs distinguish harmful contact from armed touch and respond to Hyper
  attacks through the ROM’s separate collision-property path.
  Both replica fights keep the widescreen camera
  centred on their native arenas through knockback. Their preliminary camera
  bounds use the same projection, so ordinary widescreen approaches can reach
  the boss-entry gates. The final Mecha Sonic arena also holds its native
  horizontal origin on widescreen, keeping its attack box aligned with the terrain
  even when a wider camera deadzone would otherwise retain an offset.
  Cold paired Sonic and Tails routes complete all three fights at native and
  800px widths, then continue into Death Egg with ordinary control. Rewind retains
  the transports and resets history at the real level handoff.
  Spawn-derived constants and
  restored carrier links declare their rewind policies without coverage exceptions.
  Player snapshots preserve tile priority and the independent sprite display
  bucket, including through the final transport ascent and follower history.
  Sky Sanctuary act 1 keeps its sky and cloud windows aligned through background
  transitions and rewind, and now plays its teleporter arrival instead of
  dropping the player in at the level-start position. The screen init forces the arrival
  camera and bounds, title-card entry preserves its camera lock, and the beam draws only
  its light columns without a floating pad. The controller beams Player 1 up the sanctuary column and hands control
  back on the settle swing, Tails arrives on her own beam, and the act's dynamic camera bands
  and vertical wrap follow the player. The cutscene Knuckles who opens the route — his beam,
  the Death Egg he watches rise, the grey button and the three-section bridge that extends over the gap,
  with the checkpoint it leaves behind — is in place. Death at that checkpoint
  restores the extended bridge and skips the arrival scene, including repeated reloads. The rising Death Egg applies and restores
  its ROM cutscene palette, cloud, skyline sprite mask and animated trails, with
  the Egg, cloud and mask following the camera at the ROM's half-speed parallax, rising
  behind the terrain, and launches its
  decorative missiles on the V-int cadence. The act's sky is its own now: the
  background switches between the plain sanctuary framing and the banded cloud layer as the
  camera crosses the cloud band, the clouds drift, the whole sky breathes on the act's
  oscillator, five clouds roam across the screen and ten invisible cloud platforms can be
  stood on, and the banded cloud layer is drawn from the part of the act's background the
  cartridge draws it from, so the climb happens against real clouds instead of flat blue.
  Knuckles' act-2 encounter now has its own camera oscillator and ROM-backed foreground
  bands and background waves, including rewindable scroll state and the island
  priority overlay. Its crane grabs Knuckles, pans into the arena and releases him
  into Mecha Sonic's first phase. His first defeat now leads through the Master Emerald
  transformation and forced run into Super Mecha, with both fights reaching the pre-ending
  stop and recording Knuckles’ clear state. Widescreen pans keep the native arena
  timing and player limits while centring the visible window; the Master Emerald
  appears before the dash reaches it, and Mecha stays in front of the floating island.
  Sky Sanctuary's animated tiles run in act 1 and, as on the cartridge, not in act 2. The act's teleporter pads are real now: they draw, they can be stood on, they lift
  the player and the camera the distance their placement asks for, and the two that wait on
  a defeated boss sit sunk in the floor until it is beaten and then rise back into place.
  The small floating platforms dip under a standing player, the tall columns break into eight
  falling pieces when one is stood on. Rewind preserves those falling pieces after
  their column leaves the active world. The flat bridge sections crumble away from under the
  player four pieces at a time — except the one section the cartridge marks permanent — and the
  sloped walkways follow their ROM collision surfaces and split into eight matching
  fragments that fall in the cartridge's staggered order, with the shrinking solid sliding
  along their own slope as they go. The permanent staircase now carries players
  uphill instead of letting them fall beneath its tiles. The rest of
  the act's traversal is in too: the little clouds squash under a standing player and throw
  them up and back in a puff of four, the horizontal bars catch a player from above and swing
  them in the correct facing pose before flinging them where they are steering, the short posts spin a player in place
  and their invisible carriers walk them round in a circle, with the ROM pose
  flips keeping the player sprite aligned throughout the turn, the swinging and rotating arms
  carry a player at the tip of a jointed arc, and the retracting springs fold away until
  somebody comes at them and then fire them along the deck from side contact,
  using the cartridge's full sloped collision rather than a top-only platform. Swinging carrier arms and rider bars
  now remain alive until their hub releases them, preserving the platform at camera edges
  and avoiding dangling rewind references. Sky Sanctuary's EggRobos patrol
  the act now, in all three shapes the cartridge gives them: the distant one that crosses the
  sky and, by crossing, lets its partner appear; the hovering one that tracks the player,
  keeps its gun and jet flame attached with the native facing and hover lag,
  restores their live positions during rewind,
  levels its arm and fires a laser once it has them lined up; and the one that lets four
  animals go before it takes off and joins the others — and which, until it takes off, is the
  harmless invisible marker the cartridge makes it rather than something that hurts a player
  falling past, as the distant sky-crossing one is too. The first of the act's rebuilt boss
  fights is in: walk into the lower arena and the camera closes behind you, the Green Hill ship
  drops out of the sky with Mecha Sonic's head on it, runs the arena and pays out a six-piece
  ball and chain whose ball hurts to touch, sweeps the floor and turns the ship around at each
  end. The ship flashes three of its own colours while it is reeling from a hit, exactly as the
  cartridge does — including the off-by-one that makes the flash duller than it was meant to be.
  Eight hits send it away and open the way on. Both recreated ships shed explosions
  during defeat and escape, with the cartridge's three-frame cadence and allocation order.
  Rewind preserves the EggRobo pairing signals and boss-active state. Mecha Sonic
  flashes and sparks after defeat using the original palette sequence, and his
  animated secondary hitbox remains dangerous during his hit-flash window. The second one follows it: climb to the upper
  arena and the camera closes again, the Metropolis ship falls in with a ring of seven orbs
  turning around it — passing in front of the ship and behind it as it turns — patrols the
  arena and lifts its arms to swing the ring wide. Hitting it throws one orb off the ring at
  you, and the ship will not come back down until every thrown orb has been dealt with; it has
  seven of those in it. After the seventh it stops raising its arms, dives at the floor and
  fires three pairs of lasers along it instead. The eighth hit sends it away and raises the
  sunk pad in the arena floor. Mecha Sonic himself now turns up for the third: reach the far
  end of the act and the pad there breaks apart as he comes tearing in from the right, runs
  clean off the left of the screen, turns and comes back along the top with an after-image
  trailing him, with its own palette and foreground priority. From there he works through his own repertoire — dropping on the floor, dashing
  along it, turning to face the player, jumping, and following the jump with an air dash, a
  ground pound or a landing that sets up two more dashes. Which parts of him can be hit and
  which hurt to touch change frame by frame with his animation, as they do on the cartridge.
  The eighth hit now ends him too: he is thrown back, holds still for a little over two seconds,
  drops to the floor in his beaten pose, and the act hands over to its results — which waits for
  the player to be standing rather than ending on a timer alone. All three bosses award 1,000
  points, and the results handover survives rewind after expired dash trails are removed.
  Results now release the collapsing arena, forced jump and spiral ascent past the rising
  Death Egg, including the crumbling column and falling ramps, before loading Death Egg
  Act 1. The spiral clears inherited facing flips, and arena bounds suspend vertical
  wrapping so the column crumbles at the ROM’s intended point. The sequence and its retained background survive rewind; Mecha's arena stays over
  its floor at wider viewports.

  Act 2 now allocates its encounter camera oscillator after Knuckles’ arrival and preserves
  its fractional cloud offset through rewind. Its post-defeat floor patch and
  emerald-dependent presentation camera movement also survive rewind; the remaining
  act-2 sequence and complete-route validation are still in progress.

- **Sky Sanctuary cutscene bridge:** Walking off either end clears that
  player's standing bit on the same frame, as the shipped game does. The bit
  previously lingered for one more frame after the player had left.

- **Sky Sanctuary rotating platforms:** Jumping off a spinning post or its
  carrier bar clears the player's "standing on an object" status on the next
  frame, as the shipped game does. The status previously stayed set until
  another object cleared it, so the player could run flat through the next
  slopes instead of following them.

- Sky Sanctuary Act 2 rewind reuses unchanged terrain; the ending resource adapter
  restores art while the existing layout owner handles scene geometry. A rendered
  cache audit covers 68 stock act/scene entries across all three games.

- Sky Sanctuary diagonal bridges retain a rolling player's pose while transferring
  between sections, and Tails resumes roll animation during the arrival swing.

#### Death Egg and The Doomsday Zone

- **S3K Death Egg:** the Death Egg acts now hold their background still instead of scrolling it
  at a quarter of the camera speed, cycle their console and panel colours, and animate their
  machinery tiles. Its object-table registrations preserve the glowing-sphere bonus
  stage bumper at the shared slot. Widescreen keeps one central background in each act: Act 1 extends its
  side walls with ROM tiles, while Act 2 continues the planet's curve using its existing surface
  pixels. The native centre stays unchanged. Tails’ separate tail sprite mirrors under reversed gravity, preserving its directional rolling animation; spindash dust mirrors with it and skid dust appears on the ceiling side. Knuckles keeps his feet on the ceiling when getting up from a glide slide. Death Egg Act 2's gravity now reverses: crossing one of the act's invisible
  gravity triggers flips which way is down, and the player falls to the ceiling, stands and runs
  on it, rolls, jumps, lands, gets hurt and dies against a death plane that has moved to the top
  of the level. Shields, spilled rings, dust, springs, solid objects and a carried or respawning
  sidekick all follow the flip. Player sprites consume the animator’s final orientation
  directly, avoiding a second gravity mirror that cancelled the visible flip. Which direction of crossing turns gravity on is the level's own
  choice, so running back the way you came restores it. Act 2's pressure pads flip it too,
  from either face, so a pad works whichever way up you reach it; the pads are now drawn,
  sinking as they are pressed, and sound the transporter note.
  Act 2's teleporter columns now work too: step into one and it takes hold, spins up, carries
  you to its partner and flips gravity on the way, and a second player can ride without the
  gravity changing under the first.
  Gravity pads preserve pending contact through rewind, and attracted-ring slot
  reservations survive rewind across act transitions,
  and the released Robotnik runner and door stop retaining the retired Act 2 boss.
  The act's gravity tunnels now take hold too: run into one and it carries you along, lifting
  and swinging you as it goes, and leaving one while gravity is reversed turns you the right
  way up, and the ride is continuous rather than dropping and catching you every other frame.
  The junctions the tunnels feed into now catch you as well: they pull you to the middle, hold
  you spinning there, and fire you out along whichever way you press of the ones that junction
  opens onto. Act 1's turbine corridor blows you along it now as well, tumbling, with up and
  down to steer by. Its controller stays alive as the camera follows you, and the bobbing
  shaft and walls bounce airborne riders; hitting all six panels opens the exit,
  and rewind retains the panel contact owner, including the original two-player behavior.
  A cold 800px Sonic route now covers both main acts, their boss phases and
  the ordinary ending through the final fingers, core and escape ship, with
  whole-world replay checks. Falling tilting-bridge
  sections release their retired controller reference so rewind remains available,
  and the following door admits the player while the turbine still owns movement. The Spikebonker mace robots patrol both acts, hover, and swing at
  you when you come at them from the side they are walking toward. Act 2's retracting springs
  work: they push out of the wall while you are below them, pull back in once you are well
  above, latch as they start and finish each stroke, and launch from the correct contact face
  in either gravity direction, including when arriving in a roll.
  The energy bridges across both acts switch on and off on their own schedules, carry you while
  they are lit, and drop you when they go out. The curved bridge enables its terrain
  path while lit; both forms retain the final displayed phase when they switch off.
  The new mechanisms stay loaded across widescreen approaches.
  The Chainspike robots charge across both acts, slowing to a stop and turning around, and
  stab their spikes into the floor when you get close; their child spikes retain the correct
  parent across rewind. Lightning flashes from the floor
  emitters with its short damage window and sound. Conveyor belts carry grounded players
  along either face in their placed direction. Wall launchers fire torpedoes while in view
  and recoil between shots. Four-section staircases react to landing or underside contact,
  wait or shake, and lift or lower their steps. Hover machines suspend players
  above their orbiting rotors. Hanging carriers grab players, rise to the ceiling,
  travel along it and let players jump away. Act 2 floating platforms oscillate or
  accelerate along their placed axis, carrying riders while their lights alternate. Tilting
  bridges respond to where players stand, then break into falling sections and release
  riders onto the floor. Conveyor pads start when ridden, follow the floor or travel a fixed
  vertical distance, and reverse their belts for newly arriving riders and inverted gravity.
  Lift pads swing Sonic upward, wait while he stands on them, then return after he leaves.
  Vertical gravity tubes keep player movement active so riders travel through their span.
  Light-tunnel launchers count down, carry captured players through the winding paths
  with glowing ring trails, and release them at the exit; rewind remains valid when
  the trail finishes before its riders. The Act 1 miniboss now fights
  through its orbiting-sphere and moving-platform phases, including its eye, beam,
  spikes and breakup; detached sphere fragments and their stationary explosions remain rewindable after their creator disappears. Defeating it runs the results and carries the player into Act 2,
  where the floor opens, the launch plays and normal control returns. Act 2's gravity boss
  now releases spiked enemies, takes damage from their return impacts, breaks apart,
  opens the door and releases the camera into the final-stage transition. Its transporter
  columns stop accepting new riders after defeat. The destination final boss now creates its entry floor, restores carried rings/time and progresses through the forced run-in to the hand phase, with retained boss/floor rendering and a single centred widescreen planet. Its invisible floor support waits for real landing contact, keeping Sonic on the visible floor with continuous animation, and repositions without dragging the player sideways, and its defeat explosions follow the moving boss and escape ship, including the finite regular-explosion burst. The widescreen arena framing is released before loading Doomsday. Complete encounter validation remains in progress.
  Shock floors and ceilings now hurt on
  their intended face; the lightning shield protects you while other shields do not.

- **Death Egg gravity puzzle:** New top landings use the native shaft width,
  allowing edge contacts to establish support before the turbine moves the player.
  Bobbing retains each occupant's rider state until its own solid call consumes an
  airborne seat, preventing a second landing in that same call. Grounded
  walk-off clears the departing player's latch before a later re-entry.
  The puzzle also retains its native display-child slot, preserving later bumper
  execution after the turbine moves the player.

- **Death Egg staircases:** Moving sections preserve their own standing bits.
  A jumping rider releases at the ridden section's collision checkpoint,
  preserving the native jump height.

- **Death Egg moving pads:** Exact surface contact leaves an airborne player in
  flight until the native overlap window is reached, preserving the original
  cold route's landing timing. Walking or jumping off clears that player's
  standing latch, allowing the lift's return pause to resume after P1 leaves.
  Conveyor pads retain each rider's standing bit as they move, preserving the
  native jump height and clearing support at the pad's own contact checkpoint.
  Walking off a conveyor pad also retires that player's carry bit, preventing
  another conveyor push after support has ended.

- **S3K The Doomsday Zone:** the Master Emerald palette cycle and boss flash colors now
  read their script, destinations and color words from the ROM; rewind preserves the shared
  emerald script cursors and child creation coordinates through boss-graph recreation. The emerald starts following Sonic on the boss exit
  signal immediately and survives rewind after the ship disappears. Doomsday is now playable. Sonic falls in, transforms (Hyper with
  all Super Emeralds, with the ROM-backed trailing stars on the Super branch) and flies through the autoscrolling asteroid field under Robotnik's
  ship, whose body is drawn on the foreground plane over the six-band space background.
  Background scroll words now retain integer-pixel wrapping, preventing thin black seams
  where widescreen rendering repeats the native background plane.
  Recorded/headless transitions now honor the same source-level freeze as the live
  loop during zone-change fades, including Doomsday's exit.
  Knuckles's inverted glide and slide use ceiling contacts and mirrored snaps;
  wall climbing follows the native reversed probes, movement and camera bound.
  Fall-from-glide landings preserve surface contact when restoring standing radii.
  Glide landing poses now yield to ducking and spindashing during their movement
  lock, allowing Knuckles to shake off Toxomister clouds with a spindash.
  Ledge-balance checks perform their own floor probes, so rewinding from a jump
  no longer changes facing or look-up timing through stale collision flags.
  Upside-down monitors retain their native knock-loose and ceiling-settling behavior,
  including the reversed-gravity direction check. Their shells and reward icons
  keep their placement flips, with the native inverted reward motion and timing.
  Spikes select their damaging contact face from their orientation and gravity
  at initialization, retaining that native choice through later gravity changes.
  Direct sloped-platform helpers retain the ROM’s upright landing calculation
  under reversed gravity; flat platforms keep their separate inverted branch.
  Doomsday's entry ring award preserves the displayed count until the native
  HUD redraw request, independently of the live ring count.
  Hyper's stars wait for their ROM art queue before starting their native animation,
  retain their sprite-list priority independently of Sonic, and
  his afterimages mirror vertically when gravity reverses. Asteroids
  shatter into smaller rocks and debris, homing missiles ride and chase, and dashing
  or ramming costs speed. The end boss runs both phases: turrets, launchers, missiles that can
  be steered back into the ship, the chase with bombs, rockets and the Master Emerald, the
  level wrap that repeats the field, and the defeat, explosions and white fade before the
  handover to the playable rescue and credits in the ending act ($D01). S3K transformations now release the
  player on the ROM palette-fade schedule.
  S3K level palette changes now appear on the frame the matching sprites do, one frame after
  the game writes them, as the hardware shows them, so boss hit flashes are no longer a frame early. S3K palette cycles (such as
  Carnival Night's lights) now start after the level fade-in finishes, as in the ROM, instead of
  from the first level frame.

- Doomsday queues its misc and boss Master Emerald art through the native
  module queue, and the S3K ending load queues its terrain art and waits for the
  queue to drain before the ending scene starts, as the shipped game does. The
  ending previously started at once with its terrain art unqueued.

- The Doomsday Super transformation shows its sparkle animation and releases
  Sonic on the shipped frames: the animation starts when the flight controller
  triggers it, and Sonic moves again on the frame his palette fade finishes.

#### Character routes, endings and campaign coverage

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

- **S3K endings:** The final Death Egg scene preserves native terrain definitions
  across its act handoff, uses its own collision bank and clears its unused layout
  bytes once at scene entry, retaining later terrain edits through rewind.
  Sonic and Tails continue from Death Egg or Doomsday into
  the Tornado rescue, earned Super Emerald restoration, reunion, ROM credits
  and their progress-dependent final scenes. Knuckles continues from the final
  Sky Sanctuary fight through his plane flight, island credits and terminal
  scene. Scene resources, palette and animation owners, terminal Start input
  and rewind state remain inside the native level lifecycle. The final eyecatch
  keeps Sonic, Tails, Knuckles and the tail in their native overlap order through
  both sprite-table collection and direct drawing. Island-lift sprites
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
  line. Sonic's and Tails' endings now start the credits music where the original
  requests it, instead of staying silent after the rescue fade. After the credits, the
  closing logo for every Super Emerald ending fades in through white with its characters,
  as in the original. Sonic returns to his normal colours rather than staying Hyper white,
  the sea's colours stop cycling once the picture has faded to black, the fade to black
  starts on the original's frame, and Tails' tail is back behind him. The ending then hands
  over to the title screen on the original's frame. The closing picture stays up without its
  logo and fades out under the title instead of losing its characters first.

- **S3K cold campaign coverage:** Sonic alone now has one uninterrupted
  replay from Lava Reef act 1 through the Lava Reef boss, Hidden Palace and Sky
  Sanctuary act 1 into Death Egg, beside the Sonic and Tails, Tails-alone and
  Knuckles campaigns. Lava Reef's end boss needed no change: its mines run on
  the original's fixed clock whoever plays, and the route takes the placed
  fire shield that keeps the lava from hurting Sonic during the fight.
  Wider views restore the entry objects when the camera returns from beyond the level's left edge.
  Lava Reef crusher timers now decrement during their first dispatch, preserving the
  original world-change and generated-bridge creation edge.

#### Special stages, bonus stages and the Super Emerald shrine

- **S3K bonus stages:** keep the slots player behind the central capsule glass
  during gameplay; the shared bonus loop no longer overrides the slot player's
  priority. Bonus title-card release completes the initial object setup before
  the first gameplay tick, preserving the first controller input and its movement.
  Saved-origin bonus returns carry collected origin rings in the kept respawn-table
  snapshot; ordinary level reloads continue to clear ring collection.

- S3K bonus-stage entries and returns hold the destination players behind
  the title loop like any fresh level load: the slot is cleared first, the
  start position appears when the title loop exits, and the players are
  created just before the stage begins. The title-card control lock survives
  player placement and releases as gameplay resumes. Returns restore the saved
  apparent act before water selection, preserving seamless-act water on return.
  The saved water mean no longer overwrites the new level’s target. Slots keeps
  its final bonus camera position until the destination load replaces it.

- The Super Emerald shrine keeps both players' existing standing state while a
  selected pedestal counts down to special-stage entry, as the shipped game
  does, instead of running another collision check and detaching the player.

- S3K Special Stage automatic starts test the banner exit offset before advancing and publish the start flag after player movement.

- **S3K bonus stages:** Gumball bumpers disappear when hit, springs are fully
  solid and crumble in the same pass, gumballs drop from the container with the
  machine's native random seed, ejected balls also push the second player, and a
  companion that drops into the exit is removed as in the original (it rejoins
  on return). Pachinko orb rewards follow the level frame counter, and round
  bumpers show their ten-point score with the native sprite priority.
  Each Slot-machine spike impact removes one carried ring while any remain.
  Lightning-attracted rings award at the native object update and retain pending
  pickups through rewind. The three standalone bonus traces replay without errors. The Slot cage keeps re-centring the machine
  through the goal wind-down, so the machine stays where the original shows it.
  Its background keeps moving at zero cage rotation and publishes queued rows
  at the native VBlank boundary, including through rewind. Restoring its layout
  preserves the published background rows while rebuilding derived geometry.
  Foreground tiles retain their original priority so rotated walls and peppermint
  pieces show correctly, and overlapping cells keep the original sprite priority;
  the Gumball background stays pinned as in the original instead of scrolling
  with the camera, and Pachinko's background survives rewind. Saved-origin bonus
  returns carry their interior-return load cause once, keeping later ordinary
  loads independent.

- **S3K special stage presentation:** Blue Sphere players and their tails use
  the original perspective projection through jumps, with fixed ground shadows
  under the players. Distant spheres retain the original shared mapping frames
  and tile sizes. Overlapping spheres use the original sprite-table order and
  compare the winning pixel's priority against the floor and HUD planes.
  The results card sits on the locked-on game's grey backdrop
  instead of white, matching native frames of won and failed stages. The final
  Chaos Emerald reveal uses the original single countdown before returning to
  the level, including its zero-timer expiry pass.
  A Super Emerald pedestal loads the S&K sphere layout for its selected stage and
  selects that stage on the frame the player lands on it,
  as the original does, instead of one frame later. White stage transitions follow
  the original palette colours and 22-frame wait, including the scene backdrop
  and underwater colours, instead of adding a yellow/red overlay. Rewinding a
  special-stage entry releases its restored palette on the next scene update. The host's
  white-fade policy is bound to its gameplay session, keeping detached UI fades
  and other worlds independent.
  Score and bonus
  digits use the original mapping positions and trailing zero, and overlapping
  labels and sphere icons follow the console's sprite priority.

- Clear the native sanctuary openings so stray terrain no longer appears above
  the left Super Emerald pedestals during the results camera descent.

#### Characters, shields, objects and shared behavior

- **S3K parity fixes from the discrepancy audit:** Madmole's body is its own object
  in its own slot; the MHZ dragonfly tail enters its return on the same frame as
  the ROM; the MHZ pulley-lift button sequence writes the debug cheat flag; the HPZ
  sanctuary shakes its background when the crystal lands; the ICZ miniboss ice shell
  is drawn from creation; Robotnik's ship is drawn in front of his head; LBZ2 boss
  smoke puffs keep the ROM's spawn and delete timing; Slot Machine randomness reads a
  real power-on V-int count; and Gumball exits through its native trigger before
  fading for the ROM's 22 V-ints, without an earlier exit from the disabled death plane.
  Headless bonus-return replay prepares and advances each physical title/fade row,
  closing source gameplay ownership after its final published row while retaining
  recorded return-tail comparisons and timing checks. HCZ fan bubbles rise at
  the native speed and remain allocated until reaching the water surface. Fan
  bubble and sound cadence use the gameplay clock; a full object pool leaves
  the random-number state unchanged.

- **Shared S3K object behavior:** HCZ reuses common capsule opening, effects,
  geometry, drawing and rewind-safe opening control ownership while retaining
  its native button/results handoff. FBZ
  shares common opening writes and native sprite/plane priority without losing
  its child-slot graph; its button and final-capsule fragments retain high art priority. Cutscene
  buttons share proximity and presentation, including native sprite priority.
  Explosion placement now uses the ROM’s high random word for Y, and failed
  ICZ explosion allocation consumes no RNG or sound. Teleporter swing arithmetic
  also uses its common transition without changing rewind storage. ICZ tension
  platforms apply rider impulses in native player order, keeping their shared
  spring velocity independent of object identity-map traversal.

- S3K invisible solid and hurt blocks suppress edge balancing as their native status bit requires.

- S3K top-solid platforms, bonus flippers and curled vines now select the ROM’s
  1–16 pixel landing window on the native helper surface. Flat helpers use their
  landing radius, and AIZ flipping bridges use their raw signed slope heights. Collapsing
  slopes also retain that full window while Knuckles uses his smaller climbing
  radius, so he can land during the ledge-climb animation.

- S3K Lava Reef collapsing bridges and Sky Sanctuary floating platforms now reject
  exact-boundary new landings, matching the ROM’s one-to-sixteen-pixel overlap
  check and preserving falling speed until the actual landing frame.

- **Upright S3K Act 2 capsules:** Use the normal seven-explosion sequence and
  child-owned break sound, including Hydrocity’s separate capsule owner.

- **S3K shield rewind:** Restore the captured dormant Insta-Shield identity
  alongside collected elemental shields, keeping their native dispatch ownership
  and animation continuity across rewind and seamless reloads. Unshielded cold
  captures retain their existing animation binding through restore.

- **S3K shield returns:** Elemental shields keep animating after Bonus/Special stages; deferred Insta-Shield registration no longer takes their shared object slot.
  Their native playback publishes the first frame on dispatch, defers animation
  switches, and pauses elemental shields while the player is invincible or blank.
  AIZ2 cutscene Knuckles uses the ROM standing pose and raw run/jump/laugh scripts.

- **Powered shields:** Super/Hyper players can collect shields in S3K and S2.
  Shields remain hidden while transformed and reappear after reverting.

- **Upside-down S3K capsules:** Use seven normal explosion effects and their
  break sound instead of the long boss-explosion sequence.

- **S3K animated scenery:** Angel Island, Hydrocity, Carnival Night, IceCap,
  Launch Base, Mushroom Hill, Sandopolis and Lava Reef animated tiles change on
  the original frame instead of one frame early, through the same once-per-frame
  upload as the hardware's VBlank transfer queue, including through rewind. Angel
  Island 2's first tree is restored on every frame before the scroll point, as in
  the original. Hydrocity 1's background waterline follows the original routine:
  at the waterline both repair strips load and the other animations pause, and far
  from it the last drawn strip stays. Angel Island 2's water, trickle and torch
  colour cycles continue from Act 1's cycle counters across the act change, as in
  the original, instead of restarting, and the frame of the act change still runs
  Act 1's cycle. A full level load or restart clears the counters. Angel Island 1's
  water cycle pauses only from the plane intro's explosion until Knuckles leaves,
  continues correctly out of the intro's fire colours, and changes to its normal
  colours on the original frame. Knuckles' Angel Island 1 starts with his own four
  palette colours, and the main palette reloads at the original camera position for
  every start without the plane intro.
  Launch Base's Death Egg rise reassigns its native background row pointers and
  preserves their later terrain edits through rewind.
  Hydrocity 2 restores its moving wall and camera shake together, reconnecting
  the event to the restored wall. Player water-splash animation resumes from
  its saved phase instead of continuing from the abandoned timeline.

- **S3K objects and bosses:** The Angel Island end boss starts its defeat at the
  native hit check of whichever phase it is in. Angel Island's flipping bridges
  time their landing sound from the level clock. When the object table is full,
  pending placements wait for the next camera block crossing, which restores the
  Hydrocity fan timing. Timer-controlled fans retain their initialization pass
  and the original idle intervals across repeated pulses. Sliding fan platforms
  occupy their original object slots and allocate fans during initialization,
  preserving latch reads between other fans and retrying when slots become free.
  Their allocated fan links are explicitly covered by stable-identity rewind capture.
  Spinning columns stop inertia at native zero-velocity left-side contacts. Hydrocity's hand
  launcher checks its top surface on every pass, as in the original, so a rising
  hand catches a player rolling over it.
  Its removal preserves Sonic and Tails' held state, as in the original, while
  ordinary launches still release them. Transfers to another support clear
  the hand's retained standing latch before its next grab.
  Knuckles' glide foot probes use his own live solidity bits and collision array,
  including inverted ceiling contacts, independently of camera focus. Glide collision
  follows the original velocity quadrant through wall, ceiling and floor checks;
  simultaneous contacts use S3K's floor-first response and KiS2's wall-first response.
  Reverse probes retain the solidity bit and collision path left by preceding checks.
  Slides apply collision corrections before their separate floor scan, keep the
  original signed zero-speed boundary, and preserve the turn byte through get-up
  and landing callbacks. KiS2 keeps its temporary radii across both scans.
  Ordinary object landings reset Knuckles' raw glide animation to walking
  while retaining that frame's already-produced sprite mapping.
  All glide states apply their native camera and level-boundary prelude before
  moving; sliding, climbing and ledge returns retain the original velocity order.
  Sonic 3 & Knuckles ground slides also queue their ROM sound on the level clock
  through the installed sound profile.
  Wall climbs
  include the signed native radius in vertical probes, powered speed and upper bound;
  floor landings preserve the native angle, animation and turn byte before landing
  callbacks, including values written by those callbacks. Grounded pose selection
  and host-owned native Up/Down precedence survive terrain detachment, including Knuckles'
  special-stage return; a steep-wall slip retains the incoming animation when
  the ground routine makes no pose write. The native
  second player keeps its ringless hurt response under human control.
  Hydrocity solid blocks retain underside and side contacts while offscreen.
  Snake blocks use their saved origin for camera culling and can reload when the
  camera returns.
  Spinning columns use cached sprite visibility to release and recapture riders
  while preserving platform attachment. Airborne releases retain the movement
  lock through the next player update.
  Water skimming launches on a new jump-button press, so holding the spin-dash
  button through release preserves the run across water.
  A Mega Chopper that catches Sonic alone locks his controls and passes through
  only left and right, including both directions together, so he must shake it
  off rather than roll, duck or jump.
  Jawz and Turbo Spiker expose collision attributes only after their native
  visible initialization passes. Jawz joins the touch list after its first swim
  pass, so the player cannot touch it during initialization.
  The Hydrocity miniboss spends the original start-up pass before it descends,
  so its dives, hits and whirlpool grab and release land on the original frames.
  Its retained act-change carriers keep their captured swing targets and the
  arena's camera lock until the original release event widens the boundaries.
  A drowning player sinks with the original motion, even while an object holds it,
  and in Sonic 3&K it keeps its underwater state until it dies, as in the original.
  Native fixed air slots own the lifetime; the primary drowning routine bypasses
  normal control and releases carried players before motion. Fresh physical input
  still releases a held Hydrocity bar while logical controls remain latched.
  Sonic 3&K's fixed air countdown also selects and maintains the drowning
  animation when object control bypasses ordinary movement animation.
  Tails' movement-suppressed player dispatch clears active flight while preserving
  the controlling object's animation, so release resumes ordinary airborne physics.
  Offscreen Tails flight recovery in Sonic 2 and Sonic 3&K resets the native
  player status while retaining body size, water physics and fractional position.
  A broken monitor's contents still rise and pay out after the player leaves the
  monitor behind. Ending reunion loads use measured native decompression
  costs, and the credits start on the original frame. Timing diagnostics distinguish
  verified instruction terms from work that is still unpriced. Giant-ring capture retains
  the original blank-frame ownership and art-reload state; ordinary animation
  is released again on return. Robotnik's ship stays with the beaten boss through
  its defeat pause before flying off, as in the original. Its ship component now uses
  the original escape frame and rise/countdown timing. Its separate head and
  flame keep their original dispatch, animation and deletion behavior through
  rewind. Knuckles gets the EggRobo head art, and defeat explosions follow the
  ship. Death Egg Act 1's player launchers receive their missing ROM-backed art.
  Death Egg's gravity tubes retain native rider transfers, mount velocities and
  body-size adjustment. Their animation word preserves the pending push clear,
  keeping Tails' wall approach and later floor-launcher contact aligned with the ROM.
  Their live routine word also preserves the original offscreen launcher-to-tube
  despawn decision.
  The turbine room prepares terrain probes from each
  controlled player's current motion, preserving native ceiling separation.
  Direct floor contacts also reset vertical speed, inertia and tumble state before
  the room resumes airborne control without prematurely ending hurt recovery.
  Chainspike's floor probe uses its native collision radius.
  Spikebonker's mace initializes before its first orbit step, preserving the native
  companion contact timing in Death Egg's opening.
  Carnival Night's miniboss stays dormant until the arena camera gate and native
  wait release it, including at wide viewports. Its defeated top releases standing
  riders before deletion, preserving their status and the rewindable encounter.
  Act 2's vertical camera boundary follows the primary player even when a custom
  component directs the camera toward a sidekick.
  After Knuckles' Act 2 capsule results, control returns to him and the camera
  opens gradually toward the teleporter lane; a full object pool leaves the
  handoff pending until allocation succeeds. The teleporter uses the native
  charging beam, capture and lift, with ROM-backed beam graphics and rewindable
  parent/child links, then hides Knuckles and raises the camera into the real
  IceCap load. His fresh IceCap arrival has its native contracting beam, swing
  release and solid pad, with allocation retry and complete-world restoration.
  Knuckles also advances through IceCap's camera-triggered indoor refresh without
  waiting for Sonic's snow-pile sequence.
  The ROM-backed teleporter beam is also listed in the stock art catalogue.
  IceCap's direct animation tiles enter the snapshot domain before their first
  upload, preserving the earlier displayed bytes through entry rewind.
  Sky Sanctuary's EggRobo starts on the original object pass, hovers and launches
  with the original fractional motion, and retains its last drawn visibility
  through camera movement and rewind. Its fly-by keeps the original waiter's
  fractional Y residue. Its jet flame and gun arm keep their own
  first-dispatch setup and original retirement order through recreation. The arm
  preserves its cooldown and last parent refresh before retirement. Charging
  shots stop with their gun, while fired shots continue independently and retain
  the native zero shield-reaction descriptor. Art
  priority follows the original body writes and child copies at allocation. Its
  aim now follows the original signed word comparison, including equal and
  wrapped coordinates. Body and attachment drawing preserve vertical facing;
  the gun mirrors its delayed Y offset and the jet mirrors its live Y offset,
  while shots retain their original horizontal-only facing.
  It releases animals directly into their original walking
  or flying launch, with native facing, random lift and stationary first drawing;
  the launch and rendering state survive recreation. Scaled fly-by art remains
  an implementation gap. Ordinary freed animals retire from the preceding
  sprite-render visibility flag, with separate render and collision dimensions.
  The Metropolis replica allocates its first orb from the original global pool;
  that orb initializes the ring on its own dispatch and returns before orbiting,
  drawing or publishing collision. Partial allocation keeps only the successful
  prefix through rewind.

  Boss water/effect
  children use less repeated population and ordering work while retaining native
  slots and rewind. CNZ, LRZ, DEZ, SSZ and SOZ consumers replace repeated scans
  with native-owned-slot queries. FBZ squeeze discovery skips ineligible actors
  and reuses winning projections; unchanged SOZ vine surfaces and displays reuse
  their cached work. Direct signed surface sampling avoids bulk copies while
  bulk reads remain defensive; FBZ/SOZ solid getters reuse bounded immutable
  parameters. Optional sprite
  transforms share picture calculations and batch compatible geometry in painter
  order. Instanced masks share batches, frozen art restores compare physical atlas
  bytes, and final offscreen geometry skips submission after native admission.
  Results/snowboard pattern updates batch atlas writes and results refresh only
  changed counter ranges. Stable touch configurations reuse immutable profiles
  while dynamic provider flags remain live.

- **S3K underwater objects:** Large-bubble pickup follows persistent character
  identity for leaders and followers. Sonic clears the rolling state; Tails
  and Knuckles retain it with the ROM's literal radii and position adjustment.

- **S3K objects:** Death Egg bosses are admitted only by their stock runtime
  context, while Carnival Night and stateless custom-zone aliases retain their
  own factories. Boss initialization waits for its typed zone state before
  publishing encounter side effects. Death Egg's miniboss eye finishes its last
  tracking pass before becoming independent debris, preserving movement and
  rewind after the boss retires. GAME OVER and TIME OVER wait for the event-owned ROM PLC3
  job before the cards initialize or GAME saves lives/continues; ordinary level
  service and partial-work rewind retain the native queue ordering. Hydrocity 2
  cutscene Knuckles probes actual terrain, retaining penetration and facing on
  the first bounce and correcting only the second floor contact. Monkey Dude retains five independent arm and hand objects,
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
  Marble Garden's flying-boss warning art follows the ROM's packed-byte merges
  and final partial-block clearing.
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
  separate fragment velocity table. Ice Cap's crushing-column decoration keeps
  its original construction record through rewind while continuing to follow
  the restored moving column.

- Knuckles glide speed halves above the native top boundary, including signed-word wrapping and arithmetic-shift behavior.

- Knuckles restores the native animation after non-flat glide landings.

- Correct MHZ1 miniboss four-colour damage flashing and the AIZ1/HCZ custom
  boss flash phase, preserving the final normal palette row and hit countdown.

### Sonic 1

- **Sonic 1 background scrolling:** Scrap Brain Act 1 uses the REV01 cloud
  interpolation and three building bands, with separate fractional cameras,
  rewind restoration and tile-cache window coverage at wider viewports. Scrap
  Brain Act 2 and Final Zone keep their uniform scrolling and use the REV01
  background-Y entry offset.

- **Marble Zone burning platforms:** Flames are retired from their native object
  slots immediately when their platform cleans them up, preventing an orphan
  flame from spawning after its parent disappears and preserving rewind closure.

- **Labyrinth water transport:** Sonic 1 wind tunnels and water slides observe the
  admitted VBlank before player movement, preserving the original suction and
  waterfall-sound phase across lag and rewind. Jumping from a breakable pole
  preserves its intact pose; its timeout still breaks it.

- **Sonic 1 native behavior:** folded staircases release their own native slots
  during unloading while child slots await their turn in the object walk.
  Exploding Bomb fuses retain position fractions and status in the reused slot,
  moving that first fragment immediately while fresh fragments initialize before
  animating. Grounded rolling preserves the camera's vertical look bias, with
  ordinary recentering resuming in the air. Green Hill edge walls load their
  three original mapping frames and palette from the native ROM pointer.
  Ending returns preserve a pending fade callback until it hands off to the title.
  Results waits finish before the following object routine starts the next level
  or special stage, retaining the final ordinary player-art upload.
  Boss faces, flames and accessories occupy real slots and play their ROM
  scripts. Green Hill's six-piece wrecking ball can hurt while deploying;
  defeat bursts use Sonic 1's original allocation, art and lifetime. Star Light's
  spikeballs accept native edge contact, and its escaping ship retains both
  movement calls. Final Zone's panels, launcher and cylinders dispatch in their
  native slots with independent animation and rewind state; its final flight
  retains the original scripted player input. Dormant untracked
  Scrap Brain saws retain their original objects while placement re-entry can
  create the shipped duplicate.

- **Sonic 1 level entry:** background scrolling starts from each level or credits
  demo's final camera, and lamppost returns retain the original background camera
  words. Scrap Brain Act 3 uses the original Labyrinth background scrolling
  while keeping its Scrap Brain progression. Rewind restores fractional scroll
  positions and cloud phase. Hidden
  100-point pickups retain the shipped game's 10-point award and 100-point popup.
  Consumed native level-entry requests hold source gameplay during their
  blocking fade, preserving the completed event pass and the destination's
  normal load and release. Sonic's prepared art stays pending during palette
  fades until a native player-art service boundary. Special-stage entry retains
  that palette handler through both the white-out and reveal, with one PLC
  preparation per owned token. A full level load clears
  old readiness before the fresh player's art is primed.
  Native Results exits retain the final player-art comparison row and every
  physical transfer through the following transition.

### Sonic 2 and Knuckles in Sonic 2

- **Knuckles in Sonic 2:** selecting Knuckles as the Sonic 2 main
  character now activates a built-in game patch implemented from the s2disasm
  `knuckles-in-sonic-2` branch instead of S3K donation: KiS2 physics (`$600`
  jump, `$300` underwater, single-facing balance, KiS2 landing form and duck
  hit-box), glide and climb, Knuckles' art converted through the lock-on
  program's `ArtConvTable` with the Knuckles palette line, and the
  rewritten object layouts read from the `Off_Objects_KiS2` table through
  the lock-on address space. Tier one needs only the S3K image (S&K half)
  and the Sonic 2 ROM. When the user-supplied S&K + Sonic 2 lock-on dump
  (3,407,872 bytes, MD5 `3E5E4B18D035775B916A06F2B3DC5031`) is present the
  patch runs tier two and reads the 256 KiB chip through the full lock-on
  address space: the Casino Night layouts, the Knuckles lives counter and
  1-up monitor face, the grey shield, invincibility stars and monitor icons,
  the signpost face, the continue-screen icon, the chip's own line-0 palette
  and the recoloured Chemical Plant and Aquatic Ruin underwater palettes.
  The chip also supplies the Knuckles title intro, special-stage character/HUD
  data and ring targets, results lettering, ending and continue-player
  presentation, and Super Knuckles' palette cycle. Super Knuckles uses the
  lock-on movement constants and transformation timing, with rewindable
  controller state and overlapping jump-button activation. Checkpoints restore
  their saved rings and extra-life flags; glide collision, air momentum, balance,
  solid contacts and wind/held-object mechanics follow the lock-on branches.
  Glide and slide attacks work without elemental shields; active boss-hit
  glides enter the native falling state while sliding attacks retain their slide.
  The CNZ slot face, title level-select code, Super sound-test code and independent
  results-message motion also use the KiS2 presentation. Dynamic-art lifecycle
  observations model the combined converted-RAM transfer and chip special-stage
  DPLCs, including pending transfers across loads and rewind. The chip supplies
  its own PLC table and queue workloads. Solo replay retains the title-card object
  prelude; glide wall grabs check terrain fit, and floor contact preserves flipped
  tile angles and the slide animation register. Wall grabs use the native
  position-word anchor and detach on displacement or object carry; ledge climbs
  retain the ROM animation holds and fractional position words. Native signed-width
  wall probes retain the live solidity path, wall jumps preserve the center while
  changing shape, and glide/release animation cursors follow the ROM register
  writes. Coconuts retains its separate initialization pass before idle decisions. Special-stage return title cards
  release control after their final locked object pass without an extra wait.
  Continuous replay retains the locked title-card sequence across results-driven
  act changes and starts destination gameplay only after the transition gap. Held level iterations retain the native
  queue-service tail without advancing gameplay.
  Remaining route and
  hardware-rendering limits are listed in the known-discrepancies entry and
  `docs/kis2/BRANCH_DIFFS.md`.

- **Sonic 2 parity:** companion players register their ROM
  art owner by character identity, restoring Tails DPLC submissions for runtime
  names such as `tails_p2`. Horizontal springs apply the native unsigned
  launch-side check for both participants, including the flipped equality case.
  Launchers, flippers and Point Pokey preserve captured rolls through object
  control without enabling native pinball jump blocking or speed boosts.
  All five native spring types finish initialization before checking players on
  the following update, preventing early launches when a spring reloads.
  Coconuts faces and targets the nearer native player, preserving the ROM's
  signed-word range check and main-player tie preference.
  CPZ spin tubes preserve native zero-duration waypoints and the signed
  fixed-point velocity calculation, preventing an extra movement step.
  Seventh-emerald results use the ROM's leave/return movement, wait and draw
  suppression, including the exact-seven and Tails-alone gates.

- **Collapsing platforms (Oil Ocean, Mystic Cave, Aquatic Ruin):** When a
  platform's last stood-on piece gives way, Sonic and Tails each lose their
  "standing on an object" status if it is set, whatever they are standing on,
  as the shipped game does. A Tails who died on the oil and is still falling
  therefore drops the stale status at that moment instead of keeping it.

- **Sonic 2 elevators and monitors:** Casino Night elevators keep their
  fractional position between trips and accelerate the shipped way when they
  pass exactly through their centre, so a second ride starts moving on the
  original frame. Rolling through a monitor while another platform carries the
  player no longer knocks the player off that platform. After a plunger launch,
  Tails' next landing ends the plunger's hold on her roll, so a later stop at
  zero speed uncurls her as the shipped game does. A plunger seats a rider at the
  height the shipped game's landing gives, and a bumper block on its third hit
  stays touchable while its hit animation plays, as in the shipped game.

- **Sonic 2 level start:** The title card spends the extra frame the shipped
  game waits after the zone name lands, and its exit runs the camera and zone
  events only on the frame the blue background starts to leave. Casino Night's
  slot machine therefore draws its first reels on the native frame, a finished
  spin pays out on the frame the cage sees it, and the seventh-emerald and
  Knuckles in Sonic 2 results return to the level on the same frame as ordinary
  results. Hill Top and Mystic Cave events read the level frame counter the
  shipped game reads. Sonic 2 and Knuckles in Sonic 2 reset that counter when
  control returns, so Crawl shield bounces use the original angles and Aquatic
  Ruin’s rising boss pillars begin colliding on the original frame. Crawl also
  keeps native touch active beyond the camera, so an off-screen CPU follower
  receives the shipped game's hurt response.

- **Metropolis Zone boss draw layer:** The boss now takes its display layer from
  its height as the shipped game does, so it moves from layer 3 to layer 4 once it
  descends to its pacing height. It previously stayed on layer 3, which is the
  disassembly's `fixBugs` behaviour.

- S2 ARZ leaves read the native gameplay clock after title-card release, preserving
  bubble and boss-arrow random sequences in continuous runs.

- **Sonic 2 sidekick visibility:** Tails resets his off-screen respawn counter
  directly from the native on-screen flag, removing an extra entry delay.

- **Sonic 2 object-slot timing:** Defeated objects with folded children release
  those children as the object walk reaches their slots. This preserves native
  allocation and lost-ring bounce timing in Chemical Plant.

- **Sonic 2 player and entry timing:** Super Sonic control runs between movement
  and animation while its palette advances after objects, including hurt and
  death. Transformation stars, timed-invincibility clear and audio requests occur
  at transform start, and captured normal stars survive Super rewind restoration
  and failed reconstruction retries with their original identity and animation.
  It preserves native balance, reversion animation and invincibility timing, and
  keeps the literal speed, acceleration and deceleration words written by water,
  shoes, Super and Wing Fortress events across rewind, including shoes expiring
  while still underwater. Ordinary special-stage entry presents its loading hold
  without duplicating replay timing. Special-stage player pieces use ROM mappings
  and resolve each tile through their DPLC requests, preserving whole native
  sprite footprints even when the source art is scattered. Curved-track player
  and fixed-shadow offsets retain the native gated writer across rewind, and
  special-stage palettes follow the session ROM with canonical CRAM channels.
  Ordinary special-stage bodies, objects and UI share the native H32 sprite
  table and fixed scanline limits across viewport widths. Native shadow-family
  pixels use projected terrain intensity through fades and display profiles,
  including composed underwater rows and draw-only black reveals; stage drawing
  retires inherited underwater rendering.
  Leaf generators retain the native touch phase after slow contact. ARZ bubbles
  preserve their native slot lifetime, pop animations and generator allocation
  order, including the final out-of-range spawn and a full object pool.
  Authored stages publish prepared stock or namespaced music through ordinary
  presentation, honoring both automatic-playlist suppression owners. Their common
  object art loads without a native zone header; native stages retain their title
  timing and zone-owned spring art.

- **Sonic 2 palette and ending timing:** Metropolis Act 3, Sky Chase and
  Death Egg now run their original palette cycles. The Death Egg Robot keeps
  following and exploding while its own colours fade to white, and hands the
  ending its palette and music without an extra transition. Credit slides use
  the original PAL or NTSC waits and the final fade before the closing logo.

- **S2 retail options:** The title opens the original ROM options screen, with
  Sonic and Tails, Sonic alone, or Tails alone; the two-player item setting;
  and the native sound test. Both controllers can operate the menu. Direct
  options launches retain emerald progress, while returning through the title
  starts a new game with the selected team. Competition stays unavailable until
  its complete native session is connected.

- **S2 capsules:** Buttons, locks and animal-release pieces use the original
  first-free object slots, including holes below the capsule. Each piece runs
  in native slot order, preserving launch and release timing under slot pressure
  and through rewind.

- **Metropolis boss art:** the Sonic 2 act-3 arena registers its ROM boss sheet both
  at level load and through the boss PLC.

- Solo S2 title-card entry records leader history during its native object passes.

### Cross-game gameplay

- **Stock-game results and issue fixes:** act and special-stage bonuses award
  the full native point totals, with S1/S2 trailing-zero art and S3K bonus digits
  matching the score. Locked title cards consume the unpause press. Sonic 1
  retains boss music through invincibility expiry, shows its special-stage Continue
  award, and preserves Spring Yard's cleared lower background rows while keeping
  the original foreground layout dimensions. Sonic 2 hurt
  recovery releases pipe control at the native landing boundary. Ice Cap's broken
  chain detaches into rewindable falling links, Carnival Night's escape flame
  stays behind Robotnik, and Marble Garden's head triggers, platform skid sound
  and raised-background sprite occlusion follow their ROM paths. Standalone
  background passes preserve their caller's framebuffer, viewport and blend state
  while retaining priority metadata.

- **Native death reactions:** rolling deaths perform each game's floor/radius
  reset before the death launch, preserving native centres and subpixels.
  Rewind retains the restored radius and reverse-gravity behavior.

- **Stock title starts:** starting a new Sonic 1 game clears emeralds and the
  special-stage cursor while preserving configured stage counts; Continue keeps
  existing progress. Unsupported two-player title actions return to the title
  menu instead of starting an ordinary level. Sonic 1 results transitions defer
  returned player art until the native release prelude and palette tail have
  completed; rewind preserves the pending tail's boundary. Movie-driven level
  starts apply the first recorded input immediately; later neutral rows release
  held directions and jump instead of leaving Up or Right stuck.

- **Frame-counter low-byte gates:** routines that read `(V_int_run_count+3)` or
  `(Level_frame_counter+1)` take the counter's low byte instead of adding 3 or 1
  to the count. The Sky Sanctuary EggRobo releases its animals and flashes its
  laser charge on the native frames and flickers while it launches, Cluckoid
  breath debris spawns on the native frames, MHZ pollen starts its sway from the
  current level frame, and the CNZ hover-fan sound and LBZ gate-laser flicker
  follow the level clock. In Sonic 2 the WFZ/SCZ vertical propeller's helicopter
  sound, the OOZ sun heat haze and the CPZ water ripple step on the native frames.

- **Ring visibility:** restore full-X sorting of expanded ring placements so a
  nearer ring cannot be hidden behind a farther off-screen record, fixing late
  ring appearance in EHZ1 after the 0.7 branch rollover. Ring placement also
  sorts editor reloads correctly.

- **Sidekick CPU follow routine:** The CPU sidekick's ground-follow routine is
  now a direct translation of S2 `TailsCPU_Normal` and S3K Tails CPU routine 6.
  Push-grace windows, object-order and interact-slot push bridges, fast-leader,
  sliding-leader and underwater push heuristics, deferred follow nudges,
  frame-counter projections and trace-bootstrap entry branches that had been
  tuned to individual trace fixtures are removed, with the object, level-event,
  title-card and rule hooks that fed them. Affected trace frontiers are recorded
  in the [audit](docs/architecture/audits/2026-10-31-sidekick-cpu-fitted-logic-removal.md).

- **Enemy-chain bonuses:** Ordinary enemy kills award 10,000 points from the
  sixteenth consecutive defeat, matching the native games. Normal and powered
  kills share the calculation, including native counter wrap and rewind. Sonic 2
  and 3&K select the native 10,000-point popup, and Sonic 2 selects the correct
  1,000-point popup.

- **Spin dash camera:** While a spin dash charges, and on its release frame, the
  camera eases back toward its normal height as it does when standing, as Sonic 2
  and Sonic 3 & Knuckles do. A look up or down no longer holds until the dash ends.

- **Tails appendage state:** The tail's animation and previous animation remain
  independent through scripted animation changes. Flick retains its final picture
  until the next native dispatch begins Swish, including after rewind.

- Death sprites draw without hurt-timer blinking, and crushed players reject further hurt and object-owned ring-loss requests.

### Rendering, widescreen and frame pacing

- **Widescreen presentation:** ordinary UI/HUD surfaces, titles, results, endings,
  diagnostics, all three special stages, scene effects, and trace-video capture.
  Native 320x224 remains the gameplay/trace authority. 352x224 and 400x224 are
  supported presentation targets, 528x224 is a best-effort smoke tier, and
  800x224 remains exploratory. Sonic 2 and Sonic 3 & Knuckles stage rings now
  cover the configured viewport, preventing rings from appearing late inside
  a wider screen while preserving native-width collection boundaries.

- **Window and display aspect:** a launch that changes the display aspect (a launch
  profile's aspect, or a module's required aspect such as a mod laid out for 16:9) now
  refits the window instead of letterboxing the new picture inside the old window: a
  windowed window keeps its whole-number height scale and takes the new aspect's width,
  within the monitor. Fullscreen and maximised windows, and `display.windowAutosize: false`,
  are left alone.

- Automatically mask widescreen pixels beyond current native horizontal camera
  bounds, including asymmetric arena and level edges. During staged boss entry,
  the mask follows the earlier boundary until the final lock arrives. Each side
  crossfades between world-space mask shapes; a new effective boundary starts
  from the displayed blend. Presentation history rewinds, and native-width output
  is unchanged.

- **Boss parts in their own sprite layers:** ICZ miniboss orbs and shards, ICZ end
  boss body parts, LBZ miniboss panels and box pieces, and HCZ end boss children now
  draw in the display list their ROM `priority` word selects instead of their
  owner's, and the lightning shield spark keeps the shield's tile priority.

- **Sprite priority buckets:** objects transcribed from S3K now convert the ROM
  `priority` word to a bucket (`$280` is bucket 5, not clamped to 7), so the CNZ
  hover fan, cannon and trap door and the LRZ collapsing bridge draw in their ROM
  layer, and the S1/S2/S3K objects that never stated a bucket (HCZ water splash,
  ICZ miniboss, LBZ launcher and grapple among them) no longer draw in front of the
  player. A structural guard now rejects any drawing object without a declared bucket.

- **Native sprite producers:** Ordinary mapping pieces cannot accidentally emit
  an X=0 mask and hide later sprites, with limits either enabled or disabled.
  The end-of-act tally now culls whole offscreen elements and individual mapping
  pieces using the ROM multi-draw bounds before charging sprite budgets.
  Loaded offscreen rings, collapsing bridges/platforms, ICZ columns, walls,
  freezers and tension-bridge child groups also stop consuming sprite capacity.
  The committed ICZ2 cube/button/bridge route now guards raw table submissions
  with native limits enabled.

- **Sprite limits disabled:** Also remove the total 80/64-entry sprite-table cap,
  so later sprites survive crowded scenes and results screens. Explicit level
  masks remain active; enabling limits retains native table and line budgets.

- **Repeated picture draws:** MGZ Knuckles boss fragments advance their defeat
  flicker only when the object executes, preserving the ROM's first hidden phase.
  Redrawing ICZ snow overlays and Pachinko traps no longer changes rewind state
  through diagnostic counters. FBZ cloud draws and MGZ capsule animal art selection
  also keep their temporary presentation values out of native object state.

- **Input-pass ownership prerequisite:** Existing GameLoop passes keep controller
  edges stable across logical rereads and acknowledge keyboard/text lifetime once.
  Engine display controls sample current controller input and hand it to the first
  GameLoop consumer without nesting scopes or coalescing later fast-forward passes.
  Modal display frames acknowledge their own controller edges, and pad frame-step
  capture decisions use the same fresh sample as GameLoop. Deferred passes restore
  their unacknowledged controller baseline; text claims, recorded native Start and
  the existing slow-motion carry retain their owners.
  Ordered gameplay admission now preserves observed keyboard/controller taps released
  before the consuming tick, including repeated taps and simultaneous controls.
  Zero-step service previews the same pending prefix; modal/text claims, fixed menu
  keys and recorded overrides retain their input authority. Native menu bindings
  consume each admitted edge once; controlled native rows preserve later queued taps,
  keyboard resume remaps retired paused taps before both ordinary and controlled
  gameplay, and fixed menu/text controls keep physical authority during replay.
  Live simulation and audio now use 60 Hz NTSC / 50 Hz PAL independently of the
  window FPS limit. Catch-up is bounded; pause, frame-step, focus, restore and
  session changes retain their semantic owners. Repeated pictures acknowledge no
  input and produce no audio. Window pictures follow completed services: a service
  is shown in the iteration that ran it, a limit below the region rate only thins
  those pictures, and unchanged state is not redrawn; a recording's picture doubles
  as the window picture.
  With `display.pacing: AUTO` (default), a display refreshing at a whole multiple of
  the region rate is probed for two seconds and then paces one service per swap,
  removing the periodic repeated or doubled picture on 59.94/60.02 Hz panels. Locked
  play runs at the measured display speed and returns to host time on repeated missed
  vblanks, non-blocking swaps or an out-of-band rate; `EXACT` keeps nominal speed.
  The lock needs a rate-regulated or silent speaker; an unregulated backend keeps host time.
  Native menu/background and MGZ blink clocks advance
  with updates. Trace ghosts advance their private animation on admitted replay
  rows, including pictures omitted during fast-forward; repeated pictures and
  layer/visibility filtering preserve the pose. Seeks restore observed private
  presentation checkpoints in constant time, preserving historical tunnel context
  and isolating live player timers and landing callbacks; unobserved history stays
  hidden until replay establishes it.
  Speaker playback gently trims its output rate to hold a bounded source backlog,
  absorbing host/device clock drift without changing producer, history or recording
  PCM. Pause, reverse and output replacement reset the trim.
  Opt-in `display.interpolation` (default off) blends camera and scroll, players and
  ordinary objects between the two latest displayed level pictures at every refresh
  above the region rate, so 75/144/165 Hz panels and 120/240 Hz panels move once per
  refresh. Blending is render-only and whole-pixel: art, animation, palette,
  visibility and priority come from the newest picture, S3K's native one-tick display
  lag and lag frames are kept, and the view trails the newest tick by up to one tick.
  Recordings, screenshots, traces and offline tools stay exact. Rewind, loads, act
  reloads, Tails respawns, the CNZ teleporter and camera jumps beyond the native scroll
  cap snap;
  special stages, competition, bonus stages, Stageplay and the editor stay exact. It
  uses the default Auto rendering limit, which follows the display refresh. The limit
  sits beside Interpolation in Display → Picture; the rebindable `N` shortcut toggles and
  saves interpolation during play with brief On/Off feedback. VRR qualification remains future work.

- **Native sprite submissions:** an internal queue retains call-time bucket
  pressure, duplicate slot references and late slot reuse through rewind.
  Bomb bodies, fuses and fragments submit at their native routine calls,
  including the shipped repeated fuse submission on expiry. A body keeps its
  Bomb pose through its in-place explosion handoff; explosion initialization
  draws nothing before the fiery animation begins. Unported objects retain their existing
  render path. Rewind also retains empty or rejected native passes, and converted
  sources can exclude inferred drawing independently of accepted admissions. Attracted
  rings submit at their actual object slots and retain native conversion art,
  priority and independent visibility through rewind. Suppressed player touch
  cannot attract new rings or enter the allocation-failure award path; existing
  attracted-ring motion remains owned by its object slot. Existing rings continue
  during player death; converted bouncers retain the animation frame copied at
  their own dispatch through late global animation and rewind. Native object-word
  snapshots synchronize only proven words, leaving unrelated fixed-slot shield
  helpers outside that memory projection while rejecting ambiguous native access.
  Fresh S2 competition initialization owns its explicit object-memory clear;
  bank eviction clears current live native owners in physical order while
  preserving empty-slot residue and replacements created during notification.

- **Native terrain rendering:** Retained plane images keep their original width,
  height and wrap independently of ordinary level layouts. Camera movement
  preserves their cells; owner revisions and rewind refresh the derived images.
  Internal S3K competition views combine these planes with published sprite pages
  and split scroll registers, retaining the picture through human reconstruction.
  Complete competition entry remains gated.

- **Widescreen HUD position:** score, time, rings and lives can keep their
  centered native-frame position or align to the screen's left edge. Native
  4:3 positioning is unchanged.

- **Colour expansion:** Settings → Display offers a saved Full/Limited range
  independently of the existing colour profile. Full keeps current output;
  Limited caps palette RGB at 238 after fades and profile conversion, including
  creator colours, with live normal and underwater palette refresh. The palette
  backdrop and headless gameplay captures use the same saved setting. Underwater
  donor, appearance and texture colours share one tint policy. Palette sampling
  follows each texture's own row capacity when sidekick banks are reclaimed or
  independent graphics hosts retain different texture sizes.

- **Live frame timing preparation:** Engine now separates its live advance phase
  from scene presentation, retaining fade-before-update ordering, modal input
  acknowledgment and one outer audio packet. Frame-step and modal decisions
  survive input acknowledgment through capture. Ending background scroll and its
  level clock advance with admitted ending updates rather than scene drawing,
  including updates without pictures and repeated pictures of a held state.
  Region-service scheduling
  later built on this separation (see *Input-pass ownership prerequisite* above).

### Audio

- **Sonic 2 title SFX priority:** the flashing star's last twinkle now runs to
  its own stop, as in the ROM, releasing the sound driver's SFX priority. A stop
  at star deletion had left the latch set, so lower-priority sounds that mods play
  over the stock title, such as Mutator Lab's `SndID_Blip`, stayed silent until
  another sound of equal or higher priority finished.

- Restored Sonic 1 percussion on direct gameplay launches by applying the ROM’s DAC enable at audio initialization and FM6 stereo routing at music load.

- AIZ2 and ICZ2 restore level music after results for their outgoing cutscene
  and snow corridor, including ICZ2’s run toward LBZ1.

- **Music ownership:** Invincibility monitors preserve boss/countdown music; P2
  drowning cannot replace shared music. S2 boss fades and sound test use native
  mailboxes. S3K restores the apparent act or invincibility at the native restore
  boundary, corrects Knuckles cutscene fade triggers/timing and lowest-free song
  allocation, and uses native miniboss IDs. S2 ring-threshold jingles retain the
  native secondary mailbox in solo and competition play.

- S3K Act 1 results defer Act 2 music to the in-level title-card wait gate,
  preventing a duplicate start during AIZ, HCZ, MGZ and other act transitions.

- **Native-compatible PCM output (diagnostics):** An opt-in audio session setting
  feeds the accurate FM chip's own pin sums through a Java port of blip_buf. It
  uses BizHawk's Nuked YM2612 scale and the movie's filter, which is None,
  LowPass or ThreeBand with musl's `sin` and the stock core's single-precision
  pi in its cutoffs. It produces 44.1 kHz PCM for comparison
  against BizHawk. On an identical strobe schedule it matches Genesis Plus GX
  source vectors byte for byte. `GameplayCaptureTool --native-pcm` records it from
  live gameplay using a BK2's own settings; only NTSC Nuked movies are accepted.
  A first real window measured the presentation clock running 1.29 per mille
  short of the ROM's hardware frame. The PSG half follows GPGX's integrated
  PSG and also matches its source vectors. The MAME YM2612 core and waveform
  parity are not covered. Native PCM comparisons validate complete selected row
  spans and capture digests, rejecting truncated-prefix false matches, and stream
  PCM in bounded chunks for long captures. S1/S2
  complete-run producer plumbing remains gated by native publication evidence
  and independently installed artifact attestation.

- **Audio cadence:** Each presented frame now advances the sound chips by exactly
  one NTSC hardware frame (3420 x 262 master clocks) instead of 1/60 s of chip
  time. Previously 600 frames completed only 599 hardware frames. The
  presentation still delivers 800 samples per frame at 48 kHz, so at 60 Hz music
  and effects sound about 1.29 per mille higher and faster than a 59.92 Hz
  console, while staying in step with gameplay frames. PAL is unchanged. The SEGA
  chant's sample bytes follow the exact Z80 loop cadence on that budget instead of
  a rate rounded to whole hertz.

- **Sound hardware across level entry:** A level load that re-installs the same ROM
  and audio profile no longer resets the sound chips. The ROM's sound driver keeps
  running into the level, and the engine now keeps the chips with it; Sonic 3 &
  Knuckles' Data Select hand-off used to rebuild them twice. A different ROM or
  game, or a reset, still installs fresh hardware, and rewind restores the kept
  chips exactly. The Sonic 3 & Knuckles title screen now opens with the ROM's
  music fade-out request, which silences the PSG on entry as the console does.
  Sonic 1's SEGA logo begins with its directly loaded ROM palette and native
  light scan, without an additional generic fade from black.

### Rewind, recording and capture

- Add a default 10-second consumable rewind budget with Off / Per checkpoint / Per act / Per zone, Unlimited, optional life-loss refill, and independent bonus/special-stage visit allowances. The compact rewind HUD shows seconds and a draining bar; an empty press shows the drained treatment for one second.

- **Rewinding dynamically created children:** restoring a saved moment now keeps the
  children a live object created while it ran. The camera-limit easing objects a Lava
  Reef rock crusher spawns, and the Fireworm's body segments, used to disappear on
  restore, and a restored segment's flame stayed behind while the segment swam on.

- Rewind verification compares private records and complete captured opaque arrays
  by content, with explicit failures when snapshot inspection cannot complete. It also
  checks all captured ObjectManager gameplay cursors and contact buffers. Palette
  copies retain complete raw RGB comparison.
  Codec-backed field snapshots also compare and hash their scalar and opaque array
  contents, so separate captures of unchanged state remain equal.
  S3K held rewind retains unchanged foreground and full-layout background tilemaps;
  actual geometry and encoded row-pointer changes still rebuild their derived images.
  Boss rewind also retains child construction history after those children retire,
  so future effects keep the same identities when replayed.
  Typed level and object-manager snapshot comparisons also accept equal untyped
  content. Rewinding to a point before a level's first object-load pass reloads its
  objects again (Flying Battery's act-one boss included), the Angel Island intro keeps
  drawing Sonic after it is recreated, a departing scripted carry-in Tails is restored
  as the same sprite, and a recreated skid-dust puff keeps its owner's art.

- **Recording audio buffering:** Capture retains a bounded stream of final PCM
  across producer packets and video drains, preserving sample order and unused
  packet tails. A stalled capture that exceeds its bound falls back to correctly
  clocked silence while speaker playback continues. Live recording emits a current
  final-viewport picture with matching region-duration PCM for each admitted
  service, independently of window swaps. Recording requires these extra draws;
  overload slows recorded time with simulation time rather than inventing pictures.

- **Movie and trace controls:** Playback and recording suppress live debug actions
  that could change gameplay, so opening trace details no longer enters a special
  stage.

- **Recordings:** copied BK2 recordings are bounded during decompression
  and parsing, including metadata, input rows and verification sidecars. Oversized,
  ambiguous or inconsistent files are rejected without hiding healthy recordings.
  Restarting a recording releases the retired audio reader before replacing the
  ROM, while a live audio capture continues on the reopened source.

- **Gameplay capture tooling:** `GameplayCaptureTool` pictures or films any
  gameplay section on the production boot path (any game, zone, act, position,
  width, donor or team), driven by a BizHawk input log, and writes PNG frames, a
  per-frame state CSV (including live player priority) and an MP4. `InputLogAuthorTool` compiles a short controller
  script into that input log and proves it loads through `Bk2MovieLoader`, which now
  also reads a bare `Input Log.txt`. Skills `gameplay-capture` and
  `bk2-input-authoring` document the workflow. Native BizHawk reference capture
  uses a shared host with explicit zone plans, input hashes and failed-export
  detection; existing FBZ commands remain compatible.
  Controller-branch probes now stop on game-mode changes as well as level replacement,
  preventing restoration of an outgoing gameplay snapshot after special-stage entry.

- **Gameplay capture tool:** a capture can now declare the ring count its route carried in
  (`--rings`). A boss filmed from a positioned start otherwise begins on no rings, where the
  first touch is fatal and the fight cannot be filmed at all.

- Headless gameplay captures run on macOS (the hidden boot window requests the OpenGL 4.1 core profile the engine renders with) and can apply a packaged patch mod (`GameplayCaptureTool --mod <jar>`) to film mod gameplay.

- **Capture and controller tools:** Direct-level captures can explicitly seed
  the inherited end-of-level flag once after declared setup; title-first captures
  reject that seed and later gameplay remains authoritative. Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
  An individual trace segment resolves its validated containing run's declared
  movie, preserving shared-movie priority and rejecting unsafe source aliases.
  Continuous runs keep executing an active native level after their final
  comparison segment closes. Full S3K campaign certification requires a
  recorder-observed movie endpoint and checks its remaining movie tail;
  partial runs retain optional endpoints. Playback cleanup releases only its own input
  publication, preserving the next driver's held input and first press edge.
  Closing or rebooting a headless capture releases its owned audio ROM reference,
  so a subsequent title and saved-slot launch can read a fresh ROM in the same process.
  Configured headless startup keeps no-device audio ownership through the native
  title, save selection and first gameplay frame.
  A public state projection tool records controller-driven cold Sky Sanctuary
  paired widescreen play before and after each step and normal draw, including
  copied ring state and local player bindings, with explicit inputs and bounded
  temporary output.
  Silent headless boots also retire inherited audio backends before playback, keeping
  reused capture processes off desktop audio devices. Configured engine startup
  retains the supplied no-device backend through ROM detection and title entry,
  with synthesis enabled or disabled. Recorded controller Start reaches native
  consumers, including gameplay pause, without toggling host pause during a
  capture or complete-run audio step; physical keyboard pause remains available.
  Native PCM live checks admit graphics prerequisites before running capture, so
  boot, gameplay and audio failures fail instead of being skipped as unavailable GL.
  Standing-geometry probes retire their private audio manager through its complete
  lifecycle while preserving caller-owned audio.

### Menus, settings, input, ROMs and languages

- **Master title hub:** a cartridge shelf of game cards shows each game's original
  animated ROM logo, sliding between games, with missing ROMs named on their card.
  Setup chips under the selected game say what Start will launch (players, aspect,
  rewind, borrowed features, debug, mods); amber chips are set for that game only. Chips are
  status only: they take no focus and are changed through Game Setup. Start, Game Setup and Recordings follow; Mods,
  Settings, Extras, Advanced and Quit are engine-wide header tabs. Opening Mods from
  a game filters the list to applicable mods; catalog entries use consistent compact
  lettering regardless of name length. Footers show only
  the keys that work on the focused row as key caps or controller buttons, with
  status kept apart; a line under the buttons describes the focused item. Back
  returns to the card before offering Quit, and Escape no longer also runs a hidden
  hold-to-exit timer behind that dialog. In a game, the old "hold ESC 2sec" debug text
  becomes a menu-styled toast that slides in at the top (an Esc key cap with *Hold to
  return to games*), fills gold over a one-second hold and reads *Returning to games*
  while the screen fades. Holding Escape in a Sonic 1, 2 or 3&K game now suspends it
  instead of closing it: the game stays loaded behind a **Paused** card first on the
  shelf, showing its last frame and where it was, with **Resume** and **End game**.
  Resume returns to the same frame, camera and music position. Anything that would
  replace the paused game (another game's Start, Recordings, Mods, Extras, trace
  replays, editor projects, content import) asks first, defaulting to *Keep it paused*,
  and Quit warns that the paused game will end. Settings and Game Setup stay
  available, with a Game Setup button on the paused card: on resume the game takes
  new rewind, debug tools, controls, wide camera movement, automatic window size and
  capture settings, and, if paused in a level, a new aspect ratio (the camera, level,
  parallax and background widths are refreshed in place; renders after a mid-level
  switch match a native start pixel for pixel in GHZ, SBZ, EHZ, HTZ, CPZ, AIZ, HCZ and
  MGZ). Settings and Game Setup rows say whether the paused game takes them; the rest
  (characters, Borrow from, ROMs, sound, region, frame rate) apply to the next launch.
  Fixed: the background renderer's per-column scroll buffer kept the width of the
  first game played, so a later game at a wider aspect lost column scroll beyond it.
  Recordings, traces, hosted runs, editor playtests, mod
  scenes and standalone games keep the full return. In mod scenes and standalone mod
  games a tap of Escape reaches the mod without the toast, which appears only after a
  quarter-second hold. Mouse hover, click and wheel work across
  the shelf, buttons and tabs, and in the Mod Manager's list, buttons and
  prompts. Confirmation, cancel and error cues stay
  host-owned with soft fades and moderate volume; compact text and shapes share
  an atlas batch, and streamed quad buffers avoid GPU waits during drawing and
  the launch flash to keep speaker playback fed. Start uses white lettering on
  its gold button, and Time Attack arrives through Extras as a bundled mod.

- The master title takes the mouse: hovering highlights actions and clicking opens them, the carousel's arrows, neighbouring names and the wheel change game, clicking the game's name browses the list, and right-click goes back; the game browser, quit prompt, tools and start choices answer clicks too. Pointer positions map through the letterboxed viewport with the same helper mod scenes use. Game cards retain their ROM-decoded title artwork while idle, without switching to gameplay scenery.

- **Game Setup:** replaces Launch Options with grouped Players, Screen and Extras
  rows labelled THIS GAME ONLY. Rewind, debug tools and aspect ratio follow the
  all-games Settings value ("Default (Off)") until a game overrides them, so turning
  Rewind on in Settings now reaches every game that has not chosen otherwise;
  existing boolean profiles migrate (`true` to on, the old stock `false` to default).
  Cross-game donation is labelled **Borrow from**, and the S3K save screen's team is
  shown rather than hidden; the focus stays on Borrow from when that swaps the
  Character and Sidekick rows for Team. Backspace resets the focused row, and the description
  box explains it.

- **Settings:** categories become tabs (Display, Sound, Controls, Gameplay, Files,
  Capture) labelled ALL GAMES, with eight full-width rows, a scrollbar, one type size
  per page and a description box carrying the row's keys and default. Every non-debug
  shortcut moves to Controls; debug, Discord, direct-launch characters and cross-game
  keys move to Advanced > Advanced settings; launch profiles leave Settings for Game
  Setup. Settings, Game Setup and Mods share one draft flow: the footer counts unsaved
  changes and Back asks Save, Discard or Keep editing. Shared text editors keep
  onscreen value/path/key editing, numeric/address keypads, an asynchronous
  controller file browser and full-text details; unsupported glyphs retain their
  Unicode identity without changing stored values. Recordings, traces, mods, room
  browsing and lobbies keep bounded pages and visible actions. Startup notices use
  crisp native fonts and controller navigation; catalog scans run in cancellable
  background tasks and unchanged ROM previews survive saving. The experimental
  editor exposes a controller command palette for editing, saving, exporting and
  playtesting. Settings that only apply after a restart (frame rate, display shaders,
  colour profile, the rewind VHS effect, Discord, start-up screens, test mode, window
  size and turning sound off) carry an amber RESTART tag and say so in their help;
  once one is saved with a new value, the Settings and game-shelf footers read
  RESTART NEEDED until OpenGGF restarts, and Details for every setting says whether
  it applies on save, at the next game start or after a restart. `MasterTitleCaptureTool` renders these menus offscreen for review. Advanced also offers
  Editor Projects and Import Content for reviewing dropped editor projects, exported
  level folders or ZIPs, and mod/mutator packages before opening or installing them.
  Imported mods remain disabled until enabled through the mod manager. The
  level-editor guide includes illustrated authoring examples.

- **Main menu:** Compact and bold lettering now share one proportional
  pixel-font family, with narrower lowercase, steady digits for counters and
  clear navy lettering on gold launch buttons; long names, paths and text fields
  fit and scroll by measured width. Gameplay transport and trace overlays keep
  their fixed-width lettering. Holding Escape freezes a
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
  quiet title music decoded through an isolated stock audio source that preserves
  the suspended game and bootstrap owner. Controller hints
  follow the last device used. Compact Controls, Advanced and ROM browsing pages,
  direct ROM location, bounded setup badges, explicit Save changes actions,
  clearer inherited values, calmer backgrounds and more readable text,
  player binding groups, conflict warnings, confirmed tab resets and save summaries
  make setup easier to review. Expert loading and rewind tuning live in Advanced;
  Display and Advanced group their rows under captions (Window, Picture, Widescreen
  and HUD; Shaders, Rewind, Debug and the rest), Advanced settings carry authored
  names such as VHS rewind effect, help never shows a configuration key (Details keeps
  the exact text), and the tape coast's tuning sits indented under Tape coast, dimmed
  but editable while it is off. Details says when each change applies in one sentence.
  Game Setup and Playback on a card whose ROM is missing open the same ROM locator as
  Start, and a failed ROM load offers Locate ROM first and points to Settings > Files.
  First-run discovery can locate one ROM or scan a folder for all three games,
  retaining partial discoveries at scan limits and explaining file-access errors; it
  says nothing about the limit once every game is found.
  Mods, the Playback browser, details pages, the text keypad, Import content
  and the startup notices now share the hub's header, key-cap hints, focus frame and
  buttons: mods show on/off switches, game badges and status tags. One Playback
  browser now lists engine recordings, BK2 movies and ROM trace segments/runs,
  with game/type filters, explicit row/frame counts, retained recording options,
  aligned kind/game badges, entry lengths, and clear readiness or setup warnings.
  Recording options share the library's factual summary card; details and held trace
  failures wrap complete values beside labels, with paged diagnostics, moving an
  identifier such as `VDP_DMA` whole to the next line rather than splitting it.
  Bare BK2 files show that launch setup is required;
  invalid metadata stays visible without weakening import limits. Settings and
  Game Setup show readable values in sentence case, key bindings as keys and
  button names without a repeated player prefix; save prompts fit their changes
  and show each one as old value to new value. The quit prompt offers Stay in
  OpenGGF or Quit to desktop. Locating a ROM shows a readable folder breadcrumb,
  file and folder icons and an empty-folder state; its ROM? tag, like the shader
  picker's format tag, shows on the focused row unless the visible files differ. Mod
  trust and dependency confirmations, and their results, name the affected mods and
  what changes, and the Mod manager counts
  art slots instead of pattern windows. Trace playback diagnostics sit on backing
  panels, and long overlay text no longer loses its first characters when first shown. The
  display shader picker is a framed panel with folder navigation, a filter and the active
  preset marked. Rewind and input recording show compact one-row plates (`REW`, `REC`, `PLAY`) that stay clear of
  the game's own HUD, with translucent backgrounds, consistent word spacing and outlined
  status icons. Native 6x8 text keeps complete transport counters and configured recording
  shortcuts visible (`REC: HOLD SHIFT+F9`, or the remapped key), with frame labels such as
  `F:44`. Both replay types use `Tab: Details` to open a readable, paged mismatch report;
  verification failures do not open it automatically or change playback policy. Mutator Lab uses the same switches, sliders and key hints as Settings,
  and Settings rows lead the eye from each setting to its value. Every Settings tab now
  uses the same light row lettering, description text has open leading, capture
  settings read as Video codec FFV1, 192 MB or Automatic, and Settings and Mod details
  line values up in a label column. The Advanced page and Add your games list each
  choice with an icon and note, the launch button and missing-ROM card carry glyphs,
  Mod order shows the numbered load order, empty Playback scopes explain the next step
  and trace loading names the trace and its launch steps. Pause and window video
  capture show as compact top-right plates (`PAUSE`, `VIDEO`) that no longer overlap
  the playback plate, and the hold-Escape toast no longer covers the score; while a
  pause or video plate holds the top-right corner it rests just below it and slides in
  from the right beneath it, never across it, eases back up when the plate goes, and
  shows its progress as a thin meter along its bottom edge. Controls and help reads as
  Move, Select and Shortcuts groups, and a controller sees one Up/Down row that says where
  Mods and Game Setup sit and its own Help button. When the hub's setup chips end in
  "+N more", the line below names every hidden one on the card and on Start, in amber
  where it is set for that game only; the Sonic 3&K donor shows one chip and says its save
  screen picks the team, and Game Setup lists Borrow from above the characters it decides.
  The paused card keeps one status line under its buttons: what resuming applies, or
  what ending costs. The hub keeps one spacing rhythm from the game card to the footer, its
  page dots stay visible over the clouds, and the paused card's badge uses the pause icon
  that pairs with Resume's play icon. Quit and end prompts mark each choice with a check
  where nothing is lost and a warning where something is; the ROM-not-found card fits its
  content and breaks a long path between folders; Advanced rows keep each name clear of its
  note; and every Add your games step sits centred, with found games in one aligned column
  and a scan that stops early saying so. Controls bindings
  have room between rows, Mod details labels drop the colon beside their aligned values,
  a failed trace says Back to list (keeping the same row selected) and leads with one plain
  headline above its exact, searchable error, and the editor project menu ends its keys
  with Close like every other page, says what each command does and shows scroll position
  when its actions fill more than one screen. Importing content
  states each fact once, the keypad's accept key reads Set, a mod with code says to switch
  it on and confirm Trust and enable. Current native packages load code-bearing mods,
  so the obsolete native-capability warning has been removed. Reset and
  window-preview prompts match the other prompts, with a draining countdown meter on
  the preview. Add your games shows what it is reading and a working meter, and lines
  found games up as one list; the number keypad offers only real keys (Delete, Clear,
  Default and paired cursor keys) and its help says what each one does. The
  level editor's menu, property and settings sheets read in sentence case with whole
  labels, aligned shortcut keys and a scroll bar; its palette shows each colour's
  hardware channel steps, and button prompts fit the smallest editor window. Its menu opens
  on a short front page (Resume, Save, both playtests, the level pages, projects and
  **Exit to title**, which first offers Save and exit, Exit without saving or Keep editing);
  **All commands and shortcuts** opens a grouped command sheet (Editor, Edit, Region,
  Pattern and tile, Objects, View, Playtest, Project) listing each command once with the shortcut it really has, such as Del for
  Delete region: Undo and Redo lead Edit, Stageplay sits with the editor's own pages, and
  the two playtests say where the player starts (Shift+Tab from the cursor, F5 from the
  level start). Ctrl+0 resets the zoom without also picking hotbar slot 10.
  Time Attack's chat card names Tab for keyboard Details, including while ready.
  The bundled Time Attack mode's pages (menu, settings, profile, vote pools, text
  entry, room access, invites, room browser and room details) share a header band,
  light rows in a measured label column, key-cap footers that follow the last device
  and wrapped messages instead of cut-off lines; a revealed LAN invite wraps whole
  beside its Hide button, and lobby notices read whole in the row face inside their card.
  Lobby actions read as sentence-case rows (Ready up, Ready rule, Room chat, Leave room, matching Settings),
  your own row's character stays readable, and room chat reads Full chat in every view.
  The newest chat message wraps onto a second line instead of being cut, with senders
  kept to a third of the row; Room details scrolls through every player and message,
  each whole, opening at the newest message. Every Time Attack page's Details key (F1, or
  R3 on a controller) opens a read-only page under that page's own title, with its facts in
  the same label column and the values worded as the page shows them; sentences wrap at
  spaces and addresses after a separator, so no word is split. In a room it opens Room
  details, which now begins with the room's name, next track, phase, rules and any notice.
  Playback Browser and its recording options, target-frame keypad and trace feedback
  pages now support mouse selection, actions and wheel scrolling; right-click goes back
  one level, and the side gutters of a centred native page activate nothing.
  Text fields in mod scenes reserve typing keys before global shortcuts and keyboard
  mappings run, so entering a name cannot open playback or toggle debug panels; the
  field still receives its text and editing keys, and shortcuts resume after it closes.
  Narrow footers leave out arrow hints and shortcuts that repeat a row before an action,
  Details or Back, and secondary buttons such as Back use the row face. Controller key caps
  name the button that actually does the job on that pad (X and A on Xbox, Square and Cross
  on PlayStation, West and South otherwise, for confirm and back) instead of the Genesis
  letters, which on an Xbox pad named the back button as confirm; once ready over the
  lobby level a keyboard names its bound select key, a pad is no longer offered the
  keyboard's vote digits, and a key bound to the opposite button (Enter to B, say) does that
  one thing. Sonic 1 profiles say when the ROM colours cannot be read on the page instead
  of showing unexplained grey swatches. Creator scenes can name player 1's bound key or pad
  button the same way with `SceneContext.buttonLabel`. The Stageplay
  editor names its mode or take in a top-bar chip at every window size, beside at most
  five key caps (or pad buttons) for the focused pane that repeat no button's key, and shows shortcuts as
  caps on its buttons and Commands rows. Long scene names keep the unsaved-change
  marker visible. Mutator Lab's list and options pages name their shared timing once in
  the band (rows keep only exceptions such as pending edits or a Restart-or-death mutator),
  the switch row reads Enabled, and while play is held the list shows no opaque Hold key
  (Resume is the way back). The Act Practice example's menu now follows the same design
  language: a navy page with the game on a focused card showing its best time and page
  dots, key caps that name the keyboard or the controller's own buttons, whichever was
  pressed last (Genesis A, C and Start confirm, as in every scene), results whose icon and colour say whether the
  act was completed (and set a new best), failed to load or left the best unsaved, and a
  no-games page that says what to do next. Its in-run timer reads m:ss.cc, as Time Attack
  does, instead of drawing a question mark for the double quote the compact face lacks.

- **Team editors in Settings:** Settings > Advanced edits the direct-launch
  sidekick line and the save screen's extra character teams as ordered teams
  instead of comma- and semicolon-separated text. Each sidekick row shows the
  character it follows; Left/Right changes the character, and its actions move or
  remove it. Sidekicks can be added up to 16 per team. Extra teams can be added,
  edited (player and sidekicks), reordered or removed, and read as names on the
  Settings row. Empty lists explain what remains; unlisted stored codes are kept
  until changed. Every full catalogue has current machine drafts of the new
  wording, revised after independent Opus 5.5 and GPT-6.1 Sol reviews.

- **Support notices:** Starting Sonic 3 & Knuckles from the hub first shows a
  translated Please note dialog saying its support is in alpha and asking for bug
  reports on Discord or GitHub, with both links selectable to open in the browser.
  Back returns to the shelf; Continue starts the game, and Don't show again hides
  that game's notice until its stage changes. Any stock game can be gated or
  released by editing one table.

- **ROM image catalogue:** the engine now recognises every user-supplied image by
  size and cartridge header instead of filename, from the three per-game keys plus a
  `roms.directory` scan. Lock-on dumps serve the games they contain (Sonic 2 from an
  S&K + Sonic 2 dump, Sonic 3 and Sonic & Knuckles from a Sonic 3 & Knuckles dump),
  and a separate Sonic 3 image plus a separate Sonic & Knuckles image boot Sonic 3 &
  Knuckles as an in-memory view with no joining on disk (`roms.preferComposite`
  picks it over a dump). The per-game keys remain explicit overrides; among
  duplicates a hash-verified image wins, and an unverified image is still used.
  The master title hub dims games from the same catalogue. Engine code no longer
  reads through the ROM's shared file channel, so catalogue views and files behave
  identically.

- **Original ROM revisions:** Sonic 1 and Sonic 2 recognize their original
  REV00 and REV01 images and select matching ROM data locations and shipped
  behavior. Revision choices belong to the loaded image, including logical
  cartridge views, while shared gameplay code and game-specific providers are
  retained. Unknown images keep the existing unverified compatibility path.

- **Interface languages:** OpenGGF menus, settings and the built-in editor use
  contributor-editable ARB catalogs, with 20 initial locale choices and explicit
  machine-draft status. A ROM-free language picker and saved preference update
  wording and measured layout together. ICU handles whole-message grammar and
  script-safe fallback; the translation tools support partial contributions,
  source-change detection and exact-text review without a translation service.
  Independent Opus 5.5 and GPT-6.1 Sol reviews informed corrections to 2,324
  messages. Draft status and canonical-English legal fallback remain explicit.
  FFmpeg help now displays literal template tokens without requesting runtime
  arguments, and ambiguous source labels have clearer translator notes.
  Ellipsis fitting warns with the language, text and measured space, including
  English, while suppressing repeated warnings across frames and resizing.
  Settings uses the extra room up to 16:9 and stays centered beyond it; measured
  rows wrap and focused overflow pages without ellipses. Built-in menus, editor
  sheets and bundled Time Attack pages expose complete long text through paging
  or an advertised read-only reader, including paths, errors and retained chat.
  Added and changed menu-overhaul wording now has current drafts in every full
  catalogue. Playback facts and import guidance use complete ICU messages;
  special-stage playback labels come from metadata rather than translated text.

- **Host text in every interface script:** menus, help and editor text measure, fit,
  wrap and draw through one shaping owner. Latin accents and Cyrillic use the native menu
  lettering, including bold labels; Chinese, Japanese and Korean use bundled pixel fonts with
  language-correct Han forms; Arabic, Urdu, Hindi, Bengali and Thai are shaped by HarfBuzz
  with bundled Noto fonts at a legible size and a taller line pitch. Fitting, wrapping and
  text-field editing respect grapheme clusters and language line breaks. Field carets follow
  visual bidi order, and scrolling preserves the full shaped line and Arabic joining. Text no
  bundled font can draw shows a visible box instead of a substitute. Text fields now
  display OS IME composition, focused clauses and a shaped caret before commit.
  Creator scene and course text capture receive a host composition strip. Native
  composition has been checked with an isolated Linux X11/IBus session; other OS
  IMEs still need platform verification. Empty native composition updates, which
  Wayland sends with ordinary keys even without an IME, no longer hide Backspace,
  Enter or Escape once typing starts.

- **Mouse wheel in menus:** the wheel now moves through Engine Settings (including its
  value and key-binding choices, language picker and team editors), Game Setup and its
  change summary, the display shader picker, mutator configuration, content import, the
  editor project menu and the editor's library, menu, settings, properties and Details
  sheets; Details pages scroll with it wherever they open. Every menu reading the mouse
  in a frame sees the same notches, and notches scrolled during gameplay are dropped
  instead of jumping the next menu or zooming the editor canvas. Creator menus read the
  notches from `MenuInput.Pointer.wheel()`.

- Default Player 1 Start to Enter and Player 2 Start to Backspace; unify live Start with the visible pause,
  preventing a second native pause when resuming. A mod scene that reads the Start key itself now owns
  it, so one Enter press no longer also counts as Start; pads still send Start.

### Level editor and route tools

- **Level authoring:** Named projects create, edit and reopen complete authored
  levels for Sonic 1, Sonic 2 and Sonic 3&K using ROM-derived asset libraries.
  Atomic saves retain placements and missing dependency records; close and quit
  protect unsaved work. Start/cursor
  playtests use fresh native runtime snapshots and return to the authored
  level with selection and undo history, including native in-place editing without
  automatically publishing gameplay sidecars. Host-validated exports support
  S1/S2 v1 and S3K v2, including a host-owned native results goal in every game. Title Tools and
  the editor menu expose creation, import, properties, resizing and
  Save As; pattern browsing, descriptor controls, local collision edits, erase
  and spawn inspection complete layout authoring. 52 named hazard, checkpoint, badnik, spring, monitor and
  platform schemas share lossless subtype schemas between desktop inspectors
  and a controller-navigable HUD property list; other IDs retain raw-byte editing.
  World-cell regions copy terrain and native spawn groups, with undoable cut/paste,
  translucent destination previews, delete and rectangle/flood fill. Independent
  mouse, keyboard and controller canvas pan and integer 1×–8× magnification keep
  hit tests aligned. Creator-owned palette cells use exact Genesis RGB values and
  preserve colors through undo, project saves and existing exports. Provider-backed
  background scrolling, fixed water height and required teams persist into native
  playtests and packages. Typed camera/music regions, paired warps and blocking
  public-API Stageplay triggers support runtime rewind. Ordered saved projects
  export data-only playable campaigns; project-contained PNG/sheet imports install
  native art and optional palettes in one undo step. A controller-first HUD drawn at
  window resolution frames the level with a brush hotbar, a full-screen library
  of ROM-art thumbnails, native sprite placements and translucent brush previews.
  Picture-based object browsing hides internal IDs by default, while compact gizmo
  labels and a dock-fitted desktop canvas keep the level readable. Native editing
  retains ROM collision semantics, with explicit compatible-layout cloning and
  save/cancel/discard protection before replacing unsaved work. A tools wheel,
  block and chunk workbenches and button
  prompts for the pad in use. Canvas zoom spans 0.25×–8×, revealing more terrain
  below 1×; arrow keys and the D-pad step whole blocks for terrain or eight pixels
  for objects and rings. It is the sole built-in editor UI, with world-space
  editing guides underneath; a separate OpenGGF-Editor provider can replace it
  through the window-chrome extension. Stock Emerald Hill can be adopted, playtested
  and exported. A full clone keeps unplaced library chunks whose art lies outside the
  decoded pattern table, drawing them transparent; placed ones still fail. Sonic 2
  placements may carry the Y-word bit 12 that the ROM's object loader never reads.

- **Route tapes in both editors:** the in-engine editor's Routes workspace and
  OpenGGF Editor open `<stem>.route.json` routes and edit both pads' physical
  button rows on a two-lane timeline with undo. Replay and qualified saves run in
  a separate worker; the editors show qualified or the first row a replay cannot
  complete, draw the replayed path over the route's level, list the manifest's
  launch and native observations (display only), and export a BizHawk movie on
  demand. Saving changes the files only after a passing replay; unfinished work
  saves as a draft that tests and native checks refuse. Repair keeps a route's
  rows where play still tracks a reference and reports the share it kept.
  Stageplay's applied routes are now input rows; **Edit input rows** opens them in
  the same timeline and applies edits back to the scene as one undo step.
  Routes carry goal legs (reach, board, ride, attach, hold, await a phase, defeat,
  cross) on placed or runtime-spawned objects, picked from a scan of live objects;
  Plan searches rows that complete them after a kept prefix.

- **Shared route planner:** Reference-path authoring, Stageplay player routes
  and the SSZ and DEZ boss input authors now share one planner. A route is a
  frozen list of controller rows with a manifest (`.route.json` and `.script`);
  it counts as qualified only after a cold replay of those rows, and BK2 movies
  are exported from it. Stageplay saves planned player routes as frozen input
  tracks (document version 9). Bridges, platforms, updrafts, vines and bosses
  report their phase, attachment or hit points, so route goals can target them.
  Repair keeps a route's original rows and searches only where play drifts from
  a track recorded on the engine the rows were written for; an exact repair
  edits single rows until both runs agree field for field again. Twelve Sky
  Sanctuary Act 1 cold routes are repaired this way (one or two edited rows
  each) and replay their original runs. Native checks compose the route after a
  recorded entry movie with one filler row per observed lag frame.

- Editor Enter descends into tile composition without also cycling spawn mode through the default Start binding.

### Stageplay

- **OpenGGF Stageplay:** shared directing documents power the in-engine HUD and
  OpenGGF Editor's Stageplay workspace. Both editors share compact grouped forms,
  named choices, collapsed Advanced settings and structured F1 details with buttons,
  plain route advice and a dim More toggle for full diagnostics. In-level
  position picking pans through the level with coordinate tags and optional queried
  floor snapping, marked by a yellow floor line and dashed guide; placement pickers
  start snapped and numeric entry stays available. Uniform HUD rows keep primary actions visible at the standard size;
  expanded settings can scroll. Author actor movement, logical Left/Right facing, animation/poses,
  camera movement, smooth animated zoom, earthquake effects, captions, ROM audio and WAV/Ogg voices;
  direct scene changes across locations, acts and games with cuts, fades and wipes.
  Authored music control holds off automatic level playlists and entry fades through
  deferred entry and shot changes in playback/export; named music choices and exact
  ROM sound IDs agree across the screenplay, catalog and both editors; ROM cue gain is checked during authoring.
  ICZ's arrival teleporter and MegaChopper release only their acquired player
  controls for direction, preserving native state and rewind identities.
  Free objects, including released animals outside the main view, retain native
  appearances when switched to Puppet/Pose within their cast activation windows,
  including native sprite priority and foreground-wall occlusion in directed films.
  Capturing a native appearance preserves its animation phase and reports visual
  readiness before requiring facing ownership.
  Puppet mode follows destinations and urgency through native acceleration, terrain
  collision and jump physics; floor snapping defaults on. Shared Place on floor
  direction and a batched ground CLI enumerate exact visual support and native player
  terrain proposals from decoded entry layouts or prepared scene frames, with bounded
  work, typed refusals and one-step undo for safe editor placement. Shared native terrain
  scans preserve the caller's collision-array selection. Poser mode retains manual
  positioning, animation and frame selection. Every scene uses one native world
  with supplied-game performers, authored terrain and common framing, binding and
  activation-window rules. Timed object clears preserve separately owned rings;
  unpublished documents and screenplays accept only their current format. Native ROM worlds share moving objects, collision pickups, native hurt and per-actor controllers;
  bounded routes/placement targets, native flight/glide and actor/group
  camera follow use the production gameplay and rewind owners. Directed foreground,
  characters and occlusion stay aligned through camera movement. Depth-backed scene
  targets retain native sprite overlap and ghost blending while preserving their
  original depth image. Current native
  scenes project background parallax/deformation at the directed camera without
  advancing native effect phases; separated performers share native object slots
  through independent activation windows. Inspect actual state
  and events through windowless simulation and placement/capability discovery. Live
  editor previews run in an isolated offscreen worker, preserving the active world;
  preview and export preserve logical-ROM windows and composite images.
  Cross-game scene changes keep music and sound effects bound to the destination
  game; same-game level entries preserve continuous playback.
  Record directed takes, undo edits, save/reopen and export synchronized
  lossless films. Shot-local screenplay tools and a mirrored agent skill support
  resource discovery, validation, lossless canonical-to-screenplay round trips and
  offline filmmaking; encoder paths resolve from the caller's directory. Both editors
  import/export the same screenplay and start new dialogue at the same top margin.
  Simulation reports abilities from each performer's own game capabilities. A bounded
  route helper preserves per-route starting action, a slow stroll or live native Run speed,
  gentle/hard/overshoot endings and final facing (Spindash requires Run),
  and emits native movement/jump intents over actual collision terrain, including
  Sonic 1 loop plane switching, with observed native charge/release and cold LIVE verification, penalised back-up
  run-ups only after direct steering fails, and location-specific speed advice
  supported by a matched Run probe with its own bounded diagnostic horizon and
  explicit advice when more attempt frames are needed, plus visual walking-object routes over slopes and
  steps up to eight pixels. Both editors show the simulated route before Apply,
  with grounded/airborne segments and endpoints over the picture, plus route state,
  matching goal legends and complete short result sentences in editor chrome; applied
  plans remain visible during authoring playback without entering saved scenes or films.
  Centred camera pushes preserve their framing centre,
  fractional origins survive composition, and the world framebuffer is reused.
  Evaluated framing stays within native level bounds in films and both editors;
  authored keys keep their original values.
  Tall views extend native background pattern bands without stacking another sky.
  Tails uses native accessory
  relationships, and both editors keep a usable constrained authoring window.
  Named dialogue, reusable typing profiles and creator PNG skins give mod scenes
  clear speakers and deterministic text reveal in playback, both editors and exports.
  Player and object speakers have distinct Genesis PSG speech noises synchronized to
  that reveal, with named voice presets and compact per-speaker tone, rhythm, pitch,
  variation, length and level controls, including silence. Object dialogue pointers
  follow native cast positions; screenplay import/export preserves voice settings.
  Mods play the same scenes with held native gameplay and optional explicit
  main-player handover. ROM object roles add Free native behavior and visual Puppet/Pose
  direction with child-part frames, portable placed identities and timed clear/removal
  commands. Interrupting a native holder, including nested ending/P2 helpers, releases its
  acquired players while preserving native routine progress; seeking restores
  that ownership. The hand launcher releases acquired players for visual direction
  while native unload retains its separate behavior. Logical viewport dimensions
  remain exact while native SAT admission limits stay fixed.
  A generated ID/context catalog reports native arena and appearance
  restrictions. The [Stageplay guide](docs/guide/creating/stageplay.md) has a
  first-film tutorial for the editor and screenplays, task chapters,
  troubleshooting, a glossary, a complete reference and a case study of the
  *Day Off* film, whose screenplay joins the
  [directing examples](examples/stageplay-demo/README.md) beside a 26-second
  beginner scene with native reactions, timed dialogue and a single-setting jump
  comparison.

### Mod framework (unpublished candidate API)

- **Unpublished Mod API candidate:** mod loading, creator tooling, characters,
  standalone games, and custom-zone work are now available on the development
  line. Added zones preserve inherited native title lifecycle callbacks; object
  helpers follow their injected owner. Creator reconstruction uses the bounded
  context, while host snapshot and restore-queue shapes remain internal.
  Explicit development-directory launches show their active mod, details,
  and notices in a read-only Mod Manager without changing installed-mod settings.
  The installed-mod manager applies saved enablement, order, and code trust at the
  master title by rescanning jars and rebuilding the mod runtime and launch choices;
  restarting the engine is no longer required to change loaded mods.
  Its list identifies each mod's target game (S1, S2, S3K, all three, or standalone).
  Compatibility notices distinguish published overlaps, suspected interactions
  and verified preparation blockers, with targets, participant builds and affected
  content. Declared conflicts and valid exclusive overlaps warn while retaining
  ordinary override policy; hard contracts and failed-owner recovery remain enforced.
  Healthy dependencies are identified as context rather than blocked content, and
  retired audio no longer retains active overlap evidence.
  The master title's game pane shows enabled mod names below the launch profile,
  cycling through names when several apply to the selected game.
  The creator handbook now starts with a Java 25 setup path, seven purpose-specific
  starters, a capability matrix and the complete recipient install/update/trust/
  rollback journey. Creator kits pair the engine, SDK, API documentation and
  production-backed Jupiter test support from one source commit, with its
  module-only rewind composition isolated from engine artifacts, portable
  example projects, repeatable builds, artifact-only CI and explicit packaging
  warnings suitable for automation.
  Exported projects retain their authored tests, fixtures and source generators;
  the portable test launcher checks matching artifacts and runs Jupiter with
  empty discovery treated as failure. Generated starters include a separate test
  profile and packed-mod integration test. Infinite Sonic and Sonic Survivors
  share the example builder while keeping their documented jar paths.
  Separate experimental Windows and Linux native friends bundles package the maintained
  example mods and launch shortcuts using GraalVM runtime class loading, with
  generated engine/JDK member retention, runtime bootstrap controls and startup checks.
  Retention includes the separately loaded controls' bytecode dependencies, keeping
  their callable JDK bodies available when example-mod references change, and
  engine-created policy implementations returned through service factories.
  Factory bytecode discovery includes returned anonymous service implementations.
  Windows shortcuts forward diagnostic arguments and are checked through the native executable.
  The Linux builder also exercises
  representative rendered gameplay, audio and scene lifecycle paths. The shared
  gameplay probe checks every declared one-shot sample through the production
  session wrapper into final mixed PCM, including Eggman's Sky's Vorbis voice bank.
  Scene startup probes open the backing game's initial zone before the scene,
  preserving scene-owned admission to authored activities.
  Gameplay qualification remains
  experimental; the normal native release policy is unchanged.
  Art overlays preserve their base provider's title-card and runtime-art admission
  leases across respawn, transitions and rewind; generated badnik starters resolve
  the active game's destruction sound through its semantic sound map.
  Owner-local zone and level identities compose across mods and retain namespaced
  save locations. Multi-act campaigns, typed runtime contributions and captured
  zone events use deterministic ordering and inspectable conflict decisions.
  Returned callbacks, decoded-level transforms and shared service/rewind graphs
  retain the verified owner's fault and restoration boundaries.
  Save-field inputs are frozen through the provider's owner boundary, standalone
  audio profiles can omit a native ROM loader, and object reconstruction preserves
  optional restore services without exposing runtime fallback access.
  Shared helpers cover declarative owned object assembly, factory-bound character
  identity and standard art emission, immutable physics and placement edits,
  checked owner-local save slots and captured state, authored fonts and overlays,
  remappable actions and ROM-qualified scene art. Save slots preserve rejected files,
  report failed publication and retry the same checkpoint without repeating rewards.
  Ordered sprite composition and scoped canvas transforms share geometry across logic
  and drawing; gameplay row admission distinguishes native input, controlled neutral
  stepping and a complete hold. Injected floor/contact, spring sound and destruction
  services remove repeated native-object workarounds.
  Provider title-card positions share choreography while preserving each scene's
  loading/release policy, and atlas fonts support authored mixed-case glyphs with
  an explicit fallback.
  Shared compact-font metrics and drawing support scales 1–8, including Sitar's
  five-times-size count-in without changing its authored pixels or audible clock.
  Generated character starters draw their installed baked art; walkthroughs use
  repeatable Maven outputs and the examples' actual required ROMs.
  Generated standalone starters materialize authored audio as runtime resources
  before packaging.
  The maintained examples adopt these helpers while retaining their game rules,
  visuals, input formats, random sequences and saved profiles. Prepared definitions
  and generation-owned caches avoid repeated construction; Slay targeting is independent
  of draw cadence and Survivors settlements credit once across rewind. Creator tests use
  production validation/loading/resolution and record real scene input and draws;
  authored playable routes retain their wider gameplay/rewind coverage obligations.
  The API descriptor remains the version/publication authority; this
  branch rollover does not publish or freeze it.

- Supported mod builds now check attributed Java source and compiled payloads against
  the reachable Mod API member contract. Engine-only singleton accessors are excluded
  from the candidate promise; the portable builder offers an explicit expert legacy lane.

- **Mod licensing:** Mods can use any licence. The OpenGGF Mod Exception, a
  GPL-3 section 7 permission, covers mods that call, implement or extend engine
  types, including those outside the `@ModApi` set, without copying engine code.
  The Nuked OPN2 and blip_buf ports are conveyed under their LGPL rather than
  converted to the GPL, so mods under other licences can ship with them. `ggfmod init` adds no licence and its output is MIT-0. The example
  mods, the bundled Time Attack mod and the creator kit's sample campaign are
  MIT; each carries a `LICENSE` file that the creator kit exports with it. The
  engine itself remains GPL-3.0-only.

- **Mod authoring entry points:** Six task recipes and mirrored agent guidance
  route creator work to supported helpers while retaining expert overrides. The
  portable project launcher supports local artifact/ROM paths, CLI overrides and
  read-only text/JSON preflight, alongside build/test/run and the advanced Maven
  workflow. Portable builds convert object sheets, playable art and level inputs
  from the seven maintained starter conventions. Starter and kit projects ignore personal local path configuration.
  SDK validate/package reports retain their v1 fields and add next actions and
  documentation references; requested JSON also covers input/operational errors.
  Ownership, trust, warning policy, runtime APIs and publication rules are unchanged.

- **Mod API source checks:** Commit hooks allow implementation changes inside
  established engine-internal nested terminals without a false signature-pin
  update. Nested identity and visibility, outer and sibling API declarations,
  and candidate/published pin enforcement remain checked against Git snapshots.

- **Native code mods:** Default Linux x64, Windows x64 and Apple Silicon native
  packages use pinned GraalVM Community runtime class loading with generated API
  and service retention. Code mods use the JVM's trust, validation and fault
  boundaries; native packages include first-party bundled mods such as Time Attack.
  Friends packages add the maintained examples and launch shortcuts using the same
  native build recipe. macOS keeps size optimization and a 5 GiB compiler heap
  on the standard runner; Linux and Windows use default optimization and 8 GiB.
  Maven preparation and image compilation run separately in CI to reduce build memory. Runtime loading remains an
  experimental VM facility. The free macOS runner cannot hold the code-mod image's compiler
  working set, so its build is tolerated rather than required: 0.7.20261105 publishes without
  `OpenGGF-macos.zip`, and Macs use the universal JAR until the macOS package is built.

- **Multi-game controlled mods:** one code mod can provide per-game patches with shared
  objects and services, alongside an optional mutator catalogue. Patches need no scene,
  including in development captures; catalogue-only packages retain their bounded role.
  Gameplay observers receive world-owned callbacks, rewind and cleanup even without
  mutator definitions, without creating mutator preference files. World retirement
  unpublishes the session and resets admission even when creator cleanup fails.
  Scene audio belongs to the current visit, including its retained native act;
  ended and replaced visits cannot issue commands. Finite scene playback and
  title-exit commands wait while an owned native act runs.
  Startup scenes can launch stock acts and switch supplied games
  or teams; frame controllers supply reproducible pad input and capture layout-aware
  typed text. Course restores preserve engine ownership, and supplied testkit scene
  capabilities survive storage isolation. Custom SFX can declare a load-time DAC
  rendering. These additions retain the unpublished Mod API 0.7 candidate.

- **Hosted gameplay and bundled mods:** scenes can launch stock, non-saving runs
  through a bounded run API, observe steps, hold gameplay, draw ghosts and overlays,
  and request retry or return. Scene capabilities expire on exit and creator faults
  retire the run before returning to the title. Build-pinned bundled jars use the
  ordinary mod validation and trust flow; private binary storage supports ghost data.
  Shared client, room and protocol contracts are available to other mods.
  Ambiguous broker timeouts retire the connection, fail pending operations and
  require a fresh connection before retrying, keeping late replies out of later requests.

- **Mod scenes:** a patch mod can register a full-screen startup scene (`ModContext.registerStartupScene`) that opens in place of the base game's title screen and can hand back to that title or to the master title. Scenes draw on a `SceneCanvas` (fills, images, built-in menu text, clipping; per-draw scale, mirror, tint and flash) with RGBA `SceneImage`s decoded from PNGs, built from pixels, or rasterised from the player's ROM: `SceneRomArt` reads sprite art (Nemesis, Kosinski, Kosinski Moduled or uncompressed, with or without either S3K DPLC layout) through the game's own mappings in any palette, plus character frames, their ROM animation scripts and Tails' tails. For Sonic 3 & Knuckles it also builds zone pictures without touching the running game: `SceneRomArt.zoneBackdrop` returns a zone's background as one opaque `SceneBackdrop` image with its parallax bands (speed and cloud drift taken from the zone's own deformation tables), and `SceneRomArt.levelOverview` returns a whole act as a box-averaged miniature of its foreground over its background, cropped to the playable area and sized to a height limit (Angel Island acts 1 and 2, Hydrocity act 1, Launch Base act 1 and Sky Sanctuary act 1, each in one documented presentation state; `SceneRomArt.hasZonePictures` says which). `SceneRomArt.titleCard` returns an act's stock title card as its four sliding elements, with the ROM's slide, hold and exit timings documented so a scene can play it. `SceneRomArt.levelStages` finds runs of an act's floor with clear headroom from its collision (a per-column floor profile in each `SceneLevelStage`), and `SceneRomArt.levelForeground` renders the foreground for any rectangle of the level at full size with transparent sky, so a scene can stand characters on the real level in front of the backdrop. Scenes read named pad buttons (`SceneButtons`, with the engine menus' key repeat) and keys (`SceneKeys`), with B as back as on the Genesis, decode raw ROM tiles (`SceneRomArt.tiles`), draw a parallax backdrop in one call (`SceneCanvas.drawBackdrop`), and also get merged keyboard/gamepad input, the mouse in game pixels (left and right button edges, whether it was the last input) with wheel notches, music and sound effects, and owner-scoped text storage under the save root. The mod validator accepts the static code javac itself generates for `assert` and for a `switch` over an enum. A mod can fix its session's display width without a game patch (`ModContext.requireDisplayWidth`), and scenes can offer tools a debug entry point (`DebuggableScene`). Every scene callback runs inside the mod fault boundary, and the mode reports itself as `GameMode.MOD_SCENE`.

- **Mod scene level kits and streaming images:** `SceneRomArt.levelKit(zone, act)` returns any supported act's building blocks for scenes that assemble terrain of their own: the foreground layout as a grid of layout blocks, each block rendered alone with transparent sky, each block's primary-path collision as a pixel mask (empty, top-solid or solid), the playable area, the zone's background and its palette. Sonic 1 and Sonic 2 offer every act their detached decoders can build (all but Sonic 2's Hill Top and Wing Fortress); Sonic 3 & Knuckles offers zones 0-12 and Hidden Palace. `SceneImage.streaming(width, height)` makes an image a scene re-renders every frame with `update(pixels)`; the renderer refreshes the same GPU texture instead of uploading a new image each frame.

- **Stock dash dust for scenes:** `StockSceneArt.S3K_DASH_DUST` names Sonic 3 & Knuckles's dash dust (the spin dash cloud and the skid puffs) as a ROM-qualified recipe, so a scene can draw it from the player's ROM and play its own animation scripts with `RomAnimationPlayer`. Skid children reuse the bank selected by their parent's ROM DPLC preload through an uncompressed scene-art request.

- **Named ROM object art:** compiled mods can request Sonic 1 object sheets from
  `LogicalRom.S1` while running on an S3K base. The host resolves the supplied ROM,
  parses Sonic 1 mappings and retains registration bounds and creator fault ownership;
  the existing Sonic 2 intake keeps its already-open host ROM and needs no
  additional resolver. Cross-ROM intake requires the named source. Runtime art stays in memory.

- **Scene-to-act round trips:** the unpublished Mod API lets a scene suspend into an
  owned registered act with native character, centre-position and health-ring
  overrides, then receive one typed result on the same retained scene/context.
  Scene audio is re-prepared on return; frame-boundary exits and normal load/mode
  boundaries isolate rewind. Native shield effects recreated during player refresh retain
  their captured creator callback owner and rewind identity. Starpost Valley connects its farm gate and existing
  town menus through a native-collision placeholder act, with its scene valley
  retained as a debug path until encoded terrain integration.

- **Mod scene music from any supplied ROM:** `SceneAudio.playMusic(game, id)` plays a song from any supplied ROM as the scene's current music, so an S3K scene can play Sonic 1's Green Hill and still hear its own sound effects over it. Another game's song uses the cross-game donor route (the one that plays S3K Super music in Sonic 2), so it loops at its own loop point and needs no second sound chip. The existing `stopMusic`, `fadeOutMusic` and stock jingles treat it like any song. The scene borrows the donor route only while it is open, and leaving restores whatever cross-game donation had registered. The call returns false when that ROM was not supplied or has no such song. Scene-owned music tempo percentages (25–100) preserve note and DAC pitch, SFX timing and in-song tempo changes, follow retained native acts and rewind, and restore normal pacing on final scene exit.

- `ggfmod run` of a patch mod now opens the mod's base game directly instead of the master title game picker (holding Escape still returns to it), so a creator's build-and-run loop lands in their mod; test mode keeps its configured startup. Slay the Robotnik's `play.sh` uses it to build and launch the example in one step.

- The Sonic 1 title screen accepts an optional background override (`Sonic1TitleScreenManager.setBackgroundOverride`) that replaces its scrolling Green Hill plane and backdrop while keeping the emblem, sprites and fades.

- Game modules can pin a display aspect for their session (`GameModule.requiredDisplayAspect()`, restored at the master title; trace test mode stays 4:3) and hide the level select (`GameModule.suppressesLevelSelect()`).

### Example mods (framework previews, not released)

- **Sonic3K Chaos Mode:** a maintained code mod adds 105 selectable effects over
  ordinary native S3K, with available/selected lists, interval and duration controls,
  severity-weighted scheduling and five optional ROM-art sidegames. Owner-scoped
  temporary mutators and immutable gameplay observations keep scheduling alongside
  native movement, objects, audio and presentation; accepted decisions, deadlines
  and cleanup participate in rewind. Native target, art, route and control
  qualifications constrain effects, including bounded Cluckoid gusts and ghost
  encounters. Too Many Sonics leaves up to 24 bright native poses; viewport-wide
  diagonal ring rain and recoverable Leaky Pockets drops use native ring physics.
  Spawned bumpers and ghosts retain their ROM donor colours without changing the
  host palette. Offscreen title captures share the native save-selection launch,
  retain the selected team and prepare fresh mod callbacks for the new world.
  Menus use framed lists with scroll position, severity colours, filtered add/remove,
  player-facing effect descriptions and controller-aware hints; search keeps Space,
  Backspace and letters bound to game buttons as text. A top bar counts down
  to each draw, new effects announce themselves with a Starpost cue and a short card
  over the Chaos panel, clear of the player, native challenge prompts show progress, receipts name their effect and sidegame
  boards show their title, score, timer and controls. The HUD no longer appears over
  native save selection. Stream voting remains a future extension.

- **Second Strike prototype:** a source-first difficulty patch follows the native
  S3K Angel Island opening with cooperating spotters, committed guards/artillery,
  five original rings across forest/fire, hostile Robotnik monitors with speed
  shoes retained, multi-hit enemies and invulnerable flame-shield rhino charges
  with a 225-pixel budget and bounded vulnerable return, twelve-body native
  Caterkiller chains, and two broad ten-seed plant fans,
  a warned late Fire Breath follow-up, first-board
  Blue Sphere turn gates and a three-spring Gumball variant. The normal campaign
  retains terrain, physics, lives, checkpoints, save UI, stage rewards and act
  progression. An opt-in decoded placement contract preserves the native level
  class, fixed object-slot initialization and transition owners; owner-bound
  stage policies validate isolated data.
  Bounded creator helpers preserve native destruction, shields, monitor contents
  and presentation; exact lifetime links survive rewind and colocated placements.
  The tree-exit lookout retires its native arm chain in object-slot order before
  its parent slot can be reused, while already launched coconuts keep their own lifetime.
  Game-owned recipes keep native construction out of shared effects.
  Field and powered callbacks retain the receiving creator's fault boundary,
  aborting a failed frame without blaming another mod that invoked the receiver.
  The example passes strict packaging without unsupported engine references.
  Its launcher preserves a separate native save/config profile and atomically
  publishes only a validated build; creator kits include ROM-free walkthroughs.

- **Mutator Lab catalogue:** one JVM boot-prepared package exposes eleven effects
  in Sonic 1, Sonic 2 and Sonic 3 & Knuckles, with native game labels, monitor-type
  availability, scrolling settings and explicit unavailable-feature reasons. Effects use
  typed scoped options, atomic admission, owner fault isolation and session rewind
  separate from saved preferences. The title/configuration/play/restart/hub flow
  keeps native movement and sprite admission, with declared character/art support
  and maintained package source/build-along guide. Modal title Back
  preserves the save boundary and host prompt ownership; title cues use ROM SFX.
  World-held policy services keep core lifecycle independent of creator schemas;
  title-first capture preserves existing direct-level fade and input-last CSV contracts.
  Its title card sits below the native emblem, How to play names the live key
  bindings, play-hold configuration dims the held frame, and option help wraps to
  two lines with plain-language boundary feedback. Backing out keeps the focused
  effect, rows name the choice that applies each edit or why a row is unavailable,
  and a chosen restart or hub return holds native play behind its fade. Big Head
  can optionally scale with rings: normal at zero, the chosen size at 100 rings.
  Its Afflicts group independently selects Player (default), Sidekicks, Badniks
  and Bosses; legacy team preferences migrate without losing explicit choices.
  Pose-specific ROM lassos cover native Sonic/Tails/Knuckles and the separate
  S3K Super Sonic bank, preserving foreground limbs and native sprite admission.
  Reviewed enemy heads enlarge independently of vehicles and projectiles;
  ball, obscured and unknown art keeps its native presentation.
  Ringfall can optionally drop the full inventory past the native 32-ring ceiling
  as real recoverable rings that never take level object slots.
  Typed catalogue contributions also declare scatter, head-presentation, semantic
  monitor removal, checkpoint/ring suppression, independent stage-entry gates,
  defeat knockback and whole-step speed across ordinary play and native special/bonus
  gameplay. Native setup, stage results and canonical movie/trace ticks retain their
  existing owners; live rewind preserves admitted taps and fractional pacing.

  Native special/bonus-stage clock, input and offline PCM observations are
  reproducible with `MutatorStageProbeTool`; its direct-entry and hidden-GL
  limits are documented separately from desktop footage.
  Routine validation and footage use headless/offscreen paths; window-specific
  diagnostics create an owned virtual display and fail if isolation is unavailable,
  preventing automated walkthroughs from taking desktop keyboard focus. Linux
  captures can opt into a surfaceless EGL context that needs no display connection
  and releases its platform selection for later engine boots. Native ring/enemy
  observations and layout CSVs are available through maintained display-free tools.
  The gameplay observer can explicitly capture native special-stage drawing and
  interactive speed with production input/pacing and offline audio, preserving
  canonical movie stepping by default.

- **Homing Attack mutator:** Mutator Lab adds Homing Attack for Sonic 1, Sonic 2
  and Sonic 3 & Knuckles. Jumping again in the air homes the leader in on the best
  target in range: a boss part with a hidden priority first, then targets ahead,
  then bosses, badniks, breakable walls, grab points and breakables. The leader
  stays in the native roll, so hits, breakage and grabs follow the original rules.
  With no target, the native double-jump move runs; failing that, Sonic dashes
  forward. A reticle marks the target, launches lag the camera like the fire-shield
  dash, a faint blue glow trails each strike, strikes chain and bosses bounce the
  player off. Sonic, Tails and Knuckles
  can each be enabled. Instagib mode builds friction heat in front of the player,
  makes the strike invulnerable, ends bosses in one hit and bursts defeated enemies
  into shards that defeat other badniks they hit; bosses are immune to shards.

- **Second Strike hard-mode prototype:** Native brittle supports, earned zero-ring
  bonus invitations, finite badnik/boss patterns and original-ring retention keep
  player physics unchanged. Authored AIZ and first-five-zone campaign ingredients
  are being developed, with native MHZ wind pulses, LRZ echo jets and an FBZ
  extra attack beat available as finite ingredients. Native finale ingredients
  cover HPZ feints, an SSZ returning-boss extra pair, DEZ guard detection and a
  delayed Doomsday tail missile; these remain unactivated pending qualification.
  Cold routes, character
  breadth, full later-zone authoring and visual verification remain in progress.

- **Putt Putt Paradise — Sonic 2 Mini Golf:** added an external source-first mod
  using the player's full Emerald Hill acts. Sonic and Tails putt or chip with
  Dream Course-style A-only power timing and chip top/backspin contact points,
  stay rolling until supported settlement, and play
  practice, alternating local matches, or direct IP/port matches. Golfers retain
  independent course worlds; optional shot rewind restores the entire pre-shot
  world and refunds the stroke, with faster playback for longer shots and
  configurable per-golfer hole/turn allowances (default 3/1). Rewind is available
  during WATCH with the host's configured VHS effect and backward playback of
  the shot's recorded audio; online peers follow the host's phase and rate using
  their own locally heard audio. Settlement and finishes
  pass the turn automatically. Clearer scorecards, guide panels, controller menu
  prompts and authoritative online turn hints keep presentation policy in the mod.
  Animated turn handoffs wait for the incoming golfer's fresh ready press and
  synchronize over netplay; shared engine prompts name each player's actual
  keyboard or controller binding. ROM sound cues, sliding shot panels and an
  animated results tally accompany the action. Penalties restore the pre-shot world, and the
  online host owns simulation and scores while guests render ROM-backed views.
  Recorded play uses the same neutral flight input as live play.
  Includes a creator menu themed with the ROM's title landscape, winged emblem,
  Sonic/Tails portraits and sparkles, the selected practice act through the native
  title transition, completed entry overlays before shot checkpoints, scorecards, immediately visible idle golfers
  after turn switches, Tails' independent tail in held poses, a held native duck pose during
  shot selection, native spindash audio, all existing
  viewport presets, a spin/power departure guide aligned with the rolling launch
  centre and supporting slope (including authoritative online guest coordinates),
  upward springs that also fire on rolling side entry, 90-degree chips with a
  small forward bias for clearing walls, and a
  build/package/launch script. The unpublished
  candidate API now supports held gameplay admission, opaque course checkpoints and
  read-only value scenes without changing stock modules' frame behavior. Bounded
  `SceneReplay` and session-owned `AudioReplay` make reverse presentation reusable
  for creators while permission, scoring and checkpoint timing stay in the mod. The final
  example adds a source-reading path and extension walkthrough, shared logical
  menu mouse input, elapsed native-pose clocks, and a golf-owned impulse adapter;
  the controller API keeps character/destination/impulse operations reusable.

- **Putt Putt Paradise multiplayer:** Scene admission bounds aggregate clipped
  raster work, lines clip before traversal, and unchanged presentations reuse
  composed images; invisible geometry skips raster row traversal. Incoming scenes
  coalesce within ordered packet boundaries at transport and room delivery, with
  each mailbox capped at 256 entries / 4 MiB retained bytes. Ended room state stays
  final across buffered reconnects and failed sends. Hosting defaults to literal
  loopback; explicit LAN hosting displays a private session invite and verifies a
  fresh HMAC proof before admitting a guest. LAN game traffic remains plaintext.

- **Emerald Remix:** a new example mod for Sonic 2 with two level changes. Emerald Hill
  Act 1 is rebuilt at load time from column strips of the player's own ROM, using a small
  text recipe: an Act 2 stretch, a triple corkscrew made from Act 1's pieces, and an
  authored monitor. Clover Valley, an original act with generated tiles and a parallax
  background, plays between Emerald Hill and Chemical Plant. Both levels open in the
  level editor.

- **Super Sonic Bros.:** a new example mod, a platform fighter for one to four
  players (people or CPUs) on Sonic 3 & Knuckles. Sonic, Tails, Knuckles, Robotnik,
  Mecha Sonic, Egg Robo and Rhinobot each have a full move set (tilts, chargeable
  smashes, aerials, four specials, grabs and throws) drawn from their ROM sprites, and
  fight on ten islands carved from real acts with their own backdrops and music.
  Damage builds from 0% and drives knockback; stock, time and stamina modes, teams,
  eight monitors, nine CPU levels, a six-round single-player Classic ladder, a live
  move list, two keyboard layouts plus gamepads, saved rules and records.
  Original synthesized announcer and character voices accompany in-game events.
  Fighter facing follows ledge hangs, skids, lying poses, multipart spins and
  directional effects. See [the README](examples/super-sonic-bros/README.md).

- **[Typing of the Hedgehog](examples/typing-of-the-hedgehog/README.md):**
  a typing action mod for any supplied Sonic 1, Sonic 2 and Sonic 3 & Knuckles ROMs.
  Sonic runs between fights, homing-attacks badniks when their words are completed,
  rescues animals and wins back spilled letter-rings. ROM-art bosses use typed dodges,
  phrases, taunt echoes and trivia. The widescreen hub offers Story, Arcade, Boss Rush,
  Endless, records, options and Tails' Typing School, with an original voice cast and
  clean or Mega Drive-style playback. All 31 story stops have courses: the four retained
  continuous routes plus shorter selected sections, with native movement between ground
  fights, Tornado escort waves in Sky Chase and a Super Sonic flying finale in Doomsday.
  Route authoring shares Stageplay's native steering and braking for destinations and
  fight stops; selected sections rehearse native terrain while holding the stock world.
  Their widescreen views reuse Stageplay's scoped native scroll and palette presentation.
  Independent ROM sheets supply late boss art, including Doomsday's plane-built ship.
  Checkpoints preserve pause, slow motion and retries, and menus offer each stage's
  supported modes.
  The example uses the shared Java 25 builder and accepts explicit ROM/artifact paths.

- Added an independent [Infinite Sonic mod prototype](examples/infinite-sonic/README.md): seeded endless sections assembled from the user’s Sonic 1 ROM in every act of Green Hill, Marble, Spring Yard, Labyrinth, Star Light and Scrap Brain (Final Zone stays stock), with forward world recycling for solo Sonic and a background that scrolls on across each recycle without a jump, seeded terrain-aware ground/flying patrols of the badniks each act's own ROM object layout places (Orbinaut spikes, indestructible Rollers and Walking Bombs), collectible ring rows and spaced jump corridors (64–192px pits and ledge drops that climb or descend between four ground levels) between hills and dips, with sections that join the halves of different ROM columns and climb or drop a ground level on the zone's own slopes between corridors. The survival challenge raises whole-game speed every 30 seconds by a quarter of its current speed (1× → 1.25× → 1.55× → 1.95×), gliding to each new speed over a second, enforces a minimum forward scroll while letting Sonic build a lead to just right of centre, with spare lives earned only from every 100 rings (none at the start) shown on the HUD, a CONTINUE/RESTART death menu whose CONTINUE spends a life and rewinds the run with the engine's live rewind (recorded even with live rewind off, through a new `GameModule.scriptedRewind()` hook) to the first fair moment at least a second before the death, with a pit-free runway ahead, then READY waits for the player and the speed eases back up from 1×, and a game over drawn in the mod's own font with an instant restart instead of the stock card, and replaces the timer with speed/countdown information and speed-based survival scoring. A prominent five-second countdown and warning chimes announce each increase, and music/effects follow the challenge speed in both tempo and pitch, with sequencer updates interleaved through accelerated PCM rendering. The Sonic 1 title screen adds an animated INFINITE wordmark above the emblem and a left/right zone picker that starts the chosen zone and shows that zone's own scrolling background behind the emblem, and course title cards drop the act number. Platform stretches bridge wider pits with the zone's own stock Sonic 1 platforms (stationary or falling floating platforms, wide moving blocks) flush together as one raft in the middle of the pit, or, at random where the zone has Buzz Bombers or Batbrains, with a hovering flyer to bounce off instead of or as well as the raft, rings arc over pits, the mod always plays at 16:9, and each zone is a single course with the stock level select unavailable. Some ring rows are hop arcs, stretches split into a high road of the zone's stock platforms (climbed by steps, 128px up, with rings and monitors) and a badnik-patrolled ground path, each zone has its own timed hazards drawn with ROM art (Green Hill wrecking balls, Marble and Star Light pit fireballs, Spring Yard rolling spiked balls, Labyrinth spiked ball-and-chains, Scrap Brain flamethrowers, and spike beds everywhere) that take hits through the same shield/ring toll as badniks, Star Light gains platform stretches on its elevators, and the death menu and GAME OVER offer EXIT to the Sonic 1 title, as does a tap of Escape mid-run. Sonic shows his full-speed running frames at the course's lower top speed. Enemy hits are gentler: a shield, picked up from non-solid monitors on the course (which also hold Super Rings), absorbs one hit, and otherwise carrying 20 or more rings spills just 20 of them, both without knockback; Sonic's top running speed is 7/8 of stock, the minimum scroll is 4 px per tick, and the camera holds Sonic 45% of the way across the screen. While Sonic has no shield and fewer than 20 rings he sweats and the HUD ring count flashes red, and the HUD heads with the zone's top score. Every run lays a new random course (a JVM property pins one seed), and each speed-up flashes the screen, rushes speed lines past its edges and announces the new speed. Each zone keeps a saved top-10 leaderboard: it shows as each run starts, a banner and chime mark overtaking the zone's top score, the death screen congratulates a new top score or shows the run's rank, and the idle title pages through the zone leaders and each zone's top 10 like an arcade attract loop. Includes a source build/package script and focused ROM-backed traversal, platform crossing, enemy interaction and rewind tests.

- Added an independent [Sonic Survivors mod](examples/sonic-survivors/README.md), a bounce-driven survivors roguelike over the user's Sonic 2 ROM: every route zone (Emerald Hill to the Death Egg, Sky Chase skipped) becomes a walled arena cut from its own act's terrain, with the stock objects, rings and zone events removed so the act cannot be finished the usual way. Badniks from each zone's own ROM art (19 species across seven AI archetypes: walkers, hoppers, leapers, flyers, divers, turrets and Asteron's burst) have hitpoints, health bars and elites, and pour in from both sides and above for a five-minute survival clock; Sonic starts with only his jump, every airborne stomp rebounds him and chains a combo that multiplies damage and ring drops, and bounces charge a six-second Fever burst with an eighteen-second recovery. Rings are health (a ring toll that grows with the arena's pressure clock and route position instead of knockback, lethal at zero) and experience: each level-up pauses for three cards (rerolls available) from twenty-four upgrades, limited to three weapon types and three buffs (owned types can still gain ranks) — bounce weapons (Shockwave, Spark Burst, Chain Zap, Homing Rings), auto weapons (Orbit Rings, Sonic Boom, Flicky Squad), moves (Homing Dash, Air Jump, Ground Pound) and passives. At 0:00 Eggman arrives in the zone's own vehicle and attacks with the zone's projectiles; beating him clears the zone, drops its Chaos Emerald once (seven permanent emerald powers) and offers the next zone's acts (act 2 and 3 harder and richer) or retiring. The Death Egg is Silver Sonic followed by a fleeing Eggman on foot. Elite badniks drop monitors (Eggman's is a screen bomb). Runs bank rings for a camp shop of permanent upgrades, the Sonic 2 title gains a SURVIVORS wordmark and a start-zone picker for zones previous runs reached (with catch-up level-ups), and progress is saved under the engine's save root. Includes a build/package script and ROM-backed tests over every route act. The mod supports solo Sonic or Tails, enforces an arena ceiling, and moves every menu confirmation to Enter/Start without triggering host pause. It completes multipart boss sprites, adds Whisp Queen/Balkiry Ace/Oil Sentinel encounters, enlarges and labels elites, warns before dives, and prevents lost rings from being magnetized or counted as fresh experience; hit-spilled rings fade out within 0.75 seconds. A first boss clear unlocks saved ten-minute and unlimited camp modes alongside the standard five-minute route; ring-pickup chimes are batched during showers while retaining immediate feedback after quiet. Encounters alternate Crossfire, Air Raid and Stampede with warned surges and regroup windows; endless health and wave batches keep escalating. Mod enemies, shots, pickups and growable player projectile arrays have no fixed population ceiling and preserve rewind without consuming native object slots. Fever and invincibility expiry preserve continuous arena/boss music. A Fever meter and current hit-cost readout explain the rebalanced Armor, ring-bank risk and gentler combo rewards. Uncollected ring/monitor rewards expire after a minute. Nearby reward rings, including scattering and magnetized enemy drops, merge across value tiers and consolidate into larger cyan (5+), purple (25+) and red (125+) rings, retaining their combined value while reducing clutter; hit-spilled rings stay separate. Badnik score popups are replaced by actual damage numbers that grow and change from red through orange to pink-red with stronger hits. The unreleased game is branded simply Sonic Survivors. Super Sonic is disabled pending a dedicated Survivors implementation; the seven-emerald damage bonus remains. Menu text uses precomputed compact glyph geometry and cached card descriptions to reduce allocation and drawing overhead. One difficulty curve now drives badnik and boss health, waves, the toll and ring values from the route tier and the arena clock (never total run time), growing faster than linearly so Metropolis onward is meant to be hard; bosses scale with the waves, a crowded arena (160 badniks) turns overflow into elites, and unlimited mode climbs a tier every two minutes. Maxed weapons with their partner buff evolve from treasure chests (twelve evolutions) dropped by bosses, every fourth elite and mid-stage warden squads; zone events (telegraphed rain, swarms, Casino Night's Jackpot) break up each clock; maxed builds take stacking Overdrive. Sonic charges Fever faster and gains more per combo bounce, Tails hovers on a held jump. Twelve upgrades unlock from lifetime milestones, six optional Eggman's Rules raise the bank, and camp gains shop, rules and records pages (records, unlocks, evolutions, bestiary, unlimited times). The ring shop is ten global power-ups in small long-tail steps with rising prices; older saves convert to the same totals. Starting and leaving at once, or leaving before a fatal hit, no longer banks free rings, and a crouched spin-dash charge is no longer an untouchable attack.

- Added [Eggman's Sky](examples/eggmans-sky/README.md), a No Man's Sky-style S3K mod scene starring Dr. Eggman. Planets are loops of terrain remixed from 55 stock acts of every supplied Sonic ROM through the new level kits (runs of layout columns joined only at seams the act already contains, with mismatched cells repaired), each in its own Genesis-quantised palette with a climate, storms, day and night, a parallax sky that preserves the artwork’s band boundaries and scrolls continuously around the planet, procedural flora with six specimens per species and additional fern, rosette, reed, fan, bell-flower and shelf-fungus forms (existing resources and yields), minerals, 83 badnik and animal bodies as fauna with species-correct ROM sprite layouts and animals facing their travel direction, and monitors, Egg Capsules, Starposts, echidna ruins, Giant Rings and wrecks. ROM music and effects play from scene startup, and regular exposed blue-crystal deposits keep launch-fuel ingredients accessible. The jar includes 122 original Alice system announcements, shipped as compact Ogg/Vorbis quality 4 with preserved external WAV masters, composed delivery and metallic/vocoder/retro DAC processing, a bounded priority queue with warning cooldowns and stale-message removal, retries for temporarily unavailable playback, vital warnings acknowledged only after accepted playback, and a persistent separate voice mute setting. Owned patch scenes can play validated audio-manifest SFX by local name through `SceneAudio`, with prepared PCM retained through the session adapter into the final mixer, without numeric mod IDs or runtime network access. The expedition includes pinned recipe and upgrade checklists, capacity-aware batch crafting/refining, protected cargo reserves, remembered menu selections, grouped pickup notices and a browsable planet/species journal. The Egg Mobile hovers, mines with a heat-limited laser (or carves terrain), scans and catalogues species, survives life support and hazard drain, and refines and crafts resources; greedy play summons Flickies, Tails, Sonic, Knuckles and Super Sonic. Space is a software-rendered cockpit with ray-traced planets textured from their own zone blocks, rings, nebulae, asteroids, pirate dogfights and the Tornado; scene vertex uploads avoid waiting for the GPU between sprite batches; Egg Stations trade, install 23 technologies and give missions; a galaxy map warps between star classes, black holes and Giant Rings toward the core (Enter/Start confirm menu selections and warps, while dedicated back/menu keys close them), where seven Chaos Emeralds from ruin-revealed shrines end the expedition and start a new galaxy.

- Added the [Slay the Robotnik example mod](examples/slay-the-robotnik/README.md), a Slay the Spire-style deck-building roguelike played on Sonic 3 & Knuckles as a full-screen mod scene. Sonic (Focus and Combo multi-hits), Tails (Dexterity, discards and Ring Bombs) and Knuckles (Strength) each have a starter deck, a starting relic and 42–43 more cards; 55 relics, 16 item monitors and 22 events round out four acts — Angel Island, Hydrocity and Launch Base as branching 15-floor zone maps with badnik fights, three elites and two bosses each, then a short Sky Sanctuary finale against Mecha Sonic, who returns as Super Mecha Sonic. Every character, badnik, boss, monitor, ring, the Tornado, explosions, music and sounds come from the player's ROM: bosses are assembled from their mapping frames at the original child-object offsets, defeated badniks explode and free a Flicky, and card and relic pictures are one-line text recipes of ROM sprites and drawn effects. Fights, Starposts and capsules are staged on stretches of each act's real level, moving through it as the map climbs, with every character standing on the ground under it. Runs save at every room; the title adds a Compendium and per-hero records. The title screen flies through four zones' real levels behind a Tornado chase, each act opens with its zone's ROM title card, and every event plays out the chosen option with the game's own objects (the slot machine spins the bonus stage's own reels). Fights open with an entrance that names each boss, claimed rings, cards and relics fly to the HUD, and every highlighted card term and HUD readout has a tip. The engine suite builds, validates and runs the example's tests and opens every fight and event through the scene against S3K.

- Added [Starpost Valley](examples/starpost-valley/README.md), a Stardew Valley-style farming and life game combining a belt-view farm mod scene with real Sonic 3 & Knuckles acts, with Sonic 1 required for Green Hill and Sonic 2 optional. After the credits, the farmer (Sonic, Tails or Knuckles, each farming and moving differently; the other two become neighbours) restores a badnik-churned Green Hill through one complete year. The farm is a belt-view field in front of Green Hill's own cliffs and folds at the Star Post gate into a real S3K act re-encoded from Green Hill, with native Sonic/Tails/Knuckles movement, springs, slopes, the loop and a town built from Green Hill's pixels. The valley, forty Marble/Labyrinth/Scrap Brain Ruins chambers, Race, Ring Hunt, snowboard and Waterfall Lake use the engine’s real player; a scene↔act round trip retains the farm, save and activity state across doors, menus and return visits. Scenes retain cutscenes, invitations, results, stationary booths and the belt-view farm pond. Outdoor acts reuse the farm’s seasonal and evening/night palettes, snow and aurora; the Ruins retain their zone palettes. ROM music plays at 65% pace with its pitch preserved and SFX at normal speed, including scene-to-act visits; farmer selection includes Tails’s animated tail layer. Chores spend Momentum, which laps of the farm loop, rings and springs refill (classic stamina is an option); the spin dash tills, a shield-fed watering can tips and pours onto the tile in front of the farmer with a matching hotbar sprite, the Fire Shield breaks rocks, and Buzz Bomber waterers and the Caterkiller Crawler automate the field. Thirteen neighbours keep schedules, take gifts, write letters and play 42 heart events, the animals speaking in pictures until Tails's translator, with Partners in place of romance. The Marble Ruins are forty chambers remixed daily from Marble, Labyrinth and Scrap Brain Zones and screened by an offline generation model and exercised by native player routes. Fishing uses a Bubble Bar against Sonic 1's submerged badniks; coops, pens, artisan machines and a Flicky Roost house the freed animals, whose number grows the valley. Fruit trees, sneaker tiers that widen the spin dash, Tails's Workshop Museum with its badnik bestiary, eight festivals, the Signpost Board's requests, the Great Capsule's six bundle chambers or Robomart, skills with professions, weather, seasons and the Signpost Spin evaluation complete the year. The world, characters, title cards and HUD digits come from the player's ROMs, with small original farming/item pictures; the soundtrack plays the games' own songs with sound effects over them. Engine additions include the scene↔act launch/return bridge and S1 ROM object-art intake; the engine suite builds, validates and runs the example’s creator tests and native act routes against S3K.

- Added [Three Islands](examples/three-islands/README.md), a story-driven S3K mod scene JRPG with fully turn-based (full-wait) battles across South Island (Sonic 1), West Side Island (Sonic 2), Angel Island and the Death Egg (S3K). The mod opens at a gold-lettered title with three framed island vistas, the party and animated Chaos Emeralds; fixed vertical crops prevent sky repeats beneath the scenery. New Game and Continue lead into the journey; physical trails connect ten explorable areas whose onward routes open after freeing their anchors, replacing the level-selection map and village hub. Dialogue, shops and fully turn-based battles take place over the same field. Green Hill reuses its ROM grass, checkerboard cliffs, palms, plants and water in a larger overworld with an optional bell garden puzzle, a flooded orchard and local records. Exploration has no objective banner or undiscovered quest markers; the journal recalls the party's purpose and found evidence, and grounded character dialogue leaves room for deduction. Records, control settings and character knowledge follow the playable route, without unexplained prophecies. Local dungeon discoveries enable relays that remove guardian shields; defeating the guardians opens visible rift barriers on the onward trails. All ten shrine landmarks open into distinct explorable dungeons, with solid exterior buildings in different districts. Authored outdoor routes wind through branch controls and blocked crossings; interiors grow from seven to seventeen rooms, introducing winches, compass sighting dials, lens and brazier sequences, pressure circuits, ancestral seals and archive chronology puzzles; outdoor crossings mix repairs, ordered shutdowns, gauge circuits and relay dials. Marble, Labyrinth (Star Light’s observatory), Scrap Brain (Spring Yard’s freight catacombs), Mystic Cave, Hydrocity, Lava Reef and machinery ROM art give the interiors their own rooms, guarded gates, supplies and story discoveries; the missing Flicky follows Sonic back out of the seaside shrine. Indoor checkpoints, persistent fights, one-time rewards and revisitable entrances support continuing the journey. Visible patrols can be avoided; discoveries persist and can be revisited in a journal. Forty additional traveller, discovery and camp scenes develop the mystery and party relationships; discoveries and boss milestones advance underlevelled heroes without grinding. Repeatable free rests, post-battle recovery, gentler enemy damage and reliable escape reduce attrition. Battles retain the encounter's field renderer and camera, dimmed beneath a staged left/right formation. Each island's heroes use that game's own art. Tails and Knuckles join the story, and Dual and Triple Techs share partners' turns. Shield elements and weaknesses, monitor items, villages with rest and shop, multi-part ROM bosses, seven Chaos Emeralds and Super Sonic complete the adventure. Outdoor areas and dungeons use their own ROM themes, including Marble Zone in the seaside shrine. Live Sonic 1/2 playback starts immediately after loading saves and follows native intros and loops without one-minute restarts or placeholder switches. Chapters whose ROM is missing are skipped. Green Hill water uses full-size ROM water reflections and their original four-step colour animation. Fire, Water and Lightning hits can burn, soak or stun; healing cures them. Every boss has a signature mechanic and mid-fight lines: Bomb King and Coconuts Chief summon helpers, bombs explode when their fuses run out, the Grabber Queen seizes a hero, Silver Sonic's spikes rebound plain blows, Screw Mobile plants depth charges, Beam Rocket's barrier yields only to techs, Eggman, Knuckles and Mecha Sonic power up at half health, and the Convergence Engine's weakness cycles each round. Twenty-three accessories (one per hero) come from Pocky's island-by-island stock, a treasure room in every dungeon, gifts for rescuing ten lost animals hidden in new side branches, and four optional Rift Echo duels against pairs of remembered bosses. Green Hill and Star Light are rebuilt as hand-drawn tile maps: Green Hill's jungle trails, river bridge, shrine plateau, orchard and switchbacks, and Star Light's night highways and catwalks over the city with its street lamps, rails and skyline, both with badniks holding narrow passes, mid-route Starposts and corner sliding; Star Light's observatory no longer blocks its route with invisible walls. Other outdoor routes read as raised terraces with ROM cliff faces and ROM backgrounds driven by the engine’s native scanline scroll handlers, with layered parallax, ripples and complete viewport coverage; Emerald Hill keeps its native vertical framing and Death Egg its fixed widescreen view. Late-game fights against a full trio last about three rounds instead of two.

- Added [Flappy Tails](examples/flappy-tails/README.md), an S3K mod scene in which Tails flaps through Angel Island, burning Angel Island, Hydrocity, Launch Base and Sky Sanctuary on a line-for-line port of the ROM's flight routine (`Tails_Move_FlySwim`). Pillars and ground are cut from each act's own floor, zones change behind their real title cards and music, badniks hover in the later zones, and a full tour turns Tails Super (invincibility theme, Flickies, pillars bursting on contact). CLASSIC and SONIC RULES modes (rings absorb hits and scatter in the ROM's ring-loss pattern; flight tires after the ROM's eight seconds), practice from any reached zone, medals, records, a live attract mode and title-card lettering recovered from every zone's name card complete it; a planning autopilot proves every seeded course clearable and flies tests, attract mode and captures. It is the finished game of a new nine-part [Flappy Tails tutorial](docs/modding/guides/flappy-tails/README.md), whose first part chooses between changing the native game (the maintained native sample) and replacing the screen with a scene, with buildable, engine-tested checkpoints for the scene path; `examples/build_example.py` builds nested tutorial checkpoints, and `launch-flappy-tails.sh` builds and plays the game.

- Added [Starfall Frontier](examples/starfall-frontier/README.md), a Terraria inspired S3K mod scene with a seeded destructible world, whole-tree harvesting, resource gathering, crafting and building, equipment progression, a seven-stage quest journal, three shrine wardens and a beacon restoration objective. Player-selectable Small, Medium and Large frontiers (4,096–16,384 tiles wide and 384 deep), with proportional biome widths and shrine spacing, eleven S3K-inspired regions with their own badniks, underground Hydrocity, Sandopolis tombs, Lava Reef and Hidden Palace, build-accessible Sky Sanctuary ruins, native ROM terrain and location music, ROM backgrounds throughout with smooth background fades, palms, badniks that face their target through recoil, monitors, starposts and emeralds, Sonic momentum and variable jumps with firmer friction and bounce attacks, a pannable exploration atlas, mouse and controller menus, initialized ROM music/effects at startup, and persistent worlds with chunked backup recovery and original-world compatibility complete the solo adventure.

- Added [Robotnik Tower Defence](examples/robotnik-tower-defense/README.md), an S3K mod scene in which Robotnik defends his base door from 15 waves of unionised Flickies. Six badnik defenses provide direct fire, ground splash, anti-air, armor piercing, slowing and chain attacks; towers can be upgraded or sold, the door repaired between waves, and an emergency bomb repels breaches. Couriers, shield carriers, flyers, organisers and saboteurs introduce coordinated wave tactics. ROM-backed Launch Base art and audio, mouse/pad controls, pause, help and saved records complete the game without changing stock gameplay.

- Added [hello-scene](examples/hello-scene/README.md), a two-class starter for mod scenes (Sonic collecting rings over Angel Island's background), and `examples/build_example.py`, which builds and runs any example. Mod launch shortcuts compile the engine and generate its classpath using only the checkout's build lock, so tests in other checkouts do not delay them. `ggfmod sprites` draws every frame of a ROM sprite request, or a playable character, into one numbered PNG. In the engine's tests, `ExampleModHarness` and `ExampleModCapture` build any example scene from source and record it headless to PNG, MP4 and WAV.

- **Sitar Hero:** added a source-first full-song rhythm example combining the
  supplied Sonic 1, 2 and 3 & Knuckles ROM libraries into 79 songs. Looping performances last
  two complete loops or two minutes, whichever is longer; non-looping ending/credits
  tracks play to their natural stops. Career follows Sonic worlds with main-act
  setlists, shared tour progress, optional side gigs and satirical authored
  intermissions on Robotnik's shared tour. Quick play, no-fail practice,
  four difficulties, local co-op/score duels and direct-connect peer matches share
  seven cosmetic performers and four real FM/PSG/DAC parts. Mistakes mute the
  selected part and produce restrained, instrument-specific ROM-timbre fumbles;
  local and online co-op retain a successful partner's part, while sequenced,
  batched peer cues remain independent of scoring and cannot cross a rematch.
  Generic scene part cues are bounded, pitch-gliding and pause/stop-owned.
  ROM-pixel arms and
  hands layer over instruments and move on successful judgments with sound waves.
  Charts preserve native attacks, voice handoffs and medley tempo changes while
  curating density, chords, sustains, HOPO and Star Power. Independent player
  controls/calibration, records, help, mouse menus, lefty mode, reduced flashes
  and highway speed complete the arcade flow. Candidate scene APIs provide
  timestamped physical input, owner-scoped peer transport and consumed-audio-clock
  ROM playback. Full and selected-part synthesis have cancellable background jobs
  with progress, a ten-minute cap and a 256 MiB combined PCM budget. Runtime
  character, stage and music assets come from the user's ROMs. The main menu stages
  the installed band rehearsing on a rotating real act with parallax and stage
  lights. Performers stand on each act's floor, intermissions stage both speakers
  on the world's level, and Sonic 3 & Knuckles zone themes load behind the ROM's
  own title card. Screens ease in, highlights glide, the count-in follows the audio
  clock, and results tally like an act clear. Menus play the running game's own
  theme, cursor, ring, giant-ring, tally and act-clear sounds; the driver is
  silenced before a song starts. The README now walks through the source in
  reading order with recipes.

### Time Attack and multiplayer

- **Time Attack bundled mod:** solo ghosts, retries and LAN/master-server racing now
  ship together in an enabled-by-default mod, launched through **Extras > Time Attack**.
  Disable it in Mods. Existing ghosts and identities keep their locations; the mod
  imports legacy retry and network preferences once and owns its settings thereafter.
  Reusable networking remains in the engine for mods to use; dedicated/master servers
  and verification tools build separately from `racing/server`. Normal native builds
  do not load the bundled code mod.

- **Time Attack master servers by host name:** Settings > Master server starts on
  **Build default**, which follows the running build's own master
  (`multi-dev.openggf.com` for develop, `multi-next.openggf.com` for next,
  `multi.openggf.com` for releases) and is never saved into the profile. **Custom**
  accepts a bare host name or a full `wss://` URL (now including bracketed IPv6), and
  **Off** keeps LAN play only. A host name's port comes from its `_openggf._tcp` DNS
  SRV record; the record must name the same host, so certificate checks are unchanged
  and there is no guessed fallback port. A missing record, a failed lookup and a
  record naming another host each give their own message. Recording uploads use the
  port the master connection was admitted on. Native packages include the JDK DNS
  provider this needs.

- **Time Attack multiplayer security:** identity proofs bind the complete join
  intent to the master, direct-room or relay-room role and observed TLS
  certificate. Remote browser, join and recording endpoints require TLS;
  plaintext and insecure TLS trust are restricted to literal-loopback development,
  including recording uploads. Master challenge solving is serial, cancellable and
  limited to 24 bits and 30 seconds, with one coalesced pending proof write under
  transport backpressure.
  Protocol 8 binds listed direct and relayed admission to one-use broker grants,
  retires direct routes before fallback or rejoin, owns direct registration through
  activation and cleanup, and freezes each participant's
  character and the voted round configuration in the host's launch. Bounded
  connection writers and owner mailboxes release accepted work on retirement;
  verifier infrastructure faults void results without sanctions. Completed
  attempts publish replay-identical ghost bytes, direct hosts use their local
  listener for broker-granted self-join, and congested status, heartbeat and
  cosmetic lobby traffic no longer ends a healthy room or connection. Shared
  transport saturation backpressures an unrelated peer through a globally
  charged 8 MiB deferred reserve inside the 64 MiB explicitly reserved work
  limit; physical writes expire after two seconds without completion. Loaded-track
  bounds use a reserved host control slot; master-link controls, broker/room
  mailbox-full verification handoffs and verified verdicts retry after mailboxes
  drain instead of losing the guest or verified result. Broker loss hides
  unusable manual invites.
  Private rooms are unlisted and password-protected across direct and relayed
  joins. Six-character invite codes work for master and player-hosted rooms,
  expire after ten minutes and limit lookups to five per minute per identity
  and source IP. Invites start masked, support an eye and explicit Copy, and
  let hosts disable member sharing by rotating the code. Passwords and complete
  invites stay out of saved settings, automatic exports and logs. Scene-owned
  clipboard writing and focus-loss notifications support the same privacy
  controls in other mods. Losing focus or minimizing withdraws a presented scene,
  including a scene over gameplay, with one neutral frame while updates pause.
  Ordinary gameplay keeps its paused picture.
  Separate master/relay and verifier Docker images support individual containers
  or a single-host Compose deployment, with TLS, file-backed secrets, persistent
  identities, master readiness and verifier startup retries and bounded shutdown.
  The same verifier image can run on another machine; its engine build must match
  the clients it verifies. See the [deployment guide](racing/deploy/README.md).
  Time Attack opens directly from a fresh boot, retains its lobby and connections
  while launching a level, and resumes its owning scene after a run or load failure.
  Every visible multiplayer ghost shows its player name above the sprite,
  including ghosts beyond the four nearest racers. Nameplates draw after the world
  so deferred level rendering cannot overwrite them, on a small backing plate;
  bunched names stack instead of overlapping and fade with their ghost. The in-run HUD is a narrower, translucent right-hand column that
  shrinks to its text: one rival ahead and one behind instead of three, and a slim
  progress strip with a finish flag beside the lives counter. `CompactFont` gains
  `@`, `$` and `#`, which are common in player names.

- **Time Attack communication:** Full Chat includes typed and Quick Chat, with
  canned messages available by keyboard and controller in lobbies and during
  races. Controller activation opens an eight-way radial chooser with direct
  right-stick aiming and right-trigger sending; keyboard activation keeps the
  numbered list. Movement and jump stay available. Room owners can choose
  Quick Chat or Off, while public relay servers default to a Quick Chat maximum.
  Personal settings offer default-on local profanity censorship using a small
  multilingual whole-word dictionary and an option to hide all player messages.
  Connection and race notices remain visible. Quick Chat uses semantic phrase IDs
  and host-supplied track context; its wording is English initially. Protocol 6
  requires clients, hosts and the master to be upgraded together.

- **Time Attack player names and looks:** names can be written in any left-to-right
  script (accented Latin, Vietnamese, Greek, Cyrillic, Japanese, Chinese, Korean and
  more) and are drawn with bundled Fusion Pixel 10px and GNU Unifont glyphs in the
  lobby, browser, chat, standings, HUD and above ghosts. Text fields accept typed
  characters from any keyboard layout or input method. A new player chooses a name
  before their identity is created, and can rename later in Settings or from the
  lobby without a new identity. A look editor offers ten presets or a custom palette:
  pick a base colour (the name's colour) and edit the character's Mega Drive colours.
  Others see the ghost in those colours, and the player's own character uses them
  too; rings, the HUD and other sprites keep their stock colours, and Super palette
  cycling still shows. Only the colours that differ from the ROM palette are sent
  when joining. Hosts reject names with hidden or direction-changing characters.
  Protocol version 4; right-to-left reordering and script shaping are not yet done.

- **Time Attack lobby level:** a room's lobby is now a short, fenced level of its own
  game that every player spawns into behind the lobby menu: the Hidden Palace arena
  where Knuckles fights Sonic (S3K, left of the fight trigger so the fight never
  starts), the start of Green Hill 1 (S1) or Emerald Hill 1 (S2), so nobody needs a ROM
  the room does not race. Players ready up from the menu; once ready, Start or Enter
  hides the menu and hands the controller to the character, and pressing it again
  brings the menu back. The menu is a set of cards in the race HUD's style (the room
  and next track, the players with ready boxes and characters, the chat, vote or
  podium, and the menu with how many players the round still waits for), with the level moving
  around them; the stock HUD and ghost nameplates are hidden under it. The vote shows
  all three tracks. Other players walk the lobby as named ghosts in their looks
  (presence is streamed between rounds only, and relay rooms send each player its
  nearest eight). The room owner chooses whether the round start waits for everyone
  to be ready: always, never, or (the default) only with five or fewer players
  present; rounds still start only when the owner starts them. A round start hands
  the lobby level straight to the race through one black load, and the lobby level
  returns after the round. A lobby death reloads at once at the lobby spawn, with no
  lives or game over. Ghosts and their nameplates now also draw in S3K's
  sprite-masking zones (Sandopolis, Lava Reef, Sky Sanctuary and Hidden Palace), in
  races and in the lobby. They retain their chosen colours and blend between
  native sprites without consuming hardware sprite capacity or opaque coverage.
  Protocol version 5. Mods get `RunArea` (a fenced spawn for a run),
  `RunHandle.holdControls`, `RunHandle.setLevelCovered` and `SceneGameplay.launchBehind`
  (a run that plays behind its still-active scene).

- **Time Attack multiple attempts:** players can submit several runs inside one
  round window, and the best valid run counts. While the window is open a finish's
  results tally is followed by a fresh attempt; the retry key still restarts mid-run.
  Retried attempts and late joiners no longer trip the host's pacing check, a retry
  after the deadline or a second retry press during the reload no longer aborts the
  run, and an unfinished run returns to the lobby when the host closes its round.
  Verified rooms keep superseded claims as fallbacks, so a failed or voided later run
  no longer erases an earlier run that passed. Deadline clock skew no longer kicks
  players, a player pushed out of the top ten sees its new rank, finished and
  reloading racers stop holding nearby ghost slots and show their state in the
  roster, and verifiers keep polling after a failed cycle. Mods can opt into the
  same act-completion retry through `RunHost.retryOnActCompletion()`.
  Hosted Sonic 1 mid-run retries keep gameplay moving beneath the reload fade;
  retries after act completion retain their gameplay hold.
  Round-end votes can draw from a custom pool: the room owner starts from the game's
  default pool, adds or removes any act that ends in a results tally (signpost acts
  and boss/capsule acts, never special or bonus stages), names it and chooses it when
  hosting a LAN or master-listed room. Pools hold 3-32 acts of the room's own game.
  The master and the host reject a malformed, foreign or ineligible act with the reason.
  S1 and S2 boss acts now finish a timed run at the capsule switch, where the ROM clock stops.
  The HUD's TIME can show hundredths of a second (`0:00.00`), as Sonic CD does, in
  every game's own HUD digits: `display.hudTimeHundredths` turns it on for normal play,
  and runs can ask for it and show their own clock there (`RunHandle.setHudTime`,
  `setHudTimeHundredths`). Time Attack uses both, so the game's TIME is the attempt's
  time and the run panel no longer repeats it. The optional digits use the native
  HUD's sprite budget and clipping, with matching bounds for compact HUD effects;
  the default four-character clock keeps its original footprint. Hosted runs can
  also override native HUD rows with `RunHandle.setHudProfile` and align compact
  overlays using `LevelOverlayCanvas.hudOriginX()`, preserving each game's artwork
  and HUD anchor setting; clearing the override or ending the run restores the
  zone's layout.
  Time Attack uses it: TIME and RINGS on the top rows with no score or lives, its best
  time and split under them, and the room panel in the top-right corner. Its progress bar
  spans the screen with every racer in their colour at their true place, racers at the
  same place stacked so a column counts them, and flashes and shakes a racer's segment
  while they are hurt (`GhostFrame.hurt`, `PlayerPose.hurt`, carried in the ghost
  frame's spare flag bit).
  Time Attack has no lives counter and unlimited tries: after a death (skippable
  with jump after half a second) the act reloads at once from its start with no
  title card, then a 3-2-1 countdown over the visible level holds control before a
  fresh attempt. In S3K that countdown and a room's round-start countdown sound like
  Death Egg Zone's tunnel launcher: a ready beep on each of 3, 2 and 1, and its launch
  sound on GO. Angel Island 1 starts on the beach without the plane intro.
  `GameplayRunPolicy.withFastRestarts()` (`DeathRestart.FAST_FROM_START`, hidden
  lives counter), `withoutZoneIntros()`, `RunLevelStart.deathRestart()`,
  `RunInput.levelFadingIn()` and `RunHost.holdsPlayerInput()` give mods the same.

- **Multiplayer services:** The master recording/verifier API bounds outstanding
  requests and copied bodies across connections, closes overlapping HTTP pipelines,
  bounds rejection writes before HTTP aggregation, discards further input during
  terminal rejection writes, and releases queued work
  on disconnect or timeout even during executor shutdown. Netty and Jackson use their
  patched 4.1.139.Final and 2.22.4 releases. Control JSON rejects fractional or
  overflowing integer values and trailing roots. Fallback-tunnel inner bounds,
  receiver queues above 1 MiB, and disconnect/attach races isolate the affected
  guest without retiring its host's shared broker link. Reproducible adversarial
  state tests and a local socket stress tool exercise admission, room capacity,
  varied ghost traffic and connection cleanup.
  Creation-proof reconnect stamps and one-use room-attach grants have explicit
  expiry and cardinality bounds.
  Capacity reports count completed work and rejections separately from latency and
  colocated process costs. A multi-process scale harness now isolates one to four
  full 256-player relay rooms from their load generators and records room/broker
  latency, traffic, CPU, memory and lifecycle evidence. Recording quota checks,
  verifier input reads and client proof solving avoid scale-dependent allocation
  or directory scans; verifier heartbeats preserve long-running job authorization,
  and live SQLite sanction/identity cleanup predicates are indexed. Protocol v3
  replaces repeated whole-room membership snapshots with incremental joins/leaves,
  while packet-local ghost deltas reduce steady relay traffic without cross-packet
  state; at four full rooms these cut admission traffic 98.5% and steady traffic
  about 24%. Valid attempts at the published countdown boundary are
  admitted even when the scheduled host tick has not run yet. Multiplayer race
  panels now preserve the stock HUD and act-completion card, with a compact
  nearby-player list. The capacity recording places the live player mid-pack
  and checks ghosts leaving and returning to its viewport. Nearby ghost
  routing bounds selected-sender work and avoids redundant copies and standings
  sorting. Slow-consumer removal remains safe within an aggregation tick.
  Valid rapid-attempt backlogs split into ordered packets within both the binary
  and fallback-envelope caps. Fallback host downlink quotas are isolated per
  guest, and coalesced broker fanout is bounded by frame count and retained bytes,
  so healthy multi-tunnel fanout no longer retires the shared broker link and an
  oversized unclassified backlog has a fixed memory ceiling.

- Add an opt-in `external-master` test profile for live master discovery, TLS, player admission and room browsing; normal test runs remain offline.

### Trace replay, parity and development tools

- Trace segments read the recorder's `level_entry_placement_source`, so a
  standalone boot takes the ROM's own fresh or return entry branch: S3K
  start-table segments use the post-spawn camera derivation (including the
  Tails-alone +4), saved-placement returns skip zone intros, and KiS2 EHZ1 keeps
  its native start history. The three S3K campaign chains also record their
  movie endpoint. Standalone LRZ/DEZ continuation segments retain their separately
  recorded Act3 counter bank until the destination screen event consumes it;
  fresh, Saved and Saved2 entries remain distinct. Fixtures without the entry
  observation keep their previous setup.

- Fresh S3K trace segments derive the entry camera from the ROM start-location
  entry, as `Get_LevelSizeStart` does, rather than from a body that load-time
  objects (the DDZ fly-in, the shrine) have already moved. Fresh Level assembly
  then runs its native initial camera movement before zone setup; the sanctuary
  starts at `$326`, while its special-stage results backdrop retains `$240`.
  Actorless resource loads retain their camera state; checkpoint loads retain
  the same native initial movement before normal gameplay bounds take effect.

- Standalone S3K run segments enter with the run state the ROM carried in:
  rings, Chaos and Super Emerald progress, used giant rings, star-post progress,
  Player 1's shield and path bits, and, from the recorded saved blocks, the
  star-post checkpoint, camera bounds, water level, timer and lives. One segment-entry import reads them from the run manifest and
  the segment's recorded entry row; nothing is written after entry. Standalone
  special stages select their stage and Chaos or Super layout set through the
  ROM's own search instead of defaulting to the Chaos set. The S3K emerald
  ledger lives with the game's special-stage trace support, keeping shared
  replay packages free of game-specific classes.
  MHZ switch doors detach from their retired buttons before pending child
  cleanup, keeping rewind references valid on carried checkpoint entries.
  Special-stage returns use their own saved camera bound for the first snap
  while retaining the distinct star-post bound for a later death.
  Recorded standalone water entries restore the saved height while retaining
  the freshly initialized movement target, as the ROM's saved-block load does.

- S3K bonus-return chain replay retains the first destination gameplay row,
  preventing a late enemy-art batch from stranding the KosM queue. FIFO overflow
  diagnostics now identify pending archive jobs, direct children and submitters.

- Trace entry placement distinguishes native level boots from intentional
  positioned entries. Explicit native loads retain their ROM start history;
  positioned S3K camera focus follows declared SpawnPlayers ordering. Unknown
  entry kinds retain their existing placement, history and camera behavior.
  Intentional re-anchoring moves the whole team without refilling native history,
  and initial Tails assembly preserves
  placement already assigned by native objects.

- Held transition fades continue servicing VBlank work and the native clock
  after trace comparison closes, while preserving movie-row ownership. Bonus entry
  also reserves its fade-completion iteration, preventing premature title-loop
  resource preparation.

- **Trace playback handoffs:** Visual run replay keeps its boundary observer
  active between segments after closing the previous comparison payload, so
  special-stage requests are not missed at a giant-ring transition. Recorded S3K
  fresh loads retain the source's recorded terrain-readiness ownership and use real
  resumable decoding, placement, object and presentation phases. A dedicated,
  matched timing stream can pace those phases without importing gameplay state;
  incomplete or mismatched observations fail the replay.
  Fresh bonus cards also reclaim child updates from a preceding in-level title.

- **Trace loading:** compressed hardware-timing streams use the same strict
  schema checks as plain streams; damaged and truncated gzip inputs are rejected.

- **Stock parity measurement foundation:** A versioned, explicitly partial
  S1/S2/S3K obligation catalogue and on-demand report distinguish declared
  coverage, observed disagreement and unresolved scope. Source/contract/ROM
  identity, required observation extents and producer authority protect the
  arithmetic. A ROM-backed inventory preserves 801 original dispatch entries as
  unresolved semantic gaps. The first producer measures the existing S1 GHZ1,
  S2 EHZ1 and S3K AIZ-to-HCZ-entry trace gates with pinned movies and completed
  invocation ownership. These gates do not certify whole games, visuals or every
  entity. The
  [measurement design](docs/architecture/designs/2026-10-29-quantitative-stock-parity.md)
  defines native trace, visual, entity and audio producer integration work.

- **TAS trick regressions:** Focused tests exercise native movement, object-contact
  and special-stage behaviors described in published Sonic TAS notes. The
  [representative verdict record](docs/architecture/validation/2026-10-26-tas-trick-verdicts.md)
  separates supported named tricks from executed engine limitations, with
  controls, original publication links and explicit route boundaries. Passing
  limitation checks document incompatibilities without treating them as repairs.
  An opt-in [test reel tool](tools/test_reel/README.md) records actual fixture
  frames and stitches labelled clips, with one-frame editorial cards for reviewed
  non-drawable cases and strict execution/source checks for complete compilations.
  Normal tests remain recording-free. The separate
  [repair assessment](docs/architecture/validation/2026-10-26-tas-supportability.md)
  groups unsupported mechanisms by feasible repair scope and complexity. Its
  bounded low/medium repairs restore unsigned level bounds, SOZ slope response,
  S1 offscreen solid omission, S2 launcher and ARZ ordering, Blue Sphere boot
  input, and S3K boss/slot lifetime behavior, with restore-forward controls.
  Further [engine support](docs/architecture/validation/2026-10-28-tas-engine-support.md)
  keeps off-screen sidekicks on the correct collision path when horizontal
  placement entries retire, restoring Sandopolis ceiling attachment. It also
  restores paused clock/rewind phase, typed Reset/Power lifecycle, native ORT
  recovery, ordered S2 art-slot reuse and the P2 intro input/palette consequence.
  Verified original-cartridge resources now serve standalone SK title/MHZ startup
  and the native S3 Competition host, with both-human arena replay and bounded
  results/restart consumers. These are named mechanism proofs, not complete TAS
  movie or campaign synchronization. KosM adjacent-memory corruption and exact
  halfpipe hardware lag remain explicitly outside the engine support boundary.
  Historical reel footage and limitation evidence retain their original source
  identities; the [accuracy repair record](docs/architecture/validation/2026-10-26-tas-accuracy-repairs.md)
  preserves the earlier low/medium stage.

- **Local ROM qualification:** Release preparation can bind the existing bounded
  AIZ1/HCZ1 route pilot to a reviewed candidate commit, reject changed inputs and
  require every named case to execute without skips. Original ROMs remain local
  or private; hosted builds and automatic publication retain their existing flow.

- **Plane opacity probe:** a development tool answers, from the decoded ROM layout
  alone, whether the plane behind another can show through at a coordinate. Where a
  before/after frame capture cannot separate "wrong pixels drawn" from "no pixels
  reachable" -- both look byte-identical -- this separates them. It settled why Lava
  Reef Act 1's dome background change produced no visible difference: that act's
  foreground is opaque across the whole dome. No gameplay or rendering behavior changes.

### Reliability and runtime architecture

- **Recording, editor and service reliability:** input movies keep a single recording
  control tick and block rewind while recording; malformed metadata stays isolated,
  and older movies with missing engine identity remain playable. ZIP movie detection
  follows file contents, live rewind preserves completed ticks across gamepad pause,
  and decimal-valued standalone saves retain valid checksums. Time Attack imports
  ghosts for the selected act before applying its race limit and shows the finish
  delta against the previous best. Editor painting
  follows signed vertical camera coordinates, blank projects reserve empty terrain
  before spare slots, and separate ring layouts support their full height. Stereo
  creator sounds replay correctly, PNG imports retain transparency keys, legacy
  save deletion cannot restore older data, and Mod Manager reevaluates freed budgets.
  Multiplayer retains verified bests after failed improvements, refreshes podiums
  after verdicts, preserves result slot ownership, restores far-player positions
  after near streams expire, supplies current votes and podiums to late joiners,
  joins authenticated IPv6 hosts, and retains per-address connection limits during
  concurrent turnover. Null
  configuration files recover safely, legacy Start bindings migrate, and capture
  codec placeholders expand into separate ffmpeg arguments. Gameplay videos use
  only their current capture's frames and retain the exact cadence of strided captures.
  Trace video output honors the configured container, including MP4.
  Key names resolve consistently across locales, legacy keypad bindings keep their
  physical keys, and binding capture accepts modifier taps alongside chords.
  Editor composition tools follow layer switches and adjust the selected cell's
  collision profile. Full-window Kosinski backreferences decode correctly, and
  split DPLC sprite pieces retain their flipped tile positions. Creator starter
  IDs produce valid Java names and YAML strings, and packaged mod bytes stay
  stable across host time zones.

- **Runtime ownership:** A loop-lifetime suspended visit keeps admission, picture
  readiness, parking, saved state, resume and retirement under one authority;
  the title keeps its card, textures and zoom presentation. Live rewind retains
  its gameplay owner and outgoing
  input, audio and fade cleanup through suspension and replacement. Gameplay
  construction releases acquired owners when a later provider fails. Reentrant
  clock binding retains the newest installation and its carried count. Retired
  level managers cannot clear a replacement session's palette, effect or render-mode
  registries, including when the same world is reopened with fresh managers. Native lock-on
  rewind publications keep their selected module owner across fresh service
  incarnations, while both exposed and suppressed native services remain protected
  from creator capture. Destination
  history starts after deferred title-card assembly, and replay cannot mutate a
  replacement owner's history. Floors and checkpoints include completed frame
  publication; inactive outgoing worlds cannot seed new history. Recorded playback
  releases only the input handler and raw snapshot it published, preserving a
  replacement driver's first input. Pending ending fades complete before exit
  cleanup. Sonic 1 palette fades retain prepared player art; full loads clear
  unpublished readiness, and results enter the next-level routine on the following
  object pass. Audio's private
  manager-lifetime source owner keeps base/donor publication, generations, binding restoration and
  rollback together, preserving immutable tuples, callback ordering and partial
  reset effects while session-owned audio resources retain their own lifetime.
  A narrow logical publication owner keeps fallback ring state, snapshot sampling,
  eager rollback and fixed held ledger publication together, preserving current
  collaborator reads and existing partial effects across producer replacement.
  A permanent per-manager level-music admission owner keeps both scope counts,
  request-category decisions and token release together, preserving manager
  locking, callback order and scope lifetimes across resets and replacement.
  A private diagnostic installation owner keeps complete observer installation,
  rollback, lease ownership and reset policy together, preserving live target
  sampling, manager locking and existing reentrant partial effects. A permanent
  native-mutator binding owner keeps publication, displacement, exact release and
  manager-wide feedback exclusion together, preserving per-token caller/thread
  admission and callback/reset ordering.
  Level and special-stage setup prepare the selected ROM and sound profile in one
  transaction; identical selections retain the running presentation and live capture.
  Terminal streamed-music cleanup releases presentation slots without recapturing
  retired cursors, while ordinary reset commands retain transactional rollback.
  Sonic 1 and Sonic 2 selected-slot images use the shared data-select
  converter and payload validators; Sonic 3 & Knuckles keeps explicit host ROM
  donation while consuming the same patterns, palette and mapping contract.
  One loop-lifetime special-stage lifecycle now owns entry presentation, retained
  provider identity, captured results and return completion through the existing
  virtual controller seams. Shared rewind admission stays in the game loop; a
  loop-lifetime title owner keeps its lazy provider binding, presentation, locks
  and complete release sequence together through the existing entry and rendering
  seams, preserving input timing and native destination assembly.
  Architecture ratchets measure the dispatcher bodies behind profiling wrappers,
  reject stale rewind and dependency allowances, and identify raw lifecycle calls
  by caller rather than a shared total. Shared menu animation and execution-time
  graphics injection close the remaining menu/framework seams. Ending provider
  state, credits-demo execution and ending cleanup share one session owner while
  the game loop retains frame admission. Bonus visits likewise keep admission,
  provider publication, deferred assembly and return restoration in one loop-owned
  session, preserving the shared frame and title-card boundaries. Each graphics
  host owns its complete palette texture, publication, fade and residency lifetime, preserving CPU bank
  allocation and immediate rewind redraws. SAT replay preserves default-framebuffer
  stencil contents independently of the host depth format, including 32-bit depth.
  Sampler-state capture uses an OpenGL 4.1-compatible query so macOS level entry
  does not fail with an invalid-enum error during SAT replay.
  SAT replay uses a
  graphics-owned geometry value and one tile-expansion implementation while
  preserving the existing candidate Mod API submission signature. Captured sprites
  keep their palette masks, coloured ghost style and constrained shadow effect
  when a scene is copied. Stock title
  previews use detached game-owned providers, preserving animated pixels and
  catalogue ROM views without consulting a suspended game's live services.
  Title-card mapping and drawing now share one domain owner. Event and object-service
  bridge allowances pin callers and call counts, and special-stage rewind coverage
  follows the registered provider through replacement and retirement. Standalone
  provider failures, including object-art and bonus callbacks, stop the failed frame
  through the creator recovery boundary. Current-level music lookup preserves creator
  aborts while retaining ordinary lookup fallbacks. Object ROM reads follow their
  owning world's source, and world metadata writes remain with the engine lifecycle.
  Preparation retirement waits for its decoder before releasing ROM resources.
  Initial object dispatch stops when a callback replaces or restores its owners.
  Its cleanup preserves restored standing state, and valid successor operations
  remain usable without quarantining creator code for engine cancellation.
  Rewind seeks, backward steps and optional
  audits refuse fade snapshots whose completion callbacks cannot be restored.
  Standalone audio callers supply their selected profile to the reusable presentation host;
  desktop sound-test composition lives with the developer tools. SMPS backends
  keep their explicitly supplied restore-request destination for their usable
  lifetime, including later source construction after engine-root reconfiguration.
  Production backends also retain their supplied manager as legacy configuration
  authority, querying its current factory after resets; existing constructors keep
  lazy singleton selection, independently of restoration. SoundTest windows retain
  their configured destination for WAV export. A private manager-lifetime binding
  owns incremental physical presentation assembly, source-dependent replacement
  and sink retirement while preserving source publication, capture continuity and
  held-rewind policy with their existing owners. Capture
  admission, rebuild continuity and retirement share one manager-owned lifetime
  boundary, preserving live recordings across presentation replacements. Held
  rewind target selection, boundary replacement and release retries share a private
  manager-lifetime owner while retaining existing presentation and failure behavior.
  Native SMPS
  loaders retain their own ROM references while the shared cache and hexadecimal
  dispatch base stays ROM-free. Rewinding a
  partially collapsed Sonic 1 false floor preserves the remaining break sequence
  and cleanup. Sonic 2 falling fragments retain their mapping pieces and resolve
  current renderers after rewind. Sonic 2 Sideways platforms reserve child slots
  before constructing links; exhaustion preserves a moving solo parent and strict
  rewind without reproducing the ROM's writes into Tails. Parent retirement owns
  child cleanup, while child retirement leaves its initialized parent independent.
  ICZ miniboss snapshots restore progressed helpers;
  LBZ's ride ship restores its exact child inventory while preserving flame
  retirement before boss allocation, including full-slot conditions.
  Rexon necks and Sol fireballs restore their exact owners and sparse links while
  preserving context-free object round trips. LBZ panels and wall explosions retain
  their progressed state and shared RNG binding; AIZ finale actors retain their
  exact links after rewind. Initialized LBZ box rigs, cameo child ownership and
  saved swing anchors restore with their encounter. Freezer debris keeps its
  managed lifetime, S1 ring groups retain ordered placement recipes, and lightning
  sparks restore their supplied art and refresh atlas residency.
  Donated teams keep each character's normal and underwater palette policy through
  art refresh. Rewinding a donor's Super colors restores the first redraw while
  keeping replacement palette banks isolated from older captures.
  Registry metadata and bonus-stage capabilities own
  root table selection, bootstrap repair and debug behavior. Sonic 1 music parsing
  lives beside its game-owned loader, and level-animation composition contracts
  live with their managers. Palette ROM and byte decoding lives beside the
  canonical palette model without changing its input or color semantics.
  The unpublished art-provider candidate moves `PlayerSpriteArtProvider` and
  `SpindashDustArtProvider` from `com.openggf.data` to `com.openggf.sprites.art`,
  preserving game-owned construction and donation. This intentionally breaks
  old imports and binaries: mods, donor implementations and callers must rebuild
  against matching artifacts even when their manifest range still accepts `0.7.0`.
  See the [import migration](docs/modding/getting-started.md#player-art-provider-import-migration).
  The complete annotated abstract `Game` moves from `com.openggf.data` to
  `com.openggf.game`, beside its module and standalone composition contracts.
  Its methods and behavior are preserved; imports, class identity, `ModGame`'s
  superclass and factory/getter binary descriptors change. Rebuild compiled mods,
  donors and callers, including inferred-type and declarative standalone users,
  against matching SDK/engine artifacts. The unchanged candidate manifest range
  cannot distinguish old binaries and does not automatically reject every one.
  See the [Game migration](docs/modding/getting-started.md#game-contract-import-migration).
  Object-service guards also recognize forbidden
  method references. Remaining
  migrations and exact allowance accounting live in the
  [exception guide](docs/architecture/archunit-exceptions.md).

- **Object service failures:** AIZ intro camera calls, Lava Reef lava-fall clocks
  and launch/drop sound calls propagate service failures instead of silently
  continuing. S2 object PLC submissions retry only queue-capacity rejection;
  ROM, art and renderer failures remain visible. A ROM-free source guard ratchets
  existing broad catch handlers across main Java sources, with explicit legacy
  inventory and adversarial scanner controls.

- **Maintenance:** share save decoding, fallback drawing, screen texture creation,
  tool WAV output, S2 participant-list handling, indexed palette validation and
  collision-profile construction. Audio configuration binding reuses frozen
  settings while retaining session handler ownership. Donated preview caches share
  file validation, image scaling and publication; raw art decoding, S1 voice
  normalization, scroll uploads and audio reference input checks each reuse their
  existing behavior. Public API and ROM behavior
  remain unchanged.

- **Existing feature foundations:** retain editor, racing, prepared-loading,
  audio-core, and mod regression coverage while stock campaigns mature.

- **Java runtime ownership:** debug shortcuts, rewind constructor defaults, static
  object-art remapping, native player selection, shield playback, CPZ boss-child
  presentation and donated preview capture use shared implementations. Sidekick
  diagnostic construction is separated from CPU decisions. Results bonus digits
  use one ROM-pattern writer while retaining each game's score and tally policy.
  Session teardown attempts every independent owner and clears references even
  when an earlier cleanup step fails. Grounded hurt recovery has one owner while
  retaining its native movement and animation order.
  Monitor contents share icon drawing while keeping their visibility and lifetime rules.
  Deferred lost-ring spawns retain their queue across rewind and release reserved
  slots when the owning level is reset or rebuilt. Released solid contacts retain
  their provenance across rewind and reused object slots, preserving CPU follower
  decisions through HCZ fans, conveyors and springs. Pending explosions retain their
  configured animal/points allocation across recreation. Public profile adapters retain
  their compatibility identities while using canonical profile mechanics.

  CNZ rival cutscene deletion reuses the shared coarse range predicate while keeping
  activation and respawn cleanup local.
  Solid objects no longer implement contact listeners solely to provide empty
  callbacks; manager-owned collision, riding and live callbacks are preserved.
  S2 player and dust art share the S2 mapping/DPLC decoder, with the public
  player DPLC entry point retained as a compatibility delegate.
  SBZ Act 2 and Final Zone share uniform scroll mechanics with independent camera
  state; SBZ Act 1 uses its native cloud and building bands.
  Removed an unused radius-transition duplicate; live hurt and death paths retain their owners.
  S1 and S3K rings decode ROM mappings, correcting sparkle flips to the ROM sequence
  while preserving animation timing and the S3K pattern cap.
  SMPS music headers use an explicit format decoder, avoiding constructor-time
  virtual calls while retaining the legacy Mod API extension constructor.
  CNZ and S3K slots share GPU drawing and quad ownership; CNZ releases its actual
  draw resources so the renderer can be reused after graphics-context recreation.
  Timing-file loaders share strict field decoding while keeping schemas and
  timing authority in their existing owners.

### Build, packaging and project process

- **Java 25 baseline:** source builds, the JVM engine, creator SDK and examples now
  target Java 25, with compatible test instrumentation and explicit native access.
  Audio voice bounds retain their exception contract without relying on integer overflow.
  Release builds use GraalVM 25; Intel macOS continues through the JVM distribution.

- **`ggfmod package` works on macOS.** Packaging a mod from a directory failed with
  "Directory entry escapes root" because the snapshot's temporary root sat under the
  `/var` symlink while entries were checked by their `/private/var` real paths. The
  snapshot root is now resolved to its real path before the containment check.

- **Native builds no longer crash on Sonic 3 & Knuckles.** GraalVM native-image
  embeds only the classpath resources its `resource-config.json` names, and the S3K
  load-time manifests had never been listed there. In a native build the manifest
  lookup returned a null stream and the S3K game module threw while building its
  load-time profile, so the game failed to start under the default `FAST` load-time
  simulation. The window icon set, the bundled track-validation profiles and the mod
  SDK templates were missing from the same file and are now embedded as well,
  including the starter resource inventory. A
  structural guard fails whenever a runtime resource under `src/main/resources` is
  unreachable from that config, so the drift cannot reach a shipped bundle again.

- **Faster test validation:** buffer request-aware S2 capture reads, reuse read-only
  launcher references and large-capture hash preparation, read bounded capture
  lines in bulk, reuse emitted canonical bytes for hashing, cache repeated guard
  analysis, share immutable S3K oracle captures, read only the required trace-input
  column, consolidate equivalent FBZ traversals, and advance integration-test room
  deadlines through a controlled clock with observed membership and publication barriers.
  Measured fixtures release source/evidence caches and owned mock history, with
  fixed frame-driving stubs installed once. Route checks avoid discarded pixel
  readback and omit traversal drawing where state/pixel controls agree, while
  keeping checkpoints and complete rewind branches drawn, with an opt-in
  mode that draws every traversal frame. Screenshot checks keep pixel readback.
  Lower-rock puzzle checks compute each whole-world rewind diff once, and
  background checks read pixels only from frames used by their assertions.
  The solo Sonic Sandopolis approach uses a shorter authored controller route,
  with boss-entry and sand-defeat rewind checks beside the traversal checks.
  Strict byte validation, digest pins, ROM configurations, stress sizes, and real
  socket exchanges remain covered. An opt-in two-JVM test profile retains serial
  execution inside each worker. Public audio tests retain synthetic chip vectors;
  game-derived audio captures move to explicitly supplied, digest-checked external
  fixtures, and the full-size streaming memory check has an explicit stress lane.
  FBZ local compatibility checks run independently of traversal failures; ordinary
  runs keep one native route and `fbz-routes` retains the eleven-route matrix.
  A shared level-test standard now defines per-act widescreen/donor/character/team
  breadth, independent mechanic/lifecycle checks and consistent rewind replay spots.
  New-work guidance links to the standard; an all-game registry inventory and phased
  coverage backlog track adoption without claiming existing levels already comply.

- **Local test categories:** select related subsystem checks from changed paths, with
  common tests and structural guards retained, broad fallback for shared changes,
  and bounded diagnostics and automatic temporary-file cleanup. Process cleanup
  rejects invalid PIDs before signalling, so test doubles
  cannot broadcast termination to the user's desktop session. An explicit
  AIZ1/HCZ1 obligation lane rejects missing or skipped required
  cases. Rewind coverage protects individual object identities and distinguishes
  unavailable ROM prerequisites from executed round trips. Tool prerequisites
  are checked before testing, with the Maven version probe respecting isolated
  measurements and the Python safety suite's real JDK controls participating in
  build activity and execution admission; category runs and a focused Maven wrapper wait automatically
  for shared test execution slots across worktrees. Build-only Maven commands bypass
  the test queue while retaining exclusion against builds/tests in their own worktree.
  Linux admission can overlap up to three test runs
  when conservative memory/CPU reservations fit, crediting each running job's measured
  usage instead of counting it twice, while keeping each worktree exclusive and
  preserving a serial override. Single-fork trace and audio profiles, including
  explicit default fork settings, no longer force exclusive runs. Isolated benchmarks
  drain actual build/test execution without locking idle worktrees or repeatedly
  abandoning admission. A bounded log (`maven_queue.py --stats`) records queue waits, holds
  and peak memory. A lightweight local browser dashboard shows running jobs,
  waiting priority, resource usage and recent completions across worktrees,
  refreshing automatically without changing queue state and explaining admission
  blockers when known. It names blocking requests, shows reported tooling revisions
  across worktrees and labels scheduling estimates as coarse ordering hints.
  A compact layout shows slot occupancy, per-job memory/CPU meters, colour-coded
  admission reasons and mean CPU per completion, following the system light/dark theme.
  Waiting requests favour short checks, with aging and one
  qualified small backfill before an older capacity-blocked request drains the queue.
  Reviewed small focused checks and ordinary batches automatically use 1 GiB JVM
  heaps with 4 GiB reservations; other checks retain normal budgets, with explicit
  lean and normal overrides for focused iteration. A busy worktree's aged request retains its
  place without blocking unrelated trees. A waiter whose worktree metadata vanished
  no longer aborts another worktree's admission scan; active lock authority is retained.
  Optional profilers measure process-tree RSS/CPU
  for ordinary/guard runs and per-test allocation, sampled peaks and repeated post-GC
  retention for explicitly selected tests. An ordinary-suite observer measures actual
  Surefire class floors, native evidence and the Maven parent's separate memory cost.
  No task registration, validation receipts,
  cumulative budgets or retry gates are needed. Per-invocation category timeouts exclude
  queue waiting. Category runs explicitly use the bounded smoke suite; even the
  broad fallback excludes exhaustive oracles. Ordinary coverage queues in batches
  that release shared admission between jobs while retaining worktree ownership;
  guards queue separately. Compiled tests and resources are prepared once per
  category invocation, with lifecycle and source checks protecting reuse across
  batches. Bounded wait credit carries between batches and is spent by execution,
  allowing a long-waiting suite to progress without monopolizing later admissions.
  Supported headless graphics flags no longer force
  exclusive admission, while unmeasured JVM overrides remain conservative.
  Full ordinary validation is reserved
  for preparing new public releases through the explicit `full-suite` profile.
  A separate bounded-result comparator checks full failure identities/assertions
  and causal skip reasons against an explicit source/run baseline, rejecting
  incomplete evidence and preserving the existing runner selection and cleanup.
  Isolated runs can discover original ROMs in an explicit directory without
  Java environment injection or ROM aliases; affected fixtures honor supplied
  absolute paths. JUnit abort stacks are validated before summary bounds are
  applied, retaining their complete first causal line for strict negative-case
  comparison without accepting chained or malformed tails.

- **CI trigger policy:** `develop` and `next` run the smoke suite once a branch has had
  no push for 30 minutes, so bursts of pushes cost one run; non-draft
  pull requests into any branch and manual CI use the bounded smoke default and
  structural guards (with branch policy on pull requests);
  master pushes keep full release validation, packaging and publication. Feature and
  bugfix pushes no longer start CI, and the ROM-fixture jobs are manual opt-ins again.

- **Release-line integration:** preserve hosted release builds, snapshot policy
  checks, current launcher artifact selection, Linux packaging, and automatic
  publication on master pushes from the 0.6 release branch. Local launchers explain
  build and launch progress; updated LWJGL uses Java 25 native interop without
  deprecated Unsafe or unsupported JNI warnings.

- **Rollover-safe tooling:** SDK scaffold dependencies and the complete-audio
  launcher derive artifact versions from build metadata. Keep the unpublished
  API candidate independent from the engine branch version.

- **Guards:** the build-tooling guard checks the queued-Maven commands the agent
  guidance actually documents, the TraceChaser cutover inventory retains the
  re-created native capture guide, and the two opt-in probes now assert their output.

- **Local validation cost:** Canonical entry/reload smoke checks cover supported
  configuration pairs using the same real lifecycle and rewind assertions.
  Complete configuration products, whole-tree wording/terminology audits and
  nested release-tool sweeps move to a deliberately manual `exhaustive-audit` profile, separate from normal
  integration and release gates. Routine bonus, sanctuary and checkpoint checks
  keep real contact/reload/replay witnesses; full configuration products, song
  catalogues and long cold campaigns use the same manual audit lane. Mod routes
  and ending lifecycles retain representative branch/width/character witnesses.
  Immutable example packages compile once per test class while each scene gets
  a fresh validated runtime. Source guards batch parsing without sharing scanner
  state. Native-holder and active-payload audits use two owned parser workers,
  retaining source-order diagnostics and completing workers before teardown.
  Independent trace aux streams also use two owned workers. Compact source line
  indexes preserve diagnostics without a boxed allocation per character.
  Category batches group reviewed low-heap tests separately, preserving coverage
  while capping Maven and each qualified fork at one GiB; qualified two-fork batches
  share admission at five GiB / eight cores, with heavy batches remaining exclusive.
  Ending priority-pixel checks omit unobserved prefix drawing, retaining every
  original ROM-derived pixel oracle and a full continuous-drawing comparison;
  a contrast control rejects logical-facing substitution for the native render flip.
  Two terrain-only Infinite Sonic checks omit unasserted looping music. Survivors
  smoke checks actual wave admission, formation replacement and expiry cleanup
  at a seeded boundary; the unchanged 20,000-frame soak moves to manual audit.
  Terrain goldens retain their full probe corpus with direct delegation.
  Relay expiry advances the real test server's injected clock instead of waiting
  thirty-one seconds, retaining the actual socket admission check.
  Category diagnostics identify expensive classes and individual cases before
  deleting raw reports. The complete local command targets thirty minutes.

- **Interactive project atlas:** A persisted documentation view maps components,
  packages, Java types and explicit imports alongside Git activity and milestone
  evidence. A time slider distinguishes present, absent and subsequently removed
  source items and follows verified namespace moves across the package refactor,
  retaining original-path provenance. The [on-demand generator](tools/project_atlas/README.md) rebuilds
  the views from a chosen Git ref without ROMs or an engine build.
  Public-release preparation rebuilds and commits the atlas before final
  candidate qualification and the publishing push.

- `AGENTS.md` is now the only project instruction file. Claude Code loads it
  because the repository has no `CLAUDE.md`, and the commit hook rejects one.
  Skills are edited only under `.agents/skills/`; `tools/git/sync_skills.py`
  regenerates the `.claude/skills/` copy, and the hook rejects a staged copy that
  differs from the source tree.

