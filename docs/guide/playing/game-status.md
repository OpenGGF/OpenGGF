# Game Status

Reviewed against the **0.7.prerelease development branch on 2026-10-23**.
Published 0.6 packages have a narrower feature set; this is not their release
certification. The [release notes](../../../CHANGELOG.md) describe each release.

Implementation, playable routes and verified ROM parity are separate claims.
The [level coverage backlog](../../status/level-test-coverage.md) records tested
act/character combinations and inherited gaps; the
[trace frontier log](../../status/trace-frontier-log.md) records replay evidence.
This documentation review did not rerun those gameplay checks.

---

## Sonic the Hedgehog (S1)

**Status: Broadly playable from start to finish.**

### What works

- All 7 zones (Green Hill through Final Zone) are loadable and playable.
- 6 boss fights implemented (GHZ, MZ, SYZ, LZ, SLZ, FZ).
- Special stages functional with emerald collection.
- Title screen, level select, title cards, and HUD.
- Labyrinth Zone water system with drowning mechanics, air bubbles, and underwater
  palettes.
- Per-zone level events (camera boundaries, zone-specific triggers).
- Ending sequence and credits.
- Demo playback.
- SMPS audio with S1-specific driver configuration.

### Known gaps

- The [S1 object checklist](../../../S1_OBJECT_CHECKLIST.md) lists all 80 placed
  types as implemented; behavior and complete-route parity can still differ.
- Edge-case physics behaviors (specific slope interactions, push block scenarios) may
  differ from the original.
- SBZ2-to-FZ transition sequence may have minor visual differences.
- Special stage parity is functional but not pixel-perfect.

### Notable quirks

