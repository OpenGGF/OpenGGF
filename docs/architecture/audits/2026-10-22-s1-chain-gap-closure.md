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

## Native DisplaySprite producers and authenticated PLC timing candidate

The internal queue dependency `d89c09e660264f242192b82f3caa7600af9e1d00`
is locally composed as `2cbcb6e90`, after the original own-slot retirement fix.
Only shared prose required reconciliation. Its native FIFO prefix and legacy
SST suffix are an explicit bridge: this does not certify all objects' mixed
order, capacity or multipart slot projection.

The Bomb consumer opts fuse and shrapnel sources into explicit submissions.
`Bom_Main` returns without a call for fresh subtype 4/6 children
(`_incObj/5F Badnik - Walking Bomb.asm:22-33`). Retail `Bom_Fuse` calls burn,
then AnimateSprite and RememberState (`:153-158`). Under `FixBugs=0`, expiry
retains its return address, enters Bom_Shrapnel in the reused fuse SST, and
returns through that fuse tail (`:171-220`). It therefore submits the same SST
twice in range, or once before RememberState deletes it out of range. Fuse
range retirement must occur after the routine; an earlier manager cull would
skip the real orphan-pointer path. Fresh shrapnel submits nothing; an ordinary
subsequent shrapnel pass submits once after its existing render-bit test.
The normal Bomb body remains unported in the legacy suffix. This change makes
no whole-object topology or ordinary-body capacity claim.

The supplied REV01 ROM was freshly rehashed as CRC32 `AFE05EEE`, SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`. `$12234..12243` is
`610E 43F9 000122DC 6100 8B64 6000 DAF6`; the shrapnel tail
`$122A6..122CB` ends `4A28 0001 6A00 BA08 6000 B9C8`.
`DisplaySprite` at `$DC92` compares its byte count against `$7E`, permitting
63 word-sized SST references per bucket. Producer regressions use the real
manager walk, explicit call-order admissions, full-bucket pressure, an actual
out-of-range conversion and destructive graph/queue restore. No display count
is stored on the object, and admission success does not gate its gameplay.

The existing external canonical capture is reused, without another emulator
run: `parity-r3-s1-canonical-timing-20261008/capture-v5` under the authorized
scratch directory. Its 131-file, 41,932,357-byte sorted inventory has SHA-256
`3590bd88eb32c644af297fc8175cbe0abdd925a06f1277b86c4c779f2032e6a2`.
All 34 decoded physics and 34 decoded auxiliary streams equal the committed
comparison data. The manifest is semantically identical. The candidate adds
28 compressed timing streams (242 continuous ordinals, 38 distinct ROM-backed
identities) and updates only the recording date in all 34 metadata files.
The six special stages have no stream; an empty stream is not invented.
Publication must be atomic and retain the existing physics, auxiliary, BK2 and
manifest bytes.

Surviving provenance identifies TraceChaser
`e0a2443e086ca657a49227c5467eeecd06e40ece`, BizHawk 2.11/GPGX, and canonical
executable SHA-256
`ecea6c71c94f7800afe303a76da7d70e2ea98aec56756fbeb33383dece0ae178`.
Original BK2 SHA-256 is
`f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b`.
The prior capture/build completed successfully; capture-time Mono version,
core/shared-library digests and retained canonical executable path are
unavailable. The separate callback probe executable is not substituted for
that identity. Current TraceChaser `322851cb` has byte-identical S1 PLC observer
and run-capture source, but its later host additions do not prove whole-build
identity.

The authority already exists: the production S1 service parses ROM cue entries,
submits its own idle FIFO head, and prepares that descriptor at PRE_MAIN_LOOP.
The timing port matches kind, ordinal, stable fingerprint, prepared payload and
service boundary before admitting readiness only. Normal production owns the
payload and subsequent 3/9-pattern service. Comparison rows supply no gameplay
values, create no job and call no gameplay owner. No runtime or timing-contract
change accompanies this fixture candidate. Direct callback/ROM evidence shows
MZ2 row101 and MZ3 row102 arming 18 patterns before the Huffman-tree call, while
GHZ2_2 row107 holds false until row108 despite the same coarse held-counter
shape. A global counter-hold removal is consequently rejected.

The earlier candidate's strict unprepared-job failure at ordinal170 remains
historical blocking evidence. Later source fixes do not retroactively qualify
it. Current whole-chain strict consumption/terminal comparison, independent
original-movie controls and a new S1-specific literal publication guard are
required before committing the complete 62-file candidate. Existing S3K-only
publication coverage is not counted as S1 evidence. The initial Bomb test
attempts stopped at production/test compilation due to temporary observer and
probe-harness omissions; they executed no test cases and establish no coverage.

### Current composed qualification and withheld publication

Completed job 38000 tests `2cbcb6e90` plus the two Bomb producer sources,
new producer regression, complete 62-file candidate and literal S1 guard.
Temporary FZ owner callbacks and concrete-test row observers are read-only and
were removed before affected structural guards. The row hook announces the
upcoming movie row **before production**; its state is not a post-row sample.
The selected 17 classes execute 129 cases: 128 pass, one full-chain assertion
fails, zero errors/skips. Maven execution is 1m13s; queue wait was not separately
measured. Selection and environment:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY OPENGGF_S1_CHAIN_ROW_PROBE=1 OPENGGF_S1_FZ_SOLID_PROBE=1 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay -Dsurefire.forkCount=1 -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1CompleteEmeraldRunChain,TestS1CompleteEmeraldRunPrefix,TestS1Slz1CompleteRunTraceReplay,TestS1Slz2CompleteRunTraceReplay,TestS1Slz3CompleteRunTraceReplay,TestS1Sbz2CompleteRunTraceReplay,TestS1Lz2CompleteRunTraceReplay,TestS1Lz3CompleteRunTraceReplay,TestS1CommittedHardwareTimingFixtures,TestSonic1PlcArmTiming,TestHardwareTimingReplayPort,TestHardwareTimingService,TestHardwareTimingRewind,TestHardwareTimingStreamLoader,TestTraceDataHardwareTiming,TestTraceRunHardwareTimingCoordinator,TestTraceV5LoadingContract' "-Dsonic1.rom.path=$S1_ROM" test -B
```

