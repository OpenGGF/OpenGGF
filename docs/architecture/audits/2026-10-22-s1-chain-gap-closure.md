# S1 continuous emerald-route gap closure — 2026-10-22

The isolated `bugfix/ai-gap-s1-chain-20261022` tree starts at
`94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`. The destination and foreign dirty
S1 disassembly were preserved. This record covers focused validation only.

## Pre-object VBlank publication

Native `VBlank_Exit` increments the longword before returning to
`Level_MainLoop`, which calls `LZWaterFeatures` before `ExecuteObjects`
(`docs/s1disasm/sonic.asm:684-687,2998-3006`). The engine increments its object
clock inside `ObjectManager.update`, after zone pre-physics. The S1 water reader
therefore observes the admitted tick as `(stored + 1) & 0xFF`; it does not move
or tick the shared clock. Lag and pause closures do not dispatch this routine.

Under shipped `FixBugs=0`, `LZWindTunnels` reuses the sound gate's clobbered d0
for suction (`docs/s1disasm/_inc/LZWaterFeatures.asm:376-403`), so the wrong tick
changes gameplay as well as sound. Verified original ROM CRC32 `AFE05EEE`,
SHA1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`: address `$0B64` contains
`52B8 FE0C` (increment); `$3E7E` contains `1038 FE0F 0200 003F 660A 303C 00D0`
(low byte, mask, branch, waterfall id); `$3EBC` contains `D169 000C` (word Y add).
References were read from pinned disassembly `28872a46`; no pin was changed.

The independent positioned production-frame regression observes the real
`RecordingFrameDriver` physics wrapper after the zone pre-physics callback,
then executes all original physics and objects. Seven cases cover both sides
of the 64-tick gate, byte/word rollover, a lag predecessor with no gameplay,
and registry restore followed by forward execution. The original end-of-frame
oracle was rejected: first-frame terrain/radius work moved Y by three pixels.
Sampling the actual owning boundary reproduced six exact gate failures and
one passing control before the fix. After the reader correction all seven pass;
the existing three clock/logical-input tests pass too. No trace values seed
these tests, and no new persistent state is introduced.

## Measured chain and second movie

All commands use `LUA_BIN=/usr/bin/lua5.4`, Java 25, the shared Maven queue,
`env -u DISPLAY -u WAYLAND_DISPLAY` and the original absolute S1 ROM property.
The full chain remains red; no comparison, tolerance, timing edge or route was
removed. Fresh baseline: one test, one assertion failure, zero errors/skips,
17 axes. Clock candidate: 13 tests, one inherited chain assertion failure,
zero errors/skips, 15 axes. Both alternate-movie complete-run LZ2 and LZ3
controls pass on baseline and candidate.

| Segment | Baseline | Clock candidate |
| --- | --- | --- |
| 12 MZ2_3 | 3, first 101 queue prepared | unchanged |
| 15 MZ3_2 | 6, first 102 queue prepared | unchanged |
| 22 SYZ3_2 | 44 camera-only | unchanged |
| 25 LZ2 | 10, first 2705 Y `$0452/$0453` | 0 |
| 26 LZ3 | 10,212, first 3838 Y `$0655/$0653` | 3, first 10185 queue prepared |
| 27 SLZ1 | 3,880, first 1771 rings 1/0 | unchanged |
| 29 SLZ3 | 1,456, first 8557 X fraction `$F500/$0000` | unchanged |
| 31 SBZ2 | 1, first 7821 rings 9/10 | unchanged |
| 32 LZ4 | 12, first 11 queue prepared | unchanged |
| 33 FZ | incomplete 3,203, first 356 dynamic art | unchanged |

The LZ3→SLZ1 art gap also closes (edge ordinal `$13316/$13318`, decimal
78,614/78,616); six later art gaps remain. Segment 33 still loses ownership
in TITLE_CARD at BK2 cursor 210396. The harness starts at recorded gameplay
row 860; it does not certify natural title startup from movie row zero.

Commands (`S1_ROM` denotes the existing original ROM path, not a generated alias):

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 94c40570a15a11b70caf7456e9b0d0a8eec8a9b4 --preflight
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1CompleteEmeraldRunChain' "-Dsonic1.rom.path=$S1_ROM" test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonic1LZWaterPreObjectClock' "-Dsonic1.rom.path=$S1_ROM" test -B
env -u DISPLAY -u WAYLAND_DISPLAY OPENGGF_S1_RING_PROBE=1 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSonic1LZWaterPreObjectClock,TestSonic1LZWaterEventsClock,TestS1Lz3CompleteRunTraceReplay,TestS1Lz2CompleteRunTraceReplay,TestS1CompleteEmeraldRunChain' "-Dsonic1.rom.path=$S1_ROM" test -B
```

