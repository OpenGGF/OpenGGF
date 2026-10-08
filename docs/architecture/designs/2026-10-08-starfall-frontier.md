# Starfall Frontier

Task: deliver a Terraria inspired mining, crafting, building and quest mod,
install it into the local development build, and commit/push a feature branch.
Integration base: `d740b7a0fadd97b2e7c104d56481a0235bdffb4c` (`develop`).
Working branch: `feature/ai-starfall-frontier`. The user explicitly requested
the main checkout instead of a worktree for this task.

## Initial delivery (`d9989e68`)

### Ownership and implementation

The adventure registers one S3K startup scene through `ModContext`. Its
528-pixel display request is part of the registration transaction. There are
no production engine, API signature, stock zone, physics, timing, PLC or
layout changes. The creator scene owns its original tile arrays and collision
rules; it does not mutate an engine `Level` or bypass the level mutation
pipeline. Stock zone/act coverage obligations therefore do not apply to this
scene world. Its own state/interaction checks live with the example.

- `Content`: immutable item enum and instance-owned recipe/quest catalogue.
- `World`: seeded terrain, discovery, inventory, terrain edits, collision,
  platforms, crafting, combat, healing, respawn and quest progression.
- `SaveCodec`: bounded, versioned GZIP/Base64 documents decoded into fresh
  worlds. It retains world edits, discovery, resources, hotbar, progression,
  RNG state, player state, enemies and projectiles. Cosmetic particles are
  deliberately excluded. A forward replay test verifies restored simulation.
- `FrontierScene`: physical/logical input, camera easing, panel transitions,
  audio, autosaves and backup recovery. Menus hold the simulation. Debug
  inspection cannot save prepared states over real progress.
- `FrontierView`: original code-authored pixel scenery and UI, deterministic
  detailing, biome color, parallax, lantern halos, damage/resource messages,
  map and menus. Drawing never advances gameplay or random state.

Sonic, his animation frames and all music/effects come from the supplied S3K
ROM through scene capabilities. The font is the original text font already
used by the other creator examples. Terrain, item icons and creatures are
new creator content, not disassembly assets or a replacement for missing
ROM content. The mod includes no Terraria art, music, names or extracted
game data. The player retains resources on death and can recall to camp;
this favors exploration and construction over punitive survival.

Progression is timber → bench → furnace → copper pick → iron/anvil → moon
crystals and sigils → three shrine wardens → starlight core and beacon.
Three surface biomes and a deeper crystal region provide the travel route.
The starting shaft has lanterns and one-way platforms; copper and iron are
guaranteed beside it for every seed. Caches provide optional equipment
resources and maximum-health upgrades. Moss uses aimed fans, Frost uses
radial shots, and Ember uses fast fans and lunges; each warns before firing.
Restoring the beacon leaves mining/building available in the same world.

## Decisions and rejected approaches

All decisions below were made against the integration base above during
this feature task; the implementation is the feature commit containing
this document.

- The first catalogue used `static final List` fields and an enum
  constructor which ORed alpha into a color. `ggfmod package` rejected these
  with `STATIC_STATE_UNSUPPORTED`. Keep catalogues instance-owned; supply
  full ARGB literals and assign scalar enum constructor parameters directly.
  The shipped jar passes validation with **zero findings**, without changing
  or weakening the validator.
- A progression fixture discovered that a generated berry bush could occupy
  the initial crafting site. Surface bushes now leave the starter camp clear;
  the production progression check then passed.
- Death originally risked removing a warden during the enemy iterator.
  Death resolves after enemy/projectile processing. A lethal-contact
  regression checks safe iteration, retained resources and an unblocked camp.
- Backpack use is restricted to food, potions and heartstones. It cannot
  convert an arbitrary resource into healing. Mining strength follows the
  selected pick, and the axe cannot substitute for a mining tool.
- Initial capture scripts used `BACKSPACE`; the existing capture tool calls
  that key `back`. Corrected scripts produced the actual scene screenshots.
  Captures must run after Maven finishes updating `target/test-classes`.

## Validation and its limits

The unchanged-base planner selected **3,057 ordinary engine classes plus
guards** because `examples/` is unclassified. This task changes an isolated
creator scene plus its test bridges and documentation. Focused checks directly
exercise its behavior and its production registration/render/storage boundary;
the full engine selection was disproportionate to those consumers. This is
proportionate focused validation, **not a full-suite pass**.

