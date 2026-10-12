# Part 2: a scene of your own

*[Flappy Tails tutorial](README.md), scene path. Checkpoint:
[`tutorial/part-2-first-scene`](../../../../examples/flappy-tails/tutorial/part-2-first-scene).*

By the end of this part, choosing Sonic 3 & Knuckles opens your scene instead of the stock title:
Angel Island's sky and sea scrolling with the zone's own parallax, and a strip of the act's
real grass rolling past at the bottom. About eighty lines, none of them art.

```bash
python3 examples/build_example.py flappy-tails/tutorial/part-2-first-scene --run
```

## The manifest and the entry point

A mod is a jar with a manifest. Flappy Tails is a **patch** on S3K with a code entry point:

```yaml
# src/main/resources/META-INF/openggf-mod.yaml
formatVersion: 1
id: flappy-tails
name: Flappy Tails
version: 1.0.0
engineApiRange: ">=0.7.0 <0.8.0"
type: patch
baseGame: s3k
entrypoint: flappytails.FlappyTailsMod
dependencies: []
audioOverrides: {}
artOverrides: {}
```

The entry point registers one thing, a startup scene, and asks for the 400-pixel 16:9 screen
so the player sees further ahead. The engine switches the display for the session and restores
the player's own setting at the master title.

```java
public final class FlappyTailsMod implements GgfMod {
    @Override
    public void register(ModContext context) {
        context.requireDisplayWidth(400);
        context.registerStartupScene(FlappyScene::new);
    }
}
```

This class never changes again; the finished game registers exactly the same. (Each
checkpoint's manifest uses its own id, such as `flappy-tails-part2`, so checkpoints can be
installed beside the finished game.)

## The scene's four calls

A `ModScene` has `enter` (once), `update` (sixty times a second), `draw` (once per presented
frame) and `exit`. The rule that shapes everything later: **state changes only in `update`.**
`draw` can be skipped or repeated, so it only reads. Our scroll position advances in `update`
and `draw` paints from it:

```java
@Override public void update(SceneContext ctx) {
    ticks++;
    scroll += SPEED;          // two pixels a frame
}
```

## A zone's background, with its parallax

`ctx.art().rom()` is the running game's ROM art. For the acts it can picture,
`zoneBackdrop(zone, act)` returns the act's background as one picture cut into horizontal
bands, each with the speed the stock scroll routine gives it. Angel Island act 1 is zone 0,
act 0:

```java
SceneRomArt rom = ctx.art().rom();
if (rom != null && rom.hasZonePictures(0, 0)) {
    backdrop = rom.zoneBackdrop(0, 0);
}
```

`drawBackdrop(backdrop, top, scrollX, ticks)` draws the rows from `top` down and scrolls each
band by its own speed (and the clouds drift by themselves). Scroll by the distance travelled,
and the sea, islands and sky move at their own depths, as in the game:

```java
canvas.drawBackdrop(backdrop, BACKDROP_TOP, scroll, ticks);
```

`BACKDROP_TOP` (220) was chosen by looking: it puts the horizon low on the screen with the
jungle just under the ground strip. Always check for null: an act without pictures, or a ROM
that cannot supply them, gives you nothing, and the scene should still draw (here, a blue sky).

## The ground, cut from the level

Flappy Bird has a ground that scrolls under everything. Ours is Angel Island's own. Two calls do
it. `levelStages(zone, act, width, headroom, maxRise)` finds runs of the act's floor at least
`width` wide, with `headroom` pixels clear above, rising at most `maxRise`; each stage knows the
floor row of every column. `levelForeground(zone, act, x, y, width, height)` renders a
rectangle of the act's foreground with the sky left transparent.

```java
List<SceneLevelStage> stages = rom.levelStages(0, 0, 400, 96, 8);
SceneLevelStage stage = stages.get(0);
int x = stage.x() + 150;                       // past a waterfall at the stage's left end
int floor = stage.floorAt(x + 125);
ground = rom.levelForeground(0, 0, x, floor - 12, 250, 48);
```

Twelve rows above the floor line keep the grass blades; 48 rows in all keep some earth. Why
`x + 150`? Because the first time this ran, a waterfall poured through the strip. Pictures of
real levels contain whatever the level contains; look at what you cut.

To scroll a strip forever, repeat it, mirroring every other copy. A strip and its mirror image
meet at the same column, so the joins never show:

```java
for (int i = 0, x = -offset; x < width; i++, x += tile) {
    canvas.draw(ground, x, GROUND_Y - 12, SceneDraw.plain().withFlipX(i % 2 == 1));
}
```

## Music

`ctx.audio()` plays the running game's music and sounds by driver id, the ids in the
disassembly's `sonic3k.constants.asm`. `mus_AIZ1` is `$01`:

```java
ctx.audio().playMusic(MUSIC_AIZ1);
```

## Try it

- Change `BACKDROP_TOP` to 0, then 400. The backdrop is one tall picture; you are choosing the
  window.
- Try Hydrocity (`zoneBackdrop(1, 0)`) or Sky Sanctuary (`10, 0`). Their music is `$03` and `$15`.
- Set `SPEED` to 6. Parallax is just multiplication.

**Next: [part 3, Tails from the ROM](03-tails.md).**
