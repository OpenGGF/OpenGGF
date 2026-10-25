# OpenGGF 0.7.prerelease — Development changelog

Unreleased. The earlier development themes remain in
[CHANGELOG.0.7.md](CHANGELOG.0.7.md).

## Gameplay and presentation

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
  Free objects, including released animals outside the main view, retain native
  appearances when switched to Puppet/Pose within their cast activation windows,
  including native sprite priority and foreground-wall occlusion in directed films.
  Capturing a native appearance preserves its animation phase and reports visual
  readiness before requiring facing ownership.
  Puppet mode follows destinations and urgency through native acceleration, terrain
  collision and jump physics; floor snapping defaults on. Shared Place on floor
  direction and a batched ground CLI enumerate exact visual support and native player
  terrain proposals from decoded entry layouts or prepared scene frames, with bounded
  work, typed refusals and one-step undo for safe editor placement. Poser mode retains manual
  positioning, animation and frame selection. Every scene uses one native world
  with supplied-game performers, authored terrain and common framing, binding and
  activation-window rules. Timed object clears preserve separately owned rings;
  unpublished documents and screenplays accept only their current format. Native ROM worlds share moving objects, collision pickups, native hurt and per-actor controllers;
  bounded routes/placement targets, native flight/glide and actor/group
  camera follow use the production gameplay and rewind owners. Directed foreground,
  characters and occlusion stay aligned through camera movement. Current native
  scenes project background parallax/deformation at the directed camera without
  advancing native effect phases; separated performers share native object slots
  through independent activation windows. Inspect actual state
  and events through windowless simulation and placement/capability discovery. Live
  editor previews run in an isolated offscreen worker, preserving the active world;
  preview and export preserve logical-ROM windows and composite images.
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
  that ownership. A generated ID/context catalog reports native arena and appearance
  restrictions. The [Stageplay guide](docs/guide/creating/stageplay.md) has a
  first-film tutorial for the editor and screenplays, task chapters,
  troubleshooting, a glossary, a complete reference and a case study of the
  *Day Off* film, whose screenplay joins the
  [directing examples](examples/stageplay-demo/README.md) beside a 26-second
  beginner scene with native reactions, timed dialogue and a single-setting jump
  comparison.

- **Recording imports:** copied BK2 recordings are bounded during decompression
  and parsing, including metadata, input rows and verification sidecars. Oversized,
  ambiguous or inconsistent files are rejected without hiding healthy recordings.

- **Time Attack multiplayer security:** identity proofs bind the complete join
  intent to the master, direct-room or relay-room role and observed TLS
  certificate. Remote browser, join and recording endpoints require TLS;
  plaintext and insecure TLS trust are restricted to literal-loopback development,
  including recording uploads. Master challenge solving is serial, cancellable and
  limited to 24 bits and 30 seconds, with one coalesced pending proof write under
  transport backpressure.
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
  podium, and the menu with who the room is waiting for), with the level moving
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
  races and in the lobby. Protocol version 5. Mods get `RunArea` (a fenced spawn for a run),
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
  time and the run panel no longer repeats it. Hosted runs can also override native HUD rows
  with `RunHandle.setHudProfile` and align compact overlays using
  `LevelOverlayCanvas.hudOriginX()`, preserving each game's artwork and HUD anchor setting;
  clearing the override or ending the run restores the zone's layout.
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

- **Putt Putt Paradise multiplayer:** Scene admission bounds aggregate clipped
  raster work, lines clip before traversal, and unchanged presentations reuse
  composed images; invisible geometry skips raster row traversal. Incoming scenes
  coalesce within ordered packet boundaries at transport and room delivery, with
  each mailbox capped at 256 entries / 4 MiB retained bytes. Ended room state stays
  final across buffered reconnects and failed sends. Hosting defaults to literal
  loopback; explicit LAN hosting displays a private session invite and verifies a
  fresh HMAC proof before admitting a guest. LAN game traffic remains plaintext.