Tool environment: Java 21. Maven is available in the IDE's bundled Maven
directory and was added to the launch PATH. The actual category preflight
reported a Lua version mismatch and missing `pwsh`; no broad run was started.
Those tools are unrelated to the new mod's exercised paths.

Commands from the feature checkout:

```sh
python3 examples/build_example.py starfall-frontier
python3 tools/testing/run_categories.py --base d740b7a0fadd97b2e7c104d56481a0235bdffb4c
python3 tools/testing/run_categories.py --base d740b7a0fadd97b2e7c104d56481a0235bdffb4c --preflight
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestModSceneHost,TestModStorage,TestModValidator,TestModRegistrationRuntime' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM path>' test
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM path>' test
python3 examples/starfall-frontier/build.py --skip-engine --install
```

The six-class focused check completed with **62 outer Jupiter tests**, zero
failures/errors/skips. The final creator suite has **21 tests**, including
actual held mouse chopping, building and ignored letterbox clicks, and runs
inside the example bridge. The narrow follow-up covers the final mod edits;
the unrelated host/validator/storage checks are not repeated. The ROM scene
check ran rather than skipping. Model checks include deterministic generation,
collision, jump/platform rules, mining tiers and reach, whole-tree drops,
atomic recipe costs/station proximity, placement exclusion, wall salvage and
shelter, ammo/mana/cooldowns, health upgrades, lethal-contact respawn, unique
warden rewards, the entire objective chain, bounded invalid-save handling,
600 restored forward steps and a 22,000-step bounded-population simulation.

The progression check prepares resources and positions to exercise rules;
it does **not** establish a complete unassisted traversal of every cave or
an encounter balance playthrough. The adventure is finite and solo, with one
active world, no multiplayer, and no stock developer rewind. Packaging checks
code structure; the tests and captures supply the gameplay/UI evidence.

Actual GL captures used `ExampleModCapture`, 528 × 224 at 3×, from the existing
local S3K ROM (SHA-1 `b711a909cce238ca4af3e517a2edca306228efa5`). This is the
user's detected ROM, not a claim of canonical REV identity or Sonic parity.
Title, surface play, crafting, inventory, journal, warden chamber and map
were visually inspected. Final PNGs are kept in the explicit external task
directory `OpenGGF-captures/starfall-frontier` under the user's home. Rebuild
directories and capture saves are temporary and are removed after review.

The installed jar is the same validator-checked build. Installation enables
and trusts that exact hash while preserving other local mod entries. It is
ignored local runtime state; neither the jar nor mod trust state is committed.


## Angel Island revision

Revision base: `d9989e68e62dba5fa69b81b37b216badee9940f3`, on the same
feature branch and checkout. The user's follow-up requests Sonic movement,
firmer friction, audible sound, and S3K ROM scenery starting with AIZ.

`AngelIslandArt` loads AIZ1 background and foreground through `SceneRomArt`,
with the native zone palette. The sample bank selects fully opaque grassy
and brown 16-pixel cells from decoded native flat stages, then caches crops
for the creator's 12-pixel terrain. It uses no disassembly file as a runtime
asset. `Obj_AIZ1Tree` supplies trunks from primary level art (VRAM base 1),
and `Map_AIZForegroundPlant` supplies fronds. `ArtNem_AIZMisc1` / `Map_AIZRock`
supply cave stone. Runtime requests also load Rhinobot (object DPLC), Monkey
Dude, Bloominator, monitor, starpost, intro emeralds, rings and RobotnikShip.
The first three retain the creator enemy AI; the ship represents shrine
sentinels. Workshop items and material overlays remain authored graphics.
All ROM images and mapped sets are cached at scene entry, not decoded during
drawing. Other level biomes are deferred; new worlds generate AIZ grass across
the surface. Existing snow/ember tags remain readable and render as AIZ.

The flat tile controller now uses `Sonic_Move`, `Sonic_Jump` and
`Sonic_JumpHeight` as references: $600 running limit, $680 jump impulse,
$400 released-jump cap, $38 gravity and twice $0C acceleration for air control.
Ground acceleration is deliberately $18 and neutral friction $40, rather than
native $0C, to make construction precise. Reverse braking remains $80.
Air momentum has no artificial drag. A descending spin contact damages and
bounces off badniks. Run animation pacing follows speed. This does not claim
native movement parity: slopes, rolling and spindash remain outside this flat
controller. Held jump is input, not new persistent state, so the version-1
save contract and item ordinals remain unchanged. Save restoration still
receives identical forward inputs and reproduces the same model state.

