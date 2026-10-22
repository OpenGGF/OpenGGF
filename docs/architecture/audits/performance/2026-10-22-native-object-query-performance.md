# Native object query and collision allocation follow-up, 2026-10-22

This wave composes with the [initial pipeline work](2026-10-22-object-pipeline-performance.md),
[FBZ/SOZ work](2026-10-22-fbz-soz-cpu-performance.md), and
[tile publication work](2026-10-22-sprite-tile-reuse.md).
The pinned pre-task integration base is `8aa692b045c195ef8a42896b6bbc9549158470c0`.
The initial integration branch is `bugfix/ai-object-pipeline-performance-integration-20261022`.
Final composition uses `bugfix/ai-object-pipeline-performance-final-20261022`,
allowing the initial tree to remain frozen during its combined smoke collection.

## Decisions and preservation boundaries

- Foundation `4b437189c7d89fe94302847abac98b3078f0ce6b` introduces engine-only
  owned-SST queries. A saved slot follows its replacement occupant; it is not
  a retained Java parent identity or an execution-slot alias. The raw `atSlot`
  variant includes an occupant awaiting deletion until population removal.
  `liveAtSlot` preserves callers that previously filtered `isDestroyed()`.
- CNZ active fans consult the two adjacent owned slots in ascending order.
  Layout order, active/static variants, overlapping influence windows and
  pending-deletion visibility retain their previous selection rules. A review
  caught a conditional duplicate-slot case: an ineligible first fan must not hide
  a later eligible fan in the same adjacent slot. Typed occurrence lookup retains
  that full predicate walk without allocating a result list. This avoids
  constructing and sorting the full typed fan result during execution setup.
- LRZ boss platforms and streams, DEZ beam particles and SSZ Death Egg trails
  dereference their saved slot. Generic replacement types, absent-slot zero
  positions, pending deletion and animation-owned lifetime retain their existing
  distinctions. No movement, damage, allocation cadence or lifetime gate changes.
- SOZ vine display writes use the raw slot query, including pending deletion and
  replacement displays. Single-byte slope sampling reads the signed native byte
  with the same bounds as the provider default. Bulk `getSlopeData()` remains
  defensive; signed sampling follows restored surface RAM.
- Thirty-nine constructor sites across 32 collision getters in 31 FBZ/SOZ files
  use the existing bounded `SolidObjectParams.of(...)` factory. Arguments,
  offsets and state-dependent branches are unchanged. Static constants are left
  alone. The factory already compares complete immutable values and retains a
  bounded per-thread direct-mapped table; collisions fall back to construction.
  Its historical allocation measurement is motivation, not a new measurement
  of this wave or a claim of allocation elimination under every workload.

## Review-driven revision

The first index eagerly rebuilt a HashMap node, list and backing array for every
occupied slot even when a caller requested only a type. A steady-state query
check did not cover that churn. The follow-up uses reusable raw population/type
buffers and a separately lazy primitive `(ownedSlot,populationOrdinal)` array.
Type-only queries do not service the general rendering/collision cache, sort the
population or invoke unrelated slot callbacks. Slot queries sort primitive pairs
once per invalidation and binary-search the requested range. Rebuild cost is
O(N log N), lookup O(log N + duplicate occupants); no per-slot containers are
constructed. Stable ordinals preserve duplicate-slot source ordering.

All population-cache dirty writes invalidate the native index. Slot reassignment
and rewind adoption also invalidate it. Reset and spawn resync explicitly clear
population, type and callback references without waiting for another query.
Snapshot copying precedes routed slot/liveness callbacks, preserving ordinary
callback retirement while the next query refreshes invalidated membership.
These are bounded derived caches, not captured gameplay state.

## Discovery limits and rejected work

The remaining FBZ magnetic pendulum/crane/chain/projectile and CNZ flash scans
inspected in this wave are in `afterRewindRestoreSettled`; changing them does
not address ordinary frame work. SOZ limb-detachment scans execute on removal,
and CNZ capsule notification executes once at results completion. They are not
selected as current hot-path fixes. Native trigonometry must not be replaced
with approximate tables without ROM/arithmetic evidence. Small animation arrays,
spawn-only script reads and a packed tile-storage redesign remain unselected
without a material-cost case. The shared `ObjectManager.objectIdInSlot()`
scan is also left alone: routing its sparse ordinary sidekick queries through
a newly built slot index could add a second O(N log N) sort under population
churn while the general sorted cache is still needed later. A profile would
need to demonstrate a net benefit before changing that public consumer.

