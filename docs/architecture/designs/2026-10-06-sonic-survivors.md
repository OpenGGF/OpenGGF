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


## V2 — solo roster, modal input and readable encounters

Base `be1b3982c271df5803791864c7a7673facf0b137`; isolated worktree
`.worktrees/sonic-survivors-v2`, branch `feature/ai-sonic-survivors-v2`.
Mod version 0.2.0 retains the profile format. Sonic and Tails activate the same patch;
`supportsSidekick=false` and per-zone suppression enforce solo play. The launch screen's
existing character picker supplies the leader; no mid-run character swap is introduced.

The physical ceiling uses the camera's existing minimum Y plus 24 pixels of player clearance.
An out-of-bounds player is moved through `NativePositionOps`, upward velocity is stopped,
and airborne state is restored so terrain above the arena cannot hold them grounded.

`LevelInputOverlay` is the one general host addition: module input is dispatched before the
host's pause toggle and native Pause_Loop admission. Modal camp/cards/results own Enter and Start; ordinary gameplay releases
them. Card opening now freezes the player and mod objects immediately and skips selection on
the opening frame. Jump is exclusively gameplay. The host-pause regression belongs in the
ROM-backed mod fixture: a bare `TestGameLoop` LEVEL setup lacks both a loaded level and a
focused camera target, and cannot establish this interaction.

Lost rings carry explicit rewind-captured provenance. Both magnets reject them, reward-pile
merging excludes them, and collection restores health without creating new XP or ring-bank
credit. They expire at 300 gameplay frames with a 60-frame blink, bounding lingering spills.
This closes the repeated-damage XP farming loop without changing reward rings.

Elites use 1.5x ROM mapping tiles through the existing scaled pattern renderer, larger touch
and weapon hit regions, scaled ground clearance, and a labelled gold bar. No extracted art
or new runtime asset is introduced. Boss component selection follows the stock Java owners:
EHZ vehicle bottom + top (top palette 0), CPZ eggpod + Robotnik (face palette 0), CNZ electrode,
body, propeller and generator, MCZ paired drills/body/face/exhaust, MTZ pod/body/face, and HTZ
body/eyes. Whisp Queen (ARZ) and Balkiry Ace (WFZ) use complete 2x badnik mappings; Oil Ocean
uses the existing armoured-core drawing with native escorts. Queen volleys spread, the Ace
commits to a strafe, and other flying bosses telegraph dives. Flying hover targets keep 72 pixels above the vehicle origin clear for its full silhouette and HUD.
Patrol clears leftover vertical
dive velocity; without that reset a previous attack could drag the next patrol downward.

### V2 affected act/character matrix

All rows use native S2 movement, the enforced 400x224 viewport and solo Sonic/Tails. Each act
runs both-way real-physics traversal in `everyRouteActIsAWalledArenaSonicCanCross`, including
start/camp, terrain, side walls and survival without falling. This is a mod-arena matrix,
not certification of the stock acts. Other viewport requests resolve to the mod's 16:9 policy.

| Zone / act | Sonic | Tails | Boss presentation / changed obligations |
| --- | --- | --- | --- |
| EHZ 1 | traversal | traversal | vehicle + face; Enter host integration; lost-ring rewind |
| EHZ 2 | traversal | traversal | vehicle + face |
| CPZ 1 | traversal | traversal | eggpod + face |
| CPZ 2 | traversal | traversal | eggpod + face |
| ARZ 1 | traversal + ceiling | traversal + ceiling | Whisp Queen; configured follower rejected |
| ARZ 2 | traversal | traversal | Whisp Queen |
| CNZ 1 | traversal | traversal | electrode/body/propeller/generator |
| CNZ 2 | traversal | traversal | electrode/body/propeller/generator |
| HTZ 1 | traversal | traversal | tank eyes |
| HTZ 2 | traversal | traversal | tank eyes |
| MCZ 1 | traversal | traversal | complete drill vehicle |
| MCZ 2 | traversal | traversal | complete drill vehicle |
| OOZ 1 | traversal | traversal | Oil Sentinel |
| OOZ 2 | traversal | traversal | Oil Sentinel |
| MTZ 1 | traversal | traversal | pod/body/face |
| MTZ 2 | traversal | traversal | pod/body/face |
| MTZ 3 | traversal | traversal | inherited Asteron core |
| WFZ 1 | traversal | traversal | Balkiry Ace |
| DEZ 1 | traversal | traversal | inherited Silver Sonic + fleeing Eggman |

