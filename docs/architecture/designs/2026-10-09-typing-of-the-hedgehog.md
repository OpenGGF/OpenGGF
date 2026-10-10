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

User review of the first captures (2026-10-10), and what it changed:

- **Keyboard.** The backpack picture did not read as a keyboard. Sonic now wears a QWERTY board
  about his height, sideways along his back (`art/KeyboardPack`, shared by the hub and the
  course), and its keys light gold for a right key and red for a wrong one as the player types.
- **Attacks.** Typed words no longer fire ring sparks at badniks. A completed word sends Sonic on
  a homing attack (`play/Homing`): he curls up, dashes to the badnik, monitor or boss, the kill
  lands on contact, and he bounces back over an arc to his stand spot before the next strike.
  The badnik is doomed (stopped, harmless) the moment its word completes.
- **Projectile plates.** Run tags labelled badnik projectiles (the badniks packages also hold
  missiles, shrapnel and child parts); only classes named `*BadnikInstance` carry words now.
- **Start.** Acts open with "3, 2, 1, START!".
- **Route.** The recorded runs wander, backtrack and smash badniks before the first stand. The
  wanted shape: a spindash countdown start, release on START while holding right, a plateau and
  the first fight at once, the level's loaded badniks joining the attackers, a short celebration,
  spindash bursts between fights, no backtracking, four or five fights an act besides the boss,
  and ideally branching paths. Rail authoring is being redesigned for this (consulting
  GPT-6.1-Sol on forward-only authoring, stock badniks in the pool, the countdown and branches).

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
usage from $1.827 to $1.932. One take repeated its line at least four times while Whisper
reported a single utterance, so checks also bound clip length and refuse degenerate word
timestamps.

The user chose harry (announcer) and callum (Robotnik) with the Mega Drive DAC, then asked for
the effect to be applied programmatically. The jar ships only the clean render: 101 lines, 266 s,
3.0 MB of bit-exact Ogg Vorbis (q4). Each line declares `voice-<line>` and `voice-<line>-md` on
the same file, the latter with the engine's per-SFX `dac:` block (8 kHz, 7 bits, 3.4 kHz
low-pass, 10% recovery above 2.4 kHz). The generator's gate mirrors `ModSfxDac` on the decoded
shipped clip. The engine point-samples the low-passed input, so it is grittier than the
auditioned `megadrive_strong`, which resampled through a polyphase anti-alias filter. Clean
clips pass the blind check strictly. Retakes fixed a repeated "Ten chain", a stuttered
"Impossible", a double growl, a 15-second laugh and a gate mishearing of "Curses". Two gate
mishearings are accepted and recorded per line: "Ten chain" heard as "Can-chain" and "Boss
rush" as "Both rush" (both also shown on screen). US spellings and "Dr." became orthographic
aliases. Letters are joined before repeats collapse, so "outtyped" matches "out-typed".
`rank_e` has a reviewed 3-second length bound (one reading with a deadpan pause).
stb_vorbis decodes every clip at its granule length; FFmpeg did not (recorded hazard).
`TestTypingHedgehogVoiceAssets` checks every clip through `OggPcmDecoder` against its
provenance and `VoiceLine` lease.

Tails round 1 was rejected: jessica and laura sounded "more girly than young" and liam "closer
to a Sonic than a Tails". Round 2 directs a bright, eager nine-year-old boy. It auditions will,
charlie, chris, river, bella and the retagged liam through the engine DAC, plus pitch/formant
shifts: +3 semitones pitch with +1.5 formant, and +5 with +2.5. The shifts use an FFmpeg rate
change plus Rubber Band with formants preserved, applied to liam, will and river (+3 only).
Mature voices, the two cast voices and the adult women were excluded.

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

