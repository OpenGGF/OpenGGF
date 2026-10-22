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
