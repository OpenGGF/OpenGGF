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


### Menu allocation reduction (2026-10-07)

Current checkout `feature/ai-sonic-survivors`, base `210bda97e4583f3ab33f7ef45e7e050be875bdc6`.
The level-up menu emitted a new GL rectangle for every horizontal lit glyph span,
then the host CPU presentation path allocated geometry and primitive records and
recreated commands for replay. Extra card text amplified both allocation and draw calls.
The mod now decomposes its fixed font into non-overlapping maximal rectangles once
per module: 261 primitives per complete alphabet instead of 466 (44% fewer). This
reduces each downstream per-rectangle allocation too. Every glyph retains exact
pixel coverage at scales 1, 2 and 3, including fractional alpha without overlaps.
Card rank labels and descriptions are derived once for the finite upgrade catalogue;
timer formatting no longer invokes `String.format` each frame.

The first static-array cache was rejected by `ggfmod`'s static-state validator.
`MenuArt` instead lives in the module's existing game-service registry, contains no
GL resources or live gameplay values, and is recreated with the module. Its fixed
font/card data needs no rewind snapshot or invalidation on rerolls, rank changes,
viewport changes, or camera movement. A shared renderer/API change was unnecessary;
this deliberately leaves the engine presentation contract intact.

The selection plan chooses 2,993 classes plus guards due to the example-path fallback
(and an unrelated untracked movie). Focused validation is proportionate: production
changes are mod-local, with all font pixels and upgrade ranks directly checked plus
the existing full Survivors fixture. The existing per-act matrix and inherited gaps
are unchanged; this is presentation work, not new route certification.

Verification on the base plus these changes:
- Java 21, queued Maven `-B -Dmse=off -Dtest=TestSonicSurvivors` with the absolute
  root S2 REV01 ROM: 73 checks passed, zero failures/errors/skips (69.47 seconds in
  the fixture). Includes all glyph pixels, finite card descriptions and timer boundaries.
- `examples/sonic-survivors/build.py` compiled and passed `ggfmod package` validation.
- Gameplay capture of the installed baseline and rebuilt jar: CPZ1, 400x224, seed
  12345, isolated save roots, existing authored Start-at-frame-30 input, 120 frames.
  All five sampled PNGs and the state CSV are byte-identical. Frame 100 visibly
  contains three level-up cards, queued-level count and reroll row; inspected both.
  Captures: `/private/tmp/sonic-survivors-menu-20261007`.
- Copied the verified jar to the existing `mods/sonic-survivors.jar` installation and
  refreshed only its trusted SHA-256; other mod entries and the user's save are intact.

This is focused mod validation, not a full engine-suite pass. The 44% figure measures
font primitive count, not total heap traffic or end-to-end frame-rate improvement;
remaining host work and uncapped arena populations still contribute to frame cost.


### Tiered reward-ring consolidation (2026-10-07)

Current checkout `feature/ai-sonic-survivors`, base `c292555cd13458ce3ce75188f4920df2a0b807de`.
Use thresholds 5 / 25 / 125 instead of 10 / 100: ordinary enemies often drop only
one ring, so five reduces early clutter while retaining a readable progression.
Yellow holds 1–4, cyan 5–24, purple 25–124, red 125+. These are thresholds, not
rounded denominations: every stored ring survives merging. Red pairs keep merging.

`RingClusters` rebuilds a reusable hashed spatial grid every 15 active frames.
Same-tier rewards at least 45 frames old, within 48 pixels of an existing anchor,
combine if their total reaches the next threshold. The two-pass check leaves
sub-threshold groups untouched and rejects integer overflow before deleting anything.
Lost, homing, collected and destroyed rings are excluded, as are monitors/emeralds.
The anchor stays in place. No spawning or per-pickup all-arena neighbour scan is needed.
Grid arrays grow with peak population and release all pickup references after each pass;
the module owns scratch only, rebuilt from live state after rewind/load. The Stage's
15-frame clock and each pickup's value/lifetime are ordinary captured object state.

