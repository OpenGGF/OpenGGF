# Starfall Frontier

Task: deliver a Terraria inspired mining, crafting, building and quest mod,
install it into the local development build, and commit/push a feature branch.
Integration base: `d740b7a0fadd97b2e7c104d56481a0235bdffb4c` (`develop`).
Working branch: `feature/ai-starfall-frontier`. The user explicitly requested
the main checkout instead of a worktree for this task.

## Ownership and implementation

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
