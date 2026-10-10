# Three Islands: combat depth, equipment and optional content

Task: a broad polish and content pass over the Three Islands creator mod, with freedom to
change existing design. Branch `feature/ai-three-islands` in the main checkout, base
`78ad7aea74`. No engine, Mod API, stock gameplay, physics or audio-driver code changed.

## What changed and why

Review started from native `ExampleModCapture` frames and the creator balance simulation.
Three problems stood out: battles reused a walkable-ground search that stacked heroes on
top of each other and of bosses, with field labels (for example "Bridge winch") showing
through; bosses differed only in stats and a shared charge-up; and late-game fights against
a full trio ended in 2.0 rounds on average.

- **Statuses.** Fire, Water and Lightning hits can burn (damage at the owner's turn start),
  soak (two-thirds speed) or stun (lose the next turn). Bosses cannot be stunned; guarding
  halves the chance; healing cures; an existing status is not re-rolled.
- **Boss signatures** (`EnemyKind.signature`): summons (Bomb King, Coconuts Chief, the
  Convergence Engine's Egg Robos), bomb fuses, half-health power-ups with dialogue (Eggman,
  Knuckles, golden double-acting Mecha Sonic), the Grabber Queen's grip, Silver Sonic's
  rebounding spikes, Flame Craft napalm, Screw Mobile depth charges, a tech-only barrier and a
  cycling core weakness. Signature turns fall at `turnsTaken % 4 == 2`; charge-ups moved
  from every third to every fourth turn so a cycle mixes normal, signature and charge turns.
  Helpers leave when their summoner falls and pay a third of normal rewards.
- **Accessories** (`Gear`): one per hero, stored on `Hero` so every stat reader sees it.
  Saves add `gear=` and `equip.<HERO>=` keys; decoding applies accessories before hero HP so
  bonus HP survives a reload, and saves without these keys load unchanged.
- **Content.** Each authored layout gains a final branch ending in a secret room: a lost
  animal outdoors (Green Hill uses a fixed southern grove spot) and a treasure monitor indoors.
  Puzzle controls now use every alcove except that room, so existing control placement is
  unchanged. Rescues pay rings, with gifts at 3/6/10. Four optional Rift Echoes pair earlier
  bosses after each island's final anchor (Death Egg: after Mecha Sonic) at area level + 3.
- **Puzzles.** A ROTATE rule (quarter-turn dials from a written clue) is used for Emerald
  Hill's interior and the Death Egg's outdoor relays; Chemical Plant's outdoor pumps became an
  ordered sequence and Hydrocity's intakes a circuit.
- **Presentation.** Battles draw the unchanged field camera without landmarks and dimmed,
  with fixed left/right stage positions, depth-sorted fighters, shadows, fuse counters, stance
  rings, status tags and planted charges. Outdoor layouts render as terraces (ROM cliff faces,
  edge shadows). Emerald Hill's floor uses the kit's greenest opaque tile instead of the
  checkerboard cliff, which had made floor and walls indistinguishable.

## Rejected or revised during the task

- Bomb King's first summon tuning (two full-HP helpers, 12-power explosions) lost the
  story-only simulation at Star Light seed 1: a lone level-3 Sonic spent every turn on
  helpers. Helpers now have 40% HP, at most one per hero, explosions use power 10, and the
  summoners' HP fell (Bomb King 700 to 560, Coconuts Chief 700 to 600).
- Flame Craft napalm on every signature turn *plus* a 20% random chance burned a duo down in
  the story-only route. It is now signature-turn only with a 45% burn chance.
- The simulated player's item rule used `battle.events().isEmpty()`, effectively random. It
  now heals itself or a badly hurt ally, guards against a planted charge and hits a fuse at 1.
  This changes the scripted policy, not the game's difficulty.
- Trio ordinary-foe HP scaling went from 260% to 470% after 380% still gave 2.5 rounds.
- Two-boss echoes against a lone Sonic took 22 rounds; echo foes facing one hero use 55% HP.
- A first summon placement reused the summon's slot number, which would stack two living
  helpers in the same spot in a long fight; helpers now take the first free stage position.

## Verification

Creator suite (fast loop: javac plus the Jupiter console against `target/test-classpath.txt`):
75 tests, zero failures. Balance results on the final code: ordinary fights 60/60 in every
zone at 2.9-4.4 rounds; all twelve bosses 60/60 at 4.6-8.3 rounds; echoes 40/40, 38/40, 20/40
and 20/40 at 7-12 rounds. These measure the scripted policy, not human difficulty.

The change-based plan against `78ad7aea74` selects the full ordinary suite because
`examples/` is unclassified. Proportionate focused validation applies: only the creator mod
and its engine test bridge changed. Final queued command (Java 21, absolute root ROMs):

```sh
python3 tools/testing/maven_queue.py -B -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

Result: 14 tests, zero failures or skips; 10 of 10 zones and 10 of 10 interiors exercised,
including the dial puzzles through real interaction input. `build.py --skip-engine` reported
zero validator findings. Native captures were inspected for boss, ordinary and Bomb King
battles (summon, fuse and burn), the shrine treasure room, a lost animal, the shop's accessory
tab, the Equip preview, Emerald Hill and Chemical Plant terraces and a Launch Base echo. Not
covered: the full engine suite, a human playthrough at real speed and real-device audio.

## Follow-up: authored Green Hill and Star Light maps

User feedback on the above: Green Hill was one large square with landmarks dotted around and
every patrol avoidable; Star Light looked poor and had invisible collisions that made it
impossible to finish; Green Hill to Star Light should take at least five to ten minutes.

**Cause of the Star Light walls.** Its dungeon entrance sat on route room (2,1). The facade's
solid footprint (door x ±80, y -120..-12) covered the corridor from the north and the exit to
the east, leaving 8-pixel slivers at the room's edges. Nothing was drawn there, so the party
appeared to hit invisible walls. Room-graph placement could not account for the facade.

**Replacement.** Both areas are now `TileMap`s: 80 x 44 (Green Hill) and 80 x 42 (Star Light)
32-pixel cells, authored with drawing primitives in `examples/three-islands/tools/generate_maps.py`.
The script checks markers sit on ground and that the exit cannot be reached without fighting,
then emits `Maps.java` text-block constants (static `String` constants pass the validator).
The facade has explicit `H` cells, so drawing and collision agree. `Field` builds spots from
the map legend and keeps every existing spot id, so saves, puzzles, journal entries and story
triggers carry over; Star Light's `route` lock became the map's `R` gate.

- **Forced encounters.** `BLOCK` legend entries are stationary encounters in one-cell passes,
  solid within `Field.BLOCK` (17 px, a full cell) with a contact range of 26 px. A first 14-px
  box let the 8-px flood fill slip along the cell edge; the cell-wide box fixed it. Fleeing
  retreats along the party's own trail beyond contact range, so the grace period cannot be
  used to walk past. Avoidable `PATROL`s remain in clearings.
- **Length.** A first Green Hill draft needed 143 cells to reach the exit. Moving the river
  crossing downstream with a far-bank switchback, and adding a switchback to the relay,
  raised the measured required route (village, shrine, relay, anchor) to 172 cells. Star
  Light's (both feeders, observatory, relay, anchor) is 212. With six forced fights per area,
  their dungeons and scenes, each area should take several minutes; that is an estimate from
  route length and battle counts, not a timed human playthrough.
- **Navigation.** Single-cell trails need corner assist: pressing into a wall slides up to
  14 px toward an opening. A waypoint Starpost (checkpoint value 3) sits mid-route in each
  map; values 1 and 2 keep their meaning for older saves.
- **Art.** `MapArt` draws Green Hill clearings, dirt trails (palette browns `$B66D49`/`$924900`
  after the first nearest-colour pick came out pink), a jungle plateau with south-facing
  checker cliffs, animated water, `Nem_Bridge` logs (palette line 2) and `Nem_PplRock`
  (line 3), with palms only where their crowns stay over plateau (an earlier rule let canopies
  cover walkable ground). Star Light draws its starfield backdrop, chunk 17's city lights at
  half parallax, plating, rails (chunk 1 y 224), lamps (chunk 1), lattice and cones (chunk 20),
  barriers (chunk 24) and rooftops (chunk 2), with lamplight pools. Props (palms, rocks, lamps)
  are depth-sorted with walkers. Crop coordinates came from temporary native block views,
  removed after use; markers draw the trail, bridge or grass they interrupt.

Verification: 82 creator tests (new `MapTest`: blockers bar the relay, each blocker cuts a
real pass, every other landmark is reachable once they fall, the Star Light gate needs both
feeders, markers and Starposts stand on ground, waypoint resume, corner sliding, required
route lengths; rewritten Green Hill and field tests now use spot ids rather than old
coordinates). The same queued `TestThreeIslandsExample,TestThreeIslandsScene` command with the
three root ROMs passed 14 tests with no skips, 10 of 10 zones and interiors exercised; the
Green Hill mystery scene test now positions by spot id. Zero validator findings. Native
captures covered both maps at a dozen points, a keyboard walk into Green Hill's first blocker
and its battle, the log bridge, orchard island and switchbacks. Not covered: a timed human
playthrough, the full engine suite.

## Native background follow-up

Base: `1ede498795f92af831bbd08cae3a50f621f83c49`, current checkout
`feature/ai-three-islands`. The previous viewport-only fix in `1ede498795` removed the
88-pixel world strip but preserved the wrong data model: S1/S2 kits expose one quarter-speed
band; EHZ has unused layout rows below its fixed native view; DEZ1 has blank right-hand
layout padding; Star Light applied an additional 0.3 multiplier. Clamping a generic 0.15
vertical pan could not reproduce the native games. That approach is replaced, not tuned.

`SceneRomArt.levelBackground` now delegates to game-owned detached background profiles.
They instantiate the existing `SwScrlSyz`, `SwScrlSlz`, `SwScrlEhz`, `SwScrlCpz`,
`SwScrlMcz`, `SwScrlAiz`, `SwScrlHcz`, `SwScrlLbz` and `SwScrlS3kDez` handlers.
MCZ's shake input is injected; AIZ/LBZ detached constructors suppress ambient event reads.
Native gameplay retains the existing default behavior. `DetachedBackground` retains native
scroll words (including EHZ's shipped two unwritten lines) and each view owns its own state.
The host batches adjacent equal scroll lines into unscaled image regions, wrapping rows and
columns inside the populated ROM art. AIZ includes the forest rows below the postcard crop.
DEZ1 shares its extracted native reflected-wall sampler, preserving the central 320 pixels.
The mod removes its generic vertical pan, secondary Star Light multiplier and synthetic
city overlay. It still composes its authored terrain and battles above the background.

This reuses native scrolling, not a second mod-side implementation of the ROM routines.
The disassembly was consulted as supporting provenance, but there is no new copied scroll
algorithm. Background pictures retain their existing detached initial-art/palette state;
live zone events, art animation and underwater palette transitions are not simulated.

Verification: ROM-backed `TestRomSceneBackground` checks all nine exposed profiles at 320
and 400 pixels across five camera positions, comparing every output pixel to the native
scroll offsets and requiring complete, non-overlapping viewport coverage. It poisons
`GameServices` during build and draw to enforce isolation, and checks repeated-draw identity.
The existing mod, API signature, S3K bootstrap/loading and native DEZ widescreen checks
cover integration.

Validation on the above base plus this uncommitted follow-up:

- Queued focused ROM tests (`TestRomSceneBackground,TestThreeIslandsExample,TestS3kDezWidescreenBackground`)
  passed 15 outer tests and 83 creator checks, with no skips. An earlier focused selection
  also passed the S3K bootstrap/loading/decoding and AIZ headless obligations, API signatures,
  and the all-zones/all-bosses Three Islands route (75 outer tests, no skips).
  Both commands supplied the three absolute root ROM paths; no ROM aliases were created.
- An independent temporary probe compared all nine registered profiles with separately
  instantiated stock scroll handlers over 720 moving-camera frames each: 1,451,520 scanlines
  matched. The committed renderer tests additionally cover the image wrapping/composition.
- Actual Three Islands GL captures were reviewed for the eight terrace areas; independent
  renderer checks cover southern cameras even where a scripted party teleport was blocked.
  Normal-engine `GameplayCaptureTool` views corroborated CPZ at camera (400,816), EHZ's
  fixed sky, and the 400-pixel DEZ interior. CPZ's white lower factory is real scenery,
  not an empty gap. A high-Y EHZ comparison was discarded because foreground hid its sky.
  These are engine comparisons, not a claim of BizHawk/ROM pixel parity or live art animation.
- Java/Lua/PowerShell preflight passed. The unmodified change-based selection was 3,079
  classes plus guards. `run_categories.py --base 1ede498795f92af831bbd08cae3a50f621f83c49 --run`
  timed out after 40 minutes: 2,390 reports / 19,503 tests, two failures, five errors,
  151 skips. This is incomplete validation, not a full-suite pass. Skip reasons included
  hard-coded ROM aliases, absent lock-on data, unsupported graphics contexts, opt-in
  diagnostics, and route assumptions. The runner diagnostics were inspected and acknowledged.
- Three errors were caused by an added descriptor comment: `ModApiReleasePolicy` accepts
  only exact key=value lines. Removing it restores the unchanged candidate descriptor.
  Queued `TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModApiSignatureSurface`
  then passed 27 tests with no skips. The additive signature pin is retained; no version
  or publication status changed.
- Remaining broad-run failures/errors were S3K data-select GLSL 4.10 unsupported, two
  sample-character `base64` command failures on macOS, and a network-test connection reset.
  No matched baseline run was performed: these remain unattributed and were not repaired
  as part of this rendering task. The full ordinary run was not repeated.
- `build.py --skip-engine` validated the mod with zero findings; queued
  `-Dmse=off -DskipTests package` built the matching engine. The installed
  `mods/three-islands.jar` bytes, enabled/trusted SHA-256, and `SceneBackground` class in
  the packaged engine were checked. Restart the JVM to load the new engine and mod.

- Separate queued `-Dmse=off -Pguards test -B` completed: 674 tests, one failure,
  zero errors/skips. The failure is `TestBuildToolingGuard` invoking
  `test_release_trace_collection.CollectTests.test_failure_messages_and_report_payloads_have_checkout_paths_normalized`.
  A bounded matched check ran that exact Python test against `tools/testing` archived from
  the base commit and against the current checkout; both failed identically on macOS
  `/var/folders` versus resolved checkout-path normalization. This is a confirmed inherited
  failure; no tooling fix was made. All other 673 guards passed.
