# S3K AIZ Miniboss Napalm Evidence Implementation Plan

Date: 2026-08-09

Design:
`docs/architecture/designs/2026-08-09-s3k-aiz-miniboss-napalm-evidence.md`

## Files

Create:

- `src/test/java/com/openggf/game/sonic3k/objects/TestAizMinibossNapalmProductionRoute.java`

Modify for tests and evidence:

- `src/test/java/com/openggf/game/sonic3k/objects/TestAizMinibossNapalmRoute.java`
- `docs/status/s3k-known-bugs.md`
- `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`
- `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`
- `docs/architecture/research/s3k-zones/aiz-analysis.md`
- `docs/guide/playing/game-status.md`
- `README.md`
- `CHANGELOG.md`
- the design and this plan

Modify only if the exact-identity regression is red:

- `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`
- `src/main/java/com/openggf/game/sonic3k/objects/AizMinibossRewindLinks.java`
- `src/main/java/com/openggf/game/sonic3k/objects/AizMinibossNapalmProjectile.java`
- `src/main/java/com/openggf/game/sonic3k/objects/AizMinibossBarrelShotFlareChild.java`
- another AIZ miniboss linked class only if the same new assertion proves its
  structural edge wrong; do not broaden the rewind refactor speculatively

## 1. Pin the environment, oracle, and green baseline

Use Maven's JDK 21 runtime and the canonical locked-on ROM. The ROM property is
`s3k.rom.path`; its SHA-1 must be
`CFBF98C36C776677290A872547AC47C53D2761D6`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
mvn -v
sha1sum "$S3K_ROM_PATH"
python3 tools/traces/validate_trace_v5.py \
  src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/aiz_3
mvn -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Dtest='com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProjectile,com.openggf.game.sonic3k.objects.TestAizMinibossNapalmRoute,com.openggf.game.rewind.TestS3kAizMinibossGraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard' \
  test
```

Expected baseline: Maven reports Java 21; the SHA-1 and trace-v5 validator
match/pass; the existing focused suite runs 14 tests with zero failures,
errors, or skips. Preserve that result before adding tests.

## 2. Replace the reflection-only activation proof

In `TestAizMinibossNapalmProductionRoute`, load AIZ zone 0, zero-based act
index 1 with `SharedLevel`. Build an isolated S3K `ObjectManager` over the live
ROM-backed level terrain, put a real `AizMinibossInstance` in dynamic slot 12,
set the camera to native arena `(0x10C0,0x0450)`, and install a real
`AizZoneRuntimeState` for the character under test. Do not invoke a private
callback or write parent bit 1.

Add `liveBossUsesNativeWaitEntriesAndGatesNapalmToKnuckles` first. Its entry
oracle is exact:

- after the trigger installs literal `#180`, routine `WAIT` executes
  `Obj_Wait`; the callback fires on WAIT entry 181, not entry 180;
- after that callback installs literal `#$AF`, routine `DESCEND` executes
  `MoveSprite2` plus `Obj_Wait`; the callback fires on DESCEND entry 176;
- after that callback installs literal `#20`, routine `SWING` executes swing,
  movement, and `Obj_Wait`; bit 1 first becomes visible on SWING callback entry
  21 and is absent on entry 20;
- Knuckles reaches bit 1; a separately constructed Sonic-alone scenario drives
  past the same callback and never sets bit 1 or creates a FallingShot.

Run this method before any production edit:

```bash
mvn -Dmse=off "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Dtest='com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProductionRoute#liveBossUsesNativeWaitEntriesAndGatesNapalmToKnuckles' \
  test
```

Expected current result is green if the audited boss wait implementation is
correct. If it is red, retain the failing assertion, reduce the mismatch to the
cited ROM dispatch, and make only that source-owned correction. Once green,
delete the reflection-driven
`TestAizMinibossNapalmRoute#knucklesGateIsSetAtStartOfThirtyFrameFlameDelayOnly`
and its now-unused private invocation helper. Keep the synthetic allocation and
slot-exhaustion tests, but do not present them as live gate evidence.

## 3. Prove native allocation, impact, and collision lifetime

Add these methods to the same production-route class, one at a time, running
the class after each addition before changing production:

1. `liveBarrelsSpawnNativePairsAtActivationEntriesAndAfterCurrentSlots`
   observes pair creation at activation-relative entries 32, 48, and 64. For
   each barrel subtype 0, 2, and 4, assert flare slot greater than barrel slot,
   FallingShot slot greater than flare slot, child subtype `$02`, collision
   `$98`, and current/post-movement touch publication. The absolute native
   frames 4486, 4502, and 4518 remain comparison-only fixture evidence.
2. `romTerrainImpactsSpawnNativeExplosionGraphAndCollisionWindows` lets all
   shots run through `ObjectTerrainUtils` against the loaded AIZ2 terrain. The
   first two fixed-fixture comparison coordinates are `(0x10E4,0x0506)` and
   `(0x1134,0x0504)`; do not fit terrain or production constants to them. At
   each impact assert the seven ROM offsets, subtypes 0 through 12 by two,
   ascending after-current allocation with occupied slots respected, zero
   collision before routine-4 animation publication, `$97` during the harmful
   window, four-entry subtype staggering, the exact `AniRaw_BossExplosion`
   lifetime, and destruction after the last animation dispatch.