The last run included temporary environment-gated read-only ring/slot diagnostics,
removed before commit. It confirmed two ownerless allocated slots after SLZ
staircase unloads; that separate shared-manager issue remains under coordination.
The full chain's original queue comparisons require native timing streams, not
a predicate fitted to lag counters.

## Preserved native timing capture

The existing external `parity-r3-s1-canonical-timing-20261008/capture-v5` was
reverified rather than recorded again: 131 files, 41,932,357 bytes; ordered
path/bytes/SHA256 inventory hash
`3590bd88eb32c644af297fc8175cbe0abdd925a06f1277b86c4c779f2032e6a2`.
Every decompressed physics and auxiliary payload in all 34 segments is identical
to the current fixture, and manifests are semantically identical. Its 28 timing
streams contain 242 events, contiguous ordinals 0–241. This is authenticated
native evidence; it is not yet whole-route consumer qualification or fixture
publication. Prior strict job170 mismatch downstream of the SLZ1 ring miss is
retained as a pending obligation. No edge may be dropped, renamed or renumbered.

## Native SST retirement and Bomb conversion

The coordinated manager correction changes only the argument passed by
`retireBorrowedExecutionSlotOwnerAtOwnSlot`: out-of-range unloading receives
the owner's own slot, rather than its highest borrowed execution slot. Native
`Staircase` deletes each SST separately (`_incObj/5B SLZ Staircase.asm:6-12`);
`ExecuteObjects` walks ascending slots (`_inc/ExecuteObjects.asm:18-30`), and
`DeleteObject` clears only a0 (`_incObj/sub DeleteObject.asm:10-18`). REV01
`$DCCE` contains `2248 7200 700F 22C1 51C8 FFFC 4E75`. The previous argument
left the parent's allocator bit occupied after the owner disappeared. Temporary
diagnostics measured two such bits before SLZ1's first lost-ring disagreement;
five were present later. Ring update phase depends on the real SST index.

The independent regression uses a real staircase with parent 34 and folded
children 36–38, a genuinely deleted lower hole 32, and an intervening allocator 35.
It checks reuse of 32 then 34, forward 39 executing in the same pass, replacements
behind the cursor waiting, children reserved at 35, and child 36 available inside
39's update before finally cleanup. Both manager execution loops repeat the
same decisions after destructive capture/restore. Old source fails both cases
because the next allocation returns 39 instead of 34. Deferred child release is
unchanged; there is no shared clock or API refactor.

`Bom_BurnFuseAndExplode` reuses the fuse's SST, writes only the integer Y word,
and falls directly into `Bom_Shrapnel` (`_incObj/5F Badnik - Walking Bomb.asm:171-220`).
The engine now retains 16.16 fractions/status, uses `SpeedToPos`, and executes
the first fragment during conversion. Fresh forward children still initialize
without moving. Full and partial forward-tail tests preserve `FindNextFreeObj`:
a lower hole cannot substitute for an unavailable higher slot. Before conversion,
143 real fuse steps yield fraction `$F000`; old source yielded `$00F0`.
REV01 `$12250` contains `4268 0030 4228 0024 3168 0034 000C` (timer/routine clear,
integer Y restore). The four focused cases include destructive restore/forward.