Further inspection selected the per-update DEZ fireball emitter/fragments,
hand defeat fingers and SSZ crane encounter lookups for live slot dereference.
The SOZ failed-particle-allocation terminal check uses raw dereference instead,
preserving its pending-deletion flag reads. Existing boss sequence tests cover
empty/reused slots, failure prefixes and rewind; their affected rerun is recorded below.

## Validation checkpoint

The first root focused command on foundation `4b437189c` plus the consumer/raw-slot
and parameter-factory draft passed **23 tests, zero failures/errors/skips**:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestNativeObjectQueries,TestNativeObjectSlotOccupancy,TestCnzHoverFanNativeSlotQueries,TestCnzHoverFanObjectInstance,TestSolidObjectParamsInterning' test
```

This command predates the lazy primitive index revision, the FBZ/SOZ merge and
SOZ direct sampling. It is focused evidence for that tested state, not a final
combined pass. The revised checks below qualify churn/reset/sampling; the affected ROM/domain
selection was pending at that checkpoint and is recorded below. The delivery lead owns one final change-based smoke
selection and guards against the pinned integration base; no release-only
full-suite run is part of this task.

The revised native index plus SOZ sampling/display changes passed 29 focused
checks at integration HEAD `bd4368453` plus the unstaged candidate:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestNativeObjectQueries,TestNativeObjectSlotOccupancy,TestCnzHoverFanNativeSlotQueries,TestSozSpringVine' test
```

Zero failures/errors/skips; Maven 1:23, no admission wait. This includes five
new churn/reset/order/callback cases and signed sampling/no-copy/restore checks.
The subsequent four boss-child scan replacements were not yet present.

A mechanical getter-body comparison verified all 39 factory substitutions preserve
the complete original getter text after replacing only the constructor token.
It is a source-equivalence check, not runtime behavior or throughput measurement.

## Bounded final discovery checkpoint

The independent CPU pass reviewed OOZ `cab1fcd056` and found no actionable
snapshot-order, reentrancy, cleanup or rewind issue. Its remaining scan rejected
WFZ/MTZ rewind-only parent searches, attack-admission-only SYZ/SLZ scans, cached
SLZ seesaw discovery and already guarded unchanged-position spawn updates.
The GPU pass on `0741d27cf` found no further material candidate in inspected
ordinary batching, scroll-buffer, initialization, state-restoration or diagnostic
paths, but identified one mixed upload retry issue to fix before acceptance:
an ordinary immediate failure can update the CPU mirror without dirtying it,
allowing later equal frozen restoration to skip recovery. The three-phase
(begin/upload/end) regression reproduced three failures, zero errors/skips at
`49f009714` plus the unstaged root candidate: each expected one recovery upload
but observed zero. The immediate upload now marks its slot dirty when bind,
upload or unbind fails, then rethrows. Successful immediate writes retain their
existing behavior. Its affected check is included in the next focused selection.

The isolated tile experiment on `44d11628a` rejected production reuse: allocation
savings did not compensate for its comparison cost in any tested scene's median
CPU preparation result. The tile owner removed the hint/reuse paths in
`b322716c7` and preserved exact measured evidence in the existing tile audit. These are bounded
CPU preparation observations, not whole-engine FPS or GPU measurements.

The last discovery pass found no further material safe CPU candidate after
reviewing the then-candidate OOZ snapshot change, including its nested callbacks,
oversized teams, cleanup and native/extension ordering. The omitted-extension
snapshot remains mutation-safe; removing its occasional copy lacks a measured
material-cost case. This is a bounded discovery stopping decision, not a claim
that every possible engine optimization has been exhausted.

The subsequent bounded real-update comparison rejected OOZ scratch storage in
`644feb744`: recurring empty WAIT added CPU while blocked/carry gains were small
or mixed. Its production source again equals `98fc161d2`; useful callback,
ordering, large-team and rewind regressions remain. The detailed protocol,
limits and rejected implementation are recorded in the FBZ/SOZ audit. Neither
the tile nor OOZ allocation experiment remains enabled. Reopening these or the
remaining trig candidates requires fresh material-cost evidence, not another
unchanged discovery loop.

