# Three Islands

A story-driven JRPG with **fully turn-based ("full wait") combat** for OpenGGF's
unpublished **Mod API 0.7 candidate**. It runs as a Sonic 3 & Knuckles startup scene and
borrows from all three of your Sonic ROMs: Sonic 1 for South Island, Sonic 2 for West Side
Island and Sonic 3 & Knuckles for Angel Island and the Death Egg. Java 21 and the matching
development JVM build are required; code mods do not load in native builds.

> Three islands, cut off by Eggman's machines.

Dr. Eggman's **Convergence Engine** is pulling the islands toward his base through Warp Rings.
Local anchors block the roads, while shield relays protect their controllers. The Chaos
Emeralds power those installations; the stolen Master Emerald supplies the central engine.
Sonic starts alone on South Island, meets Tails on West Side Island, clashes with (and then recruits) Knuckles on Angel Island, and boards the Death Egg
for the final battle, where all seven emeralds let him turn Super. Island crossings use the
service Warp Rings; the party prepares a safe shutdown and escapes in a pod.

Dungeons occupy different districts of each area, with solid ROM-textured facades,
buttresses, recessed doors and steps. Their routes grow from seven rooms in the seaside
shrine to seventeen in the Sky Archive. Sentries, side wings and mechanisms protect the
story discoveries, and every interior hides a treasure room holding an accessory. Green Hill's missing Flicky follows Sonic back out for her reunion.
Entrances stay usable, and cleared fights, completed mechanisms and rewards persist.
The refuge Starpost restores and saves the party; continuing or retrying an indoor save
starts inside the entrance room.

| Area | Outdoor route | Interior task |
|---|---|---|
| Green Hill | Flicky Village, forest trails, a downstream log bridge, the shrine plateau, bell garden, flooded orchard and relay switchback | Raise the side-crypt floodgate and rescue the Flicky |
| Star Light | Night highways and catwalks: arrival promenade, observatory plaza, two feeder platforms and the powered skywalk gate | Align the star and moon lenses in the recorded order |
| Spring Yard | Southern loading yards and northern freight platforms | Route power to the lift's three lamps |
| Emerald Hill | Hilltop loop and workshop branches | Set three sighting dials from Tails' survey notes |
| Chemical Plant | Lower service pipes and upper pump district | Balance the reservoir's linked pressure controls |
| Mystic Cave | Winding shafts, switchbacks and winch chambers | Read the miner's verse and light the braziers in order |
| Angel Island | Terraces wrapping around the guardian precinct | Restore the vows in three ancestor alcoves |
| Hydrocity | Lower aqueduct, return galleries and intake spurs | Balance three tide chambers to reach the mural |
| Launch Base | Southern moorings, northern gantry and service spurs | Execute the fuel, ignition and release checklist |
| Death Egg | Long outer circuit with four auxiliary relay wings | Reconstruct the four-part archive chronology |

Outside Green Hill, branch controls restore a blocked crossing into the final district:
most are repairs, while Chemical Plant's pumps shut down in flow order, Hydrocity's intake
gauges form a circuit and the Death Egg's relay dials follow a wiring diagram. The local
dungeon and relay still unlock the anchor shield. Each area also has a hidden side
branch (see Lost animals). Maps show terrain rather
than hidden objectives. Instructions near each entrance can be reread in the journal.
Sequence mistakes reset the phrase; circuit handles are reversible. Neither consumes
items. Individual repairs and fully solved puzzles survive saves; unfinished sequences
and circuit attempts restart on re-entry. Previously completed dungeons and cleared
chapters retain their access. Later interiors add rooms, longer deductions and patrols.
In most areas ordinary patrols can be avoided; Green Hill and Star Light are hand-drawn maps
whose single-file trails and catwalks are held by badniks (marked "!") that must be beaten
to pass. Fleeing steps the party back the way it came, leaving the pass still blocked.