**Restore slot-order caveat (kill test 2, rails lane, `TypingHedgehogRailProbe` at e87acba21).**
Nine stands (stock badniks removed, 600-row neutral soak, then restore and replay to the
validated end) kept P1 position, speeds, rings, `GameRng` seed and the level/V-int/timer
counters exactly equal to the uninterrupted run, with no layout-version rejection (43 in S1/S2,
52 in S3K, Sonic+Tails included). The whole course snapshot was identical for S3K DEZ1 and SOZ1
and S2 HTZ2. In S1 LZ3, S1 SBZ2 and S2 CPZ2, however, any restore (the no-soak control shows
the same) puts dynamic objects back in different SST slots: `object-manager.usedSlotsBits`
differs (CPZ2: slots 20-23 and 49-52 in the uninterrupted run, 0-3 and 31-34 after the restore),
along with the player's `interactSlotIndex` and `solid-execution` dynamic ids; S1 palette colours
also differ, depending on the soak. P1 was unaffected over 1,300-2,900 rows, but slot order is
execution and RNG-draw order, so a later object interaction can diverge after a stand. Engine
code is not changed in this lane. Mitigation: the rail builder accepts a stand only when Course's
whole stand chain (each stand captured, held for an 1800-row soak and restored) reproduces the
straight run's P1 state on every row, and the shipped-rail regression test drives the real
Course through every stand.

#### Rails v2: forward courses (2026-10-10)

The user's review of the first captures asked for a different route shape (see the Brief). GPT-6.1-Sol's
consultation (`sol-rails-design.md` in the task directory) proposed bounded engine-input search instead of
recording repair; the user's initial decisions were a stock Sonic 1 crouch/run launch (superseded by
the held-run polish below), while S2/S3K charge a native spindash under 3-2-1; the world stays animated during
fights (Sol's option (c)); one forward path per act first; misfit acts are dropped and named, never
relaxed per act.

- **Format 2** (`Rail.java`, engine-free, `RailChecks`): policy, combat mode, course kind (regular,
  boss-only), launch (native-spindash, native-run-roll or held-run with a burst), profile (width, S3K intros), ROM SHA-1, an
  authored countdown row, stand specs (before-row, quiet cleanup rows, the loaded-badnik manifest by class,
  slot and layout) and a stock endpoint; `validated` is the whole program. `waypoints` names rows that
  replay the act's recorded run (an authoring aid, validated like any other row).
- **Runtime** (`Course`): authored entry rows, a hold at the countdown row until presentation is ready
  (held rows change no native state and do not advance `V_int_run_count`), the native countdown under the
  3-2-1, then at each stand `CombatHandoff` takes the visible eligible pool (`Cast.convertible`), checks it
  against the manifest by class and layout, captures the checkpoint and converts it
  (`ObjectLifetimeOps.deleteNoRespawn`); the fight's world lives with Sonic held for 3600 native rows, then
  holds still while the fight goes on the combat clock; victory restores the checkpoint, puts the camera's
  frozen flag back to its captured value, reacquires the same bodies by identity and converts them again,
  so the boundary row plays exactly as authored. A native hit on the held Sonic mid-fight restores at once.
  A forward boss stand retires the stock boss family (`CombatHandoff.retireBoss`, arena machinery kept),
  lets two native rows unload it, then holds the world. Native faults, manifest or profile mismatches hand
  back to the hub as a RAIL FAULT. No run tags.
- **Builder** (`TypingHedgehogForwardRailBuilder`): beam search over input macros, every row checked for
  hurt, death, level change, native kills, camera/player backtracking (camera within 16 px of its high
  water, Sonic within 64 px, back within 30 rows) and stalls; fights at braked stops with flat floor
  samples (4 px over the strip), a grounded level 96 px strip walk, no collapsing ground, Sonic not behind
  high-priority foreground, an open view; each soaked 3600 held rows; four or five fights, the first within
  300 rows and 800 px of the release. The act's recorded run steers the beam as a comparison-only corridor
  and offers its own pad rows as waypoint macros; routes are replayed cold and under `V_int_run_count`
  start offsets `+1..+128`.
- **Gate** (`TestTypingHedgehogRails`): parses through `Rail.parse`; forward rails must qualify and pass
  four real-Course runs (typed at once, held 1800 rows, retried, hub-like clock phase 333), clean on every
  native row outside fights, with the boss rig's art registered at the endpoint. Native kills are seen as
  a badnik's slot turning into an explosion (the engine's `destroyBadnik` slot hand-off, the ROM's in-place
  ExplosionItem). Limit: a kill by an object that destroys a badnik without that hand-off would not show;
  the builder's search uses a score jump of 100 or more (Sonic or the CPU sidekick) instead.

