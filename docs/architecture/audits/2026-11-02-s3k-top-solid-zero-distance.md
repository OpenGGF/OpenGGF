# S3K top-solid zero-distance audit

This is the first-step record. The [helper-contract follow-up](2026-11-02-s3k-top-solid-helper-contract.md) supersedes its decision to leave the accepting rows unchanged and corrects four false positives in the method-only audit.

Base: `4311f21c4` (origin/develop). Scope: all S3K object source files declaring
`isTopSolidOnly`, including nested providers. False-only full-solid providers are
listed separately below. This is a source/routine audit, not route certification.

## ROM and engine contract

`SolidObjectTop_1P` (sonic3k.asm:41838-41841) sends new contacts to
`loc_1E42E`. `loc_1E45A` (sonic3k.asm:42044-42055) computes object surface minus
(player centre + radius + 4). `bhi` rejects positive results; unsigned
`cmpi.w #-$10` / `blo` also rejects zero, leaving only -16..-1.
`SolidObjCheckSloped2` and `SolidObjCheckSloped` (sonic3k.asm:42116-42165)
also branch to this check. Continued riders use a separate path.

The shared engine provider is deliberately broader than that ROM entry: full
solids, sloped full solids and custom object helpers also implement it. The
original opt-in commit `e124fff0f` incorrectly documented S3K's shared entry as
accepting zero. Commit `ecb1d9244` corrected the documentation, leaving the
compatibility default intact. Therefore the safest bounded correction is to
select the existing ROM-helper predicate at the two measured object owners.
Changing the interface or game-wide default would change unrelated helper
families and require a separate migration with route evidence.

`loc_39CCC` / `loc_39CE8` (sonic3k.asm:77472-77490) call `SolidObjectTop` for
LRZ's collapsing slab. `loc_44ABE` (sonic3k.asm:90041-90049) calls it for SSZ's
floating platform. Both now reject exact-boundary fresh contacts.

## Provider audit

“Reject” means an explicit provider override already exists; conditional overrides
retain their existing player/subtype rules. “Accept” means the inherited S3K
zero-distance policy remains. Additional gaps below are findings, not changes in
this two-object lane: their order, slope sampling and trace frontiers need their
own execution evidence before altering them. In particular, existing CNZ trapdoor
tests explicitly assert the historically incorrect accepting behavior.

