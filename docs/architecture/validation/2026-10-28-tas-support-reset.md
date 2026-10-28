# TAS Reset/Power support — 2026-10-28

Implementation stream: `reset`; historical scenarios `s3k-reset:1` and
`s3k-nolr:3`. Parent owns central classification and combined validation.

## Starting evidence and boundary

Base: `8945c0a789b27be57b2361c70f561f31872a76ca`.
Approved title-state prerequisite fast-forwarded while the test request was
still queued: `987de58c20ce339101e6e5bdc054b2589fb6bc90`. Its ownership and
standalone behavior qualification remain with that stream.
Worktree: `$TAS_RESET_WORKTREE`.
Branch: `feature/ai-tas-support-reset-20261028`.
The unrelated dirty `docs/s1disasm` checkout is preserved. Path variables in
this record denote the exact assigned checkout and original ROM directory
reported in the worker handoff; no ROM copies or links are created.

The historical negative result remains valid for its tested source:
`964c29d25fdab09fe8edd4acbe541a27fc64904c` recorded Reset/Power rows becoming
neutral pad input while Start survived. Inspection of the current base finds
the same missing system-action parser/dispatch, rather than a limitation test
made stale by a later reset implementation. Keeping pad masks neutral is
correct; losing the separate system action is the defect.

The original S3&K ROM at `$OPENGGF_TAS_ROM_DIR/s3k.gen` was
independently hashed: 4,194,304 bytes, CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. No ROM copies or links are used.
Native references use immutable `docs/skdisasm` pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`.

## Native rationale

EntryPoint (sonic3k.asm:115-189) distinguishes soft reset from power-on.
Test_LockOn (sonic3k.asm:270-289) clears ordinary work state below the
cross-reset bank on both paths. Test_Checksum (sonic3k.asm:378-419) keeps
that bank on a recognized warm boot and initializes it on a cold boot.
SonicAndKnucklesStartup (sonic3k.asm:423-435) reinitializes sound and
controllers, reloads SRAM and selects the Sega/title flow.
Sega_Screen (sonic3k.asm:5419-5422) immediately selects title.
These are translated game initialization semantics,
not CPU reset, device registers, bus transactions or uninitialized RAM.

CrossResetRAM (sonic3k.constants.asm:800-1018) contains the level frame counter, `V_int_run_count`, score,
lives, rings/time, special-stage and emerald progress, player selection and
cheat unlocks. RNG_seed (sonic3k.constants.asm:582) is outside that bank.
Title_Screen (sonic3k.asm:5424-5464) further clears the
zone/act, active starpost, special/bonus entry, runtime debug and competition
state and resource queues.

The published no-Left/Right author's note says reset skips the MHZ-to-FBZ
cutscene. That claim requires reaching the save/progression boundary and
relaunching the resulting slot; merely parsing Reset does not prove it.
Full publication playback and arbitrary crash/corruption persistence remain
outside this representative implementation evidence.

## Implemented state inventory

Both actions retire the outgoing gameplay world, roster, rewind registry,
pending special/bonus/ending visits and callbacks, fades, dynamic art and
resource queues. The replacement world uses the identical pinned ROM data
source and recreates managers; it starts the native title provider. Active
save-slot ownership is detached. Neither action requests a save. Durable
save files and already-submitted save writes stay owned by the save system.
Audio source/voice state is rebuilt using the existing backend; a headless
no-device backend stays a no-device backend.

Warm reset retains score, lives, continues, special-stage cursor, emerald
states/conversion, special-ring bits, pending item bonus, rings/ring-life
flags, timer and level-frame counter. The engine-lifetime V-int carrier
adopts the retiring object clock. Cheat unlocks and native title selection
stay with their module-owned policy. RNG, runtime boss/gravity/pause flags,
camera, objects, active checkpoint and loaded zone/act are fresh.

Cold Power clears that retained progress bank and the V-int carrier, as well
as cheat unlocks. Locked-on title has zero lives until its native Start path
initializes play. Host configuration remains host configuration; it is not
a retained native gameplay roster. Cold native title-selection transfer
applies a new `Sonic3kTitleState.Snapshot(0)` to the replacement module's policy.
Warm uses the identical module-owned policy; no old-world supplier is retained.

The public `Bk2FrameInput`, `Bk2Movie` and `PlaybackDebugManager` signatures
are unchanged. System actions are an immutable private movie sidecar exposed
through an internal helper. The actual movie cursor owns action dispatch;
a comparator's applied-pad-row offset cannot select a different reset.
Each asserted Reset/Power row initializes a session once. Power dominates a
row containing both. Reset clears controller predecessor state. Following
title/menu rows have one input/cursor owner; ordinary non-reset movies retain
their previous level/bonus ownership.

## Validation checkpoint

The initial baseline request for `TestTasS3kUnavailableCapabilities` was
cancelled after 271 seconds of queue wait, before execution: closer inspection
showed the old test checked neutral pad masks but could not distinguish the
presence of a separate typed action. No baseline execution/pass is claimed.
The new witness retains those pad/Start controls and adds discriminating
system-action assertions.

Implementation compilation first found an absent internal movie accessor;
the repair adds a package-private accessor rather than expanding the Mod API.
Test compilation first found an incorrect fixture setter; the fixture now
advances the native stage cursor through its real owner. These were compile
failures, not engine-test failures.

Parent owns combined smoke coverage and the four mandatory S3K gates.

The updated candidate compiles successfully at prerequisite HEAD
`987de58c20ce339101e6e5bdc054b2589fb6bc90` plus the owned staged changes.
Its compiled `src/` tree is `7da803148f43b8f90460ae4470b147c14b8c623b`.
Command (build-only, no tests):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl test-compile
```

