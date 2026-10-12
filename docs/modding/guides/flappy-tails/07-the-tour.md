# Part 7: the tour

*[Flappy Tails tutorial](README.md), scene path. Checkpoint: the finished game.*

One zone is a level. Five is a journey. This part takes Flappy Tails from Angel Island through
the burning jungle, Hydrocity and Launch Base to Sky Sanctuary, with each zone's real title
card, music and badniks, and spells the game's own words in the title cards' lettering.

## Which zones?

A scene can picture any act for which `SceneRomArt.hasZonePictures(zone, act)` is true. Today
that is five S3K acts, each in one state chosen for presentation, and they make a tour:

```java
static List<Zone> tour() {
    return List.of(
            new Zone(0, 0, "ANGEL ISLAND", 0x01, 220, 0x200, 100, false, new Ground(0, 150, 400)),
            new Zone(0, 1, "ANGEL ISLAND", 0x02, 320, 0x220, 96, false, new Ground(1, 0, 400)),
            new Zone(1, 0, "HYDROCITY", 0x03, 64, 0x240, 92, true, new Ground(5, 0, 360)),
            new Zone(6, 0, "LAUNCH BASE", 0x0D, 8, 0x260, 88, true, new Ground(1, 0, 400)),
            new Zone(10, 0, "SKY SANCTUARY", 0x15, 520, 0x280, 84, true, new Ground(4, 0, 380)));
}
```

Each stop has its zone and act, music id, backdrop window, pace (1/256 pixel a frame), gap and
ground cut. It is a method returning a new list, not a static field: a mod may not keep static
collections, because static state would outlive the scene and leak between sessions. Ten gates
make a zone; fifty, a lap; later laps run faster and tighter (`Zone.forLap`), down to a gap of
72.

Choosing `backdropTop` and the ground cuts was done by eye. Render what you are choosing
from: a throwaway program that calls `levelStages` and draws each candidate stage's strip one
under another settles in a minute what an hour of guessing would not. Hydrocity's stages 3, 4
and 18 have waterfalls pouring through them; stage 5 does not.

## The pillars of every zone

`ZoneArt` needs no per-zone pillar art, because it only asks where each act's floor is.
Angel Island grows leafy columns topped with grass; the burning jungle, scorched ones;
Hydrocity, brick piers with stone caps; Launch Base, yellow girders under purple railings; Sky
Sanctuary, the ruins' green cylinders. Every pillar is the zone's own.

## Changing zones mid-flight

After the tenth gate of a zone comes 640 pixels of open sky with a wave of rings. The run shows
the next zone only once the last pillar of the current one has left the screen, so the switch
never cuts through a pillar:

```java
int ahead = Zone.tourIndex(nextGateNumber());
if (ahead == tourIndex) return;
for (Course.Gate gate : course.gates()) {
    if (gate.tourIndex == tourIndex && gate.right() >= course.scrollX()) return;
}
tourIndex = ahead;            // the backdrop, ground and pillars change here
```

The scene flashes the screen white, starts the next zone's music and plays its title card,
while Tails keeps flying over the open sky. The pace eases to the new zone's by two 256ths a
frame rather than jumping.

## Title cards on the ROM's timing

`rom.titleCard(zone, act)` returns the four sprites the ROM's `Obj_TitleCard` moves: the red
banner, the zone's name, "ZONE" and the act number, each with its object position as origin.
The API documents the choreography, and
[`TitleCard`](../../../../examples/flappy-tails/src/main/java/flappytails/TitleCard.java) plays
it: every element slides in at 16 pixels a frame to its rest position, the card holds for 90
frames once all have arrived, then an exit counter runs and each element leaves at 32 pixels a
frame when the counter reaches its tick (the banner first, upwards; the rest to the right).
Everything is a function of the ticks since the card began, so drawing it changes nothing.
On a 400-pixel screen every x moves right by `(400 - 320) / 2`.

The tables live in `switch` methods, because a static array is static state too:

```java
private static int startX(int i) { return switch (i) { case 0 -> 96; case 1 -> 480; case 2 -> 636; default -> 708; }; }
```

## An alphabet from the title cards

The title should say FLAPPY TAILS in the title-card lettering, and the ROM has no such
alphabet: each zone's card art holds only its own name's letters. But the API returns every
zone's finished name picture, and the names are known.
[`CardFont`](../../../../examples/flappy-tails/src/main/java/flappytails/CardFont.java) cuts the
letters back out:

1. Mark each column of the name picture that has a letter pixel (bright: the dark band behind
   the letters is part of the same tiles).
2. Split the columns into runs and share the name's letters among them. Usually a run is a
   letter. But some letters touch (MARBLE's M and A are one run) and one letter is split (the
   M has a gap). So the sharing is a small search: a run may hold several letters whose known
   widths add up to its own, with at most one letter still unknown, its width what is left.
3. Keep each new letter's light pixels.

Thirteen names give every letter the game uses except J, Q, W, X and Z. Enough for FLAPPY
TAILS, GET READY, GAME OVER, PAUSE, RECORDS and SUPER TAILS; a word with a missing letter falls
back to the menu font. To draw a word, paint one dark band behind it and the letters on top,
so the gaps between letters stay dark as they are on a real card.

## Badniks and palettes

Badniks hover between gates in the three later zones, from `StockSceneArt` recipes: Hydrocity's
Buggernaut (cycling its wing frames a tick apart, as `AniRaw_Buggernaut` does), Launch Base's
Orbinaut (with four spike balls circling it) and Sky Sanctuary's Egg Robo. A sprite's colours
come from the palette lines its art uses, so each badnik is decoded with Sonic and Tails' line
0 and *its zone's* three lines, as the game holds them in its colour RAM:

```java
int[] palette = new PaletteAssembly().rom(rom, PAL_SONIC_TAILS, 0, 16).rom(rom, PAL_HCZ1, 16, 48).build();
SceneSpriteSet buggernaut = cache.sprites(StockSceneArt.S3K_BUGGERNAUT, palette);
```

Palette addresses come from the disassembly's listing file (`Pal_HCZ1` is `$A8D9C`), never from
line numbers. A recipe is checked against the ROM's SHA-1; on another revision it throws, and
the scene draws a stand-in rather than wrong colours.

## Try it

- Swap Launch Base and Hydrocity in the tour.
- Start the title card at the GET READY screen too, for the zone you are starting in. (The
  finished game does; find where.)
- Spell your own name with `CardFont`. If it has a W, you know why it fails.

**Next: [part 8, screens and polish](08-screens-and-polish.md).**
