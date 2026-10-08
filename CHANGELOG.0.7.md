# OpenGGF 0.7 — Development changelog

Unreleased. This line carries the work promoted from `next` after 0.6.20260911.
The [0.7 roadmap](docs/project/v0.7-roadmap.md) prioritizes complete stock-game
campaigns; feature/API publication remains subject to the 0.8 roadmap.

## Gameplay and presentation

- **Sonic 1 background scrolling:** Scrap Brain Act 1 uses the REV01 cloud
  interpolation and three building bands, with separate fractional cameras,
  rewind restoration and tile-cache window coverage at wider viewports. Scrap
  Brain Act 2 and Final Zone keep their uniform scrolling and use the REV01
  background-Y entry offset.

- **Sonic 2 playable art and springs:** companion players register their ROM
  art owner by character identity, restoring Tails DPLC submissions for runtime
  names such as `tails_p2`. Horizontal springs apply the native unsigned
  launch-side check for both participants, including the flipped equality case.

- **Stock title starts:** starting a new Sonic 1 game clears emeralds and the
  special-stage cursor while preserving configured stage counts; Continue keeps
  existing progress. Unsupported two-player title actions return to the title
  menu instead of starting an ordinary level.

- **Window and display aspect:** a launch that changes the display aspect (a launch
  profile's aspect, or a module's required aspect such as a mod laid out for 16:9) now
  refits the window instead of letterboxing the new picture inside the old window: a
  windowed window keeps its whole-number height scale and takes the new aspect's width,
  within the monitor. Fullscreen and maximised windows, and `display.windowAutosize: false`,
  are left alone.
- **S3K level entry:** Initialize the camera with the ROM’s zero/maximum clamps,
  independently of the runtime minimum bounds. This removes the opening AIZ1
  camera offset while preserving MHZ1’s separate locked-on camera focus.
  Live zone handoffs now run the fresh title and terrain-loading lifecycle,
  restoring players before the initial sprite pass without adding a frame.

- **S3K Launch Base:** Cup elevators now resolve each player’s solid contact before checking capture, restoring airborne side collisions and normal landing admission, and preserve native movement and animation control through the NPC Knuckles cutscene. Rolling drums preserve native rider state when unloading, after completing their final player updates. The Act1 miniboss preserves the fatal-hit dispatch before its defeat countdown, including explosion timing through rewind. Carried results now finish their twelve real children before publishing control release, then initialize the next title owner on its following dispatch. Retained title owners reset level counters only after their ROM-backed art and moving children settle, preserving the independent presentation clock. Ribot creates its visual children before its first active orbit, matching the ROM initialization return. The seamless Act 2 reload holds the inherited camera limits until the title owner releases them, then runs its gradual boundary children in their native creation pass.

