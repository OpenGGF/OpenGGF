# Emerald Remix

Ordinary Sonic 2 with two level changes:

1. **Emerald Hill Act 1 is remixed.** At load time the act is rebuilt from column strips of
   your own ROM. An early stretch comes from Act 2, Act 1's double and single corkscrews are
   joined into one triple corkscrew, and the single one appears again where the double used
   to be. Act 1's art, collision, background, music, checkpoints and signpost are unchanged.
   The jar ships only the recipe, [`remix/ehz1.remix`](src/main/resources/remix/ehz1.remix),
   never any ROM layout bytes.
2. **Clover Valley Zone is new.** It is a single original act, with its own tiles, palette,
   collision and parallax background. It plays after Emerald Hill Act 2 and leads on to
   Chemical Plant. It uses stock rings, monitors, a spring, a checkpoint and the signpost,
   and plays the two-player Emerald Hill track.

Everything else is stock: the title, save slots, the other zones and Act 2.

This is a code mod for the unpublished **Mod API 0.7 candidate** on the development line.
It needs Java 25, the matching development JVM build and a Sonic 2 World REV01 ROM.

## Build and install

```sh
python3 examples/emerald-remix/build.py                 # target/emerald-remix/emerald-remix.jar
python3 examples/emerald-remix/build.py --skip-engine   # reuse an already compiled engine
python3 examples/emerald-remix/build.py --run           # build, then launch with the mod
```

Copy the jar into `mods/`, enable and trust it in the Mod Manager, choose **Apply**, then
start Sonic 2.

## The remix recipe

`remix/ehz1.remix` is a small text file:

| Line | Meaning |
|---|---|
| `target ehz1` | The act the recipe rebuilds. |
| `strip <act> <first> <last>` | Copy 128-pixel map columns `first..last` (inclusive) from `ehz1` or `ehz2`, with every object and ring standing in them. Strips are laid left to right, and a column may be used more than once. |
| `drop <act> <id>` | Leave a stock object id out of every strip copied from that act. Act 2's checkpoints are dropped so Act 1's stay in order. |
| `object <x> <y> <id> <subtype> [flags]` | Add a stock object at remixed-level pixel coordinates. |

Join strips where the ground meets at the same height. Check the main path and the lower
path, or a run can fall into a pocket with no way out. Rows below the target act's lowest
camera view are not copied. The camera's right bound moves with the strip that holds the
target's signpost. `RemixedLevel` does the splicing and `EmeraldRemixMod.Module` serves the
result in place of the stock act; every other level loads natively.

## Clover Valley's assets

[`tools/generate_clover_valley.py`](tools/generate_clover_valley.py) draws every tile from a
world-space pixel function. It then builds the patterns, chunks, blocks, collision profiles,
maps and `level.json` in `src/main/resources/levels/clover-valley/`. It reads no ROM.

```sh
python3 examples/emerald-remix/tools/generate_clover_valley.py --check            # verify the committed export
python3 examples/emerald-remix/tools/generate_clover_valley.py --preview /abs/cv.png
```

The terrain, ledge, decoration and placement tables at the top of the script are the level
design. Edit them and rerun the script to change the level.

## Working with the level editor

- **Clover Valley** is a canonical `ModLevelDefinition` v1 export. In the editor choose
  **Import exported level** → Sonic 2 and pick
  `examples/emerald-remix/src/main/resources/levels/clover-valley`. Save it as an
  `.ogfproject`, edit and playtest, then **Export level** to a new directory and copy that
  export over the folder. Once you do, the project is the source; stop running the generator.
- **The remixed act** can be opened with `Shift+Tab` (with `debug.flags.editor`) while you
  play it. The editor adopts the live, remixed layout, and you can playtest or export it as
  an authored level. An export of it contains Emerald Hill's art, so keep it to yourself.

## Tests

`TestEmeraldRemix` compiles this source, packages and validates the jar, and admits it
through the ordinary scanner, validator and resolver. It then:

- checks every remixed column against the stock acts;
- drives Sonic through the whole remixed act and through Clover Valley to their native
  results;
- round-trips Clover Valley through editor import, `.ogfproject`, export and playtest;
- adopts and exports the remixed act.

Run it with an absolute ROM path (it skips without one):

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestEmeraldRemix "-Dsonic2.rom.path=/abs/Sonic 2.gen" test
```

The ROM catalogue must also find the S2 ROM in the working directory, because the remix
patch lists it as a prerequisite. Add `-Dremix.trace=1` to print the route bot's position
every second.
