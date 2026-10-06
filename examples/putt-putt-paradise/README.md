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

The title screen remixes Sonic 2's original landscape, winged emblem, Sonic and
Tails portraits, palettes, and sparkle art, loaded from your ROM at runtime. A
red-and-gold Putt Putt Paradise banner sits above the blue golf menu. The jar
contains no extracted artwork. The landscape fills every supported viewport;
the emblem stays centred, and returning from a course restores its title palettes.

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
| Up / Down | Continuously raise/lower loft from a flat putt to a 90° chip |
| Left / Right | Face the shot direction without walking |
| A while aiming | Open the shot panel; putts go to power, chips first time a contact point |
| Up / Down in the chip panel | Choose intended topspin / backspin; the cyan target and dots update |
| A in the chip panel | Stop the moving hit marker; matching the target gives the planned spin |
| A on the power gauge | Stop one rising/falling sweep and commit the stroke; full power turns pink |
| B before commitment (optional binding) | Cancel freely and return to aiming |
| C while aiming (optional binding) | Toggle survey; arrows pan, C returns to the golfer |
| Start | Pause; choose Resume, Rewind Shot, Concede, or Main Menu with arrows and A |

A and the aiming arrows are sufficient for every shot. Default keyboard A is
Space for P1 and Right Shift for P2; B/C are unbound unless configured. Ground
putts use two A taps; chips use three. The chip marker's position determines
neutral, forward topspin or backward spin. Timing outside the selected cyan
band changes the spin to the stopped marker position, rather than changing loft
or facing. Topspin increases forward flight/carry; backspin reduces it and can
reverse the first landing. These are Sonic physics adaptations of Dream Course's
hit-point controls, not SNES physics emulation.

Upward springs also fire when a rolling golfer enters from either side, using
their original ROM strength, animation and sound. Other spring orientations
keep their native contact rules. At 90° the chip keeps full upward power plus a
small forward bias in the selected direction. A wall still stops horizontal
movement; the bias retries while rising so the ball can move over the edge once
it clears the wall. It stops retrying at the apex and never moves through a wall.
Tall obstacles still need enough shot power to clear them.

During shot selection, the duck pose plays its native entry once and holds the
settled crouch, as when holding Down in-game.
After power locks, feedback and a short pause lead to automatic release.
The gauge rises and falls once; missing it commits a very light shot. Further
charge/cancel presses cannot undo a committed stroke. The golfer stays rolling
until settled. Higher power requests more native charge sounds before the same
brief pause; charge/release sounds retain the game's native spindash pitch behavior.

The initial guide uses full power, as in Dream Course. The chip panel previews
the intended hit point; the power panel previews the actual stopped spin and
current meter power. All shown dots share the real release's spin-adjusted,
surface-relative velocity and rolling-centre correction. They show a short
departure arc, stop before the starting support plane, and do not predict later
terrain/object contacts, landing spin, loops, springs, or the final lie. Online
guest guides use the host's lie, surface angle, roll offset and accepted scene
camera; guest gameplay never advances. Old charge-format peers cannot connect
with the new power/spin wire schema.

Damage, death, a lost ball, or the bounded shot watchdog restores the pre-shot
course state and adds one penalty while retaining the committed stroke. A finish
or valid settlement yields one result even when several conditions coincide.
Successful attacks and pickups alone are not terminal failures.

### Rewind a shot

Each golfer starts with **3 rewinds per hole and 1 per turn**. Press the configured
rewind key (R by default), the primary controller's left bumper, or choose
**Rewind Shot** in the Start menu with arrows and A. A rewind plays the shot
backward and restores the entire course to its pre-shot checkpoint, including
objects, pickups and timers. It refunds that shot's stroke, keeps the same turn,
and spends one allowance. A retry does not renew the per-turn allowance.
Long shots rewind faster, taking at most 90 presentation ticks (about 1.5 seconds
at 60 Hz). The playback uses bounded recordings of ROM-backed scenes; course
physics remains held until the checkpoint is restored.

While an allowance remains, a completed shot waits for **A to keep the result**
or rewind to retry. This includes finishes and penalties, before score changes
or turn/act transitions are finalized. With rewinds off or either budget spent,
shots pass the turn automatically. The setup menu offers **off / 3 / 5 / \*** per
hole and **1 / 3 / \*** per turn; `*` means unlimited. Budgets belong to each
golfer, and the next hole grants its own fresh allowance. Online peers select
matching rewind rules; the host validates ownership, restores the world and
publishes the spent budget and refunded score, including after reconnect.

For BK2/user-recording replay, use the Start-menu command: Genesis movie rows
cannot encode the keyboard R or controller bumper. Raw rewind input is ignored
when stepping a logical movie override. The mod owns live rewind input in every
mode so developer rewind cannot bypass a configured limit.

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
