# Native object query and collision allocation follow-up, 2026-10-22

This wave composes with the [initial pipeline work](2026-10-22-object-pipeline-performance.md),
[FBZ/SOZ work](2026-10-22-fbz-soz-cpu-performance.md), and
[tile publication work](2026-10-22-sprite-tile-reuse.md).
The pinned pre-task integration base is `8aa692b045c195ef8a42896b6bbc9549158470c0`.
The local integration branch is `bugfix/ai-object-pipeline-performance-integration-20261022`.

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
empty/reused slots, failure prefixes and rewind; their affected rerun is pending.

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
selection is still pending. The delivery lead owns one final change-based smoke
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
CPU preparation result. The tile owner is removing the hint/reuse paths and
preserving exact measured evidence in the existing tile audit. These are bounded
CPU preparation observations, not whole-engine FPS or GPU measurements.

The last discovery pass found no further material safe CPU candidate after
reviewing the accepted OOZ snapshot change, including its nested callbacks,
oversized teams, cleanup and native/extension ordering. The omitted-extension
snapshot remains mutation-safe; removing its occasional copy lacks a measured
material-cost case. This is a bounded discovery stopping decision, not a claim
that every possible engine optimization has been exhausted.

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
-Dsonic1.rom.path=${ROM_ROOT}/s1.gen -Dsonic2.rom.path=${ROM_ROOT}/s2.gen
-Ds3k.rom.path=${ROM_ROOT}/s3k.gen test`. The existing absolute ROM files were
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