Result: exit 0, Java 25, 4,616 main and 5,007 test sources, Maven execution
1m29s, completed `2026-10-28T19:11:39Z`. This prepared the existing queued
test request's current build inputs; it is not an engine-test pass.

## First focused execution

Tested source: prerequisite HEAD `987de58c20ce339101e6e5bdc054b2589fb6bc90`
plus staged tree `54a62097964c6b97e8a155b63b9730d7638e410e`, whose `src/` tree
is `cf0298197cabd5dbcd62f67e93fc5a45889176b9`.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_TAS_ROM_DIR/s3k.gen" \
  '-Dtest=TestBk2SystemActions,TestTasS3kUnavailableCapabilities,TestTasS3kSessionReset,TestTasS3kSessionResetOutput,TestBk2MovieLoader' test
```

Completed `2026-10-28T19:32:39Z`: 20 tests, 1 failure, 2 errors, 0 skips.
Shared queue wait was 3,823s; Maven execution including preparation was
1m40s. Whole-command wall time was not separately measured.

The five action/parser tests, one upgraded historical witness, seven existing
loader controls and the real EGL output test all passed. The output test
reached the native title menu after repeated reconstruction, retained option
1 on warm reset, cleared it to 0 on Power, produced matching visible title
pixels, nonzero native title PCM and one frame of PCM per presentation from
the same capture lease across a cold audio rebuild.

Three of the six lifecycle invocations stopped on fixture assumptions:
`ordinaryMovieKeepsItsLevelCursorAndRestoreForwardReplayAfterResetUsesFreshRegistry`
expected cursor 1 before native level entry finished (observed 0); the two
`warmAndColdRebuildWorldsAndPreserveDurableSaveWithoutAnImplicitWrite`
invocations dereferenced the fresh title world's absent checkpoint. These
do not justify changing engine semantics. The corrected fixture reaches a
completed native LEVEL/title/fade boundary, checks an outgoing real active
checkpoint, accepts absent or inactive title checkpoint ownership, and checks
that the next ordinary load cannot revive it. No production source changed
for this fixture correction; unchanged parser/output coverage is reused.

## Completed focused qualification

Final tested `src/` tree: `b1120eaa5ce9d7bfc0cd6287fdcf8932ccb58d97`, at
prerequisite HEAD `987de58c20ce339101e6e5bdc054b2589fb6bc90` plus staged tree
`4a4bf400e28548c24c1e3c1b68b1834894409afa`. Only this validation record is
updated after that completed source check.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=$OPENGGF_TAS_ROM_DIR/s3k.gen" \
  '-Dtest=TestTasS3kSessionReset,TestGameLoopSessionResetVisits,TestPlaybackDebugManagerPreparedInput,TestPlaybackInputBridgeOwnership,TestModApiSignatureSurface,TestGameLoopNativeMenus,TestGameLoopBonusStageSession,TestGameLoopSpecialStageLifecycle,TestGameLoopEndingSession' test
```

