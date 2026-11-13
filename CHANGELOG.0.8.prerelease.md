# OpenGGF 0.8.prerelease — Development changelog

Unreleased. Development since the [0.7.20261105](CHANGELOG.0.7.md) release. Add entries
under the matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).


## Gameplay and presentation

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

- **Art upload stutters:** changing level art, animated tiles or a tilemap no
  longer makes the frame wait for the graphics driver. Uploading straight from
  memory made the engine wait until the driver had worked through everything
  queued before it. On an integrated GPU that also draws the desktop, one small
  update could take 100-700 ms: the Angel Island 1 intro, Hydrocity, Flying
  Battery, and about 230 ms at the Marble Garden act change. The art now goes
  through a pixel buffer that the GPU copies from, so the frame carries on. Over
  twelve runs per side on the integrated GPU, uploads slower than 16.7 ms after
  the level had loaded went from seven (worst 701 ms) to none (worst 8 ms); the
  discrete GPU was already below 12 ms. Waits while a level loads remain. Each
  art upload also stopped allocating a version record and a boxed tile number,
  about 16% of Angel Island's per-frame allocation. Pixels are unchanged.

- **Blue Spheres:** new under **Extras** when the Sonic 3&K and Sonic 1 ROMs
  are both present. It is the game Sonic & Knuckles starts with Sonic 1 locked
  on, rebuilt from the S&K ROM: the title and menu, endless generated stages,
  12-digit level codes, Sonic or Knuckles, the results screen and the perfect
  showcase. Stages run on the Sonic 3&K special stage. Sonic 1 is read only for
  its header. One intentional difference: any button opens the menu from NO WAY!,
  where the cartridge needs A, B and C together. The single fixed stage other
  cartridges unlock is not offered. Blue Spheres is an extra of Sonic 3&K, not a
  separate game.

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
    TIME and is hidden when monitors are off.
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
  immediately, and the S2 half-pipe ring HUD composes names from its native
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

- **Seeded Data Select captures:** `GameplayCaptureTool --save-slots <file.json>`
  writes declared save slots through the production save manager into the
  capture's private save root before a title-first capture, and `--extra-teams`
  sets the Data Select extra team combos for that capture. The Sonic 3&K hero
  reel uses both to show custom-team saves. Title-first headless boots also run the
  donated Data Select warm-up that Engine runs before its startup mode, so a
  Sonic 1 or Sonic 2 capture with S3K donating shows the generated zone previews
  instead of S3K's placeholders.

## Maintenance and release preparation

- **All-level performance sweep:** `tools/performance/level_sweep.py` plays every
  trace fixture and Sonic 3&K route movie through the frame-time probes, one at a
  time. It reports frame time, allocation, garbage collection and hitches per
  level and how far each replay got. It lists the slow frames that recur across
  runs, and it matches profiler samples, including JDK 25 method traces, to those
  frames. `RouteFrameBudgetProbe` takes a route's launch settings (`--game`,
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
  behind the existing runtime entry points. Retiring multipart solids expires
  owned contact bits and frame history without releasing player state. Contact
  restore discards uncaptured future observations. Sole spawn-keyed dynamic solids
  now expire contact bits without clearing surviving shared owners. Constructor-child
  rewind adoption uses captured parent families, preserving interleaved structural
  links; independent children can recreate when a parent defers to player refresh,
  and CNZ defeat candidates retain their ship family. Generic recreation installs
  construction services centrally; supported exact-spawn recipes rebuild once,
  avoiding discarded constructor graphs while preserving custom hooks and the
  fallback for declarations without that constructor. Destroyed
  object paths share unload and retirement phases within their existing schedules.
  Ordinary family/reference/coverage guidance points to the actual owners.
  Production badnik and contact witnesses, shared coarse-X arithmetic with
  explicit native/wider bounds, and
  a typed rewind HUD frame make contribution paths easier to follow. HCZ owns
  its next screen-shake value without using the renderer as scratch state.
  Failed trace replays print their exact test/report/triage route; triage reads
  compressed fixture auxiliaries, preserves pre-trace evidence and distinguishes
  incomplete or unknown completion from a completed comparison. Retained raw-field
  diagnostics follow that storage and are exercised on successful test paths.

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
