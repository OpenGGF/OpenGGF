# An original character across three games

Build a character from a design, then take the same sheet into Sonic 1, Sonic 2
and Sonic 3 & Knuckles. This walkthrough uses MICA, an original jade pangolin,
from [MICA and TALON](../../../examples/mica-and-talon/README.md). It is a framework
demonstration on the unpublished Mod API 0.7 candidate, not a released mod.
TALON is planned for phase 2.

Start with Java 25, Maven, Python 3 with Pillow, and matching engine/SDK sources.
Run commands below from the repository root. You need your own supported ROMs to
play or run ROM acceptance; art reduction and assembly need none. Use the
[setup guide](../getting-started.md) for a new mod project and the
[character reference](../characters.md) for the complete API contract.

## 1. Turn a concept into a small design contract

Decide the silhouette, colours, route and one defining action before generating
poses. MICA's jade scales, cream face, coral boots, gloves and trailing tail must
read at native resolution. Her route is Sonic's, and her action is airborne
homing. Gilded turns the jade scales gold. These decisions constrain both art and
code; drawing a flying character does not grant native flight.

Use the approved references under
[`examples/mica-and-talon/concept/`](../../../examples/mica-and-talon/concept/).
For your own character, first approve a turnaround and a few key poses. Ask GPT
Image for image edits against that same reference rather than fresh unrelated
characters. A useful pose request specifies facing, silhouette, planted feet,
transparent or flat keyed background, separate atlas cells, and no text or scenery.
Keep the accepted source images and prompts together. MICA's actual prompt and
accepted/rejected-cell record is
[`art/mica/generated/prompts.json`](../../../examples/mica-and-talon/art/mica/generated/prompts.json).
Her weak first walk and muzzle-leading dive were rejected before reduction.

Inspect WAIT, WALK and ROLL at 1× before expanding the set. Generated detail often
vanishes at this size; plan a pixel cleanup pass for eyes, hands and foot contact.

## 2. Reduce against one palette and one hotspot convention

Read the [playable-art pipeline notes](../../../tools/modding/playable_art/README.md),
then examine `examples/mica-and-talon/art/mica/recipe.json` and `palette.yaml`.
The recipe selects atlas cells, dimensions, anchors, native pixel rows and explicit
patches. The palette has transparent index 0 and fifteen fixed opaque colours on
the Mega Drive colour ladder. Never quantize each pose independently.

```sh
python3 tools/modding/playable_art/pipeline.py examples/mica-and-talon/art/mica/recipe.json --review /tmp/mica-art-review
python3 tools/modding/playable_art/verify.py examples/mica-and-talon/art/mica
```

The reducer removes background/noise, scales, thresholds alpha, assigns fixed
indices without dithering and applies the ink outline. Its verifier checks palette
identity, transparency, geometry and byte-identical regeneration. Review the 1×
contact sheet and cycle GIFs as well: these checks cannot judge a convincing stride.

Hotspots are engine centres, with `top_left = centre - hotspot`. MICA's standing
support ends at centre+18; centre+19 is the exclusive bottom edge. Balls are
centred. Keep slope phases aligned with their flat counterparts. The current
redrawn slope contours and motion sources supersede the early rigid rotations;
`art/mica/motion.py` records the RUN, SPRING and WAIT refinements. Do not rerun
historical generation rounds indiscriminately: some rebuild older manifests.

## 3. Assemble one canonical sheet

`frames.yaml` is the handoff: frame IDs, order, files, hotspots and slope sets.
Assembly pads crops to tiles and splits pieces at 32 pixels. It produces the
indexed atlas, converter manifest and custom-frame index map:

```sh
python3 tools/modding/playable_art/assemble.py examples/mica-and-talon/art/mica examples/mica-and-talon/src/main/mod
python3 -m unittest discover -s tools/modding -p test_playable_art_assemble.py
```