| Object | ROM helper family / evidence | Current engine boundary | Changed? |
| --- | --- | --- | --- |
| `AizCollapsingLogBridgeObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `AizFallingLogObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `AizFlippingBridgeObjectInstance` | custom AIZFlippingBridge_CheckPlayer (:59147-59233), same unsigned gate | Accept — additional gap | No |
| `AizTransitionFloorObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `CnzCannonInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `CnzRisingPlatformInstance` | SolidObjectTop (:67188) | Accept — additional gap | No |
| `CnzTrapDoorInstance` | SolidObjectTop, Obj_CNZTrapDoor | Accept — additional gap | No |
| `CollapsingBridgeObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `DezFinalArenaFloor` | SolidObjectTop, loc_5A860 / loc_5A912 | Accept — additional gap | No |
| `DezMinibossArm` | top-solid helper, loc_7E4CE..7E748 | Accept — additional gap | No |
| `FbzDezPlayerLauncherInstance` | SolidObjectTop, Obj_FBZDEZPlayerLauncher | Accept — additional gap | No |
| `FbzDezPlayerLauncherObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `FbzDisappearingPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `FbzEndBossEventControlInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `FbzTrapSpringObjectInstance` | SolidObjectTop (:80649) | Accept — additional gap | No |
| `FloatingPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `HCZHandLauncherObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `HPZSuperEmeraldObjectInstance` | SolidObjectTop, loc_908DE | Accept — additional gap | No |
| `IczBigSnowPileInstance` | SolidObjectTopSloped2 (:110503) | Accept — additional gap | No |
| `IczIceBlockObjectInstance` | SolidObjectTop, Obj_ICZIceBlock | Accept — additional gap | No |
| `IczTensionPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `LbzMovingPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `LrzBossLavaSurfaceObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `LrzBossPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `LrzCollapsingBridgeInstance` | SolidObjectTop (:77478, :77488) | Accept → reject | Yes |
| `MGZSwingingPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `MhzCurledVineObjectInstance` | custom slope → loc_1E45A (:83037) | Accept — additional gap | No |
| `MhzMushroomCapObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `MhzMushroomCatapultObjectInstance` | SolidObjectTopSloped2 (:84280, :84408) | Accept — additional gap | No |
| `MhzMushroomPlatformObjectInstance` | SolidObjectTopSloped2 (:83857) | Accept — additional gap | No |
| `MhzSwingBarVerticalObjectInstance` | SolidObjectTop (:83605) | Accept — additional gap | No |
| `PachinkoFlipperObjectInstance` | SolidObjectTopSloped2 (:96430) | Accept — additional gap | No |
| `PachinkoPlatformObjectInstance` | SolidObjectTop (:96806) | Accept — additional gap | No |
| `S3kDezEnergyBridgeObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `S3kDezLiftPadObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `S3kDezRetractingSpringObjectInstance` | SolidObjectTop_1P | Reject (existing) | No |
| `SSZHPZTeleporterObjectInstance` | SolidObjectTopSloped2 (:91061, :91244) | Accept — additional gap | No |
| `SinkingMudObjectInstance` | SolidObjectTop_1P | Reject (existing) | No |
| `Sonic3kButtonObjectInstance` | SolidObjectTop (top subtype); SolidObjectFull otherwise | Reject (existing) | No |
| `Sonic3kCollapsingPlatformObjectInstance` | SolidObjectTopSloped2 | Reject (existing) | No |
| `SozSpawningSandBlocksObjectInstance` | SolidObjectTop (:85161) | Accept — additional gap | No |
| `SozSpringVineObjectInstance` | SolidObjectTopSloped (:85575) | Accept — additional gap | No |
| `SozSwingingPlatformObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `SszBouncyCloudObjectInstance` | SolidObjectTop_1P (:90643) | Accept — additional gap | No |
| `SszCollapsingBridgeDiagonalObjectInstance` | SolidObjectTopSloped2, Obj_SSZCollapsingBridgeDiagonal | Accept — additional gap | No |
| `SszCollapsingBridgeObjectInstance` | SolidObjectTop (:90181, :90264) | Accept — additional gap | No |
| `SszCollapsingColumnObjectInstance` | SolidObjectTop (:90118) | Accept — additional gap | No |
| `SszCutsceneBridgeObjectInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |
| `SszFloatingPlatformObjectInstance` | SolidObjectTop (:90049) | Accept → reject | Yes |
| `SszRotatingPlatformCarrierObjectInstance` | SolidObjectTop (:91921) | Accept — additional gap | No |
| `SszRotatingPlatformObjectInstance` | SolidObjectTop (:91819) | Accept — additional gap | No |
| `SszSolidCloudObjectInstance` | SolidObjectTopSloped2, loc_57B8E | Accept — additional gap | No |
| `TensionBridgeObjectInstance` | sub_38AA2 → sub_1E410 → loc_1E45A | Reject (existing) | No |
| `HczEndBossWaterColumn` | SolidObjectTop, loc_6B394 | Accept — additional gap | No |
| `LbzEndBossInstance` | SolidObjectTop (caller citation in source) | Reject (existing) | No |

False-only full-solid declarations (no top-solid-default change): `AizDrawBridgeObjectInstance`, `BreakableWallObjectInstance`, `CnzCylinderInstance`, `CorkFloorObjectInstance`, `DoorObjectInstance`, `GumballMachineObjectInstance`, `HCZ2WallObjectInstance`, `IczBreakableWallObjectInstance`, `LbzTubeElevatorInstance`, `Mgz2LevelCollapseSolidInstance`, `SszRetractingSpringObjectInstance`, `SszSwingingCarrierBarObjectInstance`.

## Validation commands and scope

