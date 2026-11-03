# LRZ boulder lifetime and row-2726 hurt

Base: origin/develop `e6a8b4a34`. ROM: original locked-on S3K; JDK25,
surfaceless EGL, single Surefire fork, alphabetical order. No comparison weakened.

## Executed mechanism

A temporary touch probe at Tails (0E58,022A) found no active LRZ boulder and
only the DE8,234 monitor in the touch list. A second probe executed the unload
branch: base=0E70, live=0EAF,0226, camera=0E04, unsigned delta=FFFC, GRIND.
A later allocation was deleted at camera0EFE with deltaFF02. These probes
were removed. Native aux has the boulder in slot29 at0E3B,0224 on row2725.
At2726 Tails has CPU routine6, object_control00, no ride, airborne, routine4,
y_velFC00. Engine retained routine2/roll and FA5C because that hazard was gone.

`loc_1B7F2 (sonic3k.asm:37591-37598)` derives coarse-back camera as
(cameraX-$80)&$FF80. `loc_1B666 (sonic3k.asm:37412-37417)` subtracts it from
aligned placed X. At camera0E04 the proper distance is0080, notFFFC.
The object now calls the existing coarse-camera helper; no shared owner changed.

## Matched measurements

| r7 class suffix | Before errors / first row-field | After errors / first row-field |
| --- | --- | --- |
| SonicTailsLrzSegmentTraceReplay | 8325 / 2726 tails_y_speed | 8114 / 3415 tails_x_speed |
| TailsFullChainLrzSegmentTraceReplay | 307 / 2120 rings | 214 / 2120 rings |
| TailsFullChainLrz2SegmentTraceReplay | 1051 / 0 camera_y | identical |
| TailsFullChainLrz3SegmentTraceReplay | 1074 / 0 y_speed | identical |

All four complete, zero skips. No newly red class or earlier first error.
Expanded row/field lists first remove Sonic+Tails tails_animation_id at2726;
first addition is tails_y_speed at3452, after the remaining corkscrew divergence3415.
Solo first removes g_speed at2592; first addition angle3193 follows its remaining
motion divergence3186. LRZ2/LRZ3 lists are identical. The solo ring blip2120
self-heals and is not this boulder cause; later solo errors share the lifetime cause.
LRZ2/LRZ3 are bonus-return entry/carried-state frontiers (74/304 rings versus0),
held under user policy A/B; no row-state hydration or entry changes attempted.

69 ordinary focused tests pass, zero skips. Restoring the old raw-camera formula
intentionally makes the exact unload regression fail (1 test/1 failure/0 skips),
proving discrimination; the corrected formula was restored afterward.
Existing citation-check warnings elsewhere in this object's older documentation
remain inherited; both newly added routine citations validate.

## Commands

All Maven invocations use `python3 tools/testing/maven_queue.py -Dmse=off -B`,
with all six absolute ROM properties from the lane brief, and its exact environment:
DISPLAY/WAYLAND_DISPLAY/JDK_JAVA_OPTIONS/MAVEN_ARGS/LD_LIBRARY_PATH unset;
JAVA_HOME=/usr/lib/jvm/java-25-openjdk, LUA_BIN=/usr/bin/lua5.4,
JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl, ~/.local/bin prepended to PATH.

Replay arguments (before and after):

```text
-Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical
-Dtest=TestS3kSonicTailsLrzSegmentTraceReplay,TestS3kTailsFullChainLrzSegmentTraceReplay,TestS3kTailsFullChainLrz2SegmentTraceReplay,TestS3kTailsFullChainLrz3SegmentTraceReplay test
```

Ordinary arguments:

```text
-Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical
-Dtest=TestLrzSpikeBall,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test
```

Negative control uses that ordinary command with
`-Dtest=TestLrzSpikeBall#theUnloadTestUsesThePlacedXAndNotTheSwungOne`.
The two same-name level-loading classes both ran. Validation is bounded focused
coverage, not a full-profile or full-suite pass. The full-three-profile shared-owner
gate is not triggered by this object-local lifetime correction.