**Authoring policy `forward-v2` (the user's rule for every act, 2026-10-10).** Sol's site bounds proved too
tight for most act openings (S3K HCZ1, MGZ1, MHZ1 and SOZ1 had no qualifying early stop at 4 px), so for every
act: floor range at most 8 px over a 128 px strip (40 px behind Sonic, 88 ahead), angle within 8, first
fight within 600 rows and 1600 px of the release; water and object support stay excluded and the soak
rejects a held Sonic who would drown. Loops are not backtracking (the user's complaint was a run that goes
forward and then back): while Sonic is grounded at wall or ceiling angles the backtracking bounds are not
measured, and he must regain his furthest x within 60 rows of leaving the loop; leftward travel on ordinary
floor stays forbidden. Rails name their policy and the gate applies it (EHZ2 was built under the stricter
`forward-v1`; its 400 px rebuild uses the uniform `forward-v2` rules). At sloped sites ground badniks follow the floor through a read-only probe in `Encounter`.

Probe findings that set these rules (`TypingHedgehogRailsV2Probe`, 5dc9c9cd6, S1 GHZ3, S2 EHZ2, S3K
SOZ1): host load-time simulation, cross-game features and live rewind leave the first 500 native rows
unchanged; S3K skipped intros change SOZ1 from row 0 (so the mod requires skipped intros, ec15b6093); the
production boot and hub start match the fixture natively but report presentation ready on row 20 (fixture
0, 52, 0); the act inherits the session's `V_int_run_count` (hub time) and EHZ2's old rail allocates
differently from row 523 under an odd phase, so rails are certified phase-robust rather than pinned; S1
charge pulses jump (DOWN only), S2/S3K pulses charge from the first and release at 0xB80; conversion
retires S1/S2 bodies and S3K Skorp's tail in one row but leaves S3K Sandworm's segments (Sandworm is not
convertible); converted S1 placements reload when the camera returns (forward courses never return); a
600-row live fight with homing writes, 20 search restores and the victory restore plus reconversion match
a zero-length fight over the next 300 rows in all three games.

Engine fixes found on the way: `ModDecoratedObjectRegistry` dropped the stock game's fixed SST occupants
under any object-registering mod (0247b568d), so the Course and builder worlds allocated differently from
row 1 in HCZ and MHZ.

**Acts (forward-v2 sweep, 2026-10-10; shipped courses refreshed at 400 px below; each attempt bounded).**

| Act | Result | Notes |
|---|---|---|
| S2 EHZ2 | qualified at 400 px (forward-v2) | 2292 rows, five fights; 492k search rows, 14 s; no recorded-input macros; five empty native pools (the zone's authored waves still fight); prior 320 px rail: 3549 rows, five fights, one loaded Buzzer |
| S1 GHZ3 | qualified at 400 px (forward-v2) | 2947 rows, four fights (one loaded Buzz Bomber and four Newtrons); held-run $600 release, recorded-input macro suggestions and the existing authored targets; 1.56M search rows, 54 s; prior 320 px rail: 3302 rows, five fights |
| S1 FZ, S2 DEZ | qualified at 400 px, boss-only | 408 and 341 rows (formerly 424 and 335); FZ uses held-run $600; DEZ uses native spindash; both have zero regular fights |
| S3K SOZ1 | dropped: no early site | dunes; under forward-v1, 1797 of 1844 early stops failed geometry; a wall of about 80 px at x 692 |
| S3K HCZ1 | dropped: no early site | every early stop in water (1159) or on an object (745) |
| S3K MHZ1 | dropped: no early site | flattest early stop 10 px over the strip; stall at x 905 |
| S1 MZ3, S3K SOZ2, LRZ1 | dropped: no early site | flattest early stops 10, 12 and 7 px with 3 samples missing |
| S3K MGZ1 | dropped: route goes back | one fight, then a 112 px wall at x 2165; the recording turns back left to a shaft at x 1700 |
| S2 SCZ, WFZ | dropped: no standing start | Sonic starts on the Tornado; never still on the ground |
| S1 SYZ3, LZ3, SLZ3, SBZ2; S2 ARZ2, HTZ2, MCZ2, OOZ2, MTZ3; S3K CNZ1, FBZ1, DEZ1 | dropped at a frontier | one or two fights, then every lineage dies at a wall, steep ramp or loop exit (furthest x 1141 to 7468); HTZ2, MCZ2, CNZ1, DEZ1 and SOZ2 hit the 10-minute cap; candidates for waypoints where a recording shows a forward route |

Forward courses only (the user's decision, 2026-10-10): the dropped acts ship no rail, their story stops
show as under construction (the S3K chapter has none railed yet), and the recording-derived format 1, the
world-held countdown, run tags and the legacy stand path were removed from the mod.

#### Rails v2 polish: widescreen and pace (2026-10-10)

Implementation `f258d5075`, on the rails lane after fast-forwarding the lead to `67d14a5ef`.
The user's next capture review asked for 400 px widescreen, continuous progress between fight
sites, and a Sonic 1 running-in-place countdown instead of crouching. All four courses remain
shipped. `TypingHedgehogMod` requires `WIDE_16_9`; every rail names `profile width=400`.
The gate checks that the profile matches the mod's declared aspect and actual course viewport;
art tables use and assert that profile width both at the act start and during endpoint replay.
Course and hub harnesses now use 400 px for the shipped behavior.

Sonic 1 now names `launch held-run` and `burst 0x600`. `Launch.holdRun` uses object control,
zero horizontal/vertical velocity, and ground speed $600 to drive the stock fast running
animation without moving. Each countdown beat plays the ROM roll effect through `Sounds.REV`
and the existing course sound path. Sources: `_incObj/01 Sonic.asm` defines `son_maxspeed=$600`
and `Sonic_Animate` selects `SonAni_Run` at $600; `_anim/Sonic.asm` uses frames $1E..$21;
`_Constants.asm` defines `sfx_Roll` from `ptr_sndBE` ($BE), played by `Sonic_Roll`'s `.roll`.
On START, `Launch.release` drops object control and sets horizontal and ground speed to the
header burst before the RIGHT row. The builder and art endpoint replay invoke those same
production methods. Both S1 routes qualify at $600; no lower burst was needed.
Only `Course.forwardCountdown` changed; ENTRY, RUN, stand and BOSS behavior remain as before.

`ForwardRails.Pace` measures native RUN rows. Stationary means less than 8 px displacement in
both axes over the preceding eight rows (rocking 640↔641 counts; a moving lift does not).
Slow roll means grounded rolling below $300 ground speed. Both measurements count braking
and settling; only countdown, a stand's declared cleanup rows and actual native spindash
charge are excluded. The inherited attempt excluded every DOWN row and the final 60 rows
before each stop; that masked precisely the slow rolling and long approaches being polished
and was replaced before the baseline below. Limits are 30 stationary and 45 slow-roll rows:
these allow short acceleration/braking/settling (the native stop macro asks for 12 still rows)
while rejecting the observed 150-row rocking ball and the longer measured stalls. Both maxima
penalize the beam score by 20 points per row, and exceeding either rejects a search child or
any of the gate's four modes. Metric controls cover the rocking ball, motion on a lift, the
$300 boundary, exclusions and independent copied search histories.

| Rail | Rows, before → after | Fights, before → after | Longest stationary, before → after | Longest slow roll, before → after |
|---|---|---|---|---|
| S1 GHZ3 | 3302 → 2947 | 5 → 4 | 273 → 24 | 32 → 0 |
| S1 FZ | 424 → 408 | 0 → 0 | 14 → 9 | 0 → 0 |
| S2 EHZ2 | 3549 → 2292 | 5 → 5 | 308 → 14 | 189 → 0 |
| S2 DEZ | 335 → 341 | 0 → 0 | 10 → 10 | 0 → 0 |

Before: `67d14a5ef` plus the unfinished lane metric, tightened as described above, at each
original 320 px profile. After: the implementation tree committed as `f258d5075`, at 400 px.
These are QUICK-mode gate measurements, not RLE neutral-input lengths: a neutral row can
still carry Sonic quickly or move him on a spring/platform. The same limits pass in HOLD,
RETRY and clock-phase 333 modes. Both boss-only courses intentionally have no regular fights.

Tried and rejected in that worktree before `f258d5075`:

- Keeping the inherited DOWN/last-60-rows exclusions would hide slow rolls and approaches;
  the tightened original rail reports above demonstrate both checks fail the old regular routes.
- EHZ2 with the default recorded-input macros found 2504 rows and five fights, with maxima
  18 stationary / 11 slow roll, but cold replay differed in occupied native slots at fight
  row 1849 (for example search slots 31/32 held ids 75/6; cold replay held 8/8). It was not
  written or shipped. The slot comparison stayed intact; this lane did not repair an unrelated
  snapshot/slot owner. This is evidence of a rejected candidate, not a diagnosis of its cause.
- Trying to continue the same Search immediately after its cold validation was also discarded:
  validation opens a new World/global session, so the old search cannot simply resume afterwards
  (its old level service was null). The existing one-search-then-replay lifecycle remains.
- EHZ2 at 400 px under `forward-v1` found no early site: all 124 attempted sites failed
  geometry/support/slope/strip checks; no soak or fight was accepted, and the bounded search
  stopped at x 1096 after 1.04M rows / 41 s. The shipped replacement uses the uniform
  `forward-v2` policy, not an EHZ-specific exemption, with `typinghog.forward.recorded=false`.
- An initial new START assertion inspected speed after native physics and wrongly expected
  exact $600 on GHZ3's start terrain. The corrected separate test invokes the actual Course's
  release boundary before physics and requires $600; the ordinary gate requires release and
  immediate forward movement after physics. It still requires all four fast running frames
  and no displacement throughout the countdown.

Reproduction: set `OPENGGF_ROM_ROOT` to the absolute directory containing the original
`s1.gen`, `s2.gen` and `s3k.gen` files, to avoid silent ROM skips. The actual runs used those
verified files in the main checkout; no ROMs were copied or linked into this worktree.

```bash
: "${OPENGGF_ROM_ROOT:?Set the absolute directory containing the three ROMs}"
# GHZ3/FZ/DEZ: default recorded suggestions; GHZ3's established authored targets.
python3 tools/testing/maven_queue.py -Dmse=off -Dmaven.compiler.fork=true \
  -Dtest=TypingHedgehogForwardRailBuilder -Dtypinghog.forward=ghz3,fz,dez \
  '-Dtypinghog.forward.waypoints=ghz3:600,740;750,514;1040,621;1705,508;1918,639;2589,524' \
  -Dtypinghog.forward.out=target/typinghog-wide \
  -Dsonic1.rom.path="$OPENGGF_ROM_ROOT/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_ROM_ROOT/s3k.gen" test
# EHZ2: native search without recorded-input macros. Both commands cold/phase-validate.
python3 tools/testing/maven_queue.py -Dmse=off -Dmaven.compiler.fork=true \
  -Dtest=TypingHedgehogForwardRailBuilder -Dtypinghog.forward=ehz2 \
  -Dtypinghog.forward.recorded=false -Dtypinghog.forward.out=target/typinghog-wide \
  -Dsonic1.rom.path="$OPENGGF_ROM_ROOT/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_ROM_ROOT/s3k.gen" test
python3 tools/testing/maven_queue.py -Dmse=off -Dmaven.compiler.fork=true \
  '-Dtest=TestTypingHedgehog*' \
  -Dsonic1.rom.path="$OPENGGF_ROM_ROOT/s1.gen" \
  -Dsonic2.rom.path="$OPENGGF_ROM_ROOT/s2.gen" \
  -Ds3k.rom.path="$OPENGGF_ROM_ROOT/s3k.gen" test
mvn -Dmse=off compile
python3 examples/build_example.py typing-of-the-hedgehog --skip-engine
```

Final focused result on the tree committed as `f258d5075`: 64 tests in 14 classes, zero
failures/errors/skips; rail gate four tests, all four courses × four modes; art tables 62 S1
and 52 S2 frame uses. S3K has no shipped route and reports nothing to check, rather than
skipping for a missing ROM. The three ROM SHA-1s match the repository's pinned identities.
Packaging passes validation (12 engine-internal-reference warnings, no errors).
An in-process javac invocation twice failed generic inference in unchanged
`TestModApiSignatureSurface:99`; a fresh forked Java 21 compiler succeeded, so the final
commands above explicitly use `maven.compiler.fork=true`. No unrelated test was edited.

Validation scope: the change-based plan against `67d14a5ef` selected 3094 classes and guards
because example paths are unclassified. Proportionate validation used the complete requested
mod set and ROM-backed route/art checks instead: no engine production code or public engine
contract changed. This is a focused pass, not a full ordinary/guard suite pass. Existing
native-kill detection limits and unqualified S3K/other acts remain; the new EHZ2 sites have
empty native conversion pools. No lead-branch integration or push belongs to this lane.

### Mod structure

- One `baseGame: any` jar: the hub is its startup scene, and one patch per game
  installs the course controller. Stock badniks stay in the level: removing them
  changes the recorded runs the rails replay (see Rails). Shared state crosses the scene/gameplay boundary only
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
- Who each zone stages is data: `cast/<game>.txt` lists its badniks (stock art key,
  archetype, ROM animation frames), `props/<game>.txt` the monitor icons and animal,
  and `bosses/<game>.txt` its boss rig (`BossRig`): ROM sprite parts drawn back to front
  round Robotnik, each with a frame cycle per mood (idle, window open, lunge, hit,
  laugh, defeat, flee) and optional pendulum (GHZ wrecking ball), chain links or orbit
  (FZ plasma). Frames and delays come from the stock bosses' mappings and animation
  scripts. `TestTypingHedgehogArt{Sonic1,Sonic2,S3k}` load every shipped rail's act and
  fail on any table frame that act cannot draw.
- The boss plays the stock fight's beats: it enters from the right, lunges at Sonic
  when a window closes unanswered (wind-up, dash, hit, laughing retreat), shudders
  back when struck, and when beaten explodes in place for 120 rows before fleeing
  right with its escape flame, as the stock bosses do.
- Draw order: the engine draws display list 0 front-most (as the ROM's BuildSprites),
  and slotless auxiliary objects in one list draw in no fixed order (`activeObjects` is an
  identity map). A boss is therefore one compound visual whose single puppet draws the
  rig's sprites in file order (list 3, behind Sonic, as the stock bosses), with `back`
  parts in a second visual one list further back. Explosions and score sprites use list 1.
- Rig art gaps found while compiling the tables (from reading the engine, not yet run):
  S3K `robotnik_ship` is not registered in CNZ, MHZ, LRZ, DEZ or DDZ, nor in act 1 of
  MGZ, FBZ and SOZ; `cnz_end_boss` loads only behind the stock boss's camera gate; S2
  Metropolis act 3 (ROM zone $05) registers no `mtz_boss`. Possible stock-boss rendering
  bugs seen on the way: the engine's MTZ boss face frames ($D/$E/$F) disagree with
  `Ani_obj53` ($C/$D normal, $E/$F laugh, $10 hit, $11 defeat) and it draws frame 0 (jet
  tiles the sheet lacks) as the pod; the CNZ boss treats face frames 6/7 as a propeller.
- A `demo=true` intent makes the course type for the player (the locked word's next
  letter, else the most urgent word's, about ten keys a second, on its own random
  stream). It drives captures, and the hub can use it for an attract demo.

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

- **Recording repair as the rail source (superseded 2026-10-10).** Recorded runs wander, backtrack and
  smash badniks before the first stand; repairing them kept that shape. Forward courses are searched
  from the engine's own inputs instead (Rails v2).
- **Restoring mid-fight at the native cap (rejected 2026-10-10).** A periodic restore snaps platforms and
  anything moving back to the stand's checkpoint, which the player sees; past the cap the world holds
  still instead and the fight continues on the combat clock.
- **A frozen stock boss beside the typing boss (rejected 2026-10-10).** A boss-arena endpoint reached
  after the stock boss spawns left it on screen; the boss stand retires it (`CombatHandoff.retireBoss`).

- **HCZ1 stand-restore crash blamed on puppets (rejected 2026-10-09).** The real Course's
  restore of the HCZ1 stand at rail row 1214 throws `Missing required object reference`
  (`DefaultSolidExecutionRegistry.restore`) for `HCZCGZFanObjectInstance$FanPlatformChild`
  (spawnless, `RewindRecreatable`), while the rail builder's chain restores the same stand
  held 1872 or 1850 rows with both fan children alive (`TypingHedgehogStandRestoreProbe`).
  Two explanations were killed: the child despawning during a long stand (it is alive on
  the last held row), and rewindable puppets drawing rewind ids from the act's counter
  (puppets moved to the non-rewindable auxiliary path; the failure is unchanged, same
  `dynamicId=92`). Cause (rails lane, 04ad7e361): the Course restores inside its
  `beforeTick`, under the creator owner scope, so engine children rebuilt during the
  restore inherited the mod as owner and `adoptRewindReconstructionChild` dropped them
  as unmatched. `CourseControl.restore` now runs with no creator owner in scope
  (`EngineOwnerScope`, engine callers only); `TestCourseRestoreCreatorOwnerScope` failed
  before the fix, and HCZ1 ships (ddd1e941e).
