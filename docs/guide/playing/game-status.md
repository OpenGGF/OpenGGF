# Game Status

Last updated: 2026-06-12 (v0.6.prerelease development)

This page describes the current state of each supported game. It is intended to set
expectations honestly -- what works well, what is incomplete, and what you might encounter.

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

- Some objects and badniks may be missing or have minor behavior differences.
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

**Status: Most complete. Playable from start to finish with minor gaps.**

### What works

- All zones playable: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ (3 acts), SCZ, WFZ, DEZ.
- 9 boss fights: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, WFZ, and both DEZ bosses (Mecha Sonic
  and Death Egg Robot) plus the Robotnik escape sequence.
- 99.2% of objects implemented (119 of 120 unique object types).
- Special stages functional with emerald collection.
- Tails CPU AI follower with flight and input replay.
- Super Sonic with per-game physics.
- Title screen, level select, title cards, HUD.
- Complete credits and ending cutscene system.
- Water system for ARZ and CPZ.
- HTZ earthquake and lava systems.
- Per-zone level events across all zones.
- Demo playback.
- Full SMPS audio.

### Known gaps

- 1 unimplemented object (see OBJECT_CHECKLIST.md for details).
- MTZ boss is implemented but may have minor accuracy issues.
- Some visual effects (screen distortion, specific palette transitions) may differ
  slightly from the original.
- Oil Ocean Zone oil surface behavior is partially implemented.

### Notable quirks

- S2 is the engine's reference game -- it has the most test coverage and the most
  refined implementations.
- Cross-game feature donation uses S2 as the default donor for sprites and spindash.

---

## Sonic 3 & Knuckles (S3K)

**Status: Near-complete vertical-slice coverage, and the current development focus.**

S3K is no longer just early-game bring-up. AIZ, HCZ, CNZ, MGZ, ICZ, MHZ, and parts
of LBZ now have substantial route coverage, focused tests, and complete-run trace
frontiers. It is still not a fully polished full-game path, but most remaining work
is parity closure, route stabilization, and late-zone content rather than proving
that the module can work.

### What works

- Angel Island Zone intro cutscene, Act 1 gameplay, miniboss defeat flow, signpost, results,
  fire transition, Flying Battery bombing sequence, AIZ2 end boss, post-boss capsule flow,
  and AIZ-to-HCZ transition.
- Hydrocity route coverage including water rush, conveyor, fan, block, door, water skim,
  miniboss, HCZ1-to-HCZ2 transition, HCZ2 moving-wall chase, end-boss/capsule work, and
  complete-run trace diagnostics.
- Carnival Night, Marble Garden, Ice Cap, Mushroom Hill, and Launch Base have substantial
  object/event/scroll/palette work, with route-critical pieces covered by focused tests and
  complete-run trace frontiers.
- Title screen, level select screen, and data select screen with 8 save slots and team selection.
- Knuckles as a playable character, including glide/climb support.
- Blue Ball special stages and active bonus-stage work across Gumball, Glowing Sphere/Pachinko,
  and Slots.
- Shield system, water system, palette cycling, runtime-owned zone state, and broad badnik/object
  coverage.
- Water state now restores correctly after returning from side stages.
- SMPS audio with S3K-specific driver configuration (Z80 bank-switching, DPCM).

### Known gaps

- S3K is not yet a polished start-to-finish full-game path.
- FBZ and later zones remain the largest content/frontier gap compared with the opened AIZ-LBZ
  route slices.
- Some route slices still have complete-run trace divergences, especially around sidekick CPU
  handoff, object lifetime/order, ring/hurt interactions, and boss/event state.
- Some S3K objects, badniks, and bosses are still missing or implemented only to the extent needed
  by current route slices.
- Bonus stages are still in active parity work rather than final polish.
- S3K's more complex PLC/art loading system still has partial parity.
- Data select visual parity is still in progress (native selector art, emerald display).

### Notable quirks

- S3K uses KosinskiM (Kosinski Moduled) compression, combined 1P+2P mapping tables,
  and a more complex Z80 sound driver than S1/S2.
- S3K work is prioritized as playable vertical slices rather than isolated checklist completion.
  A useful slice has traversal objects, event/camera behavior, scroll/parallax, animated tiles,
  palette/PLC state, bosses or transitions, trace coverage for known blockers, and visual
  validation where practical. AIZ through HCZ remains the primary release slice, but CNZ, MGZ,
  ICZ, MHZ, and LBZ now have enough coverage that new work should follow current trace/frontier
  evidence rather than first-pass bring-up order.

---

## Experimental Tooling

### Level Editor Overlay

An experimental editor overlay is now available behind `debug.flags.editor` in `config.yaml`.
When enabled, use `Shift+Tab` during gameplay to park the current playtest and enter the editor
overlay, then use the same shortcut to resume. The current snapshot supports:

- World cursor and grid navigation.
- Focused block and chunk previews.
- Tile placement, undo/redo, persistence, and early derive/edit flows for live level data.
- Resume and restart handling around editor playtests.

This is still a development tool rather than a polished end-user level editor.

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

Enable with `crossGame.enabled` and `crossGame.source` in `config.yaml`.

When S3K donates the Data Select frontend, the save screen stays visually S3K-native,
but save semantics remain host-owned. Slot routing, progression, clear-restart rules,
and emerald identity come from the host game. Donated host emerald colors are adapted
into the S3K save-card palette contract instead of assuming raw palette-slot parity.
