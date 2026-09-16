# OpenGGF 0.7 — Development changelog

Unreleased. This line carries the work promoted from `next` after 0.6.20260911.
The [0.7 roadmap](docs/project/v0.7-roadmap.md) prioritizes complete stock-game
campaigns; feature/API publication remains subject to the 0.8 roadmap.

## Gameplay and presentation

- **S3K slots bonus:** keep the player behind the central capsule glass during
  gameplay; the shared bonus loop no longer overrides the slot player's priority.

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
  act changes and starts destination gameplay only after the transition gap.
  Remaining route and
  hardware-rendering limits are listed in the known-discrepancies entry and
  `docs/kis2/BRANCH_DIFFS.md`.

- **HCZ1 miniboss:** retain the vertical arena lock and full rocket slowdown;
  admit the arena at the native gameplay position across supported viewport widths;
  match the body, rockets and individual exhausts to ROM sprite priorities and
  flicker gates. Restore the whirlpool slowdown, bubble animation and depth,
  falling rocket debris, and bubble cleanup at the defeat handoff, including
  after rewinding the explosion sequence.

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
  across S3K zones. Numeric HUD tiles follow their own
  VBlank updates, and mutable player art stays paired with the prepared mapping. The Act 1 boss arms
  clamp their angles and release their chain state in the original order; defeat
  preserves the native wait and score bonus before the end sign, and the ending
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

- **AIZ1 rewind:** capture the hollow-tree reveal counter and intro Super Sonic
  palette timer/frame, preserving tree reveal children and palette cadence after
  restoring gameplay. Add a native route matrix with independently reported intro,
  cutscene, tree and act-reload replay checks. Route controllers complete the
  viewport and movement-donor axes using ordinary inputs and live object gates.

- **Ring visibility:** restore full-X sorting of expanded ring placements so a
  nearer ring cannot be hidden behind a farther off-screen record, fixing late
  ring appearance in EHZ1 after the 0.7 branch rollover. Ring placement also
  sorts editor reloads correctly.

- **Master title hub:** controller-first navigation uses left/right to change games
  from either pane and up/down to enter and move through actions, with confirmation
  on menu entry, distinct cancel feedback on back, and consistent sounds within
  nested menu pages. Shared row geometry centers focus frames around their text. It keeps the existing
  animated ROM logos, adds visible launch, time-attack, recordings, mods, settings,
  advanced, and quit entries, and follows intentional input with keyboard/controller prompts.
  Catalog-backed engine settings offer visible categories, onscreen value/path/key
  editing, atomic Apply/Cancel, and amber non-default values. Shared text editors let
  keyboard and controller users move focus between the field and onscreen keypad.
  Launch options retain
  white stock, amber changed, and red experimental status at native resolution.
  Checkerboard pages retain full-size primary lettering and use an authored native
  small font with lowercase descenders for metadata instead of fractional downscaling.
  A wrapping game carousel shows the selected game, neighbors and catalog position,
  supports arbitrary catalog sizes, and dims missing ROMs. Confirm from game selection
  opens a full-width Browse Games list with availability and paging. The sky remains visible beside the action list,
  unavailable games hide profile status, and missing-ROM hints have a dark backing.
  Error pages retain accurate diagnostics until dismissed. Settings help shows
  two lines at once; launch rows use the shared left-aligned focus style. Recordings, traces,
  mods, time attack, room browsing, and lobbies have bounded pages and visible
  actions; text entry works with keyboards or controllers without triggering global
  shortcuts, and failed trace launches can be acknowledged and retried. Game logos render from
  their original textures at window resolution, preserving detail as the window grows.
  Held directions repeat consistently; controller prompts identify Xbox, PlayStation,
  or physical button positions. Settings, chat and input errors have manual full-text
  details. Numeric/address keypads and an asynchronous controller file browser share
  the text editor; unsupported glyphs retain their Unicode identity without changing
  stored values. Mods uses explicit Apply and draft discard, matching Settings.
  Startup notices use crisp native fonts and controller navigation; native-mod notices
  paginate all names. Room refreshes retain room identity and publish on the UI thread.
  Checkerboard geometry is batched and cached, catalog scans run in cancellable
  background tasks, unchanged ROM previews survive Apply, and settings presentation
  caches follow draft revisions. The experimental editor exposes a controller command
  palette for editing, saving, exporting and playtesting.

