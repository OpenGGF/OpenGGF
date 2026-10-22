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