- S1 uses 256x256 metatiles (vs. S2/S3K's 128x128), which affects level layout
  granularity.
- Loop plane switching uses a different mechanism than S2 (coordinate-based triggers
  rather than object-based switchers).

---

## Sonic the Hedgehog 2 (S2)

**Status: Broadly playable from start to finish, with remaining parity gaps.**

### What works

- All zones playable: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ (3 acts), SCZ, WFZ, DEZ.
- Boss implementations for EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ, WFZ, and
  both DEZ bosses (Mecha Sonic and Death Egg Robot), plus the Robotnik escape sequence.
- All 122 unique placed object types found in the ROM are implemented (see the [object checklist](../../../OBJECT_CHECKLIST.md)); registration alone does not establish behavioral parity.
- Special stages functional with emerald collection.
- Tails CPU AI follower with flight and input replay.
- Super Sonic with per-game physics.
- Title screen, level select, title cards, HUD.
- Complete credits and ending cutscene system.
- Water system for ARZ and CPZ.
- HTZ earthquake and lava systems.
- Per-zone level events across all zones.
- Mecha Sonic's DEZ attack loop follows the shipped ROM's outer `ObjectMove`
  and child-alignment ordering; the existing Mecha Sonic and Death Egg Robot
  implementations pass the dedicated DEZ ending replay.
- DEZ's opening exterior window samples the ROM-seeded Plane B camera origin,
  so its star field is visible and scrolls from level start.
- Special Stage checkpoint wings remain fixed while the independent hand peer
  bobs for both successful and failed ring checks.
- Demo playback.
- SMPS music and sound effects.

### Known gaps

- MTZ boss is implemented but may have minor accuracy issues.
- Some visual effects (screen distortion, specific palette transitions) may differ
  slightly from the original.
- Oil Ocean surface collision and oil slides are implemented; complete act,
  character/team, donor and viewport coverage remains incomplete.
- Native Sonic 2 competition mode and item-placement debug mode are unavailable.
  Player 2 bindings control the configured sidekick/manual-input path.

### Notable quirks

- S2 is the original reference game for the engine. Its long implementation
  history does not imply that every route or configuration has current parity evidence.
- Cross-game feature donation uses S2 as the default donor for sprites and spindash.

---

## Sonic 3 & Knuckles (S3K)

**Status: Broad playable campaign implementation, with remaining route and parity gaps.**

Development includes the late game and native endings. Current evidence covers
bounded routes and configurations; an uninterrupted, fully verified campaign for
every character, team, donor and viewport remains unqualified.

### What works

- Angel Island and Hydrocity have intro, boss, results, act-transition and
  outgoing-zone implementations, with route and rewind coverage.
- Marble Garden, Carnival Night, Ice Cap, Launch Base and Mushroom Hill have
  substantial traversal, event, boss, scroll, palette and transition implementations.
- Flying Battery, Sandopolis, Lava Reef/Hidden Palace, Sky Sanctuary, Death Egg
  and Doomsday have gameplay and progression implementations. Their individual
  [act matrices](../../status/level-test-coverage.md) record route evidence and limits.
- Sonic, Tails and Knuckles have playable character paths; Knuckles has glide,
  climb and character-specific routes.
- Title, level select and eight-slot Data Select screens, with team selection.
- Blue Sphere special stages and all three bonus-stage families: Gumball,
  Glowing Spheres and Slot Machine.
- Shields, underwater state, palette cycling, dynamic art loading and rewind.
- Native ending, credits and post-credits flows, including Sonic/Tails and
  Knuckles branches. The [ending matrix](../../architecture/validation/levels/s3k-ending.md)
  separates lifecycle/route evidence from remaining presentation coverage.
- S3K SMPS audio, including the banked Z80 driver and DPCM.

### Remaining limits

- Full campaign parity is not certified. Route, checkpoint/respawn, rewind,
  character/team, donor and widescreen coverage is uneven.
- Canonical trace frontiers remain open, including timing, sidekick behavior,
  object lifetime/order and boss/event interactions. A focused passing test or
  an implemented zone does not close its complete-run trace.
- Native visual and audio parity remains incomplete, including broader ending
  scenes and special/bonus-stage products.
- Knuckles' Launch Base Big Arm route has a native fight, grab/throw, defeat,
  capsule/results, falling-floor escape and Mushroom Hill handoff. Its documented
  canonical replay boundary remains distinct from those implementation and
  focused rewind results.

See [S3K known bugs](../../status/s3k-known-bugs.md) for defects and
[S3K discrepancies](../../S3K_KNOWN_DISCREPANCIES.md) for intentional departures.
These records include dated evidence; consult the latest entry for a particular
route instead of treating an old pass as a current whole-game guarantee.

### Cartridge scope

Sonic 3 and Sonic & Knuckles images can supply the combined S3K game through ROM
discovery. That does not promise each standalone cartridge's complete original
campaign behavior. See [ROM discovery](../../../CONFIGURATION.md#rom-files).

---

## Experimental Tooling

### Level authoring projects

The experimental editor is available from **Advanced → Editor projects** at the
master title; `debug.flags.editor` enables gameplay entry with `Shift+Tab`.
It supports named blank levels and supported layout clones for S1, S2 and S3K,
canonical imports, foreground/background painting, local block/chunk composition,
pattern descriptor flags, collision assignments, object/ring editing, and
project properties/resizing.

Complete `.ogfproject` saves reopen independently of stock gameplay saves.
Playtests use fresh runtime copies and preserve the document, selection and
history. Export checks the destination game: S1/S2 use v1 and S3K uses v2 with
explicit object family and palette ownership. Missing keys and unsupported native
features produce visible errors without discarding project data.

This is a bounded level authoring tool, not a pixel-art editor or a native zone
script/campaign studio. See the [level editor guide](../creating/level-editor.md)
for the workflow and limits.

---

## Cross-Game Features

The engine supports **cross-game feature donation**: a donor game provides player
sprites, spindash mechanics, and sound effects while you play a different base game.

| Feature | Status |
|---------|--------|
| S2 sprites in S1 | Working |
| S2 spindash in S1 | Working |
| Super Sonic cross-delegation | Working |
| S3K Data Select donated to S1/S2 | Working |
| S3K sprites in S1/S2 | Experimental |

Choose **Borrow from** in the game’s **Game Setup**. For direct launches that
bypass the hub, use `crossGame.enabled` and `crossGame.source` in `config.yaml`.
Availability does not certify every donor/character/route combination; consult the
[coverage backlog](../../status/level-test-coverage.md) for bounded evidence.

When S3K donates the Data Select frontend, the save screen stays visually S3K-native,
but save semantics remain host-owned. Slot routing, progression, clear-restart rules,
and emerald identity come from the host game. Donated host emerald colors are adapted
into the S3K save-card palette contract instead of assuming raw palette-slot parity.
