# S3K native top-solid helper contract follow-up

## Source and decision

Baseline: accepted first-step fix rebased as `6f454d71b808964a7346fc98e4646321f0ea03c2`
on `origin/develop` `506c653a0`. The branch remains isolated; no merge is authorized.
This follow-up supersedes the earlier audit's decision to leave accepting rows
unchanged. It also corrects that audit's method-only classification.

Commit `e124fff0f` introduced the opt-in and incorrectly described S3K's shared
SolidObjectTop entry as accepting zero. `ecb1d9244` corrected the documentation,
but did not migrate the remaining callers. There is no ROM reason for a native
SolidObjectTop-family caller to retain the accepting default. The generic
SolidObjectProvider remains broader than that family: full-solid-style
helpers accept zero even within S3K. The player's GameRules are also
the wrong owner for an object's native helper in mixed-game/donor configurations
(ObjectSolidContactController.collisionRulesOrNull reads player.getGameRules()).

The fix therefore selects a semantic native provider at each proved caller:
S3kTopSolidProvider for flat helpers; S3kSlopedTopSolidProvider for raw sampled
surfaces. The native provider rejects zero by default, independently of the
player's donor. There are no object/zone-name conditions in shared gameplay code.
Full-solid providers remain on their existing helper contract. Already-correct
per-object rejection overrides, including the first-step LRZ/SSZ fix, retain
existing behavior; this is an incremental migration of the remaining callers.

The contrast is **not** that S1/S2 PlatformObject accept zero. S1's
Plat_NoXCheck_AltY (docs/s1disasm/_incObj/sub PlatformObject & SlopeObject.asm:42-51)
and S2's PlatformObject_ChkYRange (s2.asm:35968-35977) use the same unsigned
negative window and also reject it. The S1/S3K compatibility game flags that
allow zero are historical engine defaults, not proof of those ROM helpers'
semantics. Full-solid top checks differ: S3K loc_1E0D4
(sonic3k.asm:41586-41591), S2 SolidObject_TopBottom (s2.asm:35494-35500), and
S1 Solid_TopBottom (docs/s1disasm/_incObj/sub SolidObject.asm:270-275)
branch to landing for nonnegative overlap below 16, including zero. That
within-game helper distinction is the reason not to turn a generic collision
flag into the authority. This task migrates the proved S3K callers; it does not
claim to correct inherited S1/S2 platform defaults.

## ROM contract and necessary surface corrections

SolidObjectTop_1P (sonic3k.asm:41838-41867) uses d1's exact horizontal window;
there is no sticky edge extension. Its new contact goes to loc_1E42E.
loc_1E44C and loc_1E45A (sonic3k.asm:42038-42063) use objectY-d3 as surface,
subtract playerY+radius+4, then reject positive results and unsigned values
below $FFF0. Only -16..-1 survive; zero is below $FFF0. The accepted write is
playerY += d0+3 before RideObject_SetRide, not a PlatformObject absolute re-seat
using a subsequently restored radius. The native provider selects the d3 (groundHalfHeight) detection surface.
Existing owner-specific placement modes remain in force, including the separate
flat reverse-gravity re-seat; this change does not replace those with a universal
upright snap. A generic TopSolidLandingWindowProvider capability supplies the
exclusive limit to either collision model without identifying a game or object.
Constant topSolid profile factories are replaced by fromProvider so they do not
silently discard the helper's selected flags.

Using the right surface is necessary before applying the unsigned window.
CNZ's rising platform passes d3=$11 at loc_31BD2 (sonic3k.asm:67184-67188),
but the old detector used d2=$10. At the old detector's zero, native overlap is
already one: preserving the wrong surface while merely rejecting zero would
introduce a late landing. Other explicit d3 cases include the DEZ arm ($15,
not d2=8), SSZ collapsing bridge (9, not d2=$10), and bouncy cloud
(0, not d2=$10; sub_45170, sonic3k.asm:90638-90643).

SolidObjCheckSloped2 / SolidObjCheckSloped (sonic3k.asm:42116-42165) instead
supply objectY minus the raw signed slope byte to the same window. Their native
provider selects direct overlap 1..16, independent of body height or the generic
full-solid side/bottom classifier. The curled vine supplies segmentY-8 at
loc_3EA1E (sonic3k.asm:83021-83037). AIZFlippingBridge_CheckPlayer duplicates the
unsigned predicate at loc_2AC82 (sonic3k.asm:59198-59229).

AIZ flipping bridge previously negated its height samples even though both ROM
and resolver subtract them. AIZFlippingBridge_ArcHeights (sonic3k.asm:59061-59065)
starts at -$1C, putting that surface 28 pixels below objectY. Returning the raw
table fixes the window's surface; it is not a fixture-specific adjustment.

## Corrected caller audit