- **Widescreen presentation:** ordinary UI/HUD surfaces, titles, results, endings,
  diagnostics, all three special stages, scene effects, and trace-video capture.
  Native 320x224 remains the gameplay/trace authority. 352x224 and 400x224 are
  supported presentation targets, 528x224 is a best-effort smoke tier, and
  800x224 remains exploratory. Sonic 2 and Sonic 3 & Knuckles stage rings now
  cover the configured viewport, preventing rings from appearing late inside
  a wider screen while preserving native-width collection boundaries.
- **S3K campaign work:** the promoted development baseline includes further MHZ
  and FBZ work, LBZ Big Arm, Super Emerald sanctuary/progression, and powered
  effects. Sandopolis now implements its four quicksand variants, including
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
  admission camera gate from firing. The last-checkpoint debug shortcut reloads
  destination events, so skipping to the SOZ2 boss no longer retains early-room
  darkness or omits the boss background. Arena and laser sprite masks clip later
  sprites, including the opening door and sinking golem beneath the sand.
  Sprite overlap follows bucket and native slot order independently of terrain
  priority, and golem dust keeps its own priority across the sand boundary.
  Detached golem parts retain their priority, Rockn eyes follow its walking direction,
  and the rising pyramid shakes terrain and sprites together. Its widescreen source
  window spans the viewport instead of repeating the native 512-pixel cache;
  background priority passes share the same wrap period. Sprite-art replay
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
  stage returns, and object graphs—including promoted collapsing platforms—reconstruct
  across rewind using stable object and player identities. Live rewind preserves
  the host audio clock across repeated death/reloads. Sonic 2 and S3K companions
  copy the leader's collision plane and art priority when flight recovery ends,
  and release stale engine platform support while retaining native interaction state.
  Fixed controller replays now complete both acts from cold native Sonic + Tails
  starts through playable destinations. Compatibility acceptance follows the
  supported donor roster and checks ROM-backed art and animations for each live
  participant before and after tested loads. Broader character/donor/viewport routes,
  the lower Act 2 rock puzzle and exact native parity remain documented in the
  per-act matrices.
  Giant-ring sanctuary entry uses the same emerald ceremony, palette,
  camera, background setup, and emerald/teleporter sprite art as direct sanctuary
  loading, including the ROM’s `$1701` level identity. MHZ end-boss debris
  retains the ROM trajectory when the boss faces left, and Madmole’s submerged
  body keeps its final collision position until the ROM’s deferred deletion.
  Cutscene doors retain their lowered state when streamed out and back in, and
  boss debris follows the native initialization and flicker sequence. Act 1
  camera limits use the locked-on ROM’s height rule for all characters, and
  the Act 1 boss and its thrusters stay alive during offscreen attack phases.
  Its defeat loads the explosion art and finishes the full burst sequence
  across the signpost handoff.
  Seamless act handoffs retain fixed object owners without duplicates, avoiding
  a rewind-capture crash after the MHZ signpost.
  Complete routes, finales, and continuous replay chains remain gates.

## ROM images

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

## Development features carried forward

- **Maintenance:** share save decoding, fallback drawing, screen texture creation,
  tool WAV output, S2 participant-list handling, indexed palette validation and
  collision-profile construction. Audio configuration binding reuses frozen
  settings while retaining session handler ownership. Donated preview caches share
  file validation, image scaling and publication; raw art decoding, S1 voice
  normalization, scroll uploads and audio reference input checks each reuse their
  existing behavior. Public API and ROM behavior
  remain unchanged.

