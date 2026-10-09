# Part 9: tests, autopilot and the promo

*[Flappy Tails tutorial](README.md), scene path. Checkpoint: the finished game.*

The last part proves the game works, makes it play itself, and films it. All three lean on one
decision from part 6: a `Run` is plain Java that takes one button a tick.

## Tests that need no ROM

The rules are plain Java, so most tests need neither a ROM nor a screen. The finished example
keeps them in its own [`src/test/java`](../../../../examples/flappy-tails/src/test/java/flappytails),
and the engine suite runs them (`TestFlappyTailsExample`) after building and validating the jar
the way a creator would:

- **`FlightTest`** checks the port against the routine, a claim per test (part 4).
- **`CourseTest`** checks that a seed always lays the same course, that every gap is on
  screen, and that no gap moves further from the last than flight can follow.
- **`RunTest`** flies whole runs: GET READY waits for a press; never flapping hits the ground;
  in SONIC RULES the ground knocks Tails back and scatters exactly 32 rings in the ROM's
  pattern; a lap of the tour turns him Super and he smashes pillars; records round-trip and a
  future file format is refused.

## Tests on a real session

`TestFlappyTailsScene` builds the mod, opens its scene against a real S3K session with no GL,
and plays it through `ExampleModHarness`: every zone's backdrop, pillar, ground and title card
comes out of the ROM, the lettering spells every word the game uses, a real flight (one press,
then nothing) crashes into the results and writes `records.txt`, the menus move, and the
autopilot flies the whole tour into lap two without the fault boundary catching anything.
`TestFlappyTailsTutorial` builds and plays every checkpoint and requires the classes they share
with the finished game to be identical, so this tutorial cannot teach stale code.

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=TestFlappyTails*" \
  "-Ds3k.rom.path=/absolute/path/to/your/s3k.gen" test
```

## Debug jumps

A scene that implements `DebuggableScene` takes text commands from tests and capture tools.
Flappy Tails understands `title`, `records`, `play[:classic|sonic[:zone]]`, `seed:<n>`,
`autopilot:on|off`, `daring:<percent>`, `rings:<n>` and `results`. Commands that need audio
or storage are queued and carried out at the next `update`, inside the scene's own lifecycle.
Unknown or malformed commands return false, and a test checks that they do.

## An autopilot that plays fair

[`Autopilot`](../../../../examples/flappy-tails/src/main/java/flappytails/Autopilot.java) flies
by looking ahead. Each tick, if a press would do anything, it tries every plan of the form
"flap after waiting *w* ticks" (w = 0, 2, 4, ... 62) on a **copy of the real `Flight`**,
simulating 110 ticks of the coming course. After the planned flap, both futures follow the
same habit: flap when, gliding, Tails would be below where he wants to be in sixteen ticks.
It presses now only if flapping now is the best plan; ties keep the later flap, so it never
presses without a reason. Where it wants to be is as close to the gap *after* next as the
next gap safely allows: leaving each gap already heading for the next.

Three lessons came out of building it, and all three changed the game:

1. **The first autopilot aimed at each gap's centre and crashed constantly.** Tails is too
   floaty to fly gap to gap; you have to set up for the next one. That is a fact about the
   game a player must learn too, so the README now says it.
2. **When every plan crashed, it pressed.** It preferred the earliest of equal plans, and
   flapping repeatedly flew it into the ceiling. Ties now go to waiting.
3. **Big drops after a flap were unfair.** The course generator allowed drops bigger than
   climbs, reasoning that falling is easy. The autopilot showed otherwise, so climbs and drops
   share one limit (part 5). The suite now flies 100 gates on many seeds and requires every
   gate cleared.

An autopilot that clears everything proves a course *possible*, not *pleasant*; it reacts in
one tick and never misjudges. Difficulty for people is tuned by playing.

The same autopilot flies the title screen's attract mode (with a little `daring`, leaning
towards gap edges for show) and, while Super, aims into pillars instead of between them.

## Filming it

`ExampleModCapture` boots the game headless with GL, opens the scene, plays a script of inputs
and debug jumps, and saves PNGs, a WAV of the scene's music and sound (rendered by the engine's
own sound driver) and an MP4. With the autopilot, a whole tour films itself:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests test-compile \
  dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
CP="target/test-classes:target/classes:$(cat target/test-classpath.txt)"
java -cp "$CP" com.openggf.mods.code.ExampleModCapture --rom s3k.gen --mod examples/flappy-tails \
  --out /abs/out --scale 5 --every 0 --ticks 6400 \
  --script "1:jump=seed:0x5eed 2:jump=autopilot:on 3:jump=play:classic" \
  --audio /abs/out/tour.wav --video /abs/out/tour.mp4
```

At `--scale 5` the frame is 2000x1120: crop 40 pixels from each side and 20 from top and bottom
for a pixel-exact 1920x1080. The capture runs as fast as it can, not in real time; the video
and sound are still in step, because both are produced per tick.

### The promo

The Flappy Tails promo is cut from four such captures (the title, a full autopilot tour into
Super Tails, a SONIC RULES ring scatter, a crash into the results) and from title cards drawn
with the game's own pieces. Every capture's timing was planned headlessly first: the same seed
and autopilot fly the same run, so a few lines of Java printed the tick of every zone change,
close call and pillar smash before anything was filmed.

- **Cards** are PNG sequences drawn at 400x224 by a small program that uses the mod's own
  classes: `CardFont` lettering, the red banner, Tails, rings, Flickies, Eggman, backdrops. The
  lettering has no W, X or Z, so the trailer says THIS SEASON rather than IN A WORLD, FIVE
  ZONES borrows the card's own ZONE sprite for its Z, and the end card says START FLAPPING.
  Spell-check what you cannot spell: the first cut read NO FLAPPING.
- **Clean sounds.** Sound effects lifted from gameplay carry its music. A throwaway scene that
  plays one effect a second with the music stopped, filmed with the same tool, gives clean
  stingers; a debug command makes it play a single song for a music bed.
- **The edit** is an `ffmpeg` script: scale 5x with nearest-neighbour filtering and crop to
  1920x1080, slow a close call to 40% with its sound pitched down, punch the zoom on every
  Super Tails smash, flash the cuts, join, normalise to -14 LUFS and limit the true peak.

Everything in it, picture and sound, came out of the ROM through the mod.

That is the tutorial. You chose a road in part 1, built a scene in part 2, gave it Tails,
flight and pillars, rules, a tour, screens and polish, and proved, automated and filmed it.
Go and make your own.
