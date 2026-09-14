# OpenGGF 0.7 — Development changelog

Unreleased. This line carries the work promoted from `next` after 0.6.20260911.
The [0.7 roadmap](docs/project/v0.7-roadmap.md) prioritizes complete stock-game
campaigns; feature/API publication remains subject to the 0.8 roadmap.

## Gameplay and presentation

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
  The CNZ slot face, title level-select code, Super sound-test code and independent
  results-message motion also use the KiS2 presentation. Remaining route and
  hardware-rendering limits are listed in the known-discrepancies entry and
  `docs/kis2/BRANCH_DIFFS.md`.

- **HCZ1 miniboss:** retain the vertical arena lock and full rocket slowdown;
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
  pattern animation pass before gameplay, as the ROM does. The Act 1 boss arms
  clamp their angles and release their chain state in the original order; defeat
  preserves the native wait and score bonus before the end sign, and the ending
  pose retains existing plunger support. Zone-owned tumble
  presentation and snake-platform standing ownership follow the original routines. The donated Sonic 1
  route clears the elevator squeeze with an ordinary run-up and timed roll.
  Upright spikes use their animated FBZ art, while wall spikes retain the shared
  sideways artwork; both tile banks participate in renderer refresh.
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
  effects. Giant-ring sanctuary entry uses the same emerald ceremony, palette,
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
  `bk2-input-authoring` document the workflow.
- **Existing feature foundations:** retain editor, racing, prepared-loading,
  audio-core, and mod regression coverage while stock campaigns mature.

- **Java runtime ownership:** debug shortcuts, rewind constructor defaults, static
  object-art remapping, native player selection, shield playback, CPZ boss-child
  presentation and donated preview capture use shared implementations. Sidekick
  diagnostic construction is separated from CPU decisions. Results bonus digits
  use one ROM-pattern writer while retaining each game's score and tally policy.
  Monitor contents share icon drawing while keeping their visibility and lifetime rules.
  Deferred lost-ring spawns retain their queue across rewind and release reserved
  slots when the owning level is reset or rebuilt. Public profile adapters retain
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
  for a shared execution slot across worktrees. No task registration, validation receipts,
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