Base measurements use `4311f21c4` plus temporary diagnostic prints and the new
boundary regression, with no gameplay fix. Candidate measurements add the two
provider overrides. Temporary prints are removed before delivery.

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
```

Each trace command below is run before and after the fix, with fresh
`target/trace-reports` and `target/surefire-reports` directories:

```bash
ROM_FLAGS=(
  -Dsonic1.rom.path=$HOME/code/projects/OpenGGF/s1.gen
  -Dsonic2.rom.path=$HOME/code/projects/OpenGGF/s2.gen
  -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen
  -Dkis2.rom.path=$HOME/code/projects/OpenGGF/kis2.gen
  -Dsonic1.rev00.rom.path=$HOME/code/projects/OpenGGF/s1-r0.gen
  -Dsonic2.rev00.rom.path=$HOME/code/projects/OpenGGF/s2-r0.gen
)
R7=TestS3kTailsFullChainLrzSegmentTraceReplay,TestS3kTailsFullChainLrz2SegmentTraceReplay,TestS3kTailsFullChainLrz3SegmentTraceReplay,TestS3kTailsFullChainHpzSegmentTraceReplay,TestS3kTailsFullChainHpz2SegmentTraceReplay,TestS3kTailsFullChainHpz22SegmentTraceReplay,TestS3kTailsFullChainHpz222SegmentTraceReplay,TestS3kTailsFullChainSszSegmentTraceReplay,TestS3kTailsFullChainSsz2SegmentTraceReplay,TestS3kTailsFullChainSsz3SegmentTraceReplay
SEG=TestS3kSonicTailsLrzSegmentTraceReplay,TestS3kSonicTailsHpzSegmentTraceReplay,TestS3kSonicTailsHpz2SegmentTraceReplay,TestS3kSonicTailsHpz3SegmentTraceReplay,TestS3kSonicTailsHpz22SegmentTraceReplay,TestS3kSonicTailsHpz222SegmentTraceReplay,TestS3kSonicTailsSszSegmentTraceReplay
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "-Dtest=$R7" "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "-Dtest=$SEG" "${ROM_FLAGS[@]}" test
```

The zone/fixture search finds nine isolated segment classes for internal zones
9 (LRZ) and 10 (Sky Sanctuary, fixture name `hpz`). The selection additionally
includes HPZ22/222 and SSZ-named classes as conservative neighboring-route
checks. Run-chain tests starting in AIZ do not reach these objects at their
current earlier frontiers; this is isolated segment coverage, not campaign
continuation certification. No shared default changed, so full trace profiles
and broad smoke are outside this lane's requested validation.

Negative control, before adding the opt-ins:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -B '-Dtest=TestSolidObjectManager#s3kLrzBridgeAndSszPlatformRejectZeroDistanceButLandInside' test
```

Result: one failure, zero errors/skips; LRZ overlap 0 incorrectly lands. This
intentionally red check establishes that the new regression discriminates.

Post-fix boundary and keep-green selection:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B '-Dtest=TestSolidObjectManager#s3kLrzBridgeAndSszPlatformRejectZeroDistanceButLandInside,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "${ROM_FLAGS[@]}" test
```

Diagnostic execution evidence on baseline: zero-distance collision branches ran
with `sticky=false`, opt-in false and falling speed preserved on entry: Tails LRZ
`frameCounter=219`, speed 1962 (`0x07AA`); Sonic/Tails LRZ `frameCounter=209`, speed
1981 (`0x07BD`); Tails HPZ `frameCounter=1838`, speed 616 (`0x0268`); Sonic/Tails
HPZ `frameCounter=1739`, speed 616. These manager clocks are not trace rows.
Completed comparison reports locate their corresponding velocity-clearing
mismatches at rows 218, 208, 1841 and 1743 respectively.

## Measured results

All 17 trace classes completed on baseline and candidate, with zero skips and
zero infrastructure errors. All remain comparison-red at their remaining
frontiers. Four target classes improve; the other thirteen retain their counts
and first physics/animation errors. No measured class worsened on those metrics.
Counts below are report `error_count`, with physics and animation combined;
rows are report indices. Compared-row counts match between each pair.

| Class | Rows compared | Errors before → after | First physics error before → after | First animation error before → after |
| --- | ---: | ---: | --- | --- |
| `TestS3kTailsFullChainHpz222SegmentTraceReplay` | 4457 | 237 → 237 | 0 `camera_x` → 0 `camera_x` | 482 `player_animation_id` → 482 `player_animation_id` |
| `TestS3kTailsFullChainHpz22SegmentTraceReplay` | 12747 | 709 → 709 | 0 `y_speed` → 0 `y_speed` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainHpz2SegmentTraceReplay` | 10419 | 932 → 932 | 0 `x` → 0 `x` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainHpzSegmentTraceReplay` | 5865 | 1013 → 742 | 1841 `y_speed` → 2159 `y` | 1848 `player_mapping_frame` → 2159 `player_animation_id` |
| `TestS3kTailsFullChainLrz2SegmentTraceReplay` | 9405 | 1051 → 1051 | 0 `camera_y` → 0 `camera_y` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainLrz3SegmentTraceReplay` | 15175 | 1074 → 1074 | 0 `y_speed` → 0 `y_speed` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kTailsFullChainLrzSegmentTraceReplay` | 4452 | 659 → 307 | 218 `y_speed` → 2120 `rings` | 302 `player_animation_id` → 2592 `player_animation_id` |
| `TestS3kTailsFullChainSsz2SegmentTraceReplay` | 5083 | 265 → 265 | 0 `camera_y` → 0 `camera_y` | 157 `player_animation_id` → 157 `player_animation_id` |
| `TestS3kTailsFullChainSsz3SegmentTraceReplay` | 3820 | 437 → 437 | 0 `camera_y` → 0 `camera_y` | 405 `player_animation_id` → 405 `player_animation_id` |
| `TestS3kTailsFullChainSszSegmentTraceReplay` | 23127 | 990 → 990 | 0 `camera_x` → 0 `camera_x` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kSonicTailsHpz222SegmentTraceReplay` | 14592 | 1758 → 1758 | 0 `rings` → 0 `rings` | 889 `tails_animation_id` → 889 `tails_animation_id` |
| `TestS3kSonicTailsHpz22SegmentTraceReplay` | 1991 | 489 → 489 | 0 `y_speed` → 0 `y_speed` | 0 `player_animation_id` → 0 `player_animation_id` |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | 4208 | 1177 → 1177 | 0 `rings` → 0 `rings` | 619 `player_animation_id` → 619 `player_animation_id` |
| `TestS3kSonicTailsHpz3SegmentTraceReplay` | 3792 | 283 → 283 | 0 `rings` → 0 `rings` | 985 `player_animation_id` → 985 `player_animation_id` |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | 7474 | 2157 → 1781 | 1743 `y_speed` → 1948 `y` | 121 `tails_mapping_frame` → 121 `tails_mapping_frame` |
| `TestS3kSonicTailsLrzSegmentTraceReplay` | 38813 | 8897 → 8328 | 208 `tails_y_speed` → 2355 `tails_cpu_respawn_counter` | 208 `tails_animation_id` → 2726 `tails_animation_id` |
| `TestS3kSonicTailsSszSegmentTraceReplay` | 39989 | 3076 → 3076 | 0 `camera_x` → 0 `camera_x` | 4198 `player_mapping_frame` → 4198 `player_mapping_frame` |

