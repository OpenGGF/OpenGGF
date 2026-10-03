# Infinite Sonic — endless Sonic 1 challenge

A code mod for OpenGGF's JVM build. Start Sonic 1 as **Sonic, solo**. The title
screen's zone picker chooses **Green Hill, Marble, Spring Yard, Labyrinth, Star Light
or Scrap Brain**, and that zone becomes one endless course built from its first act's
ROM terrain, art, background and music. Each zone is a single level: the stock level
select is unavailable while the mod is active (the `LEVEL_SELECT_ON_STARTUP` setting
and the debug level-select key are ignored). Final Zone and the ending stay stock.

The mod always plays in **16:9** (the 400-pixel `WIDE_16_9` view), whatever the
global aspect setting: the wider screen shows more of the course ahead at scroll
speed. The player's own aspect returns at the master title. Trace test mode still
forces native 4:3. Sonic starts running at normal speed. The whole game speeds up by **1.5× every 30 seconds of active play**: 1× → 1.5× → 2.25× → 3.375×. Pausing stops the countdown.
The camera scrolls at a minimum of **75% of Sonic’s normal maximum run speed**,
letting Sonic gain ground until his centre reaches 60% of the screen width, just right of centre.
There the camera follows his position, keeping him on screen while preserving
his native running and jumping physics. Leaving the left edge
completely loses a life, regardless of rings or invulnerability. Pits and lethal
enemy hits also cost a life. Collect ring rows to survive ordinary enemy hits.

A session starts with **no spare lives**. The only way to earn one is rings: every time
the ring counter reaches 100, 200, 300 and so on, you gain a life (reaching 100 again
after losing your rings counts too). Points never award lives. Spare lives are shown on
the HUD. When Sonic dies with a spare life, the stock card is replaced by a menu:
**CONTINUE** spends the life and revives Sonic right where the run is, at the last safe
spot he stood on (solid, pit-free floor), with no level reload. Score, speed, the
countdown to the next speedup, the terrain and cleared enemies all carry on; rings reset
to 0 and Sonic blinks for two seconds. **RESTART** reloads a fresh session at 1× with no
spare lives and a zero score. Press up/down to choose and **SPACE** (player 1 button A)
to confirm. Dying with no spare lives shows the mod's own GAME OVER text; after a second,
**PRESS SPACE TO RESTART** starts a fresh session. Neither path returns to the title screen.

The Sonic 1 title screen gains an **INFINITE** wordmark above the emblem: once
Sonic has risen it streaks in from the right, then glints every few seconds. Below the
emblem, a zone picker rises in: press **left/right** to choose Green Hill, Marble,
Spring Yard, Labyrinth, Star Light or Scrap Brain, then Start to begin that zone's
course. The choice wraps around and is remembered when you return to the title. Both
are drawn in code over the stock ROM title, which otherwise behaves normally. Course
title cards show only the zone name (no "ACT n"), because each zone is a single endless run.

The HUD shows score, current speed, time until the next speedup, rings and lives.
The last five seconds also show a large centered countdown with a chime each
second. Music and sound effects speed up and rise in pitch with the challenge;
pause, rewind, game over and leaving the level release the playback rate.
Survival earns one point per minimum-scroll pixel: **270 points/second at 1×**,
**405 at 1.5×**, and about **608 at 2.25×**, plus normal enemy points.
All movement, enemies, animation and gameplay clocks accelerate together; native
per-tick jump and collision rules remain unchanged. The host caps pacing at 32×
(the HUD then says MAX SPEED). There is no finish line or stock time limit. Disable the mod to restore the stock acts.
Other character/team selections retain their stock behavior.

The mod reads your Sonic 1 ROM through the normal level loader. It selects continuous
floor sections with at least 112 pixels of open space above them (so mazes, tunnels and
overhangs are skipped), aligns them vertically, and pairs them with horizontal reflections
so their outside edges join. A fixed seed chooses sections as you move. Art, palettes,
music and collision tiles come from the ROM; the jar contains only code and a manifest.
It never reads the disassembly or includes exported Sega assets.

Each zone keeps its own look: Marble's grass ledges and brick blocks, Labyrinth's
stone slabs, Star Light's girders and Scrap Brain's machinery. Floors that the stock
level marks with Marble Zone lava hazards are never used as ground. The course is dry:
Labyrinth (and Scrap Brain Act 3, which reuses its layout) has no water, currents,
water slides or drowning, because underwater top speed is slower than the scrolling
edge.

The course logic still accepts any stock act, which keeps the table below and the tests
covering acts 2 and 3, but only act 1 of each zone is reachable in play. Each act uses the badniks its own stock level places, read from the ROM's object
layout for that act and picked as often as the act places them (so Marble's sky is
mostly Batbrains, and Green Hill Act 3 is heavy on Buzz Bombers). They are drawn with
the ROM art the zone loads:

