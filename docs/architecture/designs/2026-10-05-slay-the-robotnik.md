# Slay the Robotnik — deck-building roguelike example mod

Task: build "Slay the Robotnik", a Slay the Spire–style deck-building roguelike, as a
documented OpenGGF example mod that feels like a complete game rather than a prototype.
Base: `fa129ccf4` (`develop`, after the Infinite Sonic merge).
Branch: `feature/ai-slay-the-robotnik` in `.worktrees/ai-slay-the-robotnik`.
Source project: `examples/slay-the-robotnik/` (same layout as `examples/infinite-sonic/`).

## Brief and decisions

The user's brief (2026-10-05), using Slay the Spire (StS) terminology:

- Three playable characters, each with a primary stat and a card colour:
  - **Sonic** (blue): quick attacks, multi-hit, Vulnerable stacking, Exhaust.
    Primary stat **Focus**: +1 repetition per stack on multi-hit/multi-action cards.
  - **Tails** (orange-yellow): utility, discards, summoned shiv-like token cards.
    Primary stat **Dexterity**: +1 Block per stack on every Block gain.
  - **Knuckles** (red): big hits. Primary stat **Strength**.
- Card types as StS: Attack, Skill, Power. Relics behave as in StS.
- Each act is a zone presented as an StS map of chained node icons. Node types: Start,
  Enemy, Elite, Event, Treasure, Shop, Rest Site, Boss.
- Start: flying the Tornado over an endlessly scrolling zone-themed background, with
  Neow-style opening rewards (and smaller rewards at the start of later acts, as StS2).
- Enemy: zone badniks with health pools and per-type intent cycles. Elite: scaled-up
  bigger badniks with more elaborate debuff/attack turns. Boss: the act boss, turn-based.
- Shop: the Egg Robo sells cards, relics, potions and one card removal for rings —
  "Don't tell Robotnik, ya hear?!". Rest Site: rest (heal a percentage) or smith
  (upgrade a card). Treasure: a relic.
- Art is Sonic-style pixel art: ROM assets where possible, pre-drawn otherwise.
- New Mod API hooks are authorised when they have real-world use and are easy to use.

Kickoff answers:

| Question | Decision |
|---|---|
| Base ROM | Sonic 3 & Knuckles patch mod; the user's S3K ROM supplies zone, badnik, boss, character and audio assets. Pre-drawn art covers UI and gaps only. (A brief switch to Sonic 2 was reverted: Knuckles and the Egg Robo are not in the S2 ROM.) |
| Content scale | Full run at mid scale: three acts plus a final boss, ~45 cards per character, ~50 relics, ~15 potions, ~20 events; data-driven so it can grow. |
| Delivery | One pull request against `develop` when finished; no milestone merges. |
| Agent use | Lead plus a few subagents at a time; no multi-agent workflow fan-out. |

## Game rules

The rules follow StS closely so that its balance knowledge transfers; names and
presentation are Sonic-themed. Anything not stated here behaves as in StS.

### Combat

- The player starts each turn with 3 **Energy** (shown as a Chaos Emerald orb) and draws
  5 cards. Unplayed cards are discarded at end of turn unless they **Retain**.
- **Block** absorbs damage and expires at the start of the player's next turn.
- Enemies show an **intent** above their sprite: attack (with the damage and hit count
  after modifiers), defend, buff, debuff, strong debuff, escape, sleep or unknown.
- Turn order: player turn, then each enemy left to right performs its intent and picks
  the next one from its pattern.
- Damage pipeline (attack): base + Strength → ×0.75 if attacker Weak → ×1.5 if target
  Vulnerable → floor → Block first, then HP. Block pipeline: base + Dexterity → ×0.75 if
  Frail → floor.

### Stats and keywords

| Term | Meaning |
|---|---|
| Strength | +1 damage per hit of every Attack. Negative values reduce it. |
| Dexterity | +1 Block per Block gain from cards. |
| Focus | +1 repetition per stack for every **Combo** effect. Negative values reduce it (minimum one repetition). |
| Combo N | The marked effect repeats N times (+Focus). "Combo 2: Deal 3 damage" with 1 Focus deals 3 damage three times. Applies to damage, Block, draws and debuffs alike, so Focus is Sonic's general scaling stat. |
| Vulnerable | Takes 50% more attack damage. Duration stacks; decreases at end of round. |
| Weak | Deals 25% less attack damage. |
| Frail | Gains 25% less Block from cards. |
| Exhaust | Removed until end of combat. |
| Ethereal | Exhausted if still in hand at end of turn. |
| Retain | Not discarded at end of turn. |
| Innate | Starts each combat in the opening hand. |
| Unplayable | Cannot be played. |