Completed `2026-10-28T19:47:15Z`: exit 0, **129 tests, 0 failures, 0 errors,
0 skips**. Queue wait: 688s. Maven execution including preparation: 49.186s.
Whole-command wall time was not separately measured; the earlier build-only
preparation overlapped the first request's queue wait.

| Class | Passing invocations |
| --- | ---: |
| TestTasS3kSessionReset | 6 |
| TestGameLoopSessionResetVisits | 4 |
| TestPlaybackDebugManagerPreparedInput | 8 |
| TestPlaybackInputBridgeOwnership | 7 |
| TestModApiSignatureSurface | 11 |
| TestGameLoopNativeMenus | 7 |
| TestGameLoopBonusStageSession | 21 |
| TestGameLoopSpecialStageLifecycle | 29 |
| TestGameLoopEndingSession | 36 |

The corrected lifecycle cases verify visible warm clock `$12345678` and cold
clock zero after teardown/unbind and before a new object clock exists, retained
versus cleared progress/emeralds/cheats, cleared pause/gravity/checkpoint state,
unchanged durable save bytes, a fresh world/roster/rewind registry, exactly-once
retirement, consecutive Reset/Power/Start rows and held-action consumption.
The normal FBZ load then executes two actual level frames and two visible
V-ints; restoring the new registry reproduces the same forward position and
clock. This is an ordinary load/control, not the claimed MHZ-to-FBZ cutscene skip.

Together with the 14 unchanged passing parser/loader/historical/output cases
from the first run, this gives 143 unique passing invocations across 13 classes,
with zero skips in either completed request. It is bounded focused evidence,
not smoke/full-suite or complete-publication parity. Consumed Surefire XML/text
diagnostics are removed after recording; native extraction is retained.

## Parent integration and scenario limits

Parent must compose the exact native registration into
`Sonic3kGameModule.getGameService` beside the concurrent Competition service:

```java
if (type == com.openggf.game.session.NativeSessionResetPolicy.class) return type.cast(Sonic3kSessionResetPolicy.INSTANCE);
```

The real-ROM tests use a local registration decorator for that exact service
seam; this worker does not edit the shared module registry. The required title
policy dependency is commit `987de58c20ce339101e6e5bdc054b2589fb6bc90`.

`s3k-reset:1` now has qualified representative typed-input/session semantics,
pending the parent registration/integration gates. Its historical unsupported
result is preserved above. This does not certify arbitrary crash chains,
uninitialized RAM or exact hardware reset timing. `s3k-nolr:3` remains unproven:
no full route or reached MHZ2 save/cutscene-reset/relaunch boundary was executed.
No forced zone change was added to reset semantics.

The qualified movie path is `PlaybackDebugManager.startSession` → production
`GameLoop.step`. Frame-only capture drivers that extract `Bk2FrameInput` without
the owning movie do not yet dispatch its separate system-action sidecar; parent
reel/capture integration must use the qualified path or adapt its driver.
Parent owns the composed post-reset pause/resume phase witness after importing
Pause, combined smoke, the four mandatory S3K gates and central classification.
No SK-specific clock patch or new clock API was needed.

## Bounded structural correction after parent composition (2026-10-28)

