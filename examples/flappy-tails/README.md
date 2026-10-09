# Flappy Tails

**One button. One fox. Five zones of Sonic 3 & Knuckles, on the ROM's own flight physics.**

Tails flaps through Angel Island, the burning jungle, Hydrocity, Launch Base and Sky
Sanctuary, threading pillars cut from each act's own ground. Every ten gates the next zone's
real title card slides in and its music starts. Clear all five and he turns **Super Tails**:
golden, ringed by Flickies, to the invincibility theme, bursting every pillar he touches.
Then the tour begins again, faster.

This is an S3K **mod scene** for OpenGGF's JVM build (Mod API 0.7 candidate). Everything you
see and hear comes from **your own locked-on Sonic 3 & Knuckles ROM** at runtime: Tails'
frames and animation scripts, his spinning tails, the zone backdrops and their parallax, the
pillars, the ground, the title cards and their lettering, the HUD digits, badniks, Eggman, the
music and the sounds. The jar contains only code and a manifest. Native builds cannot load
code mods.

It is also the finished game of the [Flappy Tails tutorial](../../docs/modding/guides/flappy-tails/README.md),
which builds it step by step. Its first part asks whether you want to change the native game
or replace the whole screen; this is where the second road leads.

## Play

From the repository root, with Java 21, Maven and your S3K ROM set up for OpenGGF:

```bash
./launch-flappy-tails.sh                      # build the engine and the mod, then play
python3 examples/build_example.py flappy-tails   # just package target/examples/flappy-tails/flappy-tails.jar
```

To install it instead, copy the jar into `mods/`, enable and trust it in the Mod Manager,
restart and choose Sonic 3 & Knuckles. The game always plays on the 400-pixel 16:9 screen; your
own display setting returns at the master title. Holding Escape returns to the master title.

| Control | Does |
| --- | --- |
| A, B, C, Space or a mouse click | Flap |
| Up / down, left / right | Menus and options |
| Start or P | Pause; B while paused returns to the title |

## How to play

Tails holds his place a third of the way across the screen while the course scrolls towards
him. Each press is one flap of the ROM's flight: it pushes him upwards for up to half a second
(less if he is already rising), then he glides down under the flight's gentle gravity. Pass
between a pair of pillars to score. Touch a pillar, a badnik or the ground and the flight is
over. The top of the screen does not hurt: the ROM stops Tails rising sixteen pixels below it.

Flight is **floaty** because it is Tails' real flight, not a hop. Flap early for a climb, and
let him sink well before a low gap: a flap that saves you from one pillar's floor carries you
upwards for half a second.

### Modes

- **CLASSIC.** One touch and you're done. Tails never tires (the native sample's rule).
- **SONIC RULES.** Rings save you, as in the games: a hit with rings knocks Tails back and
  scatters up to 32 of them in the ROM's ring-loss pattern, then he blinks for two seconds and
  can catch some back. A hit with none ends the run. And flight **tires** after the ROM's eight
  seconds; every ring buys back a little over a second. The FLIGHT bar shows what is left; a
  tired Tails cannot flap.

**PRACTICE** starts from any zone you have reached. Practice flights count towards your totals
but never set a best or earn a medal.

### The tour

| Gates | Zone | Music | Pace | Gap | Badniks |
| --- | --- | --- | --- | --- | --- |
| 1–10 | Angel Island 1 | Angel Island 1 | 2 px/frame | 100 | — |
| 11–20 | Angel Island 2 (burning) | Angel Island 2 | 2.13 | 96 | — |
| 21–30 | Hydrocity | Hydrocity 1 | 2.25 | 92 | Buggernaut |
| 31–40 | Launch Base | Launch Base 1 | 2.38 | 88 | Orbinaut |
| 41–50 | Sky Sanctuary | Sky Sanctuary | 2.5 | 84 | Egg Robo |
| 51+ | the tour again | | +1/8 per lap, up to 4 laps | −4 per lap, never under 72 | every zone that has them |

