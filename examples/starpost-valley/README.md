# Starpost Valley — a farming life in Green Hill, after the credits

A code mod for OpenGGF's JVM build: a complete *Stardew Valley*–style farming and life game played
as a full-screen **mod scene** over Sonic 3 & Knuckles. Every Sonic game ends with Sonic running away
from the wreckage of his victory. This is the part the credits skip: Green Hill churned up by
badniks, and dozens of freed animals with nowhere to go. Farm the valley's old fields, rebuild
Robotnik's cracked animal capsule into the valley's heart (or sell out to his Robomart), pop badnik
pests, run the loop when you're out of Momentum, make friends, and get through a year in the valley.

Everything you see and hear comes from your own ROMs at runtime: Green Hill is Sonic 1's own level
blocks, collision, background and palette, and the town is built from its pixels; Sonic, Tails,
Knuckles, the Egg Robo, the shields and the title-card lettering come from Sonic 3 & Knuckles; the
Marble Ruins are Marble, Labyrinth and Scrap Brain Zones. The soundtrack is the games' own songs,
played by the sound driver with the sound effects over them. The jar holds code plus a few original
pictures of crops, fish, seed packets and food.

## Requirements

- The OpenGGF JVM build from this line (Mod API 0.7 candidate). Native builds cannot load code mods.
- **Sonic 3 & Knuckles** (locked-on) **and Sonic 1**, both set up as for the stock games. Without
  Sonic 1 the mod explains what is missing. **Sonic 2** is optional: when supplied, more tracks join
  the jukebox.

## Build and run

```bash
examples/starpost-valley/play.sh             # build what changed and play
examples/starpost-valley/play.sh --fresh     # ...after deleting the save and settings
python3 examples/build_example.py starpost-valley   # just package target/examples/starpost-valley/starpost-valley.jar
```

To install the jar instead, copy it into `mods/` and enable and trust it in the Mod Manager; then
choose **Sonic 3 & Knuckles** on the master title. The mod plays at 16:9 (400×224). *Play Sonic 3 &
Knuckles* on its title returns to the stock game; holding Escape returns to the master title.

## Controls

| Action | Keyboard | Pad |
|---|---|---|
| Move (the farm: along the valley and into or out of the field) | Arrows | D-pad |
| Jump; confirm in menus | Space, Z (Enter in menus) | A, C |
| Act: till, plant, water, harvest, clear, place, use; back in menus | X | B |
| Spin dash: hold Act to charge, release to roll along the row, tilling | hold X | hold B |
| Enter a doorway in the valley | Up | Up |
| Talk to a neighbour; give the held item | X near them | B |
| Fish: cast with the rod at the pond's edge or at Waterfall Lake (hold to wind up); in the Bubble Bar hold to rise | X | B |
| Monitors (inventory), skills, valley population; the social page | Tab, I or Enter; then Up or E | Start |
| Options (in the inventory) | O | — |
| Hotbar | Q / E, 1-0, mouse wheel | — |

## How to play

If you know *Stardew Valley*, you know the rhythm; the names are Sonic's.

| Stardew Valley | Starpost Valley |
|---|---|
| Gold | Rings (also your health in the Ruins) |
| Energy | **Momentum**: chores drain it; laps of the farm loop, rings and springs refill it (or classic stamina in Options) |
| Hoe / watering can / axe & pickaxe | Spin dash / Water Shield / Fire Shield (S3K's own monitor icons) |
| Shipping bin | The shipping signpost (it spins at night, like the end of an act) |
| Community Center | The Great Capsule: six chambers of bundles |
| JojaMart / Joja Cola | Robomart / Robo Cola |
| Robin and Clint | Tails's workshop: Buzz Bomber waterers, the Caterkiller Crawler, Item Monitors, coops and pens, upgrades |
| Crows | Badnik pests (pop them: each frees an animal and grows the valley) |
| The mines | The Marble Ruins: forty chambers through Marble, Labyrinth and Scrap Brain Zones |
| Romance | **Partners**: a ten-heart bond with a gift each morning |
| Villagers | Thirteen neighbours (the two heroes you did not choose among them); the animals speak in pictures until Tails builds his translator |
| Fishing minigame | The Bubble Bar, with Sonic 1's badniks lurking in the deep |
| Bulletin board | The Signpost Board by the Lamppost Inn |
| Grandpa's evaluation | The Signpost Spin, on the first morning of year two |

**The farm** is a field in front of Green Hill's cliffs: walk along it and into and out of its
rows. The Star Post at its east end folds the view into **the valley**, which plays in side view
with Sonic's own movement: springs, slopes, the loop, and the town (Dandel's seeds, the Lamppost Inn,
Tails's workshop, Robomart), up to the plateau where the Great Capsule stands.

**A day** runs from 6:00 to 2:00 (14 real minutes; 20 or 28 in Options). Sleep at the farmhouse door
to ship, grow, and see the night's tally; stay out past 2:00 and you pass out, losing some rings.

**Choose your farmer.** The other two become your neighbours, and each farms differently:

| Farmer | On the farm | In the valley and the Ruins |
|---|---|---|
| Sonic | The strongest spin dash: it tills its whole roll | Fastest; springs reach the high ground |
| Tails | A weaker spin (three plots), but his tails fan each Water Shield charge over two plots | Flies for a while after a second jump |
| Knuckles | Digs instead of tilling, turning up buried rings and finds; punches rocks apart | Glides after a second jump and climbs walls; too heavy for the loop's full Momentum |

**Neighbours.** Talk once a day and give gifts to raise hearts; heart events play out around the
valley, and a Flicky brings the morning's letters. The animals talk in pictures until Tails's
Chirp Translator. Two neighbours can become Partners.

**The Marble Ruins.** Down from the valley's doorway: forty chambers, new every morning, with rings
as your health, minerals, geodes and lost Records to find, and a Star Post elevator every fifth
chamber.

**Waters and barns.** Fish the farm pond and Waterfall Lake (the legends are badniks). Build a coop
and a pen at Tails's workshop for the freed animals, and turn produce into goods with Monitor Jars,
kegs, the loom and the press.

**Festivals.** Two a season, posted on the Signpost Board's calendar: a Ring Hunt, the Sunflower
Parade, the Great Valley Race, the Night of the Flickies, the Valley Fair, Scrap Brain Night, the Ice
Cap Festival and the Star Light Feast. The board also carries the neighbours' requests.

**The Great Capsule.** Fill its six chambers' bundles to restore the valley, or buy the same
improvements at Robomart. The year ends with the Signpost Spin.

## Status

Built as an example of the scene API at production scale; the design, the decisions and the
rejected approaches are in `docs/architecture/designs/2026-10-09-starpost-valley.md`.