- **Widescreen HUD position:** score, time, rings and lives can keep their
  centered native-frame position or align to the screen's left edge. Native
  4:3 positioning is unchanged.

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

- **Colour expansion:** Settings → Display offers a saved Full/Limited range
  independently of the existing colour profile. Full keeps current output;
  Limited caps palette RGB at 238 after fades and profile conversion, including
  creator colours, with live normal and underwater palette refresh. The palette
  backdrop and headless gameplay captures use the same saved setting. Underwater
  donor, appearance and texture colours share one tint policy. Palette sampling
  follows each texture's own row capacity when sidekick banks are reclaimed or
  independent graphics hosts retain different texture sizes.

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
  direct ROM location, bounded setup badges, explicit Save changes actions,
  clearer inherited values, calmer backgrounds and more readable text,
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
  prompts for the pad in use. It is the sole built-in editor UI, with world-space
  editing guides underneath; a separate OpenGGF-Editor provider can replace it
  through the window-chrome extension. Stock Emerald Hill can be adopted, playtested
  and exported. A full clone keeps unplaced library chunks whose art lies outside the
  decoded pattern table, drawing them transparent; placed ones still fail. Sonic 2
  placements may carry the Y-word bit 12 that the ROM's object loader never reads.

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
  from it the last drawn strip stays. Angel Island 2's water, trickle and torch
  colour cycles continue from Act 1's cycle counters across the act change, as in
  the original, instead of restarting, and the frame of the act change still runs
  Act 1's cycle. A full level load or restart clears the counters. Angel Island 1's
  water cycle pauses only from the plane intro's explosion until Knuckles leaves,
  continues correctly out of the intro's fire colours, and changes to its normal
  colours on the original frame. Knuckles' Angel Island 1 starts with his own four
  palette colours, and the main palette reloads at the original camera position for
  every start without the plane intro.

- **S3K special stage presentation:** Blue Sphere players and their tails use
  the original perspective projection through jumps, with fixed ground shadows
  under the players. Distant spheres retain the original shared mapping frames
  and tile sizes. Overlapping spheres use the original sprite-table order and
  compare the winning pixel's priority against the floor and HUD planes.
  The results card sits on the locked-on game's grey backdrop
  instead of white, matching native frames of won and failed stages.
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
  Previously the level stayed black and then appeared at full brightness. Each fade
  step and each move of the card reaches the screen with the same frame's sprites and
  HUD, one frame after it is computed, as the original's palette and sprite uploads
  do, and a rewind shows what was on screen. The data
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
  Hydrocity fan timing. Hydrocity's rising hand now lands and carries players
  after moving and respects transfers to another support; Mega Chopper retains
  the original capture control lock and both logical left/right bits until release. Ending reunion loads use measured native decompression
  costs, and the credits start on the original frame. Timing diagnostics distinguish
  verified instruction terms from work that is still unpriced. Giant-ring capture retains
  the original blank-frame ownership and art-reload state; ordinary animation
  is released again on return. Robotnik's ship stays with the beaten boss through
  its defeat pause before flying off, as in the original. Boss water/effect
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

- **Spin dash camera:** While a spin dash charges, and on its release frame, the
  camera eases back toward its normal height as it does when standing, as Sonic 2
  and Sonic 3 & Knuckles do. A look up or down no longer holds until the dash ends.

- **Tails appendage state:** The tail's animation and previous animation remain
  independent through scripted animation changes. Flick retains its final picture
  until the next native dispatch begins Swish, including after rewind.

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
  Fresh level loads submit their Nemesis art before the title wait, and the
  first title iteration prepares that queue without dispatching the card twice.
  Title entry preserves level rebinding through callbacks while keeping the
  hardware tail outside the retained presentation owner.
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
  exactly. Unordered rider sets encode stable team membership independently of
  allocation history, while ordered collections retain their encounter order.
  Launch Base falling platforms keep their riders and Marble Garden
  sinking mud keeps each player's sink depth through rewind. Rewinding to the first
  frame of a Lava Reef load replays its background setup: act 2 no longer refills
  its background or adds a second Death Egg, and Knuckles keeps act 1's background
  chunk.

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