These are the five acts the engine can picture today
([`SceneRomArt.hasZonePictures`](../../docs/modding/guides/mod-scenes.md#4-art-from-the-players-rom)).
Between zones there is a stretch of open sky with a wave of rings while the title card plays.
Badniks hover between gates, well off the straight line between two gaps, and bob; they
threaten a lazy line rather than close the way.

Every gap is reachable from the last: a climb or drop between neighbours is limited to what
Tails can fly at that zone's pace (at most 40 pixels). The test suite proves it by flying 100
gates on every seed it tries with an autopilot that sees only what a player sees.

### Medals and records

Each zone cleared earns a medal, a ring the colour of its tier: **bronze** (10), **silver**
(20), **gold** (30), **platinum** (40) and the rainbow **SUPER** ring (50). The results screen
counts up your score, stamps the medal and marks a **NEW BEST!** with the 1-up jingle.
RECORDS shows each mode's best and medal, the furthest zone reached and lifetime flights,
gates and rings. Records live in `saves/mods/flappy-tails/records.txt`.

## What comes from the ROM

| Seen or heard | From |
| --- | --- |
| Tails | `SceneRomArt.character("tails")`: frames and the ROM's animation scripts |
| His spinning tails | `characterAccessory("tails")`, frames $27/$28 as `AniTails_Tail` $B and $C play them |
| Backdrops and parallax | `zoneBackdrop(zone, act)`: each act's background with its stock band speeds |
| Pillars and ground | `levelStages` and `levelForeground`: the act's own floor, cut and stacked |
| Title cards | `titleCard(zone, act)`, slid in at the ROM's speeds and timings |
| "FLAPPY TAILS", "GET READY", "GAME OVER" | letters cut back out of every zone's title-card name |
| Score digits | `ArtUnc_HUDDigits` through `tiles(...)` |
| Rings, explosions, badniks, Eggman, Flickies | `StockSceneArt` recipes with the zones' ROM palettes |
| Music and sound | the running S3K sound driver, by `mus_*` and `sfx_*` id |

## How it is made

The [tutorial](../../docs/modding/guides/flappy-tails/README.md) builds this game step by
step. To read the finished source, go in this order; each file builds on the ones before.

| # | File | What it teaches |
|---|---|---|
| 1 | [FlappyTailsMod](src/main/java/flappytails/FlappyTailsMod.java) | Registration: one startup scene on a fixed 16:9 screen |
| 2 | [Flight](src/main/java/flappytails/Flight.java) | Porting a ROM routine (`Tails_Move_FlySwim`) line for line, in its own units |
| 3 | [TailsArt](src/main/java/flappytails/TailsArt.java) | A character and its accessory from the ROM, layered as the game does |
| 4 | [Zone](src/main/java/flappytails/Zone.java) and [ZoneArt](src/main/java/flappytails/ZoneArt.java) | Backdrops, and pillars and ground cut out of a real act |
| 5 | [Course](src/main/java/flappytails/Course.java) | A seeded, endless course that is always fair |
| 6 | [Run](src/main/java/flappytails/Run.java) | The rules with no screen: phases, hits, rings, Super Tails, reported as events |
| 7 | [Autopilot](src/main/java/flappytails/Autopilot.java) | A pilot that plans on copies of the real flight: tests, attract mode, promo |
| 8 | [CardFont](src/main/java/flappytails/CardFont.java) and [TitleCard](src/main/java/flappytails/TitleCard.java) | Recovering an alphabet from title cards; playing a card on the ROM's timing |
| 9 | [Hud](src/main/java/flappytails/Hud.java) and [Effects](src/main/java/flappytails/Effects.java) | The score in HUD digits, medals, sparkles, shakes, flashes |
| 10 | [FlappyScene](src/main/java/flappytails/FlappyScene.java) | The screens, the flow and the presentation of events |

The split that matters: **`Run` decides, `FlappyScene` presents.** A run takes one boolean a
tick (was a button pressed?) and reports what happened as `Run.Event`s: a gate passed, a ring
taken, a hit, a zone reached. The scene turns those into sound, sparkles and title cards. So
tests, the attract mode and the autopilot all fly real runs with no screen at all.

Nothing in the engine knows about Flappy Tails. The mod uses only Mod API types, keeps no
static state except literal constants (tables are `switch`es, lists are built per call), and
reads every asset byte from the player's ROM.

## Recipes

Each is a change to this mod only; rebuild with `./launch-flappy-tails.sh --skip-engine`.

**Make it easier.** Widen `gap` in `Zone.tour()`, or lower `speed` (1/256 pixel a frame).
The generator's reach limit in `Course.addGate` follows the speed automatically.

**Change a zone's look.** `backdropTop` picks which rows of the backdrop the screen shows.
`Ground(stage, from, to)` picks which floor stage and columns the ground strip is cut from;
the tutorial's part 5 shows how to preview every candidate.

**Add a zone.** Any act for which `hasZonePictures` is true works: add a `Zone` to the tour
and raise `TOUR_LENGTH`. The engine pictures five acts today.

**Change the sounds.** `Sounds` maps every cue to a driver id from `sonic3k.constants.asm`.

## Testing and captures

The engine suite builds this mod from source and plays it:

- `TestFlappyTailsExample` (no ROM): packages and validates the jar, then runs the mod's own
  tests in `src/test/java`: the flight port against the routine, course fairness over many
  seeds, the ring-loss pattern, Super Tails and the records file.
- `TestFlappyTailsScene` (S3K ROM): every zone pictured, the lettering spells its words, a
  real flight crashes into the results and saves records, the menus work, and the autopilot
  flies the whole tour into Super Tails without a fault.
- `TestFlappyTailsTutorial`: the tutorial's checkpoints build, validate, play, and share the
  finished game's classes byte for byte.

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestFlappyTails*" \
  "-Ds3k.rom.path=/absolute/path/to/your/s3k.gen" test
```

`FlappyScene.debugJump` lists the debug commands. `ExampleModCapture` plays them for pictures,
video and sound; with the autopilot it flies a whole tour unattended:

```bash
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/flappy-tails \
  --out /abs/out --script "1:jump=seed:0x5eed 2:jump=autopilot:on 3:jump=play:classic" \
  --ticks 6000 --every 0 --audio /abs/out/tour.wav --video /abs/out/tour.mp4
```

(`CP` is the test classpath; see the [mod scene guide](../../docs/modding/guides/mod-scenes.md#6-testing-a-scene).)

## Limitations

- Five acts, because those are the acts the engine can picture. Each is shown in one state:
  Hydrocity from below its waterline, Sky Sanctuary's cloud sea.
- Badniks hover and bob; they do not run their ROM behaviour. Flying Tails cannot hurt them,
  as in the game, except as Super Tails.
- The flight is the ROM's; the rest (course, scoring, medals, Super Tails' trigger) is this
  mod's own design. Super Tails borrows the game's sound, music and Flickies but not its
  palette cycle: his glow is a tint.
- `ExampleModCapture` renders as fast as it can with offline audio: it shows the game, not
  controller latency.

## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md)
for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports
artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py flappy-tails --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Explicit paths create isolated development configuration and saves; no ROM is copied or linked.
The creator kit exports this example with a portable POM and `tools/build_project.py`. Only
production sources and resources enter the validated jar. Read
[recipient installation](../../docs/modding/installing-mods.md) before sharing.
