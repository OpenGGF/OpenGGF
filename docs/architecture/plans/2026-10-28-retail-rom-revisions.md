# Original Sonic 1 and Sonic 2 revision support

## Scope and design

Support the verified original Sonic 1 and Sonic 2 REV00 and REV01 images through
the existing engine. The user supplied `s1-r0.gen` and `s2-r0.gen` on 2026-10-28
after the initial revision-support scope discussion. REVXB, REV02-derived editions,
modern ports and standalone Sonic 3 / Sonic & Knuckles remain separate work.

The pre-task integration base is `15ffd4349c645254347d88e8a55b557e08cbffbe` on
`develop`. Work belongs to `feature/ai-retail-rom-revisions-20261028` in
`.worktrees/ai-retail-rom-revisions-20261028`; the main workspace stays on its
existing branch and retains unrelated changes.

Reference builds used the already-present disassembly checkouts at S1
`f6ece657c1cf253404312137dfcb8ec15fa42318` and S2
`380f37a731bfc720bb0371a35a593184a7ec5e43`. These are newer than this project's
recorded gitlinks (`28872a46e2d4c94c5c4818cbd3c04e6eb17edb67` and
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`). Both selected revision builds were
byte-identical to their original input ROMs. The task changes neither gitlink
nor the user's existing checkout; new source citations identify their actual
reference or use stable routine labels.

Use exact size/CRC32/SHA-1 identification of each immutable logical ROM view.
Cache recognition on that view, so opening another ROM or donor cannot change an
existing owner's revision. An internal composition hook binds the stock module
before its providers escape detection; direct `createGame(rom)` paths also bind.
No published Mod API constructor or detector contract needs to change.

Keep native ROM bytes and native pointer values intact. Game-owned immutable
address profiles resolve declared canonical REV01 data bases to the selected
ROM's addresses. Pointer-derived addresses are already native and must never be
relocated a second time. Profile mappings are verified from matching disassembly
labels and the original images; source trees never supply runtime assets.

Choose existing semantic owners for behavior: scrolling and background builders,
object profiles, zone events, restart/score policy, audio arbitration and front-end
providers. Shared movement remains shared where its shipped algorithm is equal.
Both revisions retain their shipped bugs; `FixBugs=1` is not a revision choice.
Console PAL/NTSC timing and Japanese/overseas presentation are separate axes.
KiS2 retains its own lock-on rules and resource ownership.

Unknown images keep the prior unverified-image compatibility path. A matching
header alone must not be reported as verified retail revision support. Do not
add process-global active revision or mutate static offsets to select a ROM.

Inherited coverage limits remain explicit: this work adds no Japanese territory
selector, S1 title attract-demo dispatcher, or CPU fault / invalid sprite-memory
emulation. Existing credits-demo execution is in scope; an absent title attract
mode is not certified by testing its ROM data. Existing zone, viewport, donor and
route gaps are not converted into full-game parity claims by original-image loading.

## Alternatives considered

- Forking the engines would duplicate common movement and object code and make
  later accuracy fixes harder to share.
- Repacking a REV00 image into a synthetic REV01 address space would conflate
  pointers and table bases, obscure native data, and complicate trace evidence.
- Explicit per-ROM profiles reuse current modules and data pipelines while making
  every revision distinction reviewable. This is the chosen implementation.

## Revision impact and architecture

| Area | Sonic 1 REV00 / REV01 | Sonic 2 REV00 / REV01 | Sonic 3 & Knuckles |
| --- | --- | --- | --- |
| Identity and assets | Exact original-image recognition; explicit fixed-base address map across levels, objects, player art, audio and front ends | Exact original-image recognition; fixed-base relocation below the unchanged high asset banks, including competition and special stages | Existing locked-on image and cartridge-view handling retained; no new retail revision profile |
| Backgrounds and level events | REV00 deformation/background construction, MZ bounds, LZ water and boss behavior | Existing algorithms consume the selected native tables; HTZ/WFZ supplemental graphics use the native addresses | Existing per-zone providers remain authoritative |
| Object behavior | Caterkiller partial allocation and turns, floating-block persistence, MZ brick motion, FZ score/width, bubble wobble | Spring-wall and launcher hurt/death branches, native debug geometry, monitor awards | No revision-dependent object changes in this task |
| Shared semantic owners | Score limit, time-over timer retention, drowning water motion, dying-player solid push release, credits input press semantics | Score and ring caps, competition held/collected ring banks, swaps and Tails time-over lifecycle | Shared defaults preserved; host selection of an S2 emerald palette resolves the selected S2 ROM |
| Front ends | Native level-select order, ending hold and audio reset rules | Native credits text (including the shipped spelling), menus and attract data | Standalone Sonic 3, standalone Sonic & Knuckles and later editions require separate identity/data analysis |

Before this work, game detection and `GameModule` composition already selected
per-game providers, `GameRules` held game-wide movement gates, and ROM loaders,
PLCs, scrolling, object registries and rewind owners separated the relevant
subsystems. That structure supported the existing reference image for each game;
recognizing a game header did not establish a retail revision or relocate its data.

The added identity lives on `RomByteReader`, the immutable logical cartridge
view. `RetailRomAware` binds it to a module before provider use. S1 and S2 own
their address profiles and local behavior; narrow internal semantic services
carry the few differences into shared counter, water, solid-contact and credits
input owners. Existing snapshots retain the state these algorithms already own.
This avoids a global revision switch or a second physics engine for REV00.

## Work ownership

- Lead: common `RetailRomRevision` identity, detection binding, shared runtime
  owner changes requested by specialists, documentation and delivery validation.
- S1 worker: S1 loaders/art/audio/front ends, REV00 deformation, local object and
  event changes, and focused regression tests. Only the S1 source/test tree.
- S2 worker: S2 loaders/art/audio/front ends, local interaction and competition
  changes, and focused regression tests. Only the S2 source/test tree.
- Shared owner edits are coordinated through the lead. Workers do not compile
  while another worker is changing the same worktree's source snapshot.

## Implementation and checks

- [x] Verify original inputs: S1 REV00 `F9394E97`, S1 REV01 `AFE05EEE`,
  S2 REV00 `24AB4C3A`, S2 REV01 `7B905383` (CRC32; full SHA-1 pins in code).
- [x] Fetch and fast-forward the main branch; it was already current.
- [x] Create the isolated worktree and install hooks.
- [x] Check actual Java/Lua/PowerShell prerequisites. Initial default Lua failed;
  `LUA_BIN=/usr/bin/lua5.4` passed the actual category preflight.
- [x] Add original-byte recognition, altered-byte negative controls, lock-on view
  recognition and module-binding regression checks.
- [x] Verify S1 and S2 address profiles against originals, including pointer-native
  controls, level loading, art, collision, audio and front-end consumers.
- [x] Implement and test the differing shipped semantics through their narrow owners.
- [x] Run focused tests with absolute original ROM paths; inspect failures and skips.
- [x] Inspect `run_categories.py --base <pre-task SHA>` and run combined smoke with
  two workers on this qualified host. State candidate count/cost/stopping rule
  before submission. Full-suite is reserved for public release preparation.
- [x] Attribute disputed failures with bounded matched baseline/current selectors.
- [x] Review the resulting code and policy/documentation changes; resolve findings.

Delivery follows main-workspace `develop` integration, affected post-integration
checks, a push of `develop` only, and accounted task cleanup. Completion is
reported after those actions, separately from this source-validation checkpoint.

Coverage must distinguish recognition from loading, loading from behavior, and
logic assertions from recorded pixels. Existing tests and catalogue checks are
reusable infrastructure, not proof of revision correctness. No ROM, synthesized
ROM, raw trace payload, build tree, validation receipt or success-log archive is
committed. Consume and acknowledge category-run diagnostics before delivery.

## Work checkpoint

The first `test-compile` failed with three new scope/checked-exception errors;
those were corrected before test execution. The first focused run executed 176
tests with no skips (20 selected classes, Maven execution 87 seconds, queue wait
reported zero). It reported three assertion failures and one fixture error:

- `TestSonic1RetailRomLoading.everyZoneBuildsItsObjectAndBossArtWithoutRelocatingNativePointersTwice`:
  the new test asked the native ending zone for boss-explosion art.
- `TestSonic1RetailCaterkiller.bodyTurnRestoresTheOldPositionOnlyInTheOriginalRevision`:
  the new witness used a 16-bit fraction where its existing motion owner uses eight bits.
- `TestSonic2CompetitionSession.retailMonitorRevisionsKeepHeldAndCollectedCountersIndependentAndRewindable`:
  record equality compared cloned emerald arrays by identity; the owned scalar bank
  is now checked explicitly after restore/forward replay.
- `TestSonic2RetailObjectSemantics.rev00SpringWallBouncesHurtAndDeadActorsWhileRev01RejectsThem`:
  the new direct-callback fixture omitted the real solid-contact owner.

These are development results, not a passing candidate. The corrected source and
additional shared water/input/solid paths still require their affected checks.

The second focused run executed 312 tests with no skips (23 selector names,
24 classes, Maven execution 98 seconds). It passed the original failures and
the required S3K loading/bootstrap/decoding checks. Remaining failures were two
WFZ rendered-frame assertions, the new shared-path S2 spring-wall witness, two
existing launcher fixtures without injected services, and an ending-session
fixture whose audio mock omitted the rewind timeline. These remain development
failures until their bounded reruns complete; no broad smoke run has yet run.

Affected reruns passed: the fixture corrections in the 59-test selection, then
all ten tests in `TestRetailRevisionRendering,TestSonic1RetailCaterkiller` (eight
EGL boots and two Caterkiller witnesses; 35 seconds, two seconds queue wait).
The final S1 REV00 run passed 46 tests with zero skips in 32 seconds, with zero
reported queue wait:

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4
python3 tools/testing/maven_queue.py -Dmse=off
-Dtest=TestSonic1RetailRomLoading,TestSonic1Rev00Scroll,TestSonic1RetailCaterkiller,TestSonic1RetailObjectSemantics,TestSonic1UnknownRevisionPolicy
-Dsonic1.rom.path=${ROM_DIRECTORY}/s1-r0.gen test
```

`ROM_DIRECTORY` denotes the absolute directory containing the original supplied
images; local execution used the original file, with no copies or renamed images.

The 59-test development selection still failed three newly added assertions:
the Caterkiller witness originally counted destroyed children before their
normal retirement pass, and the two WFZ views sampled the intro's empty sky.
The final witnesses check destruction then retirement, and use a declared
fortress-deck entry while retaining terrain/player pixel assertions. No runtime
behavior was changed to satisfy these corrections.

The final change-based plan selects 4,259 candidate classes across all ten
smoke categories plus guards, with two workers and surfaceless EGL. Expected
local execution is about 30 minutes; the runner's 40-minute invocation limit
and no-output limit remain the stopping rules. Queue waiting is separate.
The source is frozen for that combined run; it is not yet a smoke pass.

## Combined validation and repairs

The combined command on candidate `69d96da3a0d2d261994bc1b86c9eff0c97447728` was:

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4
python3 tools/testing/run_categories.py
--base 15ffd4349c645254347d88e8a55b557e08cbffbe --workers 2
--headless-gl surfaceless-egl --rom-directory ${ROM_DIRECTORY} --run
```

All 86 ordinary batches completed: 35,237 tests, two assertion failures,
83 errors and 52 skips. The fresh-JVM guard lane completed 808 tests with seven
failures, one error and no skips. Preparation took 30.97 seconds; ordinary batch
execution totaled 1,561.52 seconds and guards 161.76 seconds. These are recorded
execution intervals, not elapsed delivery time; shared queue waits were additional.
The run was red, not a full-suite pass. Its results and skipped-case reasons were
inspected, then its diagnostics were acknowledged and deleted.

Eighty-four ordinary failures belonged to this task: closed or incomplete sparse
ROM fixtures and lost ROM-free metadata/optional-load behavior. Repairs keep
actual ROM attachment strict, give synthetic read fixtures explicit open UNKNOWN
backing, preserve table aliases and intentional read failures, and restore the
existing null/optional-query boundaries. Runtime guards also prompted active-world
counter-policy lookup, update-local launcher skip bookkeeping, and extraction of
credits dispatch from the size-limited ending owner. The existing S2 emerald
donation adds one explicit native-address resolver member to the cross-game
allowance; its exact scope is recorded in the architecture exception ledger.

The broad run's three revision-specific property-gated skips were two S2 lifecycle
witnesses and one S1 dynamic-water witness. Those checks had already executed
without skips in the focused original-ROM run with explicit REV00 properties.
The remaining skips retain their opt-in, context or deferred-coverage limits and
are not counted as passing coverage. The paired loading
and eight rendered original-image witnesses did execute in the broad run.

After repair, a 26-class focused selection passed 264 tests with zero failures,
errors or skips. It covered all 84 failed ordinary cases, original-image consumers,
the active-world counter and credits owners, launcher restore/replay, water,
palettes, scroll tables and eight EGL boots. It used the original absolute ROM
paths through `sonic1.rom.path`, `sonic2.rom.path`, `s3k.rom.path`,
`sonic1.rev00.rom.path` and `sonic2.rev00.rom.path`.

The command used `maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl`, those five ROM properties, `test`, and
this exact selector:

```text
-Dtest=TestS1S2PlcSessionOwnership,TestSonic1FloatingBlockObjectInstance,TestSonic1FZBalanceWidth,TestSonic1UnknownRevisionPolicy,TestSonic1RetailRomLoading,TestSonic2WaterDataProvider,TestSonic2PlcLifecycle,TestSonic2SpecialStageGeometryOffsets,TestSonic2SpecialStagePaletteNativeChannels,ParallaxMczTest,TestBackgroundScroll,SwScrlCnzTest,SwScrlCpzTest,SwScrlMczTest,SwScrlOozTest,TestSonic2RetailObjectSemantics,TestLauncherSpringObjectInstance,TestS2LauncherGraphRewind,TestRetailRevisionRendering,TestSonic2RetailRevisions,TestRetailRomRevisionIntegration,TestRetailDynamicWaterMotion,TestRetailCounterPolicies,TestCreditsDemoInputPresses,TestGameLoopEndingSession,TestHostEmeraldPresentation
```

Bounded baseline checks on `15ffd4349c645254347d88e8a55b557e08cbffbe` reproduced
these unrelated failures; their owning source and debt lists are unchanged:

- `TestCnzMinibossRegistered.numericSlotIsImplementedInBothPointerTables`:
  expected `true`, received `false` for the shared numeric-slot catalogue.
- `TestRewindCoverageGuard.noNewCoverageGapsBeyondBaseline`:
  `MhzEndBossInstance#objectRef#hitProxy` is outside its coverage baseline.
- `TestBuildToolingGuard.s3kStaticSessionStateDebtShouldNotGrow`:
  `HCZWaterSkimHandler.slotRetired` and
  `Aiz2BossEndSequenceState.cutsceneKnucklesSlot` exceed the static-state debt list.
- `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`:
  `TestTestReelExtension#bodyPasses` has no assertion/verify/fail oracle.

The catalogue baseline command was `maven_queue.py --lean -Dmse=off
-Dtest=TestCnzMinibossRegistered test` (two tests, one failure, no skips). The guard
baseline command used `-Dmse=off -Pguards
-Dtest=TestRewindCoverageGuard,TestBuildToolingGuard#s3kStaticSessionStateDebtShouldNotGrow,TestNoAssertionFreeDiagnostics,TestArchUnitRules test`
(38 tests, three failures, no errors or skips).

The repaired-tree affected-guard selection added
`TestBootstrapModuleProviderCachingGuard`, `TestArchitecturalSourceGuard`,
`TestCapturedPolicyCompactReachabilityGuard` and `TestRewindFieldDispositionGuard`
to that selector. It executed 142 tests with the same three baseline failures,
no skips, and one remaining frozen-record error. That error was the exact
overload descriptor: the existing palette offset is a `long`, so the reviewed
member must name `address(Rom, long)`. A subsequent strict
`-Dmse=off -Pguards -Dtest=TestArchUnitRules test` passed all 34 cases with zero
skips; no freeze-update/refreeze setting was used for the passing run. An earlier
diagnostic request was cancelled as no longer needed after bytecode inspection
identified the overload; it is not counted as validation.

The catalogue check was repeated with the exact baseline command on the repaired
tree: two tests, the identical one failure, no errors or skips. All four remaining
failure identities therefore match the original base; no new or worsened failure
remains unresolved. No broad smoke pass or release qualification is claimed;
the broad red run, repaired focused checks, matched pre-existing failures and
explicit skip limits are separate evidence.