Every character can gain any stat through relics, potions or colourless cards; a primary
stat is only the one that character's own card pool builds.

### Characters

| Character | HP | Starter deck | Starting relic |
|---|---|---|---|
| Sonic | 72 | 4 Spin Attack, 4 Side Step, Spin Dash, Homing Attack | Red Sneakers — the first Combo card each turn repeats 1 more time. |
| Tails | 70 | 5 Tail Swipe, 5 Tail Guard, Tail Flick, Tinker | Tinker Kit — at the start of each combat, add 2 Ring Bombs to your hand. |
| Knuckles | 80 | 5 Punch, 4 Guard, Hammer Punch | Master Emerald Shard — at the end of combat, heal 6 HP. |

Tails' token card is the **Ring Bomb** (0 Energy Attack, deal 4 damage, Exhaust), Tails'
counterpart to the Silent's Shiv; bombs are Tails' weapon in *Tails Adventure*.

### Run structure

- Three acts, each a zone map of 15 floors in the StS layout (seven columns, enemies on
  floor 1, treasure on floor 9, rest sites before the boss), followed by the final boss.
  The generator walks three paths from distinct starts that meet before the boss; StS's six
  overlapping paths read as clutter on the left-to-right zone map.
- The StS map rules apply: no elites or rest sites before floor 6, no two consecutive
  shops/rest sites/elites on a path, and siblings branching from the same node use
  different node types.
- Rings are the currency (StS gold). Potions are presented as **item monitors**
  broken during combat; the player holds three.
- Runs are seeded, can be saved and resumed at any node boundary, and record a run history.

## Engine integration

### What exists (reconnaissance at `fa129ccf4`)

- Non-level screens are per-mode providers polled by `GameLoop` (`TitleScreenProvider`,
  `DataSelectProvider`, ...). `DATA_SELECT` is the lightest model: provider supplied by the
  module, `update(InputHandler)`, screen-space draw, exit flag, no PLC phase.