Keep-green plus boundary command: **61 passed, zero failures/errors/skips**.
The selector matches both classes named `TestSonic3kLevelLoading`: 36 tests in
`game.sonic3k`, seven in `tests`; the other counts are AIZ skip eight, bootstrap
six, decoding three and the boundary regression one. Negative control: one
expected assertion failure on the unmodified provider, no errors/skips.

Post-fix diagnostic execution saw the same Tails contact branches with rejection
true (LRZ counter 219 and HPZ counter 1838), before the probes were removed.
The final boundary/keep-green and Sonic/Tails trace commands ran with probes
removed. The Tails trace command measured the same gameplay with diagnostic
prints; removing those prints is the only subsequent runtime-source difference.

`git diff --check` passes. The disassembly citation check exits zero and accepts
the new call sites; it also reports 13 pre-existing stale citations elsewhere
in the LRZ implementation. No broad smoke or full trace profiles were run.
Queue waits dominated elapsed time (roughly ten minutes per admission, with
requests overlapping); these are not execution runtimes or additive delivery
time. No aggregate delivery-time measurement was taken.

Remaining limits: later trace divergences still exist, and the additional audit
gaps above are unchanged. This correction is not a claim of complete S3K
landing parity or run-chain certification.

## Proposed frontier-log entry

2026-11-02 — S3K SolidObjectTop exact-boundary landing: at base `4311f21c4`,
prove LRZ collapsing slabs (`loc_39CCC` / `loc_39CE8`) and SSZ floating platforms
(`loc_44ABE`) reach `loc_1E45A`'s unsigned -16..-1 window; add per-object
zero-distance rejection. Sonic/Tails LRZ: 8897→8328 errors, first physics
208 `tails_y_speed`→2355 `tails_cpu_respawn_counter`; Tails LRZ: 659→307,
218 `y_speed`→2120 `rings`. Sonic/Tails HPZ: 2157→1781, physics
1743 `y_speed`→1948 `y` (animation row121 unchanged); Tails HPZ: 1013→742,
1841 `y_speed`→2159 `y`. Thirteen neighboring trace classes retain counts and
first errors; zero skips. Boundary plus keep-green: 61 pass. All trace classes
remain red at later/inherited frontiers. See this audit for exact commands,
ROM evidence and the additional unchanged provider gaps.