`Pole_Action`'s timeout writes frame1, while `.checkLetGo`/`.release` do not
write obFrame (`_incObj/0B LZ Pole that Breaks.asm:35-79`). Manual release now
keeps the held pose and native position/fractions/velocity. Native `$1192C`
contains `4A68 0030 670E 5368 0030 6608 117C 0001 001A 6046`; manual `$1197C`
contains `1038 F603 0240 0070 676C`. Seventeen cases include both manual and
timeout capture/restore. Four manual-release assertions fail on old source.

The old-source focused check executes 23 tests: 9 assertion failures, 1 missing-new-field
reflection error, 13 passes, 0 skips. Restoring the exact five production files
passes all 23, including the strengthened before-finally release observation.
The earlier cross-game affected selection passes the existing child allocation 8,
slot-recycle 1, staircase activation/wall 2, and S3K required loading/bootstrap/
decoding/AIZ checks 53. Its initial Bomb touch-boundary and Pole restore harness
failures were corrected in tests, then all affected object cases passed. A touch
eligibility assertion must observe the next production frame-start snapshot,
and player restore requires an active gameplay timer owner.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestBorrowedExecutionSlotRetirement,TestSonic1BombFuseConversion,TestSonic1PoleThatBreaksObjectInstance' test -B
env -u DISPLAY -u WAYLAND_DISPLAY OPENGGF_S1_BOUND_PROBE=1 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSonic1BombFuseConversion,TestSonic1PoleThatBreaksObjectInstance,TestBorrowedExecutionSlotRetirement,TestS1Slz1CompleteRunTraceReplay,TestS1Slz2CompleteRunTraceReplay,TestS1Slz3CompleteRunTraceReplay,TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix' "-Dsonic1.rom.path=$S1_ROM" test -B
```

The route check on `dde29476` plus the five production edits completes 29 tests:
28 pass, 1 inherited chain assertion failure, 0 errors/skips. Both prefix tests
and the independent original-BK2 SLZ1/2/3 replays pass. The emerald chain reports
12 axes, down from 15 after the water correction and 17 on the original base.
SLZ1's 3,880 errors become 0; its first rings mismatch 1771 disappears. The SLZ1→SLZ2
and SLZ2→SLZ3 dynamic-art gaps close too. Remaining completed segment errors:
12 = 3 prepared@101; 15 = 6 prepared@102; 22 = 44 camera-only; 26 = 3 prepared@10185;
29 = 1456 first non-camera 8557 X fraction `$F500/$0000`; 31 = 1 rings 9/10@7821;
32 = 12 prepared@11. Four later art gaps remain. Segment 33's incomplete report
still has 3,203 errors, first non-camera 356 dynamic-art edges, and the walk loses
TITLE_CARD ownership at cursor 210396. An incomplete report is not an extra
completed-segment axis or coverage of the movie suffix.

The temporary SLZ3 boundary observer confirms that engine maxX is `$20A8`
before row 8557 physics, clamping X to `$21D0` and clearing its fraction/speed;
the post-object escape step raises maxX to `$20AA`. Native already has `$20AA`
before that physics and retains `$F500`/speed `$0532`, then clamps at `$21D4` on
the following row. This is an earlier boss escape boundary dependency, not a
capsule width correction. The observer was removed before committing.

Two additional reviewed animation/render obligations remain separate: fresh
Bomb children retain native frame 0 during `Bom_Main` before their first animation
call; shipped `FixBugs=0` also submits the reused fragment twice to `DisplaySprite`
on conversion. The latter belongs to the shared sprite-queue owner; these motion
and slot regressions do not certify that queue behavior. The SYZ3 grounded-roll
camera reset is likewise coordinated with the shared movement/rules owner.


## Retail grounded-roll camera and fresh fragment animation

The coordinated `CameraRules.groundedRollRecentersCameraBias` semantic disables
only S1 grounded-roll recentering. The existing six-argument constructor defaults
to true, preserving callers' prior behavior; S1/S2/S3K construction explicitly
selects false/true/true. `doRollSpeed` gates only its existing bias reset. Air,
normal-ground, glide, participant ownership and movement-rule definitions are
unchanged. `CameraRules` is published API: its new canonical seven-argument
constructor/accessor require the aggregate native-modes owner's candidate pin
reconciliation. The local hook required a same-commit pin update, so the parent
and native-modes owner explicitly delegated only three additions to the current
0.7 candidate: the canonical constructor, accessor and record component. No other
pin, descriptor or version changed. `TestModApiSignatureSurface` then passed all
11 cases without errors/skips; the final aggregate owner will compose its other
API changes separately.

Retail `Sonic_AngledRollSpeed` omits its recenter block because it is inside
`if FixBugs` (`_incObj/01 Sonic.asm:829-842`); the shipped branch is zero.
`Sonic_RollJumpLock` retains ordinary air recentering (`:974-982`). S2
`Obj01_Roll_ResetScr` (`s2.asm:37074-37084`) and S3K `loc_115C6`
(`sonic3k.asm:23038-23047`) always step toward `$60`. Original verified ROMs:
S1 `$138A0` begins `1028 0026 4EB9 000029D2 C1E8 0014 E080 3140 0012`
with no bias reset; S2 `$1A85A` contains `0C78 0060 EED8 670A 6404 5878 EED8 5578 EED8`;
S3K `$115C6` contains `0C55 0060 6706 6402 5855 5555`.

Twenty-one independent production-routine cases use biases `$50` and `$80`,
not the fixture's `$58`: S1 holds each bias during rolling, S2/S3K ease by two,
zero-inertia unrolling keeps that dispatch's camera branch, S1 air resumes
recentring, and destructive player/movement/camera restore repeats the result.
All pass, as do seven existing rolling-speed and four camera-rewind cases.

Fresh shrapnel's native `Bom_Main` does not animate or submit a display pointer
(`_incObj/5F Badnik - Walking Bomb.asm:22-33`). Its stored obFrame remains zero;
first animation occurs on its next pass. The reused fuse calls `AnimateSprite`
twice under `FixBugs=0`, leaving its first A duration at two rather than three.
Verified REV01 `$122F8` is `03 0A 0B FF`. A fifth Bomb regression checks native
stored frames on conversion `[A,0,0,0]`, then `[A,A,A,A]` twice,
`[B,A,A,A]` twice and `[B,B,B,B]`. Forced recreation after conversion also
checks retained ceiling flips, fractions, allocation and the full forward
sequence. This tests SST animation state; frame 0 is not permission to draw.
The coordinated queue backend/consumer still owns the display-call obligation.

The necessary combined run starts from `702f142c5` (the authorized `d8aca9ad7`
boss component dependency applied after `52a346902`) plus these camera/animation
changes. It completes 104 cases: 103 pass, 1 inherited complete-chain assertion
failure, 0 errors/skips. Original-movie SLZ1/2/3 and SYZ3, both prefix cases,
and all 60 selected S3K loading/bootstrap/decoding/AIZ checks pass. SYZ3 segment 22
is now exact, removing 44 camera errors and reducing the full chain 12→11 reported
axes. Other completed segment counts and segment 33's incomplete 3203-error
report/ownership loss remain unchanged from the previous table. Temporary
SLZ/SBZ2 diagnostics were removed before the next checks.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY OPENGGF_S1_SBZ2_INVENTORY=1 OPENGGF_S1_BOSS_TRANSITION_PROBE=1 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestGroundedRollCameraBias,TestPlayableSpriteRollSpeed,TestCameraRewindSnapshot,TestSonic1BombFuseConversion,TestS1Slz1CompleteRunTraceReplay,TestS1Slz2CompleteRunTraceReplay,TestS1Slz3CompleteRunTraceReplay,TestS1Syz3CompleteRunTraceReplay,TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$S1_ROM" "-Ds3k.rom.path=$S3K_ROM" test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestPerGameRuleArchitectureGuard,TestRewindCoverageGuard,TestRewindFieldDispositionGuard,TestTraceReplayInvariantGuard,TestHardwareTimingAuthorityGuard,TestObjectServicesMigrationGuard' test -B
```