Independent short regressions cover opening-frame menu freeze and jump rejection,
Enter host pause ownership, ARZ ceiling recovery for both leaders, absence of configured
followers, and lost-ring magnet/XP semantics through snapshot restore. Existing checks cover
boss defeat/emerald/next-act handoff, death/camp, the shop, ring tolls, the DEZ finale, and
fight capture/restore plus identical forward replay. Inherited gaps: donor combinations,
full-run balance with human input, per-act boss attack/defeat rewind spots, Tails-specific
full campaign progression, and audio listening. Native captures supplement state tests;
they do not establish ROM parity for these intentionally custom combat rules.

### V2 validation evidence

- `maven_queue.py -B -Dmse=off -Dtest=TestSonicSurvivors,TestGameLoop test` with the
  absolute root S2 ROM: 152 checks passed, zero skipped (55 Survivors, 97 host checks).
  The rendering-only label/bar adjustment followed this focused run and is in the final
  change-based run. `TestModApiSignatureSurface` separately passed all nine checks after
  adding the six normalized signature lines for `LevelInputOverlay`.
- `GameplayCaptureTool --mod target/sonic-survivors/sonic-survivors.jar`, native OpenGL,
  400x224, seed 123, survival timer shortened to three seconds: all nine ordinary route
  zones rendered; the changed hover heights were recaptured in MCZ, ARZ and WFZ. ARZ and
  WFZ use Tails and a configured Tails follower; every CSV row has `sk_present=0`.
  The task capture directory is `/private/tmp/sonic-survivors-v2-20261006` outside the repo.
  `preview/capture.mp4` shows 460 frames of the final Tails/WFZ build; frame 540 shows the
  Balkiry Ace. `elite-final/elite.png` is a controlled comparison of normal and elite
  Buzzers spawned through the actual mod controller, not traversal evidence.
- The first broad run was interrupted to finish the remapped-Start and hover-height fixes.
  It produced no completed suite result; its incomplete diagnostics were inspected and
  acknowledged.

- Final change-based invocation: `JAVA_HOME=<JDK21> LUA_BIN=/opt/homebrew/bin/lua5.4
  python3 tools/testing/run_categories.py --base be1b3982c271df5803791864c7a7673facf0b137 --run`,
  worktree `.worktrees/sonic-survivors-v2`, implementation committed as `2e2fc0c9c9`.
  Run `20261006T184137Z-2935d039` selected all 2,969 ordinary classes plus guards.
  It reached the 40-minute limit during ordinary tests: 2,528 reports, 21,834 checks,
  two failures, four errors, 151 skips. This is **incomplete validation**, not a full
  suite pass. All 55 Survivors cases passed with no skips. Long checkpoint routes and
  repository-policy checks consumed substantial runtime; guards were queued separately.
- Three policy errors came from an unsupported comment added to the strict release
  descriptor. The comment was removed before `2e2fc0c9c9`; the descriptor remains at its
  unchanged mutable 0.7 candidate. Focused `TestModApiReleasePolicy,TestModApiRuntimePolicy,
  TestModApiPinPolicy` then passed 18 checks with no skips.
- Matched single-test baseline (`be1b3982c2`, main checkout) and candidate runs reproduced
  the macOS GLSL 410 compilation error in
  `TestS3kDataSelectPresentation#visualCapture_selectedSaveSlotShowsRightBodyRail`, the
  macOS `base64` argument failure in `TestPhase3SampleCharacterIntegration`, and the
  `instaShieldRegistered` restore mismatch in `TestFbzSandopolisTimelineHeadless`.
  Commands used `maven_queue.py -B -Dmse=off -Dtest=<case> test` with absolute root ROM
  properties. These unrelated failures were left unchanged; no broad baseline rerun.