### Audio cause and fix

The original scene called the audio facade, but normal launch enters its
startup scene before `initializeTitleScreenMode` or level initialization.
Those stock entry paths attach the base audio profile and ROM; the mod scene
path omitted that step. A capture harness that first loads a stock level can
mask the omission. `ModSceneLauncher` now attaches the resolved module's
profile and ROM before opening a ROM-backed scene. Standalone modules retain
their creator audio path. This is a bounded startup-registration fix; there
are no changes to audio synthesis, timing, presentation policy or public API.

`TestModSceneLauncherAudio` deliberately resets audio after loading the ROM,
then enters the actual production startup launcher. It checks ROM profile
attachment and nonzero PCM for music, followed by the isolated jump SFX after
stopping music. The mod now plays AIZ1 at the surface, AIZ2 deep below, boss
music for sentinels, and cues for jumps, mining, building, weapons and damage.

### Rejected presentation choices

The first flat native floor sample was a solid canopy: it turned soil green.
The next 128-pixel strip included diagonal cutouts: it made block collision
look inconsistent. Both were rejected after actual GL captures. Selecting
fully opaque native material cells preserves the creator's rectangular
collision silhouette. The initial truncated plant crop also clipped fronds;
the finished crown uses all native plant rows. Raw trial captures are
regenerable and replaced by the final capture set.

### Revision validation

The plan against `d9989e68` selects 3,059 ordinary classes and guards because
external example files and the launcher are unclassified. Proportionate
focused validation covers the creator rules, production mod host/registration,
ROM art, the exact cold startup path and the existing outer audio boundary.
A full engine suite is disproportionate to the isolated scene and startup
attachment; no engine physics, zone geometry or audio algorithm changed.
Category preflight with the IDE Maven on PATH still reports Lua version and
missing PowerShell prerequisites. No broad suite was started or claimed.

Commands (Java 21, IDE Maven directory added to PATH):

```sh
python3 tools/testing/run_categories.py --base d9989e68e62dba5fa69b81b37b216badee9940f3
python3 tools/testing/run_categories.py --base d9989e68e62dba5fa69b81b37b216badee9940f3 --preflight
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestModSceneLauncherAudio,TestGameLoopAudioPresentationModes' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM path>' test
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestModSceneLauncherAudio,TestModSceneHost,TestModRegistrationRuntime' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM path>' test
```

The two initial revision focused invocations each completed 31 outer Jupiter checks with zero
failures/errors/skips. The creator suite nested inside the example bridge has
24 passing tests, including acceleration/friction, momentum/air steering,
high versus short jumps, bounce damage and legacy terrain tags. The final
ROM scene exercise explicitly draws all three badnik types as well as panels,
caves and the Egg Mobile. The unchanged outer-audio mode checks ran once.
The final narrow follow-up reruns the two example bridges after the last
presentation and badnik coverage edits (3 outer checks plus the nested 24).
These are focused checks, not a full-suite result.

Final GL footage uses the same detected ROM SHA-1 recorded above, 528 × 224
at 3×. PNGs, PCM WAV and a short MP4 are in
`$HOME/OpenGGF-captures/starfall-frontier-aiz`. Surface movement, jumping,
workshop, pack and sentinel chambers were inspected. The captured WAV peaks
at 6,674 / 32,767. PCM capture proves synthesized content and cue timing, not
physical speaker latency or a complete encounter-balance playthrough.
The local jar is rebuilt, enabled and trusted by exact hash; users must restart
the JVM to load both the jar and the engine startup audio fix.

The delivery also queued `maven_queue.py -B -q -Dmse=off -DskipTests package`
to refresh the local development engine jars with the startup fix. Packaging
completed; tests were intentionally skipped in that packaging command. The
installed mod SHA-256 is `25bfcfcbaab5af137f677ce387fdfd619405cfe38eaf4c4a4c0fdf3f84303344`.


## Biome revision

Implementation: `ca947bb1d3` (version 1.2.0).
Revision base: `e6844866ed12c0a812b9dba0d646c987347b93d4`.
Worktree: `.worktrees/starfall-biomes`, branch `feature/ai-starfall-biomes`,
based on the existing Starfall Frontier feature. This revision implements
biomes, location music, native terrain, and enemy-facing corrections.
No engine implementation, API signature, stock zone or save-format changes.

