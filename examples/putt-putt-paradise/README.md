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
The lower total of strokes plus penalties wins; equal totals draw. The HUD shows
the total directly; `(+1 PEN)` means that total already includes one penalty. Concession or
DNF loses the match. Each golfer has independent course state.

Competition introduces each incoming golfer with a turn card, including their
player number when both choose the same character. **Press the named action
button when ready**, release it, then press again to start selecting a shot.
The prompt follows that player's actual binding: for example, `SPACE` for P1,
`RIGHT SHIFT` for P2, or the assigned controller's physical button. A held button
cannot confirm the handoff or accidentally start the next shot. Practice and a
rewind retry keep their existing shot flow.

Online, both peers see the same handoff. The active player confirms; the other
sees who they are waiting for. The host accepts readiness before allowing a shot,
and the course stays held while waiting. Settlement still passes the outgoing
turn automatically. Reconnecting preserves the current handoff and pause state.

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

The title menu also accepts mouse hover/left click, with right click to return to
the mode picker. Letterbox clicks are ignored. Keyboard/controller navigation
remains available. A and the aiming arrows are sufficient for every shot. Default keyboard A is
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
settled crouch, as when holding Down in-game. Unmoving golfers use a ROM-backed
idle view immediately after a turn switch. Tails' separate appendage swishes
in idle/duck poses and spins during charge, without stepping the held course.
After power locks, feedback and a short pause lead to automatic release.
The gauge rises and falls once; missing it commits a very light shot. Further
charge/cancel presses cannot undo a committed stroke. The golfer stays rolling
until settled. Higher power requests more native charge sounds before the same
brief pause; charge/release sounds retain the game's native spindash pitch behavior.

The HUD slides into place for shot selection, flashes when power locks, and
leaves the course visible during flight. A short trail marks the current shot;
penalties, refunds and finishes have their own feedback. Turn cards, menu choices
and the animated results tally use Sonic 2 sounds from the ROM. These effects
advance on the mod's presentation clock and stop while play is paused.

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
physics remains held until the checkpoint is restored. The mod requests the
engine's existing VHS picture-search effect during local and remote reverse
playback; `rewind.vhsEffect` and `rewind.vhsTearBands` still control its appearance.
This works independently of developer rewind enablement and stops while paused.
The actual recorded shot audio also plays backward at the view's speed. Music and
sound-driver state stay live behind that replay and resume with a short crossfade;
rewinding never stops the driver or borrows developer history. Online peers use
the host's reverse phase/rate to replay their own locally heard audio. A peer that
joins midway cannot replay sounds it did not hear before joining.

Rewind is available during WATCH, while the shot is moving. Settlement, finishes
and penalties resolve automatically and pass the turn; there is no A-to-accept
step. A completed turn cannot be rewound. The setup menu offers **off / 3 / 5 / `*`**
per hole and **1 / 3 / \*** per turn; `*` means unlimited. Budgets belong to each
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

## Learn from this example

Start with the small registration and pure rules files. Networking and scene
streaming are optional layers; a first mod does not need them.