The first audit found 27 missing boolean overrides. Four already reject zero
via an explicit direct-overlap limit of 17; they were false positives in its
"accept" column. The expanded search also checks topSolid profile factories,
finding the LRZ dome platform missed by a search for isTopSolidOnly methods.
All 28 routes below are proved. No unproved gap is changed. The four existing
direct-window providers are bound to the semantic contract without claiming a
new zero-distance correction for them. Existing full-solid and already-rejecting
rows remain listed in the first audit.

| Object | Proved ROM call / equivalent gate | Prior zero boundary | New binding | Zero behavior changed? |
| --- | --- | --- | --- | --- |
| `AizFlippingBridgeObjectInstance` | loc_2AC82 (sonic3k.asm:59198-59198) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `CnzRisingPlatformInstance` | loc_31BD2 (sonic3k.asm:67184-67188) | Accepts | S3kTopSolidProvider | Yes |
| `CnzTrapDoorInstance` | loc_31CCA (sonic3k.asm:67262-67270) | Accepts | S3kTopSolidProvider | Yes |
| `DezFinalArenaFloor` | loc_5A860 (sonic3k.asm:120737-120742); loc_5A8C4 (sonic3k.asm:120766-120773); loc_5A912 (sonic3k.asm:120790-120795) | Accepts | S3kTopSolidProvider | Yes |
| `DezMinibossArm` | loc_7EC24 (sonic3k.asm:169156-169160) | Accepts | S3kTopSolidProvider | Yes |
| `FbzDezPlayerLauncherInstance` | loc_3B9AC (sonic3k.asm:79467-79477); loc_3BA70 (sonic3k.asm:79543-79547) | Accepts | S3kTopSolidProvider | Yes |
| `FbzTrapSpringObjectInstance` | loc_3CBA6 (sonic3k.asm:80644-80649) | Accepts | S3kTopSolidProvider | Yes |
| `HPZSuperEmeraldObjectInstance` | loc_908DE (sonic3k.asm:197812-197817) | Accepts | S3kTopSolidProvider | Yes |
| `HczEndBossWaterColumn` | HCZEndBossPlatform_Main (sonic3k.asm:141154-141164); HCZEndBossPlatform_SolidWait (sonic3k.asm:141252-141257) | Accepts | S3kTopSolidProvider | Yes |
| `IczBigSnowPileInstance` | loc_53A5A (sonic3k.asm:110496-110503) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `IczIceBlockObjectInstance` | loc_8A344 (sonic3k.asm:187872-187877) | Accepts | S3kTopSolidProvider | Yes |
| `LrzDomeLavaPlatformObjectInstance` | loc_56F0C (sonic3k.asm:115658-115670) | Accepts | S3kTopSolidProvider | Yes |
| `MhzCurledVineObjectInstance` | loc_3EA1E (sonic3k.asm:83021-83037) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `MhzMushroomCatapultObjectInstance` | loc_3F966 (sonic3k.asm:84269-84280); loc_3FA8E (sonic3k.asm:84398-84408) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `MhzMushroomPlatformObjectInstance` | loc_3F3CC (sonic3k.asm:83852-83857) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `MhzSwingBarVerticalObjectInstance` | loc_3F08C (sonic3k.asm:83600-83605) | Accepts | S3kTopSolidProvider | Yes |
| `PachinkoFlipperObjectInstance` | loc_49C8A (sonic3k.asm:96425-96430) | Accepts | S3kSlopedTopSolidProvider | Yes |
| `PachinkoPlatformObjectInstance` | loc_4A1B2 (sonic3k.asm:96797-96806) | Accepts | S3kTopSolidProvider | Yes |
| `SSZHPZTeleporterObjectInstance` | loc_45616 (sonic3k.asm:91055-91061); sub_45856 (sonic3k.asm:91240-91244) | Already rejects via direct 1..16 | S3kSlopedTopSolidProvider | No; already rejected |
| `SozSpawningSandBlocksObjectInstance` | loc_403A2 (sonic3k.asm:85158-85161) | Accepts | S3kTopSolidProvider | Yes |
| `SozSpringVineObjectInstance` | loc_40852 (sonic3k.asm:85569-85575) | Already rejects via direct 1..16 | S3kSlopedTopSolidProvider | No; already rejected |
| `SszBouncyCloudObjectInstance` | sub_45170 (sonic3k.asm:90638-90643) | Accepts | S3kTopSolidProvider | Yes |
| `SszCollapsingBridgeDiagonalObjectInstance` | loc_44D98 (sonic3k.asm:90279-90285); loc_44EF6 (sonic3k.asm:90420-90426) | Already rejects via direct 1..16 | S3kSlopedTopSolidProvider | No; already rejected |
| `SszCollapsingBridgeObjectInstance` | loc_44C76 (sonic3k.asm:90176-90181); loc_44D48 (sonic3k.asm:90259-90264) | Accepts | S3kTopSolidProvider | Yes |
| `SszCollapsingColumnObjectInstance` | loc_44B9E (sonic3k.asm:90108-90118) | Accepts | S3kTopSolidProvider | Yes |
| `SszRotatingPlatformCarrierObjectInstance` | loc_45F3A (sonic3k.asm:91915-91921) | Accepts | S3kTopSolidProvider | Yes |
| `SszRotatingPlatformObjectInstance` | loc_45E2E (sonic3k.asm:91812-91819) | Accepts | S3kTopSolidProvider | Yes |
| `SszSolidCloudObjectInstance` | loc_57B8E (sonic3k.asm:116770-116778) | Already rejects via direct 1..16 | S3kSlopedTopSolidProvider | No; already rejected |

