# OpenGGF 0.8.prerelease — Development changelog

Unreleased. Development since the [0.7.20261105](CHANGELOG.0.7.md) release. Add entries
under the matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).


## Gameplay and presentation

- **The hub header carries the new OpenGGF logo,** the quilled, two-tailed mark
  from the README. It is drawn from a high-resolution image with smooth
  filtering, so its edges stay clean at every window size instead of breaking
  into uneven pixels like the shrunken old wordmark.

- **Angel Island Act 2's bombing run in widescreen** shows the scenery about to
  scroll in across the whole picture. The camera lock that follows the
  auto-scroll used to cover the extra width with static that trailed into the
  original 320-pixel screen, and every loop of the terrain reset it to full
  static bars, in both widescreen draw modes.

- **Knuckles' continue icon on Sonic 3 & Knuckles special-stage results** is
  red again instead of Sonic blue. Chaos Emerald stages now apply the same
  Knuckles colours as the cartridge, as Super Emerald stages already did.
  Sonic 1 special-stage results show the playing character's own continue
  icon beside CONTINUE, as its continue screen does, instead of mini-Sonic.

- **Controllers with newer firmware are recognised.** The engine now ships the
  SDL_GameControllerDB mapping table on top of GLFW's own, honours
  `SDL_GAMECONTROLLERCONFIG`, and maps a controller whose firmware is newer
  than both tables from the nearest known version of the same device. An Xbox
  Wireless Controller on Bluetooth with firmware 0x0523 was ignored entirely.

- **Knuckles in Sonic 2 title with the lock-on ROM:** the strip above the N in
  SONIC shows the logo again instead of stray tiles. While rising, Knuckles stays
  behind the emblem as on the cartridge.

- **Knuckles in Sonic 2 starts from the hub again.** Choosing Knuckles for
  Sonic 2 in Game Setup or the setup guide used to start the game as Sonic,
  because Start re-checked the profile without the built-in Knuckles option.

- **Knuckles in Sonic 2:** the hub warns when starting tier one with the stock
  Sonic 2 title screen, special stages and ending and without Super Knuckles.
  The notice explains how to supply the optional lock-on image for tier two
  and can be hidden for later launches.

- **Pit deaths during camera resize** use the live bottom limit in Sonic 1,
  Sonic 2 and Sonic 3 & Knuckles. Sky Chase also lowers the live limit before
  moving the camera with the Tornado, matching the cartridge's update order.

- **First-run setup guide:** the first time you reach the hub, a short guide
  adds your games, asks **How do you want to play?** and starts one. It finds
  Sonic 1, Sonic 2, Sonic 3 & Knuckles and Sonic CD by content; Sonic CD shows
  Found until its whole disc is checked at start, and an incomplete, foreign or
  unreadable disc names the problem and the fix without blocking the other
  games. **Stock** keeps 4:3 and the original timing choices, **Balanced**
  (recommended) is a 16:9 picture with Native widescreen gameplay, **Enhanced**
  adds smooth motion and rewind, and **Just here for multiplayer** changes no
  settings and opens Time Attack rooms or Sonic 2's two-player mode. Upgrades
  keep their games and settings unless a listed change is accepted. It opens
  once per run until finished or skipped, and again from **Extras › Setup guide**.
  Its text is a machine draft in every shipped language, awaiting native review.

- **Live rewind refills are visible:** when a checkpoint, act, zone or lost life
  refills spent rewind time, the REW plate appears and its meter and seconds fill
  back to the maximum, hold for a second and hide. Pressing rewind cancels it.
  An Unlimited budget now shows a full meter with an infinity mark in place of the
  frame counter, and the plate's edge pulses slowly while the key is held.
  A new refill choice, **Never**, gives one allowance for the whole game: it
  starts full for a new game or loaded save, and no checkpoint, act, zone or lost
  life refills it (Refill on life loss shows as inactive). Each bonus or special
  stage visit still has its own fresh allowance. **Off** still disables rewind.

- **Sonic 2 positioned starts:** object loading publishes the new camera
  position before the next object update. Widescreen EDGE keeps Emerald Hill's
  loop switchers alive so Sonic can follow both sides of the loop.

- In Sonic 3 & Knuckles, invincibility now ends on the frame the original
  does. The game counts it down once every eight frames, so it could last up
  to seven frames too long. A bubble shield collected while invincible now
  starts its animation on the right frame and stays in step with the original.

- Sonic 1 and Sonic 3 & Knuckles special stages now award a Continue at 50
  rings and lives at 100 and 200 rings. Sonic 1's 1UP block grants a life
  and shows its four-frame sparkle. Sonic 2 and Knuckles in Sonic 2 award
  a Continue and show the mini-Sonic cue when the completed act bonus
  reaches 10,000 points. Earned Continues can be spent after game over.

- In standalone Sonic & Knuckles, star posts cycle between Slots and Pachinko.
  A star post activated with 50 rings opens Slots. Once all seven Chaos Emeralds
  are collected, giant rings award 50 rings.

- **Sonic 2 and Knuckles in Sonic 2** now play cartridge demos when the title
  idles. The cycle runs Emerald Hill, Chemical Plant, Aquatic Ruin and Casino
  Night. Stock Sonic 2 opens with split-screen Sonic and Tails. Knuckles in
  Sonic 2 runs solo with its higher demo jump. Gliding and Super are disabled
  during its demos. The demo returns through SEGA on timeout or directly to
  the title on Start.

- Sonic 1 draws the act number over the blue oval on the results card. Drum
  samples resume when level music follows the special-stage theme. Leaving a
  special stage also restores the active display projection, keeping sprites
  aligned with terrain when switching to a game with a different aspect ratio.

- **Sonic 2 and Knuckles in Sonic 2** now recognize their level-select debug
  cheats and use the cartridge object lists and accelerating cursor for
  B/A/C placement. Rewind restores the cursor and its placed objects. The
  unlocked cheat survives title attract demos. Select Engine free-fly in
  Settings to use the debug movement shortcut.
- **Sonic 2 runtime art:** the Wing Fortress Tornado cue and shared animal
  and explosion graphics now change tiles as the native queue services them.
  Their prior pixels remain visible until the write, and rewind restores
  partially written art. Knuckles in Sonic 2 uses the lock-on ROM for its
  queue's pattern sources.

- **Launch Base Act 1 miniboss:** retiring box panels now submit the cartridge's
  art restore requests in order. Knuckles queues bubbles for each lingering
  panel. Other characters queue monitors, spikes and springs, then bubbles when
  the subtype-$C panel retires.

- **Sonic 1 special-stage results** now advance the object V-int clock on each
  native finish, tally and exit wait, preserving its phase through the level
  reload. Results setup and straight-line return code add no tick.

- **Sonic 2 and Knuckles in Sonic 2 special-stage results** now show a
  flashing Perfect Bonus and tally an extra 50,000 points when every spawned
  ring is kept. Both tally sequences play blips in phase with the results
  loop's V-int counter.

- **Sonic 1 and 2 extra-life audio:** a jingle that plays over silence now
  keeps sound effects blocked for the cartridge's full fade-in hold. Sonic 1
  music fades finish with the original global stop, which resets FM mode and
  lets new effects play again on the following service.

- **Sonic 2 extra-life audio:** requests refused during the jingle or its fade-in
  clear the sound-effect priority without advancing ring panning, gloop alternation
  or spindash pitch. The fade-in restores the saved priority, which the cartridge clears before backup.

- **Death Egg's final fight:** entering the arena now links foreground and
  background rows 4 and 31 to the same terrain as foreground row 0, as on the
  cartridge. Rewind preserves those links and edits to the shared terrain.

- **PAL music in live play:** Sonic 2 adds a music update every five driver
  services, except during the drowning countdown. Standalone Sonic 3 repeats
  the music and sound-effect update on services 6, 12 and 18, then every six.
  Sonic & Knuckles and locked-on Sonic 3 & Knuckles repeat on services 6, 11
  and 16, then every five.

- **Knuckles' glide-slide dust:** Sonic 3 & Knuckles and Knuckles in Sonic 2 now
  emit dust when Knuckles slides out of a glide. In Angel Island, the dust slots
  let the first giant-ring flash start on the correct frame, so Knuckles can
  grab the ride vine after the special stage.

- **Sonic 1 and Sonic 2 title cards** now count each serviced wait toward the
  V-int clock used by objects, preserving the timing phase carried into the act.

- **Sonic 2 spindash release:** the sound effect now keeps the cartridge's
  original FM5 transpose byte. The opening FM hit and delayed PSG noise sound
  the same.

- **Sonic 3 & Knuckles badnik score popups** now rise from the freed animal's
  first object pass, matching the cartridge's timing and slot allocation.

- **Hydrocity water walls:** the horizontal wall and vertical geyser now use
  the coarse camera window and previous render pass to choose when to
  disappear or begin cleanup. The window retains the original bounds at
  native width and expands to keep walls loaded in wider viewports. Respawn
  bits clear at the native callbacks, enemy art reloads on cleanup underflow,
  and spray advances the random stream only after a forward slot is available.

- **Knuckles in Sonic 2 and Sonic 3 & Knuckles:** grabbing a wall initializes
  Knuckles' animation duration and cursor to the cartridge values.

- **Carnival Night Act 1:** Tails keeps flying and playing his flight sound
  after setting Sonic down. The carry cooldown starts at the cartridge value
  and advances during the team flight pass.

- **Angel Island Act 2:** defeating the miniboss sends four flickering pieces
  of wreckage falling from the ship, as on the cartridge.

- **Angel Island Act 2:** the end boss now loads and shows EggRobo's head when
  Knuckles reaches the fight. Sonic and Tails keep Eggman's head. Rewind
  preserves the art queue's progress through the encounter. The arena camera
  bounds begin expanding on the boss's first repositioning update. The boss
  returns to the cartridge's emerge routine when the repositioning timer expires.

- **Angel Island Act 1 right-wall transitions:** Sonic 3 and Sonic 3 & Knuckles
  now use the direction of the terrain probes for the ROM's penetration counter.
  A deep right-wall probe arms a three-count hold. Subsequent shallow negative
  right-wall results spend one count, keep the wall angle and leave X in place.

- **Placed rings in patched and hosted levels** use the underlying
  game's vertical placement and clipping rules. In Knuckles in Sonic 2, rings
  disappear as their centres cross the top of the screen, as on the cartridge.

- **Sonic 2 Mystic Cave:** each rotating crate gets its final movement and
  solid-object pass before its slot is freed. A crate set keeps only the
  children that fit in the object slots available when it loads. In two-player
  mode, crates that survive the unload of their parent's block keep their
  original display orientation.

- **Sonic 2 Chemical Plant Act 2:** the visible water surface and underwater
  palette split now bob at the same height where Sonic enters the water.

- **Sonic 2 Chemical Plant:** blue balls use the previous sprite pass to decide
  which gloop sounds to queue, matching the cartridge at screen edges and
  on the first launch. The sound driver handles alternating playback.

- **Sonic 2 SEGA and title screens:** the original music-stop request goes
  through the sound mailbox, so an admitted stop waits for a compressed song
  load to finish before it takes effect.

- **The Doomsday Zone:** the second boss phase preserves the ship's fractional
  vertical position, Hyper Sonic's after-image uses the original history slots,
  and his palette keeps cycling through the white exit fade.

- **Sky Sanctuary Act 1:** distant EggRobos grow into view using the cartridge's
  scaled fly-by art.

- **Hydrocity Zone Act 2:** the boss turbine starts the rising water platform
  on the correct frame, so rolling Tails lands at the original height.

