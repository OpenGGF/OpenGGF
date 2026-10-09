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
  party catches up in level. Green Hill requires the Sonic 1 ROM.

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

**Start in the world.** Launching the mod opens directly on Sonic in Green Hill, or resumes
your saved field. Walk to begin the opening conversation. Dialogue appears over the same
field, with the characters and scenery still visible. There is no level-selection map or
village hub. The optional title screen is accessible through the pause menu for starting
a new save; it is never part of travel or chapter progression.

**Travel and exploration.** Use all four directions to explore, and walk through the eastern
or western trail openings to move between connected areas. Ordinary paths within an island
are open before their bosses are defeated: for example, you can walk from Green Hill to
Star Light and Spring Yard to investigate. Earlier areas remain accessible by walking back.
Story barriers are specific: Knuckles guards the passage beyond Angel Island; an island's
final guardian remains shielded while its other anchors are active; island-crossing Warp
Rings open after that island's anchors are freed. Defeating a boss leaves the party in the
same field. Cross the onward trail when ready.

Use **A / Enter** beside characters, landmarks, monitors, Starposts or bosses. Pocky's
travelling stall sells supplies in the field; closing it returns to the same spot. Starposts
provide repeatable free rest. Visible patrols can be avoided. The minimap marks discoveries
in gold, foes in red and supplies/rest points in green.

**Story and journal.** The optional discoveries develop the Convergence mystery, the people
affected by it and the party's relationships. They can be investigated in either order and
do not serve as mandatory keys for every boss. **Menu -> Journal** replays discoveries and
gives directions. Defeating a chapter boss provides level milestones and supplies, so the
main story can be followed without grinding or collecting every clue. Traveller dialogue
responds to discoveries, and camp conversations give the party time to talk.

**Saving.** Field arrivals, discoveries, Starposts and chapter milestones save automatically.
Loading returns directly to a safe position in that field. Defeated patrols, opened caches,
traveller gifts and discoveries remain completed across visits. Older checkpoints and saves
made on the removed map are migrated into the world without losing party or chapter progress.

| Island (ROM) | Zones | Bosses |
|---|---|---|
| South Island (Sonic 1) | Green Hill, Star Light, Spring Yard | Giga Motobug, Bomb King, Dr. Eggman |
| West Side Island (Sonic 2) | Emerald Hill, Chemical Plant, Mystic Cave | Coconuts Chief, Grabber Queen, Silver Sonic |
| Angel Island (S3&K) | Angel Island, Hydrocity, Launch Base | Flame Craft and Knuckles, Screw Mobile, Beam Rocket |
| The Death Egg (S3&K) | Death Egg | Mecha Sonic, the Convergence Engine |

**Battles happen in the exploration field**, using the same scenery and camera. The party
moves into a formation on nearby walkable ground, then fully turn-based combat begins. At the start of each round every fighter is queued by speed
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
- **Guard** halves damage until your next turn. **Flee** always succeeds against ordinary foes; bosses cannot be escaped.
- **Super!** appears for Sonic once you hold all seven Chaos Emeralds and 50 rings. Super
  Sonic hits twice as hard, cannot be hurt and drains 10 rings every turn.