| Read in this order | Responsibility | Useful pattern to copy |
| --- | --- | --- |
| [Manifest](src/main/resources/META-INF/openggf-mod.yaml) and [entry point](src/main/java/paradise/PuttPuttParadiseMod.java) | Declare the candidate API and register one patch and one namespaced object | Register through `ModContext`; let the engine assign ownership |
| [GolfModule](src/main/java/paradise/GolfModule.java) | Wrap the S2 module, keep ROM geometry/art, replace selected object placements, supply the title and controller | Delegate stock behavior and override only what your rules need |
| [GolfMenu](src/main/java/paradise/ui/GolfMenu.java) | Turn menu choices into one validated `Selection` before gameplay starts | Keep setup separate from frame simulation |
| [ShotMeter](src/main/java/paradise/model/ShotMeter.java) and [GolfRules](src/main/java/paradise/model/GolfRules.java) | A-only shot state machine and deterministic power/spin math | Test rules without a ROM, renderer or socket |
| [GolfMatch](src/main/java/paradise/model/GolfMatch.java) and [RewindAllowance](src/main/java/paradise/model/RewindAllowance.java) | Scores, turns, shot identity and undo budgets | Keep the ledger outside world rollback |
| [TurnReadiness](src/main/java/paradise/model/TurnReadiness.java) | Fresh-press handoff and authoritative ready acceptance | Consume confirmation separately from gameplay input; a retry keeps its ready turn |
| [GolfMode](src/main/java/paradise/GolfMode.java) and [GolfSwing](src/main/java/paradise/GolfSwing.java) | Coordinate held/native frames, then apply golf's calculated impulse and landing spin | Choose policy in the mod; use bounded native operations for world changes |
| [GolfHud](src/main/java/paradise/ui/GolfHud.java) and [GolfOverlay](src/main/java/paradise/ui/GolfOverlay.java) | Pure local/remote phase, ownership, hints and score display | Keep friendly UI policy in the mod and render immutable values; hide choices the viewer cannot make |
| [GolfFeedback](src/main/java/paradise/ui/GolfFeedback.java), [GolfMotion](src/main/java/paradise/ui/GolfMotion.java), [GolfCards](src/main/java/paradise/ui/GolfCards.java) and [GolfSounds](src/main/java/paradise/GolfSounds.java) | Presentation events, easing, cards and ROM sound choices | Return sound cues from a pure timeline; keep rendering and audio adapters separate from rules |
| [GolfPoseClock](src/main/java/paradise/presentation/GolfPoseClock.java), [GolfScene](src/main/java/paradise/presentation/GolfScene.java) and [ShotReplay](src/main/java/paradise/presentation/ShotReplay.java) | Pose timing, finish decoration and golf's reverse duration policy | Use the engine's bounded `SceneReplay` and session-owned `AudioReplay`; restore one checkpoint afterward |
| [GolfOnline](src/main/java/paradise/GolfOnline.java) then [GolfRoom](src/main/java/paradise/net/GolfRoom.java) | Adapt the game to a bounded TCP room with host-owned decisions | Keep protocol and transport away from physics rules |

### Follow one shot

1. `GolfMenu.Selection` configures the controller. The ROM-backed spawn settles
   before `GolfMode.openTurn` captures a neutral lie. The real title route keeps
   the selected practice act through the menu reset and finishes the native entry
   overlay before capturing that lie, so shot rewind never restores the title text.
   It holds the settled world while that text exits; waiting for presentation
   does not advance platforms, enemies or physics. The generic course operation
   reports whether this row serviced the entry fade, so the mod needs no access
   to the engine's fade manager.
   Competition holds the
   incoming golfer for readiness. Locally a fresh A confirms; online that owner
   requests confirmation and waits for the host's authoritative AIM state.
2. `beforeTick` advances the shot meter from logical A and arrow inputs. It
   returns `false` while aiming, charging or rewinding; native world
   timers, objects and physics remain held. Pending level-entry music still
   counts down at its native presentation rate; holding physics does not stop
   the sound driver.
3. The meter emits a committed `GolfShot`. `GolfMatch` assigns its identity and
   charges a stroke; `GolfSwing` calculates the same velocity used by the dots,
   calls `CourseControl.launchRolling`, and requests the native release sound.
4. During WATCH, `beforeTick` admits one neutral native frame. `afterTick`
   observes support, damage and the finish gate, applies the first landing's
   spin, and records a bounded view sample if an undo is available.
5. Settlement resolves the result and automatically opens the next turn. Rewinding plays
   views and captured audio backward, presents the final origin row, then closes
   the audio lease and restores the opaque pre-shot `CourseCheckpoint`, refunds the
   stroke and spends the separate allowance. A checkpoint excludes the mode
   adapter, so restoring the world cannot restore a spent allowance.