- Skips were inspected: hardcoded `s2.gen`/`s1.gen` and other ROM assumptions despite
  absolute configured paths, missing optional KiS2 dump, inapplicable platform routes,
  unavailable graphics contexts, and explicitly opt-in diagnostics/soaks. No ROM aliases
  were created to conceal those coverage gaps.
- Installing the v2 mod before updating the user's IntelliJ engine produced
  `NoClassDefFoundError: LevelInputOverlay`. The main checkout was then fast-forwarded
  to the matching engine code and rebuilt; an old running JVM must be restarted.

- Separate `maven_queue.py -B -Dmse=off -Pguards test` on `2e2fc0c9c9` completed
  672 checks with five failures, one error, zero skips. Two findings were addressed in
  the main checkout: use the active world's module for modal input, and extract pause,
  frame-step and takeover decisions into `GameLoopPauseInput` to meet the GameLoop size
  ratchet. The extraction preserves host audio and playback-takeover behavior.
  Remaining guard findings are explicitly unattributed: the LRZ flame object's touch
  profile hook, S1 title background pattern-range constant, release-trace path
  normalization Python test, and the object-clock tooling subprocess timing out after
  120 seconds (`Stream closed`). These paths were not changed to make the suite green.

- Final main-checkout focused run after extraction: `maven_queue.py -B -Dmse=off
  -Dtest=TestSonicSurvivors,TestGameLoop,TestGameLoopFreshLevelHandoff,TestBootstrapModuleProviderCachingGuard,TestArchitecturalSourceGuard
  -Dsonic2.rom.path=<absolute-root-S2> test`: 235 checks, zero failures/errors, four
  S3K handoff checks skipped because that invocation only supplied S2. Thus 231 passed,
  including all 55 Survivors cases and both repaired structural guards. The handoff
  cases were subsequently rerun with their required S3K property: all four passed,
  zero skipped. The combined final focused coverage is 235 passing checks; this does
  not supersede the incomplete broad run or unresolved unrelated guard findings.


## Longer modes and quieter pickups (2026-10-06)

The existing Survivors branch was reconciled with destination `develop` at `2d91ef756e`
and pushed as merge `a54dcf56f8` before this follow-up. The checkout stayed on the personal
feature branch. Conflicts combined documentation additions and the API version comment;
the shared engine merged automatically. Focused merge verification used queued Maven with
`TestSonicSurvivors,TestGameLoop,TestGameLoopFreshLevelHandoff,TestModApiSignatureSurface,
TestGameplayCaptureToolArgs,TestBootstrapModuleProviderCachingGuard,TestArchitecturalSourceGuard`
and absolute root S2/S3K ROM paths: 250 checks passed, zero skipped.

The user selected both five-minute and endless modes. The camp MODE row cycles through
2 minutes, 5 minutes and endless, after any first boss clear. Existing route unlocks,
emeralds or wins establish that unlock without a profile migration; the persisted integer
mode defaults to standard if absent/invalid and cannot bypass the unlock. A run copies the
mode at its start and captures it for rewind, so later profile changes do not alter it.
Five-minute waves stretch the normal difficulty ramp, with the existing real-time ring and
elite cadence. Endless counts elapsed active gameplay, never schedules the stage boss,
retains enemy-count/spawn-rate caps, and uses existing death/retirement banking. It does not
award a clear or emerald for elapsed time. The Death Egg remains a direct boss finale.

Pickup audio grouping lives in the mod's run state, shared by reward piles, lost rings and
Super Ring monitors. A pickup after 30 gameplay frames without a chime sounds immediately;
otherwise a ten-ring batch needs a 12-frame minimum interval. Large merged pickups emit at
most once and do not schedule a backlog of sounds. Both the batch and last chime frame are
rewind state, reset on new runs. Health, XP, bank credit and the host audio API are unchanged.
A simple every-tenth-pickup counter was rejected because merged piles have different ring
values and isolated rings would often give no feedback.

