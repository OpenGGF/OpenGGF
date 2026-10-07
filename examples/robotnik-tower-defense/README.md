# Robotnik Tower Defence — Industrial Action

Robotnik's workforce has unionised. The Flickies are marching on the base door,
and his response is predictably mechanical: build badnik defenses, upgrade
them, and survive a 15-wave general strike.

This is an experimental S3K **mod scene** for OpenGGF's JVM build on the
development line (Mod API 0.7 candidate). It owns its screen and rules without
changing the stock game's physics. Its Flickies, badniks, Robotnik, Launch Base
background, music and sounds come from your **locked-on Sonic 3 & Knuckles ROM**
at runtime. The packaged jar contains code and the original text font only.
Native builds cannot load code mods.

## Build and play

With Java 21 and the ROM configured in OpenGGF:

```bash
examples/robotnik-tower-defense/play.sh
# Or build/package without launching:
python3 examples/build_example.py robotnik-tower-defense
```

The script launches this mod directly through `ggfmod run`. To install it,
copy `target/examples/robotnik-tower-defense/robotnik-tower-defense.jar` into
`mods/`, enable/trust it in the Mod Manager, restart and choose Sonic 3 &
Knuckles. The title offers **Play Sonic 3 & Knuckles** to return to the stock
game. Holding Escape returns to the master title. The scene asks for 400×224;
the engine restores your display aspect at the master title.

## Play

Pick a defense and an empty numbered site. The five lower sites cover the
ground approach; the upper gantries are useful for flyers and are beyond a
ground saboteur's immediate reach. Towers fire automatically at the bird
closest to the door within their range. You can build during a wave.

Repelled birds leave recovered scrap and cleared waves pay a bonus. Towers
have three levels, improving damage, range and fire rate. Selling returns
75% of the total investment. Between waves, $25 repairs 40 door HP, up to 200.
Hover a card or action for details; selecting a tower shows its range, stats,
upgrade cost and sale value.

| Defense | Cost | Use |
| --- | ---: | --- |
| Snale Blaster | 30 | Cheap direct fire against ground and air |
| Monkey Mortar | 45 | Ground splash against packed pickets |
| Buggernaut | 55 | Rapid anti-air; ignores ground birds |
| Turbo Spiker | 65 | Strong piercing shots that ignore shield armor |
| Orbinaut | 60 | Short-range area damage and slowing |
| Egg Robo | 100 | Long-range fire chaining to three nearby targets |

Yellow couriers move fast, shield carriers block 90% of direct fire after
their additional armor (piercing shots and mortar explosions bypass it), flyers
take the high approach, red-flag organisers speed nearby comrades, and green
saboteurs temporarily jam nearby towers. The wave preview introduces each
tactic. Birds which reach the door keep pecking until repelled. The emergency
bomb damages and knocks back the whole picket, with a 20-second cooldown
counted during active waves. Defeated birds retreat: this is cartoon slapstick.

| Action | Mouse | Keyboard / pad |
| --- | --- | --- |
| Choose site | Click a numbered pad or tower | Arrows / d-pad |
| Open shop | Click an empty site | A/C or Enter on an empty site |
| Build | Click a card, then an empty site | 1–6 or left/right in shop; A/C or Enter |
| Upgrade / sell | Select tower, click action | U / X; or choose from action bar |
| Repair door | Click FIX | R; or choose from action bar |
| Send next wave | Click SEND WAVE | E; or choose from action bar |
| Emergency bomb | Click BOMB | F; or choose from action bar |
| Help | Click HELP | H; or choose from action bar |
| Pause | Right-click without a shop selection | P / Start / Backspace / B |
| Cancel selection | Right-click | B / Backspace |

On a pad, up from the upper sites enters the shop; down from the lower sites
enters the action bar. Left/right selects an action, A/C confirms, and up/down
returns to the grid. The pause menu offers resume, restart and title. Help and
pause freeze every gameplay timer. The title's help has two pages; left/right
or the mouse wheel changes page. Scores, furthest wave and wins are saved
under the mod's owner-scoped `records.txt`; campaign state is not saved.

## Implementation and verification

The visual style follows Slay the Robotnik's Sonic 3-inspired blue framed
panels, gradient buttons, gold focus, outlined two-tone lettering and compact
pixel font. The battlefield's gantries, door and union props are original
canvas geometry; its game sprites and background are ROM-derived.

- `core/`: catalog, deterministic simulation, visual events, records and a
  normal-purchase demonstration strategy. No engine imports.
- `art/RomArt`: cited ROM sprite/palette requests, decoded through `SceneRomArt`.
- `ui/`: small font atlas and drawing only; rendering never advances rules.
- `TowerDefenseScene`: mouse/pad/key input, screens, music, pause and storage.
- `src/test/java/`: independent JUnit rules and records tests. The engine's
  `TestRobotnikTowerDefenseExample` builds, validates and runs these, and
  `TestRobotnikTowerDefenseScene` exercises the real-ROM scene and input.

```bash
python3 tools/testing/maven_queue.py -B -Dmse=off \
  -Dtest=TestRobotnikTowerDefenseExample,TestRobotnikTowerDefenseScene \
  -Ds3k.rom.path=/absolute/path/to/your/locked-on.gen test
```

`ExampleModCapture` can draw the title, help, any campaign wave or terminal
screens using `DebuggableScene`: `title`, `help`, `wave:1` through `wave:15`,
`win`, `lose`. Wave jumps replay ordinary purchases and combat to reach that
wave; demonstration runs do not alter saved records. These are capture entry
points, not proof of balance. Automated runs verify completion; human play
remains the judge of difficulty.

The [design and delivery record](../../docs/architecture/designs/2026-10-07-robotnik-tower-defense.md)
contains decisions, verification scope and evidence.


## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md) for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py robotnik-tower-defense --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Use `--s1`, `--s2`, or `--s3k` for the games this example consumes. Explicit paths create isolated development configuration and saves; no ROM is copied or linked. The creator kit exports this example with a portable POM and `tools/build_project.py`; it needs no engine source checkout. Only production sources/resources enter the validated mod jar. Read [recipient installation](../../docs/modding/installing-mods.md) before sharing.