- Automatically mask widescreen pixels beyond current native horizontal camera
  bounds, including asymmetric arena and level edges. During staged boss entry,
  the mask follows the earlier boundary until the final lock arrives. Each side
  crossfades between world-space mask shapes; a new effective boundary starts
  from the displayed blend. Presentation history rewinds, and native-width output
  is unchanged.

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
  handover to the ending act ($D01, not yet implemented). S3K transformations now release the
  player on the ROM palette-fade schedule.
  S3K level palette changes now appear on the frame the matching sprites do, one frame after
  the game writes them, as the hardware shows them, so boss hit flashes are no longer a frame early. S3K palette cycles (such as
  Carnival Night's lights) now start after the level fade-in finishes, as in the ROM, instead of
  from the first level frame.
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
  them, Blastoid hits scatter rings, and rewinding past an opened egg capsule keeps
  its explosions going. Players balance at the ROM distance from the edges of egg
  capsules and horizontal springs, and corkscrew ramps draw the tumble frames the ROM
  shows for left-facing players.

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
  recorded return-tail comparisons and timing checks.

- **Boss parts in their own sprite layers:** ICZ miniboss orbs and shards, ICZ end
  boss body parts, LBZ miniboss panels and box pieces, and HCZ end boss children now
  draw in the display list their ROM `priority` word selects instead of their
  owner's, and the lightning shield spark keeps the shield's tile priority.

- **Guards:** the build-tooling guard checks the queued-Maven commands the agent
  guidance actually documents, the TraceChaser cutover inventory retains the
  re-created native capture guide, and the two opt-in probes now assert their output.

- **Sprite priority buckets:** objects transcribed from S3K now convert the ROM
  `priority` word to a bucket (`$280` is bucket 5, not clamped to 7), so the CNZ
  hover fan, cannon and trap door and the LRZ collapsing bridge draw in their ROM
  layer, and the S1/S2/S3K objects that never stated a bucket (HCZ water splash,
  ICZ miniboss, LBZ launcher and grapple among them) no longer draw in front of the
  player. A structural guard now rejects any drawing object without a declared bucket.

- **S3K bonus stages:** keep the slots player behind the central capsule glass
  during gameplay; the shared bonus loop no longer overrides the slot player's
  priority. Bonus title-card release completes the initial object setup before
  the first gameplay tick, preserving the first controller input and its movement.

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

- **S3K Fireworm:** killing a Lava Reef fireworm now retires its whole body. Its segments scatter, fall and flicker away and its flames go out, instead of leaving
  frozen body pieces or invisible fire that burns the player seconds later.
  Retired segments stay harmless and continue their fall after rewinding,
  even when their deleted head no longer exists in the restored world.

- **S3K Lava Reef domes:** the background now locks onto the dome as the player crosses each
  of the three dome thresholds, rises and falls with the lava surface inside it, and stays on
  the dome view for the eight frames the plane takes to redraw on the way out. Knuckles gets
  his own background chunk in act 1.

- **Rewinding dynamically created children:** restoring a saved moment now keeps the
  children a live object created while it ran. The camera-limit easing objects a Lava
  Reef rock crusher spawns, and the Fireworm's body segments, used to disappear on
  restore, and a restored segment's flame stayed behind while the segment swam on.

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
  and FBZ work, with physical checkpoint activation, rewind and repeated death/reload
  coverage across MHZ's accepted rosters, supported donors and five viewport presets,
  plus cold Tails completion of both acts and native-pair Act 1 completion with
  rewind-verified boss and act/zone handoffs; LBZ Big Arm, Super Emerald sanctuary/progression, and powered
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

- Added an independent [Infinite Sonic mod prototype](examples/infinite-sonic/README.md): seeded endless sections assembled from the user’s Sonic 1 ROM in every act of Green Hill, Marble, Spring Yard, Labyrinth, Star Light and Scrap Brain (Final Zone stays stock), with forward world recycling for solo Sonic and a background that scrolls on across each recycle without a jump, seeded terrain-aware ground/flying patrols of the badniks each act's own ROM object layout places (Orbinaut spikes, indestructible Rollers and Walking Bombs), collectible ring rows and spaced jump corridors (64–192px pits and ledge drops that climb or descend between four ground levels) between hills and dips, with sections that join the halves of different ROM columns and climb or drop a ground level on the zone's own slopes between corridors. The survival challenge raises whole-game speed every 30 seconds by a quarter of its current speed (1× → 1.25× → 1.55× → 1.95×), gliding to each new speed over a second, enforces a minimum forward scroll while letting Sonic build a lead to just right of centre, with spare lives earned only from every 100 rings (none at the start) shown on the HUD, a CONTINUE/RESTART death menu whose CONTINUE spends a life and rewinds the run with the engine's live rewind (recorded even with live rewind off, through a new `GameModule.scriptedRewind()` hook) to the first fair moment at least a second before the death, with a pit-free runway ahead, then READY waits for the player and the speed eases back up from 1×, and a game over drawn in the mod's own font with an instant restart instead of the stock card, and replaces the timer with speed/countdown information and speed-based survival scoring. A prominent five-second countdown and warning chimes announce each increase, and music/effects follow the challenge speed in both tempo and pitch, with sequencer updates interleaved through accelerated PCM rendering. The Sonic 1 title screen adds an animated INFINITE wordmark above the emblem and a left/right zone picker that starts the chosen zone and shows that zone's own scrolling background behind the emblem, and course title cards drop the act number. Platform stretches bridge wider pits with the zone's own stock Sonic 1 platforms (stationary or falling floating platforms, wide moving blocks) flush together as one raft in the middle of the pit, or, at random where the zone has Buzz Bombers or Batbrains, with a hovering flyer to bounce off instead of or as well as the raft, rings arc over pits, the mod always plays at 16:9, and each zone is a single course with the stock level select unavailable. Some ring rows are hop arcs, stretches split into a high road of the zone's stock platforms (climbed by steps, 128px up, with rings and monitors) and a badnik-patrolled ground path, each zone has its own timed hazards drawn with ROM art (Green Hill wrecking balls, Marble and Star Light pit fireballs, Spring Yard rolling spiked balls, Labyrinth spiked ball-and-chains, Scrap Brain flamethrowers, and spike beds everywhere) that take hits through the same shield/ring toll as badniks, Star Light gains platform stretches on its elevators, and the death menu and GAME OVER offer EXIT to the Sonic 1 title, as does a tap of Escape mid-run. Sonic shows his full-speed running frames at the course's lower top speed. Enemy hits are gentler: a shield, picked up from non-solid monitors on the course (which also hold Super Rings), absorbs one hit, and otherwise carrying 20 or more rings spills just 20 of them, both without knockback; Sonic's top running speed is 7/8 of stock, the minimum scroll is 4 px per tick, and the camera holds Sonic 45% of the way across the screen. While Sonic has no shield and fewer than 20 rings he sweats and the HUD ring count flashes red, and the HUD heads with the zone's top score. Every run lays a new random course (a JVM property pins one seed), and each speed-up flashes the screen, rushes speed lines past its edges and announces the new speed. Each zone keeps a saved top-10 leaderboard: it shows as each run starts, a banner and chime mark overtaking the zone's top score, the death screen congratulates a new top score or shows the run's rank, and the idle title pages through the zone leaders and each zone's top 10 like an arcade attract loop. Includes a source build/package script and focused ROM-backed traversal, platform crossing, enemy interaction and rewind tests.
- Added an independent [Sonic Survivors mod](examples/sonic-survivors/README.md), a bounce-driven survivors roguelike over the user's Sonic 2 ROM: every route zone (Emerald Hill to the Death Egg, Sky Chase skipped) becomes a walled arena cut from its own act's terrain, with the stock objects, rings and zone events removed so the act cannot be finished the usual way. Badniks from each zone's own ROM art (19 species across seven AI archetypes: walkers, hoppers, leapers, flyers, divers, turrets and Asteron's burst) have hitpoints, health bars and elites, and pour in from both sides and above for a five-minute survival clock; Sonic starts with only his jump, every airborne stomp rebounds him and chains a combo that multiplies damage and ring drops, and bounces charge a six-second Fever burst with an eighteen-second recovery. Rings are health (a ring toll that grows with the arena's pressure clock and route position instead of knockback, lethal at zero) and experience: each level-up pauses for three cards (rerolls available) from twenty-four upgrades, limited to three weapon types and three buffs (owned types can still gain ranks) — bounce weapons (Shockwave, Spark Burst, Chain Zap, Homing Rings), auto weapons (Orbit Rings, Sonic Boom, Flicky Squad), moves (Homing Dash, Air Jump, Ground Pound) and passives. At 0:00 Eggman arrives in the zone's own vehicle and attacks with the zone's projectiles; beating him clears the zone, drops its Chaos Emerald once (seven permanent emerald powers) and offers the next zone's acts (act 2 and 3 harder and richer) or retiring. The Death Egg is Silver Sonic followed by a fleeing Eggman on foot. Elite badniks drop monitors (Eggman's is a screen bomb). Runs bank rings for a camp shop of permanent upgrades, the Sonic 2 title gains a SURVIVORS wordmark and a start-zone picker for zones previous runs reached (with catch-up level-ups), and progress is saved under the engine's save root. Includes a build/package script and ROM-backed tests over every route act. The mod supports solo Sonic or Tails, enforces an arena ceiling, and moves every menu confirmation to Enter/Start without triggering host pause. It completes multipart boss sprites, adds Whisp Queen/Balkiry Ace/Oil Sentinel encounters, enlarges and labels elites, warns before dives, and prevents lost rings from being magnetized or counted as fresh experience; hit-spilled rings fade out within 0.75 seconds. A first boss clear unlocks saved ten-minute and unlimited camp modes alongside the standard five-minute route; ring-pickup chimes are batched during showers while retaining immediate feedback after quiet. Encounters alternate Crossfire, Air Raid and Stampede with warned surges and regroup windows; endless health and wave batches keep escalating. Mod enemies, shots, pickups and growable player projectile arrays have no fixed population ceiling and preserve rewind without consuming native object slots. Fever and invincibility expiry preserve continuous arena/boss music. A Fever meter and current hit-cost readout explain the rebalanced Armor, ring-bank risk and gentler combo rewards. Uncollected ring/monitor rewards expire after a minute. Nearby reward rings, including scattering and magnetized enemy drops, merge across value tiers and consolidate into larger cyan (5+), purple (25+) and red (125+) rings, retaining their combined value while reducing clutter; hit-spilled rings stay separate. Badnik score popups are replaced by actual damage numbers that grow and change from red through orange to pink-red with stronger hits. The unreleased game is branded simply Sonic Survivors. Super Sonic is disabled pending a dedicated Survivors implementation; the seven-emerald damage bonus remains. Menu text uses precomputed compact glyph geometry and cached card descriptions to reduce allocation and drawing overhead. One difficulty curve now drives badnik and boss health, waves, the toll and ring values from the route tier and the arena clock (never total run time), growing faster than linearly so Metropolis onward is meant to be hard; bosses scale with the waves, a crowded arena (160 badniks) turns overflow into elites, and unlimited mode climbs a tier every two minutes. Maxed weapons with their partner buff evolve from treasure chests (twelve evolutions) dropped by bosses, every fourth elite and mid-stage warden squads; zone events (telegraphed rain, swarms, Casino Night's Jackpot) break up each clock; maxed builds take stacking Overdrive. Sonic charges Fever faster and gains more per combo bounce, Tails hovers on a held jump. Twelve upgrades unlock from lifetime milestones, six optional Eggman's Rules raise the bank, and camp gains shop, rules and records pages (records, unlocks, evolutions, bestiary, unlimited times). The ring shop is ten global power-ups in small long-tail steps with rising prices; older saves convert to the same totals. Starting and leaving at once, or leaving before a fatal hit, no longer banks free rings, and a crouched spin-dash charge is no longer an untouchable attack.
- Headless gameplay captures run on macOS (the hidden boot window requests the OpenGL 4.1 core profile the engine renders with) and can apply a packaged patch mod (`GameplayCaptureTool --mod <jar>`) to film mod gameplay.

- Added [Eggman's Sky](examples/eggmans-sky/README.md), a No Man's Sky-style S3K mod scene starring Dr. Eggman. Planets are loops of terrain remixed from 55 stock acts of every supplied Sonic ROM through the new level kits (runs of layout columns joined only at seams the act already contains, with mismatched cells repaired), each in its own Genesis-quantised palette with a climate, storms, day and night, a parallax sky, procedural flora and minerals, 83 badnik and animal bodies as fauna, and monitors, Egg Capsules, Starposts, echidna ruins, Giant Rings and wrecks. ROM music and effects play from scene startup, and regular exposed blue-crystal deposits keep launch-fuel ingredients accessible. The Egg Mobile hovers, mines with a heat-limited laser (or carves terrain), scans and catalogues species, survives life support and hazard drain, and refines and crafts resources; greedy play summons Flickies, Tails, Sonic, Knuckles and Super Sonic. Space is a software-rendered cockpit with ray-traced planets textured from their own zone blocks, rings, nebulae, asteroids, pirate dogfights and the Tornado; Egg Stations trade, install 23 technologies and give missions; a galaxy map warps between star classes, black holes and Giant Rings toward the core, where seven Chaos Emeralds from ruin-revealed shrines end the expedition and start a new galaxy.
- Added the [Slay the Robotnik example mod](examples/slay-the-robotnik/README.md), a Slay the Spire-style deck-building roguelike played on Sonic 3 & Knuckles as a full-screen mod scene. Sonic (Focus and Combo multi-hits), Tails (Dexterity, discards and Ring Bombs) and Knuckles (Strength) each have a starter deck, a starting relic and 42–43 more cards; 55 relics, 16 item monitors and 22 events round out four acts — Angel Island, Hydrocity and Launch Base as branching 15-floor zone maps with badnik fights, three elites and two bosses each, then a short Sky Sanctuary finale against Mecha Sonic, who returns as Super Mecha Sonic. Every character, badnik, boss, monitor, ring, the Tornado, explosions, music and sounds come from the player's ROM: bosses are assembled from their mapping frames at the original child-object offsets, defeated badniks explode and free a Flicky, and card and relic pictures are one-line text recipes of ROM sprites and drawn effects. Fights, Starposts and capsules are staged on stretches of each act's real level, moving through it as the map climbs, with every character standing on the ground under it. Runs save at every room; the title adds a Compendium and per-hero records. The title screen flies through four zones' real levels behind a Tornado chase, each act opens with its zone's ROM title card, and every event plays out the chosen option with the game's own objects (the slot machine spins the bonus stage's own reels). Fights open with an entrance that names each boss, claimed rings, cards and relics fly to the HUD, and every highlighted card term and HUD readout has a tip. The engine suite builds, validates and runs the example's tests and opens every fight and event through the scene against S3K.
- Added [Robotnik Tower Defence](examples/robotnik-tower-defense/README.md), an S3K mod scene in which Robotnik defends his base door from 15 waves of unionised Flickies. Six badnik defenses provide direct fire, ground splash, anti-air, armor piercing, slowing and chain attacks; towers can be upgraded or sold, the door repaired between waves, and an emergency bomb repels breaches. Couriers, shield carriers, flyers, organisers and saboteurs introduce coordinated wave tactics. ROM-backed Launch Base art and audio, mouse/pad controls, pause, help and saved records complete the game without changing stock gameplay.
- Added [hello-scene](examples/hello-scene/README.md), a two-class starter for mod scenes (Sonic collecting rings over Angel Island's background), and `examples/build_example.py`, which builds and runs any example. `ggfmod sprites` draws every frame of a ROM sprite request, or a playable character, into one numbered PNG. In the engine's tests, `ExampleModHarness` and `ExampleModCapture` build any example scene from source and record it headless to PNG, MP4 and WAV.
- **Sitar Hero:** added a source-first arcade rhythm example with Green Hill,
  Chemical Plant and Angel Island Act 1, filtered by any supplied ROM subset.
  Seven cosmetic performers share the library; Sitar, Harp and Synth use five
  frets/strum, chords, sustains, HOPO and Star Power, while Bongos uses four
  direct-hit pads plus kick. ROM-derived timings feed curated charts, remappable
  keyboard/gamepad controls, calibration, finite results and retry. Reusable
  candidate scene APIs provide mixed-ROM art, timestamped physical events and
  bounded semantic ROM music on the consumed audio clock. ROM assets stay runtime
  inputs; career and multiplayer remain future requirements.
- `ggfmod run` of a patch mod now opens the mod's base game directly instead of the master title game picker (holding Escape still returns to it), so a creator's build-and-run loop lands in their mod; test mode keeps its configured startup. Slay the Robotnik's `play.sh` uses it to build and launch the example in one step.
- The master title takes the mouse: hovering highlights actions and clicking opens them, the carousel's arrows, neighbouring names and the wheel change game, clicking the game's name browses the list, and right-click goes back; the game browser, quit prompt, tools and start choices answer clicks too. Pointer positions map through the letterboxed viewport with the same helper mod scenes use.
- The Sonic 1 title screen accepts an optional background override (`Sonic1TitleScreenManager.setBackgroundOverride`) that replaces its scrolling Green Hill plane and backdrop while keeping the emblem, sprites and fades.
- Game modules can pin a display aspect for their session (`GameModule.requiredDisplayAspect()`, restored at the master title; trace test mode stays 4:3) and hide the level select (`GameModule.suppressesLevelSelect()`).
- **Mod scenes:** a patch mod can register a full-screen startup scene (`ModContext.registerStartupScene`) that opens in place of the base game's title screen and can hand back to that title or to the master title. Scenes draw on a `SceneCanvas` (fills, images, built-in menu text, clipping; per-draw scale, mirror, tint and flash) with RGBA `SceneImage`s decoded from PNGs, built from pixels, or rasterised from the player's ROM: `SceneRomArt` reads sprite art (Nemesis, Kosinski, Kosinski Moduled or uncompressed, with or without either S3K DPLC layout) through the game's own mappings in any palette, plus character frames, their ROM animation scripts and Tails' tails. For Sonic 3 & Knuckles it also builds zone pictures without touching the running game: `SceneRomArt.zoneBackdrop` returns a zone's background as one opaque `SceneBackdrop` image with its parallax bands (speed and cloud drift taken from the zone's own deformation tables), and `SceneRomArt.levelOverview` returns a whole act as a box-averaged miniature of its foreground over its background, cropped to the playable area and sized to a height limit (Angel Island acts 1 and 2, Hydrocity act 1, Launch Base act 1 and Sky Sanctuary act 1, each in one documented presentation state; `SceneRomArt.hasZonePictures` says which). `SceneRomArt.titleCard` returns an act's stock title card as its four sliding elements, with the ROM's slide, hold and exit timings documented so a scene can play it. `SceneRomArt.levelStages` finds runs of an act's floor with clear headroom from its collision (a per-column floor profile in each `SceneLevelStage`), and `SceneRomArt.levelForeground` renders the foreground for any rectangle of the level at full size with transparent sky, so a scene can stand characters on the real level in front of the backdrop. Scenes read named pad buttons (`SceneButtons`, with the engine menus' key repeat) and keys (`SceneKeys`), with B as back as on the Genesis, decode raw ROM tiles (`SceneRomArt.tiles`), draw a parallax backdrop in one call (`SceneCanvas.drawBackdrop`), and also get merged keyboard/gamepad input, the mouse in game pixels (left and right button edges, whether it was the last input) with wheel notches, music and sound effects, and owner-scoped text storage under the save root. The mod validator accepts the static code javac itself generates for `assert` and for a `switch` over an enum. A mod can fix its session's display width without a game patch (`ModContext.requireDisplayWidth`), and scenes can offer tools a debug entry point (`DebuggableScene`). Every scene callback runs inside the mod fault boundary, and the mode reports itself as `GameMode.MOD_SCENE`.
- **Mod scene level kits and streaming images:** `SceneRomArt.levelKit(zone, act)` returns any supported act's building blocks for scenes that assemble terrain of their own: the foreground layout as a grid of layout blocks, each block rendered alone with transparent sky, each block's primary-path collision as a pixel mask (empty, top-solid or solid), the playable area, the zone's background and its palette. Sonic 1 and Sonic 2 offer every act their detached decoders can build (all but Sonic 2's Hill Top and Wing Fortress); Sonic 3 & Knuckles offers zones 0-12 and Hidden Palace. `SceneImage.streaming(width, height)` makes an image a scene re-renders every frame with `update(pixels)`; the renderer refreshes the same GPU texture instead of uploading a new image each frame.
- Restored Sonic 1 percussion on direct gameplay launches by applying the ROM’s DAC enable at audio initialization and FM6 stereo routing at music load.

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
  line. Explicit development-directory launches show their active mod, details,
  and notices in a read-only Mod Manager without changing installed-mod settings.
  The API descriptor remains the version/publication authority; this
  branch rollover does not publish or freeze it.
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

- **Gameplay capture tool:** a capture can now declare the ring count its route carried in
  (`--rings`). A boss filmed from a positioned start otherwise begins on no rings, where the
  first touch is fatal and the fight cannot be filmed at all.

- **Plane opacity probe:** a development tool answers, from the decoded ROM layout
  alone, whether the plane behind another can show through at a coordinate. Where a
  before/after frame capture cannot separate "wrong pixels drawn" from "no pixels
  reachable" -- both look byte-identical -- this separates them. It settled why Lava
  Reef Act 1's dome background change produced no visible difference: that act's
  foreground is opaque across the whole dome. No gameplay or rendering behavior changes.

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

## Build and release

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
  SDK templates were missing from the same file and are now embedded as well. A
  structural guard fails whenever a runtime resource under `src/main/resources` is
  unreachable from that config, so the drift cannot reach a shipped bundle again.

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
  for shared execution slots across worktrees. Linux admission can overlap up to three runs
  when conservative memory/CPU reservations fit, crediting each running job's measured
  usage instead of counting it twice, while keeping each worktree exclusive and
  preserving a serial override. Single-fork trace and audio profiles no longer force
  exclusive runs, and a bounded log (`maven_queue.py --stats`) records queue waits, holds
  and peak memory. Waiting requests favour short checks, with aging and
  bounded backfilling to protect large runs from starvation. An opt-in lean lane
  bounds both JVM heaps for small focused checks and accounts for their smaller
  reservations alongside normal runs. A busy worktree's aged request retains its
  place without blocking unrelated trees. Optional profilers measure process-tree RSS/CPU
  for ordinary/guard runs and per-test allocation, sampled peaks and repeated post-GC
  retention for explicitly selected tests. No task registration, validation receipts,
  cumulative budgets or retry gates are needed. Per-invocation category timeouts exclude
  queue waiting. Full CI and release validation remain unchanged.

- **CI trigger policy:** `develop` and `next` run the smoke suite once a branch has had
  no push for 30 minutes, so bursts of pushes cost one run; non-draft
  pull requests into any branch run branch policy, the full suite and structural guards;
  master pushes keep full release validation, packaging and publication. Feature and
  bugfix pushes no longer start CI, and the ROM-fixture jobs are manual opt-ins again.
- **Release-line integration:** preserve hosted release builds, snapshot policy
  checks, current launcher artifact selection, Linux packaging, and automatic
  publication on master pushes from the 0.6 release branch.
- **Rollover-safe tooling:** SDK scaffold dependencies and the complete-audio
  launcher derive artifact versions from build metadata. Keep the unpublished
  API candidate independent from the engine branch version.

See the [development ledger](docs/changelog/v0.7-prerelease-detailed.md) for
integration evidence and the [release summary](docs/changelog/v0.7-release-summary.md)
for scope and limitations. No new feature is certified by this rollover alone.
