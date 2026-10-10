# Starpost Valley — a farming life in Green Hill, after the credits

A code mod for OpenGGF's JVM build: a complete *Stardew Valley*–style farming and life game played
through a belt-view farm **mod scene** and real Sonic 3 & Knuckles **acts** for the valley,
Marble Ruins, running festivals and Waterfall Lake. Every Sonic game ends with Sonic running away
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
| Farm spin dash: charge, release to till along the row | hold X | hold B |
| Native act spin dash | hold Down + tap Space, release Down | hold Down + tap A/C, release Down |
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
| Tool upgrades | Sneakers: Power Sneakers, Speed Shoes and Chaos Sneakers widen the spin dash (and run on water) |
| Fruit trees | The Green Hill palm, the Ring Fruit Tree and the Chaos Cherry |
| The museum | Tails's Workshop Museum: minerals, the badnik Scrap Collection, relics and Records |
| Grandpa's evaluation | The Signpost Spin, on the first morning of year two |

**The farm** is a field in front of Green Hill's cliffs: walk along it and into and out of its
rows. The Star Post at its east end folds the view into **the valley**, which plays in side view
with the engine's native S3K player: springs, slopes, the loop, and the town (Dandel's seeds, the Lamppost Inn,
Tails's workshop, Robomart), up to the plateau where the Great Capsule stands. Doors bring
up scene menus; closing them returns to the same act doorway. The gate brings you home
to the same farm and day. Seasons, evening/night and weather follow you outdoors; winter
frosts Green Hill, and the Emerald Aurora lights its night sky.

**A day** runs from 6:00 to 2:00 (14 real minutes; 20 or 28 in Options). Sleep at the farmhouse door
to ship, grow, and see the night's tally; stay out past 2:00 and you pass out, losing some rings.

**Choose your farmer.** The other two become your neighbours, and each farms differently:

| Farmer | On the farm | In the valley and the Ruins |
|---|---|---|
| Sonic | The strongest spin dash: it tills its whole roll | Native S3K running, slopes, loops and springs |
| Tails | A weaker spin (three plots), but his tails fan each Water Shield charge over two plots | Flies for a while after a second jump |
| Knuckles | Digs instead of tilling, turning up buried rings and finds; punches rocks apart | Native glide and wall climb |

**Neighbours.** Talk once a day and give gifts to raise hearts; heart events play out around the
valley, and a Flicky brings the morning's letters. The animals talk in pictures until Tails's
Chirp Translator. Two neighbours can become Partners.

**The Marble Ruins.** Down from the valley's doorway: forty chambers, new every morning, with rings
as your health (you carry up to ten of your own down, and bank what you bring back), minerals, geodes and lost Records to find, and a Star Post elevator every fifth
chamber. These are native S3K acts re-encoded from Sonic 1's Marble, Labyrinth and Scrap
Brain terrain at load; their zone palettes remain intact. The elevator, inventory and
results are scenes, with chamber state retained across the round trip.

**Waters and barns.** Fish the farm pond and Waterfall Lake (the legends are badniks). Build a coop
and a pen at Tails's workshop for the freed animals, and turn produce into goods with Monitor Jars,
kegs, the loom and the press.

**Festivals.** Two a season, posted on the Signpost Board's calendar: a Ring Hunt, the Sunflower
Parade, the Great Valley Race, the Night of the Flickies, the Valley Fair, Scrap Brain Night, the Ice
Cap Festival and the Star Light Feast. The board also carries the neighbours' requests.

**Orchard, sneakers and museum.** Saplings grow into trees that pay out for years (the Ring Fruit
Tree bursts ten rings a day for you to catch). Tails's sneakers widen the spin dash a row at a time.
Donate minerals, badnik parts and relics to the museum beside the workshop for milestone rewards;
its Records page is the Inn's jukebox, the valley's Sound Test.

**The Great Capsule.** Fill its six chambers' bundles to restore the valley, or buy the same
improvements at Robomart. The year ends with the Signpost Spin.

## Status

Built as an example of the scene and additive-act APIs at production scale; the design, the decisions and the
rejected approaches are in `docs/architecture/designs/2026-10-09-starpost-valley.md`.

The farm gate visits a real S3K act at `starpost-valley:valley`, with Green Hill
terrain decoded from Sonic 1 and played by the native Sonic, Tails or Knuckles.
Buildings and neighbours use its real floor. Up enters a doorway; closing its
scene menu returns to that door in the same act. Up at the farm gate returns to
the same farm/day/session. Native health rings are separate from the saved wallet;
TIME shows the day clock and RINGS shows the wallet using Sonic 1 HUD art.
The chosen scene soundtrack follows the act, and the farm song resumes at home.
The native solo round trip is verified at 320/400 pixels. Race, Ring Hunt, snowboard
and Waterfall Lake also use native acts; title/choice/verdict screens, stationary
Fair booths and the belt-view farm pond remain scenes. The old `town scene` and
`ruins scene` debug fallbacks have been removed.

The jar contains tiny original typed placeholder resources for registering the
acts; `tools/make_placeholder.py` reproduces them without ROM input. Loading an
act replaces them with terrain read from the supplied Sonic 1 ROM; its native
objects use ROM-backed art intake. No Sega
asset bytes are committed or bundled.