## Baseline and discrimination

Before runs measured the rebased first-step gameplay. Temporary report-owner
prints carry no gameplay authority. The segments/r6 baseline additionally has a
once-per-provider zero-contact probe; it changes no predicates or player state.
The full r7 baseline preceded that probe. Runtime zero-contact probes are removed
before the after campaign; only the test-artifact owner print remains during
comparison collection and is removed before delivery.

- Full trace-replay-r7: 117 tests, 84 failures, 12 errors, no skips.
- Full trace-segments: 70 tests, 48 failures, eight errors, no skips.
- S3K classes selected by trace-replay: 86 tests, six failures, one error, two skips.
- Keep-green plus all solid-manager tests: 147 tests, one deliberate negative-control
  failure, no errors/skips. All 60 keep-green tests passed. The corrected CNZ
  assertion fails because the old provider lands at zero.

The two r6 skips are TestS3kBonusRoundTripChain's gumballRoundTrip and
pachinkoRoundTrip, whose optional run directories do not exist. The S3K ROM
SHA-1 is CFBF98C36C776677290A872547AC47C53D2761D6. No ROM tests were silently
skipped for a missing ROM property.

Existing errors are retained by exact class/test identity: missing recorder
expected_movie_end_mode for complete-campaign gates, MegaRunChain AIZ2 KosM art
admission, Tails FBZ2 KosM FIFO capacity, special-stage hardware-completion closure,
and the Sonic/Tails AIZ2 invalid rewind reference closure. Their outcomes must
be compared after, not silently treated as passing coverage.

The baseline probe observes accepted zero contacts on MhzCurledVineObjectInstance
(manager counters 362 and 858, falling speeds 1232 and 840) and PachinkoFlipperObjectInstance
(counters 1422 and 266, speeds 2208 and 968). These are manager clocks, not trace
row numbers. The survey's earlier Pachinko shield/bumper frontiers and the MHZ
wall-push frontiers are separate hypotheses; do not claim they move without the
completed comparisons.

The initial summary collector handled standalone error_count but omitted the
live-chain errorCount schema. A focused rerun of the four r7 chain classes
recollected those numbers on unchanged gameplay: seven tests, three existing
errors, no failures/skips. Its report ownership and suite identities match the
full baseline; the full profile was not rerun.

## Validation commands

```bash
unset DISPLAY WAYLAND_DISPLAY JDK_JAVA_OPTIONS MAVEN_ARGS LD_LIBRARY_PATH
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
export JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl
export PATH="$HOME/.local/bin:$PATH"
ROM_FLAGS=(
  -Dsonic1.rom.path=$HOME/code/projects/OpenGGF/s1.gen
  -Dsonic2.rom.path=$HOME/code/projects/OpenGGF/s2.gen
  -Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen
  -Dkis2.rom.path=$HOME/code/projects/OpenGGF/kis2.gen
  -Dsonic1.rev00.rom.path=$HOME/code/projects/OpenGGF/s1-r0.gen
  -Dsonic2.rev00.rom.path=$HOME/code/projects/OpenGGF/s2-r0.gen
)
```

The full-profile commands are run once before and once after:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "${ROM_FLAGS[@]}" test
```

The r6 selector is derived from its POM include/exclude paths and scope tags,
including every S3K test class in that profile:

```bash
R6=TestS3kAizTraceReplay,TestS3kBonusComparedSpriteSeam,TestS3kBonusRoundTripChain,TestS3kBonusTerminalScope,TestS3kCnzTraceReplay,TestS3kHardwareTimingReplay,TestS3kKnucklesLbz2BigArmTraceReplay,TestS3kMgzF498AirRollPhysics,TestS3kMgzTraceReplay,TestS3kReplayCheckpointDetector,TestS3kRequiredCheckpointGuard,TestS3kSlotsBonusTraceReplay,TestS3kSonicTailsCompleteEmeraldRunChain,TestS3kSonicTailsCompleteEmeraldRunPrefix
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical "-Dtest=$R6" "${ROM_FLAGS[@]}" test
```

Before keep-green and negative control used a private Surefire directory so the
queued full profiles could not delete its reports:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Dopenggf.surefire.reports=target/topsolid-before-keep '-Dtest=TestSolidObjectManager,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "${ROM_FLAGS[@]}" test
```