Merging existing rewards uses the minimum reward age, preserving the youngest
remaining lifetime instead of resetting expiry every time a pile combines. Fresh
`addValue` retains its existing lifetime refresh behavior. Ring credit and normal
XP scaling are applied to the full stored value on collection; fractional bonus XP
retains the existing stochastic rounding per pickup, so grouping may change individual
rounding/RNG outcomes but not its expected value. Magnets and one-chime-per-pickup
behavior remain intact. Static circular silhouettes are cached in `MenuArt`; the
old per-frame numeric value label is removed. The first ten-rectangle outline looked
square in a native preview, so the final art uses compact decompositions of circular
masks with a small highlight. No shared engine or ROM rendering path changes.

The change-based plan selects all 2,993 classes by example-path fallback. Focused
validation is proportionate for this isolated mod behavior. The full Survivors run
with Java 21, queued Maven `-B -Dmse=off -Dtest=TestSonicSurvivors` and the absolute
root S2 REV01 ROM ran 76 checks: 75 passed, zero skips; one new negative-coordinate
case accidentally used an unsigned-normalized ObjectSpawn. The test now explicitly
sets the signed position. A queued two-case follow-up passed that corrected test
and a new 300-reward merge/restore/replay stress case without skips (1.769 seconds).
The 77 distinct cases have passing coverage across these runs. Tier promotion,
250-ring/XP collection, overflow, exclusions, threshold/distance, menu pause,
remaining lifetime and rewind/forward replay are covered. This is focused validation,
not a full engine-suite pass; inherited per-act matrix gaps remain unchanged.

The final `build.py` package passed mod validation. A temporary native preview used
`GameplayCaptureSession` with the packaged mod, EHZ1, 400x224, seed 12345, authored
Start-at-frame-30 input and isolated saves. Four actual reward objects (1/5/25/125)
were seeded only for presentation at frame 200. The state CSV confirms live solo
Sonic in LEVEL mode; the inspected PNG shows the four increasing circular tiers.
Artifact: `/private/tmp/sonic-survivors-clusters-20261007/final/tiers.png`.
This is an art preview, not evidence of earning those drops; the production merge
and collection paths are exercised by the tests. Frame-rate gains are not measured.

Installed the final jar into `mods/sonic-survivors.jar` and refreshed only its trusted
SHA-256, preserving the other mod entries and the real save profile.

### Tougher enemies and six-slot builds (2026-10-07)

Current checkout, base `a815571da28309de68f815a3e2a3c5600dfed40c`. Health time
scaling is now `1 + 1.4p + 0.4p²` (p = stage pressure seconds / 120), with
35% per route tier. Ordinary batches gain one enemy per 60 pressure seconds
instead of 30; intervals bottom out at 20 frames instead of 12. Surges and
uncapped admission remain. Ring toll scales before Armor by 17.5% per completed
30 seconds of active run time, plus 15% per route stage and 10% per extra act.
Long mode reduces the time contribution to 75%. Run time carries across arenas,
so reaching a boss or choosing the next act cannot reset incoming damage.

Three weapon types and three buff types constrain offers, including rerolls;
owned ranks remain eligible and a maxed equipped build yields ring-bonus cards.
Homing Dash/Ground Pound use weapon slots; Air Jump uses a buff slot. Shops,
relics and temporary monitors remain independent. Appended IDs preserve existing
upgrade identity. Twin Lance, Meteor Shower and Pulse Field reuse the mod's
captured projectile/effect paths; Amplifier, Second Wind and Quick Study derive
stats from captured upgrade ranks. New auto attacks use the active fight clock,
not the presentation clock that advances under menus. Card labels and HUD counts
make slot ownership visible. No new persistent profile fields are needed.

Rejected: retaining density as the main difficulty lever would compound existing
population costs while leaving each hit inexpensive. Raising ring toll only by
stage would leave endless flat. No ROM parity claim applies to this mod balance.

Validation: the change-based plan selects all 2,993 classes plus guards via the
unclassified example-path fallback. Focused validation is proportionate: changes
remain inside the mod, use existing host contracts, and production paths are
exercised by its complete fixture. Java 21 queued Maven `-B -Dmse=off
-Dtest=TestSonicSurvivors -Dsonic2.rom.path=<absolute root S2 REV01 ROM> test`
passed 80 tests, zero failures/errors/skips (36.95 seconds fixture, 1:09 total).
New checks cover cap filtering over repeated deals, movement classification,
maxed-build fallback, slot restore, projectile emission, buff stats and escalating
toll with rewind. Existing encounter/endless tests cover reduced but continuing
batch growth and growing HP. Final package passes `ggfmod` validation.