The mod act/character matrix above inherits its prior gaps. Added checks focus on saved
mode/unlock compatibility, camp navigation, the timed boss boundary, endless pause/rewind
and retirement, the Death Egg exception, and actual audio request counts with full rewards.
Human difficulty balance and audio listening remain separate from deterministic checks.


Focused follow-up verification: `maven_queue.py -B -Dmse=off -Dtest=TestSonicSurvivors
-Dsonic2.rom.path=<absolute-root-S2> test` exercised 62 cases. Sixty passed immediately;
two new Death Egg cases had used the raw ROM zone index 14 instead of registry index 10.
After correcting that test setup, the matched two-case rerun passed without skips.
`examples/sonic-survivors/build.py` packaged mod 0.3.0 successfully through `ggfmod`.
`GameplayCaptureTool` at 400x224 with the packaged mod and seed 123 verified fresh locked,
unlocked five-minute, and endless profiles in separate native JVMs. Frame 29 shows camp,
100 the intro, and 240 the active clock; CSV rows confirm live solo Sonic in LEVEL mode.
Artifacts: `/private/tmp/sonic-survivors-modes-20261006`. The 300-frame Start input was
round-tripped by `InputLogAuthorTool`; seeded capture profiles are presentation setup,
not evidence of earning the unlock. Unlock and next-act handoff behavior are covered by tests.


### Combined validation and bounded long-run follow-up

At `1ae1596837`, `JAVA_HOME=<JDK21> LUA_BIN=/opt/homebrew/bin/lua5.4 python3
 tools/testing/run_categories.py --base 2d91ef756e3289080bf463ad3e33773ebe6b7508 --run`
selected 2,993 ordinary classes plus guards in the current checkout. Run
`20261006T212445Z-4f0a25cf` reached its 40-minute timeout during ordinary tests:
2,316 reports, 18,971 checks, one failure, one error, 149 skips. All 62 Survivors
cases passed without skips. The two reported failures repeat the previously baseline-
reproduced macOS GLSL 410 save-select error and sample-character `base64` invocation error
recorded above. Skips include hardcoded ROM names despite configured absolute paths,
optional KiS2 dumps, inapplicable platform routes, opt-in diagnostics and unavailable GL
contexts. This is incomplete validation; guards had not started.

The last class was `TestObjectControlledGravity`. A native sample of its unresponsive JVM
showed the main thread in an AWT `NSApplication.run` loop entered while GLFW polled events;
Java attach also timed out. This shared-JVM stall remains unattributed, not evidence of a
physics failure or an exonerated integration. Diagnostics were inspected and acknowledged.

Review while the source was frozen found two capacity leaks relevant to the new longer
modes: periodic floating ring formations and monitors never expired, and elite/Whisp buddy
spawns bypassed the nominal enemy cap. The follow-up bounds every wave spawn at 34 enemies,
expires uncollected ring/monitor rewards after 3,600 active frames, and blinks them in the
last second. Emeralds stay permanent and lost rings retain their five-second lifetime.
A separate reward-age counter resets when fresh value is merged, keeping newly earned
rewards collectible without restarting movement/animation. Pauses and rewind own that
counter exactly like the other pickup fields.

The follow-up plan still selects all 2,993 classes because example paths fall back to the
full suite. Focused validation is proportionate for this mod-only fix: it changes no host
code or contract, and the full Survivors fixture plus expiry/pause/rewind, population-cap
and 20,000-frame endless checks directly exercise its consumers and failure modes. The
unchanged engine suite is not repeated; its incomplete coverage remains explicit.


