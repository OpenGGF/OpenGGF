# Part 4: flight

*[Flappy Tails tutorial](README.md), scene path. Checkpoint:
[`tutorial/part-4-flight`](../../../../examples/flappy-tails/tutorial/part-4-flight).*

One button now flies Tails. Not a Flappy Bird hop: his real flight, ported from the
Sonic 3 & Knuckles routine that flies him in the game, so a native player's hands already know
it. This part is about porting a ROM routine faithfully, and it adds
[`Flight`](../../../../examples/flappy-tails/src/main/java/flappytails/Flight.java).

```bash
python3 examples/build_example.py flappy-tails/tutorial/part-4-flight --run
```

## Read the routine first

The disassembly's `Tails_FlyingSwimming` runs every frame while Tails flies. Its core is
`Tails_Move_FlySwim`. Reduced to what matters out of water and not carrying anyone:

```text
Tails_Move_FlySwim:
    ; every other frame, the flight timer counts down
    btst #0, Level_frame_counter+1 / beq  ->  tst double_jump_property / subq #1
    cmpi.b #1, double_jump_flag
    beq   gliding
flapping:                              ; flag 2..$1F
    cmpi.w #-$100, y_vel / blt done_flapping
    subi.w #$20, y_vel                 ; push up
    addq.b #1, double_jump_flag
    cmpi.b #$20, double_jump_flag / bne ceiling
done_flapping:
    move.b #1, double_jump_flag
    bra ceiling
gliding:                               ; flag 1
    A, B or C newly pressed, and y_vel >= -$100, and timer > 0?  ->  flag = 2
    addi.w #8, y_vel                   ; gravity, gentle
ceiling:
    y_pos <= Camera_min_Y + $10 and rising?  ->  y_vel = 0
; then MoveSprite2 adds y_vel to y_pos, with no further gravity
```

`Tails_Test_For_Flight`, which starts flight, sets the flag to 1 and the timer to
`(8*60)/2`: 240, counted down every other frame, eight seconds.

Three things fall out of reading it, and all three shape the game:

1. **A flap is a push, not a jump.** It subtracts `$20` a frame until Tails rises faster than
   `$100` (one pixel a frame) or thirty frames pass. From a fall it takes a while to turn round.
2. **Presses during a flap do nothing.** A new flap can only start while gliding, and not
   while already rising fast. Mashing is not faster.
3. **Gravity is tiny.** Eight 256ths of a pixel per frame per frame, against Sonic's `$38`.
   Tails sinks slowly and speeds up slowly.

## Port it in its own units

Keep the ROM's units and the port reads like the routine. `y_vel` is a signed word in 1/256
pixel; `y_pos` here is also kept in 1/256 pixel:

```java
boolean tick(boolean pressed, boolean oddFrame, int ceilingY) {
    if (oddFrame && timer > 0) timer--;
    boolean flapStarted = false;
    if (flag != 1) {
        if (yVel >= RISE_LIMIT) {              // -$100
            yVel = word(yVel - LIFT);          // $20
            flag++;
            if (flag == FLAP_FRAMES_END) flag = 1;
        } else {
            flag = 1;
        }
    } else {
        if (pressed && yVel >= RISE_LIMIT && timer > 0) {
            flag = 2;
            flapStarted = true;
        }
        yVel = word(yVel + GLIDE_GRAVITY);     // 8
    }
    if ((yPos >> 8) <= ceilingY + 0x10 && yVel < 0) yVel = 0;
    yPos += yVel;
    return flapStarted;
}
```

`word` wraps to sixteen bits as the 68000 would. The underwater and carrying checks are left
out, with a comment saying so: the scene has neither. Name what you leave out; the next
reader will look for it.

The animation comes from the same place. `Tails_Set_Flying_Animation` picks `$24` (tired)
when the timer is zero, otherwise `$21` while rising and `$20` otherwise; and every sixteen
frames, `(Level_frame_counter + 8) & $F == 0`, it plays `sfx_Flying` (or `sfx_FlyTired`).
That buzz is the sound of Tails flying. `Flight.animation()` and `Flight.buzzDue()` port both.

## One button

The scene reads A, B, C, Space and the left mouse button as one flap. Until the first press
Tails bobs; the first press starts flight with that press as the first flap, as Flappy Bird
does:

```java
boolean pressed = ctx.buttonPressed(SceneButtons.ACTIONS) || ctx.keyPressed(SceneKeys.SPACE)
        || ctx.mouse().leftPressed();
if (!flying) {
    if (!pressed) return;
    flying = true;
    flight.start(Math.round(bobY()));
}
flight.tick(pressed, (ticks & 1) == 1, 0);
flight.refill(Flight.FULL_TIMER);   // never tires, as in the native sample (part 6 makes this a rule)
if (Flight.buzzDue(ticks)) ctx.audio().playSfx(SFX_FLYING);
```

The camera's minimum Y is the top of the screen, so the ROM's ceiling sits 16 pixels down:
fly into it and Tails simply stops rising. The ground is a crash: when his feet (centre plus
`y_radius`, `$F`) reach the ground line, the death sound plays, he shows the hurt pose for a
second, and the scene resets.

## Prove the port

A port is a claim about a routine. The finished example's
[`FlightTest`](../../../../examples/flappy-tails/src/test/java/flappytails/FlightTest.java)
checks the claims one by one against the routine's text: gliding adds 8; a flap from rest
pushes ten frames and leaves `y_vel` at `-$118`; presses mid-flap or while rising fast are
ignored; a flap ends after thirty frames; the ceiling stops a climb; the timer counts on odd
frames only and a tired Tails cannot flap; the buzz comes every sixteen frames. Write tests
like these whenever you port: each one is a line of the routine you can point at.

## Try it

- Delete the `refill` line. After eight seconds Tails tires and cannot climb: the ROM's rule.
  Part 6 turns that into a game mode.
- Make `LIFT` `$40`. It feels like Flappy Bird now, and nothing like Tails.

**Next: [part 5, pillars](05-pillars.md).**
