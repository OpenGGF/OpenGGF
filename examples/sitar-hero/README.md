# Sitar Hero

A source-first arcade rhythm mod for the unpublished OpenGGF Mod API 0.7 candidate.
Choose a cosmetic Sonic performer, instrument and song, then play one finite
performance and retry from results. The jar contains code, chart choices and new
pixel instrument props. Music, stages and characters come from your ROMs at runtime.

## Build and play

Use Java 21 and Maven from this checkout:

```bash
python3 examples/sitar-hero/build.py
python3 examples/sitar-hero/build.py --run --s1 /absolute/path/s1.gen --s2 /absolute/path/s2.gen --s3k /absolute/path/s3k.gen
```

Supply any nonempty subset of those three options. The script uses an isolated
configuration under `target/examples/sitar-hero/run/`; it leaves your normal engine
settings and ROM files alone. Output is `target/examples/sitar-hero/sitar-hero.jar`.
`--skip-engine` reuses a compiled engine. With ROM paths already configured for the
engine, `python3 examples/build_example.py sitar-hero --run` also works. Compiled
mods require the JVM engine and its normal code-trust flow.

| ROM | Song | Extra performers |
|---|---|---|
| Sonic 1 World REV01 | Green Hill — 52.8 seconds | Sonic, Robotnik |
| Sonic 2 World REV01 | Chemical Plant — 61.95 seconds | Tails, Silver Sonic |
| Sonic 3 & Knuckles locked-on | Angel Island Act 1 — 65.53 seconds | Tails, Knuckles, Mecha Sonic, SSZ Egg Robo |

Sonic and Robotnik are available with every supported ROM. All supplied content
shares one library: Knuckles can perform Green Hill, for example. The durations
include the intro and first complete music cycle, followed by a short silent
judgment window. There is one curated difficulty.

The arcade screens take their navy panels, cyan focus frames and checkerboard
accents from the master title, with the existing mods’ gold pixel wordmarks and
ROM scenery. Performers, musical roles and songs share a three-step selection
layout; the performance HUD leaves the note highway clear.

## Controls

Menus use arrows/Enter/Escape or ordinary gamepad D-pad/A/B/Start. Press Tab in a
selection screen to open settings. Performance has separate melodic and bongo
profiles, each with independently remappable keyboard and gamepad alternatives.

| Action | Keyboard | Ordinary gamepad |
|---|---|---|
| Frets / pads 1–4 | A, S, D, F | Left trigger, LB, RB, right trigger |
| Fret 5 / kick | G / Space | A |
| Strum up / down | Up / Down | D-pad up / down |
| Whammy | Space | Right stick up |
| Star Power | Left Shift | Back / View |
| Pause | Escape | Start / Menu |

Sitar (FM lead), Harp (FM accompaniment) and Synth (PSG) use five frets and strum.
Hold sustain tails; white-centred gems are hammer-ons/pull-offs after a successful
chain. Lower frets can stay held beneath a single higher fret; chords require their
exact frets. Bongos has four pads plus a kick pedal: each new press strikes its
note directly, with no strum or sustain. Green Hill and Chemical Plant genuinely
use only kick and snare; Angel Island uses the full five-input kit. Chemical Plant's
Synth part is its original PSG noise hi-hat, rather than an invented pitched part.

In settings, Enter/A captures a binding, Tab switches keyboard/pad, Delete clears
it, and the profile row switches melodic/bongos. Adjust input and display offsets
independently, or tap Space/pad A to the original percussion at least eight times
in calibration and press Enter to save the median input offset. Positive input
offset compensates late observed taps; display offset shifts note travel. Lefty
mode reverses the four/five coloured lanes while keeping the kick bar.

Notes score 50 per gem, sustains add 25 ticks per beat, streaks build a multiplier
up to 4×, and Star Power doubles it. Complete highlighted phrases to gain power;
whammy their sustain tails for additional charge. Activate at half a meter.
Misses and extra strikes lower the rock meter and mute the selected musical part;
the next hit restores it while the backing keeps playing. A depleted meter fails
the performance. Completed player records are saved per song/role; tool autoplay
is labelled DEMO and never overwrites them. Pause/retry starts with fresh input
edges, and interrupted audio/input pauses with a notice.
If speaker output fails, the performance stays frozen. Restore output and start
a fresh attempt; the failed attempt cannot resume on the silent fallback clock.

## Learn from this example

- [SitarScene](src/main/java/sitarhero/SitarScene.java) owns the complete arcade flow.
- [RhythmSession](src/main/java/sitarhero/model/RhythmSession.java) judges timestamped
  inputs in sample coordinates, independently of drawing, devices and performer.
- [ChartCurator](src/main/java/sitarhero/chart/ChartCurator.java) selects real ROM
  attacks using authored section/role profiles, then curates density, pitch-contour
  lanes, actual simultaneous chords, tails, HOPOs and power phrases.
- [MappedControls](src/main/java/sitarhero/controls/MappedControls.java) consumes raw
  press/release events, preserving short taps between scene updates and independent
  keyboard/pad alternatives.
- [PerformerArt](src/main/java/sitarhero/PerformerArt.java) loads native sprite banks
  and composes cosmetic performers with new instrument props.

The host prepares bounded PCM with the ROM's actual SMPS driver and chip voices.
Playback and input share the consumed audio coordinate, with optional OpenAL Soft
backend latency correction and saved calibration. Selected-part muting uses a full
mix and independently synthesized masked mix, retaining shared-chip interaction;
Green Hill changes part ownership at authored section boundaries. Closing the
scene releases its playback and additionally opened ROMs.

The [design and verification record](../../docs/architecture/designs/2026-10-06-sitar-hero-arcade.md)
distinguishes observed checks from remaining fidelity limits. GH III is the rules
reference; the ±100 ms judgment window, rock weights and residual vibrato whammy
are explicit PoC choices, without certification of original executable/DSP parity.
Queued audio delays miss/whammy feedback by the device buffer; calibration aligns
judgments but does not remove that feedback delay. Timestamped gamepad polling is
an observation clock, rather than a hardware timestamp. Stage pictures are static
ROM rasterizations; native level events/parallax do not run behind the highway.

Career progression, local multiplayer and direct-connect multiplayer remain
full-version requirements. This arcade PoC keeps song sessions and timestamped
inputs separable for that future work.

For reproducible ROM artwork and arcade acceptance captures, compile engine test
classes and its test classpath, then run
`com.openggf.tools.SitarHeroCapture --rom-root /absolute/rom/folder --out /absolute/capture/folder --subset all`
on `target/test-classes:target/classes:<test dependencies>` (use `;` on Windows).
The helper expects existing `s1.gen`, `s2.gen`, `s3k.gen` paths and also accepts
`--subset s1`, `s2`, or `s3k` in separate JVMs. It never creates ROM copies/links;
use the ordinary configured-catalog launch for other filenames. Captures include
tool-labelled autoplay and use an offline PCM sink, so they prove assets and flow
rather than physical speaker/controller timing.


## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md) for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py sitar-hero --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Use `--s1`, `--s2`, or `--s3k` for the games this example consumes. Explicit paths create isolated development configuration and saves; no ROM is copied or linked. The creator kit exports this example with a portable POM and `tools/build_project.py`; it needs no engine source checkout. Only production sources/resources enter the validated mod jar. Read [recipient installation](../../docs/modding/installing-mods.md) before sharing.
