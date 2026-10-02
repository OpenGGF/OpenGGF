# Infinite Sonic — endless Green Hill challenge

A code mod for OpenGGF's JVM build. Start Sonic 1 as **Sonic, solo**, then enter
**Green Hill Act 1**. Sonic starts running at normal speed. The whole game speeds up by **1.5× every 30 seconds of active play**: 1× → 1.5× → 2.25× → 3.375×. Pausing stops the countdown.
The camera scrolls at a minimum of **75% of Sonic’s normal maximum run speed**,
letting Sonic gain ground until his centre reaches 60% of the screen width, just right of centre.
There the camera follows his position, keeping him on screen while preserving
his native running and jumping physics. Leaving the left edge
completely ends the run, regardless of rings or invulnerability. Pits and lethal
enemy hits also end the run. Collect ring rows to survive ordinary enemy hits.

The HUD shows score, current speed, time until the next speedup, and rings.
The last five seconds also show a large centered countdown with a chime each
second. Music and sound effects speed up and rise in pitch with the challenge;
pause, rewind, game over and leaving the level release the playback rate.
Survival earns one point per minimum-scroll pixel: **270 points/second at 1×**,
**405 at 1.5×**, and about **608 at 2.25×**, plus normal enemy points.
All movement, enemies, animation and gameplay clocks accelerate together; native
per-tick jump and collision rules remain unchanged. The host caps pacing at 32×
(the HUD then says MAX SPEED). There is no finish line or stock time limit. Disable the mod to restore GHZ1.
Other acts and other character/team selections retain their stock behavior.

The mod reads your Sonic 1 ROM through the normal level loader. It selects continuous
floor sections, aligns them vertically, and pairs them with horizontal reflections
so their outside edges join. A fixed seed chooses sections as you move. Art, palettes,
music and collision tiles come from the ROM; the jar contains only code and a manifest.
It never reads the disassembly or includes exported Sega assets.

Seeded encounters mix ground Motobug patrols, flying Buzz Bomber patrols and empty
sections. Ground enemies require a gentle stretch across their whole patrol; flyers
stay clear of the highest terrain beneath their patrol and bob. The first 1,536 pixels
are enemy-free and have no pits. Jump or roll into enemies to defeat them and earn points. These use
ROM sprites with custom bounded patrols; flyers do not fire missiles.

Every fourth section after the opening is a jump corridor: a 64 to 192 pixel pit
between flat banks, with at least 160 pixels of approach on either side. Corridors
also change elevation. The course moves between four ground levels 32 pixels apart,
climbing or dropping up to 64 pixels per corridor. Climbs use pits of at most 128
pixels, and 192 pixel pits stay level. Some drops are a plain ledge with no pit. Hold Jump while moving to clear a pit;
releasing Jump early shortens the arc. Enemy patrols never occupy these corridors.
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
from that window can regenerate its encounters. There are no loops, moving platforms, breakable floors or checkpoints. Section reflections can mirror scenery. Background scrolling at a world
rebase still needs visual verification. The coverage matrix and current evidence are
in [the project design](../../docs/architecture/designs/2026-10-01-infinite-sonic.md).