The chain recollection used private report directories:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.surefire.reports=target/topsolid-chain-before/surefire -Dopenggf.trace.reports=target/topsolid-chain-before/trace '-Dtest=TestS3kKnucklesSuperEmeraldRunChain,TestS3kMegaRunChain,TestS3kTailsFullChainRunChain,TestS3kTailsFullChainRunPrefix' "${ROM_FLAGS[@]}" test
```

Focused candidate iteration:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off -B -Dtest=TestSolidObjectManager test
```

The first lean focused attempt exhausted the 1 GiB compiler heap during testCompile (Java 25.0.5); no tests executed. It is rerun with --normal, without dropping flags or coverage.

The first normal focused run executed 89 tests: 88 passed; the new mixed-rule
flat witness failed at native overlap 1. It exposed the global contact path
hard-coding d2 while the inline path honored d3. Direct-window providers now
use their selected surface in both paths; ordinary classic/full-solid global
classification is unchanged. A reverse-gravity witness also pins the distinct
flat entry and final placement.

The corrected normal focused selection passes all 90 tests, zero failures/errors/skips.
It covers d3 and 0/1/16/17 overlaps under S1/S2/S3K player rules, signed raw
slope samples independent of body height, and the flat reverse-gravity entry.

## Initial candidate comparison (delivery blocked)

The full r7 and segment after measurements have completed on the initial
semantic-provider candidate. Native rejection is retained while regressions
are investigated; these results do not qualify the branch for integration.
The r6 after run completed: 86 tests, 12 failures, one error, two skips. Keep-green plus solid-manager/AIZ rewind
checks pass 157 tests, with no failures, errors or skips. A separate 252-test
object selection has one failure: the Pachinko platform profile test expects
the old sticky edge buffer. The native exact-X contract requires updating
that expectation, not retaining a nonexistent ROM tolerance.

Initial after totals: r7 117 tests / 84 failures / 12 errors / no skips;
segments 70 / 49 / 8 / no skips. The extra failing segment class is AIZ's
previously green zone slice. Exact per-class changes follow. Error counts
are grouped spans; cells sum the spans' compared fields, so a larger group
count can coexist with fewer bad comparison cells. Neither measure alone
proves a causal improvement after an earlier divergence.


### Initial trace-replay-r7 changes