The initial capacity-fix check used queued Maven with
`-Dtest=TestSonicSurvivors,TestObjectControlledGravity` and absolute S2/S3K ROM properties:
78 checks passed, zero skipped (66 Survivors and all 12 fresh-JVM capture cases).
Thus the previously stalled class runs successfully with the merged host, but the long
shared-JVM AWT interaction remains unattributed. The guards profile then completed with
`JAVA_HOME=<JDK21> LUA_BIN=/opt/homebrew/bin/lua5.4 maven_queue.py -B -Dmse=off -Pguards test`:
672 checks, one failure, zero errors/skips, in 10:18. The remaining failure is
`TestBuildToolingGuard.releaseGateToolsShouldRejectCorruptAndChangedEvidence`, from
`test_release_trace_collection.CollectTests.test_failure_messages_and_report_payloads_have_checkout_paths_normalized`:
the emitted `/var/folders/.../checkout/x` path did not become `<CHECKOUT>/x`. This is the
previously recorded, still-unattributed tooling finding; that code was left unchanged.
Guard reports were read and deleted. The prior object-clock timeout and other old guard
findings did not recur on this merged tree.

The final mod-only polish defers a scheduled elite while the arena is full, rather than
announcing an enemy that failed to spawn. The same cap test checks that its announcement
appears once a slot opens. Ring grouping also asserts the exact 30-XP result: three earned
levels and five remaining XP. No unchanged host checks are repeated for this mod-only edit.


Final-source verification reran `-Dtest=TestSonicSurvivors` with the absolute S2 ROM:
65 of 66 checks passed; the stronger audio test initially expected six remaining XP,
but the unchanged curve's third level costs 12 (not 11), so the correct remainder is five.
After correcting only that assertion, its single-case queued Maven rerun passed without
skips. All 66 Survivors cases therefore have passing focused coverage on the final behavior,
including the 20,000-frame endless route and deferred elite admission. This is not a full
engine-suite pass. The source build/package script rebuilt mod 0.3.0, and the existing
trusted/enabled local installation was refreshed with its matching hash. Other mod settings
and the user's actual save profile were preserved.


## Escalating hordes and Fever (2026-10-07)

User-directed difficulty/polish pass, base `bfc83b6f8abf4f179c4984f9a40b85c2091ea410`,
current checkout `feature/ai-sonic-survivors`. Mod 0.4.0 keeps the profile format and removes
unreleased version branding. The user explicitly requested uncapped entities.

- Removed the 34-enemy ceiling and native-slot admission checks. `ArenaObjects` constructs
  mod-owned enemies, bosses, shots and pickups with injected services and registers them
  through the existing rewindable auxiliary-object path. Native games and their ROM slot
  budgets are unchanged. Player projectile arrays grow; only cosmetic damage/effect visuals
  retain a bounded pool. Pickups still expire, and each reward uses up to three sprites.
- Thirty-second encounters alternate mixed, aerial and ground compositions, warn three
  seconds before seven-second surges, then allow five seconds of ordinary-spawn recovery.
  Health and batch sizes continue increasing; elite spacing falls toward eight seconds.
  Long mode uses 75% time pressure rather than stretching two minutes over five, with twice
  the timed boss health. The direct Death Egg finale is unchanged.
- Ten eligible bounces charge an eight-second Fever with fifteen seconds of subsequent
  recovery. No charging while invincible or Super. Charge survives a broken combo; attacks
  retain combo damage. Stage-owned counters pause with menus and restore with rewind.
- Hit toll is max(10, ceil(rings/12)), reduced by 8% per Armor level and 15% for the red
  emerald, rounded up with a five-ring floor. One-second hurt recovery replaces two seconds.
  Combo ring bonuses are gentler and chain-end payouts are linear rather than quadratic.
  The HUD exposes hit cost, Fever/recovery, encounter phase and progress.

Rejected: merely deleting the enemy-count check still leaves mod gameplay constrained by
native SST allocation. Raising that shared limit would change stock games. The existing
rewindable extension path passes the new 300-enemy movement/admission/restore check while
preserving native slot availability. Keeping fixed player weapon arrays would silently drop
attacks in the very hordes this change enables, so they grow and have a separate rewind test.

