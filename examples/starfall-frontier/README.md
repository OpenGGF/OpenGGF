# Starfall Frontier

A Terraria inspired mining, crafting and building adventure for OpenGGF's
unpublished **Mod API 0.7 candidate**, played as Sonic using your Sonic 3 &
Knuckles ROM. Java 21 and the matching development JVM build are required.

An ancient beacon on **Angel Island** has fallen silent. Run through the palm
jungle, mine beneath the island, gather power rings and chaos shards, and
recover three emerald fragments from Eggman's shrine sentinels. Restore the
beacon, then keep expanding your world. Explore eleven S3K-inspired biomes, from palm jungle and icy cliffs to
buried waterworks, volcanic caves and floating sanctuary ruins.

The seeded world is 256 × 96 tiles with caves, three shrine chambers, buried
treasure, surface berries, trees and copper, iron and chaos shard deposits.
There are 32 inventory items and 18 recipes: three pick tiers, an axe, two
swords, a bow and arrows, a regenerating magic staff, armor, potions,
heartstones, building materials and crafting stations. Seven quest stages
guide the progression. The shrine sentinels warn before attacks: Jungle fires a fan,
Ruins sends a ring of shots, and Core fires faster volleys and lunges.

Terrain uses native 16-pixel blocks decoded from each zone's ROM art,
mappings and palettes, cropped to the adventure's 12-pixel grid. Angel Island,
Hydrocity, Launch Base and Sky Sanctuary also use ROM parallax backgrounds;
other regions use original scenery colored for their zone. Palm trunks and
foliage come from Angel Island, with original mushroom crowns in the woodland. Rhinobots, Monkey Dudes and Bloominators
represent the foes; monitors hold caches, starposts mark camp and shrines,
and an Egg Mobile represents each sentinel. Rings and emerald sprites also
appear in the pack. Crafting equipment, material highlights, lighting and
menus remain original creator visuals. The jar contains code and a text font,
with no bundled Sega or Terraria assets.

Movement uses Sonic's six-pixel running limit, 6.5-pixel jump impulse,
variable jump height, air control and momentum. Ground acceleration and
friction are intentionally stronger for mining and building. Descending onto
a badnik deals spin damage and bounces Sonic upward. This is a flat tile-world
controller; native slope physics, rolling and spindash are not implemented.
Each biome selects its own ROM music as you cross horizontal and depth
boundaries. Hydrocity, Sandopolis tombs and deep Lava Reef use their Act 2
tracks; boss music overrides the biome until the encounter ends. Continuing
a save and recalling to camp select the correct track immediately. The
restored beacon keeps location music active. Jumping, mining, attacks and damage have ROM
sound effects. Startup now attaches ROM audio before entering the mod scene.

## Biomes

The surface runs west to east through Angel Island (tiles 1–63), Marble
Garden (64–95), Mushroom Hill (96–127), Carnival Night (128–159), Icecap
(160–191), Sandopolis (192–223) and Launch Base (224–254). Camp stays in
Angel Island. The exploration map colors terrain by biome.

| Region | Where to explore |
| --- | --- |
| Hydrocity | At least 8 tiles below the local surface, west of tile 192; Act 2 music below depth 20 |
| Sandopolis tombs | At least 8 tiles underground beneath the desert and base |
| Lava Reef | **Underground only**, at depth 30 or more; crystal-cave palette and Act 2 music below depth 45 |
| Hidden Palace | Deep central crystal pocket: tiles 96–159, depth 43 or more |
| Sky Sanctuary | More than 14 tiles above the surface, east of tile 64; new worlds have mineable cloud ruins with caches |

Reach the cloud ruins by building platforms. Biomes change scenery and music;
Hydrocity currently has no water simulation, and Lava Reef has no lava damage.
Mining tiers, shrine rewards and the equipment/quest progression stay shared
across the regions. The native terrain samples preserve solid rectangular
blocks, with original resource highlights marking copper, iron and shards.

## Build, install and launch

From the repository root, with Java 21, Maven and Python 3 on PATH:

```sh
python3 examples/starfall-frontier/build.py
python3 examples/starfall-frontier/build.py --skip-engine --install
python3 examples/starfall-frontier/build.py --skip-engine --run
```

The first command queues the engine compile and packages the creator sources
through `ggfmod package`. The jar is
`target/examples/starfall-frontier/starfall-frontier.jar`. `--install` copies
it to this checkout's `mods/`, enables it, and grants code trust to that exact
jar hash, preserving other mod state. Restart the JVM and select **Sonic 3 &
Knuckles** on the master title. The mod requests a 528 × 224 logical viewport.
Other installed mods remain enabled; later applicable startup scenes take
precedence. Use Mod Manager to disable Starfall Frontier when you want the
stock S3K title again. This code mod does not run in native-image releases.

`--run` uses the existing engine configuration and its ROM paths. The generic
runner also supports an isolated launch with an explicit ROM:

```sh
python3 examples/build_example.py starfall-frontier --skip-engine --run --s3k /absolute/path/to/sonic3k.gen
```

## Controls

