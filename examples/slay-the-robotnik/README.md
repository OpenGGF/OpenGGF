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
python3 examples/slay-the-robotnik/build.py          # compile + package target/slay-the-robotnik/slay-the-robotnik.jar
python3 examples/slay-the-robotnik/build.py --run    # ...then launch the engine with it as a development mod
python3 examples/slay-the-robotnik/build.py --run --skip-engine   # reuse the engine build already in target/
```

To install it normally, copy the jar into `mods/` and enable it in the Mod Manager. Then
choose **Sonic 3 & Knuckles** on the master title: the mod's title screen opens instead
of the stock one. *Play Sonic 3 & Knuckles* on that menu returns to the stock game, and
holding Escape returns to the master title as everywhere else. The mod always plays in
16:9 (400×224); your aspect setting returns at the master title.

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

To play a card, pick it and then pick a target. Card numbers show the damage the card
would really deal to the highlighted enemy. Runs save at every room, so you can quit and
**Continue** later. The title screen also has the **Compendium** (every card, upgraded or
not; every relic; every monitor) and **Records** (runs, wins, best floor per hero).

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

## How it is built (a tour for modders)

```
src/main/java/slaytherobotnik/
  SlayTheRobotnikMod.java   registration: reads the text assets, registers the scene and a 16:9 patch
  core/                     the rules: cards, powers, combat, enemies, relics, potions, events, run state
  map/  run/                the act map generator and the run's rooms (fight, reward, shop, rest, event...)
  content/                  the game's data: every card, relic, monitor, enemy, encounter, event and act
  art/                      ROM sprite addresses (RomSprites), text art, card and relic picture recipes
  scene/  ui/               the screens: title, character select, map, fights, shop, compendium...
src/main/resources/
  META-INF/openggf-mod.yaml the manifest (a patch mod for base game s3k)
  art/font.txt icons.txt    pixel font and icons, drawn in text
  art/cards.txt relics.txt  one picture recipe per card and per relic
src/test/java/              rules tests, content checks and a bot that plays whole runs
```

**The rules know nothing about the engine.** `core/`, `map/`, `run/` and `content/` are
plain Java: a fight is a `Combat` that produces a list of `CombatEvent`s, which the screen
then replays as animations. That keeps the rules testable without a ROM and the screens
free of game logic.

**The scene is the only engine-facing part.** `SlayTheRobotnikMod.register` calls
`ModContext.registerStartupScene(...)`, so the engine opens `SlayScene` instead of the S3K
title. A scene gets a `SceneContext` each tick — input, the mouse in game pixels, music
and sound effects, file storage under `saves/mods/slay-the-robotnik/`, and a
`SceneCanvas` to draw on. Images come from PNGs, from pixels made in code (the text art),
or from the ROM through `SceneRomArt`: `RomSprites` lists each sprite's art, mappings,
DPLC and palette by its disassembly label, and `EnemyVisuals` assembles bosses from those
frames using the offsets of the original child objects. See the
[mod scene guide](../../docs/modding/guides/mod-scenes.md) for the API itself.

**Mod code has no static state.** The mod validator rejects enums, static collections and
static initialisers, so kinds such as card types are `String` constants and tables are
built per instance. `build.py` packages through `ggfmod`, which runs that validator.

## Extending it

Add a card (in `content/SonicCards.java`, say):

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

and give it a picture in `art/cards.txt`:

```
sonic:buzz_saw: fx speed; hero sonic anim=0x2 -6,2; rom explosion anim=0/1/2/3/4@4 18,0 x0.75
```

`{D}`, `{B}` and `{M}` show live damage, Block and magic numbers; `<g>…</g>`, `<r>…</r>`
and `*Keyword*` colour text. Relics are anonymous `Relic` subclasses in
`content/Relics.java` overriding the hooks they need, with a picture in `art/relics.txt`.
An enemy is an `Enemy` subclass choosing a `Move` each turn (see `content/AngelIsland.java`),
an encounter lists enemies for an act's pool, and an event is a short script written
against `EventContext` (`content/Events.java`). `ContentIntegrityTest` and
`CardRecipesTest` check new content is wired up.

## Testing

- `src/test/java` holds the rules tests and `RunBot`, which plays complete runs with
  every hero to prove no run can get stuck. The engine suite runs them
  (`TestSlayTheRobotnikExample`) after building and validating the mod, and
  `TestSlayTheRobotnikScene` (needs the S3K ROM) opens every fight and event through the
  real scene and checks nothing faults.
- `src/test/java/com/openggf/mods/code/SlayTheRobotnikCapture.java` in the engine takes
  headless screenshots; its script can jump straight into any room
  (`"10:jump=tails:7:fight:hcz:big_shaker"`, see `SlayScene.debugJump`).
- `src/test/java/com/openggf/mods/scene/SpriteSheetDump.java` renders every frame of a ROM
  sprite (or a character) into one numbered PNG, for choosing frames.

## Known gaps

- Zone backgrounds are drawn in code; ROM backgrounds are planned.
- Balance follows *Slay the Spire*'s numbers but has only been tested by the bot.