Affected matrix: all existing Sonic/Tails arena rows retain traversal and boss obligations.
EHZ1 adds phase warning/surge/recovery, continuing pressure, ring risk, Fever pause/rewind and
projectile growth/restore tests; ARZ1 adds 300-enemy Whisp admission, updates and restore without
native slot consumption. The existing long endless simulation covers continued admission and
pickup expiry. Inherited per-act boss rewind, donor breadth and human balance-play gaps remain.

Validation selection: example paths trigger the full 2,993-class fallback. Focused validation
is proportionate: all production edits are in the isolated mod, with no host contract,
physics or build-policy changes. Run the entire ROM-backed Survivors fixture, including both
leaders/all acts, plus build/package and rendered gameplay inspection. This is focused mod
validation, not a full engine-suite pass; prior unrelated engine/guard findings remain as
recorded above.


Validation results on this base plus the working changes:
- Queued Maven with Java 21, `-Dmse=off -Dtest=TestSonicSurvivors` and the absolute
  `Sonic The Hedgehog 2 (W) (REV01) [!].gen` property: 70 tests passed, no skips, 69.3 seconds
  in the fixture (1:40 total). Includes the 20,000-frame endless simulation.
- A follow-up `feverProtectionDoesNotExpireBehindLevelUpCards` regression exposed the host
  player clock continuing to consume invincibility under mod object-control menus (478 to
  358 over 120 menu frames) while the mod's Fever clock stopped. The mod now captures both
  protection timers on freeze, holds them above expiry, and restores their exact saved
  duration on release. A queued five-test selection covering the reproducer, Fever,
  fight rewind, modal confirmation and lost-ring rewind passed with zero skips (3.049 seconds).
  The unchanged remainder of the 70-test fixture was not repeated.
- `python3 examples/sonic-survivors/build.py` compiled and packaged mod 0.4.0 successfully.
- `GameplayCaptureTool` with seed 12345, isolated save roots and authored controller logs
  rendered the camp, live HUD and first surge. Opening frame 400 and surge frame 1300 were
  inspected after their state CSVs. Temporary preview upgrades were written only to the
  capture's separate profile. The idle upgraded preview died at frame 1626; this is a
  stationary diagnostic, not a human difficulty assessment. Artifact directory:
  `/private/tmp/sonic-survivors-hordes-20261007`.

Remaining limits: human balance judgement and long-session performance beyond this fixture
are not certified. Unlimited admission intentionally allows load to rise with survival time;
no universal frame-rate guarantee is implied. No engine-wide suite or guards were rerun.

Final packaging and local delivery: rebuilt after the protection-timer fix, inspected final
surge frame 1300 with the HUD backing, and refreshed the existing enabled/trusted
`mods/sonic-survivors.jar` with its matching SHA-256 in local mod state. Other mod entries and
the user's actual save profile were preserved.


### Super Sonic deferred (2026-10-07)

At James's request, Survivors omits its Super State controller until a dedicated
implementation is designed. This blocks native and debug transformation entries,
including when host emerald state was set independently. Permanent relics and the
all-seven +25% damage bonus remain; arena start no longer grants host emeralds.
The shared engine implementation is retained.

Validated in the current checkout on `feature/ai-sonic-survivors`, based on
`ffc99771e67cfbd1198e0717a3d65d4ae907ddc9`. The change-based plan selected 2,993
classes because example paths (and the pre-existing untracked movie) fall back to
the full suite. Focused validation is proportionate: only the mod's controller
registration and emerald grant change, with no host or public-contract changes.
`JAVA_HOME=<JDK21> python3 tools/testing/maven_queue.py -B -Dmse=off
-Dtest=TestSonicSurvivors -Dsonic2.rom.path=<absolute S2 REV01 ROM> test` passed
71 checks with zero failures, errors or skips. The revised seven-emerald test
checks the retained relics/damage bonus and absent transformation controller.
`python3 examples/sonic-survivors/build.py` rebuilt the local mod jar successfully.
This is focused validation; the engine suite and guards were not repeated.
