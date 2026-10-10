# Typing of the Hedgehog — type to save South Island

A code mod for OpenGGF's JVM build, after *The Typing of the Dead*: on "3, 2, 1, START!"
Sonic spindashes into a real stock act and runs it on his own, stopping four or five times
on open ground where the zone's badniks (including the ones already there) come for him
carrying words. Type a word and Sonic homing-attacks its badnik. Type the rings that fly
when he is hit to win them back, and type Robotnik's weak points, taunts and trivia answers
to beat each zone's boss. A pulsing **GET READY** banner marks travel between fights
and clears when the next fight or boss warning begins. The HUD and hub fit both
320- and 400-pixel viewports. Freed animals use the current zone's own species and
ROM mapping frames.

Everything you see and hear from the games (zones, Sonic, badniks, bosses, monitors,
animals, explosions, score sprites, music and sound effects) comes from your own ROMs at
runtime. The jar holds code, text tables, an original pixel font and logo, and an original
generated voice cast: a B-movie announcer, Robotnik and Tails.

## Requirements

- The OpenGGF JVM build (code mods don't load in native builds) from the `develop` line,
  which carries the Mod API 0.7 candidate.
- Any of your **Sonic 1**, **Sonic 2** or **Sonic 3 & Knuckles** ROMs, set up as for the
  stock games. Each game you own adds its chapter; the others stay locked.

## Build and run

```bash
examples/typing-of-the-hedgehog/play.sh             # build what changed and play
examples/typing-of-the-hedgehog/play.sh --fresh     # ...after deleting progress, records and settings
examples/typing-of-the-hedgehog/play.sh --rebuild   # ...forcing an engine rebuild
python3 examples/typing-of-the-hedgehog/build.py    # just compile and package target/examples/typing-of-the-hedgehog/typing-of-the-hedgehog.jar
```

To install it normally, copy the jar into `mods/` and enable it in the Mod Manager. The
mod's title opens from the master title; holding Escape returns there. The mod plays at
400×224 (`WIDE_16_9`; the window refits). The routes are rebuilt and validated at that
width, including stock object activation and the boss arenas.

## How to play

Type the word on a badnik's plate. The first letter you type locks on to the most urgent
plate starting with it; the rest must follow. A wrong key is a miss and breaks your chain;
Backspace lets go of a locked word. Escape pauses (Resume, Retry the fight, Quit). The keys
light up on the keyboard Sonic wears as you type: gold for a hit, red for a miss.

- **Fights.** Sonic stops on open ground and the zone's badniks come in, the stock ones
  already there first, each with its own move: rushers charge, divers swoop, shooters fire,
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
- **Bosses.** ROM-art puppets follow the original attack shapes, with typing time before
  the hazard moves. Type its short dodge word to hop aside and return standing; miss it and
  Sonic loses rings. Green Hill patrols with its swinging wrecking ball. Final Zone alternates
  crushing cylinder pairs and falling plasma: each pillar has a word, and only the one showing
  Robotnik takes a hit (an empty pillar costs time). Death Egg walks, launches rocket fists,
  tracks Sonic for a body slam, and drops bombs; strike its exposed head between attacks.
  Emerald Hill charges with its drill and opens its cockpit afterward. Strike windows keep
  the phrase, talk-back and trivia styles. Death Egg takes twelve hits, the others eight.
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

Four acts have forward routes so far:

| Game | Acts |
|---|---|
| Sonic 1 | Green Hill 3 (five fights and the wrecking-ball boss); Final Zone (boss only) |
| Sonic 2 | Emerald Hill 2 (five fights and the drill-car boss); Death Egg (boss only) |
| Sonic 3 & Knuckles | none yet |

Every other story stop shows as under construction. A route must spindash off a standing
start, never run back the way it came, stop four or five times on open, near-flat ground
(the first within about ten seconds), and reach the stock boss arena without Sonic being
hurt or smashing a badnik himself; a search builds it from the act's own physics. The other
acts did not yet yield such a route: their early ground is sloped, sandy or underwater, they
start on the Tornado, or the search could not get past a wall, ramp or loop exit. The design
note lists each act and why.

## How it is built

- **Forward routes.** Each act's route (`rails/*.rail`) is a validated controller program
  (pad masks per row) that drives the stock act from a cold start: the spindash countdown
  (Sonic 1 runs in place to the ROM roll sound, then releases at top speed),
  the run, four or five fights and the boss arena. A bounded search over the headless engine
  builds it (`TypingHedgehogForwardRailBuilder`), checking every row for hurt, death,
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