The gameplay-capture skill produced a 120-frame CPZ1 card preview with seed 12345,
the existing authored Start input and isolated saves under
`/private/tmp/sonic-survivors-builds-20261007`. State CSV confirms live solo Sonic
in LEVEL mode; frame 100 shows Twin Lance, Ground Pound and Haste cards. Visual
inspection prompted a dark backing behind the slot counter. This is presentation
verification, not human difficulty or late-run performance certification.
Affected matrix: existing solo Sonic/Tails act obligations and inherited donor/
per-act boss rewind gaps remain; EHZ1 gains build-cap and damage-pressure checks.
No full engine suite or guards were rerun. Refreshed the local installed jar and
its existing trusted hash; actual player saves and other mod settings preserved.

### Five-/ten-minute mode durations (2026-10-07)

Follow-up on the same base and checkout: standard stages now last 300 seconds,
long stages 600 seconds, and the endless option is labelled Unlimited. Mode IDs
remain 0/1/2, preserving saved selection and the existing first-boss unlock rule.
The Death Egg remains a direct finale. Pressure rates and long-mode boss scaling
are unchanged, so the longer survival periods continue the requested difficulty
ramp rather than stretching the previous encounters over a longer clock.

The combined change-based plan still selects 2,993 classes by example fallback.
The prior 80-case Survivors run covers the unchanged balance/loadout work; this
localized follow-up uses the mode regressions, parameterizing the production
countdown/boss-boundary rewind test over both timed durations. Mode persistence,
legacy defaults, unlock cycling, endless pause/rewind and Death Egg are included.

Java 21 queued Maven with `-Dmse=off -Dtest=TestSonicSurvivors#<five mode methods>`
and the absolute root S2 REV01 ROM passed all seven parameterized cases, zero
failures/errors/skips (2.313 seconds fixture). `build.py` and mod package validation
passed; installed jar and its existing trust hash refreshed. No engine-wide tests
or repeat of unchanged gameplay checks; actual save data preserved.

### Damage feedback and enemy-drop consolidation (2026-10-07)

Current checkout, base `2b2388b0f2f51c3676b600f57920d54f18621fd6`.
Survivors supplies its own destruction configuration: the stock S2 explosion
and deferred animal remain, but the animal's points factory is null. Removing
only the explosion's points factory would do nothing: S2's animal creates the
score object later. Shared stock-game behavior is untouched. Enemy and boss
hits display HP actually removed, capped at remaining health; red 1–9 damage
uses 1x digits, orange 10–49 uses 2x, and pink-red 50+ uses 3x. Magnitude owns
the style instead of elite/boss identity. Existing captured effect arrays hold
value, colour and scale; numbers remain centred, rise and fade.

The prior ring consolidation did accept enemy rewards, but its 45-frame delay,
same-tier requirement, next-tier minimum and homing exclusion prevented many
visible merges during ordinary scatter/attraction. Reward rings now merge after
the 12-frame spawn pickup delay, within 48 pixels on the existing 15-frame tick,
regardless of tier or magnet attraction. Combining two low-value rings reduces
sprites even before a colour promotion; a magnetized constituent transfers its
homing state to the survivor. Full value, younger remaining lifetime, overflow
protection and menu pause remain. Lost rings, monitors and emeralds stay separate.

The combined plan chooses 2,993 classes plus guards due to example-path fallback.
Focused mod validation is proportionate: no shared host behavior or API changes,
and the complete mod fixture plus affected reward regressions cover the consumers.
Before the ring follow-up, Java 21 queued Maven `-B -Dmse=off
-Dtest=TestSonicSurvivors -Dsonic2.rom.path=<absolute root S2 REV01 ROM> test`
passed 82 cases with no failures/errors/skips (37.56 seconds fixture). The new
kill regression verifies damage values/styles, overkill, delayed animal creation,
absence of PointsObjectInstance, and replay after restoring the explosion.
The ring follow-up uses actual enemy kills and 31 frames of unmodified scatter
physics, plus cross-tier and homing merges, thresholds, distance, overflow,
full reward/XP credit, pause, expiry and rewind regression checks.

