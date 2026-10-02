# Infinite Sonic — endless Green Hill challenge

A code mod for OpenGGF's JVM build. Start Sonic 1 as **Sonic, solo**, then enter
**Green Hill Act 1**. Move and jump normally. There is no finish line, time limit or ring placement. Sonic starts with zero rings: one enemy hit is fatal. The HUD timer is paused. Disable the mod to restore GHZ1.
Other acts and other character/team selections retain their stock behavior.

The mod reads your Sonic 1 ROM through the normal level loader. It selects continuous
floor sections, aligns them vertically, and pairs them with horizontal reflections
so their outside edges join. A fixed seed chooses sections as you move. Art, palettes,
music and collision tiles come from the ROM; the jar contains only code and a manifest.
It never reads the disassembly or includes exported Sega assets.

Seeded encounters mix ground Motobug patrols, flying Buzz Bomber patrols and empty
sections. Ground enemies require a gentle stretch across their whole patrol; flyers
stay clear of the highest terrain beneath their patrol and bob. The first 1,536 pixels
are enemy-free. Jump or roll into enemies to defeat them and earn points. These use
ROM sprites with custom bounded patrols; flyers do not fire missiles.

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
from that window can regenerate its encounters. There are no loops, bridges, checkpoints
or difficulty progression. Section reflections can mirror scenery. Background scrolling at a world
rebase still needs visual verification. The coverage matrix and current evidence are
in [the project design](../../docs/architecture/designs/2026-10-01-infinite-sonic.md).
