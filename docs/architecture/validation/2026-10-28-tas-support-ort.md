# S3K ORT support qualification — 2026-10-28

Stream: `ort`; scenario: `s3k-ort:1`. Assigned base:
`8945c0a789b27be57b2361c70f561f31872a76ca`.

## Initial assessment and historical evidence

At the assigned base, placement advances layout cursors before queued
construction and admits one live object per source placement. The backward
layout/respawn mismatch is still missing. This is an actual mechanism gap,
not an obsolete limitation test.

The 2026-10-26 audit remains valid historical negative evidence: native duplicate
count expected two B instances and observed one. See
[the historical assessment](2026-10-26-tas-supportability.md#ort-allocation-failure-duplicate-spawn).
The original synthetic layout begins with A, so repeated backward recovery can
address the byte before the declared ORT. Qualification uses an earlier declared
layout entry to keep every exercised respawn byte within the native table;
there is no invented value for adjacent memory.

## Native rationale

Reference: immutable `docs/skdisasm` commit
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, read from the main checkout's
submodule object database without changing the unrelated dirty reference tree.
Original `${TAS_ROM_ROOT}/s3k.gen` is 4194304 bytes,
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.

`loc_1B892` (sonic3k.asm:37649-37671) decrements the layout cursor and ORT
cursor before calling the load helper. The helper consumes six layout bytes on
both skip and creation. A successful allocation that consumes the final available
SST causes `CreateNewSprite4` (sonic3k.asm:37934-37944) to return failure while
the object just created remains live. `loc_1B8A4` then advances the layout pointer
another entry and the ORT pointer one byte. They no longer identify the same
source index. Fully full admission exits before the loop and creates no drift.

`loc_1B8D2` (sonic3k.asm:37685-37718) advances the forward ORT cursor after
the helper and saves its already advanced layout pointer. Forward exhaustion
therefore parks on the next entry without introducing this backward mismatch.
The following pass allocates from the first empty SST again; within a pass,
`CreateNewSprite4` searches only later slots.
`loc_1BA92` (sonic3k.asm:37891-37932) tests the selected ORT byte and stores its
address in the live instance, independently of its layout source.

## Implementation

The two-axis profile advances X cursors synchronously with actual allocation.
Front/back ORT offsets remain independent of layout positions; the existing
`fwdCounter` / `bwdCounter` snapshot fields carry those offsets without an
8-bit mask. Their remaining consumers were audited: S1 routines and supplemental
Stageplay counter admission are guarded by counter mode, and public cursor
diagnostics return before reading them outside that mode. There is no snapshot
or public object API change.

Each admitted occurrence retains every canonical immutable placement field,
including its source layout index and mod ownership. Only the Java map-key
identity differs. Mutator and direction decisions use the canonical source.
Existing monotonic object ordinals keep graph IDs unique. A weak immutable-key
interner uses the canonical source index, actual ORT byte, and preallocated SST
slot so restore-forward recreates the same occurrence key while a live object or
snapshot retains it. It neither allocates gameplay identities nor retains retired
keys; equal source values alone never identify a live instance. Each object's
existing captured `respawnStateIndex` owns the actual high/low-bit address; restore rebuilds
occurrence associations after restoring those fields without rewriting the
captured table. Ordinary unload clears the selected byte, and player kills leave
it set.

Parent approved the additional narrow solid-contact helper ownership. Equal
record-valued occurrences would otherwise alias `HashSet` standing/pushing keys.
Explicitly registered noncanonical occurrences use the existing instance-owned
key and restored slot identity. Canonical ordinary keys remain unchanged.
Ownership remains available until object-side latch expiry, and duplicate
retirement does not evict the equal canonical source key. No collision algorithm
or player-slot delete semantics changed.

Exactly two free slots also exercise native drift: B loads, then A consumes the
last SST and triggers recovery even when A is the last newly exposed entry. A
later pass duplicates A while B stays single. The acceptance tests pin this
control rather than assuming all two-free passes finish normally.

## Verification and source attribution

Disposition: `s3k-ort:1` has a qualified local native mechanism. No new scenario
ID was introduced. Historical negative evidence remains valid for its old source;
the four-boss/results publication route is not certified.

Final owned Java/test content fingerprint: `79455c63d67e69c69a71ce7836bb348a64dd9ae487d94fe46b6dbc42f9deae7e`.
This is SHA-256 over the six owned Java files, sorted by relative path, with
big-endian 4-byte pathname length and 8-byte content length prefixes. The final
engine source is the source exercised by the nine-case run and storage recheck.
Subsequent changes only corrected the failed contact fixture and its diagnostic;
the unchanged passing cases are reused below.

- Initial candidate: **8 cases, 6 pass, 2 fail, 0 errors/skips**. Strict replay
  reported a different immutable occurrence-key reference; the first solid
  checkpoint did not seat its participant. This request was prepared as a fresh
  red check but had not executed before implementation was applied. It compiled
  the prior Y-scan getter, confirmed from class files, and is **not a baseline red**.
- Consumer selection: **177 cases, 175 pass, 2 fail, 0 errors/skips** across 14
  classes. The two failures were
  `TestNativeCompetitionUpfrontPlacement#explicitSpawnResyncReinitializesFromReplacementSourceAndKeepsScratchSeparate`
  and `#sharedSeparateScratchStateHasNoLoadLatchAndRestoresWithoutEightBitTruncation`:
  scratch index expected 300, observed 768. The unneeded expansion to all `$300`
  physical-ROM table bytes was rejected. Original `max(256, layout size)` storage
  was restored: in-scope drift only addresses bytes at/before the declared source
  index. No test assertion was changed. All five original-ROM Competition layout
  controls executed without skips.
- Corrected ORT selection: **9 cases, 8 pass, 1 fail, 0 errors/skips**. Native
  zero/one/two-free controls, both X directions, multiple pending entries,
  logical-SST fallback, unload/reload, strict restore-forward and cyclic graphs
  passed. Duplicate-solid standing, unseating, removal and restoration also
  reached their controls; its later pushing checkpoint did not reach contact.
- Storage recheck: **14/14 pass, 0 errors/skips**, covering the 12 Competition
  upfront cases and two lower-respawn-bit cases, including byte indices above 255.
- Failed-contact diagnostic: **1 fail, 0 errors/skips**. Native output was
  `ContactKind.NONE`, `visible=false`, actor `(800,256)`, player `(785,237)`,
  grounded, X speed 256 and Y speed zero. The fixture had bound camera focus to
  the player's initial position, which relocates both live and copied camera
  coordinates. A later update restored X but lost the declared Y=$80 window.
- Corrected contact case: **1/1 pass, 0 errors/skips**. Restoring the declared
  camera window after focus binding reaches real side contacts. Both participants'
  standing/pushing bits, independent unseat/removal, restored slot keys, distinct
  graph IDs and player-slot preservation pass. The offscreen gate and collision
  algorithms remain unchanged.

Thus all **186 distinct focused cases** are qualified through these bounded runs:
177 consumer cases and nine ORT cases. Rechecked cases are not counted twice.
Unchanged consumer and eight passing ORT cases were reused; the affected storage
and failed contact checks were rerun. This is focused qualification, not a smoke
or release-suite pass. Parent owns combined smoke, four mandatory S3K gates,
central classification/changelog/discrepancy updates and integration.

The weak interner repairs strict replay without relaxing snapshot comparison.
The solid fixture uses a native 320x224 viewport, places its Y=$100 actors inside
that viewport with camera Y=$80, preserves the window after focus binding, and
advances the contact frame before airborne unseat. Visibility and actual contact
outputs are reached controls. No duplicate count, boss/result state or native
physics output is seeded. Different object tick counts come from their actual
birth/execution passes.

### Commands (local path prefixes parameterized)

All commands ran from the assigned worktree. Machine-local user-home prefixes
are parameterized to obey repository resource policy: `TAS_ROM_ROOT` identifies
the original ROM directory and `TAS_MAIN_CHECKOUT` the original main checkout
(their local values are supplied in the private handoff). All ROM arguments were
absolute at execution; flags and selectors below are the executed arguments.
Test stdout/stderr was temporarily
redirected under its `target/` and consumed/removed; no result archive is retained.
Java home identifies Arch OpenJDK `25.0.5.0.2`. Lua was not needed by these cases.

Build-only command, run twice before the final fixes (Maven 1m31s and 1m30s):

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile -B
```

Initial candidate:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestTasS3kOrtExhaustion test -B
```

Consumer selection:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic1.rom.path=${TAS_ROM_ROOT}/s1.gen -Dsonic2.rom.path=${TAS_ROM_ROOT}/s2.gen -Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen -Dtest=TestObjectPlacementManager,TestObjectManagerVerticalPlacement,TestPlacementLowerRespawnBits,TestObjectPlacementControllerS1Counter,TestS1CounterPlacementRememberedParity,TestMutatorPlacementAdmission,TestNativeCompetitionUpfrontPlacement,TestObjectManagerRewindSnapshot,TestTwoPhaseRestoreOrdering,TestObjectManagerChildSlotAllocation,TestNativeObjectSlotOccupancy,TestPlacedChildSlotReservations,TestObjectReservedSlotConstruction,TestSolidObjectManager test -B
```

Nine-case ORT selection:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen -Dtest=TestTasS3kOrtExhaustion test -B
```

Storage recheck:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen -Dtest=TestNativeCompetitionUpfrontPlacement,TestPlacementLowerRespawnBits test -B
```

Contact diagnostic and corrected recheck (same command, corrected fixture):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen '-Dtest=TestTasS3kOrtExhaustion#duplicateSolidStandingPushingAndRetirementKeepIndependentPlayerBits' test -B
```

### Timing and citation checks

Official queue telemetry, in seconds (combined interval is queue plus execution;
intervals overlap and must not be summed as delivery elapsed time):

| Run | Queue | Execution | Combined recorded interval |
| --- | ---: | ---: | ---: |
| Initial ORT candidate | 2861.5 | 88.4 | 2949.9 |
| Consumer selection | 3145.9 | 102.4 | 3248.3 |
| Nine-case ORT | 2195.9 | 93.3 | 2289.2 |
| Storage recheck | 257.5 | 35.9 | 293.4 |
| Contact diagnostic | 4.9 | 34.5 | 39.4 |
| Corrected contact | 109.5 | 34.9 | 144.4 |

Unmetered preparation and whole-delivery wall time are unknown. Admission waits
were resource delays, not failed tests. No resource limits or queue locks were
changed. Maven reported 1m27s, 1m41s, 1m32s and 34.170s for the initial, consumer,
nine-case and corrected-contact runs respectively. These are not performance
measurements.

Citation checking used the immutable pin and the main checkout's object database:

```bash
OPENGGF_SKDISASM_GIT=${TAS_MAIN_CHECKOUT}/docs/skdisasm python3 tools/disasm/disasm_citations.py check src/main/java/com/openggf/level/objects/ObjectPlacementController.java src/main/java/com/openggf/level/objects/ObjectManager.java src/main/java/com/openggf/level/objects/ObjectSolidContactController.java src/test/java/com/openggf/level/objects/TestTasS3kOrtExhaustion.java docs/architecture/validation/2026-10-28-tas-support-ort.md --rev skdisasm=8b2cf3c4178f2382f844da67798de4befc50c5e0
```

Whole-file checking reports inherited citation debt. A bounded matched check with
`parse_file` / `classify` from the same checker compared those files at base
`8945c0a789b27be57b2361c70f561f31872a76ca` against current content: **43 stale,
two missing-file and one out-of-range findings on each**, with **zero new hard or
stale findings**. New citations were independently checked against the immutable
S3K pin. Unrelated dirty references and inherited citations were preserved.

## Route and memory limits

Qualification is bounded to the reached native allocation/recovery mechanism,
ordinary placement/slot controls, per-instance contact outputs, and strict
restore-forward graphs. It is not a four-boss or results publication replay.
Actual AIZ2 boss combat, capsule/results progression, and publication timing have
not been qualified by this stream. Existing level/route obligations are inherited;
no act/character-route certification is added. The engine's existing manual
large-relocation/negative-viewport catch-up policy is retained; carrying ORT
desynchronization through those rebuilds was not qualified.

No physics or auxiliary trace rows initialize or synchronize gameplay. No CPU,
VDP, bus, or arbitrary adjacent-RAM execution was introduced. Respawn addresses
outside the modeled table are explicitly rejected. KosM adjacent-memory overwrite
and full publication completion remain excluded.
