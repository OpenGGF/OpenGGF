# Part 8: screens and polish

*[Flappy Tails tutorial](README.md), scene path. Checkpoint: the finished game.*

A game is more than its rules. This part adds the screens around a flight (title, results,
records, pause) and the dressing that makes moments land: the score in the game's own digits,
medals, sparkles, shakes, flashes, and the reward for clearing the tour, Super Tails.

## The screens

`FlappyScene` owns four screens:

```java
enum Screen { TITLE, PLAY, RESULTS, RECORDS }
```

`update` and `draw` each switch on the screen, and `show(next)` resets the screen's own tick
count so every screen can animate from zero. Transitions are drawn, never waited for: a panel
rises over its first thirty ticks because `draw` computes its position from the screen's tick,
while input is accepted the moment the screen allows it.

**The title** is a live attract mode. Behind the menu, a silent run is flown by the autopilot
forever: real pillars, real rings, a real Tails. The logo is built from title-card pieces: the
red banner drops in at the left as a card's does, FLAPPY TAILS (cut from the cards' lettering)
and the card's own ZONE slide in from the right at 16 pixels a frame. Now and then Eggman, who
put the pillars up, crosses the sky in his Egg Mobile: the ROM's ship, his head inside its
dome, its exhaust flickering. The menu sits right of Tails' flight line, so the attract Tails
never hides behind it, and takes keys, pad and mouse alike.

**The flight** draws the world, the HUD, the zone's title card while it plays, GET READY in
card lettering with a tapping hand, medal toasts and the pause overlay.

**The results** come in the way S3K's game over does: GAME from the left and OVER from the
right, meeting in the middle. A panel rises, the score counts up with a click every other step
and the register sound at the end, the medal stamps down with `sfx_BigRing`, and a new best
earns the 1-up jingle. A press during the count finishes it; the next press flies again.

## The score in HUD digits

`ArtUnc_HUDDigits` holds the 8x16 digits the S3K HUD counts with: two tiles each, top tile
first. `rom.tiles` decodes raw tiles that have no mappings:

```java
digits = rom.tiles(HUD_DIGITS, RomSpriteRequest.Compression.UNCOMPRESSED, 0, 10, 2, true, palette);
```

Ten tiles across, two down, column by column: one image with the ten digits side by side.
Drawn twice size at the top of the screen, they are Flappy Bird's big score. Each gate pulses
the score up half a size for eight ticks.

## Medals

Each zone cleared is a medal: bronze, silver, gold, platinum and, at fifty, a rainbow SUPER.
A medal is the ROM's ring three times its size, washed in the tier's colour with
`SceneDraw.withFlash` (which mixes towards a colour and keeps the shading; `withTint`
multiplies, and a yellow ring multiplied by silver is not silver), with a sparkle frame
glinting at its rim.

## Juice

[`Effects`](../../../../examples/flappy-tails/src/main/java/flappytails/Effects.java) holds
short-lived dressing, added when the run reports an event and advanced in `update`:

| Event | Dressing |
| --- | --- |
| ring | the ROM ring's sparkle frames, drifting left with the course |
| gate | the score pulses |
| ten gates | a medal toast slides down |
| new zone | a white flash, the zone's title card, its music |
| hit with rings | a small shake, an orange flash, the rings scattering |
| crash | a big shake, a white flash, the ROM's explosion, the music fading |
| Super Tails | a gold flash, SUPER TAILS sliding across |

The shake is deterministic (a sine of the tick count, settling as it ends), so a capture made
twice is identical.

## Super Tails

Clear all five zones and Tails turns Super for twenty seconds, as fifty rings and the
emeralds make him in the game: the transformation sound (`sfx_SuperTransform`), the
invincibility theme, a golden glow, a trail of sparkles and four Flickies circling him, the
ROM's blue Flicky washed gold as his Super Flickies are. Pillars and badniks he touches burst
into explosions; the ground bounces him back up. His theme plays on through the next zone's
card; when it ends, the zone's music returns.

It is the moment the whole tour builds to, so it is staged rather than switched on: the
transformation sound, the flash and the banner come on the same tick, and the autopilot (which
normally threads gaps) deliberately aims into pillars while Super, so attract mode and the
promo show it off.

## Try it

- Give the results screen a second line: "BEST IN HYDROCITY" for a run that ended there.
- Swap the medal's ring for the special stage's Chaos Emerald art
  (`ArtKosM_SStageChaosEmerald` and `Map_SStageChaosEmerald` in the listing).
- Make Super Tails last until the next crash.

**Next: [part 9, tests, autopilot and the promo](09-tests-and-promo.md).**
