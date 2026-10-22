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
remain inherited. The initial commit left a source-only SBZ3 scroll-alias concern. The bounded
follow-up below reproduces and closes that concern; the other coverage limits
above remain unchanged.


## SBZ3 native scroll alias follow-up

The follow-up starts from local commit
`6d5c41e4b73fceeb2fdb99fef4e8c4445a0fd00f`. A production full load and
`advanceToNextLevel()` from SBZ2 both preserve progression zone **5/act 2**
while loading terrain from native **LZ/act 4** (`LevelData` index `0x91`).
The previous scroll provider incorrectly selected `Deform_SBZ2` from the
progression identity, although the native scroll dispatch reads `v_zone` alone.

Direct REV01 bytes establish the owning routines:

| ROM location | Verified bytes | Meaning |
| --- | --- | --- |
| `0x629E` | `7000 1038FE10 D040 303B0006 4EFB0002` | Dispatch by `v_zone` |
| `0x62AE` | `000E 0108 029A 0392 0462 050E 000E` | LZ entry points to `0x63B6` |
| `0x63B6` | `3838F73A 48C4 EF84 3A38F73C 48C5 EF85 610006FC` | Half-speed X/Y camera shifts |
| `0x61D4` | `000E 002A 0032 0034 0044 005A 0068` | LZ background setup points to `0x61FE` |
| `0x61FE` | `E280 31C0F70C 4E75` | Initial BG Y = camera Y / 2 |

Before changing production code, the actual EGL load/render probe started at
camera `(0x0AE0, 0)` and moved it by `(32, 16)`. The engine produced BG
`(0x0AE8, 3)` instead of native LZ `(0x0AF0, 8)`. Comparing the same terrain,
foreground camera and render with independent LZ scroll inputs gave **10,571
pixel differences**. The deliberate SBZ2 negative control differed by the
same count, proving the background was visible to this probe. The independent
SBZ2-to-SBZ3 progression regression also failed BG X `2800` versus `2792`.
These two tests failed by assertion, with zero errors/skips, on the unchanged
production source of `6d5c41e4b`.

The fix stays inside `Sonic1ScrollHandlerProvider`. It resolves the native
scroll zone separately from terrain/progression identity, with one dedicated
LZ handler for SBZ3. Initialization selects the zone/act **before** resolving
that handler. Checking the global act alone was rejected because an ordinary
LZ act-3 load could then change an inactive SBZ handler query. Sharing the
ordinary LZ instance was also avoided so the two routes retain independent
entry anchors. The resolved native scroll zone also selects checkpoint word
capture, preserving LZ's otherwise-unused BG2/BG3 X words rather than taking
zeros from the SBZ branch. Initialization, active-handler rewind and checkpoint
capture/restore consequently select the same owner. No public API, game loop,
level-manager, terrain, progression or comparison-trace contract changes.

`TestSonic1Sbz3ScrollAlias` checks direct ROM opcode/table bytes, actual full
load and rendered output with a visible wrong-routine control, actual SBZ2
progression, a real lamppost activation/death reload, retained BG words,
fractional restore/forward replay, and return to ordinary SBZ2 scrolling.
Provider isolation and existing SBZ1 band/SBZ2 uniform controls remain separate
short checks. No full SBZ3 route, trace frontier or wider-view/donor/team
coverage is claimed.

The corrected-source focused run passes **38 tests**, zero failures/errors/skips.
The identical render probe now gives actual BG `(0x0AF0, 8)`, zero pixel
differences from the LZ reference and the same 10,571-pixel difference from the
SBZ2 negative control. The command uses the existing supplied absolute ROM via
`SONIC1_ROM`:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestSonic1Sbz3ScrollAlias,TestSonic1ScrollLifecycle,TestSonic1ScrollLoadBoundary,TestSwScrlSbz' \
  "-Dsonic1.rom.path=$SONIC1_ROM" test -B
```

The pre-fix command used only `-Dtest=TestSonic1Sbz3ScrollAlias`, with the same
ROM, environment and graphics flag. Java 25/Lua 5.4/PowerShell preflight also
passed for the follow-up. The focused selection's queue wait exceeded 60s;
Maven execution took 1m17s, separately from waiting.

The two affected rewind guards pass, zero failures/errors/skips (27.966s Maven
execution):

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindCoverageGuard,TestRewindFieldDispositionGuard' test -B
```

The original unchanged public-API checks were not repeated for this private
provider alias. No broad smoke, full suite or trace replay was launched by this
follow-up. Source whitespace and new audit link checks also pass.