| Class | Groups before → after | Cells before → after | First physics row/field before → after |
| --- | --- | --- | --- |
| `TestS3kSonicTailsDez238SegmentTraceReplay` | 819 → 772 | 72740 → 72154 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainDez238SegmentTraceReplay` | 434 → 412 | 58649 → 58629 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainHpzSegmentTraceReplay` | 742 → 740 | 43392 → 43467 | 2159 y → 2159 y |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | 1781 → 1808 | 138288 → 137906 | 1948 y → 1948 y |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | 1177 → 1103 | 88174 → 90814 | 0 rings → 0 rings |
| `TestS3kSonicTailsMhz2SegmentTraceReplay` | 631 → 628 | 106528 → 106530 | 0 tails_y_speed → 0 tails_y_speed |
| `TestS3kSonicTailsMhz7SegmentTraceReplay` | 493 → 494 | 50763 → 50822 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainMhz4SegmentTraceReplay` | 411 → 687 | 81405 → 77772 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainPachinko2BonusTraceReplay` | 633 → 26 | 17680 → 1859 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainPachinkoBonusTraceReplay` | 760 → 10 | 20952 → 26 | 0 camera_y → 0 camera_y |
| `TestS3kTailsFullChainPachinko3BonusTraceReplay` | 192 → 22 | 3886 → 1368 | 0 camera_y → 0 camera_y |
| `TestS3kSonicTailsSszSegmentTraceReplay` | 3076 → 2558 | 567456 → 577451 | 0 camera_x → 0 camera_x |

### Initial trace-segments changes

| Class | Groups before → after | Cells before → after | First physics row/field before → after |
| --- | --- | --- | --- |
| `TestS3kAizZoneSliceTraceReplay` | 0 → 1456 | 0 → 266166 | none → 16766 y |
| `TestS3kCnzZoneSliceTraceReplay` | 4255 → 7637 | 670447 → 1066128 | 14616 camera_x → 8328 y_speed |
| `TestS3kSonicTailsDez238SegmentTraceReplay` | 819 → 772 | 72740 → 72154 | 0 camera_y → 0 camera_y |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | 1781 → 1808 | 138288 → 137906 | 1948 y → 1948 y |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | 1177 → 1103 | 88174 → 90814 | 0 rings → 0 rings |
| `TestS3kSonicTailsMhz2SegmentTraceReplay` | 631 → 628 | 106528 → 106530 | 0 tails_y_speed → 0 tails_y_speed |
| `TestS3kSonicTailsMhz7SegmentTraceReplay` | 493 → 494 | 50763 → 50822 | 0 camera_y → 0 camera_y |
| `TestS3kSonicTailsPachinkoBonusTraceReplay` | 1638 → 1659 | 52549 → 43523 | 122 rings → 122 rings |
| `TestS3kSonicTailsPachinko2BonusTraceReplay` | 1258 → 443 | 44334 → 23470 | 469 x_speed → 469 x_speed |
| `TestS3kSonicTailsSszSegmentTraceReplay` | 3076 → 2558 | 567456 → 577451 | 0 camera_x → 0 camera_x |

### Regression investigation checkpoint

CNZ's earlier divergence is at row 8328: the native trap door at (0x21E0,
0x0604) lands the falling player, while the candidate retains y-speed 0x07E0.
The old object override samples one historical player position. Its comment
explicitly compensates for the old zero-accepting detector; native
loc_31CCA (sonic3k.asm:67262-67270) and loc_1E45A (sonic3k.asm:42038-42063)
read the current position. A diagnostic replay is queued before changing it.

AIZ's row 16766 bridge has placement status bit 0 set. Obj_AIZFlippingBridge
(sonic3k.asm:58917-58942) resets render_flags to 4, while its status bit only
selects animation direction. The engine currently uses that placement bit
for both animation direction and slope mirroring. With the raw height table
now selected, the independent render-flip error is exposed. A diagnostic
replay is queued before correcting that ownership.

The continued-rider path is also under investigation: SolidObjectTopSloped2_1P
(sonic3k.asm:41885-41913) and sub_3E9C6 (sonic3k.asm:82983-83015) select a
latched rider before the new-landing window and contour without reapplying
that window. No change to this path is justified yet by execution evidence.
The MHZ row 155/171 wall-push frontiers predate the later observed zero contacts.

### Initial trace-replay (S3K selection) changes

| Class/report | Errors before → after | First physics row/field before → after |
| --- | --- | --- |
| `TestS3kSonicTailsCompleteEmeraldRunPrefix` (`run-chain/s3k-sonic-tails-complete-emeralds_dynamic_art_gap-dynamic-art-gap-290e35386aa5ac03.json`) | 0 → 0 | none → none |
| `TestS3kSonicTailsCompleteEmeraldRunPrefix` (`run-chain/s3k-sonic-tails-complete-emeralds_seg6-segment-6-8c15099324776dd5.json`) | 0 → 32948 | none → none |
| `TestS3kSonicTailsCompleteEmeraldRunPrefix` (`run-chain/s3k-sonic-tails-complete-emeralds_seg8-segment-8-d257b47894835eed.json`) | 0 → None | none → none |
| `TestS3kAizTraceReplay` (`trace/s3k_aiz1-single-bc61c7f82ef2fd87.json`) | 0 → 1059 | none → 12256 y_speed |
| `TestS3kCnzTraceReplay` (`trace/s3k_cnz1-single-3b6e6f180fe3b76f.json`) | 3112 → 6976 | 17322 camera_x → 9802 y_speed |

MGZ (8469 errors) and Knuckles LBZ2 Big Arm (232 errors) are unchanged. The
AIZ class's main replay adds a physics divergence at row 12256 and reaches a
different terminal hardware-work assertion. The complete-run prefix fails to
observe the AIZ4 giant-ring exit and consequently does not produce its later
segment report. CNZ adds four native checkpoint failures after its earlier
trap-door landing discrepancy. Those are regressions, not baseline exclusions.

## Executed regression diagnosis and corrections

A five-class diagnostic replay reproduces the initial after reports byte for
byte (normalized report digests match for AIZ/CNZ zone slices, Sonic/Tails HPZ,
MHZ7 and Pachinko). Temporary probes write observations only; all runtime
probe code is removed before the correction measurements.

- CNZ trap-door probe, manager clock 8328: history sample (8689,1508), current
  (8691,1515), radius 19, y-speed 2016, post-movement=true, overlap 0. It rejects.
  At clock 8329 the historical overlap becomes 7 and lands a frame later.
  Native surface is 0x604-9=1531; current overlap at clock 8328 is already
  1515+19+4-1531=7. The ROM helper must land on that frame. The old history
  override is removed; rejection stays. Process_Sprites and sub_1AAFC
  (sonic3k.asm:36005-36037) execute the player slots before the trap door, and
  loc_1E45A reads their current x/y values.
- AIZ probe at the left edge of a flipped-placement bridge (objectX=11840,
  x=11713) reads raw sample +28, proving the engine mirrors the surface using
  the placement direction bit. Native initialization writes render_flags=4;
  status bit 0 controls animation direction only. The raw table is retained,
  while collision sampling and segment drawing use the cleared render flip.
  A component assertion covers flipped placement and both raw table endpoints.
- MHZ7 probe, clock 858: x=4393, y=997, radius=14, surface=1032-17=1015,
  y-speed=840, overlap 0: rejection. Clock 859: x=4396, y=1000, speed=896,
  overlap 3: landing. Both decisions match loc_1E45A on the actual engine
  state. The first differing comparison cells around trace row 857/858 occur
  long after pre-existing x/y/y-speed divergence (including tails_y_speed
  from row 842). Reverting rejection would conceal the native boundary error.
- The rider probe observes 845 continued-riding calls across the five classes,
  including MHZ with sticky=false at clock 119, air=false/on-object=true.
  The inline ride path runs before the generic slope resolver, so the
  suspected sticky-flag/new-landing coupling is ruled out for these replays.
  No continued-rider algorithm change is made.

The correction checks target the two affected zone slices, the r6 AIZ/CNZ
classes and complete-run prefix, then the keep-green/solid-manager/affected
component selection. The remaining initial HPZ/MHZ/Pachinko counts remain
blocking until the final comparison is assessed; they are not waved through
as green because they follow an earlier divergence.

Additional executed observations bound the remaining interpretation:

- Sonic/Tails Pachinko's flipper probe at clock 266 has object (128,3112),
  player (154,3119), radius 14, sample -25, falling speed 968. Surface and
  feet+4 are both 3137: loc_49C8A's SolidObjectTopSloped2 call
  (sonic3k.asm:96425-96430) reaches loc_1E45A and must reject. Clock 267 has
  sample -23 and overlap 5, and lands. Its survey row 122 rings frontier is
  unchanged and precedes this observed contact; zero rejection does not
  establish the cause of that earlier frontier.
- HPZ's continued-rider probe near the first added cell (trace row 3175)
  observes the engine sidekick on SszCollapsingColumn at (4420,2627),
  radius 15, at manager clock 3174. The recorded sidekick is instead near
  (1930,2489), with object_control=$81 and no OnObj. This is already a
  different route/state after the baseline frontier. The later metric
  changes cannot be used as same-state ROM landing evidence. They remain
  blocking metrics under the requested gate; the unchanged earlier HPZ
  frontier and the separate MHZ wall-push frontier still need their own fixes.

### Surveyed Pachinko contacts

The local landing hypothesis is confirmed on all four surveyed contacts.
The after reports have no y-speed, Y, air or status error spanning the target
row, where the baseline lands early and zeroes y-speed:

| Class | Surveyed landing row | Before | After |
| --- | --- | --- | --- |
| TestS3kSonicTailsPachinkoBonusTraceReplay | 266 | y-speed 0x03C8 → 0; Y/air/status wrong | All four fields match |
| TestS3kSonicTailsPachinko2BonusTraceReplay | 1435 | y-speed zeroed; Y/air/status wrong | All four fields match |
| TestS3kTailsFullChainPachinkoBonusTraceReplay | 468 | y-speed zeroed; Y/air/status wrong | All four fields match |
| TestS3kTailsFullChainPachinko2BonusTraceReplay | 666 | y-speed zeroed; Y/air/status wrong | All four fields match |

Rows 122 and 469 cited in the task are the classes' earlier ring and bumper
frontiers; the survey lists the flipper contacts separately. Their unchanged
first-error rows do not mean the landing correction failed. Sonic/Tails
Pachinko nevertheless has a higher later grouped-error total, so the requested
count gate is still blocked rather than waived on that basis.

### Correction measurements completed

The two corrected segment zone slices emit reports byte-identical to baseline:
AIZ is green (zero errors); CNZ is back to 4255 errors, first physics error
14616 camera_x. Two tests run, one inherited CNZ failure, no errors/skips.

The corrected r6 selection runs 47 tests: AIZ 17/17 pass; the complete-run
prefix 3/3 pass; CNZ retains its four baseline failures in 27 tests. All 14
emitted reports match the before report digests exactly, including restored
campaign exit coverage. There are no new errors or skips in this selection.
The earlier AIZ/CNZ regressions are resolved while retaining the native window.
Remaining AIZ/CNZ consumer replays and the final component checks are pending.

## Post-diagnosis commands

These use the environment and ROM_FLAGS above. Commands below omit only the
stdout/stderr redirection into temporary files under target; those outputs are
consumed and removed. The final component command uses the normal memory lane.

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.surefire.reports=target/topsolid-corrected-trace-segments/surefire -Dopenggf.trace.reports=target/topsolid-corrected-trace-segments/trace "-Dtest=TestS3kAizZoneSliceTraceReplay,TestS3kCnzZoneSliceTraceReplay" "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.surefire.reports=target/topsolid-corrected-trace-replay/surefire -Dopenggf.trace.reports=target/topsolid-corrected-trace-replay/trace "-Dtest=TestS3kAizTraceReplay,TestS3kCnzTraceReplay,TestS3kSonicTailsCompleteEmeraldRunPrefix" "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py --normal -Dmse=off -B -Dopenggf.surefire.reports=target/topsolid-corrected-keep "-Dtest=TestCnzRisingPlatformInstance,TestCnzTraversalRegistry,TestDezFinalArenaFloor,TestDezFinalEntryObjects,TestDezFinalScreenEntry,TestDezMinibossArm,TestDezMinibossEncounter,TestFbzTrapSpring,TestHczEndBossChildBuckets,TestHczEndBossWaterColumnControlPolicy,TestHpzSanctuaryObjects,TestLrzDomeLavaPlatform,TestMhzCurledVineObjectInstance,TestMhzMushroomCatapultObjectInstance,TestMhzMushroomPlatformObjectInstance,TestMhzSwingBarVerticalObjectInstance,TestPachinkoFlipperObjectInstance,TestPachinkoPlatformObjectInstance,TestPachinkoRegistry,TestRewindFixS3KBatch4Codecs,TestRewindFixS3KBatch7Codecs,TestRewindFixS3KIczBigSnowPileCodec,TestS3kCnzLocalMechanicsRewind,TestS3kCnzMechanismRewind,TestS3kIczIceObjectRewind,TestS3kPachinkoStandaloneRewind,TestS3kRewindFixBatch1Codecs,TestSonic3kModZoneObjectSet,TestSozBreakableSandRock,TestSozSandMechanisms,TestSozSpringVine,TestSszRotatingPlatformSolidContacts,TestSolidObjectManager,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestAizFlippingBridgeRewind" "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-replay-r7 -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.surefire.reports=target/topsolid-remaining-trace-replay-r7/surefire -Dopenggf.trace.reports=target/topsolid-remaining-trace-replay-r7/trace "-Dtest=TestS3kKnucklesSuperEmeraldRunChain,TestS3kMegaRunChain,TestS3kTailsFullChainAiz2SegmentTraceReplay,TestS3kTailsFullChainAiz3SegmentTraceReplay,TestS3kTailsFullChainAiz4SegmentTraceReplay,TestS3kTailsFullChainAiz5SegmentTraceReplay,TestS3kTailsFullChainAizSegmentTraceReplay,TestS3kTailsFullChainCnzSegmentTraceReplay,TestS3kTailsFullChainRunChain,TestS3kTailsFullChainRunPrefix" "${ROM_FLAGS[@]}" test
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.surefire.reports=target/topsolid-remaining-trace-segments/surefire -Dopenggf.trace.reports=target/topsolid-remaining-trace-segments/trace "-Dtest=TestS3kSonicTailsAiz2SegmentTraceReplay,TestS3kSonicTailsAiz3SegmentTraceReplay,TestS3kSonicTailsAiz4SegmentTraceReplay,TestS3kSonicTailsAiz5SegmentTraceReplay,TestS3kSonicTailsAizSegmentTraceReplay,TestS3kSonicTailsCnzSegmentTraceReplay" "${ROM_FLAGS[@]}" test
```