| Zone | Ground | Air |
| --- | --- | --- |
| Green Hill | Motobug, Crabmeat | Buzz Bomber |
| Marble | Yadrin | Batbrain, Buzz Bomber |
| Spring Yard | Crabmeat, Yadrin, Roller (acts 1–2) | Buzz Bomber |
| Labyrinth (and Scrap Brain 3) | Burrobot | Orbinaut |
| Star Light | Walking Bomb | Orbinaut |
| Scrap Brain 1–2 | Ball Hog, Walking Bomb | Orbinaut |

Rolling Rollers and Walking Bombs cannot be destroyed, as in the original game, so jump
over them. Orbinauts keep their four circling spikes, which hurt on contact. Jaws are
left out because the dry course has no water, and Caterkillers (Marble's most common
ground badnik) are not included.

Seeded encounters mix ground patrols, flying patrols and empty sections. Ground enemies require a gentle stretch across their whole patrol; flyers
stay clear of the highest terrain beneath their patrol and bob. The first 1,536 pixels
are enemy-free and have no pits. Jump or roll into enemies to defeat them and earn points. These use
ROM sprites with custom bounded patrols. Ball Hogs hop in place instead of patrolling.
None of them fire projectiles; bombs never light their fuses; Yadrin's spiked back is not modeled.

Every fourth section after the opening is a jump corridor: a 64 to 192 pixel pit
between flat banks, with at least 160 pixels of approach on either side. Corridors
also change elevation. The course moves between four ground levels 32 pixels apart,
climbing or dropping up to 64 pixels per corridor. Climbs use pits of at most 128
pixels, and 192 pixel pits stay level. Some drops are a plain ledge with no pit. Hold Jump while moving to clear a pit;
releasing Jump early shortens the arc. Enemy patrols never occupy these corridors.
Four rings arc over each pit, highest in the middle, tracing the jump that clears it.

From the third corridor on, about a third of corridors become a **platform stretch**:
a 320 to 448 pixel bottomless pit bridged by one to three of the zone's own stock
platforms, level with the lower bank, with at most 144 pixels between footholds. These
are the shipped Sonic 1 objects themselves, with their ROM art, solidity, riding and
sink: Green Hill and Spring Yard use the floating platform (Obj18), half
the time as the kind that falls 30 frames after Sonic lands; Marble and Scrap Brain use
the wide moving blocks (Obj52) held stationary. Only platforms the act's own stock layout
places are used, and only those at least 64 pixels wide, so the Labyrinth course (whose
only block is 32 pixels) and the Star Light course (whose first act places none) keep
ordinary corridors. Rings sit above each platform. A strong run-up can clear smaller
stretches in one jump; wider ones need a platform. No enemies patrol platform stretches.

The other sections retain the ROM-derived hills and dips, raised or lowered to the
current level; raised and lowered levels use fewer hill shapes because the layout
has 256 block slots. Rows of rings appear periodically along the terrain.

At local X=8,192 the engine shifts Sonic and the camera left by 4,096 pixels and
advances the terrain window. Logical distance continues increasing, selecting new
seeded sections. Individual motifs recur, but the complete window does not loop.
The terrain generator supports reverse recycling below local X=2,048, but
backtracking in normal play loses ground against the scrolling camera.

From the repository root, with Java 21 and Maven on PATH:

```sh
python3 examples/infinite-sonic/build.py
python3 examples/infinite-sonic/build.py --run
```

`--run` uses the SDK's explicit development-mod launch. Select/configure your Sonic 1
ROM in the engine as usual. For normal launches, copy
`target/infinite-sonic/infinite-sonic.jar` into the root `mods/` directory, enable
**Infinite Sonic** in Mod Manager, grant code trust, and restart the JVM engine.
Native-image builds cannot load code mods.

The project is source-first and builds against this checkout's unpublished Mod API
0.7 candidate. `build.py` queues the engine compilation, compiles this separate mod,
and packages it through `ggfmod` validation. It does not run tests. Regression:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=/absolute/path/to/your/Sonic 1 ROM.gen" test
```

The finite 64-column window recycles in 16-column steps while preserving Sonic's
fractional position and speed. Backtracking regenerates the same terrain from the
same logical coordinates. Change `TerrainLibrary.SEED` and rebuild for another course.
Enemy positions and patrol phases shift with the world and participate in rewind.
Cleared encounters stay cleared within the retained window; revisiting terrain discarded
from that window can regenerate its encounters. Stock platforms keep their own
coordinates, so the window waits to shift until none are loaded (forcing the shift at
local X=12,288 if necessary). There are no loops, moving platforms, breakable floors or checkpoints. Section reflections can mirror scenery. Background scrolling at a world
rebase still needs visual verification. The coverage matrix and current evidence are
in [the project design](../../docs/architecture/designs/2026-10-01-infinite-sonic.md).
