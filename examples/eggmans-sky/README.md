# Eggman's Sky — a No Man's Sky-style expedition for Dr. Eggman

A code mod for OpenGGF's JVM build, played as a full-screen **mod scene** over Sonic 3 & Knuckles.
Dr. Eggman crash-lands in an uncharted galaxy. Pilot the Egg Mobile across procedurally
generated planets **remixed from the zones of every Sonic ROM you own**, scan and catalogue alien
badniks and flora, mine and refine resources, upgrade the Egg Mobile at Egg Stations, dogfight
pirates and the Tornado in space, outrun Sonic's wanted-level patrols, follow echidna ruins to the
seven Chaos Emeralds and warp to the galactic core.

Everything you see and hear — terrain, backgrounds, Eggman, badniks, animals, heroes, the
Tornado, monitors, capsules, Starposts, music and sounds — comes from your ROMs at runtime. The
jar holds only code.

## Requirements

- The OpenGGF JVM build from this line (Mod API 0.7 candidate with `SceneRomArt.levelKit` and
  streaming `SceneImage`s). Native builds cannot load code mods.
- **Sonic 3 & Knuckles** (locked-on), set up as for the stock game. **Sonic 1** and **Sonic 2**
  are optional: when supplied, their zones join the galaxy as more biomes (55 in all).

## Build and run

```bash
python3 examples/build_example.py eggmans-sky          # compile, validate and package target/examples/eggmans-sky/eggmans-sky.jar
python3 examples/build_example.py eggmans-sky --run    # ...then launch the engine with it as a development mod
```

To install it, copy the jar into `mods/` and enable and trust it in the Mod Manager; then choose
**Sonic 3 & Knuckles** on the master title. The mod plays at 16:9 (400×224). *Play Sonic 3 &
Knuckles* on its title returns to the stock game; holding Escape returns to the master title.

## Controls

| Action | Keyboard | Pad | Mouse |
|---|---|---|---|
| Fly / steer | Arrows or WASD | D-pad | steer toward the pointer (space) |
| Mining laser / fire | Space or Z | A | left button (aims at the pointer) |
| Scan pulse (tap) / analysis visor (hold) | X | B | right button |
| Boost; hold Up + boost to **launch**; hold boost in space for the **pulse drive** | C or Shift | C | — |
| Open menu | Enter, Tab or I | Start | — |
| Confirm a menu selection / warp | Enter, Space, Z or C | A, C or Start | select a star, then click it again to warp |
| Close menu / galaxy map | Tab or I | B | right button |
| Recharge life support / hazard / launch fuel / hull | 1 / 2 / 3 / 4 | menu | — |
| Back | Backspace or X | B | right button |

## The expedition

1. **Crash landing.** An intro shows the Tornado shooting the Egg Mobile down onto a gentle
   world. The objective panel walks through the first loop: mine Ferrite, laser blue
   Di-hydrogen crystals (regular exposed deposits supplement the random spawns), refine both in the menu, craft **Egg Fuel**, fuel the launch thrusters
   and hold Up + C to blast into orbit.
2. **Planets.** Each planet is a loop of terrain (walk all the way round and you come back)
   remixed from one stock act, in its own Mega Drive palette, with a climate (temperate, lush,
   scorched, frozen, toxic, irradiated, exotic, barren, dead), storms, a day–night cycle,
   parallax sky, procedural flora and minerals, badnik and animal fauna, rings, monitors,
   Egg Capsules, Starposts, echidna ruins, Giant Rings and badnik wrecks.
3. **Survival.** Life support always drains; hazardous climates drain hazard protection
   (faster in storms — caves shelter you). Recharge from Oxygen and Sodium or crafted gels and
   batteries. The hull takes the rest; if it fails, the Egg Mobile is rebuilt at your last
   Starpost and your cargo waits in the wreck.
4. **Scanning.** Tap scan for a pulse that tags deposits and points of interest; hold it for
   the analysis visor and catalogue each species (rings and Chaos Shards for every discovery,
   a bonus for 100% of a planet). Name planets from the menu.
5. **Sentinels.** Greedy mining and harming wildlife raise a five-star wanted level: Flicky
   drones, then Tails dropping ring bombs, Sonic's homing attacks, Knuckles, and finally Super
   Sonic. Laser a hero to knock rings loose; empty one and it retreats. Hide underground or
   leave and they give up. Launch while wanted and the Tornado follows you into orbit.
6. **Space.** A software-rendered cockpit view: ray-traced planets whose surfaces are mosaics of
   their own zone blocks, rings, atmospheres, nebulae, asteroid fields to mine, the Egg Station
   (a Death Egg with a moustache), pirates (Balkiry, Nebula and Turtloid raiders) and the
   Tornado. Dive into a planet to land; fly into the station to dock.
