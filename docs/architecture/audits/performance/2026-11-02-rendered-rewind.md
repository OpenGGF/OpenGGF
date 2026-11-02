# LRZ1 and SOZ2 rendered rewind

The unchanged-layout restore path rebuilt both terrain tilemaps for every held
rewind picture. Preserving the captured map-byte identity and retaining the
camera-independent background removes that work without skipping gameplay restore
or rendering.

## Result

Five runs per binary and scenario used the same version-3 probe in separately
compiled worktrees. The table reports the median of each run's average service
time, including the live Engine tick, drawing and `glFinish`, without frame pacing.
Ranges are the minimum and maximum run averages. These are local measurements,
not a claim about every device or viewport.

| Scenario | Baseline average, ms | Fixed average, ms | Reduction | Baseline range, ms | Fixed range, ms |
| --- | ---: | ---: | ---: | --- | --- |
| LRZ1 entry | 12.764 | 3.434 | 73.1% | 12.729 to 12.955 | 2.929 to 3.594 |
| SOZ2 before boss | 12.773 | 2.659 | 79.2% | 12.192 to 12.894 | 2.565 to 2.891 |

Every baseline run rebuilt foreground and background 180 times during 180 measured
reverse services. Every fixed run rebuilt neither. LRZ1 used a 1024 by 320 tile
background; SOZ2 used 592 by 256 tiles. Both measured backgrounds were unwrapped.
The SOZ2 result does not qualify other event-owned or wrapped background stages.

All 20 runs selected exactly one earlier history frame per service. All 60 engine
forward-versus-restored picture comparisons matched. They are engine regression
checks, not emulator parity evidence. The route cursor and selected history frames
matched across binaries and repetitions. There were no failed or skipped runs in
the accepted batch. Its isolation audit reported no competing build, test or
profiler process. Execution took 175 seconds after 510 seconds of queue waiting.

## Cause and fix

The baseline is `e2c32e2517d51098f5007f61f76ea5c93b012deb`.
`S3kCollisionLayoutRewindAdapter` runs after the shared level adapter. It recreated
the map, copied its byte array and unconditionally rebound the level geometry.
This invalidated both tilemaps immediately and manufactured a different map-byte
identity for the following shared restore. Even unchanged level snapshots paid
for full-level descriptor construction and texture upload.

The adapter still restores encoded layout, dimensions and background collision
rows. It preserves the captured COW map-byte reference and rebinds derived geometry
only when encoded bytes, map identity or dimensions differ. Shared block and chunk
changes retain their own invalidation in `LevelRewindSnapshotAdapter`.

The post-restore tilemap hook also retains a valid full-layout background with no
active descriptor owner. It does not clear pending invalidation. Camera windows,
nonzero descriptor revisions, retained planes and persistent physical planes keep
their existing restore paths.

## Rejected explanations and controls

- The first logic-only fixed-snapshot probe omitted drawing and cold replay. It
  could not resolve the reported held-rewind symptom.
- Animated-art restore uses unbatched uploads, but the profile did not justify
  changing it in this task.
- Java leaf samples initially suggested a background-only problem. Full stacks
  split 1,346 `writeChunkAt` samples into 459 background and 887 foreground callers.
  Sample counts identify callers; they are not wall-time percentages.
- The background-only cache patch passed its focused tests but did not reduce
  the actual rebuild count. Both arms of that preliminary comparison rebuilt the
  background 180 times. The missing owner was the S3K collision-layout adapter.
- A forced-invalidation control was replaced by actual baseline and fixed
  binaries. The original profiled diagnostic and the later comparison have
  different warm-up protocols; their timings are not compared.
- A positioned SOZ2 probe failed to retain history. The accepted scenario reaches
  the area through the committed cold Sonic/Tails input movie instead.
- Two isolation attempts were rejected when another worktree launched an unleased
  JVM or compiler. Their timings are excluded. Shared build-lease coordination
  preceded the accepted batch.

## Workload and reproduction

The ROM was S3&K locked-on, CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. Java 25 builds ran on the 32-logical-CPU
Linux host. The GL renderer reported AMD Ryzen 9 9950X, radeonsi,
`raphael_mendocino`, ACO. Both binaries used a 3 GiB Java heap, NTSC 60 Hz,
320 by 224 output, Sonic with Tails, one rewind step per service and no-device
SMPS synthesis. No desktop input or audio device was used.

[The probe](../../../../src/test/java/com/openggf/TestRenderedRewindPerformance.java)
boots `GameplayCaptureSession`, binds the actual Engine scheduler and held-key
owner, and completes every measured picture on GL. Each fresh JVM performs 360
forward services, 30 reverse warm-up services and 180 measured reverse services.
For SOZ2, it first follows
`src/test/resources/routes/s3k/soz2-cold-sonic-tails.bk2` to X >= `$4000` with the
same cold-entry setup as the existing route fixture. The measured forward window
ends at input cursor 25,342. No physics or auxiliary trace rows supply state.

To reproduce, compile the same probe source in an isolated baseline checkout and
the candidate checkout. Keep their build trees separate. Use test-scope dependency
classpaths and run `com.openggf.TestRenderedRewindPerformance` as a standalone Java
main under [run_isolated.py](../../../../tools/performance/run_isolated.py).
Pass these common JVM properties and a separate output directory per run:

```text
-Dopenggf.headless.gl=surfaceless-egl
-Ds3k.rom.path=/absolute/existing/locked-on.gen
-Dopenggf.perf.variant=baseline-or-fixed
-Dopenggf.perf.output=/absolute/task-directory/run
```

Unset `DISPLAY` and `WAYLAND_DISPLAY`. Use the default zone 9, act 0 for LRZ1.
For SOZ2, add:

```text
-Dopenggf.perf.zone=8
-Dopenggf.perf.act=1
-Dopenggf.perf.route=src/test/resources/routes/s3k/soz2-cold-sonic-tails.bk2
-Dopenggf.perf.routeX=0x4000
```

Run five pairs in A/B, B/A, A/B, B/A, A/B order. Do not include `openggf.perf.jfr`
in timed runs. A separate `-Dopenggf.perf.jfr=true` run supplies sampling evidence.
The explicit JUnit entry is `-Pbenchmarks -Dtest=TestRenderedRewindPerformance`.
This is manual performance coverage, excluded from routine smoke.

## Correctness checks

The background-cache component passed 153 focused checks. After the S3K owner fix,
118 affected checks passed with no failures or skips. The final selection was:

```text
TestS3kCollisionLayoutTilemapRestore
TestS3kLrzBossCameraHeadless
TestSonic3kLbzLaunchSignals
TestDezFinalScreenEntry
TestS3kSlotPlanePublication
TestS3kAiz1SkipHeadless
TestSonic3kLevelLoading
TestSonic3kBootstrapResolver
TestSonic3kDecodingUtils
```

These ran through `maven_queue.py -Dmse=off -Dtest=... test -B`, with the explicit
EGL property, both display variables unset and verified absolute S1, S2 and S3&K
ROM properties. Coverage includes unchanged whole-world restore, production chunk
mutation rollback, encoded row-pointer changes after shared map restoration,
scene/slot publication, required S3K loading checks and LRZ camera/terrain replay.
The existing full-layout mutation test remains in
`TestLevelTilemapManagerRewindReset`.

The change-based broad plan selected 3,361 smoke classes. Focused checks replaced
that disproportionate selection because the change only avoids redundant derived
cache invalidation. Wrapped/retained/descriptor-owned images and real geometry
mutations have direct regressions. No full suite or exhaustive route sweep ran.