Affected matrix: EHZ1 gains destruction/popup rewind and actual enemy-drop merging
checks; existing act/character obligations and inherited donor/per-act boss gaps
remain. This is focused validation, not an engine-wide suite or guard pass.

Final results: the queued ten-case reward selection passed with zero failures,
errors or skips (24.92 seconds fixture), including the 20,000-frame endless test.
`build.py` compiled and passed mod package validation. The gameplay-capture skill's
production session rendered seeded 7/25/100 damage examples at frame 220, EHZ1,
400x224, seed 12345, isolated saves. Its state CSV shows live solo Sonic in LEVEL;
the inspected image demonstrates all three centred colour/size tiers. These are
seeded presentation examples, while actual hit/kill behavior is covered by the
regression. Artifacts: `/private/tmp/sonic-survivors-damage-20261007/preview`.
Refreshed the installed jar and matching trusted hash, preserving save data.

### Continuous music and short lost-ring fades (2026-10-07)

Current checkout, base `b17d31930a7a1847ac5c48b5017fa89ed1b620e9`. The native
playable expiry path reissues the current level music even when a caller grants
invincibility without starting its theme. Survivors does exactly that for Fever
and monitor stars, so expiry restarts arena music or replaces the mod-owned boss
track. `PowerUpRules.restoreLevelMusicAfterInvincibility` now makes that request
explicit: all stock factories pass true; Survivors passes false. Star cleanup,
shield visibility, protection duration and legitimate phase music changes remain
owned by their existing paths. Music reads the host module's rule, not donated
player physics. The first regression run caught the latter distinction: simply
reading the sprite's cached physics rules ignored the module override. Do not
spoof the boss flag or prevent the power-up timer reaching zero to suppress music.

The mutable unpublished 0.7 candidate constructor/pin and runtime API description
are updated together; the release descriptor remains unchanged. Compiled users of the PowerUpRules
constructor must rebuild; the API version remains 0.7.0. No released baseline is
changed. Host launchers compile/package the changed engine; the installed mod is
rebuilt against it.

Hit-spilled rings now fade linearly over 45 active frames (0.75 seconds), then
expire. Their small golden loop uses the mod's alpha-capable geometry, because
the native ring sprite entrypoint has no opacity argument. Rewards retain their
60-second lifetime; magnets, consolidation and XP still exclude lost rings.
Fade uses captured age, pauses with cards and replays the same expiry boundary.

The focused run (Java 21 queued Maven, TestSonicSurvivors, TestPowerUpMusicRestore,
TestModApiSignatureSurface, absolute S2 REV01 ROM) ran 100 cases with no skips:
97 passed, two new leader expiry cases exposed the physics/host rule mismatch,
and the inherited formation check expected five sprites despite the preceding
consolidation change. It now checks five rings of value and at most five sprites.
Existing stock music restoration and candidate signature checks passed. Focused
follow-up covers those corrections before normal combined validation.

The change-based plan selects 2,993 ordinary classes plus guards. Shared runtime
and candidate-contract edits require that normal selection, rather than the
mod-only proportionate exception. Tool preflight passed; native execution uses
the known working macOS display/service permissions. The reference cost is about
24 minutes ordinary plus 10 guards; the runner's 40-minute invocation / 10-minute
no-output stopping rules apply. Existing macOS full-suite AWT interaction remains
an inherited risk, not a reason to claim the entire engine was previously green.
Affected matrix: EHZ1 Sonic/Tails cover Fever/monitor expiry in arena/boss playback
and restored timer boundaries, plus lost-ring fade pause/expiry rewind. Existing
donor/per-act boss gaps remain explicit.

The eight-case follow-up passed the corrected formation and five stock restore
cases. Both leader tests advanced past the corrected Fever expiry and then exposed
a test setup typo: monitor subtype 7 is not the invincibility monitor (8). Corrected
that setup and reran only those two cases. The rendered EHZ1 fade preview shows
lost-ring ages 0, 22 and 40 through the production HUD at frame 220, 400x224,
seed 12345, isolated saves; state CSV confirms solo Sonic in LEVEL. It is a seeded
opacity preview, not a claim of three naturally timed hits. Artifact directory:
`/private/tmp/sonic-survivors-music-20261007/preview`.

