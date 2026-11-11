# MICA and TALON

An official character-mod example for Sonic 1, Sonic 2 and Sonic 3 & Knuckles.
MICA is an original jade pangolin with Sonic physics, spindash and a homing
strike. TALON is an original falcon with Tails physics, flight and a targeted
divebomb. MICA defaults to TALON as sidekick. Gilded and Solar provide powered
forms. The example also replaces character identity in titles, HUDs, special
stages, Competition, reels, continue screens and endings.

The stable IDs are `mica-and-talon:mica` and `mica-and-talon:talon`.
This is artwork and character registration over native host mechanics; it does
not certify complete routes or introduce standalone Blue Sphere hosting.

## Directory map

```text
mica-and-talon/
  src/main/java/micaandtalon/   registration, movement and presentation bindings
  src/main/resources/          mod descriptor
  art/{mica,talon}/            indexed runtime artwork, grouped by surface
    sheet/                    playable frames, palette and symbolic handoff
    titles/                   native mascot images, plane masks and clear mask
    special-stage/            current posed Blue Sphere and half-pipe drawings
    stage-poses/              native object poses and base special-stage banks
    hud-and-screens/           names, lives and presentation identity
    competition/              miniature body mappings
    competition-menu/         player and record portraits
    reels/                    normal and powered slot symbols
    continue/                 native screen pose banks
    portrait/                 data-select portraits
    endings/                  native ending and cockpit replacements
    powered/                  MICA's host-specific Gilded sheet inputs
    snowboard/                MICA's native snowboard poses
  production/{mica,talon}/     recipes, selected parts, prompts and provenance
  concept/                    approved original character references
  build.py                    declarative jar inventory and build entry point
```

See each [MICA](art/mica/README.md) and [TALON](art/talon/README.md) surface table
for files and regeneration. The `production/` tree retains original batch names
as provenance identifiers; these names do not select runtime behavior.

## Build and run

Requirements: Java 25, Maven, Python 3 with Pillow, and your verified host ROMs
configured as described in the repository guide. No ROM data is distributed.
From the repository root:

```bash
python3 examples/mica-and-talon/build.py
python3 examples/mica-and-talon/build.py --run
```

The jar is `target/mica-and-talon/mica-and-talon.jar`. Generated converter sheets,
custom-frame inventories, compiled classes and requested development preferences
stay under `target/`. `--save-root <directory>` selects another owned preference
root. Select MICA and TALON in the engine's character rows. The build uses the
queued Maven wrapper and leaves tracked source and untracked source unchanged.

To assemble only a playable sheet:

```bash
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica/sheet target/mica-and-talon/generated
```

## Class map

All classes are in `src/main/java/micaandtalon/`. The descriptor names `MicaMod`.

| Surface | Classes | Asset folder beneath `art/<character>/` |
| --- | --- | --- |
| Registration and team | `MicaMod`, `TalonMod` | `sheet/` → packed GGFP |
| Movement and abilities | `MicaCharacter`, `TalonCharacter` | `sheet/` |
| Base presentation and stage selectors | `MicaPresentation`, `TalonPresentation` | `hud-and-screens/`, `stage-poses/` |
| Title mascot | `TitlePresentation` | `titles/` |
| Final native special-stage orientation | `OrientedStageArt` | `special-stage/` |
| Continue, portrait and snowboard poses | `MicaScreenFrames`, `TalonScreenFrames` | `continue/`, `portrait/`, `snowboard/` |
| Ending actors and cockpit | `MicaEndings`, `MicaS3kEndings`, `TalonEndings` | `endings/` |
| Competition miniature | `CompetitionPresentation`, `MicaCompetitionFrames`, `TalonCompetitionFrames` | `competition/` |
| Competition identity | `CompetitionMenuPresentation` | `competition-menu/` |
| Slot symbols | `ReelPresentation` | `reels/` |
| Powered forms | `MicaGilded`, `TalonSolar` | `powered/`; palette records in `production/*/presentation/` |
| Load-scoped form options | `GildedSettings` | none |

## Art rules

Use the approved model and WAIT anatomy with real-palette native pose references
at 8× nearest-neighbour scale. Generate original character drawings, then reduce
against the locked indexed palette while preserving aspect, pose, material-shadow
contours, compact upper-left highlights and stepped clusters. Never repaint or
ship decoded native art. Raw generations and decoded references stay in external
scratch; retain selected indexed sources, prompts, hashes and measurements.

Native selectors, origins, facing, mapping order, priority, occlusion, palettes,
fades and animation clocks remain host-owned. Dedicated final-orientation drawings
preserve upper-left lighting through native flips. Carry contacts use the native
28-pixel centre separation. Current special-stage upright drawings derive from
canonical parts with measured native body motion; curl sources stay independent.
Respect the native per-frame colour budgets and distinguish positioned capture
proof from full-route coverage. Do not rerun superseded recipes over approved art.

## History

`2e7d9fa348` is the last pre-cleanup layout and contains all previous dated
README logs and the removed `art/mica/cycle-experiment/` (including `exp2/` and
`frames/`). Recover any of those with `git show 2e7d9fa348:<old-path>`.
The superseded generated sheets in `src/main/mod/` are rebuilt under `target/`.

The original body-motion correction is `7190f49ae4` (integration `9318420b42`).
Approved title, carry, native object and ending work is recorded in the
[design audit](../../docs/architecture/designs/2026-11-08-original-character-mod.md).
Production records retain selected sources and rejected-attempt evidence;
other batch/reference helpers remain because tooling still uses them.

Removed dated README logs (all last present at `2e7d9fa348`):

- `art/mica/README.md`
- `art/mica/competition-menu/README.md`
- `art/mica/competition/README.md`
- `art/mica/endings/README.md`
- `art/mica/endings/s3k/README.md`
- `art/mica/oriented-stage/README.md`
- `art/mica/powered/README.md`
- `art/mica/reels/README.md`
- `art/mica/round3/README.md`
- `art/mica/titles/README.md`
- `art/mica/wave3/README.md`
- `art/talon/README.md`
- `art/talon/competition-menu/README.md`
- `art/talon/competition/README.md`
- `art/talon/endings/README.md`
- `art/talon/oriented-stage/README.md`
- `art/talon/reels/README.md`
- `art/talon/titles/README.md`
