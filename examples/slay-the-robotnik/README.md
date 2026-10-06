# Slay the Robotnik — a Sonic deck-building roguelike

A code mod for OpenGGF's JVM build: a complete *Slay the Spire*-style deck-builder played
on top of **Sonic 3 & Knuckles**. Choose Sonic, Tails or Knuckles, fly the Tornado to
Angel Island, and climb four acts of branching zone maps — fights against the zones'
badniks, elites and bosses, "?" events, the Egg Robo's shop, Starposts to rest at and
Egg Capsules full of relics — to Mecha Sonic at the top of Sky Sanctuary.

It is also the reference example for **mod scenes**: a mod that owns the whole screen
instead of patching a level. Everything you see — characters, badniks, bosses, monitors,
rings, the Tornado, explosions, music and sounds — comes from your own ROM at runtime.
The jar holds only code and text files.

## Requirements

- The OpenGGF JVM build (code mods don't load in native builds) from the `develop`
  line, which carries the Mod API 0.7 candidate.
- Your **Sonic 3 & Knuckles** (locked-on) ROM, set up as for the stock game.

## Build and run

```bash
examples/slay-the-robotnik/play.sh             # build what changed and play (opens straight into the mod)
examples/slay-the-robotnik/play.sh --fresh     # ...after deleting the saved run, records and settings
examples/slay-the-robotnik/play.sh --rebuild   # ...forcing an engine rebuild
python3 examples/slay-the-robotnik/build.py    # just compile and package target/examples/slay-the-robotnik/slay-the-robotnik.jar
```

`play.sh` (and `build.py --run`) launches the engine with the mod as a development mod,
which opens Sonic 3 & Knuckles, and so the mod's title screen, straight away. To install it
normally instead, copy the jar into `mods/` and enable it in the Mod Manager; then choose
**Sonic 3 & Knuckles** on the master title and the mod's title screen opens instead of the
stock one. *Play Sonic 3 & Knuckles* on that menu returns to the stock game, and
holding Escape returns to the master title as everywhere else. The mod always plays in
16:9 (400×224, from `ModContext.requireDisplayWidth(400)`), and the window refits to it; your
aspect setting returns at the master title.

## How to play

If you know *Slay the Spire*, you know the rules; the names are Sonic's.

| Slay the Spire | Slay the Robotnik |
|---|---|
| Gold | Rings |
| Potions | Item monitors (three slots) |
| Energy | Energy (shown as a Chaos Emerald) |
| Neow | The Tornado's cargo hold at the start of each act |
| Merchant | The Egg Robo ("Don't tell Robotnik, ya hear?!") |
| Campfire | Starpost: rest (heal 30%) or smith (upgrade a card) |
| Treasure chest | Egg Capsule |
| Ironclad / Silent / Defect | Knuckles (Strength) / Tails (Dexterity, Ring Bombs, discards) / Sonic (Focus, Combo) |

**Focus** is Sonic's stat: a **Combo N** effect repeats N times, plus one per Focus.
**Ring Bombs** are Tails' 0-cost throwaway attacks (his Shivs). Status, curse, Exhaust,
Ethereal, Retain, Innate, Vulnerable, Weak, Frail, Artifact, Intangible and the rest
behave as in *Slay the Spire*.

| Action | Keyboard | Gamepad | Mouse |
|---|---|---|---|
| Move the cursor | arrow keys (or your mapped directions) | d-pad | point |
| Choose / play a card | Enter or Space (or mapped A/C) | A, C | left click |
| Back / cancel | Backspace or Escape (or mapped B) | B | right click |
| End turn | E | Start | END TURN button |
| Deck / map | D / M | — | top-bar buttons |
| Use a monitor | 1–5 | — | click the slot |
| Scroll a map or card list | arrows (left / right on the map viewed from a room) | d-pad | wheel |

To play a card, pick it and then pick a target. Card numbers show the damage the card
would really deal to the highlighted enemy, and its gold terms (Exhaust, Combo,
Vulnerable, Ring Bomb...) are explained in tips beside whichever card you point at. The
top bar (hero, HP, rings, monitors, relics, zone) and a fight's energy, piles, Block and HP
bars have tips too, and clicking a pile shows its cards. Runs save at every room, so you
can quit and **Continue** later. The title screen also has the **Compendium** (every card, upgraded or
not; every relic; every monitor), **Records** (runs, wins, best floor per hero) and
**Settings** (combat speed, screen shake, music and sound effects).

Each act's map runs left to right over a zoomed-out picture of the zone's real level. Fights,
Starposts, capsules and events are staged in the level itself: each room picks a stretch of
the act's real floor - near the start for early floors, the far end for the boss - and the
characters stand on that ground in front of the zone's parallax background, all rendered from
your ROM.

## What's in it

| | |
|---|---|
| Heroes | Sonic, Tails and Knuckles, each with a 10–12-card starter deck of 3–4 basic cards, a starting relic and 42–43 more cards (16–17 common, 17 uncommon, 9 rare) |
| Other cards | colourless cards, status cards and curses |
| Relics | 55, in the usual tiers (starter, common, uncommon, rare, boss, shop, event) |
| Item monitors | 16 |
| Events | 22, across acts 1–3 |
| Acts | Angel Island, Hydrocity, Launch Base: 15-floor maps with weak and strong fights, three elites and two bosses each. Sky Sanctuary: a short final act — Starpost, shop, an Egg Robo elite pair, then Mecha Sonic, who seizes the Master Emerald and returns as Super Mecha Sonic. |

Bosses: Fire Breath and Flame Craft (Angel Island), Big Shaker and Screw Mobile
(Hydrocity), Big Arm and Beam Rocket (Launch Base), Mecha Sonic (Sky Sanctuary).

## How it was made

Slay the Robotnik was written in under twelve hours by Claude Opus 5.5, Anthropic's model,
working in Claude Code. The brief arrived at 17:36 BST on 5 October 2026 as a single message
describing the game. The last change to the mod's own code landed at 05:26 the next morning,
11 hours 50 minutes later. The engine-side clean-up and docs were pushed by 07:35.

- One lead agent planned and integrated the work, with a few subagents at a time taking
  self-contained pieces.
- Raiscan (`@raiscan`) wrote the brief and played the builds. Their feedback shaped each round
  after the first playable version:
  - the map's scroll wheel and tips for card terms and the top bar;
  - staging rooms in the real level;
  - the slot machine's real reels;
  - fewer map branches;
  - the final polish.
- That time produced the whole game above: about 29,000 lines of Java and 126 tests, including
  a bot that plays whole runs.
- It also produced the engine's mod scene API, the capture and sprite tools, the
  [hello-scene](../hello-scene/README.md) starter and this documentation.

The design record,
[2026-10-05-slay-the-robotnik.md](../../docs/architecture/designs/2026-10-05-slay-the-robotnik.md),
logs each step with its commits, including the approaches that were tried and dropped. The
project's [AI journey](../../docs/project/ai-journey.md) tells where this fits in OpenGGF's story.

## How it is built (a tour for modders)

New to mod scenes? Read [hello-scene](../hello-scene/README.md) first: it is the same idea in
two classes. This example is the same scene API used for a whole game.

```
src/main/java/slaytherobotnik/
  SlayTheRobotnikMod.java   registration: reads the text assets, registers the scene, asks for 400 wide
  core/                     the rules: cards, powers, combat, enemies, relics, potions, events, run state
  content/                  the game's data: every card, relic, monitor, enemy, encounter, event and act
  map/  run/                the act map generator and the run's rooms (fight, reward, shop, rest, event...)
  art/                      loading pictures: ROM sprite addresses (RomSprites), text art, picture recipes
  ui/                       small reusable helpers: font, colours, easing, panels, hotspots, controls
  scene/                    everything on screen: the scene, its screens, room views and pictures
src/main/resources/
  META-INF/openggf-mod.yaml the manifest (a patch mod for base game s3k)
  art/font.txt icons.txt    pixel font and icons, drawn in text
  art/cards.txt relics.txt  one picture recipe per card and per relic
src/test/java/              rules tests, content checks and a bot that plays whole runs
```

Each package has a `package-info.java` saying what belongs in it.

**The rules know nothing about the engine.** `core/`, `content/`, `map/` and `run/` are
plain Java with no engine imports. A fight is a `Combat` that applies every rule at once and
appends what happened to a list of `CombatEvent`s; the fight screen replays that list as
animations. The rules never wait for a frame, so the tests and the run bot play whole games
without a ROM or a window.

**One scene, a stack of screens.** The engine runs one `ModScene`; everything else is the
mod's own code:

```
engine ──► SlayScene            (ModScene: enter / update / draw / exit; DebuggableScene)
             └─► Shell          shared state: controls, font, art, music, fades, saving
                   └─► Screen   one at a time: TitleScreen, CharacterSelectScreen, CompendiumScreen,
                                RecordsScreen, SettingsScreen, or RunScreen
                         RunScreen: the HUD and its tips, flying rings/cards/relics, room transitions
                           └─► RoomView for run.room(): StartView, MapView, CombatView, RewardView,
                               EventView, ShopView, RestView, TreasureView, EndView
                                     ▲ reads and drives
run.Run ── room() ───────────────────┘   (plain Java; each Room is a small state machine)
```

Read it in that order: `SlayTheRobotnikMod`, `SlayScene`, `Shell` and `Screen`, `TitleScreen`,
`RunScreen`, then `CombatView` against `core/Combat`.

**Animation lives in `update`.** Views advance their timers in `update` and only read them in
`draw`, because the engine may skip or repeat `draw` (headless capture draws only some frames).

**The engine surface is 20 types**, all in two packages: `GgfMod` and `ModContext` to
register, and `ModScene`, `SceneContext`, `SceneCanvas`, `SceneDraw`, `SceneImage`,
`SceneSprite`, `SceneSpriteSet`, `SceneRomArt`, `RomSpriteRequest`, `SceneBackdrop`,
`SceneLevelStage`, `SceneButtons`, `SceneKeys`, `SceneMouse`, `SceneStorage` and
`DebuggableScene` to run. Everything else is plain Java. The pictures come from the ROM
through `SceneRomArt`:
- `art/RomSprites` lists each sprite's art, mappings, DPLC and palette by disassembly label;
- `scene/EnemyVisuals` assembles badniks and bosses from those frames, at the offsets of the
  original child objects;
- `scene/LevelStages` stands each room on a stretch of the act's real floor (`levelStages`,
  `levelForeground`);
