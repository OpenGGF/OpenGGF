# Three Islands

A story-driven JRPG with **fully turn-based ("full wait") combat** for OpenGGF's
unpublished **Mod API 0.7 candidate**. It runs as a Sonic 3 & Knuckles startup scene and
borrows from all three of your Sonic ROMs: Sonic 1 for South Island, Sonic 2 for West Side
Island and Sonic 3 & Knuckles for Angel Island and the Death Egg. Java 21 and the matching
development JVM build are required; code mods do not load in native builds.

> Three islands. Three skies. Three adventures that were never meant to meet.

Dr. Eggman's **Convergence Engine** is pulling the islands into one sky through Warp Rings,
powered by the Chaos Emeralds. Sonic starts alone on South Island, meets Tails on West Side
Island, clashes with (and then recruits) Knuckles on Angel Island, and boards the Death Egg
for the final battle, where all seven emeralds let him turn Super.

## Requirements

- The OpenGGF JVM build from the `develop` line, which carries the Mod API 0.7 candidate.
- **Sonic 3 & Knuckles** (locked-on), set up as for the stock game. The mod launches from it.
- **Sonic 1** and **Sonic 2** ROMs configured as well for their chapters. A chapter whose ROM
  is missing is skipped: its zones count as cleared, its emeralds are handed over and the
  party catches up in level, and the title screen says which chapters are skipped.

## Build, run and install

```bash
examples/three-islands/play.sh                       # build and launch straight into the mod
python3 examples/three-islands/build.py              # just build target/examples/three-islands/three-islands.jar
python3 examples/three-islands/build.py --install    # build, then install, enable and trust it in ./mods
```

Installing copies the validated jar to the checkout's `mods/` folder and enables and trusts
that exact jar in `mods/modstate.json`, leaving other mods' entries alone. Restart the JVM
engine and choose Sonic 3 & Knuckles on the master title.

## Controls

| Action | Pad | Keyboard (default mapping) |
|---|---|---|
| Move cursor / walk | D-pad | Arrow keys |
| Confirm, advance text | A or C | Space, Enter or Z |
| Back | B | X or Escape |
| Run in a field | Hold B | Hold Shift or X |
| Party menu / skip a story scene | Start | Backspace (Start) or M |
| Hurry battle animations and text | Hold A | Hold Enter or Z |

## Playing

**Island maps.** Each island has a village and three zones; the next zone opens when the
previous one is cleared. The postcards are cut from each act's own level blocks. Villages let
you rest (full heal and save), shop for monitors with rings and hear the locals.

**Fields.** Every zone is the stock act itself, drawn from the ROM's level kit (layout,
blocks, collision, palette and parallax background) and walked side-on from the act's real
start position. Badniks wait on the path in plain sight; touch one to fight. Monitors break
open into items or rings, Starposts heal the party and save your place, and the zone boss
blocks the way at the end. Fields reset their badniks and monitors on each visit, so you can
train. Walk off the left end to return to the map.

| Island (ROM) | Zones | Bosses |
|---|---|---|
| South Island (Sonic 1) | Green Hill, Star Light, Spring Yard | Giga Motobug, Bomb King, Dr. Eggman |
| West Side Island (Sonic 2) | Emerald Hill, Chemical Plant, Mystic Cave | Coconuts Chief, Grabber Queen, Silver Sonic |
| Angel Island (S3&K) | Angel Island, Hydrocity, Launch Base | Flame Craft and Knuckles, Screw Mobile, Beam Rocket |
| The Death Egg (S3&K) | Death Egg | Mecha Sonic, the Convergence Engine |

**Battles** are fully turn-based. At the start of each round every fighter is queued by speed
(the strip under the message box shows the order), and the battle **waits indefinitely** on
each hero's turn; nothing moves until you choose. Commands:

- **Attack** one foe.
- **Skills**, which cost EP. Each hero learns more as they level:
  - Sonic: Spin Dash, Homing Attack, Super Peel Out, Sonic Boom and Light Speed Dash.
  - Tails: Patch Up, Analyze, Tail Twister, Energy Ball, Ring Shower and Restart.
  - Knuckles: Drill Claw, Glide Smash, Guardian Stance and Maximum Heat.
- **Dual and Triple Techs** appear in the skill list once the partners have joined:
  Tornado Spin (Sonic + Tails), Double Spin Attack (Sonic + Knuckles), Thunder Glide
  (Tails + Knuckles) and Trinity Rush (all three). A tech needs every member alive and still
  waiting to act this round; it spends their turns and EP too.
- **Items** are the classic monitors: Super Ring, Ring Bundle, Blue Sphere (EP), Speed
  Shoes, Flame, Bubble and Lightning Shields, Invincibility and the 1-Up (revive).
- **Guard** halves damage until your next turn. **Flee** works except against bosses.
- **Super!** appears for Sonic once you hold all seven Chaos Emeralds and 50 rings. Super
  Sonic hits twice as hard, cannot be hurt and drains 10 rings every turn.