The six independent original-movie controls pass, as do both unchanged prefix
pins, all three literal publication cases and the timing tests (6 PLC arm,
39 port, 25 service, 11 rewind, 19 stream loader, 2 TraceData, 9 run coordinator,
6 v5 contract). All 21 complete level-comparator reports before FZ are exact,
including MZ2/MZ3/LZ3/LZ4 prepared state, SLZ3's former 1456 errors and SBZ2's
one-row ring mismatch. Six special stages and six presentation bridges also
close without a reported axis, through segment32. The FZ comparator is the
22nd level report, now complete through all5086 rows. It has 722 errors:
20 physics (dynamic art), 702 animation. Its first **overall** mismatch is
row4735, native LOOK_UP07/map05 versus engine WAIT05/map01, with all physical
fields matching. First non-camera physics mismatch is the downstream DPLC
mapping5/1 on4736. These are different frontiers; the generic assertion text
calls all722 errors "physics", so the JSON verification groups and first-error
console observation are authoritative for that distinction.

Four full-chain axes were reported:

| Axis | Current measured result |
| --- | --- |
| FZ segment33 | Complete5086 rows; first animation4735, first dynamic-art4736; 20 physics +702 animation errors |
| SBZ2→LZ4 art gap | Expected2 edges, actual12; first expected movie202106/map1, actual201916/map50 |
| LZ4→FZ art gap | Expected2 edges, actual8; first expected movie209046/map1, actual208858/map9 |
| Terminal movie | All remaining10943 rows consumed, expected TITLE_SCREEN, actual LEVEL |

The hardware coordinator closes in the harness `finally` and invokes
`HardwareTimingReplayPort.verifyRunComplete`; no unmatched, missing or late
edge failure accompanies this run. The old ordinal170 failure is not reproduced
on this composition. That strict readiness result does not make the chain or
fixture publication green: terminal and comparison failures still block the
complete candidate. All62 files and the independent S1 literal guard remain
uncommitted; no filtering, partial publication or lower prefix pin is allowed.
The terminal expected-mode assertion precedes the terminal dynamic-art ledger
comparison, so that ledger was **not reached** in job38000. Four reported axes
are not proof of four remaining causes; zero JUnit skips does not establish
terminal-art coverage.
Capture-time Mono/core digests remain **UNKNOWN**.

