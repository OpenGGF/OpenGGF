# Sonic Survivors: a bounce-driven survivors roguelike for Sonic 2

Task: create a Vampire-Survivors-style roguelike mod for Sonic 2 on a new branch in the main
checkout without worktrees: badniks with hitpoints, weapons alongside stomping, Sonic starting
with only his jump, levels that cannot be completed the stock way, every zone and act reachable,
time-based completion and longer-term upgrades from rings or emeralds.
Base: `eaceceda44` (`develop`). Branch: `feature/ai-sonic-survivors`.
User decisions up front: bounded, zone-themed arenas whose core is "keep bouncing on enemies to
keep going"; survive-the-clock acts chained into a run; auto-firing weapons; mod-only first,
with small general engine hooks only when blocked. Mid-task the user added that pickup weapons
were optional and the roguelike could be built around bouncing, so weapons became mostly
bounce-triggered.

## Implementation

The independent source project is [examples/sonic-survivors](../../../examples/sonic-survivors/README.md);
its README is the player-facing and structural description. Design choices:

- **Arenas from the stock acts.** Each route act keeps its own terrain, art, animated tiles and
  music; the patch replaces the act's objects and rings with one controller and holds the
  camera bounds (and therefore Sonic's level boundary) to a window with continuous, pit-free
  floor. S2 ordinary play lets Sonic run `$40` px past `Camera_Max_X_pos + $128`, so the camera
  limit sits 384 px inside the right wall; the engine's widescreen arena mask then fills the
  space beyond the wall with static, which doubles as the visible wall.
- **Bounce first.** Stomps rebound Sonic upward every time (the kill path sets `y_vel` positive
  so the engine's `Touch_KillEnemy` negation turns it into the rebound; survivors are rebounded
  directly), every airborne rebound chains the combo, and the weapon catalogue is mostly
  triggered by rebounds. Fever (stock invincibility every tenth chained bounce) and ring
  showers for long chains reward staying airborne.
- **Rings are health and experience,** so the ROM's own economy carries the roguelike: ring
  tolls replace knockback, dropped rings level Sonic up.
- **Route with act choice.** One act per zone keeps a run near 25 minutes; choosing act 2/3 is a
  risk-for-rings choice, and every act of every route zone is reachable.
- **Meta-progression** splits into a ring-bank shop (incremental) and one Chaos Emerald per
  boss (seven distinct permanent powers, a set bonus for all seven), with the title's zone
  picker unlocking later starts that grant catch-up level-ups.
- Every badnik and boss is drawn with ROM art the zone's PLCs already load (the S2 object art
  provider registers each zone's badnik and boss sheets at zone load); frames were chosen from
  contact sheets. The Death Egg has no badnik art, so it is a boss stage: Silver Sonic, then a
  fleeing Eggman drawn with `dez_eggman`.

## Engine and tooling additions

- `HeadlessGameBoot` on macOS requests the 4.1 core profile `Engine` uses and skips the
  fixed-function matrix calls: a 2.1 request gives a legacy context that cannot compile the
  `#version 410` shaders, so headless captures failed on macOS.
- `HeadlessGameBoot.setModuleDecorator`, `DevelopmentPatchLoader` and
  `GameplayCaptureTool --mod <jar>` apply a packaged patch mod to a headless capture the way the
  launcher applies an enabled trusted mod; without it a mod could not be filmed.
- Two opt-in probes: `FloorSegmentSurveyProbe` (continuous floor paths per act) and
  `ObjectArtContactSheetProbe` (labelled PNGs of every registered sheet's frames).

## Rejected approaches and evidence

- **Walking the floor from the act start** to find arena windows: every S2 act stopped within a
  few hundred pixels at a wall, loop, bridge gap or steep step (EHZ1 1344, CPZ1 384, HTZ1 384).
  Linking per-column floor surfaces across the whole act found 750-3300 px paths in every act.
- **Arena windows taken straight from the survey:** HTZ1 (9420-10510) and MCZ2 from 3240 had
  slopes Sonic cannot climb from a standstill, and MCZ1's right edge met a pit once the S2 `$40`
  extension was accounted for. The final windows were checked by holding right and left across
  every arena in `TestSonicSurvivors` and by captures.
- **Packing hitpoints, projectile art and ring values into the spawn subtype:** `ObjectSpawn`
  masks the subtype to a byte, so every badnik silently had 1 hitpoint and every shot used the
  fallback art until a test exposed it. Initial values now go through constructors into fields
  (rewind restores fields); the subtype carries only species, elite, kind and stage.
- **A no-op level-event provider:** Hill Top's scroll handler requires the zone runtime state
  the stock event manager installs in `initLevel`. The arena runs the stock `initLevel` and no
  per-frame events instead.
- **Menu confirm from the held jump bit alone:** a dead sprite's held bit can stay latched, so
  menus also accept the engine's own just-pressed edge.
- **HUD in the far right of the screen:** at the right wall the arena mask (drawn after world
  objects) covers the last 16 px, so the HUD keeps a 26 px right margin.
- **Pickup weapons as a separate economy** (the original plan) were folded into rings and the
  card deal after the user's mid-task note; monitors remain as elite drops.

## Validation and limits

`TestSonicSurvivors` (28 tests) packages the mod through `ggfmod` and covers camp, the arena
walls, all 19 route acts (Sonic crosses each arena both ways without dying or falling), stomps
and the combo, level-up cards and the whole arsenal at once, boss, emerald and route choice,
the Death Egg finale, death and banking, the shop, ring tolls and a rewind round trip.
Captures with `GameplayCaptureTool --mod` covered every zone, the camp, title, level-up cards,
zone clear, route transitions through Emerald Hill, Chemical Plant and Aquatic Ruin, game over,
Tails as sidekick and the Death Egg victory. Not covered: live play with a controller,
difficulty balance beyond scripted-bot runs, audio, and the native build (which cannot load
code mods).

## Follow-ups after the first commit

- **Boss reach (`63cc5918bd`).** Eggman first hovered 88 px above the first surface found
  below him; in several arenas that was a high ledge, leaving him at the top of the screen
  and out of jump range from the floor Sonic stood on. He now hovers relative to the ground
  Sonic last stood on and eases toward it. Hill Top's tank rolls on the ground and charges.
  Metropolis act 3 is its own ROM zone and registers no MTZ boss sheet (a headless check of
  every act's boss key found it the only one), so its boss is a code-drawn armoured core
  ringed by the zone's Asterons.
- **Ring formations (`48351f6999`).** Rings came only from kills, so roaming the arena found
  nothing. Every ten seconds of the survival phase five floating rings now appear at least
  120 px from Sonic, above the floor at that point.
- The mod jar is installed in the local `mods/` folder (trusted, enabled) next to Infinite
  Sonic for an IntelliJ launch; `modstate.json` is local state, not part of the branch.
