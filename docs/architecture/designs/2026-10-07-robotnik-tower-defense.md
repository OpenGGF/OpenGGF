# Robotnik Tower Defence: Industrial Action

## Brief and design

Build a playable OpenGGF mod in which Robotnik defends his base door from
unionised Flickies. The player places, upgrades and sells machinery at fixed
sites, repairs the door between waves, and survives a 15-wave campaign.

The mod is an S3K startup scene in `examples/robotnik-tower-defense`, using
the existing candidate API and the JVM build. Launch Base scenery, Flickies,
Robotnik and badnik art are decoded from the player's locked-on ROM. No ROM
assets are packaged and no engine gameplay or public API changes are needed.
The scene requires 400 by 224, provides mouse and mapped pad/keyboard controls,
pausing, a help screen and saved records. Stock S3K remains accessible.

The battlefield has ten fixed construction sites, ground and flying approaches,
and one door. Fixed sites keep routes readable and prevent path-blocking traps.
Cleared waves award scrap; repelled birds drop recovered scrap. Birds that reach
the door remain there pecking until repelled, making emergency targeting useful.
Between waves the player can spend scrap repairing the door. Building and
upgrading are also possible during a wave. Selling returns part of the investment.

| Defense | Role |
| --- | --- |
| Snale Blaster | Affordable direct fire against ground and air |
| Monkey Mortar | Ground splash damage against packed pickets |
| Buggernaut | Fast anti-air fire |
| Turbo Spiker | Slow armor-piercing shots |
| Orbinaut | Short-range damage and slowing field |
| Egg Robo | Expensive long-range fire with chain hits |

Each tower has three levels. Flicky roles are picketers, fast couriers, shield
carriers, flyers, organisers whose nearby comrades move faster, and saboteurs
which temporarily jam nearby towers. Roles are distinguished by props, tint and
the wave preview. Every fifth wave combines tactics into industrial action.
Robotnik has a manually triggered emergency knockback bomb with a cooldown.
This is cartoon slapstick: defeated birds retreat rather than die.

## Implementation plan

1. **Rules and tests.** Plain Java `towerdefense.core` owns the catalog, wave
   schedule, battlefield and deterministic tick simulation. Test purchases,
   upgrades, refunds, repairs, target eligibility, armor, area/chain damage,
   slow/organiser/jam effects, door pecking, bomb cooldown, wave completion,
   loss and a complete campaign with an ordinary purchasing strategy.
2. **Scene and art.** `TowerDefenseMod` registers the startup scene;
   `TowerDefenseScene` handles screens, input edges, pause and records;
   `BattleView` draws the battlefield and UI; `RomArt` loads cited sprite
   requests. Rendering must never mutate the rules. Keep simulation speed
   independent of draw frequency and pause all gameplay timers.
3. **Production-path verification.** The engine example harness builds,
   packages, validates and registers the mod. Run independent example tests,
   real-ROM scene input tests, every-wave/terminal-state draws and captures
   through `ExampleModCapture`. Exercise both mouse and mapped keys and check
   the owner fault boundary. Records parsing must tolerate malformed saves.
4. **Delivery.** Document controls/build/install and the new experimental mod,
   inspect the combined category plan against the pinned integration base,
   run proportionate or normal selection as its actual impact requires,
   integrate into main `develop`, verify, push only `develop`, then account
   for changes and remove the merged task worktree and local branch.

## Decisions and evidence

- Integration base: `a54dcf56f8237e18cee69e867af39fef4bf4a7d7`; main was
  fetched and fast-forward pulled before worktree creation.
- Reuse scenes instead of changing level physics: the scene already supplies
  fixed-rate input, ROM sprites, backgrounds, audio, storage and fault isolation.
- Reuse one locked-on ROM rather than mixing S1/S2/S3 art requirements.
- Fixed construction sites instead of player-defined mazes: pathfinding and
  blocked-route handling add complexity without serving the base-door siege.
- UI follows Slay the Robotnik's original small font, blue beveled panels,
  gold focus, gradient buttons and outlined two-tone logo, as requested.