The separate fresh-JVM affected guards pass 54 cases, 0 errors/failures/skips:
rule architecture 3, rewind coverage 1, field disposition 1, trace invariant 10,
hardware authority 25, and injected object services 14. This is focused validation;
the parent owns combined smoke and final aggregate API pin validation.

The same necessary run gives the boss owner causal SLZ evidence: engine own-slot
updates still have hitCount1/defeatedfalse through row 8284, then enter row 8285
with phase 06/timer 78 and consume the deferral. Native's final hit sets status80
on 8283, enters phase 06/timer 78 on 8284, and decrements on 8285. Engine recovers on 8406
instead of 8405 and enters escape on 8472 instead of 8471. Previous ship-render flag
is true throughout; no Recover→Escape fallthrough or capsule-width change is
justified. The independent later escape movement gap and exact collision endpoint
cause belong to the boss lane. Player/row/VInt alignment remains explicit.

The SBZ2 slot observer independently finds a missing second untracked dormant
saw at `$1080,$06C0`, subtype 03. Native retains slot 47 from row 7107, then cursor
re-entry creates another at 70 on 7397; engine retains only 47 and dedupes the second
placement. Native has one additional type 6A both at 7397 (7/6) and before ring spill
7584 (4/3). The resulting native 92/engine 91 ring phase accounts for the single
row 7821 pickup difference. Original ROM `$70ED4` contains `1080 06C0 6A03` with
tracking bit clear. This source-backed follow-up remains independent of shared
ring collision thresholds; no ring/touch behavior was fitted.