The boss dependency `9f7c034f` (local `ed56906ef`) is qualified on this route:
pre-production8284 (after8283) has hitCount0, phase06/timer78;8285 still78 and
8286 has77. Before8472 it has phase0A/timer2A/x211E/right2000; before8473 it has
x2126/right2002, proving the native first escape expansion and eight-pixel move.
Before8557 right20AA preserves the incoming player21CB.C300/vx0526; before8558
player21D0.F500/vx0532 survives, closing the prior clamp-fraction error. Internal
phase representation is not asserted identical to the native phase byte during
its explicitly deferred settle tick. No fallthrough or capsule change is used.
The saw source fix `a1f10b680` is independently qualified by original SBZ2 and
its now-exact continuous segment31; no ring/touch threshold was changed.

The FZ354 observation identifies the actual word-write owner. On executed
VInt3030, controller33 enters with the jumped player anim02/previous02 and
instance push keys{33,42}. Its pushing-clear callback changes that pair to00/01
and removes only33. Cylinder42 subsequently clears42 and receives a side/air
callback without another animation-word change. Native auxiliary status for
both owners changes20→00 on354, and native animation is00/map2E. This supports
controller NoCollision then cylinder SideAir as branch **inferences**, not an
instruction trace. Repeated getSolidParams calls overwrite the probe's saved
baseline, so its automatic NoCollision label can be absent despite the observed
word transition. No stale spawn keys appear with the dependency's instance
latches. The existing attack-suppression approximation was not changed.

Producer-only validation is independent of that route: final eight admission,
pressure and destructive-restore cases pass (job75166), and all five existing
motion/animation conversion cases pass on the same production sources
(job86052). The initial restore harness lacked a constructor supported by the
generic probe codec; supplying a harmless zero-argument probe fixes the test
fixture rather than production recreation. The first two attempts executed no
tests due to diagnostic/test compilation omissions. No broad smoke/full suite
is claimed; the parent owns combined delivery validation.

