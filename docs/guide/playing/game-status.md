# Game Status

Reviewed against the **0.7 development branch on 2026-10-23**, before the
0.7.20261105 release; this is not release certification. The [release notes](../../../CHANGELOG.md) describe each release.

Implementation, playable routes and verified ROM parity are separate claims.
The [level coverage backlog](../../status/level-test-coverage.md) records tested
act/character combinations and inherited gaps; the
[trace frontier log](../../status/trace-frontier-log.md) records replay evidence.
This documentation review did not rerun those gameplay checks.

| Game | Status | Read before reporting a problem |
|---|---|---|
| [Sonic 1](#sonic-the-hedgehog-s1) | Broadly playable from start to finish | [Known gaps](#known-gaps) |
| [Sonic 2](#sonic-the-hedgehog-2-s2) | Broadly playable from start to finish, with remaining parity gaps | [Known gaps](#known-gaps-1) |
| [Sonic 3 & Knuckles](#sonic-3--knuckles-s3k) | Broad playable campaign implementation, with remaining route and parity gaps | [Remaining limits](#remaining-limits) and [S3K known bugs](../../status/s3k-known-bugs.md) |
| [Sonic CD](#sonic-cd-development-preview) | Development preview on `develop` (0.8), from your own USA disc image | [Known gaps](#known-gaps-2) |

---

## Sonic the Hedgehog (S1)

**Status: Broadly playable from start to finish.**

### What works

- All 7 zones (Green Hill through Final Zone) are loadable and playable.
- 6 boss fights implemented (GHZ, MZ, SYZ, LZ, SLZ, FZ).
- Special stages functional with emerald collection.
- Title screen, level select, title cards, and HUD.
- Labyrinth Zone water system with drowning mechanics, air bubbles, and underwater
  palettes.
- Per-zone level events (camera boundaries, zone-specific triggers).
- Ending sequence and credits.
- Demo playback.
- SMPS audio with S1-specific driver configuration.

### Known gaps

- The [S1 object checklist](../../../S1_OBJECT_CHECKLIST.md) lists all 80 placed
  types as implemented; behavior and complete-route parity can still differ.
- Edge-case physics behaviors (specific slope interactions, push block scenarios) may
  differ from the original.
- SBZ2-to-FZ transition sequence may have minor visual differences.
- Special stage parity is functional but not pixel-perfect.

### Notable quirks

- S1 uses 256x256 metatiles (vs. S2/S3K's 128x128), which affects level layout
  granularity.
- Loop plane switching uses a different mechanism than S2 (coordinate-based triggers
  rather than object-based switchers).

---

## Sonic the Hedgehog 2 (S2)

**Status: Broadly playable from start to finish, with remaining parity gaps.**

### What works

- All zones playable: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ (3 acts), SCZ, WFZ, DEZ.
- Boss implementations for EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ, WFZ, and
  both DEZ bosses (Mecha Sonic and Death Egg Robot), plus the Robotnik escape sequence.
- All 122 unique placed object types found in the ROM are implemented (see the [object checklist](../../../OBJECT_CHECKLIST.md)); registration alone does not establish behavioral parity.
- Special stages functional with emerald collection.
- Tails CPU AI follower with flight and input replay.
- Super Sonic with per-game physics.
- Title screen, level select, title cards, HUD.
- Complete credits and ending cutscene system.
- Water system for ARZ and CPZ.
- HTZ earthquake and lava systems.
- Per-zone level events across all zones.
- Mecha Sonic's DEZ attack loop follows the shipped ROM's outer `ObjectMove`
  and child-alignment ordering; the existing Mecha Sonic and Death Egg Robot
  implementations pass the dedicated DEZ ending replay.
- DEZ's opening exterior window samples the ROM-seeded Plane B camera origin,
  so its star field is visible and scrolls from level start.
- Special Stage checkpoint wings remain fixed while the independent hand peer
  bobs for both successful and failed ring checks.
- Demo playback.
- Two-player Competition: Emerald Hill, Mystic Cave, Casino Night and the
  special stage, with results.
- SMPS music and sound effects.

### Known gaps

- MTZ boss is implemented but may have minor accuracy issues.
- Some visual effects (screen distortion, specific palette transitions) may differ
  slightly from the original.
- Oil Ocean surface collision and oil slides are implemented; complete act,
  character/team, donor and viewport coverage remains incomplete.
- Item-placement debug mode is unavailable.
- Two-player Competition shows one interlace field, splits exactly at line 108,
  never shows the original's lag-frame slowdown or torn sprites, and has no
  live rewind. Outside Competition, Player 2 bindings control the configured
  sidekick/manual-input path.

### Notable quirks

- S2 is the original reference game for the engine. Its long implementation
  history does not imply that every route or configuration has current parity evidence.
- Cross-game feature donation uses S2 as the default donor for sprites and spindash.

---

## Sonic 3 & Knuckles (S3K)

**Status: Broad playable campaign implementation, with remaining route and parity gaps.**

Development includes the late game and native endings. Current evidence covers
bounded routes and configurations; an uninterrupted, fully verified campaign for
every character, team, donor and viewport remains unqualified.

### What works

- Angel Island and Hydrocity have intro, boss, results, act-transition and
  outgoing-zone implementations, with route and rewind coverage.
- Marble Garden, Carnival Night, Ice Cap, Launch Base and Mushroom Hill have
  substantial traversal, event, boss, scroll, palette and transition implementations.
- Flying Battery, Sandopolis, Lava Reef/Hidden Palace, Sky Sanctuary, Death Egg
  and Doomsday have gameplay and progression implementations. Their individual
  [act matrices](../../status/level-test-coverage.md) record route evidence and limits.
- Sonic, Tails and Knuckles have playable character paths; Knuckles has glide,
  climb and character-specific routes.
- Title, level select and eight-slot Data Select screens, with team selection.
- Blue Sphere special stages and all three bonus-stage families: Gumball,
  Glowing Spheres and Slot Machine.
- Shields, underwater state, palette cycling, dynamic art loading and rewind.
- Native ending, credits and post-credits flows, including Sonic/Tails and
  Knuckles branches. The [ending matrix](../../architecture/validation/levels/s3k-ending.md)
  separates lifecycle/route evidence from remaining presentation coverage.
- S3K SMPS audio, including the banked Z80 driver and DPCM.

### Remaining limits

- Full campaign parity is not certified. Route, checkpoint/respawn, rewind,
  character/team, donor and widescreen coverage is uneven.
- Canonical trace frontiers remain open, including timing, sidekick behavior,
  object lifetime/order and boss/event interactions. A focused passing test or
  an implemented zone does not close its complete-run trace.
- Native visual and audio parity remains incomplete, including broader ending
  scenes and special/bonus-stage products.
- Knuckles' Launch Base Big Arm route has a native fight, grab/throw, defeat,
  capsule/results, falling-floor escape and Mushroom Hill handoff. Its documented
  canonical replay boundary remains distinct from those implementation and
  focused rewind results.

See [S3K known bugs](../../status/s3k-known-bugs.md) for defects and
[S3K discrepancies](../../S3K_KNOWN_DISCREPANCIES.md) for intentional departures.
These records include dated evidence; consult the latest entry for a particular
route instead of treating an old pass as a current whole-game guarantee.

### Cartridge scope

Sonic 3 and Sonic & Knuckles images can supply the combined S3K game through ROM
discovery. That does not promise each standalone cartridge's complete original
campaign behavior. See [ROM discovery](../../../CONFIGURATION.md#rom-files).

---

## Sonic CD (development preview)

Stageplay's development preview can open supplied CD media, select a native
stage including its period, and prepare a native Sonic scene or cold-level
route tape. Native/frozen/baked object pictures preserve foreground occlusion
in a disc-backed Palmtree Panic Past canopy regression. Poser body and accessory
lookups use the selected stage program. Disc-backed scene cues exercise CDDA
and the Past-stage PCM driver through offline production audio. This is not authoring
certification; see the
[bounded validation record](../../architecture/validation/2026-11-16-sonic-cd-stageplay-editor.md).

**Status: on the `develop` (0.8) development line. It is on the master title shelf when
your USA disc image is in the ROM folder; it also runs in tests, trace replay
and the capture tools.**

Sonic CD runs from your own copy of the **USA** Mega-CD release. Nothing from
the disc is in the repository. The engine reads the disc image directly: the
data track's program files and the CD audio tracks. The engine implements the
backup RAM format the BIOS writes, so it needs no BIOS image. Tests can also
verify and play FM effects from the Japanese and European discs. See the
[regional media validation](../../architecture/validation/2026-11-18-sonic-cd-regional-media.md)
for the remaining startup and gameplay dependencies.

### Disc setup

Point the engine at the disc image (the `.cue` sheet with all 35 track files
beside it, or a lossless `.chd` made from that set with MAME's `chdman createcd`)
in either of two ways:

- set `roms.soniccd` in `config.yaml` (or the **Sonic CD disc (.cue/.chd)** row of
  master-title **Settings**) to the `.cue` or `.chd` file's path; or
- leave it blank and put the disc in a folder of its own inside your ROM folder
  (the folder holding `config.yaml`, or the one `roms.directory` names). The
  engine looks there and up to two sub-folders down, and recognises the disc by
  its data track's header, not by file names.

Sonic CD then appears on the master title shelf with its disc-backed native title
preview and optional hub theme. Menu playback owns its own disc/audio session,
so it does not replace suspended gameplay audio. With the master title turned
off, `roms.default: scd` starts it directly. A `roms.soniccd` path that is not
the USA disc is refused, not replaced by the folder search.

When you start it, the engine checks every track against the USA release
(Redump 29188) before the title appears. A CHD passes exactly when its decoded
tracks equal the Redump set, so a CHD made from the Redump BIN/CUE is accepted
and one made from another dump is refused like that dump. OpenGGF reads CHD
version 5 with the `cdzl`, `cdlz`, `cdfl` and `cdzs` codecs (any of chdman's
CD defaults) or uncompressed; a delta CHD that needs a parent, an older CHD
version, and a non-CD CHD are refused with a message saying which. Another release, a missing track or a
damaged or re-encoded track is refused with a message that says which, as a
wrong cartridge ROM is. If no disc is found, the card reads DISC MISSING and
Start shows where the engine looked.

For tests and the capture tools, the JVM property
`-Dsoniccd.media.root=<parent>` names a folder whose `USA` sub-folder holds
the `.cue` (or, with no `.cue` there, a `.chd`); otherwise
`-Dsoniccd.disc.path=<file>` names the `.cue` or `.chd` itself. Either one, when
set, is used instead of `roms.soniccd` and the ROM folder, and the media root wins
over the disc path. The queued test wrapper sets `-Dsoniccd.disc.path` from a USA
CHD in the project folder. See
[configuration](../../../CONFIGURATION.md#sonic-cd-disc-and-native-captures).
For example, `GameplayCaptureTool --game scd --zone R11A` films Palmtree Panic
act 1 Present; `--zone ppz --act N` counts a zone's stages 1 to 10 (act 1
Present, Past, Good Future, Bad Future; act 2 the same; act 3 Good and Bad
Future). `SonicCdEndingCaptureTool --ending good|bad` films an ending.

### What works

- All 70 stage programs load: seven zones, acts 1 and 2 in four time
  periods each, act 3 in the Good and Bad Future.
- Zone presentation (scrolling, palette cycles, animated tiles, water) and the
  zones' objects, badniks and all seven bosses.
- Sonic on Sonic 1's movement with Sonic CD's own rules: spin dash, super
  peel-out, the extended camera, 3D ramps, the per-zone player routines and the
  idle timer: left alone long enough, Sonic gives up: he jumps, falls and dies.
- Time travel: time posts, the warp and its cutscene, good and bad futures.
- Special stages (floor, UFOs, timer and presentation), the title screen, the
  stage-select secret, Time Attack, Special Stage 8 from the sound test,
  the sound-test pictures, best staff times, the opening video, both
  special-stage attract demos and both endings with credits.
- Clearing Special Stage 8 opens an OpenGGF acknowledgement with the disc's
  ending music. Choose **Back to title** to dismiss it. The visit preserves
  your saves and Time Stones.
- The title's unlockable **Visual Mode** opens an OpenGGF browser for the
  opening video, both endings and the silent **Pencil Test** from the
  verified USA disc. **Back** stops the current video and returns to the
  selected row. Pencil Test also returns there when the clip ends.
  When the good ending completes, Visual Mode opens a static **Thank You**
  contact sheet of disc tiles. **Back** returns to the Good ending row.
- The stage-select **Comin' Soon** row opens an OpenGGF contact sheet of tiles
  from the verified USA disc. **Back** returns to the title.
- The unlockable **D.A. Garden** opens an OpenGGF browser for music tracks
  from the disc. Choose a track to play it, or choose the playing track
  again to stop it. **Planet** (F1 or the controller's Details button)
  opens a still view of Little Planet in the Present. Music continues
  while the art is open. Back returns to your selected track, and Back
  from the track list returns to the title.
- Saves: the backup RAM, in the format the BIOS writes, is kept in
  `saves/soniccd/slot0.json` beside the other games' saves (`openggf.saveRoot`
  moves the folder). It is written whenever the game writes its backup RAM:
  New Game, the save after each zone, Time Attack records and initials, and
  the title's unlocks. CONTINUE, Time Attack times and unlocked menu entries
  survive a restart. A missing file starts like a console with new backup
  RAM; a damaged file is kept as `slot0.json.corrupt` and the game starts on
  fresh backup RAM. **RAM DATA** opens an OpenGGF prompt. Choose
  **Reset Sonic CD save** to restore disc defaults for campaign progress,
  Time Stones, unlocks, and Time Attack records. The default choice keeps the save.
  Other backup RAM files are preserved.
- FM sound effects, CD audio tracks, and PCM music and sound.
- Pause holds a regular special stage. The stage fades to white on exit,
  and the results show the ring and time bonuses. Press Start to finish the
  tally and return through a second white fade.
- Held-key live rewind in stages and special stages. Stage rewind restores the
  zones' rings, switches, lamps, bosses and Sonic's idle timer, as well as the
  CD audio and backup RAM state; special-stage rewind restores the floor
  pipeline, UFOs, effects and Time Stone.

### Known gaps

- The Pencil Test clip uses OpenGGF's playback view. The native border fades
  and closing effects remain open.
- The Thank You preview covers a static tile sample. The original screen's
  composition, animations, fades, and exit sequence remain open.
- The Comin' Soon preview shows the screen's 150 tiles as a static contact
  sheet. The original screen's composition and fades remain open.
- Ordinary-stage attract demos, the
  D.A. Garden planet animation, and the original Special Stage 8 credits presentation
  remain unhosted. The attract cycle skips ordinary-stage demos.
- CD patch mods, `any` registrations, scenes and Sonic gameplay runs use the disc
  pipeline. Authored zones support flat scrolling, one collision path, fixed-height water,
  owned objects, collectible rings, fixed vertical and horizontal springs, and ten-ring monitors.
  Other stock placements and native ring group edits need their stage context.
  Cartridge donation supports
  S1 Sonic, S2 Sonic/Tails and S3K Sonic/Tails/Knuckles; route coverage remains
  bounded to the donor-host validation record. Editor projects keep a bounded
  disc/stage source reference: native edit replay retains the loaded game owners,
  source layout semantics and object secondary bytes, project creation selects a
  zone, act and time period, and source-backed playtests reload the native owners.
  Projects can resize within the stage's collision limits. Native editor exports
  share edits in folders or ZIPs. The importer reloads the referenced stage from
  the matching disc. See the [CD mod contracts](../../modding/ggfmod.md#sonic-cd-runtime-mods).
- Sonic CD Game Setup saves Borrow from and character choices. Without donation
  it defaults to Sonic alone. CD itself cannot donate player art.
  Each donor's supported roster can also be paired as leader/follower, including
  duplicates (14 pairs tested). S1 Tails/Knuckles and S2 Knuckles are refused for
  either role with the missing donor character named. S3K elemental shields are
  exercised on the disc host. Donated life icons use their own object-art tiles,
  preserving transparent foreground cells and the native HUD.
- Robotnik's projector in the Past can be destroyed. Its flying and ground
  hologram animals animate in both Palmtree Panic Past acts, with native picture
  checks on act 1. Animal animation coverage varies elsewhere.
- Native frames dropped while the CPU is busy (lag frames) are not modelled;
  the engine reimplements the game, not the console's CPU timing. How long a
  stage load and the opening video's disc wait take follows timings measured on
  an emulator under `gameplay.loadTimeSimulation` (`FAST`, the default, and
  `PROFILED`); `NONE` uses no measured timing. Other disc loads are not
  paced. See
  [known discrepancies](../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled).
- OpenGGF pauses music with gameplay. The USA disc's level program explicitly
  pauses CD audio or Past-stage PCM, and the special-stage program pauses CD
  audio through the BIOS. This is verified in the disc program instructions
  (`PauseMusic`, scddisasm/src/Level/Main.asm:620-643; `MainLoop`,
  scddisasm/src/Special Stage/Sub.asm:255-274). Physical-console audio timing
  remains unmeasured.
- Wider stage views extend object visibility, touch eligibility, load/unload
  windows and boss arena centring. Static Palmtree Panic background bands
  retain their 512-pixel repeat beside moving bands; the time tunnel fills
  the selected width. Title cards, results and game-over text centre in the native
  frame, independently of the HUD preference, as in the cartridge games. All HUD
  pieces (including period and time-warp icons) follow the centred or left-edge
  HUD setting in EDGE and NATIVE modes; time posts remain world-positioned.
  At 320 these presentation offsets are zero. These are engine extensions without a native wide
  reference. In special stages, widescreen extends the scenery and floor
  beside the original playfield. The HUD, sprites and gameplay stay centred.
  Results and other fixed-width programs retain their original dimensions.
  Level walls and scripted world coordinates retain their native positions.
- Accuracy is measured against native recordings; how far each stage is
  certified is in the [Sonic CD stage matrix](../../architecture/validation/levels/scd-stages.md).

---

## Experimental Tooling

### Level authoring projects

The experimental editor is available from **Advanced → Editor projects** at the
master title; `debug.flags.editor` enables gameplay entry with `Shift+Tab`.
It supports named blank levels and supported layout clones for S1, S2 and S3K,
canonical imports, foreground/background painting, local block/chunk composition,
pattern descriptor flags, collision assignments, object/ring editing, and
project properties/resizing.

Complete `.ogfproject` saves reopen independently of stock gameplay saves.
Playtests use fresh runtime copies and preserve the document, selection and
history. Export checks the destination game: S1/S2 use v1 and S3K uses v2 with
explicit object family and palette ownership. Missing keys and unsupported native
features produce visible errors without discarding project data.

This is a bounded level authoring tool, not a pixel-art editor or a native zone
script/campaign studio. See the [level editor guide](../creating/level-editor.md)
for the workflow and limits.

---

## Cross-Game Features

The engine supports **cross-game feature donation**: a donor game provides player
sprites, spindash mechanics, and sound effects while you play a different base game.

| Feature | Status |
|---------|--------|
| S2 sprites in S1 | Working |
| S2 spindash in S1 | Working |
| Super Sonic cross-delegation | Working |
| S3K Data Select donated to S1/S2 | Working |
| S3K sprites in S1/S2 | Experimental |

Choose **Borrow from** in the game’s **Game Setup**. For direct launches that
bypass the hub, use `crossGame.enabled` and `crossGame.source` in `config.yaml`.
Availability does not certify every donor/character/route combination; consult the
[coverage backlog](../../status/level-test-coverage.md) for bounded evidence.

When S3K donates the Data Select frontend, the save screen stays visually S3K-native,
but save semantics remain host-owned. Slot routing, progression, clear-restart rules,
and emerald identity come from the host game. Donated host emerald colors are adapted
into the S3K save-card palette contract instead of assuming raw palette-slot parity.
