# Part 3: Tails from the ROM

*[Flappy Tails tutorial](README.md), scene path. Checkpoint:
[`tutorial/part-3-tails`](../../../../examples/flappy-tails/tutorial/part-3-tails).*

Now Tails hovers over Angel Island, rotor spinning: his body from the ROM's character art and
animation scripts, his tails from the separate tails art, layered the way the game layers them.
The new class is [`TailsArt`](../../../../examples/flappy-tails/src/main/java/flappytails/TailsArt.java),
exactly as the finished game has it.

```bash
python3 examples/build_example.py flappy-tails/tutorial/part-3-tails --run
```

## A character is frames plus scripts

`rom.character("tails")` returns every frame of Tails' sprite (251 of them in S3K) together
with the ROM's animation scripts. Ask a script for its frames and its delay:

```java
SceneSpriteSet body = rom.character("tails");
int[] frames = body.animationFrames(0x20);   // AniTails $20, flying: a single frame
int delay = body.animationDelay(0x20);       // its delay byte plus one
```

Animation ids are the ROM's, from its `AniTails` table. Flappy Tails needs five, and
`TailsArt` names them:

| Id | ROM name | Used for |
| --- | --- | --- |
| `$20` | TailsAni_Fly | flying, not rising |
| `$21` | TailsAni_Fly2 | rising |
| `$24` | TailsAni_Tired | out of flight time |
| `$1A` | TailsAni_Hurt | knocked back (`HurtCharacter` sets it) |
| `$18` | the death pose | the crash (`Kill_Character` sets it) |

Playing a script is division: which frame of the script are we on, `ticks / delay`, wrapped by
the script's length. The tick count comes from `update`, so the drawing stays a pure function:

```java
SceneSprite frame(int animation, long ticks) {
    int[] frames = body.animationFrames(animation);
    int delay = Math.max(1, Math.min(64, body.animationDelay(animation)));
    return body.frame(frames[(int) ((ticks / delay) % frames.length)]);
}
```

## The tails are a second object

In the game, Tails' two tails are not part of his sprite. They are a separate object,
`Obj_Tails_Tail`, which copies his position every frame and picks its own animation from his:

```text
Obj_Tails_Tail_AniSelection:      (one byte per Tails animation)
    ... dc.b $B,$C   ; TailsAni_Fly,2  -> Fly1, Fly2
        dc.b $B      ; TailsAni_Carry  -> Fly1
        dc.b $C      ; TailsAni_Ascend -> Fly2
        dc.b $B      ; TailsAni_Tired  -> Fly1
AniTails_Tail0B: dc.b 1, $27, $28, $FF    ; frames $27,$28, two ticks each
AniTails_Tail0C: dc.b 0, $27, $28, $FF    ; the same, one tick each
```

So while flying, the tails show mapping frames `$27` and `$28`, the rotor seen from the side:
every second tick normally, every tick while rising. `rom.characterAccessory("tails")` gives
the tails' frames. Draw them first, at the same position, so the body sits in front:

```java
void draw(SceneCanvas canvas, int animation, long ticks, float x, float y, SceneDraw style) {
    if (tails != null && (animation == ANIM_FLY || animation == ANIM_FLY_UP || animation == ANIM_TIRED)) {
        long step = animation == ANIM_FLY_UP ? ticks : ticks / 2;
        canvas.draw(tails.frame(step % 2 == 0 ? ROTOR_A : ROTOR_B), x, y, style);
    }
    canvas.draw(frame(animation, ticks), x, y, style);
}
```

Sprites carry an origin, like hardware sprites, and the ROM's character origins are their
centres. Drawing at (`x`, `y`) puts Tails' centre there: the same coordinate the game calls
`x_pos` and `y_pos`. Every position in the rest of the tutorial is a centre.

## A hover

Until the player flaps, Tails bobs four pixels on a sine of the tick count:

```java
float y = TAILS_Y + (float) Math.sin(ticks * Math.PI * 2 / 64) * 4;
tails.draw(canvas, TailsArt.ANIM_FLY, ticks, TAILS_X, y, SceneDraw.plain());
```

`TAILS_X` is 112: a little left of a third of the way across a 400-pixel screen, so most of the
screen shows what is coming.

## Try it

- Draw `ANIM_TIRED` instead. Two frames, twelve ticks each: the drooping Tails.
- Draw every frame of `body` in a grid to find your own poses; `ggfmod sprites s3k.gen s3k
  tails.png char=tails` does the same from the command line.
- Pass `SceneDraw.plain().withFlash(0xA0FFF070)`. You will want that glow in part 8.

**Next: [part 4, flight](04-flight.md).**