| Input | Action |
| --- | --- |
| Arrows or A / D | Accelerate, run and brake |
| Space, W or pad A | Jump; hold for height, release for a short hop |
| Mouse | Aim at a tile or enemy |
| Hold left mouse, F or pad C | Mine, chop, attack, build or consume the selected item |
| Hold right mouse | Place the selected building material |
| E or pad Up | Gather a bush, open a cache, offer a sigil or restore the beacon |
| 1–8, mouse wheel, Q / R | Select a hotbar slot |
| Tab / I | Backpack; select an item and Enter / pad A assigns it to the current slot |
| Q / R or pad C in the backpack | Change the destination hotbar slot |
| H in the backpack | Consume selected food, potion or heartstone |
| C or pad B | Workshop; arrows/wheel select a recipe, Enter / pad A crafts |
| J / M | Quest journal / exploration map |
| H during play | Drink a potion, or eat a berry if no potion is held |
| Down + use | Aim below your feet; Down also drops through platforms |
| Pad Up + use | Aim overhead |
| P, Escape tap or Start | Pause menu, including backpack, crafting, quests, map, recall and save |
| F1 | Field guide |
| B / Backspace | Close a panel; menus also support right click |

Pad buttons use the engine's bindings. The pause menu makes every panel
available without a keyboard. With a pad, your tool aims two tiles in the
facing direction; Up/Down aim vertically. Mouse players can click the hotbar,
quest tracker, footer shortcuts and menu choices. Letterbox input is ignored.
Menus freeze the simulation.

## First evening on the frontier

1. Pick hotbar slot **2** and chop a nearby tree. Each trunk falls as a whole
   and drops timber plus acorns. Plant an acorn on empty ground to regrow a tree.
2. Open **C**, craft a workbench, then open **Tab**, select it and assign it to
   an unused slot. Close the pack and place it on the ground near camp.
3. Use the pick to gather **20 stone** and make a furnace while standing within
   five tiles of your workbench. Place the furnace in the same camp.
4. Follow the lanterns down the shaft east of the beacon. Copper is available
   beside the descent. Smelt **12 ore into 4 ingots**, then craft a copper pick.
5. Mine iron below the copper. Smelt ingots, build an anvil, and forge the iron
   pick. Pick upgrades replace slot 1 automatically; the iron sword replaces
   slot 3. Other crafted items are assigned through the backpack.
6. Collect chaos shards in the deeper caves. Forge sigils at your anvil and
   offer one at each shrine. Seek the shrines beneath the western jungle, ancient
   groves and eastern highlands. Your map records discoveries.
7. Forge a starlight core from all three fragments, 12 crystals and 6 iron
   ingots. Return to the camp beacon and interact to restore it.

Craft timber walls, platforms and lanterns for your camp. Blocks attach to
neighboring tiles or a timber wall; solid blocks cannot occupy the player's
body. A roof within five tiles, eight surrounding timber wall cells, and a
nearby lantern make a shelter. Shelter suppresses nearby enemy spawning and
permits recovery; lanterns discourage spawning around them in caves too.
Use a pick on an empty timber wall cell to salvage it. Mining never reaches
beyond six tiles, and bedrock and shrines cannot be destroyed.

Open explorer caches for supplies and heartstones. Consume a heartstone
from the backpack to raise maximum health by 20, up to 200. A potion restores
50 health and berries restore 15; healing has a two-second cooldown. Mana
regenerates for the staff. Death returns you to camp with your resources and
terrain intact; an active warden despawns and needs another sigil. Recall is
available from pause when a warden is not active.

## Saves and validation

Version 1 worlds remain compatible: existing terrain, resources and quests
are retained, including snow/ember terrain tags. Biomes are derived from location, so
existing worlds gain region art and music without replacing mined or built
terrain. New worlds generate snow in Icecap, sand-colored desert terrain,
woodland trees and cloud ruins; existing worlds retain their original layout.

One world is saved automatically after every 30 seconds of active simulation,
on save-and-return, and on scene exit. The engine keeps it under
`saves/mods/starfall-frontier/world.sav`, with `world-backup.sav` holding the
previous valid save. A failed write displays a message and keeps progress in
memory. An unreadable primary loads its valid backup; unreadable saves are
not replaced until you confirm creating a new world. Back up these files
before starting a new frontier. New World replaces the active world after
confirmation. Mod scenes use their own save state; stock developer rewind
does not rewind this adventure.

The creator tests cover biome and depth boundaries, music transitions without
song restarts, boss overrides, saved-region music, recoil-safe enemy facing,
terrain generation, tile collision, platforms, mining
tiers, tree harvesting, station/cost rules, placement, shelter, weapons,
healing, death, shrine rewards, quest progression, bounded saves and forward
replay after save restoration. Production scene tests cover packaged loading,
panels, pause, draw purity, save recovery and debug isolation. The engine bridge
also checks ROM terrain/characters and every major screen; a cold production
startup regression verifies both music and the isolated jump effect in PCM:

```sh
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestStarfallFrontierExample,TestStarfallFrontierScene,TestModSceneLauncherAudio' \
  '-Ds3k.rom.path=/absolute/path/to/sonic3k.gen' test
```

The progression test exercises each production rule with prepared resources
and positions; it is not evidence of a complete unassisted playthrough.
Presentation captures use the existing `ExampleModCapture`. Debug commands
`play`, `craft`, `inventory`, `journal`, `map`, `cavern`, `warden` and `victory`
provide inspection views. `biome-angel_island`, `biome-marble_garden`,
`biome-mushroom_hill`, `biome-carnival_night`, `biome-icecap`,
`biome-sandopolis`, `biome-launch_base`, `biome-hydrocity`, `biome-lava_reef`,
`biome-hidden_palace` and `biome-sky_sanctuary` inspect individual regions; debug visits never write player saves. The architecture
and delivery evidence are recorded in the
[design](../../docs/architecture/designs/2026-10-08-starfall-frontier.md).