- **Frame interpolation background continuity:** vertical camera movement no longer
  mistakes parallax band crossings for horizontal teleports. The previous frozen
  scroll phase is composed at the current vertical view before comparing horizontal
  offsets, including aligned vertical line/column arrays. Host capture intent is
  installed before scroll dispatch and independent of renderer history. AIZ1
  positioned coverage checks actual midpoint motion and restart capture; pair
  tests cover 224/448-row rasters. Sonic CD ordinary levels now opt into frame
  interpolation: all seven zone scrollers freeze native phase and camera-selected
  bands without advancing cloud/water clocks, preserving native scroll-lock/dead
  holds. Disc-backed checks cover actual blended pixels and exact-picture/state
  isolation in all seven Act 1 Present stages. Whole-native-pixel stepping remains
  unchanged.

- **Angel Island screen events:** AIZ owns the canopy-wave origin and captures
  its publication with the shared ROM shake words. Shake setup respects stored
  death/restart routines and the contiguous timed table; water and bomb producers
  write the same flag without the water timer masking later bomb writes. Water
  timer edges publish through a game-neutral handler lifecycle; real-intro and
  independent last-offset rewind witnesses cover the publication boundaries.

- **Mushroom Hill screen events:** the end boss now starts the original timed
  horizontal shake at its final escape threshold, applied once to foreground,
  sprites and render bounds through the native camera copy. Background loop, animation
  bands and pillar collision positions belong to screen events and restore
  together with shake; rebuilding scroll output no longer changes gameplay.

- **Flying Battery event publication:** gameplay owns cloud drift, positions and
  shake setup; extra draws and renderer rebuilds preserve the completed event
  state, including after rewind. Subbosses request shake through its flag alone.
  Seamless reload supports mod-wrapped event providers; projected indoor views
  retain camera-copy shake, and rewind preserves timed as well as constant shake.