Inspect `src/main/mod/mica.png`, `mica-sheet.yaml` and `mica-custom-frames.json`
under the example. MICA currently has 98 frames and 18 canonical animations.
`WAIT` is mandatory; `idle` is not an alias. WALK/RUN have four slope cycles in
selector order 0/2/4/6. WALK, RUN, ROLL, ROLL2 and PUSH use speed timing when
present; other animations have positive durations and an explicit end action.
MICA's four `HOMING_DIVE` frames loop at two ticks each. Her WAIT breathing/blink
precedes the fidget at tick 186. Use the reference's
[animation table](../characters.md#authoring-the-animation-table) when adapting it.

Bake with matching engine/SDK jars from the [CLI setup](../ggfmod.md).
The repository wrapper takes both jar paths before the command:

```sh
sh docs/modding/ggfmod /absolute/path/engine.jar /absolute/path/sdk.jar convert art --playable --image examples/mica-and-talon/src/main/mod/mica.png --sheet examples/mica-and-talon/src/main/mod/mica-sheet.yaml --out target/mica.ggfp
```

The `.ggfp` v2 file includes mappings, palette, animations and DPLC runs. The
current full-frame runs retain distinct destination offsets: the largest frame's
36 tiles are not the resident source-bank budget. Read the converter's bank
report and resolve capacity errors before installation.

## 4. Register identity, physics and ability

Follow the actual sources:

- [MicaMod.java](../../../examples/mica-and-talon/src/main/java/mica/MicaMod.java)
  reads bounded GGFP bytes from `context.modAssets()`, materializes them and calls
  `registerCharacter("mica", ...)` within the registration transaction.
- [MicaCharacter.java](../../../examples/mica-and-talon/src/main/java/mica/MicaCharacter.java)
  constructs `CharacterKey.mod(owner, "mica")`, yielding `mica-and-talon:mica`.
  Its `SONIC_AND_TAILS` archetype resolves to the solo Sonic route without a follower.
- [The manifest](../../../examples/mica-and-talon/src/main/resources/META-INF/openggf-mod.yaml)
  uses `baseGame: any`, so the same definition appears in all three hosts.

Extend `AbstractPlayableSprite`, construct a per-instance `CharacterPhysicsSpec`,
and call `drawStandardArt()` from `draw()`. MICA uses
`PhysicsProfile.SONIC_2_SONIC`, the shared normal single-player Sonic profile.
Change a grouped physics builder deliberately for your own character; preserve
host rules and modifiers instead of copying a whole provider.

Her `onAbilityActivate` first offers the press to
`getSuperStateController().activateFromAirAbility()` when a controller exists.
Then `HomingAttack.start(this, mode)` uses `HOMING` normally and `INSTAGIB` when
`isSuperSonic()` is true. No target gives a forward dash. Use `startIfTarget` if
you want a native air move to receive an untargeted press. A flight character can
request the reticle and call `divebomb`; the facade does not grant flight itself.
The world owns targets, strike state and rewind. Capture any additional mutable
subclass latches with the subclass rewind hooks; do not cache targets or engine
objects in static fields.

## 5. Finish the surfaces outside gameplay

Load indexed PNGs through `PresentationImages` and bounded `ModAssetRoot` paths.
[MicaPresentation.java](../../../examples/mica-and-talon/src/main/java/mica/MicaPresentation.java)
shows per-host HUD and monitor faces, semantic sign poses with separate native-line
backings, and native-frame stage sets. Native glyphs take precedence: only S1
HUD M/A need supplementary images for MICA. Test your own name's coverage and fit.

Continue in
[MicaScreenFrames.java](../../../examples/mica-and-talon/src/main/java/mica/MicaScreenFrames.java)
for continue banks, portrait frame 5 and snowboard frames 0–12. Use the ending
loaders beside it for S1, S2 and S3K sets. Preserve native frame IDs and hotspots:
the host still owns animation, projection, timing, vehicles and companions.
Private palettes avoid recolouring native text, stage scenery or Tails.

For water, MICA omits `underwaterPaletteSupplier`, using the current host zone's
normal/water colour shift. Supply an authored sixteen-colour line if that derived
look is unsuitable. Test real zone changes and palette cycles, not just dry art.

The example's `build.py` copies presentation, stage, screen and ending PNGs beside
`art/mica.ggfp` in the packaged classes. GGFP presentation sections remain pending;
adding a PNG to the source tree alone does not load or package it. Keep the loader
paths and build copy list aligned. Never package ROM pixels as fallback assets.

## 6. Author a powered form

Read [MicaGilded.java](../../../examples/mica-and-talon/src/main/java/mica/MicaGilded.java).
Set `supportsSuperForm` and supply a `SuperFormProfile` keyed by game and tier.
Each form needs sixteen-word palette rows, fade and dry cycles, ticks, physics
and a canonical transformation animation; water cycles, animation overrides,
after-images and stars are optional. MICA changes only jade indices 2–6, uses
WAIT for transformation, and enables neither after-images nor stars. S1/S2 use
the nine-row cycle at 8 ticks and six fade rows at 4; S3K uses three cycle rows at
7 ticks and fades at 2. Hyper deliberately retains the gold look and homing.

S1's six-emerald/50-ring form is mod-defined, requires no donor ROM and defaults to
invincibility music. S2 defaults to seven emeralds; S3K keeps the Chaos/Super
Emerald gates. [GildedSettings.java](../../../examples/mica-and-talon/src/main/java/mica/GildedSettings.java)
uses existing LOAD-scoped widgets and an owner-matched `CharacterSuperFormPolicy`:
enable the override before changing game switches, S1 emeralds, rings or S1 music.

## 7. Package, play and collect evidence

```sh
python3 examples/mica-and-talon/build.py
python3 examples/mica-and-talon/build.py --run --save-root target/mica-tutorial-settings
```

The script builds against this checkout, assembles/bakes the sheet, and packages
`target/mica-and-talon/mica-and-talon.jar`. `--run` loads development classes.
Choose each host and MICA in the launch row. For normal jar installation, use the
Mods manager and enable trusted code. Keep engine and candidate SDK identity matched.

Run focused acceptance through the queue, with verified ROMs and surfaceless GL:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestMicaCharacterIntegration' test
```

Read the wrapper's ROM found/missing line and report skips. The integration class
packages these exact sources, checks launch/save/fallback, physics, homing and
rewind, and contains surface capture methods. Stage captures go to
`target/mica-stage-captures`, screen captures to `target/mica-screen-captures`,
and S3K ending captures to `target/mica-ending-captures`. Override with
`-Dmica.stage.captureRoot=...`, `-Dmica.captureDir=...` or
`-Dmica.ending.captureRoot=...`. See the
[test source](../../../src/test/java/com/openggf/mods/integration/TestMicaCharacterIntegration.java)
for method selectors and the exact entry used by each capture.

For title-first gameplay footage use the [gameplay-capture skill](../../../.agents/skills/gameplay-capture/SKILL.md)
with a BK2 input log and `--interactive-pacing`; retain frame/state provenance.
Compare at 1×, check grounded/slope contact, ability chaining, dry/water forms,
results and stock fallbacks. Programmatic stage/ending entry proves those
surfaces, not the physical entry trigger or a complete route. Full title-to-ending
route captures remain open, as do S2 2P presentation, title/branding art, reel prize
symbols, custom Tornado pilots and TALON. Keep those limits visible when sharing
what your tests actually establish.