## Combined focused selection before final smoke

At `49f009714` plus the root source draft, the 30-class command executed
361 tests: one new fixture assertion failed, zero errors/skips. All 19 atlas
residency/dirty-upload checks passed, including all three formerly red mixed-path
retry cases. The other native-query, CNZ duplicate-slot, vine sampling, collision
parameter, LRZ/DEZ/SSZ/SOZ encounter and FBZ/SOZ object checks passed. Maven
execution was 2:02 after 385 seconds of admission waiting.

The sole failure was
`TestSszDeathEggCutscene.trailRefreshReadsPendingAndReplacementOwnedSlotOccupants`:
it expected spawn Y `$C68` plus `$1D`, but the parent's first update already
subtracts a fractional pixel from its 16.16 accumulator, exposing `$C67`. The
assertion now uses the actual parent's SST Y plus the unchanged native offset;
no gameplay code or expected movement was tuned. The affected class retry passed all 14 tests with zero failures/errors/skips
at `cee846335` plus the staged root candidate. Maven execution was 31.256s
after 195s of queue waiting. The unchanged other 347 test cases need no
repeated focused run. Together these completed checks qualify the affected
selection; they are not one pristine 361-test passing command.

The 30-class selector was:

```text
TestNativeObjectQueries,TestNativeObjectSlotOccupancy,TestCnzHoverFanNativeSlotQueries,
TestCnzHoverFanObjectInstance,TestSolidObjectParamsInterning,TestSozSpringVine,
TestSszDeathEggCutscene,TestLrzEndBossEncounterHeadless,TestDezFinalMouthSequence,
TestDezFinalFireball,TestDezFinalHand,TestSszCranePlayerRelease,TestSszCraneClaw,
TestSszCraneCameraPan,TestSozEndBoss,TestFbzSharedObjectSubtypes,TestFbzTrapSpring,
TestFbzPlayerTransportObjects,TestFbzPolePropellerPistonAndBlocks,
TestFbzSnakeAndRotatingPlatforms,TestFbzMagneticObjects,TestFbzMissileObjects,
TestSozSolidSprites,TestSozSandMechanisms,TestSozPushableRock,TestSozPillarAndRockSwitch,
TestSozEndBossProduction,TestSozLightAndGhosts,TestFrozenPatternResidency,TestPatternAtlasDirtyUploads
```

Command: `env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl '-Dtest=<comma-joined selector above>'
"-Dsonic1.rom.path=${ROM_ROOT}/s1.gen" "-Dsonic2.rom.path=${ROM_ROOT}/s2.gen"
"-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test`. The existing absolute ROM files were
verified by SHA-1/CRC32 against the repository identities; none was copied or linked.

The atlas failure-path correction is committed as `cee846335`; its Java source
is the tested source from this command. The later Death Egg edit is test-only.

