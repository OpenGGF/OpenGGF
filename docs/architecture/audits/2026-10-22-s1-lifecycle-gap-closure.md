# S1 bonus, scroll entry and rewind closure — 2026-10-22

Implementation lane `s1_lifecycle`, based on develop
`94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`, in
`bugfix/ai-gap-s1-lifecycle-20261022`. Integration and combined smoke belong
to the swarm lead. This record establishes bounded contracts, not completion
of every S1 route, viewport, donor or team combination.

## ROM decisions

The supplied S1 World REV01 ROM is 524,288 bytes, CRC32 `AFE05EEE`, SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`. Its `Bonus_Points` table at
`0x179BE` contains `0000 03E8 0064 0001`. `AddPoints` consumes tens of
points, so subtype 3 awards **10**, while keeping mapping frame 3's **100**
popup. The shipped `FixBugs=0` branch deliberately preserves this mismatch.
Changing the popup or awarding 100 would implement the optional fixed branch.

The pinned S1 reference is `28872a46e2d4c94c5c4818cbd3c04e6eb17edb67`.
Research also used the existing main reference checkout at
`f6ece657c1cf253404312137dfcb8ec15fa42318`; the owning `Bonus_Points` and
`BgScrollSpeed` branches were checked against the pinned Git objects without
changing either reference checkout.

`BgScrollSpeed` in `_inc/LevelSizeLoad & BgScrollSpeed.asm` seeds BG X
words from the **final foreground camera** only for a fresh entry. On a
lamppost entry it skips that common seed, after `Lamp_LoadInfo` restores the
six words banked by `Lamp_StoreInfo` in `_incObj/79 Lamppost.asm`. Both paths
still execute zone-specific setup:

| Zone | Words surviving a lamppost load | Setup which still runs |
| --- | --- | --- |
| GHZ | BG2/BG3 X | BG1 X and all Y/cloud state cleared |
| MZ | All three X | Y is recalculated by `Deform_MZ` |
| LZ | BG1 X | BG1 Y becomes foreground Y / 2 |
| SLZ | Inactive words only | REV01 clears BG1 X; Y becomes foreground Y / 2 + `0xC0` |
| SYZ | Inactive words only | BG1 X cleared; Y becomes foreground Y * 48 / 256 + 1 |
| SBZ/FZ | Active X words | Y becomes `((foregroundY & 0x7F8) >> 3) + 1` |

Saving full handler snapshots at a lamppost was rejected: native lamppost RAM
banks **integer words**, not fractional camera accumulators or the cloud epoch.
Those logical values are captured independently for ordinary rewind. Likewise,
unconditional fresh reseeding on a death load would destroy the native bank.

## Production ownership

`Sonic1ScrollHandlerProvider.initForZone` now dispatches the normal handler
initialization overload. A new S1 profile step follows `InitCamera`, before
level-event initialization. Internal `ParallaxCheckpointAccess` dispatches to the
provider actually retained by `ParallaxManager`: calling the module getter again
was rejected after the production regression exposed two null-bank errors,
because S1's getter creates a new provider each time. Caching that getter was
also avoided; the active parallax manager remains the provider's owner. It resets parallax's entry tracking and initializes
from `LevelManager`'s actual progression zone/act and final camera. The public
load context's zone/act fields are not populated by the ordinary load path and
must not be used here. Decode-only loads do not acquire this fresh-load step.

Checkpoint transport consists of two package-private fields and an internal
access helper carrying an immutable six-word bank. The existing checkpoint
coordinator restores that field during `RestoreCheckpoint`; the S1 profile
consumes it only when the load context actually has a checkpoint. A registered
S1 adapter captures the bank for rewind without changing the exposed
`CheckpointState.RewindState` record or any Mod API signatures.

Credits position the player and camera after the ordinary loader. Therefore
`Sonic1CreditsManager.onDemoZoneLoaded` repeats entry initialization at the
final credits camera and recomputes the scroll output before the hidden delay.
The fourth demo's `EndDemo_LampVar` bank is read from the runtime ROM at
`0x592A`: `04AB 03A6 0000 028C 0000 0000`. It is not synthesized from the
foreground camera or loaded from the development disassembly. GHZ credits 0
and 7 deliberately have different starts, making stale-seed reuse observable.

## Verification

Java 25, Lua 5.4 (`LUA_BIN=/usr/bin/lua5.4`) and PowerShell preflight passed.
No broad smoke or release suite was launched by this lane.
The broad citation scan of the existing S1 pitfall catalogue reports inherited
stale/out-of-range citations; this task's added entry introduces no numeric
citation and the pinned owning routines above were inspected directly. Foreign
reference checkouts were preserved.

Initial bonus/scroll/guard checks passed 35 tests, no failures/errors/skips.
The initial generic GHZ1 reference-closure integration passed its 3,905-frame
fixture, one test, no failures/errors/skips. Later load-boundary changes require
fresh affected checks; the final commands and outcomes are recorded below on
completion.

The generic standalone S1 replay now uses the same reference-closure gate as
S2 and S3K. This proves closure for captured references at the exercised real
route steps; it does not certify excluded final/structural fields, custom
snapshot implementations or unexecuted routes. Existing boss reconstruction
checks remain separate evidence owned with the boss lane.


Final ordinary focused command (78 tests pass, zero failures/errors/skips).
`SONIC1_ROM` denotes the existing absolute path to the supplied REV01 ROM:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestCheckpointBackgroundScrollTransport,TestSonic1ScrollLoadBoundary,TestSonic1ScrollLifecycle,TestSonic1LevelInitProfile,TestSonic1HiddenBonusObjectInstance,SwScrlGhzTest,SwScrlMzTest,TestSwScrlSbz,TestCheckpointStateRewind,TestS1BadnikChildGraphRewind,TestS1GhzBossGraphRewind' \
  "-Dsonic1.rom.path=$SONIC1_ROM" test -B
```

This includes the **registered production rewind registry** restoring the saved
checkpoint bank, followed by a real death reload and fractional forward replay;
all seven gameplay zone setup controls use deliberately different checkpoint
and foreground words. The independent read-only review found no concrete defect.

The actual GameLoop credits-load/renderer check and final GHZ1 replay passed
together: two tests, zero failures/errors/skips, 3,905 compared GHZ1 frames and
zero comparator/bootstrap errors/warnings. The command is recorded in the
[trace frontier log](../../status/trace-frontier-log.md#2026-10-22--s1-standalone-reference-closure-validation-enabled).
No trace timing/physics comparisons were weakened or hydrated.

Affected structural/API checks passed 29 tests, zero failures/errors/skips:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindCoverageGuard,TestRewindFieldDispositionGuard,TestObjectServicesMigrationGuard,TestObjectPriorityBucketGuard,TestGameModuleRegistryUsageGuard,TestModApiSignatureSurface' test -B
```

The API signature check confirms that the private transport adds no public Mod
API surface. Source whitespace checks, the root guidance mirror, the S1 pitfall
mirror and new local documentation links also pass. Queue waiting and execution
are separate: the final ordinary job waited over five minutes before its
1m22s Maven execution; the affected guards also waited before their 34.759s
execution. These overlapping job intervals are not elapsed delivery time.

Remaining scope is explicit: actual later-zone ordinary lamppost routes,
complete act matrices, donor/team/wider-viewport breadth and full-campaign rewind
remain inherited. A separate source-only concern was sent to the lead: SBZ3's
progression zone 5/act 2 appears to select the ordinary SBZ nonzero-act scroll
handler despite its ROM LZ4 identity. It was not reproduced or changed by this
bounded lifecycle deliverable.