## Final replay comparison

The full shared-helper campaign ran once before and once on the first native
contract candidate. The subsequent AIZ/CNZ owner repairs were rechecked across
all their affected consumers and prefixes. The after column below combines
that full campaign with these bounded repair checks; it is not represented as
a second full-profile invocation on the repaired source. All 19 remaining r7
reports and all six remaining segment reports match baseline digests exactly.

Coverage after the repair checks: r7 117 tests / 84 failures / 12 errors / zero
skips; segments 70 / 48 / 8 / zero; S3K r6 86 / 6 / 1 / two. These retain the
baseline test-outcome identities. Every baseline report is accounted for:
119 r7, 69 segment and 17 r6 reports. R6 has no changed report digest. The two
r6 skips and inherited error identities remain as listed above.

Every class with changed replay numbers is listed below. First ERROR is the
earliest error span across physics and animation, not just the physics group.
Those row/field values are unchanged before/after. The earlier tables retain
first-physics values and mismatch-cell totals for detailed interpretation.

| Class | Profiles | Groups before → after | First ERROR row/field, before = after |
| --- | --- | --- | --- |
| `TestS3kSonicTailsDez238SegmentTraceReplay` | r7, seg | 819 → 772 | 0 camera_y |
| `TestS3kSonicTailsHpz2SegmentTraceReplay` | r7, seg | 1177 → 1103 | 0 rings |
| `TestS3kSonicTailsHpzSegmentTraceReplay` | r7, seg | 1781 → 1808 | 121 tails_mapping_frame |
| `TestS3kSonicTailsMhz2SegmentTraceReplay` | r7, seg | 631 → 628 | 0 tails_y_speed |
| `TestS3kSonicTailsMhz7SegmentTraceReplay` | r7, seg | 493 → 494 | 0 camera_y |
| `TestS3kSonicTailsPachinko2BonusTraceReplay` | seg | 1258 → 443 | 469 x_speed |
| `TestS3kSonicTailsPachinkoBonusTraceReplay` | seg | 1638 → 1659 | 122 rings |
| `TestS3kSonicTailsSszSegmentTraceReplay` | r7, seg | 3076 → 2558 | 0 camera_x |
| `TestS3kTailsFullChainDez238SegmentTraceReplay` | r7 | 434 → 412 | 0 camera_y |
| `TestS3kTailsFullChainHpzSegmentTraceReplay` | r7 | 742 → 740 | 2159 y |
| `TestS3kTailsFullChainMhz4SegmentTraceReplay` | r7 | 411 → 687 | 0 camera_y |
| `TestS3kTailsFullChainPachinko2BonusTraceReplay` | r7 | 633 → 26 | 0 camera_y |
| `TestS3kTailsFullChainPachinko3BonusTraceReplay` | r7 | 192 → 22 | 0 camera_y |
| `TestS3kTailsFullChainPachinkoBonusTraceReplay` | r7 | 760 → 10 | 0 camera_y |

