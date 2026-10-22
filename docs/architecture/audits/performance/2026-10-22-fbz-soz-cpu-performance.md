# FBZ squeeze and SOZ spring-vine CPU work, 2026-10-22

Bounded implementation wave based on `8aa692b045c195ef8a42896b6bbc9549158470c0`,
in `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-548b22c8`, branch
`bugfix/ai-fbz-soz-cpu-performance-20261022`. The lead owns combined release
notes, smoke/domain coverage and integration. This wave changes only the FBZ
squeeze adapter/traversal and SOZ spring-vine parent/display, with focused tests.
No gameplay formula, native limit, collision boundary or timing constant changes.

## Changes and deterministic evidence

- FBZ ARMED frames that cannot apply the donation floor return before object
  discovery: inactive donation, spindash capability, incompatible rolling/input,
  nonpositive speed, or speed at/above `$0800`. Existing null rules/capabilities,
  dead and hurt guards remain ahead of this logic. CONSUMED still executes exact
  discovery regardless of donor/capability/input changes, retaining its original
  exact-episode rearming condition, including stale episodes.
- The traversal queries/sorts elevator cars once per discovery instead of once
  per eligible invisible block, and retains zero car queries when no eligible
  block lies ahead. It preserves block/car order, exact-class/live
  predicates, geometry, nearest-danger tie rejection, and the 512-frame native
  simulations. `findEpisode` retains its existing signature and selection. A new
  engine-only `ProjectedEpisode` result bridges the object/event packages and
  carries the winning current/native projections: eligible application no longer
  repeats those two simulations after discovery.
- SOZ generation caches pivot/tension. Matching nonzero inputs bypass the
  96-byte generator, including its divisions; matching display inputs bypass
  eight Y calculations/writes. Caches are transient and invalidate through
  `afterGenericRewindStateRestored`, so they never override captured surface RAM
  or child Ys. Pivot zero still skips generation and child distribution entirely,
  as in `sub_40A08` (`sonic3k.asm:85768-85839`). Construction initializes the
  surface; new/recreated display children begin uncached. The immutable fixed
  `SolidObjectParams` is shared. `getSlopeData` still makes a defensive copy.
- Focused tests exercise the actual adapter entrypoint and count zero traversal
  calls in ineligible ARMED play, one projection per speed in eligible play,
  one car query per multi-block search, retained projection identity and tie
  rejection. SOZ tests exercise 256 unchanged production updates, actual work
  methods returning no regeneration/distribution, crossing afterward, defensive
  slope ownership, replacement display slots, pivot-zero restore and cache
  invalidation. No timing or percentage claims are made.

## Rejected or deferred alternatives

- Gating CONSUMED discovery with ARMED eligibility would change rearming after
  donor/input/speed changes. Retained exact discovery instead.
- Caching a direct display-child reference or skipping the slot scan on unchanged
  parent inputs would miss a replacement Display in the saved slot. A focused
  replacement test requires distribution to the new occupant even when the
  parent's surface is unchanged. The scan remains an explicit follow-up for the
  lead's separately owned live-slot query helper; this branch has no dependency
  on that uncommitted helper.
- Removing the 96-byte slope clone would expose mutable collision state. Kept
  the defensive contract and tested mutation of a returned array.
- A local `sampleSlopeByte(int)` override is viable as a next wave: the provider
  default clones `getSlopeData()` before signed sampling, and
  `ObjectSolidContactController.java:4173` calls
  `slopedAdapter.provider().sampleSlopeByte(sampleX)` directly. The adapter
  exposes `provider()`; it does not forward a sampling method itself. A bounded
  signed direct SOZ sample would preserve defensive bulk queries and remove only
  these per-sample clones; shape guards still clone. The lead supplied this
  finding after the four-class job had entered compilation. Per the explicit
  handoff instruction, that live job was neither canceled nor restarted and
  the sampling change remains deferred, with signed/bounds/restore checks needed.
- Projection internals, pair geometry, native frame limit and roll physics remain
  unchanged. Their allocations/work are further candidates only with separate
  evidence and ownership; this bounded wave does not approximate them.

## Validation and limits

- Hooks installed with `tools/testing/install-hooks.sh`.
- `python3 tools/testing/maven_queue.py -v`: Maven 3.10.0, Java 25.0.5;
  observed queue wait 315 seconds. Build-only compilation overlapped part of
  that interval; these durations must not be summed as elapsed delivery time.