Elements come from the S3K shields. A shield blocks one whole hit and charges its holder's
attacks with Fire, Water or Lightning. Foes take half again as much damage from their
weakness (Tails' Analyze reveals it) and half from what they resist. Bosses charge up
before a big party-wide attack; guard when you see "charging up!". Victory pays experience
to every party member, including fallen heroes, and rings to the party. Afterwards, everyone
recovers to at least 60% HP and regains 20% of maximum EP (at least 2). Enemy attack strength
is reduced and enemies no longer land random critical hits; telegraphed boss attacks still
reward guarding. Foes are capped at one level above the party, up to the zone's suggested level.
Boss victories fully restore the party. A defeat offers a retry from your last save.

## Audio

The mod runs on the S3K sound driver, so the title, battles and every sound effect
are S3K's own. Angel Island and Death Egg zones play their S3K themes. Sonic 1 and Sonic 2
zones play **their own ROM's zone theme**, synthesised in the background through
`ctx.music()` (about a twelfth of the song's length on a desktop machine, then a background
part render). The island's S3K theme covers synthesis while you are already in the field. Field crossings
briefly retain the previous view while destination assets are prepared. While a Sonic 1 or 2 theme plays, the host replaces
the driver's output with it, so field sound effects are not heard there; battles switch back
to the driver, so combat always has its sounds.

## Where everything comes from

Every sprite, terrain fragment, palette, background and song is read from your
ROMs at runtime: characters through `SceneRomArt.character`, badniks and bosses through
`RomSpriteRequest` at their disassembly labels (cited in `art/Art.java`), and zones through
`SceneRomArt.levelKit`. Each island's heroes use that island's own game art, so Sonic 1's
Sonic walks South Island and S3K's trio walks Angel Island. The jar holds only code and
text: the story script (`text/story.txt`), the original mixed-case font (`text/font.txt`)
and the manifest. The field geometry is assembled for free exploration from ROM terrain. Green Hill uses
its decoded grass, checkerboard cliff, palm, plant and water pieces; its ground plane takes
its colour from the ROM palette. Battle and exploration share this renderer. Sonic the Hedgehog is a trademark of SEGA; this is an unofficial fan project.

## Source tour

| Path | What it is |
|---|---|
| `ThreeIslandsMod` | Registration: reads the font and story, asks for the 400-pixel display, registers the scene. |
| `IslandsScene` | Adapts the game to the scene lifecycle; debug jumps for tests and captures. |
| `Game` | Shared services, field startup/resume, physical travel, story gates, joins, emeralds and saves. |
| `core/` | Pure rules with no engine types: `Battle` (turn queue, commands, AI, damage), `Combatant`, `Skill`, `Item`, `EnemyKind`, `Zone`, `Island`, `Progress`, `SaveCodec`, `Story`. |
| `field/` | `Field` (movement, collision and persistent landmarks), `FieldArt` (ROM field composition), `Stage` (kit ownership), plus legacy route diagnostics. |
| `art/` | `Art` (every ROM request and palette), `Heroes` (poses from each ROM's animation scripts), `EnemyArt` (badniks and multi-part bosses), `Font`. |
| `audio/Audio` | Driver songs and effects, background Sonic 1/2 synthesis with a stand-in. |
| `screen/`, `view/` | Field, in-place battle and dialogue, travelling shop, party menu, loading, optional title, game over and ending; shared windows and controls. |

The old act route finder is retained for ROM loading/diagnostics only. Exploration
uses its own two-dimensional coordinates; its terrain and encounter collision do not depend
on the stock Sonic physics or the act's original horizontal route.

## Debug jumps

`IslandsScene.debugJump` takes `title`, `new`, `shop`, `menu`,
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
  two-axis collision, connected routes, avoiding encounters, persistent discoveries, legacy
  checkpoints and story/font coverage. They also include a balance simulation in which a
  simple player clears every zone's ordinary fights and bosses at the zone's level, a
  sustained sequence without resetting supplies, and a beginner using basic attacks.
- `TestThreeIslandsExample` (engine tests) packages and validates the mod and runs that
  suite; `TestThreeIslandsScene` plays it against a real S3K session: a new game through the
  field startup and in-world dialogue/save, all zones and bosses, a keyboard-fought battle,
  discovery persistence, free local travel/backtracking, exact battle field/camera identity,
  and Green Hill's Sonic 1 theme taking over from the stand-in.

## Known limits

- Fields currently share a compact clearing topology, joined by physical exits. Green Hill
  has curated ROM decorations; other regions currently use simpler ROM texture composition.
  Character sprites retain their original side-facing poses.
- Marble Zone is left out: its level kit has floors without block art and a background that
  decodes as plain sky. Star Light Zone takes its place.
- ROM fragments are the initial level-kit art; stock animated tiles and palette cycles are not simulated.
- Sound effects are silent in Sonic 1 and Sonic 2 fields while their ROM theme plays
  (see Audio).