Interiors have their own ROM terrain: Marble masonry for the seaside shrine and freight
catacombs, Mystic Cave for the workshop caverns and lantern shrine, Lava Reef for the
guardian shrine and mooring vault, Hydrocity for the tidal sanctuary, and the corresponding
machinery kits for the observatory, pump station and Death Egg archive. Walls and gates use
the same room boundaries as movement. Battles retain the interior and camera.

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

**Start at the title.** Launching the mod opens the title menu. Choose **New Game** to start
with Sonic in Green Hill, or **Continue** to resume your save. Continue is selected when a
save exists, but waits for confirmation. Walk to begin the opening conversation. Dialogue
appears over the same field, with the characters and scenery still visible. There is no
level-selection map or village hub. The title is also accessible through the pause menu;
it is never part of travel or chapter progression.

**Travel and exploration.** Use all four directions to explore, and walk through the eastern
or western trail openings to move between connected areas. Eggman's anchors protect his
installations with powered barriers: a visible curtain of light blocks each eastern trail
until its guardian falls. Local dungeon discoveries let the party operate the area's relay; shutting it down
removes the guardians' shields. Defeat those guardians to free the anchor and reopen the
trail. Backtracking remains available. Island-crossing Warp Rings open after that island's
anchors are freed. Defeating a boss leaves the party in the same field; cross when ready.
Previously cleared areas in older saves retain their open trails.

Use **A / Enter** beside characters, landmarks, monitors, Starposts or bosses. Pocky's
travelling stall sells supplies in the field; closing it returns to the same spot. Starposts
provide repeatable free rest. Visible patrols can usually be avoided (not the badniks holding passes in Green Hill and Star Light). The minimap shows terrain, your position, and places you have already investigated;
it does not reveal unseen discoveries or patrols. Nearby interaction prompts retain the controls.

**Story and journal.** Sonic starts by helping people trapped behind Eggman's barriers.
Following Eggman's installations brings him to the Convergence Engine, while Tails and Knuckles discover how to
return the stolen power safely. Characters explain the stakes, respond to local changes,
and offer observations when something resists them. Dungeon discoveries and relays matter
to progression; side puzzles, caches and ordinary patrols remain optional. **Menu -> Journal**
recalls the party's purpose and discoveries already made, without listing undiscovered
objectives. Boss milestones and discoveries provide level catch-up and supplies without
requiring patrol grinding.

**Green Hill and Star Light** are hand-authored tile maps (80 cells across, 32 pixels each)
rather than room grids. Green Hill winds from Flicky Village through jungle trails and a
hollow, down the river to a log bridge, up a switchback to the Seaside Shrine plateau, past
the bell garden and flooded orchard, and round the lake to the relay and the anchor arena.
Star Light is a night highway system over the city: catwalks to the observatory plaza,
feeder platforms east and far south, and the skywalk gate they power. Six badniks hold
narrow passes in each, a mid-route Starpost (the shrine plateau, the skywalk) saves and
restores, and pressing against a wall beside a trail slides the party onto it. The maps are
generated by `tools/generate_maps.py`, which also checks markers and forced passes.
Green Hill's optional puzzles:
Inscriptions and physical responses provide the clues. Both puzzles are optional; mistakes
cost no items and never block the main route. Solved bells, individual sluices, discoveries
and their one-time rewards survive saving and revisiting. An unfinished bell phrase starts
fresh when the field reloads. Old shrine, chapter and camp saves remain valid.

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