## Untracked dormant saw re-entry

The SBZ2 inventory disagreement is a second live copy, not a missing pizza-cutter
implementation. Native untracked placement `$1080,$06C0`, subtype 03, retains its
first saw in slot 47 and reloads another into 70 at the later cursor crossing.
`OPL_SpawnObj` bypasses the respawn-bit gate for untracked entries
(`_inc/ObjPosLoad.asm:257-275`). In shipped `FixBugs=0`, the ground saw's X-hide
branch pops the caller's return, bypassing the ordinary delete tail
(`_incObj/6A SBZ Saws and Pizza Cutters.asm:131-160,181-207`). The existing
engine placement map's one-instance key therefore suppresses a valid native
second load even while the first SST correctly survives.

The S1-only correction uses existing `ObjectLifetimeOps.releaseSpawnForRespawn`
at that skipped-delete boundary: only untracked hidden type 3/4 with unsigned
chunk distance greater than `$280` relinquish placement ownership. The actual
SST, slot and rewind identity become a dynamic survivor. No placement/allocator,
ring, shared clock or collision code changes. In-range hidden saws retain their
ownership; tracked entries retain their respawn gate; Y-failure paths still return
through ordinary deletion. Releasing on first execution was rejected in favor
of the owning native skipped-delete/out-of-range condition. The existing release
operation is idempotent and compares the owning instance, so a detached old saw
cannot release its newer placement copy.

Direct original-ROM checks: `$70ED4` is `1080 06C0 6A03`; `$E0CA` is
`4A28 0004 6A0E`, branching untracked entries to `$E0DE` (`6100 003A`, FindFreeObj)
instead of the respawn-bit operation at `$E0D0`. Saw X-hide at `$1626A` and
`$162DC` is `588F 4E75` (pop return, RTS); normal range-tail `$16148..16162`
reads original X, masks `$FF80`, subtracts camera minus `$80`, and compares
`$0280` with unsigned BHI. Previously quoted loader addresses from another
revision were rejected; these bytes come from verified REV01.

The focused command passes 11 cases, 0 failures/errors/skips on `a69971edf` plus
the two saw files:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonic1SawPlacementReentry' test -B
```

Synthetic placements drive the actual S1 counter loader and object execution,
without trace input: type 3/4 at viewport 320/528, inclusive distance `$280`
versus outside `$300`, a genuinely freed lower slot 32 and original slot 33,
real cursor retreat/re-entry, two live moving saws, no third without another
crossing, and destructive restore before re-entry preserving both distinct
identities and allocations. Controls cover tracked 3/4, ordinary pizza types 0–2,
and X-pass/Y-fail dormant 3/4. The original/emerald BK2 route checks remain pending
for the next necessary composed run; 11 focused passes do not claim that the
row 7821 route comparator is already green.