- Two build-only invocations of
  `python3 tools/testing/maven_queue.py -Dmse=off test-compile` failed on the
  rewind-hook context import (missing, then the wrong package). Corrected to
  `com.openggf.game.rewind.schema.RewindCaptureContext` before focused execution.
  Neither failed compilation executed tests.
- The initial four-class focused command (below) executed 149 tests, with two
  failures, one error and zero skips. All 114 ROM-backed ordinary-roll cases and
  13 SOZ tests passed. The three new static-mock checks failed:
  `consumedProductionPathStillUsesExactDiscoveryAcrossDonorAndCapabilityChanges`,
  `eligibleArmedProductionPathReusesBothWinningProjections`, and
  `discoveryQueriesCarsOnceAndRetainsWinningProjectionsWithUnchangedTieRejection`.
  The CONSUMED failure came from nested real static calls during mock setup
  polluting invocation counts; replaced that setup with selective answers that
  intercept only the intended geometry method. Also added a focused assertion
  retaining zero car queries when every block lies behind the launch point.
- The first affected two-class retry executed 23 tests, with one failure, one
  error and zero skips. It resolved the CONSUMED check and isolated the two
  synthetic geometry failures: new blocks still had first-frame solid-contact
  suppression because the test omitted `snapshotPreUpdatePosition()`. Production
  correctly rejected those blocks. Added the ordinary pre-update lifecycle to
  the fixtures, including a live-solid positive assertion. This is the existing
  measurement-hazard 131, not a gameplay defect or a new ROM discrepancy.
- Final affected-class retry (`--lean`, bounded ROM-free unit checks) passed:
  23 tests, zero failures/errors/skips (11 adapter, 12 traversal), exit 0.
  Maven execution took 28.990 seconds after 26 seconds of queue waiting.
  All production code is unchanged from the first affected-class retry; only
  these test fixtures changed. The initial four-class job waited 392 seconds;
  the first two-class retry waited 321 seconds. These are separate queue waits,
  not test execution time or frame-time measurements.
- All owned current behavior has focused coverage across the completed runs:
  the final 23 FBZ unit checks plus the earlier 114 ROM-backed ordinary-roll
  cases (96 arrival phases, 15 width/donor combinations, three rewind spots)
  and 13 SOZ tests. The final lazy car-list edit is covered by the FBZ retry;
  the ordinary-roll cases never request assist discovery (RIGHT and DOWN are
  never combined), and SOZ production has not changed since its passing run.
  This is combined focused evidence, not a claim that all 150 tests ran together
  in the final command. No live jobs remain.

The commands below use `${ROM_ROOT}` for the verified external ROM directory;
set it to the existing directory, without copying or linking ROMs.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestFbzS1DonationSqueezeAssist,TestFbzMovingSqueezeTraversal,TestFbzSqueezeOrdinaryRoll,TestSozSpringVine' \
  "-Dsonic1.rom.path=${ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test

env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestFbzS1DonationSqueezeAssist,TestFbzMovingSqueezeTraversal' test

