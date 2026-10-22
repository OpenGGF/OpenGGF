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
