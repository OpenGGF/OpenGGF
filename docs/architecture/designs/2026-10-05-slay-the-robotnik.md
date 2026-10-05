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

| Character | HP | Starter deck | Starting relic (draft) |
|---|---|---|---|
| Sonic | 72 | 4 Spin Attack, 4 Side Step, Spin Dash, Homing Attack | Red Sneakers — the first Combo card each turn repeats once more. |
| Tails | 70 | 4 Tail Swipe, 5 Tail Guard, Twin Tail Toss, Workbench | Toolbox — at the start of each combat, add a Ring Bomb to your hand. |
| Knuckles | 80 | 5 Punch, 4 Guard, Hammer Punch | Spiked Gloves — after each combat, heal 6 HP. |

Tails' token card is the **Ring Bomb** (0 Energy Attack, deal 4 damage, Exhaust), Tails'
counterpart to the Silent's Shiv; bombs are Tails' weapon in *Tails Adventure*.

### Run structure

- Three acts, each a zone map of 15 floors in the StS layout (seven columns, six
  generated paths, enemies on floor 1, treasure on floor 9, rest sites before the boss),
  followed by the final boss.
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
  level.


## Progress log

- 2026-10-05: worktree created from `origin/develop` at `7aed87c4f`, fast-forwarded to
  `fa129ccf4` after James merged Infinite Sonic, which introduced the top-level `examples/`
  convention this mod follows.