The corrected class command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSszDeathEggCutscene' \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test
```

## Final composition and affected checks

In command records, `ROM_ROOT` denotes the actual verified original-ROM
directory and `MAIN_WORKTREE` the existing main checkout. Neutral variables
replace machine-specific absolute paths; selections and flags are unchanged.

The final checkout composes the accepted performance source from `3055eb336`,
reviewed rewind-harness repair `e229e67c0`, OOZ rejection `644feb744` and upstream
`23a18e2a2` in merge `de0bda7da`. Upstream changed multiplayer, hosted retries,
audio ownership and API signatures. Its production file set is disjoint from
this task's; only the version changelog, workflow catalogue and measurement
hazards overlap. Both contributions were preserved. No main-workspace branch
switch was used.

The two SOZ failures found by the initial broad collection compared transient
`generatedPivot` before/after restore. `GenericFieldCapturer` and
`RewindSchemaRegistry` already exclude Java transient fields; the diagnostic
scalar walker had omitted that modifier. The uniform repair retains all
previously eligible non-transient scalar/final comparisons, and changes no SOZ
production source. A real transient-cache control passes; deliberately losing
an authoritative scalar still reports exactly `37 -> 0`. The independent
read-only review found no contract weakening or class/name exception.

At clean `de0bda7da`, session `30735` passed **1,586 tests across 23 classes,
zero failures/errors/skips**. Admission waited 971 seconds; Maven including
compilation took 1:28. This covers the repaired 1,331-case object sweep, ratchet
and negative controls; SOZ sampling and cache checks; the 88 OOZ cases; hosted
completion/retry and fresh-load ownership; results pattern/PLC lifecycles; audio
ownership; ghost playback/roster; all required S3K classes (both level-loading
packages); and the imported compiled Mod API signatures.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestRewindRoundTripHarnessFailures,TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet,TestSozSpringVine,TestOOZPoppingPlatformMultiSidekick,TestSonic2TriggerParticipation,TestOOZPlacedObjectGaps,TestHostedRunActCompletionWiring,TestHostedRunController,TestFirstBootModScene,TestEngineConfiguredHeadlessStartup,TestHubMusicRomThemes,TestModApiSignatureSurface,TestRemoteGhostPlayback,TestRosterConsumption,TestResultsPatternRanges,TestSonic1ResultsPlcReadiness,TestSonic2ResultsPlcReadiness,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=${ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test
```

The subsequent singleton guard exposed three diagnostic-probe bootstrap
violations at `de0bda7da`, not runtime object/render regressions. Correction
`7462543b5` uses an owned headless graphics host and preserves caller service
ownership. Its exact guard/probe rerun passed **44 tests, zero failures/errors/skips**
(835s admission wait, Maven 1:21). The commands, red proof and corrected probe
hash are in the tile audit. The 1,586-test executable inputs outside that tool
and its test remain unchanged; they were not repeated. The 25 remaining focused
XML reports were inspected and consumed, with native extraction retained.

