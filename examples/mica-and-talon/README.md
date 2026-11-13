# MICA and TALON

MICA and TALON ships with OpenGGF as a bundled mod, turned off by default.
Enable it in the mod manager to use the characters; your choice is saved.

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
    powered/                  host-specific Gilded/Solar sheets, cycles and effects
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

## Powered art and the feedback checkpoint

Gilded and Solar use host-specific powered GGFP banks. TALON retains Tails’
native movement clocks; his integrated wings read the active bank’s already
relocated canonical script. S1 creator forms can play the same five-pose wind-up
without a donor ROM or a native Super animation slot. See
[powered script addresses](../../docs/modding/characters.md) for the candidate API.

MICA’s run now keeps the native two-phase lean and contact clock. TALON’s free
flight uses the approved carry profile and wing design. His flight, carry and other
oversized poses are redrawn to the head scale of his idle (`scale_audit.py`,
2026-11-12); MICA’s hand-to-feet registration is unchanged.
TALON’s idle is his own (he preens, ruffles up and catches a loose feather), his victory
is a salute off his goggles, and both characters’ signposts carry one portrait in all three
games (`idle_sign_victory.py`, 2026-11-12). MICA keeps her idle.
The owner-picked concepts (2026-11-11) are rolled out: Open Cone Gilded MICA redraws
every powered pose, and Corona Crest Solar TALON every common pose, for S2 and S3K.
Normal TALON stands 37 px (owner rung); Solar's crest rises above that body. Gilded plates breathe from a deep amber underside to a pale-gold edge; Hyper
reuses that bank and flashes Prism Gilt (gold, white-hot, a pale pastel jade, rose-gold). Solar
keeps a dark bronze slot for TALON's bars and moustache, with fixed amber/red
flame tips. Powered silhouettes use the cycling contour slot, retaining only
interior eye, glove and shoe separators. Normal-bank aliases remain explicit.

Selected indexed pixels, original generation attempts, anatomical scales and
material remapping are recorded in `production/*/feedback-generation.json` and,
for the powered concepts, `production/*/powered-concepts-generation.json`.
Check or rebuild the selected images without native pixels or raw generations:

```bash
python3 examples/mica-and-talon/production/talon/feedback_art.py --check
python3 examples/mica-and-talon/production/talon/tonal_reshade.py --check
python3 examples/mica-and-talon/production/talon/walk_cleanup.py --check
python3 examples/mica-and-talon/production/talon/pose_cleanup.py --check
python3 examples/mica-and-talon/production/talon/scale_audit.py --check
python3 examples/mica-and-talon/production/talon/lineart_pass.py --check
python3 examples/mica-and-talon/production/idle_sign_victory.py --check
python3 tools/modding/playable_art/style_audit.py examples/mica-and-talon/production/style-audit.json --check
python3 examples/mica-and-talon/production/slope_sets.py --check
python3 examples/mica-and-talon/production/mica/carried_reshade.py --check
python3 examples/mica-and-talon/production/mica/sheet_despeckle.py --check
python3 examples/mica-and-talon/production/mica/titles/s1_despeckle.py --check
python3 examples/mica-and-talon/production/powered_palettes.py --check
python3 tools/modding/playable_art/gilded_java.py --check
python3 tools/modding/playable_art/solar_java.py --check
```

The feedback review is one self-contained HTML file with embedded PNGs and
640px, CRF 30, 60 fps MP4s; lossless masters and per-frame CSVs stay in the external
capture directory. S3K carry has live-controller and manual-acquisition captures.
S2 has no native carry state: its carry comparison is a declared pose-only
renderer check, alongside native Tails flight. Neither certifies a complete route.

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
