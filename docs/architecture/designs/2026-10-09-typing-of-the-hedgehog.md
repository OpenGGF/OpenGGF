# Typing of the Hedgehog (2026-10-09)

A Typing of the Dead homage as an OpenGGF example mod. Sonic runs the real stock acts of every
supplied ROM on rails with a keyboard strapped to his back; badniks carry words, and typing a
word spin-attacks its badnik. Source project: `examples/typing-of-the-hedgehog/`.

Base: `8668a9012` (`develop`). Branch: `feature/ai-typing-of-the-hedgehog` in
`.worktrees/ai-typing-of-the-hedgehog`.

## Brief and decisions

User brief (2026-10-09): a new mod with the polish and very high production values of the other
examples, inspired by The Typing of the Dead; type words to defeat badniks. Kickoff answers:

| Question | Decision |
|---|---|
| Core loop | Rails and stands: Sonic auto-runs real stock acts; camera-locked encounter stands plus run-and-type segments between them. |
| ROM scope | Any owned subset of S1, S2 and S3K; each supplied ROM adds its zones, badniks and bosses. |
| Scale | Full game: story across all owned zones (one act each), Arcade, Typing School, Endless, every zone boss as a typing set piece. |
| Voice | Offline-generated voice lines shipped in the jar (user's OpenRouter credit authorised); no runtime network. |
| Delivery | One PR against `develop`; lead plus a few subagents (the Slay the Robotnik arrangement). |

## Reference rules

From The Typing of the Dead (TotD, 1999/2000) and Overkill (2013), as documented by
[Wikipedia](https://en.wikipedia.org/wiki/The_Typing_of_the_Dead) and
[MobyGames](https://www.mobygames.com/game/77990): each enemy carries a prompt box (a letter,
word, phrase or sentence, growing harder through the game); bosses replace the prompt with an
altered test (a long phrase typed without error, or a question answered correctly); challenge
areas demand a number of kills within a time limit; rescues change the route; Drill mode trains
speed, accuracy, reflex and special keys with A–E grades and a Final Exam unlocked at C or
better; Boss mode replays bosses with a star level that rises each win; end-of-game assessment
reports speed, accuracy, reflexes, judgment and special keys with WPM and rank; Overkill's
error-free combos fill a multiplier and dictionaries can be mixed and user-supplied. Agents carry
the keyboards on Dreamcast backpacks. None of these establish numeric constants; ours are
explicit design choices.

## Game rules

### Typing

- Every target shows a word plate. Typing a character that begins a visible unlocked prompt
  locks the most urgent such target (least time to impact). Later characters must continue the
  locked prompt; a wrong character is an **error**: it does not advance, breaks the chain and
  is counted against accuracy. Backspace releases the lock (also breaks the chain).
- Matching is case-insensitive unless the Capitals option is on; spaces in phrases are typed;
  punctuation is typed only with the Punctuation option. Text comes from the operating system's
  character stream, so any keyboard layout types what it shows.
- Each correct key throws a ring spark from Sonic to the target and lights that letter; the last
  letter makes the kill: Sonic's spin flash, ROM explosion, freed animal and ROM score sprite.

### Health, lives and rings

- **Rings are health.** A hit with rings spills up to 20 of them as ROM bouncing rings, each
  carrying one letter for the spill lifetime; typing a letter collects the nearest ring with it.
  A hit with no rings costs a life and restarts the current stand.
- Rings collected by Sonic's run add to health. Lives start at 3; a game over offers a continue
  (score reset) or the results screen.
- Native physics are never changed by the typing layer: hits, spills and health are drawn and
  scored by the mod.

### Enemies

Zone badniks (drawn with the zone's own ROM art) map onto archetypes:

| Archetype | Behaviour → typing | Examples |
|---|---|---|
| Rusher | Short word, ground approach | Motobug, Crabmeat (body), Rhinobot, Grounder |
| Diver | Medium word, swoops to Sonic | Buzz Bomber, Batbrain, Buzzer, Flybot767 |
| Shooter | Body word; its shots carry one letter each (type to deflect) | Crabmeat, Coconuts, Aquis, Monkey Dude |
| Segmented | One word per segment, head last | Caterkiller, Caterkiller Jr, Rexon |
| Orbiter | Four single letters on the orbiting balls, then the body | Orbinaut, Asteron-style |
| Surfacer | Word only visible at the top of the jump or while surfaced | Chopper, Masher, Jawz, Chopchop |
| Armoured | Shell word then core word | Spiny, Bubbles, Penguinator |
| Elite | A long phrase; enters with a ROM warning cue | scaled zone badnik |
| Decoy | Red "Eggman" monitor or bomb: typing it hurts | stand trap |
| Rescue | Fleeing animal carrying a word: bonus | ROM animals |

Power monitors (ROM monitor art) carry power words: **Shield** absorbs a hit, **Speed Shoes**
slow time for 10 seconds, **Invincibility** pre-types half of every prompt for 10 seconds,
**Super Ring** adds 10 rings, **1-Up** adds a life. Seven emeralds (one per boss in story)
unlock **Super Sonic** in Arcade: first letters type themselves.

### Scoring and grades

- Kill score = word-tier points × chain multiplier. Tiers by length and keyboard difficulty
  (E 10, D 20, C 30, B 50, A 80, phrase 150). The chain counts error-free kills and steps the
  ROM's own score sprites (100, 200, 500, 1000); the multiplier is 1 + chain/8, capped at 4.
- The act tally is the stock act-clear card reworded: SONIC TYPED THROUGH, **SPEED BONUS**
  (WPM), **ACCURACY BONUS**, **RING BONUS**, **RESCUE BONUS**, PERFECT (no hits), then a grade
  S/A/B/C/D/E.
- WPM = (correct characters / 5) / active minutes, where active time counts only rows with a
  live target.

### Bosses

Mod-owned typing bosses drawn with the zone's ROM boss art and choreographed after the stock
fight. Archetypes, reused across zones:

- **Weak point**: a phrase opens on the boss's vulnerable pose and closes on a timer (GHZ Egg
  Wrecker, EHZ drill car, AIZ flame craft).
- **Talk back**: Robotnik's voiced taunt appears in full; typing it back stuns him for a hit.
- **Quiz**: Robotnik asks Sonic trivia with three answer plates; type the right one (one per
  game, the TotD final-boss homage).
- **Mirror duel**: Silver/Mecha Sonic types the same phrase at a fixed WPM; finish first.
- **Finale**: multi-phase per game (S1 Final Zone, S2 Death Egg Robot, S3K Doomsday) ending in
  a letter rain.

Each boss takes the stock hit count (8 unless the stock boss differs).

## Modes

- **Story**: one chapter per supplied game, one act per zone (the act holding the zone boss),
  played in order with a chapter map spanning every owned ROM. Each act has 4–8 stands, run
  segments and its boss: about 6–8 minutes.
- **Arcade**: any cleared act, local top-10 per act and difficulty.
- **Boss Rush**: cleared bosses back to back; each win raises that boss's star level.
- **Tails' Typing School**: home row, top row, bottom row, numbers, capitals and punctuation
  lessons; Ring Rush (60-second speed), Sudden Death (accuracy), Reflex (single letters),
  Long Words, Phrases and Boss Training drills graded A–E; a Final Exam at C or better
  everywhere; a per-key speed/accuracy heatmap on a Sonic-styled keyboard; an adaptive Weak Keys
  drill.
- **Endless**: one stand in any cleared zone with escalating waves.
- **Options**: difficulty (Easy/Normal/Hard/Expert: enemy speed and word tiers), dictionaries
  (Standard, Sonic Lore, Tricky, and a user word list in the mod's save folder) mixable as in
  Overkill, Capitals and Punctuation toggles, No-Fail and Slow-Motion assists, large plates,
  voice/music/effects volumes.

## Presentation

- Original pixel art (shipped): the title logo, Sonic's keyboard backpack (the Dreamcast nod),
  word plates and their font, lock-on reticle, chapter map frame, Typing School keyboard. Every
  zone, character, badnik, boss, monitor, animal, explosion, score sprite, ring, music track and
  sound effect comes from the user's ROMs at runtime.
- Voice cast generated offline: a B-movie announcer, Robotnik (taunts, quiz host) and Tails
  (instructor). Lines are original performances; no game voice cloning.
- Menus accept typed words (type STORY to choose Story) as well as arrows and Enter.

### Voice bank (2026-10-09)

`examples/typing-of-the-hedgehog/voice-bank.json` drafts 128 literal lines: 53 announcer, 18
Robotnik plus 30 talk-back taunts (tagged with their exact `taunts.txt` text and tag, from the
`any`/`s1`/`s2`/`s3k` tables the boss picks from), and 27 Tails. `tools/audio/typing_hedgehog_voice.py`
generalises Eggman's Sky's generator (receipted, never auto-retried paid requests, key-usage
ceiling, blind word checks, whole-utterance edits, approved-recipe pins) to per-speaker voices
and presets and generates `typinghog.audio.VoiceLine`. OpenRouter lists 21 ElevenLabs premade
voices for `elevenlabs/eleven-v4`. Auditions (three voices per speaker, two lines each, clean and
through a light 8-bit/16 kHz arcade DAC and a 7-bit/8 kHz Mega Drive DAC) moved reported key
usage from $1.827 to $1.932; the voices and presets await the user's choice. One take
repeated its line at least four times while Whisper reported a single utterance, so checks
also bound clip length and refuse degenerate word timestamps.

## Architecture

### Engine facts the design rests on

- A code mod has one `baseGame`; `any` may register only a startup scene
  (`ModContext.freeze`, `ModRegistrationPlan`). `ModRegistrationPlan.stockScenePlans()` already
  expands an `any` plan into per-game plans, and `ModBackedGamePatch` activates per game.
- A startup scene's only exits are the game title and the master title
  (`ModSceneLauncher`); it reopens only when a game launches.
- A mod `GameplayFrameController` (installed through a service bundle) chooses advance or hold
  per row, draws an overlay and owns state that course checkpoints deliberately omit;
  `CourseControl.capture/restore` snapshot every other rewind-registered subsystem.
  `ControlledFrameRuntime.step` runs the native step with a neutral pad.
- Typed text exists (`MenuInput.consumeText`) but reaches only menus holding an `InputHandler`;
  many default host keys are letters.
- Committed complete-run BK2s exist for all three games; open-loop replay desyncs after
  variable pauses, lag rows and configuration changes.

### Candidate Mod API additions

Each is generic and documented as a creator feature with its own test:

1. **Multi-game code mods**: `baseGame: any` code mods may register game patches, objects,
   service bundles and audio declarations; each patch names its game, and per-game plans keep
   only their game's patches.
2. **Scene ↔ gameplay**: a startup scene launches a stock act of the running game; gameplay
   returns to the startup scene; a scene may switch the running game (programmatic master-title
   selection) and reopens in the new game.
3. **Controlled input**: a frame controller supplies the row's pad input to the native step
   (default neutral).
4. **Text capture**: scenes and frame controllers receive the frame's typed characters, and while
   capturing, letter hotkeys and gameplay bindings are suppressed (pause and the master-title
   hold remain).

### Rails

Reviewed by a second-opinion consult (2026-10-09), which verified the mechanism against the code;
still subject to the feasibility kill tests below.

- **Programs.** Per act, an engine-frame pad-mask program in the jar. It is the same category of
  input as a BK2, never trace physics or aux data. Sources: the engine-authored cold routes
  under `src/test/resources/routes/s3k/` where they exist (no lag rows, cold act start), slices
  of the committed complete-run movies otherwise (lag rows identified offline from the
  fixture's lag columns). Stock badniks are removed from the spawn list before the first row
  (`LevelManager.resyncObjectSpawnListFromLevel`) and monitors are decided before validation,
  because both change slot order, RNG draw order and physics.
- **Validated length, not pass/fail.** Any prefix of a validated program is valid. The rail
  builder replays each program headless under the mod's exact configuration (400-pixel width,
  team, forced load-time simulation) and records the validated row count. The boss stand sits
  where validation ends when the stock arena is not reached. Each program stores the engine
  identity and ROM SHA-1 it was validated against; the completion test runs in the mod's own
  lane.
- **Stands.** Placed on program rows where Sonic is grounded with zero ground speed or flat
  ground, not riding an object, and presentation is ready. On entry the controller captures a
  course checkpoint and holds Sonic in place while the world keeps animating; on exit it
  restores the checkpoint behind a "GO!" flash and resumes the program, so a stand's
  player-dependent length never shifts the deterministic run. The partition includes V-int,
  RNG, oscillators, timers, camera and object/ring managers; audio stays outside by design.
  Auxiliary objects are in the partition (stand enemies roll back with it); score, effects and
  HUD state that must survive the restore live in the controller. A stand soak in the rail
  builder runs each stand neutral for the longest allowed stand and asserts no hurt, death or
  time-over. Rails end before an S3K act handover (a level change invalidates checkpoints). A
  native death is a logged rail fault, never a silent retry. Debug rewind is off.
- The typing layer never touches native player physics, RNG or native object slots.
- Bullet time advances the native world on only some rows.

**Kill-test results (rails lane, probe e87acba21, 2026-10-09; tables in the task directory).**
At 400 px no recording slice reaches a boss or act end (best variants drift after 234–2,558
rows; only the engine-authored S3K DDZ route completes). At 320 px with lag rows dropped, the
recordings replay exactly to the act end in S1 GHZ3, SYZ3, SLZ3, SBZ2 and FZ and S2 HTZ2, OOZ2
and ARZ2 (DEZ in sync to its last row; CPZ2 and MCZ2 reach the boss), but the recordings' own
hits cap validated length (S1 GHZ3 row 5044, SYZ3 1239, SLZ3 1141, SBZ2 2170), removing stock
badniks breaks the exact replays (GHZ3 drifts at row 373, MZ3 240, LZ3 123), S3K recordings drift
even at 320 from cold act-1 starts, and every cold act-2 slice drifts at row 0. Stand
capture/soak/restore passed in nine stands across all three games (Sonic solo and Sonic+Tails):
no adapter-layout rejection, no hurt in 600-row soaks, and Sonic's final state, rings, RNG and
counters equal an uninterrupted run. Caveat: in S1 and S2 CPZ2 a restore returns dynamic objects
in different object slots (and S1 palette colours differ, display only); Sonic was unaffected over
1,300–2,900 rows, but slot order sets execution and RNG order, so this is a latent engine restore
fidelity risk outside this task.

**Decisions.** View 320 px (`requireDisplayWidth(320)`). Stock badniks stay in the level (removal
breaks exact replays and S3K reuses badnik ids for bosses across zones); during runs they get
optional bonus plates instead, and the typed fights happen at stands. Teams: S1 Sonic alone, S2
and S3K Sonic and Tails (the recordings' team). Rails come from an offline repair tool that
replays a source program under the mod's exact semantics and repairs hurts and drift by local
input search against the recording's own 320 path, then picks soak-tested stand rows; a
regression test runs every shipped rail through the packaged course. A story stop uses act 1
when the boss act cannot be railed.

Kill tests, in order: (1) replay every source program headless at 400 wide with the chosen team
and record validated length (settles team and repair budget); (2) capture at the first stand,
play to the validated end, restore, and expect no layout-version rejection (including a
two-sprite Sonic and Tails roster); (3) typed text from a non-US layout reaches the controller.

### Mod structure

- One `baseGame: any` jar: the hub is its startup scene, and one patch per game
  installs the course controller and removes stock badniks through
  `transformDecodedLevel`. Shared state crosses the scene/gameplay boundary only
  through the mod's storage: the hub writes `intent.txt` (mode, act, difficulty,
  options, seed) before `startStockAct`; the course writes `result.txt` before its
  title request reopens the hub.
- The course controller owns all typing state (field, scorecard, rings, lives,
  enemies, effects). Enemies are controller records drawn by auxiliary puppet
  objects with the zone's own ROM art; a puppet whose record is gone (for example
  after a stand restore) deletes itself, so the controller stays authoritative.
- Plates and the HUD draw as screen-space fills with an original 5x9 face
  (`art/plate-font.txt`), exact ARGB colours independent of the zone palette. Each
  fill is one GL draw call, so text has no outline pass; plates sit on a dark
  translucent box instead. Profile before adding outlines; a baked glyph sheet is
  the fallback.
- The engine-free core (`typinghog.core`: prompt forms, typing field, scorecard,
  lexicon) is checked by `CoreChecks` without the engine.

## Delivery plan

1. Feasibility kill tests: controller-driven pad input reaches the act end on one act per game;
   stand capture/hold/restore resumes exactly.
2. Mod API additions with docs and pins.
3. Rail builder and all story act rails.
4. Typing core, enemies, bosses, HUD, results.
5. Hub scene: title, chapter map, Typing School, records, options.
6. Content: dictionaries, trivia, taunts, voice bank.
7. Captures, highlight reel, README, validation and PR.

## Verification record

(Filled in as work lands.)

## Rejected approaches

(Filled in as work lands.)