Final focused expiry coverage parameterizes eight independent fixtures over both
leaders, arena/boss and Fever/monitor. Full gameplay steps exercise cleanup before
capture/replay; the initial tight direct-tick loop reused destroyed fixed-slot stars
without dispatching their cleanup and was rejected as an invalid repeated-grant test
setup. All eight final scenarios pass, zero skips (2.622 seconds fixture), with no
music requests on grant, expiry or replay. The independent fade checks, stock restore
checks and candidate pin checks also have passing focused coverage across the runs.

At James's explicit request, stopped the broad run and delivered with focused
validation, overriding the normal shared-contract change-based requirement for
this change. Run `20261007T115019Z-98c8eedf` was interrupted during ordinary tests;
its status is incomplete and guards did not start. No completed lane/results.json
was produced. Inspected both rolling logs before acknowledgement: the run included
skips (optional KiS2/visual and special-stage prerequisites) and hit
`TestS3kDataSelectPresentation.visualCapture_selectedSaveSlotShowsRightBodyRail`
with GLSL `version '410' is not supported`, matching the previously baseline-
reproduced macOS error recorded above. No new baseline run was attempted for that
existing signature. Diagnostics were acknowledged and deleted. This is not a full
suite pass. No unrequested engine/platform repair was added.

Queued `-B -Dmse=off -DskipTests package` passed (tests explicitly skipped),
refreshing the local engine distribution with the new candidate constructor.
The validated Survivors jar was copied to the installed mod and its trusted hash
refreshed, preserving the save and other mod settings. Focused verification is
spread over the original fixture/API run and corrected cases described above;
there is no claim that an entire final engine suite passed.

The commit policy rejects descriptor edits during ordinary candidate signature
regeneration. Removed the explanatory descriptor comment; version and publication
state were never changed. The signature pin and API description carry the addition.


## Roguelike depth and one difficulty curve (2026-10-07)

Current checkout `feature/ai-sonic-survivors`, base `0745ca5374`. Mod 0.5.0; profiles stay
`key=value` and gain keys only. Requested as a content/longevity and balance pass: fix the
exploits found in review, unify difficulty, then add evolutions and chests, character perks,
optional handicaps, milestone unlocks, mid-stage events and a records page. James steered two
points mid-task: keep the unusual arena floors (including low ceilings) and make the late route
genuinely hard, answered by deep global power-ups; and make shop items small, near-limitless
increments with rising prices.

Changes:

- **Exploits.** Start-then-Escape banked the free start rings every few seconds; Escape before a
  fatal hit always beat dying. Leaving mid-fight now banks like a game over; only a clear-screen
  retirement banks held rings, less the run's free start and revive rings. The stock touch pass
  treats a spin-dash charge or any roll as an attack, which against hitpoint badniks made a
  crouched charge untouchable while chipping everything in reach; a grounded contact now attacks
  only at 4 px/frame or more, otherwise the badnik's (or boss's) touch is a hit.
- **One curve (`Difficulty`).** Enemy hitpoints used the arena clock while the toll used total run
  time, so later arenas opened with soft badniks and a crushing toll and skipping zones was safer.
  Hitpoints, spawn interval/batch, toll, ring value, elite spacing and boss hitpoints now read the
  route tier and the arena pressure clock only. Hitpoints grow superlinearly with the tier
  (`1 + 0.40t + 0.05t²`), so Metropolis onward expects a long-built profile. Boss hitpoints are
  12x the zone's average badnik at arrival (Death Egg 18x at five minutes' pressure); previously an
  elite at 4:59 out-healthed the Emerald Hill boss. Boss weapon i-frames fell from 10 to 6 frames so
  several weapons share the window, and boss-phase reinforcements come a third as often.
- **Population.** At 160 live badniks ordinary batches stop joining; every twelve held back arrive
  as one elite. Enemy objects remain slotless and otherwise uncapped.
- **Power-up fixes.** Fever 6 s on, 18 s recovery (was about a third of the time invincible).
  Super Ring pays two base tolls, chest ring prizes likewise, invincibility monitors last 10 s,
  the Eggman bomb destroys ordinary on-screen badniks and takes 40% from elites (a fixed 20 did
  nothing late). The weapon-guarantee card picked only ids 0-6 and could duplicate a dealt card.
  The red emerald text matched an old flat reduction. Gravity shots break on the floor.