- **Capture and controller tools:** Input branches reject checkpoints whose pending
  fresh assembly or transition callback cannot be restored by gameplay rewind.
  An individual trace segment resolves its validated containing run's declared
  movie, preserving shared-movie priority and rejecting unsafe source aliases.
  Closing or rebooting a headless capture releases its owned audio ROM reference,
  so a subsequent title and saved-slot launch can read a fresh ROM in the same process.
  Silent headless boots also retire inherited audio backends before playback, keeping
  reused capture processes off desktop audio devices.
  Native PCM live checks admit graphics prerequisites before running capture, so
  boot, gameplay and audio failures fail instead of being skipped as unavailable GL.
  Standing-geometry probes retire their private audio manager through its complete
  lifecycle while preserving caller-owned audio.

- **Mod API source checks:** Commit hooks allow implementation changes inside
  established engine-internal nested terminals without a false signature-pin
  update. Nested identity and visibility, outer and sibling API declarations,
  and candidate/published pin enforcement remain checked against Git snapshots.

- **Runtime ownership:** A loop-lifetime suspended visit keeps admission, picture
  readiness, parking, saved state, resume and retirement under one authority;
  the title keeps its card, textures and zoom presentation. Live rewind retains
  its gameplay owner and outgoing
  input, audio and fade cleanup through suspension and replacement. Native lock-on
  rewind publications keep their selected module owner across fresh service
  incarnations, while both exposed and suppressed native services remain protected
  from creator capture. Destination
  history starts after deferred title-card assembly, and replay cannot mutate a
  replacement owner's history. Floors and checkpoints include completed frame
  publication; inactive outgoing worlds cannot seed new history. Audio's private
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
  allocation and immediate rewind redraws. SAT replay uses a
  graphics-owned geometry value and one tile-expansion implementation while
  preserving the existing candidate Mod API submission signature. Stock title
  previews use detached game-owned providers, preserving animated pixels and
  catalogue ROM views without consulting a suspended game's live services.
  Title-card mapping and drawing now share one domain owner. Event and object-service
  bridge allowances pin callers and call counts, and special-stage rewind coverage
  follows the registered provider through replacement and retirement. Standalone
  audio callers supply their selected profile to the reusable presentation host;
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
  current renderers after rewind. ICZ miniboss snapshots restore progressed helpers;
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
  parity are not covered.
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
S3K item monitors retain contents only when a later object slot admits them.
A full tail leaves a remembered broken shell without an icon or reward, and
rewind can capture that state immediately after the break.

The AIZ2 bombing ship's two propellers are now native objects. They take
the slots after the ship, turn at the original three-frame rate, and
disappear when the ship's departure redraw begins. The departing ship hands
its own slot to the small Eggman craft and starts the background-tree maker
after it. The loop boundary moves only once that redraw completes.

The AIZ2 background-tree maker now places all seventeen trees, one per frame,
in the slots after itself. It retries when no slot is free and gives each tree
its original drawing priority.

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
- **Multi-game controlled mods:** one code mod can provide per-game patches with shared
  objects and services, alongside an optional mutator catalogue. Patches need no scene,
  including in development captures; catalogue-only packages retain their bounded role.
  Startup scenes can launch stock acts and switch supplied games
  or teams; frame controllers supply reproducible pad input and capture layout-aware
  typed text. Course restores preserve engine ownership, and supplied testkit scene
  capabilities survive storage isolation. Custom SFX can declare a load-time DAC
  rendering. These additions retain the unpublished Mod API 0.7 candidate.
- **Metropolis boss art:** the Sonic 2 act-3 arena registers its ROM boss sheet both
  at level load and through the boss PLC.

## Maintenance and release preparation

- **Local ROM qualification:** Release preparation can bind the existing bounded
  AIZ1/HCZ1 route pilot to a reviewed candidate commit, reject changed inputs and
  require every named case to execute without skips. Original ROMs remain local
  or private; hosted builds and automatic publication retain their existing flow.