**Battles happen in the exploration field**, using the same scenery and camera, dimmed
behind the fighters. The party steps into a staggered line on the left and the foes onto
the right, then fully turn-based combat begins. At the start of each round every fighter is queued by speed
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
attacks with Fire, Water or Lightning. Elemental hits can leave a status: **Burn** deals
damage at the start of each turn, **Soaked** slows the target and **Stun** costs its
next turn (bosses cannot be stunned). Guarding halves the chance, healing skills and
items cure statuses, and an elemental charm protects its wearer from its own element. Foes take half again as much damage from their
weakness (Tails' Analyze reveals it) and half from what they resist. Bosses charge up
before a big party-wide attack; guard when you see "charging up!". Victory pays experience
to every party member, including fallen heroes, and rings to the party. Afterwards, everyone
recovers to at least 60% HP and regains 20% of maximum EP (at least 2). Enemy attack strength
is reduced and enemies no longer land random critical hits; telegraphed boss attacks still
reward guarding. Foes are capped at one level above the party, up to the zone's suggested level.
Boss victories fully restore the party. A defeat offers a retry from your last save.

**Every boss has a signature.** Analyze describes it, and the party's lines before each
fight hint at it:

| Boss | Signature |
|---|---|
| Bomb King, Coconuts Chief | Call smaller helpers, who leave when their leader falls |
| Bomb (any) | A visible fuse counts down; the bomb explodes over the party when it runs out |
| Dr. Eggman, Knuckles, Mecha Sonic | Power up at half health; golden Mecha Sonic strikes twice |
| Grabber Queen | Seizes a hero, who cannot act until the Queen takes a heavy hit or lets go |
| Silver Sonic | Curls into spikes: plain blows rebound, elemental hits break the guard |
| Flame Craft | Carpets the field with firebombs that readily burn |
| Screw Mobile | Plants a depth charge beside a hero; guard before it goes off |
| Beam Rocket | Raises a barrier that only a Dual or Triple Tech can crack |
| Convergence Engine | Its exposed element cycles Fire, Water, Lightning each round; calls Egg Robos |

## Equipment, lost animals and Rift Echoes

**Accessories.** Each hero wears one accessory, chosen from **Menu -> Equip**, which
previews the stat change. Pocky's stall gains accessories island by island (left/right
switches the shop between supplies and accessories). Every dungeon's treasure monitor holds
a unique one, including elemental charms that change plain attacks' element and ward off
that element's status, and accessories that restore EP every turn.

**Lost animals.** Eggman's badniks carried animals off to power them. One is hiding in
each area, usually at the end of a side branch beyond the main route; the Flicky sisters
in Green Hill explain the quest. Each rescue pays 50 rings, and 3, 6 and all 10 rescues
earn gifts. The journal shows your count.

**Rift Echoes.** Once an island's final anchor falls (aboard the Death Egg, once Mecha
Sonic is beaten), a violet echo of two earlier bosses appears near that anchor: Spring
Yard, Mystic Cave, Launch Base and the Death Egg. Echoes are optional, fought three levels
above the area, and leave behind a unique accessory, rings and a 1-Up.

## Audio

Exploration uses each outdoor area's theme and a theme matching its dungeon's ROM
terrain: Green Hill's seaside shrine plays **Marble Zone**, for example. Dialogue,
shops, menus and local mechanisms leave the current theme playing. Battles, victory,
emerald rewards, the title and the ending have their own deliberate cues.

Sonic 1/2 music runs live from your ROM through `ctx.audio().playMusic(game, id)`.
It starts immediately on arrival or Continue, including indoor saves. The ROM sound
sequence owns its intro and loops; there is no one-minute render or periodic restart.
Entering a different music area or returning from combat starts that area's theme.
S3K areas and battle/story cues use the running S3K driver. An island's S3K fallback
is used only if foreign-ROM playback is unavailable. Crossing areas keeps the source
music until the destination opens. Foreign music still masks the base driver's sound
effects; battles use the driver and retain their sound effects.

Use the matching current JVM engine build: older candidate builds do not implement
live scene ROM music. `examples/three-islands/play.sh` builds and launches that engine.

## Where everything comes from

Every sprite, terrain fragment, palette, background and song is read from your
ROMs at runtime: characters through `SceneRomArt.character`, badniks and bosses through
`RomSpriteRequest` at their disassembly labels (cited in `art/Art.java`), and zones through
`SceneRomArt.levelKit`. Each island's heroes use that island's own game art, so Sonic 1's
Sonic walks South Island and S3K's trio walks Angel Island. The jar holds only code and
text: the story script (`text/story.txt`), the original mixed-case font (`text/font.txt`)
and the manifest. The field geometry is assembled for free exploration from ROM terrain. Green Hill uses
its decoded grass, checkerboard cliff, palm, plant and water pieces, including full-size water reflections with the original four-step ROM palette cycle, plus the log
bridge (`Nem_Bridge`) and purple rock (`Nem_PplRock`) objects; its grass and trails take their colours from the ROM palette.
Star Light draws its starfield backdrop, the city-lights chunk, street lamps, red-lit rails, girder lattice, cones,
hazard barriers and plated buildings from the decoded level kit. Battle and exploration share this renderer. Sonic the Hedgehog is a trademark of SEGA; this is an unofficial fan project.