### Geography and presentation

`Biome.at` owns one position/depth rule used by the HUD, map, terrain and
music. The surface is Angel Island, Marble Garden, Mushroom Hill, Carnival
Night, Icecap, Sandopolis and Launch Base. Underground regions become
Hydrocity or Sandopolis tombs; Lava Reef starts 30 tiles below the local
surface and switches to its cooler Act 2 palette/song at depth 45. A central
Hidden Palace pocket takes precedence below depth 43. Sky Sanctuary starts
more than 14 tiles above ground east of tile 64. New worlds gain cloud ruins
with caches, snow/desert surface tags, and trees concentrated in jungle and
woodland. Existing worlds keep all arrays, edits, resources and progression;
location-derived geography requires no migration or new persisted state.

`BiomeArt` reads the ROM's 24-byte `LevelLoadBlock` entries at `$091F0C`,
resolves palettes through `PalPoint` at `$0A872C`, uses `SceneRomArt.tiles`
for KosM art, and composes native blocks with their four tile words, palette
lines and X/Y flags. Its bounded standard Kosinski reader handles only the
creator's ROM block table. This does not change production PLC queues or
introduce disassembly assets as runtime fallbacks. Hidden Palace uses entry
47, the locked-on sanctuary record. Inspected block IDs select fully opaque
cells, keeping collision rectangular. Art is cached on scene entry. Supported
stock backdrops are reused; other regions have original parallax scenery
colored for their zone. Mushroom crowns and resource highlights are creator art.

Music IDs follow `sonic3k.constants.asm`'s `mus_*` table. Music updates after
simulation/player actions, deduplicates identical song requests, overrides
live encounters with `$19`, selects saved-location music on continue and
camp music on recall. Beacon completion keeps region music. Hydrocity below
depth 20 and buried Sandopolis use Act 2. Hidden Palace shares Lava Reef Act 2.

### Enemy facing and rejected approaches

The original drawing used velocity sign with the player-facing convention.
`Obj_Rhinobot` / `loc_86E7E` has clear render bit 0 accelerating left.
Decoded Monkey Dude and Egg Mobile poses also face left; Bloominator is
symmetric. Drawing now flips left-native poses toward an explorer on the
right and uses target position, not recoil velocity. The ship's two layers
receive the same flip.

Correcting xflip alone was insufficient: the Launch Base capture still
showed a backwards Rhinobot. A decoded mapping sheet proved frame 3 faces
right, unlike frames 0/1. The existing `(tick/6)%4` loop treated mappings
as a walking animation. Final creator locomotion uses only 0/1, with frame 0
at rest; native frame 2 brakes. The production regression checks both target
sides with opposing velocities at all 48 presentation phases, including
texture identity and mirrored UVs. The reusable lesson is in implementation
pitfalls. Arbitrary opaque samples were replaced with inspected ice faces,
temple masonry, woodland rock, carnival panels, desert sand, industrial brick
and volcanic/crystal rock. Transparent cutouts and animated symbols were
rejected. ROM sheets were temporary research output and removed after inspection.

### Verification and coverage limits

The plan against the revision base selects **3,059 ordinary classes plus
guards**, solely because creator example paths are unclassified. This bounded
mod scene and its test bridge are exercised directly through packaging,
model replay, native rendering and S3K bootstrap/decoding checks. A full engine
run is disproportionate. This is focused validation, not a full-suite or
stock-zone certification.

Commands from the worktree, Java 21 and the IDE Maven directory on PATH:

```sh
python3 tools/testing/run_categories.py --base e6844866ed12c0a812b9dba0d646c987347b93d4
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM>' test
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM>' test
python3 examples/starfall-frontier/build.py --skip-engine
```

The combined focused invocation completed **64 outer Jupiter tests** with
zero failures/errors/skips and **30 nested creator tests**. The final
three-check bridge invocation reran the creator suite and production ROM
rendering after the animation correction; all passed without skips.
Unchanged S3K loader/bootstrap/decoder checks were not repeated. Packaging
validates with zero findings. New tests cover all eleven regions and depth
boundaries, boss/music release, continued underground music, no song restart
in one biome or a paused menu, recoil-facing intent, saved edits, restored
forward simulation and malformed archives, alongside inherited gameplay checks.

