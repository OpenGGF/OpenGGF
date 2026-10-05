# Putt Putt Paradise

**Sonic 2 Mini Golf** — a source-first Java mod for the unpublished OpenGGF
Mod API 0.7 candidate. Use the matching development checkout; released 0.6
engines and native-image builds do not support this creator code.

The courses use the player's Sonic 2 ROM and the full Emerald Hill Act 1 and
Act 2 terrain. No ROM, Sega art, or music is included in the jar. Bosses and
minibosses are excluded. The implementation and route evidence are tracked in
the [design](../../docs/architecture/designs/2026-10-05-sonic-mini-golf.md) and
[delivery plan](../../docs/architecture/plans/2026-10-05-putt-putt-paradise.md).
Building or loading the mod alone does not certify that every route passes.

## Build and launch

Install Java 21, Maven, and Python 3, then run from this checkout:

```sh
python3 examples/putt-putt-paradise/build.py
python3 examples/putt-putt-paradise/build.py --run --rom /absolute/path/to/sonic2.gen
```

The script queues the engine compile, compiles the external creator sources
separately with `javac --release 21`, and runs the ordinary `ggfmod package`
validation boundary. The output is `target/putt-putt-paradise/putt-putt-paradise.jar`.
It skips engine tests; packaging validation is not gameplay verification.
`--dry-run` prints these steps without changing files or starting Maven/Java.

`--run` launches through `ggfmod run`. It requires an existing ROM path and
writes an isolated configuration and runtime state under
`target/putt-putt-paradise/run`, leaving the checkout's configuration alone.
The ROM is referenced by absolute path, never copied or linked into the build.
Use Sonic 2 World REV01 (SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`). For normal installation, place the
validated jar in the matching engine's `mods` directory, enable it, and explicitly
trust its Java code in Mod Manager. Restart the JVM after replacing a code mod.

## Play

The title menu offers Practice on either act, local alternating two-player
competition, Host, and Join. Both golfers can choose Sonic or Tails independently,
including Sonic/Sonic or Tails/Tails. Competition plays Act 1 then Act 2, reversing
the starting golfer for Act 2 and skipping a golfer who has already finished.
The lower total of strokes plus penalties wins; equal totals draw. Concession or
DNF loses the match. Each golfer has independent course state.

Choose a viewport on the menu: native 320, or 352, 400, 528, and 800 logical
pixels wide, all 224 pixels high. Online peers must use matching engine, API,
mod/rules, ROM identity, and viewport settings. Host and Join default to
`127.0.0.1:20502`; edit the port (1–65535) and, for Join, the IP address or hostname.
Each online participant chooses their own character. For another machine, use
the host's reachable address and allow its chosen TCP port through the network.

Controls below are logical controller buttons; keyboard bindings follow the
engine settings.

| Input | Action |
| --- | --- |
| Up / Down | Continuously raise/lower loft from a flat putt to a higher chip |
| Left / Right | Face the shot direction without walking |
| First A | Duck and start the oscillating power meter |
| Next two A presses | Lock two charge contributions; the second commits the stroke |
| B before commitment | Cancel freely and return to aiming |
| C while aiming | Toggle survey; arrows pan, C returns to the golfer |
| Start | Pause; choose Resume, Concede, or Main Menu with arrows and A |

After the second charge, feedback and a short pause lead to automatic release.
There is no extra launch button or accuracy stage. Further charge/cancel presses
cannot undo the committed stroke. The golfer stays rolling until settled.
Higher power requests more native charge sounds before the same brief pause. The
charge/release sounds use the game's existing native spindash behavior and pitch
rise; the mod adds no pitch calculation.

Damage, death, a lost ball, or the bounded shot watchdog restores the pre-shot
course state and adds one penalty while retaining the committed stroke. A finish
or valid settlement yields one result even when several conditions coincide.
Successful attacks and pickups alone are not terminal failures.

The online host owns simulation and scoring. The guest sends shot choices and
renders authoritative course views; it does not run its own gameplay simulation.
Disconnects hold play while the room allows a 30-second reconnect window. After
match results, Start returns to the title menu.

## Source and checks

The model, menu/HUD, direct TCP room, and ROM course adapter live in separate
creator packages. Engine-side tests compile these actual external sources.
Use the existing ROM's absolute path so ROM tests execute:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestGolfModel,TestGolfMenu,TestGolfProtocol,TestGolfTransport,TestPuttPuttParadise,TestGolfOnlineIntegration" \
  "-Dsonic2.rom.path=/absolute/path/to/sonic2.gen" test
```

`TestPuttPuttParadise` includes fresh full-act input routes for both characters
at all five viewports, every local character pairing, held-course checkpoints,
penalty rollback, non-keyframe live rewind, and the normal development loader's
stock-profile/menu/fade launch. `TestGolfOnlineIntegration` uses separate host
and guest JVMs and verifies real shots, scene images, scores, pause ownership,
concession and teardown. The dated delivery plan records completed verification
and its limits. Scene parity is a presentation check; it does not certify stock
ROM timing or every possible shot through every object.