```bash
mvn -Dmse=off "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Dtest='com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProductionRoute' \
  test
```

Expected result for each slice is green on the current production path. Any
red result is the TDD gate: preserve it, cite the owning disassembly branch,
apply the narrow fix, and rerun to green before continuing.

## 4. Force an exact rewind-identity RED -> GREEN slice

Add `rewindPreservesExactPerBarrelGraphAndForwardEvolution` to the production
route test. Use the real objects produced above, not manually constructed
representatives.

Capture two manager snapshots. The first is after all three FallingShots exist
and before they consume their source barrel's `$39` position counter/facing;
the second is in the later mixed projectile/explosion phase, when at least one
FallingShot has moved far enough that geometric-nearest reconstruction cannot
identify its owner reliably. Before each capture, record slot and `ObjectRefId`
for the boss, all three barrels, each live flare, and each FallingShot.

Immediately after restore, before any replay update, resolve the objects by
their restored slots/rewind IDs and use `assertSame` for every structural edge:

- each subtype-0/2/4 barrel's parent is the restored boss;
- each flare's anchor is the exact restored barrel that created it;
- each FallingShot's parent is the restored boss and its barrel is the exact
  restored subtype-0/2/4 source barrel, not another member of the trio.

Then replay forward and compare per-barrel `$39` counters, selected drop X/Y,
slot/type/subtype/collision state, explosion evolution, and destruction with
the first run. The pre-consumption snapshot proves source-state continuation;
the later snapshot proves identity after the projectile has left its barrel.

Run the method before editing production:

```bash
mvn -Dmse=off "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Dtest='com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProductionRoute#rewindPreservesExactPerBarrelGraphAndForwardEvolution' \
  test
```

Expected pre-fix result is RED if the current final references reconstructed by
`AizMinibossRewindLinks.nearestLiveObject` adopt the wrong barrel after the
projectile teleports. Preserve the exact failure. Fix only the proven edges:
make the relevant projectile `parent`/`barrel` and flare `anchor` references
eligible for the existing compact-schema object-reference capture, or install
an equally narrow two-phase identity relink. `AizMinibossRewindLinks` may seed
phase-one construction, but phase two must restore the captured identity; do
not replace the bug with a distance, frame, zone, or route heuristic. Rerun the
single method to GREEN, then run `TestS3kAizMinibossGraphRewind` and
`TestRewindCoverageGuard` to prove no adjacent graph or coverage regression.

## 5. Correct current documentation without a frontier claim

Only after every production-route and rewind assertion passes:

- change the napalm entry and table-of-contents anchor in
  `docs/status/s3k-known-bugs.md` from OPEN to RESOLVED; name `aiz_3` as the
  actual Knuckles miniboss capture, remove the false claim that no such capture
  exists, and distinguish capture evidence from the production test;
- mark the napalm P0 row resolved in
  `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md` and Wave 1
  resolved in `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`;
- add the exact follow-up evidence/tests to
  `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`;
- refresh the current napalm paragraph in
  `docs/architecture/research/s3k-zones/aiz-analysis.md`, the S3K known-gap
  wording/date in `docs/guide/playing/game-status.md`, the S3K status row in
  `README.md`, and the current napalm bullet in `CHANGELOG.md`;
- state in every current-status claim that no 68-segment Knuckles run-chain
  replay exists. Do not edit `docs/status/trace-frontier-log.md`: no replay
  frontier moved, and the `aiz_3` aux stream remains comparison-only.

## 6. Final verification, review, policy, and commit

Run the complete focused gate under JDK 21:

```bash
mvn -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
  "-Ds3k.rom.path=$S3K_ROM_PATH" \
  -Dtest='com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProjectile,com.openggf.game.sonic3k.objects.TestAizMinibossNapalmRoute,com.openggf.game.sonic3k.objects.TestAizMinibossNapalmProductionRoute,com.openggf.game.rewind.TestS3kAizMinibossGraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.sonic3k.TestSonic3kObjectArtProvider,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' \
  test
python3 tools/traces/validate_trace_v5.py \
  src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/aiz_3
git diff --check
git add CHANGELOG.md README.md docs src/main src/test
./.githooks/run-policy pre-commit
```

Expected result: zero focused failures/errors, valid trace-v5 fixture, clean
whitespace check, and a passing staged-content policy check. Inspect the staged
set so no ROM, generated output, uncompressed trace, or unrelated file is
included. Obtain independent code/documentation review and resolve every
blocker. Commit on `feature/ai-aiz-miniboss-napalm-evidence` with all required
trailers. Do not merge or push.

## Execution outcome

The three live-route slices were green on the existing gameplay path. The
rewind identity method was red with a FallingShot linked to the wrong restored
barrel, then green after the narrow captured-reference policies described in
the design. `AizMinibossRewindLinks` required no edit. The obsolete
reflection-only negative gate test was removed after its production-route
replacement passed. Final command results and policy status are recorded in
the task handoff rather than inferred here.