**Delivery remains blocked.** Four classes have higher grouped-error totals:
Sonic/Tails HPZ (1781→1808), Sonic/Tails MHZ7 (493→494), Tails Full Chain MHZ4
(411→687), and Sonic/Tails Pachinko (1638→1659). This is not called a green
migration. The local unsigned-window decisions have ROM and executed evidence;
that does not waive the requested whole-class metric gate. MHZ4's aggregate
increase has not been independently attributed beyond its later, already
diverged portion. No native rejection is reverted or softened to fit a replay.

The four requested first-step LRZ/HPZ replay classes retain their accepted
first-step report digests; the new dome binding does not disturb those routes.
The final component results are recorded below.

## Final component checks and held delivery

The combined ordinary selection executes 410 tests: 409 pass and the new CNZ
current-position witness fails because its fixture omitted the normal initial
snapshot. AbstractObjectInstance.solidContactFirstFrame remains true until
snapshotPreUpdatePosition clears it, so the test never reaches collision
resolution. This is corrected by advancing that fixture lifecycle, without
changing runtime code or assertions. The complete 90-test solid-manager class
then passes with zero failures/errors/skips. The other 320 component/keep-green
tests passed in the combined run. This is 410 tests covered across completed
invocations, not a claim that the earlier 410-test invocation was green.
All 60 keep-green tests pass. The 252 direct component tests (including corrected
Pachinko profile expectations) and eight AIZ rewind/surface tests pass.