After removing all three FZ diagnostic files/edits and the concrete-chain row
observer, fresh-JVM affected guards pass65 cases (job74205), zero failures,
errors or skips. These are the nine exact classes below, not every XML left in
the reports directory and not the full guards profile. Maven execution1m38s;
queue wait is not separately measured.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS1S2PlcComparisonOnlyGuard,TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestTraceFixtureCompressionGuard,TestTraceFixtureMovieAlignmentGuard,TestTraceFixtureLagPolledInputGuard,TestObjectServicesMigrationGuard,TestRewindCoverageGuard,TestRewindFieldDispositionGuard' test -B
```


## Consumed native level-entry fade boundary

The two remaining transition-art gaps start immediately after their source
segments: SBZ2 ends movie201914 and LZ4 ends208851, while engine extra uploads
start201916/map50 and208858/map9. This is a source-loop scheduling hypothesis,
not a permission to filter the comparison journal. Parent approved ownership of
only GameLoop.startZoneActFade's consumed boundary, a new non-ModApi internal
BlockingLevelEntryFade capability, S1 profile opt-in and focused tests. Event
writers, shared timing/queue/journal code, audio admission and other GameLoop
regions remain untouched.

Retail REV01 Level_MainLoop tests f_restart after ExecuteObjects, before
DeformLayers (sonic.asm:3006,3011-3017). Dynamic events can set restart later in
DeformLayers; marking the event write itself inactive would collapse valid
source work. The engine therefore retains the existing request publication and
only sets its already-rewind-captured inactive flag when GameLoop consumes the
zone/act request and starts the profile's native blocking entry fade.

Direct original-ROM bytes: $3AFE is 4A78 FE02 6600 FC9A, testing restart and
branching to GM_Level at$379E. The pit intercept at$13A2E is
4238 FE30 31FC 0001 FE02 31FC 0103 FE10 4E75; DLE_SBZ3 at$75C0 is
4238 FE30 31FC 0001 FE02 31FC 0502 FE10 11FC 0001 F7C8 4E75.
GM_Level calls ClearPLC at$37B2 (6100 DE22 ->$15D6), then PaletteFadeOut
at$37B6 (6100 E692 ->$1E4A). That routine begins
31FC 003F F626 383C 0015 and its loop runs WaitForVBlank/FadeOut_ToBlack/RunPLC,
then DBF22 (51CC FFEE), without ExecuteObjects (_inc/Palette Fading.asm:134-145).
These observations explain the capability; no row/frame measurement supplies
its selection or duration.

A positive preLevelFadeOutFrames is deliberately not used as a proxy. S2 also
returns22 and creator profiles may supply a duration. Only the explicitly
opted S1 native profile receives the new consumed-entry hold. Unopted profiles,
ordinary callback, respawn and act-completion fades keep their existing policy.
Actual owner-bound callback proxies collect public interfaces; they do not
magically erase an inherited internal marker. An unopted creator profile stays
unopted, while an unchanged inherited native S1 profile retains its native
capability. No new ModApi method, signature pin, state field or rewind adapter
is introduced.

The independent production regression uses both real S1 event producers:
SBZ2 pit interception and SBZ3's upper exit. Each source pass reaches camera,
fixed objects and its ordinary oscillator tail with inactivity still false.
The next GameLoop consumption holds player pose/animation and object dispatch
while the fade progresses, then loads the ROM destination, clears inactivity,
runs its real title and player/object release prelude, and resumes ordinary
execution. Destructive restore uses only the existing transition adapter before
and after consumption; it does not claim whole-registry restoration of an
in-flight callback token. Negative controls retain live gameplay for an unopted
22-frame profile, an ordinary S1 callback fade, and an actual consumed S2 request
with caller deactivation false.

Old-consumer job70556 executed3 cases, with both native source cases failing at
consumed inactivity (expected true, actual false); the unopted control passed.
Initial compile attempts23089/24013 executed no tests; the actual helper's checked
IOException declaration was repaired before build-only test-compile47302 passed.
Candidate32837 executed40 cases:38 passed and both native source cases reached
the destination but failed the new title-seen assertion. That was an intentional
positioned-headless presentation omission in LevelManager.requestTitleCardIfNeeded,
not a production fade defect. The final harness uses a scoped, all-neutral
synthetic input movie through scheduleSessionAtNextLevelLoad, the same existing
production boundary used by continuous replay to request the destination title.
It writes no reentry flag or graphics mode and supplies no trace gameplay data.
All destination/title/prelude assertions remain intact. Corrected job30119 passes
all5 cases, zero failures/errors/skips, queue wait1s and Maven29.790s. The37 cases in the other six
unchanged classes from32837 remain passing (including11 signature cases);
no additional production edits followed those checks.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestGameLoopBlockingLevelEntryFadeS1' "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" test -B
```

Fresh-JVM affected guards71219 pass40 cases (25 hardware timing authority,
10 trace invariants,1 rewind coverage,1 rewind field disposition,3 rule placement),
zero failures/errors/skips, Maven35.239s. The selection is bounded to the new
internal capability, existing captured flag and shared consumption boundary;
parent owns combined smoke. Route qualification of both art-gap hypotheses and
the terminal tail remains pending, so this record claims no continuous-chain
improvement or fixture publication.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestHardwareTimingAuthorityGuard,TestTraceReplayInvariantGuard,TestRewindCoverageGuard,TestRewindFieldDispositionGuard,TestPerGameRuleArchitectureGuard' test -B
```

Owner-bound provider and native PLC fade-owner controls70657 pass23 cases
(21 owner-bound,2 fade-owner), zero failures/errors/skips; queue wait0s and
Maven30.865s. These complement the42 distinct behavioral/signature cases and
40 affected guards, for105 distinct passing focused cases on the unchanged
production gate. Self-review found no source conflict with reserved GameLoop
regions and re-read the ROM bytes above independently of the regression.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestOwnerBoundGamePatch,TestNativePlcFadeOwnerCoverage' test -B
```
