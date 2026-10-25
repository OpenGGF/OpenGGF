# Typing of the Hedgehog — type to save South Island

A code mod for OpenGGF's JVM build, after *The Typing of the Dead*: on "3, 2, 1, START!"
Sonic runs through selected parts of a real stock act, stopping four times
on open ground where the zone's badniks come for him
carrying words. Type a word and Sonic homing-attacks its badnik. Type the rings that fly
when he is hit to win them back, and type Robotnik's weak points, taunts and trivia answers
to beat each zone's boss. A pulsing **GET READY** banner marks travel between fights.
It waits for the countdown and launch banner to clear, and yields to every fight, warning
and result banner. The HUD and hub fit both
320- and 400-pixel viewports. Freed animals use the current zone's own species and
ROM mapping frames.

Everything you see and hear from the games (zones, Sonic, badniks, bosses, monitors,
animals, explosions, score sprites, music and sound effects) comes from your own ROMs at
runtime. The jar holds code, text tables, an original pixel font and logo, and an original
generated voice cast: a B-movie announcer, Robotnik and Tails.

## Requirements

- Java 25 and the OpenGGF JVM build (code mods don't load in native builds) from the `develop` line,
  which carries the Mod API 0.7 candidate.
- Any of your **Sonic 1**, **Sonic 2** or **Sonic 3 & Knuckles** ROMs, set up as for the
  stock games. Each game you own adds its chapter; the others stay locked.

## Build and run

```bash
examples/typing-of-the-hedgehog/play.sh             # build what changed and play
examples/typing-of-the-hedgehog/play.sh --fresh     # ...after deleting progress, records and settings
examples/typing-of-the-hedgehog/play.sh --rebuild   # ...forcing an engine rebuild
python3 examples/typing-of-the-hedgehog/build.py    # just compile and package target/examples/typing-of-the-hedgehog/typing-of-the-hedgehog.jar
python3 examples/typing-of-the-hedgehog/build.py --help # explicit ROM and matching engine/SDK paths
```

To install it normally, copy the jar into `mods/` and enable it in the Mod Manager. The
mod's title opens from the master title; holding Escape returns there. The mod plays at
400×224 (`WIDE_16_9`; the window refits). Continuous rails are validated at that
width with stock object activation. Selected sections rehearse native terrain
movement in isolation and keep the stock world held during their encounters.

## How to play

Type the word on a badnik's plate. The first letter you type locks on to the most urgent
plate starting with it; the rest must follow. A wrong key is a miss and breaks your chain;
Backspace lets go of a locked word. Escape pauses (Resume, Retry the fight, Quit). The keys
light up on the keyboard Sonic wears as you type: gold for a hit, red for a miss.

- **Fights.** Sonic stops on open ground and the zone's badniks come in. Continuous
  courses convert the stock badniks already there first; selected sections use their
  prepared ROM-art cast. Each has its own move: rushers charge, divers swoop, shooters fire,
  segmented badniks bring a chain of words, orbiters shield a word behind smaller ones,
  surfacers pop up, armoured ones take two words. Each finished word sends Sonic on a homing
  attack: he curls up, strikes the badnik and bounces back to his spot. Clear the wave, and
  after a short celebration he spindashes on.
- **Rings.** Sonic carries rings as a buffer: a badnik that reaches him spills them as
  letter-rings you can type back before they fade. Hit with none, he loses a life and the
  fight starts again (with No-Fail he shrugs it off, rings refilled).
- **Monitors.** Some fights bring a monitor with a word: *shield*, *speed shoes* (slow
  motion), *invincible*, *super ring*, *extra life*. The Eggman monitor is a trap.
- **Rescues.** Freed animals carry a short word; typing it rescues them for points.
- **Bosses.** ROM-art puppets use typing attack windows. Green Hill, Final Zone,
  Emerald Hill and Death Egg have dedicated adaptations of their original attacks;
  the other bosses use their ROM-art rigs with a shared moving-hazard pattern.
  Type its short dodge word to hop aside and return standing; miss it and
  Sonic loses rings. Green Hill patrols with its swinging wrecking ball. Final Zone alternates
  crushing cylinder pairs and falling plasma: each pillar has a word, and only the one showing
  Robotnik takes a hit (an empty pillar costs time). Death Egg walks, launches rocket fists,
  tracks Sonic with its landing sensor and impact hazard, and drops bombs; its body keeps a
  clear gap from his stand. Strike its exposed head between attacks.
  Emerald Hill charges with its drill and opens its cockpit afterward. Strike windows keep
  the phrase, talk-back and trivia styles. Talk-back speaks and displays the whole taunt
  while you echo a short clause or quotation. A completed dodge releases its attack immediately.
  Death Egg takes twelve hits, the others eight. The four dedicated arenas fill the 400-pixel
  view through entry and tally.
  Boss Rush uses the same fights at the act's start; native boss AI is retired during typing.
- **Scoring.** Kills chain into a multiplier; the tally adds speed, accuracy, ring and
  perfect bonuses and grades the act S to E.

The menus take typed words too: type STORY, ARCADE, BOSS RUSH, ENDLESS, TYPING SCHOOL,
RECORDS or OPTIONS, or use the arrows and Enter. Leave the title alone for twenty seconds
and it plays a demo, typing by itself; any key returns.

## Modes

- **Story**: one chapter per game you own, one act per zone (the act holding its boss), in
  play order across a chapter map. Clearing an act opens it for Arcade and Boss Rush.
- **Arcade**: any cleared act, with a local top 10 per act and difficulty.
- **Boss Rush**: cleared bosses back to back; each win raises that boss's star level.
- **Endless**: one fight in a cleared zone with escalating waves.
- **Tails' Typing School**: home row, top row, bottom row, numbers, capitals and
  punctuation lessons; Ring Rush, Sudden Death, Reflex, Long Words, Phrases and Boss
  Training drills graded A to E; a Final Exam once every drill is at C or better; and a
  per-key speed and accuracy map of your keyboard.
- **Records**: your best scores, grades and keys.
- **Options**: difficulty (Easy, Normal, Hard, Expert: enemy speed and word length), word
  lists mixed as you like (Standard, Sonic Lore, Tricky, and your own `words.txt` in the
  mod's save folder, one word or phrase per line), Capitals, Punctuation, No-Fail,
  Slow-Motion, and VOICE: MEGA DRIVE (the engine renders the voice cast through a Mega
  Drive-style DAC), CLEAN, or OFF.

## Acts

The story covers all 31 zone stops: seven in Sonic 1, eleven in Sonic 2 and thirteen in
Sonic 3 & Knuckles. Four retained courses traverse the stock act continuously:

| Game | Story stops | Continuous courses |
|---|---:|---|
| Sonic 1 | 7 | Green Hill 3; Final Zone (boss only) |
| Sonic 2 | 11 | Emerald Hill 2; Death Egg (boss only) |
| Sonic 3 & Knuckles | 13 | All use selected sections |

The other ground stages use shorter sections chosen from the loaded ROM terrain. Sonic
runs and brakes at four separate fights, then meets the zone's boss. Each section is
rehearsed through native movement and collision before it starts; fight delays, pause,
slow motion and retries preserve its continuation. Sky Chase is four airborne escort
waves on the native Tornado, and Doomsday is a flying finale with Super Sonic and the
ROM ship. Boss Rush lists stages with bosses; Endless lists stages with regular waves.

Selected sections hold the stock world while the isolated native player drives Sonic's
presentation. Their typed enemies and bosses use independent ROM-backed sprite sheets,
including boss art normally loaded near the end of an act. They do not certify a
continuous stock-start route or reproduce the stock boss's AI.

## How it is built

- **Selected sections.** `sections.txt` names each stage and its ground, escort or finale
  role. The ground selector finds real supported terrain and rehearses four arrivals plus
  the boss destination through native physics, using Stageplay's shared steering and
  braking policy. A route checkpoint travels with the combat checkpoint. ROM art is
  decoded into private sprite sheets and uses owned atlas space; native sheets and VRAM
  remain with the stock world. The section gate completes every enabled stage through
  the real course, including pause, slow motion and retry; the art gate checks all 31 stops
  and rejects blank or incorrectly mapped frames.
- **Forward routes.** Each act's route (`rails/*.rail`) is a validated controller program
  (pad masks per row) that drives the stock act from a cold start: the spindash countdown
  (Sonic 1 runs in place to the ROM roll sound, then releases at top speed),
  the run, four or five fights and the boss arena. A bounded search over the headless engine
  builds it (`TypingHedgehogForwardRailBuilder`). It shares Stageplay's native movement,
  braking and stopping policy: authors can give ordered centre-coordinate destinations
  instead of planning individual button holds, and the search suggests braked fight stops.
  Stageplay keeps its scene qualification; this builder checks the continuous act, including
  every row for hurt, death,
  backtracking, native kills and long stationary/slow-roll stretches, and each fight site for
  open, near-flat ground. At a fight
  the course captures a checkpoint, converts the stock badniks on screen into typing targets
  and lets the world live on; when the wave is cleared it restores the checkpoint and removes
  the same badniks again, so the run continues exactly as authored however long the fight
  took. `TestTypingHedgehogRails` plays every shipped route through the real course, typed at
  once, held, retried and from another clock phase, and compares the native state.
- **Puppets.** Typing badniks, effects and bosses are records in the course's stagehand,
  drawn by slotless auxiliary objects with the zone's ROM art; they never touch native
  object slots and stay out of rewind snapshots.
- **Cast and bosses as data.** `cast/<game>.txt` lists each zone's badniks with their stock
  art keys and ROM animation frames; `bosses/<game>.txt` builds each boss from ROM sprite
  parts per mood (idle, window open, lunge, hit, laugh, defeat, flee), with pendulums
  (Green Hill's wrecking ball), orbits (Metropolis's orb shield) and per-act minibosses.
  `TestTypingHedgehogArt{Sonic1,Sonic2,S3k}` load every route's act and fail on any frame
  the act cannot draw.
- **Frame controller.** The course is a Mod API `GameplayFrameController`: it supplies the
  native pad input from the rail, captures keyboard text, and draws the HUD and plates as
  screen-space fills in an original 5×9 font.

The design record, with the feasibility tests and rejected approaches, is
[docs/architecture/designs/2026-10-09-typing-of-the-hedgehog.md](../../docs/architecture/designs/2026-10-09-typing-of-the-hedgehog.md).