## Source tour

| Path | What it is |
|---|---|
| `ThreeIslandsMod` | Registration: reads the font and story, asks for the 400-pixel display, registers the scene. |
| `IslandsScene` | Adapts the game to the scene lifecycle; debug jumps for tests and captures. |
| `Game` | Shared services, field startup/resume, physical travel, story gates, joins, emeralds and saves. |
| `core/` | Pure rules with no engine types: `Battle` (turn queue, commands, AI, statuses, boss signatures, summons, damage), `Combatant`, `Skill`, `Item`, `Gear` (accessories), `EnemyKind`, `Zone`, `Island`, `Progress`, `SaveCodec`, `Story`. |
| `field/` | `Field` (movement, collision and persistent landmarks, treasure, lost animals, Rift Echoes), `AreaLayout` (authored room graphs and secret rooms), `MechanismPuzzle` (repair, sequence, circuit and dial rules), `FieldArt` (ROM field composition and terraces), `DungeonArt`, `Stage` (kit ownership), plus legacy route diagnostics. |
| `art/` | `Art` (every ROM request and palette), `Heroes` (poses from each ROM's animation scripts), `EnemyArt` (badniks and multi-part bosses), `Font`. |
| `audio/Audio` | Driver songs and effects, live Sonic 1/2 ROM music with native intros/loops and deliberate battle/story cues. |
| `screen/`, `view/` | Field, in-place battle and dialogue, travelling shop, party menu, loading, title, game over and ending; shared windows and controls. |

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
  checkpoints and story/font coverage. `MechanicsTest` covers statuses, every boss signature,
  summons and accessories with save round trips. They also include a balance simulation in which a
  simple player clears every zone's ordinary fights and bosses at the zone's level, a
  sustained sequence without resetting supplies, a beginner using basic attacks, and Rift
  Echoes that are winnable but long at their level.
- `TestThreeIslandsExample` (engine tests) packages and validates the mod and runs that
  suite; `TestThreeIslandsScene` plays it against a real S3K session: a new game through the
  field startup and in-world dialogue/save, all zones and bosses, a keyboard-fought battle,
  discovery persistence, sealed local trails and open backtracking, exact battle field/camera identity,
  and immediate Sonic 1 music, Marble music in the seaside shrine, and audible Star Light/indoor Continue saves. Dungeon checks exercise all
  available interiors through the actual entrances, both sentry battles, indoor save/continue,
  the story reward, repeat interaction and returning/re-entering, then the relay, shield removal,
  save/continue, every guardian, onward travel and the final ending. Model checks flood-fill the
  gate geometry and reachable puzzle controls, preserve old saves/entrances and simulate dungeon combat, including a
  level-one Sonic in the first shrine. Green Hill checks cover both sluice orders, the
  locked/revealed orchard route, bell mistakes and recovery, one-time rewards, save/continue,
  and the expanded eastern trail through real scene interactions.

## Known limits

- Green Hill and Star Light are hand-drawn maps; other regions still use simpler ROM texture
  composition around their authored room graphs.
  Character sprites retain their original side-facing poses.
- Marble Zone is not an outdoor chapter: its stock route and background are unsuitable
  for the current route renderer. Its decoded masonry is used directly in dungeon rooms.
- Other ROM fragments are the initial level-kit art; stock animated tiles and palette cycles beyond Green Hill field water are not simulated.
- Sound effects are silent in Sonic 1 and Sonic 2 fields while their ROM theme plays
  (see Audio).