Resumed the retained reset branch from `7274607b1`, with only the already
accounted dirty S1 reference. The local parent ref
`feature/ai-tas-engine-support-20261028` resolved exactly to
`d904596bb5eb83d042ea02e1bebb686aeec9a4fe`; fast-forwarded that commit and
reinstalled hooks. This brings the other streams, stock service registration,
playback timeline protection, native Start publication, upstream lifecycle
changes and DEZ common-art correction into the candidate. The earlier parent
registration/pause prerequisites above describe the original handoff, not this
composed candidate. The parent retains combined delivery and route ownership.

The parent's completed fresh guard run supplies the red evidence: root
`stepInternal` was 59 lines against 58; `stepInternalBody` was 273 against 263
after upstream removed 13 obsolete notice lines (previously 286 against 276).
Bonus, special and ending owners were respectively 367/357, 589/571 and
324/320 effective lines. These are architectural size failures, not new native
behavior observations. Unrelated baseline guard failures remain parent-owned.

The correction extracts existing coherent execution without deleting state:

- `consumeSuspendedOrResetFrame` keeps suspension, editor hold, then reset
  admission before lifecycle/PLC capture. The same input updates and audio flag
  writes remain at their original boundaries.
- `GameLoopPauseInput.resolveOwnership` owns recorded native publication and
  pause/configuration arbitration. Publication precedes the native consumer;
  overlay/hosted short-circuiting, lazy title reads and live mode rereads remain.
  The helper never advances a movie cursor. Four new controls cover those
  ownership boundaries, including a native consumer that changes the mode.
- `BonusStageShieldRestoration` contains the existing stateless saved shield
  encoding, native priority and entry/reward restoration rules. Existing
  delegates and pending-shield ownership remain in the visit owner.
- `SpecialStageFrameExecution` contains captured-frame debug/input, trace,
  pacing, provider, presentation and rewind execution. All fifteen lifecycle
  fields and result/reward/return choreography remain in the original owner.
- `EndingReturnTransition` contains the existing captured-slot cleanup and
  pending-fade-completion handoff. The original captured-argument seam remains;
  provider state, ending progression and return callback ownership stay put.

The three moved execution bodies match the parent text exactly apart from
class/method visibility and name. Each complete `retireForSessionReset` body
matches the parent exactly, including provider retirement and all pending
state clears. No reflection is used by these collaborators; existing tests
retain their own historical access helpers. No guard budget is increased and
no source statements are packed to meet a count.

| Owner or dispatcher | Parent actual | Corrected actual | Guard budget |
| --- | ---: | ---: | ---: |
| GameLoop.stepInternal | 59 | 52 | 58 → 52 |
| GameLoop.stepInternalBody | 273 | 262 | 263 → 262 |
| GameLoop effective class lines | 2309 | 2303 | 2367 unchanged |
| GameLoopBonusStageSession | 367 | 326 | 357 unchanged |
| GameLoopSpecialStageLifecycle | 589 | 479 | 571 unchanged |
| GameLoopEndingSession | 324 | 310 | 320 unchanged |

The parent explicitly authorized only the two downward method-ratchet changes
because that guard requires equality. All other guard contracts are untouched.
The extraction changes no native algorithm, ROM input, timing policy, retained
reset bank or scenario ID. In particular, the full `s3k-nolr:3` route remains
unproven; no reset-assisted zone switch or capture hydration is added.

Native references remain the existing pinned routines. Checked the moved
shield rules against `SpawnLevelMainSprites_SpawnPowerup` / `loc_6A02`
(`sonic3k.asm:8296-8355`) at S3K pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, and the ending departure against
`TryAg_Exit` (`sonic.asm:4034-4036`) at S1 pin
`28872a46e2d4c94c5c4818cbd3c04e6eb17edb67` using immutable Git contents.
The pre-existing dirty S1 and alternate S2 checkouts are preserved. The pinned
citation checker for the three new collaborators exits 0 with five
unverifiable range-only citations and no stale/out-of-range finding; direct
pin inspection above supplies the relevant label/range check.