- A patch can already hijack `getTitleScreenProvider()` (Infinite Sonic's title does), but
  that runs creator code without the mod fault boundary, reports the wrong presence state,
  can only exit to data select or a level, and has no owned pattern range, storage or
  decompression API.
- ROM sprites can be drawn outside a level: the S3K Continue screen draws Sonic, Tails,
  Knuckles and an Egg Robo from ROM art, DPLCs and palettes. `Sonic3kObjectArt.loadStandaloneSheet`
  flattens any `Sonic3kPlcArtRegistry` entry (KosM/Nemesis/uncompressed, DPLC) into a
  static sheet. None of these classes are `@ModApi`; the existing `RomArtRequest` intake
  is Sonic 2–only and serves level objects only.
- Palette lines are the main conflict: badniks use zone line 1, Sonic and Knuckles share
  line 0, boss palettes overwrite line 1.
- Mouse state is pinned `@ModApi` but only in raw window coordinates; the editor's
  transform hard-codes a 320-pixel width.
- There is no mod save namespace; Infinite Sonic writes through the non-API `SavePaths`.
- Mod classes may not have static initialisers or non-literal static fields
  (`STATIC_STATE_UNSUPPORTED`), which excludes enums, static collections and static
  counters. The mod uses literal String constants for kinds and instance registries.

### Chosen design: mod scenes

A new Mod API capability lets a mod own the whole screen as a **scene**:

- `GameMode.MOD_SCENE`, driven like `DATA_SELECT` (no PLC phase; hold-Escape to the master
  title and no pause come for free) with render-dispatch entries.
- Registration through `ModContext` so the engine owns ownership, the fault boundary
  (`ModFaultBoundary` around every scene callback) and game-start exclusivity.
- A `@ModApi` scene context exposing logical input, mouse in logical pixels, audio, an
  owner-namespaced storage directory, exit requests, and an RGBA **canvas**.
- Art reaches the canvas as RGBA images: from the mod's PNG assets, from pixels built in
  code, or decoded from ROM sprites (art + mappings + optional DPLC + palette) on the CPU.
  Decoding to RGBA sidesteps palette-line conflicts entirely: every sprite carries its own
  colours, and tinting, flashing and scaling become per-draw options.
- Zone backgrounds with stock parallax rendered from the ROM level data, without starting a
  level (`SceneRomArt.zoneBackdrop`, added late in the task; see the progress log).

### Content as data

The mod keeps its look editable without code: the pixel font, every icon and the relic
icons are text art (`art/font.txt`, `art/icons.txt`), and every card and relic picture is a
one-line recipe of layers (`art/cards.txt`, `art/relics.txt`: hero poses, ROM sprite
frames, item monitors, icons and drawn effects). Enemy looks are code
(`scene/EnemyVisuals`) because they are assembled from each object's ROM child offsets.


## Progress log

- 2026-10-05: worktree created from `origin/develop` at `7aed87c4f`, fast-forwarded to
  `fa129ccf4` after James merged Infinite Sonic, which introduced the top-level `examples/`
  convention this mod follows.
- `975cd17c5`: rules core (combat engine with an event log, StS damage/Block pipeline,
  intents, choices, map generator, rewards, shop, rest, events, save codec) and first
  content. The rules are plain Java with no engine imports so the bot and tests run without
  a ROM.
- `8d668c7d8`: the mod scene API (`GameMode.MOD_SCENE`, `ModContext.registerStartupScene`,
  `SceneCanvas`, `SceneRomArt`, scene storage, mouse in game pixels and wheel notches),
  pinned in the 0.7 candidate surface.
- `542ee3aee`: the scene, its screens and ROM art; `fb4d11b14`: acts 2-4 and Mecha Sonic;
  `3e3f2e736`/`494089dac`: Tails' and Knuckles' 42-card pools (written by two subagents in
  their own worktrees, then cherry-picked); `faa565f80`: twenty events and act 2-4 enemy
  art; `f817ec47c`: compendium and card recipes; `2135821cd`: relic art and death effects;
  `76abd6aa0`: engine-suite tests that build the example, run its tests and smoke-test the
  scene against S3K.

- Later the same day: every card got its own picture (`7e2d8fe85`, by a subagent); the
  shop, capsule and grid-scrolling fixes (`1175d5ce2`); settings (`f2dc43a45`); card motion
  (`d40fc56c0`); the final boss ending the run without rewards (`d7e207fab`); the act map
  turned left to right at the user's request (`c84b878a2`); `SceneRomArt.zoneBackdrop` and
  `levelOverview` (written by a subagent from a research write-up, cherry-picked); and the
  mod drawing over both (`f9312cbbc`).
- `77846eb7d`: `-Pguards` found that the scene work had made `game` depend on `mods`
  (`GameModule.startupScene()` returned `ModSceneFactory`), closing a package cycle. The
  startup scene now travels through the existing `getGameService(ModSceneFactory.class)`
  lookup and `startupScene()` left the unpublished candidate pin.
- 2026-10-05/06, after the first review on PR #211: the map wheel scrolls by a fixed step
  and stops at either end (`bc26dbcea`); gold card terms and every HUD and fight readout
  have tips (`5844b7cda`, `ab8d61cbd`); rooms are staged on the act's real level:
  `LevelFloorScanner` finds runs of solid floor with headroom in the level's collision, and
  `SceneRomArt.levelStages`/`levelForeground` hand them to the mod (`a40bbf626`).
- The slot machine event spins the bonus stage's own reels (`SlotReels`, after
  `S3kSlotOptionCycleSystem`) and shows the faces it pays out; every one of the 22 events
  then got a picture class that plays out the chosen option with the game's objects before
  the result appears (`d6b03fdec` for the framework, then three subagent lanes in their own
  worktrees, cherry-picked: `acefe24a8`, `2b8f2876a`, `2d95d9d72`).
- Maps: the user asked to halve the fights per act, then clarified that the real problem
  was the clutter of branches; the generator now walks three paths from distinct starts
  that meet before the boss instead of six (`af75de7b5`).
- The engine window now refits to the mod's 16:9 aspect instead of letterboxing it
  (`DisplayWindowFit`, `4c464a508`).