- **Unpublished Mod API candidate:** mod loading, creator tooling, characters,
  standalone games, and custom-zone work are now available on the development
  line. The API descriptor remains the version/publication authority; this
  branch rollover does not publish or freeze it.
- **Gameplay capture tooling:** `GameplayCaptureTool` pictures or films any
  gameplay section on the production boot path (any game, zone, act, position,
  width, donor or team), driven by a BizHawk input log, and writes PNG frames, a
  per-frame state CSV and an MP4. `InputLogAuthorTool` compiles a short controller
  script into that input log and proves it loads through `Bk2MovieLoader`, which now
  also reads a bare `Input Log.txt`. Skills `gameplay-capture` and
  `bk2-input-authoring` document the workflow. Native BizHawk reference capture
  uses a shared host with explicit zone plans, input hashes and failed-export
  detection; existing FBZ commands remain compatible.
- **Existing feature foundations:** retain editor, racing, prepared-loading,
  audio-core, and mod regression coverage while stock campaigns mature.

- **Java runtime ownership:** debug shortcuts, rewind constructor defaults, static
  object-art remapping, native player selection, shield playback, CPZ boss-child
  presentation and donated preview capture use shared implementations. Sidekick
  diagnostic construction is separated from CPU decisions. Results bonus digits
  use one ROM-pattern writer while retaining each game's score and tally policy.
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
  SBZ and Final Zone share uniform scroll mechanics with independent camera state.
  Removed an unused radius-transition duplicate; live hurt and death paths retain their owners.
  S1 and S3K rings decode ROM mappings, correcting sparkle flips to the ROM sequence
  while preserving animation timing and the S3K pattern cap.
  SMPS music headers use an explicit format decoder, avoiding constructor-time
  virtual calls while retaining the legacy Mod API extension constructor.
  CNZ and S3K slots share GPU drawing and quad ownership; CNZ releases its actual
  draw resources so the renderer can be reused after graphics-context recreation.
  Timing-file loaders share strict field decoding while keeping schemas and
  timing authority in their existing owners.

## Build and release

- **Faster test validation:** buffer request-aware S2 capture reads, reuse read-only
  launcher references and large-capture hash preparation, read bounded capture
  lines in bulk, reuse emitted canonical bytes for hashing, cache repeated guard
  analysis, share immutable S3K oracle captures, read only the required trace-input
  column, consolidate equivalent FBZ traversals, and advance integration-test room
  deadlines through a controlled clock with observed membership and publication barriers.
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
  and bounded diagnostics and automatic temporary-file cleanup. Tool prerequisites
  are checked before testing; category runs and a focused Maven wrapper wait automatically
  for shared execution slots across worktrees. Linux admission can overlap two runs when
  conservative memory/CPU reservations fit, while keeping each worktree exclusive and
  preserving a serial override. Waiting requests favour short checks, with aging and
  bounded backfilling to protect large runs from starvation. An optional profiler measures process-tree RSS and CPU
  for ordinary/guard runs. No task registration, validation receipts,
  cumulative budgets or retry gates are needed. Per-invocation category timeouts exclude
  queue waiting. Full CI and release validation remain unchanged.

- **Release-line integration:** preserve hosted release builds, snapshot policy
  checks, current launcher artifact selection, Linux packaging, and automatic
  publication on master pushes from the 0.6 release branch.
- **Rollover-safe tooling:** SDK scaffold dependencies and the complete-audio
  launcher derive artifact versions from build metadata. Keep the unpublished
  API candidate independent from the engine branch version.

See the [development ledger](docs/changelog/v0.7-prerelease-detailed.md) for
integration evidence and the [release summary](docs/changelog/v0.7-release-summary.md)
for scope and limitations. No new feature is certified by this rollover alone.