Validation environment: Java 25 at `/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, surfaceless EGL and unset `DISPLAY` / `WAYLAND_DISPLAY`.
Original absolute ROM properties are passed without copying or linking assets:
`$OPENGGF_TAS_ROM_DIR/s1.gen`, `/s2.gen`, `/s3k.gen`. Identity was rechecked:

| ROM | Bytes | CRC32 | SHA-1 |
| --- | ---: | --- | --- |
| S1 | 524288 | AFE05EEE | 69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B |
| S2 | 1048576 | 7B905383 | 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9 |
| S3K | 4194304 | 63522553 | CFBF98C36C776677290A872547AC47C53D2761D6 |

First exact-guard request (session 45724), candidate tree
`836d9e824ba97f717714d51ed93b7e2ae7bca46d`, source
`e6f6af3347dd26e0c6016440c0ca354c7516e90b`, exited 1 during test compilation:
the new ownership test imported `RuntimeArtCoordinator` from `.resources`
instead of `.game`. Zero tests executed; this is not a guard result. Queue wait
1s; Maven 1m27s; ended `2026-10-28T23:39:45Z`. Corrected that import only, without
changing production source. The subsequent source tree is
`b84bcffa6cfd4bba1d10e946dc7ea3616611bb77` (staged tree
`c1d363aac7273d2ae94468455006c9985f430007` before this record append).

The exact guard command (both requests; normal admission, separate guards JVM):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off -Pguards \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$OPENGGF_TAS_ROM_DIR/s1.gen" \
  "-Dsonic2.rom.path=$OPENGGF_TAS_ROM_DIR/s2.gen" \
  "-Ds3k.rom.path=$OPENGGF_TAS_ROM_DIR/s3k.gen" \
  '-Dtest=TestArchitecturalSourceGuard#rootDispatchMethodsDoNotGrowBeyondCurrentBudgets+releaseCriticalLargeClassesDoNotGrowWithoutExtraction' test
```

Corrected request (session 10201) ended `2026-10-28T23:41:08Z`, exit 0:
**2 tests, 0 failures, 0 errors, 0 skips**. Queue wait 29s; Maven execution
33.482s including preparation; JUnit 0.712s. This is the two requested ratchets,
not a claim that the entire architectural guard class or guards profile passed.
The generic load-time warning printed during guard setup is unrelated to the
agent service tier and selects no TAS behavior.

The bounded behavior command on the same source:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$OPENGGF_TAS_ROM_DIR/s1.gen" \
  "-Dsonic2.rom.path=$OPENGGF_TAS_ROM_DIR/s2.gen" \
  "-Ds3k.rom.path=$OPENGGF_TAS_ROM_DIR/s3k.gen" \
  '-Dtest=TestGameLoopPauseInputOwnership,TestTasS3kSessionReset,TestTasS3kSessionResetOutput,TestGameLoopSessionResetVisits,TestPlaybackDebugManagerPreparedInput,TestPlaybackInputBridgeOwnership,TestBk2SystemActions,TestGameLoopNativeMenus,TestGameLoopSpecialStageLifecycle,TestGameLoopBonusStageSession,TestGameLoopEndingSession,TestEndingReturnFadeOwnership,TestBonusStageShieldRestore,TestBonusStageShieldRestoreOnTitleCardExit,TestGameLoopSuspension,TestNativePauseClockLifecycle,TestRecordedInputNativePause,TestSonic3kStandaloneNativeLaunch,TestGameLoopSpecialStageSkipGate,TestTasS3kSpecialStageEntryInput,TestModApiSignatureSurface' test