Final GL PNGs show all eleven regions and the map at 528 × 224, 3×, under
`$HOME/OpenGGF-captures/starfall-frontier-biomes`. The existing detected ROM
SHA-1 is `b711a909cce238ca4af3e517a2edca306228efa5`, not a canonical revision
claim. The synthesized WAV changes songs every 30 ticks and establishes
content/cue changes, not speaker latency or complete arrangements. Captures
use existing `ExampleModCapture` with `biome-*` debug jumps described in the
example README. Debug pockets never save over player progress. Regeneration
must remove the previous capture build jar before packaging again.

Water/lava simulation, zone-native hazards and enemy AI, a full unassisted
progression/biome-balance playthrough, and developer rewind remain outside
this revision. All regions share the creator resource/quest rules.
Local mod version **1.2.0** requires a JVM restart. Validated jar SHA-256:
`8ba02291b85d5125ddb5d29d23b929f94c68ce49a8879a8cbe1489c9c575f394`.


## ROM backgrounds and background fades — 2026-10-08

User correction: the requested fade applies to **backgrounds only**. Revision
base `68d1e034821f6a5670747eb29236d9f68f509c6a`; isolated worktree
`.worktrees/starfall-backgrounds`, branch `feature/ai-starfall-backgrounds`.
The mod is version 1.2.1. World generation, terrain samples, save format, music
selection and enemy facing behavior are unchanged.

### Cause and implementation

`SceneRomArt.hasZonePictures` only offers AIZ1/2, HCZ1, LBZ1 and SSZ1. The
previous mod intentionally drew authored rectangles when that API returned
false. AIZ and LBZ therefore showed native backgrounds while MGZ, MHZ, CNZ,
ICZ and SOZ did not. This revision keeps the shared engine/API unchanged and
adds bounded background composition inside the creator mod using raw ROM
reads and typed tile/palette decoders. All eleven regions now have ROM pictures.

`LoadLevelLoadBlock2` appends each distinct primary/secondary 16px mapping
bank and 128px chunk bank. `LevelPtrs` at `$09D5C0` supplies the background
layout: dimensions in the eight-byte header, interleaved foreground/background
row pointers, chunk IDs and flipped block descriptors. Pictures cache at scene
entry; render calls reuse immutable images. The ICZ outdoor plane starts at
layout X `$1880`, as selected by `ICZ1_BackgroundInit`. Its line-4 colours are
read from the immediate ROM writes in `ICZ1_SetIntroPal` / `sub_23DE96`, with
opcode checks. These colours affect the background picture only.

The first composition attempt decoded the correct layouts but left numbered
ROM filler in CNZ lights, SOZ pyramids and LRZ caves. GL/full-picture inspection
rejected that result. `BackdropTiles` now reads the first frame of the selected
AniPLC list, respecting signed-duration frame tables and even alignment, and
loads representative phase-zero strips from the owning `AnimateTiles_*` direct
DMA sources into the private background bank. SOZ does not execute its nominal
LRZ AniPLC list, so it uses only SOZ direct sources. Runtime art still comes
entirely from the user's ROM; disassembly assets are never loaded or packaged.
These added pictures use static art and creator horizontal parallax; native
animation/palette timelines, heat shimmer and event-driven scenery remain
outside this presentation change.

A 45-playing-tick smoothstep blends background weights. A new destination
starts from the current mixture, preserving interrupted transitions and
turnbacks. Each layer's source-over alpha is its weight divided by cumulative
weight, so three-way mixtures stay opaque and retain their intended colours.
Only the update owner advances the fade. Menus freeze it, draws remain pure,
and loading, recall and debug teleports snap to the destination. Depth/act
changes use the same mechanism. The background darkness colour blends with
the picture; foreground tiles retain their region appearance and full opacity.

### Validation and delivery

The unchanged category planner selects 3,059 ordinary classes plus guards
because creator example paths are unclassified. This work changes isolated
creator presentation and the native test bridge, with no shared engine,
physics, public API or live animation writes. Focused packaging, model tests,
production scene rendering and the required S3K loader/bootstrap checks cover
the changed paths directly; the broad engine run is disproportionate. These
results are focused validation, not a full-suite pass or stock-zone certification.

Commands run in this worktree with Java 21 and the IDE Maven directory on PATH:

```sh
python3 tools/testing/run_categories.py --base 68d1e034821f6a5670747eb29236d9f68f509c6a
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM>' test
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene' \
  '-Ds3k.rom.path=<absolute existing root S3K ROM>' test
python3 examples/starfall-frontier/build.py --skip-engine
```