env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestFbzS1DonationSqueezeAssist,TestFbzMovingSqueezeTraversal' test
```

- ROMs discovered in `${ROM_ROOT}`: `s1.gen`, `s2.gen`,
  `s3k.gen`; SHA-1/CRC32 match the repository's required World REV01/locked-on
  identities. No copies or links created.
- Inspected change-based plan with
  `python3 tools/testing/run_categories.py --base 8aa692b045c195ef8a42896b6bbc9549158470c0`.
  It selects 3,566 smoke classes plus guards because the event adapter is
  unclassified. Per the task's bounded ownership, focused behavior/rewind checks
  replace that disproportionate local selection; the lead owns combined
  delivery validation. No smoke/full suite, trace sweep or frame-time benchmark
  was run by this implementation wave. Broader act/viewport/donor/team coverage
  obligations remain inherited; this work does not certify an entire act.

## Follow-up wave: OOZ snapshots and rejected trig candidates

The OOZ implementation in `cab1fcd05` described below was subsequently rejected
and removed after the paired real-update experiment recorded later in this audit.
The behavioral regression coverage remains.

This independent wave starts at `98fc161d2c0b2bdfbe7a9f694979ab6bf486a1ab`,
which the lead already integrated locally. It changes only the OOZ platform,
its multi-sidekick tests, and this audit. No shared query, graphics, solid-param,
physics, or ROM behavior changes.

**Cadence and structural savings.** Obj33's player-triggered WAIT mode can run
indefinitely without riders or with an off-center rider. Previously every WAIT
constructed an `ArrayList`; nonempty waits also allocated its backing storage
and invoked a stream for the center check. Two distinct instance-owned transient
stores now reuse standing and locked-player snapshots. Each retains capacity for
only eight players; this is a memory-retention bound, not a gameplay team limit.
Oversized teams and nested borrows use independent temporary lists. For an
ordinary uninterrupted pop, the unchanged fixed-point physics reaches the exact
apex on rising update 16: the old locked-snapshot helper constructed 16 lists,
with backing arrays on the 15 nonempty carry updates when all riders remain
standing until launch. These repeated list constructions are suppressed for
normal-size teams; WAIT's stream is replaced by the same ordered short-circuit
check. Indexed snapshot traversal also avoids an explicit list iterator.

The tradeoff is two scratch helpers, two lists and two eight-reference backing
arrays per platform instance. Allocation counts here describe Java source
operations, not measured heap bytes: escape analysis can remove some old lists,
iterators, streams or lambdas. The participant query and omitted-extension copy
still allocate. Net CPU/GC benefit and game-wide materiality are **unmeasured**;
no percentages or frame-time claim is made. The recurring WAIT case was the initial rationale
for this bounded allocation cleanup; source counts did not establish its net
cost. The paired experiment below supersedes that initial viability assessment. No timing ran alongside the tile worker's isolated
measurement window.

**Lifetime and behavior evidence.** Full snapshots remain in place before player
callbacks. Locking a standing snapshot can enter `onUnload`, so standing and
locked storage are separate. Unload can also nest while a carry snapshot is
borrowed; that path falls back to independent storage. All borrow sites include
population and iteration in `try/finally`, clearing references on early returns,
exceptions and normal completion. Rewind excludes scratch state and clears idle
storage; an active callback's local snapshot survives until its own finally.
P1/P2/extension order, identity lock migration, omitted-player release and exact
apex behavior remain unchanged. The existing mutation-safe
`List.copyOf(extensionLockedPlayers)` is deliberately retained. Replacing it is
an unmeasured residual candidate requiring its own materiality assessment, not
permission to iterate the live set during callbacks.

New focused checks observe the actual reusable storage through 128 empty and
128 off-center WAIT updates, verify native order during nested unload, separate
standing/release lifetimes, exception cleanup, a 13-player fallback, and captured
platform/player restore plus forward replay through all 16 rise updates. Existing
omitted-player, reordered-launch and native-coordinate/contact checks also run.

**Rejected trig changes.** S1 lamppost twirl computes 33 orbit updates and S2's
checkpoint dongle 32, each with just two trigonometric calls per update. The
16-phase byte-angle observation is correct, but these short-lived effects lack
profile evidence for a material CPU cost. S1 grassy-platform type 5 can repeat
`calcSine($40)` while held down, but that is one sine call per held update; at
angle zero its helper already returns before trigonometry. Without profile
evidence, bounded tables/caches and their exact floating-point equivalence tests
would add churn without an established payoff. All three remain unchanged; no
ROM sine table substitution or floating-point evaluation change was attempted.

**Verification.** Java 25 build gate passed. One queued focused invocation:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestOOZPoppingPlatformMultiSidekick,TestSonic2TriggerParticipation,TestOOZPlacedObjectGaps' test
```

Passed 88 tests, zero failures/errors/skips: multi-sidekick 11, trigger
participation 60, placed-object gaps 17. Queue wait was 38 seconds; Maven
execution (including compilation) was approximately 1 minute 29 seconds. These
are validation durations, not performance measurements. No ROM, rendering,
broad/full-suite or isolated timing run was needed for these bounded state and
snapshot changes. Lead-owned integration validation remains separate.

The inspected change-based plan (`run_categories.py --base 98fc161d2`) selects
3,091 smoke classes plus guards. The requested focused checks replace that
selection for this local snapshot-lifetime change; no shared algorithm or
participation policy changed. There are no running task-owned jobs.

## Java transient scalar comparison correction