- Balance probe rejected weak shields: ordinary Blasters alone initially won
  with an untouched door. Shield carriers now block 90% of direct fire after
  armor; piercing and mortar splash bypass that protection. The same normal
  buying strategies now give mixed defenses a 15-wave win, Blasters alone a
  wave-9 loss, and mortars alone a wave-8 loss. These checks establish role
  differentiation, not human-certified difficulty.
- The repository's authorization and delivery rules override the generic
  planning skill's extra approval stages. The user selected a playable mod.

## Validation

- Java: Maven reports OpenJDK 21.0.12.1. Tool preflight passed with
  `LUA_BIN=/usr/bin/lua5.4`; the default `lua` does not satisfy the 5.4 guard.
- Verified the existing main-workspace `s3k.gen` identity (passed by absolute path):
  CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
- Initial focused checks: 17 rules/records JUnit tests pass, with zero skips,
  including campaign determinism, mixed-build victory, shield counter pressure,
  effects and atomic economy actions. A boundary regression was first observed
  at `190.338` vs `190.4563`: aura membership read mutated organiser positions.
  Computing every membership before movement removes that ordering dependency.
- The completed change plan selects the full 2,995-class ordinary suite because
  `examples/robotnik-tower-defense` is unclassified. Proportionate validation is
  chosen under repository policy: production changes are confined to this mod,
  with no engine source, public API, physics, build or selection-policy changes.
  Its full rules tests, packaging/validator path, scene-host compatibility,
  real-ROM input integration and rendered captures exercise the actual consumers.
  This is focused validation, not a full engine-suite claim.
- Baseline at the pinned base, using unchanged production code:
  `python3 tools/testing/maven_queue.py -B -q -Dmse=off
  -Dtest=TestModSceneHost,TestSlayTheRobotnikExample test`: 141 tests pass,
  zero failures/errors/skips. Expected fault-isolation tests log caught faults.
- Candidate command used the same queue wrapper with `test`, the absolute
  `-Ds3k.rom.path`, and
  `-Dtest=TestRobotnikTowerDefenseExample,TestRobotnikTowerDefenseScene,TestModSceneHost,TestSlayTheRobotnikExample,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
  All 201 existing tests passed; the 23 mod checks exposed one saved-record
  regression: a debug-wave clear polluted memory and a later normal-game save
  (`200,1,0` instead of `0,0,0`). Guarding both record mutation and writing fixes
  it. Re-running the two mod classes passes all 23, zero skips, after the fix
  and final art changes. The example class includes 17 independent rules tests.
- `python3 examples/build_example.py robotnik-tower-defense --skip-engine`
  packages and validates the mod successfully. The 39,920-byte jar contains
  15 classes, the manifest and original text font, with no ROM asset files.
- `ExampleModCapture`, using the same verified ROM and this worktree's compiled
  classes, rendered the title, both help pages, wave 8, pause, win and loss.
  Captures exposed and corrected two presentation errors: backdrop top `0x100`
  showed a blank lower area (use Launch Base row 20 as the existing mod does),
  and Egg Robo mapping frame 0 is empty (assemble body 1/3, arm 2, legs 5 as
  `ChildObjDat_919D0` specifies). Snale cannons/hatch and Spiker shell also use
  their child frames. All generated PNGs were shared with the user during work.
- Durable captures are outside the checkout in the task's
  `robotnik-tower-defense/` capture directory. The normal-input
  `playthrough/inputs.txt` reproduces `siege.mp4`: 1,501 frames, 800×448,
  25.0167 seconds. Stereo 48 kHz audio has AC RMS about 1,507, DC mean 166 and
  peak 9,501; it is neither silent nor constant DC. No native audio parity is
  claimed. Debug captures never contribute to saved records.
- The user explicitly authorized cancelling the older main-workspace full
  validation in `TestLrzWideActTwoColdRouteCapture`. SIGINT to its category
  runner stopped Maven/Surefire and released the queue normally. That run is
  incomplete and supplies no full-suite result for this task. No queue locks
  were removed. No category `--run` diagnostics were produced by this task.
- Shell syntax and Markdown links pass. Human difficulty tuning remains open;
  the deterministic purchasing strategy proves a complete reachable win loop.
  Integration verification and delivery commit references follow below.
