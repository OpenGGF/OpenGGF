# Part 6: the rules

*[Flappy Tails tutorial](README.md), scene path. From here on the checkpoint is the finished
game, [`examples/flappy-tails`](../../../../examples/flappy-tails/README.md).*

Part 5's scene did everything: read the button, moved the course, judged collisions, played
sounds, drew. That is fine for 150 lines. The finished game has two rule sets, rings that
scatter, a Super form and an autopilot, and it splits the work in two:

- [`Run`](../../../../examples/flappy-tails/src/main/java/flappytails/Run.java) **decides.** It
  takes one boolean a tick (was a button pressed?) and owns everything that matters: the
  phase, the course, the flight, score, rings, hits. It never draws and never plays a sound.
- [`FlappyScene`](../../../../examples/flappy-tails/src/main/java/flappytails/FlappyScene.java)
  **presents.** It reads input, feeds the run, and turns what the run reports into sound,
  sparkles, shakes and title cards.

## Phases and events

A run moves through five phases:

```java
enum Phase { READY, FLYING, HURT, CRASHED, OVER }
```

`READY` waits for the first press, bobbing, while the backdrop and ground keep moving and the
pillars do not (Flappy Bird's GET READY). `FLYING` is the game. `HURT` is a knock-back with no
control. `CRASHED` plays the death fall. `OVER` tells the scene to show results.

Everything noteworthy is reported, not performed:

```java
record Event(Type type, int value, int x, int y) { }
enum Type { SFX, MUSIC, GATE, RING, SPILL, HIT, CRASH, ZONE, ZONE_CLEAR, FLAP, SUPER, SUPER_END, SMASH }
```

A ring taken is `RING` (with where, for the sparkle) plus `SFX sfx_RingRight`. A zone reached is
`ZONE` plus `MUSIC`. The scene's `present` method is one `switch` over the tick's events. This
split is what makes the rest possible: tests fly whole runs with no screen, the title screen's
attract mode is just a silent run, and the autopilot (part 9) plans by simulating copies.

## Two rule sets

```java
enum Mode {
    CLASSIC("CLASSIC", "ONE TOUCH AND YOU'RE DONE", false),
    SONIC("SONIC RULES", "RINGS SAVE YOU. FLIGHT TIRES", true);
    ...
}
```

A mod may declare an enum only if its fields are primitives or strings, as here; the validator
rejects enums with mutable state. **CLASSIC** is the native sample's rule: one touch ends the
flight, and the flight timer is refilled every frame. **SONIC RULES** borrows the games'
own rules, and every number comes from the ROM:

- **Rings protect you.** A hit with rings is `HurtCharacter`: a knock-back at `-$400` under
  `$30` hurt gravity, no control until the hop peaks, and `$78` (120) frames of blinking.
  Tails blinks exactly as `Tails_Display` draws him: only when bit 2 of the invulnerability
  timer is set.
- **The rings scatter.** `Obj_Bouncing_Ring` throws at most 32, in mirrored pairs: angle
  `$88` at speed shift 2, stepping `$10` an angle, sixteen rings, then sixteen more at shift 1.
  Each falls under `$18` gravity and bounces off the floor every eighth frame, losing a
  quarter of its speed, and vanishes after 255 frames. `Run.spill()` ports the loop; a test
  checks the first pair against it. Catch them back once the first half second of blinking
  has passed.
- **Flight tires.** The timer runs as the ROM runs it, eight seconds; each ring buys back
  `$28`, a little over a second. A tired Tails cannot flap. With no rings, the next hit (or the
  ground) ends the flight.

## The crash

A fatal hit is `Kill_Character`'s death: a hop at `-$700`, then ordinary gravity (`$38`) until
Tails falls off the bottom of the screen. Flappy Tails adds a twelve-frame freeze first, so the
hit lands. The run reports `CRASH`; the scene shakes the screen, flashes it white, sets off the
ROM's explosion and fades the music.

## Records

[`Records`](../../../../examples/flappy-tails/src/main/java/flappytails/Records.java) keeps
bests per mode, the furthest zone reached and lifetime totals in `records.txt`, as
`VersionedSettings`: ordered `key=value` lines with a format version. Reading a file whose
version it does not know, it starts empty rather than trusting it. A **practice** flight
(started past Angel Island) counts towards the totals but never sets a best.

## Try it

- Make `RING_FLIGHT` `$80`. SONIC RULES becomes forgiving.
- Give CLASSIC the ROM's tiring timer too: delete the `refill` in `Run.fly`.
- Add a third mode. It is one enum constant, and `Run` reads `mode.sonicRules`; give it a field
  of its own (a primitive) and a branch.

**Next: [part 7, the tour](07-the-tour.md).**
