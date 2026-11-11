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

To learn to build a character like these, follow the
[nine-part tutorial](../../docs/modding/guides/original-character/README.md). Its part 2
checkpoint mod is in [`tutorial/part-2-first-playable`](tutorial/part-2-first-playable).

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
  tutorial/                   the tutorial's part 2 checkpoint mod
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
| Art-side table loading | `FrameTable` | `*-frames.txt`, `frames.txt`, `powered/palettes.txt` |
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

## Editing frame tables

Selector/file/hotspot inventories live beside their images, rather than in Java:
`stage-poses/frames.txt`, `special-stage/frames.txt`, `competition/frames.txt`,
`endings/**/frames.txt`, and host-specific `s1-frames.txt`, `s2-frames.txt` and
`s3k-frames.txt` in titles and screen surfaces. The shared `FrameTable` helper
reads them through `ModAssetRoot` and `PresentationImages`; no engine extension
or additional dependency is involved.

Each non-comment frame row has five whitespace-separated fields:

```text
# bank selector image hotspotX hotspotY
HAND 9 s2-9.png -18 0
```

Bank names are native surface/part names, or screen banks such as
`continue/player`. Images are relative to the table, selectors are decimal and
hotspots may be signed where the native surface permits them. Duplicate selectors
within one bank are rejected. `special-stage/frames.txt` names banks such as
`S2_HALF_PIPE/n` and `S2_HALF_PIPE/hv`; `OrientedStageArt` uses the existing API
orientation methods while the host retains native selectors and flip decisions.

Ending tables also declare `palette <bank> <image>`, naming the image whose
sixteen colours the ending bank owns. `powered/palettes.txt` stores one cycle
row per line: its bank (`fade`, `s2`, `s3k`) followed by sixteen hexadecimal
Mega Drive colour words. The existing `gilded_java.py` and `solar_java.py`
commands now regenerate/check those data files from the production records;
the historical command names remain available. Form rules and timing stay in Java.

To change an image binding, edit its row and rebuild. Keep native bank names,
selectors, origins, palettes and ownership unless the intended art change
specifically requires them. MICA's S2 HAND selector 9 intentionally uses the
owner-approved −18 origin. Title masks and clear-mask rules remain in
`TitlePresentation`; the table only binds drawings.

## Art rules

Use the approved model and WAIT anatomy with real-palette native pose references
at 8× nearest-neighbour scale. Generate original character drawings, then reduce
against the locked indexed palette while preserving aspect, pose, material-shadow
contours, compact upper-left highlights and stepped clusters. Never repaint or
ship decoded native art. Raw generations and decoded references stay in external
scratch; retain selected indexed sources, prompts, hashes and measurements.

TALON’s S2 title BODY0–4 turn three-quarter toward the camera, retaining the
closed-eye rise phase and open-beak settled smile. The face-only generation
record is `production/talon/titles/s2-gaze-generation.json`.

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

S2 cockpit ownership follows the cast: TALON pilots for MICA (paired or solo),
and MICA pilots for solo TALON. The optional solo companion ending banks retain
native pilot selectors and clocks in the ending and SCZ/WFZ. S3K endings use the
same companion policy; the small S3K title fly-by remains native. The S2 title
body pass now honors the native emblem mask, including transparent logo gaps;
settled poses and MICA's hand origin remain unchanged.

The S2 final front-view Tornado has its own pilot surface: TALON for MICA and
MICA for solo TALON. Authored binary masks remove the stock cockpit pilot while
the plane, its livery, wings and struts stay ROM-backed. Ending surfaces use
independent pattern residency so queued cockpit pixels survive later rider draws.

TALON uses his full five-letter name on S1/S2/S3K lives displays. S3K packs
its native glyphs into 32 pixels by removing only empty edge columns when the
ordinary word is too wide. No glyph ink, shadow, portrait pixels or palettes
change; MICA's name and the S1/S2 words keep their original composition. The
S2 special-stage HUD also retains full TALON. No new art is generated or stored. All three hosts preserve the native lower
counter row, including ×; S3K now replaces only the upper name tiles.