A fresh dry change-based plan against the pinned pre-task base at `7462543b5`
selects all **3,591 smoke classes plus guards**, reflecting the imported upstream
inventory. The original source-frozen collection selects 3,577 classes at
`3055eb336`. The delivery uses each result at its tested state, plus the focused
composition checks above: production file sets are disjoint, and the actual
indirect launch/retry/results/audio/API boundaries were checked. No second
unchanged broad run or merged-SHA broad pass is implied. No release-only full
suite is requested. The [public smoke job](https://github.com/OpenGGF/OpenGGF/actions/runs/39784182726/job/119842860420)
for exact imported commit `23a18e2a2` ran its smoke and coverage-assertion steps
successfully (25,972 cases, zero failures/errors, **5,146 skips** in the log
summary). Its separate policy/test/guard jobs were skipped. CI is supplementary
evidence; it is not a no-skip or ROM/graphics certification. The affected imported
boundaries and ROM-backed requirements use the zero-skip local checks above.

## Interrupted smoke collection and exact continuation

Session `90812` ended with `KeyboardInterrupt` (exit 130) while waiting for
admission after 59 of 72 ordinary batches; guards had not started. No deliberate
cancellation was requested by the lead; the signal's origin was not recorded.
The user explicitly authorized continuing the remaining 627 classes and guards.
The inspected run `20261022T122109Z-d3ad292e` at clean `3055eb336` completed
**27,972 cases, three failures, zero errors and 85 skips** across the first
2,950 classes. Ordinary execution took 3,582.89s and preparation 27.88s; queue
waiting is excluded. This incomplete invocation is not a passing smoke run.

Both rewind failures were the `generatedPivot` transient comparison described
above (`TestEveryObjectRewindRoundTrip.everySpawnableObjectRoundTrips(String)[1313]`
and `TestRewindHarnessCoverageRatchet.everyDeclaredClassRetainsItsCoverage`).
The third was `TestHczMinibossNativeRendering.allOrbitPhasesMatchRomSpriteCompositionInNativePixels`, line 53: GLFW failed
to initialize before pixels were checked. One exact matched invocation on clean
pre-task `8aa692b04` reproduced that same assertion: one test, one failure, zero
errors/skips. It used Java 25, display variables unset, the same surfaceless-EGL
property and verified S3K ROM. This attributes the environment limitation; it
does not validate the pixel comparison. The baseline worker made no code edits.

All 85 skip reasons were inspected, with none omitted. They include optional
measurement/capture flags, unavailable GLFW/EGL and shader diagnostics, missing
SecondStrike mod jars, 32 route cases lacking applicable platforms/flyers, and
one spin-tube assumption where the tube at `(1920,896)` did not capture/release
Sonic. These are coverage limits, not 85 passing cases or exclusively environment
skips. The affected ROM-backed focused requirements above have zero skips.

The original change-based command was:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  MAVEN_ARGS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base 8aa692b045c195ef8a42896b6bbc9549158470c0 \
  --rom-directory "${ROM_ROOT}" \
  --max-minutes 120 --run
```

The continuation used the same environment and ROM directory, invoking the
owned `/tmp/openggf-object-pipeline-continue-20261022.py` driver described below.
Its invocation budget is 60 execution minutes. The temporary driver's source
was independently reviewed; its exact suffix derivation is recorded here so
no one needs a retained pass cache to reconstruct the selection.

The continuation verifies the original complete plan against a fresh plan and
source fingerprint `54a7417bf7b3d74ed8ad624875e7663b1587d3901678b4dd4042c8a728a0f908`.
The first 59 batches are exactly the original 2,950-class prefix; the suffix is
627 classes in 13 unchanged batches (12 of 50, then 27), followed by the entire
guard lane. An owned temporary driver outside the checkout invokes the existing
runner API with its worktree lock, preparation contract, preflight, fingerprint
checks and shared admission unchanged. The original plan/results remain only
in memory across acknowledgement of consumed diagnostics. No pass cache,
runner patch, resource override or repeated completed prefix is introduced.
The continuation has a 60-minute execution budget, excluding queue waiting;
the existing no-output timeout remains ten minutes.

The independently reviewed continuation launched as session `11181`, run
`20261022T153220Z-55525ca4`. Preparation passed in 27.82s; test admission
remains separate. The driver was temporary and registered to this task; it was removed after
results were consumed. Original diagnostics were acknowledged.

While it queued, main advanced to `d59ea164d`: names now remain present for
every visible multiplayer ghost, with finished opacity unchanged. This removes
the nearest-four sorting/filtering path in the racing coordinator, outside this
task's changed production files. Merge `8b3bb0b80` preserved that behavior and
both release-note contributions without conflicts. The changed presentation
check and its coordinator consumer were checked together with:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestRemoteGhostPresentation,TestMultiplayerRaceCoordinator' test
```

At `8b3bb0b80`, session `13946` passed **28 tests, zero failures/errors/skips**
(27 coordinator cases and one all-nameplates/finished-opacity case). Queue wait
was 268s; Maven execution including test compilation was 28.837s. Both exact XML
reports were inspected and consumed. No performance-domain executable inputs
changed in this upstream merge.

A further upstream advance to `1c7e412f4` moved ghost nameplates from immediate
font drawing during sprite preparation into the hosted overlay after world
rendering. It also added promo compositor checks and mirrored skill guidance.
Merge `ba786af5e` preserved all changes without conflicts. Independent review
confirmed that the nameplate screen-space flush and final hosted-overlay flush
prevent adjacent geometry batches from crossing the immediate font boundary.
Nameplates use a separate font texture path; frozen art and tile culling do not
apply to them. Unload clears pending labels. The new production files remain
disjoint from this task's source changes.

At `ba786af5e`, session `55463` passed **41 tests, zero failures/errors/skips**,
after 65s queue waiting and 1:20 Maven execution including compilation:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestGhostRendererLayerSelection,TestHostedRunController,TestGhostRenderRegistry,TestPresentationPatternBatching,TestGraphicsManagerPatternAtlasPageOrdering' test
```

The five XML reports were inspected and consumed. These are label lifecycle and
deferred-command boundary checks, not a new native-pixel verification claim.
Existing accepted renderer pixel checks remain at their tested source states;
unchanged EGL coverage was not repeated solely for this merge.

All **19 promo compositor tests passed**, zero errors/skips, in 0.785s with
`target/promo-validation-venv/bin/python -m unittest tools/media/test_promo_kit.py`
(Python 3.12, NumPy 2.5.4, Pillow 13.0.0). The initial system-Python invocation
failed at import before tests because NumPy was absent. The temporary dependency
environment was created under the owned checkout's `target/`, holding its
worktree lock and using no package download cache; no system package or source
was changed. All four changed skill mirror pairs are byte-identical, and both
changed Python sources parse. The environment is disposable task output.

## Trace-boot order attribution

Continuation batch 8 reported two errors in
`TraceCaptureBootDimensionsTest.realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int)[1]`
and `[2]`: `IllegalStateException: Unable to initialize GLFW`, at
`HeadlessGameBoot.java:329` / test line 41, before the width assertions.
`TestSurfacelessEglContext` clears the JVM backend property in its existing
`@AfterEach`. Both test classes and the boot owner are byte-identical between
pre-task `8aa692b04` and composed `ba786af5e`. This repeats the documented
[backend-property hazard](../../../agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output),
and was attributed for this collection by the matched checks below.

The exact same bounded alphabetical pair was run on baseline and current:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  MAVEN_ARGS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 "$QUEUE_WRAPPER" \
  --normal -Dmse=off -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestSurfacelessEglContext,TraceCaptureBootDimensionsTest' \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test
```

