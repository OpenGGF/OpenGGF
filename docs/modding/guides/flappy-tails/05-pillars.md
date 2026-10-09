# Part 5: pillars

*[Flappy Tails tutorial](README.md), scene path. Checkpoint:
[`tutorial/part-5-pillars`](../../../../examples/flappy-tails/tutorial/part-5-pillars).*

This part makes it a game. Pillars cut from Angel Island's own ground slide in from the right;
each one passed scores; touching one, or the ground, ends the flight; the best score is saved.
That is all of Flappy Bird, in about 150 lines of scene plus three of the finished game's
classes: [`ZoneArt`](../../../../examples/flappy-tails/src/main/java/flappytails/ZoneArt.java),
[`Zone`](../../../../examples/flappy-tails/src/main/java/flappytails/Zone.java) and
[`Course`](../../../../examples/flappy-tails/src/main/java/flappytails/Course.java).

```bash
python3 examples/build_example.py flappy-tails/tutorial/part-5-pillars --run
```

## A pillar from the ground

Flappy Bird's pipes would look wrong in Angel Island, and an S3K patch cannot ship Sega's art
anyway. So `ZoneArt` builds a pillar from the act itself, with the same two calls part 2 used
for the ground:

1. **Find a flat piece of floor.** Of the stages `levelStages` returns, take the one whose floor
   varies least, then the first pillar-wide window in it where the floor is perfectly level.
2. **Cut its surface and its inside.** Just below a floor's surface the act is solid ground. A
   48-pixel-wide cut 48 rows down is the zone's wall texture: Angel Island's leaves,
   Hydrocity's bricks, Launch Base's girders. A cut from 12 rows above the floor line is the
   surface itself: grass, a railing, a ledge.
3. **Stack them.** Surface on top, texture repeated beneath, 256 rows tall so a pillar never
   shows its end. Shade the two outer columns on each side a little darker and it reads as
   round.

A **bottom** pillar is that picture with its surface line at the gap's lower edge. A **top**
pillar is the same picture flipped upside down, its surface line at the gap's upper edge, the
grass hanging into the gap:

```java
canvas.draw(art.pillar, x, gate.bottom() - 12, SceneDraw.plain());
canvas.draw(art.pillar, x, gate.top() + 12 - ZoneArt.PILLAR_HEIGHT, SceneDraw.plain().withFlipY(true));
```

Because the method only asks "where is this act's floor?", it works on every act the engine
can picture, with no per-zone art. Part 7 shows the five results side by side.

The ground strip comes from the same stages. Some stages carry waterfalls or machinery, so
each `Zone` names a stage and a column range chosen by eye (`Zone.Ground`). The finished game
mirrors the strip into one seamless picture once, rather than flipping every other copy while
drawing as part 2 did.

## A course that is always fair

`Course` lays gates out along an endless line. Gate `n` stands at a position that depends only
on `n`: 176 pixels apart, with 640 pixels of open sky after every tenth (part 7 fills that sky
with a title card). Its gap is drawn from one seeded random generator, in gate order, so the
same seed always lays the same course.

The interesting rule is how far a gap may move from the last one. Part 4 showed that Tails
climbs at most a pixel a frame, and that a flap which saves him from one gap's floor carries
him upwards for half a second. So a gap's centre may move at most two-fifths of the frames
between gates (at most 40 pixels) either way:

```java
int frames = SPACING * 256 / zone.speed();
int maxRise = Math.min(40, frames * 2 / 5);
int maxDrop = maxRise;
```

The first version allowed bigger drops than climbs, because falling is easy. An autopilot
(part 9) kept crashing on drops: it would flap to clear the floor of one gap and still be rising
when it reached the roof of the next. Fairness came from flying the course, not from arithmetic
alone; the engine tests now fly 100 gates on many seeds and require every one cleared.

The course also keeps itself small: each tick it lays out gates that are about to come on
screen and forgets those that have left it.

## Scoring and crashing

Tails' hit box is the ROM's player touch box (`Touch_NoInstaShield`): eight pixels either side
of his centre, and his `y_radius` less three above and below. A gate is scored once its middle
passes his centre; he crashes if his box overlaps a pillar outside the gap:

```java
if (!gate.passed && gate.x + ZoneArt.PILLAR_WIDTH / 2 < x) { gate.passed = true; score++; ... }
if (x + HALF_WIDTH > gate.x && x - HALF_WIDTH < gate.right()
        && (y - HALF_HEIGHT < gate.top() || y + HALF_HEIGHT > gate.bottom())) crash = true;
```

The gate sound is `sfx_BlueSphere`, the special stage's ding.

## Saving the best

`ctx.storage()` holds small text files under `saves/mods/<mod id>/`:

```java
best = ctx.storage().read("best.txt").map(FlappyScene::number).orElse(0);
...
if (score > best) ctx.storage().write("best.txt", Integer.toString(best = score));
```

Parse defensively: the player can edit that file.

## Try it

- Seed the course with the tick count when the flight starts: every flight differs.
- Make `PILLAR_WIDTH` 64, or cut the body from deeper in the ground (`BODY_DEPTH`).
- Change `Zone.tour()`'s first gap from 100 to 72. Then to 60, and see whether you can still
  play it. The autopilot can; part 9 explains why that is not the same thing.

You have a game. The rest of the tutorial makes it a finished one.

**Next: [part 6, the rules](06-rules.md).**
