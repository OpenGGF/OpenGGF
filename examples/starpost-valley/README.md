# Starpost Valley — a farming life in Green Hill, after the credits

A code mod for OpenGGF's JVM build: a complete *Stardew Valley*–style farming and life game played
as a full-screen **mod scene** over Sonic 3 & Knuckles. Every Sonic game ends with Sonic running away
from the wreckage of his victory. This is the part the credits skip: Green Hill churned up by
badniks, and dozens of freed animals with nowhere to go. Farm the valley's old fields, rebuild
Robotnik's cracked animal capsule into the valley's heart (or sell out to his EGG store), pop badnik
pests, run the loop when you're out of Momentum, and get through a year in the valley.

Everything you see and hear comes from your own ROMs at runtime: Green Hill is Sonic 1's own level
blocks, collision, background and palette, and the town is built from its pixels; Sonic, Tails,
Knuckles, the Egg Robo, the shields and the title-card lettering come from Sonic 3 & Knuckles. The
jar holds code plus a few original pictures of crops, seed packets and food.

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
| Monitors (inventory), skills, valley population | Tab, I or Enter | Start |
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
| JojaMart | EGG — Eggman Enterprises General Goods |
| Robin and Clint | Tails's workshop: Buzz Bomber waterers, the Caterkiller Crawler, Item Monitors, upgrades |
| Crows | Badnik pests (pop them: each frees an animal and grows the valley) |
| Grandpa's evaluation | The Signpost Spin, on the first morning of year two |

**The farm** is a field in front of Green Hill's cliffs: walk along it and into and out of its
rows. The Star Post at its east end folds the view into **the valley**, which plays in side view
with Sonic's own movement: springs, slopes, the loop, and the town (Dandel's seeds, the Lamppost Inn,
Tails's workshop, EGG), up to the plateau where the Great Capsule stands.

**A day** runs from 6:00 to 2:00 (14 real minutes; 20 or 28 in Options). Sleep at the farmhouse door
to ship, grow, and see the night's tally; stay out past 2:00 and you pass out, losing some rings.

## Status

This example is being built (see `docs/architecture/designs/2026-10-09-starpost-valley.md`).
Villagers and the Marble Ruins are in progress on their own branches.