- **Progression.** XP curve `6 + 8L + 2L²` (was `5 + 3L + L²/3`, which maxed a build inside
  Emerald Hill). Twelve evolutions (maxed weapon plus owned partner buff) open from chests: boss
  chests hold 3 (one in five 5) prizes, every fourth elite and each warden squad drop one. A maxed
  build's cards offer Overdrive (+6% damage, unbounded) or a ring bonus. Twelve upgrades are core;
  twelve unlock from lifetime milestones, so existing profiles keep what they earned.
- **Content.** Sonic: Fever in 10 bounces and +30% per combo bounce. Tails: hold-jump hover
  (75 frames per jump), +24 px pull, Fever in 12. Six Eggman's Rules (bank bonus 15-30% each).
  Zone events every 150 s from 1:45 (telegraphed rain, swarms, Casino Night's Jackpot) and warden
  squads from 3:00. Unlimited mode adds a tier every two minutes. Camp became a short menu with
  shop, rules and records pages (stats, unlock requirements, evolutions, bestiary, best unlimited
  times). The shop is ten global power-ups, mostly small steps to high caps with geometric prices;
  older saves convert once to the same totals (`shopVersion=2`).
- **Rendering.** ROM rings use the ring renderer, which presents after the sprite list and so drew
  over menu panels; reward, orbit and homing rings now hide while a menu shows. Badnik art was
  already correctly behind the panels.

Balance evidence comes from the new opt-in `TestSonicSurvivors#balanceProbe`: a bot that jumps at
the nearest badnik, takes weapons until it owns three and then ranks owned upgrades, playing each
zone as a fresh launch with the run state carried across. It records rings against toll, live
badniks, hitpoints spawned against damage dealt per second, level, chests and evolutions every 30
seconds. Findings that drove changes, in order:

| Run | Finding | Change |
| --- | --- | --- |
| Fresh, old curve | Level 22 and three maxed weapons before the Emerald Hill boss | Steeper XP curve |
| All profiles | 70,000 rings in Aquatic Ruin: ring prizes read the toll at the held total, which reads the held total | Prizes use the base toll |
| Fresh, steep curve | Died inside a minute: no weapon from the first-card bot, 30 rings | Weapon-first bot picks; 40 start rings |
| Mid/max | Aquatic Ruin banked 5-9k: Whisps spawn in threes but each paid full | Whisps pay a third |
| Max | 16 chests at the end of Wing Fortress: the boss sweep counted every fourth elite | Sweeps drop rings only |
| Mid/max | Casino Night walls weapon-light builds (Crawl base 8 HP, about 3x neighbours) | Crawl 5 HP |
| Five fresh seeds | Endings from Emerald Hill to a full win; good runs banked the whole old shop at once | Bank a quarter of rings; deep long-tail shop |
| Unlimited | Damage kept pace with the horde at 18 minutes, fed by tier-scaled rewards | +1 tier per two minutes survived; rewards stay at the zone tier |

Final shape (Sonic, standard mode): fresh runs end between Emerald Hill and Casino Night; a mid
profile reaches Metropolis to the Death Egg on a good seed; a veteran profile (tens of runs of shop)
can win, with single-digit rings at the Death Egg. The bot neither dodges nor plans evolutions, so
these are relative, not human, difficulty. Casino Night 1's low ceiling can pin a bot under a Crawl;
by James's direction the arena stays.

Rejected:

- **Relocating low-ceiling arenas.** A jump-height survey found Casino Night 1, Hill Top 1/2 and the
  Death Egg corridor allow 0-30 px of rise in long stretches. James prefers the shapes. The survey
  remains as `FloorSegmentSurveyProbe`'s `openggf.floorsurvey.headroom` option.
- **Carrying the probe across zones through the clear screen.** The headless frame driver performs
  the route request (fade, load, title card), but the next arena's controller never updated under
  it; a manual load on top of that double-loaded the act. Each zone is a fresh launch with the run
  state copied by reflection instead. The live game loop's transitions are unaffected.
- **A flat 80 px headroom threshold** was stricter than the bounce rebound (about 70 px).
- **A coarse seven-item shop** (+10% steps, about 4,300 rings to buy out) was replaced at James's
  request; a deep run now banks thousands, so only a long tail keeps the bank meaningful.

Validation: the change-based plan falls back to the full suite for example paths; focused
validation is proportionate because production changes are mod-local and the only shared-tree
edit is the opt-in, test-scope `FloorSegmentSurveyProbe`. Java 21 queued Maven
`-B -Dmse=off -Dtest=TestSonicSurvivors` with the absolute root S2 REV01 ROM: 109 cases, 108
passed, one skipped (the opt-in `balanceProbe`), 24 s fixture. New cases cover forfeit/retire
payouts, the spin-dash rule, chest evolution order and rewind, every evolution firing, unlock
gating and announcement, all six rules, both leaders' perks and hover, zone events with
telegraphs, warden chests, overflow elites, boss scaling, sweep prizes, shop conversion and
pricing, camp pages and persisted records. `FloorSegmentSurveyProbe` ran with headroom over nine
acts. `build.py` packaged 0.5.0 (passes `ggfmod` validation); a temporary
`GameplayCaptureSession` preview with the packaged mod (EHZ1, 400x224, seed 12345, isolated
saves) was inspected for camp, shop, rules, all four records pages, cards, chest, HUD, Coconut
Rain and a warden banner. The final probe batch (seeds 22/33) is the "final shape" above. No
engine-wide suite or guards were run. The installed `mods/sonic-survivors.jar` and its trusted
hash were refreshed; other mod entries are unchanged and the user's save converts its shop on load.

Follow-up at James's request: the chest opening became a staged sequence (drop and rattle, lid
burst with white flash, light rays and pillar, a 48-ring fountain in ROM ring art, Super Sonic's
theme, prizes spinning through the catalogue before landing). It derives entirely from the
chest's frame count, so pause, skip and rewind need no new state; Enter reveals everything and a
second press closes, restoring the boss track, the zone's music (`getCurrentLevelMusicId`) or, at
a clear, fading out. The ring renderer draws over panels, which suits the fountain. The chest
test now checks the fanfare request, skip and music restore; 108 Survivors cases pass, the opt-in
probe skipped. Inspected in a `GameplayCaptureSession` preview at nine points of the sequence.

