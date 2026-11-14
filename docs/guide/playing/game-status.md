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
| [Sonic CD](#sonic-cd-development-preview) | Development preview on `next`, from your own USA disc image | [Known gaps](#known-gaps-2) |

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

**Status: on the `next` development line. It is on the master title shelf when
your USA disc image is in the ROM folder; it also runs in tests, trace replay
and the capture tools.**

Sonic CD runs from your own copy of the **USA** Mega-CD release. Nothing from
the disc is in the repository. The engine reads the disc image directly: the
data track's program files and the CD audio tracks. No BIOS image is needed;
the backup RAM format the BIOS writes is reimplemented. The Japanese and
European releases are not supported: the sound and backup RAM loaders refuse
a disc whose header is not the USA release. A header check does not certify a
dump.

### Disc setup

Point the engine at the disc image (the `.cue` sheet with all 35 track files
beside it) in either of two ways:

- set `roms.soniccd` in `config.yaml` (or the **Sonic CD disc (.cue)** row of
  master-title **Settings**) to the `.cue` file's path; or
- leave it blank and put the disc in a folder of its own inside your ROM folder
  (the folder holding `config.yaml`, or the one `roms.directory` names). The
  engine looks there and up to two sub-folders down, and recognises the disc by
  its data track's header, not by file names.

Sonic CD then appears on the master title shelf; with the master title turned
off, `roms.default: scd` starts it directly. A `roms.soniccd` path that is not
the USA disc is refused, not replaced by the folder search.

When you start it, the engine checks every track against the USA release
(Redump 29188) before the title appears. Another release, a missing track or a
damaged or re-encoded track is refused with a message that says which, as a
wrong cartridge ROM is. If no disc is found, the card reads DISC MISSING and
Start shows where the engine looked.

For tests and the capture tools, the JVM property
`-Dsoniccd.media.root=<parent>` names a folder whose `USA` sub-folder holds
the `.cue`; when set, it is used instead of `roms.soniccd` and the ROM folder. See
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
  stage-select secret, Time Attack, the sound test and its secrets, best
  staff times, the opening video and both endings with credits.
- Saves: the backup RAM, in the format the BIOS writes, is kept in
  `saves/soniccd/slot0.json` beside the other games' saves (`openggf.saveRoot`
  moves the folder). It is written whenever the game writes its backup RAM:
  New Game, the save after each zone, Time Attack records and initials, and
  the title's unlocks. CONTINUE, Time Attack times and unlocked menu entries
  survive a restart. A missing file starts like a console with new backup
  RAM; a damaged file is kept as `slot0.json.corrupt` and the game starts on
  fresh backup RAM. The RAM DATA screen is not hosted, so the in-game way to
  erase a save is missing: delete `saves/soniccd/slot0.json` to start over.
- FM sound effects, CD audio tracks, and PCM music and sound.

### Known gaps

- Not hosted: Visual Mode, D.A. Garden, the pencil test, Thank You, Comin'
  Soon, the attract demos (the opening video plays; the stage and special
  stage demos do not), the backup RAM manager (RAM DATA), Special Stage 8
  from the sound test. Each is a recorded "not
  hosted" step: choosing RAM DATA, D.A. Garden or Visual Mode on the title
  menu (the last two appear once unlocked) fades out and returns to the
  title, and an attract demo is skipped.
- The master title has no Setup page for Sonic CD: it always starts with
  Sonic alone, under the all-games display and rewind settings.
- Special stage results graphics, pause and exit fade are not drawn.
- Robotnik's projector in the Past can be destroyed, but the animals in its
  hologram are not drawn.
- Native frames dropped while the CPU is busy (lag frames) are not modelled;
  the engine reimplements the game, not the console's CPU timing. How long a
  stage load and the opening video's disc wait take follows timings measured on
  an emulator under `gameplay.loadTimeSimulation` (`FAST`, the default, and
  `PROFILED`); `NONE` uses no measured timing. Other disc loads are not
  paced. See
  [known discrepancies](../../status/known-discrepancies.md#sonic-cd-hardware-timings-not-modelled).
- Whether the music keeps playing while paused has not been checked against a
  console.
- Wider views draw full pictures, but no native reference exists to check
  them.
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