7. **Egg Station.** An Egg Robo trades (prices follow the system's wealth and its wanted good),
   installs 23 technologies (hull, barrier, life support, hazard shielding, laser power and
   coolant, scanner, survey bonus, jets, thrusters, cargo, Egg Blaster, terrain shaper, auto
   recharger, ring magnet, sentinel jammer, pulse engine, deflector, photon cannon, hyperdrive
   range and the Cadmium, Emeril and Indium drives), offers missions and repairs.
8. **The galaxy.** Craft Warp Cells (Antimatter + Antimatter Housing) and warp from the galaxy
   map. Red, green and blue stars need drive upgrades; black holes fling you toward the core;
   Giant Rings are free long jumps. Echidna ruins reveal Chaos Emerald shrines one at a time.
   With all seven emeralds, warp to the **galactic core** for the ending — then a new galaxy
   begins with your technology and fortune intact.

### Expedition tools

- **Recipe tracking:** in CRAFT, confirm a recipe and choose **PIN RECIPE**. The
  surface/cockpit checklist shows owned/required materials; indented steps explain missing
  crafted or refined ingredients. Scan pulses mark needed deposits with a gold `*`.
  TECH lets you pin the next upgrade tier, including its Chaos Shard cost. One objective
  is pinned at a time; select it again to unpin. The tutorial objective remains visible.
- **Batch production:** confirm a refining or crafting row, then choose **1**, **5**, or
  **maximum** batches. The preview shows available inputs and output quantity. A batch
  either fits in full (including slots freed by ingredients) or changes nothing.
- **Cargo reserves:** highlight a cargo item and press **C / pad C** to reserve the amount
  currently held across its stacks; press again to clear it. Refining, crafting and station
  sales leave that quantity untouched. Recharge and installed upgrades can still use it.
  Reserved items cannot be discarded until their reserve is cleared.
- **Menu memory:** reopening the expedition menu restores its tab and selection; each tab
  remembers its row for the session. Refining and station trading track the selected material
  as their lists change; stations remember the last trading tab when you return.
- **Discovery journal:** choose **LOG**, then **A / Enter** to browse visited planets.
  Confirm a planet to inspect fauna, flora and minerals; left/right changes category.
  Each category shows discovered/total counts. Catalogued species have portraits and
  descriptions or resource yields; undiscovered entries stay anonymous. **B / X** returns
  to the planet list, then the menu. Existing saves use their recorded visits and discoveries.
- **Notifications:** resource pickups accumulate by material. Critical red warnings have
  their own panel and temporarily defer discovery banners.

Pins and cargo reserves survive saves; menu position is remembered only within the session.

Progress saves to `saves/mods/eggmans-sky/expedition.txt` (plain `key=value` lines) at
Starposts, landings, launches, docking, warps, every two minutes and on exit.

## How it is built

- `world/TerrainGen` remixes an act's foreground layout. A planet is a loop of runs of source
  layout columns; a run may jump to another column only where the new neighbours sit side by
  side somewhere in the stock layout, or their facing pixel and collision edges match. Up to a
  sixth of the rows may disagree at a seam; those cells are swapped for a block that fits all
  four neighbours. `world/Terrain` reads collision from the kit's block masks, wraps around the
  planet and supports carving (the terrain shaper).
- `world/Biomes` lists the 55 acts used, each with a climate, weather, music and flora style.
  Sandopolis, Mushroom Hill, IceCap 2, Chemical Plant, Oil Ocean and Metropolis draw a
  procedural climate sky because their detached background art is incomplete; Hydrocity 2 is
  left out for the same reason in its foreground.
- `art/FaunaCatalog` lists 83 creature bodies (badniks and animals from all three games) by ROM
  label; `art/PixelArt` grows outlined, lit, Genesis-quantised flora, crystals and boulders.
  Flora includes ferns, succulent rosettes, reeds, fan leaves, bell flowers and shelf fungi,
  with six seeded specimens per species and climate-specific shape pools. These are visual
  variations of the existing resources; material types, yields and discovery IDs are unchanged.
- Backgrounds retain their source artwork's parallax boundaries. Single-layer ROM planes and
  generated skies scroll as intact pictures, and background motion stays continuous when the
  camera crosses the planet's looping seam.
- `space/SpaceRenderer` ray traces the sky, sun, planets, rings and the station into one
  half-resolution streaming image each frame and keeps a depth buffer so sprites hide behind
  planets; `space/PlanetTexture` builds each planet's orbital map from shrunken layout blocks.
- `surface/SurfaceMode`, `space/SpaceMode`, `station/StationMode`, `space/GalaxyMode` and the
  `ui/` screens are the game's modes, switched by `Game`.

`TestEggmansSkyScene` builds and validates the jar and plays it headlessly through the scene
host: crash landing, laser, scanner, visor and menus, launch, docking, a warp, a landing on a
planet remixed from every supplied biome, and a save round trip.

Debug commands (for capture tools and tests, through `DebuggableScene`): `new[:seed]`,
`planet:i[:seed]`, `biome:game:zone:act[:seed]`, `space[:i]`, `station`, `galaxy`, `rich`,
`wanted:n`, `storm`, `night`, `pirates[:tornado]` and `title`.