### Lethal tolls, rationed fire and rolling (2026-10-07)

James noticed Sonic effectively could not die. The engine's death at zero rings worked (a new
regression shows a badnik or shot ending a run), but stock Sonic survives any hit while holding a
ring, and half the toll scatters at his feet to be grabbed back, so the count was almost never zero
when the next hit landed. Now a hit whose toll would empty the rings ends the run through the
engine's own death routine, unless a revive (now three base tolls) catches it; the HUD shows NEXT
HIT FATAL. That made fire the dominant killer: probes died within seconds to unavoidable shots.
At James's direction badniks fire far less: each shooter waits twice its period, elites no longer
fire faster, and every badnik shot draws on one shared arena budget (1.5 s early, 0.5 s at tier 9),
so a crowd fires no faster than one shooter. James also found rolling into badniks hurt Sonic: the
spin-dash fix treated any roll under 4 px/frame as a hit, and a surviving badnik's knockback
(3 px/frame) guaranteed the next contact hurt. Now only a crouched charge or an all-but-stopped
roll (under 1 px/frame) is a hit; a roll damages and, if the badnik survives, knocks Sonic back
for free. Runs start with 50 rings and an 8-ring base toll (six hits).

With rare shots and safe rolls the bot rarely got hurt and fresh profiles won, largely on stacking
Overdrive (26 picks at +6%). Overdrive is now +3%, health grows `1 + 0.40t + 0.06t²`, the toll adds
30% per zone (was 20%) and late badniks pay `1 + t/4` rings (was `t/3`). Final probe (seeds 22,
33, 44): fresh runs end at Casino Night or Metropolis, mid profiles at Casino Night (the bot never
rolls under its low ceiling), the veteran dies at the Death Egg or wins. `-Dsonic-survivors.balance.hits=true`
adds a line per lost toll to the probe output. 110 Survivors cases pass, the probe skipped.