Baseline used `${MAIN_WORKTREE}/tools/testing/maven_queue.py`;
current used `tools/testing/maven_queue.py` from its checkout. Those wrapper
sources are byte-identical; the Maven selection, flags and environment above
were identical.

| Source | Session | Tests | Failures | Errors | Skips | Queue wait | Maven including compilation |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Clean baseline `8aa692b04` | `20507` | 5 | 0 | 2 | 0 | 27s | 30.919s |
| Current `ba786af5e` | `52420` | 5 | 0 | 2 | 0 | 70s | 28.582s |

Both runs passed the backend-selection class's three cases and reproduced both
exact boot-dimension error identities, type, message and source lines. The four
XML reports were inspected and consumed by their sole observers. Baseline source
remained clean and unchanged; no desktop fallback or unrelated helper repair
was used. This attributes the errors to inherited order sensitivity. It does not
claim those two width checks passed in the broad JVM.

A fresh current JVM selecting only the two dimension cases passed **2 tests,
zero failures/errors/skips** at `ba786af5e` (session `96373`, 7s admission wait,
27.528s Maven including test compilation):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TraceCaptureBootDimensionsTest' \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test
```

Its XML was inspected and consumed. This verifies native gameplay camera width
and wide presentation/cache capacities in the selected EGL path. It does not
erase the two historical broad-JVM errors or the separate HCZ test's GLFW limit.
No production/helper code changed for this attribution.

## Completed continuation and guard follow-up

Session `11181` ended with exit 1 after **all 13 remaining ordinary batches and
the full guard lane completed** at unchanged `3055eb336`, with the original
source fingerprint preserved. It was not cancelled or timed out.

| Collection at `3055eb336` | Candidate batches | Tests | Failures | Errors | Skips | Maven execution excluding admission |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Original prefix | 59 | 27,972 | 3 | 0 | 85 | 3,582.89s |
| Exact continuation suffix | 13 | 9,519 | 0 | 2 | 8 | 2,639.12s |
| All ordinary coverage | 72 | 37,491 | 3 | 2 | 93 | 6,222.01s |
| Fresh full guard lane | 1 | 718 | 7 | 0 | 0 | 273.60s |

Original and continuation preparation took 27.88s and 27.82s respectively,
separate from these lane durations. Queue waiting is excluded. The prefix and
suffix partition the original 3,577-class plan without overlap or omission;
the 718 guards are separate from ordinary smoke. The later focused checks
overlap this collection and must not be added as unique-case totals. This is a
completed red smoke/guard collection followed by qualified fixes and attribution,
not an all-green or release-only full-suite result.

All eight continuation skips were inspected, none omitted: three explicit SOZ
capture flags, slot-glass native opt-in, two external SecondStrike mod prerequisites,
a missing local deterministic BizHawk audio reference and an unrequested local
audio timeline capture. No continuation skip was caused by a missing base ROM.
The two continuation errors are the exact trace-boot order errors attributed
above; the current isolated two-case dimension check passes.

The seven historical guard failures comprise:

- Three singleton-closure rules reported the old tile diagnostic bootstrap/raw
  singleton calls; two of those rules also reported inherited
  `MenuAudioProbeTool.java:41` audio singleton access. The diagnostic owner was
  repaired in `7462543b5`; the imported upstream source removes the Menu probe
  access. The 44-case closure/probe check above already passed at that state.
- `TestArchUnitRules.shared_code_does_not_construct_concrete_sonic_provider_classes`
  reported three `HubMusicController.openRomSession` constructors at line 118;
  `shared_layers_do_not_depend_on_game_specific_packages` reported nine related
  constructor/enum dependencies at lines 118/120. The imported upstream Hub music
  owner changes these sites; the current focused guard check below covers them.
- `TestNativeImageResourceGuard.everyRuntimeResourceIsReachableFromTheNativeImageConfig`
  reported missing `META-INF/services/com.openggf.level.resources.AuthoredLevelDecoder`.
  Its native-resource configuration was changed by imported upstream; the
  current focused guard check below covers it.
- `TestArchitecturalSourceGuard.objectManagerFacadeStaysWithinExtractedCollaboratorBudget`
  found **3,106 effective lines against the unchanged 3,086-line budget**. This is
  task-owned growth and required a real collaborator extraction, without raising
  the budget or compressing formatting.

The lead ran bounded rendering/semantic checks and the four affected
guard classes after the extraction, as recorded below. No broad ordinary repeat
is needed for a cohesive rendering delegation with the same callbacks/order and unchanged
non-render behavior. Guard rules and budgets remain authoritative.

## Final guard repair and qualification

Commit `d4c5735bb1404c37477a9528e0397b967ed01274` moves cohesive bucket submission
into the existing `ObjectRenderParts` collaborator. It owns the same reusable
command list and injected callback/services/graphics references; ordinary and
priority callbacks retain the same capture shapes. Empty-bucket returns and
vertical-wrap `try/finally` scopes remain in `ObjectManager`. Native SAT versus
painter ordering, mask transitions and debug command enqueueing are unchanged.
The computed facade size falls **3,106 → 3,073 effective lines**; the unchanged
3,086-line guard passes. No guard scope/budget, public API or gameplay timing was
changed. This adds a manager-owned collaborator, not per-frame containers/copies.

The exact two-file extraction was frozen on `ba786af5e`, reviewed, then verified
with the following commands. Source digests were checked unchanged before
committing it as `d4c5735bb`; no executable edit followed these checks.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestObjectManagerMultiBucketRenderable,TestObjectPriorityBucketGuard,TestPresentationPatternBatching,TestGraphicsManagerPatternAtlasPageOrdering,TestGraphicsManagerSpriteSatReplay,TestObjectHeadPresentation,TestPresentationSpritePixels,TestPresentationViewport,TestS3kInvisibleObjectRenderIsolation,TestNativeObjectQueries,TestNativeObjectSlotOccupancy' \
  "-Dsonic1.rom.path=${ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test

env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS -u MAVEN_ARGS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestProductionSingletonClosureGuard,TestArchUnitRules,TestArchitecturalSourceGuard,TestNativeImageResourceGuard' test
```

The first command (session `40017`) passed **51 tests in 11 classes, zero
failures/errors/skips**, including all five actual presentation-pixel cases,
viewport, head composition, native SAT ordering and slot-query checks. Admission
waited 3s; Maven including compilation took 1:18. The second (session `9453`)
passed **147 tests, zero failures/errors/skips**: 42 singleton closure, 72
architectural source, 32 ArchUnit and one native-resource case. Admission waited
95s; Maven took 40.052s. All 15 XML reports were inspected and consumed. Every
historical guard-failure identity is covered by this passing current selection.

All requested ordinary batches and guards are accounted for. Task-owned rewind
harness, diagnostic ownership and facade-size regressions have passing focused
qualifications; imported upstream fixes have passing consumer/guard checks.
The historical collection retains its matched GLFW/order limitations and 93
ordinary skips. It is not presented as a passing broad run at a merged SHA.
No additional viable material candidate remains in the bounded discovery scope;
reopening rejected tile/OOZ/trigonometry work needs fresh profile evidence.
Both consumed category-run directories, the owned continuation driver and the
media-test environment have been removed; no validation or timing job remains.
