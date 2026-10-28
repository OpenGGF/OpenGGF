# Sonic Echoes

Robotnik's Echo Engine has caught Sonic in a loop. Solve small puzzles on real Sonic 2
terrain by recording a team of your past selves. An echo replays your **inputs through
Sonic's physics**, so changing its world can change its fate.

Requires the OpenGGF Java 25 JVM engine (0.7 API candidate) and your own Sonic 2 World REV01
ROM. The mod contains no ROM assets. Select **Sonic 2 as Sonic**: the stock title intro plays,
then **ECHOES** appears beneath the emblem. Press Start to open **THE ECHO ENGINE**.

## Play

The hub has three world tabs and four room cards per world. Move with the directional
controls; left/right across the grid's edge changes world. **A/C/Start** plays the selected
room; **B** returns to the title. Keyboard Enter also confirms; Escape backs out.
The installed package includes all twelve room definitions.

| In a room | Keyboard | Standard pad |
| --- | --- | --- |
| Move / jump | Your normal Sonic bindings | Your normal Genesis directions / A/B/C |
| Echo: commit this attempt | E | Y |
| Undo newest echo | U | Right bumper |
| Reset all echoes | R | Left-stick click |
| Toggle hint | H | Left bumper |
| Return to hub | Q | Back / Select |
| Pause | Your normal pause binding | Start |

The first loop displays these controls. All three Genesis face actions still jump in a
room; none is used for Echo, Undo, Reset or Hint. Live debug rewind is disabled.

The clock waits at full until your first movement or jump. Run a short route, then press
**Echo** or let time expire. After a twelve-frame flash, the room resets and your recording
plays alongside you. When its recorded input ends, an echo releases its controls: friction,
gravity and objects still apply. **A stopped echo keeps holding a pressure plate.**

Echo slots are cyan, magenta, gold, lime and violet. Filled pips are recordings; hollow pips
are spare slots; cracked pips have fractured this loop. The bottom timeline shows each
recording's length and your current loop position. Death restarts without recording. At
the room's echo cap, committing restarts without adding a slot; Undo frees the newest slot.

If an echo dies or drifts more than 48 pixels from its recorded path, **PARADOX!** flashes in
its colour and it disappears for this loop. Its recording survives; the next loop tries again.
Touch the Exit Rift with the live Sonic to clear. Wait for the stars, then confirm to return
to the hub. Exit also returns to the hub; the cursor advances to the next card.

## Worlds and stars

| World | Rooms |
| --- | --- |
| **1 Emerald Hill — First Echo** | 1-1 Hold the Door · 1-2 Two Hands · 1-3 Relay · 1-4 Escort |
| **2 Hill Top — Seesaw Science** | 2-1 Lift Off · 2-2 Double Bounce · 2-3 High Ground · 2-4 Counterweight |
| **3 Chemical Plant — Paradox Plant** | 3-1 Don't Touch · 3-2 Spring Relay · 3-3 Clockwork · 3-4 The Last Loop |

Each room awards three independent stars: **Clear**, **Par** (use no more echoes than the
room's par), and **Shards** (collect all three Chrono Shards during the same final loop).
Your live Sonic or any echo can collect shards. Earned stars remain earned across later runs.
Rooms unlock in order within each world. World 2 requires 3 total stars, world 3 requires 9,
and room 3-4 requires 24 plus the preceding room's clear. There are 36 stars in all.

Best echo count, fastest final-loop time and earned stars survive restarts. Saves use the
engine's owner-scoped storage at `saves/mods/sonic-echoes/progress.txt` (or beneath the configured
`openggf.saveRoot`). Writes are atomic and failed writes retry. An unreadable, corrupt or
unsupported save falls back to fresh in-memory progress and is protected from overwrite;
move that file aside manually if you want a fresh writable profile.

## Build and install

From the checkout root, with Java 25 and Maven selected:

```sh
export JAVA_HOME=<path-to-jdk-25>
export PATH="<maven-bin>:$PATH"
python3 examples/sonic-echoes/build.py
python3 examples/sonic-echoes/build.py --skip-engine --install
```

The jar is `target/sonic-echoes/sonic-echoes.jar`. `--skip-engine` reuses the existing engine
classes. `--install` copies it into the checkout's `mods/`, enables it and trusts that exact
SHA-256 while preserving other mod entries. Restart the JVM engine after installation.
`--run` launches a development copy without installing. Tests are excluded from the jar.

## Creator notes

Room resources are `src/main/resources/rooms/<world>-<room>.properties`. The hub has twelve
fixed card slots. Registration reads all twelve definitions through `ModContext.modAssets()`
with bounded reads, retaining immutable room data, hints and authored solutions. Runtime
callbacks use that catalogue after registration closes; classpath resource lookup is not
available to installed mods. A missing or malformed definition rejects registration. Example:

```properties
name=Hold the Door
hint=An echo keeps standing where it stopped.
zone=0
act=0
left=32
right=800
top=480
bottom=560
spawnX=96
spawnY=655
loopFrames=600
par=1
echoCap=5
object.0=plate,145,671,48,8,cyan,0,0
object.1=gate,440,550,24,240,cyan,0,0
object.2=rift,620,612,32,48,,0,0
solution.0=40:R,60:-
solution.1=240:R
```

Zone and act are zero-based. `left` and `right` are physical room bounds; camera X is clamped
to `left..right-384`. `top`/`bottom` bound camera Y origins. Positions are native centre
coordinates except plate Y, which is the contact surface. `hint` is optional (empty by
default); keep it to two short lines. The HUD supplies a generic hint when it is absent.

Objects are contiguous `object.N` entries:
`kind,x,y,width,height,channels,latch,reserved`. Reserved is zero. Kinds are `plate`, `gate`,
`rift`, `shard`, `native`; consult the room parser for additional content-side variants.
Plate latch is the release delay in frames (`0` holds only while occupied). Plates sharing a
channel OR together. Gates AND channels separated by `+` and never close on an overlapping
body. A rift's empty channel list means unsealed. A native placement uses channels for its
numeric object ID (for example `0x14`) and latch for its subtype. All art comes from the ROM.

Reference solutions are contiguous `solution.N` loops, with comma-separated `frames:buttons`
segments. Buttons are `R L U D J` or `-`. Commit between loops and drain the 12-frame rewind
hold; the last loop must clear. Samples store inputs and comparison-only post-physics
positions; recorded positions never drive a body. New rooms need real ROM-backed solution
checks, including shard routes and native-object participation where applicable.

`EchoHub` decorates the stock `TitleScreenProvider`; `startZoneIndex()` and `startActIndex()`
feed its normal level exit path. `RoomLauncher` selects data first. `RoomSession` owns
recordings, presentation counters and progress outside rewind adapters; course snapshots
restore only the world. The HUD follows Survivors' stock-HUD suppression seam and draws
through the persistent `EchoDisplay` object's `LevelOverlayCanvas`.

Focused checkout tests:

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestSonicEchoesFoundation,TestSonicEchoesPresentation \
  '-Dsonic2.rom.path=<absolute-path-to-sonic2-rev01.gen>' test
```

The foundation checks undisturbed zero drift, tutorial/solo routes, fractures and restore
history. Presentation checks save round trips/retries/corruption, unlocks, hub selection and
button policy, twelve-frame freeze/restore timing, and clear/progress/next-card flow. These
are focused checks, not certification of every room or native object's participation rules.