`CourseControl` owns checkpoint compatibility, native rolling-radius/support
changes, exact registered character replacement and explicit level loading.
It knows nothing about EHZ, golf power, spin, scoring, turn order or golf's
presentation cues. Those decisions belong to the files above; native level
initialization and music scheduling remain engine-owned. Character replacement is for a
single controlled main player; this example intentionally has no CPU sidekick.
Speeds passed to `launchRolling` use signed native 8.8 units: **256 = one pixel
per physics step**. Positions in `playerState()` are character centres.

### Make a small change first

Try changing `GolfRules.POWER_SWEEP_TICKS` from 120 to 150 and rebuilding. The
meter, its preview and the online rules fingerprint all read that constant.
Run `TestGolfModel` before trying a ROM route. Changing just the HUD would show
a different timing bar while retaining the old shot decisions.

For a different course, change the mod's level selection, finish-marker lookup
and explicit `loadLevel(zone, act)` destination together. The engine capability
accepts a module-owned destination; this mod's `GolfModule` deliberately admits
only Emerald Hill. Add a route check for every new act and character before
claiming it works. For a different object rule, register your own namespaced
factory and preserve the ROM placement fields when replacing the spawn.

`GolfHud` is a small next step after the rules: change a friendly stage label or
hint there, then run `TestGolfMenu`. Remote observers use the authoritative phase
and never show a stale local aiming meter. Presentation requests carry only
intensity/speed; the host owns shaders and its user settings.

For a presentation exercise, adjust a card's easing in `GolfMotion` or timing in
`GolfFeedback`, leaving `ShotMeter` and `GolfMatch` unchanged. Its timeline has no
renderer, ROM or socket dependency and is checked by `TestGolfModel`. Use
`CourseControl.buttonLabel` for action prompts rather than guessing a key name.
Networking changes also require `TestGolfTransport` and the actual two-JVM
`TestGolfOnlineIntegration`; matching text on two screens alone does not prove
that readiness or scoring is synchronized. Golf protocol 6 includes handoff
state and the authoritative reverse rate, and explicitly refuses peers built
against a different protocol. The
[replay recipe](../../docs/modding/content-mods.md#replay-a-view-and-its-audio)
shows the minimal engine API pattern without golf's score or turn rules.

Use instance-owned state. Capture every field that affects subsequent frames
in the controller's `State`; dispose owned presenters and sockets in `close`.
The SDK rejects mutable static creator state and validates the same jar that
users install. Do not package ROM bytes or use disassembly assets at runtime.
The current package also reports ten `NON_API_ENGINE_REFERENCE` warnings.
Those come from the session service facade, the ROM spring/contact helpers and
the native Sonic 2 title decoder/mappings. They have no compatibility promise;
this source-first example must be rebuilt against its matching checkout.
Use the model, UI timeline, transport and `CourseControl` reading paths as the
reusable starting points. Keep a native object or title-art bridge isolated when
your mod needs one, and review these warnings when upgrading the engine rather
than treating an internal class as a supported API. The
[SDK troubleshooting guide](../../docs/modding/troubleshooting.md) explains the distinction.
For a smaller starting point, follow the examples in the
[creator handbook](../../docs/modding/index.md).

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


## Use matching creator artifacts

The mutable 0.7 Mod API is unpublished. See [candidate setup](../../docs/modding/getting-started.md) for Java 21 and matching engine/SDK jar paths. From this checkout the shared launcher supports artifact-only builds and explicit ROM paths:

```sh
python3 examples/build_example.py putt-putt-paradise --engine /absolute/engine.jar --sdk /absolute/sdk.jar --run --s3k /absolute/own-s3k.gen
```

Use `--s1`, `--s2`, or `--s3k` for the games this example consumes. Explicit paths create isolated development configuration and saves; no ROM is copied or linked. The creator kit exports this example with a portable POM and `tools/build_project.py`; it needs no engine source checkout. Only production sources/resources enter the validated mod jar. Read [recipient installation](../../docs/modding/installing-mods.md) before sharing.