The combined invocation passed 64 outer tests and 33 nested creator tests
without failures/errors/skips. Final regression additions verify signed
AniPLC frame-table alignment, palette/transparent pixels, source offsets,
VRAM bounds, Icecap's snowy outdoor palette and fully opaque terrain during a
fade. Fade tests cover completion, interruption by a third region, turnbacks,
depth/act keys, repeated draws and pause. A native-picture assertion initially
required nine distinct colours and failed on SSZ's legitimate smaller cloud
palette; it now rejects single-colour placeholders while also asserting that
the actual decoded background image is rendered.

Durable GL captures are outside the repository under
`$HOME/OpenGGF-captures/starfall-frontier-backgrounds`: `gallery/` shows all
regions at 528 × 224, 2×; `crossing/backgrounds-crossing.mp4` shows real movement
from MGZ into MHZ. `crossing/frame-00080.png` shows both background layers;
frame 110 shows the finished forest. The existing `ExampleModCapture` runs
`biome-*` debug setup then held movement/jumps, without saving player progress.
The full-picture diagnostic was temporary and removed. An attempted capture
while Maven rebuilt test classes failed with `ClassNotFoundException`; the
completed capture ran after that compile, using its own generated build.

The final focused bridge passed three outer tests and **35 nested creator tests**,
without failures/errors/skips. Packaging reported zero findings. The validated
1.2.1 jar SHA-256 is `396f192f3d6dd4492bba083a49528ebe0bc7d4417c6bad2f8b6102b7c3b297f3`. Unchanged S3K loader checks were not repeated
after these test-only additions. Installation enables and trusts this exact jar;
a JVM restart loads the update.

Implementation and verification landed in `74792c5478` before local fast-forward
integration into `feature/ai-starfall-frontier`. No remote push or release publication
was requested.


## Expanded frontier and biome enemies (1.3.0)

Task: the user found that all biomes could be visited in about a minute and
requested much longer exploration plus enemies appropriate to every biome.
Implementation checkout: `.worktrees/starfall-expansion`, branch
`feature/ai-starfall-expansion`, integration base
`df3bb00124a4b79cb99ed1c650db66fe03b1ece0`.

New worlds are 8,192 × 384 twelve-pixel tiles: 32× wider, 4× deeper and
128× the area. At the existing six-pixel speed limit, uninterrupted horizontal
travel alone takes approximately 272 seconds from camp to the east border.
This is a lower bound calculated from distance/speed, not an observed route
completion time. The seven surface regions keep their relative widths; every
later surface region is 1,024 tiles wide and Angel Island is 2,048. Depth
thresholds scale by four, including underground act/music changes and the sky
boundary. Terrain's local hills, mining distances, player physics, tree sizes
and combat distances stay at the usable original scale. The camp and its
starter ore/descent stay close together; shrines are distributed across the
expanded width/depth. Buried treasure room count scales with horizontal size.
The atlas shows a surface strip plus a bounded 256 × 82 tile discovery window;
arrows/pad directions pan, the mouse wheel scrolls, clicking the strip jumps
horizontally, and reopening centers on the player. It does not render millions
of cells per draw or reveal unexplored caves.

World dimensions are instance data persisted by save version 2. Version 1
loading explicitly uses 256 × 96 dimensions, the original biome thresholds,
camp, shrine positions and collision bounds. It retains terrain and progress
without stretching player buildings. New Frontier is required for the expanded
world and the confirmation states its size. Save publication remains within
SceneStorage's 1 MiB/file contract: compressed base64 data exceeding 750,000
characters is split into immutable generation parts, then published by atomic
`world.sav` manifest replacement. `world-backup.sav` points to the last valid
generation. Partial writes remove unpublished parts; missing/corrupt parts
fall back to the backup. After successful publication, obsolete generations
are deleted. A backup must copy the entire mod save directory. No engine
storage or Mod API changes were needed.