Root's frozen combined smoke at `3055eb336` exposed `generatedPivot: 96 -> -1`
in the SOZ object probe and identity ratchet. Inspection of the owning runtime
path found that `AbstractObjectInstance.captureRewindState(context)` uses
`GenericFieldCapturer` (generic or compact sidecar), then restoration applies
that sidecar before `afterGenericRewindStateRestored`. Java transient fields
are explicitly excluded by `GenericFieldCapturer.isSkipped`,
`RewindSchemaRegistry.policyFor`, and the documented
`RewindCoverageAnalyzer.isUncapturedFinalScalar` contract. Invalidating the SOZ
cache after restoring authoritative surface RAM is valid.

The harness scalar walker skipped static/synthetic fields but compared Java
transients. It now also skips Java transient fields uniformly, with no class or
field-name exceptions. Production SOZ cache shape and invalidation are unchanged;
root's independent display-query/direct-sample changes need no conflicting edit.
A real round-trip control invalidates a transient cache and passes. A deliberately
broken fixture drops its authoritative sidecar and restores a zeroed scalar;
the same harness still reports exactly its non-transient `authoritative` field
as `37 -> 0` (`isFinal=false`). No authoritative exclusion was introduced.

Queued focused verification (existing external ROM directory substituted below):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestEveryObjectRewindRoundTrip,TestRewindHarnessCoverageRatchet,TestSozSpringVine,TestRewindRoundTripHarnessFailures' \
  "-Dsonic1.rom.path=${ROM_ROOT}/s1.gen" "-Dsonic2.rom.path=${ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${ROM_ROOT}/s3k.gen" test
```

Passed 1,349 tests, zero failures/errors/skips (1,331 object sweep, one ratchet,
four failure/contract controls, 13 SOZ). Ratchet: 1,086 isolated executions,
243 graph declarations, zero unavailable prerequisites. Queue wait 17 seconds;
Maven execution 30.342 seconds. Root's live smoke source/job was untouched.

## OOZ paired real-update experiment and rejection

**Decision.** Remove the `cab1fcd05` scratch implementation: its small, mixed CPU
benefit does not justify the borrow/fallback/cleanup machinery and extra retained
storage. The production platform is restored byte-for-byte to parent
`98fc161d2`. This decision does not alter the earlier FBZ/SOZ optimization or the
transient rewind-contract repair. No further candidate discovery was attempted.

**Inputs and boundaries.** A dedicated detached cowtree at
`.worktrees/ooz-measure-20261022-cab1` owned its compiled outputs and temporary
probe. Old platform and flame sources came directly from `98fc161d2`; the only
source transformations renamed their two mutually referenced types. Reversing
both renames reproduced the originals exactly. The current arm used the actual
`cab1fcd05` classes. Both arms called `AbstractObjectInstance.update` through the
same probe callsite, with real `ObjectManager` riding-state identity checks,
`ObjectPlayerQuery`, and `TestablePlayableSprite` callbacks. Inspected timed
manager paths compare object identity and do not require the platform's concrete
type/name. Distinct class shapes still permit different JIT inlining/escape
analysis, so these are local paired microcases, not whole-engine estimates.

Cases were empty WAIT, indefinitely blocked WAIT (main rider exactly at the
exclusive +16 X boundary), and center admission plus all 16 rising updates through
release. Each ran with native P1/P2 and with two additional extension players.
The untimed oracle compared platform mode/position/fixed-point velocity/timer,
lock flags, extension membership and player positions/speeds/control/air/support
after every update, including the exact apex. Timed batches also consumed and
compared final state. Cycles reset the same instances outside counters; object
construction, reset costs, scratch retention, real scene frequency and total
one-shot lifetime cost are excluded. Cycle counters surround each 17-update
sequence, including counter overhead equally in both arms.

Three fresh Java 25 JVMs warmed six passes over all cases, then ran eight paired
rounds with alternating old/current order (also reversed by JVM ordinal). WAIT
batches contained 16,384 real updates; cycle batches contained 256 sequences.
Current-thread CPU and allocated bytes came from `com.sun.management.ThreadMXBean`.
The one-off probe remained temporary; it did not replace algorithms with a list
surrogate or introduce ROM/assets/gameplay fallback.

**Admission and failed setup.** The first exclusive invocation waited 264 seconds,
then failed before any timing: an untimed cycle reset used `setAir(false)`, whose
landing callback requires an active gameplay session. It produced no usable
performance evidence. One authorized correction used the existing fixture's
`setAirForTest(false)` solely in reset; measured production calls and the equality
oracle were unchanged. The retry waited 69 seconds, held exclusive test admission
and then exclusive build activity, and completed successfully. Both leases were
released normally; the 0.5-second interference guard reported no bypassing
build/test/profiler. Three JVMs plus driver took 1.251 seconds (isolated lease
execution 1.527 seconds), within the 30-second bound. No timing overlapped a
build/test, no foreign job/lock was canceled or changed, and root's frozen smoke
remained alive.

The prebuilt launch was:

```bash
python3 tools/performance/run_isolated.py --worktree . --cwd . \
  --audit-report target/ooz-isolation-retry.json --estimate-seconds 30 \
  -- python3 target/run_ooz_probe.py
