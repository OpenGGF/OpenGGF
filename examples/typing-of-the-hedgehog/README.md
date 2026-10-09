# Typing of the Hedgehog — type to save South Island

A code mod for OpenGGF's JVM build, after *The Typing of the Dead*: Sonic runs the real
Sonic 1, Sonic 2 and Sonic 3 & Knuckles acts on his own, and at each stand the badniks of
that zone come for him carrying words. Type a word to destroy its badnik. Type the rings
that fly when he is hit to win them back, tag the badniks he smashes on the run for a
bonus, and type Robotnik's weak points, taunts and trivia answers to beat each zone's boss.

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
the native 320×224 (it requires that width, and the window refits); the rails Sonic runs
were validated at 320 pixels, where the stock acts' objects activate exactly as recorded.

## How to play

Type the word on a badnik's plate. The first letter you type locks on to the most urgent
plate starting with it; the rest must follow. A wrong key is a miss and breaks your chain;
Backspace lets go of a locked word. Escape pauses (Resume, Retry the stand, Quit).

- **Stands.** The run stops and the zone's badniks come in, each with its own move: rushers
  charge, divers swoop, shooters fire, segmented badniks bring a chain of words, orbiters
  shield a word behind smaller ones, surfacers pop up, armoured ones take two words.
  Clear the wave and Sonic runs on.
- **Rings.** Sonic carries rings as a buffer: a badnik that reaches him spills them as
  letter-rings you can type back before they fade. Hit with none, he loses a life (or, with
  No-Fail, retries the stand).
- **Monitors.** Some stands bring a monitor with a word: *shield*, *speed shoes* (slow
  motion), *invincible*, *super ring*, *extra life*. The Eggman monitor is a trap.
- **Rescues.** Freed animals carry a short word; typing it rescues them for points.
- **Run tags.** While Sonic runs, the badniks he is about to smash wear words; tag one
  before he reaches it for a bonus.
- **Bosses.** Each zone's boss is built from that zone's own boss sprites. It opens a
  window carrying a phrase, one of Robotnik's taunts to type back, or a trivia question
  with three answers; close it in time and he takes a hit, miss and he lunges at Sonic.
  Eight hits win. In Sonic 3 & Knuckles act 1 the boss is the act's miniboss.
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
- **Endless**: one stand in a cleared zone with escalating waves.
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

Twenty-four acts have rails (the recorded runs Sonic follows):

| Game | Acts |
|---|---|
| Sonic 1 | Green Hill 3, Marble 3, Spring Yard 3, Labyrinth 3, Star Light 3, Scrap Brain 2, Final Zone |
| Sonic 2 | Emerald Hill 2, Aquatic Ruin 2, Hill Top 2, Mystic Cave 2, Oil Ocean 2, Metropolis 3, Sky Chase, Wing Fortress, Death Egg |
| Sonic 3 & Knuckles | Marble Garden 1, Carnival Night 1, Flying Battery 1, Mushroom Hill 1, Sandopolis 1 and 2, Lava Reef 1, Death Egg 1 |

Story stops without a rail yet show as under construction: Chemical Plant 2 and Casino
Night 2 replay differently between sessions; Angel Island 1, Hydrocity 1, IceCap 1, Launch
Base 1, Sky Sanctuary and Doomsday are not yet reproducible or are pending an engine fix.

## How it is built

- **Rails.** Each act's rail is a validated controller program (pad masks per row) that
  drives the stock act from a cold start, with stand and boss rows. A stand captures a
  course checkpoint, holds Sonic while the world keeps living, and restores the checkpoint
  when the wave is cleared, so the run continues exactly as recorded however long the
  stand took. Stock badniks stay in the level: removing them would change the run.
  `TestTypingHedgehogRails` plays every shipped rail through the real course.
- **Puppets.** Typing badniks, effects and bosses are records in the course's stagehand,
  drawn by slotless auxiliary objects with the zone's ROM art; they never touch native
  object slots and stay out of rewind snapshots.
- **Cast and bosses as data.** `cast/<game>.txt` lists each zone's badniks with their stock
  art keys and ROM animation frames; `bosses/<game>.txt` builds each boss from ROM sprite
  parts per mood (idle, window open, lunge, hit, laugh, defeat, flee), with pendulums
  (Green Hill's wrecking ball), orbits (Metropolis's orb shield) and per-act minibosses.
  `TestTypingHedgehogArt{Sonic1,Sonic2,S3k}` load every rail act and fail on any frame the
  act cannot draw.
- **Frame controller.** The course is a Mod API `GameplayFrameController`: it supplies the
  native pad input from the rail, captures keyboard text, and draws the HUD and plates as
  screen-space fills in an original 5×9 font.

The design record, with the feasibility tests and rejected approaches, is
[docs/architecture/designs/2026-10-09-typing-of-the-hedgehog.md](../../docs/architecture/designs/2026-10-09-typing-of-the-hedgehog.md).