The final rerun uses the same environment/ROM_FLAGS:

```bash
python3 tools/testing/maven_queue.py --normal -Dmse=off -B -Dopenggf.surefire.reports=target/topsolid-final-manager -Dtest=TestSolidObjectManager "${ROM_FLAGS[@]}" test
```

The read-only diagnostic checkpoint used temporary probes, now removed:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -Ptrace-segments -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical -Dopenggf.debug.topSolidWindow=true -Dopenggf.surefire.reports=target/topsolid-probe/surefire -Dopenggf.trace.reports=target/topsolid-probe/trace '-Dtest=TestS3kAizZoneSliceTraceReplay,TestS3kCnzZoneSliceTraceReplay,TestS3kSonicTailsHpzSegmentTraceReplay,TestS3kSonicTailsMhz7SegmentTraceReplay,TestS3kSonicTailsPachinkoBonusTraceReplay' "${ROM_FLAGS[@]}" test
```

No trace fixtures, comparison tolerances, gameplay hydration, frame exceptions
or recorder metadata are changed. Runtime/test report-owner prints are removed.
The four higher grouped-error totals remain unresolved and block delivery.
The second commit is preserved locally for lead review; there is no push or
merge. The worktree and branch are retained, as requested. Temporary diagnostic
reports and scripts are consumed and removed after recording these results.
No release full-suite or broad smoke run is claimed.

## Proposed frontier-log entry (lead-owned file is untouched)

> 2026-11-02 — S3K top-solid helper audit follow-up, candidate held.
> Base: rebased first-step 6f454d71b on origin/develop 506c653a0.
> Native flat/sloped providers select loc_1E45A's 1..16 window at 24 proved
> missing callers; four apparent gaps already used that window. d3/raw-surface
> and AIZ render-flip/CNZ sampling corrections preserve native helper ownership.
> Pachinko landing errors clear at Sonic/Tails 266/1435 and Tails Full Chain
> 468/666. Earlier shield/bumper and MHZ wall-push frontiers remain.
> Full r7/segments and all S3K r6 classes measured before/after, with all affected
> AIZ/CNZ consumers rechecked after repairs. 410 focused tests covered; keep-green
> passes. Four class totals worsen: Sonic/Tails HPZ 1781→1808, Sonic/Tails MHZ7
> 493→494, Tails Full Chain MHZ4 411→687, Sonic/Tails Pachinko 1638→1659.
> Delivery is blocked; no native rejection is reverted to fit a trace.
> See docs/architecture/audits/2026-11-02-s3k-top-solid-helper-contract.md for
> every changed class, exact commands, skips, inherited failures and ROM evidence.