```

**Results.** Ranges below span the three per-JVM medians of eight paired rounds;
bytes are allocated bytes per real update. All six cases passed state equality
in all three JVMs. Ratios are medians of paired current/old CPU samples, not
ratios of unrelated minimum/maximum values.

| Case | Old CPU ns/update | Scratch CPU ns/update | Old bytes/update | Scratch bytes/update | Paired CPU ratio |
| --- | ---: | ---: | ---: | ---: | ---: |
| Empty, P1/P2 | 18.53–22.67 | 21.53–26.63 | 184 | 160–184 | 0.95–1.41 |
| Empty, four players | 35.58–41.35 | 40.65–42.71 | 232 | 208–232 | 1.03–1.14 |
| Blocked, P1/P2 | 35.42–42.02 | 26.91–31.96 | 368–416 | 160–184 | 0.64–0.89 |
| Blocked, four players | 54.75–63.33 | 50.44–51.65 | 416–464 | 208–232 | 0.82–0.92 |
| Admission/rise/release, P1/P2 | 44.03–46.49 | 44.80–51.71 | 282.82–306.82 | 217.88–241.88 | 0.95–1.18 |
| Admission/rise/release, four players | 144.57–151.79 | 144.45–154.18 | 616.94–640.94 | 576–642.35 | 1.00–1.02 |

Empty WAIT retained identical allocation in two JVMs while adding CPU; this is
consistent with scalar replacement of old local storage, but does not prove a
particular JIT decision. The third JVM's different allocation shapes underline
that limitation. Blocked WAIT saved 184–256 bytes and roughly 4–15 ns per update.
The short carry/release sequence showed no consistent CPU improvement, with
variable allocation savings. These local magnitudes and tradeoffs do not support
a material runtime benefit for the additional machinery. No FPS, frame-time,
percentage-of-engine or GC-pause claim follows from these measurements.

**Retained coverage.** Scratch-identity/borrow/reference-retention assertions were
removed. The six added tests now assert gameplay invariants: repeated empty and
boundary-blocked waits before admission; callback-safe native-order carry after
nested unload; standing-snapshot survival during unload; recovery after throwing
callbacks; 13-player admission/carry/release; and capture/restore plus exact replay
of every rising update through apex. Existing omitted-player release, identity
reordering and replacement-ref coverage remains. The mutation-safe snapshots are
preserved by restoring the original production algorithm.

**Removal verification and cleanup.** Java 25 gate and the exact focused command
below passed 88 tests, zero failures/errors/skips (11 multi-sidekick, 60 trigger
participation, 17 placed-object gaps). Queue wait was 305 seconds; Maven execution
including compilation was 1 minute 30 seconds. The tested source restores OOZ to
`98fc161d2`; unchanged rewind-harness repair coverage remains the 1,349-test run
above. No broad/full suite was rerun; lead-owned source-frozen smoke is separate.

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestOOZPoppingPlatformMultiSidekick,TestSonic2TriggerParticipation,TestOOZPlacedObjectGaps' test
```

The owned measurement identity/workspace/24-hour expiry and terminal job states
were registered under the local measurement ownership directory. After checking
unchanged tracked files and the three expected generated Java sources, the owned
detached cowtree and its raw samples/probe/build outputs were removed. No foreign
outputs, jobs, locks, ROMs or implementation worktrees were removed. No task-owned
measurement or validation job remains live.