- **Sonic CD (development preview):** Sonic CD runs from the user's own USA
  disc image (a cue sheet and its tracks, or a lossless CHD made from them with
  MAME's chdman, named by `roms.soniccd` or found in the ROM folder) as
  a stock game module on the shared engine. Nothing from the disc is in the
  repository. All 70 stage programs load: seven zones; acts 1 and 2 in the
  Present, Past, Good Future and Bad Future; act 3 in both futures. Each zone
  has its own presentation, objects and boss. Quartz Quadrant's booth sound
  fades after the booth gives out. Death restarts release held FM effects, as
  do pause restarts in every Tidal Tempest act and in Quartz Quadrant acts 1
  and 2. Ring sounds alternate speakers and resume on the right after the FM
  stop command. Palmtree Panic's solid boulders use the current section's
  graphics, replacing the title-card tiles after loading.
  The vertical tunnel doors in acts 1 and 2 now open from the left and block
  Sonic from the right while closed.
  Flowers finish growing, diagonal springs and monitor rewards use their proper
  graphics, and object names follow the current stage's object table. The
  Palmtree Panic Act 3 time-stop monitor holds palette cycling and selected
  object animations until the stage reloads. Collision
  Chaos acts 1 and 2 have rotating spike chains with period-specific lengths
  and graphics loaded from the disc. With sprite limits enabled, offscreen object
  sprites no longer displace visible springs and blocks. Visible springs, blocks
  and stage rings share the sprite budget in object order. Stage rings draw
  once per frame, and unloaded rings in native widescreen margins stay frozen.
  All ten Collision Chaos stages draw moving background bands from their disc
  layouts and keep the static bands repeating. Their foreground terrain wobbles
  along the lower part of the stage as it does on the disc. Defeated badniks
  play the disc's destroy sound, and Mosqui, Tamabboh and Palmtree Panic's Taga-Taga
  play their own effects on impact, firing, takeoff or landing while the badnik is
  on screen. Palmtree
  Panic backgrounds sample each row's own camera section, retaining static-row
  repetition without wrapping the moving scenery. All ten Wacky Workbench stages
  preserve their static backgrounds while moving rows scroll through the stage layout,
  including the scenery behind both boss arenas. Results show the changing
  score and bonus digits and play the tally, final chime and special-stage warp cues.
  Placed and lost rings that award a life play only the voice jingle. Wacky Workbench's
  bouncy floor plays its cue when it sends Sonic into a roll. Both Wacky Workbench boss
  defeat fades hold gameplay for 22 V-INTs each. Sprites, scenery and HUD
  tiles stay in place as the fade colors change. The level HUD updates its
  digits with the ordinary stage picture and shows the same point total as the
  results screen. Wider views extend CD object draw/touch and lifetime
  windows, centre boss locks, title cards, results and game-over text, and fill the time tunnel
  through its white fade. CD HUD pieces, including the time-period and warp icons,
  follow the centred or left-edge HUD setting in both widescreen draw modes.
  Special-stage widescreen extends the backdrop and projected floor. The
  centre picture, sprites, HUD and playfield retain their native dimensions,
  as do level walls and fixed-width programs. Holding Escape returns Sonic CD to the game shelf
  with a resumable card, the correct act label and settings restored on resume.
  Sonic plays on Sonic 1's physics
  with Sonic CD's own rules (spin dash and super peel-out, the extended camera,
  the 3D ramps, per-zone player routines and the idle timer that ends in
  Sonic giving up). The stage title card holds player controls from load until
  its art queue drains. Sonic keeps the walking pose selected by ground input on
  the frame he slips off a steep slope. Time travel, special stages
  (with pause during play, exit fades and a visible results tally that Start can finish),
  the title, ordinary title-idle stage demos with recorded disc inputs, stage
  select, Time Attack, Special Stage 8 from the sound test,
  both special-stage attract demos, backup RAM (saved in `saves/soniccd`),
  the opening video, both endings, FM
  effects, CD audio and PCM sound are hosted. Special-stage extra lives cap the
  campaign total at 249 and can award a life before the score caps. Level 1-Ups
  can raise lives past 249 and wrap from 255 to 0, where a living Sonic can
  still pause. Dying with 129 or more lives triggers Game Over, as on the disc.
  Live rewind starts a fresh timeline after each time warp or act load.
  In-level fades preserve later rewind history when older frames expire.
  Special-stage rewind reaches the first frame after the reveal.
  The FM loader also accepts verified Japanese and European discs
  containing the identical driver bank. The title's RAM DATA entry confirms a
  reset of Sonic CD's save to disc defaults.
  The prompt also runs in headless sessions.
  Linked objects in Collision Chaos, Tidal Tempest and Metallic Madness free
  their slots immediately when the disc calls for it, so later objects can
  reuse those slots in the same frame.
  Time Attack follows the disc's object rules for time posts, the HUD time icon,
  1-Up monitors, the special-stage ring and the Collision Chaos intro.
  The special-stage ring disappears once Sonic holds all seven Time Stones,
  including after loading a save. Starting a new game clears the stones and
  makes the rings available again.
  Start pauses Sonic CD gameplay and music and allows frame-stepping during
  live play. During movie playback, recorded Start pauses and resumes the
  CD track or Past PCM according to the current time zone, including the
  destination once a time warp begins. Pausing stops the charge sound each
  paused frame. Passing a signpost clears the speed-shoes flag and leaves the
  boosted movement constants in effect until the next native physics write.
  Power-up music checks the live time zone, including the warp bit, before fading PCM.
  Press A, B or C during a pause to leave a Time Attack attempt or spend one
  life in the campaign. With lives left, Sonic restarts at the stage entrance
  with a fresh clock and zero rings. The title card stays hidden, and Quartz
  Quadrant acts 1 and 2 keep the saved checkpoint.
  Clearing Special Stage 8 opens an OpenGGF acknowledgement with the disc's
  ending music and a return to the title.
  Visual Mode plays the opening, both endings and the silent Pencil Test
  from the verified USA disc. Completing the good ending in Visual Mode opens
  a read-only Thank You disc preview. D.A. Garden plays disc music and can
  show a still view of Little Planet in the Present while music continues.
  The stage-select Comin' Soon row opens an OpenGGF
  contact sheet of tiles from the verified USA disc. Back returns to the title.
  Held-key live rewind works inside special stages, including the three-frame
  floor pipeline, UFOs and effects. Stage rewind restores Tidal Tempest's rotate
  block parts, Wacky Workbench's launcher sides, and Stardust Speedway's swings
  and spring launcher parts. Tidal Tempest's water resumes at its saved height,
  bob phase, and pump speed. Stardust Speedway's Kanabun, Kabasira, booster
  sparks, and path swaps resume their saved animation script frame and timer.
  In Palmtree Panic Act 1 Present, Amy Rose chases even a stationary Sonic
  until she's close enough to wait. Sonic can jump free, and removing Amy
  during a grab also returns control. Time Attack restores the stage palette.
  The layout spawner preserves its cursors and live slots during gameplay,
  so objects load and run in disc order through the act clear. Explicit
  relocations rebuild the object window and refresh collision responses so
  Stageplay can capture rewind state at the destination.
  Rewind also restores the title
  card, HUD, and points popups. The master title lists Sonic CD when the disc is
  configured or found (`roms.default: scd` boots it directly) and verifies it
  against the USA release before starting. A CHD
  is verified by the same content fingerprint as a BIN/CUE set, so it is
  accepted exactly when its decoded tracks equal the Redump set; the reader is
  pure Java (CHD v5; `cdzl`, `cdlz`, `cdfl` and `cdzs` hunks or uncompressed;
  hunk CRCs and the header SHA-1s checked) and refuses delta, older-version
  and non-CD CHDs by name.
  The original D.A. Garden planet animation, ordinary-stage attract demos,
  the original Stage 8 credits and Japanese and European gameplay remain open. Disc audio, video songs and PCM sound follow the music
  and effects volumes; CD audio and PCM also share the FM presentation scale
  for their relative level. Special-stage dust replaces any active FM1 effect
  without retriggering or releasing its note. FM1 stays held until the next
  request for that channel.
  The time-warp stars animate from the disc in every
  stage, including Tidal Tempest. While the warp stars are active, Sonic keeps
  his rings when he lands on spikes with the disc's warp check or touches a
  Tidal Tempest boss missile. The stars also protect Sonic from Wacky
  Workbench's freezer, electric beams and sparks, and let him attack Metallic
  Madness's Scarab from its vulnerable side. Defeating it rebounds Sonic.
  When a Scarab, curled Dango, or Kabasira shot hurts Sonic, his lost rings
  scatter in the contact frame.
  Grabbing a Wacky Workbench hang
  bar ends star protection and resets the warp timer. Robotnik's projectors can be destroyed in the
  Past. In the Past acts of Palmtree Panic and Wacky Workbench, the projectors'
  flying and ground hologram animals use art from the disc.
  In both Collision Chaos Past acts, the flying and ground holograms use
  the projector's art and animate from the disc. In Palmtree Panic and
  Collision Chaos, hologram animals keep their slots until their projector
  shuts down or unloads.
  In both Tidal Tempest Past acts, two hologram animals circle the projector
  and vanish when Sonic shuts it down.
  Wacky Workbench's layout animals fly or hop after the projector is destroyed
  and in the Good Future.
  Layout objects claim slots before Sonic's turn, so player bubbles and splashes
  use the slots left by the layout pass.
  Stardust Speedway's freed ground animals hop twice before turning. Its
  flying animals now flap with the disc's sprites and retire on the native
  frame. Palmtree Panic, Quartz Quadrant and Metallic Madness draw their
  freed animals from the current section's graphics. Freed animals check
  their range after drawing, as on the disc.
  Accuracy is measured against native recordings made on an emulator running
  the USA disc: 26 of 28 full-act routes (start to signpost) equal native on
  every compared field to their end (146,390 of 149,633 rows), and 68 of 70
  fourteen-second stage fixtures do (56,879 of 57,049 rows). On a focused rerun,
  the recorded Metallic Madness Present act 2 route also matches through its end.
  Its walker platforms let Sonic pass their sides, and the invisible blocks let
  him jump through from below. A separate native recording captures Metallic
  Madness Past act 1 through its signpost, tally and Act 2 Present exit state.
  The engine matches the recording's player and camera through the last trace
  row. Metallic Madness lasers shrink Sonic on the original contact frame.
  In Stardust Speedway and Metallic Madness, a curled Dango bumps rolling
  Sonic away and reacts as an ordinary enemy to standing Sonic, including the
  rebound on defeat. Kabasira's shot bursts on rolling contact and can be
  defeated with a rebound by unrolled, invincible Sonic. Tidal Tempest hosts
  its vertical moving blocks,
  break poles and stationary spike balls, with sprites loaded from the disc. In Act 1 Present,
  Sonic can ride the rotating swing and raise the elevator with its pressure
  switch. A break pole keeps its release timer through hurt, death and
  drowning. Sonic can still climb while drowning. After the pole releases
  him, he returns to the walk pose and can steer as he falls while the
  bubbles slot continues the sink. Tidal Tempest's post-scroll
  currents use the retained direction on the hurt recovery frame. R42A matches
  through the results and next-act handoff. Both Tidal Tempest Act 3 boss
  recordings now verify Sonic's water re-entry after Robotnik escapes. Boss
  bubbles turn and pop where they do on the disc, and the boss slot comparisons
  run through the end of both recordings.
  Amenbo and Taga-Taga missiles free their object slots at the disc's despawn checks.
  Quartz Quadrant Past conveyors
  change speed on the warp trigger frame, and the R51B short fixture matches
  all 569 recorded rows. Metallic Madness Act 1 hosts Poh-Bee and its swinging
  weights. Its shared badnik defeat and Good Future explosion preserve native
  slot use. Ring counts and positions match native over the 28 recorded routes
  and two Palmtree Panic loops. Ring spills and slots now match throughout the
  recorded Metallic Madness Present, Past, and Good Future routes. In Quartz
  Quadrant, Noro Noro frees its spike slot when Sonic leaves range, preserving
  the disc's object allocation order. Metallic Madness uses the collision type
  to decide which objects Sonic can touch across layers. Sonic can recover
  dropped rings on the layer where he was hurt. That layer also sets the spill's
  initial sprite bucket and tile priority. In Stardust Speedway and Metallic
  Madness, object terrain probes update sprite priority as they read collision
  blocks, including wall probes and dropped rings. Dropped rings and other
  floor-probing objects lose high tile priority when their probe reads an ordinary
  chunk. In Wacky Workbench, floor, wall and ceiling reads track each object's
  loop path and clear tile priority on entry through the loop chunk. Seesaws
  probe the inward edge of their pads, and Bata-pyon and the bounce platform
  use the native upward ceiling probe. The Past
  robot generator reacts to rolling contact over the disc's wider touch box.
  Stage loads clear the state native
  clears, so playing stages in any order matches playing each alone. In normal
  play the object clock a stage starts from and the video player's disc wait
  follow measured emulator timings under `gameplay.loadTimeSimulation`
  (`FAST` and `PROFILED`; `NONE` starts the clock at 0); trace replay admits
  the V-INTs the console dropped, under two timing-contract extensions the
  owner approved on 2026-11-13. A checkpoint or time-warp load uses the graphics list
  selected by its stage program when looking up measured timings. The capture tools can film Sonic CD
  (`--no-hud` leaves the HUD out; a trace clip may run past the recording's
  last row into the game's own flow, such as a time warp; lag-free clips retain
  every replayed gameplay step). Queued test runs
  pass a USA CHD found in the project folder as `-Dsoniccd.disc.path`
  (identified by its CHD header; the disc is verified whole when opened), so the
  single-disc Sonic CD tests run without a hand-set media root; the regional
  and CUE-layout checks still take `-Dsoniccd.media.root`. A single-disc test
  that still skips while the disc is supplied now fails the category run. Third-party code is credited in `NOTICE.md`:
  the backup-RAM format code follows superctr/buram (MIT), and the PCM chip and
  stamp graphics are translated from MAME (BSD-3-Clause); earlier code derived
  from Genesis Plus GX (non-commercial licence) was replaced, and Genesis Plus
  GX is used only as BizHawk's reference core. Most shared-engine changes are selected by
  Sonic CD alone (listed in the engine map); a few apply to every game and
  were checked against the other games' trace replays: wall-probe angle
  writes, the left headroom probe, the replay driver's lag-row pad rule and
  the speed-shoes timer skipping the turn on which a hurt state ends. See
  [game status](docs/guide/playing/game-status.md#sonic-cd-development-preview).

- **Widescreen that plays like the original:** a new setting,
  `display.widescreenDrawMode`, chooses how a wide screen plays. `EDGE`, the default,
  is the existing behaviour: objects wake up across the whole wide screen, so a 16:9
  game plays a little differently from a 4:3 one. `NATIVE` keeps the game itself on
  the original 320-pixel screen and draws the extra width as scenery, so every screen
  size plays exactly the same game. That makes widescreen fair for time attack, races
  and shared replays. Objects in the extra width that the original game has not woken
  up yet are drawn where they start, in greyscale by default
  (`display.widescreenDormantStyle`: `GREYSCALE`, `NORMAL` or `HIDDEN`). Objects that
  animate in place play their animation there; anything that moves is drawn still. They
  come to life when the original screen reaches them. When it leaves them, an object
  that was standing still keeps the look it had, and one that moved goes back to where
  it starts, unless it was destroyed for good. Rings waiting in the extra width spin in
  step with the live rings, so a ring never jumps when it wakes up, and rings you have
  collected stay gone. Title
  cards and results screens time their exits from the game's own screen rather than the
  picture, and results screens and title, level select and save menus sit centred in the
  wider picture instead of one margin's width to the right. The HUD stays fully visible when the wider picture slides to frame
  a locked boss arena. A wider picture never delays the next act or the enemies' art. That also
  lets recorded Sonic 3 & Knuckles runs be filmed wider than 320: Hydrocity and Marble
  Garden used to stop with a full KosM queue. Tests run Sonic 1, Sonic 2 and Sonic 3 &
  Knuckles at 32:9 and 4:3 with the same input and check that the game state matches on every
  frame. The capture tool gained `--draw-mode` and `--dormant-style`; both are pinned so
  a personal setting never changes a capture.

- **Walking partners jump back in where there is no floor:** a CPU partner that
  returns by walking in (a Sonic partner, or a walking mod character) used to wait
  off screen for good wherever the level had no terrain floor to walk in on, such
  as the Sonic 3 & Knuckles final boss arena, whose floor is made of objects. After
  three failed walk-in attempts it now jumps in from below the screen with the
  jump sound, arcs over the leader, and follows again as soon as it lands on
  terrain or on a solid object. It takes no damage until it lands, and if it falls
  out again it waits and retries. Where the walk-in already worked it is unchanged.
  This is an engine extension; see "Multi-Sidekick Daisy Chain" in
  [known discrepancies](docs/status/known-discrepancies.md#multi-sidekick-daisy-chain).

- **Crash page:** an engine fault during play or on a menu no longer closes
  OpenGGF. The game that failed is torn down to the game shelf and a crash page
  opens over it, with a MICA gag for the kind of fault (holding the bomb, an
  Atari ST homage; tangled in cassette tape after a rewind fault; teetering over
  missing ground when a level fails to load), a row of bombs, the exact error
  and where it was raised. The full report is saved to `crash-reports/`.
  **Report on GitHub** opens a pre-filled issue form (new
  `.github/ISSUE_TEMPLATE/crash.yml`) that the player reviews and submits
  themselves, with the home folder written as `~` and the trace shortened to fit
  the link; **Copy report**, **Details** and **Return to games** cover the rest.
  Choosing the picture, or the details key, swaps it for a QR code of the issue
  form with a title naming the error, for reporting from another device (Project
  Nayuki's MIT-licensed `qrcodegen`, listed in `NOTICE.md`). The failed work is
  never resumed, and a second fault, a failed teardown, tests and headless tools
  still rethrow; `-Dopenggf.crash.rethrow=true` restores the old behaviour. The
  pictures are reduced from generated drawings by
  `tools/modding/crash_screen_art/reduce.py`.

- **Lava Reef 1 frame hitches:** two stutters on the Lava Reef Act 1 route are
  gone. When the rock crusher opens the floor, the engine rebuilt the whole
  foreground layer (10-60 ms in a frame); it now redraws only the rewritten layout
  cells and uploads only their rows, and a test checks the result against a full
  rebuild. A sprite update of about 65-70 scattered tiles passed the old 64-tile
  limit and uploaded their whole bounding rectangle, about 37 times the changed
  bytes, and that one driver call took 1-100 ms. Changes of up to 256 tiles now
  upload only those tiles, joining neighbours in a row (about 0.02 ms there). The tilemap shader also no longer looks up its uniform
  locations on every draw. Pixels are unchanged.

- **Layout-edit frame hitches in every zone:** any change to a level's layout,
  block or chunk data made the engine rebuild and re-upload the whole foreground
  layer: 7-57 ms and 2-4 MB in one frame. Marble Garden 2's collapse did this about
  every seven frames; Sandopolis doors and passages, Flying Battery and Launch Base
  area swaps, the Angel Island battleship, the Casino Night 2 boss walls, the Wing
  Fortress rivet and the Hill Top 2 earthquake (which changes only the background)
  all stuttered. The foreground now keeps a record of the layout it was drawn from,
  redraws only the layout cells whose layout, block or chunk data differ (about
  2 microseconds a cell) and uploads only those rows, as separate spans when the
  cells are far apart. This covers every writer, tiles written straight into the
  image, prebuilt act-transition images and rewind restores; tests check each
  writer's result against a full rebuild. The Lava Reef crusher's single-cell path
  is folded into it. Pixels are unchanged.

- **Level load frame hitches:** every death respawn, act change and new game
  built the whole level's foreground layer in the frame that finished the load
  (9-16 ms, about 50 ms for Icecap 1), on top of the reload itself. A load now
  draws only the columns around the camera; the rest of the layer is drawn a slice
  per frame (about 1 ms) over the following frames, nearest the camera first, from
  the layout recorded at the load, and every foreground draw first draws any column
  it is about to show, so no visible pixel comes from an undrawn part, whatever the
  camera jump, picture width or per-line scroll. A respawn in the same act keeps the
  drawn layer and redraws only the cells the reloaded layout changes, and the Angel
  Island intro and fire transition no longer build whole-level layers in advance.
  Tests check every step against a whole-level build. In Sonic 3&K a death
  respawn also decodes its act on a background thread during the fade to black;
  the reload still happens on the same frame and uses that decode only if the
  inputs a load at that moment would use are unchanged, else it decodes as
  before. Death respawn frames went from 33-45 ms to 17-32 ms, act changes from
  25-82 ms to 15-25 ms (Casino Night 2 to Icecap 1 from 73-133 ms to 26-52 ms),
  and the frames that follow stay under 2 ms of foreground work. A new game
  after GAME OVER is unchanged (40-75 ms). Pixels and gameplay are unchanged.

- **Art upload stutters:** changing level art, animated tiles or a tilemap no
  longer makes the frame wait for the graphics driver. Uploading straight from
  memory made the engine wait until the driver had worked through everything
  queued before it. On an integrated GPU that also draws the desktop, one small
  update could take 100-700 ms: the Angel Island 1 intro, Hydrocity, Flying
  Battery, and about 230 ms at the Marble Garden act change. The art now goes
  through a pixel buffer that the GPU copies from, so the frame carries on. Over
  twelve runs per side on the integrated GPU, uploads slower than 16.7 ms after
  the level had loaded went from seven (worst 701 ms) to none (worst 8 ms); the
  discrete GPU was already below 12 ms. Loading object, HUD, player, title-card,
  special-stage and title-screen art, and Sonic 1 and 2 animated tiles, still sent
  each 8x8 tile through the buffer on its own: about 7,500 uploads per Sandopolis
  respawn, one of which waited 83 ms on the integrated GPU and up to 278 ms on the
  discrete GPU at a special stage entry. That art is now written in one batch per
  set-up step, so a respawn makes about five uploads for it, taking well under a
  millisecond. Level tiles are still uploaded one at a time while a level loads. Each
  art upload also stopped allocating a version record and a boxed tile number,
  about 16% of Angel Island's per-frame allocation. Pixels are unchanged.

- **Tails' flight in Sonic 1 and Sonic 2 with Sonic 3&K donating:** a flying
  Tails was drawn as the ball he jumped in, and a Sonic he carried was not drawn
  at all (Sonic 1) or was drawn from the wrong frames (Sonic 2). Tails and
  Knuckles wear the donor's art with the donor's own animation table, but their
  poses were looked up in the host game's table, which has no flight; only
  Sonic's lookups went to the donor. They now go to the donor's table too. The
  carried Sonic was switched to the host game's own art, which has no carried
  frames; where the host has no flight of its own he now keeps the donor's art.
  The carry with Sonic 3&K as the host is unchanged. In Sonic 1 he also had no
  tails in any pose, flying or standing: they are animated by a pass that
  follows the object loop, and Sonic 1's loop never ran it. It does now, which
  changes nothing for Sonic 1 without a donor.

- **Blue Spheres:** new under **Extras** when the Sonic 3&K and Sonic 1 ROMs
  are both present. It is the game Sonic & Knuckles starts with Sonic 1 locked
  on, rebuilt from the S&K ROM: the title and menu, endless generated stages,
  12-digit level codes, Sonic or Knuckles, the results screen and the perfect
  showcase. Stages run on the Sonic 3&K special stage. Sonic 1 is read only for
  its header. One intentional difference: any button opens the menu from NO WAY!,
  where the cartridge needs A, B and C together. The single fixed stage other
  cartridges unlock is not offered. Blue Spheres is an extra of Sonic 3&K, not a
  separate game.

- **Knuckles climbs Green Hill's cliffs in Sonic 1:** with Sonic 3&K donating,
  Knuckles could not grab a Green Hill cliff face unless terrain lay behind it,
  and there he pulled up short of the top and fell back. The faces are edge-wall objects,
  not terrain, and his grab and climb read terrain only. His probes now also see
  those walls, and a wall leaves him alone while he glides, climbs and pulls up.
  This is a rule of the engine's own, recorded in the known discrepancies; the
  original games are unchanged because none of their levels has such a wall.

- **Sonic 1 end-of-act PLC replacement:** NewPLC and ClearPLC cancel the active
  decoder as well as waiting cues, matching the ROM. Positioned GHZ starts near
  the signpost no longer fail when the signpost art replaces unfinished work.

- **Final Death Egg boss floor and wide views:** in the giant robot fight that
  ends Sonic 3 & Knuckles, the robot's lower body covered the girder floor. The
  original draws the floor in front of the robot, as it does Sonic and Tails.
  The floor is now in front at every width. Wide views also lost the planet
  beside the robot: live play greyed out both margins, and wide trace captures
  ended the planet with a black band on the right. The planet and floor now
  reach the screen edges. In wide views the robot also vanished once the
  emerald-beam chase carried the camera past the end of the level layout, and
  black patches appeared at the bottom of the extended planet while the screen
  shook. Both are fixed. This shipped in 0.7.

- **Mushroom Hill swing bars and the HUD after a giant ring:** the horizontal and
  vertical swing bars were always drawn unflipped, so a bar the level places
  mirrored showed its plain end where its cap belongs. They now follow the
  layout's flip bits, as the original's sprite code does. Separately, after
  running past a Super Emerald giant ring the HUD's yellow stayed a pale cream
  for the rest of the zone. The ring flashes three palette colours, two of which
  the HUD draws with; the original puts them back on every frame the ring is off
  screen, and the engine did so only when the ring was entered or paid out. It
  now restores them off screen too, and the flashing script stands still there.

- **Sonic 3 & Knuckles endings fill a wide screen:** on any screen wider than
  4:3 the endings were a 320-pixel picture between two dark bars, and inside it
  the sky, sea and island were drawn one bar's width to the left of the aircraft
  and characters (at the widest setting the island sat behind the left bar while
  its waterfalls hung in open sky). The endings now fill the screen with the
  original picture in the middle: Sonic's and Tails's rescue, Angel Island rising
  or being returned its emerald, the credits flight, and Knuckles' flight from Sky
  Sanctuary with its credits. The sky, sea and cloud layers continue to both
  edges, Angel Island slides in from the real edge of the screen, and the Tornado
  and the clouds enter and leave at the real edges instead of appearing or
  vanishing where the old 320-pixel edge was. Every scene keeps its original
  timing at every width, and at 4:3 the picture is unchanged pixel for pixel.
  Only the closing art on black (the Eggman or Mecha Sonic picture and the large
  Sonic 3 & Knuckles logo) stays 4:3 between the bars: it is 320 pixels of art
  with nothing beside it. What the wider picture adds is described under
  [ending scenes in a wider picture](docs/S3K_KNOWN_DISCREPANCIES.md#ending-scenes-in-a-wider-picture).

- **Separate music and sound-effect volumes:** Settings → Audio has Music
  volume and Sound effects volume sliders (`audio.musicVolume`,
  `audio.sfxVolume`, 0–100%, applied on save). Each sound-chip channel takes
  the volume of whichever track last wrote to it, so a sound effect that
  borrows a music channel keeps the sound-effect volume. Music at 0% gives
  game audio with only sound effects, for example for recordings; the
  headless gameplay and trace capture tools have `--no-music` for the same. The volumes
  only scale the output: the sound driver, gameplay, rewind and traces are
  unchanged, and at 100% the output is bit-identical to before.

- **Sonic 1's Sonic on Sonic 3&K tumble objects:** Sonic 1 has no tumble frames,
  so a donated Sonic 1 Sonic riding a Flying Battery wire cage or another tumble
  object vanished. He now keeps his own walk and run frames there, and a donor's
  own tumble frame base (Sonic 2 Tails') carries into its translated profile.
- **Lightning-shield rings in Sonic 3&K replays and captures:** rings pulled in
  by the lightning shield moved and counted correctly but were never drawn, so
  they vanished from their places instead of flying to Sonic and sparkling.
  An object-system reset (the trace-replay start, among others) dropped the
  ring slots' link to the object pass. The link now survives resets. This
  regressed in 0.7.

- **Keyboard in the Create Room form:** the room-access form claimed the
  keyboard for typing whenever it was open, which hid the arrow keys from its
  own rows, so a keyboard player could not reach CREATE ROOM. It now takes
  typed text only while the room password is being edited. This shipped in
  0.7.

- **S3K support notice says beta:** starting Sonic 3 & Knuckles from the hub
  now shows the beta notice, matching the 0.7 release notes. 0.7 shipped with
  the alpha notice. Players who hid the alpha notice see the beta one once.

- **Donated Sonic 2 Super Sonic has its transformation sound:** with Sonic 2's
  Super Sonic donated into another game, such as Sonic 1, the transformation was
  silent: the Super music started on the same frame and cut the sound off. The
  song now starts first and the sound plays over it, as it does in Sonic 2.

- **S2 Crawl:** paused proximity includes its zero countdown pass, Super Sonic
  selects ordinary enemy contact, and accepted enemy attacks retire through the
  shared touch owner. The Sonic+Tails chain advances from CNZ2 to HTZ2; later
  route errors and the special-stage return timing differences remain open.

- **Knuckles' lives name in Knuckles in Sonic 2:** without the optional lock-on
  dump, the "K.T.E" name beside his HUD head was green. The stand-in Sonic &
  Knuckles icon was drawn for a different palette line, and its name went
  through the same colour conversion as Knuckles' sprites. The name now takes
  the original game's yellow-orange, white and black, matching the lock-on
  dump's art pixel for pixel. Sonic 2 with the Sonic 3&K donor and Knuckles as
  the lead had the same green name and is fixed too.

- **S2 diagonal launcher springs:** capture both riders before deciding to
  release or decompress. A fresh Tails capture now uses the native launch force
  and can hold Sonic on the spring. The complete-chain CNZ2 segment is green;
  later campaign and art-gap failures remain open.

- **S2 Competition (two-player mode):** Choose 2 PLAYER on the Sonic 2 title. Sonic 2's own two-player mode runs natively, with Sonic on
  controller 1 and Tails on controller 2. Its menu selects Emerald Hill, Mystic
  Cave, Casino Night or the special stage. Each zone plays two acts with
  separate rings, lives, score, time and results, then zone and game results
  with their own music; a tied zone goes to a special-stage tiebreaker.
  EHZ spiral jumps now retain airborne state and upward velocity when releasing
  the invisible path controller, including simultaneous two-human routes.
  A frozen Stageplay EHZ route now races both humans concurrently in each act,
  with opposite act winners, readable results holds and a tied special-stage
  inlet. Eight short frozen Stageplay routes exercise every question-mark monitor
  outcome, with award assertions and six-second neutral holds for native comparison.
  The two-act route is ENGINE-qualified only: cold native replay encounters level lag
  before the first finish and does not reach act 2.
  The screen renders at 320x448 progressive resolution with half-height pixels,
  preserving every row of the native 8x16 cells without interlace combing.
  It splits at progressive line 216 through the shared Competition renderer; each
  half draws its player's planes, sprites and HUD from the ROM's two-player art.
  Title and menu transitions fade, and both halves show the complete title card
  before player control and the level clock start. Stageplay's visible flip checks
  use the same complete native cells as the progressive display. Signposts publish
  their native two-player pole and animated face art; act timers spend the interrupt before a
  finish crossing. Teleport monitors swap the players' positions,
  speeds, cameras and backgrounds. Badnik, bumper, bonus-block and cage
  scores go to the player who earned them, and the Casino Night slot machines
  and cages serve both players. Casino Night round bumpers, bonus blocks, slot
  cages and prizes now enter both sprite pages; plungers retain their moving head
  and fixed base with the ROM two-player art, palette and priority. Enemies whose ROM routine ends in
  `MarkObjGone_P1` retire once both cameras are more than `$300` away and
  reload when revisited; the finish-grace countdown runs before players move;
  GAME OVER and TIME OVER cards appear in both views. The competitive special stage runs both players
  on one halfpipe with its checkpoints and results. Its entry fades the previous
  results to white, holds the shared load delay, and runs startup waits; live
  pacing uses the same lag approximation as the one-player special stage. Held rewind is off in
  Competition; the progressive presentation, seam and lag-frame limits are in
  [known discrepancies](docs/status/known-discrepancies.md#s2-competition-split-screen-native-lag-and-rewind-scope).

- **S2 and KiS2 badnik chains are shared:** one chain counter serves both
  players, as in the ROM, so CPU Tails' kills and landings now continue or reset
  Sonic's chain. Every ROM landing that clears it does so, including Knuckles'
  landings in Knuckles in Sonic 2. Smashable ground and breakable blocks score
  through player 1, and only once their points popup has an object slot.

- **S2 breakable fragments use native slots:** smashable ground, breakable
  blocks and Oil Ocean launchers put their first piece in the parent's slot and
  later pieces after it, stopping at the first full slot. Fragments no longer
  depend on whether their art is ready to draw.

- **S2 points popups draw in front:** the score popup sets the high-priority
  art bit, as `Obj29_Init` does, so it is no longer hidden behind
  high-priority terrain.

- **S2 and KiS2 touch checks reach off-screen objects:** the ROM's touch scan
  has no on-screen test, so objects outside the main camera can now hurt,
  give rings or be destroyed. Sonic 1 keeps its on-screen test. The rule is a
  new `ObjectInteractionRules` component, which changes that record in the
  unpublished 0.7 candidate Mod API.

- **S2 Casino Night conveyor belts carry the sidekick:** `Obj72` moves both
  players, as the ROM does, so a CPU sidekick rides the belts too, in Sonic 2
  and Knuckles in Sonic 2.

- **S2 Casino Night horizontal flippers accept the full landing width:**
  horizontal `Obj86` uses the ROM's `$18` landing half-width instead of 8, so
  players land on the flipper's whole top. Vertical flippers are unchanged.

- **Freed animals keep their points popup across rewind:** a destructive
  rewind restore now keeps how an animal spawns its points child, so the popup
  appears and later objects keep their identities after restore and replay.

- **S2 title cards follow Obj34:** the card's parts enter, wait and leave in the
  ROM's slot order and timing. Native Emerald Hill 1 recordings match its part
  positions on every frame and its pixels at seven sampled frames.

- **S2 signpost sparkles and thrown coconuts keep their native identities:**
  signpost sparkles stay ring-sparkle objects and Coconuts' coconuts stay their
  own child object, so rewind recreates both with their animation.


- **Time Attack Competition mode and the Sonic 3&K Competition arenas:** a
  multiplayer room can now race in Competition mode as well as Time Attack.
  Everyone starts at the same scheduled moment, there are no restarts, and the
  first past the post wins. Placement counts each player's own frames from GO,
  so network lag never changes the order. The host picks the monitor rules
  and a cutoff after the first finisher. Sonic 2 acts use the original
  two-player monitors by default: each box gives one of eight items, picked as
  the original picks them, so teleport is never guaranteed. Normal or no
  monitors are the alternatives. Sonic 2 and Sonic 3&K acts can be raced in
  either mode.
  - Sonic 3&K's five Competition arenas (Azure Lake, Balloon Park, Chrome
    Gadget, Desert Palace and Endless Mine) are new Competition-only tracks.
    Each player races them alone in one full-screen view, under the shipped
    Match Race rules: five laps, losing a lap for a backwards crossing or a
    death, false starts and the 9'59"99 limit.
  - The arena view shows exactly what the original top half shows, magnified
    twice, with full-size Sonic, Tails and Knuckles (and ghosts) posed and angled
    from the original animations. They still pass behind foreground scenery, and
    TIME, laps and the countdown keep their screen size. The item icon sits under
    TIME and is hidden when monitors are off. A wider display shows more of the
    arena to each side at the same scale, with the scenery where the racers
    stand on it.
  - A player who jumps or falls off a platform, spring or blade stops counting
    as standing on it, as in the original. Before, the flag stayed set, sometimes for
    the rest of the race, so slopes stopped turning the player and the pose kept
    the angle it had when it left. This also applies to the two-player mode
    started from the Sonic 3&K title.
  - Azure Lake and Chrome Gadget now have full five-lap native Sonic regressions;
    wall pushing and spring release retain the original object-owned state.
    Idle miniature racers now balance at terrain and object edges with the
    original facing, pose thresholds and character-specific margins.
  - Sand bars now reject exact zero-distance top contacts, matching the
    original contact routine instead of landing a frame early. Turning on the
    ground also restarts Sonic's walk/run animation as in the original.
    Same-facing acceleration preserves pushing, and unrolled object landings
    preserve the skid pose; a native Balloon Park Sonic race covers five laps
    through balloons, bumpers and elephants.
    Endless Mine preserves the spring's contact-release pass and rock-pushing
    animation, with a full five-lap native Sonic regression.
  - A racer who rises above an arena's top edge, which the original allows,
    is no longer removed by the room's speed check as if they had jumped
    65,535 pixels.
  - Lap times, the total and the best lap are shown and ranked.
  - A teleport monitor swaps you with the nearest racer through the host.
  - Rooms without a verifier show the finish order labelled UNVERIFIED.
  - Solo arena runs now swap shoe state with the empty second slot, as the
    ROM does, instead of ignoring the swap item.
  - While the mass start holds an arena, a start card shows the track, its
    laps, the field, the racers and the live time to GO, then fades to black
    as the arena is released and its own fade brings in the 3-2-1-GO. The
    held arena is never drawn before it has run a frame: as in the original
    game, it is black until then, instead of a frozen frame with garbage
    tiles in its background.
  - After your finish, your final frame stays on screen while the other
    racers keep moving and the standings and cutoff keep counting. When the
    host ends the round, you return to the lobby: an arena has no results
    tally to end the run, so a finisher used to stay on the finish screen.

## Modding and tools

- Mutator Lab adds **Beta Flight** for Knuckles in Sonic 3 & Knuckles. It replaces
  his glide with the flight found in the unused Knuckles object of the Sonic 3 Winter
  CES cartridge (Dec 22, 1993): an untimed, Tails-style flap whose left and right only
  turn him around, as in that build. A **Fixed Steering** option makes left and right
  steer him in flight instead, and a **Super Jump** option gives Super and Hyper
  Knuckles that object's higher jump. The glide is unchanged while the mutator is off.

- **Mutator Lab starting values and ring hunger:** five new mutators work in
  Sonic 1, Sonic 2 and Sonic 3 & Knuckles. Start with Rings grants rings at every
  act start and life, optionally after a delay, without paying a 100/200-ring
  extra life. Start with Time puts up to 9:30 on the clock at act starts;
  checkpoint restarts keep their saved time. Start with Score and Start with
  Lives seed a new game. Hungry for Rings drains rings after a grace period and
  kills a player who stays at or under a ring count for too long; a dial beside
  the RINGS counter, in the HUD's own palette inks, shows the grace, the drain and
  a red starvation countdown in both widescreen HUD anchors. Its state rewinds.
  Held Left/Right accelerates on long sliders. `GameplayCaptureTool` and
  `MutatorGameplayCaptureTool` take a repeatable `--mutator id:option=value,...`
  flag to set mutators for a capture without touching saved settings. The 0.7
  candidate API gains `StartingStatePolicy`, `RingHungerPolicy` and five
  capabilities.

- **Gameplay capture rewind direction:** `GameplayCaptureTool` accepts several
  `--rewind FRAME:LENGTH[:half|normal|double]` windows, each holding the configured
  speed modifier with the rewind key, and pins the capture's rewind budget with
  `--rewind-scope` (now including `never`), `--rewind-seconds`, `--rewind-refill-on-life-loss`,
  `--rewind-bonus-stages` and `--rewind-special-stages`. Special-stage captures now
  draw the VHS picture and the REW plate, as the engine window does.

- Music-pack patches can replace Sonic 1, Sonic 2 and Sonic 3 & Knuckles songs
  with native SMPS packages exported by external authoring tools. Package tracks keep native
  sequencing, jingle restoration and rewind. Failed package tracks fall back
  through the normal override order and show a reason in the Mod Manager.
  S3K retail patch packages rebuild the song bank from the active cartridge
  and display a retail-derivation warning in the Mod Manager and log.

- **Sonic CD menu media:** show the native disc title on its master-title card
  and play its title theme in an isolated optional hub audio session. The preview
  closes its media after decoding; menu audio retains its own media until closed.
  Native editor persistence now retains a bounded disc/stage reference and validates
  it before native materialization. The flat-project collision guard remains
  unchanged. Internal edit replay reloads the native stage through its game owner
  and applies authored terrain, tiles, objects and rings, retaining loop semantics
  and existing object secondary bytes after moves. Source-backed playtests now
  use fresh native stage owners and authored spawn coordinates. The creator menu
  offers CD zones with act/period labels and creates a source-backed stage copy.
  Native player starts retain terrain coordinates independently of camera limits.
  Content-import and save round trips retain the reference. Native folder and ZIP
  exports carry the stage reference and authored differences. The content importer
  reconstructs the editor project using the matching verified disc. The resize
  command grows or crops source-backed stages within their collision limits
  and carries the resulting dimensions through playtest, save, and export.
- **Sonic CD Stageplay preview:** open verified disc media for native scenes and
  cold-level route tapes; preserve zone/act/period program identity through
  detached art and worker transport. Finish native title-card preparation before
  placing directed players, and admit native Sonic terrain-ground queries.
  Native/frozen/baked CD object pictures retain tile priority and foreground
  occlusion, with a disc-backed canopy negative control. Poser body/animation
  and accessory lookup follows each shot’s selected program rather than default
  stage art.
  Scene music cues reach CDDA and the selected Past-stage PCM driver through
  the no-device production audio path. Stageplay and route-tape game choices
  include CD, with native solo roster defaults. Route discovery and CLI snapshots
  use verified disc sources, binding all tracks rather than CUE/container bytes.
  Object coverage inventories all 70 zone/act/period contexts without implying
  behavior or pixel certification. Cartridge preview and exports retain their
  reader boot path alongside disc hosting. Validation scope is recorded in the
  Sonic CD Stageplay validation record. The preview worker accepts a supplied
  Sonic CD disc in both media descriptor and direct-path modes.

- **Sonic CD mod host:** admit `scd` patch manifests and `any` registrations;
  route hosted runs through the disc launcher, expose file-addressed scene and
  object art, and accept CDDA/PCM music-command replacements. The stock game
  catalogue supplies mod targets and tool choices. Contribution reports include
  CD for `any` registrations, and `ggfmod art-keys --game scd` lists CD art keys.
  The portable creator kit accepts a user's disc path with `--scd` and includes
  that flag in Sonic CD example launch and test commands.
  Module capabilities select native run characters. CD can host cartridge-donated S1 Sonic, S2
  Sonic/Tails and S3K Sonic/Tails/Knuckles, with donor-owned art, abilities,
  life icons and results names. Donated life icons use owned object-pattern banks
  so empty foreground cells and HUD text retain the disc stage's tiles.
  CPU Tails fly-ins use the donor's flight poses; host-native followers retain
  host art when the donor lacks their sheet. Game Setup saves CD donor/team choices.
  Stage-backed host animation scripts no longer require a cartridge host.
  CD authored acts support fixed-height water, collectible rings with disc art,
  fixed vertical and horizontal springs, ten-ring monitors, and owned objects, which
  spawn in X order whether or not the act has rings. Audio-state
  restoration also restores the native/streamed music selection. This extends
  unpublished API candidate 0.7.0.


- **Game rules belong to the module:** `GameModule.getPolicies()` now returns an
  immutable `GamePolicies` record holding the game's `GameRules`, so camera, ring,
  object, sidekick, power-up and drowning rules are no longer supplied by the
  physics provider. `PhysicsProvider.getRules()` and `GameModule.getRules()` are
  gone, `PhysicsProvider.fixed(profile, modifiers)` takes no rules, and a
  standalone game sets them with `StandaloneGameSpec.Builder.policies(...)`
  (Sonic 2's rules when omitted). A patch that ships a different rule overrides
  `getPolicies()` instead of wrapping physics. Cross-game donation goes through
  one composer that takes whole policies and the donor's declared capabilities,
  so only the listed player abilities can come from the donor. Gameplay is
  unchanged. Compiled mods need rebuilding against the 0.7.0 candidate; see the
  [migration note](docs/architecture/mod-api-compatibility.md#game-policy-ownership-candidate-migration).
  The [design record](docs/architecture/designs/2026-11-15-game-policies.md) plans
  the audio-rules half as a follow-up.

- **Debug labels on widescreen:** with a display aspect wider than 4:3, debug
  text tied to a place in the level (sensor, object and plane labels, the
  object-debug text) was drawn too far left: at 400 wide, at 0.8 of its distance
  from the left edge, so the sensor labels sat on the player. Lines, boxes and
  panels were right. The text renderer scaled positions back by a fixed 320
  while the picture is drawn 400 wide; it now uses the configured width.

- **Built-in sidekicks beside mod sidekicks:** a team that put Tails, Knuckles
  or a second Sonic in a sidekick slot together with a mod character anywhere in
  the team drew every character, the leader included, as a white box. The
  built-in sidekick's art was asked for by its slot code (`tails_p2`), which
  names no art, and the whole team's art was then dropped without a word. It is
  asked for by character now, and a team whose art cannot be loaded says so in
  the log.

- **Dust of mod sidekicks:** a mod character in a sidekick slot drew its spin
  dash dust, skid dust and water splash near black, as MICA's and TALON's did,
  while the same character leading the team drew them white. The dust is the
  host game's own Sonic dust, drawn in that game's palette, but a sidekick's
  dust was given the mod character's palette, whose matching colour is a dark
  grey. A mod sidekick's dust now keeps the host palette, as a leader's does.

- **MICA, an original character framework example:** The complete MICA and TALON
  mod now ships bundled with JVM and native distributions, disabled by default.
  Enable it in the mod manager; saved enable/disable choices survive upgrades. TALON’s S3K lives label now
  spells his full name using native glyph pixels with tighter blank padding; S1,
  S2 and MICA retain their existing words and portraits. Creator S3K lives names
  preserve the native × beside the counter, as S1/S2 already do. TALON now joins as her
  default sidekick and is selectable solo. Powered banks now keep each movement
  archetype's native clocks and expose host-independent authored flight and
  transformation scripts. Powered forms follow the owner-picked concepts: Open
  Cone Gilded MICA (lifted gold plates; Hyper flashes Prism Gilt gold, white-hot,
  a pale pastel jade and rose-gold) and Corona Crest Solar TALON (flame crest, goggles down,
  flame-tipped wings), with new wait, run, roll and transformation art. Powered
  forms can hold a strike palette while a strike flies and tint each strike echo
  with its spawn-time cycle phase (`PoweredEffects.strikePalette`,
  `StrikeTrailStyle.echoPhaseSlot`). Normal TALON now stands 37 px, and every
  Solar pose keeps one gloved wing-arm per side; new
  profile-facing flight art matches his carry identity, and his flight and carry
  frames, like MICA's carried frames, share the ink outline and shading of their
  other poses; his normal sheet is despeckled, his flight, carry, victory and
  other oversized poses are redrawn to the head scale of his idle, most of his poses now
  share his idle's head and line weight instead of each drawing's own, MICA's flat poses
  and her Sonic 1 title portrait are despeckled with their scale edges kept, and every slope and
  homing-dive frame of both characters, powered forms included, is re-derived
  with a cleaner rotation. TALON's idle is now his own: he preens a flight feather,
  ruffles up and catches a loose feather instead of repeating Tails' blink and yawn.
  His victory is a two-finger salute off his goggles, and both characters' signposts
  carry one readable portrait in all three games (MICA's peace sign, TALON's salute).
  MICA keeps her idle. S1 creator
  forms play animated transformations. Joint native-pose carry redraws now
  keep MICA’s raised gloves against TALON’s shoes throughout his wingbeat,
  with native-style material contours and the complete S3K hanging sequence.
  S3K title wag sprites now own the complete glove and backing context,
  removing doubled thumbs and the cutout halo while retaining native timing.
  MICA's Sonic 1 title wag frames now share one body and change only her hand,
  so her scale edges no longer shimmer at each swap.
  TALON's S2/S3K native mapping namespaces now bind all declared poses through
  archetype-owned NFRM aliases, including S2 object tumble, S3K gravity loops,
  corkscrews, swing/grip/chain/carry and later object turns. The audit exposes
  per-ID results; native Tails remains unchanged and unknown bytes use WAIT.
  TALON ending assets now cover solo S2/Tails poses, sidekick search photos,
  goggles-down ending/SCZ/WFZ cockpit crops, a shared S3K reunion pilot and solo S3K reunion poses. Native-cell, locally paletted reel
  portraits now cover S2 CNZ and S3K Slots lead/sidekick symbols, TALON solo
  and Gilded/Solar symbols through optional candidate character presentation;
  slot rewards and other symbols remain native. MICA’s S3K title now
  adds baked jade roll-in smears and heavy radial/turn ghosts through the large
  close-ups while preserving the settling pose, ring and final portrait, and keeps a coral-pink ear, a wink owning her whole continuous eyelash group,
  short gloved finger poses with one five-digit master rotated through native wag angles and a rounded lower glove, with an unperforated silhouette over the ring. Optional per-host title mascot banks
  now supply native-selector body/hand frames and masked S3K plane art, with
  independent role palettes and native fallback; MICA and TALON supply all three
  hosts while title timing, branding and occlusion remain native. The S2 duo
  keeps MICA centre-right in the Sonic slot with a separately animated palm-facing
  thumbs-up and TALON left in the Tails slot with connected wings, no glove, and
  a three-quarter face looking at the camera through the rise and settled smile; all S1
  poses follow native geometry, and MICA's S3K bank uses baked3D shading. Native
  opaque palette clearing prevents sky holes around animated patches. S3K Competition wheel, arena selection, results panels and record icons now
  use original MICA/TALON portraits with independent palettes and native origins;
  absent portraits retain native art; failed creator loads propagate through the
  registered owner fault boundary. Intro renderer inputs are prepared during update,
  and Tornado pilot art is re-derived after rewind recreation. S2 title bodies
  now obey the native emblem masking rows through the rise without changing
  approved settled poses. Explicit solo companion cockpit banks let MICA pilot
  for solo TALON and TALON pilot for MICA; ending and SCZ/WFZ support both native
  pilot mapping families, and Tails-role ending art binds its own animation families.
  The separate final front-view cockpit now selects the companion too, using
  bounded native clear masks and independent ending-surface pattern residency. Ending
  fixes: in Sonic 2 a creator lead is drawn on the Tornado again under the level's sky
  (its playable bank no longer overwrites the level tiles, and wait/walk frames come from
  the bound sheet); in S3K every ending surface owns its pattern bank, so the rider is no
  longer doubled and the cockpit no longer corrupts through the credits; a Tails-role lead
  falls on Tails's float script and is flown by its partner from the Sonic seat; a solo
  lead can supply its partner's Sonic 2 search photos (`S2_SOLO_PHOTOS`) and the partner's
  seat on the final S3 graphics card (`S3K_SOLO_EYECATCH`); the mid-size Tornado's rider
  and cockpit head are creator surfaces (`S3K_PASSENGER` selectors 14/15,
  `S3K_MINI_TORNADO_PILOT`), and its exhaust flame is native again. TALON has his own Sonic 1
  celebration, final-card figure and island-arrival sprites. Native Competition has no character-name
  strings. S3K Competition renders MICA/TALON with
  independent native-ID miniature banks and hotspots, per-frame native fallback
  and private palettes; configured creator team members occupy their native
  archetype wheel slots while the miniature simulation stays native. His Tails archetype uses native S3K
  flight and explicitly declared S1/S2 flight; Solar doubles flap acceleration
  without Flickies. His own 282-frame sheet and palette replace the placeholder;
  generated Solar tables use white-gold plumage and flame wingtips. Shared family
  art adds ledge, hanging, tumble, air pickup, recoil, recovery and flight/swim
  variants; TALON's private flight scripts select the native pose branch and
  preserve relocated mapping indices. Airborne
  down previews targets; down+jump divebombs, while ordinary
  jump retains flight/flaps. TALON owns HUD/1-up portraits, signs, continue and save portraits,
  half-pipe and Blue Sphere body sprites independently as solo lead or follower. MICA’s
  Blue Sphere rear-view bank follows native special-stage shading, colour budgets
  and run/jump contact phases, with readable ears, face edges and scaled tail.
  Blue Sphere creator bodies share native pixel ownership before spheres and
  shadows, preserving their colours and stage fades; real stock/creator captures
  verify native mapping selections and timing.
  Dedicated final-orientation special-stage drawings keep both characters'
  highlights and asymmetric features from jumping sides. The additive candidate
  API selects explicit mirrored phases in Blue Sphere and H/V/HV phases in the
  S2 half-pipe while preserving native selectors, clocks and draw ownership.
  Missing Blue Sphere orientation drawings retain native flips. Character strike-style
  callbacks use owner fault containment, and underwater-palette and super-form
  suppliers retain their publishing owner's callback provenance.
  Both special-stage banks now reuse locked generated head/torso layers across
  upright phases, posed with native-measured hip-pivot lean, sway and bob,
  screen-space lighting, reattached limbs and separately locked curl/glint phases.
  Real 60 Hz captures verify body motion, colours, shadows and fades.
  S1 supplements spell TALON; S3K uses the longest native-cell prefix TALO;
  results retain his full name. The candidate API adds default sidekick
  selection, flight declarations and the native Tails respawn factory.
  Optional directional homing
  previews airborne homing targets, locks the reticle during strikes, and draws
  native ROM spindash/skid dust for canonical-sheet characters. A fault-bounded
  `onMovementTick()` character hook supports per-tick preview requests. Directional
  strike sheet poses follow current velocity with phase-preserving turns, normal
  facing mirrors and generic/native fallbacks. The maintained
  `examples/mica-and-talon` demo groups runtime art by surface, separates production
  records, documents its class map, and builds generated sheets only under `target/`.
  Native selector, filename, hotspot and palette-cycle tables live beside the art
  and load through one example helper; formatted Java retains gameplay and native presentation decisions.
  Its paired `micaandtalon` package keeps the same mod/character IDs. The demo plays in all three stock games with Sonic's
  single-player physics, native S2/S3K spindash (disabled in S1), character-owned
  homing (including an untargeted forward
  dash), a four-frame HOMING_DIVE pose, and Hyper-only Instagib strikes. Creator
  strike colours (`StrikeTrailStyle`) and per-form particles (`PoweredEffects`) give
  MICA a jade trail, Gilded gold glints and trail, Hyper echoes and strike flashes,
  and Solar TALON sun-ember motes and a heat trail, instead of hue-rotated blue. Its
  rerunnable original sheet has 274 frames, 52 canonical animations, slope cycles,
  a shared palette and a breathing/fidget idle. Canonical sheets preserve native
  partial-loop WAIT tails and finite hold/next endings. Shared module animation
  families bind MICA and TALON phase art at host ROM timing, using separate
  Sonic/Tails archetype vocabularies. TALON families share the approved core
  character scale, with distinct S3K yawning/stretch poses and transformation
  flash phases. The profiles supply equivalent missing
  cross-game Sonic donation scripts; 18 redundant drawings are removed without
  generating art. Ordinary art bindings keep donated animation profiles stable
  across native-scene rewind. Flight carry keeps creator leaders on their own
  carried-family sheet while native Sonic retains the ROM carry frames. MICA and TALON skip
  their idle intros after the first pass. Native object mapping bytes can
  bind semantic sheet poses; MICA supplies three native-reference hang poses for
  vines and grapples, reuses fourteen existing native mapping poses for turns, rolls, riding,
  bar release and crane release. Forty additional native-pose aliases cover early
  S3K vine swings, hollow-tree tumbles, twist front/back views, poles and bars;
  33 generated drawings and seven verified quarter-turns retain the shared palette.
  Sixty further generated object poses and three approved-art aliases close the
  remaining 63 S3K direct mappings, including gravity loops, cage entry,
  parachutes and corkscrews. Six rare/normal-transformation drawings and two TALON
  legacy poses close the requested rare scripts; tail-only TALON dummy frames
  bind blank. Complete authored tumble banks enable native rotation selection;
  partial sheets retain canonical fallbacks. Other raw poses keep named canonical
  fallbacks. The
  AIZ intro retains MICA's own art and powered palette. The 36 rotated WALK/RUN poses
  use native phase coordinates and pixel-edited contact edges; all 12 steep
  WALK/RUN hood contours are completed without changing sole support. S3K transformation
  art uses clustered gold scales and a clearer face, and the second dive shell
  has rounded scale rows. Gilded Super/Hyper forms cycle
  jade to gold and expose host settings for their gates. Optional per-form GGFP
  banks now combine normal/powered tiles once, retain exact host script timing
  and movement selection, and reconcile transformation/active rewind and art
  refresh. MICA supplies 34 generated powered base poses with deterministic
  slopes; explicit normal animation/phase aliases avoid duplicate normal PNGs,
  and Hyper shares Super geometry. S1's
  donor-free six-emerald form is explicitly mod-defined. MICA supplies HUD/name,
  act and special-stage results (including S2's Super announcement, with tally
  logic independent of ROM presentation availability), 1-up faces,
  semantic sign faces/spins/backings, special/bonus-stage player art, continue
  poses, data-select solo/team portraits and counters, thirteen ICZ snowboard
  rider frames, and endings in all three games. S1 uses eight native-timed ending
  poses; S2 retains native monochrome photos and supplies Float2 and normal/Super
  poses; S3K supplies rescue Float2/ride, reunion, transformation, tiny passengers
  and trio-eyecatch art.
  S2 photo replacements belong to the active sidekick; the first panel always
  remains native. Native art references now use the photo scene palette, S1
  continue piece palette lines and S3K transformation palette phases.
  HUD life icons and 1-up faces share her private character palette in all three
  games; save-slot life icons retain their private frontend bank.
  Private palettes preserve native stage colours, text, board/spray, companions
  and vehicles; powered ending poses use her Gilded cycle. Acceptance covers
  launch/save/rewind/disabled-owner fallback and session-owned S2 HUD/1-up art,
  with three-game headless footage
  of movement, springs, normal/powered strikes and a GHZ airborne chain. A
  [nine-part tutorial](docs/modding/guides/original-character/README.md) builds an
  original character from design to every surface, with a part 2 checkpoint mod.
  TALON is included as a playable leader and follower; this is not a released mod or full-route certification.


- **Character API on the 0.7 candidate:** canonical playable animation tables
  share host speed timing, slope cycles, explicit end actions and fallbacks across
  S1/S2/S3K. Owner-bound underwater palette suppliers complement zone-aware water
  derivation. Optional `CharacterPresentation` provides immutable indexed images,
  per-host names and supplementary glyphs, native-frame stage/screen/ending sets,
  and bounded PNG loading. Font readers inventory all three small HUD fonts and
  S3K per-zone title/results letters with pixel-level glyph coverage/composition.
  GGFP presentation sections remain pending. `SuperFormProfile` supplies per-game
  tiers, palettes, physics, animations and rewind state; scene-form operations
  support authored controllers while preserving stock ending dispatch.
  The world-session `HomingAttack` facade exposes per-call homing/Instagib,
  target-only admission, requested reticles and flight divebomb with a steep
  forward-down fallback. Mutator Lab retains its character selection and native
  air-move fallback; characters need no mutator activation.

- **Character routing and presentation:** explicitly loaded development
  characters remain available in headless native Data Select. Custom team
  archetypes use the solo Sonic route without a follower. Stock S2 Tails-alone
  results now select Miles/Tails with the native graphics-region setting.
  Data-select save life counters use the character HUD icon; miniature continue
  counters reuse its continue pose. NO SAVE refreshes custom solo/team portraits
  immediately; the Sonic+Tails+Knuckles, Sonic+Tails+Tails, Sonic+eight Tails and
  sixteen-Sonic S3K team portraits use hand-placed layouts: the leader stands in
  front with the flanking characters facing outwards, inside the save-slot rails
  and spread wider in the NO SAVE window, clear of its arrows. The S2 half-pipe ring HUD composes names from its native
  SONIC/MILES/TAILS alphabet with supplements for missing glyphs. Stock fallbacks
  and native screen/stage/ending choreography remain intact.
- **Simpler creator examples:** decoded native-level patches can add registered
  objects with engine-assigned placement identities while retaining native level
  services. Scene menus share the engine's menu theme and public drawing helpers;
  presentation-only object collections reconcile checkpoint and level changes.
  Starpost Valley uses its loaded terrain's palette claims, and Slay the Robotnik
  uses supported immutable card-type enums.

- **Two-player Competition routes in both editors:** the existing Route / Tape
  workspace now authors S2 REV01 title/menu programmes, P1/P2/joint goals and copied
  fact predicates. Isolated workers plan, replay, repair and qualify both pads;
  untouched route pairs keep their exact bytes. Locked inputs and player colours
  are shared across HUD/desktop, unrelated scenery is hidden, and observed native
  anchors use the shared BK2 splice. Provisional exports remain visibly blocked;
  engine qualification does not imply native parity. The Stageplay route planner
  behind them plans two-player goals and repairs or continues a route after a
  frozen prefix. Joint search also combines independent charge, roll and jump
  policies for the two pads, scores damage to either actor, and prunes stalled
  trials. The desktop editor's side of this lives in the separate
  OpenGGF-Editor repository.

- **Recording audio recovery:** viewport recording retains the real audio lease
  when detach is refused, even after switching to silence. Stopping or restarting
  retries that lease before acquiring another, avoiding an orphaned audio tap.
  Closing the controller also ends admission permanently, so a delayed encoder
  finalizer cannot clear resources acquired by a recording started after close.
  S2 music requests now use native ROM mailbox bytes while retaining playback IDs, fixing CNZ 2P audio crashes and music priority/classification.

- **Trace clips without the original's lag frames:** `TraceCaptureTool
  --skip-lag-frames`, with `--clip frames:START:END`, leaves out the frames on
  which the recorded game could not keep up. A frame window films every row the
  original displayed, so on those rows the picture repeated and the footage
  looked as if the engine stuttered. The clip drops short counter holds on rows
  where the replay ran without gameplay. Cartridge clips require an elapsed
  VBlank. Sonic CD also drops short repeats where both counters hold. Longer
  holds such as title cards, fades, loads and pauses stay.
  The window still selects trace rows. The tool prints the output-frame to
  trace-row mapping and writes it to the capture manifest. Each omitted row
  shortens the picture and sound by one frame. The flag is off by default.

- **Wide trace captures let the act-end run-off leave the picture:** at the end
  of a Sonic 1 or Sonic 2 act the player runs right until the level's wall, which
  the ROM places 40 pixels past the right edge of its 320-pixel screen. A trace
  capture wider than 320 draws its extra width to the right of that screen, so
  the player stopped in view and ran on the spot through the results (400 wide:
  Green Hill 3 after the capsule, Emerald Hill 1 after the signpost). While the
  player crosses those last 64 pixels at the camera's right limit, the capture
  now draws from further left until the picture's right edge is the ROM
  screen's, and the player leaves it as in the original. Only the capture's
  picture moves: the replay camera, comparison, audio and 320-wide output are
  unchanged.

- **Seeded Data Select captures:** `GameplayCaptureTool --save-slots <file.json>`
  writes declared save slots through the production save manager into the
  capture's private save root before a title-first capture, and `--extra-teams`
  sets the Data Select extra team combos for that capture. The Sonic 3&K hero
  reel uses both to show custom-team saves. Title-first headless boots also run the
  donated Data Select warm-up that Engine runs before its startup mode, so a
  Sonic 1 or Sonic 2 capture with S3K donating shows the generated zone previews
  instead of S3K's placeholders.

- **Interpolated gameplay captures:** `GameplayCaptureTool --pictures-per-frame N`
  writes N evenly spaced pictures of every logic frame (N 4 is a 240 fps video), and
  `--interpolation` draws each through the live window's `display.interpolation`
  path at phase k/N of the frame. The phase comes from the picture's index, never
  a wall clock, so a capture repeats exactly; gameplay, `state.csv` and audio are
  unchanged. Without `--interpolation` the exact picture is repeated N times, so
  an interpolated and an exact capture of one input share a picture rate for
  side-by-side comparison. `pictures.csv` names each picture's frame, phase and
  the renderer's outcome (`BLENDED`, `SNAPPED`, ...). One picture per frame and
  no interpolation remain the defaults.

## Maintenance and release preparation

- Sonic 3 & Knuckles live rewind history uses about 18 MB less memory. Every
  rewind checkpoint held its own roughly 52 KB copy of the physical object-RAM
  and sprite-queue display data, although nothing between checkpoints changes
  it. Checkpoints now share one copy until the next write, a restore shares the
  copy it restored from, and restoring no longer clones the data just to check
  its size. Angel Island Act 1's 60-second history falls from about 36 MB to
  about 17 MB.

- Sonic 2 live rewind history uses about 18 MB less memory. Each rewind
  checkpoint held its own boxed copy of the title card's two plane tables,
  about 50 KB, even long after the card had gone. Checkpoints now share the
  tables until the card next writes them, and share the title-card art by
  reference. Sixty seconds of Emerald Hill 1 history fell from 22.1 MB to
  4.2 MB. Rewinding during the title card still restores it exactly.

- ICZ background loop history and indoor selection now belong to the event owner,
  so rebuilding scroll output retains the same background. Rewind captures that
  continuation together with the shared ROM shake words; ICZ objects request
  shake through the zone bridge instead of toggling the unrelated shared flag.
  Indoor restarts select the normal background routine before resize events,
  shake and rumble use the native level clock, and rewind rejects unreachable
  shake, snow-fall and intro-phase values. Unexpected failures during validated
  ICZ rewind restoration propagate instead of being logged and swallowed.

- **All-level performance sweep:** `tools/performance/level_sweep.py` plays every
  trace fixture and Sonic 3&K route movie through the frame-time probes, one at a
  time. It reports frame time, allocation, garbage collection and hitches per
  level and how far each replay got. It lists the slow frames that recur across
  runs, and it matches profiler samples, including JDK 25 method traces, to those
  frames. `compare` sets two sweeps side by side level by level, counting the
  frames over 8.33 ms and 16.67 ms that recur across runs. `RouteFrameBudgetProbe` takes a route's launch settings (`--game`,
  `--width`, `--title-card`, `--intros`, `--emeralds`, `--vint-run-count`,
  `--setup-step`, `--input-start`). `TraceBenchmarkTool --frame-log` records when
  each frame started, so a recording can be lined up with it. The trace catalogue
  now opens the Sonic 1 Scrap Brain Act 3 trace, which the ROM stores as Labyrinth
  act 4, as Scrap Brain Act 3; trace tools failed to start it before.
  `RouteFrameBudgetProbe --audio` now presents one audio frame per step, as the
  game loop does. Before, the sound driver never advanced in route runs: an
  extra-life jingle never ended, so every later sound effect was refused, and
  sound commands queued up and were applied in one burst at the next music
  change. That burst is the 17-25 ms "first sound effect" frame the first sweep
  reported in FBZ1, SOZ1, LRZ1, DEZ1 and DDZ; with audio presented those
  frames take 1-2 ms.

- **Quieter extra-life sound effects:** a sound effect requested while the
  extra-life jingle plays is still dropped, as the ROM drivers drop it, but no
  longer writes a warning to the log for each request. The admission observer
  still records the refusal.

- **Accurate FM performance:** fold the Nuked sine and exponential lookup arithmetic
  into derived tables while preserving cycle-level output. A paired six-voice
  core benchmark measured 3.9% less rendering time; this is not a gameplay FPS claim.
  The accurate core also clocks quiet stretches of cycles in runs
  (`NukedOpn2.clocks`), with identical PCM, status and snapshots: paired
  benchmarks measured 43% less time in the six-voice core and 46% less for
  S3K AIZ1 and HCZ1 music rendered end to end, once HotSpot has compiled the
  run loop. A JVM pays about three times the old per-frame cost for its first
  second of FM sound while that compile runs.

- **Draw-path caller checks:** engine renderers now pass the engine-caller
  stack check once per render host, not once per picture, frozen pattern, tile,
  bucket or frame. Sonic 1's placed rings keep their admitted draw handle instead
  of checking each ring every frame, and the sprite-table and HUD submissions
  check once. Calls from mod-loaded classes, including method references, are
  still refused. Physics reads the local-fluid flag without a stack walk. The
  Lava Reef 1 allocation profile put about 30% of the game's per-frame
  allocation in these walks; this is not a frame-rate claim.

- **Steady per-frame allocation:** native sprite drawing keeps one scratch
  stencil image per GL context instead of creating and deleting a renderbuffer
  and framebuffer every frame. The widescreen bounds-mask fade shares identical
  opacity rows between frames instead of copying seven rows a frame; each state
  still has its own unchangeable copy. Sprite-table assembly looks up each
  queued object slot once per pass instead of scanning every object per slot,
  and skips that scan for empty slots that hold no attracted ring. The HUD time
  text, the scroll arrays passed to the renderer, unchanged H-scroll uploads and
  the per-tile sprite replay callback no longer allocate or upload every frame.
  Which object owns a slot, and so the order in which objects run, is unchanged.
  Across Sonic 1 Green Hill 1 and Spring Yard 1, Sonic 2 Emerald Hill 1 and
  Sonic 3&K Angel Island trace replays, median allocation per frame fell from
  108-148 KB to 70-111 KB (25-37% less), so young collections run less often.

- **Sonic 3&K level load and respawn cost:** every Sonic 3&K level load, including
  each death respawn, read the spring mapping tables using a frame count taken
  from their first offset word. That word points past the neighbouring table,
  so each load decoded about 646,000 meaningless sprite pieces that no spring
  ever drew. The spring entries now read their own 11 frames; the frames that
  are drawn are unchanged. Player sprite renderers also digest their art one
  tile at a time instead of one pixel at a time, with the same result. In a
  headless Lava Reef 1 measurement a respawn now allocates 8.8 MB instead of
  65.6 MB and takes about 10-15 ms instead of 21-28 ms once warm; the first load
  allocates about 103 MB instead of 160 MB.

- **Load, act-transition and menu load cost:** five Sonic 2 sprite tables
  (springs, breathing bubbles, the Grabber's string, Grounder and its rocks)
  and the Sonic 3&K Gumball bonus springs took their frame count from a first
  offset word that points past the table, so loading them decoded up to about
  a million meaningless sprite pieces (about 40 MB for the Grabber string
  alone). They now read the number of frames the disassembly lists; the frames
  that are drawn are unchanged. Kosinski Moduled art now decompresses straight
  from the ROM instead of from a copied 64 KB-256 KB window, and the title
  card, title screen, continue screen, character font and Sonic 3&K data
  select no longer copy everything from the art to the end of the ROM before
  decompressing it. Pattern decoding reuses one tile buffer, and ROM reads use
  the ROM's in-memory copy instead of a locked file read. In headless
  measurements a Sonic 3&K seamless act transition allocates about 3.1-4.1 MB
  instead of 3.9-5.1 MB (FBZ 6.8 MB instead of 7.6 MB), a respawn reload about
  1.5-2.5 MB less, a Sonic 2 CPZ or ARZ respawn about 3 MB instead of
  11-12 MB, the Sonic 3&K data select load 1 MB instead of 24 MB and a title
  card's art 0.1 MB instead of 36 MB.

- **Sprite presentation performance:** the native SAT post-pass evaluates each
  scanline from primitive per-entry arrays and only over lines that sprites cover.
  SAT ingress, mask clipping, sprite preparation, native tile drawing and the
  widescreen bounds-mask fade no longer build intermediate entries, per-tile
  lambdas, list copies or per-frame working arrays. Output is unchanged; this is
  not yet a measured gameplay FPS claim.

- **Engine quality and contributions:** the README and contributor entry point now
  acknowledge maintainability weaknesses and invite focused improvements to object
  lifetime, collision contact, rewind reconstruction, trace diagnosis and navigation.

- **Contributor readability:** live objects, slots and stable identities share
  population storage; actor reconstruction and contact memory have separate owners
  behind the existing runtime entry points. Mutable contact memory is package-private
  beside its collision controller, distinct from the immutable saved contact state.
  Retiring multipart solids expires
  owned contact bits and frame history without releasing player state. Contact
  restore discards uncaptured future observations. Sole spawn-keyed dynamic solids
  now expire contact bits without clearing surviving shared owners. Constructor-child
  rewind adoption uses captured parent families, preserving interleaved structural
  links. Routine-spawned Clamer springs, Orbinaut satellites and Shellcracker claws
  share exact captured-parent lookup in the admitted population; legacy and unknown
  families retain their existing proximity lookup. Detached claws preserve their
  independent falling state after parent retirement. Restoring captured placed
  actors publishes their saved membership before child reconstruction, retaining
  authored families even when fresh scene admission is closed. Independent children can
  recreate when a parent defers to player refresh, and CNZ defeat candidates retain
  their ship family. Generic recreation installs
  construction services centrally; supported exact-spawn recipes rebuild once,
  avoiding discarded constructor graphs while preserving custom hooks and the
  fallback for declarations without that constructor. Destroyed
  object paths share unload and retirement phases within their existing schedules.
  Ordinary family/reference/coverage guidance points to the actual owners.
  Compact and scalar subclass fields share hierarchy ownership; diagnostic tools
  check actual capture membership and distinguish structural handles from dropped
  state. Mantis declares its captured parent/child references on their fields;
  the exact-policy guard rejects duplicate central ownership, and scalar fallback
  still reports links it omits.
  Production badnik and contact witnesses, shared coarse-X arithmetic with
  explicit native/wider bounds, and
  a typed rewind HUD frame make contribution paths easier to follow. HCZ owns
  its completed screen/collision publication and all three shake words in one
  validated rewind payload; objects consume the preceding publication and
  rendering only projects it. Launch Base uses the same event-owned shake words;
  its cloud and Death Egg deformation continuation rewinds with the publication,
  renderer rebuilds retain timed shake, and ship/cameo reads use LBZ’s native flag
  rather than a boolean retained by another zone. Its in-play act transition retains
  the background publication and cloud longword; shake/waves use the level clock,
  final fall retains shake words, and invalid rewind payloads are rejected before
  manager state changes. Early directed-load scroll has an immutable pre-ScreenInit
  phase; unexpected validated-restore failures propagate to their caller.
  The seamless reload retains HCZ1 deformation,
  and the geyser begins shaking on its native first dispatch. CNZ suppresses
  screen shake after player death without consuming its countdown, and HPZ,
  LRZ, SSZ, SOZ and the DEZ final arena likewise stop it from the moment a
  drowning or restarting player reaches the native death routine; SOZ uses
  the shared native shake owner as a refactor with no gameplay behaviour change,
  preserving its contiguous-table reads and rewind continuation. MGZ likewise
  owns shake, cloud phase and collision publication; drawing or rebuilding its
  scroll output cannot advance gameplay, and rewind restores the composed state,
  including the first MGZ2 rising-floor publication. Finished late loads do not
  replay the rise object's crash or camera lock. Collapse arms its countdown
  before the same frame's background shake setup. The end boss keeps its native
  setup/underflow waits and draws throughout the Sonic encounter’s initial wait;
  its allocated rescue controller runs in slot order
  with the first countdown tick on setup. Scene
  presentation spells out capture, restoration and failure cleanup in operation
  order, and pixel-oracle failures retain the rendering mode and captured frame.
  Failed trace replays print their exact test/report/triage route; triage reads
  compressed fixture auxiliaries, preserves pre-trace evidence and distinguishes
  incomplete or unknown completion from a completed comparison. Retained raw-field
  diagnostics follow that storage and are exercised on successful test paths.

- **Trace replay diagnosis:** standalone replay method entry retires verified
  prior evidence for the same invocation and scope before loading the fixture.
  Replay stopping, release assertions and publication share the verification
  scope resolved before fixture assembly, and triage selects its first
  divergence, bootstrap and evidence window from that saved scope while keeping
  global counts. Triage reports missing auxiliary references and absent
  comparison coverage without changing the published gate or mismatch totals.
  Completion metadata has one report-owned value and a strict codec. Reports
  keep carried-entry provenance and disagreements, and list retained per-frame
  result fields, separate from completion. Replay guidance routes segment
  diagnosis through the correct lane, movie and report owners.

- **Fresh-level admission:** named phases expose title completion, initial
  boundary publication and player assembly while preserving their existing
  timing, pause and failure behavior. Creator-controlled native destinations
  now finish their normal title and player assembly before admitting gameplay,
  sharing the ordinary host's native title iteration. HOLD and explicit
  presentation omission remain separate choices. In-level modal overlays remain
  visible above a controlled native title in both live and captured frames.

- **Scripted website hero reels:** `tools/media/hero_reels.py` regenerates
  the Sonic 1, Sonic 2 and Sonic 3&K title-card reels from the current engine
  with one command. It replaces the hand-cut 0.6 Resolve edits. Each game's shot
  list is a committed manifest. Each shot replays a committed trace fixture or
  BK2 headlessly, and shots are joined with hard cuts. The build fails on a
  missing ROM, a short, black or frozen shot, or a death inside a shot. It
  writes a poster, a contact sheet and provenance (engine commit, manifest hash,
  frame ranges) beside each reel. The reels keep the 0.6 cut's feature beats as
  well as its zones: live rewind undoing a hit or a fall, sidekick chains (ten
  Sonics in Scrap Brain, a crowd at the Emerald Hill 2 boss), borrowed art and
  Super Sonic in Sonic 1 and Sonic 3&K, Sonic 3&K's Super Sonic in Sonic 2
  with Knuckles following, Knuckles climbing a Hill Top cliff in Sonic 2, Sonic 1
  rolling under the Flying Battery elevator, a Casino Night jackpot,
  custom-team Data Select saves, and every Sonic 3&K zone with its bonus
  and special stages. Shots sit inside their acts, and Super Sonic transforms
  only once the replay has collected 50 rings. Knuckles in Sonic 2 shots read the
  verified lock-on dump, so the HUD shows the chip's own art and colours. The reels render without native
  sprite dropout, as normal play does: `TraceCaptureTool --no-sprite-limits` turns
  off the per-line limits that trace captures otherwise keep for ROM image
  comparison. `GameplayCaptureTool --rewind FRAME:LENGTH`
  holds the live rewind key over a window and draws the rewind plate,
  `--timer FRAMES` sets the level timer a positioned start carried in,
  `--emeralds` also takes Sonic 1 and Sonic 2's six emeralds, and
  `--title-screen` now accepts `--donor`, since boot activates the donor before
  the title. `GameplayInputBranchTool` takes `--input-start`, `--resume`,
  `--resume-from` and `--skip-rows`, so a shot's input can be derived from a
  level inside a complete-run movie, with one change and the recording's own
  rows after it.

- **Trace coverage and canonical fixtures:** independent strict replay covers all
  163 segments of the Knuckles super-emerald, Mega, Sonic+Tails and KiS2 chains,
  plus six previously untested standalone slices. Existing canonical identities
  replace 30 duplicate fixture directories. The S1 complete-emerald chain now
  records native PLC readiness and real ending/credits initialization timing,
  clearing its four queue frontiers and terminal art frontier. Ending loads
  resume actual ROM-backed work; credits use their native wait and fade loops.
  Generic ending callers retain their gameplay order, and failed initialization
  preserves the original error while cleaning up its owned continuation.
  Recorded physics, auxiliary observations and movie input are preserved exactly.
  The LRZ title owner submits its three enemy archives in native order,
  preventing a later results-art FIFO overflow. AIZ2 art preparation preserves
  Knuckles' terrain-only branch; Sonic/Tails also submit the battleship object
  art. The boss-glide continuation remains open pending the Knuckles SOZ route.
  Existing animation and route frontiers remain visible, including
  the fresh sanctuary's intermediate load state; its routine comparison does
  not establish player dispatch.
  Special-stage entry honors the recorded team and production lock-on module;
  the single entry-state import consumes S2-family manifest destination rings.