- `scene/Backdrops`, `scene/MapView` and `scene/TitleCard` use the zone backgrounds, the
  level overview and the act title cards.

See the [mod scene guide](../../docs/modding/guides/mod-scenes.md) for the API itself.

**Mod code has no static state.** The mod validator rejects enums, static collections and
static initialisers, so kinds such as card types are `String` constants and tables are
built per instance. `build.py` packages through `ggfmod`, which runs that validator.

## Extending it

| To add | Touch |
|---|---|
| a card | `content/<Hero>Cards.java` (or `CommonCards`) for the rules; `art/cards.txt` for its picture |
| a relic | `content/Relics.java` (an anonymous `Relic` overriding the hooks it needs); `art/relics.txt` |
| an item monitor | `content/Potions.java` |
| an enemy | `content/<Zone>.java`: an `Enemy` subclass and an `EncounterDef` for its pool; `scene/EnemyVisuals.compose` for its look; `art/RomSprites` for any sprite it needs |
| an event | `content/Events.java`: a script against `EventContext`. To play its choices out, a `scene/<Name>Picture.java` (an `EventPicture`) added to `scene/EventPictures` |
| an act | `content/<Zone>.java` (`ActDef`: ROM zone and act, music, rooms) and its line in `content/Content` |
| a screen | a `scene/Screen`, opened with `shell.go(new ...)` |
| a sound or song | a constant in `scene/Sounds.java` (Sonic 3 & Knuckles' driver ID) |

For example, a card (in `content/SonicCards.java`, say):

```java
// Pommel Strike.
c.add(card("sonic:buzz_saw", "Buzz Saw", CardType.ATTACK, CardRarity.COMMON)
        .cost(1).target(CardTarget.ENEMY).damage(9, 1).magic(1, 1)
        .text("Deal {D} damage. Draw {M|card}.")
        .effect(p -> {
            p.attack();
            p.draw(p.magic());
        })
        .build());
```

and its picture in `art/cards.txt`:

```
sonic:buzz_saw: fx speed; hero sonic anim=0x2 -6,2; rom explosion anim=0/1/2/3/4@4 18,0 x0.75
```

`{D}`, `{B}` and `{M}` show live damage, Block and magic numbers; `<g>…</g>`, `<r>…</r>`
and `*Keyword*` colour text, and keywords get tips. `ContentIntegrityTest` and
`CardRecipesTest` check that new content is wired up.

## Testing and tools

- `src/test/java` holds the rules tests and `RunBot`, which plays complete runs with
  every hero to prove no run can get stuck. The engine suite runs them
  (`TestSlayTheRobotnikExample`) after building and validating the mod, and
  `TestSlayTheRobotnikScene` (needs the S3K ROM) opens every fight and event through the
  real scene and checks nothing faults.
- `SlayScene.debugJump` (the scene's `DebuggableScene` entry) jumps straight into any room,
  fight, event or compendium tab; its Javadoc has the command grammar.
- The engine's `ExampleModCapture` records the scene headless to PNGs, an MP4 and a WAV from
  an input script, using those jumps
  (`--jump "tails:7:fight:hcz:big_shaker" --script "150:enter +30:enter"`); see the
  [mod scene guide](../../docs/modding/guides/mod-scenes.md#6-testing-a-scene).
- `ggfmod sprites` draws every frame of a ROM sprite (or a character) into one numbered PNG,
  for choosing frames and checking `RomSprites` entries.

## Known gaps

- Balance follows *Slay the Spire*'s numbers but has only been tested by the bot.
- Backgrounds are still pictures: palette cycling, water tint and drifting HCZ waterlines
  are frozen at one state.