```

Behavior request (session 58962) ended `2026-10-28T23:44:09Z`, exit 1:
**183 tests, 0 assertion failures, 4 teardown errors, 0 skips**. Queue wait
109s; Maven execution 47.509s. All 179 pre-existing cases passed. The four new
`TestGameLoopPauseInputOwnership` controls reached their assertions but their
`@AfterEach` called shared engine teardown while its mock module was still
published, so `getLevelInitProfile()` returned null. Shared teardown already
cleared the session and reset the bootstrap module before that exception;
subsequent unchanged cases passed. Corrected only the new fixture cleanup to
retire its mock session first, then call shared teardown; production code and
all guard inputs are unchanged. Reuse the 179 unchanged successful cases and
the completed two guards rather than repeating that coverage.

| Behavior class | Cases | First result |
| --- | ---: | --- |
| TestGameLoopPauseInputOwnership | 4 | Teardown errors; corrected-only rerun below |
| TestBonusStageShieldRestore | 6 | Pass |
| TestBonusStageShieldRestoreOnTitleCardExit | 1 | Pass |
| TestEndingReturnFadeOwnership | 6 | Pass |
| TestGameLoopBonusStageSession | 21 | Pass |
| TestGameLoopEndingSession | 36 | Pass |
| TestGameLoopNativeMenus | 8 | Pass |
| TestGameLoopSessionResetVisits | 4 | Pass |
| TestGameLoopSpecialStageLifecycle | 29 | Pass |
| TestGameLoopSpecialStageSkipGate | 2 | Pass |
| TestGameLoopSuspension | 7 | Pass |
| TestBk2SystemActions | 5 | Pass |
| TestPlaybackDebugManagerPreparedInput | 8 | Pass |
| TestPlaybackInputBridgeOwnership | 7 | Pass |
| TestModApiSignatureSurface | 11 | Pass |
| TestNativePauseClockLifecycle | 5 | Pass |
| TestRecordedInputNativePause | 1 | Pass |
| TestSonic3kStandaloneNativeLaunch | 7 | Pass |
| TestTasS3kSessionReset | 9 | Pass |
| TestTasS3kSessionResetOutput | 1 | Pass |
| TestTasS3kSpecialStageEntryInput | 5 | Pass |

The affected-only rerun uses the preceding normal-admission behavior command,
replacing its `-Dtest` value with `TestGameLoopPauseInputOwnership`, with the
same Java/Lua/EGL/display and three original absolute ROM properties. This
fixture-only cleanup correction does not change the 179 existing cases or the
production source exercised by the two guard selectors.

Affected-only request (session 65611) ended `2026-10-28T23:46:07Z`, exit 0:
**4 tests, 0 failures, 0 errors, 0 skips**. Queue wait 50s; Maven execution
35.056s including test compilation; JUnit 0.789s. Final source tree:
`7d2f81910cfa1e2b98972defac6b74d62e6d41a4`; production `src/main` tree:
`d7d53191e1e29431f1eec174d351b04cea1e8166`, unchanged across the successful
guards and 179 reused successful controls. The only subsequent source change
was the new test's cleanup described above. This gives **185 unique passing
invocations across 22 classes**: 2 fresh guards, 179 unchanged behavior cases,
and 4 corrected ownership cases. All completed runtime requests had zero
skips. This does not erase the first command's recorded teardown errors and
is neither broad smoke nor a whole-profile/full-suite claim.

These controls retain actual movie cursor ownership across Reset/Power/Start,
paused movie resumption and same-movie replacement timelines; real warm/cold
world/save/audio/art reconstruction and visible V-int phase; post-reset
pause/resume; real special entry retirement and ordinary captured-frame/trace
pacing; bonus live/deferred ownership and shield/reward timing; ending pending
fade callback ownership; and ordinary suspension/restore-forward behavior.
The output control uses real EGL pixels and no-device native PCM. Public Mod
API signature checks pass unchanged. The extraction adds no new supported
scenario or route claim.

Consumed owned Maven logs/XML/text diagnostics are removed after this record;
no active tests or native probes remain. The owned branch/worktree are retained
for the parent with only the pre-existing dirty S1 reference outside the owned
change. No main operations, push, delegate, checkout removal, broad rerun or
guard-budget increase was performed. The registry expiry remains
`2026-10-29T18:07:50Z`. Whole-command elapsed times were not separately measured;
queue waits and Maven execution above are distinct, and overlapping intervals
must not be summed as delivery wall time.