- Final polish round: the title screen flies through four zones' real levels with the
  Tornado chase; character select spin-dashes off; rooms dip between each other; rings,
  cards, monitors and relics fly to where they belong; HP readouts trail a ghost of lost
  HP; fights open with an entrance (bosses named); each act opens with the zone's title
  card from the ROM; the Starpost shows its rest and tune-up; game over follows
  `Obj_GameOver` and victory runs the hero in beside the summary (`7ec37dd9e` to
  `c4c746335`).
- Mod API clean-up from an encapsulation audit (`f1132bf10`, `08ebe6066`, `8bd871eed` to
  `895cd7601`, written by a subagent lane): scenes close at shutdown; scene textures are evicted when unused; the startup scene goes only through an
  engine-keyed factory behind the fault boundary; `SceneMouse`, `SceneDraw` and
  `SceneLevelStage` became engine-made final classes; named keys and buttons, menu repeat,
  `drawBackdrop`, raw `tiles`, and title cards were added for things the example had
  worked around; the display aspect is requested without a `GamePatch`; zone pictures are
  served by the game module; `DebuggableScene` replaces reflective debug entry.
- Tooling: `ExampleModHarness` and `ExampleModCapture` build any example mod from source
  and capture its scene to PNG, MP4 and WAV (`d9be9a254`, written for the example's highlight reel), and `examples/hello-scene` is a
  two-class starter for newcomers (`3fd553324`).

### Rejected approaches and their evidence

- **Enums and static tables in the mod.** The mod validator rejects class initialisers and
  non-literal static fields (`STATIC_STATE_UNSUPPORTED`). Card types, rarities, intents and
  the like are String-constant classes; tables that would be static arrays are instance
  fields or locals. The packaging step catches regressions (it failed once on the
  compendium's tab arrays).
- **Sonic 2 as the base game.** Requested briefly, then withdrawn: Knuckles and the Egg
  Robo are not in the Sonic 2 ROM.
- **The Death Egg Robot as the final boss.** It is drawn on the background plane
  (`DezFinalBossController`), not as sprites, so it cannot be composed from mapping frames
  like the other bosses; Mecha Sonic (one DPLC sprite with ROM Super palettes) took its place.
- **Requiring every bot run to win.** The sturdy run bot played full runs and asserted every
  run won; it failed for reasons that were bot weakness, not rules bugs (Fire Breath's
  divider scales with HP, stalemates against Block-heavy enemies, attrition). It now heals
  at each fight start, gets a Strength boost against stalemates, and asserts that at least
  three quarters of runs win and every act boss falls (44-45 of 45 at the time of writing).
  Two real rules bugs it did find: a manual 1-Up use set HP to 30% (AUTO potions are now
  blocked from manual use), and a permanently Intangible elite (Intangible now expires at
  its owner's turn start, as StS).
- **Moving the scene API into `com.openggf.game`** to break the `game -> mods` cycle. It
  would have renamed every public scene type for a problem that one service lookup removes.
- **Halving the fights per act.** The literal request after the first review; the user then
  said the problem was the clutter of branches, so the map lost paths (six to three), not
  fights.
- **Advancing animation timers in `draw`.** Worked on screen, but headless capture draws only
  sampled frames, so readouts animated at the sampling rate. All timers now advance in
  `update`.
- **Half-dead bosses counted as inactive.** Two-phase bosses (Beam Rocket, Mecha Sonic)
  ended the fight when they "died" before reviving. `Enemy.isPresent()` (still takes turns,
  blocks victory) is now separate from `isActive()` (targetable).

### Engine bugs found by the example

- `SceneRenderer` uploaded first-seen images mid-frame; the upload rebinds `GL_TEXTURE_2D`
  and unbinds it, so the pending batch drew with texture 0 and every fill in it came out
  black (seen as whole event panels and the HUD HP text vanishing). New images are now
  uploaded before drawing (`faa565f80`).
- The test harness never refreshed the mapped input snapshot each tick, so d-pad input never
  reached scenes in captures (`f817ec47c`).
- Found by the art research, not fixed here (ROM-faithfulness gaps in unrelated objects):
  CaterkillerJrBodyInstance spark frames/delays, AizMinibossFlameChild and
  AizMinibossImpactFlameChild using the wrong sheets, the Egg Robo fighter never bobbing,
  and `(vIntRunCount + 3) & 1` read as arithmetic instead of the byte at
  `V_int_run_count+3` in EggRoboShotInstance and two others. Recorded in
  `docs/status/s3k-known-bugs.md`.