Enemy save IDs 0–3 retain Rhinobot, Monkey Dude, Bloominator and shrine sentinel.
IDs 4–13 add Spiker (MGZ), Dragonfly (MHZ), Batbot (CNZ), Penguinator (ICZ),
Skorp (SOZ), Ribot (LBZ), Jawz (HCZ), Toxomister (LRZ), crystal Orbinaut (HPZ)
and Egg Robo (SSZ). Crystal Orbinaut adapts LBZ art to the creator's palace;
it is not a claimed native HPZ spawn. Source-zone palettes come from
LevelLoadBlock and PalPointers, and all sprite bytes come from the ROM.
EnemyArt cites each Art/Map pair; Penguinator uses its object DPLC. Native
mapping tables have auxiliary/blank frames: `ObjDat_Toxomister` selects body
frame 1 and `sub_91988` selects Egg Robo frames 1/3 (0 is blank, 2 is a shot).
The gallery exposed the wrong Toxomister pose after an opacity-only check;
selecting the actual object pose resolves it. Orbinaut uses frame 0 for its
body and frame 1 for the four orbiting spikes.

Creator AI supplies pursuers, hoppers, charging ground foes, hovering cave/sky
foes and stationary ranged Skorps. Projectile attacks warn 20 ticks before
firing. Spawn candidates use their actual horizontal/depth biome, fit collision
bounds and, for ground enemies, search for a real nearby floor. Shelter and
lantern suppression remain; remote enemies are culled horizontally and
vertically. Health is assigned from the selected type at construction rather
than changing a kind after constructing another type.

Rejected approaches: stretching old terrain would corrupt buildings and mined
layouts, so old worlds retain their dimensions. Raising the engine storage
limit would enlarge a public capability unnecessarily, so the mod owns save
chunking. An enum-valued motion field failed the existing immutable-enum
validator; primitive motion constants preserve the same profiles within the
current SDK contract. Early iteration checks were red for that packaging
rejection, a stale test's absolute starter-ore rows, and the blank Egg Robo
frame. They were corrected locally; no unrelated engine changes were made.

Validation uses the proportionate exception: the combined change-based plan
selects all 3,059 ordinary classes plus guards solely because creator example
paths are unclassified. These changes affect only this private mod's world,
combat, rendering and storage; no engine algorithms, API, selection policy or
native zone behavior change. A full run (roughly 24 minutes ordinary plus
10 minutes guards in the documented normalization measurement) does not add
relevant coverage to the isolated creator tests and ROM-backed scene bridge.
Actual preflight with Java 21 and IDE Maven on PATH still reports the inherited
Lua 5.4 mismatch and missing PowerShell; no broad run was launched.

Focused commands from this worktree, Java 21, IDE Maven on PATH and the
existing absolute verified locked-on ROM property:

```sh
python3 tools/testing/run_categories.py --base df3bb00124a4b79cb99ed1c650db66fe03b1ece0
python3 tools/testing/run_categories.py --base df3bb00124a4b79cb99ed1c650db66fe03b1ece0 --preflight
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  '-Ds3k.rom.path=<existing absolute locked-on ROM>' test
python3 tools/testing/maven_queue.py --lean -B -q -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene' \
  '-Ds3k.rom.path=<existing absolute locked-on ROM>' test
python3 examples/starfall-frontier/build.py --skip-engine
```

The S3K checks passed 60 tests with zero skips in the combined invocation;
affected creator/scene checks were then rerun after the local fixes. Added
coverage includes all biome spawn passes, warning/projectile and chase/flying
collision behavior, far-eastern builds and deep discoveries after save/load,
90-tick forward replay with all enemy kinds in the expanded world, a real V1
wire document, malformed dimensions/manifests, dense-world chunk saves,
failed part/manifest publication, generation collection, missing-part backup
read, atlas panning and reopening, and production draw records for every new
ROM enemy. These are focused validation, not a full engine-suite pass or a
complete unassisted adventure certification. Native level/act route matrices
are unchanged because this is creator geometry rather than a stock zone port.

Presentation uses the existing `ExampleModCapture`, with `enemy-biome-*`
debug views and `map`, at 528 × 224, scale 3. Captures live outside the repo in
`$HOME/OpenGGF-captures/starfall-frontier-expansion/gallery`. The source
jar contains code/text only. Positioning views are visual evidence, not proof
of an unassisted route. Debug captures never write the user's actual saves.

Final affected check: 43 creator Jupiter tests, zero failures/errors/skips;
packaging and ROM-backed scene bridge passed with zero owner findings/skips.
The separate native regression checks above passed unchanged. Final source
was inspected with `git diff --check`. Captures were refreshed after correcting
the Toxomister body pose and adding Orbinaut's ROM spike sprites. A refresh
attempt hit the SDK's refusal to overwrite an existing output jar before any
frames were rendered; deleting only that generated capture jar allowed a fresh
capture from the final source. It did not affect gameplay or player saves.