Elements come from the S3K shields. A shield blocks one whole hit and charges its holder's
attacks with Fire, Water or Lightning. Foes take half again as much damage from their
weakness (Tails' Analyze reveals it) and half from what they resist. Bosses charge up
before a big party-wide attack; guard when you see "charging up!". Victory pays experience
to every standing hero and rings to the party; fallen heroes get back up with 1 HP.
A defeat offers a retry from your last save.

**Saving.** Villages and Starposts save automatically, and the party menu saves on the map.
Continue resumes at your last Starpost, or on the island map.

## Audio

The mod runs on the S3K sound driver, so the title, maps, battles and every sound effect
are S3K's own. Angel Island and Death Egg zones play their S3K themes. Sonic 1 and Sonic 2
zones play **their own ROM's zone theme**, synthesised in the background through
`ctx.music()` (about a twelfth of the song's length on a desktop machine, then a background
part render). The zone's loading card waits up to four seconds for it, and the island's S3K
map theme covers any remaining wait. While a Sonic 1 or 2 theme plays, the host replaces
the driver's output with it, so field sound effects are not heard there; battles switch back
to the driver, so combat always has its sounds.

## Where everything comes from

Every sprite, level block, collision mask, palette, background and song is read from your
ROMs at runtime: characters through `SceneRomArt.character`, badniks and bosses through
`RomSpriteRequest` at their disassembly labels (cited in `art/Art.java`), and zones through
`SceneRomArt.levelKit`. Each island's heroes use that island's own game art, so Sonic 1's
Sonic walks South Island and S3K's trio walks Angel Island. The jar holds only code and
text: the story script (`text/story.txt`), the original mixed-case font (`text/font.txt`)
and the manifest. Sonic the Hedgehog is a trademark of SEGA; this is an unofficial fan project.

## Source tour

| Path | What it is |
|---|---|
| `ThreeIslandsMod` | Registration: reads the font and story, asks for the 400-pixel display, registers the scene. |
| `IslandsScene` | Adapts the game to the scene lifecycle; debug jumps for tests and captures. |
| `Game` | Shared services and the story flow: islands, joins, emeralds, saves, zone clears. |
| `core/` | Pure rules with no engine types: `Battle` (turn queue, commands, AI, damage), `Combatant`, `Skill`, `Item`, `EnemyKind`, `Zone`, `Island`, `Progress`, `SaveCodec`, `Story`. |
| `field/` | `FieldPath` (the route through an act's collision), `Field` (spots, walking, contact), `Stage` (level-kit drawing), `KitTerrain`. |
| `art/` | `Art` (every ROM request and palette), `Heroes` (poses from each ROM's animation scripts), `EnemyArt` (badniks and multi-part bosses), `Font`. |
| `audio/Audio` | Driver songs and effects, background Sonic 1/2 synthesis with a stand-in. |
| `screen/`, `view/` | Title, map, village and shop, field, battle, story, menu, loading, game over and ending; shared windows and controls. |

The route finder walks each act column by column, choosing the nearest standable floor
(solid with clear headroom above) within a step or short drop. When nothing is in reach it
takes the nearest floor anywhere in the column, so tall walls and deep falls become quick
hops; only bottomless columns are arced over. Its search covers the layout's full height,
because some acts' starting camera boundary (Marble Zone's, for one) is lowered by level
events later. The `routestats` debug jump writes each act's route statistics to the mod's
storage.

## Debug jumps

`IslandsScene.debugJump` takes `title`, `new`, `map:<island>`, `village`, `shop`, `menu`,
`field:<zone>[:<fraction>]`, `battle:<zone>[:KIND,...]`, `boss:<zone>`, `win`,
`story:<scene>`, `level:<n>`, `emeralds`, `gameover`, `ending`, `audiostate` and `routestats`
(zones: `ghz slz syz ehz cpz mcz aiz hcz lbz dez`). For example:

```bash
java -cp target/test-classes:target/classes:$(cat target/test-classpath.txt) \
  com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/three-islands \
  --out /tmp/cap --jump "boss:hcz" --script "120:enter +10:enter" --every 30
```

## Tests

- `src/test/java/threeislands`: Jupiter tests for the rules, including full wait, tech
  turn-sharing, shields, elements, rewards, Super Sonic, saves, routes on synthetic terrain,
  field contact and story/font coverage. They also include a balance simulation in which a
  simple player clears every zone's ordinary fights and bosses at the zone's level.
- `TestThreeIslandsExample` (engine tests) packages and validates the mod and runs that
  suite; `TestThreeIslandsScene` plays it against a real S3K session: a new game through the
  story, map, village save and a zone's loading card into a battle, every zone's terrain and
  boss, a keyboard-fought battle, and Green Hill's Sonic 1 theme taking over from the stand-in.

## Known limits

- The field is a route through each act, not the act's physics: no loops, springs or
  objects. Bridges and platforms are objects, so the party hops over those gaps.
- Marble Zone is left out: its level kit has floors without block art and a background that
  decodes as plain sky. Star Light Zone takes its place.
- Level kits show the act as it loads: no animated tiles, palette cycles or water.
- Sound effects are silent in Sonic 1 and Sonic 2 fields while their ROM theme plays
  (see Audio).
